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

/** Algorithms to produce linear variable orderings */
package frodo2.algorithms.varOrdering.linear;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID.MessageLEoutput;
import frodo2.algorithms.varOrdering.linear.LinearOrdering.MaxWidthMinDom.IntIntStringTuple;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** Distributed algorithm to compute one variable linear ordering per connected component in the constraint graph
 * 
 * The algorithm is initiated by the variable(s) chosen by VariableElection. At each iteration, 
 * the current variable prompts all agents for their best proposals for the next variable. 
 * 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @warning Requires that each agent know the identity of all other agents in the problem. 
 * 
 * @todo Write a dedicated unit test. 
 * 
 * @author Thomas Leaute
 */
public class LinearOrdering < V extends Addable<V>, U extends Addable<U> > implements StatsReporter {

	/** Variable ordering heuristic
	 * @note All heuristics must have a constructor that takes in a ProblemInterface. 
	 * @param <T> the type used for variable scores
	 */
	public static interface Heuristic < T extends Comparable<T> & Serializable > {

		/** Computes the score of a given variable
		 * @param order 	the current partial order
		 * @param var 		the variable
		 * @return 			the score for the variable
		 */
		public T getScore (List<String> order, String var);
	}

	/** A heuristic that maximizes the number of neighbors already in the order, 
	 * breaking ties by minimizing the domain size, and then by variable name
	 */
	public static class MaxWidthMinDom implements Heuristic <IntIntStringTuple> {

		/** An (int, int, String) tuple */
		public static class IntIntStringTuple implements Comparable<IntIntStringTuple>, Serializable {

			/** Used for serialization */
			private static final long serialVersionUID = -8595083726015081194L;

			/** The first integer */
			private int int1;

			/** The second integer */
			private int int2;
			
			/** The String */
			private String str;

			/** Constructor
			 * @param int1 	the first integer
			 * @param int2 	the second integer
			 * @param str 	the String
			 */
			public IntIntStringTuple (int int1, int int2, String str) {
				this.int1 = int1;
				this.int2 = int2;
				this.str = str;
			}

			/** @see java.lang.Comparable#compareTo(java.lang.Object) */
			public int compareTo(IntIntStringTuple o) {

				if (this.int1 > o.int1) 
					return +1;
				else if (this.int1 < o.int1) 
					return -1;
				else if (this.int2 > o.int2) 
					return +1;
				else if (this.int2 < o.int2) 
					return -1;
				else 
					return this.str.compareTo(o.str);
			}

			/** @see java.lang.Object#toString() */
			@Override
			public String toString () {
				return "[" + this.int1 + "; " + this.int2 + "; " + this.str + "]";
			}
		}

		/** The agent's subproblem */
		private DCOPProblemInterface<?, ?> problem;

		/** Constructor
		 * @param problem 	the agent's subproblem
		 */
		public MaxWidthMinDom (DCOPProblemInterface<?, ?> problem) {
			this.problem = problem;
		}

		/** @see LinearOrdering.Heuristic#getScore(java.util.List, java.lang.String) */
		public IntIntStringTuple getScore(List<String> order, String var) {

			// Compute the number of variables in the order that are neighbors of this one
			Collection<String> neighbors = this.problem.getNeighborVars(var);
			int width = 0;
			for (String neighbor : order) 
				if (neighbors.contains(neighbor)) 
					width++;

			return new IntIntStringTuple (width, - this.problem.getDomainSize(var), var);
		}
	}

	/** All relevant information about a connected component of the constraint graph */
	private class ComponentInfo {

		/** Set of variables in this component that have not yet been picked */
		private HashSet<String> openVars;

		/** The linear order on variables in this component */
		private List<String> order = new ArrayList<String> ();

		/** The agent corresponding to each variable in the order */
		private ArrayList<String> agentList = new ArrayList<String> ();

		/** The set of agents controlling a variable in this component */
		private HashSet<String> agentSet = new HashSet<String> ();

