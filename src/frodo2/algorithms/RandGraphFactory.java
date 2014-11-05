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

package frodo2.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Generator of random graphs
 * @author Thomas Leaute
 */
public class RandGraphFactory {
	
	/** A graph 
	 * 
	 * Nodes are represented by Integers, starting at 0.
	 */
	public static class Graph {
		
		/** List of nodes */
		public List<String> nodes;
		
		/** List of edges */
		public Edge[] edges;
		
		/** Components of the graph */
		public List< List<String> > components;
		
		/** For each node, the index of its connected component */
		public HashMap<String, Integer> componentOf;
		
		/** For each node, the set of its neighbors */
		public Map< String, Set <String> > neighborhoods;

		/** A list of node clusters */
		public List < List <String> > clusters;
		
		/** The index of the cluster each node belongs to */
		public Map<String, Integer> clusterOf;

		/** Constructor 
		 * @param nodes list of nodes
		 * @param edges list of edges
		 * @param components components of the graph
		 * @param neighborhoods for each node, the list of its neighbors
		 * @param clusters list of node clusters
		 */
		public Graph(List<String> nodes, Edge[] edges, List< List<String> > components, Map< String, Set<String> > neighborhoods, 
				List< List<String> > clusters) {
			this.nodes = nodes;
			this.edges = edges;
			this.components = components;
			this.neighborhoods = neighborhoods;
			this.clusters = clusters;

			if (clusters != null) {
				// Compute the clusters
				this.clusterOf = new HashMap<String, Integer> (nodes.size());
				for (int i = 0; i < clusters.size(); i++) {
					for (String var : clusters.get(i)) {
						clusterOf.put(var, i);
					}
				}
			}
			
			// Compute the components
			this.componentOf = new HashMap<String, Integer> ();
			for (int i = 0; i < this.components.size(); i++) 
				for (String node : this.components.get(i)) 
					this.componentOf.put(node, i);
			
		}
		
		/** @return DOT representation of the graph */
		public String toString () {
			StringBuilder out = new StringBuilder ("graph {\n\tnode [shape = \"circle\"];\n");
			if (clusters == null) {
				for (String node : this.nodes) {
					out.append(node + ";\n");
				}
			} else {
				for (int i = 0; i < clusters.size(); i++) {
					out.append("\tsubgraph cluster_" + i + " {\n");
					out.append("\t\tlabel = " + i + ";\n");
					List<String> nodes = clusters.get(i);
					if (nodes.isEmpty())
						out.append("\t\tempty_" + i + " [label=\"\" shape=none];\n");
					for (String node : nodes) {
						out.append("\t\t" + node);
						
						// If this node is connected to at least one node inside a different cluster, fill it
						for (String neigh : this.neighborhoods.get(node)) {
							if (i != this.clusterOf.get(neigh)) {
								out.append(" [style=\"filled\"]");
								break;
							}
						}
						
						out.append(";\n");
					}
					out.append("\t}\n");
				}
			}
			out.append("\n");
			for (Edge edge : edges) {
				out.append("\t" + edge.source + " -- " + edge.dest + ";\n");
			}
			out.append("}\n");
			return out.toString();
		}
		
		/** @return the p1 density, computed as the number of edges divided by the maximum possible number of edges */
		public double computeDensity() {
			final int nbrNodes = this.nodes.size();
			if (nbrNodes <= 1) 
				return 0.0;
			return this.edges.length / (nbrNodes * (nbrNodes-1) / 2.0);
		}
		
		/** @return the highest degree (i.e. number of neighbors) of any given node */
		public int computeMaxDeg() {
			int maxDeg = 0;
			for (Set<String> neighbors : this.neighborhoods.values()) 
				maxDeg = Math.max(maxDeg, neighbors.size());
			return maxDeg;
		}
	}
	
	/** An edge */
	public static class Edge {
		
		/** Source node */
		public String source;
		
		/** Destination node */
		public String dest;
		
		/** Constructor 
		 * @param source source node
		 * @param dest destination node
		 */
		public Edge (String source, String dest) {
			this.source = source;
			this.dest = dest;
		}
		
		/** @see java.lang.Object#toString() */
		public String toString(){
			return "Edge [" + this.source + " -> " + this.dest + "]";
		}
	}
	
