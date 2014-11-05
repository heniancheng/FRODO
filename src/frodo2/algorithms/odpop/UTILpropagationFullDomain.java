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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
import frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNodeTree;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** The UTIL propagation phase for O-DPOP
 * @author brammert
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @param <L> class used for the leaf node
 * 
 * @todo make it work for minimisation problems as well!
 *
 */
public class UTILpropagationFullDomain < Val extends Addable<Val>, U extends Addable<U>, L extends LeafNode<U> > implements StatsReporter {

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of the UTIL message*/
	public static final String UTIL_MSG = "ODPOP_UTIL";

	/** The type of a UTIL message with variables */
	public static final String UTIL_MSG_VARS = "UTIL_VARS";

	/** The type of the UTIL message with domain information */
	public static final String UTIL_MSG_DOM = "UTIL_DOM";

	/** The type of the UTIL message with domain information */
	public static final String UTIL_MSG_DOM_VARS = "UTIL_DOM_VARS";

	/** The type of the ASK message*/
	public static final String ASK_MSG = "ASK";

	/** The type of the DONE message*/
	public static final String DONE_MSG	= "Done message";

	/** The type of the messages containing optimal utility values sent by roots */
	public static final String OPT_UTIL_MSG_TYPE = "ODPOP_OptUtilMessage";

	/** The type of the message used to start the value propagation */
	protected static final String VALUE_PROP_START_MSG_TYPE = "Start VALUE propagation";

	/** The type of the message used to send the GoodsTree to the VALUE propagation module */
	protected static final String GOODS_TREE_MSG_TYPE = "Goods tree message";

	/** The type of messages sent to the statistics monitor */
	protected static final String UTIL_STATS_MSG_TYPE = "ODPOP_UTILstatsMessage";

	// Variables used to collect statistics
	
	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;

	/** The optimal, total utility reported to the stats gatherer */
	protected U optTotalUtil;

	/** The queue through which all communication flows */
	protected Queue queue;

	/** The agent's problem */
	protected DCOPProblemInterface<Val, U> problem;

	/** Gives the variables' indexes in the arrays */
	protected HashMap<String, Integer> variablePointer;

	/** The VariableInfo for each variable */
	protected VariableInfo[] infos;

	// Information that is needed during runtime

	/** Tells one when a variable is ready to start the algorithm*/
	protected boolean[] ready;

	/**For each variable the agent that owns it. */
	protected Map<String, String> owners;

	/** Whether the algorithm has been started */
	protected boolean started = false;

	/** For each variable it stores whether an ASK message has been received */
	protected boolean[] receivedASK;

	// fields used for logging, only used when debugging

	/** To log or not to log*/
	protected static final boolean LOG = false;

	/** A list of buffered writers used to log information during debugging*/
	protected HashMap<String, BufferedWriter> loggers;

	/** The minus infinty object */
	protected U infeasibleUtil;

	/** \c true when the agent has finished, and false otherwise */
	protected boolean agentFinished;

	/** In stats gatherer mode, the maximum number of variables in a UTIL message */
	protected Integer maxMsgDim = 0;

	/** The time at which all UTIL propagation modules are finished */
	protected long finalTime;

	/** \c true when solving a maximization problem, and false otherwise */
	protected boolean maximize;
	
	/** \c true when statistics should be collected in the tree, and \c false otherwise */
	protected boolean collectStats;

	/**
	 * Constructor for the statsreporter
	 * 
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public UTILpropagationFullDomain (Element parameters, DCOPProblemInterface<Val, U> problem) {}

	/**
	 * Constructor
	 * 
	 * @param problem		The problem description
	 * @param parameters	The parameters
	 */
	public UTILpropagationFullDomain (DCOPProblemInterface<Val, U> problem, Element parameters) {
		this.problem = problem;
		
		String collectStatsString = parameters.getAttributeValue("collectStats");
		if(collectStatsString == null)
			collectStats = false;
		else
			collectStats = Boolean.parseBoolean(collectStatsString);
		
	}

	/**
	 * Alternative constructor not using XML
	 * 
	 * @param problem		The problem description
	 */
	public UTILpropagationFullDomain (DCOPProblemInterface<Val, U> problem) {
		this.problem = problem;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OPT_UTIL_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(UTIL_STATS_MSG_TYPE, this);
	}