		/** The number of proposals we are still waiting for */
		private int countdown;

		/** The current candidate variable to be put in the order */
		private String candidate;

		/** The agent owning the current candidate */
		private String candAgent;

		/** The score of the current candidate */
		private IntIntStringTuple score;
	}

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of the messages sent to request proposals for the next variable to put in the order */
	static final String REQUEST_MSG_TYPE = "NextVarRequest";

	/** The type of the messages containing a proposal for the next variable to put in the order */
	static final String PROPOSAL_MSG_TYPE = "NextVarProposal";

	/** The type of the messages notifying an agent that one of its variable is next in the order */
	static final String NEXT_VAR_MSG_TYPE = "NextVarChosen";
	
	/** The type of the message notifying the agent of the chosen ordering, excluding the spaces */
	private static final String TEMP_OUTPUT_MSG_TYPE = "VarOrderNoSpace";

	/** This module's queue */
	private Queue queue;

	/** Whether the stats gatherer should display the resulting linear order */
	private boolean silent = false;
	
	/** Renderer to display DOT code */
	private String dotRendererClass;

	/** The problem */
	private DCOPProblemInterface<V, U> problem;

	/** Whether the module has already started the algorithm */
	private boolean started = false;

	/** The number of my variables for which I have not yet received the output of VariableElection */
	private int countdown;

	/** The information about each connected component in the constraint graph */
	private HashMap<Comparable<?>, ComponentInfo> compInfos;

	/** This agent's name */
	private String myID;

	/** The set of all agents in the problem */
	private Set<String> allAgents;

	/** A list of pending requests */
	private ArrayList<RequestMsg> pendingMsgs;

	/** The heuristic */
	private Heuristic<IntIntStringTuple> heuristic;

	/** For each internal variable, its list of agent neighbors */
	private Map< String, Collection<String> > agentNeighborhoods;

	/** The owner of each variable I know */
	private Map<String, String> owners;

	/** The component ID for this agent's variables and all their neighbors */
	private HashMap< String, Comparable<?> > componentIDs;

	/** For each internal variable, its list of neighbors */
	private Map<String, ? extends Collection<String>> neighborhoods;

	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public LinearOrdering (Element parameters, DCOPProblemInterface<V, U> problem)  {
		this.problem = problem;
		this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
		this.countdown = problem.getNbrVars();
		if (parameters != null) 
			this.dotRendererClass = parameters.getAttributeValue("DOTrenderer");
		if (this.dotRendererClass == null) 
			this.dotRendererClass = "";
	}

	/** Constructor
	 * @param problem 		this agent's problem
	 * @param parameters 	the parameters for LinearOrdering
	 */
	public LinearOrdering (DCOPProblemInterface<V, U> problem, Element parameters) {
		this.problem = problem;
		this.heuristic = new MaxWidthMinDom (problem);
	}

