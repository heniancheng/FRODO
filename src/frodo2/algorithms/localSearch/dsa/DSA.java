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

/** Classes implementing the DSA algorithm */
package frodo2.algorithms.localSearch.dsa;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporterWithConvergence;
import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableConflicts;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * @author Brammert Ottens, 10 aug 2009
 * 
 * This class implements the Distributed Local Search algorithm DSA as described in
 * "Distributed stochastic search and distributed breakout: properties, comparison and applications to constraint optimization problems in sensor networks" by Zhang et al, 2005.
 * 
 * DSA is a very simple algorithm, where each variable decides whether to changes its value
 * based on the values of its neighbouring variables and some local decision procedure. If the
 * variable value changes this is reported to its neighbours via a message. The decision procedures
 * implemented here are as follows, where delta is the difference between the current local utility
 * and the optimal local utility obtainable when changing one's value, in terms of number of conflicts
 * if either one is infeasible, or in terms of utility if both are feasible. 
 * v is the value that gives this optimal utility and 0 <= p <= 1 a probability
 * 
 * 				delta > 0		conflict, delta == 0		no conflict, delta == 0	
 *  DSA-A : 	v with p		   -						   -
 *  DSA-B : 	v with p		v with p					   -
 * 	DSA-C : 	v with p		v with p					v with p
 * 	DSA-D : 	   v			v with p					   -
 * 	DSA-E : 	   v			v with p					v with p
 * 
 * @param <Val> type used for variable values
 * @param <U> 	type used for utility values
 * @todo Implement strategies B and D. 
 */
