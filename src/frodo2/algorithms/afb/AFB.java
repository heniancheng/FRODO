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

package frodo2.algorithms.afb;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.lang.reflect.Array;
import java.util.Iterator;

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

/** The AFB algorithm by Gershman, Meisels, and Zivan (JAIR'09)
 * @author Alexandra Olteanu, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class AFB < V extends Addable<V>, U extends Addable<U> > implements StatsReporterWithConvergence<V>
{	
	/** If set to true, information about each sent and received message will be printed to the console.*/
	private final boolean verbose = false;
	
	/** The type of the message telling AFB to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** The types of the messages containing the chosen linear order of clusters of variables */
	public static String ORDER_MSG_TYPE = OrderMsg.ORDER_MSG_TYPE;
	
	/** The types of the messages containing the chosen linear order of clusters of variables sent to the stats gatherer */
	public static String ORDER_STATS_MSG_TYPE = OrderMsg.STATS_MSG_TYPE;

	/** The type of the FB_CPA messages, sent to request estimations from unassigned agents.*/
	public static final String FB_CPA_TYPE = "FB_CPA";

	/** The type of the CPA messages, sent to the next variable in the ordering.*/
	public static final String CPA_MSG_TYPE = "CPA_MSG";
	
	/** The type of the FB_ESTIMATE messages, sent as response to a FB_CPA message.*/
	public static final String FB_ESTIMATE_TYPE = "FB_ESTIMATE";

	/** The type of the message containing the optimal solution found */
	public static final String OUTPUT_MSG_TYPE = "Solution";
	
	/** The type of the messages broadcast by the last variable containing the current upper bound */
	public static final String UB_MSG_TYPE = "UB"; 
		
	/** The type of the message containing the optimal solution found sent to the stats gatherer */
	public static final String STATS_MSG_TYPE = "SolutionStats";
		
	/** The type of the message reporting the convergence for a given component */
	private static final String CONV_STATS_MSG_TYPE = "Convergence";
	
			
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
		 * @param history 	The history of assignments to variables
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
			
			/** The variables' domains */
			private V[][] domains;
			
			/** The current Iterator over the solutions of the space associated with this cluster */
			private UtilitySolutionSpace.SparseIterator<V, U> iterator = null;
			
			/** The agent owning the previous variable in the ordering */
			private String prevAgent;
						
			/** The agent owning the next variable in the ordering */
			private String nextAgent;
			
			/** The constraints this cluster of variables is responsible for enforcing */
			private ArrayList< UtilitySolutionSpace<V, U> > spaces = new ArrayList< UtilitySolutionSpace<V, U> > ();  
			
			/** Cost of the partial assignment up to and excluding this cluster */
			private U prevCost = zero;

			/** The last CPA sent down */
			private PA<V, U> cpa;			
			
			/**Timestamp for this CPA*/
			private Timestamp timestamp;
						
			/** The estimates received from lower priority agents */
			private U[] estimates; /// @todo There is no point in storing the estimates in an array; just add them up
			
			/** Variable assignment counter for the timestamp mechanism.*/
			private int assignmentCounter = 0;
			
			/** Constructor 
			 * @param vars 	list of variables in the cluster
			 */
			@SuppressWarnings("unchecked")
			public ClusterInfo (List<String> vars) {
				this.vars = vars.toArray(new String[vars.size()]);
				this.domains = (V[][]) Array.newInstance(problem.getDomain(vars.get(0)).getClass(), vars.size());
				for(int i = vars.size() - 1; i >= 0; i--) 
					this.domains[i] = problem.getDomain(vars.get(i));
				this.assignmentCounter = 0;
			}			
		}
		
		/** \c true when the convergence history is to be stored */
		private final boolean convergence;
		
		/** For each variable, its assignment history */
		private HashMap< String, ArrayList< CurrentAssignment<V> > > assignmentHistoriesMap;

		/** The information about a particular component of the constraint graph */
		private class ComponentInfo 
		{
			/** Indicates if a solution message was sent for this component to avoid duplication. */
			private boolean solutionSent = false;
			
			/** Array of ClusterInfos, in the same order as in the variable order used 
			 * (i.e. this array will have null entries for external variables) */
			private ArrayList<ClusterInfo> clusterInfos;
			
			/** For each internal cluster of variables in this component, its ClusterInfo */
			private HashMap<String, ClusterInfo> infoForCluster;
			
			/** For each internal variable, the index of its cluster in the order */
			private HashMap<String, Integer> varIndexes = new HashMap<String, Integer> ();
			
			/** Array of agents(strings), in the same order as in the variable order used.
			 * Defines the agents for a cluster.*/
			private ArrayList<String> clusterAgents;
			
			/** The variable order */
			private String[][] order;
			
			/** The computed h for each cluster in the ordering. 
			 * This is computed for each cluster owned by the current agent, and for each possible value for that cluster. */
			private UtilitySolutionSpace<V,U>[] h;
			
			/** The best solution found so far */
			private V[][] bestSol;
			
			/** The cost of the best solution found so far, upper bound */
			private U B = problem.getPlusInfUtility();

			/** The best known solution at various timestamps */
			private TreeMap<Long, V[][]> history;
			
			/** The set of all agents involved in this component */
			private HashSet<String> agents;
			
			/** Constructor
			 * @param order 	the variable order
			 */
			private ComponentInfo (List<List<String>> order) {
				this.order = new String[order.size()][];
				for(int i = order.size() - 1; i >= 0; i--){
					assert  order.get(i).size() > 0: "The cluster is empty!";
					this.order[i] = order.get(i).toArray(new String[order.get(i).size()]);
				}
				if (convergence) 
					this.history = new TreeMap<Long, V[][]> ();
			}
			
			/** Empty constructor */
			private ComponentInfo () {
				if (convergence) 
					this.history = new TreeMap<Long, V[][]> ();
			}
		}
		
		/** The information about each component in the constraint graph */
		private HashMap<Comparable<?>, ComponentInfo> compInfos;
		
		/** For each cluster of variables, its component ID */
		private HashMap <String, Comparable<?> > compOfCluster;
		
		/** This module's queue */
		private Queue queue;
		
		/** The problem */
		private DCOPProblemInterface<V, U> problem;
		
		/** Whether the stats gatherer should display the solution found */
		private boolean silent = false;
		
		/** Whether the module has already started the algorithm */
		private boolean started = false;
		
		/** Whether the module has already finished the algorithm */
		private boolean finished = false;

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
		
		/** For some clusters, a FbCpaMsg that was received before the var order message */
		private LinkedList< FbCpaMsg<V, U> > pendingFbCpaMsgs;
		
		/** For some clusters, a CPAmsg that was received before the var order message */
		private HashMap< String, CPAmsg<V, U> > pendingCPAmsgs;
		
		/** The constructor called in "statistics gatherer" mode
		 * @param parameters 	the description of what statistics should be reported (currently unused)
		 * @param problem 		the overall problem
		 */
		public AFB (Element parameters, DCOPProblemInterface<V, U> problem)  {
			this.problem = problem;
			this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
			this.convergence = false;
			this.assignmentHistoriesMap = new HashMap< String, ArrayList< CurrentAssignment<V> > > ();
			this.solution = new HashMap<String, V> ();
			this.pendingSolMsgs = new LinkedList< SolutionMsg<V, U> > ();
			this.pendingConvMsgs = new LinkedList< ConvergenceMessage<V> > ();
		}
		
		/** Constructor
		 * @param problem 		this agent's problem
		 * @param parameters 	the parameters for AFB
		 */
		public AFB (DCOPProblemInterface<V, U> problem, Element parameters) {
			this.problem = problem;
			
			String convergence = parameters.getAttributeValue("convergence");
			if(convergence != null)
				this.convergence = Boolean.parseBoolean(convergence);
			else
				this.convergence = false;
			
			this.pendingSolMsgs = new LinkedList< SolutionMsg<V, U> > ();
			this.pendingFbCpaMsgs = new LinkedList< FbCpaMsg<V, U> > ();
			this.pendingCPAmsgs = new HashMap< String, CPAmsg<V, U> > ();
		}
		
		/** Parses the problem */
		private void start () {
			this.compInfos = new HashMap<Comparable<?>, ComponentInfo> ();
			this.compOfCluster = new HashMap< String, Comparable<?> > ();
			this.solution = new HashMap<String, V> ();
			this.zero = this.problem.getZeroUtility();
			assert ! this.problem.maximize() : "AFB only supports minimization problems with non-negative costs";
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
			
			this.finished = true;
		}

		/** @see StatsReporterWithConvergence#getMsgTypes() */
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (8);
			types.add(START_MSG_TYPE);
			types.add(AgentInterface.AGENT_FINISHED);
			types.add(ORDER_MSG_TYPE);
			types.add(OUTPUT_MSG_TYPE);
			types.add(FB_CPA_TYPE);
			types.add(FB_ESTIMATE_TYPE);
			types.add(CPA_MSG_TYPE);
			types.add(UB_MSG_TYPE);
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
		public void notifyIn(Message msg) 
		{	
			String msgType = msg.getType();
			
			if (msgType.equals(STATS_MSG_TYPE)) { // the message containing the solution
				
				SolutionMsg<V, U> msgCast = (SolutionMsg<V, U>) msg;
				if (verbose) 	System.out.println("received SOLUTION MESSAGE with cost "+ msgCast.cost);
				processStatsMsg(msgCast);
				
				return;
			}
			
			else if (msgType.equals(ORDER_STATS_MSG_TYPE)) { // a stats message containing the linear order on variables
				
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
			
			else if (msgType.equals(CONV_STATS_MSG_TYPE)) { // a stats message containing the history of best solutions for a given component
				
				ConvergenceMessage<V> msgCast = (ConvergenceMessage<V>) msg;
				ComponentInfo compInfo = this.compInfos.get(msgCast.compID);
				
				// Postpone this message if we have not received the corresponding linear order yet
				if (compInfo == null) {
					this.pendingConvMsgs.add(msgCast);
					return;
				}
				
				// Initialize the histories map
				String[][] clusters = compInfo.order;
				int nbrClusters = clusters.length;
				for (String[] cluster : clusters)
					for (String var: cluster)
						this.assignmentHistoriesMap.put(var, new ArrayList< CurrentAssignment<V> > ());
				
				// Fill in the history for each variable
				for (Map.Entry<Long, V[][]> entry : msgCast.history.entrySet()) {
					Long time = entry.getKey();
					V[][] sol = entry.getValue();
					
					for (int i = nbrClusters - 1; i >= 0; i--) {
						String[] cluster = compInfo.order[i];
						V[] soli = sol[i];
						for (int j = cluster.length - 1; j >= 0; j--)
							this.assignmentHistoriesMap.get(cluster[j]).add(new CurrentAssignment<V>(time, soli[j]));
					}
				}
				
				return;
			}
			
			if(this.finished)
				return;
			
			if (! this.started) 
				this.start();
			
			if (msgType.equals(AgentInterface.AGENT_FINISHED)) 
			{
				this.reset();
				return;
			}
			
			else if (msgType.equals(ORDER_MSG_TYPE)) { // a message containing the chosen linear ordering on variables
				
				// Retrieve the information from the message
				OrderMsg<V, U> msgCast = (OrderMsg<V, U>) msg;
				Comparable<?> componentID = msgCast.getComponentID();
				
				ComponentInfo compInfo = this.compInfos.get(componentID);
				
				processLinearOrdering(msgCast, msgCast.getAgents(), componentID, msgCast.getOrder(), compInfo);
			}
			
			else if (msgType.equals(AFB.FB_CPA_TYPE)) 
			{
				// Retrieve the information from the message
				FbCpaMsg<V, U> msgCast = (FbCpaMsg<V, U>) msg;
				Comparable<?> componentID = this.compOfCluster.get(msgCast.dest);
				
				if(componentID == null){ // the cluster order message has not been received yet
					this.pendingFbCpaMsgs.add(msgCast);
					return;
				}
				
				ComponentInfo compInfo = this.compInfos.get(componentID);
				int clusterIndex = compInfo.varIndexes.get(msgCast.dest);
				ClusterInfo info = compInfo.clusterInfos.get(clusterIndex);
				
				// Discard out of date messages. This only makes sense if this is not the first variable in the ordering.
				if (clusterIndex>0 && info.timestamp.compare(msgCast.timestamp, clusterIndex-1)>0) 
					return;
				else //Update the info.timestamp based on this last processed message.
					info.timestamp = msgCast.timestamp;
								
				processFbCpa(msgCast, compInfo, clusterIndex, info);
				
			} // if (msgType.equals(AFB.FB_CPA_TYPE)) 

			
			else if (msgType.equals(AFB.FB_ESTIMATE_TYPE))
			{
				// Retrieve the information from the message
				FbEstimateMsg<V, U> msgCast = (FbEstimateMsg<V, U>) msg;
				Comparable<?> componentID = this.compOfCluster.get(msgCast.dest);
				
				assert componentID != null: "That impossible to receive a FB_ESTIMATE msg if we have not received the var order msg of the component!!";
				
				ComponentInfo compInfo = this.compInfos.get(componentID);
				int clusterIndex = compInfo.varIndexes.get(msgCast.dest);
				ClusterInfo info = compInfo.clusterInfos.get(clusterIndex);

				// Discard out of date messages.
				if (info.timestamp.compare(msgCast.timestamp, clusterIndex)>0)
					return;
				else // Update the local timestamp based on this last processed message.
					info.timestamp = msgCast.timestamp;

				processFbEstimate(msgCast, componentID, compInfo, clusterIndex, info);
				
			} // if (msgType.equals(AFB.FB_ESTIMATE_TYPE))
			
			
			else if (msgType.equals(AFB.CPA_MSG_TYPE))
			{
				// Retrieve the information from the message
				CPAmsg<V, U> msgCast = (CPAmsg<V, U>) msg;
				Comparable<?> componentID = this.compOfCluster.get(msgCast.dest);
				
				if(componentID == null){ // the cluster order message has not been received yet
					CPAmsg<V,U> oldMsg = this.pendingCPAmsgs.get(msgCast.dest);
					if(oldMsg == null || msgCast.timestamp.compare(oldMsg.timestamp, msgCast.pa.index)>0) 
						this.pendingCPAmsgs.put(msgCast.dest, msgCast);
					return;
				}
				
				ComponentInfo compInfo = this.compInfos.get(componentID);
				int varIndex = compInfo.varIndexes.get(msgCast.dest);
				ClusterInfo info = compInfo.clusterInfos.get(varIndex);
				
				// Discard out of date messages. 
				if (info.timestamp.compare(msgCast.timestamp, varIndex)>0) 
					return;
				else // Update the local timestamp based on this last processed message.
					info.timestamp = msgCast.timestamp;
				
				processCpaMsg(msgCast, componentID, compInfo, varIndex, info);
				
			} // if (msgType.equals(AFB.CPA_MSG_TYPE))			
			
			
			else if (msgType.equals(AFB.UB_MSG_TYPE)) 
			{	
				SolutionMsg<V, U> msgCast = (SolutionMsg<V, U>) msg;
				ComponentInfo compInfo = this.compInfos.get(msgCast.componentID);
				
				if (verbose) System.out.println("Received UB_MSG with cost = "+msgCast.cost);
				
				if (compInfo.B.compareTo(msgCast.cost) > 0) 
				{				
					compInfo.bestSol = msgCast.solution;
					compInfo.B = msgCast.cost;
								
					// Record the current best solution if required
					if (convergence)	 
						compInfo.history.put(this.queue.getCurrentTime(), compInfo.bestSol.clone());
				}
				
			} // if (msgType.equals(AFB.UB_MSG_TYPE))
			

			else if (msgType.equals(OUTPUT_MSG_TYPE)) { // the solution for a particular component of the constraint graph
				
				// Record the solution
				SolutionMsg<V, U> msgCast = (SolutionMsg<V, U>) msg;
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
				
				if (this.solution.size() == this.problem.getNbrIntVars()) 
					this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			}
		}

		/**
		 * @param msgCast The solution message.
		 */
		private void processStatsMsg(SolutionMsg<V, U> msgCast)
		{
			// Defer the message if we have not received the linear order for this component yet
			ComponentInfo compInfo = this.compInfos.get(msgCast.componentID);
			if (compInfo == null) {
				this.pendingSolMsgs.add(msgCast);
				return;
			}
			
			// Record the solution
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
				
				this.optCost = this.problem.getUtility(solution).getUtility(0);
				
				if (! this.silent) 
					System.out.println("Total optimal " + (this.problem.maximize() ? "utility: " : "cost: ") + this.optCost);				
			}
			return;
		}

		/**
		 * @param msgCast 		A message of type CPA_MSG_TYPE containing the current PA
		 * @param componentID 	The current componentID
		 * @param compInfo 		The ComponentInfo corresponding to componentID 
		 * @param clusterIndex	The index of the current cluster
		 * @param info			The ClusterInfo corresponding to clusterIndex
		 */
		private void processCpaMsg(CPAmsg<V, U> msgCast,
				Comparable<?> componentID, ComponentInfo compInfo,
				int clusterIndex, ClusterInfo info) 
		{
			info.cpa = msgCast.pa;
			if (verbose) 	
				System.out.println("received CPA_MSG on " + Arrays.toString(compInfo.order[clusterIndex]) +" with "+msgCast.pa.toString()
						+" and ITERATOR_" + Arrays.toString(compInfo.order[clusterIndex]) 
						+"="+ (info.iterator == null ? "null" : Arrays.toString(info.iterator.getCurrentSolution())));
			
			// If this CPA was sent from a variable with higher priority, then I should reset my index and start over the domain;
			// Otherwise it must have been a backtrack message and my index should remain as it is. 
			if (compInfo.varIndexes.get(msgCast.sender) < compInfo.varIndexes.get(msgCast.dest)) { // CPA message from higher priority agent.
				info.iterator = null;
				info.prevCost = msgCast.pa.c;
			}
			
			U costUpToCurrentCluster;
			if (clusterIndex > 0)
			{
				if (info.cpa.assignments[clusterIndex] != null && info.cpa.assignments[clusterIndex].length > 0) 
					// if there is an assignment at clusterIndex, do not count it
				{
					costUpToCurrentCluster = info.prevCost;
				}
				else  // no assignment at clusterIndex means c is up to date
				{
					costUpToCurrentCluster = info.cpa.c;	
				}
			}
			else
				costUpToCurrentCluster = this.zero;
			if (compInfo.B.compareTo(costUpToCurrentCluster) <= 0)
				backtrack(componentID, compInfo, info, clusterIndex);					
			else
				assign_CPA(componentID, compInfo, clusterIndex);
		}

		/**
		 * @param msgCast		A message of type FB_ESTIMATE
		 * @param componentID 	The current componentID
		 * @param compInfo 		The ComponentInfo corresponding to componentID 
		 * @param clusterIndex	The index of the current cluster
		 * @param info			The ClusterInfo corresponding to clusterIndex
		 */
		private void processFbEstimate(FbEstimateMsg<V, U> msgCast,
				Comparable<?> componentID, ComponentInfo compInfo,
				int clusterIndex, ClusterInfo info) 
		{
			if (verbose) System.out.println("Received FB_ESTIMATE "+" from "+msgCast.sender+" to "+msgCast.dest+" for PA: "+msgCast.pa.toString()+" with estimate="+msgCast.estimate);
		
			// save the estimate from this agent into the current variable's estimates vector
			info.estimates[compInfo.varIndexes.get(msgCast.sender) - clusterIndex - 1] = msgCast.estimate;
		
			// check if the current cost + received estimates does not result in a full assignment with cost worse then the known upper bound B
			U totalCost = msgCast.pa.c;
			for (int i = info.estimates.length - 1; i >=0; i--) 
			{
				if (info.estimates[i] != null)
				{
					totalCost = totalCost.add(info.estimates[i]);
					if (totalCost.compareTo(compInfo.B)>=0)
					{
						assign_CPA(componentID, compInfo, clusterIndex);
						break;
					}
				}
			}
		}

		/**
		 * @param msgCast       A message of type FB_CPA.
		 * @param compInfo 		The ComponentInfo corresponding to componentID 
		 * @param clusterIndex	The index of the current cluster
		 * @param info			The VarInfo corresponding to varIndex
		 */
		private void processFbCpa(FbCpaMsg<V, U> msgCast, ComponentInfo compInfo, int clusterIndex, ClusterInfo info) 
		{
			
			// compute lower bound estimation based on the received assignment
			if (verbose) System.out.println("Received FB_CPA from "+msgCast.sender+" to " +msgCast.dest+" with PA: "+msgCast.pa.toString());
			
			U f = f(clusterIndex, compInfo, msgCast.pa).blindProjectAll(false);
			
			// send FB_ESTIMATE response message
			int destVarIndex = compInfo.varIndexes.get(msgCast.sender);
			if (verbose)  System.out.println("Sent FB_ESTIMATE from "+ msgCast.dest+" to "+msgCast.sender+" with "+msgCast.pa.toString()+" estimate="+f);
			this.queue.sendMessage(compInfo.clusterAgents.get(destVarIndex), 
				new FbEstimateMsg<V,U>(msgCast.sender, msgCast.dest, msgCast.pa.clone(),info.timestamp.clone(), f));
		}

		/**
		 * @param msg 			A message of type ORDER_MSG_TYPE 
		 * @param agents		The agent list received.
		 * @param componentID 	The componentID of the component for which we received the linear ordering.
		 * @param order			The variable ordering received.
		 * @param compInfo		The ComponentInfo corresponding to componentID
		 */
		@SuppressWarnings({ "unchecked" })
		private void processLinearOrdering(OrderMsg<V, U> msg, 
				List<String> agents,
				Comparable<?> componentID, 
				List<List<String>> order,
				ComponentInfo compInfo) 
		{	
			if (compInfo == null) 
			{
				compInfo = new ComponentInfo (order);
				this.compInfos.put(componentID, compInfo);
			} else 
			{
				compInfo.order = new String[order.size()][];
				for(int i = order.size() - 1; i >= 0; i--){
					assert  order.get(i).size() > 0: "The cluster is empty!";
					compInfo.order[i] = order.get(i).toArray(new String[order.get(i).size()]);
				}
			}
			
			List<String> owners = agents;
			compInfo.agents = new HashSet<String> (owners);
			final int nbrClusters = owners.size();
			
			if (compInfo.h == null) // only initialize if not already done
				compInfo.h = new UtilitySolutionSpace[nbrClusters];

			// Initialize the information about all variables agents
			compInfo.clusterAgents = new ArrayList<String> (nbrClusters);
			
			// Initialize the information about my internal variables
			compInfo.clusterInfos = new ArrayList<ClusterInfo> (nbrClusters);
			compInfo.infoForCluster = new HashMap<String, ClusterInfo> (nbrClusters);
			Set<String> myVars = this.problem.getMyVars();
			HashSet<String> nextVars = new HashSet<String> (this.problem.getVariables());
			for (int i = 0; i < nbrClusters; i++) 
			{
				List<String> cluster = order.get(i);
				
				String firstVar = cluster.get(0);
				for(String var: cluster){
					nextVars.remove(var);
					compInfo.varIndexes.put(var, i);
				}
				
				// Clusters are named after their first variable
				this.compOfCluster.put(firstVar, componentID);
				
				compInfo.clusterAgents.add(owners.get(i));
				
				// Skip this whole cluster if it is external
				if (! myVars.contains(firstVar)) {
					compInfo.clusterInfos.add(null);
					continue;
				}
				
				ClusterInfo info = new ClusterInfo(cluster);
				compInfo.clusterInfos.add(info);
				compInfo.infoForCluster.put(firstVar, info);
				
				info.timestamp = new Timestamp(nbrClusters);
				info.estimates = (U[])Array.newInstance(this.zero.getClass(), nbrClusters-i-1);
				
				if (i > 0) 
					info.prevAgent = owners.get(i - 1);
				if (i < nbrClusters - 1) 
					info.nextAgent = owners.get(i + 1);
				
				// Find the spaces this variable is responsible for enforcing
				// i.e. the spaces for which it is the last variable in the ordering (to avoid double counting constraints when estimating future costs)
				HashSet<String> vars = new HashSet<String>(cluster);
				for (UtilitySolutionSpace<V, U> space : this.problem.getSolutionSpaces(vars, false, nextVars)) 
					info.spaces.add(space);			
				
				// compute h for this variable
				compInfo.h[i] = this.h(i, compInfo);
			} // for all variables
			
			if(this.valArrayClass == null){
				valArrayClass = (Class<V[]>) problem.getDomain(myVars.iterator().next()).getClass();
				valClass = (Class<V>) problem.getDomain(myVars.iterator().next())[0].getClass();
			}
			
			// If I own the first variable in the ordering
			if (compInfo.clusterInfos.get(0) != null) 
				this.initiate(componentID, compInfo);
				
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
				if (solMsg.getCompID().equals(componentID)) {
					iter.remove();
					this.notifyIn(solMsg);
					break;
				}
			}
			
			// Handle the pending CPA message, if any
			for (String[] cluster : compInfo.order) {
				CPAmsg<V, U> pending = this.pendingCPAmsgs.remove(cluster[0]);
				if (pending != null) {
					this.notifyIn(pending);
					break;
				}
			}
			
			// Handle the pending FbCPa message, if any
			LinkedList< FbCpaMsg<V, U> > listCopy = new LinkedList< FbCpaMsg<V, U> > (this.pendingFbCpaMsgs);
			this.pendingFbCpaMsgs.clear();
			for (FbCpaMsg<V, U> fbCpaMsg : listCopy) {
				this.notifyIn(fbCpaMsg);
			}
			
		}
		
		
		/** Chooses a first value for the first variable and starts the algorithm 
		 * @param compID 	the ID of the component in the constraint graph
		 * @param compInfo 	the information about the component in the constraint graph 
		 */
		private void initiate (Comparable<?> compID, ComponentInfo compInfo) 
		{
			compInfo.B = this.problem.getPlusInfUtility();
			
			// create empty CPA and assign a value to the first variable
			ClusterInfo info = compInfo.clusterInfos.get(0);
			info.cpa = new PA<V, U> (compInfo.order.length, compInfo.clusterInfos.get(0).domains[0][0].getClass(), this.zero.getClass(), this.zero);
			assign_CPA(compID, compInfo, 0);
		}
		

		/**
		 * @param i			The index of the cluster to compute h for
		 * @param compInfo	the ComponentInfo of the component this variable belongs to
		 * @return			h for each possible value from the variable domain. 
		 * 					The indexing of this array is the same as the indexing of the variable domain.
		 */
		@SuppressWarnings("unchecked")
		private UtilitySolutionSpace<V,U> h(final int i, ComponentInfo compInfo)
		{
			/* CONCEPT: 
			 * 1) Get all spaces involving the i'th variable, no previous variable, and at least one subsequent variable
			 * 2) Blindly project all variables except the i'th out of each such space
			 * 2) Join all these projection outputs together
			 */
			
			if(this.valArrayClass == null){
				valArrayClass = (Class<V[]>) problem.getDomain(this.problem.getVariables().iterator().next()).getClass();
				valClass = (Class<V>) this.valArrayClass.getComponentType();
			}
			
			UtilitySolutionSpace<V,U> futureSpace = new ScalarHypercube<V,U>(this.zero, this.zero.getPlusInfinity(), this.valArrayClass);
			if (i==compInfo.order.length -1) // last cluster in the ordering
			{
				return futureSpace;
			}
			
			// Get all spaces involving the variables of the i'th cluster and no previous variables
			Set<String> prevVars = new HashSet<String>();
			Set<String> vars = new HashSet<String>(Arrays.asList(compInfo.order[i]));
			for (int x = i - 1; x >=0; x--)
				for (int y = compInfo.order[x].length - 1; y >=0; y--)
					prevVars.add(compInfo.order[x][y]);
			List <? extends UtilitySolutionSpace<V,U> > spaces = this.problem.getSolutionSpaces(vars, false, prevVars);
			if (spaces == null || spaces.size()==0)
			{
				return futureSpace;	
			}
			
			// Go through all spaces except the ones that only contain variables in the current cluster
			for (UtilitySolutionSpace<V,U> space : spaces) {
				
				// Skip this space if it only involves variables in the current cluster
				if (vars.containsAll(Arrays.asList(space.getVariables()))) 
					continue;
				
				// Blindly project all variables except the current one(s)
				ArrayList<String> otherVars = new ArrayList<String> (Arrays.asList(space.getVariables()));
				otherVars.removeAll(vars);
				futureSpace = futureSpace.join(space.blindProject(otherVars.toArray(new String [otherVars.size()]), false));
			}
			
			return futureSpace;
		}

		
		/** Method to compute the value f for a given cluster and some assigned value.
		 * @param clusterIndex 	Index of the cluster for which we want to compute f
		 * @param compInfo		The ComponentInfo of the component the variable belongs to
		 * @param pa			Current partial assignment.  
		 * @return				f = The sum of the cost that i has with variables already in the CPA + h(v) at the same value.
		 * */
		private UtilitySolutionSpace<V, U> f(int clusterIndex, ComponentInfo compInfo, PA<V,U> pa)
		{
			UtilitySolutionSpace<V, U> localSpace = this.getLocalSpace(compInfo, clusterIndex, pa, false);
			return compInfo.h[clusterIndex].join(localSpace);
		}

		
		/** Method to compute the local space for a given cluster of variables and an assigned values, as a function of the variables in the current cluster
		 * @param compInfo			The ComponentInfo of the current component.
		 * @param clusterIndex		Index of the cluster for which we want to get the local space
		 * @param pa				The PA 
		 * @param groundClusterVars Whether the variables in the current cluster should also be grounded
		 * @return					The local space associated with this cluster and PA index
		 */
		@SuppressWarnings("unchecked")
		private UtilitySolutionSpace<V,U> getLocalSpace (ComponentInfo compInfo, int clusterIndex, PA<V,U> pa, final boolean groundClusterVars)
		{
			UtilitySolutionSpace<V,U> localSpace = new ScalarHypercube<V,U>(this.zero, this.zero.getPlusInfinity(), this.valArrayClass);
			
			ClusterInfo cluster = compInfo.clusterInfos.get(clusterIndex);
			List<String> previousVars = new ArrayList<String>();
			List<V> previousVals = new ArrayList<V>();
			for(int i = pa.index - 1; i >= 0; i--){
				previousVars.addAll(Arrays.asList(compInfo.order[i]));
				previousVals.addAll(Arrays.asList(pa.assignments[i]));
			}
			if (groundClusterVars) {
				assert clusterIndex >= pa.index;
				previousVars.addAll(Arrays.asList(cluster.vars));
				previousVals.addAll(Arrays.asList(pa.assignments[clusterIndex]));
			}
			
			spaceLoop: for (UtilitySolutionSpace<V, U> space : cluster.spaces) 
			{	
				// Skip this space if not all previous variables are grounded; the variables in the current cluster are allowed to remain ungrounded
				for (String var : space.getVariables())
				{
					int index = compInfo.varIndexes.get(var);
					if (index > pa.index && index != clusterIndex)
						continue spaceLoop;
				}				
				localSpace = localSpace.join(space);
			}
			return localSpace.slice(previousVars.toArray(new String[previousVars.size()]), previousVals.toArray((V[])Array.newInstance(this.valClass, previousVals.size())));
		}

		/** Method to assign a value to a variable. 
		 * @param compID 	the ID of the component in the constraint graph
		 * @param compInfo 	the information about the component in the constraint graph
		 * @param clusterIndex	the index of the cluster for which we are assigning values
		 */
		private void assign_CPA(Comparable<?> compID, ComponentInfo compInfo, final int clusterIndex)
		{
			ClusterInfo info = compInfo.clusterInfos.get(clusterIndex);
			
			// clear estimates
			Arrays.fill(info.estimates, null); 

			// Optimization(time):
			// If we have not yet started to assign domain values for this variable
			// we can reset the local assignmentCounter
			if (info.cpa.assignments[clusterIndex] == null || info.cpa.assignments[clusterIndex].length == 0)
			{
				info.assignmentCounter = 0;
			}
			// if there exists an assignment of the variable varIndex, delete it and remove it from the cost.
			else
			{
				info.cpa.c = info.prevCost;
				info.cpa.assignments[clusterIndex]=null;
			}
			
			// If we don't already have an iterator over the solutions of this cluster, we create it
			if(info.iterator == null){
				info.iterator = f(clusterIndex, compInfo, info.cpa).sparseIter(info.vars, info.domains);
			}
			
			// iterate over the domain of the cluster to find next solution that is better than the current bound
			U bound = (clusterIndex == 0? compInfo.B : compInfo.B.subtract(info.cpa.c));
			U next = info.iterator.nextUtility(bound, true);

			if (next == null) // we ran out of values
				backtrack(compID, compInfo, info, clusterIndex);
			else 
			{
				// add the new values found to the assignment and adjust the cost
				info.cpa.assignments[clusterIndex] = info.iterator.getCurrentSolution().clone();
				info.cpa.index = clusterIndex;
				U add = next.subtract(compInfo.h[clusterIndex].getUtility(info.vars, info.iterator.getCurrentSolution())); // subtracting h back from f to retrieve the local cost
				U total = add;
				total = add.add(info.cpa.c);
				info.cpa.c = total;
				assert compInfo.B.compareTo(total) > 0 : compInfo.B + " > " + total;
				
				info.assignmentCounter ++;
				info.timestamp.setCounter(clusterIndex, info.assignmentCounter);
				
				// if this is the last cluster, broadcast new solution and update the upper bound B
				if (clusterIndex == compInfo.order.length - 1) 
				{ 
					// Record the best cost found so far
					compInfo.B = total;
					compInfo.bestSol = (V[][])info.cpa.assignments.clone();
					if (convergence)  
						compInfo.history.put(this.queue.getCurrentTime(), compInfo.bestSol.clone());
					
					// Broadcast the new upper bound to all agents
					if (verbose) System.out.println("Send UB message to all with B="+compInfo.B);
					this.queue.sendMessageToMulti(compInfo.agents, new SolutionMsg<V, U> (UB_MSG_TYPE, compID, compInfo.bestSol.clone(), compInfo.B));
					
					// We can already terminate if the best solution found has cost 0
					if (compInfo.B.equals(this.zero)) 
					{
						this.terminate(compID, compInfo);
						return;
					}
										
					// repeat trying the remaining values for this last cluster
					assign_CPA(compID, compInfo, clusterIndex);
				}
				else // this is not the last variable in the comp ordering
				{
					// send CPA_MSG to the next variable
					PA<V, U> pa = info.cpa.clone();
					pa.index++;
					if (verbose) 
						System.out.println("sent CPA_MSG from " + Arrays.toString(compInfo.order[clusterIndex]) +" to "+ Arrays.toString(compInfo.order[clusterIndex+1]) +" with "+pa.clone());
					this.queue.sendMessage(info.nextAgent, new CPAmsg<V, U> (compInfo.order[clusterIndex+1][0],compInfo.order[clusterIndex][0], pa,info.timestamp.clone()));
					
					//send FB_CPA to all variables with lower priority
					for (int j = compInfo.order.length - 1; j > clusterIndex; j--)
					{  
						if (verbose) System.out.println("Sent FB_CPA from "+ Arrays.toString(compInfo.order[clusterIndex]) +" to "+ Arrays.toString(compInfo.order[j]) +" with "+info.cpa.toString());
						this.queue.sendMessage(compInfo.clusterAgents.get(j), new FbCpaMsg<V, U> (compInfo.order[j][0], compInfo.order[clusterIndex][0], info.cpa.clone(),info.timestamp.clone())); /// @bug Why hasn't the PA index been incremented? 
					}
				}
			} // variable domain was not exhausted
		} // assign_CPA method			
					
		
		/**
		 * @param componentID 	the ID of the component in the constraint graph
		 * @param compInfo 		the information about the component in the constraint graph
		 * @param info			the CLusterInfo of the cluster for which we are assigning a value
		 * @param clusterIndex	the index of the cluster for which we are assigning values
		 */
		private void backtrack(Comparable<?> componentID, ComponentInfo compInfo, ClusterInfo info, int clusterIndex)
		{			
			// clear estimates for the current variable
			Arrays.fill(info.estimates, null);
			
			// Optimization(time): reset the assignment counter for this variable to 0
			info.timestamp.setCounter(clusterIndex, 0);
			
			if (clusterIndex == 0)
				terminate(componentID, compInfo);
			
			else {
				PA<V, U> pa = info.cpa.clone();
				if (pa.assignments[clusterIndex] != null && pa.assignments[clusterIndex].length > 0) 
				{
					// it is possible for a backtrack to be triggered even with no assignment 
					// (Eg: when B is exceeded in processCPA by the previous agents assignments)
					pa.c = info.prevCost;
					pa.assignments[clusterIndex]=null;
				}
				pa.index--;

				// reset the domain values for variable at position varIndex
				info.iterator = null;
				if (verbose) System.out.println("sent CPA_MSG from "+ Arrays.toString(compInfo.order[clusterIndex])
						+" to "+ Arrays.toString(compInfo.order[clusterIndex-1]) +" with "+pa.toString()
						+" VALINDEX_"+ Arrays.toString(compInfo.order[clusterIndex]) 
						+"=" + (info.iterator == null ? "null" : Arrays.toString(info.iterator.getCurrentSolution())));
				this.queue.sendMessage(info.prevAgent, new CPAmsg<V,U>(compInfo.order[clusterIndex-1][0],compInfo.order[clusterIndex][0], pa,info.timestamp.clone())); /// @todo Backtrack messages don't need to contain the CPA. 
			}							
		}
			
		
		/** Sends output and termination messages  
		 * @param compID 	the ID of the component in the constraint graph
		 * @param compInfo 	the information about the component in the constraint graph 
		 */
		private void terminate (Comparable<?> compID, ComponentInfo compInfo) 
		{			
			if (!compInfo.solutionSent) 
			{
				if (verbose) System.out.println("Sent solution message to all: cost="+compInfo.B);
				this.queue.sendMessageToMulti(compInfo.agents, new SolutionMsg<V, U> (OUTPUT_MSG_TYPE, compID, compInfo.bestSol, compInfo.B));
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V, U> (STATS_MSG_TYPE, compID, compInfo.bestSol, compInfo.B));
				compInfo.solutionSent = true;
			}
			
			if (this.convergence) 
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new ConvergenceMessage<V> (compID, compInfo.history));
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