	/**
	 * @return the optimal utility 
	 */
	public U getOptUtil() {
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
	
	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;		
	}

	/** @return the maximum number of variables in a UTIL message (in stats gatherer mode only) */
	public Integer getMaxMsgDim () {
		return this.maxMsgDim;
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList<String>(10);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(ASK_MSG);
		msgTypes.add(UTIL_MSG);
		msgTypes.add(UTIL_MSG_DOM);
		msgTypes.add(UTIL_MSG_VARS);
		msgTypes.add(UTIL_MSG_DOM_VARS);
		msgTypes.add(DONE_MSG);
		msgTypes.add(DFSgeneration.OUTPUT_MSG_TYPE);
		msgTypes.add(VALUEpropagation.GOODS_TREE_REQUEST_MESSAGE);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		return msgTypes;
	}

	/** The algorithm
	 * @see StatsReporter#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		String type = msg.getType();

		if(this.agentFinished)
			return;

		// put the stats reporter stuff here
		if(type.equals(OPT_UTIL_MSG_TYPE)) {
			OptUtilMessage<U> msgCast = (OptUtilMessage<U>)msg;
			if (!silent) 
				System.out.println("Optimal utility for component rooted at `" + msgCast.getVariable() + "\': " + msgCast.getUtility());
			if (this.optTotalUtil == null) {
				this.optTotalUtil = msgCast.getUtility();
			} else 
				this.optTotalUtil = this.optTotalUtil.add(msgCast.getUtility());

			Long time = queue.getCurrentMessageWrapper().getTime();
			if(finalTime < time)
				finalTime = time;

			return;
		}

		else if (type.equals(UTIL_STATS_MSG_TYPE)) { // we are in stats gatherer mode 

			this.maxMsgDim = Math.max(this.maxMsgDim, ((StatsMessage) msg).getMsgDim());

			return;
		}

		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			this.agentFinished = true;
			return;
		}

		if(!started)
			init();

		if (type.equals(ASK_MSG)) {
			ASKmsg msgCast = (ASKmsg)msg;
			int index = variablePointer.get(msgCast.getReceiver());
			VariableInfo variable = infos[index];
			if(variable == null) {
				receivedASK[index] = true;
			} else {
				processASK(variable);
			}
		}

		else if (type.equals(UTIL_MSG)) {
			UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>)msg;
			VariableInfo varInfo = infos[variablePointer.get(msgCast.getReceiver())];
			String sender = msgCast.getSender();
			processGOOD(varInfo, msgCast.getGood(varInfo.tree.getChildSeparatorReportingOrder(varInfo.childrenPointer.get(sender))), sender, null);
		}

		else if (type.equals(UTIL_MSG_VARS)) {
			UTILvarsDomsMsg<Val, U> msgCast = (UTILvarsDomsMsg<Val, U>)msg;
			VariableInfo varInfo = infos[variablePointer.get(msgCast.getReceiver())];
			String sender = msgCast.getSender();
			Val[][] domains = msgCast.getDomains();
			String[] variables = msgCast.getVariables();
			varInfo.tree.setChildrenSeparator(varInfo.childrenPointer.get(sender), variables);
			HashMap<String, Val[]> domainMap = new HashMap<String, Val[]>(domains.length);
			for(int i = 0; i < variables.length ; i++)
				domainMap.put(variables[i],	domains[i]);
			processGOOD(varInfo, msgCast.getGood(), sender, domainMap);
		}

		else if (type.equals(DONE_MSG)) {
			DONEmsg msgCast = (DONEmsg)msg;
			processDONE(infos[variablePointer.get(msgCast.getReceiver())], msgCast.getSender());
		}

		else if (type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) {
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>)msg;
			String var = msgCast.getVar();
			int varIndex = variablePointer.get(var);

			ready[varIndex] = true;

			DFSview<Val, U> neighbours = msgCast.getNeighbors();
			
			if (neighbours == null) // DFS reset message
				return;

			// get the relations
			String parent = neighbours.getParent();
			List<String> children = neighbours.getChildren();

			for(String child : children) {
				owners.put(child, problem.getOwner(child));
			}

			if (parent != null) 
				owners.put(parent, problem.getOwner(parent));

			// set the constraints this variable is responsible for
			List<UtilitySolutionSpace<Val, U>> usedSpaces = new ArrayList<UtilitySolutionSpace<Val, U>>(neighbours.getSpaces());
			Val[] ownVariableDomain = problem.getDomain(var);

			for (UtilitySolutionSpace<Val, U> space : usedSpaces) {
				if(LOG)
					log(var, space.toString());
				assert space.getDomain(var) != null;
			}

			VariableInfo variable = null;
			int numberOfChildren = children.size();
			boolean hasParent = (parent != null);
			int usedSpaceSize = usedSpaces.size();

			if(numberOfChildren != 0) {
				if(hasParent)
					variable = newVariableInfoInstanceInnerNode(var, children, parent, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility());
				else
					variable = newVariableInfoInstanceInnerNode(var, children, null, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility());
			} else {
				if(hasParent)
					variable = newVariableInfoInstanceLeafNode(var, children, parent, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility());
				else
					variable = newVariableInfoInstanceLeafNode(var, children, null, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility());
			}

			assert infos[varIndex] == null;
			infos[varIndex] =  variable;

			if(usedSpaceSize == 0 && numberOfChildren == 0) {
				OptUtilMessage<U> output = new OptUtilMessage<U>(this.infeasibleUtil.getZero(), variable.variableID);
				queue.sendMessage(AgentInterface.STATS_MONITOR, output);
				queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, Node<U>>(null, variable.variableID));
			} else
				startProcess(variable, varIndex);
		}

		else if (type.equals(VALUEpropagation.GOODS_TREE_REQUEST_MESSAGE)) {
			MessageWithPayload<String> msgCast = (MessageWithPayload<String>)msg;
			VariableInfo variable = infos[variablePointer.get(msgCast.getPayload())];
			queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, L>(variable.tree, variable.variableID));

		}
	}
	
	/**
	 * Method to be used when \c var is an innernode
	 * 
	 * @author Brammert Ottens, 9 feb. 2011
	 * @param var					the variable name
	 * @param children				the DFS children of the variable
	 * @param parent				the DFS parent of the variable
	 * @param ownVariableDomain		the domain of the variable
	 * @param usedSpaces			the spaces owned by the variable
	 * @param numberOfChildren		the number of DFS children of the variable
	 * @param zeroUtility			the zero utility
	 * @return	A new instance of the VariableInfo class
	 */
	public VariableInfo newVariableInfoInstanceInnerNode(String var, List<String> children, String parent, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> usedSpaces, int numberOfChildren, U zeroUtility) {
		return new VariableInfo(var, parent, children.toArray(new String[0]), new InnerNodeTree<Val, U, L>(var, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility(), leafNodeInstance(), infeasibleUtil, maximize, collectStats));
	}
	
