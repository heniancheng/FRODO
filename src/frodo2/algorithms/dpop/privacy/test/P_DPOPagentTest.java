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

package frodo2.algorithms.dpop.privacy.test;

import java.io.IOException;
import java.util.List;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.privacy.P_DPOPsolver;
import frodo2.algorithms.dpop.privacy.P3halves_DPOPsolver;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;

/**
 * JUnit test for P_DPOP agent
 * @param <V> the type used for variable values
 * @author Eric Zbinden, Thomas Leaute
 */
public class P_DPOPagentTest <V extends Addable<V> > extends TestCase {
	
	
	/**
	 * The maximum number of variables in this problem
	 */
	private int maxVar = 5;
	
	/**
	 * The maximum number of agents in this problem
	 */
	private int maxAgent = 5;
	
	/**
	 * The maximum number of constraints in this problem
	 */
	private int maxEdge = 10;
	
	/** Whether to maximize or minimize */
	private boolean maximize;
	
	/** The class used for variable values */
	private Class<V> domClass;
	
	/** Whether to use TCP pipes */
	private final boolean useTCP;
	
	/**
	 * Constructor
	 * @param method 	test method 
	 * @param maximize 	Whether to maximize or minimize
	 * @param domClass 	the class to be used for variable values
	 * @param useTCP 	Whether to use TCP pipes
	 */
	public P_DPOPagentTest(String method, boolean maximize, Class<V> domClass, boolean useTCP) {
		super(method);
		this.maximize = maximize;
		this.domClass = domClass;
		this.useTCP = useTCP;
	}

	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for P_DPOP agent");
		
		TestSuite testTmp = new TestSuite ("Tests for P_DPOP vs DPOP maximizing");
		testTmp.addTest(new RepeatedTest (new P_DPOPagentTest<AddableInteger> ("testP_DPOPvsDPOP", true, AddableInteger.class, false), 250));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P_DPOP vs DPOP maximizing using TCP pipes");
		testTmp.addTest(new RepeatedTest (new P_DPOPagentTest<AddableInteger> ("testP_DPOPvsDPOP", true, AddableInteger.class, true), 250));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P_DPOP vs DPOP maximizing with real-valued variables");
		testTmp.addTest(new RepeatedTest (new P_DPOPagentTest<AddableReal> ("testP_DPOPvsDPOP", true, AddableReal.class, false), 250));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P_DPOP vs DPOP minimizing");
		testTmp.addTest(new RepeatedTest (new P_DPOPagentTest<AddableInteger> ("testP_DPOPvsDPOP", false, AddableInteger.class, false), 500));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/**
	 * Test whenever P-DPOP's and DPOP's answers to a random problem are equivalent
	 * @throws IOException is thrown if an I/O exception occur when accessing to the description of P-DPOP or DPOP algorithm
	 * @throws JDOMException is thrown if a parsing error occurs
	 */
	public void testP_DPOPvsDPOP () throws JDOMException, IOException {
		
		//Create new random problem
		Document problem = AllTests.createRandProblem(maxVar, maxEdge, maxAgent, this.maximize);
		XCSPparser<V, AddableInteger> parser = new XCSPparser<V, AddableInteger> (problem);
		int nbrVars = parser.getNbrVars();
		
		//Compute both solutions
		Solution<V, AddableInteger> dpopSolution = new DPOPsolver<V, AddableInteger>(this.domClass, AddableInteger.class).solve(problem, nbrVars);
		Solution<V, AddableInteger> p_dpopSolution = new P_DPOPsolver<V, AddableInteger>(this.domClass, AddableInteger.class, this.useTCP).solve(problem, nbrVars);
		
		//Verify the utilities of the solutions found by P-DPOP and DPOP
		assertNotNull ("timeout", p_dpopSolution);
		assertEquals("P-DPOP's and DPOP's utilities are different", dpopSolution.getUtility(), p_dpopSolution.getUtility());
		
		// Verify that P-DPOP's chosen assignments indeed have the correct utility
		assertEquals("The chosen assignments' utility differs from the reported utility", p_dpopSolution.getUtility().toString(), p_dpopSolution.getReportedUtil().toString());
		
		
		// Do the same with P-DPOP with rerooting
		Document agentDesc = XCSPparser.parse(P_DPOPagentTest.class.getResourceAsStream("/frodo2/algorithms/dpop/privacy/P1.5-DPOPagent.xml"), false);
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(DFSgenerationWithOrder.class.getName())) 
				module.setAttribute("minIncr", "2");
		p_dpopSolution = new P3halves_DPOPsolver<V, AddableInteger>(agentDesc, this.domClass, AddableInteger.class, this.useTCP).solve(problem, nbrVars, 60000L);
		assertNotNull ("timeout", p_dpopSolution);
		assertEquals("P-DPOP's and DPOP's utilities are different", dpopSolution.getUtility(), p_dpopSolution.getUtility());
		assertEquals("The chosen assignments' utility differs from the reported utility", p_dpopSolution.getUtility().toString(), p_dpopSolution.getReportedUtil().toString());
	}

}
