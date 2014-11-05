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

package frodo2.algorithms.varOrdering.dfs;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.SingleQueueAgent;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder.DFSorderOutputMessage;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID.MessageLEoutput;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageListener;
import frodo2.communication.MessageWrapper;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** A DFS Generation module that integrates a root election mechanism
 *
 * Each variable starts its own DFS Generation algorithm, with itself as the root, 
 * in which messages are labeled with the VariableElection score of the root. 
 * One particular DFS Generation instance is interrupted as soon as a variable 
 * receives a message labeled with a root score that is lower than its own Variable Election score. 
 * When a particular DFS Generation instance successfully terminates (there can be only one), 
 * it passes messages down the DFS to notify all variables. 
 * 
 * @author Thomas Leaute
 * @param <S> the type used for the root election scores
 * @note T must properly override equals(Object) and hashCode(). 
 * 
 * @todo The DFS heuristic modules are added to each FakeQueue, which results in exchanging duplicate heuristic messages
 */
public class DFSgenerationParallel < S extends Comparable <S> & Serializable > implements StatsReporter {
	
	/** A Queue that discards all messages sent */
	private class SinkQueue extends Queue {
		
		/** @see Queue#sendMessage(java.lang.Object, Message) */
		@Override
		public void sendMessage(Object to, Message msg) { }
	}
	
	/** There is one such FakeQueue for each candidate root */
	private class FakeQueue extends Queue {
		
		/** The candidate root */
		private S root;
		
		/** For each internal variable, the DFSoutput message that is being held until it becomes clear that the candidate root is indeed a root */
		private HashMap< String, MessageDFSoutput<?, ?> > dfsOutputMsgs = new HashMap< String, MessageDFSoutput<?, ?> > ();
		
		/** For each internal variable, the DFSorder message that is being held until it becomes clear that the candidate root is indeed a root */
		private HashMap<String, DFSorderOutputMessage> dfsOrderMsgs = new HashMap<String, DFSorderOutputMessage> ();

		/** For each internal variable, the DFSstats message that is being held until it becomes clear that the candidate root is indeed a root */
		private HashMap< String, MessageDFSoutput<?, ?> > dfsStatsMsgs = new HashMap< String, MessageDFSoutput<?, ?> > ();

		/** For each internal variable, the VarNbrMsg message that is being held until it becomes clear that the candidate root is indeed a root */
		private HashMap<String, VarNbrMsg> varNbrMsgs = new HashMap<String, VarNbrMsg> ();

		/** For each variable I own, its list of children */
		private HashMap< String, List<String> > children = new HashMap< String, List<String> > ();

		/** Constructor
		 * @param root 	The candidate root
		 */
		public FakeQueue (S root) {
			this.root = root;

			inPolicies = new HashMap <String, ArrayList< IncomingMsgPolicyInterface<String> > > ();
			ArrayList< IncomingMsgPolicyInterface<String> > policies = new ArrayList< IncomingMsgPolicyInterface<String> >();
			inPolicies.put(ALLMESSAGES, policies);
			
			outPolicies = new HashMap <String, ArrayList< OutgoingMsgPolicyInterface<String> > > ();
			ArrayList< OutgoingMsgPolicyInterface<String> > policiesOut = new ArrayList< OutgoingMsgPolicyInterface<String> >();
			outPolicies.put(ALLMESSAGES, policiesOut);
		}

		/** @see Queue#sendMessage(java.lang.Object, Message) */
		@Override
		public void sendMessage(Object to, Message msg) {

			// If the message is a DFSstats, record it
			if (msg.getType().equals(DFSgeneration.STATS_MSG_TYPE)) {
				MessageDFSoutput<?, ?> msgCast = (MessageDFSoutput<?, ?>) msg;
				this.dfsStatsMsgs.put(msgCast.getVar(), msgCast);
				return;
			}
			
			queue.sendMessage(to, new ParallelDFSmsg<S> (this.root, msg));
		}
		
		/** @see Queue#sendMessage(java.lang.Object, MessageWrapper) */
		@Override
		public void sendMessage (Object to, MessageWrapper msgWrap) {
			queue.sendMessage(to, new MessageWrapper (new ParallelDFSmsg<S> (this.root, msgWrap.getMessage()), 
					msgWrap.getNCCCs(), msgWrap.getTime(), msgWrap.getDestinations(), msgWrap.getMessageCounter()));
		}
		
