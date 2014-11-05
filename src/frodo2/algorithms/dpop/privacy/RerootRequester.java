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

package frodo2.algorithms.dpop.privacy;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.UTILpropagation.OptUtilMessage;
import frodo2.algorithms.dpop.UTILpropagation.SolutionMessage;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.VarNbrMsg;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.hypercube.Hypercube;

/** A module that waits for the root variable to compute its optimal value, and then calls for a rerooting
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class RerootRequester < V extends Addable<V>, U extends Addable<U> > implements StatsReporter {
	
	/** The type of the statistics messages containing the optimal assignment to a variable */
	private static final String OPT_ASSIGNMENT_MSG_TYPE = "OptAssignment";

	/** The class of statistics messages containing the optimal assignment to a variable */
	public class RootValueMsg extends MessageWith2Payloads<String, V> {
		
		/** Empty constructor used for externalization */
		public RootValueMsg () { }

		/** Constructor
		 * @param root 		the name of the root variable
		 * @param value 	the optimal assignment to the variable
		 */
		public RootValueMsg (String root, V value) {
			super (OPT_ASSIGNMENT_MSG_TYPE, root, value);
		}
		
		/** @return the name of the root variable */
		public String getVar () {
			return super.getPayload1();
		}
		
		/** @return the optimal assignment to the variable */
		public V getVal () {
			return super.getPayload2();
		}
	}

	/** The type of the MessageWithPayload's containing the total optimal utility for one component of the constraint graph */
	private static final String OPT_UTIL_MSG_TYPE = "TotalOptUtil";
	
	/** This agent's queue */
	private Queue queue;
	
	/** For each internal variable, its list of children in the current DFS */
	private HashMap< String, List<String> > children = new HashMap< String, List<String> > ();
	
	/** For each internal variable, whether it is a root */
	private HashMap<String, Boolean> roots = new HashMap<String, Boolean> ();

	/** The problem */
	private DCOPProblemInterface<V, U> problem;

	/** Whether the stats gatherer should print out the solution */
	private boolean silent = false;

	/** For each internal variable, the number of variables it its constraint graph component */
	private HashMap<String, Integer> countdownsInit = new HashMap<String, Integer> ();

	/** For each internal variable, the number of remaining UTIL propagations */
	private HashMap<String, Integer> countdowns = new HashMap<String, Integer> ();

	/** The total optimal utility across all constraint graph components */
	private U optUtil;

	/** The total number of variables in the problem (in stats gatherer mode) */
	private int nbrVars;

	/** The optimal assignment to each variable */
	private HashMap<String, V> solution;

	/** Whether the problem has been found infeasible */
	private boolean infeasible = false;
	
	/** Constructor in "stats gatherer" mode
	 * @param params 	the parameters of the module
	 * @param problem 	the overall problem
	 */
	public RerootRequester (Element params, DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.nbrVars = problem.getNbrVars();
		this.solution = new HashMap<String, V> ();
	}
	
	/** Constructor
	 * @param problem 	the agent's subproblem
	 * @param params 	the parameters of the module
	 */
	public RerootRequester (DCOPProblemInterface<V, U> problem, Element params) {
		this.problem = problem;
		
		for (String var : problem.getMyVars()) {
			this.countdownsInit.put(var, Integer.MAX_VALUE);
			this.countdowns.put(var, Integer.MAX_VALUE);
		}
	}

	/** @return The total optimal utility across all constraint graph components */
	public U getOptUtil() {
		if (this.optUtil == null) 
			return this.problem.getZeroUtility();
		return optUtil;
	}

	/** @return the solution */
	public HashMap<String, V> getSolution() {
		return solution;
	}

	/** @see StatsReporter#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(OPT_ASSIGNMENT_MSG_TYPE)) { // in stats gatherer mode, the optimal assignment to a root
			
			RootValueMsg msgCast = (RootValueMsg) msg;
			String var = msgCast.getVar();
			V val = msgCast.getVal();
			
			this.solution.put(var, val);
			
			if (!this.silent) {
				if (--this.nbrVars <= 0) {
					
					// Display the solution found
					for (Map.Entry<String, V> entry : this.solution.entrySet()) 
						System.out.println(entry.getKey() + " = " + entry.getValue());
					
					// Display the optimal utility/cost
					if (this.problem.maximize()) 
						System.out.println("Total optimal utility: " + this.problem.getUtility(this.solution).getUtility(0));
					else 
						System.out.println("Total optimal cost: " + this.problem.getUtility(this.solution).getUtility(0));
				}
			}
		}
		
		else if (msgType.equals(OPT_UTIL_MSG_TYPE)) { // in stats gatherer mode, the total optimal utility of one component of the constraint graph
			
			MessageWithPayload<U> msgCast = (MessageWithPayload<U>) msg;
			// Going through a String to get the utility in case the agents have used a different class from the stats gatherer
			U util = this.problem.getZeroUtility().fromString(msgCast.getPayload().toString());
			
			if (this.optUtil == null) 
				this.optUtil = util;
			else 
				this.optUtil = this.optUtil.add(util);
			
			if (util.equals(util.getMinInfinity()) || util.equals(util.getPlusInfinity())) { // the problem is infeasible
				
				this.nbrVars = 0;
				
				if (!this.silent) {
					if (this.problem.maximize()) 
						System.out.println("Total optimal utility: -infinity");
					else 
						System.out.println("Total optimal cost: infinity");
				}
			}
		}
		
		else if (msgType.equals(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE)) { // the number of variables in a DFS
			
			VarNbrMsg msgCast = (VarNbrMsg) msg;
			String var = msgCast.getDest();
			int total = msgCast.getTotal();
			
			this.countdownsInit.put(var, total + 1);
			this.countdowns.put(var, total + 1);
			
			// If the variable is isolated, release its DFSoutput
			if (this.problem.getNbrNeighbors(var) == 0) {
				DFSview<V, U> view = new DFSview<V, U> (var);
				view.setSpaces(this.problem.getSolutionSpaces(var, false));
				this.queue.sendMessageToSelf(new MessageDFSoutput<V, U> (DFSgeneration.OUTPUT_MSG_TYPE, var, view));
			}
		}
		
		else if (msgType.equals(DFSgenerationWithOrder.OUTPUT_MSG_TYPE)) { // initial, temporary DFSoutput
			
			// Extract the information from the message
			MessageDFSoutput<V, U> msgCast = (MessageDFSoutput<V, U>) msg;
			String var = msgCast.getVar();
			DFSview<V, U> relationships = msgCast.getNeighbors();
			
			// If the variable is isolated, no need to reroot
			if (this.problem.getNbrNeighbors(var) > 0) {
				
				// Record the list of children
				this.children.put(var, relationships.getChildren());

				// If this is the root, request a reroot
				if (relationships.getParent() == null) 
					this.queue.sendMessageToSelf(new RerootingMsg (var));
			}
		}
		
		else if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // DFSoutput
			
			// Extract the information from the message
			MessageDFSoutput<V, U> msgCast = (MessageDFSoutput<V, U>) msg;
			String var = msgCast.getVar();
			DFSview<V, U> relationships = msgCast.getNeighbors();
			
			// Record the list of children, and whether this variable is a root
			if (relationships != null) {
				this.children.put(var, relationships.getChildren());
				this.roots.put(var, relationships.getParent() == null);
			}
		}
		
		else if (msgType.equals(SecureRerooting.REROOTING_REQUEST_TYPE)) { // a rerooting request
			
			RerootingMsg msgCast = (RerootingMsg) msg;
			String var = msgCast.getDest();
			
			// Forward the request to all children
			for (String child : this.children.get(var)) 
				this.queue.sendMessage(this.problem.getOwner(child), new RerootingMsg (child));
		}
		
		else if (msgType.equals(UTILpropagation.OUTPUT_MSG_TYPE)) { // optimal conditional assignments to a variable
			
			SolutionMessage<V> msgCast = (SolutionMessage<V>) msg;
			BasicUtilitySolutionSpace < V, ArrayList <V> > assignment = msgCast.getCondOptAssignments();
			String var = msgCast.getVariables()[0];
			
			// Decrement the countdown
			int countdown = this.countdowns.get(var) - 1;
			this.countdowns.put(var, countdown);
			if (assignment == null && countdown + 1 == this.countdownsInit.get(var)) // skipped iteration before the real first one
				this.countdownsInit.put(var, countdown);
			
			// Check if this is the root
			if (assignment != null && assignment.getNumberOfVariables() == 0) {

				V value = assignment.getUtility(0).get(0);

				// Send assignment to stats gatherer
				if (! this.infeasible) 
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new RootValueMsg (var, value));

				// Add constraint var = value

				// Construct the domains
				V[] dom = this.problem.getDomain(var);
				V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
				doms[0] = dom;

				// Construct the utilities
				U zero = this.problem.getZeroUtility();
				U[] utils = (U[]) Array.newInstance(zero.getClass(), dom.length);
				U inf = (this.problem.maximize() ? this.problem.getMinInfUtility() : this.problem.getPlusInfUtility());
				Arrays.fill(utils, inf);
				for (int i = dom.length - 1; i >= 0; i--) {
					if (dom[i].equals(value)) {
						utils[i] = zero;
						break;
					}
				}

				// Add the constraint
				Hypercube<V, U> equality = new Hypercube<V, U> (new String[] {var}, doms, utils, inf);
				equality.setName(var + "=" + value);
				this.problem.addSolutionSpace(equality);

				// Request a reroot if not all variables have been root already
				if (this.infeasible || countdown == 1) 
					this.queue.sendMessageToSelf(new StopMsg (var));
				else 
					this.queue.sendMessageToSelf(new RerootingMsg (var));
			}
			
			else if (countdown == 1 && this.roots.get(var)) 
				this.queue.sendMessageToSelf(new StopMsg (var));
		}
		
		else if (msgType.equals(UTILpropagation.OPT_UTIL_MSG_TYPE)) { // the optimal utility of a constraint graph component
			
			OptUtilMessage<U> msgCast = (OptUtilMessage<U>) msg;
			String root = msgCast.getRoot();
			
			// Check whether the problem has been found infeasible
			U util = msgCast.getUtility();
			
			// Report the utility to the stats gatherer only if this is the first UTIL propagation for this component
			if (this.countdowns.get(root).equals(this.countdownsInit.get(root))) 
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageWithPayload<U> (OPT_UTIL_MSG_TYPE, util));
			
			if (util.equals(problem.getMinInfUtility()) || util.equals(problem.getPlusInfUtility())) {
				this.infeasible  = true;
				this.countdowns.put(root, 1);
			}
		}
		
		else if (msgType.equals(StopMsg.STOP_MSG_TYPE)) { // a message stating that the problem has been found infeasible
			
			StopMsg msgCast = (StopMsg) msg;
			String var = msgCast.getDest();
			
			this.countdowns.put(var, 0);
			
			// Forward to children
			for (String child : this.children.get(var)) 
				this.queue.sendMessage(this.problem.getOwner(child), new StopMsg (child));
			
			// Check the countdowns
			for (Integer count : this.countdowns.values()) 
				if (count > 0) // this one's not done yet
					return;
			
			// All my other variables are done 
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
		}
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return Arrays.asList(
				DFSgenerationWithOrder.VARIABLE_COUNT_TYPE, 
				DFSgeneration.OUTPUT_MSG_TYPE, 
				DFSgenerationWithOrder.OUTPUT_MSG_TYPE, 
				SecureRerooting.REROOTING_REQUEST_TYPE, 
				UTILpropagation.OUTPUT_MSG_TYPE, 
				UTILpropagation.OPT_UTIL_MSG_TYPE, 
				StopMsg.STOP_MSG_TYPE
				);
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OPT_ASSIGNMENT_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(OPT_UTIL_MSG_TYPE, this);
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.optUtil = null;
		this.nbrVars = problem.getNbrVars();
		this.solution = new HashMap<String, V> ();
		this.infeasible = false;
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

}
