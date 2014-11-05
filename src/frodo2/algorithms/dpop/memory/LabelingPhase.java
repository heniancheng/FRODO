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

/** MB-DPOP's memory-bounded version of DPOP */
package frodo2.algorithms.dpop.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** MB-DPOP's labeling phase that identifies cluster roots (CRs) and cycle-cut (CC) nodes. 
 * 
 * Adrian Petcu and Boi Faltings. MB-DPOP: A new memory-bounded algorithm for distributed optimization. 
 * In Manuela M. Veloso, editor, Proceedings of the Twentieth International Joint Conference on Artificial Intelligence (IJCAI'07), 
 * pages 1452-1457, Hyderabad, India, January 6-12 2007.
 * 
 * @author Thomas Leaute
 * 
 * @param <V> the type used for variable values
 */
public class LabelingPhase < V extends Addable<V> > implements StatsReporter {
	
	/** A class containing all the useful information about one of the agent's variables */
	private class VarInfo {
		
		/** The variable name */
		private final String name;
		
		/** The variable's view of the DFS */
		private DFSview<V, ?> dfsView;
		
		/** The neighboring variables */
		private final Collection<String> neighbors;
		
		/** The variable's separator */
		private final HashMap<String, V[]> sep = new HashMap<String, V[]> ();
		
		/** The CC nodes below this variable, with their domains */
		private final HashMap<String, V[]> ccs = new HashMap<String, V[]> ();
		
		/** The number of label messages we are still waiting for from children */
		private int nbrMsgsLeft = 0;
		
		/** Constructor 
		 * @param name 			The variable name
		 * @param neighbors 	The neighboring variables
		 */
		private VarInfo (String name, Collection<String> neighbors) {
			this.name = name;
			this.neighbors = neighbors;
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			return "VarInfo:\n\t var = " + this.name + "\n\t dfsView = " + this.dfsView + "\n\t neighbors = " + (this.neighbors == null ? null : this.neighbors) + "\n\t sep = " + this.sep + "\n\t css = " + this.ccs;
		}
	}

	/** The type of the stats messages */
	private static final String STATS_MSG_TYPE = "ClusterStats";
	
	/** A message containing stats about a variable 
	 * @param <V> the type used for variable values
	 */
	public static class StatsMsg < V extends Addable<V> > extends MessageWith3Payloads< String, DFSview<V, ?>, HashSet<String> > {
		
		/** Empty constructor for externalization */
		public StatsMsg () { }
		
		/** Constructor
		 * @param var 		the variable name
		 * @param dfsView 	the variable's DFSview
		 * @param ccs 		the cycle cutset nodes this variable is involved with
		 */
		private StatsMsg (String var, DFSview<V, ?> dfsView, HashSet<String> ccs) {
			super (STATS_MSG_TYPE, var, dfsView, ccs);
		}
	}
	
	/** The type of the output messages */
	public static final String OUTPUT_MSG_TYPE = "LabelingPhaseOutput";
	
	/** The output message for a given variable 
	 * @param <V> the type used for variable values
	 */
	public static class OutputMsg < V extends Addable<V> > extends MessageWith2Payloads< String, HashMap<String, V[]> > {
		
		/** Empty constructor for externalization */
		public OutputMsg () { }
		
		/** Constructor
		 * @param var 		the name of the variable
		 * @param ccs 		the set of cycle-cutset nodes (and their domains) in the cluster rooted at the variable; if empty, the variable is not the CR; if \c null, the variable is not part of any cluster
		 */
		private OutputMsg (String var, HashMap<String, V[]> ccs) {
			super(OUTPUT_MSG_TYPE, var, ccs);
		}
	}
	
	/** The agent's queue */
	private Queue queue;
	
	/** The agent's subproblem */
	private DCOPProblemInterface<V, ?> problem;

	/** The maximum number of variables in a UTIL message */
	private final short maxDim;

	/** Whether the algorithm has already started */
	private boolean started = false;
	
	/** Each interval variable's VarInfo */
	private HashMap<String, VarInfo> varInfos;

	/** Whether to display the DFS with the clusters */
	private boolean silent = false;

	/** The name of the class of the DOT renderer */
	private String dotRendererClass = "";
	
	/** Constructor
	 * @param problem 	the agent's subproblem
	 * @param params 	the module parameters
	 */
	public LabelingPhase (DCOPProblemInterface<V, ?> problem, Element params) {
		this.problem = problem;
		this.maxDim = Short.parseShort(params.getAttributeValue("maxDim"));
		assert this.maxDim > 0 : "maxDim = " + this.maxDim + " <= 0";
	}
	
	/** Constructor in stats gatherer mode
	 * @param params 	the module parameters
	 * @param problem 	the overall problem
	 */
	public LabelingPhase (Element params, DCOPProblemInterface<V, ?> problem) {
		this.problem = problem;
		this.silent = ! Boolean.parseBoolean(params.getAttributeValue("reportStats"));
		this.dotRendererClass = params.getAttributeValue("DOTrenderer");
		this.maxDim = 0;
		this.varInfos = new HashMap<String, VarInfo> (this.problem.getNbrVars());
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		
		return Arrays.asList(
				AgentInterface.START_AGENT, 
				DFSgeneration.OUTPUT_MSG_TYPE, 
				LabelMsg.LABEL_MSG_TYPE);
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(STATS_MSG_TYPE, this);
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent  = silent;
	}

	/** @see StatsReporter#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
				
		if (msgType.equals(STATS_MSG_TYPE)) {
			
			@SuppressWarnings("unchecked")
			StatsMsg<V> msgCast = (StatsMsg<V>) msg;
			String var = msgCast.getPayload1();
			
			VarInfo varInfo = new VarInfo (var, null);
			this.varInfos.put(var, varInfo);
			varInfo.dfsView = msgCast.getPayload2();
			for (String cc : msgCast.getPayload3()) 
				varInfo.ccs.put(cc, null);
			
			if (!this.silent && this.varInfos.size() >= this.problem.getNbrVars()) 
				this.printDFS();
			
			return;
		}
		
		if (! this.started) 
			this.start();
		
//		System.out.println(msg);

		if (msgType.equals(AgentInterface.START_AGENT)) 
			return;
		
		VarInfo varInfo = null;
		
		if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { 
			
			@SuppressWarnings("unchecked")
			MessageDFSoutput<V, ?> msgCast = (MessageDFSoutput<V, ?>) msg;
			DFSview<V, ?> dfsView = msgCast.getNeighbors();
			
			varInfo = this.varInfos.get(msgCast.getVar());
			varInfo.dfsView = dfsView;
			varInfo.nbrMsgsLeft += dfsView.getChildren().size();
			
			// Start computing the separator
			HashSet<String> sep = new HashSet<String> (varInfo.neighbors);
			sep.removeAll(dfsView.getChildren());
			sep.removeAll(dfsView.getAllPseudoChildren());
			for (String var : sep) 
				varInfo.sep.put(var, this.problem.getDomain(var));
			
			
		} else if (msgType.equals(LabelMsg.LABEL_MSG_TYPE)) {
			
			@SuppressWarnings("unchecked")
			LabelMsg<V> msgCast = (LabelMsg<V>) msg;
			String myVar = msgCast.getDest();
			varInfo = this.varInfos.get(myVar);
			
			// Get the separator for this child, augmented with its CC variables
			String child = msgCast.getSender();
			HashMap<String, V[]> childSep = msgCast.getSep();
			HashMap<String, V[]> childCCs = msgCast.getCCs();
			HashSet<String> augmentedSep = new HashSet<String> (childSep.keySet());
			augmentedSep.addAll(childCCs.keySet());
			this.queue.sendMessageToSelf(new UTILpropagation.SeparatorMessage (
					child, myVar, augmentedSep.toArray(new String [augmentedSep.size()]), this.problem.getOwner(child)));
			
			// Update my separator and my CCs
			childSep.remove(myVar); // removing myself from my separator
			varInfo.sep.putAll(childSep);
			varInfo.ccs.putAll(childCCs); /// @todo This is not optimal; subtrees should coordinate to choose common CCs
			
			varInfo.nbrMsgsLeft--;
		}
		
		
		if (varInfo.nbrMsgsLeft != 0) // we haven't received all messages yet
			return;
		
		String parentVar = varInfo.dfsView.getParent();
		String parentOwner = (parentVar == null ? null : this.problem.getOwner(parentVar));
		
		// Check whether I am cluster root
		if (! varInfo.ccs.isEmpty() && varInfo.sep.size() <= this.maxDim && Collections.disjoint(varInfo.sep.keySet(), varInfo.ccs.keySet())) { // CR
			
			this.queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMsg<V> (varInfo.name, varInfo.dfsView, new HashSet<String> (varInfo.ccs.keySet())));
			this.queue.sendMessageToSelf(new OutputMsg<V> (varInfo.name, new HashMap<String, V[]> (varInfo.ccs)));
			
			varInfo.ccs.clear();
			
		} else { // not CR

			// Choose the new CCs, if any
			/// @todo Make the heuristic customizable
			TreeSet<String> nonCCs = new TreeSet<String> (varInfo.sep.keySet());
			nonCCs.removeAll(varInfo.ccs.keySet());
			int sepSize = nonCCs.size();
			if (sepSize > this.maxDim /*&& ! parentOwner.equals(this.problem.getAgent())*/) { /// @todo Ignore virtual messages once the output of project() has been made intensional
				for (Iterator<String> iter = nonCCs.iterator(); sepSize > this.maxDim; sepSize--) {
					String var = iter.next();
					varInfo.ccs.put(var, varInfo.sep.get(var));
				}
			}
			
			this.queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMsg<V> (varInfo.name, varInfo.dfsView, new HashSet<String> (varInfo.ccs.keySet())));
			
			if (varInfo.ccs.isEmpty()) // not part of any cluster
				this.queue.sendMessageToSelf(new OutputMsg<V> (varInfo.name, null));
			else
				this.queue.sendMessageToSelf(new OutputMsg<V> (varInfo.name, new HashMap<String, V[]> ()));
		}
		
		// Send to parent, if any
		if (parentVar != null) 
			this.queue.sendMessage(parentOwner, new LabelMsg<V> (varInfo.name, parentVar, varInfo.sep, varInfo.ccs));
	}

	/** Prints the DFS with the clusters */
	private void printDFS () {
		
		// Compute the clusters
		ArrayList< HashSet<String> > clusters = new ArrayList< HashSet<String> > (this.problem.getNbrVars());
		HashSet<String> ccs = new HashSet<String> (this.problem.getNbrVars());
		for (VarInfo varInfo : this.varInfos.values()) {
			
			// Look for a cluster involving this variable or any of the CCs it is involved in
			HashSet<String> myCluster = null;
			ccs.addAll(varInfo.ccs.keySet());
			varInfo.ccs.put(varInfo.name, null);
			for (HashSet<String> cluster : clusters) {
				if (! Collections.disjoint(cluster, varInfo.ccs.keySet())) {
					cluster.addAll(varInfo.ccs.keySet());
					myCluster = cluster;
					break;
				}
			}
			
			// Create a new cluster if necessary
			if (myCluster == null) {
				myCluster = new HashSet<String> ();
				myCluster.addAll(varInfo.ccs.keySet());
				clusters.add(myCluster);
			}
		}
		
		StringBuilder builder = new StringBuilder ("digraph {\n\tnode [shape = \"circle\"];\n\n");
		
		// Go through the list of clusters found
		for (HashSet<String> cluster : clusters) {
			
			if (cluster.size() > 1) // multi-variable cluster
				builder.append("\tsubgraph cluster_" + cluster.hashCode() + " {\n");
			
			for (String var : cluster) {
				builder.append("\t" + var);
				if (! ccs.contains(var)) 
					builder.append(" [style=\"filled\"]");
				builder.append(";\n");
			}
			
			if (cluster.size() > 1) 
				builder.append("\t}\n\n");
			
			// Print the edges
			for (String var : cluster) {
				DFSview<V, ?> dfsView = this.varInfos.get(var).dfsView;
				
				// Print the edge with the parent, if any
				String parent = dfsView.getParent();
				if (parent != null) {
					builder.append("\t" + parent + " -> " + var + ";\n");
					
					assert this.varInfos.get(parent).dfsView.getChildren().contains(var) : 
						parent + " is a parent of " + var + " but " + var + " is not a child of " + parent;
					assert ! dfsView.getChildren().contains(parent) : 
						parent + " is both a parent and a child of " + var;
					assert ! dfsView.getPseudoParents().contains(parent) : 
						parent + " is both a parent and a pseudo-parent of " + var;
					assert ! dfsView.getAllPseudoChildren().contains(parent) : 
						parent + " is both a parent and a pseudo-child of " + var;
				}

				// Print the edges with the pseudo-parents, if any
				for (String pseudo : dfsView.getPseudoParents()) {
					builder.append("\t" + pseudo + " -> " + var + " [style = \"dashed\" arrowhead = \"none\" weight=\"0.5\"];\n");
					
					assert this.varInfos.get(pseudo).dfsView.getAllPseudoChildren().contains(var) : 
						pseudo + " is a pseudo-parent of " + var + " but " + var + " is not a pseudo-child of " + pseudo;
					assert ! dfsView.getChildren().contains(pseudo) : 
						pseudo + " is both a pseudo-parent and a child of " + var;
					assert ! dfsView.getAllPseudoChildren().contains(pseudo) : 
						pseudo + " is both a pseudo-parent and a pseudo-child of " + var;
				}
				
				// Check the children, if any
				for (String child : dfsView.getChildren()) {
					assert var.equals(this.varInfos.get(child).dfsView.getParent()) : 
						child + " is a child of " + var + " but " + var + " is not a parent of " + child;
					assert ! dfsView.getAllPseudoChildren().contains(child) : 
						child + " is both a child and a pseudo-child of " + var;
				}

				// Check the pseudo-children, if any
				for (String pseudo : dfsView.getAllPseudoChildren()) {
					assert this.varInfos.get(pseudo).dfsView.getPseudoParents().contains(var) : 
						pseudo + " is a pseudo-child of " + var + " but " + var + " is not a pseudo-parent of " + pseudo;
				}

				builder.append("\n");
			}
		}
		
		builder.append("}\n");
		
		if(dotRendererClass.equals("")) {
			System.out.println("Chosen DFS tree, with clusters:");
			System.out.println(builder.toString());
		}
		else {
			try {
				Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("DFS tree, with clusters", builder.toString());
			} 
			catch(Exception e) {
				System.out.println("Could not instantiate given DOT renderer class: " + this.dotRendererClass);
				e.printStackTrace();
			}
		}
	}

	/** Starts the algorithm */
	private void start() {
		
		this.varInfos = new HashMap<String, VarInfo> (this.problem.getNbrIntVars());
		for (Map.Entry< String, ? extends Collection<String> > entry : this.problem.getNeighborhoods().entrySet()) {
			String varName = entry.getKey();
			this.varInfos.put(varName, new VarInfo (varName, entry.getValue()));
		}
				
		this.started = true;
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

}
