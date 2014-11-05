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
package frodo2.algorithms.odpop;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTree.LeafNode;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** O-DPOP's VALUE propagation module
 * @author brammert
 * @param <Val>	The type used for domain values 
 * @param <U> 	The type used for utility values
 *
 */
public class VALUEpropagation < Val extends Addable<Val>, U extends Addable<U> > implements StatsReporter {
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** The type of the value message */
	public static final String VALUE_MSG_TYPE = "ODPOP_VALUE";
	
	/** The type of the message used to request the GoodsTree from the UTIL propagation module */
	public static final String GOODS_TREE_REQUEST_MESSAGE = "Goodstree request message";
	
	/** The type of the output messages containing the optimal assignment to a variable */
	public static final String OUTPUT_MSG_TYPE = "ODPOP_OutputMessageVALUEpropagation";
	
	// Variables used to collect statistics

	/** Whether the stats reporter should print its stats */
	private boolean silent = false;
	
	/** A repository for variable assignments collected in stats mode*/
	private HashMap<String, Val> assignments;
	
	// Variables needed during runtime
	/** The agent's problem */
	private DCOPProblemInterface<Val, U> problem;
	
	/** For each variable its index in the arrays*/
	private HashMap<String, Integer> variablePointer;
	
	/** For each variable owned by this agent, its domain*/
	protected Val[][] domains;
	
	/**For each variable the agent that owns it. */
	private Map<String, String> owners;
	
	/** Per variable a list of its children */
	private ArrayList<List<String>> children;
	
	/** For each variable whether it is a root*/
	private boolean[] root;
	
	/** For each variable its context */
	private ArrayList<HashMap<String, Val>> contextMap;
	
	/** \c true when this agent has been initialized*/
	private boolean started;
	
	/** This agent's queue*/
	private Queue queue;
	
	/** Counts the number of variables that have not yet terminated, 0 if all variables have terminated*/
	private int variablesReadyCounter;
	
//	fields used for logging, only used when debugging
	
	/** To log or not to log*/
	protected static final boolean LOG = false;
	
	/** A list of buffered writers used to log information during debugging*/
	protected HashMap<String, BufferedWriter> loggers;

	/** \c true when the agent has finished, and false otherwise */
	private boolean agentFinished;
	
	/** The percentage of the problem that has been stored*/
	private double cumulativeFillPercentage;
	
	/** The cumulative dummy fill percentage */
	private double cumulativeDummyFillPercentage;
	 
	/** The total number of dummie present in the problem */
	private long cumulativeNumberOfDummies;
	
	/** Represents to what extent the trees owned by this agent are filled */
	private int fillPercentageCounter;
	
	/** The time all modules of the type VALUE propagation have finished*/
	private long finalTime;
	
	/** The number of goods that has been sent */
	private long numberOfGoodsSent;
	
	/** The total size of the space*/
	private long spaceSize;
	
	/** The sum of maximal cuts*/
	private U maximalCutSum;

	/** For each variable, a list of VALUE messages that are waiting to be processed until the DFS output has been received */
	private HashMap< String, List< VALUEmsgWithVars<Val> > > postponed = new HashMap< String, List< VALUEmsgWithVars<Val> > > ();
	
	/**
	 * Constructor used for stats reporter
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public VALUEpropagation(Element parameters, DCOPProblemInterface<Val, U> problem) {
		assignments = new HashMap<String, Val>();
	}
	
	/**
	 * Constructor used in the AgentFactory
	 * 
	 * @param problem		The problem definition
	 * @param parameters	Parameters to the listener
	 */
	public VALUEpropagation(DCOPProblemInterface<Val, U> problem, Element parameters) {
		this.problem = problem;
	}
	
