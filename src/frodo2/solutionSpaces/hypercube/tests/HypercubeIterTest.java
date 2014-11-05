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

import java.util.ArrayList;
import java.util.Arrays;

import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.hypercube.Hypercube;

import junit.extensions.RepeatedTest;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit tests for the BasicHypercube iterator
 * @author Thomas Leaute
 */
public class HypercubeIterTest extends TestCase {

	/** @return a TestSuite */
	public static Test suite() {
		TestSuite testSuite = new TestSuite ("Tests for the BasicHypercubeIter class");
		
		TestSuite suiteTmp = new TestSuite ("Tests for the case when the variables of iteration are the hypercube's variable, in the same order");
		suiteTmp.addTest(new RepeatedTest (new HypercubeIterTest ("testSameOrder"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the case when the variables of iteration are the hypercube's variable, in a different order");
		suiteTmp.addTest(new RepeatedTest (new HypercubeIterTest ("testOtherOrder"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the case when the variables of iteration include variables not in the space");
		suiteTmp.addTest(new RepeatedTest (new HypercubeIterTest ("testMoreVars"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the case when the input domains are sub-domains of the space's");
		suiteTmp.addTest(new RepeatedTest (new HypercubeIterTest ("testSubDoms"), 1000));
		testSuite.addTest(suiteTmp);
		
		return testSuite;
	}
	
	/** Constructor
	 * @param name 	name of the test method
	 */
	public HypercubeIterTest(String name) {
		super(name);
	}

	/** Tests the case when the variables of iteration are the hypercube's variable, in the same order */
	public void testSameOrder () {
		
		Hypercube<AddableInteger, AddableInteger> space = HypercubeTest.random_hypercube();
		Iterator<AddableInteger, AddableInteger> iter = space.iterator();
		
		AddableInteger utility;
		AddableInteger[] solution;
		
		int i = 0;
		for ( ; iter.hasNext(); i++) {
			
			if (Math.random() > 0.5) {
				utility = iter.nextUtility();
				solution = iter.getCurrentSolution();
			} else {
				solution = iter.nextSolution();
				utility = iter.getCurrentUtility();
			}
			
			assertTrue (space.getUtility(solution).equals(utility));
			assertTrue (space.getUtility(i).equals(utility));
		}
		
		assertTrue (i == space.getNumberOfSolutions());
	}
	
	/** Tests the case when the variables of iteration are the hypercube's variable, in a different order */
	public void testOtherOrder () {
		
		Hypercube<AddableInteger, AddableInteger> space = HypercubeTest.random_hypercube();
		
		// Choose a random new order for the variables
		int nbrVars = space.getNumberOfVariables();
		String[] variables = new String [nbrVars];
		AddableInteger[][] domains = new AddableInteger [nbrVars][];
		ArrayList<String> pool = new ArrayList<String> (Arrays.asList(space.getVariables()));
		for (int i = 0; i < nbrVars; i++) {
			variables[i] = pool.remove((int) (Math.random() * pool.size()));
			domains[i] = space.getDomain(variables[i]);
		}
		Hypercube<AddableInteger, AddableInteger> space2 = space.changeVariablesOrder(variables);
		
		Iterator<AddableInteger, AddableInteger> iter = space.iterator(variables, domains);
		Iterator<AddableInteger, AddableInteger> iter2 = space.iterator(variables);
		
		AddableInteger utility, utility2;
		AddableInteger[] solution, solution2;
		
		int i = 0;
		for ( ; iter.hasNext(); i++) {
			
			if (Math.random() > 0.5) {
				utility = iter.nextUtility();
				solution = iter.getCurrentSolution();
				utility2 = iter2.nextUtility();
				solution2 = iter2.getCurrentSolution();
			} else {
				solution = iter.nextSolution();
				utility = iter.getCurrentUtility();
				solution2 = iter2.nextSolution();
				utility2 = iter2.getCurrentUtility();
			}
			
			assertTrue (space2.getUtility(solution).equals(utility));
			assertTrue (space2.getUtility(i).equals(utility));
			assertTrue (space2.getUtility(solution2).equals(utility2));
			assertTrue (space2.getUtility(i).equals(utility2));
		}
		
		assertTrue (i == space2.getNumberOfSolutions());
	}
	
	/** Tests the case when the variables of iteration include variables not in the space */
	public void testMoreVars ()	 {
		
		Hypercube<AddableInteger, AddableInteger> space = HypercubeTest.random_hypercube();
		
		// Choose additional variables (at least one)
		ArrayList<String> pool = new ArrayList<String> ();
		for (int i = 100; i < 105; i++) 
			if (Math.random() > 0.5) 
				pool.add("X" + i);
		if (pool.isEmpty()) 
			pool.add("X" + 100);
		
		// Generate an augmented hypercube including the new variables
		Hypercube<AddableInteger, AddableInteger> bigSpace = space;
		AddableInteger[] newDom = new AddableInteger[] { new AddableInteger(0), new AddableInteger(1), new AddableInteger(2) };
		for (String var : pool) 
			bigSpace = (Hypercube<AddableInteger, AddableInteger>) bigSpace.applyAugment(new String[] { var }, new AddableInteger[][] { newDom });
		
		// Choose an order for the augmented list of variables
		for (String var : space.getVariables()) 
			pool.add(var);
		int nbrVars = pool.size();
		String[] variables = new String [nbrVars];
		AddableInteger[][] domains = new AddableInteger [nbrVars][];
		for (int i = 0; i < nbrVars; i++) {
			variables[i] = pool.remove((int) (Math.random() * pool.size()));
			domains[i] = space.getDomain(variables[i]);
			if (domains[i] == null) 
				domains[i] = newDom;
		}
		bigSpace = bigSpace.changeVariablesOrder(variables);
		
		Iterator<AddableInteger, AddableInteger> iter = space.iterator(variables, domains);
		
		AddableInteger utility;
		AddableInteger[] solution;
		
		int i = 0;
		for ( ; iter.hasNext(); i++) {
			
			if (Math.random() > 0.5) {
				utility = iter.nextUtility();
				solution = iter.getCurrentSolution();
			} else {
				solution = iter.nextSolution();
				utility = iter.getCurrentUtility();
			}
			
			assertTrue (bigSpace.getUtility(solution).equals(utility));
			assertTrue (bigSpace.getUtility(i).equals(utility));
		}
		
		assertTrue (i == bigSpace.getNumberOfSolutions());
	}
	
	/** Tests the case when the input domains are sub-domains of the space's */
	public void testSubDoms () {
		
		Hypercube<AddableInteger, AddableInteger> space = HypercubeTest.random_hypercube();
		
		// Generate sub-domains for the variables
		int nbrVars = space.getNumberOfVariables();
		AddableInteger[][] subDoms = new AddableInteger [nbrVars][];
		for (int i = 0; i < nbrVars; i++) {
			AddableInteger[] dom = space.getDomain(i);
			
			ArrayList<AddableInteger> newDom = new ArrayList<AddableInteger> ();
			for (AddableInteger val : dom) 
				if (Math.random() < .5) 
					newDom.add(val);
			
			if (newDom.isEmpty()) 
				newDom.add(dom[ (int) (Math.random() * dom.length) ]);
			
			subDoms[i] = newDom.toArray(new AddableInteger [newDom.size()]);
		}
		
		// Compute the slice
		Hypercube<AddableInteger, AddableInteger> slice = space.slice(space.getVariables(), subDoms);
		
		Iterator<AddableInteger, AddableInteger> iter = space.iterator(space.getVariables(), subDoms);
		
		AddableInteger utility;
		AddableInteger[] solution;
		
		int i = 0;
		for ( ; iter.hasNext(); i++) {
			
			if (Math.random() > 0.5) {
				utility = iter.nextUtility();
				solution = iter.getCurrentSolution();
			} else {
				solution = iter.nextSolution();
				utility = iter.getCurrentUtility();
			}
			
			assertTrue (slice.getUtility(space.getVariables(), solution).equals(utility));
			assertTrue (slice.getUtility(i).equals(utility));
		}
		
		assertTrue (i == slice.getNumberOfSolutions());
	}
}
