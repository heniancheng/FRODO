/*
FRODO: a FRamework for Open/Distributed Optimization
Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek

FRODO is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

FRODO is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.


How to contact the authors: 
<http://frodo2.sourceforge.net/>
*/

package frodo2.algorithms.dpop.privacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** Enables sending a message to the previous/next variable in a circular ordering of variables based on a DFS
 * 
 * This is the routing procedure from the following paper: 
 * Thomas Leaute and Boi Faltings. Privacy-preserving multi-agent constraint satisfaction. In Proceedings of 
 * the 2009 IEEE International Conference on PrivAcy, Security, riSk and Trust (PASSAT'09), Vancouver, 
 * British Columbia, August 29-31 2009. IEEE Computer Society Press.
 * 
 * @author Thomas Leaute
 */
public class SecureCircularRouting implements StatsReporter {
	
	/** The type of the wrapper messages used to forward a message to the next variable in the ordering */
	public static String NEXT_MSG_TYPE = "ToNext";
	
	/** The type of the wrapper messages used to forward a message to the previous variable in the ordering */
	public static String PREVIOUS_MSG_TYPE = "ToPrev";
	
	/** The type of the output messages containing a message to be delivered to a given variable */
	public static String DELIVERY_MSG_TYPE = "Delivery";
	
	/** The type of the wrapper message containing a payload message 
	 * that must be forwarded to the last leaf in the sub-tree rooted at the destination variable 
	 */
	static String TO_LAST_LEAF_MSG_TYPE = "ToLeaf";
	
	/** The type of the messages containing statistics */
	public static final String STATS_MSG_TYPE = "LinearOrderStats";
	
	/** The agent's queue */
	private Queue queue;
	
	/** Whether the stats gatherer should print out the circular ordering */
	private boolean silent = true;
	
	/** The problem */
	private DCOPProblemInterface<?, ?> problem;
	
	/** The underlying DFS */
	private HashMap< String, DFSview<?, ?> > dfs;
	
	/** For each variable, the Messages that are pending the reception of the DFS information for that variable */
	private HashMap< String, ArrayList<Message> > pendingMsgs;
	
	/** Whether the problem has already been parsed */
	private boolean started = false;

	/** For each known variable, the name of the agent that owns it */
	private Map<String, String> owners;

	/** Renderer to display DOT code */
	private String dotRendererClass = null;
	
	/** The iteration number */
	private int iter;
	
	/** Constructor in "stats gatherer" mode
	 * @param params 	the parameters of the module
	 * @param problem 	the overall problem
	 */
	public SecureCircularRouting (Element params, DCOPProblemInterface<?, ?> problem) {
		this.problem = problem;
		dfs = new HashMap< String, DFSview<?, ?> > (problem.getVariables().size());
		
		if (params != null) {
			String silent = params.getAttributeValue("reportStats");
			if (silent != null) 
				this.silent = ! Boolean.parseBoolean(silent);
			
			dotRendererClass = params.getAttributeValue("DOTrenderer");
		}
		if (this.dotRendererClass == null) 
			this.dotRendererClass = "";
	}
	
	/** Constructor
	 * @param problem 	the agent's subproblem
	 * @param params 	the parameters of the module
	 */
	public SecureCircularRouting (DCOPProblemInterface<?, ?> problem, Element params) {
		this.problem = problem;
	}
	
	/** Parses the problem */
	private void init () {
		this.owners = problem.getOwners();
		dfs = new HashMap< String, DFSview<?, ?> > (problem.getNbrIntVars());
		this.pendingMsgs = new HashMap< String, ArrayList<Message> > ();

		this.started = true;
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.dfs = new HashMap< String, DFSview<?, ?> > ();
		this.pendingMsgs = null;
		this.owners = null;
		this.iter = 0;
		this.started = false;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(STATS_MSG_TYPE, this);
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (7);
		types.add(AgentInterface.START_AGENT);
		types.add(DFSgeneration.OUTPUT_MSG_TYPE);
		types.add(DFSgenerationWithOrder.OUTPUT_MSG_TYPE);
		types.add(NEXT_MSG_TYPE);
		types.add(PREVIOUS_MSG_TYPE);
		types.add(TO_LAST_LEAF_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#notifyIn(Message) */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(STATS_MSG_TYPE)) { // in stats gatherer mode
			
			// Parse the information in the message
			DFSgeneration.MessageDFSoutput<?, ?> msgCast = (MessageDFSoutput<?, ?>) msg;
			String var = msgCast.getVar();
			DFSview<?, ?> neighbors = msgCast.getNeighbors();
			
			if (neighbors == null) // DFS output reset
				return;
			
			// If we already know the relationships for this variable, first reset all relationships for the corresponding component
			if (this.dfs.containsKey(var)) 
				this.resetComponent(var);
			
			// Record the variable's parent and children
			this.dfs.put(var, neighbors);
			
			if (! this.silent && dfs.size() == problem.getVariables().size()) { // print out the linear ordering. 

				Map< String, ? extends Collection<String> > neighborhoods = this.problem.getNeighborhoods();

				StringBuilder builder = new StringBuilder ("digraph {\n");
				builder.append("\tgraph [rankdir = \"LR\"];\n");
				builder.append("\tnode [shape = \"circle\" style = \"filled\"];\n\n");

				// Traverse each DFS
				for (Map.Entry< String, DFSview<?, ?> > entry : dfs.entrySet()) {
					DFSview<?, ?> relationships = entry.getValue();
					String myVar = entry.getKey();
					List<String> children = relationships.getChildren();
					
					builder.append("\t" + myVar + " [style=\"filled\"];\n");

					if (relationships.getParent() == null) { // the variable is a root
						
						ArrayList<String> order = new ArrayList<String> ();
						this.traverse(myVar, order);

						int nbrNonLastVars = order.size() - 1;
						for (int i = 0; i < nbrNonLastVars; i++) {
							String head = order.get(i);
							String tail = order.get(i + 1);

							// Check whether the two variables are not neighbors
							if (! neighborhoods.get(tail).contains(head)) 
								builder.append("\t" + head + " -> " + tail + " [style = \"dotted\"];\n");
						}
					}

					// Plot edges with children
					if (! children.isEmpty()) {

						// Plot a full edge with the first child
						builder.append("\t" + myVar + " -> " + children.get(0) + ";\n");

						// Plot backedges with other children
						int nbrChildren = children.size();
						for (int j = 1; j < nbrChildren; j++) 
							builder.append("\t" + myVar + " -> " + children.get(j) + 
									" [style = \"dashed\" arrowhead = \"none\" constraint = \"false\"];\n");
					}

					// Plot backedges with pseudo-children
					for (String pseudo : relationships.getAllPseudoChildren()) 
						builder.append("\t" + myVar + " -> " + pseudo + 
								" [style = \"dashed\" arrowhead = \"none\" constraint = \"false\"];\n");

					builder.append("\n");
				}
				
				builder.append("}\n");
				
				// Check which DOT renderer should be used
				if(dotRendererClass.equals("")) {
					System.out.println("Chosen secure linear order at iteration " + (iter++) + ":");
					System.out.println(builder);
				}
				else {
					try {
						Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("Secure linear order at iteration " + (iter++), builder.toString());
					} 
					catch(Exception e) {
						System.out.println("Could not instantiate given DOT renderer class: " + this.dotRendererClass);
					}
				}
			}
			
			return;
		}
		
		else if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if it has not been done yet
		if (! this.started) 
			init();
		
		if (msgType.equals(AgentInterface.START_AGENT)) 
			return;
		
		else if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE) || msgType.equals(DFSgenerationWithOrder.OUTPUT_MSG_TYPE)) { 
			// a message containing information about a variable's neighbors in the DFS

			// Parse the information in the message
			DFSgeneration.MessageDFSoutput<?, ?> msgCast = (MessageDFSoutput<?, ?>) msg;
			String var = msgCast.getVar();
			DFSview<?, ?> neighbors = msgCast.getNeighbors();

			// Record the variable's parent and children
			this.dfs.put(var, neighbors);
			
			// Report this part of the DFS to the stats gatherer
			queue.sendMessage(AgentInterface.STATS_MONITOR, new DFSgeneration.MessageDFSoutput(STATS_MSG_TYPE, var, neighbors));
			
			// Process pending messages, if any
			List<Message> pending = this.pendingMsgs.remove(var);
			if (pending != null) 
				for (Message pendingMsg : pending) 
					this.notifyIn(pendingMsg);

			return;
		}
		
		else if (msgType.equals(TO_LAST_LEAF_MSG_TYPE)) { // a wrapper message containing a payload that must be forwarded to the last leaf
			
			// Parse the information from the message
			ToLastLeafMsg msgCast = (ToLastLeafMsg) msg;
			String var = msgCast.getVar();
			
			// Postpone this message if we haven't received the DFS information for this variable yet
			DFSview<?, ?> neighbors = this.dfs.get(var);
			if (neighbors == null) {
				ArrayList<Message> pending = this.pendingMsgs.get(var);
				if (pending == null) {
					pending = new ArrayList<Message> ();
					this.pendingMsgs.put(var, pending);
				}
				pending.add(msg);
				return;
			}
			
			// Look up the last child of var
			List<String> children = neighbors.getChildren();
			if (children.isEmpty()) // var is a leaf; simply deliver the message
				queue.sendMessageToSelf(new DeliveryMsg<Message> (var, msgCast.getPayload()));
			
			else { // route the payload message through var's last child
				String lastChild = children.get(children.size() - 1);
				queue.sendMessage(this.owners.get(lastChild), new ToLastLeafMsg (lastChild, msgCast.getPayload()));
			}
			
			return;
		}
		
		// Parse the information in the routing message
		RoutingMsg<Message> msgCast = (RoutingMsg<Message>) msg;
		String sender = msgCast.getSender();
		String dest = msgCast.getDest();
		
		// Get the destination's DFS information; postpone the message if this information has not be received yet
		DFSview<?, ?> neighbors = this.dfs.get(dest);
		if (neighbors == null) {
			ArrayList<Message> pending = this.pendingMsgs.get(dest);
			if (pending == null) {
				pending = new ArrayList<Message> ();
				this.pendingMsgs.put(dest, pending);
			}
			pending.add(msg);
			return;
		}
		
		if (msgType.equals(NEXT_MSG_TYPE)) { // contains a payload message that must be routed to the next variable in the ordering
			
			// Get the destination's list of children
			List<String> children = neighbors.getChildren();

			if (sender.equals(dest)) { // this is the very first routing request sent by a client module
				
				if (children.isEmpty()) { // no children
					
					// Route the payload message through the destination's DFS parent
					assert neighbors.getParent() != null : 
						"Attempting to route a message to the next variable of the isolated variable " + dest;
					String parent = neighbors.getParent();
					queue.sendMessage(this.owners.get(parent), new RoutingMsg<Message> (NEXT_MSG_TYPE, dest, msgCast.getPayload(), parent));
				}
				
				else { // simply forward the payload message to the first child
					String firstChild = children.get(0);
					queue.sendMessage(this.owners.get(firstChild), new DeliveryMsg<Message> (firstChild, msgCast.getPayload()));
				}
			}
			
			else { // the routing message was generated by this module
				
				// Look for the next variable after the sender in the destination's list of children
				int pos = children.indexOf(sender);
				if (pos < children.size() - 1) { // sender is not the last child; simply forward the payload message to the next child
					String nextChild = children.get(pos + 1);
					queue.sendMessage(this.owners.get(nextChild), new DeliveryMsg<Message> (nextChild, msgCast.getPayload()));
				}
				
				else { // sender is the last child
					
					// Check if the destination is the root of the DFS
					String parent = neighbors.getParent();
					if (parent == null) { // DFS root
						
						// This is the case when the DFS' last leaf wants to send a message to its next variable, i.e. the root
						queue.sendMessageToSelf(new DeliveryMsg<Message> (dest, msgCast.getPayload()));
						
					} else { // not the root of the DFS
						
						// Route the payload message through the destination's DFS parent
						queue.sendMessage(this.owners.get(parent), new RoutingMsg<Message> (NEXT_MSG_TYPE, dest, msgCast.getPayload(), parent));
					}
				}
			}
		}
		
		else if (msgType.equals(PREVIOUS_MSG_TYPE)) {
			// contains a payload message that must be routed to the previous variable in the ordering
			
			if (sender.equals(dest)) { // this is the very first routing request sent by a client module
				
				// Check whether the sender is the root of the DFS
				String parent = neighbors.getParent();
				if (parent == null) { // sender is root
					
					// The root's previous variable is the last leaf
					List<String> children = neighbors.getChildren();
					String lastChild = children.get(children.size() - 1);
					queue.sendMessage(this.owners.get(lastChild), new ToLastLeafMsg (lastChild, msgCast.getPayload()));
					
				} else { // sender is not root
					
					// Route the payload message through the destination's DFS parent
					queue.sendMessage(this.owners.get(parent), new RoutingMsg<Message> (PREVIOUS_MSG_TYPE, dest, msgCast.getPayload(), parent));
				}
			}
			
			else { // the routing message was generated by this module
				
				// Look for the first variable before the sender in the destination's list of children
				List<String> children = neighbors.getChildren();
				int pos = children.indexOf(sender);
				assert pos >= 0 : "The sender of the following message is absent from the list " + children 
									+ " of the destination's children: " + msg;
				if (pos == 0) // sender is the first child; simply deliver the message to the parent/destination
					queue.sendMessageToSelf(new DeliveryMsg<Message> (dest, msgCast.getPayload()));
				
				else { // route the message to the last leaf of the sub-tree rooted at the previous child
					String prevChild = children.get(pos - 1);
					queue.sendMessage(this.owners.get(prevChild), new ToLastLeafMsg (prevChild, msgCast.getPayload()));
				}
			}
		}
		
	}

	/** Resets all entries in this.dfs corresponding to variables in the component of the input variable
	 * @param var 	a variable in the component to be reset
	 */
	private void resetComponent(String var) {
		
		// First find the root of this component
		String root = var;
		String parent = this.dfs.get(root).getParent();
		while (parent != null) {
			root = parent;
			parent = this.dfs.get(root).getParent();
		}
		
		// Iteratively remove all descendants of the root from this.relationships
		ArrayList<String> openList = new ArrayList<String> ();
		openList.add(root);
		while (! openList.isEmpty()) 
			openList.addAll(this.dfs.remove(openList.remove(0)).getChildren());
	}

	/** Traverses a DFS subtree
	 * @param root 		the root of the subtree 
	 * @param order 	the list to which visited nodes should be added
	 */
	private void traverse(String root, ArrayList<String> order) {
		
		order.add(root);
		
		// Call recursively on each child
		for (String child : this.dfs.get(root).getChildren()) 
			traverse(child, order);
	}

}
