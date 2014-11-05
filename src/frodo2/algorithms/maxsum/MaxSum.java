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

package frodo2.algorithms.maxsum;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporterWithConvergence;
import frodo2.algorithms.varOrdering.factorgraph.FactorGraphGen;
import frodo2.algorithms.varOrdering.factorgraph.FunctionNode;
import frodo2.algorithms.varOrdering.factorgraph.VariableNode;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;

/** The Max-Sum Algorithm
 * 
 * Max-Sum Implementation mostly based on "Decentralised Coordination of Low-Power Embedded
 * Devices Using the Max-Sum Algorithm" by 
 * A. Farinelli, A. Rogers, A. Petcu, N. R. Jennings
 * 
 * Each function node is simulated by the agent owning the first variable in its scope. 
 * 
 * @author Thomas Leaute, remotely based on a preliminary contribution by Sokratis Vavilis and George Vouros (AI-Lab of the University of the Aegean)
 * 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values in stats gatherer mode
 */
public class MaxSum < V extends Addable<V>, U extends Addable<U> > implements StatsReporterWithConvergence<V> {
	
	/** The type of the messages received from function nodes of the graph */
	public static final String FUNCTION_MSG_TYPE = FunctionMsg.FUNCTION_MSG_TYPE;

	/** The type of the messages received from variable nodes of the graph */
	public static final String VARIABLE_MSG_TYPE = VariableMsg.VARIABLE_MSG_TYPE;

	/** The type of the messages containing the assignment history */
	private static final String CONV_STATS_MSG_TYPE = "MaxSumConvStatsMsg";

	/** The type of the messages containing the final assignment to a variable */
	private static final String SOLUTION_MSG_TYPE = "MaxSumSolMsg";
	
	/** Information about an internal variable */
	private class VarInfo extends VariableNode<V, U> {
		
		/** The last marginal utility received from each function node */     
		private HashMap< String, UtilitySolutionSpace<V, U> > lastMsgsIn = new HashMap< String, UtilitySolutionSpace<V, U> > ();
		
		/** The remaining number of iterations for this node */
		private int nbrIter;
		
		/** The current optimal value for this variable */
		private V optVal;
		
		/** Constructor
		 * @param varName 		The name of this variable
		 * @param agent 		The agent controlling this variable node
		 * @param functions 	The list of neighboring function nodes
		 */
		private VarInfo (String varName, String agent, ArrayList< FunctionNode<V, U> > functions) {
			super(varName, agent, problem.getDomain(varName));

			if (agent.equals(problem.getAgent())) { // I control this variable node
				
				this.nbrIter = maxNbrIter;
				
				// Pick the first possible value for the variable
				this.optVal = this.dom[0];
				
				// Initialize the last messages received from and sent to the neighboring function nodes
				for (FunctionNode<V, U> funct : functions) {
					this.addFunction(funct);
					this.lastMsgsIn.put(funct.getName(), zeroSpace(this.varName, this.dom));
				}
			}
		}
	}
	
	/** Returns a single-variable space full of zeros
	 * @param varName 	the name of the variable
	 * @param dom 		the variable domain
	 * @return the space
	 */
	@SuppressWarnings("unchecked")
	private Hypercube<V, U> zeroSpace (String varName, V[] dom) {
		
		U[] utils = (U[]) Array.newInstance(this.zero.getClass(), dom.length);
		Arrays.fill(utils, this.zero);
		V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
		doms[0] = dom;
		
		return new Hypercube<V, U> (new String[] { varName }, doms, utils, infeasibleUtil);
	}
	
