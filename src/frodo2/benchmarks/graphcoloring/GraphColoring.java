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

/** A graph coloring problem generator */
package frodo2.benchmarks.graphcoloring;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.RandGraphFactory.Edge;
import frodo2.algorithms.RandGraphFactory.Graph;

/** A graph coloring problem generator
 * @author Thomas Leaute
 */
public class GraphColoring {

	/** Generates a random graph coloring problem and writes it to a file
	 * @param args 	[-soft] nbrNodes density nbrColors [stochNodeRatio]
	 * @throws IOException 	if an error occurs while attempting to write the output file
	 * @todo Add support for various graph topologies
	 */
	public static void main(String[] args) throws IOException {
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek\n" +
				"This program comes with ABSOLUTELY NO WARRANTY.\n" +
				"This is free software, and you are welcome to redistribute it\n" +
				"under certain conditions. \n");
		
		ArrayList<String> args2 = new ArrayList<String> (Arrays.asList(args));
		
		boolean intensional = args2.remove("-i");
		boolean soft = args2.remove("-soft");
		boolean mpc = args2.remove("-mpc");
		boolean nbrLinks = args2.remove("-nbrLinks");

		if (args2.size() < 4 || args2.size() > 5) {
			System.err.println("Usage: " + GraphColoring.class.getSimpleName() + " [-i] [-soft] [-mpc] [-nbrLinks] nbrNodes density tightness nbrColors [stochNodeRatio]\n" +
					"\t -i [optional]               if present, the output problem is expressed in intensional form\n" +
					"\t -soft [optional]            if present, the output is a Max-DisCSP instead of a DisCSP with hard constraints (default is DisCSP)\n" +
					"\t -mpc [optional]             if present, also outputs an alternative problem formulation in which all constraints are public\n" +
					"\t -nbrLinks [optional]		if present, the density parameter states that there must be density*nbrNodes links in the graph\n" +
					"\t nbrNodes                    the number of nodes\n" +
					"\t density                     the fraction of pairs of nodes that are neighbors of each other\n" +
					"\t tightness                   if >0, the output problem contains unary constraints of expected tightness equal to this input parameter\n" +
					"\t nbrColors                   the number of colors\n" +
					"\t stochNodeRatio [optional]   the fraction of nodes whose color is uncontrollable; the output is a StochDCOP (default is 0)");
			System.exit(1);
		}
		
		final int nbrNodes = Integer.parseInt(args2.get(0));
		assert nbrNodes >= 2 : "To few nodes (" + nbrNodes + ")";
		final double density = Double.parseDouble(args2.get(1));
		assert nbrLinks || (density >= 0 && density <= 1): "The input density is not between 0 and 1: " + density;
		final double tightness = Double.parseDouble(args2.get(2));
		assert tightness >= 0 && tightness <= 1: "The input tightness is not between 0 and 1: " + tightness;
		final int nbrColors = Integer.parseInt(args2.get(3));
		assert nbrColors > 0 : "The number of colors must be positive (" + nbrColors + ")";
		
		// Look for the ratio of stochastic nodes
		int nbrStochNodes = 0;
		if (args2.size() >= 5) 
			nbrStochNodes = (int) (nbrNodes * Double.parseDouble(args2.get(4)));
		assert nbrStochNodes >= 0 && nbrStochNodes <= nbrNodes : "Incorrect ratio of stochastic nodes";
		
		// Generate the random instance
		GraphColoring instance = new GraphColoring (nbrNodes, density, tightness, nbrColors, nbrStochNodes, nbrLinks);
//		new DOTrenderer ("graph", instance.graph.toString(), "twopi");
		
		// Generate the XCSP representation and write it to a file
		Document problem = instance.toXCSP(false, soft, intensional);
		new XMLOutputter(Format.getPrettyFormat()).output(problem, new FileWriter ("graphColoring.xml"));
		System.out.println("Wrote graphColoring.xml");

