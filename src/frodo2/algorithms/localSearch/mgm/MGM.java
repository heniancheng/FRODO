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
package frodo2.algorithms.localSearch.mgm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporterWithConvergence;
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
public class MGM <V extends Addable<V>, U extends Addable<U>> implements StatsReporterWithConvergence<V> {
	
	/** The type of the START message */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** Type used for the OK message */
	public static final String OK_MSG_TYPE = "ok";

	/** Type used for the IMPROVE message */
	public static final String IMPROVE_MSG_TYPE = "improve";

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
	protected int max_distance = 200;

	/** \c true when the START_AGENT message has been received, and \c false otherwise */
	protected boolean started;

	/** Contains, for each variable, an object containing all information needed by the algorithm */
	protected HashMap<String, VariableInfo<V, U>> infos;

	/** The local problem definition */
	protected DCOPProblemInterface<V, U>  problem;
	
	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;
	
	/** The number of variables owned by this agent */
	protected int numberOfVariables;

	/** The number of variables that are finished */
	protected int variables_finished;

	/** The global assignment */
	protected Map< String, V > assignment;

	/** If \c true, the assignment history must be stored */
	protected final boolean convergence;

	/** For each variable its assignment history */
	protected HashMap<String, ArrayList<CurrentAssignment<V>>> assignmentHistoriesMap;
	
	/** The global utility of the final variable assignment */
	protected U finalUtility;
	
	/** \c true when this agent has sent the agent finished message */
	private boolean terminated;
	
	/** The state of the current variable:
	 */
	protected enum StateValue { 
		/** waiting for OK messages from neighbors */ 		OK, 
		/** waiting for IMPROVE messages from neighbors */	IMPROVE
	};
	
	/**
	 * Constructor for the stats reporter
	 * 
	 * @param parameters	parameters of the stats reporter
	 * @param problem		the problem that is being solved
	 */
	public MGM(Element parameters, DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.maximize = false;
		this.convergence = false;
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
	public MGM(DCOPProblemInterface<V, U> problem, Element parameters) {
		this.problem = problem;
		this.maximize = problem.maximize();
		
		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;
		
		String nbrCycles = parameters.getAttributeValue("nbrCycles");
		if(nbrCycles == null)
			this.max_distance = 200;
		else
			this.max_distance = Integer.parseInt(nbrCycles);
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
	public void reset() {
		numberOfVariables = problem.getNbrVars();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<V>>>();
		assignment = new HashMap<String, V>();
		this.finalUtility = this.problem.getZeroUtility();
	}

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
				UtilitySolutionSpace<V, U> sol = problem.getUtility(assignment); 
				finalUtility = sol.getUtility(0);
				
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

		if(type.equals(IMPROVE_MSG_TYPE)) {
			IMPROVE<U> msgCast = (IMPROVE<U>)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());
			if(varInfo.processIMPROVE(msgCast)) {
				sendOK(varInfo);
				varInfo.reset();
				if(varInfo.processStoredOKMessages()) {
					sendIMPROVE(varInfo);
				}
			}
		}

		else if (type.equals(OK_MSG_TYPE)) {
			OK<V> msgCast = (OK<V>)msg;
			VariableInfo<V, U> varInfo = infos.get(msgCast.getReceiver());
			if(varInfo.processOK(msgCast)) {
				sendIMPROVE(varInfo);
				if(varInfo.processStoredIMPROVEMessages()){
					sendOK(varInfo);
					varInfo.reset();
				}
			}
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
		infos = new HashMap<String, VariableInfo<V, U>>(variables.size());
		if (variables.isEmpty()) { // empty agent
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			terminated = true;
			return;
		}
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<V>>>();
		List<? extends UtilitySolutionSpace<V,U>> spaces = problem.getSolutionSpaces();

		for(String variable : variables) {
			VariableInfo<V, U> varInfo = this.createVariableInfo(variable, problem, spaces, maximize);
			assignmentHistoriesMap.put(variable, new ArrayList<CurrentAssignment<V>>());
			varInfo.state = StateValue.OK;
			infos.put(variable, varInfo);
			for(int i = 1; i < varInfo.neighbors.length; i++) {
				String neighbor = varInfo.neighbors[i];
				queue.sendMessage(owners.get(neighbor), new OK<V>(variable, neighbor, varInfo.currentValue));
			}

			if(varInfo.neighbors.length == 1) {
				varInfo.terminated = true;
				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<V>(varInfo.variableID, varInfo.currentValue));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, variable, this.assignmentHistoriesMap.get(variable)));

				if(++this.variables_finished == variables.size()) {
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
		ArrayList<String> msgTypes = new ArrayList<String>(4);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(IMPROVE_MSG_TYPE);
		msgTypes.add(OK_MSG_TYPE);
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
	 * Sending an OK message to all neighbors
	 * 
	 * @author Brammert Ottens, 21 feb. 2011
	 * @param varInfo	the variable that is sending the messages
	 */
	protected void sendOK(VariableInfo<V, U> varInfo) {
		varInfo.termination_counter++;
		
		if(varInfo.termination_counter == max_distance) {
			varInfo.terminated = true;
			queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<V>(varInfo.variableID, varInfo.currentValue));

			if(++variables_finished == infos.size())
				queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));

			return;
		}