	/** Returns a scaled random space
	 * @param varName 	the name of the (only) variable
	 * @param dom 		the domain of the variable
	 * @return a scaled random space over the variable
	 */
	@SuppressWarnings("unchecked")
	private Hypercube<V, U> scaledRandSpace (String varName, V[] dom) {
		
		// Randomly fill in the utilities
		final int domSize = dom.length;
		U[] utils = (U[]) Array.newInstance(this.zero.getClass(), domSize);
		AddableDelayed<U> sum = this.zero.addDelayed();
		Random rand = new Random ();
		for (int i = 0; i < domSize; i++) 
			sum.addDelayed(utils[i] = this.zero.fromString(Integer.toString(rand.nextInt(100))));
		
		// Rescale the random utilities so that they sum up to 0
		U scalar = sum.resolve().divide(this.zero.fromString(Integer.toString(domSize)));
		for (int i = 0; i < domSize; i++) 
			utils[i] = utils[i].subtract(scalar);

		V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
		doms[0] = dom;
		
		return new Hypercube<V, U> (new String[] { varName }, doms, utils, infeasibleUtil);
	}
	
	/** For each internal variable, its VarInfo */
	private HashMap<String, VarInfo> varInfos;
	
	/** Information about each neighboring function node */
	private class FunctionInfo extends FunctionNode<V, U> {
		
		/** The last marginal utility received from each variable node */     
		private HashMap< String, UtilitySolutionSpace<V, U> > lastMsgsIn = new HashMap< String, UtilitySolutionSpace<V, U> > ();
		
		/** The remaining number of iterations for this node */
		private int nbrIter;
		
		/** Constructor
		 * @param name 		The name of the constraint
		 * @param space 	The constraint
		 * @param agent 	The agent responsible for simulating this function node
		 */
		private FunctionInfo (String name, UtilitySolutionSpace<V, U> space, String agent) {
			super(name, space, agent);
			
			this.nbrIter = maxNbrIter;
			
			if (space != null) 
				for (String var : space.getVariables()) 
					this.addVariable(var, problem.getDomain(var));
		}
		
		/** Adds a variable to this FunctionInfo
		 * @param varName 	the variable name
		 * @param dom 		the variable domain
		 */
		private void addVariable (String varName, V[] dom) {
			
			// Initialize the last messages received from and sent to this variable node
			this.lastMsgsIn.put(varName, zeroSpace(varName, dom));
		}
	}
	
	/** For each constraint in the agent's subproblem, its FunctionInfo */
	private HashMap<String, FunctionInfo> functionInfos;

	/** The algorithm will terminate when ALL function nodes have gone through that many iterations */
	private final int maxNbrIter;

	/** This module's queue */
	private Queue queue;

	/** The problem */
	private DCOPProblemInterface<V, U> problem;
	
	/** Whether the stats gatherer should display the solution found */
	private boolean silent = false;

	/** Whether the module has already started the algorithm */
	private boolean started = false;

	/** The 0 cost */
	private U zero;

	/** The solution */
	private HashMap<String, V> solution;

	/** The optimal cost */
	private U optCost;
	
	/** Whether to maximize utility or minimize cost */
	private final boolean maximize;

	/** The infeasible utility */
	private final U infeasibleUtil;
	
	/** Whether to initialize the algorithm with random messages, or with messages full of zeros */
	private final boolean randomInit;

	/** Whether the listener should record the assignment history or not */
	private final boolean convergence;

	/** For each variable its assignment history */
	private final HashMap< String, ArrayList< CurrentAssignment<V> > > assignmentHistoriesMap;
	
	/** For each recipient (variable or function), the last message received and waiting to be processed */
	private HashMap<String, Message> pendingMsgs = new HashMap<String, Message> ();

	/** Constructor
	 * @param problem       this agent's problem
	 * @param parameters    the parameters for this module
	 */
	public MaxSum (DCOPProblemInterface<V, U> problem, Element parameters) {
		
		this.problem = (DCOPProblemInterface<V, U>) problem;
		this.maximize = problem.maximize();
		this.infeasibleUtil = (this.maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility());
		this.zero = this.problem.getZeroUtility();

		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;
		this.assignmentHistoriesMap = (this.convergence ? new HashMap< String, ArrayList< CurrentAssignment<V> > >() : null);

		String maxNbrIter = parameters.getAttributeValue("maxNbrIter");
		if(maxNbrIter == null)
			this.maxNbrIter = 200;
		else
			this.maxNbrIter = Integer.parseInt(maxNbrIter);
		
		String randomInitStr = parameters.getAttributeValue("randomInit");
		if (randomInitStr != null) 
			this.randomInit = Boolean.parseBoolean(randomInitStr);
		else 
			this.randomInit = true;
	}