		/** @see Queue#sendMessageToMulti(java.util.Collection, Message) */
		@Override
		public <T extends Object> void sendMessageToMulti (Collection<T> recipients, Message msg) {
			queue.sendMessageToMulti(recipients, new ParallelDFSmsg<S> (this.root, msg));
		}
		
		/** @see Queue#sendMessageToSelf(Message) */
		@Override
		public void sendMessageToSelf(Message msg) {
			
			String msgType = msg.getType();
			
			// If the message is a DFSoutput, record it
			if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)) {
				MessageDFSoutput<?, ?> msgCast = (MessageDFSoutput<?, ?>) msg;
				String var = msgCast.getVar();
				this.dfsOutputMsgs.put(var, msgCast);
				this.children.put(var, msgCast.getNeighbors().getChildren());
			}
			
			// If the message is the output of DFSgenerationWithOrder that contains a variable's order, record it
			else if (msgType.equals(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE)) {
				DFSorderOutputMessage msgCast = (DFSorderOutputMessage) msg;
				this.dfsOrderMsgs.put(msgCast.getVar(), msgCast);				
				return;
			}
			
			// If the message contains the total number of variables in the DFS
			else if (msgType.equals(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE)) {
				VarNbrMsg msgCast = (VarNbrMsg) msg;
				String var = msgCast.getDest();
				
				if (this.dfsOutputMsgs.get(var) == null) // the DFS output has already been released
					queue.sendMessageToSelf(msgCast);
			}
			
