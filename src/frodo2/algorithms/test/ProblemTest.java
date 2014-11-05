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

package frodo2.algorithms.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;

import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test case for the class Problem
 * @author Thomas Leaute
 */
public class ProblemTest extends TestCase {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 15;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 100;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;

	/** Random XCSP problem */
	private Document problem;
	
	/** A random graph*/
	private Graph graph;
	
	/** The parser */
	private XCSPparser<AddableInteger, AddableInteger> parser;

	/** For each agent, its correct subproblem */
	private HashMap<String, DCOPProblemInterface<AddableInteger, AddableInteger>> trueSubProbs;

	/** For each agent, its computed subproblem */
	private HashMap<String, DCOPProblemInterface<AddableInteger, AddableInteger>> subProbs;

	/** @return the test suite for this test */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("JUnit tests for Problem");
		
		TestSuite tmp = new TestSuite ("Tests for getAgent");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetAgent"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getAgents");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetAgents"), 1000));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getVariables");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetVariables"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getAnonymVars");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetAnonymVars"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getExtVars");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetExtVars"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getMyVars");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetMyVars"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getNbrIntVars");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetNbrIntVars"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getOwner");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetOwner"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getOwners");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetOwners"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for isRandom");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testIsRandom"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getDomain");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetDomain"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getDomainSize");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetDomainSize"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for setDomain");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testSetDomain"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getNeighborhoods");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetNeighborhoods"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getAnonymNeighborhoods");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetAnonymNeighborhoods"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getAgentNeighborhoods");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetAgentNeighborhoods"), 1000));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getNeighborhoodSizes");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetNeighborhoodSizes"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getNbrNeighbors");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetNbrNeighbors"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getSolutionSpaces");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetSolutionSpaces"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getSolutionSpacesString");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetSolutionSpacesString"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getSolutionSpacesStringBoolean");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetSolutionSpacesStringBoolean"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getSolutionSpacesStringSet");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetSolutionSpacesStringSet"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getSolutionSpacesStringBooleanSet");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetSolutionSpacesStringBooleanSet"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getProbabilitySpaces");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetProbabilitySpaces"), 100));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests for getProbabilitySpacesString");
		tmp.addTest(new RepeatedTest (new ProblemTest ("testGetProbabilitySpacesString"), 100));
		suite.addTest(tmp);
				
		return suite;
	}

	/** Constructor 
	 * @param method 	name of the test method to launch
	 */
	public ProblemTest(String method) {
		super(method);
	}

	/** @see junit.framework.TestCase#setUp() */
	protected void setUp() {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problem = AllTests.generateProblem(graph, graph.nodes.size(), false);
		parser = new XCSPparser<AddableInteger, AddableInteger> (problem);
		trueSubProbs = new HashMap<String, DCOPProblemInterface<AddableInteger, AddableInteger>> ();
		subProbs = new HashMap<String, DCOPProblemInterface<AddableInteger, AddableInteger>> ();
		for (String agent : parser.getAgents()) {

			// Skip this agent if it owns no variable
			if (this.parser.getVariables(agent).isEmpty()) 
				continue;
			
			DCOPProblemInterface<AddableInteger, AddableInteger> subproblem = (DCOPProblemInterface<AddableInteger, AddableInteger>)parser.getSubProblem(agent);
			trueSubProbs.put(agent, subproblem);
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = subproblem.getSolutionSpaces(true);
			HashMap<String, AddableInteger[]> domains = new HashMap<String, AddableInteger[]> ();
			for (String var : subproblem.getVariables()) 
				domains.put(var, subproblem.getDomain(var));
			for (String var : subproblem.getAnonymVars()) 
				domains.put(var, subproblem.getDomain(var));
			subProbs.put(agent, new Problem<AddableInteger, AddableInteger> (agent, subproblem.getOwners(), domains, spaces));
		}
	}

	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown() throws Exception {
		super.tearDown();
		this.graph = null;
		problem = null;
		parser = null;
		trueSubProbs = null;
		subProbs = null;
	}

	/** Test method for Problem#getAgent(). */
	public void testGetAgent() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : subProbs.entrySet()) 
			assertEquals (entry.getKey(), entry.getValue().getAgent());
	}

	/** Test method for Problem#getAgents(). */
	public void testGetAgents() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getAgents(), subProbs.get(entry.getKey()).getAgents());
	}

	/** Test method for Problem#getVariables(). */
	public void testGetVariables() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getVariables(), subProbs.get(entry.getKey()).getVariables());
	}

	/** Test method for Problem#getAnonymVars(). */
	public void testGetAnonymVars() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getAnonymVars(), this.subProbs.get(entry.getKey()).getAnonymVars());
	}

	/** Test method for Problem#getExtVars(). */
	public void testGetExtVars() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getExtVars(), this.subProbs.get(entry.getKey()).getExtVars());
	}

	/** Test method for Problem#getMyVars(). */
	public void testGetMyVars() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getMyVars(), subProbs.get(entry.getKey()).getMyVars());
	}

	/** Test method for Problem#getNbrIntVars(). */
	public void testGetNbrIntVars() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getNbrIntVars(), this.subProbs.get(entry.getKey()).getNbrIntVars());
	}

	/** Test method for Problem#getOwner(java.lang.String). */
	public void testGetOwner() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : trueSubProbs.entrySet()) {
			DCOPProblemInterface<AddableInteger, AddableInteger> trueSubProb = entry.getValue();
			DCOPProblemInterface<AddableInteger, AddableInteger> subproblem = subProbs.get(entry.getKey());
			
			for (String var : trueSubProb.getVariables()) {
				String trueOwner = trueSubProb.getOwner(var);
				String owner = subproblem.getOwner(var);
				if (trueOwner == null) {
					assertNull (owner); 
				} else 
					assertEquals (trueOwner, owner);
			}
		}
	}

	/** Test method for Problem#getOwners(). */
	public void testGetOwners() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getOwners(), this.subProbs.get(entry.getKey()).getOwners());
	}

	/** Test method for Problem#isRandom(java.lang.String). */
	public void testIsRandom() {
		/// @todo Not implemented. 
	}

	/** Test method for Problem#getDomain(java.lang.String). */
	public void testGetDomain() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) {
			DCOPProblemInterface<AddableInteger, AddableInteger> trueSubProb = entry.getValue();
			DCOPProblemInterface<AddableInteger, AddableInteger> subProb = this.subProbs.get(entry.getKey());
			for (String var : trueSubProb.getAllVars()) 
				assertEquals (Arrays.asList(trueSubProb.getDomain(var)), Arrays.asList(subProb.getDomain(var)));
		}
	}

	/** Test method for Problem#getDomainSize(java.lang.String). */
	public void testGetDomainSize() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) {
			DCOPProblemInterface<AddableInteger, AddableInteger> trueSubProb = entry.getValue();
			DCOPProblemInterface<AddableInteger, AddableInteger> subProb = this.subProbs.get(entry.getKey());
			for (String var : trueSubProb.getAllVars()) 
				assertEquals (trueSubProb.getDomainSize(var), subProb.getDomainSize(var));
		}
	}

	/** Test method for Problem#setDomain(java.lang.String, Addable[]). */
	public void testSetDomain() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getNeighborhoods(). */
	public void testGetNeighborhoods() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getNeighborhoods(), subProbs.get(entry.getKey()).getNeighborhoods());
	}

	/** Test method for Problem#getAnonymNeighborhoods(). */
	public void testGetAnonymNeighborhoods() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getAgentNeighborhoods(). */
	public void testGetAgentNeighborhoods() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getAgentNeighborhoods(), subProbs.get(entry.getKey()).getAgentNeighborhoods());
	}

	/** Test method for Problem#getNeighborhoodSizes(). */
	public void testGetNeighborhoodSizes() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) 
			assertEquals (entry.getValue().getNeighborhoodSizes(), this.subProbs.get(entry.getKey()).getNeighborhoodSizes());
	}

	/** Test method for Problem#getNbrNeighbors(java.lang.String). */
	public void testGetNbrNeighbors() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) {
			DCOPProblemInterface<AddableInteger, AddableInteger> trueSubProb = entry.getValue();
			DCOPProblemInterface<AddableInteger, AddableInteger> subProb = this.subProbs.get(entry.getKey());
			for (String var : trueSubProb.getMyVars()) 
				assertEquals (trueSubProb.getNbrNeighbors(var), subProb.getNbrNeighbors(var));
		}
	}

	/** Test method for Problem#getSolutionSpaces(boolean). */
	public void testGetSolutionSpaces() {
		
		for (Map.Entry<String, DCOPProblemInterface<AddableInteger, AddableInteger>> entry : this.trueSubProbs.entrySet()) {
			DCOPProblemInterface<AddableInteger, AddableInteger> trueSubProb = entry.getValue();
			DCOPProblemInterface<AddableInteger, AddableInteger> subProb = this.subProbs.get(entry.getKey());
			
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > trueSpaces = trueSubProb.getSolutionSpaces(true);
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = subProb.getSolutionSpaces(true);
			assertEquals (trueSpaces, spaces);

			trueSpaces = trueSubProb.getSolutionSpaces(false);
			spaces = subProb.getSolutionSpaces(false);
			assertEquals (trueSpaces, spaces);
		}
	}

	/** Test method for Problem#getSolutionSpaces(java.lang.String). */
	public void testGetSolutionSpacesString() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getSolutionSpaces(java.lang.String, boolean). */
	public void testGetSolutionSpacesStringBoolean() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getSolutionSpaces(java.lang.String, Set). */
	public void testGetSolutionSpacesStringSet() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getSolutionSpaces(java.lang.String, boolean, Set). */
	public void testGetSolutionSpacesStringBooleanSet() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getProbabilitySpaces(). */
	public void testGetProbabilitySpaces() {
		/// @todo Not yet implemented
	}

	/** Test method for Problem#getProbabilitySpaces(java.lang.String). */
	public void testGetProbabilitySpacesString() {
		/// @todo Not yet implemented
	}

}
