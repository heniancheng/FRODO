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

/** The MGM algorithm */
package frodo2.algorithms.localSearch.mgm.mgm2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporterWithConvergence;
import frodo2.algorithms.localSearch.mgm.AssignmentMessage;
import frodo2.algorithms.localSearch.mgm.IMPROVE;
import frodo2.algorithms.localSearch.mgm.MGM;
import frodo2.algorithms.localSearch.mgm.OK;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableConflicts;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** The MGM algorithm
 * 
 * @author Brammert Ottens, 21 feb. 2011
 * @param <V> type used for domain values
 * @param <U> type used for utility values
 * 
 */
public class MGM2 <V extends Addable<V>, U extends Addable<U>> implements StatsReporterWithConvergence<V> {

	/** The type of the START message */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of an OFFER message */
	public static final String OFFER_MSG_TYPE = "offer";

	/** The type of a ACCEPT message */
	public static final String ACCEPT_MSG_TYPE = "accept";

	/** The type of a REJECT message */
	public static final String REJECT_MSG_TYPE = "reject";

	/** Type used for the IMPROVE message */
	public static final String IMPROVE_MSG_TYPE = "improve";

	/** Type used for the GO message */
	public static final String GO_MSG_TYPE = "go";

	/** Type used for the NO GO message */
	public static final String NO_GO_MSG_TYPE = "nogo";

	/** type used for output message */
	public static final String OUTPUT_MSG_TYPE = "MGM output";

	/** The type of the message containing the assignment history */
	public static final String CONV_STATS_MSG_TYPE = "MGMConvStatsMsg";

	/** The queue to which this listener is registered*/
	protected Queue queue;

	/** When \c true, every variable writes log information to a log file */
	protected final boolean LOG = false;

	/** A list of buffered writers used to log information during debugging*/
	protected HashMap<String, BufferedWriter> loggers;

	/** \c true when solving a maximization problem, and false otherwise */
	protected final boolean maximize;

	/** Maps variables to the agent that owns it */
	protected Map<String, String> owners;

	/** The maximal number of rounds to be performed */
	private int cycles;

	/** \c true when the START_AGENT message has been received, and \c false otherwise */
	private boolean started;

	/** Contains, for each variable, an object containing all information needed by the algorithm */
	protected HashMap<String, VariableInfo<V, U>> infos;

	/** The local problem definition */
	private DCOPProblemInterface<V, U>  problem;

	/** The number of variables owned by this agent */
	private int numberOfVariables;

	/** The number of variables that are finished */
	private int variables_finished;

	// stats reporter fields

	/** Whether the stats reporter should print its stats */
	private boolean silent = false;

	/** The global assignment */
	private Map< String, V > assignment;

	/** If \c true, the assignment history must be stored */
	protected final boolean convergence;

	/** For each variable its assignment history */
	protected HashMap<String, ArrayList<CurrentAssignment<V>>> assignmentHistoriesMap;

	/** The global utility of the final variable assignment */
	private U finalUtility;

	/** value between 0 and 1, used to determine whether a variable is offering or receiving */
	private double q;

	/** \c true when the agent has terminated upon initialization, and \c false otherwise*/
	private boolean terminated;

	/** The state of the current variable:
	 */
	protected enum StateValue { 
		/** waiting for OK messages from neighbors */ 		OK, 
		/** waiting for OFFER messages from neighbors */	OFFER,
		/** waiting for IMPROVE messages from neighbors */	IMPROVE,
		/** waiting for a GO message from neighbor */		GO
	};

	/**
	 * Constructor for the stats reporter
	 * 
	 * @param parameters	parameters of the stats reporter
	 * @param problem		the problem that is being solved
	 */
	public MGM2(Element parameters, DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.maximize = false;
		this.convergence = true;
		numberOfVariables = problem.getNbrVars();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<V>>>();
		assignment = new HashMap<String, V>();
		this.finalUtility = this.problem.getZeroUtility();
	}

	/**
	 * Constructor
	 * 
	 * @param problem		the local problem definition
	 * @param parameters	parameters of the listener
	 */
	public MGM2(DCOPProblemInterface<V, U> problem, Element parameters) {
		this.problem = problem;
		this.maximize = problem.maximize();

		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;

		String q = parameters.getAttributeValue("q");
		if(q != null)
			this.q = Double.parseDouble(q);
		else
			this.q = 0.5;

		String cycles = parameters.getAttributeValue("cycles");
		if(cycles != null)
			this.cycles = Integer.parseInt(cycles);
		else
			this.cycles = 200;
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#getStatsFromQueue(frodo2.communication.Queue)
	 */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OUTPUT_MSG_TYPE, this);
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#setSilent(boolean)
	 */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#reset()
	 */
	public void reset() { }

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#notifyIn(frodo2.communication.Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		String type = msg.getType();

