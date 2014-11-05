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

/** Contains a random problem generator for the party game */
package frodo2.benchmarks.party;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.hypercube.ScalarSpaceIter;

/** A random problem generator for the party game
 * 
 * The party game is described in the following paper:
 * Satinder Singh, Vishal Soni, and Michael P. Wellman. Computing approximate Bayes-Nash equilibria in tree-games of incomplete information. 
 * In Jack S. Breese, Joan Feigenbaum, and Margo I. Seltzer, editors, Proceedings of the Fifth ACM Conference on Electronic Commerce (EC'04), 
 * pages 81-90, New York, NY, USA, May 17-20 2004. ACM.
 * 
 * @author Thomas Leaute
 */
public class PartyGame {
	
	/** The method used to formulate the game as a DisCSP */
	protected static enum Method {
		/** Each player owns one variable for each neighbor's strategy and for her own */
		Leaute11, 
		/** Each player owns one variable for her own strategy */
		Vickrey02,
		/** Each player owns one variable for the joint strategy of her neighbors, including herself */
		Soni07
	}
	
	/** An instance of a party game */
	public static class PartyInstance {
		
		/** Each player's private cost incurred by attending the party */
		public TreeMap<String, Double> privateCosts;
		
		/** For each player, for each neighbor, the player's cost if they both attend the party */
		public TreeMap< String, TreeMap<String, Double> > likes;
		
		/** Name of the problem instance */
		final private String instanceName;
		
		/** The underlying party graph */
		final private Graph graph;

		/** Constructor
		 * @param graph 		The underlying party graph
		 * @param privateCosts 	Each player's private cost incurred by attending the party
		 * @param likes 		For each player, for each neighbor, the player's cost if they both attend the party
		 */
		public PartyInstance(Graph graph, TreeMap<String, Double> privateCosts, TreeMap<String, TreeMap<String, Double>> likes) {
			this.graph = graph;
			this.privateCosts = privateCosts;
			this.likes = likes;
			this.instanceName = "partyProblem_" + System.currentTimeMillis();
			
//			new DOTrenderer ("Game", this.toString());
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			
			StringBuilder dotString = new StringBuilder ("digraph {\n\tnode [shape = \"circle\"];\n");
			
			// Go through the list of nodes/players
			for (Map.Entry<String, Double> entry : this.privateCosts.entrySet()) {
				String player = entry.getKey();
				
				dotString.append("\t" + player + ";\n");
				
				// Each player assigns a private cost to attending the party
				dotString.append("\t" + player + " -> " + player + " [label = " + entry.getValue() + "];\n");

				// Each player either likes or dislikes each neighbor
				for (Map.Entry<String, Double> entry2 : this.likes.get(player).entrySet()) {
					
					// If the neighbor attends the party, then this incurs a cost of -1 if the player likes the neighbor, +1 otherwise
					dotString.append("\t" + player + " -> " + entry2.getKey() + " [label = " + entry2.getValue() + "];\n");
				}
				
				dotString.append("\n");
			}

			dotString.append("}");
			
			return dotString.toString();
		}
	}
	
	/** Creates a "stats" element
	 * @param name 		the value of the "name" attribute
	 * @param value 	the text
	 * @return a new "stats" element
	 */
	public static Element createStats (String name, String value) {
		
		Element stats = new Element ("stats");
		stats.setAttribute("name", name);
		stats.setText(value);
		
		return stats;
	}
	
	/** Generates an instance based on an acyclic graph
	 * @param nbrPlayers 		the number of players
	 * @param branchingFactor 	each node has at most (branchingFactor + 1) neighbors
	 * @param epsilon 			the margin used for approximate equilibria
	 * @param mixed 			whether to allow mixed strategies
	 * @param method 			the DisCSP formulation method
	 * @param intensional 		whether to use intensional constraints
	 * @return a problem instance
	 */
	public static Document generateAcyclicProblem (int nbrPlayers, int branchingFactor, double epsilon, boolean mixed, Method method, boolean intensional) {
		return encodeProblem(generateProblem(RandGraphFactory.getAcyclicGraph(nbrPlayers, branchingFactor)), epsilon, mixed, method, intensional);
	}
	