public class DSA < Val extends Addable<Val>, U extends Addable<U> >
implements StatsReporterWithConvergence<Val> {

	// The message types

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of the message used to report a value to neighboring variables */
	public static String VALUE_MSG_TYPE = "VALUE";

	/** The type of the message containing the assignment history */
	public static final String CONV_STATS_MSG_TYPE = "DSAConvStatsMsg";

	/** The type of the output messages containing the optimal assignment to a variable */
	public static final String OUTPUT_MSG_TYPE = "OutputMessageDSA";

	/** When \c true, every variable writes log information to a log file */
	protected final boolean LOG = false;
	
	/** A list of buffered writers used to log information during debugging*/
	protected HashMap<String, BufferedWriter> loggers;
	
	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;

	/** Whether the listener should record the assignment history or not */
	protected final boolean convergence;

	/** The global assignment */
	protected Map< String, Val > assignment;

	/** For each variable its assignment history */
	protected HashMap<String, ArrayList<CurrentAssignment<Val>>> assignmentHistoriesMap;

	/** The global utility of the final variable assignment */
	protected U finalUtility; 

	// Information on the problem

	/** The agent's problem */
	protected DCOPProblemInterface<Val, U> problem;

	/** The probability with which a new value is chosen */
	protected double p;

	/** The number of variables owned by this agent */
	protected int numberOfVariables;

	// Agent specific information

	/** The message queue*/
	protected Queue queue;

	/** For each variable a container with information */
	protected HashMap<String, VariableInfo<Val, U>> infos;

	/** Strategy used to determine a new assignment */
	protected DetermineAssignment<Val, U> decisionStrategy;

	/** \c true when the module has been initialized, and false otherwise*/
	protected boolean started;

	/** For each variable the agent that owns it. */
	protected Map<String, String> owners;

	/** The number of synchronous cycles before termination (except for isolated variables) */
	protected int nbrCycles;

	/** Counts the number of variables that have hit the desired cycle count */
	protected int variableFinishedCounter;

	/** \c true when this agent has sent the agent finished message during initialization */
	protected boolean terminated;

	/** Constructor for the stats gatherer mode
	 * @param parameters 	the parameters of the module
	 * @param problem 		the overall problem
	 */
	public DSA(Element parameters, DCOPProblemInterface<Val, U> problem) {
		assignment = new HashMap< String, Val> ();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<Val>>>();
		this.problem = problem;
		numberOfVariables = problem.getNbrVars();
		convergence = false;
	}

	/**
	 * Constructor
	 * @param problem		description of the local problem
	 * @param parameters	parameters of the module
	 * @throws ClassNotFoundException 	if the module parameters specify an unknown class for utility values
	 */
	public DSA(DCOPProblemInterface<Val, U> problem, Element parameters) throws ClassNotFoundException {
		this.problem = problem;

		String p = parameters.getAttributeValue("probability");
		if(p != null )
			this.p = Double.parseDouble(p);

		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;

		String decisionStrategy = parameters.getAttributeValue("strategy");
		if(decisionStrategy == null)
			decisionStrategy = DSA.A.class.getName();

		String nbrCycles = parameters.getAttributeValue("nbrCycles");
		if(nbrCycles == null)
			this.nbrCycles = 199;
		else
			this.nbrCycles = Integer.parseInt(nbrCycles) - 1;

		setDecisionStrategy(decisionStrategy);

	}
	
	/**
	 * Constructor
	 * @param problem		description of the local problem
	 * @param parameters	parameters of the module
	 * @param nbrCycles		the number of cycles that the algorithm should run
	 */
	public DSA(DCOPProblemInterface<Val, U> problem, Element parameters, int nbrCycles){
		this.problem = problem;

		String p = parameters.getAttributeValue("probability");
		if(p != null )
			this.p = Double.parseDouble(p);

		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;

		String decisionStrategy = parameters.getAttributeValue("strategy");
		if(decisionStrategy == null)
			decisionStrategy = DSA.A.class.getName();
		
		this.nbrCycles = nbrCycles;
		
		setDecisionStrategy(decisionStrategy);
	}
	
	/**
	 * Method used to set the decision strategy
	 * @author Brammert Ottens, 23 mrt. 2011
	 * @param decisionStrategy the decision strategy to be used
	 */
	@SuppressWarnings("unchecked")
	protected void setDecisionStrategy(String decisionStrategy) {
		try {
			Class< DetermineAssignment<Val, U> > strategyClass = (Class< DetermineAssignment<Val, U> >) Class.forName(decisionStrategy);
			Class<?> parTypes[] = new Class[1];
			parTypes[0] = DSA.class;
			Constructor< DetermineAssignment<Val, U> > constructor = strategyClass.getConstructor(parTypes);
			Object[] args = new Object[1];
			args[0] = this;
			this.decisionStrategy = constructor.newInstance(args);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	/**
	 * Initializes the agent's variables
	 * 
	 * @author Brammert Ottens, 10 aug 2009
	 */
	protected void init() {
		String[] myVars = problem.getMyVars().toArray(new String[0]);
		owners = problem.getOwners();
		numberOfVariables = myVars.length;
		if (this.numberOfVariables == 0) { // empty agent
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			return;
		}
		infos = new HashMap<String, VariableInfo<Val,U>>();
		Map< String, ? extends Collection<String> >  neighbourhoods = problem.getNeighborhoods();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<Val>>>();

		for(int i = 0; i < numberOfVariables; i++) {
			String var = myVars[i];
			Collection<String> neighbours = neighbourhoods.get(var);

			VariableInfo<Val, U> varInfo = createVariableInfo(var, problem.getDomain(var), neighbours.toArray(new String[0]), problem);
			
			infos.put(var, varInfo);
			assignmentHistoriesMap.put(var, new ArrayList<CurrentAssignment<Val>>());
			if(convergence)
				assignmentHistoriesMap.get(var).add(new CurrentAssignment<Val>(queue.getCurrentTime(), varInfo.cycleCounter, varInfo.currentValue));
			
			if(neighbours.size() == 0) {
				Val value = varInfo.currentValue;
				// check if we want to measure convergence
				if(convergence) {
					ArrayList<CurrentAssignment<Val>> history = assignmentHistoriesMap.get(var);
					history.add(new CurrentAssignment<Val>(queue.getCurrentTime(), varInfo.cycleCounter, value));
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(CONV_STATS_MSG_TYPE, var, history));
				}

				queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U>(var, varInfo.currentValue, varInfo.currentUtility));
				if(++variableFinishedCounter == numberOfVariables) {
					queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
					terminated = true;
				}
			} else {
				for(String neighbour : neighbours) {
					VALUEmsg<Val> msg = new VALUEmsg<Val>(var, neighbour, varInfo.currentValue);
					queue.sendMessage(owners.get(neighbour), msg);
					varInfo.lastSentMsgs.put(neighbour, msg);
				}
			}
		}

		started = true;
	}

	/**
	 * method used to create a VariableInfo object
	 * 
	 * @author Brammert Ottens, 16 feb. 2011
	 * @param variableID	the ID of the variable
	 * @param domain		the domain of the variable
	 * @param neighbours	the neighbors of the variable
	 * @param problem		the local problem definition
	 * @return	a VariableInfo object for \c variableID
	 */
	protected VariableInfo<Val, U> createVariableInfo(String variableID, Val[] domain, String[] neighbours, DCOPProblemInterface<Val, U> problem) {
		return new VariableInfo<Val, U>(variableID, domain, neighbours, problem, this);
	}

	/** @see StatsReporterWithConvergence#reset() */
	public void reset() {
		/// @todo Auto-generated method stub
	}

	/** 
	 * @see frodo2.algorithms.StatsReporterWithConvergence#getAssignmentHistories()
	 */
	public HashMap<String, ArrayList<CurrentAssignment<Val>>> getAssignmentHistories() {
		return assignmentHistoriesMap;
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#getStatsFromQueue(frodo2.communication.Queue)
	 */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OUTPUT_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(CONV_STATS_MSG_TYPE, this);
	}

	/**
	 * Getter method
	 * @author Brammert Ottens, 12 aug 2009
	 * @param variable	the variable for which the value is requested
	 * @return	the value of the variable
	 */
	public Val getCurrentValue(String variable) {
		return infos.get(variable).currentValue;
	}

	/**
	 * Getter method
	 * @author Brammert Ottens, 12 aug 2009
	 * @param variable	the variable for which the utility is requested
	 * @return	the current utility 
	 */
	public U getCurrentUtility(String variable) {
		return infos.get(variable).currentUtility;
	}

	/**
	 * Getter method
	 * @author Brammert Ottens, 14 aug 2009
	 * @return the final variable assignments 
	 */
	public Map<String, Val> getFinalAssignments() {
		return assignment;
	}

	/**
	 * Getter method
	 * @author Brammert Ottens, 14 aug 2009
	 * @return the global utility of the final variable assignment
	 */
	public U getFinalUtility() {
		return finalUtility;
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
		ArrayList<String> msgTypes = new ArrayList<String>(4);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(VALUE_MSG_TYPE);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		msgTypes.add(AgentInterface.STOP_AGENT);
		return msgTypes;
	}

	/** 
	 * @see StatsReporterWithConvergence#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		String type = msg.getType();

		if (type.equals(OUTPUT_MSG_TYPE)) { // in stats gatherer mode, the message containing information about an agent's assignments
			AssignmentMessage<Val, U> msgCast = (AssignmentMessage<Val, U>) msg;

			String variable = msgCast.getVariable();
			Val value = msgCast.getValue();
			U utility = msgCast.getUtility();
			assignment.put(variable, value);
			if (!silent) 
				System.out.println("var `" + variable + "' = " + value + ", \t local utility = " + utility);

			if(assignment.size() == numberOfVariables) {
				UtilitySolutionSpace<Val, U> sol = problem.getUtility(assignment); 
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
			StatsReporterWithConvergence.ConvStatMessage<Val> msgCast = (StatsReporterWithConvergence.ConvStatMessage<Val>)msg;
			assignmentHistoriesMap.put(msgCast.getVar(), msgCast.getAssignmentHistory());

			return;
		}

		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}

		if(!this.started)
			init();

		if (type.equals(VALUE_MSG_TYPE)) {
			VALUEmsg<Val> msgCast = (VALUEmsg<Val>)msg;
			String var = msgCast.getReceiver();
			VariableInfo<Val, U> varInfo = infos.get(var);
			if(varInfo.setContext(msgCast)) {
				varInfo.resetReportedNeighbours();
				if(++varInfo.cycleCounter != nbrCycles) {
					Val newValue = null;
					if(decisionStrategy.determineAssignment(var)) {
						newValue = varInfo.getCurrentValue();
						if(convergence)
							assignmentHistoriesMap.get(var).add(new CurrentAssignment<Val> (queue.getCurrentTime(), varInfo.cycleCounter, newValue));
					}
					
					for(int i = 0; i < varInfo.neighbourPointers.size(); i++) {
						String neighbour = varInfo.neighbours[i];

						if (newValue == null) // the value hasn't changed
							queue.sendMessage(owners.get(neighbour), varInfo.lastSentMsgs.get(neighbour)); /// @todo Reusing messages is no longer useful

						else { // the value has changed

							VALUEmsg<Val> msg2 = new VALUEmsg<Val> (var, neighbour, newValue);
							queue.sendMessage(owners.get(neighbour), msg2);
							varInfo.lastSentMsgs.put(neighbour, msg2);
						}
					}
					varInfo.processPendingMessages();
				} else {
					Val value = varInfo.currentValue;

					// check if we want to measure convergence
					if(convergence) {
						ArrayList<CurrentAssignment<Val>> history = assignmentHistoriesMap.get(var);
						history.add(new CurrentAssignment<Val>(queue.getCurrentTime(), varInfo.cycleCounter, value));
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(CONV_STATS_MSG_TYPE, var, history));
					}

					queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U>(var, varInfo.currentValue, varInfo.currentUtility));
					if(++variableFinishedCounter == numberOfVariables) {
						queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
					}
				}
			}
		} else if(type.equals(AgentInterface.STOP_AGENT)) {

			if(!terminated) {
				for(DSA.VariableInfo<Val, U> varInfo : infos.values()) {
					Val value = varInfo.currentValue;
					String var = varInfo.variableID;
				
					// check if we want to measure convergence
					if(convergence) {
						ArrayList<CurrentAssignment<Val>> history = assignmentHistoriesMap.get(var);
						history.add(new CurrentAssignment<Val>(queue.getCurrentTime(), varInfo.cycleCounter, value));
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(CONV_STATS_MSG_TYPE, var, history));
					}

					queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val, U>(var, varInfo.currentValue, varInfo.currentUtility));
				}

				queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
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
	 * Container class for information needed for each variable
	 * @author Brammert Ottens, 10 aug 2009
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static class VariableInfo < Val extends Addable<Val>, U extends Addable<U> > {

		/** The variable's ID */
		protected String variableID;

		/** The variable's domain*/
		private Val[] domain;

		/** The size of the domain */
		private int domainSize;

		/** The values of its neighbours, i.e. its context. For efficiency reasons there
		 * should also be an entry for this variable. */
		protected Map<String, Val> context;

		/** The ID's of its neighbours. For efficiency reasons there should
		 * also be a slot for this variable */
		protected String[] neighbours;

		/** A map that maps neighbours to positions in an array */
		protected HashMap<String, Integer> neighbourPointers;

		/** The number of neighbours this variable has */
		protected int numberOfNeighbours;

		/** Its current value */
		protected Val currentValue;

		/** Its current utility */
		protected U currentUtility;

		/** The variable's local problem */
		private List< ? extends UtilitySolutionSpace<Val, U> > spaces;

		/** The set of neighbours that have reported a value */
		protected Set<String> reportedNeighbours;

		/** the zero utility */
		protected U zero;

		/** \c true when the problem is a maximization problem and \c false otherwise */
		protected final boolean maximize;

		/** Contains the current cycle the variable is in */
		protected int cycleCounter;

		/** Received messages that one cannot yet process */
		protected HashMap<String, LinkedList<VALUEmsg<Val>>> pendingMessages;

		/** For each neighbor of this variable, the last message sent to it */
		protected HashMap< String, VALUEmsg<Val> > lastSentMsgs = new HashMap< String, VALUEmsg<Val> > ();

		/** The utility value used for infeasible solutions */
		private U infeasibleUtil;

		/** pointer to the listener that owns this object */
		protected DSA<Val, U> listener;

		/**
		 * Constructor
		 * @param variableID	The ID of the variable
		 * @param domain		The domain of the variable
		 * @param neighbours	The neighbouring variables
		 * @param problem		The local problem
		 * @param listener		The listener that owns this object
		 */
		public VariableInfo(String variableID, Val[] domain, String[] neighbours, DCOPProblemInterface<Val, U> problem, DSA<Val, U> listener) {
			this.variableID = variableID;
			this.domain = domain;
			this.domainSize = domain.length;
			maximize = problem.maximize();
			this.listener = listener;
			if(maximize)
				this.infeasibleUtil = problem.getMinInfUtility();
			else
				this.infeasibleUtil = problem.getPlusInfUtility();
			zero = problem.getZeroUtility();

			numberOfNeighbours = neighbours.length;
			this.context = new HashMap<String, Val>(numberOfNeighbours + 1);
			this.neighbours = new String[numberOfNeighbours + 1];
			System.arraycopy(neighbours, 0, this.neighbours, 0, numberOfNeighbours);
			neighbourPointers = new HashMap<String, Integer>(numberOfNeighbours);
			reportedNeighbours = new HashSet<String>(numberOfNeighbours);
			this.neighbours[numberOfNeighbours] = variableID;
			pendingMessages = new HashMap<String, LinkedList<VALUEmsg<Val>>>(numberOfNeighbours);
			for(int i = 0; i < numberOfNeighbours; i++) {
				String neighbour = neighbours[i];
				neighbourPointers.put(neighbour, i);
				pendingMessages.put(neighbour, new LinkedList<VALUEmsg<Val>>());
			}

			/// @todo All spaces should be joined to make use of the power of the local solver, but then we can't count constraint violations anymore... 
			spaces = problem.getSolutionSpaces(variableID, false);

			if(numberOfNeighbours == 0) {
				VarAssignment<Val, U> ass = bestAssignment();
				currentValue = ass.value;
				currentUtility = ass.util;
			} else {
				currentValue = domain[(int)(Math.random()*domainSize)];
				if(maximize)
					currentUtility = zero.getMinInfinity();
				else
					currentUtility = zero.getPlusInfinity();
			}
			context.put(variableID, currentValue);
		}

		/**
		 * Method used to determine the best assignment to the variable
		 * given the current context
		 * 
		 * @author Brammert Ottens, 10 aug 2009
		 * @return the currently best variable value
		 */
		public VarAssignment<Val, U> bestAssignment() {
			AddableConflicts<U> max = new AddableConflicts<U> (this.infeasibleUtil, Integer.MAX_VALUE);
			Val value = domain[0];

			for(int i = 0; i < domainSize; i++) {
				Val v = domain[i];
				context.put(variableID, v);
				AddableConflicts<U> util = calculateUtility();

				if(maximize ? max.compareTo(util) < 0 : max.compareTo(util) > 0) {
					max = util;
					value = v;
				}
			}

			context.put(variableID, currentValue);
			return new VarAssignment<Val, U> (value, max.getUtility());
		}

		/**
		 * Sets the current value to the value in ass.
		 * @author Brammert Ottens, 10 aug 2009
		 * @param ass	The new assignment
		 * @return returns \c false if the assignment does not change, and \c true otherwise
		 */
		public boolean setCurrentValue(VarAssignment<Val, U> ass) {
			currentUtility = ass.util;
			if(currentValue.equals(ass.value))
				return false;

			currentValue = ass.value;
			context.put(variableID, currentValue);
			return true;
		}

		/**
		 * Method to update the context of this variable
		 * @author Brammert Ottens, 10 aug 2009
		 * @param msg the message that needs to be processed
		 * @return \c true when all neighbours have reported their states
		 */
		public boolean setContext(VALUEmsg<Val> msg ) {
			String neighbour = msg.getSender();
			Val value = msg.getValue();
			if(reportedNeighbours.add(neighbour)) {
				context.put(neighbour, value);
				if(reportedNeighbours.size() == numberOfNeighbours) {
					currentUtility = calculateUtility().getUtility();
					return true;
				}

				return false;
			} else {
				pendingMessages.get(neighbour).addLast(msg);
				assert pendingMessages.get(neighbour).size() < 2;
				return false;
			}
		}

		/**
		 * Given the current context and variable assignment,
		 * this method calculates the utility for the local
		 * problem
		 * @author Brammert Ottens, 19 aug 2009
		 * @return	the current local utility
		 */
		@SuppressWarnings("unchecked")
		protected AddableConflicts<U> calculateUtility() {
			
			int conflicts = 0;
			AddableDelayed<U> util = zero.addDelayed();
			for(int j = 0; j < spaces.size(); j++) {
				UtilitySolutionSpace<Val, U> space = spaces.get(j);
				String[] vars = space.getVariables();
				int nbrVars = vars.length;
				Val[] values = (Val[]) Array.newInstance(this.domain.getClass().getComponentType(), nbrVars);
				for(int k = 0; k < nbrVars; k++) {
					values[k] = context.get(vars[k]);
				}
				U u = space.getUtility(vars, values);
				if(u == this.infeasibleUtil)
					conflicts++;
				util.addDelayed(u);
			}

			return new AddableConflicts<U> (util.resolve(), conflicts);
		}

		/**
		 * Resets the reported neighbours counter
		 * @author Brammert Ottens, 14 aug 2009
		 */
		public void resetReportedNeighbours() {
			reportedNeighbours.clear();
		}

		/**
		 * Method to process the already received,
		 * but not yet processed messages. There
		 * should be at most one message per
		 * neighbour
		 * @author Brammert Ottens, 19 aug 2009
		 */
		public void processPendingMessages() {
			for(Entry<String, LinkedList<VALUEmsg<Val>>> e : pendingMessages.entrySet()) {
				LinkedList<VALUEmsg<Val>> list = e.getValue();
				if(! list.isEmpty()) {
					VALUEmsg<Val> v = list.poll();
					listener.notifyIn(v);
				}
			}
		}

		/**
		 * Getter method
		 * @author Brammert Ottens, 12 aug 2009
		 * @return	the current value of this variable
		 */
		public Val getCurrentValue() {
			return currentValue;
		}
		
		/** @see java.lang.Object#toString() */
		public String toString() {
			String str = "Variable " + this.variableID + "\n";
			str += "-----------------------------\n";
			str += "cycle = " + this.cycleCounter + "\n";
			str += "Current value = " + this.currentValue + "\n";
			str += "Current context = " + this.context + "\n";
			str += "Reported neighbors = " + this.reportedNeighbours + "\n";
			str += "neighbors = " + this.neighbourPointers + "\n";
			return str;
		}
		
	}

	/**
	 * Interface used to implement the different decision strategies of
	 * DSA.
	 * @author Brammert Ottens, 10 aug 2009
	 * 
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static interface DetermineAssignment < Val extends Addable<Val>, U extends Addable<U> > {

		/**
		 * Method that is used to determine a variable's
		 * new assignment based on the neighbours' assignments
		 * and the local problem
		 * @author Brammert Ottens, 10 aug 2009
		 * @param var The ID of the variable for which the assignment must be determined
		 * @return	whether the current assignment has changed
		 */
		public boolean determineAssignment(String var);
	}

	/**
	 * 
	 * @author Brammert Ottens, 10 aug 2009
	 *
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static class A < Val extends Addable<Val>, U extends Addable<U> > 
	implements DetermineAssignment<Val, U> {

		/** A link to the containing class */
		protected DSA<Val, U> superClass;

		/** Constructor 
		 * @param superClass A pointer to the super class*/
		public A(DSA<Val, U> superClass) {
			this.superClass = superClass;
		}

		/** 
		 * @see frodo2.algorithms.localSearch.dsa.DSA.DetermineAssignment#determineAssignment(String var)
		 */
		public boolean determineAssignment(String var) {

			VariableInfo<Val, U> varInfo = superClass.infos.get(var);

			VarAssignment<Val, U> ass = varInfo.bestAssignment();

			int comparison = ass.util.compareTo(varInfo.currentUtility);
			if(!varInfo.maximize) {
				comparison = -comparison;
			}

			if (comparison > 0) {
				if(Math.random() <= superClass.p) {
					return varInfo.setCurrentValue(ass);
				}
			}

			return false;
		}
	}

	/**
	 * 
	 * @author Brammert Ottens, 10 aug 2009
	 *
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static class C < Val extends Addable<Val>, U extends Addable<U> > 
	implements DetermineAssignment<Val, U> {

		/** A link to the containing class */
		private DSA<Val, U> superClass;

		/** Constructor 
		 * @param superClass A pointer to the super class*/
		public C(DSA<Val, U> superClass) {
			this.superClass = superClass;
		}

		/** 
		 * @see frodo2.algorithms.localSearch.dsa.DSA.DetermineAssignment#determineAssignment(String var)
		 */
		public boolean determineAssignment(String var) {

			VariableInfo<Val, U> varInfo = superClass.infos.get(var);

			VarAssignment<Val, U> ass = varInfo.bestAssignment();

			int comparison = ass.util.compareTo(varInfo.currentUtility);
			if(!varInfo.maximize) {
				comparison = -comparison;
			}

			if (comparison >= 0) {
				if(Math.random() <= superClass.p)
					return varInfo.setCurrentValue(ass);
			}

			return false;
		}
	}

	/**
	 * 
	 * @author Brammert Ottens, 10 aug 2009
	 *
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static class E < Val extends Addable<Val>, U extends Addable<U> > 
	implements DetermineAssignment<Val, U> {

		/** A link to the containing class */
		private DSA<Val, U> superClass;

		/** Constructor 
		 * @param superClass A pointer to the super class*/
		public E(DSA<Val, U> superClass) {
			this.superClass = superClass;
		}

		/** 
		 * @see frodo2.algorithms.localSearch.dsa.DSA.DetermineAssignment#determineAssignment(String var)
		 */
		public boolean determineAssignment(String var) {
			VariableInfo<Val, U> varInfo = superClass.infos.get(var);

			VarAssignment<Val, U> ass = varInfo.bestAssignment();
			int comparison = ass.util.compareTo(varInfo.currentUtility);
			if(!varInfo.maximize) {
				comparison = -comparison;
			}

			if ( comparison > 0) {
				return varInfo.setCurrentValue(ass);
			} else if(comparison == 0 && Math.random() <= superClass.p) {
				return varInfo.setCurrentValue(ass);
			}

			return false;
		}
	}

	/** A message holding an assignment to a variable
	 * @param <Val> type used for variable values
	 * @param <U>	type used of utility values
	 */
	public static class AssignmentMessage < Val extends Addable<Val>, U extends Addable<U> >
	extends MessageWith3Payloads <String, Val, U> {

		/** Empty constructor used for externalization */
		public AssignmentMessage () { }

		/** Constructor 
		 * @param var 		the variable
		 * @param val 		the value assigned to the variable \a var
		 * @param utility	the local utility of this variable
		 */
		public AssignmentMessage (String var, Val val, U utility) {
			super (OUTPUT_MSG_TYPE, var, val, utility);
		}

		/** @return the variable */
		public String getVariable () {
			return this.getPayload1();
		}

		/** @return the value */
		public Val getValue () {
			return this.getPayload2();
		}

		/** @return the utility */
		public U getUtility () {
			return this.getPayload3();
		}
	}

	/**
	 * Container for a value assignment and its utility
	 * @author Brammert Ottens, 10 aug 2009
	 * 
	 * @param <Val> type used for variable values
	 * @param <U> 	type used for utility values
	 */
	public static class VarAssignment < Val extends Addable<Val>, U extends Addable<U> > {

		/** The value of the variable assignment */
		Val value;

		/** The utility that corresponds to this assignment */
		U util;

		/**
		 * Constructor
		 * @param value		A variable assignment
		 * @param util		The corresponding utility
		 */
		public VarAssignment(Val value, U util) {
			this.value = value;
			this.util = util;
		}
	}
	
	/**
	 * @see StatsReporterWithConvergence#getCurrentSolution()
	 */
	public Map<String, Val> getCurrentSolution() {
		/// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}

}
