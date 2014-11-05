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

package frodo2.solutionSpaces.vehiclerouting.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.adopt.ADOPTsolver;
import frodo2.algorithms.afb.AFBsolver;
import frodo2.algorithms.asodpop.ASODPOPBinarysolver;
import frodo2.algorithms.asodpop.ASODPOPsolver;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.privacy.P2_DPOPsolver;
import frodo2.algorithms.dpop.privacy.P_DPOPsolver;
import frodo2.algorithms.localSearch.dsa.DSAsolver;
import frodo2.algorithms.localSearch.mgm.MGMsolver;
import frodo2.algorithms.localSearch.mgm.mgm2.MGM2solver;
import frodo2.algorithms.maxsum.MaxSumSolver;
import frodo2.algorithms.odpop.ODPOPsolver;
import frodo2.algorithms.synchbb.SynchBBsolver;
import frodo2.benchmarks.vehiclerouting.CordeauToXCSP;
import frodo2.benchmarks.vehiclerouting.XCSPparserVRP;
import frodo2.benchmarks.vehiclerouting.XCSPparserVRPODPOP;
import frodo2.solutionSpaces.AddableBigDecimal;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.vehiclerouting.VehicleRoutingSpace;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit tests for the Vehicle Routing Problem benchmarks
 * @author Thomas Leaute
 * @note Disable asserts if you don't want to see algorithms disagree or even fail because of floating point errors. 
 */
public class VRPtests extends TestCase {
	
	/** The overall problem */
	private Document problem;
	
	/** Path to the Cordeau problem file */
	final private String filePath;

	/** The visibility radius around any given depot */
	final private float radius;
	
	/** If positive, allow split deliveries, with a minimum split size of minSplit */
	private final int minSplit;
	
	/** Constructs the test suite 
	 * @return the test suite
	 */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("Tests for the Vehicle Routing Problem benchmarks");
		
		String probName = "p01";
		String probFile = VRPtests.class.getResource(probName).getFile();
		TestSuite tmp = new TestSuite ("Varying radius for " + probName);
		for (float radius = 13; radius <= 14; radius++) 
			tmp.addTest(new VRPtests(probFile, radius, 0));
		suite.addTest(tmp);
		
		probName = "p01";
		probFile = VRPtests.class.getResource(probName).getFile();
		tmp = new TestSuite ("Varying radius for " + probName + " with split deliveries");
		for (float radius = 13; radius <= 14; radius++) 
			tmp.addTest(new VRPtests(probFile, radius, 5));
		suite.addTest(tmp);
		
		probName = "p11";
		probFile = VRPtests.class.getResource(probName).getFile();
		tmp = new TestSuite ("Varying radius for " + probName);
		for (float radius = 21; radius <= 27; radius++) 
			tmp.addTest(new VRPtests(probFile, radius, 0));
		suite.addTest(tmp);
		
		probName = "p11";
		probFile = VRPtests.class.getResource(probName).getFile();
		tmp = new TestSuite ("Varying radius for " + probName + " with split deliveries");
		for (float radius = 21; radius <= 27; radius++) 
			tmp.addTest(new VRPtests(probFile, radius, 5));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Testing the best first iterator");
		tmp.addTest(new RepeatedTest (new VRPtests("testBestFirstiterator", probFile, 21, 0), 10)); /// @todo Test with split deliveries
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Testing the compound best first iterator");
		tmp.addTest(new RepeatedTest (new VRPtests("testCompoundBestFirstIterator", probFile, 21, 0), 10)); /// @todo Test with split deliveries
		suite.addTest(tmp);
		