	/** Generates an instance based on a chordal graph
	 * @param nbrPlayers 	the number of players
	 * @param rateOfChords 	rateOfChords % of the edges are chords
	 * @param epsilon 		the margin used for approximate equilibria
	 * @param mixed 		whether to allow mixed strategies
	 * @param method 		the DisCSP formulation method
	 * @param intensional 	whether to use intensional constraints
	 * @return a problem instance
	 */
	public static Document generateChordalProblem (int nbrPlayers, double rateOfChords, double epsilon, boolean mixed, Method method, boolean intensional) {
		return encodeProblem(generateProblem(RandGraphFactory.getChordalGraph(nbrPlayers, rateOfChords)), epsilon, mixed, method, intensional);
	}
	
	/** Generates an instance based on a ring graph
	 * @param nbrPlayers 	the number of players
	 * @param epsilon 		the margin used for approximate equilibria
	 * @param mixed 		whether to allow mixed strategies
	 * @param method 		the DisCSP formulation method
	 * @param intensional 	whether to use intensional constraints
	 * @return a problem instance
	 */
	public static Document generateRingProblem (int nbrPlayers, double epsilon, boolean mixed, Method method, boolean intensional) {
		return encodeProblem(generateProblem(RandGraphFactory.getRingGraph(nbrPlayers)), epsilon, mixed, method, intensional);
	}
	
	/** Generates an instance based on a square grid
	 * @param side 			the graph contains side*side players
	 * @param epsilon 		the margin used for approximate equilibria
	 * @param mixed 		whether to allow mixed strategies
	 * @param method 		the DisCSP formulation method
	 * @param intensional 	whether to use intensional constraints
	 * @return a problem instance
	 */
	public static Document generateGridProblem (int side, double epsilon, boolean mixed, Method method, boolean intensional) {
		return encodeProblem(generateProblem(RandGraphFactory.getSquareGrid(side)), epsilon, mixed, method, intensional);
	}
	
	/** Generates a random instance of the party game
	 * @param graph 	the graph in which players are nodes
	 * @return a party game instance
	 */
	public static PartyInstance generateProblem (Graph graph) {
		
//		new DOTrenderer ("Graph", graph.toString(), "neato");
		
		TreeMap<String, Double> privateCosts = new TreeMap<String, Double> ();
		TreeMap< String, TreeMap<String, Double> > likes = new TreeMap< String, TreeMap<String, Double> > ();
		
		// Go through the list of nodes/players
		for (String player : graph.nodes) {
			
			// Each player assigns a private cost to attending the party
			privateCosts.put(player, 2 * Math.random() - 1);
			
			// Each player either likes or dislikes each neighbor
			TreeMap<String, Double> myLikes = new TreeMap<String, Double> ();
			for (String neigh : graph.neighborhoods.get(player)) {
				
				// If the neighbor attends the party, then this incurs a cost of -1 if the player likes the neighbor, +1 otherwise
				double like = (Math.random() < 0.5 ? 1.0 : -1.0);
				myLikes.put(neigh, like);
			}
			likes.put(player, myLikes);
		}
		
		return new PartyInstance (graph, privateCosts, likes);
	}

	/** Encodes a party game instance as a DisCSP in XCSP format
	 * @param party 		the party instance
	 * @param method 		the DisCSP formulation method
	 * @param intensional 	whether to use intensional constraints
	 * @return a problem instance
	 * @note \a epsilon is set to \c 0.0, and \a mixed to \c false
	 */
	public static Document encodeProblem (PartyInstance party, final Method method, final boolean intensional) {
		return encodeProblem (party, 0.0, false, method, intensional);
	}
	
	/** Encodes a party game instance as a DisCSP in XCSP format
	 * @param party 		the party instance
	 * @param epsilon 		the margin used for approximate equilibria
	 * @param mixed 		whether to allow mixed strategies
	 * @param method 		the DisCSP formulation method
	 * @param intensional 	whether to use intensional constraints
	 * @return a problem instance
	 */
	protected static Document encodeProblem (PartyInstance party, double epsilon, final boolean mixed, final Method method, final boolean intensional) {
		
		// Compute the probability discretization parameter for mixed strategies
		double tau = 1.0;
		if (mixed) {
			if (epsilon <= 0) 
				System.err.println("epsilon must be positive");
			
			int k = 0; // maximum number of neighbors of any given node
			for (TreeMap<String, Double> likes : party.likes.values()) 
				k = Math.max(k, likes.size());
			tau = epsilon / (4 * 2 * k * 0.5); // probabilities are: 0, tau, 2*tau, ..., 1
		}

		return encodeProblem(party, epsilon, tau, mixed, method, intensional);
	}
	
