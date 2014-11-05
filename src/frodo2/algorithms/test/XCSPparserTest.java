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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Test suite for XCSPparser
 * @author Thomas Leaute
 * @todo Fully test extendedRandNeighborhoods
 */
public class XCSPparserTest extends TestCase {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 15;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 100;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;

	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;
	
	/** Random XCSP problem */
	protected Document problem;
	
	/** The parser for integer utilities */
	protected XCSPparser< AddableInteger, AddableInteger > parserInt;

	/** The parser for real utilities */
	protected XCSPparser< AddableInteger, AddableReal > parserReal;

	/** The list of solution spaces in the problem */
	protected ArrayList< Hypercube<AddableInteger, AddableInteger> > solutionSpaces = new ArrayList< Hypercube<AddableInteger, AddableInteger> > ();
	
	/** The list of probability spaces in the problem */
	protected ArrayList< Hypercube<AddableInteger, AddableReal> > probSpaces = new ArrayList< Hypercube<AddableInteger, AddableReal> > ();

	/** Whether we want extended random neighborhoods */
	private boolean extendedRandNeighborhoods;

	/** Whether each agent knows the identities of all agents */
	private boolean publicAgents = false;

	/** @return the test suite for this test */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("JUnit tests for XCSPparser");
		
		suite.addTest(new XCSPparserTest ("testGetZeroUtility"));

		suite.addTest(new XCSPparserTest ("testGetPlusInfUtility"));

		suite.addTest(new XCSPparserTest ("testGetMinInfUtility"));
		
