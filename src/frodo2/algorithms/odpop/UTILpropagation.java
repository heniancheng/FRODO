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

import java.util.List;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.odpop.Good;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTree.InnerNodeTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTree.LeafNode;
import frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNodeTree;
import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** The UTIL propagation phase for O-DPOP
 * @author brammert
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * 
 * @todo make it work for minimisation problems as well!
 *
 */
public class UTILpropagation < Val extends Addable<Val>, U extends Addable<U> > extends UTILpropagationFullDomain<Val, U, LeafNode<U>> {

	/**
	 * Constructor for the statsreporter
	 * 
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public UTILpropagation (Element parameters, DCOPProblemInterface<Val, U> problem) {
		super(parameters, problem);
	}

	/**
	 * Constructor
	 * 
	 * @param problem		The problem description
	 * @param parameters	The parameters
	 */
	public UTILpropagation (DCOPProblemInterface<Val, U> problem, Element parameters) {
		super(problem, parameters);
	}

	/**
	 * Alternative constructor not using XML
	 * 
	 * @param problem		The problem description
	 */
	public UTILpropagation (DCOPProblemInterface<Val, U> problem) {
		super(problem);
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
		
		if (type.equals(UTIL_MSG)) {
			UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>)msg;
			VariableInfo varInfo = infos[variablePointer.get(msgCast.getReceiver())];
			String sender = msgCast.getSender();
			processGOOD(varInfo, msgCast.getGood(varInfo.tree.getChildSeparatorReportingOrder(varInfo.childrenPointer.get(sender))), sender, null, null);
		}

		else if (type.equals(UTIL_MSG_VARS)) {
			UTILvarsMsg<Val, U> msgCast = (UTILvarsMsg<Val, U>)msg;
			VariableInfo varInfo = infos[variablePointer.get(msgCast.getReceiver())];
			String sender = msgCast.getSender();
			varInfo.tree.setChildrenSeparator(varInfo.childrenPointer.get(sender), msgCast.getVariables());
			processGOOD(varInfo, msgCast.getGood(), sender, null, null);
		}

		else if (type.equals(UTIL_MSG_DOM)) {
			UTILdomMsg<Val, U> msgCast = (UTILdomMsg<Val, U>)msg;
			VariableInfo varInfo = infos[variablePointer.get(msgCast.getReceiver())];
			String sender = msgCast.getSender();
			String[] variables = varInfo.tree.getChildSeparatorReportingOrder(varInfo.childrenPointer.get(sender));
			int[] domInfo = msgCast.getDomInfo();
			processGOOD(varInfo, msgCast.getGood(variables), sender, domInfo, variables);
		}

		else if (type.equals(UTIL_MSG_DOM_VARS)) {
			UTILvarsMsgWithDom<Val, U> msgCast = (UTILvarsMsgWithDom<Val, U>)msg;
			VariableInfo varInfo = infos[variablePointer.get(msgCast.getReceiver())];
			String[] variables = msgCast.getVariables();
			int[] domInfo = msgCast.getDomInfo();
			String sender = msgCast.getSender();
			varInfo.tree.setChildrenSeparator(varInfo.childrenPointer.get(sender), variables);
			processGOOD(varInfo, msgCast.getGood(), sender, domInfo, variables);
		} 
		