		if (mpc) {
			problem = instance.toXCSP(true, soft, intensional);
			new XMLOutputter(Format.getPrettyFormat()).output(problem, new FileWriter ("graphColoring_MPC.xml"));
			System.out.println("Wrote graphColoring_MPC.xml");
		}
	}
	
	/** Generates a problem instance
	 * @param soft 				whether to make it a DisCSP (\c false) or a Max-DisCSP (\c true)
	 * @param nbrNodes 			total number of nodes
	 * @param density 			graph density
	 * @param tightness 		the tightness of the unary constraints
	 * @param nbrColors 		number of colors
	 * @param nbrStochNodes 	number of uncontrollable nodes
	 * @param intensional 		whether the output should be intensional
	 * @return a problem instance
	 */
	public static Document generateProblem (boolean soft, int nbrNodes, double density, double tightness, int nbrColors, int nbrStochNodes, boolean intensional) {
		
		GraphColoring instance = new GraphColoring (nbrNodes, density, tightness, nbrColors, nbrStochNodes, false);
		return instance.toXCSP(false, soft, intensional);
	}
	
	/** The underlying graph */
	final public Graph graph;
	
	/** For each node, its list of forbidden colors */
	final public TreeMap< String, ArrayList<Integer> > unaryCons;
	
	/** The number of colors */
	final public int nbrColors;
	
	/** The set of uncontrollable nodes */
	final private HashSet<String> stochNodes;

	/** The name of the problem instance */
	private final String instanceName;

	/** The desired density */
	private double targetDensity = -1;
	
	/** The desired tightness of the unary constraints */
	private final double targetTightness;

	/** Constructor
	 * @param nbrNodes 			total number of nodes
	 * @param density 			graph density
	 * @param tightness 		the tightness of the unary constraints
	 * @param nbrColors 		number of colors
	 * @param nbrStochNodes 	number of uncontrollable nodes
	 * @param nbrLinks 			if true, the density parameter states that there must be density*nbrNodes links in the graph
	 */
	public GraphColoring (int nbrNodes, double density, final double tightness, final int nbrColors, int nbrStochNodes, boolean nbrLinks) {
		this(nbrLinks ? RandGraphFactory.getSizedRandGraph(nbrNodes, (int) (density * nbrNodes), 0) :
						RandGraphFactory.getSizedRandGraph(nbrNodes, (int) (density * nbrNodes * (nbrNodes - 1) / 2.0), 0), 
				tightness, nbrColors, nbrStochNodes);
		this.targetDensity = density;
	}
	
	/** Constructor
	 * @param graph 			the underlying graph
	 * @param tightness 		the tightness of the unary constraints
	 * @param nbrColors 		number of colors
	 * @param nbrStochNodes 	number of uncontrollable nodes
	 */
	public GraphColoring (Graph graph, final double tightness, final int nbrColors, final int nbrStochNodes) {
		this.graph = graph;
		this.targetTightness = tightness;
		this.nbrColors = nbrColors;
		this.instanceName = "graphColoring_" + System.currentTimeMillis();
		
		// The first nbrStochNodes nodes are selected as the uncontrollable ones
		this.stochNodes = new HashSet<String> ();
		for (int i = 0; i < nbrStochNodes; i++) 
			this.stochNodes.add(graph.nodes.get(i));
		
		this.unaryCons = new TreeMap< String, ArrayList<Integer> > ();
		if (tightness > 0) { // generate the unary constraints
			
			for (String n : this.graph.nodes) {
				if (this.stochNodes.contains(n)) // skip uncontrollable nodes
					continue;
				
				ArrayList<Integer> colors = new ArrayList<Integer> (nbrColors);
				for (int i = 0; i < nbrColors; i++) 
					if (Math.random() <= tightness) 
						colors.add(i);
				
				if (! colors.isEmpty() ) 
					this.unaryCons.put(n, colors);
			}
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
	
	/** Generates an XCSP representation of a graph coloring problem
	 * @param publicInteragentConstraints 	whether inter-agent constraints should be public
	 * @param soft 							whether the output should be a Max-DisCSP
	 * @param intensional 					whether the output should be intensional
	 * @return An XCSP-formatted Document
	 */
	public Document toXCSP (final boolean publicInteragentConstraints, final boolean soft, final boolean intensional) {
		
		// Create the root element
		Element probElement = new Element ("instance");
		probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschema" + (soft || !intensional ? "" : "JaCoP") + ".xsd", 
				Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
		
		// Create the "presentation" element
		Element presElmt = new Element ("presentation");
		probElement.addContent(presElmt);
		presElmt.setAttribute("name", this.instanceName);
		presElmt.setAttribute("maxConstraintArity", "2");
		presElmt.setAttribute("maximize", "false");
		presElmt.setAttribute("format", "XCSP 2.1_FRODO");
		
		// Create the "agents" element
		Element elmt = new Element ("agents");
		probElement.addContent(elmt);
		final int nbrNodes = graph.nodes.size();
		elmt.setAttribute("nbAgents", Integer.toString(nbrNodes - this.stochNodes.size()));
		for (String varID : this.graph.nodes) {
			
			// Skip the stoch nodes
			if (this.stochNodes.contains(varID)) 
				continue;
			
			Element subElmt = new Element ("agent");
			elmt.addContent(subElmt);
			subElmt.setAttribute("name", "a" + varID);
		}

		// Create the "domains" element
		elmt = new Element ("domains");
		probElement.addContent(elmt);
		elmt.setAttribute("nbDomains", "1");
		Element subElmt = new Element ("domain");
		elmt.addContent(subElmt);
		subElmt.setAttribute("name", "colors");
		subElmt.setAttribute("nbValues", Integer.toString(nbrColors));
		subElmt.addContent("1.." + Integer.toString(nbrColors));

		// Create the "variables" element
		elmt = new Element ("variables");
		probElement.addContent(elmt);
		elmt.setAttribute("nbVariables", Integer.toString(graph.nodes.size()));
		for (String varID : this.graph.nodes) {
			subElmt = new Element ("variable");
			elmt.addContent(subElmt);
			subElmt.setAttribute("name", "n" + varID);
			subElmt.setAttribute("domain", "colors");
			if (this.stochNodes.contains(varID)) 
				subElmt.setAttribute("type", "random");
			else 
				subElmt.setAttribute("agent", "a" + varID);
		}
		
		// Create the "relations" element
		Element relElmt = new Element ("relations");
		if (soft || !intensional || !this.unaryCons.isEmpty()) {
			probElement.addContent(relElmt);
		}
		
		if (soft || !intensional) { // extensional

			subElmt = new Element ("relation");
			relElmt.addContent(subElmt);
			subElmt.setAttribute("name", "neq");
			subElmt.setAttribute("semantics", "soft");
			subElmt.setAttribute("arity", "2");
			subElmt.setAttribute("defaultCost", "0");
			subElmt.setAttribute("nbTuples", Integer.toString(nbrColors));

			StringBuilder builder = new StringBuilder (soft ? "1: " : "infinity: ");
			for (int i = 1; i < nbrColors; i++) 
				builder.append(Integer.toString(i) + " " + i + " | ");
			builder.append(Integer.toString(nbrColors) + " " + nbrColors);
			subElmt.setText(builder.toString());

		} else { // pure satisfaction, intensional

			// Create the "predicates" element
			elmt = new Element ("predicates");
			probElement.addContent(elmt);
			elmt.setAttribute("nbPredicates", "1");

			subElmt = new Element ("predicate");
			elmt.addContent(subElmt);
			subElmt.setAttribute("name", "neq");

			elmt = new Element ("parameters");
			subElmt.addContent(elmt);
			elmt.setText("int X int Y");

			elmt = new Element ("expression");
			subElmt.addContent(elmt);

			subElmt = new Element ("functional");
			elmt.addContent(subElmt);
			subElmt.setText("ne(X, Y)");
		}
		
		if (! this.stochNodes.isEmpty()) {
			
			// Create the "probabilities" element
			elmt = new Element ("probabilities");
			probElement.addContent(elmt);
			elmt.setAttribute("nbProbabilities", Integer.toString(this.stochNodes.size()));
			
			for (String varID : this.stochNodes) {
				
				subElmt = new Element ("probability");
				elmt.addContent(subElmt);
				subElmt.setAttribute("name", "n" + varID + "proba");
				subElmt.setAttribute("semantics", "soft");
				subElmt.setAttribute("arity", "1");
				subElmt.setAttribute("nbTuples", Integer.toString(nbrColors));
				
				// Choose a random probability distribution
				StringBuilder builder = new StringBuilder ();
				double[] probas = new double [nbrColors];
				double sum = 0.0;
				for (int i = 0; i < nbrColors; i++) {
					probas[i] = Math.random();
					sum += probas[i];
				}
				for (int i = 0; i < nbrColors - 1; i++) 
					builder.append(Double.toString(probas[i] / sum) + ": " + (i+1) + " | ");
				builder.append(Double.toString(probas[nbrColors-1] / sum) + ": " + nbrColors);
				subElmt.setText(builder.toString());
			}
		}
		
		// Create the "constraints" element
		Element conElmt = new Element ("constraints");
		probElement.addContent(conElmt);
		
		// Create the unary constraints
		double tightness = 0.0;
		final double weight = 1.0 / (this.nbrColors * (this.graph.nodes.size() - this.stochNodes.size()));
		for (Map.Entry< String, ArrayList<Integer> > entry : this.unaryCons.entrySet()) {
			String n = entry.getKey();
			ArrayList<Integer> colors = entry.getValue();
			tightness += colors.size() * weight;

			// Create the relation
			subElmt = new Element ("relation");
			relElmt.addContent(subElmt);
			subElmt.setAttribute("name", "unaryRel_" + n);
			subElmt.setAttribute("semantics", soft || !intensional ? "soft" : "conflicts");
			subElmt.setAttribute("arity", "1");
			if (soft || !intensional) 
				subElmt.setAttribute("defaultCost", "0");
			subElmt.setAttribute("nbTuples", Integer.toString(colors.size()));

			StringBuilder builder = new StringBuilder (soft ? "1: " : !intensional ? "infinity: " : "");
			for (int i = colors.size() - 1; i > 0; i--) 
				builder.append(colors.get(i)).append("|");
			builder.append(colors.get(0));
			subElmt.setText(builder.toString());

			// Create the constraint
			elmt = new Element ("constraint");
			conElmt.addContent(elmt);
			elmt.setAttribute("name", "unaryCons_" + n);
			elmt.setAttribute("scope", "n" + n);
			elmt.setAttribute("arity", "1");
			elmt.setAttribute("reference", "unaryRel_" + n);
			elmt.setAttribute("agent", "a" + n);					
		}
		
		// Go through all edges in the graph
		for (Edge edge : graph.edges) {
			
			// Skip this constraint if it involves two random variables
			if (this.stochNodes.contains(edge.source) && this.stochNodes.contains(edge.dest)) 
				continue;
			
			elmt = new Element ("constraint");
			conElmt.addContent(elmt);
			
			final String n1 = "n" + edge.source;
			final String n2 = "n" + edge.dest;
			
			elmt.setAttribute("name", n1 + "_neq_" + n2);
			elmt.setAttribute("scope", n1 + " " + n2);
			elmt.setAttribute("arity", "2");
			elmt.setAttribute("reference", "neq");
			if (publicInteragentConstraints) 
				elmt.setAttribute("agent", "PUBLIC");
			
			if (! soft && intensional) {
				subElmt = new Element ("parameters");
				elmt.addContent(subElmt);
				subElmt.setText(n1 + " " + n2);
			}
		}
		
		// Add the probability distributions
		for (String varID : this.stochNodes) {
			final String varName = "n" + varID;
			
			elmt = new Element ("constraint");
			conElmt.addContent(elmt);
			elmt.setAttribute("name", varName + "dist");
			elmt.setAttribute("scope", varName);
			elmt.setAttribute("arity", "1");
			elmt.setAttribute("reference", varName + "proba");
		}

		relElmt.setAttribute("nbRelations", Integer.toString(relElmt.getContentSize()));
		conElmt.setAttribute("nbConstraints", Integer.toString(conElmt.getContentSize()));

		// Write the stats
		presElmt.addContent(createStats("number of nodes", Integer.toString(this.graph.nodes.size())));
		presElmt.addContent(createStats("target density", Double.toString(this.targetDensity)));
		presElmt.addContent(createStats("true average density", Double.toString(this.graph.computeDensity())));
		presElmt.addContent(createStats("target unary tightness", Double.toString(this.targetTightness)));
		presElmt.addContent(createStats("true average unary tightness", Double.toString(tightness)));
		presElmt.addContent(createStats("number of colors", Integer.toString(this.nbrColors)));
		presElmt.addContent(createStats("number of uncontrollable nodes", Integer.toString(this.stochNodes.size())));
		presElmt.addContent(createStats("number of disconnected components", Integer.toString(graph.components.size())));
		presElmt.addContent(createStats("max degree", Integer.toString(graph.computeMaxDeg())));

		return new Document (probElement);
	}

}