	/** The constructor called in "statistics gatherer" mode
	 * @param parameters    the description of what statistics should be reported (currently unused)
	 * @param problem       the overall problem
	 */
	public MaxSum (Element parameters, DCOPProblemInterface<V, U> problem)  {
		this.problem = problem;

		this.solution = new HashMap<String, V> ();
		this.optCost = problem.getZeroUtility();
		this.started = true;
		this.maximize = this.problem.maximize();
		this.infeasibleUtil = (this.maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility());
		this.maxNbrIter = 0;
		this.randomInit = false;
		this.convergence = false;
		this.assignmentHistoriesMap = new HashMap< String, ArrayList< CurrentAssignment<V> > > ();
	}

	/** Parses the problem */
	private void start () {
		
		this.started = true;
		
		// Start the algorithm by having each of my variable nodes send a fake message to each of its neighboring function nodes
		for (VarInfo varInfo : this.varInfos.values()) {
			
			// Skip the variable nodes I do not control
			if (varInfo.optVal == null) 
				continue;
			
			// Check if this variable is unconstrained
			if (varInfo.getFunctions().isEmpty()) {
				
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageWith2Payloads<String, V> (SOLUTION_MSG_TYPE, varInfo.getVarName(), varInfo.optVal));

				if(convergence) {
					ArrayList< CurrentAssignment<V> > history = this.assignmentHistoriesMap.get(varInfo.getVarName());
					history.add(new CurrentAssignment<V>(queue.getCurrentTime(), 0, varInfo.optVal));
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, varInfo.getVarName(), history));
				}
				
