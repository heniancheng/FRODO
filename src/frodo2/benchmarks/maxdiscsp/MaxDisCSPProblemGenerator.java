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

/** A Max-DisCSP problem generator */
package frodo2.benchmarks.maxdiscsp;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Edge;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.test.AllTests;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.hypercube.Hypercube;

/**
 * Max-DisCSP problem generator  
 * 
 * Such CSPs are characterized by the 4-tuple (n,k,p1,p2), where:
 * n is the number of variables, 
 * k is the common domain size, 
 * p1 is the density, i.e. fraction of the n * (n - 1) /2 possible constraints in the graph, 
 * p2 is the tightness, i.e. the fraction of the k*k  value pairs in each constraint that are disallowed by the constraint (cost 1). 
 * All the other possible value pairs have cost 0.   
 * 
 * An agent owns only one variable, and all constraints are binary.
 *   
 * @author Alexandra Olteanu, Thomas Leaute
 */
public class MaxDisCSPProblemGenerator 
{	
		/**
		 * Method to create a random, sized, uniform domain Max-DisCSP problem with a certain density and tightness.
		 * @param n		The number of variables/agents in the problem
		 * @param k		The number of values in each variable domain
		 * @param p1	Density 
		 * @param p2	Tightness 
		 * @return		A Document containing a random Max-DisCSP problem.
		 */
		public static Document createSizedRandProblem (int n, int k, double p1, double p2) 
		{
			// apply the density p1, by reducing the possible number of edges in the graph
			Graph g = RandGraphFactory.getSizedRandGraph(n, (int)(Math.round(n * (n-1) * 0.5 * p1)), 0);
			return generateProblem (g, k, p1, p2);
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
		
		/** Creates a problem description based on the input constraint graph
		 * @param graph 	a constraint graph
		 * @param k			the uniform domain size. Each variable domain will have values {1,2, ... ,k}
		 * @param p1 		the target density of the constraint graph
		 * @param p2 		the target tightness of the constraint graph
		 * @return a problem description based on the input graph
		 * @see AllTests#createRandProblem(int, int, int, boolean)
		*/
		public static Document generateProblem (Graph graph, int k, double p1, double p2) 
		{	
			// Create the root element
			
			Element probElement = new Element ("instance");
			probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschema.xsd", 
					Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
	
			// Create the "presentation" element
			Element presElmt = new Element ("presentation");
			probElement.addContent(presElmt);
			presElmt.setAttribute("name", "randomMaxDisCSP_" + System.currentTimeMillis());
			presElmt.setAttribute("maxConstraintArity", "2");
			presElmt.setAttribute("maximize", Boolean.toString(false));
			presElmt.setAttribute("format", "XCSP 2.1_FRODO");
			
			// Create the "agents" element
			Element elmt = new Element ("agents");
			probElement.addContent(elmt);
			elmt.setAttribute("nbAgents", Integer.toString(graph.nodes.size()));
			for (String varID : graph.nodes) {
				Element agent = new Element ("agent");
				elmt.addContent(agent);
				agent.setAttribute("name", "a" + varID);
			}
	
			// Create the "domains" element
			// The random hypercubes only use the domain {1,2, ... ,k}
			elmt = new Element ("domains");
			probElement.addContent(elmt);
			elmt.setAttribute("nbDomains", "1");
			Element subElmt = new Element ("domain");
			elmt.addContent(subElmt);
			subElmt.setAttribute("name", "D");
			subElmt.setAttribute("nbValues", ""+k);
			subElmt.addContent("1.."+k);		
			
			// Create the "variables" element
			Element varsElement = new Element ("variables");
			probElement.addContent(varsElement);
			varsElement.setAttribute("nbVariables", Integer.toString(graph.nodes.size()));
	
			// Take care of the variables
			for (String varID : graph.nodes) 
			{
				elmt = new Element ("variable");
				varsElement.addContent(elmt);
				elmt.setAttribute("name", "x" + varID);
				elmt.setAttribute("domain", "D");
				elmt.setAttribute("agent", "a" + varID); // make sure each agent owns one variable
			}
	
			// Create the "relations" and "constraints" elements
			Element relElement = new Element ("relations");
			probElement.addContent(relElement);
			Element conElement = new Element ("constraints");
			probElement.addContent(conElement);
		 
			// Generate a random hypercube for each pair of connected variables in the graph
			double trueP2 = 0.0;
			final int nbrEdges = graph.edges.length;
			final double tupleWeight = 1.0 / (nbrEdges * k * k);
			for (int i = 0; i<graph.edges.length; i++)
			{
				Edge e = graph.edges[i];		
				List<String> vars = new ArrayList<String>();
				
				vars.add("x" + e.dest);
				vars.add("x" + e.source);
				Hypercube< AddableInteger, AddableInteger > hypercube = randHypercube(vars, k, p2);
				hypercube.setName("c_" + Integer.toString(i));
	
				// 	Create the "relation" element
				elmt = new Element ("relation");
				relElement.addContent(elmt);
				elmt.setAttribute("name", "r_" + String.valueOf(i));
				elmt.setAttribute("arity", "2");
				elmt.setAttribute("semantics", "soft");
				elmt.setAttribute("defaultCost", "0");
				
				// Write the tuples
				StringBuilder builder = new StringBuilder ("1: ");
				int j = 0;
				for (Iterator<AddableInteger, AddableInteger> iter = hypercube.iterator(); iter.hasNext(); ) {
					
					// Skip this tuple if it has the default utility 0
					if (iter.nextUtility().intValue() == 0) 
						continue;
					trueP2 += tupleWeight;
					
					// Write the assignment
					j++;
					for (AddableInteger val : iter.getCurrentSolution()) 
						builder.append(val.toString() + " ");
					
					builder.append("|");
				}

				// Remove the last |
				builder.deleteCharAt(builder.length() - 1);
				if (j > 0) 
					elmt.addContent(builder.toString());
				elmt.setAttribute("nbTuples", Integer.toString(j));

				
				// Create the "constraint" element
				conElement.addContent(XCSPparser.getConstraint(hypercube, "c_" + Integer.toString(i), "r_" + Integer.toString(i)));
			}
			
			relElement.setAttribute("nbRelations", Integer.toString(graph.edges.length));
			conElement.setAttribute("nbConstraints", Integer.toString(graph.edges.length));
			
			// Write the stats
			final int nbrVars = graph.nodes.size();
			presElmt.addContent(createStats("number of variables", Integer.toString(nbrVars)));
			presElmt.addContent(createStats("domain size", Integer.toString(k)));
			presElmt.addContent(createStats("target density p1", Double.toString(p1)));
			presElmt.addContent(createStats("true average density p1", Double.toString(graph.computeDensity())));
			presElmt.addContent(createStats("target tightness p2", Double.toString(p2)));
			presElmt.addContent(createStats("true average tightness p2", Double.toString(trueP2)));
			presElmt.addContent(createStats("number of disconnected components", Integer.toString(graph.components.size())));
			presElmt.addContent(createStats("max degree", Integer.toString(graph.computeMaxDeg())));
	
			return new Document (probElement);
		}
	
	
		/** Generates a random hypercube
		 * 
		 * All domains are {1, ..., k} and utility values are either 0 or 1 
		 * The utility can be 
		 * 		1, with probability p2
		 * 		0, with probability 1-p2
		 * @param variables 	list of variables involved
		 * @param k 			the size of the uniform domain
		 * @param p2 			tightness of constraint graph
		 * @return a random hypercube
		 */
		public static Hypercube<AddableInteger, AddableInteger> randHypercube (List<String> variables, int k, double p2)
		{	
			int n = variables.size();
			// Create the uniform domains
			AddableInteger[][] domains = new AddableInteger[n][];
			AddableInteger[] domain = new AddableInteger[k];
			for (int i=0; i<k; i++)
				domain[i] = new AddableInteger (i+1);
			for (int v=0; v<n; v++)
				domains[v] = domain;
					
			// Create the utilities randomly
			int nbrUtil = (int) Math.pow(k, (double)n);
			AddableInteger[] utilities = new AddableInteger[nbrUtil];
			for (int i = 0; i < nbrUtil; i++) 
			{
				if (Math.random() < p2) 
					utilities[i] = new AddableInteger(1);
				else 
					utilities[i] = new AddableInteger(0);
			}		
			return new Hypercube<AddableInteger, AddableInteger> (variables.toArray(new String[0]), domains, utilities, new AddableInteger(1));
		}
		
		 
		/**
		 * @param args Expects: n k p1 p2
		 * @todo Add support for MPC-DisWCSP4
		 */
		public static void main(String[] args) 
		{
			
			// The GNU GPL copyright notice
			System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
			System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
			System.out.println("This is free software, and you are welcome to redistribute it");
			System.out.println("under certain conditions. \n");

		/*
		CONCEPT:
			In generating a CSP (n, k, pl ,p2) exactly p1 * n * (n - 1)/2 constraints should 
			randomly be selected (rounded to the nearest integer), and 
			for each constraint selected exactly p2*k*k pairs of values 
			should be selected to violate the constraint (i.e. have cost 1)
			(again, rounded to the nearest integer)
			
			To accomplish this, the problem generator explicitly sets the number of edges in the constraint graph by:
				finding the total number of possible edges (n * (n)-1/2) and multiplying this by density (p1).
			The generator also looks at all variables in the graph that are linked by an edge
					and, for all possible value assignments from the domain, it assigns either utility 1 (with probability p), or 0 (with probability 1-p2)  				
			*/
			
			// Parse the input arguments
			if (args.length != 4) 
			{
				System.err.println("Usage: MaxDisCSPProblemGenerator nbrVars domainSize density(p1) tightness(p2). Eg: MaxDisCSPProblemGenerator 10 10 0.3 0.7");
				System.exit(1);
			}
		
			try
			{
				int n = Integer.parseInt(args[0]);
				int k = Integer.parseInt(args[1]);
				double p1 = Double.parseDouble(args[2]);
				double p2 = Double.parseDouble(args[3]);
				// Generate a problem instance
				Document problem = createSizedRandProblem(n, k, p1, p2);
				
				// Write the problem to a file
				String fileName = "random_Max-DisCSP.xml";
				FileWriter fw;
				try 
				{
					fw = new FileWriter (fileName);
					new XMLOutputter(Format.getPrettyFormat()).output(problem, fw);
				} 
				catch (IOException e) 
				{
					System.err.println("Could not save the problem to a file");
					e.printStackTrace();
					System.exit(1);
				}
				System.out.println("Wrote " + fileName);
			}
			catch (Exception e)
			{
				System.err.println("The input parameters are not numbers (nbrVars and domainSize should be integer and density and tightness should be real.");
				System.err.println("Usage: MaxDisCSPProblemGenerator nbrVars domainSize density(p1) tightness(p2). Eg: MaxDisCSPProblemGenerator 10 10 0.3 0.7");
				System.exit(1);
			}
		} // main
		
} // class