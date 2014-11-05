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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

import junit.extensions.RepeatedTest;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** A class used to test the Hypercube class
 *
 * @author Ouaret Nacereddine, Thomas Leaute, Radoslaw Szymanek
 * @todo change every test in the following manner. If it fails, log the arguments and information
 * what has failed so the test can be repeated.
 */
public class HypercubeTest extends TestCase {
	
	/**
	 * Variables are used to determine whether infinite values are allowed
	 */
	public enum Infinity {/**
	 * No infinity is used
	 */
	NONE, /**
	 * plus infinity is used
	 */
	PLUS_INFINITY, /**
	 * min infinity is used
	 */
	MIN_INFINITY};
	
	/**
	 * This variable is used to make sure that one does not mix plus infinity and
	 * min infinity in the testing
	 */
	public static Infinity inf = Infinity.NONE;
	
	/**
	 * This variable is used to choose between maximising and minimising during the tests
	 */
	public boolean maximize;
	
	/** Contructor that returns a TestCase consisting of a single test method
	 * @param name the name of the method to be used
	 */
	public HypercubeTest(String name) {
		super(name);
	}

	/**
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp() {
	
		// Randomly choose whether we should maximize or minimize
		maximize = Math.random() > 0.5;
		
		if(Math.random() < 0.2) {
			if(maximize) {
				inf = Infinity.NONE;
			} else {
				inf = Infinity.PLUS_INFINITY;
			}
		} else if (Math.random() < 0.2) {
			if(!maximize) {
				inf = Infinity.NONE;
			} else {
				inf = Infinity.MIN_INFINITY;
			}
		} else {
			inf = Infinity.NONE;
		}
	}
	
	
	/** Builds up a test suite
	 * @author Thomas Leaute
	 * @return a TestSuite in which randomized test methods are run multiple times, and others only once 
	 */
	public static Test suite() {
		TestSuite testSuite = new TestSuite ("Tests for the Hypercube class");
		
		TestSuite suiteTmp = new TestSuite ("Tests for the method testSaveAsXMLRandom");
		
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSaveAsXMLRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests that a hypercube is always equal to its clone");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testEqualClonesRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests that a hypercube is equivalent to itself with reordered variables");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testEquivalentRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testJoinRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testJoinRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testApplyJoinRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testApplyJoinRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testProjectionRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testProjectionRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testBlindProjectRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testBlindProjectRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testApplyProjectRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testApplyProjectRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testProjOutputRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testProjOutputRandom"), 50000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testProjOutputRandom2");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testProjOutputRandom2"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testSliceRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSliceRandom"), 5000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testApplySliceRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testApplySliceRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testSplittingRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSplittingRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testChangeVariablesOrderRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testChangeVariablesOrderRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testApplyChangeVariablesOrderRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testApplyChangeVariablesOrderRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testGetUtility");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testGetUtility"), 1000));
		testSuite.addTest(suiteTmp);
		
		testSuite.addTest(new HypercubeTest ("testProjection"));

		suiteTmp = new TestSuite ("Tests for the method testSerializationRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSerializationRandom"), 20));
		testSuite.addTest(suiteTmp);
		
		testSuite.addTest(new HypercubeTest ("testSerializationNullHypercube"));
		
		testSuite.addTest(new HypercubeTest ("testUnion"));

		suiteTmp = new TestSuite ("Tests for the method testProjectAllRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testProjectAllRandom"), 20));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testSmartJoinRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSmartJoinRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testSmartMultiplyRandom");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSmartMultiplyRandom"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method testExpectation");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testExpectation"), 2000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method compose");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testCompose"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method sample");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testSample"), 100));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method consensus with weighted samples");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testConsensusWeighted"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method consensusExpect with weighted samples");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testConsensusWeightedExpect"), 1000));
		testSuite.addTest(suiteTmp);
		
		suiteTmp = new TestSuite ("Tests for the method iterator that takes in a list of variables");
		suiteTmp.addTest(new RepeatedTest (new HypercubeTest ("testIterator"), 1000));
		testSuite.addTest(suiteTmp);
		
		return testSuite;
	}

	/** Tests the saving to XML functionality */
	public void testSaveAsXMLRandom() {
		
		Hypercube< AddableInteger, AddableInteger > h1 = random_hypercube();
		
		h1.saveAsXML("test_save.xml");
		Hypercube< AddableInteger, AddableInteger > h2 = new Hypercube<AddableInteger, AddableInteger>("test_save.xml");
		
		assertTrue(h2.equals(h1));
	}
	
	/** Tests that a hypercube is equal to a clone of itself */
	public void testEqualClonesRandom() {
		Hypercube< AddableInteger, AddableInteger > h1 = random_hypercube();
		Hypercube< AddableInteger, AddableInteger > h2 = h1.clone();
		assertTrue (h1.equals(h2));
	}
	
	/** Tests that a hypercube is equivalent to itself with reordered variables */
	public void testEquivalentRandom() {

		Hypercube< AddableInteger, AddableInteger > h1 = random_hypercube();

		// Choose a random new order for the variables in h1
		ArrayList<String> newOrder = new ArrayList<String> (Arrays.asList(h1.getVariables()));
		String[] newOrder2 = new String[newOrder.size()];
		for (int i = 0; i < newOrder2.length; i++) 
			newOrder2[i] = newOrder.remove((int)(newOrder.size() * Math.random()));
		
		assertTrue (h1.equivalent(h1.changeVariablesOrder(newOrder2)));
	}

	/**This method creates random number of hypercubes and then first join the hypercubes with one call of the join method.
	 * then join the hypercube two by two and compare the two results.
	 */
	@SuppressWarnings("unchecked")
	public void testJoinRandom() {
		
		String[] total_variables = new String[12];
		for(int i=0;i<12;i++)
			total_variables[i] = "X"+i;
			
		int number_of_hypercube = 1 + (int) ( Math.random() * 5 );
		
		
		Hypercube< AddableInteger, AddableInteger >[] hypercubes = new Hypercube[number_of_hypercube];
		Hypercube< AddableInteger, AddableInteger > h1 = random_hypercube();
		
		for(int i=0;i<number_of_hypercube;i++){
			hypercubes[i] = random_hypercube();
		}
		
		UtilitySolutionSpace<AddableInteger, AddableInteger> h2 = h1.join(hypercubes);
		//----------------------------------------------------------------------//
		//join the hypercubes 2 by 2
		
		UtilitySolutionSpace<AddableInteger, AddableInteger> h3 = h1;
		for(int i=0;i<number_of_hypercube;i++){
			h3 = h3.join(hypercubes[i], total_variables);
		}
		
		assertTrue(h2.equals(h3));
		
		//join using the second implementation of the join operation
		h3 = h1;
		for(int i=0;i<number_of_hypercube;i++){
			h3 = (Hypercube<AddableInteger, AddableInteger>) h3.join(hypercubes[i], total_variables);
		}
		
		assertTrue(h2.equals(h3));
	}
	
	/** This method creates two random hypercubes change the variables order of the second hypercube so that
	 *  the variables order of the two hypercubes is not necessarily consistent, then joins these two hypercubes
	 *  by using join and applyJoin methods, and compares the two outputs
	 */
	public void testApplyJoinRandom() {
		
		Hypercube<AddableInteger, AddableInteger> h1 = random_hypercube();
		Hypercube<AddableInteger, AddableInteger> h2 = random_hypercube();
		
		ArrayList<String> tmp = new ArrayList<String>();
		String[] variables = h2.getVariables();
		
		int length = variables.length;
		int index;
		while(tmp.size()<length){
			index = (int)(Math.random()*length);
			if(!tmp.contains(variables[index]))
				tmp.add(variables[index]);
		}
		String[] variables2 = new String[length];
		System.arraycopy(tmp.toArray(), 0, variables2, 0, length);
		
		h2 = (Hypercube<AddableInteger, AddableInteger>)h2.changeVariablesOrder(variables2);
		
		Hypercube<AddableInteger, AddableInteger> h3 = (Hypercube<AddableInteger, AddableInteger>) h1.clone().join(h2.clone());
		Hypercube<AddableInteger, AddableInteger> h4 = (Hypercube<AddableInteger, AddableInteger>) h1.applyJoin(h2);
		
		assertTrue(h3.equivalent(h4));
	}
	
	/**This method creates two random hypercubes and an array of random variables obtained from the variables of each hypercube.
	 * then it projects from the two hypercubes the two random arrays of variables and joins the resulting hypercubes. 
	 * It compares the result obtained with the one obtained if the join operation is done first. 
	 */
	public void testProjectionRandom() {
		
		String[] total_variables = new String[12];
		for(int i=0;i<12;i++)
			total_variables[i] = "X"+i;
		
		Hypercube<AddableInteger, AddableInteger> h1, h2;
		String[] variables1, variables2;
		
		// Loop until we get a proper random test case
		while (true) {

			h1 = random_hypercube();
			//h1.saveAsXML("save00.xml");
			h2 = random_hypercube();
			//h2.saveAsXML("save01.xml");

			String[] variables3 = Hypercube.sub( h1.getVariables(), h2.getVariables() );
			String[] variables4 = Hypercube.sub( h2.getVariables(), h1.getVariables() );

			//projected out variables must not be shared between the two hypercubes
			if( (variables3.length == 0) || (variables4.length == 0) )
				continue;

			variables1 = sub_variables( variables3 );
			variables2 = sub_variables( variables4 );

			if( (variables3.length != variables1.length) && (variables4.length != variables2.length) )
				break;
		}
		
		String[] variables = union(variables1, variables2, total_variables);
		
		//project then join the two hypercubes
		UtilitySolutionSpace<AddableInteger, AddableInteger> h3 = ((Hypercube<AddableInteger, AddableInteger>)h2.project( variables2, maximize ).getSpace()).join( (Hypercube<AddableInteger, AddableInteger>)h1.project( variables1, maximize ).getSpace(), total_variables );
		//join then project the two hypercubes
		UtilitySolutionSpace<AddableInteger, AddableInteger> h4 = h1.join( h2, total_variables ).project( variables, maximize ).getSpace();
		assertTrue( h3.equals(h4) );
		
		
		//this time use the second implementation of the join
		UtilitySolutionSpace<AddableInteger, AddableInteger> h5 = ((Hypercube<AddableInteger, AddableInteger>) h2.project( variables2, maximize ).getSpace()).join( (Hypercube<AddableInteger, AddableInteger>) h1.project( variables1, maximize ).getSpace(), total_variables );
		
		UtilitySolutionSpace<AddableInteger, AddableInteger> h6 = ((Hypercube<AddableInteger, AddableInteger>) h1.join( h2, total_variables ).project( variables1, maximize ).getSpace()).project( variables2, maximize ).getSpace();
		assertTrue( h5.equals(h6) );
		
		assertTrue( h4.equals(h5) );
	}
	
	/** This method creates a random hypercube and a random array of variables, then checks that the result of the projection of these variables out of the hypercube
	 *  by using project and applyProject is the same
	 */
	public void testApplyProjectRandom() {		
		Hypercube<AddableInteger, AddableInteger> h1 = random_hypercube();
		
		String[] variables_names = sub_variables(h1.getVariables());
		
		ProjOutput<AddableInteger,AddableInteger> projOutput = h1.project(variables_names, maximize);
		ProjOutput<AddableInteger,AddableInteger> projOutput2 = h1.clone().applyProject(variables_names, maximize);
		
		// Check the spaces
		assertTrue(projOutput.space.equals(projOutput2.space));
		
		// Check the optimal assignments
		this.verifyProjection(h1, projOutput, variables_names);
		this.verifyProjection(h1, projOutput2, variables_names);
	}
	
	/** This method tests that the projection method (taking a list of variables) computes the correct optimal assignments 
	 * @author Thomas Leaute
	 */
	public void testProjOutputRandom () {
		
		Hypercube<AddableInteger, AddableInteger> hypercube = random_hypercube();
		
		// Randomly choose variables to project
		ArrayList<String> varsTmp = new ArrayList<String> (hypercube.getNumberOfVariables());
		String[] hypercubeVars = hypercube.getVariables();
		for (String var : hypercubeVars) {
			if (Math.random() > 0.5) {
				varsTmp.add(var);
			}
		}
		String[] vars = varsTmp.toArray(new String[0]);
		
		verifyProjection (hypercube, hypercube.project(vars, maximize), vars);
	}
	
	/** This method tests that the projection method (taking a number of variables) computes the correct optimal assignments 
	 * @author Thomas Leaute
	 */
	public void testProjOutputRandom2 () {
		
		Hypercube<AddableInteger, AddableInteger> hypercube = random_hypercube();
		
		// Randomly choose the number of variables to project out
		int nbrVars = (int) ((hypercube.getNumberOfVariables() + 1) * Math.random());
		
		// Build up the list of variables projected out
		String[] vars = new String[nbrVars];
		int nbrVarsKept = hypercube.getNumberOfVariables() - nbrVars;
		for (int i = 0; i < nbrVars; i++) {
			vars[i] = hypercube.getVariable(i + nbrVarsKept);
		}
		
		verifyProjection (hypercube, hypercube.project(nbrVars, maximize), vars);		
	}
	
	/** Verifies the output of the projection method
	 * @param hypercube the initial hypercube
	 * @param projOutput the result of the projection
	 * @param vars the variables projected out
	 */
	private void verifyProjection (Hypercube<AddableInteger, AddableInteger> hypercube, UtilitySolutionSpace.ProjOutput<AddableInteger, AddableInteger> projOutput, String[] vars) {
		
		assertTrue (Arrays.asList(vars).equals(Arrays.asList(projOutput.getVariables())));
		
		Hypercube<AddableInteger, AddableInteger> hypercubeProj = (Hypercube<AddableInteger, AddableInteger>) projOutput.getSpace();
		int nbrVars = vars.length;
		
		if (nbrVars == 0) { // no variables projected out
			assertTrue (hypercubeProj.equivalent(hypercube));
			assertTrue (((BasicHypercube<AddableInteger, ArrayList<AddableInteger>>)projOutput.getAssignments()).isNull());
		} else {
			long nbrAssignments = projOutput.getAssignments().getNumberOfSolutions();
			for (long i = 0; i < nbrAssignments; i++) {
				ArrayList<AddableInteger> assignment = projOutput.getAssignments().getUtility(i);

				AddableInteger[][]	sub_domains = new AddableInteger[nbrVars][];
				for (int j = 0; j < nbrVars; j++) {
					AddableInteger[] val = { assignment.get(j) };
					sub_domains[j] = val;
				}

				BasicHypercube<AddableInteger, AddableInteger> slice = (BasicHypercube<AddableInteger, AddableInteger>)hypercube.slice(vars, sub_domains);

				assertTrue (slice.getUtility(i).equals(hypercubeProj.getUtility(i)));
			}
		}
	}
	
	/** This method creates two random hypercubes, performs a slice on them, and them joins the two sliced hypercubes. 
	 * It compares the result with the one obtained if the join is performed first. 
	 */
	public void testSliceRandom(){
		String[] total_variables = new String[12];
		for(int i=0;i<12;i++)
			total_variables[i] = "X"+i;
		
		Hypercube<AddableInteger, AddableInteger> h1, h2;
		ArrayList<String> slicedVars = new ArrayList<String> ();
		ArrayList< AddableInteger[] > slicingDoms = new ArrayList< AddableInteger[] > ();
		
		// Loop until we get a proper test case
		while (true) {

			h1 = random_hypercube();
			h2 = random_hypercube();
			
			// Choose a random list of variables to slice over
			slicedVars.clear();
			for (String var : total_variables) 
				if (Math.random() > 0.5) 
					slicedVars.add(var);
			
			// Compute the slicing subdomain for each sliced variable
			// The restrictions are that if the variable is contained in one hypercube, its slicing domain must be a subset of the one in the hypercube, 
			// and furthermore the slicing domain must not be empty. 
			slicingDoms.clear();
			List<String> h1Vars = Arrays.asList(h1.getVariables());
			List<String> h2Vars = Arrays.asList(h2.getVariables());
			boolean ok = true;
			for (String var : slicedVars) {
				
				if (h1Vars.contains(var)) {
					if (h2Vars.contains(var)) {
						
						// Both hypercubes contain the variable; its slicing domain must be a subset of its domains in the two hypercubes
						AddableInteger[] dom = intersection(h1.getDomain(var), h2.getDomain(var));
						if (dom.length == 0) {
							ok = false;
							break;
						}
						slicingDoms.add(dom);
					}
					
					else // only h1 contains the variable
						slicingDoms.add(h1.getDomain(var));
				}
				
				else if (h2Vars.contains(var)) { // only h2 contains the variable
					slicingDoms.add(h2.getDomain(var));
				}
				
				else // neither h1 nor h2 contains the variable; the domain doesn't matter
					slicingDoms.add(new AddableInteger[] {new AddableInteger(0), new AddableInteger(1), new AddableInteger(3)});
			}
			
			if (!ok) // one slicing variable has non-intersecting domains in the two hypercubes
				continue;

			// Now, randomly reduce the sizes of the slicing domains
			for (int i = 0; i < slicingDoms.size(); i++) {
				ArrayList<AddableInteger> subdom = new ArrayList<AddableInteger> ();
				AddableInteger[] dom = slicingDoms.get(i);
				for (AddableInteger val : dom) 
					if (Math.random() > 0.75) 
						subdom.add(val);
				if (subdom.isEmpty()) // we don't want empty slicing domains
					subdom.add(dom[0]);
				slicingDoms.set(i, (AddableInteger[]) subdom.toArray(new AddableInteger[subdom.size()]));
			}
			
			// We got a proper test case
			break;
		}

		String[] variables = (String[]) slicedVars.toArray(new String[slicedVars.size()]);
		AddableInteger[][] sub_domains = (AddableInteger[][]) slicingDoms.toArray(new AddableInteger[slicingDoms.size()][]);

		// Slice then join
		Hypercube<AddableInteger, AddableInteger> h1Slice = (Hypercube<AddableInteger, AddableInteger>)h1.slice(variables, sub_domains);
		Hypercube<AddableInteger, AddableInteger> h2Slice = (Hypercube<AddableInteger, AddableInteger>)h2.slice(variables, sub_domains);
		Hypercube<AddableInteger, AddableInteger> h3 = (Hypercube<AddableInteger, AddableInteger>) h2Slice.join( h1Slice, total_variables);
				
		// Join then slice
		Hypercube<AddableInteger, AddableInteger> h4 = (Hypercube<AddableInteger, AddableInteger>)h2.join(h1, total_variables).slice( variables, sub_domains );
				
		assertTrue(h3.equals(h4));
	}
	
	/**
	 * Creates one random hypercube and tests if slice and applySlice return the same output
	 */
	public void testApplySliceRandom(){
		String[] total_variables = new String[12];
		for(int i=0;i<12;i++)
			total_variables[i] = "X"+i;
		
		Hypercube<AddableInteger, AddableInteger> h1;
		ArrayList<String> slicedVars = new ArrayList<String> ();
		ArrayList< AddableInteger[] > slicingDoms = new ArrayList< AddableInteger[] > ();

		h1 = random_hypercube();
		
		// Choose a random list of variables to slice over
		for (String var : total_variables) 
			if (Math.random() > 0.5) 
				slicedVars.add(var);
		
		// Compute the slicing subdomain for each sliced variable
		// The restrictions are that if the variable is contained in one hypercube, its slicing domain must be a subset of the one in the hypercube, 
		// and furthermore the slicing domain must not be empty. 
		List<String> h1Vars = Arrays.asList(h1.getVariables());
		for (String var : slicedVars) {
			
			if (h1Vars.contains(var)) {
				slicingDoms.add(h1.getDomain(var));
			}				
			else 
				slicingDoms.add(new AddableInteger[] {new AddableInteger(0), new AddableInteger(1), new AddableInteger(3)});
		}
			
		// Now, randomly reduce the sizes of the slicing domains
		for (int i = 0; i < slicingDoms.size(); i++) {
			ArrayList<AddableInteger> subdom = new ArrayList<AddableInteger> ();
			AddableInteger[] dom = slicingDoms.get(i);
			for (AddableInteger val : dom) 
				if (Math.random() > 0.75) 
					subdom.add(val);
			if (subdom.isEmpty()) // we don't want empty slicing domains
				subdom.add(dom[0]);
			slicingDoms.set(i, (AddableInteger[]) subdom.toArray(new AddableInteger[subdom.size()]));
		}
					
		String[] variables = (String[]) slicedVars.toArray(new String[slicedVars.size()]);
		AddableInteger[][] sub_domains = (AddableInteger[][]) slicingDoms.toArray(new AddableInteger[slicingDoms.size()][]);
		
		Hypercube<AddableInteger, AddableInteger> h1Slice = (Hypercube<AddableInteger, AddableInteger>)h1.slice(variables, sub_domains);
		Hypercube<AddableInteger, AddableInteger> h2Slice = (Hypercube<AddableInteger, AddableInteger>)h1.applySlice(variables, sub_domains);
		
		assertTrue(h1Slice.equals(h2Slice));
	}
	
	/**this method creates a random hypercube. then project the hypercube into a random a set of its variables and split the resulting hypercube
	 * then it compare the result with the one obtained when splitting first
	 */
	public void testSplittingRandom() {
		
		Hypercube<AddableInteger, AddableInteger> h1;
		String[] variables;
		
		// Loop until we get a proper test case
		while (true) {

			h1 = random_hypercube();

			variables = sub_variables(h1.getVariables());

			if( variables.length != h1.getVariables().length)
				break;
		}

		AddableInteger max = null;
		for( int i = 0; i < h1.getNumberOfSolutions(); i++ ) {
			AddableInteger v = h1.getUtility(i);
			if( max == null ) max = v;
			else if( max.compareTo( v ) > 0 )
				max = v;
		}
		AddableInteger threshold = new AddableInteger( ( int )( max.intValue() * 0.5 ) );

		//combine projection and splitting

		Hypercube<AddableInteger, AddableInteger> h3 = ((Hypercube<AddableInteger, AddableInteger>)h1.project( variables, true ).getSpace()).split( threshold, true );

		Hypercube<AddableInteger, AddableInteger> h4 = (Hypercube<AddableInteger, AddableInteger>)h1.split( threshold, true ).project( variables, true ).getSpace();

		assertTrue(h3.equals(h4));
	}
	
	/** Tests changing the order of variables */
	public void testChangeVariablesOrderRandom() {
		Hypercube<AddableInteger, AddableInteger> h1 = random_hypercube();
		ArrayList<String> tmp = new ArrayList<String>();
		String[] variables = h1.getVariables();
		
		int length = variables.length;
		int index;
		while(tmp.size()<length){
			index = (int)(Math.random()*length);
			if(!tmp.contains(variables[index]))
				tmp.add(variables[index]);
		}
		String[] variables2 = new String[length];
		System.arraycopy(tmp.toArray(), 0, variables2, 0, length);
		
		Hypercube<AddableInteger, AddableInteger> h2 = (Hypercube<AddableInteger, AddableInteger>)h1.changeVariablesOrder(variables2);
		
		Hypercube<AddableInteger, AddableInteger> h3 = (Hypercube<AddableInteger, AddableInteger>)h2.changeVariablesOrder(variables);
		
		assertTrue(h1.equivalent(h2));
		assertTrue(h1.equals(h3));
	}
	
	/**
	 * creates a random hypercube and tests whether changeVariablesOrder and applyChangeVariablesOrder return the same output 
	 */
	public void testApplyChangeVariablesOrderRandom() {
		Hypercube<AddableInteger, AddableInteger> h = random_hypercube();
		ArrayList<String> tmp = new ArrayList<String>();
		String[] variables = h.getVariables();
		
		int length = variables.length;
		int index;
		while(tmp.size()<length){
			index = (int)(Math.random()*length);
			if(!tmp.contains(variables[index]))
				tmp.add(variables[index]);
		}
		String[] variables2 = tmp.toArray(new String[length]);
		
		Hypercube<AddableInteger, AddableInteger> h1 = (Hypercube<AddableInteger, AddableInteger>)h.clone();
		
		h1.applyChangeVariablesOrder(variables2);
		
		Hypercube<AddableInteger, AddableInteger> h2 = (Hypercube<AddableInteger, AddableInteger>)h1.changeVariablesOrder(variables);
		
		assertTrue(h.equivalent(h1));
		assertTrue(h.equals(h2));
	}
	
	/** This method creates a random hypercube, then gets all its utilities values and store them in a new arrray,
	 *  and finally checks this array is identical to the utilities array of the original hypercube
	 */ 
	public void testGetUtility(){
		Hypercube<AddableInteger, AddableInteger> h = random_hypercube();
		
		String[] variables = h.getVariables();
		int number_of_variables = variables.length;
		AddableInteger[] variables_values = new AddableInteger[number_of_variables];
		
		AddableInteger[][] domains = h.getDomains();
		AddableInteger[] domain;
		
		long number_of_utilities = h.getNumberOfSolutions();
		assert number_of_utilities < Integer.MAX_VALUE : "Too many solutions";
		AddableInteger[] results = new AddableInteger[(int) number_of_utilities];
		int[] indexes = new int[number_of_variables];
		int index_to_increment = number_of_variables - 1;
		int index;
		
		for(int i = 0 ; i < number_of_utilities ; i++){
			for(int j = number_of_variables - 1; j >= 0; j--){
				index = indexes[ j ];
				domain = domains[ j ];
				//the current value of the jth variable
				variables_values[ j ] = domain[ index ];
				
				//increment or not the value index of this variable
				if(j == index_to_increment){
					index = ( index + 1 ) % domain.length;
					indexes[ j ] = index;
					// when a whole loop over all values of this variable is done, increment also the next variable which 
					//is previous to this one in order
					if(index == 0)  index_to_increment--;
					else index_to_increment = number_of_variables - 1;
				}
			}
			results[ i ] = h.getUtility( variables, variables_values );	
		}
		
		for(int i = 0 ; i < number_of_utilities ; i++){
			assertTrue(results[i].equals(h.getUtility(i)));
		}
	}

	/** Computes a random sublist of the input list of variables
	 * @param variables 	list of variables
	 * @return 				a random sublist of variables
	 */
	private String[] sub_variables(String[] variables){
		
		if (variables.length == 0) 
			return new String[0];
		
		ArrayList<String> sub_variables_tmp = new ArrayList<String>();
		String[] sub_variables;
		
		while(sub_variables_tmp.size() == 0) {
			for(String v : variables){
				if( (Math.random()<0.4) && !sub_variables_tmp.contains(v) ) {
					sub_variables_tmp.add(v);
				}
			}
		}
		sub_variables = new String[sub_variables_tmp.size()];
		System.arraycopy(sub_variables_tmp.toArray(), 0, sub_variables, 0, sub_variables_tmp.size());
		return sub_variables;
	}
	
	/** @return a random hypercube */
	public static Hypercube<AddableInteger, AddableInteger> random_hypercube() {
		return random_hypercube(0.1);
	}
	
	/**
	 * @param prob 	for any given utility, the probability that it be infinite
	 * @return random hypercube
	 * @todo This implementation is too restrictive (imposes an order on variables, values...). 
	 */
	public static Hypercube<AddableInteger, AddableInteger> random_hypercube(double prob){
		return random_hypercube(prob, AddableInteger.class);
	}
	
	/**
	 * @param <U> 			the class used for utility values
	 * @param prob 			for any given utility, the probability that it be infinite
	 * @param utilClass 	the class used for utility values
	 * @return random hypercube
	 * @todo This implementation is too restrictive (imposes an order on variables, values...). 
	 */
	public static < U extends Addable<U> > Hypercube<AddableInteger, U> random_hypercube(double prob, Class<U> utilClass){
		
		String variable_name;
		int domain_size;
		
		//pick a random number of variables
		int number_variables = 2 + (int)(4*Math.random());
		String[] variables_names = new String[number_variables];
		
		AddableInteger[][] variables_domains = new AddableInteger[number_variables][];
		
		//set random domains for the variables
		int shift = (int)(5*Math.random());
		int number_of_utility_values = 1;
		for(int j=0;j<number_variables;j++){
			variable_name = "X"+(j+shift);
			variables_names[j] = variable_name;
			domain_size = 2 + (int)(3*Math.random());

			variables_domains[j] = new AddableInteger[domain_size];
			for(int k=0;k<domain_size;k++){
				variables_domains[j][k] = new AddableInteger (k);
			}
			
			
			number_of_utility_values *= domain_size;
		}
		
		U infeasibleUtil = null;
		try {
			if (inf == Infinity.PLUS_INFINITY) 
				infeasibleUtil = utilClass.newInstance().getPlusInfinity();
			else 
				infeasibleUtil = utilClass.newInstance().getMinInfinity();
		} catch (Exception e) {
			fail();
		}
		
		@SuppressWarnings("unchecked")
		U[] utility_values = (U[]) Array.newInstance(utilClass, number_of_utility_values);
		for(int j=0;j<number_of_utility_values;j++){
			if(inf != Infinity.NONE && Math.random() < prob) {
				utility_values[j] = infeasibleUtil;
			} else {
				utility_values[j] = infeasibleUtil.fromString(Integer.toString((int) (20 * Math.random())));
			}
		}
		
		return new Hypercube<AddableInteger, U>(variables_names,variables_domains,utility_values, infeasibleUtil);
	}
	
	/** 
	 * @param number_variables 	number of variables of the generated Hypercube will contain
	 * @param domain_size 		number of values that the domains of the variables of the Hypercube
	 * @param redundancy 		redundancy in the array of utilities (between 0 - 100)
	 * @param shift 			the number starting with which the variables should be named (Xshift, X(shift+1)...)
	 * @return a random hypercube, with some random utilities set to -1
	 */
	public static Hypercube <AddableInteger, AddableInteger> random_hypercube (int number_variables, int domain_size, int redundancy, int shift) {
		return random_hypercube (number_variables, domain_size, redundancy, shift, null, AddableInteger.class);
	}
	
	/** 
	 * @param number_variables 	number of variables of the generated Hypercube will contain
	 * @param domain_size 		number of values that the domains of the variables of the Hypercube
	 * @param redundancy 		redundancy in the array of utilities (between 0 - 100)
	 * @param shift 			the number starting with which the variables should be named (Xshift, X(shift+1)...)
	 * @param infeasibleUtil	the infeasible utility
	 * @return a random hypercube, with some random utilities set to -1
	 */
	public static Hypercube <AddableInteger, AddableInteger> random_hypercube 
		(int number_variables, int domain_size, int redundancy, int shift, AddableInteger infeasibleUtil) {
		return random_hypercube (number_variables, domain_size, redundancy, shift, infeasibleUtil, AddableInteger.class);
	}
	
	/** 
	 * @param <U> 				the type of utility values
	 * @param number_variables 	number of variables of the generated Hypercube will contain
	 * @param domain_size 		number of values that the domains of the variables of the Hypercube
	 * @param redundancy 		redundancy in the array of utilities (between 0 - 100)
	 * @param shift 			the number starting with which the variables should be named (Xshift, X(shift+1)...)
	 * @param infeasibleUtil	the infeasible utility
	 * @param utilClass 		the type of utility values
	 * @return a random hypercube, with some random utilities set to -1
	 */
	@SuppressWarnings("unchecked")
	public static < U extends Addable<U> > Hypercube <AddableInteger, U> random_hypercube 
		(int number_variables, int domain_size, int redundancy, int shift, U infeasibleUtil, Class<U> utilClass) {
		
		int first_value;
		
		//array of the variables of the Hypercube
		String[] variables_names = new String[number_variables];
		
		//array of the domains of the variables
		AddableInteger[][] variables_domains = new AddableInteger[number_variables][];
		
		//array of the utility values
		U[] utility_values;
		
		//counter of number of utilities that the Hypercube must contain
		int number_of_utilities = 1;
		
		/// variables names and their domains ///
		for(int j=0;j<number_variables;j++){
			//name of the variable
			variables_names[j] = "X"+(j+shift);
			
			
			//fill the domain of the variable
			variables_domains[j] = new AddableInteger[domain_size];
			first_value = (int)Math.random()*domain_size;
			for(int k=0;k<domain_size;k++){
				variables_domains[j][k] = new AddableInteger (first_value + k);
			}
			
			//increase the counter
			number_of_utilities *= domain_size;
		}
		/// array of utilities ///
		int nb = (int) (number_of_utilities * redundancy / 100);
		utility_values = (U[]) Array.newInstance(utilClass, number_of_utilities);
		U utilInstance = null;
		try {
			utilInstance = utilClass.newInstance();
		} catch (Exception e) {
			fail();
		}
		for(int j=0;j<number_of_utilities;j++){
			utility_values[j] = utilInstance.fromString(Integer.toString( (int)(Math.random()*nb) ));
		}
		
		
		
		return new Hypercube<AddableInteger, U>(variables_names,variables_domains,utility_values, infeasibleUtil);
	}
	
	/** Computes the union of two lists, respecting the input total order on the elements
	 * @param array1 			the first list
	 * @param array2 			the second list
	 * @param total_variables 	the desired total order on the elements
	 * @return union of the two input lists
	 */
	private String[] union( String[] array1, String[] array2 , String[] total_variables) {
		int array1_size = array1.length;
		int array2_size = array2.length;
		int index = 0, index1 = 0, index2 = 0;
		
		String v1, v2, v;
		
		String[] union_tmp = new String[ array1_size + array2_size ];
		boolean not_found;
		int index3 = 0;
		while( ( index1 < array1_size ) || ( index2 < array2_size ) ) {
			v = total_variables[index3];
			not_found = true;
			if( index1 < array1_size ) {
				v1 = array1[ index1 ];
				if( v.equals( v1 )) {
					union_tmp[ index ] = v1;
					not_found = false;
					index1++;
					index++;
				}
			}
			if( index2 < array2_size ) {
				v2 = array2[ index2 ];
				if(v.equals(v2)) {
					if( not_found ) {
						union_tmp[ index ] = v2;
						index++;
					}
					index2++;
				}
			}
			index3++;
		}
		if(index == 0) return null;
		
		String[] union = new String[index];
		System.arraycopy(union_tmp, 0, union, 0, index);
		return union;
	}
	
	/** Computes the intersection of two lists
	 * @param <T> 		the type of the elements in the lists
	 * @param array1 	the first list
	 * @param array2 	the second list
	 * @return the intersection of the two lists
	 */
	@SuppressWarnings("unchecked")
	private < T extends Comparable<T> > T[] intersection( T[] array1, T[] array2 ) {
		int array1_size = array1.length;
		int array2_size = array2.length;
		int index = 0, index1 = 0, index2 = 0;
		
		T v1, v2;
		
		Class<?> classOfT = array1.getClass().getComponentType();
		T[] intersection_tmp = (T[]) Array.newInstance(classOfT, Math.min( array1_size, array2_size ) );
		
		while( ( index1 < array1_size ) && ( index2 < array2_size ) ) {
			v1 = array1[ index1 ];
			v2 = array2[ index2 ];
			
			if( v1.compareTo(v2) == 0 ) {
				index1 ++;
				index2 ++;
				
				intersection_tmp[ index ] = v1;
				index++;
			}
			else {
				if( v1.compareTo(v2) < 0 )
					index1++;
				else
					index2++;
			}
		}
		
		if( index == 0 ) return (T[]) Array.newInstance(classOfT, 0);
		
		T[] intersection = (T[]) Array.newInstance(classOfT, index);
		System.arraycopy(intersection_tmp, 0, intersection, 0, index);
		return intersection;
	}
	
	/** Tests the projection methods on a simple example */
	public void testProjection () {
		Hypercube<AddableInteger, AddableInteger> in = new Hypercube<AddableInteger, AddableInteger> ("src/frodo2/solutionSpaces/hypercube/tests/files/proj_1_in.xml");
		UtilitySolutionSpace.ProjOutput<AddableInteger, AddableInteger> proj = in.project(1, true);
		Hypercube<AddableInteger, AddableInteger> out = new Hypercube<AddableInteger, AddableInteger> ("src/frodo2/solutionSpaces/hypercube/tests/files/proj_1_out.xml");
		assertTrue(out.equals(proj.getSpace()));
		
		proj = in.project("X3", true);
		assertTrue(out.equals(proj.getSpace()));
		
		in = new Hypercube<AddableInteger, AddableInteger> ("src/frodo2/solutionSpaces/hypercube/tests/files/proj_2_in.xml");
		proj = in.project("X1", false);
		out = new Hypercube<AddableInteger, AddableInteger> ("src/frodo2/solutionSpaces/hypercube/tests/files/proj_2_out.xml");
		assertTrue(out.equals(proj.getSpace()));
	}
	
	/** Tests the serialization of a random hypercube 
	 * @author Thomas Leaute
	 * @throws IOException 				if unable to create the pipes, or an I/O error occurs when deserializing the hypercube
	 * @throws ClassNotFoundException 	should never happen
	 */
	@SuppressWarnings("unchecked")
	public void testSerializationRandom() throws IOException, ClassNotFoundException {
		
		// Thread used to write an object to a stream
		class StreamWriter extends Thread {
			private ObjectOutputStream out;
			private Object obj;
			
			/** Constructor 
			 * @param out the output stream
			 * @param obj the object to be written
			 */
			public StreamWriter (ObjectOutputStream out, Object obj) {
				this.out = out;
				this.obj = obj;
				start();
			}
			
			public void run() {
				try {
					out.writeObject(obj);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		Hypercube<AddableInteger, AddableInteger> h1 = random_hypercube();

		// Create connected piped streams
		PipedOutputStream pipedOut = new PipedOutputStream ();
		PipedInputStream pipedIn = new PipedInputStream (pipedOut);
		ObjectOutputStream objOut = new ObjectOutputStream (pipedOut);
		ObjectInputStream objIn = new ObjectInputStream (pipedIn);

		// Send the hypercube through the output stream
		new StreamWriter (objOut, h1);

		// Read the hypercube from the input stream
		Hypercube<AddableInteger, AddableInteger> h2 = (Hypercube<AddableInteger, AddableInteger>) objIn.readObject();
		objIn.close();

		// Check the hypercubes are equal
		assertTrue (h1.equals(h2));
	}
	
	/**
	 * Method to test the serialization of the NULL hypercube
	 */
	@SuppressWarnings("unchecked")
	public void testSerializationNullHypercube() {
		// Thread used to write an object to a stream
		class StreamWriter extends Thread {
			private ObjectOutputStream out;
			private Object obj;
			
			/** Constructor 
			 * @param out the output stream
			 * @param obj the object to be written
			 */
			public StreamWriter (ObjectOutputStream out, Object obj) {
				this.out = out;
				this.obj = obj;
				start();
			}
			
			public void run() {
				try {
					out.writeObject(obj);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		Hypercube<AddableInteger, AddableInteger> h1 = (Hypercube<AddableInteger, AddableInteger>)Hypercube.NullHypercube.NULL;
		
		try {
			// Create connected piped streams
			PipedOutputStream pipedOut = new PipedOutputStream ();
			PipedInputStream pipedIn = new PipedInputStream (pipedOut);
			ObjectOutputStream objOut = new ObjectOutputStream (pipedOut);
			ObjectInputStream objIn = new ObjectInputStream (pipedIn);
			
			// Send the hypercube through the output stream
			new StreamWriter (objOut, h1);
			
			// Read the hypercube from the input stream
			Hypercube<AddableInteger, AddableInteger> h2 = (Hypercube<AddableInteger, AddableInteger>) objIn.readObject();
			objIn.close();
			
			// Check the hypercubes are equal
			assertTrue (h1.equals(h2));
			
		} catch (IOException e) {
			fail(e.toString()); /// @bug objIn might not be closed
		} catch (ClassNotFoundException e) {
			fail(e.toString());
		}
	}
	
	/** Tests the union() method 
	 * @author Thomas Leaute
	 */
	public void testUnion () {
		
		String[] a1 = {};
		String[] b1 = {};
		String[] c1 = {};
		
		assertTrue(Arrays.equals(Hypercube.union(a1, b1), c1));
		
		String[] a2 = {"1", "2"};
		String[] b2 = {};
		String[] c2 = {"1", "2"};
		
		assertTrue(Arrays.equals(Hypercube.union(a2, b2), c2));

		String[] a3 = {"1", "2", "3"};
		String[] b3 = {"0", "2", "4"};
		String[] c3 = {"0", "1", "2", "4", "3"};
		
		assertTrue(Arrays.equals(Hypercube.union(a3, b3), c3));
		
		String[] a4 = {"1", "2", "3"};
		String[] b4 = {"0", "3", "1"};
		String[] c4 = {"0", "1", "2", "3"};
		
		assertTrue(Arrays.equals(Hypercube.union(a4, b4), c4));
		
		String[] a5 = {"1", "5", "6", "0", "2"};
		String[] b5 = {"1", "8", "5", "0", "6", "3"};
		String[] c5 = {"1", "8", "5", "6", "0", "3", "2"};
		
		assertTrue(Arrays.equals(Hypercube.union(a5, b5), c5));
	}
	
	/** Tests the projection method when all variables are projected out 	 
	 * @author Thomas Leaute
	 */
	public void testProjectAllRandom () {
		
		Hypercube<AddableInteger, AddableInteger> hypercube = random_hypercube();
		
		// Compute the optimal utility
		AddableInteger opt = hypercube.getUtility(0);
		for (int i = 0; i < hypercube.getNumberOfSolutions(); i++) {
			AddableInteger val = hypercube.getUtility(i);
			if ((maximize && val.compareTo(opt) > 0) || (! maximize && val.compareTo(opt) < 0)) {
				opt = val;
			}
		}
		
		// Test one projection method
		UtilitySolutionSpace.ProjOutput<AddableInteger, AddableInteger> proj = hypercube.project(hypercube.getNumberOfVariables(), maximize);
		assertTrue(new ScalarHypercube<AddableInteger, AddableInteger> (opt, null, new AddableInteger [0].getClass()).equivalent(proj.getSpace()));
		assertTrue (Arrays.asList(proj.getVariables()).equals(Arrays.asList(hypercube.getVariables())));
		assertTrue(opt.compareTo(hypercube.getUtility(proj.getAssignments().getUtility(0).toArray(new AddableInteger[0]))) == 0);
		
		// Test the other projection method
		proj = hypercube.project(hypercube.getVariables(), maximize);
		assertTrue(new ScalarHypercube<AddableInteger, AddableInteger> (opt, null, new AddableInteger [0].getClass()).equivalent(proj.getSpace()));
		assertTrue (Arrays.asList(proj.getVariables()).equals(Arrays.asList(hypercube.getVariables())));
		assertTrue(opt.compareTo(hypercube.getUtility(proj.getAssignments().getUtility(0).toArray(new AddableInteger[0]))) == 0);
		
	}
	
	/** Tests blindProject() 	 
	 * @author Thomas Leaute
	 */
	public void testBlindProjectRandom () {
		
		Hypercube<AddableInteger, AddableInteger> hypercube = random_hypercube();
		
		// Randomly pick variables to project out
		ArrayList<String> candidates = new ArrayList<String> (Arrays.asList(hypercube.getVariables()));
		for (java.util.Iterator<String> iter = candidates.iterator(); iter.hasNext(); ) {
			iter.next();
			if (Math.random() < .5) 
				iter.remove();
		}
		String[] varsOut = candidates.toArray(new String [candidates.size()]);
		
		assertTrue (hypercube + "\n.blindProject(" + Arrays.toString(varsOut) + ", " + maximize + ") = \n" + 
				hypercube.blindProject(varsOut, maximize) + "\n!=\n" + hypercube.project(varsOut, maximize).space, 
				hypercube.blindProject(varsOut, maximize).equivalent(hypercube.project(varsOut, maximize).space));
	}
	
	/** Tests the additive join method that automatically computes the variable ordering */
	public void testSmartJoinRandom () {
		this.testSmartJoinRandom(true);
	}
	
	/** Tests the multiplicative join method that automatically computes the variable ordering */
	public void testSmartMultiplyRandom () {
		this.testSmartJoinRandom(false);
	}
	
	/** Tests the join method that automatically computes the variable ordering 
	 * @author Thomas Leaute
	 * @param addition 	\c true if utilities should be added, \c false if they should be multiplied
	 */
	public void testSmartJoinRandom ( boolean addition ) {
		Hypercube<AddableInteger, AddableInteger> h1 = random_hypercube();
		Hypercube<AddableInteger, AddableInteger> h2 = random_hypercube();
		String[] variables1 = h1.getVariables();
		String[] variables2 = h2.getVariables();
		
		// Choose an order on all variables
		String[] onlyIn1 = Hypercube.sub(variables1, variables2);
		String[] onlyIn2 = Hypercube.sub(variables2, variables1);
		String[] inBoth = Hypercube.sub(variables1, onlyIn1);
		String[] order1 = new String[onlyIn1.length + inBoth.length];
		System.arraycopy(onlyIn1, 0, order1, 0, onlyIn1.length);
		System.arraycopy(inBoth, 0, order1, onlyIn1.length, inBoth.length);
		String[] order2 = new String[onlyIn2.length + inBoth.length];
		System.arraycopy(onlyIn2, 0, order2, 0, onlyIn2.length);
		System.arraycopy(inBoth, 0, order2, onlyIn2.length, inBoth.length);
		String[] totalOrder = new String[onlyIn1.length + onlyIn2.length + inBoth.length];
		System.arraycopy(onlyIn1, 0, totalOrder, 0, onlyIn1.length);
		System.arraycopy(onlyIn2, 0, totalOrder, onlyIn1.length, onlyIn2.length);
		System.arraycopy(inBoth, 0, totalOrder, onlyIn1.length + onlyIn2.length, inBoth.length);
				
		// Re-order hypercubes and compute join
		UtilitySolutionSpace<AddableInteger, AddableInteger> h1New = h1.changeVariablesOrder(order1);
		UtilitySolutionSpace<AddableInteger, AddableInteger> h2New = h2.changeVariablesOrder(order2);
		
		// Compute the join and compare with the result of the smart join
		if (addition) {
			UtilitySolutionSpace<AddableInteger, AddableInteger> join = h1New.join(h2New, totalOrder);
			assertTrue(join.equivalent(h1.join(h2)));
		} else {
			
			// First offset all utility values by 1 to make sure they are all > 0, which avoids 0 * INF
			ScalarHypercube<AddableInteger, AddableInteger> one = new ScalarHypercube<AddableInteger, AddableInteger> (new AddableInteger (1), null, new AddableInteger [0].getClass());
			h1New = h1New.join(one);
			h2New = h2New.join(one);
			h1 = (Hypercube<AddableInteger, AddableInteger>) h1.join(one);
			h2 = (Hypercube<AddableInteger, AddableInteger>) h2.join(one);
			
			UtilitySolutionSpace<AddableInteger, AddableInteger> join = h1New.multiply(h2New, totalOrder);
			assertTrue(join.equivalent(h1.multiply(h2)));
		}
	}
	
	/** Tests the expectation operation 
	 * @author Thomas Leaute */
	public void testExpectation () {
		
		Hypercube<AddableInteger, AddableReal> utilSpace = null, probSpace = null;
		String[] randVars = null;
		ArrayList< AddableInteger[] > domains = null;
		HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > distributions = null;
		
		boolean valid = false;
		while (!valid) { // loop until we get a valid test case
			
			utilSpace = random_hypercube(0, AddableReal.class);
			distributions = new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
			
			probSpace = random_hypercube(1, 2 + (int)(3*Math.random()), 10000, (int)(5*Math.random()), null, AddableReal.class);
			AddableReal sum = new AddableReal(0.0);
			for (Iterator<AddableInteger, AddableReal> iter = probSpace.iterator(); iter.hasNext(); ) 
				sum = sum.add(iter.nextUtility());
			assert sum.doubleValue() > 0.0;
			for (Iterator<AddableInteger, AddableReal> iter = probSpace.iterator(); iter.hasNext(); ) 
				iter.setCurrentUtility(iter.nextUtility().divide(sum));
			distributions.put(probSpace.getVariable(0), probSpace);
			
			// With some probability, introduce a second random variable
			if (Math.random() > 0.3) {
				probSpace = random_hypercube(1, 2 + (int)(3*Math.random()), 10000, (int)(5*Math.random()), null, AddableReal.class);
				sum = new AddableReal(0.0);
				for (Iterator<AddableInteger, AddableReal> iter = probSpace.iterator(); iter.hasNext(); ) 
					sum = sum.add(iter.nextUtility());
				assert sum.doubleValue() > 0.0;
				for (Iterator<AddableInteger, AddableReal> iter = probSpace.iterator(); iter.hasNext(); ) 
					iter.setCurrentUtility(iter.nextUtility().divide(sum));
				distributions.put(probSpace.getVariable(0), probSpace);				
			}
			
			randVars = new String [distributions.size()];
			domains = new ArrayList< AddableInteger[] > ();
			int i = 0;
			for (UtilitySolutionSpace<AddableInteger, AddableReal> prob : distributions.values()) {
				randVars[i++] = prob.getVariable(0);
				domains.add(prob.getDomain(0));
			}
			
			valid = true;

			// If a random variable is contained in the utilSpace, its domain must be a superset of the one in the probSpace
			for (i = 0; i < randVars.length; i++) {
				String var = randVars[i];
				AddableInteger[] dom = domains.get(i);
				AddableInteger[] dom2 = utilSpace.getDomain(var);
				
				if (dom2 != null) { // the random variable is contained in the hypercube
					
					if (Hypercube.sub(dom, dom2).length > 0) {
						
						// The random variable can take values that are not in the corresponding domain in the utilSpace
						// Try another test case
						valid = false;
						break;
					}
				}
			}
		}
		
		// Compute the expectation
		UtilitySolutionSpace<AddableInteger, AddableReal> expectation = utilSpace.expectation(distributions);
		
		// Recompute the expectation in a different (memory-inefficient) way: fist multiply, then slice, then join
		UtilitySolutionSpace<AddableInteger, AddableReal> product = utilSpace;
		for (UtilitySolutionSpace<AddableInteger, AddableReal> prod : distributions.values()) 
			product = product.multiply(prod);
		UtilitySolutionSpace<AddableInteger, AddableReal> expectation2 = new ScalarHypercube<AddableInteger, AddableReal> (new AddableReal (0), null, new AddableInteger [0].getClass());
		
		// Re-order the random variables and their domains so that their order is consistent with the order in product
		TreeMap<Integer, Integer> treeMap = new TreeMap<Integer, Integer> ();
		int end = product.getNumberOfVariables();
		for (int i = 0; i < randVars.length; i++) {
			String var = randVars[i];
			int pos = product.getIndex(var);
			if (pos < 0) { // the variable is absent from product
				treeMap.put(end++, i);
			} else 
				treeMap.put(pos, i);
		}
		ArrayList<String> randVars2 = new ArrayList<String> (randVars.length);
		ArrayList< AddableInteger[] > domains2 = new ArrayList< AddableInteger[] > (domains.size());
		for (Integer varIndex : treeMap.values()) {
			randVars2.add(randVars[varIndex]);
			domains2.add(domains.get(varIndex));
		}
		randVars = randVars2.toArray(randVars);
		domains = domains2;
		
		// Initialize the current assignments to the random variables
		int[] valIndexes = new int [randVars.length]; // indexes of the current variable assignments
		AddableInteger[][] assignments = new AddableInteger[randVars.length][];
		for (int i = 0; i < randVars.length; i++) 
			assignments[i] = new AddableInteger[] {domains.get(i)[0]};

		// Go through all combinations of assignments to the random variables
		while (true) {
			
			// Slice the hypercube over the current assignment, and join with expectation2
			UtilitySolutionSpace<AddableInteger, AddableReal> slice = product.slice(randVars, assignments);
			expectation2 = expectation2.join(slice);

			// Move to the next assignment for the random variables
			int varIndex = 0;
			for ( ; varIndex < randVars.length; varIndex++) {
				
				// Check if we have exhausted all values in the domain of the varIndex'th variable
				AddableInteger[] dom = domains.get(varIndex);
				if (valIndexes[varIndex] == dom.length - 1) {
					
					// Reset the variable to its first domain value, and increment the next variable
					valIndexes[varIndex] = 0;
					assignments[varIndex] = new AddableInteger [] { dom[0] };
				}
				
				else { // increment the value for this variable
					valIndexes[varIndex]++;
					assignments[varIndex] = new AddableInteger [] { dom[ valIndexes[varIndex] ] };
					break;
				}
			}
			
			if (varIndex >= randVars.length) // we have exhausted all combinations
				break;
		}
		
		// Compare the two expectations solution-wise, with some error margin
		assertEquals(expectation + " != " + expectation2, expectation.getNumberOfVariables(), expectation2.getNumberOfVariables());
		Iterator<AddableInteger, AddableReal> iter = expectation.iterator();
		assertTrue (iter.getCurrentSolution() != null);
		Iterator<AddableInteger, AddableReal> iter2 = expectation2.iterator(expectation.getVariables());
		Iterator<AddableInteger, AddableReal> iter3 = expectation.resolve().iterator(expectation.getVariables());
		assertEquals (expectation.getNumberOfSolutions(), iter.getNbrSolutions());
		AddableReal util = null;
		for (int i = 0; iter.hasNext(); i++) {
			util = iter.nextUtility();
			assertTrue (i + "\n" + expectation, util != null);
			assertTrue (util + " != " + iter2.getCurrentUtility() + "\n" + expectation + " != " + expectation2, 
					util != null && util.equals(iter2.nextUtility(), 1E-6));
			assertTrue (util + " != " + iter3.getCurrentUtility() + "\n" + expectation + " != " + expectation.resolve(), 
					util.equals(iter3.nextUtility(), 1E-6));
		}
	}
	
	/** Tests the composition method */
	@SuppressWarnings("unchecked")
	public void testCompose () {
		
		// Generate a random hypercube
		Hypercube<AddableInteger, AddableInteger> hypercube = random_hypercube();
		String[] initVars = hypercube.getVariables();
		
		// Randomly generate the variables the substitution depends on
		Hypercube<AddableInteger, AddableInteger> tmp = random_hypercube();
		String[] varsInSubst = tmp.getVariables();
		int nbrUtilsSubst = 1;
		AddableInteger[][] domsInSubst = new AddableInteger[varsInSubst.length][];
		for (int i = 0; i < varsInSubst.length; i++) {
			AddableInteger[] dom = hypercube.getDomain(varsInSubst[i]);
			if (dom == null) 
				dom = tmp.getDomain(varsInSubst[i]);
			domsInSubst[i] = dom;
			nbrUtilsSubst *= dom.length;
		}
		
		// Randomly choose variables to substitute (they must not be in varsInSubst)
		HashSet<String> varsOutTmp = new HashSet<String> ();
		for (int i = (int) (5 * Math.random()); i >= 0; i--) {
			String var = "X" + (int) (15 * Math.random());
			while (tmp.getDomain(var) != null) // must not be in varsInSubst 
				var = "X" + (int) (15 * Math.random());
			varsOutTmp.add(var);
		}
		String[] varsOut = varsOutTmp.toArray(new String [varsOutTmp.size()]);
		AddableInteger[][] domsOut = new AddableInteger[varsOut.length][];
		boolean hasOneVarOut = false;
		for (int i = 0; i < varsOut.length; i++) {
			AddableInteger[] dom = hypercube.getDomain(varsOut[i]);
			if (dom != null) {
				domsOut[i] = dom;
				hasOneVarOut = true;
			} else 
				domsOut[i] = new AddableInteger[] { new AddableInteger(1), new AddableInteger(2) }; // (must not include -100)
		}
		
		// Fill in the utility array of the random substitution
		ArrayList<AddableInteger>[] utilsSubst = (ArrayList<AddableInteger>[]) new ArrayList[nbrUtilsSubst];
		for (int i = 0; i < nbrUtilsSubst; i++) {
			ArrayList<AddableInteger> newValues = new ArrayList<AddableInteger> (varsOut.length);
			for (int j = 0; j < varsOut.length; j++) {
				
				// With some low probability, choose a value that is outside the variable's domain
				if (Math.random() < 0.1) 
					newValues.add(new AddableInteger (-100));
				else {
					AddableInteger[] dom = domsOut[j];
					newValues.add( dom[ (int) (dom.length * Math.random()) ] );
				}
			}
			utilsSubst[i] = newValues;
		}
		
		// Instantiate the substitution
		BasicHypercube< AddableInteger, ArrayList<AddableInteger> > substitution;
		if (varsInSubst.length == 0) { // the substitution is constant
			substitution = new ScalarBasicHypercube< AddableInteger, ArrayList<AddableInteger> > (utilsSubst[0], null);
		} else 
			substitution = new BasicHypercube< AddableInteger, ArrayList<AddableInteger> > (varsInSubst, domsInSubst, utilsSubst, null);
		
		// Compute the composition 
		// Its variables are (initVars U varsInSubst) \ varsOut
		BasicUtilitySolutionSpace<AddableInteger, AddableInteger> composition = hypercube.compose(varsOut, substitution);
		
		// If no variable is substituted, the result should be equivalent to the input
		if (! hasOneVarOut) {
			assertTrue (composition.equivalent(hypercube));
			return;
		}
		
		// Re-order the composition, putting all variables in varsInSubst first (in the same order)
		String[] newCompOrder = new String[composition.getNumberOfVariables()];
		System.arraycopy(varsInSubst, 0, newCompOrder, 0, varsInSubst.length);
		String[] varsInCompNotInSubst = Hypercube.sub(composition.getVariables(), varsInSubst);
		System.arraycopy(varsInCompNotInSubst, 0, newCompOrder, varsInSubst.length, varsInCompNotInSubst.length);
		composition = composition.changeVariablesOrder(newCompOrder);
		int nbrUtilsInCompNotInSubst = 1;
		for (String var : varsInCompNotInSubst) 
			nbrUtilsInCompNotInSubst *= composition.getDomain(var).length;
		
		// Grow the initial hypercube with variables in varsInSubst \ initVars (if there are such variables)
		String[] varsInSubstNotInHyper = Hypercube.sub(varsInSubst, initVars);
		if (varsInSubstNotInHyper.length != 0) {
			AddableInteger[][] domsInSubstNotInHyper = new AddableInteger[varsInSubstNotInHyper.length][];
			int nbrUtilsInSubstNotInHyper = 1;
			for (int i = 0; i < varsInSubstNotInHyper.length; i++) {
				AddableInteger[] dom = substitution.getDomain(varsInSubstNotInHyper[i]);
				domsInSubstNotInHyper[i] = dom;
				nbrUtilsInSubstNotInHyper *= dom.length;
			}
			AddableInteger[] utilsInSubstNotInHyper = new AddableInteger[nbrUtilsInSubstNotInHyper];
			Arrays.fill(utilsInSubstNotInHyper, new AddableInteger(0));
			hypercube = (Hypercube<AddableInteger, AddableInteger>) hypercube.join(
					new Hypercube<AddableInteger, AddableInteger> (varsInSubstNotInHyper, domsInSubstNotInHyper, utilsInSubstNotInHyper, null));
		}

		AddableInteger infeasibleUtil = null;
		if (inf == Infinity.PLUS_INFINITY) 
			infeasibleUtil = AddableInteger.PlusInfinity.PLUS_INF;
		else 
			infeasibleUtil = AddableInteger.MinInfinity.MIN_INF;
		
		// Loop over all possible assignments to varsInSubst
		ext: for (int i = 0; i < varsInSubst.length; i++) {
			
			// Slice the hypercube over the ith substitution
			ArrayList<AddableInteger> newValues = utilsSubst[i];
			AddableInteger[][] newDoms = new AddableInteger[varsOut.length][1];
			for (int j = 0; j < varsOut.length; j++) {
				
				// Get the substitution value for the jth variable
				AddableInteger val = newValues.get(j);
				
				// Check if this substitution value is in the domain of the variable
				AddableInteger[] dom = hypercube.getDomain(varsOut[j]);
				if (dom != null && ! Arrays.asList(dom).contains(val)) {
					
					// Check that all utilities corresponding to this substitution are infeasible
					for (int k = nbrUtilsInCompNotInSubst * i; k < nbrUtilsInCompNotInSubst * (i+1); k++) 
						assertTrue (composition.getUtility(k) + " != " + infeasibleUtil, 
								composition.getUtility(k).equals(infeasibleUtil));
					
					continue ext;
				}
				
				newDoms[j] = new AddableInteger[] { val };
			}
			BasicUtilitySolutionSpace<AddableInteger,AddableInteger> slice = hypercube.slice(varsOut, newDoms);

			// Re-order the slice, using the same order as in the composition
			slice = slice.changeVariablesOrder(newCompOrder);
			
			// Check that, given the ith substitution, composition and slice agree on all utilities as a function of varsInCompNotInSubst
			for (int j = nbrUtilsInCompNotInSubst * i; j < nbrUtilsInCompNotInSubst * (i+1); j++) 
				assertTrue (slice.getUtility(j) + " != " + composition.getUtility(j), 
						slice.getUtility(j).equals(composition.getUtility(j)));
			
		}		

	}
	
	/** Test for the sample(int) method */
	public void testSample () {
		
		// Generate a random probability space
		int nbrProbs = 3;
		double[] probsTmp = new double[nbrProbs];
		double sum = 0.0;
		for (int i = 0; i < nbrProbs; i++) {
			probsTmp[i] = Math.random();
			sum += probsTmp[i];
		}
		AddableReal[] probs = new AddableReal[nbrProbs];
		for (int i = 0; i < nbrProbs; i++) 
			probs[i] = new AddableReal (probsTmp[i] / sum);
		
		AddableInteger[] vals = new AddableInteger[nbrProbs];
		for (int i = 0; i < nbrProbs; i++) 
			vals[i] = new AddableInteger (i);
		
		Hypercube<AddableInteger, AddableReal> probSpace = 
			new Hypercube<AddableInteger, AddableReal> (new String[] {"var"}, new AddableInteger[][] {vals}, probs, AddableReal.PlusInfinity.PLUS_INF);
		
		// Sample the probability space
		int nbrSamples = nbrProbs * 10000;
		Map<AddableInteger, Double> samples = probSpace.sample(nbrSamples);
		
		// Normalize the weights
		for (Map.Entry<AddableInteger, Double> entry : samples.entrySet()) 
			entry.setValue(entry.getValue() / nbrSamples);
		
		// Check that the frequencies look approximately correct
		for (int i = 0; i < nbrProbs; i++) 
			assertTrue (probs[i].equals(new AddableReal (samples.get(vals[i])), 0.01));
	}
	
	/** Tests the consensus() operation with weighted samples */
	public void testConsensusWeighted () {
		this.testConsensusWeighted(false);
	}
	
	/** Tests the consensusExpect() operation with weighted samples */
	public void testConsensusWeightedExpect () {
		this.testConsensusWeighted(true);
	}
	
	/** Tests the consensus() operation with weighted samples 
	 * @param expect whether to compose the consensus operation with the expectation operation
	 */
	private void testConsensusWeighted (final boolean expect) {
		
		Hypercube<AddableInteger, AddableReal> h1 = random_hypercube(0.1, AddableReal.class);
		
		// Choose one variable to be projected out (most probably, one of the hypercube's variables)
		String varOut = "X" + (int)(7 * Math.random());
		if (Math.random() > 0.1) 
			varOut = h1.getVariable((int) (Math.random() * h1.getNumberOfVariables()));
		
		// Choose random variables
		ArrayList<String> randVars = new ArrayList<String> ();
		for (int i = 0; i < 9; i++) 
			if (Math.random() > 0.3) 
				randVars.add("X" + i);
		randVars.remove(varOut);
		
		// Choose random distributions
		HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > distributions = 
			new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
		for (String randVar : randVars) {
			AddableInteger[] dom = h1.getDomain(randVar);
			if (dom == null) 
				dom = new AddableInteger[] { new AddableInteger (0), new AddableInteger (1), new AddableInteger (2) };
			
			AddableReal[] probs = new AddableReal [dom.length];
			double norm = 0.0;
			for (int i = 0; i < dom.length; i++) {
				double prob = Math.random();
				norm += prob;
				probs[i] = new AddableReal(prob);
			}
			AddableReal div = new AddableReal (1/norm);
			for (int i = 0; i < dom.length; i++) 
				probs[i] = probs[i].multiply(div);
			
			distributions.put(randVar, 
					new Hypercube<AddableInteger, AddableReal> (new String[] {randVar}, new AddableInteger[][] {dom}, probs, null));
		}
		
		// Perform the normal consensus operation
		ProjOutput<AddableInteger, AddableReal> projOutput1 = 
				expect ? h1.consensusExpect(varOut, distributions, maximize) : h1.consensus(varOut, distributions, maximize);
		
		// Check the variables in the projOutput
		if (h1.getDomain(varOut) != null) {
			assertTrue (Arrays.asList(projOutput1.getVariables()).equals(Arrays.asList(new String[] { varOut })));
		} else 
			assertTrue (projOutput1.getVariables().length == 0);
		
		// Check that the conditional optimal assignments are consistent with the first output 
		UtilitySolutionSpace<AddableInteger, AddableReal> composition = h1.compose(new String[] { varOut }, projOutput1.assignments);
		if (expect) 
			composition = composition.expectation(distributions);
		assertTrue (composition + " != " + projOutput1.space + "\ndistributions: " + distributions + "\nvar out: " + varOut, 
				composition.getNumberOfVariables() == projOutput1.space.getNumberOfVariables());
		Iterator<AddableInteger, AddableReal> iter1 = composition.iterator();
		Iterator<AddableInteger, AddableReal> iter2 = projOutput1.space.iterator(composition.getVariables());
		while (iter1.hasNext()) 
			assertTrue (iter1.getCurrentUtility() + " != " + iter2.getCurrentUtility() + "\n" + composition + " != " + projOutput1.space, 
					iter1.nextUtility() != null && iter1.getCurrentUtility().equals(iter2.nextUtility(), 1E-6));
		
		// Perform the advanced consensus operation
		ProjOutput<AddableInteger, AddableReal> projOutput2 = h1.consensusAllSols(varOut, distributions, maximize);
		BasicUtilitySolutionSpace< AddableInteger, ArrayList<AddableInteger> > sol = projOutput2.assignments;
		
		// Check the variables in the projOutput
		if (h1.getDomain(varOut) != null) {
			assertTrue (Arrays.asList(projOutput2.getVariables()).equals(Arrays.asList(new String[] { varOut })));
		} else 
			assertTrue (projOutput2.getVariables().length == 0);
		
		// Check that the conditional optimal assignments are consistent with the first output 
		assertTrue (h1.compose(new String[] { varOut }, sol).equivalent(projOutput2.space));
		
		
		// Check the correctness of the conditional optimal assignments
		
		if (h1.getDomain(varOut) == null) {
			assertTrue (sol == Hypercube.NullHypercube.NULL);
			return;
		}
		
		// Compute the joint probability of the random variables
		UtilitySolutionSpace<AddableInteger, AddableReal> probs = new ScalarHypercube<AddableInteger, AddableReal> (new AddableReal(1.0), null, new AddableInteger [0].getClass());
		for (String randVar : h1.getVariables()) {
			UtilitySolutionSpace<AddableInteger, AddableReal> prob = distributions.get(randVar);
			if (prob != null) 
				probs = probs.multiply(prob);
		}
		
		// Reorder the variables in probs to match the order in h1
		ArrayList<String> newOrder = new ArrayList<String> ();
		for (String var : h1.getVariables()) 
			if (probs.getDomain(var) != null) 
				newOrder.add(var);
		String[] newOrderArray = newOrder.toArray(new String [newOrder.size()]);
		probs = probs.changeVariablesOrder(newOrderArray);
		
		// Initialize the current assignments to the remaining variables
		int nbrRemainingVars = sol.getNumberOfVariables();
		int[] valIndexes = new int [nbrRemainingVars]; // indexes of the current variable assignments
		AddableInteger[][] assignments = new AddableInteger [nbrRemainingVars][];
		for (int i = 0; i < nbrRemainingVars; i++) 
			assignments[i] = new AddableInteger[] { sol.getDomain(i)[0] };

		// Go through all combinations of assignments to the remaining variables
		while (true) {
			
			// Slice h1 over the current assignments, if any variable remains
			UtilitySolutionSpace<AddableInteger, AddableReal> h1Sliced = h1;
			if (nbrRemainingVars != 0) 
				h1Sliced = h1.slice(sol.getVariables(), assignments);
			
			// Look up the chosen value
			AddableInteger chosen = sol.slice(sol.getVariables(), assignments).getUtility(0).get(0);
			
			// For each scenario, compute the optimal utility
			UtilitySolutionSpace<AddableInteger, AddableReal> optUtil = h1Sliced.blindProject(varOut, maximize).resolve();
			
			// For each possible value of varOut, compute the number of (weighted) times it is optimal
			HashMap<AddableInteger, AddableReal> counts = new HashMap<AddableInteger, AddableReal> ();
			for (AddableInteger val : h1.getDomain(varOut)) 
				counts.put(val, new AddableReal (0.0));
			for (AddableInteger val : h1.getDomain(varOut)) {
				
				// Slice h1Sliced over varOut = val
				BasicUtilitySolutionSpace<AddableInteger, AddableReal> h1Sliced2 = h1Sliced.slice(varOut, val);
				
				// For each scenario, if val is optimal, increment its counter with the probability of the scenario
				for (int i = 0; i < h1Sliced2.getNumberOfSolutions(); i++) 
					if (optUtil.getUtility(i).equals(h1Sliced2.getUtility(i))) 
						counts.put(val, counts.get(val).add(probs.getUtility(i)));
			}
			
			// Check that the chosen value has the highest counter
			AddableReal chosenCount = counts.get(chosen);
			for (Map.Entry<AddableInteger, AddableReal> entry : counts.entrySet()) 
				assertTrue (chosen + "'s count " + chosenCount + " < " + entry.getKey() + "'s count " + entry.getValue(), 
						entry.getValue().compareTo(chosenCount) <= 0);			
			
			// Move to the next assignment for the remaining variables
			int varIndex = 0;
			for ( ; varIndex < nbrRemainingVars; varIndex++) {
				
				// Check if we have exhausted all values in the domain of the varIndex'th variable
				AddableInteger[] dom = sol.getDomain(varIndex);
				if (valIndexes[varIndex] == dom.length - 1) {
					
					// Reset the variable to its first domain value, and increment the next variable
					valIndexes[varIndex] = 0;
					assignments[varIndex] = new AddableInteger[] { dom[0] };
				}
				
				else { // increment the value for this variable
					valIndexes[varIndex]++;
					assignments[varIndex] = new AddableInteger[] { dom[ valIndexes[varIndex] ] };
					break;
				}
			}
			
			if (varIndex >= nbrRemainingVars) // we have exhausted all combinations
				break;
		}
		
	}
	
	/** Test for the iterator() method that takes in a list of variables not necessarily containing all the space's variables */
	public void testIterator () {
		
		Hypercube<AddableInteger, AddableInteger> hyper = random_hypercube();
		Hypercube<AddableInteger, AddableInteger> tmp = random_hypercube();
		String[] inputVars = tmp.getVariables();
		Iterator<AddableInteger, AddableInteger> iter = hyper.iterator(inputVars, tmp.getDomains());
		
		// Check that the order of variables is consistent with the input order
		String[] iterVars = iter.getVariablesOrder();
		for (int i = 0; i < inputVars.length; i++) 
			assertTrue (inputVars[i].equals(iterVars[i]));
		
		/// @todo Further tests could be useful. 
	}
}