				varInfo.nbrIter = 0;
				this.checkForTermination();
			}
				
			else { // constrained variable
				for (FunctionNode<V, U> function : varInfo.getFunctions()) {
					UtilitySolutionSpace<V, U> space = (this.randomInit ? 
							this.scaledRandSpace(varInfo.getVarName(), varInfo.getDom()) : 
								zeroSpace (varInfo.getVarName(), varInfo.getDom()));
					space.setName("start");
					this.queue.sendMessage(function.getAgent(), new VariableMsg<V, U> (function.getName(), space));
				}
			}
		}
		
		// Process pending messages
		for (Message msg : this.pendingMsgs.values()) 
			this.notifyIn(msg);
		this.pendingMsgs.clear();
	}

	/** @see StatsReporterWithConvergence#reset() */
	public void reset() {
		/// @todo To be implemented
	}

	/** @see StatsReporterWithConvergence#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (4);
		types.add(FactorGraphGen.OUTPUT_MSG_TYPE);
		types.add(FUNCTION_MSG_TYPE);
		types.add(VARIABLE_MSG_TYPE);
		types.add(AgentInterface.ALL_AGENTS_IDLE);
		return types;
	}

	/** @see StatsReporterWithConvergence#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(SOLUTION_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(CONV_STATS_MSG_TYPE, this);
	}

	/** @see StatsReporterWithConvergence#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @see StatsReporterWithConvergence#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporterWithConvergence#notifyIn(Message) */
	public void notifyIn(Message msg) {

		String msgType = msg.getType();

		if (msgType.equals(CONV_STATS_MSG_TYPE)) { // in stats gatherer mode, the message sent by a variable containing the assignment history
			
			@SuppressWarnings("unchecked")
			StatsReporterWithConvergence.ConvStatMessage<V> msgCast = (StatsReporterWithConvergence.ConvStatMessage<V>)msg;
			assignmentHistoriesMap.put(msgCast.getVar(), msgCast.getAssignmentHistory());

			return;
			
		} else if (msgType.equals(SOLUTION_MSG_TYPE)) { // in stats gatherer mode, the final assignment to a variable
			
			@SuppressWarnings("unchecked")
			MessageWith2Payloads<String, V> msgCast = (MessageWith2Payloads<String, V>) msg;
			String var = msgCast.getPayload1();
			V val = msgCast.getPayload2();
			
			this.solution.put(var, val);
			
			if (! this.silent) 
				System.out.println("var `" + var + "' = " + val);
			
			if (this.solution.size() == this.problem.getNbrVars()) {
				this.optCost = this.problem.getUtility(this.solution, true).getUtility(0);
				if (! this.silent) 
					System.out.println((this.maximize ? "Utility" : "Cost") + " of solution found: " + this.optCost);
			}
			
			return;
		}

//		System.out.println(this.problem.getAgent() + " got " + msg);
		
		if (msgType.equals(FactorGraphGen.OUTPUT_MSG_TYPE)) { // the factor graph
			
			@SuppressWarnings("unchecked")
			MessageWith2Payloads < HashMap< String, VariableNode<V, U> >, HashMap< String, FunctionNode<V, U> > > msgCast = 
					(MessageWith2Payloads < HashMap< String, VariableNode<V, U> >, HashMap< String, FunctionNode<V, U> > >) msg;
			
			// Record the variable nodes
			this.varInfos = new HashMap<String, VarInfo> ();
			for (Map.Entry< String, VariableNode<V, U> > entry : msgCast.getPayload1().entrySet()) {
				VariableNode<V, U> node = entry.getValue();
				this.varInfos.put(entry.getKey(), new VarInfo (node.getVarName(), node.getAgent(), node.getFunctions()));
			}
			
			// Record the function nodes
			this.functionInfos = new HashMap<String, FunctionInfo> ();
			for (Map.Entry< String, FunctionNode<V, U> > entry : msgCast.getPayload2().entrySet()) {
				FunctionNode<V, U> node = entry.getValue();
				this.functionInfos.put(entry.getKey(), new FunctionInfo (node.getName(), node.getSpace(), node.getAgent()));
			}
			
			this.start();
			return;
		}
		
		if (this.varInfos == null) // the agent has already terminated
			return;

		if (msgType.equals(FUNCTION_MSG_TYPE)) { // a message sent by a function node
			
			// Retrieve the information from the message
			@SuppressWarnings("unchecked")
			FunctionMsg<V, U> msgCast = (FunctionMsg<V, U>) msg;
			UtilitySolutionSpace<V, U> marginalUtil = msgCast.getMarginalUtil();
			assert marginalUtil.getNumberOfVariables() == 1 : "Multi-variable marginal utility: " + marginalUtil;
			String var = marginalUtil.getVariable(0);
			
			// Postpone message if necessary
			if (! this.started) {
				this.pendingMsgs.put(var, msg);
				return;
			}
			
			VarInfo varInfo = this.varInfos.get(var);
			assert varInfo.optVal != null : "Received a message for a variable node I do not control";
			String functionNode = msgCast.getFunctionNode();
			
			// If this message hasn't changed since the last message received from this function node, don't respond 
			if (marginalUtil.equivalent(varInfo.lastMsgsIn.put(functionNode, marginalUtil))) {
				
				if (--varInfo.nbrIter == 0) {
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageWith2Payloads<String, V> (SOLUTION_MSG_TYPE, varInfo.getVarName(), varInfo.optVal));
					if(convergence)
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, var, assignmentHistoriesMap.get(varInfo.getVarName())));
					
					this.checkForTermination();
				}

				return;
			}

			// Compute the new optimal assignment to the destination variable, as the argmax of the join of the marginal utilities received from all function nodes
			int newOptIndex = 0;
			U newOpt = this.infeasibleUtil;
			final int domSize = (int) marginalUtil.getNumberOfSolutions();
			for (int i = 0; i < domSize; i++) { // for each possible assignment to my variable
				
				AddableDelayed<U> sumDelayed = this.zero.addDelayed();
				for (UtilitySolutionSpace<V, U> space : varInfo.lastMsgsIn.values()) 
					sumDelayed.addDelayed(space.getUtility(i));
				U sum = sumDelayed.resolve();
				
				if (this.maximize ? sum.compareTo(newOpt) >= 0 : sum.compareTo(newOpt) <= 0) {
					newOpt = sum;
					newOptIndex = i;
				}
			}
			V newOptVal = varInfo.getDom()[newOptIndex];
			
			// Report the new optimal assignment if it has changed
			if (! newOptVal.equals(varInfo.optVal)) {
				varInfo.optVal = newOptVal;
				
//				System.out.println("var `" + varInfo.varName + "' gets assigned " + varInfo.optVal);
				
				if (this.convergence) 
					assignmentHistoriesMap.get(var).add(new CurrentAssignment<V>(queue.getCurrentTime(), 0, newOptVal));
				
				if (--varInfo.nbrIter <= 0) {
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageWith2Payloads<String, V> (SOLUTION_MSG_TYPE, varInfo.getVarName(), varInfo.optVal));
					if(convergence)
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, var, assignmentHistoriesMap.get(varInfo.getVarName())));
					
					if (varInfo.nbrIter == 0) 
						this.checkForTermination();
					
					return;
				}
				
			} else if (--varInfo.nbrIter == 0) {
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageWith2Payloads<String, V> (SOLUTION_MSG_TYPE, varInfo.getVarName(), varInfo.optVal));
				if(convergence)
					queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, var, assignmentHistoriesMap.get(varInfo.getVarName())));
				this.checkForTermination();
				
				return;
			} else if (varInfo.nbrIter < 0) 
				return;
			
			// Compute and send a new message to each neighboring function node
			for (FunctionNode<V, U> function : varInfo.getFunctions()) {
				
				// Join all last marginal utilities received from all neighboring function nodes except the current one
				marginalUtil = this.zeroSpace(varInfo.getVarName(), varInfo.getDom());
				for (Map.Entry< String, UtilitySolutionSpace<V, U> > entry : varInfo.lastMsgsIn.entrySet()) {
					if (! function.getName().equals(entry.getKey())) {
						UtilitySolutionSpace<V, U> space = entry.getValue();
						for (int i = 0; i < domSize; i++) {
							U util = space.getUtility(i);
							marginalUtil.setUtility(i, marginalUtil.getUtility(i).add(util));
						}
					}
				}
				
				// Look up the number of feasible utilities
				AddableDelayed<U> scalarDelayed = this.zero.addDelayed();
				int nbrNonINFutils = 0;
				U util;
				for (int i = 0; i < domSize; i++) {
					if (! this.infeasibleUtil.equals(util = marginalUtil.getUtility(i))) {
						scalarDelayed.addDelayed(util);
						nbrNonINFutils++;
					}
				}
				
				// Rescale the join such that its utilities sum up to zero (ignoring infeasible ones)
				if (nbrNonINFutils > 0) {
					U scalar = scalarDelayed.resolve();
					scalar = scalar.divide(scalar.fromString(Integer.toString(nbrNonINFutils)));
					for (int i = 0; i < domSize; i++) 
						marginalUtil.setUtility(i, marginalUtil.getUtility(i).subtract(scalar));
				}
				
				// Send the message
				this.queue.sendMessage(function.getAgent(), new VariableMsg<V, U> (function.getName(), marginalUtil));
			}
			
			
		} else if (msgType.equals(VARIABLE_MSG_TYPE)) { // a message sent by a variable node
			
			// Retrieve the information from the message
			@SuppressWarnings("unchecked")
			VariableMsg<V, U> msgCast = (VariableMsg<V, U>) msg;
			String functionName = msgCast.getFunctionNode();
			
			// Postpone message if necessary
			if (! this.started) {
				this.pendingMsgs.put(functionName, msg);
				return;
			}
			
			FunctionInfo functionInfo = this.functionInfos.get(functionName);
			UtilitySolutionSpace<V, U> marginalUtil = msgCast.getMarginalUtil();
			assert marginalUtil.getNumberOfVariables() == 1 : "Multi-variable marginal utility: " + marginalUtil;
			String senderVar = marginalUtil.getVariable(0);
			
			if (! "start".equals(marginalUtil.getName())) { // not the foo message sent at startup
				
				// If this message hasn't changed since the last message received from this function node, don't react
				functionInfo.nbrIter--;
				if (marginalUtil.equivalent(functionInfo.lastMsgsIn.put(senderVar, marginalUtil))) 
					return;
			}
			
			// If I have exhausted all my iterations, only respond to variable nodes
			ArrayList<String> destinations = new ArrayList<String> (functionInfo.lastMsgsIn.keySet());
			if (functionInfo.nbrIter <= 0) 
				destinations.retainAll(Arrays.asList(senderVar));
			
			// Compute and send a message to each neighboring variable node
			final int nbrOtherSpaces = functionInfo.lastMsgsIn.size() - 1;
			for (String var : destinations) {
				
				marginalUtil = functionInfo.getSpace();
				
				// Join with the last marginal utilities received from all variables except the destination variable
				if (nbrOtherSpaces > 0) {
					@SuppressWarnings("unchecked")
					UtilitySolutionSpace<V, U>[] otherSpaces = new UtilitySolutionSpace [nbrOtherSpaces];
					int i = 0;
					for (Map.Entry< String, UtilitySolutionSpace<V, U> > entry : functionInfo.lastMsgsIn.entrySet()) 
						if (! var.equals(entry.getKey())) 
							otherSpaces[i++] = entry.getValue();
					marginalUtil = marginalUtil.join(otherSpaces);
				}
				
				// Blindly project all variables except the destination one
				String[] vars = new String [marginalUtil.getNumberOfVariables() - 1];
				int i = 0;
				for (String otherVar : marginalUtil.getVariables()) 
					if (! otherVar.equals(var)) 
						vars[i++] = otherVar;
				marginalUtil = marginalUtil.blindProject(vars, this.maximize);

				// Send the message
				this.queue.sendMessage(this.varInfos.get(var).getAgent(), new FunctionMsg<V, U> (functionInfo.getName(), marginalUtil.resolve()));
			}
			
		} else if (msgType.equals(AgentInterface.ALL_AGENTS_IDLE)) {
			
			// Send the stats
			for (VarInfo varInfo : this.varInfos.values()) {
				if (varInfo.nbrIter > 0 && ! varInfo.getFunctions().isEmpty()) { // unconstrained variables and variables with exhausted iterations have already been terminated
					
					assert varInfo.optVal != null;
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageWith2Payloads<String, V> (SOLUTION_MSG_TYPE, varInfo.getVarName(), varInfo.optVal));
					if(convergence)
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<V>(CONV_STATS_MSG_TYPE, varInfo.getVarName(), assignmentHistoriesMap.get(varInfo.getVarName())));
				}
			}
			
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			this.varInfos = null;
		}
	}

	/** Checks if all my variable nodes have finished */
	private void checkForTermination() {
		
		for (VarInfo info : this.varInfos.values()) 
			if (info.nbrIter > 0) 
				return;
				
		this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
	}

	/** @return for each variable in the problem, its chosen value */
	public HashMap<String, V> getOptAssignments () {
		return this.solution;
	}

	/** @return the cost of the optimal solution found */
	public U getOptCost () {
		return this.optCost;
	}

	/** @see StatsReporterWithConvergence#getAssignmentHistories() */
	public HashMap< String, ArrayList< CurrentAssignment<V> > > getAssignmentHistories() {
		return this.assignmentHistoriesMap;
	}

	/** @see StatsReporterWithConvergence#getCurrentSolution() */
	public Map<String, V> getCurrentSolution() {
		/// @todo Auto-generated method stub
		assert false: "Not Implemented";
		return null;
	}

}