	/** Creates a random graph with the desired size
	 * 
	 * There can be only at most 1 edge between any two given nodes. 
	 * @param nbrNodes 		number of nodes
	 * @param nbrEdges 		number of edges
	 * @param nbrClusters 	number of clusters; if 0, no clusters are created
	 * @return a random graph
	 */
	public static Graph getSizedRandGraph (int nbrNodes, int nbrEdges, int nbrClusters) {
		
		assert nbrNodes > 1;
		
		// Check that we are not asked for too many edges
		int maxNbrEdges = nbrNodes * (nbrNodes - 1) / 2;
		if (nbrEdges > maxNbrEdges) {
			System.err.println("Cannot create a graph with " + nbrEdges + " edges and only " + nbrNodes + " nodes; truncating to " + maxNbrEdges + " edges");
			nbrEdges = maxNbrEdges;
		}
		
		// Create the nodes
		List<String> nodes = new ArrayList<String> (nbrNodes);
		for (int i = 0; i < nbrNodes; i++) 
			nodes.add(Integer.toString(i));
		
		// Keep track of the connected components of the graph; initially one component per node
		List< List<String> > components = new ArrayList< List<String> > (nbrNodes);
		Map< String, List <String> > componentOf = new HashMap< String, List <String> > (nbrNodes);
		for (String node : nodes) {
			List <String> component = new ArrayList <String> ();
			component.add(node);
			components.add(component);
			componentOf.put(node, component);
		}
		
		// Keep track of neighbor relationships
		Map< String, Set<String> > neighbors = new HashMap< String, Set<String> > (nbrNodes);
		for (String node : nodes) {
			neighbors.put(node, new HashSet <String> ());
		}
		
		// Generate random edges
		Edge[] edges = new Edge [nbrEdges];
		for (int i = 0; i < nbrEdges; i++) {

			// Pick two nodes at random, for which an edge does not exist yet
			int i1 = 0, i2 = 0;
			String i1Name = "0", i2Name = "0";
			boolean newEdge = false;
			while (! newEdge) {
				newEdge = true;
				i1 = (int) (Math.random() * (nbrNodes - 1));
				i1Name = Integer.toString(i1);
				i2 = i1 + (int) (Math.random() * (nbrNodes - i1 - 1)) + 1;
				i2Name = Integer.toString(i2);
				for (int j = 0; j < i; j++) {
					if (edges[j].source.equals(i1Name) && edges[j].dest.equals(i2Name)) {
						newEdge = false;
					}
				}
			}
			
			// Create the edge
			edges[i] = new Edge (i1Name, i2Name);
			neighbors.get(i2Name).add(i1Name);
			neighbors.get(i1Name).add(i2Name);

			// Recompute the connected components of the graph
			List<String> component1 = componentOf.get(i1Name);
			List<String> component2 = componentOf.get(i2Name);
			if (component1 != component2) {
				component1.addAll(component2);
				components.remove(component2);
				for (String node : nodes) {
					if (componentOf.get(node) == component2) {
						componentOf.put(node, component1);
					}
				}
			}
		}
		
		// Create the clusters
		if (nbrClusters == 0) { // we don't want any cluster
			return new Graph (nodes, edges, components, neighbors, null);	
		} else {
			
			// Randomly assign nodes to clusters
			List <List <String>> clusters = new ArrayList <List <String>> (nbrClusters);
			for (int i = 0; i < nbrClusters; i++) 
				clusters.add(new ArrayList <String> ());
			for (int i = 0; i < nbrNodes; i++) {
				clusters.get((int) (Math.random() * nbrClusters)).add(Integer.toString(i));
			}
			
			return new Graph (nodes, edges, components, neighbors, clusters);	
		}
	}
	
	/** Generates a random undirected graph
	 * 
	 * The graph contains at least 2 nodes. There can be only at most 1 edge between any two given nodes. 
	 * @param maxNbrNodes maximum number of nodes. Must be at least 2. 
	 * @param maxNbrEdges maximum number of edges
	 * @param maxNbrClusters maximum number of clusters; if 0, no clusters are created
	 * @return a random graph
	 */
	public static Graph getRandGraph (int maxNbrNodes, int maxNbrEdges, int maxNbrClusters) {
		
		// Choose a random number of nodes
		int nbrNodes = (int) (Math.random() * (maxNbrNodes-1)) + 2;

		// Choose the number of edges
		int nbrEdges;
		int max = nbrNodes * (nbrNodes - 1) / 2;
		while (true) {
			nbrEdges = (int) (Math.random() * (maxNbrEdges+1));
			if (nbrEdges <= max) {
				break;
			}
		}
		
		// Choose the number of clusters
		int nbrClusters = 0;
		if (maxNbrClusters > 0) 
			nbrClusters = 1 + (int) (Math.random() * maxNbrClusters);

		return getSizedRandGraph (nbrNodes, nbrEdges, nbrClusters);
	}

