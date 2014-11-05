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

/** Tests for the Max-Sum algorithm */
package frodo2.algorithms.maxsum.tests;

import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.count.SolutionCounter;
import frodo2.algorithms.maxsum.MaxSum;
import frodo2.algorithms.maxsum.MaxSumSolver;
import frodo2.algorithms.test.AllTests;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Unit tests for the Max-Sum algorithm
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class MaxSumTests< V extends Addable<V>, U extends Addable<U> > extends TestCase {
	
	/** Maximum number of variables in the test problems */
	private static final int maxNbrVars = 20;
	
	/** Maximum number of edges in the test constraint graphs */
	private static final int maxNbrEdges = 190;
	
	/** Maximum number of agents in the test problems */
	private static final int maxNbrAgents = 20;
	
	/** @return the suite of unit tests */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("Tests for Max-Sum");
		
		TestSuite tmp = new TestSuite ("Pure acyclic maximization problems");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (true, AddableInteger.class, 0.0, false, true, true), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Pure acyclic minimization problems");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (false, AddableInteger.class, 0.0, false, true, true), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Pure acyclic minimization problems with zero initialization");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (false, AddableInteger.class, 0.0, false, true, false), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Pure maximization problems");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (true, AddableInteger.class, 0.0, false, false, true), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Pure minimization problems");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (false, AddableInteger.class, 0.0, false, false, true), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Pure minimization problems with real-valued costs");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableReal> (false, AddableReal.class, 0.0, false, false, true), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Pure minimization problems with real-valued costs and zero initialization");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableReal> (false, AddableReal.class, 0.0, false, false, false), 100));
		suite.addTest(tmp);
		
		/// @bug Max-Sum may not terminate when using TCP pipes because there is not idleness detection
//		tmp = new TestSuite ("Pure minimization problems with TCP pipes");
//		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (false, AddableInteger.class, 0.0, true, false, true), 100));
//		suite.addTest(tmp);
		
		tmp = new TestSuite ("Maximization problems");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (true, AddableInteger.class, AllTests.DEFAULT_P2, false, false, true), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Minimization problems");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableInteger> (false, AddableInteger.class, AllTests.DEFAULT_P2, false, false, true), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Minimization problems with real-valued costs");
		tmp.addTest(new RepeatedTest (new MaxSumTests<AddableInteger, AddableReal> (false, AddableReal.class, AllTests.DEFAULT_P2, false, false, true), 100));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** Whether to maximize utility or minimize cost */
	private boolean maximize;
	
	/** The class of U */
	private Class<U> classOfU;
	
	/** The constraint tightness */
	private final double p2;
	
	/** Whether to use TCP pipes */
	private final boolean useTCP;
	
	/** Whether the graph should be acyclic */
	private final boolean acyclic;
	
	/** Whether to initialize the algorithm with random messages */
	private final boolean randomInit;
	
	/** Constructor
	 * @param maximize 		Whether to maximize utility or minimize cost
	 * @param classOfU 		The class of U
	 * @param p2 			The constraint tightness
	 * @param useTCP 		Whether to use TCP pipes
	 * @param acyclic 		Whether the graph should be acyclic
	 * @param randomInit 	Whether to initialize the algorithm with random messages
	 */
	public MaxSumTests (boolean maximize, Class<U> classOfU, double p2, boolean useTCP, boolean acyclic, boolean randomInit) {
		super ("test");
		this.maximize = maximize;
		this.classOfU = classOfU;
		this.p2 = p2;
		this.useTCP = useTCP;
		this.acyclic = acyclic;
		this.randomInit = randomInit;
	}
	
	/** The test method 
	 * @throws Exception if an error occurs
	 */
	public void test () throws Exception {
		
		// Construct a random problem instance
		Document problem;
		if (acyclic) {
			Graph graph = RandGraphFactory.getAcyclicGraph(10, 3);
			problem = AllTests.generateProblem(graph, maximize, 0, false, p2);
		} else 
			problem = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, this.maximize, this.p2);
		
		// Get the agent configuration file
		Document agentConfig = XCSPparser.parse("src/frodo2/algorithms/maxsum/MaxSumAgent.xml", false);
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
			String moduleClass = module.getAttributeValue("className");
			
			if (moduleClass.equals(MaxSum.class.getName())) {
				if (acyclic) 
					module.setAttribute("maxNbrIter", "50000");
				module.setAttribute("randomInit", Boolean.toString(this.randomInit));
				
			}
		}
		
		// Instantiate the solver
		MaxSumSolver<V, U> solver = new MaxSumSolver<V, U> (agentConfig, this.useTCP);
		solver.setUtilClass(this.classOfU);
		
		// Solve the problem
		Solution<V, U> sol = solver.solve(problem);
		assertTrue ("Max-Sum timed out", sol != null);
		
		// Check that the declared utility corresponds to the solution found
		XCSPparser<V, U> parser = new XCSPparser<V, U > (problem);
		parser.setUtilClass(this.classOfU);
		assertEquals (parser.getUtility(sol.getAssignments()).getUtility(0), sol.getUtility());
		
		// Skip the completeness test if the problem is not acyclic, or has more than one optimal solutions
		if (this.acyclic && new SolutionCounter<AddableInteger, AddableInteger>().count(problem) <= 1) 
			assertEquals(new DPOPsolver<AddableInteger, AddableInteger>().solve(problem).getUtility(), sol.getUtility());
	}

}
