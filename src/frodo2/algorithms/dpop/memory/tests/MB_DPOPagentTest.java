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

/** Tests for MB-DPOP */
package frodo2.algorithms.dpop.memory.tests;

import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.memory.LabelingPhase;
import frodo2.algorithms.test.AllTests;
import frodo2.solutionSpaces.AddableInteger;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** An overall unit test for the MB-DPOP algorithm
 * @author Thomas Leaute
 */
public class MB_DPOPagentTest extends TestCase {

	/** @return the suite of tests */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("Tests for the MB-DPOP algorithm");
		
		TestSuite tmp = new TestSuite ("Maximization problems");
		tmp.addTest(new RepeatedTest (new MB_DPOPagentTest(true, false), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Minimization problems");
		tmp.addTest(new RepeatedTest (new MB_DPOPagentTest(false, false), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Maximization problems with TCP pipes");
		tmp.addTest(new RepeatedTest (new MB_DPOPagentTest(false, true), 2000));
		suite.addTest(tmp);
		
		return suite;
	}

	/** Maximum number of variables in the problem */
	private final int maxNbrVars = 10;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 45;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;
	
	/** The input random problem */
	private Document problem;
	
	/** Whether to maximize or minimize */
	private final boolean maximize;
	
	/** Whether to use TCP pipes */
	private final boolean useTCP;
	
	/** Constructor 
	 * @param maximize 	Whether to maximize or minimize
	 * @param useTCP 	Whether to use TCP pipes
	 */
	public MB_DPOPagentTest (boolean maximize, boolean useTCP) {
		super ("test");
		this.maximize = maximize;
		this.useTCP = useTCP;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	@Override
	protected void setUp() throws Exception {
		this.problem = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, this.maximize);
	}

	/** @see junit.framework.TestCase#tearDown() */
	@Override
	protected void tearDown() throws Exception {
		this.problem = null;
	}

	/** The test method 
	 * @throws Exception if an error occurs
	 */
	public void test () throws Exception {
		
		// Set the maxDim
		int maxDim = (int) (Math.random() * this.maxNbrVars) + 1;
		Document agentDoc = XCSPparser.parse("src/frodo2/algorithms/dpop/memory/MB-DPOPagent.xml", false);
		for (Element module : (List<Element>) agentDoc.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").equals(LabelingPhase.class.getName())) {
				module.setAttribute("maxDim", Integer.toString(maxDim));
				break;
			}
		}
		
		// Solve using MB-DPOP
		DPOPsolver<AddableInteger, AddableInteger> solver = new DPOPsolver<AddableInteger, AddableInteger> (agentDoc, this.useTCP);
		Solution<AddableInteger, AddableInteger> sol = solver.solve(this.problem, 60000L);
		assertFalse ("MB-DPOP timed out", sol == null);
		
		// Check that the maxDim was respected
		assertTrue (maxDim + " < " + sol.getTreeWidth(), maxDim >= sol.getTreeWidth());
		
		// Check that the solution found is optimal
		assertEquals (new DPOPsolver<AddableInteger, AddableInteger> ().solve(problem).getUtility(), sol.getUtility());
	}
}
