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

package frodo2.algorithms.asodpop;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporterWithConvergence;
import frodo2.algorithms.asodpop.ASODPOP.AssignmentMessage;
import frodo2.algorithms.asodpop.goodsTree.innerNodeTreeBinaryDomains.InnerNodeTree;
import frodo2.algorithms.asodpop.goodsTree.innerNodeTreeBinaryDomains.LeafNode;
import frodo2.algorithms.asodpop.goodsTree.leafNodeTree.LeafNodeTree;
import frodo2.algorithms.asodpop.Good;
import frodo2.algorithms.odpop.ASKmsg;
import frodo2.algorithms.odpop.DONEmsg;
import frodo2.algorithms.odpop.UTILpropagation;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * This class implements both the UTIL propagation phase and the VALUE propagation of the ASODPOP algorithm.
 * It assumes that information on the separator variable domains is discovered
 * online. To mitigate the termination problems that can result from the that,
 * every domain that is not yet full contains a dummy element 
 * 
 * @author brammert
 * @param <Val> type used for variable values
 * @param <U> 	type used for utility values
 *
 */
public class ASODPOPBinaryDomains < Val extends Addable<Val>, U extends Addable<U> >
implements StatsReporterWithConvergence<Val> {

	// The message types

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** The type of the UTIL message*/
	public static final String UTIL_MSG = UTILpropagation.UTIL_MSG;
	
	/** The type of the UTIL message with domain information */
	public static final String UTIL_MSG_DOM = UTILpropagation.UTIL_MSG_DOM;
	
	/** The type of the UTIL message with variable IDs*/
	public static final String UTIL_MSG_VARS = UTILpropagation.UTIL_MSG_VARS;
	
	/** The type of the UTIL message with variable IDs and domain information */
	public static final String UTIL_MSG_DOM_VARS = UTILpropagation.UTIL_MSG_DOM_VARS;

	/** The type of the ASK message*/
	public static final String ASK_MSG = UTILpropagation.ASK_MSG;

	/** The type of the DONE message*/
	public static final String DONE_MSG	= UTILpropagation.DONE_MSG;

	/** The type of messages sent to the statistics monitor */
	public static final String UTIL_STATS_MSG_TYPE = "UTILstatsMessage";

	/** The type of the message containing the assignment history */
	public static final String CONV_STATS_MSG_TYPE = ASODPOP.CONV_STATS_MSG_TYPE;

	/** The type of a value message to a child */
	public static final String VALUE_MSG_TYPE_CHILD = "VALUEtoChild";
	
	/** The type of a value message to a pseudo child*/
	public static final String VALUE_MSG_TYPE_PSEUDO = "VALUEtoPseudo";
	
	/** The type of the output messages containing the optimal assignment to a variable */
	public static final String OUTPUT_MSG_TYPE = ASODPOP.OUTPUT_MSG_TYPE;
	
	/** The type of an acknowledge message*/
	public static final String ACK_MSG_TYPE = "Ack";
	
	// Variables used collect statistics

	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;

	/** Whether the listener should record the assignment history or not */
	protected final boolean convergence;

	/** The global assignment */
	protected Map< String, Val > assignment;

	/** The optimal, total utility reported to the stats gatherer */
	protected U optTotalUtil;
	
	/** For each variable the assignment history */
	protected ArrayList<CurrentAssignment<Val>>[] assignmentHistory;

	/** For each variable its assignment history */
	protected HashMap<String, ArrayList<CurrentAssignment<Val>>> assignmentHistoriesMap;
	
	// Information on the problem

	/** The agent's problem */
	protected DCOPProblemInterface<Val, U> problem;

	/** Give the variables index into the arrays*/
	protected HashMap<String, Integer> variablePointer;

	/** For each variable its domain size is stored*/
	protected int[] variableDomainSize;

	/** For each variable owned by this agent, its domain*/
	protected Val[][] domains;

	/** A map from variableIDs to the corresponding VariableInfo container*/
	private VariableInfo[] infos;

	// Information that is needed during runtime

	/** Tells one when a variable is ready to start the algorithm*/
	protected boolean[] ready;

	/** Counter for the number of variables that has terminated*/
	private int variablesReadyCounter;

	/** For each variable, a list of goods received before it was initialized*/
	private LinkedList<Message>[] goodsToProcess;
	
	/** For each variable, it records whether an ASK message has been received before initialization*/
	private boolean[] askToProcess;

	/** For each variable, it records the last VALUE message received before initialization*/
	private VALUEmsgChild<Val>[] lastValueMessage;

	/** For each variable this agent owns, its current value is stored*/
	protected Val[] currentValues;

	/** Whether the algorithm has been started */
	protected boolean started = false;

	/** To log or not to log*/
	protected static final boolean LOG = false;

	// Agent specific information

	/** The message queue*/
	protected Queue queue;

	/** The ID of this agent*/
	protected String agentID;

	/** A list of buffered writers used to log information during debugging*/
	protected HashMap<String, BufferedWriter> loggers;

	/** The method used to combine VALUE and UTIL information into a speculative variable assignment */
	protected determineAssignment<Val, U> combinationMethod;

	/** For each variable the agent that owns it. */
	protected Map<String, String> owners;

	/** The minus infinty object */
	private U infeasibleUtil;

	/** \c true when the agent has finished, and false otherwise */
	private boolean agentFinished;
	
	/** The percentage of the problem that has been stored*/
	private double cumulativeFillPercentage;
	
	/** The cumulative dummy fill percentage */
	private double cumulativeDummyFillPercentage;
	
	/** The cumulative number of dummies*/
	private long cumulativeNumberOfDummies;
	
	/** The cumulative number of speculative UTIL messages that have been sent during the run of this algorithm*/
	private int cumulativeNumberOfSpeculativeUTILmessages;
	
	/** total number of UTIl messages that have been sent */
	private int cumulativeNumberOfUTILMessages;
		
	/** Represents to what extent the trees owned by this agent are filled */
	private int fillPercentageCounter;
	
	/** The time at which all ASODPOP modules have finished*/
	private long finalTime;
	
	/** when \c true we are maximizing, when \c false we are minimizing */
	private boolean maximize;
	
	/** Number of variables owned by this agent */
	private int numberOfVariables;
	
	/** The number of DFS messages received */
	private int dfsReceived;
	
	/** The number of spaces per variable */
	private int[] usedSpaceSize;
	
	/** \c true when statistics should be collected in the tree, and \c false otherwise */
	private boolean collectStats;
	
	
	/** Constructor for the stats gatherer mode
	 * @param parameters 	the parameters of the module
	 * @param problem 		the overall problem
	 */
	public ASODPOPBinaryDomains (Element parameters, DCOPProblemInterface<Val, U> problem) {
		assignment = new HashMap< String, Val> ();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<Val>>>();
		this.convergence = false;
		this.problem = problem;
	}

	/**
	 * A constructor that takes in the description of the problem and a set of parameters
	 * @param problem	 	The agents problem
	 * @param parameters 	parameters: method to use when setting the value of a variable
	 * @throws Exception  	Exceptions thrown when setting the combination method
	 */
	public ASODPOPBinaryDomains (DCOPProblemInterface<Val, U> problem, Element parameters) throws Exception  {
		this.problem = problem;
		
		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;
		
		String collectStatsString = parameters.getAttributeValue("collectStats");
		if(collectStatsString == null)
			collectStats = false;
		else
			collectStats = Boolean.parseBoolean(collectStatsString);
		
		setCombinationMethod(parameters.getAttributeValue("combination"));
	}

	/**
	 * A constructor that takes in the description of the problem. 
	 * @param problem		The agents problem
	 * @throws Exception 	if an error occurs
	 */
	public ASODPOPBinaryDomains (DCOPProblemInterface<Val, U> problem) throws Exception  {
		this.problem = problem;
		setCombinationMethod(ASODPOPBinaryDomains.DetermineAssignmentMax.class.getName());
		this.convergence = false;
	}

	/**
	 * A constructor that takes in the description of the problem
	 * @param problem					The agents problem
	 * @param combinationMethodClass 	The combination method
	 * @throws Exception 	if an error occurs
	 */
	public ASODPOPBinaryDomains (DCOPProblemInterface<Val, U> problem, String combinationMethodClass) throws Exception  {
		this.problem = problem;
		setCombinationMethod(combinationMethodClass);
		this.convergence = false;
	}
	
	/**
	 * Initialize all the variables. Do note that the problem is not yet parsed at this moment in time!
	 */
	@SuppressWarnings("unchecked")
	private void init() {
		agentID = problem.getAgent();
		String[] myVars = problem.getMyVars().toArray(new String[0]);
		numberOfVariables = myVars.length;
		if (numberOfVariables == 0) 
			return;
		goodsToProcess = (LinkedList<Message>[])Array.newInstance((new LinkedList<Message>()).getClass(), numberOfVariables);
		askToProcess = new boolean[numberOfVariables];
		lastValueMessage = (VALUEmsgChild<Val>[])Array.newInstance((new VALUEmsgChild<Val>(null, null, false)).getClass(), numberOfVariables);
		variablesReadyCounter = 0;
		infos = (VariableInfo[])Array.newInstance((new VariableInfo(true, null, null, new String[0], new String[0], new ArrayList<String>(0), -1, null)).getClass(), numberOfVariables);
		domains = (Val[][])Array.newInstance(Array.newInstance(problem.getDomain(myVars[0])[0].getClass(), 0).getClass(), numberOfVariables);
		variableDomainSize = new int[numberOfVariables];
		currentValues = (Val[])Array.newInstance(problem.getDomain(myVars[0])[0].getClass(), numberOfVariables);
		loggers = new HashMap<String, BufferedWriter>(numberOfVariables);
		owners = problem.getOwners();
		ready = new boolean[numberOfVariables];
		variablePointer = new HashMap<String, Integer>(numberOfVariables);
		maximize = problem.maximize();
		this.infeasibleUtil = maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility();
		usedSpaceSize = new int[numberOfVariables];
		
		if(convergence)
			assignmentHistory = (ArrayList<CurrentAssignment<Val>>[])Array.newInstance((new ArrayList<CurrentAssignment<Val>>()).getClass(), numberOfVariables);

		for(int i = 0; i < numberOfVariables; i++) {
			String var = myVars[i];
			variablePointer.put(var, i);
			variableDomainSize[i] = problem.getDomainSize(var);
			goodsToProcess[i] = new LinkedList<Message>();
			ArrayList<CurrentAssignment<Val>> list = new ArrayList<CurrentAssignment<Val>>();
			Val[] dom = problem.getDomain(var);
			domains[i] = dom;
			Val value = dom[(int)(Math.random() * dom.length)];
			currentValues[i] = value;
			if(convergence) {
				list.add(new CurrentAssignment<Val> (queue.getCurrentTime(), value)); 
				assignmentHistory[i] = list;
			}

			if(LOG) {
				try{
					loggers.put(var, new BufferedWriter( new FileWriter("logs/asodpop-" + var + ".log")));
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}

		this.started  = true;
	}

	/** @see StatsReporterWithConvergence#reset() */
	public void reset() {
		goodsToProcess = null;
		askToProcess = null;
		lastValueMessage = null;
		infos = null;
		domains = null;
		variableDomainSize = null;
		loggers = null;
		owners = null;
		ready = null;
		assignmentHistory = null;
		this.started = false;
		
		// Only useful in stats gatherer mode
		assignment = new HashMap< String, Val> ();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<Val>>>();
		this.optTotalUtil = null;
	}

	/**
	 * Set the method that defines how the available information is combined into a single variable assignment
	 * @param method						The method of combination
	 * @throws ClassNotFoundException 		if the heuristic is not found
	 * @throws NoSuchMethodException 		if the heuristic does not have a constructor that takes in an ASODPOPBinaryDomains
	 * @throws InvocationTargetException 	if the heuristic constructor throws an exception
	 * @throws IllegalAccessException 		if the heuristic constructor is not accessible
	 * @throws InstantiationException 		if the heuristic is abstract
	 * @throws IllegalArgumentException 	should never happen
	 */
	@SuppressWarnings("unchecked")
	private void setCombinationMethod(String method) throws ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		if(method == null)
			method = DetermineAssignmentMax.class.getName();
		Class< determineAssignment<Val, U> > heuristicClass = (Class< determineAssignment<Val, U> >) Class.forName(method);
		Class<?> parTypes[] = new Class[1];
		parTypes[0] = this.getClass();
		Constructor< determineAssignment<Val, U> > constructor = heuristicClass.getConstructor(parTypes);
		Object[] args = new Object[1];
		args[0] = this;
		this.combinationMethod = constructor.newInstance(args);
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#getStatsFromQueue(frodo2.communication.Queue)
	 */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OUTPUT_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(CONV_STATS_MSG_TYPE, this);
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#setSilent(boolean)
	 */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}
	
	/** @return the maximum number of variables in a UTIL message (in stats gatherer mode only) */
	public Integer getMaxMsgDim () {
		//@todo implement this
		return 0;
	}

	/** @return the optimal assignments to all variables */
	public Map<String, Val> getOptAssignments () {
		return this.assignment;
	}

	/** @return the total optimal utility across all components of the constraint graph */
	public U getTotalOptUtil () {
		return this.optTotalUtil;
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
	 * @see frodo2.algorithms.StatsReporterWithConvergence#getAssignmentHistories()
	 */
	public HashMap<String, ArrayList<CurrentAssignment<Val>>> getAssignmentHistories() {
		return assignmentHistoriesMap;
	}
	
	/**
	 * @author Brammert Ottens, 7 jan 2010
	 * @return the average of all reported treeFillPercentages
	 */
	public double getAverageFillTreePercentage() {
		return this.cumulativeFillPercentage/this.fillPercentageCounter;
	}
	
	/** @return the average dummy fill percentage */
	public double getAverageDummyFillTreePercentage() {
		return this.cumulativeDummyFillPercentage/this.fillPercentageCounter;
	}
	
	/** @return the average number of dummies */
	public double getAverageNumberOfDummies() {
		return this.cumulativeNumberOfDummies/this.fillPercentageCounter;
	}

	/** @return the total number of speculative UTIL messages that have been sent */
	public int getCumulativeNumberOfSpeculativeUTILmsgs() {
		return this.cumulativeNumberOfSpeculativeUTILmessages;
	}
	
	/** @return the total number of UTIL messages that have been sent */
	public int getNumberOfUTILmessages() {
		return this.cumulativeNumberOfUTILMessages;
	}
	
	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList<String>(12);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(ASK_MSG);
		msgTypes.add(UTIL_MSG);
		msgTypes.add(UTIL_MSG_DOM);
		msgTypes.add(UTIL_MSG_VARS);
		msgTypes.add(UTIL_MSG_DOM_VARS);
		msgTypes.add(DONE_MSG);
		msgTypes.add(VALUE_MSG_TYPE_CHILD);
		msgTypes.add(VALUE_MSG_TYPE_PSEUDO);
		msgTypes.add(DFSgeneration.OUTPUT_MSG_TYPE);
		msgTypes.add(ACK_MSG_TYPE);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		return msgTypes;
	}

	/** 
	 * @see StatsReporterWithConvergence#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn (Message msg) {

		if(this.agentFinished)
			return;

		String type = msg.getType();
		
		if (type.equals(ASODPOPBinaryDomains.OUTPUT_MSG_TYPE)) { // in stats gatherer mode, the message containing information about an agent's assignments
			AssignmentMessage<Val, U> msgCast = (AssignmentMessage<Val, U>) msg;
			String variable = msgCast.getVariable();
			Val value = msgCast.getValue();
			assignment.put(variable, value);
			
			this.cumulativeFillPercentage += msgCast.getTreeFillPercentage();
			this.cumulativeDummyFillPercentage += msgCast.getDummyFillPercentage();
			this.cumulativeNumberOfDummies += msgCast.getNumberOfDummies();
			this.cumulativeNumberOfSpeculativeUTILmessages += msgCast.getNumberOfSpeculativeUTIlmessages();
			cumulativeNumberOfUTILMessages += msgCast.getNumberOfUTILmessages();
			this.fillPercentageCounter++;

			if (!silent) 
				System.out.println("var `" + variable + "' = " + value);
			
			if (this.assignment.size() == this.problem.getNbrVars()) {
				this.optTotalUtil = this.problem.getUtility(assignment).getUtility(0);
				
				if (!this.silent) 
					System.out.println("Total optimal " + (this.maximize ? "utility: " : "cost: ") + this.optTotalUtil);
			}

			long time = queue.getCurrentMessageWrapper().getTime();
			if(finalTime < time)
				finalTime = time;
			
			return;
		}

		if (type.equals(ASODPOPBinaryDomains.CONV_STATS_MSG_TYPE)) { // in stats gatherer mode, the message sent by a variable containing the assignment history
			StatsReporterWithConvergence.ConvStatMessage<Val> msgCast = (StatsReporterWithConvergence.ConvStatMessage<Val>)msg;
			assignmentHistoriesMap.put(msgCast.getVar(), msgCast.getAssignmentHistory());

			return;
		}
		
		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.agentFinished = true;
			this.reset();
			return;
		}
		
		if(!this.started)
			init();

		if(type.equals(UTIL_MSG)) {	// Receiving a new good
			UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>)msg;
			int varIndex = variablePointer.get(msgCast.getReceiver());
			if(ready[varIndex]) {
				VariableInfo variable = infos[varIndex];
				String sender = msgCast.getSender();
				String[] variables = ((InnerNodeTree<Val, U>)variable.tree).getChildSeparatorReportingOrder(variable.childrenPointer.get(sender));
				if(msgCast.isRelevant(variables.length)) {
					processUTILMessage(variable, msgCast.getGood(variables), sender, null, null, false);
				} 
			} else
				goodsToProcess[varIndex].add(msgCast);
		} 
		
		else if(type.equals(ASK_MSG)) { // receiving an ASK message
			ASKmsg msgCast = (ASKmsg)msg;
			int varIndex = variablePointer.get(msgCast.getReceiver());
			if(ready[varIndex]) {
				VariableInfo variable = infos[varIndex];
				if(LOG)
					log(variable.variableID, "Received an ASK message from " + variable.parent);
				processASKMessage(variable);
			} else
				askToProcess[varIndex] = true;

		}
		
		else if(type.equals(UTIL_MSG_VARS)) {	// Receiving a new good
			UTILvarsDomsMsg<Val, U> msgCast = (UTILvarsDomsMsg<Val, U>)msg;
			int varIndex = variablePointer.get(msgCast.getReceiver());
			String child = msgCast.getSender();
			
			if(ready[varIndex]) {
				VariableInfo variable = infos[varIndex];
				int childPointer = variable.childrenPointer.get(child);
				sendMessageToVariable(child, variable.getAckMessage(childPointer));
				Val[][] domains = msgCast.getDomains();
				String[] variables = msgCast.getVariables();
				((InnerNodeTree<Val, U>)variable.tree).setChildrenSeparator(childPointer, variables);
				HashMap<String, Val[]> domainMap = new HashMap<String, Val[]>(domains.length);
				for(int i = 0; i < variables.length ; i++)
					domainMap.put(variables[i],	domains[i]);
				processUTILMessage(variable, msgCast.getGood(), msgCast.getSender(), null, domainMap, true);
			} else {
				sendMessageToVariable(child, new ACKmsg(child));
				goodsToProcess[varIndex].add(msgCast);
			}
			
			if(LOG)
				log(msgCast.getReceiver(), "Sending a ACK message to " + child);
		} 
		
		else if(type.equals(VALUE_MSG_TYPE_CHILD)) { // receiving a VALUE message from my parent
			VALUEmsgChild<Val> msgCast = (VALUEmsgChild<Val>)msg;
			int varIndex = variablePointer.get(msgCast.getDest());
			if(ready[varIndex]) {
				VariableInfo variable = infos[varIndex];
				processVALUEmessageParent(variable, msgCast.getValues(), msgCast.isConfirmed());
			} else
				lastValueMessage[varIndex] = msgCast;
		} 
		
		else if(type.equals(VALUE_MSG_TYPE_PSEUDO)) {
			VALUEmsgPseudo<Val> msgCast = (VALUEmsgPseudo<Val>)msg;
			int varIndex = variablePointer.get(msgCast.getReceiver());
			if(ready[varIndex]) {
				VariableInfo variable = infos[varIndex];
				processVALUEmessagePseudoParent(variable, msgCast.getSender(), msgCast.getContext());
			} 
		} 
		
		else if(type.equals(ACK_MSG_TYPE)) {
			ACKmsg msgCast = (ACKmsg)msg;
			int varIndex = variablePointer.get(msgCast.getReceiver());
			VariableInfo variable = infos[varIndex];
			variable.acknowledged = true;

			LinkedList<Message> goodsToSend  = variable.goodsToSend;
			if(LOG)
				log(variable.variableID, "there are " + goodsToSend.size() + " messages to send");
			if(!goodsToSend.isEmpty()) {
				Message msgNew = goodsToSend.poll();
				type = msgNew.getType();
				while(msgNew != null && !type.equals(UTIL_MSG_DOM_VARS) && !type.equals(UTIL_MSG_VARS)) {
					if(LOG)
						log(variable.variableID, "resending a good with utility " +  msgNew.toString());
					sendMessageToVariable(variable.parent, msgNew);
					msgNew = goodsToSend.poll();
					if(msgNew != null)
						type = msgNew.getType();
				}
				if(msgNew != null) {
					variable.acknowledged = false;
					sendMessageToVariable(variable.parent, msgNew);
					if(LOG)
						log(variable.variableID, "resending a good with utility " +  msgNew.toString());
				}
			}

			if(LOG)
				log(msgCast.getReceiver(), "Received an ACK message");

		}

		else if(type.equals(DONE_MSG)) { // this child has no new information to report
			DONEmsg msgCast = (DONEmsg)msg;
			int varIndex = variablePointer.get(msgCast.getReceiver());
			VariableInfo variable = infos[varIndex];
			if(variable != null &&  variable.tree.hasMore()) {
				this.processDONE(variable, msgCast.getSender());
			}
			
			if(LOG) {
				log(variable.variableID, "Received a DONE message from " + msgCast.getSender() + "\n" + variable.toString());
			}
		}
		
		else if(type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // receiving DFS tree information for ONE variable
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>)msg;
			String var = msgCast.getVar();
			int varIndex = variablePointer.get(var);

			DFSview<Val, U> neighbours = msgCast.getNeighbors();
			
			// get the relations
			String parent = neighbours.getParent();
			List<String> children = neighbours.getChildren();
			List<String> pseudoChildren = neighbours.getAllPseudoChildren();
			List<String> pseudoParents = neighbours.getPseudoParents();
			boolean root = (parent == null);

			// set the constraints this variable is responsible for
			List<UtilitySolutionSpace<Val, U>> usedSpaces = new ArrayList<UtilitySolutionSpace<Val, U>>(neighbours.getSpaces());
			Val[] ownVariableDomain = problem.getDomain(var);
			HashMap<String, Val[]> variableDomains = new HashMap<String, Val[]>();
			variableDomains.put(var, problem.getDomain(var));
			
			for (UtilitySolutionSpace<Val, U> space : usedSpaces) {
				
				if(LOG)
					log(var, space.toString());
				String[] variables = space.getVariables();
				Val[][] domains = space.getDomains();

				for(int i = 0; i < variables.length; i++) {
					variableDomains.put(variables[i], domains[i]);
				}
			}
			
			VariableInfo variable = null;
			int numberOfChildren = children.size();
			usedSpaceSize[varIndex] = usedSpaces.size();

			if(numberOfChildren != 0) {
				if(! root)
					variable = new VariableInfo(root, var, parent, children.toArray(new String[0]), pseudoChildren.toArray(new String[0]), pseudoParents, pseudoParents.size(), new InnerNodeTree<Val, U>(var, ownVariableDomain, usedSpaces, children.size(), problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
				else
					variable = new VariableInfo(root, var, null, children.toArray(new String[0]), pseudoChildren.toArray(new String[0]), pseudoParents, pseudoParents.size(), new InnerNodeTree<Val, U>(var, ownVariableDomain, usedSpaces, children.size(), problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
			} else {
				if(! root)
					variable = new VariableInfo(root, var, parent, children.toArray(new String[0]), pseudoChildren.toArray(new String[0]), pseudoParents, pseudoParents.size(), new LeafNodeTree<Val, U, LeafNode<U>>(var, ownVariableDomain, usedSpaces, problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
				else
					variable = new VariableInfo(root, var, null, children.toArray(new String[0]), pseudoChildren.toArray(new String[0]), pseudoParents, pseudoParents.size(), new LeafNodeTree<Val, U, LeafNode<U>>(var, ownVariableDomain, usedSpaces, problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
			}

			variable.currentContextMap.put(var, currentValues[varIndex]);
			assert infos[varIndex] == null;
			infos[varIndex] =  variable;

			this.dfsReceived++;
			if(dfsReceived == numberOfVariables) {
				for(int j = 0; j < numberOfVariables; j++) {
					ready[j] = true;
					variable = this.infos[j];
					var = variable.variableID;
					if(usedSpaceSize[j] == 0 && variable.numberOfChildren == 0) {
						if(LOG)
							log(var, "Variable without constraints ... we are stopping");
						queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (var, domains[varIndex][(int)(Math.random()*domains[varIndex].length)], variable.tree.getTreeFillPercentage(), variable.tree.getDummiesFillPercentage(), variable.tree.getNumberOfDummies(), 0, 0));
						variablesReadyCounter += 1;
						if(variablesReadyCounter == infos.length) {
							queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
						}
					} else /*if(this.dfsReceived >= infos.length) */
						startProcess(var);
				}
			}
		} 

	}

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/**
	 * This method starts the optimization process, by first handling the messages that have
	 * already been received, and after that by sending ASK messages to start things up
	 * 
	 * @param varID The ID of the variable that is ready.
	 */
	@SuppressWarnings("unchecked")
	private void startProcess(String varID) {
		
		int varIndex = variablePointer.get(varID);
		if(LOG)
			log(varID, "Starting ASODPOP");
		VariableInfo var = infos[varIndex];
		if(var.parent == null && var.children.length == 0) { // This variable is a singleton variable.
			Good<Val, U> aMax = (Good<Val, U>)var.tree.getAmax();
			
			if(LOG)
				log(varID, "Singleton variable ... we are stopping");
			Val assignment = null;
			
			if(aMax == null) 
				assignment = domains[varIndex][(int)(Math.random()*domains[varIndex].length)];
			else 
				assignment = aMax.getValues()[0];
			
			if(var.tree instanceof InnerNodeTree)
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (varID, assignment, var.tree.getTreeFillPercentage(), var.tree.getDummiesFillPercentage(), var.tree.getNumberOfDummies(), ((InnerNodeTree<Val, U>)var.tree).getSpeculativeUTILcounter(), ((InnerNodeTree<Val, U>)var.tree).getUTILcounter()));
			else
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (varID, assignment, var.tree.getTreeFillPercentage(), var.tree.getDummiesFillPercentage(), var.tree.getNumberOfDummies(), 0, 0));
			if(convergence) {
				assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), assignment));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(ASODPOPBinaryDomains.CONV_STATS_MSG_TYPE, varID, assignmentHistory[variablePointer.get(varID)]));
			}
			
			variablesReadyCounter += 1;
			if(variablesReadyCounter == infos.length) {
				queue.cleanQueue();
				queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			}
		} else {
			// First tell your lower priority neighbors your current value
			for(int j = 0; j < var.numberOfChildren; j++) {
				sendVALUEChild(var, currentValues[varIndex], var.currentContextMap, false, var.children[j]);
			}

			for(int j = 0; j < var.pseudo_children.length; j++) {
				sendVALUEPseudo(varID, currentValues[varIndex], var.pseudo_children[j]);
			}

			// Then process the goods that have already been received
			LinkedList<Message> goodsReceived = goodsToProcess[varIndex];
			VariableInfo variable = infos[varIndex];
			
			while(goodsReceived.size() > 0) {
				Message msg = goodsReceived.poll();
				String type = msg.getType();
				int senderIndex = 0;
				Good<Val, U> good = null;
				HashMap<String, Val[]> domainMap = null;
				if(type.equals(UTIL_MSG) || type.equals(UTIL_MSG_DOM)) {
					UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>)msg;
					senderIndex = variable.childrenPointer.get(msgCast.getSender());
					good = msgCast.getGood(((InnerNodeTree<Val, U>)variable.tree).getChildSeparatorReportingOrder(senderIndex));
				}
				
				else { // these message should be of the type UTIL_MSG_VARS or UTIL_MSG_DOM_VARS
					UTILvarsDomsMsg<Val, U> msgCast = (UTILvarsDomsMsg<Val, U>)msg;
					String[] variables = msgCast.getVariables();
					Val[][] domains = msgCast.getDomains();
					domainMap = new HashMap<String, Val[]>(domains.length);
					for(int i = 0; i < variables.length ; i++)
						domainMap.put(variables[i],	domains[i]);
					senderIndex = variable.childrenPointer.get(msgCast.getSender());
					good = msgCast.getGood();
				}

				((InnerNodeTree<Val, U>)variable.tree).add(good, senderIndex, domainMap);
			}

			if(variable != null) {
				// if we received an ASK message process it. Otherwise simple send ASK messages to all children
				if(askToProcess[varIndex]) {
					processASKMessage(variable);
				} else {
					for(int j = 0; j < variable.numberOfChildren; j++) { // send ASK messages to ones children to get things started
						if(!variable.done[j]) {
							variable.done[j] = true;
							sendASK(variable.children[j], variable);
						}
					}
				}

				// Finally process that last VALUE message that we have received
				VALUEmsgChild<Val> vMsg = lastValueMessage[varIndex];
				if( vMsg != null) {
					processVALUEmessageParent(variable, vMsg.getValues(), vMsg.isConfirmed());
				}
			}
		}
	}
	
	/**
	 * Process a good that has been received. 
	 * @param variable	The variable information
	 * @param g			The good received
	 * @param sender	The sender of the good
	 * @param variables		The variables for which domain size information is being sent
	 * @param domains		The domains of the reported variables
	 * @param withVars \c true when the util message contained variables
	 */
	private void processUTILMessage(VariableInfo variable, Good<Val, U> g, String sender, String[] variables, HashMap<String, Val[]> domains, boolean withVars) {
		if(!variable.terminated) {
			if(LOG) {
				log(variable.variableID, "Received a UTIL message from " + sender + " " + g.toString() + "\n");
			}
			String variableID = variable.variableID;
			int varIndex = variablePointer.get(variableID);

			// add the new good
			int childPointer = variable.childrenPointer.get(sender);

			if(((InnerNodeTree<Val, U>)variable.tree).ignoreGood(g, childPointer)) {
				if(LOG)
					log(variable.variableID, "Ignoring good from " + sender + " " + g.isConfirmed() + " " + g.getUtility() + "\n");
				return;
			}
			
			if(g.isConfirmed())
				variable.done[variable.childrenPointer.get(sender)] = false;
			int nbrNewVariables = ((InnerNodeTree<Val, U>)variable.tree).add(g, childPointer, domains);
			boolean newVariable = nbrNewVariables > 0;
			variable.setNewVariable( newVariable || variable.getNewVariable() );
			
			// check for valuation sufficiency
			boolean isConfirmed = variable.tree.isValuationSufficient();
			boolean sendConfirmed = isConfirmed && variable.root;
			
			// determine the current assignment
			if(variable.root && variable.tree.isValuationSufficient())
				((InnerNodeTree<Val, U>)variable.tree).ownVariableValue(variable.currentContextMap);
			else
				variable.tree.getBestAssignmentForOwnVariable(variable.currentContextMap);
//				combinationMethod.determineVariableValue(varIndex, variableDomainSize[varIndex]);
			Val currentValue = variable.currentContextMap.get(variable.variableID);
			
			boolean sendValueMessage = sendConfirmed || withVars;

			if(!currentValue.equals(currentValues[varIndex])) {
				currentValues[varIndex] = currentValue;
				variable.currentContextMap.put(variableID, currentValue);
				sendValueMessage = true; 
				if(convergence) 
					assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));
			}
			
			if(sendValueMessage) {
				if(variable != null) {
					for(int j = 0; j < variable.numberOfChildren; j++) {
						sendVALUEChild(variable, currentValue, variable.currentContextMap, sendConfirmed, variable.children[j]);
					}
					
					for(int j = 0; j < variable.pseudo_children.length; j++) {
						sendVALUEPseudo(variableID, currentValue, variable.pseudo_children[j]);
					}
				}
			}
			
			if(variable.root) {
				if(isConfirmed) {
					variable.terminated = true;
					
					// check if we want to measure convergence
					if(convergence) {
						assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(ASODPOPBinaryDomains.CONV_STATS_MSG_TYPE, variable.variableID, assignmentHistory[variablePointer.get(variable.variableID)]));
					}
					
					// report the optimal assignment to the stats monitor
					queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (variable.variableID, currentValue, variable.tree.getTreeFillPercentage(), variable.tree.getDummiesFillPercentage(), variable.tree.getNumberOfDummies(), ((InnerNodeTree<Val, U>)variable.tree).getSpeculativeUTILcounter(), ((InnerNodeTree<Val, U>)variable.tree).getUTILcounter()));
					
					// determine whether we want to quit this agent
					variablesReadyCounter += 1;
					if(variablesReadyCounter == infos.length) {
						queue.cleanQueue();
						queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
					}
					
					if(LOG)
						log(variable.variableID, "Terminated!");
				} else if(g.isConfirmed()) { // Only send a new ASK message if the last confirmed message did not contain enough information
					if(!variable.done[childPointer]) {
						variable.done[childPointer] = true;
						sendASK(sender, variable);
					}
				}
			} else {
				if(variable.notResponded) {
					processASKMessage(variable);
				}
			}
		}
		if(LOG) {
			log(variable.variableID, variable.toString());
		}
	}

	/**
	 * Process an ASK message. If the tree has no more new information, sent a DONE message.
	 * Otherwise respond with a good.
	 * @param variable The variable information of the variable that received this ASK message
	 */
	private void processASKMessage(VariableInfo variable) {
		if(!variable.terminated) {
			Good<Val, U> g = variable.getAmax();
			if(LOG)
				log(variable.variableID, "The next good is: " + g + " " + variable.tree.hasMore() + " infeasibe: " + variable.infeasible);

			if(!variable.infeasible && (g != null || variable.tree.hasMore())) {
				if(g == null || variable.tree.notEnoughInfo()) {
					variable.notResponded = true;
					for(int i = 0; i < variable.numberOfChildren; i++) {
						if(!variable.done[i]) {
							variable.done[i] = true;
							sendASK(variable.children[i], variable);
						}
					}
				} else {
					sendGood(g, variable);
					if(variable.notResponded) {
						for(int i = 0; i < variable.numberOfChildren; i++) {
							if(!variable.done[i]) {
								variable.done[i] = true;
								sendASK(variable.children[i], variable);
							}
						}
					}
				}
			} else if(!variable.isDone) {
				variable.isDone = true;
				sendDONE(variable.parent, variable.variableID);
			}

			if(LOG) {
				log(variable.variableID, variable.toString());
			}
		}
	}

	/**
	 * Process a VALUE message
	 * @param variable		The Variable information
	 * @param values		The values reporeted in the VALUE message
	 * @param isConfirmed 	\c true when the VALUE message is confirmed, i.e. the algorithm is terminating
	 */
	private void processVALUEmessageParent(VariableInfo variable, HashMap<String, Val> values, boolean isConfirmed) {
		String variableID = variable.variableID;
		int varIndex = variablePointer.get(variableID);

		if(LOG)
			log(variableID, "Received a VALUE message child " + isConfirmed + " from " + variable.parent + " " + values +  "\n");

		// Store the new context and determine ones assignment given this context
		variable.setCurrentContext(values, isConfirmed);
		variable.tree.getBestAssignmentForOwnVariable(variable.currentContextMap);
		Val currentValue = variable.currentContextMap.get(variableID);

		if(isConfirmed) {
			variable.tree.getBestAssignmentForOwnVariable(variable.currentContextMap);
			variable.terminated = true;

			if(currentValue == null) // the problem is infeasible
				currentValue = variable.getRandomValue();
			currentValues[varIndex] = currentValue;
			
			// store this assignment, if needed
			if(convergence)
				assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));

			for(int j = 0; j < variable.numberOfChildren; j++) {
				sendVALUEChild(variable, currentValue, variable.currentContextMap, isConfirmed, variable.children[j]);
			}

			for(int j = 0; j < variable.pseudo_children.length; j++) {
				sendVALUEPseudo(variableID, currentValue, variable.pseudo_children[j]);
			}			

			if(variable.tree instanceof InnerNodeTree)
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (variable.variableID, currentValues[varIndex], variable.tree.getTreeFillPercentage(), variable.tree.getDummiesFillPercentage(), variable.tree.getNumberOfDummies(), ((InnerNodeTree<Val, U>)variable.tree).getSpeculativeUTILcounter(), ((InnerNodeTree<Val, U>)variable.tree).getUTILcounter()));
			else
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (variable.variableID, currentValues[varIndex], variable.tree.getTreeFillPercentage(), variable.tree.getDummiesFillPercentage(), variable.tree.getNumberOfDummies(), 0, 0));
			
			if(convergence)
				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(ASODPOPBinaryDomains.CONV_STATS_MSG_TYPE, variable.variableID, assignmentHistory[variablePointer.get(variable.variableID)]));

			variablesReadyCounter += 1;
			if(variablesReadyCounter == infos.length) {
				queue.cleanQueue();
				queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			}
		} else {
			boolean sendVALUEmessage = false;

			// only store the assignment when it changes
			if(currentValue != null && !currentValue.equals(currentValues[varIndex])) {
				currentValues[varIndex] = currentValue;
				sendVALUEmessage = true; 

				if(convergence) 
					assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));
			}

			if(sendVALUEmessage) {
				VariableInfo varInfo = infos[varIndex];
				if(varInfo != null) {
					for(int j = 0; j < varInfo.numberOfChildren; j++) {
						sendVALUEChild(varInfo, currentValue, varInfo.currentContextMap, isConfirmed, varInfo.children[j]);
					}

					for(int j = 0; j < variable.pseudo_children.length; j++) {
						sendVALUEPseudo(variableID, currentValue, variable.pseudo_children[j]);
					}
				}
			} 
		}

		if(LOG)
			log(variableID, variable.toString());
	}

	/**
	 * Process a VALUE message
	 * @param variable		The Variable information
	 * @param sender		The sender of the VALUE message 
	 * @param value 		The value of the sending variable
	 */
	private void processVALUEmessagePseudoParent(VariableInfo variable, String sender, Val value) {
		String variableID = variable.variableID;
		int varIndex = variablePointer.get(variableID);

		if(LOG)
			log(variableID, "Received a VALUE message pseudo " + " from " + variable.parent + "\n");

		// Store the new context and determine ones assignment given this context
		variable.updateCurrentContext(sender, value);
		variable.tree.getBestAssignmentForOwnVariable(variable.currentContextMap);
		Val currentValue = variable.currentContextMap.get(variableID);


		boolean sendVALUEmessage = false;

		// only store the assignment when it changes
		if(currentValue != null && !currentValue.equals(currentValues[varIndex])) {
			currentValues[varIndex] = currentValue;
			sendVALUEmessage = true; 

			if(convergence) 
				assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));
		}

		if(sendVALUEmessage) {
			VariableInfo varInfo = infos[varIndex];
			if(varInfo != null) {
				for(int j = 0; j < varInfo.numberOfChildren; j++) {
					sendVALUEChild(varInfo, currentValue, varInfo.currentContextMap, false, varInfo.children[j]);
				}

				for(int j = 0; j < variable.pseudo_children.length; j++) {
					sendVALUEPseudo(variableID, currentValue, variable.pseudo_children[j]);
				}
			}
		} 

		if(LOG)
			log(variableID, variable.toString());
	}

	/**
	 * Process a DONE message
	 * 
	 * @author Brammert Ottens, 1 nov 2009
	 * @param variable the variable that received the DONE message
	 * @param sender the sender of the DONE message
	 */
	@SuppressWarnings("unused")
	private void processDONE(VariableInfo variable, String sender) {
		if(!variable.terminated) {
			String variableID = variable.variableID;
			int varIndex = variablePointer.get(variableID);
			int child = variable.childrenPointer.get(sender);

			variable.done[child] = true;
			variable.infeasible = ((InnerNodeTree<Val, U>)variable.tree).setChildDone(child);

			if(variable.root) {
				if(variable.root && ((InnerNodeTree<Val, U>)variable.tree).isValuationSufficient())
					((InnerNodeTree<Val, U>)variable.tree).ownVariableValue(variable.currentContextMap);
				else
					variable.tree.getBestAssignmentForOwnVariable(variable.currentContextMap);
				//				combinationMethod.determineVariableValue(varIndex, variableDomainSize[varIndex]);

				Val currentValue = variable.currentContextMap.get(variableID);

				if(!currentValue.equals(currentValues[varIndex])) {
					currentValues[varIndex] = currentValue;
					variable.currentContextMap.put(variableID, currentValue);
					if(convergence) 
						assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));
				}

				if(variable.tree.isValuationSufficient() || variable.infeasible) {
					variable.terminated = true;

					if(currentValue == null) // problem is infeasible
						currentValue = variable.getRandomValue();

					//	check if we want to measure convergence
					if(convergence) {
						assignmentHistory[varIndex].add(new CurrentAssignment<Val>(queue.getCurrentTime(), currentValue));
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(ASODPOPBinaryDomains.CONV_STATS_MSG_TYPE, variableID, assignmentHistory[variablePointer.get(variableID)]));
					}

					for(int j = 0; j < variable.numberOfChildren; j++) {
						sendVALUEChild(variable, currentValue, variable.currentContextMap, true, variable.children[j]);
					}

					for(int j = 0; j < variable.pseudo_children.length; j++) {
						sendVALUEPseudo(variableID, currentValue, variable.pseudo_children[j]);
					}

					// report the optimal assignment to the stats monitor
					queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U> (variableID, currentValue, variable.tree.getTreeFillPercentage(), variable.tree.getDummiesFillPercentage(), variable.tree.getNumberOfDummies(), ((InnerNodeTree<Val, U>)variable.tree).getSpeculativeUTILcounter(), ((InnerNodeTree<Val, U>)variable.tree).getUTILcounter()));

					// determine whether we want to quit this agent
					variablesReadyCounter += 1;
					if(variablesReadyCounter == infos.length) {
						queue.cleanQueue();
						queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
					}

					if(LOG)
						log(variable.variableID, "Terminated!");
				} else { // Only send a new ASK message if the last confirmed message did not contain enough information
					if(!variable.done[child]) {
						variable.done[child] = true;
						sendASK(sender, variable);
					}
				}
			} else if(variable.notResponded) {
				this.processASKMessage(variable);
			}
		}
	}


	/**
	 * Sent a good. If the good has already been sent, wait until a new message can be sent.
	 * @param g			The good to be send
	 * @param variable	The variable who is sending the good
	 */
	private void sendGood(Good<Val, U> g, VariableInfo variable) {
		if(variable.lastSent == null || !g.equals(variable.lastSent)) {
			if(LOG) {
				log(variable.variableID, "Sending a GOOD to " + variable.parent + ": " + g.toString());
			}
			Message newMsg = null;
			boolean send = variable.acknowledged;
			if(variable.getNewVariable()) {
				variable.setNewVariable(false);
				variable.acknowledged = false;
				if(LOG)
					log(variable.variableID, "Sending new variables");

				newMsg = new UTILvarsDomsMsg<Val, U>(variable.variableID, variable.parent, g, variable.tree.getDomains());
			} else {
				newMsg = new UTILmsg<Val, U>(variable.variableID, variable.parent, g);
			}

			if(send) {
				if(LOG)
					log(variable.variableID, "Sending a message of type " + newMsg.getType() + " to my parent " + queue.getCurrentTime());
				sendMessageToVariable(variable.parent, newMsg);
			}
			else {
				variable.goodsToSend.addLast(newMsg);
				if(LOG)
					log(variable.variableID, "goodsToSend size = " + variable.goodsToSend.size());
			}

			variable.lastSent = g;
		} else if(LOG)
			log(variable.variableID, "Not sending good");

		variable.notResponded = !g.isConfirmed();
	}

	/**
	 * Send a VALUE message to a child
	 * @param varInfo			Contains all information ASODPOP has collected on this variable
	 * @param value				The current value of the variable
	 * @param currentContext	The current context of the variable
	 * @param isConfirmed 		\c true when this is a confirmed VALUE message
	 * @param child				The variable that is to receive this message
	 */
	private void sendVALUEChild(VariableInfo varInfo, Val value, HashMap<String, Val> currentContext, boolean isConfirmed, String child) {
		if(LOG)
			log(varInfo.variableID, "Sending a VALUE message " + isConfirmed + " to " + child);
		
		VALUEmsgChild<Val> msg = new VALUEmsgChild<Val>(child, ((InnerNodeTree<Val, U>)varInfo.tree).getChildValues(currentContext, varInfo.childrenPointer.get(child)), isConfirmed);
		sendMessageToVariable(child, msg);
	}

	/**
	 * Send a VALUE message to a pseudo child
	 * @param variableID		The variable ID
	 * @param value				The current value of the variable
	 * @param child				The variable that is to receive this message
	 */
	private void sendVALUEPseudo(String variableID, Val value, String child) {
		if(LOG)
			log(variableID, "Sending a VALUE message to pseudo_child " + child);

		VALUEmsgPseudo<Val> msg = new VALUEmsgPseudo<Val>(variableID, child, value);
		sendMessageToVariable(child, msg);
	}

	/**
	 * Sent an ASK message to a child
	 * @param child			The recipient of the ASK message
	 * @param var			The sending variable
	 */
	private void sendASK(String child, VariableInfo var) {
		if(LOG) {
			log(var.variableID, "Sending an ASK message to " + child);
		}
		sendMessageToVariable(child, var.getAskMessage(child));
	}

	/**
	 * Send a DONE message to ones parent
	 * @param parent			The recepient of the message
	 * @param varID				The sending variable
	 */
	private void sendDONE(String parent, String varID) {
		if(LOG) {
			log(varID, "DONE!!");
		}
		DONEmsg msg = new DONEmsg(varID, parent);
		sendMessageToVariable(parent, msg);
	}

	/**
	 * Send a message to a variable
	 * @param variable	The recepient of the message
	 * @param msg		The message to be send
	 */
	protected void sendMessageToVariable(String variable, Message msg) {
		queue.sendMessage(owners.get(variable), msg);
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
	
	/**
	 * A convenience class that contains the following information for a variable
	 * 
	 * - information on this variables separator
	 * - a list of assignments 
	 * @author brammert
	 *
	 */
	protected class VariableInfo {

		/** Denotes whether the variable is the root or not */
		public boolean root;

		/** Stores whether this variable already send a DONE message */
		public boolean isDone;

		/**When the optimal assignment is known, this variable is set to true.
		 * It is used to ignore messages received after termination */
		public boolean terminated;

		/** The variables ID */
		public String variableID;

		/** The parents ID */
		public String parent;

		/** The children's IDs */
		public String[] children;

		/** The pseudo children of this variable */
		public String[] pseudo_children;
		
		/** A list of pseudo parents*/
		public HashSet<String> pseudo_parents;

		/** Maps a child's ID to an integer */
		public HashMap<String, Integer> childrenPointer;

		/** The number of children this variable has */
		public int numberOfChildren;

		/** \c true when the remain part of the local problem has become infeasible*/
		public boolean infeasible;

		/** This data structure contains all the information for this variable */
		public GoodsTree<Val, U, LeafNode<U>> tree;

		/** For each child it stores whether it is done or not */
		public boolean[] done;

		/** The last good that has been sent. Should be set to null after a confirmed good has been sent. */
		public Good<Val, U> lastSent;

		/** True if the agent has not yet responded to an ASK message */
		public boolean notResponded;

		/** The currentContextMap */
		public HashMap<String, Val> currentContextMap;

		/** A list of precreated ask messages, one for each child */
		private ASKmsg[] askMessages;

		/** A list of precreated ack messages, one for each child */
		private ACKmsg[] ackMessages;

		/** \c true when this variable must report a new variable in his separator to his parent*/
		private boolean newVariable;

		/** \c true when the last utility message with variable info has been acknowledge, and \c false otherwise. The
		 * acknowledgements are there to enable the algorithm to function when the order of messages is not garuanteed. */
		private boolean acknowledged = true;

		/** list of goods that are still to be send */
		public LinkedList<Message> goodsToSend;

		/**
		 * A constructor
		 * @param root						\c true when this variable is a root
		 * @param variableID				The ID of this variable
		 * @param parent					The parent of this variable
		 * @param children					The children of this variable
		 * @param pseudo_children 			A list of pseudo children
		 * @param pseudo_parents			A list of pseudo parents
		 * @param numberOfPseudoParents 	the size of the list of pseudo children
		 * @param tree						The GoodsTree
		 */
		public VariableInfo(boolean root, String variableID, String parent, String[] children, String[] pseudo_children, List<String> pseudo_parents, int numberOfPseudoParents, GoodsTree<Val, U, LeafNode<U>> tree) {
			this.root = root;
			this.variableID = variableID;
			this.parent = parent;
			this.children = children;
			this.pseudo_children = pseudo_children;
			this.pseudo_parents = new HashSet<String>(pseudo_parents.size());
			this.pseudo_parents.addAll(pseudo_parents);
			this.tree = tree;
			this.terminated = false;
			this.isDone = false;
			this.goodsToSend = new LinkedList<Message>();
			askMessages = new ASKmsg[children.length];
			ackMessages = new ACKmsg[children.length];

			if(tree != null)
				currentContextMap = new HashMap<String, Val>(0);

			numberOfChildren = children.length;
			done = new boolean[numberOfChildren];
			notResponded = false;
			childrenPointer = new HashMap<String, Integer>();

			for(int i = 0; i < numberOfChildren; i++) {
				childrenPointer.put(children[i], i);
				askMessages[i] = new ASKmsg(children[i]);
				ackMessages[i] = new ACKmsg(children[i]);
			}

			newVariable = true;
		}

		/**
		 * Method to return the next good to send
		 * @return aMax
		 */
		public Good<Val, U> getAmax() {
			return (Good<Val, U>) tree.getAmax();
		}
		
		/**
		 * @author Brammert Ottens, 26 mei 2010
		 * @return a random value from the variables domain
		 */
		public Val getRandomValue() {
			return ((InnerNodeTree<Val, U>)tree).getRandomValue();
		}

		/**
		 * Set the current context
		 * @param context the current context
		 * @param isConfirmed \c true when the received VALUE message is confirmed
		 */
		public void setCurrentContext(HashMap<String, Val> context, boolean isConfirmed) {
			if(isConfirmed) {
				currentContextMap = context;
			} else {
				for(Entry<String, Val> e : context.entrySet()) {
					String var = e.getKey();
					if(!pseudo_parents.contains(var))
						currentContextMap.put(var, e.getValue());
				}
			}
		}

		/**
		 * Updates the value of variable in the current context
		 * @param variable	The variable whos value must be updated
		 * @param value		The new value of the variable
		 */
		public void updateCurrentContext(String variable, Val value) {
				currentContextMap.put(variable, value);
		}

		/**
		 * Setter method for newVariable
		 * @author Brammert Ottens, 21 aug 2009
		 * @param newVariable the value for newVariable
		 */
		public void setNewVariable(boolean newVariable) {
			this.newVariable = newVariable;
		}

		/**
		 * @author Brammert Ottens, 21 aug 2009
		 * @return returns the value of newVariable
		 */
		public boolean getNewVariable() {
			return newVariable;
		}

		/**
		 * @author Brammert Ottens, 2 sep 2009
		 * @param child child of the variable
		 * @return pre-created ASK message for child
		 */
		public ASKmsg getAskMessage(int child) {
			return askMessages[child];
		}

		/**
		 * @author Brammert Ottens, 2 sep 2009
		 * @param child child of the variable
		 * @return pre-created ASK message for child
		 */
		public ASKmsg getAskMessage(String child) {
			return askMessages[childrenPointer.get(child)];
		}

		/**
		 * @author Brammert Ottens, 3 sep 2009
		 * @param child child of the variable
		 * @return pre-created ACK message for child
		 */
		public ACKmsg getAckMessage(int child) {
			return ackMessages[child];
		}

		/**
		 * Checks whether the number of values in \c values is consistent with
		 * the reported separator size of the child
		 * 
		 * @author Brammert Ottens, 2 sep 2009
		 * @param values the set of values reported by the child
		 * @param child the child who reported the values
		 * @return \c true when the reported separator is consistent with the reported values
		 */
		public boolean valuesAreConsistent(Val[] values, String child) {
			return ((InnerNodeTree<Val, U>)tree).getChildSeparatorSize(childrenPointer.get(child)) == values.length;
		}

		/**
		 * Method to print out state of the variable
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			String s = tree.toString();
			s += "\n";
			s += "Context is " + currentContextMap;
			for(int i = 0; i < numberOfChildren; i++) {
				s += i + ": " + done[i] + "\n";
			}

			return s;
		}
	}

	/**
	 * In the future we might want to experiment with different ways of determining a variables assignment. This interface
	 * is to be implemented by every class that provides this function
	 * @author brammert
	 *
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static interface determineAssignment < Val extends Addable<Val>, U extends Addable<U> > {

		/**
		 * Method used to determine the assignment of a variable
		 * @param varIndex		The index of the variable
		 * @param domainSize 	The size of the variables domain
		 * @return The optimal variable assignment
		 */
		public Val determineVariableValue(int varIndex, int domainSize);

		/**
		 * Method used to set the zero value
		 * @param zero 	The zero utility
		 */
		public void setZero(U zero);
	}

	/**
	 * This class should be used when only a single hierarchy is used.
	 * @author brammert
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 *
	 */
	public static class DetermineAssignmentMax < Val extends Addable<Val>, U extends Addable<U> > 
	implements determineAssignment<Val, U> {

		/**
		 * A link to all the information available in the hierarchies
		 */
		private ASODPOPBinaryDomains<Val, U> asodpop;

		/**
		 * A constructor
		 * @param asodpop		Link to the ASODPOP class
		 */
		public DetermineAssignmentMax(ASODPOPBinaryDomains<Val, U> asodpop) {
			this.asodpop = asodpop;
		}


		/**
		 * /**
		 * This method simply looks at the tree, and given the context, determines the best assignment
		 * so far.
		 * 
		 * @param varIndex		The index of the variable
		 * @param domainSize 	The size of the variables domain 
		 * @return the index of the best value
		 * 
		 * @see frodo2.algorithms.asodpop.ASODPOPBinaryDomains.determineAssignment#determineVariableValue(int, int)
		 */
		public Val determineVariableValue(int varIndex, int domainSize) {
			ASODPOPBinaryDomains<Val, U>.VariableInfo variable = asodpop.infos[varIndex];
			 
			variable.tree.getBestAssignmentForOwnVariable(variable.currentContextMap);
			
			return variable.currentContextMap.get(variable.variableID);
		}

		/**
		 * @see frodo2.algorithms.asodpop.ASODPOPBinaryDomains.determineAssignment#setZero(frodo2.solutionSpaces.Addable)
		 */
		public void setZero(U zero) {} 

	}
	
	/**
	 * @see StatsReporterWithConvergence#getCurrentSolution()
	 */
	public Map<String, Val> getCurrentSolution() {
		HashMap<String, Val> solution = new HashMap<String, Val>();
		for(Entry<String, Integer> e : this.variablePointer.entrySet()) {
			solution.put(e.getKey(), currentValues[e.getValue()]);
		}

		return solution;
	}

}