	/** Generates a random graph in which, for each cluster, all variables in that cluster belong to the same graph component
	 * @param maxNbrNodes 		max number of nodes
	 * @param maxNbrEdges 		max number of edges
	 * @param maxNbrClusters 	max number of clusters; if 0, no clusters are created
	 * @return a random graph in which, for each cluster, all variables in that cluster belong to the same graph component
	 */
	public static Graph getNiceRandGraph (int maxNbrNodes, int maxNbrEdges, int maxNbrClusters) {
		
		boolean wrong = true;
		Graph graph = null;
		while (wrong) {
			wrong = false;
			
			graph = RandGraphFactory.getRandGraph(maxNbrNodes, maxNbrEdges, maxNbrClusters);

			// Make sure that all nodes in any given cluster belong to the same component
			int nbrClusters = graph.clusters.size();
			for (int i = 0; i < nbrClusters && !wrong; i++) {
				
				// Get the component of the first node
				Iterator<String> iter = graph.clusters.get(i).iterator();
				if (! iter.hasNext()) // empty cluster
					continue;
				Integer comp = graph.componentOf.get(iter.next());
				
				// Check that all other nodes belong to the same component
				while (iter.hasNext() && !wrong) 
					if (! graph.componentOf.get(iter.next()).equals(comp)) 
						wrong = true;
			}
		}
		
		return graph;
	}

	/** Generates a random undirected graph
	 * 
	 * The graph contains at least 2 nodes. There can be only at most 1 edge between any two given nodes. 
	 * @param maxNbrNodes maximum number of nodes. Must be at least 2. 
	 * @param maxNbrEdges maximum number of edges
	 * @return a random graph
	 */
	public static Graph getRandGraph (int maxNbrNodes, int maxNbrEdges) {
		return getRandGraph(maxNbrNodes, maxNbrEdges, 0);
	}
	
	/** Generates a ring of the input size, without specified clusters
	 * @param nbrNodes 	the desired number of nodes
	 * @return the graph
	 */
	public static Graph getRingGraph (int nbrNodes) {
		
		assert nbrNodes > 2;
		
		// Create the list of nodes
		List<String> nodes = new ArrayList<String> (nbrNodes);
		for (int i = 0; i < nbrNodes; i++) 
			nodes.add(Integer.toString(i));
		
		// Create the list of edges
		Edge[] edges = new Edge [nbrNodes];
		for (int i = 0; i < nbrNodes; i++) 
			edges[i] = new Edge (Integer.toString(i), Integer.toString((i + 1) % nbrNodes));
		
		// There is only one component
		List< List<String> > components = Arrays.asList(nodes);
		
		// Create the neighborhoods
		HashMap< String, Set<String> > neighborhoods = new HashMap< String, Set<String> > (nbrNodes);
		for (int i = 1; i <= nbrNodes; i++) 
			neighborhoods.put(Integer.toString(i % nbrNodes), new HashSet<String> (Arrays.asList(Integer.toString((i-1) % nbrNodes), Integer.toString((i+1) % nbrNodes))));
		
		return new Graph (nodes, edges, components, neighborhoods, null);
	}
	
	/** Generates a square grid of the desired size
	 * @param side 	the graph contains side*side nodes
	 * @return the graph
	 */
	public static Graph getSquareGrid (int side) {
		
		// Create the nodes
		List<String> nodes = new ArrayList<String> (side * side);
		for (int i = 0; i < side; i++) 
			for (int j = 0; j < side; j++) 
				nodes.add("n" + i + "_" + j);
		
		// Create the edges
		int nbrEdges = 2 * side * (side-1);
		ArrayList<Edge> edges = new ArrayList<Edge> (nbrEdges);
		for (int i = 0; i < side; i++) {
			for (int j = 0; j < side; j++) {
				if (i < side - 1) 
					edges.add(new Edge ("n" + i + "_" + j, "n" + (i+1) + "_" + j));
				if (j < side - 1) 
					edges.add(new Edge ("n" + i + "_" + j, "n" + i + "_" + (j+1)));
			}
		}
		
		// Only one component
		List< List<String> > components = Arrays.asList(nodes);
		
		// Build the neighborhoods
		HashMap< String, Set<String> > neighborhoods = new HashMap< String, Set<String> > (side * side);
		for (int i = 0; i < side; i++) {
			for (int j = 0; j < side; j++) {
				HashSet<String> neighbors = new HashSet<String> ();
				neighborhoods.put("n" + i + "_" + j, neighbors);
				
				if (i > 0) 
					neighbors.add("n" + (i-1) + "_" + j);
				
				if (i < side - 1) 
					neighbors.add("n" + (i+1) + "_" + j);
				
				if (j > 0) 
					neighbors.add("n" + i + "_" + (j-1));
				
				if (j < side - 1) 
					neighbors.add("n" + i + "_" + (j+1));
			}
		}
		
		return new Graph (nodes, edges.toArray(new Edge [nbrEdges]), components, neighborhoods, null);
	}
	