			queue.sendMessageToSelf(new ParallelDFSmsg<S> (this.root, msg));
		}

		/** Does the same as the superclass, but without the need to synchronize on a lock
		 * @see Queue#notifyInListeners(Message) 
		 */
		@Override
		public void notifyInListeners (Message msg) {
			
			String msgType = msg.getType();
			
			// Check whether this is the DFSoutput for the candidate root
			if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)) {
				MessageDFSoutput<?, ?> msgCast = (MessageDFSoutput<?, ?>) msg;
				String var = msgCast.getVar();
				if (this.root.equals(rootElectionScores.get(var))) 
					this.releaseOutput(var, true);
			} else if (msgType.equals(RELEASE_OUTPUT_MSG_TYPE)) 
				this.releaseOutput(((ReleaseDFSmsg)msg).dest, false);

			// If the message contains the total number of variables in the DFS
			else if (msgType.equals(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE)) {
				VarNbrMsg msgCast = (VarNbrMsg) msg;
				String var = msgCast.getDest();

				if (this.dfsOutputMsgs.get(var) == null) // the DFS output has already been released
					queue.sendMessageToSelf(msgCast);
				else // record the message
					this.varNbrMsgs.put(var, msgCast);
			}
			
			// First notify the policies listening for ALL messages
			ArrayList< IncomingMsgPolicyInterface<String> > policies = new ArrayList< IncomingMsgPolicyInterface<String> > (inPolicies.get(ALLMESSAGES));
			for (IncomingMsgPolicyInterface<String> module : policies) // iterate over a copy in case a listener wants to add more listeners
				module.notifyIn(msg);

			// Notify the listeners for this message type, if any
			ArrayList< IncomingMsgPolicyInterface<String> > modules = inPolicies.get(msg.getType());
			if (modules != null) {
				policies = new ArrayList< IncomingMsgPolicyInterface<String> > (modules);
				for (IncomingMsgPolicyInterface<String> module : policies) // iterate over a copy in case a listener wants to add more listeners
					module.notifyIn(msg);
			}
		}
		
		/** Releases the DFSoutput message for the input variable, and tell its children to do the same
		 * @param var 		the variable whose DFSoutput message must be released
		 * @param isRoot 	whether the variable is the root
		 */
		private void releaseOutput (String var, boolean isRoot) {
			
			// Release the DFS output messages for this variable
			queue.sendMessage(AgentInterface.STATS_MONITOR, this.dfsStatsMsgs.remove(var));
			Message msg = this.dfsOrderMsgs.remove(var);
			if (msg != null) 
				queue.sendMessageToSelf(msg);
			queue.sendMessageToSelf(this.dfsOutputMsgs.remove(var));
			msg = this.varNbrMsgs.remove(var);
			if (msg != null) 
				queue.sendMessageToSelf(msg);
			
			// Tell my children to do the same
			for (String child : this.children.get(var)) 
				this.sendMessage(owners.get(child), new ReleaseDFSmsg (child));
		}

		/** Adds the input DFS module to the queue's listeners
		 * @param dfsModule 	DFS module
		 */
		public void addIncomingMessagePolicyForReal(DFSgeneration<?, ?> dfsModule) {
			super.addIncomingMessagePolicy(dfsModule);
		}
		
		/** The module's queue is set to a SinkQueue so that all messages sent are discarded
		 * @see Queue#addIncomingMessagePolicy(IncomingMsgPolicyInterface) 
		 */
		@Override
		public void addIncomingMessagePolicy (IncomingMsgPolicyInterface <String> policy) {
			super.addIncomingMessagePolicy(policy);
			policy.setQueue(new SinkQueue ());
		}
		
	}
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of the messages containing DFS messages for a particular candidate root */
	public static final String PARALLEL_DFS_MSG_TYPE = "ParallelDFSwrapper";
	
	/** The type of the messages telling variables to release their DFS output messages */
	final static String RELEASE_OUTPUT_MSG_TYPE = "ReleaseDFSoutput";
	
	/** The problem */
	private DCOPProblemInterface<?, ?> problem;
	
	/** Whether the stats gatherer should display stats */
	private boolean silent = false;
	
	/** Whether the module has already been started */
	private boolean started = false;

	/** The queue used to exchange messages */
	private Queue queue;

	/** The heuristic used to choose the root */
	private ScoringHeuristic<S> rootElectionHeuristic;

	/** The parameters of the underlying DFS generation module */
	private Element dfsGenerationParams;

	/** Constructor for the underlying DFS generation module */
	private Constructor< ? extends DFSgeneration<?, ?> > dfsGenerationConstructor;

	/** For each of my variables, its root election score */
	private Map<String, S> rootElectionScores;

	/** For each candidate root (identified by its root election score), the corresponding FakeQueue */
	private HashMap<S, FakeQueue> queues = new HashMap<S, FakeQueue> ();

	/** For each known variable, its owner agent */
	private Map<String, String> owners;

	/** The module used for displaying the DFS in stats gatherer mode */
	private DFSgeneration<?, ?> statsGatherer;

	/** The DFS heuristic module (if the heuristic needs to exchange messages) */
	private IncomingMsgPolicyInterface<String> dfsHeuristic;
	
	/** The DFS heuristic messages received */
	private ArrayList<Message> heuristicMsgs = new ArrayList<Message> ();

	/** This module's parameters */
	private Element myParams;

	/** Constructor
	 * @param problem 	the agent's subproblem
	 * @param params 	the parameters of the module
	 * @throws NoSuchMethodException 		if the root election heuristic or the underlying DFS generation module doesn't have a constructor that takes in (ProblemInterface, Element)
	 * @throws InvocationTargetException 	if the root election heuristic constructor throws an exception
	 * @throws IllegalAccessException 		if the root election heuristic constructor is not accessible
	 * @throws InstantiationException 		if the root election heuristic is abstract
	 * @throws IllegalArgumentException 	should never happen
	 * @throws ClassNotFoundException 		if the class for the the root election heuristic or for the underlying DFS generation module is not found
	 */
	@SuppressWarnings("unchecked")
	public DFSgenerationParallel (DCOPProblemInterface<?, ?> problem, Element params) 
	throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
		this.problem = problem;
		
		// Instantiate the root election heuristic 
		Element heuristicParams = params.getChild("rootElectionHeuristic");
		if (heuristicParams == null) 
			this.rootElectionHeuristic = (ScoringHeuristic<S>) new VarNameHeuristic (problem, heuristicParams);
		else 
			this.rootElectionHeuristic = (ScoringHeuristic<S>) Class.forName(heuristicParams.getAttributeValue("className"))
				.getConstructor(DCOPProblemInterface.class, Element.class).newInstance(problem, heuristicParams);
		
		// Create the constructor for the underlying DFS generation module
		this.dfsGenerationParams = params.getChild("dfsGeneration");
		if (this.dfsGenerationParams != null) {
			String className = this.dfsGenerationParams.getAttributeValue("className");
			Class< MessageListener<String> > moduleClass = (Class< MessageListener<String> >) Class.forName(className);
			this.dfsGenerationConstructor = (Constructor< ? extends DFSgeneration<?, ?> >) moduleClass.getConstructor(DCOPProblemInterface.class, Element.class);
			
			// Override the message types if required
			Element allMsgsElmt = this.dfsGenerationParams.getChild("messages");
			if (allMsgsElmt != null) {
				for (Element msgElmt : (List<Element>) allMsgsElmt.getChildren()) {
					
					// Look up the new value for the message type
					String newType = msgElmt.getAttributeValue("value");
					String ownerClassName = msgElmt.getAttributeValue("ownerClass");
					if (ownerClassName != null) { // the attribute "value" actually refers to a field in a class
						Class<?> ownerClass = Class.forName(ownerClassName);
						try {
							Field field = ownerClass.getDeclaredField(newType);
							newType = (String) field.get(newType);
						} catch (NoSuchFieldException e) {
							System.err.println("Unable to read the value of the field " + ownerClass.getName() + "." + newType);
							e.printStackTrace();
						}
					}
					
					// Set the message type to its new value
					try {
						SingleQueueAgent.setMsgType(moduleClass, msgElmt.getAttributeValue("name"), newType);
					} catch (NoSuchFieldException e) {
						System.err.println("Unable to find the field " + moduleClass.getName() + "." + msgElmt.getAttributeValue("name"));
						e.printStackTrace();
					}
				}
			}
			
		} else {
			this.dfsGenerationParams = new Element ("dfsGeneration");
			this.dfsGenerationConstructor = (Constructor< ? extends DFSgeneration<?, ?> >) 
					DFSgeneration.class.getConstructor(DCOPProblemInterface.class, Element.class);
		}
		
		// Instantiate the DFS heuristic if it needs to exchange messages
		Element dfsHeuristicParams = this.dfsGenerationParams.getChild("dfsHeuristic");
		if (dfsHeuristicParams != null) {
			
			// Only instantiate the heuristic if it implements IncomingMsgPolicyInterface
			Class<?> dfsHeuristicClass = Class.forName(dfsHeuristicParams.getAttributeValue("className"));
			for (Class<?> moduleInterfaces : dfsHeuristicClass.getInterfaces()) {
				if (moduleInterfaces.equals(IncomingMsgPolicyInterface.class)) {
					
					Constructor< ? extends IncomingMsgPolicyInterface<String> > constructor = 
						(Constructor< ? extends IncomingMsgPolicyInterface<String> >) dfsHeuristicClass.getConstructor(DCOPProblemInterface.class, Element.class);
					this.dfsHeuristic = constructor.newInstance(problem, dfsHeuristicParams);
					
					break;
				}
			}
		}
	}
	
	/** Constructor in stats gatherer mode
	 * @param params 	the parameters of the module
	 * @param problem 	the overall problem
	 */
	public DFSgenerationParallel (Element params, DCOPProblemInterface<?, ?> problem) {
		this.myParams = params;
		this.problem = problem;
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.queues = new HashMap<S, FakeQueue> ();
		this.rootElectionScores = null;
		this.owners = null;
		if (this.statsGatherer != null) 
			this.statsGatherer.reset();
		this.heuristicMsgs.clear();

		this.started = false;
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
		
		if (this.dfsHeuristic != null) {
			queue.addIncomingMessagePolicy(this.dfsHeuristic);
		
			// Set the heuristic's queue to a special queue that sends messages to ALL DFSgeneration modules
			this.dfsHeuristic.setQueue(new FakeQueue (null));
		}
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void getStatsFromQueue(Queue queue) {

		// Also add a stats gatherer to display the DFS
		this.statsGatherer = new DFSgeneration (this.myParams, this.problem);
		this.statsGatherer.setSilent(this.silent);
		this.statsGatherer.getStatsFromQueue(queue);
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (3);
		types.add(START_MSG_TYPE);
		types.add(PARALLEL_DFS_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** @see StatsReporter#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		if (! this.started) 
			this.init(null);
		
		if (msgType.equals(PARALLEL_DFS_MSG_TYPE)) { // a message containing a DFS message corresponding to a given candidate root
			
			// Retrieve the information from the message
			ParallelDFSmsg<S> msgCast = (ParallelDFSmsg<S>) msg;
			S root = msgCast.getRoot();
			Message dfsMsg = msgCast.getMessage();
			
			// Check if the inner message has to be delivered to ALL FakeQueues
			if (root == null) {
				
				for (FakeQueue fakeQueue : this.queues.values()) 
					fakeQueue.notifyInListeners(dfsMsg);
				
				// Record the message in order to deliver it to future FakeQueues
				this.heuristicMsgs.add(dfsMsg);
				
				return;
			}
			
			// Drop the message if its destination variable has a rootElection score greater than the candidate root
			if (dfsMsg.getType().equals(DFSgeneration.CHILD_MSG_TYPE)) 
				if (this.rootElectionScores.get(((CHILDmsg) dfsMsg).getDest()).compareTo(root) > 0) 
					return;
			
			// Get the corresponding FakeQueue
			FakeQueue fakeQueue = this.queues.get(root);
			if (fakeQueue == null) // the corresponding candidate root is unknown; create a new FakeQueue for it
				fakeQueue = this.newFakeQueue(root);
			
			fakeQueue.notifyInListeners(dfsMsg);
		}
	}

	/** Initializes the module
	 * @param notRoot 	a variable we already know is not a root
	 */
	private void init(String notRoot) {
		
		// Return immediately if I own no variable
		if (this.problem.getNbrIntVars() == 0) {
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			return;
		}
		
		// Compute the root election scores for my variables
		this.rootElectionScores = this.rootElectionHeuristic.getScores();
		
		Map< String, ? extends Collection<String> > neighborhoods = this.problem.getNeighborhoods();
		
		// Create a DFS Generation module for each of my variables
		myVarLoop: for (String var : this.problem.getMyVars()) {
			
			// Skip this variable if we already know it is not a root
			if (var.equals(notRoot)) 
				continue;
			
			// Skip this variable if one of its internal neighbors has a (known) larger root election score
			S myScore = this.rootElectionScores.get(var);
			for (String neighbor : neighborhoods.get(var)) {
				if (this.problem.getOwner(neighbor).equals(this.problem.getAgent())) {
					S otherScore = this.rootElectionScores.get(neighbor);
					if (otherScore != null && otherScore.compareTo(myScore) > 0) 
						continue myVarLoop;
				}
			}
			
			// Create a FakeQueue and tell the corresponding DFSgeneration module that this variable is a root
			FakeQueue fakeQueue = this.newFakeQueue(myScore);
			fakeQueue.notifyInListeners(new MessageLEoutput<S> (var, true, myScore));
		}
		
		// Tell the DFS heuristic to start exchanging messages
		if (this.dfsHeuristic != null) 
			this.queue.sendMessageToSelf(new Message (DFSgeneration.START_MSG_TYPE));
		
		this.owners = this.problem.getOwners();
		
		this.started = true;
	}
	
	/** Creates a FakeQueue associated with the input candidate root
	 * @param root 	the candidate root
	 * @return a new FakeQueue
	 */
	private FakeQueue newFakeQueue (S root) {

		FakeQueue fakeQueue = new FakeQueue (root);
		this.queues.put(root, fakeQueue);
		
		// Create a DFS Generation module associated with this variable and tell it to start
		try {
			DFSgeneration<?, ?> dfsModule = this.dfsGenerationConstructor.newInstance(this.problem, this.dfsGenerationParams);
			fakeQueue.addIncomingMessagePolicyForReal(dfsModule);
			
		} catch (IllegalArgumentException e) { // should never happen
			e.printStackTrace();
		} catch (InstantiationException e) {
			System.err.println("DFSgeneration class is abstract");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			System.err.println("The constructor of the DFSgeneration class is not accessible");
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			System.err.println("The constructor of the DFSgeneration class has thrown an exception");
			e.printStackTrace();
		}
		fakeQueue.notifyInListeners(new Message (DFSgeneration.START_MSG_TYPE));
		
		// Deliver the heuristic messages
		for (Message message : this.heuristicMsgs) 
			fakeQueue.notifyInListeners(message);
		
		return fakeQueue;
	}

}
