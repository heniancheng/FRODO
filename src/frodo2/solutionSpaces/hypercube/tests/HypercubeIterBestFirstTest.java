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

package frodo2.solutionSpaces.hypercube.tests;

import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.hypercube.HyperCubeIterBestFirst;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.tests.HypercubeTest.Infinity;
import junit.extensions.RepeatedTest;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Brammert Ottens, 23 nov 2009
 * 
 */
public class HypercubeIterBestFirstTest extends TestCase {

	/**
	 * Constructor
	 * @param name name of the test method
	 */
	public HypercubeIterBestFirstTest(String name) {
		super(name);
	}
	
	/** @return a TestSuite */
	public static Test suite() {
		TestSuite testSuite = new TestSuite ("Tests for the HypercubeIterBestFirst class");
		
		TestSuite suiteTmp = new TestSuite ("Tests for the case when the constraint is part of a maximization problem");
		suiteTmp.addTest(new RepeatedTest (new HypercubeIterBestFirstTest ("testMaximize"), 1000));
		testSuite.addTest(suiteTmp);

		suiteTmp = new TestSuite ("Tests for the case when the constraint is part of a minimization problem");
		suiteTmp.addTest(new RepeatedTest (new HypercubeIterBestFirstTest ("testMinimize"), 1000));
		testSuite.addTest(suiteTmp);

		return testSuite;
	}

	/**
	 * Test the best first order when maximizing
	 * @author Brammert Ottens, 23 nov 2009
	 */
	public void testMaximize() {
		HypercubeTest.inf = Infinity.MIN_INFINITY;
		Hypercube<AddableInteger, AddableInteger> space = HypercubeTest.random_hypercube();
		
		HyperCubeIterBestFirst<AddableInteger, AddableInteger> iter = new HyperCubeIterBestFirst<AddableInteger, AddableInteger>(space, true);
		AddableInteger lastUtil = AddableInteger.PlusInfinity.PLUS_INF;
		
		while(iter.hasNext()) {
			AddableInteger[] sol = iter.nextSolution();
			AddableInteger util = iter.getCurrentUtility();
			AddableInteger actualUtility = space.getUtility(sol);

			assertTrue(util.equals(actualUtility));                 // the returned solution and utility are correct
			assertTrue(lastUtil.compareTo(util) >= 0);              // the solutions are returned in best first order
			assertTrue(util != AddableInteger.MinInfinity.MIN_INF); // infeasible solutions should be ignored
			lastUtil = util;
		}
	}
	
	/**
	 * Test the best first order when minimizing
	 * @author Brammert Ottens, 23 nov 2009
	 */
	public void testMinimize() {
		HypercubeTest.inf = Infinity.PLUS_INFINITY;
		Hypercube<AddableInteger, AddableInteger> space = HypercubeTest.random_hypercube();
		
		HyperCubeIterBestFirst<AddableInteger, AddableInteger> iter = new HyperCubeIterBestFirst<AddableInteger, AddableInteger>(space, false);
		AddableInteger lastUtil = AddableInteger.MinInfinity.MIN_INF;
		
		while(iter.hasNext()) {
			AddableInteger[] sol = iter.nextSolution();
			AddableInteger util = iter.getCurrentUtility();
			AddableInteger actualUtility = space.getUtility(sol);

			assertTrue(util.equals(actualUtility));                   // the returned solution and utility are correct
			assertTrue(lastUtil.compareTo(util) <= 0);                // the solutions are returned in best first order
			assertTrue(util != AddableInteger.PlusInfinity.PLUS_INF); // infeasible solutions should be ignored
			lastUtil = util;
		}
	}

}
