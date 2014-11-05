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

/** Classes implementing the SynchBB algorithm */
package frodo2.algorithms.synchbb;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporterWithConvergence;
import frodo2.algorithms.varOrdering.linear.OrderMsg;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** The SynchBB algorithm for DCOPs
 * 
 * This is the original SynchBB algorithm by Hirayama & Yokoo (CP'97), 
 * adapted to solve DCOPs as proposed by Meisels (Springer Verlag, 2008), 
 * with two additional performance improvements: 
 * - BACKTRACK messages do not include the path
 * - PATH messages strip the first part of the path that has not changed
 * 
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class SynchBB < V extends Addable<V>, U extends Addable<U> > implements StatsReporterWithConvergence<V> {
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The types of the messages containing the chosen linear order of (clusters of) variables */
	public static String ORDER_MSG_TYPE = OrderMsg.ORDER_MSG_TYPE;

	/** The types of the messages containing the chosen linear order of (clusters of) variables sent to the stats gatherer */
	public static String ORDER_STATS_MSG_TYPE = OrderMsg.STATS_MSG_TYPE;

	/** The type of the backtrack messages */
	public static final String BACKTRACK_MSG_TYPE = "Backtrack";
	
	/** The type of the messages containing the current partial assignment */
	public static final String PATH_MSG_TYPE = "Path";
	
	/** The type of the message containing the optimal solution found */
	public static final String OUTPUT_MSG_TYPE = "Solution";
	
	/** The type of the message containing the optimal solution found sent to the stats gatherer */
	public static final String STATS_MSG_TYPE = "SolutionStats";
	
	/** The type of the messages broadcast by the last variable containing the current upper bound */
	public static final String UB_MSG_TYPE = "UB"; /// @todo Do not include the solution?
	
	/** The type of the message reporting the convergence for a given component */
	private static final String CONV_STATS_MSG_TYPE = "Convergence";

	/** Whether the algorithm should print out debugging information */
	private static final boolean DEBUG = false;
	
	/** A message reporting the convergence for a given component
	 * @param <V> the type used for variable values
	 */
	public static class ConvergenceMessage < V extends Addable<V> > extends Message implements Externalizable {
		
		/** The ID of the component in the constraint graph */
		private Comparable<?> compID;
		
		/** The history of assignments to variables */
		private TreeMap<Long, V[][]> history;
		
		/** Empty constructor used for externalization */
		public ConvergenceMessage () {
			super (CONV_STATS_MSG_TYPE);
		}

		/** Constructor
		 * @param compID 	The ID of the component in the constraint graph
		 * @param history 	The history of assignments to clusters of variables
		 */
		public ConvergenceMessage(Comparable<?> compID, TreeMap<Long, V[][]> history) {
			super(CONV_STATS_MSG_TYPE);
			this.compID = compID;
			this.history = history;
		}

		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(this.compID);
			out.writeObject(this.history);
		}
		
		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		@SuppressWarnings("unchecked")
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			this.compID = (Comparable<?>) in.readObject();
			this.history = (TreeMap<Long, V[][]>) in.readObject();
		}
		
		/** @see Message#toString() */
		@Override
		public String toString () {
			return super.toString() + "\n\tcompID = " + this.compID + "\n\thistory = " + this.history;
		}
	}

	/** Information about a cluster of variables owned by this agent */
	private class ClusterInfo {

		/** The variables' names */
		private String[] vars;

		/** The unconstrained variables' domains */
		private V[][] domain;

		/** The agent owning the previous cluster in the ordering */
		private String prevAgent;

		/** The agent owning the next cluster in the ordering */
		private String nextAgent;

		/** The join of all constraints this cluster of variables is responsible for enforcing */
		private UtilitySolutionSpace<V, U> space;

		/** Cost of the partial assignment up to and excluding this cluster */
		private U prevCost = zero;

		/** Cost of the partial assignment up to and including this cluster */
		private U cost = zero;
		
		/** The last path sent down */
		private V[][] lastPath;

		/** The current Iterator over the solutions of the space associated with this cluster */
		private UtilitySolutionSpace.SparseIterator<V, U> iterator = null;
		
		/** Constructor
		 * @param vars		The variables contained in the cluster
		 */
		@SuppressWarnings("unchecked")
		public ClusterInfo(List<String> vars){
			this.vars = vars.toArray(new String[vars.size()]);
			if (! vars.isEmpty()) {
				this.domain = (V[][]) Array.newInstance(problem.getDomain(vars.get(0)).getClass(), vars.size());
				for(int i = vars.size() - 1; i >= 0; i--) 
					this.domain[i] = problem.getDomain(vars.get(i));
			}
		}
		
	}
	
	/** \c true when the convergence history is to be stored */
	private final boolean convergence;
	
	/** For each variable, its assignment history */
	private HashMap< String, ArrayList< CurrentAssignment<V> > > assignmentHistoriesMap;

	/** The information about a particular component of the constraint graph */
	private class ComponentInfo {

		/** Array of ClusterInfos, in the same order as in the cluster order used 
		 * (i.e. this array will have null entries for external clusters) */
		private ArrayList<ClusterInfo> clusterInfos;

		/** For each internal cluster in this component, its ClusterInfo */
		private HashMap<String, ClusterInfo> infoForCluster;

		/** For each cluster, its index in the cluster order */
		private HashMap<String, Integer> clusterIndexes = new HashMap<String, Integer> ();

		/** The cluster order */
		private String[][] order;
		
		/** Each cluster's ID */
		private String[] ids;

		/** The assignment to each cluster in the ordering */
		private V[][] assignments;

		/** The best solution found so far */
		private V[][] bestSol;

		/** The best known solution at various timestamps */
		private TreeMap<Long, V[][]> history;

		/** The cost of the best solution found so far */
		private U ub = problem.getPlusInfUtility();
		
		/** The set of all agents involved in this component */
		private HashSet<String> agents;
		
		/** Constructor
		 * @param order 	the cluster order
		 */
		private ComponentInfo (List<List<String>> order) {
			this.order = new String[order.size()][];
			for (int i = order.size() - 1; i >= 0; i--) 
				this.order[i] = order.get(i).toArray(new String[order.get(i).size()]);
			this.ids = new String [order.size()];
			if (convergence) 
				this.history = new TreeMap<Long, V[][]> ();
		}
		
		/** Empty constructor */
		private ComponentInfo () {
			if (convergence) 
				this.history = new TreeMap<Long, V[][]> ();
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			
			StringBuilder builder = new StringBuilder ("ComponentInfo:");
			builder.append("\n\t agents: " + this.agents);
			builder.append("\n\t assignments: " + Arrays.deepToString(this.assignments));
			builder.append("\n\t bestSol: " + Arrays.deepToString(this.bestSol));
			builder.append("\n\t infoForCluster: " + this.infoForCluster);
			builder.append("\n\t order: " + Arrays.deepToString(this.order));
			builder.append("\n\t ub: " + this.ub);
			builder.append("\n\t clusterIndexes: " + this.clusterIndexes);
			builder.append("\n\t clusterInfos: " + this.clusterInfos);

			return builder.toString();
		}
	}
	
	/** The information about each component in the constraint graph */
	private HashMap<Comparable<?>, ComponentInfo> compInfos;

	/** For each cluster, its component ID */
	private HashMap< String, Comparable<?> > compOfCluster;

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
	
	/** The class of V */
	private Class<V> valClass;
	
	/** The class of V[] */
	private Class<V[]> valArrayClass;

	/** Used to store solution messages received by the stats gatherer before their corresponding linear order */
	private LinkedList< SolutionMsg<V, U> > pendingSolMsgs;
	
	/** Used to store convergence messages received by the stats gatherer before their corresponding linear order */
	private LinkedList< ConvergenceMessage<V> > pendingConvMsgs;

	/** For some clusters, a PathMsg that was received before the var order message */
	private HashMap< String, PathMsg<V, U> > pendingPathMsgs;
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public SynchBB (Element parameters, DCOPProblemInterface<V, U> problem)  {
		this.problem = problem;
		this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
		this.convergence = false;
		this.assignmentHistoriesMap = new HashMap< String, ArrayList< CurrentAssignment<V> > > ();
		this.solution = new HashMap<String, V> ();
		this.optCost = problem.getZeroUtility();
		this.pendingSolMsgs = new LinkedList< SolutionMsg<V, U> > ();
		this.pendingConvMsgs = new LinkedList< ConvergenceMessage<V> > ();
	}
	
	/** Constructor
	 * @param problem 		this agent's problem
	 * @param parameters 	the parameters for SynchBB
	 */
	public SynchBB (DCOPProblemInterface<V, U> problem, Element parameters) {
		this.problem = problem;
		
		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;
		
		this.pendingSolMsgs = new LinkedList< SolutionMsg<V, U> > ();
		this.pendingPathMsgs = new HashMap< String, PathMsg<V, U> > ();
	}
	
	/** Parses the problem */
	private void start () {
		this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
		this.compOfCluster = new HashMap< String, Comparable<?> > ();
		this.optCost = this.problem.getZeroUtility();
		this.solution = new HashMap<String, V> ();
		this.zero = this.problem.getZeroUtility();
		assert ! this.problem.maximize() : "SynchBB only supports minimization problems with non-negative costs";
		assert this.checkAllCostsNonNeg() : "All costs must be non-negative";
		this.started = true;
	}
	
	/** @return \c true if all utilities in all spaces are non-negative, \c false otherwise */
	private boolean checkAllCostsNonNeg() {
		U zero = this.zero;

		for (UtilitySolutionSpace<V, U> space : this.problem.getSolutionSpaces()) 
			for (UtilitySolutionSpace.Iterator<V, U> iter = space.iterator(); iter.hasNext(); ) 
				if (iter.nextUtility().compareTo(zero) < 0) 
					return false;

		return true;
	}

	/** @see StatsReporterWithConvergence#reset() */
	public void reset() {
		this.compOfCluster = null;
		this.zero = null;
		
		this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
		this.solution = new HashMap<String, V> ();
		this.optCost = this.problem.getZeroUtility();

		this.started = false;
	}

	/** @see StatsReporterWithConvergence#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (7);
		types.add(START_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		types.add(ORDER_MSG_TYPE);
		types.add(PATH_MSG_TYPE);
		types.add(BACKTRACK_MSG_TYPE);
		types.add(UB_MSG_TYPE);
		types.add(OUTPUT_MSG_TYPE);
		return types;
	}

	/** @see StatsReporterWithConvergence#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(ORDER_STATS_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(STATS_MSG_TYPE, this);
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
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(STATS_MSG_TYPE)) { // the message containing the solution
			
			SolutionMsg<V, U> msgCast = (SolutionMsg<V, U>) msg;
			
			// Record the solution
			this.optCost = this.optCost.add(msgCast.cost);
			ComponentInfo compInfo = this.compInfos.get(msgCast.componentID);
			
			// Defer the message if we have not received the linear order for this component yet
			if (compInfo == null) {
				this.pendingSolMsgs.add(msgCast);
				return;
			}
			
			final int nbrClusters = compInfo.order.length;
			for (int i = 0; i < nbrClusters; i++) {
				String[] cluster = compInfo.order[i];
				for(int j = 0; j < cluster.length; j++){
					String var = cluster[j];
					V val = this.problem.getDomain(var)[0];
					if (msgCast.solution != null && msgCast.solution[i][j] != null) 
						val = msgCast.solution[i][j];
					this.solution.put(var, val);
					if (! this.silent) 
						System.out.println("var `" + var + "' = " + val);
				}
			}
			
			if (this.solution.size() == this.problem.getVariables().size()) {
				
				this.optCost = this.problem.getUtility(solution, true).getUtility(0);
				
				if (! this.silent) 
					System.out.println("Total optimal " + (this.problem.maximize() ? "utility: " : "cost: ") + this.optCost);
			}
			
			return;
		}

		if (msgType.equals(ORDER_STATS_MSG_TYPE)) { // a stats message containing the linear order on clusters
			OrderMsg<V, U> msgCast = (OrderMsg<V, U>) msg;
			Comparable<?> compID = msgCast.getComponentID();
			this.compInfos.put(compID, new ComponentInfo (msgCast.getOrder()));
			
			// Process the potentially pending messages for this component
			for (Iterator< SolutionMsg<V, U> > iter = this.pendingSolMsgs.iterator(); iter.hasNext(); ) {
				SolutionMsg<V, U> solMsg = iter.next();
				if (solMsg.componentID.equals(compID)) {
					iter.remove();
					this.notifyIn(solMsg);
					break;
				}
			}
			for (Iterator< ConvergenceMessage<V> > iter = this.pendingConvMsgs.iterator(); iter.hasNext(); ) {
				ConvergenceMessage<V> convMsg = iter.next();
				if (convMsg.compID.equals(compID)) {
					iter.remove();
					this.notifyIn(convMsg);
					break;
				}
			}
			
			return;
		}
		
		if (msgType.equals(CONV_STATS_MSG_TYPE)) { // a stats message containing the history of best solutions for a given component
			
			ConvergenceMessage<V> msgCast = (ConvergenceMessage<V>) msg;
			ComponentInfo compInfo = this.compInfos.get(msgCast.compID);
			
			// Postpone this message if we have not received the corresponding linear order yet
			if (compInfo == null) {
				this.pendingConvMsgs.add(msgCast);
				return;
			}
			
			// Initialize the histories map
			String[][] clusters = compInfo.order;
			final int nbrClusters = clusters.length;
			for (int i = 0; i < nbrClusters; i++) {
				String[] cluster = compInfo.order[i];
				for (int j = 0; j < cluster.length; j++)
					this.assignmentHistoriesMap.put(cluster[j], new ArrayList< CurrentAssignment<V> > ());
			}

			// Fill in the history for each variable
			for (Map.Entry<Long, V[][]> entry : msgCast.history.entrySet()) {
				Long time = entry.getKey();
				V[][] sol = entry.getValue();

				for (int i = 0; i < nbrClusters; i++) {
					String[] cluster = compInfo.order[i];
					V[] soli = sol[i];
					for (int j = 0; j < cluster.length; j++)
						this.assignmentHistoriesMap.get(cluster[j]).add(new CurrentAssignment<V>(time, soli[j]));
				}
			}
			
			return;
		}
		
		if (! this.started) 
			this.start();
		
		if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			
			this.reset();
			return;
		}

		if (msgType.equals(ORDER_MSG_TYPE)) { // a message containing the chosen linear ordering on clusters of variables

			// Retrieve the information from the message
			OrderMsg<V, U> msgCast = (OrderMsg<V, U>) msg;
			Comparable<?> component = msgCast.getComponentID();
			List<List<String>> order = msgCast.getOrder();
			List< UtilitySolutionSpace<V, U> > spaces = msgCast.getSpaces();
			ComponentInfo compInfo = this.compInfos.get(component);
			if (compInfo == null) {
				compInfo = new ComponentInfo (order);
				this.compInfos.put(component, compInfo);
			} else {
				compInfo.order = new String[order.size()][];
				for (int i = order.size() - 1; i >= 0; i--) 
					compInfo.order[i] = order.get(i).toArray(new String[order.get(i).size()]);
				compInfo.ids = new String [order.size()];
			}
			List<String> agents = msgCast.getAgents();
			compInfo.agents = new HashSet<String> (agents);
			final int nbrClusters = agents.size();

			// Initialize the information about my internal clusters
			compInfo.clusterInfos = new ArrayList<ClusterInfo> (nbrClusters);
			compInfo.infoForCluster = new HashMap<String, ClusterInfo> (nbrClusters);
			if(this.valArrayClass == null){
				V[] dom = this.problem.getDomain(this.problem.getAllVars().iterator().next());
				valArrayClass = (Class<V[]>) dom.getClass();
				valClass = (Class<V>) this.valArrayClass.getComponentType();
			}
			List<String> clusterIDs = msgCast.getIDs();
			for (int i = 0; i < nbrClusters; i++) {
				List<String> cluster = order.get(i);
				String clusterID = clusterIDs.get(i);
				compInfo.ids[i] = clusterID;
				compInfo.clusterIndexes.put(clusterID, i);
				
				// Skip this whole cluster if it is external
				if (! agents.get(i).equals(this.problem.getAgent())) {
					compInfo.clusterInfos.add(null);
					continue;
				}
				
				// Record the space
				ClusterInfo info = new ClusterInfo(cluster);
				info.space = spaces.get(i);
				
				// If all the variables of the cluster are unconstrained,
				// we create a ScalarHypercube of zero utility that will allow us to iterate over the domains of these variables
				if(info.space == null){
					info.space = new ScalarHypercube<V,U>(this.zero, this.zero.getPlusInfinity(), this.valArrayClass);
				}
				
				compInfo.clusterInfos.add(info);
				compInfo.infoForCluster.put(clusterID, info);
				this.compOfCluster.put(clusterID, component);

				if (i > 0) 
					info.prevAgent = agents.get(i - 1);
				if (i < nbrClusters - 1) 
					info.nextAgent = agents.get(i + 1);

			}
			
			// If I own the first cluster in the ordering, start the algorithm
			if (compInfo.clusterInfos.get(0) != null) 
				this.initiate(msgCast.getComponentID(), compInfo);

			// If I own the last cluster in the ordering, initialize the best solution found so far
			ClusterInfo info = compInfo.clusterInfos.get(nbrClusters - 1);
			if (info != null) {
				compInfo.bestSol = (V[][]) Array.newInstance(valArrayClass, nbrClusters);
				for (int i = 0; i < nbrClusters; i++)
					compInfo.bestSol[i] = (V[]) Array.newInstance(valClass, compInfo.order[i].length);
			}

			// Process the potentially pending solution messages for this component
			for (Iterator< SolutionMsg<V, U> > iter = this.pendingSolMsgs.iterator(); iter.hasNext(); ) {
				SolutionMsg<V, U> solMsg = iter.next();
				if (solMsg.componentID.equals(component)) {
					iter.remove();
					this.notifyIn(solMsg);
					break;
				}
			}
			
			// Handle the pending PATH message, if any
			for (String clusterID : clusterIDs) {
				PathMsg<V, U> pending = this.pendingPathMsgs.remove(clusterID);
				if (pending != null) {
					this.notifyIn(pending);
					break;
				}
			}
		}
		
		else if (msgType.equals(PATH_MSG_TYPE)) { // a message containing a path
			
			PathMsg<V, U> msgCast = (PathMsg<V, U>) msg;
			
			// Look up the component ID corresponding to the destination variable
			Comparable<?> componentID = this.compOfCluster.get(msgCast.dest);

			if (componentID == null) // the cluster order message has not been received yet
				this.pendingPathMsgs.put(msgCast.dest, msgCast);
			
			else 
				this.received_from_prev(msgCast);
		}
		
		else if (msgType.equals(BACKTRACK_MSG_TYPE)) // a backtrack message
			this.received_from_next((BTmsg) msg);
		
		else if (msgType.equals(UB_MSG_TYPE)) { // a message containing the incumbent
			
			SolutionMsg<V, U> msgCast = (SolutionMsg<V, U>) msg;
			ComponentInfo compInfo = this.compInfos.get(msgCast.componentID);
			compInfo.bestSol = msgCast.solution;
			compInfo.ub = msgCast.cost;
			
			// Record the current best solution if required
			if (convergence){
				V[][] sol = (V[][]) Array.newInstance(compInfo.bestSol[0].getClass(), compInfo.order.length);
				for(int i = compInfo.order.length - 1; i >= 0; i--) 
					sol[i] = compInfo.bestSol[i].clone();
				compInfo.history.put(this.queue.getCurrentTime(), sol);
			}
		}

		else if (msgType.equals(OUTPUT_MSG_TYPE)) { // the solution for a particular component of the constraint graph
			
			// Record the solution
			SolutionMsg<V, U> msgCast = (SolutionMsg<V, U>) msg;
			this.optCost = this.optCost.add(msgCast.cost);
			ComponentInfo compInfo = this.compInfos.get(msgCast.componentID);

			// Defer the message if we have not received the linear order for this component yet
			if (compInfo == null) {
				this.pendingSolMsgs.add(msgCast);
				return;
			}

			for (int i = compInfo.order.length - 1; i >= 0; i--){
				ClusterInfo cluster = compInfo.clusterInfos.get(i);
				if (cluster != null) { // internal cluster
					String[] vars = compInfo.order[i];
					if (msgCast.solution == null) // no solution found
						for(int j = vars.length - 1; j >= 0; j--)
							this.solution.put(vars[j], null);
					else {
						V[] assignment = msgCast.solution[i];
						for(int j = vars.length - 1; j >= 0; j--)
							this.solution.put(vars[j], assignment[j]);
					}
				}
			}

			if (this.solution.size() >= this.problem.getNbrIntVars())
				this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
		}
		
	}

	/** Chooses a first value for the first cluster of variables and starts the algorithm 
	 * @param compID 	the ID of the component in the constraint graph
	 * @param compInfo 	the information about the component in the constraint graph 
	 */
	@SuppressWarnings("unchecked")
	private void initiate (Comparable<?> compID, ComponentInfo compInfo) {

		ClusterInfo info = compInfo.clusterInfos.get(0);

		// Find the first assignment of the variables in the first cluster that has a non-infinite cost
		compInfo.assignments = (V[][]) Array.newInstance(valArrayClass, compInfo.order.length);
		for (int i = compInfo.order.length - 1; i >= 0; i--)
			compInfo.assignments[i] = (V[]) Array.newInstance(valClass, compInfo.order[i].length);
		V[] vals = this.get_next(compInfo, 0, info);

		if (vals == null) { // the problem is already infeasible

			// Send output and termination messages
			compInfo.bestSol = compInfo.assignments;
			this.terminate(compID, compInfo);
			
			return;
		}

		// Send the path to the second cluster
		if (info.nextAgent == null) { // there is no second cluster

			// Look for the best assignment for this isolated cluster
			compInfo.ub = info.cost;
			while (true) {
				V[] newVals = this.get_next(compInfo, 0, info);
				if (newVals == null) 
					break;
				vals = newVals;
				compInfo.ub = info.cost;
			}

			compInfo.assignments[0] = vals;
			compInfo.bestSol = compInfo.assignments;
			
			if (this.convergence) 
				compInfo.history.put(this.queue.getCurrentTime(), compInfo.bestSol);
			
			this.terminate(compID, compInfo);
		} else {
			compInfo.assignments[0] = vals;
			info.lastPath = (V[][]) Array.newInstance(valArrayClass, compInfo.order.length);
			for(int i = compInfo.order.length - 1; i >= 0; i--) 
				info.lastPath[i] = compInfo.assignments[i].clone();
			this.queue.sendMessage(info.nextAgent, new PathMsg<V, U> (compInfo.ids[1], compInfo.assignments, 0, 1, info.cost));
		}
	}
	
	/** Sends output and termination messages  
	 * @param compID 	the ID of the component in the constraint graph
	 * @param compInfo 	the information about the component in the constraint graph 
	 */
	private void terminate (Comparable<?> compID, ComponentInfo compInfo) {
		
		this.queue.sendMessageToMulti(compInfo.agents, new SolutionMsg<V, U> (OUTPUT_MSG_TYPE, compID, compInfo.bestSol, compInfo.ub));
		this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V, U> (STATS_MSG_TYPE, compID, compInfo.bestSol, compInfo.ub));
		
		if (this.convergence) 
			this.queue.sendMessage(AgentInterface.STATS_MONITOR, new ConvergenceMessage<V> (compID, compInfo.history));
		
		/// @todo Terminate all components prematurely if the optimal cost is already infinite
	}

	/** Reacts to the reception of a path message from the previous cluster in the ordering
	 * @param msg 	 the received path message
	 */
	@SuppressWarnings("unchecked")
	private void received_from_prev (PathMsg<V, U> msg) {
		
		// Retrieve the information from the message
		Comparable<?> componentID = this.compOfCluster.get(msg.dest);
		ComponentInfo compInfo = this.compInfos.get(componentID);
		int clusterIndex = compInfo.clusterIndexes.get(msg.dest);
		ClusterInfo info = compInfo.clusterInfos.get(clusterIndex);
		
		// Check if this was a virtual message
		if (msg.nbrAssignments == msg.assignments.length) { // not a virtual message
			if (compInfo.assignments == null){
				compInfo.assignments = (V[][]) Array.newInstance(valArrayClass, compInfo.order.length);
				for (int i = compInfo.order.length - 1; i >= 0; i--)
					compInfo.assignments[i] = (V[]) Array.newInstance(valClass, compInfo.order[i].length);
			}

			for(int i = msg.offset; i < msg.assignments.length; i++) 
				compInfo.assignments[i] = msg.assignments[i].clone();
			
		} else 
			compInfo.assignments = msg.assignments;
		info.prevCost = msg.cost;

		// Choose the next assignment for the current cluster
		V[] next = this.get_next(compInfo, clusterIndex, info);
		this.send_token(componentID, compInfo, clusterIndex, info, next);
	}
	
	/** Reacts to the reception of a backtrack message
	 * @param msg 	the received backtrack message
	 */
	private void received_from_next (BTmsg msg) {
		
		// Retrieve the information from the message
		Comparable<?> componentID = this.compOfCluster.get(msg.dest);
		ComponentInfo compInfo = this.compInfos.get(componentID);
		int clusterIndex = compInfo.clusterIndexes.get(msg.dest);
		ClusterInfo info = compInfo.clusterInfos.get(clusterIndex);

		// Choose the next assignment for the current cluster
		V[] next = this.get_next(compInfo, clusterIndex, info);
		this.send_token(componentID, compInfo, clusterIndex, info, next);
	}
	
	/** Sends the next message
	 * @param compID 		the ID of the component in the constraint graph
	 * @param compInfo 		the information about the component in the constraint graph 
	 * @param clusterIndex 	index of the current cluster in the cluster ordering
	 * @param info 			information on the current cluster
	 * @param next 			next assignment chosen for the current cluster
	 */
	@SuppressWarnings("unchecked")
	private void send_token (Comparable<?> compID, ComponentInfo compInfo, final int clusterIndex, ClusterInfo info, V[] next) {

		if (next != null) { // we have not yet exhausted the domain

			final int nbrClutsers = compInfo.order.length;
			if (clusterIndex == nbrClutsers - 1) { // last cluster
				// Go through all possible assignment for the last cluster of variables to update the incumbent
				V[] next_to_next = next;
				while (next_to_next != null) {
					
					// Record the best solution found so far
					for(int i = 0; i < nbrClutsers; i++) 
						compInfo.bestSol[i] = compInfo.assignments[i].clone();

					compInfo.ub = info.cost;
					
					// We can already terminate if the best solution found has cost 0
					if (compInfo.ub.equals(this.zero)) {

						if (this.convergence) 
							compInfo.history.put(this.queue.getCurrentTime(), compInfo.bestSol);
						
						this.terminate(compID, compInfo);
						return;
					}
					
					// Iterate
					next_to_next = this.get_next(compInfo, clusterIndex, info);
				}
				
				// Broadcast the new upper bound to all agents
				this.queue.sendMessageToMulti(compInfo.agents, new SolutionMsg<V, U> (UB_MSG_TYPE, compID, compInfo.bestSol, compInfo.ub));
				
				// Record the current best solution if required
				if (convergence){
					V[][] sol = (V[][]) Array.newInstance(valArrayClass, compInfo.order.length);
					for(int i = 0; i < nbrClutsers; i++) 
						sol[i] = compInfo.bestSol[i].clone();
					compInfo.history.put(this.queue.getCurrentTime(), sol);
				}

				// Backtrack
				this.queue.sendMessage(info.prevAgent, new BTmsg (compInfo.ids[clusterIndex - 1]));
				
				if (DEBUG) {
					System.out.println("Backtracking...");
					System.out.println(this.problem.getAgent() + "'s new NCCC count: " + this.problem.getNCCCs() + "\n");
				}
			}

			else { // not the last cluster

				// Compute the path offset, i.e. the index of the first cluster assignment in the path that has changed since the last path sent
				int offset = 0;
				if (info.lastPath != null) 
					while (offset <= clusterIndex && Arrays.equals(info.lastPath[offset], compInfo.assignments[offset])) 
						offset++;
				info.lastPath = (V[][]) Array.newInstance(valArrayClass, compInfo.order.length);
				for(int i = 0; i < nbrClutsers; i++) 
					info.lastPath[i] = compInfo.assignments[i].clone();

				this.queue.sendMessage(info.nextAgent, 
						new PathMsg<V, U> (compInfo.ids[clusterIndex + 1], compInfo.assignments, offset, clusterIndex + 1, info.cost));
			}
		}
		
		else { // we have exhausted the domain
			
			if (clusterIndex == 0) { // first cluster
				this.terminate(compID, compInfo);
				return;
			}

			else // not the first cluster
				this.queue.sendMessage(info.prevAgent, new BTmsg (compInfo.ids[clusterIndex - 1]));
			
			if (DEBUG) {
				System.out.println("Backtracking...");
				System.out.println(this.problem.getAgent() + "'s new NCCC count: " + this.problem.getNCCCs() + "\n");
			}
		}
	}

	/** Chooses the next assignment for the current cluster of variables
	 * @param compInfo 		the information about the component in the constraint graph 
	 * @param clusterIndex 	index of the current cluster in the cluster ordering
	 * @param info 			information about the current cluster
	 * @return the next assignment for the current cluster, or \c null if the domain is exhausted
	 */
	@SuppressWarnings("unchecked")
	private V[] get_next (ComponentInfo compInfo, final int clusterIndex, ClusterInfo info) {
		
		// If we don't already have an iterator over the solutions of this cluster, we create it
		if(info.iterator == null){
			// Order of variables: 1) variables of this cluster 2) previous variables
			///@todo can we move a part of this computation at the creation of the clusterInfo object to avoid searching for these variables that are fixed in the order too many times? Yes, we can!(R)
			List<String> vars = new ArrayList<String>();
			List<V[]> domains = new ArrayList<V[]>();
			if (info.vars.length > 0) {
				vars.addAll(Arrays.asList(info.vars));
				domains.addAll(Arrays.asList(info.domain));
			}
			HashSet<String> spaceVars = new HashSet<String>(Arrays.asList(info.space.getVariables()));
			
			// We get the values of all the known variables in the current assignment
			for (int i = 0; i < clusterIndex; i++) {
				String[] cluster = compInfo.order[i];
				V[] values = compInfo.assignments[i];
				for (int j = cluster.length - 1; j >= 0; j--){
					assert values[j] != null: "Some previous variables in the linear order are unassigned!";
					String var = cluster[j];
					if(spaceVars.contains(var)){
						vars.add(var);
						V[] value = (V[]) Array.newInstance(this.valClass, 1);
						value[0] = values[j];
						domains.add(value);
					}
				}
			}
		
			info.iterator = info.space.sparseIter(vars.toArray(new String[vars.size()]), domains.toArray((V[][]) Array.newInstance(valArrayClass, domains.size())));
		}
		
		// We get the next assignment
		U next = info.iterator.nextUtility(compInfo.ub.subtract(info.prevCost), true);
		
		// the domain of the cluster variables is exhausted
		if(next == null){
			info.iterator = null;
			info.cost = zero;
			return null;
		}
		info.cost = info.prevCost;
		info.cost = info.cost.add(next);
		
		compInfo.assignments[clusterIndex] = Arrays.copyOf(info.iterator.getCurrentSolution(), info.vars.length);
		
		if (DEBUG) {
			System.out.print("Path: ");
			for (int i = 0; i <= clusterIndex; i++) 
				System.out.print(compInfo.order[i][0] + "=" + compInfo.assignments[i][0] + ", ");
			System.out.println("\tcost = " + info.cost);
			System.out.println(this.problem.getAgent() + "'s new NCCC count: " + this.problem.getNCCCs() + "\n");
		}
		
		return compInfo.assignments[clusterIndex];
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

	/** 
	 * @see StatsReporterWithConvergence#getCurrentSolution()
	 */
	public Map<String, V> getCurrentSolution() {
		/// @todo Auto-generated method stub
		assert false: "Not Implemented";
		return null;
	}
}