		if(type.equals(OUTPUT_MSG_TYPE)) {
			AssignmentMessage<V> msgCast = (AssignmentMessage<V>)msg;
			String var = msgCast.getVariable();
			V value = msgCast.getValue();
			assignment.put(var, value);
			if (! this.silent) 
				System.out.println("var `" + var + "' = " + value);

			if(assignment.size() == numberOfVariables) {
				List<? extends UtilitySolutionSpace<V,U>> spaces = problem.getSolutionSpaces();

				String[] variables_names = new String[assignment.size()];
				V[] variables_values = (V[])Array.newInstance(msgCast.getValue().getClass(), assignment.size());
				int i = 0;
				for(Entry<String, V> e : assignment.entrySet()) {
					variables_names[i] = e.getKey();
					variables_values[i++] = e.getValue();
				}


				for(UtilitySolutionSpace<V, U> space : spaces) {
					finalUtility = finalUtility.add(space.getUtility(variables_names, variables_values));
				}

				if (! silent) {
					if (this.problem.maximize()) 
						System.out.println("Total optimal utility: " + finalUtility);
					else 
						System.out.println("Total optimal cost: " + finalUtility);
				}
			}

			return;
		}

		else if (type.equals(CONV_STATS_MSG_TYPE)) { // in stats gatherer mode, the message sent by a variable containing the assignment history
			StatsReporterWithConvergence.ConvStatMessage<V> msgCast = (StatsReporterWithConvergence.ConvStatMessage<V>)msg;
			assignmentHistoriesMap.put(msgCast.getVar(), msgCast.getAssignmentHistory());

			return;
		}


		if(!started)
			init();


		if(type.equals(OFFER_MSG_TYPE)) {
			OFFER<V, U> msgCast = (OFFER<V, U>)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());

