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

package frodo2.algorithms.varOrdering.linear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** Creates a linear ordering among variables
 * 
 * All agents report their variables (along with possible heuristic-related information) to the first agent in lexicographic order, 
 * and this agent then centrally chooses a linear order on all variables, and broadcasts the result to all agents. 
 * 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @warning Requires that each agent know the identity of all other agents in the problem. 
 * 
 * @author Thomas Leaute
 */
public abstract class CentralLinearOrdering < V extends Addable<V>, U extends Addable<U> > implements StatsReporter {
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of messages sent by agents to the dictator with information on their variables */
	public static final String REPORT_MSG_TYPE = "VarOrderReport";
	
	/** The type of the message notifying the agent of the chosen ordering, excluding the spaces */
	private static final String TEMP_OUTPUT_MSG_TYPE = "VarOrderNoSpace";

	/** This module's queue */
	protected Queue queue;
	
	/** Whether the stats gatherer should display the resulting linear order */
	private boolean silent = false;
	
	/** The problem */
	protected DCOPProblemInterface<V, U> problem;
	
	/** Whether the module has already started the algorithm */
	protected boolean started = false;
	
	/** The name of the agent that chooses the variable order */
	protected String dictator;

	/** The sets of all agents in the problem */
	private Set<String> allAgents;

	/** The number of reports we are still waiting for from other agents */
	private int countdown = 0;
	
	/** Who owns each agent */
	protected Map<String, String> owners;

	/** This agent's ID */
	protected String myID;

	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public CentralLinearOrdering (Element parameters, DCOPProblemInterface<V, U> problem)  {
		this.problem = problem;
	}
	
	/** Constructor
	 * @param problem 		this agent's problem
	 * @param parameters 	the parameters for CentralLinearOrdering
	 */
	public CentralLinearOrdering (DCOPProblemInterface<V, U> problem, Element parameters) {
		this.problem = problem;
	}
	
	/** Parses the problem */
	protected void init () {
		
		// Choose the first agent in lexicographic order to be the dictator
		Set<String> allAgents = this.problem.getAgents();
		Iterator<String> iter = allAgents.iterator();
		this.dictator = iter.next();
		while (iter.hasNext()) {
			String var = iter.next();
			if (this.dictator.compareTo(var) > 0) 
				this.dictator = var;
		}
		
		// If I am the dictator, initialize additional fields
		myID = this.problem.getAgent();
		if (myID.equals(this.dictator)) {
			this.countdown = allAgents.size();
			this.allAgents = allAgents;
		}
		
		// Initialize the owners map with my variables
		this.owners = new HashMap<String, String> ();
		for (String var : this.problem.getMyVars()) 
			this.owners.put(var, myID);
		
		// Report my variables to the dictator
		this.reportVars();
		
		if (! myID.equals(this.dictator) && this.owners.isEmpty()) { // I am not the dictator and I own no variable; terminate immediately
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			this.started = true;
			return;
		}
		
		this.started = true;
	}
	
	/** @see StatsReporter#reset() */
	public void reset() {
		this.allAgents = null;
		this.countdown = 0;
		this.dictator = null;
		this.myID = null;
		this.owners = null;
		this.started = false;
	}