	/**
	 * Method to be used when \c var is an leafnode
	 * 
	 * @author Brammert Ottens, 9 feb. 2011
	 * @param var					the variable name
	 * @param children				the DFS children of the variable
	 * @param parent				the DFS parent of the variable
	 * @param ownVariableDomain		the domain of the variable
	 * @param usedSpaces			the spaces owned by the variable
	 * @param numberOfChildren		the number of DFS children of the variable
	 * @param zeroUtility			the zero utility
	 * @return	A new instance of the VariableInfo class
	 */
	public VariableInfo newVariableInfoInstanceLeafNode(String var, List<String> children, String parent, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> usedSpaces, int numberOfChildren, U zeroUtility) {
		return new VariableInfo(var, parent, children.toArray(new String[0]), new LeafNodeTree<Val, U, L>(var, ownVariableDomain, usedSpaces, problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
	}
	
	/** @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;		
	}
	
	/**
	 * @author Brammert Ottens, 10 feb. 2011
	 * @return a new instance of a leafnode
	 */
	@SuppressWarnings("unchecked")
	protected L leafNodeInstance() {
		return (L)new LeafNode<U>();
	}


	/**
	 * This method starts the algorithm for the specified variable, i.e. if the variable is the root an ASK message
	 * is sent. If not an ASK message is sent if an ASK message has been received
	 * 
	 * @param variable 	The variable info
	 * @param varIndex	The index of the variable
	 */
	public void startProcess(VariableInfo variable, int varIndex) {

		// if the variable is the root, it starts by sending an ASK message to all its children
		if(variable.root) {
			if(variable.children.length == 0) { // the variable is a singleton
				Good<Val, U> aMax = variable.tree.getAmax();

				if(aMax == null) {
					if(LOG)
						log(variable.variableID, this.infeasibleUtil.toString());
					OptUtilMessage<U> output = new OptUtilMessage<U>(this.infeasibleUtil, variable.variableID);
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
					queue.sendMessage(AgentInterface.STATS_MONITOR, output);
					queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, Node<U>>(null, variable.variableID));
				} else {
					if(LOG)
						log(variable.variableID, aMax.toString());
					variable.tree.removeAMax();
					OptUtilMessage<U> output = new OptUtilMessage<U>(aMax.getUtility(), variable.variableID);
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
					queue.sendMessage(AgentInterface.STATS_MONITOR, output);
					queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, L>(variable.tree, variable.variableID));
				}
			} else {
				for(int i = 0; i < variable.children.length; i++) {
					String child = variable.children[i];
					variable.done[i] = true;
					sendMessageToVariable(child, variable.getAskMessage(i), variable.variableID); /// @todo Reusing messages is no longer useful
				}
			}
		} else if(receivedASK[varIndex]) { // if we already received an ASK message, process it
			processASK(variable);
		}
	}

