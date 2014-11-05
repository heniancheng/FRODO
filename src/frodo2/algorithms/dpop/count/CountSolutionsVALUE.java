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

package frodo2.algorithms.dpop.count;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** VALUE propagation protocol
 * @author Thomas Leaute, Brammert Ottens
 * @param <Val> type used for variable values
 * @param <U> 	type used for utility values
 * @todo Improve the implementation by reasoning on groups of variables to be projected together. 
 * @todo This should be inheriting VALUEpropagation. 
 */
public class CountSolutionsVALUE < Val extends Addable<Val>, U extends Addable<U> > 
implements StatsReporter {

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of the messages containing information about the DFS */
	public static String DFS_MSG_TYPE = DFSgeneration.OUTPUT_MSG_TYPE;
	
	/** The type of the messages containing optimal conditional assignments */
	public static String UTIL_MSG_TYPE = CountSolutionsUTIL.OUTPUT_MSG_TYPE;
	
	/** The type of the messages containing information about separators */
	public static String SEPARATOR_MSG_TYPE = CountSolutionsUTIL.SEPARATOR_MSG_TYPE;

	/** The type of the VALUE messages */
	public static final String VALUE_MSG_TYPE = "VALUEmsg";
	
	/** The type of the output messages containing the optimal assignment to a variable */
	public static final String OUTPUT_MSG_TYPE = "OutputMessageVALUEpropagation";

	/** The type of the message containing the number of possible solutions seen from a leaf's perspective*/
	public static final String SOL_SIZE_MSG_TYPE = "Solution size";
	
	
	/** The queue on which it should call sendMessage() */
	protected Queue queue;
	
	/** The problem */
	private DCOPProblemInterface<Val, U> problem;
	
	/** Whether the execution of the algorithm has started */
	protected boolean started = false;
	
	/** For each variable, the VALUE message received containing its separator's optimal assignments */
	private HashMap< String, VALUEmsg<Val> > valueMessages = new HashMap< String, VALUEmsg<Val> > ();
	
	/** For each variable, its separator */
	protected HashMap<String, String[]> separators = new HashMap<String, String[]> ();
	
	/** For each variable, the list of its children */
	protected HashMap< String, List<String> > allChildren = new HashMap< String, List<String> > ();
	
	/** Stores, for each variable, whether it is a leaf or not s*/
	protected HashMap<String, Boolean> isLeaf = new HashMap<String, Boolean> ();
	
	/** For each variable, its optimal assignment conditioned on the assignments to its separator */
	protected HashMap< String, BasicUtilitySolutionSpace< Val, U > > condAssignments = 
		new HashMap< String, BasicUtilitySolutionSpace< Val, U > > ();
	
	/** For each variable, its optimal value */
	private HashMap<String, ArrayList<Val[]>> solution = new HashMap<String, ArrayList<Val[]>> ();
	
	/** The variables reported to each variable*/
	private HashMap<String, String[]> reportedVariables = new HashMap<String, String[]>();
	
	/** The values reported to each variable*/
	private HashMap<String, ArrayList<Val[]>> reportedValues = new HashMap<String, ArrayList<Val[]>>();
	
	/** The out variables calculated for each variable*/
	private HashMap<String, String[]> outVariables = new HashMap<String, String[]> ();
	
	/** For each known variable, the name of the agent that owns it */
	protected Map<String, String> owners = new HashMap<String, String> ();
	
	/** The number of variables owned by this agents that still have not sent VALUE messages to all their children */
	protected int remainingVars;
		
	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;

	/** The total number of solutions to the problem*/
	protected int numberOfSolutions = 1;
	
	/**
	 * @author brammert
	 * @param <Val> type used for variable values
	 */
	public static class SolutionSizeMessage< Val extends Addable<Val> > extends MessageWith3Payloads <String, String[], ArrayList<Val[]>> {

		/** Empty constructor used for externalization */
		public SolutionSizeMessage () { }
		
		/**
		 * Constructor
		 * @param var 				the variable that reports the solutions
		 * @param variablesOut 		the variables for which values are reported
		 * @param valuesOut 		the reported values
		 */
		public SolutionSizeMessage (String var, String[] variablesOut, ArrayList<Val[]> valuesOut ) {
			super (SOL_SIZE_MSG_TYPE, var, variablesOut, valuesOut);
		}
		
		/** @return the sender */
		public String getSender () {
			return this.getPayload1();
		}
		
		/** @return the variables*/
		public String[] getVariables () {
			return this.getPayload2();
		}
		
		/** @return the partial solutions*/
		public ArrayList<Val[]> getValues () {
			return this.getPayload3();
		}
		
	}

	/** Manual constructor that does not use XML elements
	 * @param problem 	the problem
	 */
	public CountSolutionsVALUE (DCOPProblemInterface<Val, U> problem) {
		this.problem = problem;
	}
	
	/** Constructor from XML elements
	 * @param problem 		description of the problem
	 * @param parameters 	not used because VALUEpropagation cannot be parameterized
	 */
	public CountSolutionsVALUE (DCOPProblemInterface<Val, U> problem, Element parameters) {
		this.problem = problem;
	}
	
	/** Parses the problem */
	protected void init () {
		if(problem != null) {
			this.owners = problem.getOwners();
			this.remainingVars = problem.getNbrIntVars();
		}
		this.started = true;
	}
	
	/** @see StatsReporter#reset() */
	public void reset () {
		this.owners = null;
		this.remainingVars = 0;
		this.started = false;
		
		// Only useful in stats gatherer mode
		reportedVariables = new HashMap<String, String[]>();
		reportedValues = new HashMap<String, ArrayList<Val[]>>();
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public CountSolutionsVALUE (Element parameters, DCOPProblemInterface<Val, U> problem) { }
	
	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection <String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (6);
		types.add(START_MSG_TYPE);
		types.add(UTIL_MSG_TYPE);
		types.add(SEPARATOR_MSG_TYPE);
		types.add(VALUE_MSG_TYPE);
		types.add(DFS_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** The algorithm
	 * @see StatsReporter#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if(type.equals(SOL_SIZE_MSG_TYPE)) { // in stats gatherer mode
			SolutionSizeMessage<Val> msgCast = (SolutionSizeMessage<Val>)msg;
			String var = msgCast.getSender();
			reportedVariables.put(var, msgCast.getVariables());
			reportedValues.put(var, msgCast.getValues());
			
		}
		
		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this hasn't been done yet
		if (! this.started) 
			init();
		
		if (type.equals(UTIL_MSG_TYPE)) { // the message contains the conditional optimal value assignments to a variable
			
			// Extract the information from the message
			CountSolutionsUTIL.SolutionMessage<Val, U> msgCast = (CountSolutionsUTIL.SolutionMessage<Val, U>) msg;
			String var = msgCast.getVariable();
			BasicUtilitySolutionSpace< Val, U > optSpace = msgCast.getSpace();
			String[] separator = optSpace.getVariables();
			
			// Check if this variable is a root
			if (separator == null || separator.length == 1) {
				
				// Simulate the receipt of an empty VALUE message for this root variable
				ArrayList<Val[]> values = new ArrayList<Val[]>(1);
				values.add((Val[]) Array.newInstance(this.problem.getDomain(var).getClass().getComponentType(), 0));
				valueMessages.put(var, new VALUEmsg<Val> (var, new String[0], values));
			}
			
			// Check whether the VALUE message for this variable has been received
			VALUEmsg<Val> valueMsg = valueMessages.remove(var);
			if (valueMsg != null) {
				
				// Compute the optimal value for this variable, and send VALUE messages to its children (if possible)
				computeOptValAndSendVALUEmsgs(var, optSpace, valueMsg);
			}
			
			else { // the VALUE message for this variable has not been received yet
				
				// Store the conditional optimal assignments until we receive the VALUE message
				condAssignments.put(var, optSpace);
			}
		}

		else if (type.equals(SEPARATOR_MSG_TYPE)) { // the message contains the separator for a given child variable
			
			// Extract the information from the message
			CountSolutionsUTIL.SeparatorMessage msgCast = (CountSolutionsUTIL.SeparatorMessage) msg;
			String parent = msgCast.getParent();
			
			// Check whether we have already computed the optimal assignment to this variable
			ArrayList<Val[]> opt = solution.get(parent);
			if (opt != null) {
				String child = msgCast.getChild();
				
				// Compute and send the VALUE message
				sendVALUEmessage(child, outVariables.get(parent), opt);
				
				// Remove the child from the list of children
				List<String> children = allChildren.get(parent);
				children.remove(child);
				
				// Check if the agent can terminate
				if (children.isEmpty() && --this.remainingVars <= 0) 
					this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			}
			
			else { // we haven't be able to compute the optimal assignment to the parent yet
				
				// Store the separator until we have enough information to compute the corresponding VALUE message
				separators.put(msgCast.getChild(), msgCast.getSeparator());
			}			
		}
		
		else if (type.equals(VALUE_MSG_TYPE)) { // the VALUE message
			
			// Extract the information from the message
			VALUEmsg<Val> msgCast = (VALUEmsg<Val>) msg;
			String var = msgCast.getDest();
			
			// Check whether we have received the conditional optimal assignments for this variable
			BasicUtilitySolutionSpace< Val, U > optAssignments = condAssignments.remove(var);
			if (optAssignments != null) {
				
				// Compute the optimal value for this variable, and send VALUE messages to its children (if possible)
				computeOptValAndSendVALUEmsgs(var, optAssignments, msgCast);
			}
			
			else { // we haven't received the conditional optimal assignments for this variable yet
				
				// Store the VALUE message until we can make use of it
				valueMessages.put(var, msgCast);
			}
		}
		
		else if (type.equals(DFS_MSG_TYPE)) { // the message contains information about the children of one of my variables
			
			// Extract and record the information from the message
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>) msg;
			String var = msgCast.getVar();
			DFSview<Val, U> relationships = msgCast.getNeighbors();
			allChildren.put(var, new ArrayList<String> (relationships.getChildren()));
			isLeaf.put(var, relationships.getChildren().size() == 0);
		}
		
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		ArrayList <String> msgTypes = new ArrayList <String> (2);
		msgTypes.add(OUTPUT_MSG_TYPE);
		msgTypes.add(SOL_SIZE_MSG_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent  = silent;
	}
	
	/** Instantiates a VALUE message and sends it
	 * @param child 		destination variable of the message
	 * @param variablesOut 	the variables
	 * @param valuesOut 	assignments to the variables
	 */
	@SuppressWarnings("unchecked")
	private void sendVALUEmessage(String child, String[] variablesOut, ArrayList<Val[]> valuesOut) {
		// Send the corresponding new VALUE message
		String owner = this.owners.get(child);
		queue.sendMessage(owner, new VALUEmsg<Val>(child, variablesOut, (ArrayList<Val[]>)valuesOut.clone()));
	}

	/** Compute the optimal assignment to a variable, and sends VALUE messages to its children accordingly
	 * @param var the variable to be optimized
	 * @param optSpace the optimal utility as a function of the variable
	 * @param valueMsg the VALUE message received for variable \a var
	 */
	@SuppressWarnings("unchecked")
	private void computeOptValAndSendVALUEmsgs(String var, BasicUtilitySolutionSpace< Val, U > optSpace, 
			VALUEmsg<Val> valueMsg) {
		
		String[] variablesIn = valueMsg.getVariables();
		ArrayList<Val[]> valuesIn = valueMsg.getValues();

		int numberOfVariables = variablesIn.length;
		
		String[] variablesOut = new String[numberOfVariables + 1];
		System.arraycopy(variablesIn, 0, variablesOut, 0, numberOfVariables);
		variablesOut[numberOfVariables] = var; 
		
		ArrayList<Val[]> valuesOut = new ArrayList<Val[]>();
		
		// determine how to reorder the variables
		
		String[] ownVariables = optSpace.getVariables();
		String[] separator = new String[ownVariables.length - 1];
		int index = 0;
		for(int i = 0; i < ownVariables.length; i++) {
			if(!ownVariables[i].equals(var)) {
				separator[index] = ownVariables[i];
				index++;
			}
		}
		int[] varIndex = new int[numberOfVariables];
		for(int i = 0; i < numberOfVariables; i++) {
			varIndex[i] = -1;
			for(int j = 0; j < separator.length; j++) {
				if(separator[j].equals(variablesIn[i])) {
					varIndex[i] = j;
					break;
				}
			}
		}
		
		for(int i = 0; i < valuesIn.size(); i++) {
			Val[] values = valuesIn.get(i);
			assert numberOfVariables == values.length;
			
			Val[][] doms = (Val[][]) Array.newInstance(values.getClass(), ownVariables.length);
			int ownVarIndex = optSpace.getIndex(var);
			doms[ownVarIndex] = optSpace.getDomain(var); 
			for(int j = 0; j < values.length; j++) {
				int varI = optSpace.getIndex(variablesIn[j]);
				if(varI != -1) {
					Val val = values[j];
					Val[] ass = (Val[]) Array.newInstance(val.getClass(), 1);
					ass[0] = val;
					doms[varI] = ass;
				}
			}
			
			// find all optimal assignments to the variable
			BasicUtilitySolutionSpace.Iterator<Val, U> it = optSpace.iterator(ownVariables, doms);
			ArrayList<Val> sols = new ArrayList<Val>();
			final boolean maximize = this.problem.maximize();
			U opt = (maximize ? this.problem.getMinInfUtility() : this.problem.getPlusInfUtility());
			while(it.hasNext()) {
				U util = it.nextUtility();
				int comp = util.compareTo(opt);
				if(comp == 0) {
					sols.add(it.getCurrentSolution()[ownVarIndex]);
				} else if(maximize ? comp > 0 : comp < 0) {
					opt = util;
					sols.clear();
					sols.add(it.getCurrentSolution()[ownVarIndex]);
				}
			}
			
			
			// create the new partial optimal solutions
			for(Val value : sols) {
				Val[] values2 = (Val[]) Array.newInstance(value.getClass(), numberOfVariables + 1);
				System.arraycopy(values, 0, values2, 0, numberOfVariables);
				values2[numberOfVariables] = value;
				for(Val[] val : valuesOut) {
					boolean equal = true;
					for(int j = 0; j < val.length; j++) {
						if(!val[j].equals(values2[j]))
							equal = false;
							
					}
					assert !equal;
				}
				for(int j = 0; j < values.length; j++)
					assert values[j].equals(values2[j]);
				valuesOut.add(values2);
			}
		}
		
		
		solution.put(var, valuesOut);
		outVariables.put(var, variablesOut);
		
		// Go through the list of children of this variable 
		// For each child, check if we already know its separator 
		List<String> children = allChildren.get(var);
		for (Iterator<String> iterator = children.iterator(); iterator.hasNext(); ) {
			String child = iterator.next();
			if (separator != null) {

				// Send VALUE message to this child
				sendVALUEmessage(child, variablesOut, valuesOut);

				// Remove the child from the list of children
				iterator.remove();
			}
		}
		
		if(isLeaf.get(var)) {
			this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionSizeMessage<Val>(var, variablesOut, valuesOut));
		}
		
		// Check if the agent can terminate
		if (children.isEmpty() && --this.remainingVars <= 0) 
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
	}
	
	/** @return the reported variables*/
	public HashMap<String, String[]> getReportedVariables() {
		return reportedVariables;
	}
	
	/** @return the reported values*/
	public HashMap<String, ArrayList<Val[]>> getReportedValues() {
		return reportedValues;
	}

}
