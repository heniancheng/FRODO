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

package frodo2.algorithms.dpop.count.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jdom2.Document;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.count.SolutionCounter;
import frodo2.algorithms.test.AllTests;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author brammert
 *
 */
public class TestCountSolutions extends TestCase {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 8;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 8;
	
	/** The constraint graph*/
	private Graph graph;
	
	/** A randomly generated problem*/
	private Document problem;
	
	/** Whether to maximize or minimize */
	private final boolean maximize;
	
	/** Constructor
	 * @param maximize 	Whether to maximize or minimize
	 */
	public TestCountSolutions(boolean maximize) {
		super("testRandom");
		this.maximize = maximize;
	}

	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problem = AllTests.generateProblem(graph, this.maximize);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown() throws Exception {
		super.tearDown();
		this.graph = null;
		this.problem = null;
	}
	
	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for CountAgent");
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes on maximization problems");
		tmp.addTest(new RepeatedTest (new TestCountSolutions(true), 1000));
		suite.addTest(tmp);
	
		tmp = new TestSuite ("Tests using QueueIOPipes on minimization problems");
		tmp.addTest(new RepeatedTest (new TestCountSolutions(false), 1000));
		suite.addTest(tmp);
	
		return suite;
	}
	
	
	
	/**
	 * Test the counter on a random problem
	 */
	public void testRandom() {
		
		SolutionCounter<AddableInteger, AddableInteger> counter = new SolutionCounter<AddableInteger, AddableInteger>();
		DPOPsolver<AddableInteger, AddableInteger> solver = new DPOPsolver<AddableInteger, AddableInteger>();
		
		AddableInteger optimalUtil = solver.solve(problem).getUtility();
		
		// Join all spaces together
		XCSPparser<AddableInteger, AddableInteger> parser = new XCSPparser<AddableInteger, AddableInteger>(problem);
		List<? extends UtilitySolutionSpace<AddableInteger, AddableInteger>> spaces = parser.getSolutionSpaces();
		final AddableInteger infeasibleUtil = (this.maximize ? parser.getMinInfUtility() : parser.getPlusInfUtility());
		UtilitySolutionSpace<AddableInteger, AddableInteger> global = new ScalarHypercube<AddableInteger, AddableInteger> (new AddableInteger(0), infeasibleUtil, new AddableInteger [0].getClass());
		for(UtilitySolutionSpace<AddableInteger, AddableInteger> space : spaces) 
			global = global.join(space);
		
		int count = 0;
		if(optimalUtil != infeasibleUtil) {
			Iterator<AddableInteger, AddableInteger> it = global.iterator();
			while(it.hasNext()) {
				if(optimalUtil.equals(it.nextUtility()))
					count++;
			}
			
			// Take into account the fact, for each unconstrained variable, all its values are optimal
			ArrayList<String> unconstrainedVars = new ArrayList<String> (parser.getVariables());
			unconstrainedVars.removeAll(Arrays.asList(global.getVariables()));
			for (String var : unconstrainedVars) 
				count *= parser.getDomainSize(var);
		}
		
		int numberOfSolutions = counter.count(problem);
		
		assertEquals(count, numberOfSolutions);
	}

}