	/**
	 * Method used to initialize the agents fields
	 */
	@SuppressWarnings("unchecked")
	protected void init() {
		int numberOfVariables = problem.getNbrIntVars();
		variablePointer = new HashMap<String, Integer>();
		infos = (VariableInfo[])Array.newInstance(VariableInfo.class, numberOfVariables);
		ready = new boolean[numberOfVariables];
		owners = problem.getOwners();
		receivedASK = new boolean[numberOfVariables];
		loggers = new HashMap<String, BufferedWriter>(numberOfVariables);
		started = true;
		maximize = problem.maximize();
		this.infeasibleUtil = maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility();

		Set<String> myVariables = problem.getMyVars();
		int index = 0;
		for(String varID : myVariables) {
			variablePointer.put(varID, index);
			index++;

			if(LOG) {
				try{
					loggers.put(varID, new BufferedWriter( new FileWriter("logs/odpop-" + varID + ".log")));
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.variablePointer = null;
		this.infos = null;
		this.ready = null;
		this.owners = null;
		this.receivedASK = null;
		this.loggers = null;
		this.started = false;
		this.optTotalUtil = null;
	}

	/**
	 * Method used to process the GOOD messages received by this agent
	 * 
	 * @param variable 	The variable information
	 * @param g			The good received
	 * @param sender	The sender of the good
	 * @param domains	the domains of the children variables
	 */
	protected void processGOOD(VariableInfo variable, Good<Val, U> g, String sender, HashMap<String, Val[]> domains) {
		if(LOG)
			log(variable.variableID, "Received a GOOD from " + sender +": " + g );

		int childIndex = variable.childrenPointer.get(sender);

		boolean newVariable = variable.tree.add(g, childIndex, domains) || variable.getNewVariable();

		if(LOG)
			log(variable.variableID, variable.tree.toString());


		variable.done[childIndex] = false;
		Good<Val, U> aMax = variable.tree.getAmax();
		if(aMax != null) {
			if(variable.root) {
				variable.tree.removeAMax();
				if(!variable.terminated) {
					variable.terminated = true;
					utilPropagationFinished(variable.variableID, variable.tree, aMax.getUtility(), variable.tree.getFinalDomainSize().length);
				}
			} else if (!variable.responded) {
				variable.tree.removeAMax();
				if(newVariable) {
					variable.setNewVariable(false);
//					if(variable.tree.stillToSend() && variable.tree.hasFullInfo())
//						sendMessageToVariable(variable.parent, new UTILvarsMsgWithDom<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getFinalDomainSize()), variable.variableID);
//					else
						sendMessageToVariable(variable.parent, new UTILvarsDomsMsg<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getDomains()), variable.variableID);
				} else {
//					if(variable.tree.stillToSend() && variable.tree.hasFullInfo())
//						sendMessageToVariable(variable.parent, new UTILdomMsg<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getFinalDomainSize()), variable.variableID);
//					else
						sendMessageToVariable(variable.parent, new UTILmsg<Val, U>(variable.variableID, variable.parent, aMax), variable.variableID);
				}
				variable.responded = true;
			}
		} else if (!variable.responded) {
			variable.done[childIndex] = true;
			sendMessageToVariable(sender, variable.getAskMessage(sender), variable.variableID); /// @todo Reusing messages is no longer useful
		}
	}

	/**
	 * Method to process the ASK messages received by this agent
	 * @param variable	The variable information
	 */
	protected void processASK(VariableInfo variable) {
		if(LOG)
			log(variable.variableID, "Received an ASK message from " + variable.parent);

		Good<Val, U> aMax = variable.tree.getAmax();
		if(aMax != null) {
			variable.tree.removeAMax();
			if(variable.root) {
				queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(aMax.getUtility(), variable.variableID));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
				// start the VALUE propagation
			} else {
				if(variable.getNewVariable()) {
					variable.setNewVariable(false);
						sendMessageToVariable(variable.parent, new UTILvarsDomsMsg<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getDomains()), variable.variableID);
				} else {
						sendMessageToVariable(variable.parent, new UTILmsg<Val, U>(variable.variableID, variable.parent, aMax), variable.variableID);
				}
				variable.responded = true;
			}
		} else if(variable.tree.hasMore() && !variable.infeasible) {
			for(int i = 0; i < variable.children.length; i++) {
				if(LOG)
					log(variable.variableID, "" + variable.done[i]);
				if(!variable.done[i]) {
					variable.done[i] = true;
					String child = variable.children[i];
					sendMessageToVariable(child, variable.getAskMessage(i), variable.variableID); /// @todo Reusing messages is no longer useful
				}
			}
			variable.responded = false;
		} else {
			sendMessageToVariable(variable.parent, new DONEmsg(variable.variableID, variable.parent), variable.variableID);
			variable.responded = true;
		}

		if(LOG)
			log(variable.variableID, variable.toString());
	}