	/** Encodes a party game instance as a DisCSP in XCSP format
	 * @param party 		the party instance
	 * @param epsilon 		the margin used for approximate equilibria
	 * @param tau 			the probability discretization parameter for mixed strategies
	 * @param mixed 		whether to allow mixed strategies
	 * @param method 		the DisCSP formulation method
	 * @param intensional 	whether to use intensional constraints
	 * @return a problem instance
	 */
	@SuppressWarnings("unchecked")
	private static Document encodeProblem (PartyInstance party, double epsilon, double tau, final boolean mixed, final Method method, final boolean intensional) {
		
		int domSize = (int) Math.ceil(1 / tau) + 1;

		// Create the root element
		Element probElement = new Element ("instance");
		if (intensional) 
			probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschemaJaCoP.xsd", 
					Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
		else 
			probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschema.xsd", 
					Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));

		// Create the "presentation" element
		Element elmt = new Element ("presentation");
		probElement.addContent(elmt);
		elmt.setAttribute("name", party.instanceName);
		int maxConstraintArity = 0;
		if (! intensional) 
			elmt.setAttribute("maximize", "false");
		elmt.setAttribute("format", "XCSP 2.1_FRODO");
		
		// Create the "agents" element
		elmt = new Element ("agents");
		probElement.addContent(elmt);
		elmt.setAttribute("nbAgents", Integer.toString(party.privateCosts.size()));
		for (String playerID : party.privateCosts.keySet()) {
			Element player = new Element ("agent");
			elmt.addContent(player);
			player.setAttribute("name", "a" + playerID);
		}
		
		// Create the "domains" element, representing the probability of attendance
		Element domsElement = new Element ("domains");
		probElement.addContent(domsElement);
		
		// Create the domains
		HashMap< String, HashMap<String, Double>[] > domains = new HashMap< String, HashMap<String, Double>[] > (party.privateCosts.size()); // for Method.Soni07, the signification of the variable values
		if (method != Method.Soni07) { // variables represent single-agent strategies
			
			Element subElmt = new Element ("domain");
			domsElement.addContent(subElmt);
			subElmt.setAttribute("name", "Strategies");
			subElmt.setAttribute("nbValues", Integer.toString(domSize));
			StringBuilder builder = new StringBuilder ("0 ");
			for (double prob = tau; prob < 1.0; prob += tau) {
				builder.append(prob);
				builder.append(" ");
			}
			builder.append("1");
			subElmt.setText(builder.toString());
			
		} else { // Soni07: variables represent the joint strategies of all neighbors and oneself
			
			// The domain depends on the player
			for (Map.Entry< String, TreeMap<String, Double> > entry : party.likes.entrySet()) {
				String player = entry.getKey();
				ArrayList<String> players = new ArrayList<String> (entry.getValue().keySet());
				players.add(player);
				
				// Use a scalar space iterator to go through all possible joint strategies for the player and her neighbors
				AddableReal[] dom = new AddableReal [domSize];
				for (int i = 0; i < domSize - 1; i++) 
					dom[i] = new AddableReal (i * tau);
				dom[domSize - 1] = new AddableReal (1);
				AddableReal[][] doms = new AddableReal [players.size()][domSize];
				Arrays.fill(doms, dom);
				ScalarSpaceIter<AddableReal, AddableReal> iter = 
						new ScalarSpaceIter<AddableReal, AddableReal> (null, players.toArray(new String [players.size()]), doms, null, null);
				assert iter.getNbrSolutions() < (long) Integer.MAX_VALUE : "Variable domains are too large";
				
				HashMap<String, Double>[] domain = new HashMap [(int) iter.getNbrSolutions()];
				domains.put(player, domain);
				for (int i = 0; iter.hasNext(); i++) {
					AddableReal[] jointStrat = iter.nextSolution();
					HashMap<String, Double> jointStratMap = new HashMap<String, Double> (jointStrat.length);
					domain[i] = jointStratMap;
					for (int p = 0; p < jointStrat.length; p++) 
						jointStratMap.put(players.get(p), jointStrat[p].doubleValue());
				}
				
//				System.out.println("p" + player + "'s domain: ");
//				for (int i = 0; i < domain.length; i++) 
//					System.out.println("\t" + i + "\t" + domain[i]);
				
				// Write the encoded domain
				Element domElmt = new Element ("domain");
				domsElement.addContent(domElmt);
				domElmt.setAttribute("name", "p" + player + "dom");
				domElmt.setAttribute("nbValues", Integer.toString(domain.length));
				domElmt.setText("0.." + (domain.length - 1));
			}
		}
		domsElement.setAttribute("nbDomains", Integer.toString(domsElement.getContentSize()));
		
		// Create the "variables" element
		Element varsElement = new Element ("variables");
		probElement.addContent(varsElement);

		// Create the "relations," "predicates" and "constraints" elements
		Element relElement = new Element ("relations");
		probElement.addContent(relElement);
		Element predElement = new Element ("predicates");
		if (intensional && method == Method.Leaute11) 
			probElement.addContent(predElement);
		Element conElement = new Element ("constraints");
		probElement.addContent(conElement);
		
		if (method == Method.Leaute11) { // Create a binary equality relation/predicate
			
			if (! intensional) {
				elmt = new Element ("relation");
				relElement.addContent(elmt);
				elmt.setAttribute("name", "EQ");
				elmt.setAttribute("arity", "2");
				maxConstraintArity = Math.max(maxConstraintArity, 2);
				elmt.setAttribute("nbTuples", Integer.toString(domSize));
				elmt.setAttribute("semantics", "soft");
				elmt.setAttribute("defaultCost", "infinity");
				StringBuilder builder = new StringBuilder ("0: 0 0 | ");
				for (double prob = tau; prob < 1.0; prob += tau) {
					builder.append(prob);
					builder.append(" ");
					builder.append(prob);
					builder.append(" | ");
				}
				builder.append("1 1");
				elmt.setText(builder.toString());
				
			} else { // intensional

				elmt = new Element ("predicate");
				predElement.addContent(elmt);
				elmt.setAttribute("name", "EQ");

				Element subElmt = new Element ("parameters");
				elmt.addContent(subElmt);
				subElmt.setText("int X int Y");

				subElmt = new Element ("expression");
				elmt.addContent(subElmt);

				Element subSubElmt = new Element("functional");
				subElmt.addContent(subSubElmt);
				subSubElmt.setText("eq(X, Y)");
			}
		}

		// Go through the list of players
		for (Map.Entry<String, Double> entry : party.privateCosts.entrySet()) {
			String player = entry.getKey();
			double privateCost = entry.getValue(); // the player's private cost to attending the party
			
			// Create the strategy variable
			elmt = new Element ("variable");
			varsElement.addContent(elmt);
			String myVar = "p" + player;
			if (method == Method.Leaute11) 
				myVar += "s" + player;
			elmt.setAttribute("name", myVar);
			elmt.setAttribute("domain", (method == Method.Soni07 ? "p" + player + "dom" : "Strategies"));
			elmt.setAttribute("agent", "a" + player);
			
			if (method == Method.Soni07) {
				HashMap<String, Double>[] domain1 = domains.get(player);
				
				// For each neighbor, create a binary constraint enforcing that the two joint-strategies variables must take consistent values
				for (String neigh : party.likes.get(player).keySet()) {
					
					// Don't create the two symmetries of the same binary constraint
					if (neigh.compareTo(player) < 0) 
						continue;
					
					HashMap<String, Double>[] domain2 = domains.get(neigh);
					
					assert ((long) domain1.length) * ((long) domain2.length) < Integer.MAX_VALUE : "Too many tuples in a relation";
					
					// Create the relation
					elmt = new Element ("relation");
					relElement.addContent(elmt);
					String constName = "p" + player + "_and_p" + neigh + "_are_consistent";
					elmt.setAttribute("name", constName + "_rel");
					elmt.setAttribute("arity", "2");
					maxConstraintArity = Math.max(maxConstraintArity, 2);
					int nbTuples = 0;
					StringBuilder builder = new StringBuilder ();
					if (intensional) 
						elmt.setAttribute("semantics", "supports");
					else {
						elmt.setAttribute("semantics", "soft");
						elmt.setAttribute("defaultCost", "infinity"); // let's represent explicitly only the feasible tuples
						builder.append("0: ");
					}
					
					// Build the representation of the feasible tuples
					for (int p1 = 0; p1 < domain1.length; p1++) {
						HashMap<String, Double> assignment1 = domain1[p1];
						
						builder.append("\n");
						
						p2loop: for (int p2 = 0; p2 < domain2.length; p2++) {
							HashMap<String, Double> assignment2 = domain2[p2];
							
							// Ignore this tuple if it is inconsistent
							for (Map.Entry<String, Double> entry1 : assignment1.entrySet()) {
								Double value2 = assignment2.get(entry1.getKey());
								if (value2 != null && ! value2.equals(entry1.getValue())) 
									continue p2loop;
							}
							
							builder.append(p1 + " " + p2 + "|");
							nbTuples++;
						}
					}
					elmt.setAttribute("nbTuples", Integer.toString(nbTuples));
					elmt.setText(builder.substring(0, builder.length() - 1));
					
					// Create the constraint
					elmt = new Element ("constraint");
					conElement.addContent(elmt);
					elmt.setAttribute("name", constName);
					elmt.setAttribute("arity", "2");
					elmt.setAttribute("scope", "p" + player + " p" + neigh);
					elmt.setAttribute("reference", constName + "_rel");
				}
			}
			
			// Each player either likes or dislikes each neighbor
			TreeMap<String, Double> likesMap = party.likes.get(player);
			int nbrNeighbors = likesMap.size();
			ArrayList<String> vars = new ArrayList<String> (nbrNeighbors); // excluding the variable for the player's own strategy
			String scope = "";
			ArrayList<Double> likes = new ArrayList<Double> (nbrNeighbors);
			for (Map.Entry<String, Double> entry2 : likesMap.entrySet()) {
				
				String neigh = entry2.getKey();
				Double like = entry2.getValue();
				likes.add(like);
				
				switch (method) {
				
				case Soni07: 
					break;
				
				case Leaute11: {
					
					// Create a variable for the neighbor's strategy
					elmt = new Element ("variable");
					varsElement.addContent(elmt);
					String varName = "p" + player + "s" + neigh;
					elmt.setAttribute("name", varName);
					vars.add(varName);
					scope += varName + " ";
					elmt.setAttribute("domain", "Strategies");
					elmt.setAttribute("agent", "a" + player);
					
					// Create the equality constraint with the neighbor's strategy variable
					elmt = new Element ("constraint");
					conElement.addContent(elmt);
					elmt.setAttribute("name", "p" + player + "s" + neigh + "_EQ_" + "p" + neigh + "s" + neigh);
					elmt.setAttribute("arity", "2");
					elmt.setAttribute("scope", "p" + player + "s" + neigh + " " + "p" + neigh + "s" + neigh);
					elmt.setAttribute("reference", "EQ");
					
					if (intensional) {
						Element subElmt = new Element ("parameters");
						elmt.addContent(subElmt);
						subElmt.setText("p" + player + "s" + neigh + " " + "p" + neigh + "s" + neigh);
					}

					break;
				}
				
				case Vickrey02: {
					
					// The constraint is expressed over the the neighbors' variables
					String varName = "p" + neigh;
					vars.add(varName);
					scope += varName + " ";

					break;
				}
				}
			}
			scope += myVar;
			
			// Create a relation expressing which of the player's strategies is an epsilon-best response, as a function of the neighbors' strategies
			elmt = new Element ("relation");
			String relName = "p" + player + "_rel";
			elmt.setAttribute("name", relName);
			elmt.setAttribute("arity", Integer.toString((method == Method.Soni07 ? 1 : nbrNeighbors + 1)));
			int nbrTuples = 0;
			StringBuilder builder = new StringBuilder ();
			if (intensional) 
				elmt.setAttribute("semantics", "conflicts");
			else {
				elmt.setAttribute("semantics", "soft");
				elmt.setAttribute("defaultCost", "0"); // let's only explicitly list the infeasible cases, i.e. the strategies that are not epsilon-best responses
				builder.append("infinity: ");
			}

			if (method != Method.Soni07) {
				
				// Build a scalar space iterator used to iterate over all possible strategies for the neighbors
				AddableReal[] dom = new AddableReal [domSize];
				for (int i = 0; i < domSize - 1; i++) 
					dom[i] = new AddableReal (i * tau);
				dom[domSize - 1] = new AddableReal (1.0);
				AddableReal[][] doms = new AddableReal [nbrNeighbors][domSize];
				Arrays.fill(doms, dom);
				ScalarSpaceIter<AddableReal, AddableReal> iter = 
						new ScalarSpaceIter<AddableReal, AddableReal> (null, vars.toArray(new String [nbrNeighbors]), doms, null, null);

				// Create the relation text
				while (iter.hasNext()) {

					// Compute the expected cost of attending the party (with 100% certainty)
					double cost = privateCost;
					AddableReal[] strategies = iter.nextSolution();
					StringBuilder neighTuple = new StringBuilder ();
					for (int i = 0; i < nbrNeighbors; i++) {
						
						AddableReal strat = strategies[i];
						if (strat.doubleValue() == 0.0) 
							neighTuple.append(0);
						else if (strat.doubleValue() == 1.0) 
							neighTuple.append(1);
						else 
							neighTuple.append(strat);
						
						neighTuple.append(" ");
						cost += strategies[i].doubleValue() * likes.get(i);
					}

					// Loop through the probabilities of attendance for the current player, looking for the expected cost of the best response
					double bestCost = 0.0; // this is the expected cost of attending with 0% probability
					for (AddableReal prob : dom) {
						double cost2 = prob.doubleValue() * cost;
						if (cost2 < bestCost) 
							bestCost = cost2;
					}

					// Loop through the probabilities of attendance for the current player, looking for ones that are NOT epsilon-best responses
					bestCost += epsilon; // makes it harder by a margin of epsilon to reject a strategy
					int oldNbrTuples = nbrTuples;
					for (AddableReal prob : dom) {
						if (prob.doubleValue() * cost > bestCost) {
							if (++nbrTuples == oldNbrTuples + 1) 
								builder.append("\n");
							builder.append(neighTuple.toString());
							
							if (prob.doubleValue() == 0.0) 
								builder.append(0);
							else if (prob.doubleValue() == 1.0) 
								builder.append(1);
							else 
								builder.append(prob.toString());
							
							builder.append("|");
						}
					}
				}
				
			} else { // Soni07
				
				// Go through all possible assignments to the agent's joint-strategies variable
				HashMap<String, Double>[] domain = domains.get(player);
				for (int i = 0; i < domain.length; i += domSize) { // loop through the neighbors' strategies
					
					// Compute the expected cost of attending the party (with 100% certainty)
					double cost = privateCost;
					HashMap<String, Double> jointStrat = domain[i];
					double myProb = jointStrat.remove(player); // temporarily remove the player's own strategy
					for (Map.Entry<String, Double> entry2 : jointStrat.entrySet()) 
						cost += entry2.getValue() * likesMap.get(entry2.getKey());
					jointStrat.put(player, myProb); // re-add the player's own strategy
					
					// Loop through the player's strategies, looking for the expected cost of the best response
					double bestCost = 0.0; // this is the expected cost of attending with 0% probability
					for (int j = 0; j < domSize; j++) {
						double cost2 = domain[i+j].get(player) * cost;
						if (cost2 < bestCost) 
							bestCost = cost2;
					}
					
					// Loop trough the player's strategies again, looking for ones that are NOT epsilon-best responses
					bestCost += epsilon; // makes it harder by a margin of epsilon to reject a strategy
					for (int j = 0; j < domSize; j++) {
						if (domain[i+j].get(player) * cost > bestCost) {
							nbrTuples++;
							builder.append((i+j));
							builder.append("|");
						}
					}
				}
			}
			
			if (nbrTuples > 0) {
				elmt.setAttribute("nbTuples", Integer.toString(nbrTuples));
				elmt.setText(builder.substring(0, builder.length() - 1));
				relElement.addContent(elmt);
				if (method != Method.Soni07) 
					maxConstraintArity = Math.max(maxConstraintArity, nbrNeighbors + 1);

				// Create the corresponding constraint
				elmt = new Element ("constraint");
				conElement.addContent(elmt);
				elmt.setAttribute("name", "p" + player + "_const");
				if (method == Method.Vickrey02) 
					elmt.setAttribute("agent", "a" + player);
				elmt.setAttribute("arity", Integer.toString((method == Method.Soni07 ? 1 : nbrNeighbors + 1)));
				elmt.setAttribute("scope", scope);
				elmt.setAttribute("reference", relName);
			}
		}
		
		// Set the number of variables, relations, and constraints
		varsElement.setAttribute("nbVariables", Integer.toString(varsElement.getContentSize()));
		relElement.setAttribute("nbRelations", Integer.toString(relElement.getContentSize()));
		predElement.setAttribute("nbPredicates", Integer.toString(predElement.getContentSize()));
		conElement.setAttribute("nbConstraints", Integer.toString(conElement.getContentSize()));
		
		// Set the stats
		(elmt = probElement.getChild("presentation")).setAttribute("maxConstraintArity", Integer.toString(maxConstraintArity));
		elmt.addContent(createStats("margin of error", Double.toString(epsilon)));
		elmt.addContent(createStats("equilibrium type", mixed ? "mixed" : "pure Nash"));
		elmt.addContent(createStats("number of players", Integer.toString(party.graph.nodes.size())));
		elmt.addContent(createStats("party graph average density", Double.toString(party.graph.computeDensity())));
		elmt.addContent(createStats("number of disconnected components of the party graph", Integer.toString(party.graph.components.size())));
		elmt.addContent(createStats("party graph max degree", Integer.toString(party.graph.computeMaxDeg())));
		
		return new Document (probElement);
	}

	/** Write a random problem to a file
	 * @param args 	epsilon mixed graph [graph parameters]
	 * @throws IOException if an I/O error occurs while writing the output file
	 */
	public static void main(String[] args) throws IOException {
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. \n");
		
		// Check the number of arguments
		if (args.length < 4 || args.length > 6) {
			System.err.println("Usage: PartyGame [-i] epsilon mixed topology size [param], where:\n" +
					"\t -i (optional) imposes the use of intensional constraints\n" +
					"\t epsilon  is the error for approximate equilibria (0.0 for exact equilibria)\n" +
					"\t mixed  is a Boolean indicating whether to compute mixed or pure Nash equilibria\n" +
					"\t topology  is the type of the game graph (`acyclic', `chordal', `grid' or `ring')\n" +
					"\t size  is the number of players, except for grid graphs, in which it is the length of the square grid side\n" +
					"\t param  for acyclic graphs, the branching factor; for chordal graphs, the rate of chords; else, unused");
			System.exit(1);
		}

		// Parse the input arguments
		boolean intensional = false;
		if (args[0].equals("-i")) { // intensional constraints
			intensional = true;
			
			// Remove the "-i" from the list of arguments
			System.arraycopy(args, 1, args, 0, args.length - 1);
		}
		
		double epsilon = Double.parseDouble(args[0]);
		boolean mixed = Boolean.parseBoolean(args[1]);
		if (intensional && mixed) {
			System.err.println("Unable to produce problems with both intensional constraints and mixed strategies");
			return;
		}
		String graphType = args[2];
		Graph graph = null;
		
		if (graphType.equals("acyclic")) {
			
			int nbrPlayers = Integer.parseInt(args[3]);
			int branchingFactor = Integer.parseInt(args[4]);
			graph = RandGraphFactory.getAcyclicGraph(nbrPlayers, branchingFactor);
			
		} else if (graphType.equals("chordal")) {
			
			int nbrPlayers = Integer.parseInt(args[3]);
			double rateOfChords = Double.parseDouble(args[4]);
			graph = RandGraphFactory.getChordalGraph(nbrPlayers, rateOfChords);
			
		} else if (graphType.equals("grid")) {
			
			int side = Integer.parseInt(args[3]);
			graph = RandGraphFactory.getSquareGrid(side);
			
		} else if (graphType.equals("ring")) {
			
			int nbrPlayers = Integer.parseInt(args[3]);
			graph = RandGraphFactory.getRingGraph(nbrPlayers);
			
		} else {
			System.err.println("Unknown graph type: " + graphType);
			System.exit(1);
		}
		
		PartyInstance prob = generateProblem(graph);
		for (Method method : Arrays.asList(Method.Leaute11, Method.Soni07, Method.Vickrey02)) {
			Document problem = encodeProblem(prob, epsilon, mixed, method, intensional);
			new XMLOutputter(Format.getPrettyFormat()).output(problem, new FileWriter ("party" + method + ".xml"));
			System.out.println("Wrote party" + method + ".xml");
		}
		
	}

}
