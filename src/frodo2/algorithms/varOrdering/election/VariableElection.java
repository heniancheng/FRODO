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

/** Algorithms to elect a variable */
package frodo2.algorithms.varOrdering.election;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** Protocol to elect a variable
 *
 * Contrary to LeaderElectionMaxID, this protocol elects a variable rather than an agent. 
 * The advantage is that it works when agents' subproblems have disconnected components. 
 * The implementation actually uses LeaderElectionMaxID, by treating each variable as an agent. 
 * @author Thomas Leaute
 * @param <S> the type used for the variables' scores
 * @note S must properly override equals(Object) and hashCode(). 
 */
public class VariableElection < S extends Comparable <S> & Serializable > extends Queue implements IncomingMsgPolicyInterface<String> {

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** The type of the message telling the agent finished */
	public static String FINISH_MSG_TYPE = AgentInterface.AGENT_FINISHED;

	/** The queue on which it should call sendMessage() */
	private Queue queue;
	
	/** The problem */
	private DCOPProblemInterface<?, ?> problem;
	
	/** The number of steps the protocol should run before it terminates */
	protected int nbrSteps;

	/** The heuristic */
	protected ScoringHeuristic<S> heuristic;
	
	/** Whether the execution of the algorithm has started */
	private boolean started = false;
	
	/** For each variable (internal or external), the list of internal variables connected to it */
	private Map < String, ArrayList< LeaderElectionMaxID<S> > > neighborhoods = 
		new HashMap < String, ArrayList< LeaderElectionMaxID<S> > > ();
	
	/** For each known variable, the name of the agent that owns it */
	private Map<String, String> owners = new HashMap<String, String> ();
	
	/** The LeaderElectionMaxID listeners corresponding to this agent's variables */
	private Collection < LeaderElectionMaxID<S> > listeners;
	
	/** Constructor
	 * @param problem 		the problem
	 * @param heuristic 	the heuristic
	 * @param nbrSteps 		number of steps the protocol should run before it terminates
	 * @warning \a nbrSteps must be an upper bound on the diameter of the largest component in the constraint graph for the algorithm to work properly. 
	 */
	public VariableElection (DCOPProblemInterface<?, ?> problem, ScoringHeuristic<S> heuristic, int nbrSteps) {
		this.problem = problem;
		this.nbrSteps = nbrSteps;
		this.heuristic = heuristic;
	}
	
	/** Constructor from XML descriptions
	 * @param problem description of the problem
	 * @param parameters description of the parameters of this protocol
	 * @throws Exception if an error occurs
	 * @warning \a nbrSteps must be an upper bound on the total number of variables for the algorithm to work properly. 
	 */
	@SuppressWarnings("unchecked")
	public VariableElection (DCOPProblemInterface<?, ?> problem, Element parameters) throws Exception {
		this.problem = problem;
		this.nbrSteps = Integer.parseInt(parameters.getAttributeValue("nbrSteps"));
		
		// Instantiate the heuristic 
		Element heuristicParams = parameters.getChild("varElectionHeuristic");
		if (heuristicParams == null) 
			this.heuristic = (ScoringHeuristic<S>) new VarNameHeuristic (problem, parameters);
		else 
			this.heuristic = (ScoringHeuristic<S>) Class.forName(heuristicParams.getAttributeValue("className"))
				.getConstructor(DCOPProblemInterface.class, Element.class).newInstance(problem, heuristicParams);
	}
	
	/** Incomplete constructor to be used by overriding classes
	 * @param problem 	the agent's problem
	 */
	protected VariableElection (DCOPProblemInterface<?, ?> problem) {
		this.problem = problem;
	}

	
	/** Parses the problem */
	private void init () {
		this.owners = problem.getOwners();
		Map< String, ? extends Collection<String> > neighborhoods = problem.getNeighborhoods();
		Map <String, S> scores = heuristic.getScores();
		listeners = new ArrayList <LeaderElectionMaxID<S>> (neighborhoods.size());
				
		// Construct the neighborhoods and listeners
		for (Map.Entry< String, ? extends Collection<String> > neighborhood : neighborhoods.entrySet()) { // iterate over my variables
			
			// Instantiate the LeaderElectionMaxID listener
			String myVar = neighborhood.getKey();
			Collection<String> neighbors = neighborhood.getValue();
			LeaderElectionMaxID<S> listener = this.newListener(myVar, scores.get(myVar), neighbors);
			listeners.add(listener);
			listener.setQueue(this);
			
			for (String otherVar : neighbors) { // iterate over the variables connected to mine
				ArrayList< LeaderElectionMaxID<S> > internalNeighbors = this.neighborhoods.get(otherVar);
				if (internalNeighbors == null) { // I didn't know any neighbor for this other variable up to now
					internalNeighbors = new ArrayList <LeaderElectionMaxID<S>> ();
					this.neighborhoods.put(otherVar, internalNeighbors);
				}
				internalNeighbors.add(listener);
			}
		}
		
		this.started = true;
	}
	