	/** Sends all necessary information about variables to the dictator */
	protected abstract void reportVars();

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OrderMsg.STATS_MSG_TYPE, this);
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (4);
		types.add(START_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		types.add(REPORT_MSG_TYPE);
		types.add(TEMP_OUTPUT_MSG_TYPE);
		return types;
	}

	/** @see StatsReporter#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {

		String msgType = msg.getType();
		
		if (msgType.equals(OrderMsg.STATS_MSG_TYPE)) { // stats message containing the linear order
			
			if (! this.silent) 
				this.printOrder(((OrderMsg<V, U>)msg).getFlatOrder());
			
			return;
		}
		
		if (! this.started) 
			this.init();
		
		if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			
			this.reset();
			return;
		}
		
		else if (msgType.equals(REPORT_MSG_TYPE)) {
			
			// Forget about this agent if it owns no variable
			MessageWith3Payloads< String, HashMap< String, Collection<String> >, HashMap<String, Integer> > msgCast = 
				(MessageWith3Payloads< String, HashMap< String, Collection<String> >, HashMap<String, Integer> >) msg;
			if (msgCast.getPayload2().isEmpty()) 
				this.allAgents.remove(msgCast.getPayload1());
			
			if (--this.countdown == 0) { // I have received all reports from the other agents
				
				// Choose the order on the variables
				String[] order = this.chooseOrder();
				int nbrVars = order.length;
				
				// Construct the corresponding list of owners
				String[] agents = new String [nbrVars];
				for (int i = 0; i < nbrVars; i++) 
					agents[i] = this.owners.get(order[i]);
				
				// Send the order to all other agents and to the stats gatherer
				this.queue.sendMessageToMulti(this.allAgents, new OrderMsg<V, U> (TEMP_OUTPUT_MSG_TYPE, 0, Arrays.asList(order), Arrays.asList(agents)));
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new OrderMsg<V, U> (OrderMsg.STATS_MSG_TYPE, 0, Arrays.asList(order), Arrays.asList(agents)));
				
				// If I own no variable, my job is done
				if (this.problem.getNbrIntVars() == 0) 
					this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			}
		}
		
		else if (msgType.equals(TEMP_OUTPUT_MSG_TYPE)) { // the chosen ordering, for which I must now parse the spaces
			
			OrderMsg<V, U> msgCast = (OrderMsg<V, U>) msg;
			List< List<String> > order = msgCast.getOrder();
			
			// Parse which space each of my clusters is responsible for enforcing
			ArrayList< UtilitySolutionSpace<V, U> > jointSpaces = new ArrayList< UtilitySolutionSpace<V, U> > (order.size());
			HashSet<String> nextVars = new HashSet<String> (this.problem.getVariables());
			for (List<String> cluster : order) {
				
				UtilitySolutionSpace<V, U> space = null;
				nextVars.removeAll(cluster);
				
				// Check if I own this cluster
				if (this.myID.equals(this.owners.get(cluster.get(0)))) {

					// Find the spaces this cluster is responsible for enforcing
					// i.e. the spaces for which it is the last cluster in the ordering
					List< ? extends UtilitySolutionSpace<V, U> > spaces = this.problem.getSolutionSpaces(new HashSet<String>(cluster), false, nextVars);
					if(!spaces.isEmpty()){
						space = spaces.remove(0);
						if (!spaces.isEmpty()) 
							space = space.join(spaces.toArray(new UtilitySolutionSpace [spaces.size()]));
						spaces.clear();
					}
				}
				
				jointSpaces.add(space);
			}
			
			// Send the output message including the spaces
			this.queue.sendMessageToSelf(new OrderMsg<V, U> (OrderMsg.ORDER_MSG_TYPE, order, 
					msgCast.getFlatOrder(), msgCast.getAgents(), msgCast.getComponentID(), jointSpaces));
		}
	}
	
	/** @return the chosen order on variables */
	protected abstract String[] chooseOrder ();

	/** Prints the input linear order in DOT format
	 * @param order 	the variable order
	 */
	private void printOrder(List<String> order) {
		
		StringBuilder builder = new StringBuilder ("Chosen linear order: \n");
		builder.append("graph {\n");
		builder.append("\trankdir = \"LR\";\n");
		builder.append("\tnode [shape = \"circle\" style = \"filled\"];\n\n");
		
		// First draw the nodes in the given order, linked with invisible edges
		int nbrVars = order.size();
		if (nbrVars == 1) 
			builder.append("\t" + order.get(0) + ";\n\n");
		else {
			builder.append("\t" + order.get(0));
			for (int i = 1; i < nbrVars; i++) 
				builder.append(" -- " + order.get(i));
			builder.append(" [style = \"invisible\"];\n\n");
		}
		
		// Draw the neighborhoods
		for (Map.Entry< String, ? extends Collection<String> > entry : problem.getNeighborhoods().entrySet()) {
			String var = entry.getKey();
			
			// Go through the collection of neighbors for the current variable
			for (String neigh : entry.getValue()) 
				if (var.compareTo(neigh) < 0) // don't draw the same constraint twice
					builder.append("\t" + var + " -- " + neigh + " [constraint = \"false\"];\n");
		}
		
		builder.append("}\n");
		
		System.out.println(builder.toString());
	}
	
	/** A linear ordering heuristic that chooses first variables with highest numbers of links with previous variables, 
	 * breaking ties by minimizing domain size. 
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 * @author Thomas Leaute
	 */
	public static class MaxWidthMinDom < V extends Addable<V>, U extends Addable<U> > extends CentralLinearOrdering<V, U> {
		
		/** For each variable, its list of neighbors */
		private HashMap< String, Collection<String> > neighborhoods;
		
		/** The domain size of each variable */
		private HashMap<String, Integer> domSizes;

		/** The constructor called in "statistics gatherer" mode
		 * @param problem 		the overall problem
		 * @param parameters 	the description of what statistics should be reported (currently unused)
		 */
		public MaxWidthMinDom(Element parameters, DCOPProblemInterface<V, U> problem) {
			super(parameters, problem);
		}

		/** Constructor
		 * @param problem 		this agent's problem
		 * @param parameters 	the parameters for MaxWidthMinDom
		 */
		public MaxWidthMinDom(DCOPProblemInterface<V, U> problem, Element parameters) {
			super(problem, parameters);
		}
		
		/** @see CentralLinearOrdering#init() */
		@Override
		protected void init () {
			super.init();
			
			if (this.dictator.equals(this.myID)) {
				this.neighborhoods = new HashMap< String, Collection<String> > ();
				this.domSizes = new HashMap<String, Integer> ();
			}
		}
		
		/** @see CentralLinearOrdering#reset() */
		@Override
		public void reset () {
			super.reset();
			this.neighborhoods = null;
			this.domSizes = null;
		}
		
		/** @see CentralLinearOrdering#reportVars() */
		@SuppressWarnings("unchecked")
		@Override
		protected void reportVars() {
			
			HashMap<String, Collection<String>> neighborhoods = (HashMap<String, Collection<String>>) this.problem.getNeighborhoods();
			HashMap<String, Integer> domSizes = new HashMap<String, Integer> ();
			for (String var : this.problem.getMyVars()) 
				domSizes.put(var, this.problem.getDomainSize(var));

			this.queue.sendMessage(this.dictator, 
					new MessageWith3Payloads< String, HashMap< String, Collection<String> >, HashMap<String, Integer> > (REPORT_MSG_TYPE, this.myID, neighborhoods, domSizes));
		}
		
		/** @see CentralLinearOrdering#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			if (msg.getType().equals(REPORT_MSG_TYPE)) { // information about variables owned by another agent
				
				if (! this.started) 
					this.init();
				
				// Retrieve information from the message
				MessageWith3Payloads< String, HashMap< String, Collection<String> >, HashMap<String, Integer> > msgCast = 
					(MessageWith3Payloads< String, HashMap< String, Collection<String> >, HashMap<String, Integer> >) msg;
				HashMap< String, Collection<String> > myNeighborhoods = msgCast.getPayload2();
				this.neighborhoods.putAll(myNeighborhoods);
				this.domSizes.putAll(msgCast.getPayload3());
				
				// Add the sender's variables to the owners map
				String agent = msgCast.getPayload1();
				for (String var : myNeighborhoods.keySet()) 
					this.owners.put(var, agent);

			}
			
			super.notifyIn(msg);
		}

		/** @see CentralLinearOrdering#chooseOrder() */
		@Override
		protected String[] chooseOrder() {
			
			LinkedList<String> openVars = new LinkedList<String> (this.owners.keySet());
			int nbrVars = openVars.size();
			String[] order = new String [nbrVars];
			
			// Order variables by putting first the ones that have the highest number of links with previous variables, 
			// breaking ties by minimizing domain size
			for (int i = 0; i < nbrVars; i++) {
				
				String bestVar = null;
				int maxWidth = 0;
				int minDomSize = 0;
				
				// Go through the list of remaining variables 
				for (String var : openVars) {
					
					// Compute the local width of this variable as the number of neighbors that come before in the ordering
					Collection<String> neighbors = this.neighborhoods.get(var);
					int width = 0;
					for (int j = 0; j < i; j++) 
						if (neighbors.contains(order[j])) 
							width++;
					
					// Compare with the best variable found so far
					int domSize = this.domSizes.get(var);
					if (bestVar == null || width > maxWidth || (width == maxWidth && domSize < minDomSize)) {
						bestVar = var;
						maxWidth = width;
						minDomSize = domSize;
					}
				}
				
				order[i] = bestVar;
				openVars.remove(bestVar);
			}
			
			return order;
		}

	}

}