		return suite;
	}

	/** Constructor 
	 * @param filePath 			path to the Cordeau problem file
	 * @param radius 			the visibility radius around any given depot
	 * @param minSplit 			If positive, allow split deliveries, with a minimum split size of minSplit
	 */
	public VRPtests(String filePath, float radius, int minSplit) {
		this("test", filePath, radius, minSplit);
	}

	/** Constructor
	 * @param name				the name of the test to be performed 
	 * @param filePath 			path to the Cordeau problem file
	 * @param radius 			the visibility radius around any given depot
	 * @param minSplit 			If positive, allow split deliveries, with a minimum split size of minSplit
	 */

	public VRPtests(String name, String filePath, float radius, int minSplit) {
		super(name);
		this.filePath = filePath;
		this.radius = radius;
		this.minSplit = minSplit;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	@Override
	protected void setUp () throws Exception {
		
		// Create the XCSP problem description
		CordeauToXCSP converter = new CordeauToXCSP (filePath);
		converter.parse(new BufferedReader (new FileReader (filePath)), radius, 0);
		this.problem = converter.createXCSP(false, this.minSplit, 0); /// @todo Test with uncertainty
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		this.problem = null;
	}

	/** Tests various algorithms against each other 
	 * @throws Exception 		if an error occurs
	 */
	public void test () throws Exception {
		
		// The timeout, in ms
		long timeout = 10000L;
		
		// Solve with DPOP
		Document agentDoc = XCSPparser.parse("src/frodo2/algorithms/dpop/DPOPagentVRP.xml", false);
		Solution<AddableInteger, AddableReal> dpopSolReal = new DPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("DPOP failed to find a solution", dpopSolReal);
		DPOPsolver<AddableInteger, AddableBigDecimal> dpopSolverBigDecimal = new DPOPsolver<AddableInteger, AddableBigDecimal> (agentDoc);
		dpopSolverBigDecimal.setUtilClass(AddableBigDecimal.class);
		Solution<AddableInteger, AddableBigDecimal> dpopSolBigDecimal = dpopSolverBigDecimal.solve(problem, timeout);
		assertNotNull("DPOP failed to find a solution", dpopSolBigDecimal);
		AddableReal dpopUtilReal = dpopSolReal.getUtility();
		AddableBigDecimal dpopUtilBigDecimal = dpopSolBigDecimal.getUtility();
		
		// Solve with MB-DPOP
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/dpop/memory/MB-DPOPagentVRP.xml", false);
		Solution<AddableInteger, AddableReal> solReal = new DPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("MB-DPOP failed to find a solution", solReal);
		
		// Solve with DSA
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/localSearch/dsa/DSAagentVRP.xml", false);
		solReal = new DSAsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("DSA failed to find a solution", solReal);
		
		// Solve with MGM
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/localSearch/mgm/MGMagentVRP.xml", false);
		solReal = new MGMsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("MGM failed to find a solution", solReal);
		
		// Solve with MGM2
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/localSearch/mgm/mgm2/MGM2agentVRP.xml", false);
		solReal = new MGM2solver<AddableInteger, AddableReal> (agentDoc).solve(problem, 10 * timeout);
		assertNotNull("MGM2 failed to find a solution", solReal);
		
		// Solve with Max-Sum
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/maxsum/MaxSumAgentVRP.xml", false);
		solReal = new MaxSumSolver<AddableInteger, AddableReal> (agentDoc).solve(problem, 10* timeout);
		assertNotNull("Max-Sum failed to find a solution", solReal);
		
		// Compare with SynchBB
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/synchbb/SynchBBagentVRP.xml", false);
		solReal = new SynchBBsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("SynchBB failed to find a solution", solReal);
		AddableReal utilReal = solReal.getUtility();
//		assertTrue( "DPOP and SynchBB disagree: " + dpopUtil + " != " + synchBButil, dpopUtil.equals(synchBButil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and SynchBB disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with AFB
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/afb/AFBagentVRP.xml", false);
		solReal = new AFBsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("AFB failed to find a solution", solReal);
		utilReal = solReal.getUtility();
//		assertTrue( "DPOP and AFB disagree: " + dpopUtil + " != " + synchBButil, dpopUtil.equals(synchBButil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and AFB disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with P-DPOP
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/dpop/privacy/P-DPOPagentVRP.xml", false);
		solReal = new P_DPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("P-DPOP failed to find a solution", solReal);
		utilReal = solReal.getUtility();
//		assertTrue( "DPOP and P-DPOP disagree: " + dpopUtil + " != " + pdpopUtil, dpopUtil.equals(synchBButil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and P-DPOP disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with P2-DPOP
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/dpop/privacy/P2-DPOPagentVRP.xml", false);
		solReal = new P2_DPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout * 12, (int) dpopSolReal.getUtility().doubleValue() + 1);
		assertNotNull("P2-DPOP failed to find a solution", solReal);
		utilReal = solReal.getUtility();
//		assertTrue( "DPOP and P2-DPOP disagree: " + dpopUtil + " != " + p2dpopUtil, dpopUtil.equals(synchBButil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and P2-DPOP disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with ADOPT
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/adopt/ADOPTagentVRP.xml", false);
		solReal = new ADOPTsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("ADOPT failed to find a solution", solReal);
		utilReal = solReal.getUtility();
//		assertTrue( "DPOP and ADOPT disagree: " + dpopUtil + " != " + adoptUtil, dpopUtil.equals(adoptUtil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and ADOPT disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with O-DPOP
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/odpop/ODPOPagentVRP.xml", false);
		solReal = new ODPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("ODPOP failed to find a solution", solReal);
		utilReal = solReal.getUtility();
//		assertTrue( "DPOP and ODPOP disagree: " + dpopUtil + " != " + odpopUtil, dpopUtil.equals(odpopUtil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and ODPOP disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with O-DPOP Binary Domains
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/odpop/ODPOPagentBinaryDomainsVRP.xml", false);
		solReal = new ODPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("ODPOPBinary failed to find a solution", solReal);
		utilReal = solReal.getUtility();
//		assertTrue( "DPOP and ODPOPBinary disagree: " + dpopUtil + " != " + odpopBinaryUtil, dpopUtil.equals(odpopBinaryUtil, 1E-6));
		assert dpopSolReal.getAssignments().equals(solReal.getAssignments()) || dpopUtilReal.equals(utilReal, 1E-6) : "DPOP and ODPOPBinary disagree: " + dpopUtilReal + " != " + utilReal;
		
		// Compare with ASO-DPOP
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/asodpop/ASODPOPagentVRP.xml", false);
		Solution<AddableInteger, AddableBigDecimal> solBigDecimal = new ASODPOPsolver<AddableInteger, AddableBigDecimal> (agentDoc).solve(problem, timeout);
		assertNotNull("ASODPOP failed to find a solution", solBigDecimal);
		AddableBigDecimal utilBigDecimal = solBigDecimal.getUtility();
//		assertTrue( "DPOP and ASODPOP disagree: " + dpopUtil + " != " + asodpopUtil, dpopUtil.equals(asodpopUtil, 1E-6));
		assert dpopSolBigDecimal.getAssignments().equals(solBigDecimal.getAssignments()) || dpopUtilBigDecimal.equals(utilBigDecimal, 1E-6) : "DPOP and ASODPOP disagree: " + dpopUtilReal + " != " + utilBigDecimal;
		
		// Compare with ASO-DPOP Binary Domains
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/asodpop/ASODPOPBinaryagentVRP.xml", false);
		solBigDecimal = new ASODPOPBinarysolver<AddableInteger, AddableBigDecimal> (agentDoc).solve(problem, timeout);
		assertNotNull("ASODPOPBinary failed to find a solution", solBigDecimal);
		utilBigDecimal = solBigDecimal.getUtility();
//		assertTrue( "DPOP and ASODPOPBinary disagree: " + dpopUtil + " != " + asodpopBinaryUtil, dpopUtil.equals(asodpopBinaryUtil, 1E-6));
		assert dpopSolBigDecimal.getAssignments().equals(solBigDecimal.getAssignments()) || dpopUtilBigDecimal.equals(utilBigDecimal, 1E-6) : "DPOP and ASODPOPBinary disagree: " + dpopUtilReal + " != " + utilBigDecimal;
		
		// Solve with DPOP and ignore Hypercube NCCCs
		agentDoc = XCSPparser.parse("src/frodo2/algorithms/dpop/DPOPagentVRP.xml", false);
		Element parserElmt = agentDoc.getRootElement().getChild("parser");
		parserElmt.setAttribute("countNCCCs", "true");
		dpopSolReal = new DPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		Element ignore = new Element("ignoreNCCCs");
		Element spaceToIgnore = new Element("space");
		spaceToIgnore.addContent("frodo2.solutionSpaces.hypercube.Hypercube");
		ignore.addContent(spaceToIgnore);
		parserElmt.addContent(ignore);
		Solution<AddableInteger, AddableReal> dpopSolReal2 = new DPOPsolver<AddableInteger, AddableReal> (agentDoc).solve(problem, timeout);
		assertNotNull("DPOP failed to find a solution", dpopSolReal);
		assert dpopSolReal.getNcccCount() > dpopSolReal2.getNcccCount() : dpopSolReal.getNcccCount() + " > " + dpopSolReal2.getNcccCount();
	}
	
	/**
	 * Test the best first iterator
	 * @author Brammert Ottens, 29 apr 2010
	 * @throws InstantiationException	error thrown when creating a new instance of an object fails
	 * @throws IllegalAccessException	error thrown when creating a new instance of an object fails
	 */
	public void testBestFirstiterator() throws InstantiationException, IllegalAccessException {
		
		XCSPparserVRP<AddableReal> parser = new XCSPparserVRP<AddableReal>(problem);
		parser.setUtilClass(AddableReal.class);
		List<? extends UtilitySolutionSpace<AddableInteger, AddableReal>> spaces = parser.getSolutionSpaces();
		
		int i = 0;
		while(!(spaces.get(i) instanceof VehicleRoutingSpace))
			i++;
		
		Iterator<AddableInteger, AddableReal> it = spaces.get(i).iteratorBestFirst(false); 
		
		AddableReal lastUtil = AddableReal.class.newInstance().getMinInfinity();
		
		while(it.hasNext()) {
			AddableReal util = it.nextUtility();
			assert lastUtil.compareTo(util) <= 0;
			lastUtil = util;
			AddableReal realUtil = spaces.get(i).getUtility(it.getCurrentSolution());
			assert lastUtil.compareTo(realUtil) <= 0.1 || lastUtil.compareTo(realUtil) >= 0.1;
		}
	}
	
	/**
	 * Test the best first iterator of the compound space
	 * @author Brammert Ottens, 29 apr 2010
	 * @throws InstantiationException	error thrown when creating a new instance of an object fails
	 * @throws IllegalAccessException	error thrown when creating a new instance of an object fails
	 */
	@SuppressWarnings("unchecked")
	public void testCompoundBestFirstIterator() throws InstantiationException, IllegalAccessException {
		XCSPparserVRPODPOP<AddableReal> parser = new XCSPparserVRPODPOP<AddableReal>(problem);
		parser.setUtilClass(AddableReal.class);
		
		String[] variables = parser.getVariables().toArray(new String[0]);
		String var = variables[(int)(Math.random()*variables.length)];
				
		List<? extends UtilitySolutionSpace<AddableInteger, AddableReal>> spaces = parser.getSolutionSpaces(var);
		
		UtilitySolutionSpace<AddableInteger, AddableReal> space = spaces.remove(0);
		space = space.join(spaces.toArray(new UtilitySolutionSpace[0]));
		
		
		Iterator<AddableInteger, AddableReal> it = space.iteratorBestFirst(false); 
		
		AddableReal lastUtil = AddableReal.class.newInstance().getMinInfinity();
		
		while(it.hasNext()) {
			AddableReal util = it.nextUtility();
			assert lastUtil.compareTo(util) <= 0;
			lastUtil = util;
			AddableReal realUtil = space.getUtility(it.getCurrentSolution());
			assert lastUtil.compareTo(realUtil) <= 0.1 || lastUtil.compareTo(realUtil) >= 0.1;
		}
	}
	
}