		TestSuite tmp = new TestSuite ("Tests for getAgents");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetAgents"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getOwner");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetOwner"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getOwners");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetOwners"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNbrVars");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNbrVars"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNbrVars that takes in the name of an agent");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNbrVarsString"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getVariables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetVariables"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getVariables for an input agent");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetVariablesString"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getExtVars");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetExtVars"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNeighborVars");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNeighborVars", false, false), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNeighborVars with extendedRandNeighborhoods");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNeighborVars", true, false), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNeighborVars with anonym variables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNeighborVarsWithAnonymVars", false, false), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNeighborVars with extendedRandNeighborhoods and anonym variables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNeighborVarsWithAnonymVars", true, false), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNbrNeighbors");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNbrNeighbors"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNeighborhoods");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNeighborhoods"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getAnonymNeighborhoods");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetAnonymNeighborhoods"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getNeighborhoodSizes");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetNeighborhoodSizes"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getAgentNeighborhoods");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetAgentNeighborhoods"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getDomainSize");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetDomainSize"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getDomain");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetDomain"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for setDomain");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testSetDomain"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSubProblem");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSubProblem", false, false), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSubProblem with extendedRandNeighborhoods");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSubProblem", true, false), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSubProblem with all agents knowing each other");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSubProblem", false, true), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for isRandom");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testIsRandom"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSolutionSpaces with random variables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSolutionSpacesWithRandVars"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSolutionSpaces ignoring random variables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSolutionSpaces"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getProbabilitySpaces");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetProbabilitySpaces"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSolutionSpaces for a specific variable with random variables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSolutionSpacesForVarWithRandVars"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getSolutionSpaces for a specific variable ignoring random variables");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetSolutionSpacesForVar"), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for getProbabilitySpaces for a specific variable");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetProbabilitySpacesForVar"), 100));
		suite.addTest(tmp);

		tmp = new TestSuite ("Tests for getUtility on complete assignments");
		tmp.addTest(new RepeatedTest (new XCSPparserTest ("testGetUtility"), 500));
		suite.addTest(tmp);

		return suite;
	}

	/** Generates a test using the specified method
	 * @param name 	name of the method
	 */
	public XCSPparserTest(String name) {
		super(name);
	}

	/** Generates a test using the specified method
	 * @param name 							name of the method
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 * @param publicAgents 					Whether each agent knows the identities of all agents
	 */
	public XCSPparserTest(String name, boolean extendedRandNeighborhoods, boolean publicAgents) {
		super(name);
		this.extendedRandNeighborhoods = extendedRandNeighborhoods;
		this.publicAgents = publicAgents;
	}

	/** @see junit.framework.TestCase#setUp() */
	protected void setUp() {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		solutionSpaces = new ArrayList< Hypercube<AddableInteger, AddableInteger> > ();
		probSpaces = new ArrayList< Hypercube<AddableInteger, AddableReal> > ();
		problem = AllTests.generateProblem(graph, graph.nodes.size(), solutionSpaces, probSpaces, true);
		parserInt = new XCSPparser<AddableInteger, AddableInteger> (problem, false, this.extendedRandNeighborhoods, this.publicAgents);
		parserInt.setUtilClass(AddableInteger.class);
		parserReal = new XCSPparser<AddableInteger, AddableReal> (problem, false, this.extendedRandNeighborhoods, this.publicAgents);
		parserReal.setUtilClass(AddableReal.class);
	}

	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown() throws Exception {
		super.tearDown();
		graph = null;
		problem = null;
		parserInt = null;
		parserReal = null;
		this.solutionSpaces = null;
		this.probSpaces = null;
	}

	/** Test method for getZeroUtility() */
	public void testGetZeroUtility() {

		// Test with AddableIntegers
		AddableInteger integer = new AddableInteger (1);
		AddableInteger zeroInt = parserInt.getZeroUtility();
		assertTrue (integer.equals(integer.add(zeroInt)));
		assertTrue (integer.equals(zeroInt.add(integer)));

		// Test with AddableReals
		AddableReal real = new AddableReal (1.0);
		AddableReal zeroReal = parserReal.getZeroUtility();
		assertTrue (real.equals(real.add(zeroReal)));
		assertTrue (real.equals(zeroReal.add(real)));
	}

	/** Test method for getPlusInfUtility() */
	public void testGetPlusInfUtility() {

		// Test with AddableIntegers
		AddableInteger integer = new AddableInteger (1);
		AddableInteger intPlusInf = parserInt.getPlusInfUtility();
		assertTrue (intPlusInf.equals(integer.add(intPlusInf)));
		assertTrue (intPlusInf.equals(intPlusInf.add(integer)));

		// Test with AddableReals
		AddableReal real = new AddableReal (1.0);
		AddableReal realPlusInf = parserReal.getPlusInfUtility();
		assertTrue (realPlusInf.equals(real.add(realPlusInf)));
		assertTrue (realPlusInf.equals(realPlusInf.add(real)));
	}

	/** Test method for getMinInfUtility() */
	public void testGetMinInfUtility() {

		// Test with AddableIntegers
		AddableInteger integer = new AddableInteger (1);
		AddableInteger intMinInf = parserInt.getMinInfUtility();
		assertTrue (intMinInf.equals(integer.add(intMinInf)));
		assertTrue (intMinInf.equals(intMinInf.add(integer)));

		// Test with AddableReals
		AddableReal real = new AddableReal (1.0);
		AddableReal realMinInf = parserReal.getMinInfUtility();
		assertTrue (realMinInf.equals(real.add(realMinInf)));
		assertTrue (realMinInf.equals(realMinInf.add(real)));
	}

	/** Test method for XCSPparser#getAgents() */
	public void testGetAgents() {
		
		Set<String> agents = parserInt.getAgents();
		int nbrClusters = graph.clusters.size();
		
		// Remove all cluster IDs from the list of agents
		for (int i = 0; i < nbrClusters; i++) 
			assertTrue (agents.remove(String.valueOf(i)));
		
		assertTrue (agents.isEmpty());
	}

	/** Test method for XCSPparser#getOwner(java.lang.String) */
	public void testGetOwner() {
		
		for (String var : graph.nodes) 
			assertEquals (graph.clusterOf.get(var).toString(), parserInt.getOwner(var));
	}
	
	/** Computes the correct neighborhoods
	 * @param withAnonymVars 	whether to consider anonymous neighbors
	 * @return the neighborhoods 
	 */
	protected Map< String, ? extends Collection<String> > getNeighborhoods(final boolean withAnonymVars) {

		Set<String> allVars = this.parserInt.getVariables();
		allVars.addAll(this.parserInt.getVariables(null));
		HashMap< String, HashSet<String> > out = new HashMap< String, HashSet<String> > (allVars.size());
		for (String var : allVars) {
			HashSet<String> neighbors = new HashSet<String> ();
			out.put(var, neighbors);

			if (this.extendedRandNeighborhoods) {

				// Go through all spaces, looking for direct neighbors and neighbors of the variable's extended random neighborhood
				HashSet<String> randVars = this.getExtendedRandNeighbors(var);
				for (Hypercube<AddableInteger, AddableInteger> space : this.solutionSpaces) 
					if (space.getDomain(var) != null || ! Collections.disjoint(randVars, Arrays.asList(space.getVariables()))) 
						for (String var2 : space.getVariables()) 
							if (withAnonymVars || ! this.parserInt.isRandom(var2)) 
								neighbors.add(var2);

			} else { // without extendedRandNeighborhoods

				for (Hypercube<AddableInteger, AddableInteger> space : this.solutionSpaces) 
					if (space.getDomain(var) != null) 
						for (String var2 : space.getVariables()) 
							if (withAnonymVars || ! this.parserInt.isRandom(var2)) 
								neighbors.add(var2);
			}

			// Remove var from its own neighborhood
			neighbors.remove(var);
		}
		
		return out;
	}
	
	/** Builds the set of all random variables reachable from var by paths involving only random variables
	 * @param var 	the input variable
	 * @return the extended random neighbors of var
	 */
	private HashSet<String> getExtendedRandNeighbors (String var) {
		
		// Build the set of all random variables reachable from var by paths involving only random variables
		HashSet<String> randVars = new HashSet<String> ();
		randVars.add(var);
		boolean more;
		do {
			more = false; // will be set to true if a variable is added to randVars
			
			// Go through all spaces, looking for those that involve a variable in randVars
			for (Hypercube<AddableInteger, AddableInteger> space : this.solutionSpaces) 
				if (! Collections.disjoint(randVars, Arrays.asList(space.getVariables()))) 
					for (String var2 : space.getVariables()) // go through all random variables in that space
						if (this.parserInt.isRandom(var2)) 
							more = more || randVars.add(var2);
		} while (more);
		
		// Remove the variable itself
		randVars.remove(var);
		
		return randVars;
	}

	/** Test method for XCSPparser#getOwners() */
	public void testGetOwners() {
		
		// First test on the overall problem
		Map<String, String> owners = parserInt.getOwners();
		assertEquals (graph.nodes.size(), owners.size());
		
		for (Map.Entry<String, String> entry : owners.entrySet())
			assertEquals (graph.clusterOf.get(entry.getKey()).toString(), entry.getValue());
		
		// Now test on each agent's subproblem
		for (int i = 0; i < graph.clusters.size(); i++) {
			String agent = String.valueOf(i);
			
			DCOPProblemInterface<AddableInteger, AddableInteger> subProb = parserInt.getSubProblem(agent);
			owners = subProb.getOwners();
			
			// Go through all variables owned by the current agent, and examine their neighbors
			for (String var : graph.clusters.get(i)) {
				
				assertEquals (agent, owners.get(var));
				
				// Go through all neighbors of the current variable
				for (String neigh : graph.neighborhoods.get(var)) 
					assertEquals ((int) graph.clusterOf.get(neigh), (int) Integer.parseInt(owners.get(neigh)));
			}
		}
	}

	/** Test method for XCSPparser#getNbrVars() */
	public void testGetNbrVars() {
		
		// Test for the overall problem
		assertEquals (graph.nodes.size(), parserInt.getNbrVars());
		
		// Test for each agent's subproblem
		for (int i = 0; i < graph.clusters.size(); i++) {
			
			// Compute the correct number of variables in this agent's subproblem
			HashSet<String> allVars = new HashSet<String> ();
			List<String> intVars = graph.clusters.get(i);
			for (String intVar : intVars) 
				allVars.addAll(graph.neighborhoods.get(intVar));
			allVars.addAll(intVars);
			
			assertEquals (allVars.size(), parserInt.getSubProblem(String.valueOf(i)).getNbrVars());
		}
	}

	/** Test method for XCSPparser#getNbrVars(java.lang.String) */
	public void testGetNbrVarsString() {
		
		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++)
			assertEquals (graph.clusters.get(i).size(), parserInt.getNbrVars(String.valueOf(i)));
	}

	/** Test method for XCSPparser#getVariables() */
	public void testGetVariables() {

		// First test for the overall problem
		Collection<String> vars = parserInt.getVariables();
		for (String var : graph.nodes) 
			assertTrue (vars.remove(var));
		assertTrue (vars.isEmpty());
		
		// Now test for each agent's subproblem
		for (int i = 0; i < graph.clusters.size(); i++) {
			
			// Compute the correct set of variables in this agent's subproblem
			HashSet<String> allVars = new HashSet<String> ();
			List<String> intVars = graph.clusters.get(i);
			for (String intVar : intVars) 
				allVars.addAll(graph.neighborhoods.get(intVar));
			allVars.addAll(intVars);
			
			vars = parserInt.getSubProblem(String.valueOf(i)).getVariables();
			for (String var : allVars) 
				assertTrue (vars.remove(var));
			assertTrue (vars.isEmpty());
		}
		
	}

	/** Test method for XCSPparser#getVariables(java.lang.String) */
	public void testGetVariablesString() {
		
		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++) {
			
			Collection<String> vars = parserInt.getVariables(String.valueOf(i));
			for (String var : graph.clusters.get(i)) 
				assertTrue (vars.remove(var));
			
			assertTrue (vars.isEmpty());
		}
		
		// Test for anonymous variables
		Collection<String> vars = parserInt.getVariables(null);
		for (Hypercube<AddableInteger, AddableReal> probSpace : this.probSpaces) 
			assertTrue (vars.remove(probSpace.getVariable(0)));
		assertTrue (vars.isEmpty());
	}
	
	/** Test method for XCSPparser#getExtVars() */
	public void testGetExtVars() {
		
		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++) {
			String agent = String.valueOf(i);
			
			DCOPProblemInterface<AddableInteger, AddableInteger> subProb = parserInt.getSubProblem(agent);
			Set<String> extVars = subProb.getExtVars();
			
			for (Map.Entry<String, String> entry : subProb.getOwners().entrySet()) 
				if (! entry.getValue().equals(agent)) 
					assertTrue (extVars.remove(entry.getKey()));
			
			assertTrue (extVars.isEmpty());
		}
	}

	/** Test method for XCSPparser#getNeighborVars(java.lang.String) */
	public void testGetNeighborVars() {
		this.testGetNeighborVars(false);
	}
	
	/** Test method for XCSPparser#getNeighborVars(java.lang.String, boolean) */
	public void testGetNeighborVarsWithAnonymVars() {
		this.testGetNeighborVars(true);
	}
	
	/** Test for getNeighborVars
	 * @param withAnonymVars 	if \c false, ignores variables with no specified owner
	 */
	public void testGetNeighborVars(boolean withAnonymVars) {
		
		// Test for each variable
		for (Map.Entry< String, ? extends Collection<String> > neighborhood : getNeighborhoods(withAnonymVars).entrySet()) {
			
			Collection<String> neighbors = parserInt.getNeighborVars(neighborhood.getKey(), withAnonymVars);
			for (String neighbor : neighborhood.getValue()) 
				if (! this.parserInt.isRandom(neighbor) || withAnonymVars) 
					assertTrue (neighbor + " should be a neighbor of " + neighborhood.getKey(), neighbors.remove(neighbor));
			
			assertTrue ("The following variables should not be neighbors of " + neighborhood.getKey() + ": " + neighbors, neighbors.isEmpty());
		}
	}

	/** Test method for XCSPparser#getNbrNeighbors(java.lang.String) */
	public void testGetNbrNeighbors() {
		
		// Test for each variable
		for (Map.Entry< String, ? extends Collection<String> > neighborhood : getNeighborhoods(false).entrySet()) {
			
			// First remove the anonym vars
			Collection<String> neighbors = neighborhood.getValue();
			for (Iterator<String> iter = neighbors.iterator(); iter.hasNext(); ) 
				if (this.parserInt.isRandom(iter.next())) 
					iter.remove();
			
			assertEquals (neighbors.size(), parserInt.getNbrNeighbors(neighborhood.getKey()));
		}
	}

	/** Test method for XCSPparser#getNeighborhoods(java.lang.String) */
	public void testGetNeighborhoods() {
		this.testGetNeighborhoods(false);
	}
	
	/** Test method for XCSPparser#getAnonymNeighborhoods(java.lang.String) */
	public void testGetAnonymNeighborhoods() {
		this.testGetNeighborhoods(true);
	}
	
	/** Test for XCSPparser#getNeighborhoods(java.lang.String) and XCSPparser#getAnonymNeighborhoods(java.lang.String)
	 * @param onlyAnonymVars 	whether we are only interested in random neighbors
	 */
	private void testGetNeighborhoods(final boolean onlyAnonymVars) {
		
		// Test for the overall problem
		Map< String, HashSet<String> > neighborhoods = (onlyAnonymVars ? parserInt.getAnonymNeighborhoods() : parserInt.getNeighborhoods());
		for (Map.Entry< String, ? extends Collection<String> > neighborhood : getNeighborhoods(false).entrySet()) {
			String var = neighborhood.getKey();

			// Skip random variables
			if (this.parserInt.isRandom(var)) 
				continue;

			Collection<String> neighbors = neighborhoods.remove(var);
			
			if (! onlyAnonymVars) {
				for (String neighbor : neighborhood.getValue()) 
					assertTrue (neighbors.remove(neighbor));
				assertTrue (neighbors.isEmpty());
				
			} else { // we want only anonymous neighbors
				
				// Go through the list of neighbors
				neighLoop: for (String neighbor : neighbors) {
					
					// Check that the neighbor is anonymous
					assertTrue (neighbor + " is not anonymous ", parserInt.isRandom(neighbor));
					
					// Check that we can actually find a space that contains both variables
					for (Hypercube<AddableInteger, AddableInteger> space : this.solutionSpaces) 
						if (space.getDomain(var) != null && space.getDomain(neighbor) != null) // find a space
							continue neighLoop;
					fail (var + " and " + neighbor + " are not neighbors");
				}
			}
		}
		assertTrue (neighborhoods.isEmpty());
		
		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++) {
			
			neighborhoods = (onlyAnonymVars ? parserInt.getAnonymNeighborhoods(String.valueOf(i)) : parserInt.getNeighborhoods(String.valueOf(i)));
			
			// Go through the list of correct neighborhoods for this agent
			for (Map.Entry< String, ? extends Collection<String> > neighborhood : getNeighborhoods(onlyAnonymVars).entrySet()) {
				String var = neighborhood.getKey();

				// Skip random variables
				if (this.parserInt.isRandom(var)) 
					continue;

				if (graph.clusterOf.get(var) == i) {
					
					Collection<String> neighbors = neighborhoods.remove(var);
					
					if (! onlyAnonymVars) {
						for (String neighbor : neighborhood.getValue()) 
							assertTrue (neighbors.remove(neighbor));
						assertTrue (neighbors.isEmpty());
						
					} else { // we want only anonymous neighbors
						
						// Go through the list of neighbors
						neighLoop: for (String neighbor : neighbors) {
							
							// Check that the neighbor is anonymous
							assertTrue (neighbor + " is not anonymous ", parserInt.isRandom(neighbor));
							
							// Check that we can actually find a space that contains both variables
							for (Hypercube<AddableInteger, AddableInteger> space : this.solutionSpaces) 
								if (space.getDomain(var) != null && space.getDomain(neighbor) != null) // find a space
									continue neighLoop;
							fail (var + " and " + neighbor + " are not neighbors");
						}
					}
				}
			}
			assertTrue (neighborhoods.isEmpty());
		}
	}

	/** Test method for XCSPparser#getNeighborhoodSizes(java.lang.String) */
	public void testGetNeighborhoodSizes() {
		
		// Test for the overall problem
		Map<String, Integer> sizes = parserInt.getNeighborhoodSizes();
		for (Map.Entry< String, ? extends Collection<String> > neighborhood : getNeighborhoods(false).entrySet()) {
			
			// Skip random variables
			String var = neighborhood.getKey();
			if (this.parserInt.isRandom(var)) 
				continue;
			
			assertEquals (neighborhood.getValue().size(), (int) sizes.remove(var));
		}
		assertTrue (sizes.isEmpty());

		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++) {

			sizes = parserInt.getNeighborhoodSizes(String.valueOf(i));

			// Go through the list of correct neighborhoods for this agent
			for (Map.Entry< String, ? extends Collection<String> > neighborhood : getNeighborhoods(false).entrySet()) {
				String var = neighborhood.getKey();

				// Skip random variables
				if (this.parserInt.isRandom(var)) 
					continue;

				if (graph.clusterOf.get(var) == i) 
					assertEquals (neighborhood.getValue().size(), (int) sizes.remove(var));
			}
			
			assertTrue (sizes.isEmpty());
		}
	}

	/** Test method for XCSPparser#getAgentNeighborhoods(java.lang.String) */
	public void testGetAgentNeighborhoods() {
		
		Map< String, ? extends Collection<String> > trueNeighborhoods = this.getNeighborhoods(false);
		
		// Test for the overall problem
		Map< String, Collection<String> > agentNeighborhoods = parserInt.getAgentNeighborhoods();
		
		for (Map.Entry< String, ? extends Collection<String> > entry : trueNeighborhoods.entrySet()) {
			String var = entry.getKey();
			
			// Skip random variables
			if (this.parserInt.isRandom(var)) 
				continue;
			
			// Compute the correct set of agent neighborhoods
			HashSet<String> agents = new HashSet<String> ();
			for (String neigh : entry.getValue()) 
				agents.add(graph.clusterOf.get(neigh).toString());
			agents.remove(graph.clusterOf.get(var).toString());
			
			// Compare the two collections
			Collection<String> neighbors = agentNeighborhoods.remove(var);
			for (String agent : agents) 
				assertTrue (neighbors.remove(agent));
			assertTrue (neighbors.isEmpty());
		}
		assertTrue (agentNeighborhoods.isEmpty());
		
		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++) {

			Map< String, Collection<String> > neighborhoods = parserInt.getAgentNeighborhoods(String.valueOf(i));
			
			// Go through the list of correct neighborhoods for this agent
			for (Map.Entry< String, ? extends Collection<String> > neighborhood : trueNeighborhoods.entrySet()) {
				String var = neighborhood.getKey();

				// Skip random variables
				if (this.parserInt.isRandom(var)) 
					continue;

				if (graph.clusterOf.get(var) == i) {
					
					Collection<String> agents = neighborhoods.get(var);
					
					// Compute the correct collection of neighboring agents
					Collection<String> agents2 = new HashSet<String> ();
					for (String neighboor : neighborhood.getValue()) 
						agents2.add(graph.clusterOf.get(neighboor).toString());
					agents2.remove(String.valueOf(i));
										
					// Compare the two collections
					for (String agent : agents2) 
						assertTrue (agents.remove(agent));
					assertTrue (agents.isEmpty());
				}
			}
		}
	}

	/** Test method for XCSPparser#getDomainSize(java.lang.String) */
	public void testGetDomainSize() {
		
		// Test for overall problem
		ArrayList<String> allVars = new ArrayList<String> (this.parserInt.getVariables());
		allVars.addAll(this.parserInt.getVariables(null)); // test also the variables with no specified owner 
		for (String var : allVars) 
			assertEquals (this.parserInt.getDomain(var).length, this.parserInt.getDomainSize(var));
		
		// Test for each agent's subproblem
		for (int i = 0; i < graph.clusters.size(); i++) {
			XCSPparser<AddableInteger, AddableInteger> subProb = parserInt.getSubProblem(String.valueOf(i));
			
			allVars = new ArrayList<String> (subProb.getVariables());
			allVars.addAll(subProb.getVariables(null)); // test also the variables with no specified owner 
			for (String var : allVars) 
				assertEquals (this.parserInt.getDomain(var).length, subProb.getDomainSize(var));
		}
	}

	/** Test method for frodo2.algorithms.XCSPparser#getDomain(java.lang.String).
	 * @throws JDOMException 	if an error occurs while reading the problem description 
	 */
	public void testGetDomain() throws JDOMException {
		
		// Parse all the correct domains
		HashMap< String, AddableInteger[] > doms = new HashMap< String, AddableInteger[] > ();
		for (Element domElmt : (List<Element>) problem.getRootElement().getChild("domains").getChildren()) {
			
			AddableInteger[] dom = new AddableInteger [Integer.parseInt(domElmt.getAttributeValue("nbValues"))];
			doms.put(domElmt.getAttributeValue("name"), dom);
			
			// Parse each component of the domain
			String[] comps = domElmt.getText().split("\\s+");
			int i = 0;
			for (String comp : comps) {
				
				// Check if the component is an interval or a single value
				String[] vals = comp.split("[.]{2}");
				
				if (vals.length == 1) { // single value
					dom[i++] = new AddableInteger (0).fromString(vals[0]);
				} 
				else // interval
					for (int val = Integer.parseInt(vals[0]); val <= Integer.parseInt(vals[1]); val++) 
						dom[i++] = new AddableInteger (val);
			}
		}
		
		// Go through the list of variables in the overall problem
		for (Element varElmt : (List<Element>) problem.getRootElement().getChild("variables").getChildren()) 
			assertEquals (Arrays.asList(doms.get(varElmt.getAttributeValue("domain"))), Arrays.asList(parserInt.getDomain(varElmt.getAttributeValue("name"))));
		
		// Also check each agent's subproblem
		for (int i = 0; i < graph.clusters.size(); i++) {
			XCSPparser<AddableInteger, AddableInteger> subProb = parserInt.getSubProblem(String.valueOf(i));
			for (String var : subProb.getVariables()) 
				assertEquals (Arrays.asList(parserInt.getDomain(var)), Arrays.asList(subProb.getDomain(var)));
			for (String var : subProb.getVariables(null)) // also check anonymous variables
				assertEquals (Arrays.asList(parserInt.getDomain(var)), Arrays.asList(subProb.getDomain(var)));
		}
	}
	
	/** Tests the setDomain() method */
	public void testSetDomain () {
		
		ArrayList<String> allVars = new ArrayList<String> (this.parserReal.getVariables());
		allVars.addAll(this.parserReal.getVariables(null)); // test also the variables with no specified owner 
		for (String var : allVars) {
			
			// Generate a new random domain, possibly with redundant values
			int domSize = 5;
			AddableInteger[] dom = new AddableInteger [domSize];
			for (int i = 0; i < domSize; i++) 
				dom[i] = new AddableInteger ((int) (Math.random() * domSize)); // values are between 0 and (domSize - 1) included
			
			// Reduce the domain
			TreeSet<AddableInteger> domReducedSet = new TreeSet<AddableInteger> ();
			for (AddableInteger val : dom) 
				domReducedSet.add(val);
			
			// Reorder the domain
			ArrayList<AddableInteger> domReduced = new ArrayList<AddableInteger> (domReducedSet);
			
			// Check that the domain is properly set to the reduced domain 
			this.parserReal.setDomain(var, dom);
			assertEquals (domReduced, Arrays.asList(this.parserReal.getDomain(var)));
			
			// If var is a random variable, check that its probability law has been properly updated
			if (this.parserReal.isRandom(var)) {
				
				List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > probLaws = this.parserReal.getProbabilitySpaces(var);
				UtilitySolutionSpace<AddableInteger, AddableReal> probLaw = probLaws.get(0);

				// Check that each value frequency is correct
				AddableInteger[] dom2 = probLaw.getDomain(0);
				AddableReal nbrSamples = new AddableReal (domSize);
				for (int i = 0; i < dom2.length; i++) {

					AddableReal freq = (AddableReal) probLaw.getUtility(i);
					AddableReal nbrTimes = freq.multiply(nbrSamples);

					// Check that the corresponding value appears exactly nbrTimes in the initial non-reduced domain
					int nbrTimes2 = 0;
					AddableInteger val2 = dom2[i];
					for (AddableInteger val : dom) 
						if (val.equals(val2)) 
							nbrTimes2++;
					
					assertTrue (new AddableReal (nbrTimes2).equals(nbrTimes, 1e-6));
				}
			}
		}
	}
	
	/** Test method for XCSPparser#getSubProblem(String). */
	public void testGetSubProblem() {
		
		List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > allHypercubes = parserReal.getSolutionSpaces(true);
		List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > allProbs = parserReal.getProbabilitySpaces();

		// Test for each agent
		for (int i = 0; i < graph.clusters.size(); i++) {
			String agent = String.valueOf(i);
			
			DCOPProblemInterface< AddableInteger, AddableReal > subproblem = parserReal.getSubProblem(agent);
			
			// Check the subproblem contains all constraints involving the agent's variables
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > spaces = subproblem.getSolutionSpaces(true);
			Collection<String> vars = parserReal.getVariables(agent);
			HashSet<String> randVars = new HashSet<String> ();
			for (UtilitySolutionSpace<AddableInteger, AddableReal> hypercube : allHypercubes) {
				for (String var : hypercube.getVariables()) {
					if (vars.contains(var)) {
						assertTrue (spaces.remove(hypercube));
						
						// Record all random variables mentioned in this constraint
						for (String var2 : hypercube.getVariables()) 
							if (parserReal.isRandom(var2)) 
								randVars.add(var2);
						break;
					}
				}
			}
			
			if (this.extendedRandNeighborhoods) {
				
				// The remaining spaces should be exactly spaces that involve a random variable reachable from one of the agent's variable through a random-variable-only path
				HashSet< UtilitySolutionSpace<AddableInteger, AddableReal> > remainingSpaces = new HashSet< UtilitySolutionSpace<AddableInteger, AddableReal> > ();
				for (String var : vars) {
					HashSet<String> randNeighbors = this.getExtendedRandNeighbors(var);
					randVars.addAll(randNeighbors); // we also need the corresponding probability distributions
					for (UtilitySolutionSpace<AddableInteger, AddableReal> space : allHypercubes) 
						if (! Collections.disjoint(randNeighbors, Arrays.asList(space.getVariables()))
								&& Collections.disjoint(vars, Arrays.asList(space.getVariables()))) // the spaces involving internal variables have already been checked
							remainingSpaces.add(space);
				}
				
				assertEquals (new HashSet< UtilitySolutionSpace<AddableInteger, AddableReal> > (spaces), remainingSpaces);
				
			} else // ! extendedRandNeighborhoods
				assertTrue (spaces.isEmpty());
			
			// Check the subproblem contains all desired probability spaces
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > probas = subproblem.getProbabilitySpaces();
			for (UtilitySolutionSpace<AddableInteger, AddableReal> probSpace : allProbs) {
				for (String var : probSpace.getVariables()) {
					if (randVars.contains(var)) {
						assertTrue (probas.remove(probSpace));
						break;
					}
				}
			}
			assertTrue (probas.isEmpty());
			
			// If required, check that the agent knows all other agents
			if (this.publicAgents) 
				assertEquals (parserReal.getAgents(), subproblem.getAgents());
		}
	}

	/** Test method for frodo2.algorithms.XCSPparser#isRandom(java.lang.String).
	 * @throws JDOMException 	if an error occurred when reading the problem description
	 */
	public void testIsRandom () throws JDOMException {
		
		// For each variable
		for (Element varElmt : (List<Element>) problem.getRootElement().getChild("variables").getChildren()) {
			String isRand = varElmt.getAttributeValue("type");
			assertEquals (isRand != null && isRand.equals("random"), parserInt.isRandom(varElmt.getAttributeValue("name")));
		}
	}
	
	/** Test method for getSolutionSpaces() with random variables */
	public void testGetSolutionSpacesWithRandVars() {
		this.testGetSolutionSpaces(this.parserInt, this.solutionSpaces, null, true);
	}

	/** Test method for getSolutionSpaces() ignoring random variables */
	public void testGetSolutionSpaces() {

		// Remove from the list of solution spaces the ones that involve random variables
		for (java.util.Iterator<Hypercube<AddableInteger, AddableInteger>> iter = this.solutionSpaces.iterator(); iter.hasNext(); ) {
			for (String var : iter.next().getVariables()) {
				if (parserInt.isRandom(var)) {
					iter.remove();
					break;
				}
			}
		}

		this.testGetSolutionSpaces(this.parserInt, this.solutionSpaces, null, false);
	}
	
	/** Test method for getSolutionSpaces(String, boolean) 
	 * @param parser 		the parser
	 * @param refSpaces 	the reference spaces
	 * @param var 			the variable 
	 * @param withRandVars 	if \c true, also considers random variables
	 * @todo Test with default cost
	 */
	protected void testGetSolutionSpaces(XCSPparser<AddableInteger, AddableInteger> parser, 
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > refSpaces, String var, final boolean withRandVars) {
		
		// Parse all the solution spaces involving the variable 
		List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > solutionSpaces2 = parser.getSolutionSpaces(var, withRandVars);
		
		// Check that the list indeed contains all spaces involving this variable and nothing else
		for (UtilitySolutionSpace<AddableInteger, AddableInteger> space : refSpaces) 
			if (var == null || space.getDomain(var) != null) 
				assertTrue (solutionSpaces2.remove(space));
		assertTrue (solutionSpaces2.isEmpty());
	}

	/** Test method for getProbabilitySpaces() */
	public void testGetProbabilitySpaces() {
		
		// Parse all the probability spaces in the problem
		List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > probSpaces2 = parserReal.getProbabilitySpaces();

		// Check that all probability spaces were properly read
		assertEquals (probSpaces.size(), probSpaces2.size());
		probSpaces.removeAll(probSpaces2);
		assertTrue (probSpaces.isEmpty());
	}

	/** Test method for getSolutionSpaces(String) with random variables */
	public void testGetSolutionSpacesForVarWithRandVars() {
		this.testGetSolutionSpacesForVar(true);
	}

	/** Test method for getSolutionSpaces(String) ignoring random variables */
	public void testGetSolutionSpacesForVar() {
		
		// Remove from the list of solution spaces the ones that involve random variables
		for (java.util.Iterator<Hypercube<AddableInteger, AddableInteger>> iter = this.solutionSpaces.iterator(); iter.hasNext(); ) {
			for (String var : iter.next().getVariables()) {
				if (parserInt.isRandom(var)) {
					iter.remove();
					break;
				}
			}
		}

		this.testGetSolutionSpacesForVar(false);
	}
		
	/** Test method for getSolutionSpaces(String, boolean)
	 * @param withRandVars 	if \c true, considers random variables
	 * @todo Test with default cost
	 * @todo Test with and without forbidden vars
	 */
	private void testGetSolutionSpacesForVar(final boolean withRandVars) {
		
		// Test it on each variable in the overall problem
		HashSet<String> allVars = new HashSet<String> (parserInt.getVariables());
		if (withRandVars) 
			allVars.addAll(parserInt.getVariables(null)); // add variables with no specified owner
		for (String var : allVars) 
			this.testGetSolutionSpaces(this.parserInt, new ArrayList< UtilitySolutionSpace<AddableInteger, AddableInteger> > (this.solutionSpaces), var, withRandVars);
		
		// Also test for each agent's subproblem
		for (Integer i = 0; i < graph.clusters.size(); i++) {
			XCSPparser<AddableInteger, AddableInteger> subProb = parserInt.getSubProblem(i.toString());
			
			// Filter out the spaces that don't involve any internal variable
			ArrayList< UtilitySolutionSpace<AddableInteger, AddableInteger> > mySolutionSpaces = new ArrayList< UtilitySolutionSpace<AddableInteger, AddableInteger> > ();
			ext: for (UtilitySolutionSpace<AddableInteger, AddableInteger> space : this.solutionSpaces) {
				for (String var : space.getVariables()) {
					if (i.equals(graph.clusterOf.get(var))) { // internal variable
						mySolutionSpaces.add(space);
						continue ext;
					}
				}
			}

			allVars = new HashSet<String> (subProb.getVariables());
			if (withRandVars) 
				allVars.addAll(subProb.getVariables(null)); // add variables with no specified owner
			for (String var : allVars) 
				this.testGetSolutionSpaces(subProb, new ArrayList< UtilitySolutionSpace<AddableInteger, AddableInteger> > (mySolutionSpaces), var, withRandVars);
		}
	}

	/** Test method for getProbabilitySpaces(String) */
	public void testGetProbabilitySpacesForVar() {
		
		// Test it on each variable of the overall problem
		HashSet<String> allVars = new HashSet<String> (parserReal.getVariables());
		allVars.addAll(parserReal.getVariables(null)); // add variables with no specified owner
		for (String var : allVars) {
			
			// Parse all the probability spaces involving this variable
			List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > probSpaces2 = parserReal.getProbabilitySpaces(var);

			// Check that the list indeed contains all probability spaces involving this variable and nothing else
			for (Hypercube<AddableInteger, AddableReal> space : this.probSpaces) 
				if (space.getDomain(var) != null) 
					assertTrue (probSpaces2.remove(space));
			
			assertTrue (probSpaces2.isEmpty());
		}
		
		// Also check each agent's subproblem
		for (Integer i = 0; i < graph.clusters.size(); i++) {
			XCSPparser<AddableInteger, AddableReal> subProb = parserReal.getSubProblem(i.toString());
			
			allVars = new HashSet<String> (subProb.getVariables());
			allVars.addAll(subProb.getVariables(null)); // add variables with no specified owner
			for (String var : allVars) {
				
				// Parse all the probability spaces involving this variable
				List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > probSpaces2 = subProb.getProbabilitySpaces(var);

				// Check that the list indeed contains all probability spaces involving this variable and an internal variable, and nothing else
				for (Hypercube<AddableInteger, AddableReal> space : this.probSpaces) 
					if (space.getDomain(var) != null) 
						assertTrue (probSpaces2.remove(space));
				
				assertTrue (probSpaces2.isEmpty());
			}
		}
	}

	/** Test for the method getUtility() on complete assignments */
	public void testGetUtility () {
		
		// Choose a random assignment to all variables
		int nbrVars = parserInt.getNbrVars();
		String[] vars = parserInt.getVariables().toArray(new String [nbrVars]);
		AddableInteger[] vals = new AddableInteger [nbrVars];
		HashMap<String, AddableInteger> assignments = new HashMap<String, AddableInteger> ();
		for (int i = 0; i < nbrVars; i++) {
			String var = vars[i];
			AddableInteger[] domain = parserInt.getDomain(var);
			AddableInteger val = domain[ (int) (domain.length * Math.random()) ];
			vals[i] = val;
			assignments.put(var, val);
		}
		
		AddableInteger util = parserInt.getUtility(assignments).getUtility(0);
		
		// Compute the true utility
		AddableInteger trueUtil = new AddableInteger (0);
		ext: for (UtilitySolutionSpace<AddableInteger, AddableInteger> space : this.solutionSpaces) {
			
			// Skip this space if it contains a random variable
			for (String var : space.getVariables()) 
				if (! graph.nodes.contains(var)) 
					continue ext;
			
			trueUtil = trueUtil.add(space.getUtility(vars, vals));
		}
		
		assertEquals (trueUtil, util);
	}
	
}
