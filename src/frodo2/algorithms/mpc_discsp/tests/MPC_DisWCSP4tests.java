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

/** Tests for the MPC-Dis(W)CSP algorithms */
package frodo2.algorithms.mpc_discsp.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.mpc_discsp.MPC_DisWCSP4solver;
import frodo2.algorithms.test.AllTests;
import frodo2.solutionSpaces.AddableInteger;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit tests for the MPC-Dis(W)CSP4 algorithms
 * @author Thomas Leaute
 */
public class MPC_DisWCSP4tests extends TestCase {
	
	/** The costs in each constraints take values in [0, costAmplitude], allowing infinity */
	private final int costAmplitude;
	
	/** @return a suite of tests with randomized input problem instances */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("All tests for MPC-Dis[W]CSP4");
		
		TestSuite tmp = new TestSuite ("Tests for MPC-DisCSP4");
		tmp.addTest(new RepeatedTest (new MPC_DisWCSP4tests (false), 200));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for MPC-DisWCSP4");
		tmp.addTest(new RepeatedTest (new MPC_DisWCSP4tests (true), 200));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** A random DCOP instance */
	private Document problem;
	
	/** The agent configuration file */
	private Document agentConfig;

	/** Constructor 
	 * @param w 	if true, use MPC-DisWCSP4
	 */
	public MPC_DisWCSP4tests(boolean w) {
		super ("test");
		this.costAmplitude = (w ? 5 : 0);
		try {
			this.agentConfig = XCSPparser.parse("src/frodo2/algorithms/mpc_discsp/MPC-Dis" + (w ? "W" : "") + "CSP4.xml", false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** @see junit.framework.TestCase#setUp() */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		this.problem = AllTests.createRandProblem(3, 3, 3, false, +1, costAmplitude);
	}

	/** @see junit.framework.TestCase#tearDown() */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		this.problem = null;
	}
	
	/** The test method */
	public void test () {
		
		// Solve the problem using DPOP
		AddableInteger optCost = new DPOPsolver<AddableInteger, AddableInteger> ().solve(problem).getUtility();
		
		// Construct the list of possible constraint owners
		XCSPparser<AddableInteger, AddableInteger> parser = new XCSPparser<AddableInteger, AddableInteger> (this.problem);
		ArrayList<String> owners = new ArrayList<String> (parser.getAgents()); // all agents owning at least one variable
		owners.add(null); // no specified owner
		owners.add("PUBLIC"); // public constraint
		Random rnd = new Random();
		String randOwner = Integer.toHexString(rnd.nextInt()); // an agent owning no variable
		Element agentsElmt = this.problem.getRootElement().getChild("agents");
		Element elmt = new Element ("agent");
		agentsElmt.addContent(elmt);
		elmt.setAttribute("name", randOwner);
		agentsElmt.setAttribute("nbAgents", Integer.toString(agentsElmt.getContentSize()));
		owners.add(randOwner);
		final int nbrOwners = owners.size();
		
		// Add random owners to constraints
		for (Element constElmt : (List<Element>) this.problem.getRootElement().getChild("constraints").getChildren()) {
			String owner = owners.get(rnd.nextInt(nbrOwners));
			if (owner != null) 
				constElmt.setAttribute("agent", owner);
		}
		
		// Solve the problem using MPC-DisWCSP4
		final int nbrConstraints = parser.getSolutionSpaces().size();
		Solution<AddableInteger, AddableInteger> sol = new MPC_DisWCSP4solver<AddableInteger, AddableInteger> (this.agentConfig)
			.solve(problem, false, 600000L, costAmplitude * nbrConstraints, costAmplitude * nbrConstraints * parser.getAgents().size());
		assertNotNull("timeout", sol);
		
		assertEquals(optCost, sol.getUtility());
	}

}