	/**
	 * Method sets the entry for the corresponding agent of the done array in variable to true
	 * @param variable	The variables to whom this message has been sent
	 * @param sender	The sender of the message
	 */
	protected void processDONE(VariableInfo variable, String sender) {
		if(LOG)
			log(variable.variableID, "Received a DONE message from " + sender);
		int pointer = variable.childrenPointer.get(sender);
		variable.done[pointer] = true;
		variable.infeasible = variable.tree.setChildDone(pointer);
		if(LOG)
			log(variable.variableID, variable.toString());

		if(variable.root) {
			Good<Val, U> aMax = variable.tree.getAmax();
			if(aMax != null) {
				variable.tree.removeAMax();
				if(!variable.terminated) {
					variable.terminated = true;
					utilPropagationFinished(variable.variableID, variable.tree, aMax.getUtility(), variable.tree.getFinalDomainSize().length);
				}
			} else if(variable.infeasible && !variable.terminated) {
				variable.terminated = true;
				utilPropagationFinished(variable.variableID, null, this.infeasibleUtil, variable.tree.getFinalDomainSize().length);
			}
		} else if(!variable.responded) {
			this.processASK(variable);
		}
	}

	/**
	 * Send a message to a variable
	 * @param receiver	The recipient of the message
	 * @param msg		The message to be send
	 * @param variableID THe ID of the recipient of the message
	 */
	protected void sendMessageToVariable(String receiver, Message msg, String variableID) {
		if(LOG)
			log(variableID, "Sending a message of type " + msg.getType() + " to " + receiver);
		queue.sendMessage(owners.get(receiver), msg);
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
	 * Method called when util propagation phase for a certain variable has finished
	 * 
	 * @author Brammert Ottens, 8 feb. 2011
	 * @param variableID 	the ID of the variable that is finished
	 * @param tree 			the goods tree
	 * @param utility 		the optimal utility
	 * @param inducedWidth  the induced with of the DFS tree used
	 */
	protected void utilPropagationFinished(String variableID, GoodsTree<Val, U, L> tree, U utility, int inducedWidth) {
		queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(utility, variableID));
		queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(inducedWidth));
		// start the VALUE propagation
		queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, L>(tree, variableID));
	}
	
	/**
	 * Convenience class that contains information for a specific variable
	 * @author brammert
	 */
	protected class VariableInfo {

		/** The ID of the variable */
		protected String variableID;

		/** \c true if this variable is the root node of the DFS tree */
		protected boolean root;

		/** The goods tree, containing the goods received from the children*/
		protected GoodsTree<Val, U, L> tree;

		/** A list of the children */
		protected String[] children;

		/** Maps a child's ID to an integer */
		protected HashMap<String, Integer> childrenPointer;

		/** The parent of this variable */
		protected String parent;

		/** \c false if an ASK message needs answering, \c true otherwise */
		protected boolean responded;

		/** \c true when the remain part of the local problem has become infeasible*/
		protected boolean infeasible;

		/** Records, for each child, whether it is done or not */
		protected boolean[] done;

		/** If the root, \c true when the optimal utility has been found and \c false otherwise */
		protected boolean terminated;

		/** \c true when this variable must report a new variable in his separator to his parent*/
		protected boolean newVariable;

		/** A list of precreated ask messages, one for each child */
		protected ASKmsg[] askMessages;

		/**
		 * Constructor
		 * 
		 * @param variableID	The ID of the variable
		 * @param parent		The parent of the variable
		 * @param children		The children of the variable
		 * @param tree			The GoodsTree containing utility information
		 */
		public VariableInfo(String variableID, String parent, String[] children, GoodsTree<Val, U, L> tree) {
			if(LOG)
				log(variableID, "Initialising");
			this.variableID = variableID;
			this.parent = parent;
			if(parent == null)
				this.root = true;
			this.children = children;
			this.tree = tree;
			childrenPointer = new HashMap<String, Integer>();
			done = new boolean[children.length];
			askMessages = new ASKmsg[children.length];

			for(int i = 0; i < children.length; i++) {
				childrenPointer.put(children[i], i);
				done[i] = false;
				askMessages[i] = new ASKmsg(children[i]);
			}

			newVariable = true;
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

		/** @see java.lang.Object#toString() */
		@Override
		public String toString() {
			return "infeasible = " + this.infeasible + "\n" + tree.toString();
		}
	}

	/** Message sent by roots containing the optimal utility value of their DFS tree 
	 * @author brammert
	 *
	 * @param <U> the type used for utility values
	 */
	public static class OptUtilMessage < U extends Addable<U> > extends MessageWith2Payloads <U, String> {

		/** Empty constructor used for externalization */
		public OptUtilMessage () { }

		/** Constructor
		 * @param utility 	The optimal utility
		 * @param rootVar 	the name of the root variable reporting its optimal utility value
		 */
		public OptUtilMessage(U utility, String rootVar) {
			super(OPT_UTIL_MSG_TYPE, utility, rootVar);
		}

		/** @return the optimal utility value */
		public U getUtility () {
			return this.getPayload1();
		}

		/** @return the name of the root variable reporting its optimal utility value */
		public String getVariable() {
			return this.getPayload2();
		}
	}

	/**
	 * Message used to give the pointer to the goods tree to the VALUE propagation module
	 * 
	 * @author brammert
	 *
	 * @param <Val> type used for variable values
	 * @param <U> type used for utility values
	 * @param <L> type used for the leaf nodes
	 */
	public static class GoodsTreeMessage <Val extends Addable<Val>, U extends Addable<U>, L extends Node<U>>
	extends MessageWith2Payloads <GoodsTree<Val, U, L>, String>{

		/** Empty constructor used for externalization */
		public GoodsTreeMessage () { }

		/**
		 * Constructor
		 * 
		 * @param tree		The tree to be sent
		 * @param variable	The variable that is sending the tree
		 */
		public GoodsTreeMessage(GoodsTree<Val, U, L> tree, String variable) {
			super(GOODS_TREE_MSG_TYPE, tree, variable);
		}

		/**
		 * @return	the tree
		 */
		public GoodsTree<Val, U, L> getTree() {
			return this.getPayload1();
		}

		/**
		 * @return the variable
		 */
		public String getVariable() {
			return this.getPayload2();
		}
	}

	/** Message containing statistics */
	public static class StatsMessage extends MessageWithPayload<Integer> {

		/** Empty constructor used for externalization */
		public StatsMessage () { }

		/** Constructor 
		 * @param msgDim 				the number of variables in the UTIL message sent to the parent
		 */
		public StatsMessage(Integer msgDim) {
			super(UTIL_STATS_MSG_TYPE, msgDim);
		}

		/** @return the number of variables in the UTIL message sent to the parent */
		public Integer getMsgDim () {
			return super.getPayload();
		}
	}
}