			// if not in offer mode, wait with processing this message
			if(varInfo.state != StateValue.OFFER) {
				varInfo.offerMsgsToProcess.add(msgCast);
			} else {
				if(LOG)
					log(varInfo.variableID, "Received an OFFER from " + msgCast.getSender() + " " + (msgCast.getAssignments() != null));

				this.storeOffer(msgCast, varInfo);
				if(!varInfo.offer || varInfo.committed)
					processOFFER(varInfo);

				if(LOG)
					log(varInfo.variableID, varInfo.toString());
			}

		}

		else if(type.equals(ACCEPT_MSG_TYPE)) {
			ACCEPT<V, U> msgCast = (ACCEPT<V, U>)msg;
			String receiver = msgCast.getReceiver();
			VariableInfo<V, U> varInfo = infos.get(receiver);

			if(LOG)
				log(varInfo.variableID, "Received an ACCEPT");

			assert varInfo.state == StateValue.OFFER; 	// check we are in the right mode
			assert varInfo.offer;						// only an offerer should receive this message
			assert !varInfo.committed;					// we cannot be comitted yet
			varInfo.committed = true;			
			varInfo.improve = msgCast.getGain();
			varInfo.bestOffer = msgCast.getAssignment();
			varInfo.bestOfferer = msgCast.getSender();
			varInfo.setNewValue(varInfo.bestOffer.getOwnValue());

			this.processOFFER(varInfo);
			this.processIMPROVE(varInfo);

			if(LOG)
				log(varInfo.variableID, varInfo.toString());
		}

		else if(type.equals(REJECT_MSG_TYPE)) {
			REJECT msgCast = (REJECT)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());

			if(LOG)
				log(varInfo.variableID, "Received a REJECT");

			assert varInfo.state == StateValue.OFFER;	// check we are in the right mode
			varInfo.committed = false;
			varInfo.offer = false;

			this.processOFFER(varInfo);

			if(LOG)
				log(varInfo.variableID, varInfo.toString());
		}

		else if(type.equals(IMPROVE_MSG_TYPE)) {
			IMPROVE<U> msgCast = (IMPROVE<U>)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());

			if(LOG)
				log(varInfo.variableID, "Received an IMPROVE message from " + msgCast.getSender());

			if(varInfo.state != StateValue.IMPROVE) // if not in IMPROVE mode, wait with processing this message
				varInfo.improveMsgsToProcess.add(msgCast);
			else {
				this.storeIMPROVE(msgCast, varInfo);
				this.processIMPROVE(varInfo);
			}

			if(LOG)
				log(varInfo.variableID, varInfo.toString());
		}

		else if(type.equals(GO_MSG_TYPE)) {
			GO msgCast = (GO)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());

			if(LOG)
				log(varInfo.variableID, "Received a GO message: " + true);

			if(this.processGO(varInfo, true)) // if not in GO mode, wait with processing this message
				varInfo.goMsgToProcess = msgCast;

			if(LOG)
				log(varInfo.variableID, varInfo.toString());
		}

		else if(type.equals(NO_GO_MSG_TYPE)) {
			NOGO msgCast = (NOGO)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());

			if(LOG)
				log(varInfo.variableID, "Received a GO message: " + true);

			if(this.processGO(varInfo, false)) // if not in GO mode, wait with processing this message
				varInfo.goMsgToProcess = msgCast;

			if(LOG)
				log(varInfo.variableID, varInfo.toString());
		}

		else if (type.equals(MGM.OK_MSG_TYPE)) {
			OK<V> msgCast = (OK<V>)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());

			if(LOG)
				log(varInfo.variableID, "Received an OK message from " + msgCast.getSender());

			if(this.processOK(msgCast, varInfo)) // if not in OK mode, wait with processing this message 
				varInfo.okMsgsToProcess.add(msgCast);

			if(LOG)
				log(varInfo.variableID, varInfo.toString());
		}

		else if (type.equals(AgentInterface.STOP_AGENT)) {
			if(!terminated) {
			for(VariableInfo<V,U> varInfo : infos.values()) {
				varInfo.terminated = true;
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<V>(varInfo.variableID, varInfo.currentValue));
			}

			queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));
		}
		}

	}

	/**
	 * Called when the first message is received,
	 * initializes all the fields and variables that are needed
	 * 
	 * @author Brammert Ottens, 21 feb. 2011
	 */
	protected void init() {
		started = true;
		owners = problem.getOwners();
		Set<String> variables = problem.getVariables(problem.getAgent());
		int numberOfVariables = variables.size();
		if (numberOfVariables == 0) { // empty agent
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			return;
		}
		List<? extends UtilitySolutionSpace<V,U>> spaces = problem.getSolutionSpaces();

		infos = new HashMap<String, VariableInfo<V, U>>(numberOfVariables);
		if(convergence)
			assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<V>>>();
		if(LOG)
			loggers = new HashMap<String, BufferedWriter>(numberOfVariables);

		for(String variable : variables) {
			VariableInfo<V, U> varInfo = this.createVariableInfo(variable, problem, spaces, maximize);
			if(convergence)
				assignmentHistoriesMap.put(variable, new ArrayList<CurrentAssignment<V>>());

			if(LOG) {
				try{
					loggers.put(variable, new BufferedWriter( new FileWriter("logs/MGM2-" + variable + ".log")));
				} catch(IOException e) {
					e.printStackTrace();
				}
			}

			infos.put(variable, varInfo);
			for(int i = 1; i < varInfo.neighbors.length; i++) {
				String neighbor = varInfo.neighbors[i];
				queue.sendMessage(owners.get(neighbor), new OK<V>(variable, neighbor, varInfo.currentValue));
			}

			if(varInfo.neighbors.length == 1) {
				varInfo.terminated = true;
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<V>(varInfo.variableID, varInfo.currentValue));
				if(convergence)
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, variable, this.assignmentHistoriesMap.get(variable)));

				if(++this.variables_finished == numberOfVariables) {
					queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));
					terminated = true;
				}
			}
		}
	}

	/** 
	 * @see frodo2.communication.MessageListener#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** 
	 * @see frodo2.communication.MessageListener#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		ArrayList<String> msgTypes = new ArrayList<String>(3);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(IMPROVE_MSG_TYPE);
		msgTypes.add(MGM.OK_MSG_TYPE);
		msgTypes.add(ACCEPT_MSG_TYPE);
		msgTypes.add(OFFER_MSG_TYPE);
		msgTypes.add(GO_MSG_TYPE);
		msgTypes.add(NO_GO_MSG_TYPE);
		msgTypes.add(REJECT_MSG_TYPE);
		msgTypes.add(AgentInterface.STOP_AGENT);
		return msgTypes;
	}

	/** 
	 * @see frodo2.algorithms.StatsReporterWithConvergence#getAssignmentHistories()
	 */
	public HashMap<String, ArrayList<frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment<V>>> getAssignmentHistories() {
		return this.assignmentHistoriesMap;
	}

	/** 
	 * @see frodo2.algorithms.StatsReporterWithConvergence#getCurrentSolution()
	 */
	public Map<String, V> getCurrentSolution() {
		return assignment;
	}

	/**
	 * 
	 * @author Brammert Ottens, 15 mrt. 2011
	 * @return the utility of the final solution
	 */
	public U getFinalSolution() {
		return this.finalUtility;
	}

	/**
	 * Method to process an OK message
	 * 
	 * @author Brammert Ottens, 21 feb. 2011
	 * @param msg		the message to be processed
	 * @param varInfo	info on the variable that is responsible for the message
	 * @return \c true when the message needs to be stored for later processing, and \c false otherwise
	 */
	public boolean processOK(OK<V> msg, VariableInfo<V, U> varInfo) {
		if(!varInfo.terminated) {
			if(varInfo.state != StateValue.OK)
				return true;

			varInfo.setNeighborValue(msg.getSender(), msg.getValue());
			if(++varInfo.okMsgsReceived == varInfo.number_of_neighbors) {
				varInfo.currentUtility = varInfo.calculateCurrentUtility();
				varInfo.okMsgsReceived = 0;
				varInfo.state = StateValue.OFFER;

				// determine whether this variable is an offerer or a receiver
				varInfo.offer = Math.random() < q;

				int neighborIndex = -1;
				if(varInfo.offer) {
					// randomly select a neighbor
					neighborIndex = 1 + (int)(Math.random()*varInfo.number_of_neighbors);
					if(LOG)
						log(varInfo.variableID, "making an offer to variable " + varInfo.neighbors[neighborIndex]);

					// calculate all possible BinaryAssignments to be offered
					OFFER<V,U> offer = varInfo.calculateOffer(neighborIndex);

					// send an offer message to this neighbor
					queue.sendMessage(owners.get(offer.getReceiver()), offer);
				}

				// tell all other neighbors not to expect an offer
				for(int i = 1; i < varInfo.neighbors.length; i++) {
					if( i != neighborIndex) {
						String neighbor = varInfo.neighbors[i];
						queue.sendMessage(owners.get(neighbor), new OFFER<V,U>(varInfo.variableID, neighbor, null, null));
					}
				}

				// process already received offer messages
				for(OFFER<V,U> msgOFFER : varInfo.offerMsgsToProcess) {
					this.storeOffer(msgOFFER, varInfo);
					this.processOFFER(varInfo);
				}
				varInfo.offerMsgsToProcess.clear();
			}
		}

		return false;
	}

	/**
	 * Stores an offer made
	 * 
	 * @author Brammert Ottens, 6 apr. 2011
	 * @param msgOFFER	the offer
	 * @param varInfo	the variable that received the offer
	 */
	public void storeOffer(OFFER<V,U> msgOFFER, VariableInfo<V, U> varInfo) {
		ArrayList<BinaryAssignment<V>> list = msgOFFER.getAssignments();
		varInfo.offersReceived++;
		if(list != null) { // it is not an empty offer
			String sender = msgOFFER.getSender();

			if(varInfo.offer) {
				if(LOG)
					log(varInfo.variableID, "REJECTING offer");
				queue.sendMessage(owners.get(sender), new REJECT(sender));
			} else {
				ArrayList<AddableConflicts<U>> utilities = msgOFFER.getUtilities();
				varInfo.offeringVariables.add(sender);

				// analyze all proposed value assignments
				for(int i = 0; i < list.size(); i++) {
					BinaryAssignment<V> ass = list.get(i);
					AddableConflicts<U> gain = utilities.get(i);

					// check local utility gain
					AddableConflicts<U> totalGain = null;
					if(gain.getUtility() != varInfo.infeasibleUtility.getUtility())
						totalGain = gain.add(varInfo.calculateGain(ass, sender));
					else
						totalGain = gain;

					// remember the one with the biggest combined utility gain
					if(varInfo.improve == null || varInfo.improve.compareTo(totalGain) < 0) {
						varInfo.bestOfferer = sender;
						varInfo.improve = totalGain;
						varInfo.bestOffer = ass;
					}
				}
			}
		}
	}

	/**
	 * checks whether all offers have been received, and acts
	 * accordingly
	 * 
	 * @author Brammert Ottens, 6 apr. 2011
	 * @param varInfo	the variable that checks whether all offers have been received
	 */
	public void processOFFER(VariableInfo<V, U> varInfo) {
		if(!varInfo.terminated) {

			if(varInfo.offersReceived == varInfo.number_of_neighbors) { // all neighbors have made an OFFER
				varInfo.offersReceived = 0;
				varInfo.state = StateValue.IMPROVE;
				varInfo.can_move = true;

				// if we are not committed and at least one offer has been made, accept the best offer
				// if it gives a gain bigger than zero
				if(!varInfo.committed && varInfo.bestOffer != null && varInfo.improve.compareTo(varInfo.zeroConflicts) > 0) { 
					varInfo.committed = true;
					varInfo.offeringVariables.remove(varInfo.bestOfferer);
					varInfo.setNewValue(varInfo.bestOffer.getOwnValue());
					queue.sendMessage(owners.get(varInfo.bestOfferer), new ACCEPT<V, U>(varInfo.variableID, varInfo.bestOfferer, varInfo.bestOffer, varInfo.improve));
				}

				// if not committed, find the best local improvement possible
				if(!varInfo.committed)
					varInfo.can_move = varInfo.determinePossibleImprovement();

				// report the rejections and possible improvement to neighbors
				for(int i = 1; i < varInfo.neighbors.length; i++) {
					String neighbor = varInfo.neighbors[i];
					if(varInfo.offeringVariables.contains(neighbor)) {
						queue.sendMessage(owners.get(neighbor), new REJECT(neighbor));
					}

					queue.sendMessage(owners.get(neighbor), new IMPROVE<U>(varInfo.variableID, neighbor, varInfo.improve));
				}

				// process the already received IMPROVE messages
				for(IMPROVE<U> msgIMPROVE : varInfo.improveMsgsToProcess)
					notifyIn(msgIMPROVE);
				varInfo.improveMsgsToProcess.clear();
			}
		}
	}

	/**
	 * Store an IMPROVE message received
	 * 
	 * @author Brammert Ottens, 6 apr. 2011
	 * @param msg		the message received
	 * @param varInfo	the variable that receives the message
	 */
	public void storeIMPROVE(IMPROVE<U> msg, VariableInfo<V, U> varInfo) {
		if(!varInfo.terminated) {
			varInfo.improveMsgReceived++;
			int diff = maximize ? msg.getImprove().compareTo(varInfo.improve) : -msg.getImprove().compareTo(varInfo.improve);

			if(diff > 0 || (diff == 0 && varInfo.variableID.compareTo(msg.getSender()) < 0))
				varInfo.can_move = false;
		}
	}

	/**
	 * Method used to check whether all IMPROVE messages have been received
	 * 
	 * @author Brammert Ottens, 21 feb. 2011
	 * @param varInfo	info on the variable that is responsible for the messages
	 */
	public void processIMPROVE(VariableInfo<V, U> varInfo) {
		if(!varInfo.terminated) {
			if(varInfo.improveMsgReceived == varInfo.number_of_neighbors) {
				varInfo.improveMsgReceived = 0;

				if(varInfo.committed) {
					varInfo.state = StateValue.GO;
					if(varInfo.can_move) {
						queue.sendMessage(this.owners.get(varInfo.bestOfferer), new GO(varInfo.bestOfferer));
					} else {
						queue.sendMessage(this.owners.get(varInfo.bestOfferer), new NOGO(varInfo.bestOfferer));
					}

					if(varInfo.goMsgToProcess != null) {
						notifyIn(varInfo.goMsgToProcess);
						varInfo.goMsgToProcess = null;
					}
				} else {
					sendOK(varInfo);

					// reset all variable fields;
					varInfo.reset();

					for(OK<V> msgOK : varInfo.okMsgsToProcess)
						this.processOK(msgOK, varInfo);
					varInfo.okMsgsToProcess.clear();
				}
			}
		}
	}

	/**
	 * Method used to process a GO or NOGO message
	 * 
	 * @author Brammert Ottens, 6 apr. 2011
	 * @param varInfo	the variable that received the message
	 * @param move		\c true when the message is GO, \c false when a NOGO
	 * @return	\c true when the message should be stored for later processing, and \c false otherwise
	 */
	public boolean processGO(VariableInfo<V, U> varInfo, boolean move) {
		if(varInfo.state != StateValue.GO)
			return true;

		varInfo.can_move = varInfo.can_move && move;
		sendOK(varInfo);

		// reset all variable fields;
		varInfo.reset();

		varInfo.state = StateValue.OK;
		for(OK<V> msgOK : varInfo.okMsgsToProcess)
			this.processOK(msgOK, varInfo);
		varInfo.okMsgsToProcess.clear();

		return false;
	}

	/**
	 * Sending an OK message to all neighbors
	 * 
	 * @author Brammert Ottens, 21 feb. 2011
	 * @param varInfo	the variable that is sending the messages
	 */
	protected void sendOK(VariableInfo<V, U> varInfo) {
		varInfo.termination_counter++;

		if(varInfo.termination_counter == cycles) {
			String variable = varInfo.variableID;
			V finalValue = varInfo.currentValue;
			varInfo.terminated = true;
			queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<V>(variable, finalValue));
			if(convergence) {
				ArrayList<CurrentAssignment<V>> history = assignmentHistoriesMap.get(variable);
				history.add(new CurrentAssignment<V>(queue.getCurrentTime(), varInfo.termination_counter, finalValue));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, variable, history));	
			}

			if(++this.variables_finished == infos.size())
				queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));

			return;
		}

		if(varInfo.can_move) {
			varInfo.currentValueIndex = varInfo.newValue;
			varInfo.currentValue = varInfo.neighborDomains[0][varInfo.newValue];
			varInfo.agent_view[0] = varInfo.currentValue;
			if(convergence && !varInfo.currentValue.equals(varInfo.newValue)){
				this.assignmentHistoriesMap.get(varInfo.variableID).add(new CurrentAssignment<V>(queue.getCurrentTime(), varInfo.termination_counter, varInfo.currentValue));
			}

		}

		for(int i = 1; i < varInfo.neighbors.length; i++) {
			String neighbor = varInfo.neighbors[i];
			queue.sendMessage(owners.get(neighbor), new OK<V>(varInfo.variableID, neighbor, varInfo.currentValue));
		}

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
	 * method used to create a VariableInfo object
	 * 
	 * @author Brammert Ottens, 16 feb. 2011
	 * @param variableID	the ID of the variable
	 * @param problem		the local problem definition
	 * @param spaces		list of spaces owned by the agent
	 * @param maximize 		\c true when the problem is a maximization problem, and \c false otherwise
	 * @return	a VariablInfo object for \c variableID
	 */
	protected VariableInfo<V, U> createVariableInfo(String variableID, DCOPProblemInterface<V, U> problem, List<? extends UtilitySolutionSpace<V, U>> spaces, boolean maximize) {
		return new VariableInfo<V, U>(variableID, problem, spaces, maximize);
	}

	/**
	 * Convenience class that contains all necessary information
	 * on a single variable.
	 * @param <V> type used for domain values
	 * @param <U> type used for utility values
	 */
	protected static class VariableInfo <V extends Addable<V>, U extends Addable<U>> {

		/** The name of the variable */
		protected String variableID;

		/** \c true when maximizing, and \c false when minimizing */
		protected final boolean maximize;

		/** The variable's local problem */
		private List<UtilitySolutionSpace<V, U>> spaces;

		/** The current state of the variable */
		protected StateValue state;

		/** \c true when this variable has terminated */
		protected boolean terminated;

		// information on the neighbors

		/** The number of neighboring variables */
		protected int number_of_neighbors;

		/** gives the position of a neighboring variable in the array*/
		private HashMap<String, Integer> neighborPointer;

		/** List of neighbors */
		protected String[] neighbors;

		/** The domains of not this variable and all the neighbors */
		protected V[][] neighborDomains;

		/** The utility value used for an infeasible utility */
		protected AddableConflicts<U> infeasibleUtility;

		/** The utility value used to denote zero with no conflicts */
		protected AddableConflicts<U> zeroConflicts;

		/** The utility value used to denote zero */
		protected U zeroUtility;

		// variables used in the OK state

		/** Counts the number of OK messages received */
		protected int okMsgsReceived;

		/** List of OK messages to process */
		protected List<OK<V>> okMsgsToProcess;

		/** The current context */
		protected V[] agent_view;

		// variables used in the OFFER state

		/** Counts the number of OFFER messages received */
		private int offersReceived;

		/** List of OFFER messages to process */
		protected List<OFFER<V, U>> offerMsgsToProcess;

		/** The neighbor that made the best offer so far */
		private BinaryAssignment<V> bestOffer;

		/** The best offer received in the current round so far */
		private String bestOfferer;

		/** Maps submitted offer to private gain */
		private HashMap<BinaryAssignment<V>, AddableConflicts<U>> submittedOffers;

		/** When \c true this variable is offering, otherwise it is receiving */
		private boolean offer;

		/** The neighboring variables that have submitted an offer */
		private HashSet<String> offeringVariables;

		/** \c true if at least one offer has been accepted, \c false otherwise */
		private boolean committed;

		// variables used in the IMPROVE state

		/** Counts the number of IMPROVE messages received */
		protected int improveMsgReceived;

		/** List of IMPROVE messages to process */
		protected List<IMPROVE<U>> improveMsgsToProcess;

		/** The variable's current value */
		protected V currentValue;
		
		/** The index of the current value */
		protected int currentValueIndex;

		/** The possible new value of this variable, given the context */
		protected int newValue;

		/** \c true when this variable is allowed to change value, and \c false otherwise */
		protected boolean can_move;

		/** The current utiliyt, give the current context */
		protected AddableConflicts<U> currentUtility;

		/** The value with which this variable can improve its utility, given the context */
		protected AddableConflicts<U> improve;

		/** Used to determine when to terminate */
		protected int termination_counter;

		// variables used in the GO state		

		/** Stores a GO message when the variable is not yet ready to process it */
		protected Message goMsgToProcess;

		/**
		 * Constructor
		 * 
		 * @param variableID	the ID of the variable
		 * @param problem		the local problem definition
		 * @param spaces		list of all spaces owned by the agent
		 * @param maximize		\c true when the problem is a maximization problem, and \c false otherwise
		 */
		@SuppressWarnings("unchecked")
		public VariableInfo(String variableID, DCOPProblemInterface<V, U> problem, List<? extends UtilitySolutionSpace<V, U>> spaces, boolean maximize) {
			this.maximize = maximize;
			this.variableID = variableID;
			U infeasibleUtility = maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility();
			this.infeasibleUtility = new AddableConflicts<U>(infeasibleUtility, Integer.MAX_VALUE);
			this.zeroUtility = problem.getZeroUtility();
			this.zeroConflicts = new AddableConflicts<U>(zeroUtility, 0);
			this.state = StateValue.OK;

			V[] domain = problem.getDomain(variableID);
			this.currentValueIndex = (int)(Math.random()*domain.length);
			this.currentValue = domain[currentValueIndex];

			// initialize list for messages to be processed
			okMsgsToProcess = new ArrayList<OK<V>>();
			improveMsgsToProcess = new ArrayList<IMPROVE<U>>();
			offerMsgsToProcess = new ArrayList<OFFER<V, U>>();
			offeringVariables = new HashSet<String>();
			submittedOffers = new HashMap<BinaryAssignment<V>, AddableConflicts<U>>();

			this.spaces = new ArrayList<UtilitySolutionSpace<V,U>>();
			for(UtilitySolutionSpace<V,U> space : spaces) {
				String[] variables = space.getVariables();
				for(String variable : variables)
					if(variable.equals(variableID)) {
						this.spaces.add(space);
						continue;
					}
			}


			// initialize neighbors
			this.number_of_neighbors = problem.getNbrNeighbors(variableID);
			neighborDomains = (V[][])Array.newInstance(domain.getClass(), number_of_neighbors + 1);
			neighbors = new String[this.number_of_neighbors + 1];
			neighborDomains[0] = domain;
			neighbors[0] = variableID;
			if(this.number_of_neighbors > 0)
				System.arraycopy(problem.getNeighborVars(variableID).toArray(new String[0]), 0, neighbors, 1, this.number_of_neighbors);
			neighborPointer = new HashMap<String, Integer>(this.number_of_neighbors);
			for(int i = 1; i < this.number_of_neighbors + 1; i++) {
				String neighbor = neighbors[i];
				neighborPointer.put(neighbor, i);
				neighborDomains[i] = problem.getDomain(neighbor);
			}

			// create the context
			agent_view = (V[])Array.newInstance(this.currentValue.getClass(), this.number_of_neighbors + 1);
			agent_view[0] = this.currentValue;
		}

		/**
		 * Stores the value reported by a neighbor
		 * 
		 * @author Brammert Ottens, 21 feb. 2011
		 * @param neighbor	the neighbor that reported the value
		 * @param value		the reported value
		 */
		public void setNeighborValue(String neighbor, V value) {
			agent_view[neighborPointer.get(neighbor)] = value;
		}
		
		/**
		 * @author Brammert Ottens, 20 mei 2011
		 * @param value the new value for the variable
		 */
		public void setNewValue(V value) {
			V[] domain = this.neighborDomains[0];
			for(int i = 0; i < domain.length; i++)
				if(domain[i].equals(value)) {
					newValue = i;
					return;
				}
			
			assert false; // this point should never be reached
		}

		/**
		 * Given the current agent view, the current utility
		 * is calculated
		 * 
		 * @author Brammert Ottens, 6 apr. 2011
		 * @return the current utility
		 */
		public AddableConflicts<U> calculateCurrentUtility() {

			int conflicts = 0;
			AddableDelayed<U> util = zeroUtility.addDelayed();
			for(int j = 0; j < spaces.size(); j++) {
				UtilitySolutionSpace<V, U> space = spaces.get(j);

				U u = space.getUtility(this.neighbors, this.agent_view);
				if(u == this.infeasibleUtility.getUtility())
					conflicts++;
				util.addDelayed(u);
			}

			return new AddableConflicts<U> (util.resolve(), conflicts);
		}

		/**
		 * Given the current agent view, the current utility
		 * is calculated, ignoring constraints that contain
		 * the variable
		 * 
		 * @author Brammert Ottens, 6 apr. 2011
		 * @param variable the variable that needs to be ignored
		 * @return the current utility
		 */

		public AddableConflicts<U> calculateCurrentUtility(String variable) {
			int conflicts = 0;
			AddableDelayed<U> util = zeroUtility.addDelayed();
			for(int j = 0; j < spaces.size(); j++) {
				UtilitySolutionSpace<V, U> space = spaces.get(j);
				String[] variables = space.getVariables();
				for(String var : variables)
					if(var.equals(variable))
						continue;

				U u = space.getUtility(this.neighbors, this.agent_view);
				if(u == this.infeasibleUtility.getUtility())
					conflicts++;
				util.addDelayed(u);
			}

			return new AddableConflicts<U> (util.resolve(), conflicts);
		}

		/**
		 * Calculate the utility gain of the assignment
		 * @author Brammert Ottens, 29 mrt. 2011
		 * @param assignment	the assignment
		 * @param variable		the offering variable
		 * @return	the utility gain this assignment gives (can be negative)
		 */
		public AddableConflicts<U> calculateGain(BinaryAssignment<V> assignment, String variable) {
			return calculateGain(assignment.neighborValue(), assignment.getOwnValue(), variable);
		}

		/**
		 * Calculate the utility gain of the assignment
		 * @author Brammert Ottens, 29 mrt. 2011
		 * @param ownValue		the variables own value in the offer
		 * @param neighborValue the value of the offering variable
		 * @param variable		the offering variable
		 * @return	the utility gain this assignment gives (can be negative)
		 */
		public AddableConflicts<U> calculateGain(V ownValue, V neighborValue, String variable) {
			return calculateGain(ownValue, neighborValue, this.neighborPointer.get(variable), variable);
		}

		/**
		 * Calculate the utility gain of the assignment
		 * @author Brammert Ottens, 29 mrt. 2011
		 * @param ownValue		the variables own value in the offer
		 * @param neighborValue the value of the offering variable
		 * @param neighborIndex the index of the offering variable
		 * @param variable		the offering variable
		 * @return	the utility gain this assignment gives (can be negative)
		 */
		public AddableConflicts<U> calculateGain(V ownValue, V neighborValue, int neighborIndex, String variable) {
			V variableValueCurrent = agent_view[neighborIndex];
			agent_view[neighborIndex] = neighborValue;
			agent_view[0] = ownValue;
			AddableConflicts<U> util = null;
			if(variable == null)
				util = this.calculateCurrentUtility();
			else
				util = this.calculateCurrentUtility(variable);

			if(currentUtility.getUtility() != infeasibleUtility.getUtility()) {
				util = util.subtract(currentUtility);
			}
			
			agent_view[neighborIndex] = variableValueCurrent;
			agent_view[0] = currentValue;

			return util;
		}

		/**
		 * Creates the offer to be made to the chosen neighbor
		 * 
		 * @author Brammert Ottens, 1 apr. 2011
		 * @param neighborIndex the index of the chosen neighbor
		 * @return	the offer to be made
		 */
		public OFFER<V,U> calculateOffer(int neighborIndex) {

			ArrayList<BinaryAssignment<V>> assignments = new ArrayList<BinaryAssignment<V>>();
			ArrayList<AddableConflicts<U>> utilities = new ArrayList<AddableConflicts<U>>();
			boolean createdOffer = false;
			submittedOffers.clear();

			String neighbor = neighbors[neighborIndex];
			V[] neighborDomain = neighborDomains[neighborIndex];
			V[] ownDomain = neighborDomains[0];
			V currentNeighborValue = this.agent_view[neighborIndex];
			for(int i = 0; i < ownDomain.length; i++) {
				V ownValue = ownDomain[i];
				boolean equalsCurrentValue = ownValue.equals(this.currentValue);

				for(int j =0; j < neighborDomain.length; j++) {
					V neighborValue = neighborDomain[j];

					if(!equalsCurrentValue && !neighborValue.equals(currentNeighborValue)) {
						AddableConflicts<U> gain = calculateGain(ownValue, neighborValue, neighborIndex, null);
						if(maximize ? gain.compareTo(zeroConflicts) > 0 : gain.compareTo(zeroConflicts) < 0) {
							createdOffer = true;
							BinaryAssignment<V> ass = new BinaryAssignment<V>(ownValue, neighborValue); 
							assignments.add(ass);
							utilities.add(gain);
							submittedOffers.put(ass, gain);
						}
					}
				}
			}

			offer = createdOffer;

			if(createdOffer)
				return new OFFER<V, U>(this.variableID, neighbor, assignments, utilities);
			else
				return new OFFER<V, U>(this.variableID, neighbor, null, null);
		}

		/**
		 * Given the current context (agent_view), this method
		 * determines the maximal improvement that can be made
		 * 
		 * @author Brammert Ottens, 21 feb. 2011
		 * @return \c true when the maximal improvement is positive, and \c false otherwise
		 */
		public boolean determinePossibleImprovement() {
			improve = this.infeasibleUtility;
			V[] domain = neighborDomains[0];

			for(int i = 0; i < domain.length; i++) {
				if(i != this.currentValueIndex) {
					agent_view[0] = domain[i];
					AddableConflicts<U> util = calculateCurrentUtility();

					int diff = maximize ? improve.compareTo(util) : -improve.compareTo(util);

					if( diff < 0) { 
						improve = util;
						this.newValue = i;
					} else if (diff == 0 && Math.random() < 0.5) {
						improve = util;
						this.newValue = i;
					}
				}
			}
			agent_view[0] = currentValue;
			
			if(improve.getUtility() != this.infeasibleUtility.getUtility())
				improve = improve.subtract(currentUtility);
			
			if(maximize ? improve.compareTo(zeroConflicts) >= 0 : improve.compareTo(zeroConflicts) <= 0)
				return true;

			return false;

		}

		/**
		 * Resets the fields
		 * @author Brammert Ottens, 6 apr. 2011
		 */
		public void reset() {
			Arrays.fill(this.agent_view, null);
			agent_view[0] = this.currentValue;
			this.improve = null;
			this.bestOffer = null;
			this.bestOfferer = null;
			this.offer = false;
			this.committed = false;
			this.offeringVariables.clear();
			state = StateValue.OK;
		}

		/** @see java.lang.Object#toString() */
		@Override
		public String toString() {
			String str = "";

			str += "---------------------------\n";
			str += "Variable " + this.variableID + "\n";
			str += "Offering " + this.offer + "\n";
			str += "Comitted " + this.committed + "\n";
			str += "State = " + this.state + "\n";
			str += "offers = " + this.offersReceived + "\n";
			str += "improve = " + this.improveMsgReceived + "\n";
			str += "ok counter = " + this.okMsgsReceived + "\n";
			str += "termination counter = " + this.termination_counter + "\n";
			str += "neighbors: [";
			int i = 0;
			for(; i < neighbors.length - 1; i++)
				str += neighbors[i] + "=" + agent_view[i] + ", ";
			str += neighbors[i] + "=" + agent_view[i] + "]\n";
			str += " OK messages stored:\n" + this.okMsgsToProcess + "\n";
			str += " OFFER messages stored:\n" + this.offerMsgsToProcess + "\n";
			str += " IMPROVE messages stored:\n" + this.improveMsgsToProcess + "\n";
			str += " GO message stored: " + (this.goMsgToProcess != null) + "\n";
			str += "---------------------------\n";

			return str;
		}
	}

}