	/** Instantiates a new LeaderElectionMaxID
	 * @param var 			the variable
	 * @param score 		the score
	 * @param neighbors 	the variable's neighbors
	 * @return a new LeaderElectionMaxID
	 */
	protected LeaderElectionMaxID<S> newListener (String var, S score, Collection<String> neighbors) {
		return new LeaderElectionMaxID<S> (var, score, neighbors, nbrSteps);
	}
	
	/** Listens to messages of types LeaderElectionMaxID.START_MSG_TYPE and LeaderElectionMaxID.LE_MSG_TYPE. 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection <String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList <String> (3);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(LeaderElectionMaxID.LE_MSG_TYPE);
		msgTypes.add(FINISH_MSG_TYPE);
		return msgTypes;
	}

	/** The actual algorithm
	 * 
	 * Listens to messages sent by LeaderElectionMaxID listeners, and notifies this agent's own LeaderElectionMaxID listeners. 
	 * @param msg the message that was just received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(FINISH_MSG_TYPE)) {
			this.reset();
			return;
		}
				
		// Parse the information from the problem if it hasn't been done yet
		if (! this.started) 
			init ();
		
		if (msgType.equals(START_MSG_TYPE)) { // This is the message that initiates the protocol
			
			// Return immediately if I own no variable
			if (listeners.isEmpty()) 
				this.queue.sendMessageToSelf(new Message (FINISH_MSG_TYPE));
			
			Message start = new Message (LeaderElectionMaxID.START_MSG_TYPE);
			
			// Notify all my variables
			for (LeaderElectionMaxID<S> listener : listeners) {
				listener.notifyIn(start);
			}
		}
		
		else if (msgType.equals(LeaderElectionMaxID.LE_MSG_TYPE)) { // This message contains a value for the maximum score
			
			// Extract the message's sender variable
			MaxIDmsg<S> msg2 = (MaxIDmsg<S>) msg;
			String sender = msg2.getSender();
			
			// Notify all my variables that are neighbors of the sender variable
			ArrayList< LeaderElectionMaxID<S> > neighbors = neighborhoods.get(sender);
			int nbrNeighbors = neighbors.size();
			for (int i = 0; i < nbrNeighbors; i++) 
				neighbors.get(i).notifyIn(msg2);
		}
		
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	@SuppressWarnings("unchecked")
	public void setQueue(Queue queue) {
		this.queue = queue;
		if (this.heuristic instanceof IncomingMsgPolicyInterface) 
			queue.addIncomingMessagePolicy((IncomingMsgPolicyInterface<String>) this.heuristic);
	}

	/** Method called by the LeaderElectionMaxID listeners in order to send a message to specified variables
	 * 
	 * If multiple variables are owned by the same agent, the message is only sent once to that agent.
	 * @see Queue#sendMessageToMulti(Collection, Message)
	 */
	@Override
	public <R extends Object> void sendMessageToMulti (Collection<R> recipients, Message msg) {
		
		// Build up the list of agents corresponding to the input list of variables
		HashSet<String> agents = new HashSet<String> (recipients.size());
		for (R recipient : recipients) 
			agents.add(owners.get(recipient));
		
		queue.sendMessageToMulti(agents, msg);
	}
	
	/** Method called by the LeaderElectionMaxID listeners to send their outputs
	 * @see Queue#sendMessageToSelf(Message)
	 */
	public void sendMessageToSelf (Message msg) {
		queue.sendMessageToSelf(msg);
	}

	/** Resets all problem-dependent fields (except the problem itself) */
	private void reset () {
		this.listeners = null;
		this.neighborhoods.clear();
		this.owners = null;
		this.started = false;
	}
	
}