	/**
	 * @see frodo2.algorithms.StatsReporter#getStatsFromQueue(frodo2.communication.Queue)
	 */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OUTPUT_MSG_TYPE, this);
	}

	/**
	 * @return the assignments reported by the different variables
	 */
	public HashMap<String, Val> getOptAssignments() {
		return this.assignments;
	}
	
	/**
	 * Returns the time at which this module has finished, 
	 * determined by looking at the timestamp of the stat messages
	 * 
	 * @author Brammert Ottens, 22 feb 2010
	 * @return the time at which this module has finished
	 */
	public long getFinalTime() {
		return finalTime;
	}
	
	/**
	 * @author Brammert Ottens, 7 jan 2010
	 * @return the average of all reported treeFillPercentages
	 */
	public double getAverageFillTreePercentage() {
		return (this.fillPercentageCounter == 0 ? 0.0 : this.cumulativeFillPercentage/this.fillPercentageCounter);
	}
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @return the average dummy fill percentage
	 */
	public double getAverageDummyFillTreePercentage() {
		return (this.fillPercentageCounter == 0 ? 0.0 : this.cumulativeDummyFillPercentage/this.fillPercentageCounter);
	}
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @return the average number of dummies
	 */
	public double getAverageNumberOfDummies() {
		return (this.fillPercentageCounter == 0 ? 0.0 : this.cumulativeNumberOfDummies/this.fillPercentageCounter);
	}
	
	/**
	 * @author Brammert Ottens, 7 feb. 2011
	 * @return the percentage of possible goods that have been sent
	 */
	public double getPercentageOfGoodsSent() {
		if(this.spaceSize == 0)
			return 0;
		else
			return this.numberOfGoodsSent/(double)this.spaceSize;
	}
	
	/**
	 * @author Brammert Ottens, 7 feb. 2011
	 * @return the maximal value with which a utility value has been cut
	 */
	public U getMaximalCutSum() {
		return this.maximalCutSum;
	}

	/**
	 * @see frodo2.algorithms.StatsReporter#setSilent(boolean)
	 */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList<String>(5);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(VALUE_MSG_TYPE);
		msgTypes.add(UTILpropagationFullDomain.GOODS_TREE_MSG_TYPE);
		msgTypes.add(DFSgeneration.OUTPUT_MSG_TYPE);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		return msgTypes;
	}

	/**
	 * @see StatsReporter#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		if(this.agentFinished)
			return;
		
		String type = msg.getType();
		
		if(type.equals(OUTPUT_MSG_TYPE)) {
			AssignmentMessage<Val, U> msgCast = (AssignmentMessage<Val, U>)msg;
			assignments.put(msgCast.getVariable(), msgCast.getValue());
			
			this.cumulativeFillPercentage += msgCast.getTreeFillPercentage();
			this.cumulativeDummyFillPercentage += msgCast.getDummyFillPercentage();
			this.cumulativeNumberOfDummies += msgCast.getNumberOfDummies();
			this.fillPercentageCounter++;
			this.numberOfGoodsSent += msgCast.getNumberOfGoodsSent();
			this.spaceSize = msgCast.getSpaceSize();
			this.maximalCutSum = this.maximalCutSum == null ? msgCast.getMaximalCut() : maximalCutSum.add(msgCast.getMaximalCut());

			if (!silent) 
				System.out.println("var `" + msgCast.getVariable() + "' = " + msgCast.getValue());
			long time = queue.getCurrentMessageWrapper().getTime();
			if(finalTime < time)
				finalTime = time;

			return;
		}
		
		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			this.agentFinished = true;
			return;
		}
		
		if(!started)
			init();
		
		if(type.equals(UTILpropagationFullDomain.GOODS_TREE_MSG_TYPE)) {
			UTILpropagationFullDomain.GoodsTreeMessage<Val, U, LeafNode<U>> msgCast = (UTILpropagationFullDomain.GoodsTreeMessage<Val, U, LeafNode<U>>)msg;
			String varID = msgCast.getVariable();
			int varIndex = variablePointer.get(varID);
			
			findOptimalAssignmentAndSend(varID, varIndex, msgCast.getTree());
		}
		
		else if (type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) {
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>)msg;
			String var = msgCast.getVar();
			int varIndex = variablePointer.get(var);
			
			DFSview<Val, U> neighbours = msgCast.getNeighbors();
			
			if(neighbours == null)
				return;
			
			// get the relations
			String parent = neighbours.getParent();
			List<String> children = neighbours.getChildren(); 
			this.children.set(varIndex, children);
			root[varIndex] = (parent == null);

			for(String child : children) {
				owners.put(child, problem.getOwner(child));
			}
			
			// Process the pending VALUE messages, if any
			List< VALUEmsgWithVars<Val> > msgs = this.postponed.remove(var);
			if (msgs != null) 
				for (VALUEmsgWithVars<Val> valueMsg : msgs) 
					this.notifyIn(valueMsg);
		}
		
		else if(type.equals(VALUE_MSG_TYPE)) {
			VALUEmsgWithVars<Val> msgCast = (VALUEmsgWithVars<Val>) msg;
			String varID = msgCast.getDest();
			int varIndex = variablePointer.get(varID);
			
			if(LOG)
				log(varID, "Received a VALUE message from parent: " + msgCast);
			
			HashMap<String, Val> reportedValues = msgCast.getValues();
			if(reportedValues == null) { // we are dealing with an infeasible problem

				// Check whether we have received the DFS output for this variable
				if (this.children.get(varIndex) == null) { // not received yet; postpone the processing of this message
					List< VALUEmsgWithVars<Val> > msgs = this.postponed.get(varID);
					if (msgs == null) 
						this.postponed.put(varID, msgs = new ArrayList< VALUEmsgWithVars<Val> > ());
					msgs.add(msgCast);
				} else 
					this.findOptimalAssignmentAndSend(varID, varIndex, null);
			} else {
				HashMap<String, Val> values = (HashMap<String, Val>)reportedValues.clone();
				contextMap.set(varIndex, values);

				queue.sendMessageToSelf(new MessageWithPayload<String>(GOODS_TREE_REQUEST_MESSAGE, varID));
			}
			
		}
		
	}
	
	/**
	 * Initializes the agent's variables
	 */
	@SuppressWarnings("unchecked")
	private void init() {
		String[] myVars = problem.getMyVars().toArray(new String[0]);
		int numberOfVariables = myVars.length;
		if (numberOfVariables == 0) 
			return;
		variablesReadyCounter = numberOfVariables;
		domains = (Val[][])Array.newInstance(Array.newInstance(problem.getDomain(myVars[0])[0].getClass(), 0).getClass(), numberOfVariables);
		variablePointer = new HashMap<String, Integer>();
		owners = problem.getOwners();
		children = new ArrayList<List<String>>(numberOfVariables);
		root = new boolean[numberOfVariables];
		contextMap = new ArrayList<HashMap<String, Val>>(numberOfVariables);
		loggers = new HashMap<String, BufferedWriter>(numberOfVariables);
		
		for(int i = 0; i < numberOfVariables; i++) {
			String myVar = myVars[i];
			variablePointer.put(myVar, i);
			children.add(null);
			contextMap.add(new HashMap<String, Val>(0));
			domains[i] = problem.getDomain(myVar);
			
			if(LOG) {
				try{
					loggers.put(myVar, new BufferedWriter( new FileWriter("logs/odpop-" + myVar + ".log", true)));
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		started = true;
	}
	
	/** @see StatsReporter#reset() */
	public void reset() {
		this.domains = null;
		this.variablePointer = null;
		this.owners = null;
		this.children = null;
		this.root = null;
		this.contextMap = null;
		this.loggers = null;
		this.started = false;
		assignments = new HashMap<String, Val>();		
	}

	/**
	 * Given the GoodsTree and the context, this method determines the value a variable should take
	 * 
	 * @param varID			The variable ID
	 * @param varIndex		The index of the variable
	 * @param tree			The GoodsTree containing all relevant information to determine the value of the variable
	 */
	private void findOptimalAssignmentAndSend(String varID, int varIndex, GoodsTree<Val, U, LeafNode<U>> tree) {
		Val val = null;
		List<String> children = this.children.get(varIndex);

		if(tree == null) { // we are dealing with an infeasible problem
			val = domains[varIndex][(int)(Math.random()*domains[varIndex].length)];
			for(int i = 0; i < children.size(); i++) {
				String child = children.get(i);
				if(LOG)
					log(varID, "Sending a VALUE message to child " + child + " (problem infeasible)");
				VALUEmsgWithVars<Val> msg = new VALUEmsgWithVars<Val>(child, null);
				sendMessageToVariable(child, msg);
			}
			queue.sendMessage(AgentInterface.STATS_MONITOR, new VALUEpropagation.AssignmentMessage<Val, U> (varID, val, 1, 1, 1, 1, 1, problem.getZeroUtility()));
		} else {
			HashMap<String, Val> currentContext = contextMap.get(varIndex);
			tree.getBestAssignmentForOwnVariable(currentContext);
			val = currentContext.get(varID);
			//			currentContext.put(varID, val);

			for(int i = 0; i < children.size(); i++) {
				String child = children.get(i);
				if(LOG)
					log(varID, "Sending a VALUE message to child " + child + "\n chosen value: " + val);
				VALUEmsgWithVars<Val> msg = new VALUEmsgWithVars<Val>(child, tree.getChildValues(currentContext, i));
				sendMessageToVariable(child, msg);
			}
			queue.sendMessage(AgentInterface.STATS_MONITOR, new VALUEpropagation.AssignmentMessage<Val, U> (varID, val, tree.getTreeFillPercentage(), tree.getDummiesFillPercentage(), tree.getNumberOfDummies(), tree.getNumberOfGoodsSent(), tree.getSizeOfSpace(), tree.getMaximalCut()));
		}
		

		if(--variablesReadyCounter == 0) {
			queue.cleanQueue();
			queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
		}
	}

	/**
	 * Sends a message to a variable
	 * @param receiver	The recipient of the message
	 * @param msg		The message to be sent
	 */
	protected void sendMessageToVariable(String receiver, Message msg) {
		queue.sendMessage(owners.get(receiver), msg);
	}

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
	
	/**
	 * Log function used to print the variables state during debugging
	 * @param variableID	The ID of the variable that is logging
	 * @param message		The message that must be logged
	 */
	protected void log(String variableID, String message) {
		assert LOG;
		try {
			loggers.get(variableID).write(message + "\n");
			loggers.get(variableID).flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** A message holding an assignment to a variable
	 * 	
	 * @param <Val> type used for variable values
	 * @param <U>   type used for utility values
	 */
	public static class AssignmentMessage < Val extends Addable<Val>, U extends Addable<U> >
	extends MessageWith2Payloads <String, Val> {
	
		/** Stores what percentage of the tree is filled */
		private double treeFillPercentage;
		
		/** The dummy fill percentage */
		private double dummyFillPercentage;
		
		/** The total number of dummies */
		private long numberOfDummies;
		
		/** The number of goods that has been sent */
		private long numberOfGoodsSent;
		
		/** The total size of the space*/
		private long spaceSize;
		
		/** The maximal cut*/
		private U maximalCut;
		
		/** Empty constructor used for externalization */
		public AssignmentMessage () {
			super.type = OUTPUT_MSG_TYPE;
		}

		/** @see MessageWith2Payloads#writeExternal(java.io.ObjectOutput) */
		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeDouble(this.treeFillPercentage);
			out.writeDouble(this.dummyFillPercentage);
			out.writeLong(this.numberOfDummies);
			out.writeLong(this.numberOfGoodsSent);
			out.writeLong(this.spaceSize);
			out.writeObject(this.maximalCut);
		}

		/** @see MessageWith2Payloads#readExternal(java.io.ObjectInput) */
		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			this.treeFillPercentage = in.readDouble();
			this.dummyFillPercentage = in.readDouble();
			this.numberOfDummies = in.readLong();
			this.numberOfGoodsSent = in.readLong();
			this.spaceSize = in.readLong();
			this.maximalCut = (U) in.readObject();
		}

		/** Constructor 
		 * @param var 		the variable
		 * @param val 		the value assigned to the variable \a var
		 * @param treeFillPercentage the percentage of nodes in the tree
		 */
		public AssignmentMessage (String var, Val val, double treeFillPercentage) {
			super (OUTPUT_MSG_TYPE, var, val);
			this.treeFillPercentage = treeFillPercentage;
		}
		
		/**
		 * @author Brammert Ottens, 20 jan. 2011
		 * @return the total number of possible assignments
		 */
		public long getSpaceSize() {
			return spaceSize;
		}

		/**
		 * @author Brammert Ottens, 20 jan. 2011
		 * @return the number of goods that have been submitted
		 */
		public long getNumberOfGoodsSent() {
			return this.numberOfGoodsSent;
		}

		/** Constructor 
		 * @param var 					the variable
		 * @param val 					the value assigned to the variable \a var
		 * @param treeFillPercentage 	the percentage of nodes in the tree
		 * @param dummyFillPercentage 	the percentage of dummy nodes in the tree
		 * @param numberOfDummies 		the absolute number of dummy nodes in the tree
		 * @param numberOfGoodsSent 	the number of goods that have been submitted
		 * @param spaceSize 			the total number of possible assignments in the space
		 * @param maximalCut 			the maximal value with which a utility value has been cut
		 */
		public AssignmentMessage (String var, Val val, double treeFillPercentage, double dummyFillPercentage, long numberOfDummies, long numberOfGoodsSent, long spaceSize, U maximalCut) {
			this (var, val, treeFillPercentage, dummyFillPercentage, numberOfDummies);
			this.numberOfGoodsSent = numberOfGoodsSent;
			this.spaceSize = spaceSize;
			this.maximalCut = maximalCut;
			assert maximalCut != null;
		}
		
		/** Constructor 
		 * @param var 		the variable
		 * @param val 		the value assigned to the variable \a var
		 * @param treeFillPercentage the percentage of nodes in the tree
		 * @param dummyFillPercentage the percentage of dummy nodes in the tree
		 * @param numberOfDummies the absolute number of dummy nodes in the tree
		 */
		public AssignmentMessage (String var, Val val, double treeFillPercentage, double dummyFillPercentage, long numberOfDummies) {
			super (OUTPUT_MSG_TYPE, var, val);
			this.treeFillPercentage = treeFillPercentage;
			this.dummyFillPercentage = dummyFillPercentage;
			this.numberOfDummies = numberOfDummies;
		}
	
		/** @return the variable */
		public String getVariable () {
			return this.getPayload1();
		}
	
		/** @return the value */
		public Val getValue () {
			return this.getPayload2();
		}
		
		/**
		 * @author Brammert Ottens, 7 jan 2010
		 * @return treeFillPercentage
		 */
		public double getTreeFillPercentage() {
			return this.treeFillPercentage;
		}
		
		/**
		 * @author Brammert Ottens, 25 feb 2010
		 * @return the dummy fill percentage
		 */
		public double getDummyFillPercentage() {
			return this.dummyFillPercentage; 
		}
		
		/**
		 * @author Brammert Ottens, 25 feb 2010
		 * @return the total number of dummies in the tree
		 */
		public long getNumberOfDummies() {
			return this.numberOfDummies;
		}
		
		/**
		 * @author Brammert Ottens, 7 feb. 2011
		 * @return the maximal value with which a utility value has been cut
		 */
		public U getMaximalCut() {
			return maximalCut;
		}
	}
	

}