	/** Generates a single-component, acyclic graph of the desired size, with the desired branching factor
	 * @param nbrNodes 			the number of nodes
	 * @param branchingFactor 	each node has at most (branchingFactor + 1) neighbors
	 * @return the graph
	 */
	public static Graph getAcyclicGraph (int nbrNodes, int branchingFactor) {
		
		// Generate the nodes and clusters
		List<String> nodes = new ArrayList<String> (nbrNodes);
		List< List<String> > clusters = new ArrayList< List<String> > (nbrNodes);
		for (int i = 0; i < nbrNodes; i++) {
			String name = Integer.toString(i);
			nodes.add(name);
			clusters.add(Arrays.asList(name));
		}
		
		// Initialize the neighborhoods
		HashMap< String, Set<String> > neighborhoods = new HashMap< String, Set<String> > (nbrNodes);
		for (int i = 0; i < nbrNodes; i++) 
			neighborhoods.put(Integer.toString(i), new HashSet<String> ());
		
		// Generate the edges and neighborhoods
		ArrayList<Edge> edges = new ArrayList<Edge> (nbrNodes - 1); // each node has an edge with its parent, except the root
		List<String> open = new ArrayList<String> (nodes);
		List<String> leaves = new ArrayList<String> (nbrNodes);
		Random rand = new Random ();
		leaves.add(open.remove(0)); // choose the first node as the root
		while (! open.isEmpty()) {
			
			// Pick a random leaf
			String leaf = leaves.remove(rand.nextInt(leaves.size()));
			
			// Pick children from the open list
			for (int i = 0; i < branchingFactor && !open.isEmpty(); i++) {
				
				// Pick a random child
				String child = open.remove(0);
				leaves.add(child);
				
				// Create the edge
				edges.add(new Edge (leaf, child));
				
				// Update the neighborhoods
				neighborhoods.get(leaf).add(child);
				neighborhoods.get(child).add(leaf);
			}
		}
		
		// Only one component
		List< List<String> > components = Arrays.asList(nodes);
		
		return new Graph (nodes, edges.toArray(new Edge [edges.size()]), components, neighborhoods, clusters);
	}
	
	/** Generates a chordal graph
	 * @param nbrNodes 		the number of nodes
	 * @param rateOfChords 	rateOfChords % of the edges are chords
	 * @return the graph
	 */
	public static Graph getChordalGraph (int nbrNodes, double rateOfChords) {
		
		assert rateOfChords < 1.0 && rateOfChords >= 0.0 : "Incorrect rate of chords: " + rateOfChords;
		assert nbrNodes > 3;
		
		// First generate a ring graph
		Graph graph = getRingGraph (nbrNodes);
		
		// Compute the number of chords to add
		double nbrChords = rateOfChords * nbrNodes / (1-rateOfChords);
		if (nbrNodes + nbrChords > nbrNodes*(nbrNodes-1)/2) {
			nbrChords = nbrNodes*(nbrNodes-1)/2 - nbrNodes;
			System.err.println("The rate of chords is too high; generating a complete graph with a rate of chords of " + (nbrChords / (nbrChords + nbrNodes)));
		}
		
		// Add the chords
		Random rand = new Random ();
		ArrayList<Edge> chords = new ArrayList<Edge> ((int) nbrChords + nbrNodes);
		for (int n = 0; n < nbrChords; n++) {
			
			// Pick a random pair of nodes that are not consecutive and are not already connected by a chord
			int i, j;
			do {
				i = rand.nextInt(nbrNodes);
				j = (i + 2 + rand.nextInt(nbrNodes - 3)) % nbrNodes;
			} while (graph.neighborhoods.get(Integer.toString(i)).contains(Integer.toString(j)));
			
			// Add a chord between i and j
			graph.neighborhoods.get(Integer.toString(i)).add(Integer.toString(j));
			graph.neighborhoods.get(Integer.toString(j)).add(Integer.toString(i));
			chords.add(new Edge (Integer.toString(i), Integer.toString(j)));
		}
		
		chords.addAll(Arrays.asList(graph.edges));
		return graph = new Graph (graph.nodes, chords.toArray(new Edge [(int) nbrChords + nbrNodes]), graph.components, graph.neighborhoods, null);
	}

}