	/** Parses the problem */
	private void init() {
		this.allAgents = this.problem.getAgents();
		this.owners = this.problem.getOwners();
		this.agentNeighborhoods = this.problem.getAgentNeighborhoods();
		this.neighborhoods = this.problem.getNeighborhoods();
		this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
		this.componentIDs = new HashMap< String, Comparable<?> > ();
		this.countdown = this.problem.getNbrIntVars();
		this.myID = this.problem.getAgent();
		this.pendingMsgs = new ArrayList<RequestMsg> ();
		this.started = true;
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.allAgents = null;
		this.owners = null;
		this.agentNeighborhoods = null;
		this.neighborhoods = null;
		this.compInfos = null;
		this.componentIDs = null;
		this.myID = null;
		this.started = false;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OrderMsg.STATS_MSG_TYPE, this);
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (6);
		types.add(START_MSG_TYPE);
		types.add(LeaderElectionMaxID.OUTPUT_MSG_TYPE);
		types.add(NEXT_VAR_MSG_TYPE);
		types.add(REQUEST_MSG_TYPE);
		types.add(PROPOSAL_MSG_TYPE);
		types.add(TEMP_OUTPUT_MSG_TYPE);
		return types;
	}

	/** @see StatsReporter#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {

		String msgType = msg.getType();

		if (msgType.equals(OrderMsg.STATS_MSG_TYPE)) { // in stats gatherer mode, the output message

			OrderMsg<V, U> msgCast = (OrderMsg<V, U>) msg;

			// Store the variable order for this component
			ComponentInfo compInfo = new ComponentInfo ();
			this.compInfos.put(msgCast.getComponentID(), compInfo);
			compInfo.order = msgCast.getFlatOrder();

			// Print the order if we have received information regarding all variables
			this.countdown -= compInfo.order.size();
			if (this.countdown <= 0 && ! this.silent) 
				this.printOrder();

			return;
		}

		if (! this.started) 
			this.init();

		if (msgType.equals(LeaderElectionMaxID.OUTPUT_MSG_TYPE)) { // information about the leader for a given variable

			MessageLEoutput<?> msgCast = (MessageLEoutput<?>) msg;
			String var = msgCast.getSender();

			// Retrieve the component information
			Comparable<?> componentID = msgCast.getLeader();
			this.componentIDs.put(var, componentID);
			for (String neighbor : this.neighborhoods.get(var)) 
				this.componentIDs.put(neighbor, componentID);
			ComponentInfo compInfo = this.compInfos.get(componentID);
			if (compInfo == null) {
				compInfo = new ComponentInfo ();
				this.compInfos.put(componentID, compInfo);
				compInfo.agentSet.addAll(allAgents);
				compInfo.countdown = compInfo.agentSet.size();
			}
			if (compInfo.openVars == null) 
				compInfo.openVars = new HashSet<String> ();

			// Record the information about the variable
			if (msgCast.getFlag()) // root variable
				compInfo.order.add(var);
			else 
				compInfo.openVars.add(var);

			if (--this.countdown <= 0) { // I have received leader information about all my internal variables

				// For each component, start the algorithm if I own the root variable
				for (Iterator< Map.Entry<Comparable<?>, ComponentInfo> > iter = this.compInfos.entrySet().iterator(); iter.hasNext(); ) {
					Map.Entry<Comparable<?>, ComponentInfo> entry = iter.next();
					compInfo = entry.getValue();

					// Skip this component if I don't know the root or if I don't own it
					if (compInfo.order.isEmpty()) 	continue;
					String root = compInfo.order.get(0);
					if (! agentNeighborhoods.containsKey(root)) 	continue;

					// Directly send the output if this root is isolated
					if (this.problem.getNbrNeighbors(root) <= 0) {
						compInfo.agentList.add(this.myID);
						this.queue.sendMessageToSelf(new OrderMsg<V, U> (TEMP_OUTPUT_MSG_TYPE, entry.getKey(), compInfo.order, compInfo.agentList));
						this.queue.sendMessage(AgentInterface.STATS_MONITOR, new OrderMsg<V, U> (OrderMsg.STATS_MSG_TYPE, entry.getKey(), compInfo.order, compInfo.agentList));

					} else { // Start the algorithm
						compInfo.order.clear();

						// Only send a request to myself if none of my variables in that component have external neighbors
						boolean toMyselfOnly = true;
						for (String openVar : compInfo.openVars) {
							if (! agentNeighborhoods.get(openVar).isEmpty()) {
								toMyselfOnly = false;
								break;
							}
						}
						if (toMyselfOnly && ! agentNeighborhoods.get(root).isEmpty()) 
							toMyselfOnly = false;

						if (toMyselfOnly) {
							compInfo.agentSet.clear();
							compInfo.agentSet.add(this.myID);
							compInfo.countdown = 1;
							this.queue.sendMessageToSelf(new RequestMsg (this.myID, entry.getKey(), root));

						} else {
							// Split the destination agents based upon whether they know the root or not
							HashSet<String> know = new HashSet<String> (this.agentNeighborhoods.get(root));
							know.retainAll(compInfo.agentSet);
							this.queue.sendMessageToMulti(know, new RequestMsg (null, null, root));

							HashSet<String> dontKnow = new HashSet<String> (compInfo.agentSet);
							dontKnow.removeAll(know);
							this.queue.sendMessageToMulti(dontKnow, new RequestMsg (this.myID, entry.getKey(), root));
						}
					}
				}

				// Process pending requests
				for (RequestMsg request : this.pendingMsgs) 
					this.notifyIn(request);
				this.pendingMsgs = null;
			}
		}

		else if (msgType.equals(REQUEST_MSG_TYPE)) { // message requesting a proposal for the next variable to put in the order

			// Read the information in the message
			RequestMsg msgCast = (RequestMsg) msg;
			Comparable<?> componentID = msgCast.componentID;
			String senderAgent = msgCast.senderAgent;

			// Postpone this message if I haven't received all outputs from VariableElection yet
			if (this.countdown > 0) {
				this.pendingMsgs.add(msgCast);
				return;
			}

			// Check if I need to look up the component ID and the sender agent
			if (componentID == null) {
				componentID = this.componentIDs.get(msgCast.latestVar);
				senderAgent = this.owners.get(msgCast.latestVar);
			}

			// Retrieve the information about the component
			Comparable<?> compID = componentID;
			ComponentInfo compInfo = this.compInfos.get(compID);

			if (compInfo == null) { // I don't know this component
				this.queue.sendMessage(senderAgent, new ProposalMsg<IntIntStringTuple> (this.myID, compID, null, null));
				return;
			}

			// Update the linear order for this component
			compInfo.openVars.remove(msgCast.latestVar);
			compInfo.order.add(msgCast.latestVar);
			compInfo.agentList.add(senderAgent);

			// Reply with an empty proposal if the list of open variables is empty
			if (compInfo.openVars.isEmpty()) 
				this.queue.sendMessage(senderAgent, new ProposalMsg<IntIntStringTuple> (this.myID, compID, null, null));
			else {

				// Find the open variable with the highest score
				Iterator<String> iter = compInfo.openVars.iterator();
				String nextVar = iter.next();
				IntIntStringTuple maxScore = this.heuristic.getScore(compInfo.order, nextVar);
				while (iter.hasNext()) {
					String var = iter.next();
					IntIntStringTuple score = this.heuristic.getScore(compInfo.order, var);
					if (score.compareTo(maxScore) > 0) {
						nextVar = var;
						maxScore = score;
					}
				}

				// Check if the destination agent knows this variable
				if (this.agentNeighborhoods.get(nextVar).contains(senderAgent)) 
					this.queue.sendMessage(senderAgent, new ProposalMsg<IntIntStringTuple> (null, null, nextVar, maxScore));
				else 
					this.queue.sendMessage(senderAgent, new ProposalMsg<IntIntStringTuple> (this.myID, compID, nextVar, maxScore));
			}
		}

		else if (msgType.equals(PROPOSAL_MSG_TYPE)) { // a message proposing a variable to put next in the order

			// Read the information in the message
			ProposalMsg<IntIntStringTuple> msgCast = (ProposalMsg<IntIntStringTuple>) msg;
			Comparable<?> componentID = msgCast.componentID;
			String senderAgent = msgCast.senderAgent;

			// Check if I need to look up the component ID and the sender agent
			if (componentID == null) {
				componentID = this.componentIDs.get(msgCast.nextVar);
				senderAgent = this.owners.get(msgCast.nextVar);
			}

			ComponentInfo compInfo = this.compInfos.get(componentID);

			// Update the candidate if the new proposed score is higher
			IntIntStringTuple score = msgCast.score;
			if (score == null) // the sender agent has no more variables to propose for this component
				compInfo.agentSet.remove(senderAgent);
			else if (compInfo.score == null || score.compareTo(compInfo.score) > 0) {
				compInfo.candAgent = senderAgent;
				compInfo.candidate = msgCast.nextVar;
				compInfo.score = score;
			}

			// If I have received all proposals, make a decision and send requests for the next variable
			if (--compInfo.countdown <= 0) {

				// Check if we have exhausted all lists of open variables for this component
				if (compInfo.score == null) {
					compInfo.agentSet.addAll(compInfo.agentList);
					this.queue.sendMessageToMulti(compInfo.agentSet, new OrderMsg<V, U> (TEMP_OUTPUT_MSG_TYPE, componentID, compInfo.order, compInfo.agentList));
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new OrderMsg<V, U> (OrderMsg.STATS_MSG_TYPE, componentID, compInfo.order, compInfo.agentList));

				} else {
					this.queue.sendMessage(compInfo.candAgent, new NextVarMsg (compInfo.agentSet, compInfo.candidate));
					compInfo.countdown = compInfo.agentSet.size();
					compInfo.score = null;
				}
			}
		}

		else if (msgType.equals(NEXT_VAR_MSG_TYPE)) { // the message containing the next variable chosen for a given component

			NextVarMsg msgCast = (NextVarMsg) msg;

			// Update the set of agents involved in the corresponding component
			Comparable<?> componentID = this.componentIDs.get(msgCast.nextVar);
			ComponentInfo compInfo = this.compInfos.get(componentID);
			compInfo.agentSet = msgCast.agents;
			compInfo.countdown = compInfo.agentSet.size();

			// Split the destination agents based upon whether they know the variable or not
			HashSet<String> know = new HashSet<String> (this.agentNeighborhoods.get(msgCast.nextVar));
			know.retainAll(compInfo.agentSet);
			this.queue.sendMessageToMulti(know, new RequestMsg (null, null, msgCast.nextVar));

			HashSet<String> dontKnow = new HashSet<String> (compInfo.agentSet);
			dontKnow.removeAll(know);
			this.queue.sendMessageToMulti(dontKnow, new RequestMsg (this.myID, componentID, msgCast.nextVar));
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

	/** Prints the variable order in DOT format */
	private void printOrder() {

		StringBuilder builder = new StringBuilder();
		builder.append("digraph {\n");
		builder.append("\tnode [shape = \"circle\" style = \"filled\"];\n\n");

		// First draw the nodes in the order, linked with invisible edges
		for (ComponentInfo compInfo : this.compInfos.values()) {
			int nbrVars = compInfo.order.size();
			if (nbrVars == 1) 
				builder.append("\t" + compInfo.order.get(0) + ";\n\n");
			else {
				builder.append("\t" + compInfo.order.get(0));
				for (int i = 1; i < nbrVars; i++) 
					builder.append(" -> " + compInfo.order.get(i));
				builder.append(" [style = \"dashed\"];\n\n");
			}
		}

		// Draw the neighborhoods
		for (Map.Entry< String, ? extends Collection<String> > entry : problem.getNeighborhoods().entrySet()) {
			String var = entry.getKey();

			// Go through the collection of neighbors for the current variable
			for (String neigh : entry.getValue()) 
				if (var.compareTo(neigh) < 0) // don't draw the same constraint twice
					builder.append("\t" + var + " -> " + neigh + " [constraint = \"false\" arrowhead = \"none\"];\n");
		}

		builder.append("}\n");

		if(dotRendererClass == "") {
			System.out.println("Chosen linear order:");
			System.out.println(builder.toString());
		}
		else {
			try {
				Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("Chosen linear order", builder.toString());
			} 
			catch(Exception e) {
				System.out.println("Could not instantiate given DOT renderer class: " + this.dotRendererClass);
			}
		}
	}

}