		else {
			super.notifyIn(msg);
		}


	}
	
	/** @see frodo2.algorithms.odpop.UTILpropagationFullDomain#newVariableInfoInstanceInnerNode(java.lang.String, java.util.List, java.lang.String, Val[], java.util.List, int, frodo2.solutionSpaces.Addable) */
	public VariableInfo newVariableInfoInstanceInnerNode(String var, List<String> children, String parent, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> usedSpaces, int numberOfChildren, U zeroUtility) {
		return new VariableInfo(var, parent, children.toArray(new String[0]), new InnerNodeTree<Val, U, LeafNode<U>>(var, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility(), leafNodeInstance(), infeasibleUtil, maximize, collectStats));
	}
	
	/** @see frodo2.algorithms.odpop.UTILpropagationFullDomain#newVariableInfoInstanceLeafNode(java.lang.String, java.util.List, java.lang.String, Val[], java.util.List, int, frodo2.solutionSpaces.Addable) */
	public VariableInfo newVariableInfoInstanceLeafNode(String var, List<String> children, String parent, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> usedSpaces, int numberOfChildren, U zeroUtility) {
		return new VariableInfo(var, parent, children.toArray(new String[0]), new LeafNodeTree<Val, U, LeafNode<U>>(var, ownVariableDomain, usedSpaces, problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
	}
	
	/**
	 * @author Brammert Ottens, 10 feb. 2011
	 * @return a new instance of a leafnode
	 */
	protected LeafNode<U> leafNodeInstance() {
		return new LeafNode<U>();
	}
	
	/** @see frodo2.algorithms.odpop.UTILpropagationFullDomain#utilPropagationFinished(java.lang.String, frodo2.algorithms.odpop.goodsTree.GoodsTree, frodo2.solutionSpaces.Addable, int) */
	@Override
	protected void utilPropagationFinished(String variableID, GoodsTree<Val, U, LeafNode<U>> tree, U utility, int domainSize) {
		queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(utility, variableID));
		queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(domainSize));
		// start the VALUE propagation
		queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, LeafNode<U>>(tree, variableID));
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
//					OptUtilMessage<U> output = new OptUtilMessage<U>(this.infeasibleUtil, variable.variableID);
//					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
//					queue.sendMessage(AgentInterface.STATS_MONITOR, output);
//					queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, LeafNode<U>>(null, variable.variableID));
					utilPropagationFinished(variable.variableID, null, this.infeasibleUtil, variable.tree.getFinalDomainSize().length);
				} else {
					if(LOG)
						log(variable.variableID, aMax.toString());
					variable.tree.removeAMax();
					utilPropagationFinished(variable.variableID, variable.tree, aMax.getUtility(), variable.tree.getFinalDomainSize().length);
				}
			} else {
				for(int i = 0; i < variable.children.length; i++) {
					String child = variable.children[i];
					variable.done[i] = true;
					sendMessageToVariable(child, variable.getAskMessage(i), variable.variableID);
				}
			}
		} else if(receivedASK[varIndex]) { // if we already received an ASK message, process it
			processASK(variable);
		}
	}

	/**
	 * Method used to process the GOOD messages received by this agent
	 * 
	 * @param variable 	The variable information
	 * @param g			The good received
	 * @param sender	The sender of the good
	 * @param domInfo	domain size info reported by the child
	 * @param variables the variables for which domain size info is reported
	 */
	private void processGOOD(VariableInfo variable, Good<Val, U> g, String sender, int[] domInfo, String[] variables) {
		if(LOG)
			log(variable.variableID, "Received a GOOD from " + sender +": " + g );

		int childIndex = variable.childrenPointer.get(sender);

		boolean newVariable = ((InnerNodeTree<Val, U, LeafNode<U>>)variable.tree).add(g, childIndex) || variable.getNewVariable();

		if(LOG)
			log(variable.variableID, variable.tree.toString());

		if(domInfo != null)
			variable.tree.setFinalDomainSize(variables, domInfo);

		variable.done[childIndex] = false;
		Good<Val, U> aMax = variable.tree.getAmax();
		if(aMax != null) {
			if(variable.root) {
				variable.tree.removeAMax();
				if(!variable.terminated) {
					variable.terminated = true;
					queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(aMax.getUtility(), variable.variableID));
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
					// start the VALUE propagation
					queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, LeafNode<U>>(variable.tree, variable.variableID));
				}
			} else if (!variable.responded) {
				variable.tree.removeAMax();
				if(newVariable) {
					variable.setNewVariable(false);
					if(variable.tree.stillToSend() && variable.tree.hasFullInfo())
						sendMessageToVariable(variable.parent, new UTILvarsMsgWithDom<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getFinalDomainSize()), variable.variableID);
					else
						sendMessageToVariable(variable.parent, new UTILvarsMsg<Val, U>(variable.variableID, variable.parent, aMax), variable.variableID);
				} else {
					if(variable.tree.stillToSend() && variable.tree.hasFullInfo())
						sendMessageToVariable(variable.parent, new UTILdomMsg<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getFinalDomainSize()), variable.variableID);
					else
						sendMessageToVariable(variable.parent, new UTILmsg<Val, U>(variable.variableID, variable.parent, aMax), variable.variableID);
				}
				variable.responded = true;
			}
		} else if (!variable.responded) {
			variable.done[childIndex] = true;
			sendMessageToVariable(sender, variable.getAskMessage(sender), variable.variableID);
		}
	}

	/**
	 * Method to process the ASK messages received by this agent
	 * @param variable	The variable information
	 */
	@Override
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
					if(variable.tree.stillToSend() && variable.tree.hasFullInfo())
						sendMessageToVariable(variable.parent, new UTILvarsMsgWithDom<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getFinalDomainSize()), variable.variableID);
					else
						sendMessageToVariable(variable.parent, new UTILvarsMsg<Val, U>(variable.variableID, variable.parent, aMax), variable.variableID);
				} else {
					if(variable.tree.stillToSend() && variable.tree.hasFullInfo())
						sendMessageToVariable(variable.parent, new UTILdomMsg<Val, U>(variable.variableID, variable.parent, aMax, variable.tree.getFinalDomainSize()), variable.variableID);
					else
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
					sendMessageToVariable(child, variable.getAskMessage(i), variable.variableID);
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
	@Override
	public void processDONE(VariableInfo variable, String sender) {
		if(LOG)
			log(variable.variableID, "Received a DONE message from " + sender);
		int pointer = variable.childrenPointer.get(sender);
		variable.done[pointer] = true;
		variable.infeasible = ((InnerNodeTree<Val, U, LeafNode<U>>)variable.tree).setChildDone(pointer);
		if(LOG)
			log(variable.variableID, variable.toString());

		if(variable.root) {
			Good<Val, U> aMax = variable.tree.getAmax();
			if(aMax != null) {
				variable.tree.removeAMax();
				if(!variable.terminated) {
					variable.terminated = true;
					queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(aMax.getUtility(), variable.variableID));
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
					// start the VALUE propagation
					queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, LeafNode<U>>(variable.tree, variable.variableID));
				}
			} else if(variable.infeasible && !variable.terminated) {
				variable.terminated = true;
				queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(this.infeasibleUtil, variable.variableID));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage(variable.tree.getFinalDomainSize().length));
				// start the VALUE propagation
				queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, LeafNode<U>>(null, variable.variableID));
			}
		} else if(!variable.responded) {
			this.processASK(variable);
		}
	}

}