		if(varInfo.can_move) {
			boolean changed = !varInfo.currentValue.equals(varInfo.newValue);
			varInfo.currentValueIndex = varInfo.newValue;
			varInfo.currentValue = varInfo.domain[varInfo.newValue];
			varInfo.agent_view[0] = varInfo.currentValue;
			if(convergence && changed){
				assignmentHistoriesMap.get(varInfo.variableID).add(new CurrentAssignment<V>(queue.getCurrentTime(), varInfo.termination_counter, varInfo.currentValue));
			}
		}
		
		for(int i = 1; i < varInfo.neighbors.length; i++) {
			String neighbor = varInfo.neighbors[i];
			queue.sendMessage(owners.get(neighbor), new OK<V>(varInfo.variableID, neighbor, varInfo.currentValue));
		}

	}
	
	/**
	 * Send an improve message to all neighbors
	 * 
	 * @author Brammert Ottens, 21 feb. 2011
	 * @param varInfo	the variable that is sending the messages
	 */
	protected void sendIMPROVE(VariableInfo<V, U> varInfo) {
		varInfo.determinePossibleImprovement();
		
		for(int i = 1; i < varInfo.neighbors.length; i++) {
			String neighbor = varInfo.neighbors[i];
			queue.sendMessage(owners.get(neighbor), new IMPROVE<U>(varInfo.variableID, neighbor, varInfo.improve));
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
	 * @param maximize 		\c true when dealing with a maximization problem, \c false otherwise
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

		/** The current state of the variable */
		protected StateValue state;

		/** The domain of the variable */
		protected V[] domain;

		/** The variable's local problem */
		protected List<UtilitySolutionSpace<V, U>> spaces;

		/** The variable's current value */
		protected V currentValue;
		
		/** The index of the current value */
		protected int currentValueIndex;

		/** The possible new value of this variable, given the context */
		protected int newValue;

		/** \c true when this variable is allowed to change value, and \c false otherwise */
		protected boolean can_move;
		
		/** Counts the number of received messages */
		protected int counter;

		/** The number of neighboring variables */
		protected int number_of_neighbors;

		/** The current utility, give the current context */
		protected AddableConflicts<U> currentUtility;
		
		/** The value with which this variable can improve its utility, given the context */
		protected AddableConflicts<U> improve;

		/** The utility value used for an infeasible utility */
		protected AddableConflicts<U> infeasibleUtility;

		/** The utility value used to denote zero with zero conflicts*/
		protected AddableConflicts<U> zeroConflicts;
		
		/** The utility value used to denote zero */
		protected U zeroUtility;

		/** gives the position of a neighboring variable in the array*/
		protected HashMap<String, Integer> neighborPointer;

		/** List of neighbors */
		protected String[] neighbors;

		/** The current context */
		protected V[] agent_view;

		/** Used to determine when to terminate */
		protected int termination_counter;

		/** List of OK messages to process */
		protected List<OK<V>> okMsgsToProcess;

		/** List of IMPROVE messages to process */
		protected List<IMPROVE<U>> improveMsgsToProcess;

		/** \c true when this variable has terminated */
		protected boolean terminated;

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
			domain = problem.getDomain(variableID);
			this.currentValueIndex = (int)(Math.random()*domain.length);
			this.currentValue = domain[this.currentValueIndex];
			okMsgsToProcess = new ArrayList<OK<V>>();
			improveMsgsToProcess = new ArrayList<IMPROVE<U>>();
			
			this.spaces = new ArrayList<UtilitySolutionSpace<V,U>>();
			for(UtilitySolutionSpace<V,U> space : spaces) {
				String[] variables = space.getVariables();
				for(String variable : variables)
					if(variable.equals(variableID)) {
						this.spaces.add(space);
						continue;
					}
			}
			
			U inf = maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility();
			this.infeasibleUtility = new AddableConflicts<U>(inf, Integer.MAX_VALUE);
			this.zeroUtility = problem.getZeroUtility();
			this.zeroConflicts = new AddableConflicts<U>(zeroUtility, 0);

			this.number_of_neighbors = problem.getNbrNeighbors(variableID);
			neighbors = new String[this.number_of_neighbors + 1];
			neighbors[0] = variableID;
			if(this.number_of_neighbors > 0)
				System.arraycopy(problem.getNeighborVars(variableID).toArray(new String[0]), 0, neighbors, 1, this.number_of_neighbors);
			neighborPointer = new HashMap<String, Integer>(this.number_of_neighbors);
			agent_view = (V[]) Array.newInstance(domain.getClass().getComponentType(), this.number_of_neighbors + 1);
			for(int i = 1; i < this.number_of_neighbors + 1; i++)
				neighborPointer.put(neighbors[i], i);
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
		 * Given the current agent view, calculate the current utility
		 * @author Brammert Ottens, 1 apr. 2011
		 */
		public void calculateCurrentUtility() {
			int conflicts = 0;
			AddableDelayed<U> util = zeroUtility.addDelayed();
			for(int j = 0; j < spaces.size(); j++) {
				UtilitySolutionSpace<V, U> space = spaces.get(j);
				
				U u = space.getUtility(this.neighbors, this.agent_view);
				if(u == this.infeasibleUtility.getUtility())
					conflicts++;
				util.addDelayed(u);
			}

			currentUtility =  new AddableConflicts<U> (util.resolve(), conflicts);
		}
		
		/**
		 * Given the current context and variable assignment,
		 * this method calculates the utility for the local
		 * problem
		 * @author Brammert Ottens, 19 aug 2009
		 * @return	the current local utility
		 */
		protected AddableConflicts<U> calculateUtility() {
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
		 * Given the current context (agent_view), this method
		 * determines the maximal improvement that can be made
		 * 
		 * @author Brammert Ottens, 21 feb. 2011
		 */
		public void determinePossibleImprovement() {
			improve = this.infeasibleUtility;

			for(int i = 0; i < domain.length; i++) {
				if(i != this.currentValueIndex) {
					agent_view[0] = domain[i];
					AddableConflicts<U> util = calculateUtility();

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
			
			if(improve.getUtility() != this.infeasibleUtility.getUtility())
				improve = improve.subtract(currentUtility);

			if(maximize ? improve.compareTo(zeroConflicts) > 0 : improve.compareTo(zeroConflicts) < 0)
				can_move = true;
			else
				can_move = false;

		}
		
		/**
		 * Method to process an OK message
		 * 
		 * @author Brammert Ottens, 21 feb. 2011
		 * @param msg		the message to be processed
		 * @return \c true when the message needs to be stored for later processing, and \c false otherwise
		 */
		public boolean processOK(OK<V> msg) {
			if(!terminated) {
				if(state == StateValue.IMPROVE) {
					this.okMsgsToProcess.add(msg);
					return false;
				}

				counter++;
				setNeighborValue(msg.getSender(), msg.getValue());
				if(counter == number_of_neighbors) {
					calculateCurrentUtility();
					counter = 0;
					assert state == StateValue.OK;
					state = StateValue.IMPROVE;
					return true;
				}
			}

			return false;
		}
		
		/**
		 * Process already received IMPROVE messages
		 * @author Brammert Ottens, 18 apr. 2011
		 * @return \c true when switched to OK state
		 */
		public boolean processStoredIMPROVEMessages() {
			boolean done = false;
			
			while(!done && !improveMsgsToProcess.isEmpty()) {
				IMPROVE<U> msgIMPROVE = improveMsgsToProcess.remove(0);
				done = processIMPROVE(msgIMPROVE);
			}

			assert this.improveMsgsToProcess.isEmpty();
			return done;
		}
		
		/**
		 * Method to process an IMPROVE message
		 * 
		 * @author Brammert Ottens, 21 feb. 2011
		 * @param msg		the message to be processed
		 * @return \c true when the message needs to be stored for later processing, and \c false otherwise
		 */
		public boolean processIMPROVE(IMPROVE<U> msg) {
			if(!terminated) {
				if(state == StateValue.OK) {
					this.improveMsgsToProcess.add(msg);
					return false;
				}

				counter++;
				AddableConflicts<U> improve = msg.getImprove();

				int diff = maximize ? improve.compareTo(this.improve) : this.improve.compareTo(improve);

				if(diff > 0 || (diff == 0 && variableID.compareTo(msg.getSender()) < 0))
					can_move = false;

				if(counter == number_of_neighbors) {
					counter = 0;
					assert state == StateValue.IMPROVE;
					state = StateValue.OK;
					return true;
				}
			}

			return false;
		}
		
		/**
		 * Process already received OK messages
		 * @author Brammert Ottens, 18 apr. 2011
		 * @return \c true when switched to IMPROVE state
		 */
		public boolean processStoredOKMessages() {
			boolean done = false;
			
			while(!done && !okMsgsToProcess.isEmpty()) {
				OK<V> msgOK = okMsgsToProcess.remove(0);
				done = processOK(msgOK);
			}

			assert okMsgsToProcess.isEmpty();
			return done;
		}
		
		/**
		 * reset the agent view
		 * @author Brammert Ottens, 18 apr. 2011
		 */
		public void reset() {
			Arrays.fill(agent_view, null);
			agent_view[0] = currentValue;
		}

		/** @see java.lang.Object#toString() */
		@Override
		public String toString() {
			String str = "";

			str += "---------------------------\n";
			str += "Variable " + this.variableID + "\n";
			str += "State = " + this.state + "\n";
			str += "counter = " + this.counter + "\n";
			str += "termination counter = " + this.termination_counter + "\n";
			str += "neighbors: [";
			int i = 0;
			for(; i < neighbors.length - 1; i++)
				str += neighbors[i] + "=" + agent_view[i] + ", ";
			str += neighbors[i] + "]\n";
			str += " OK messages stored:\n" + this.okMsgsToProcess + "\n";
			str += " IMPROVE messages stored:\n" + this.improveMsgsToProcess + "\n";
			str += "---------------------------\n";

			return str;
		}
	}

}
