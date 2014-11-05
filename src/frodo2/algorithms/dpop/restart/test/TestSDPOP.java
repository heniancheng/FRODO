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

/** Tests for the S-DPOP algorithm with warm restarts */
package frodo2.algorithms.dpop.restart.test;

import java.util.List;
import java.util.Random;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.test.AllTests;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;

/** JUnit tests for Util reuse in S-DPOP
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @author Jonas Helfer, Thomas Leaute
 */
public class TestSDPOP < V extends Addable<V>, U extends Addable<U> > extends TestCase {
	
	/** Maximum number of variables */
	public static int maxNbrVars = 10;
	
	/** Maximum number of edges in the primal constraint graph */
	public static int maxNbrEdges = 40;
	
	/** Maximum number of agents */
	public static int maxNbrAgents = 10;
	
	/** test type 
	 * @todo Use an enum instead */
	public static final int TEST_RANDOM_CHANGE = 1;
	/** test type */
	public static final int TEST_CHANGE_LEAF_NAME = 2;
	/** test type */
	public static final int TEST_ADD_ROOT_NEIGHBOR = 3;
	/** test type */
	public static final int TEST_ADD_DISCONNECTED = 4;
	
	/** Probability of changing any given relation when randomly modifying problem*/
	public static double changeRatio = 0.5;
	
	/** S-DPOP agent*/
	private Document SDPOPagent = null;
	
	/** DPOP agent*/
	private Document DPOPagent = null;
	
	/** DPOP solver*/
	private DPOPsolver<V, U> DPOPsolver = null;
	
	/** S-DPOP solver*/
	private DPOPsolver<V, U> SDPOPsolver = null;
	
	/** The problem*/
	private Document problem = null;

	/** The class used for variable values */
	private Class<V> domClass;

	/** The class used for utility values */
	private Class<U> utilClass;
	
	/** Constructor
	 * @param method 	the name of the test method
	 * @param domClass 	The class used for variable values
	 * @param utilClass The class used for utility values
	 */
	public TestSDPOP(String method, Class<V> domClass, Class<U> utilClass) {
		super(method);
		this.domClass = domClass;
		this.utilClass = utilClass;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () throws Exception {

		//generate agents:
		this.SDPOPagent = XCSPparser.parse(AgentFactory.class.getResourceAsStream("/frodo2/algorithms/dpop/restart/S-DPOPagent.xml"), false);
		this.DPOPagent = XCSPparser.parse(AgentFactory.class.getResourceAsStream("/frodo2/algorithms/dpop/DPOPagent.xml"), false);

		this.problem = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, true);;
		DPOPsolver = new DPOPsolver<V, U>(DPOPagent, this.domClass, this.utilClass);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown () {
		this.SDPOPagent = null;
		this.DPOPagent = null;
		this.problem = null;
		this.DPOPsolver = null;
		this.SDPOPsolver = null;
	}
	
	/** running the test by comparing Solution of S-DPOP to solution of DPOP*/
	public void testCorrectness() 
	{
		this.testGeneric(TEST_RANDOM_CHANGE);
	}
	
	/** provides a generic test method which takes the test type as argument
	 * 
	 * @param changeType specifies the type of change to be tested
	 * @return the solution of the S-DPOP execution
	 */
	public Solution<V, U> testGeneric(int changeType)
	{
		SDPOPsolver = new DPOPsolver<V, U>(SDPOPagent, this.domClass, this.utilClass);

		// Solve the random problem using DPOP and S-DPOP
		Solution<V, U> D1 = this.DPOPsolver.solve(this.problem, false);
		Solution<V, U> SD1 = this.SDPOPsolver.solve(this.problem,false);
		
		// Check that DPOP and S-DPOP agree on the utility, and that S-DPOP's assignments are consistent
		assertEquals (D1.getUtility(), SD1.getUtility());
		assertEquals(SD1.getUtility(), SD1.getReportedUtil());
		
		
		switch(changeType)
		{
			case TEST_RANDOM_CHANGE:
				this.problem = modifyProblemRandomly(problem, changeRatio);
				break;
			case TEST_ADD_ROOT_NEIGHBOR:
				this.problem = addRootNeighbor(problem);
				break;
			case TEST_ADD_DISCONNECTED:
				this.problem = addDisconnected(problem);
				break;
			case TEST_CHANGE_LEAF_NAME:
				this.problem = changeLeafName(problem);
				break;
			default:
				fail("unknown change type: " + changeType);
		}
		
		D1 = this.DPOPsolver.solve(this.problem, true);
		assertTrue ("DPOP timed out", D1 != null);
		SD1 = this.SDPOPsolver.solve(this.problem,true);
		assertTrue ("S-DPOP timed out", SD1 != null);
		assertEquals (D1.getUtility(), SD1.getUtility());
		assertEquals(SD1.getUtility(), SD1.getReportedUtil());
	
		//return the solution of S-DPOP for further assertions
		return SD1;
	}
	
	/** changes name of one leaf variable with no back-edges
	 * @param problem2 original problem
	 * @return modified problem
	 */
	private Document changeLeafName(Document problem2) {
		///@todo: for this I will need to identify leafs without roots, which is a bit tricky...
		return null;
	}

	/** adds one disconnected variable to the problem
	 * @param problem2 original problem
	 * @return modified problem
	 */
	private Document addDisconnected(Document problem2) {
		Element varElmt = problem2.getRootElement().getChild("variables");
		Element domains = problem2.getRootElement().getChild("domains");
		
		
		
		int nbVars = Integer.parseInt(varElmt.getAttributeValue("nbVariables"));
		int nbDomains = Integer.parseInt(domains.getAttributeValue("nbDomains"));
		
		//just add a stupid new var
		Element var = new Element("variable");
		String varname = "DC_test_" + (nbVars + 1);
		varElmt.addContent(var);
		var.setAttribute("name", varname);
		var.setAttribute("domain", "D_"+varname);
		var.setAttribute("agent", Integer.toString(nbVars + 1)); //this will create a new agent...

		//and give the new var a domain
		Element domain = new Element("domain");
		domains.addContent(domain);
		domain.setAttribute("name", "D_"+varname);
		domain.setAttribute("nbValues","2");
		domain.addContent("0 1");
		
		varElmt.setAttribute("nbVariables", Integer.toString(nbVars + 1));
		domains.setAttribute("nbDomains", Integer.toString(nbDomains + 1));
		
		return problem2;
	}

	/** adds a neighbor to the root of the problem
	 * @param problem2 original problem
	 * @return modified problem
	 */
	private Document addRootNeighbor(Document problem2) {
		///@todo for this I will need to know the (or a) root of the problem
		return null;
	}

	/** Add a disconnected variable to the problem, no UTIL message should be sent */
	public void testAddDisconnected()
	{
		//make sure msgs are measured
		this.SDPOPagent.getRootElement().setAttribute("measureMsgs","true");
		Solution<V, U> SD1 = this.testGeneric(TEST_ADD_DISCONNECTED);
		assertNull(SD1.getMsgNbrs().get("UTIL"));
	}
	
	/** adds a single variable to the root, only 1 util-msg should be sent */
	public void testAddOneToRoot()
	{
		this.testGeneric(TEST_ADD_ROOT_NEIGHBOR);
		///@todo assertions
	}
	
	/** change the name of a leaf variable with no backedges, only 1 util-msg should be sent */
	public void testChangeLeafVariableName()
	{
		this.testGeneric(TEST_CHANGE_LEAF_NAME);	
		///@todo assertions
	}
	
	/** Checks that no UTIL message is sent when the problem doesn't change */
	public void testReuse() {
		
		// Tell S-DPOP to measure messages
		this.SDPOPagent.getRootElement().setAttribute("measureMsgs", "true");
		SDPOPsolver = new DPOPsolver<V, U>(SDPOPagent, this.domClass, this.utilClass);
		
		// Solve the random problem using DPOP and S-DPOP
		Solution<V, U> D1 = this.DPOPsolver.solve(this.problem, true);
		assertTrue ("DPOP timed out", D1 != null);
		Solution<V, U> SD1 = this.SDPOPsolver.solve(this.problem,false);
		
		// Resolve the same problem
		SD1 = this.SDPOPsolver.solve(problem, true);
		assertTrue ("S-DPOP timed out", SD1 != null);
		assertEquals (D1.getUtility(), SD1.getUtility());
		assertEquals(SD1.getUtility(), SD1.getReportedUtil());
		
		// Check that no UTIL message was sent, since the problem hasn't changed
		assertNull(SD1.getMsgSizes().get("UTIL"));
	}
	
	/**@return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Tests for S-DPOP");
		
		TestSuite tmp = new TestSuite ("Correctness: solution same as DPOP");		
		tmp.addTest(new RepeatedTest (new TestSDPOP<AddableInteger, AddableInteger> ("testCorrectness", AddableInteger.class, AddableInteger.class), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Correctness: solution same as DPOP using real-valued variables");		
		tmp.addTest(new RepeatedTest (new TestSDPOP<AddableReal, AddableInteger> ("testCorrectness", AddableReal.class, AddableInteger.class), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Correctness: solution same as DPOP using real-valued utilities");		
		tmp.addTest(new RepeatedTest (new TestSDPOP<AddableInteger, AddableReal> ("testCorrectness", AddableInteger.class, AddableReal.class), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Reuse: no UTIL message sent when disconnected Var is added");		
		tmp.addTest(new RepeatedTest (new TestSDPOP<AddableInteger, AddableInteger> ("testAddDisconnected", AddableInteger.class, AddableInteger.class), 100));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Reuse: no UTIL message sent if the problem hasn't changed");		
		tmp.addTest(new RepeatedTest (new TestSDPOP<AddableInteger, AddableInteger> ("testReuse", AddableInteger.class, AddableInteger.class), 100));
		suite.addTest(tmp);
		
		///@todo: implement and add the rest of the tests
		
		return suite;
	}
	
	/**
	 * @param problem 	the initial problem
	 * @param nodeRatio probability of changing any given relation
	 * @return the modified problem
	 */
	public static Document modifyProblemRandomly(Document problem, double nodeRatio)
	{
		Random rnd = new Random();

		problem = (Document)problem.clone();
		if (nodeRatio <= 0) 
			return problem;
		
		List<Element> relations = problem.getRootElement().getChild("relations").getChildren();
		for(Element e: relations)
		{
			//only change utilities for a set percentage of nodes
			if(rnd.nextFloat() > nodeRatio) 
				continue;
			
			String[] tuples = e.getText().split("\\|");
			StringBuilder new_utilities = new StringBuilder ();
			for(int i = 0; i< tuples.length;i++)
			{
				String[] util = tuples[i].split(":");
				String edge = util[1];

				if(rnd.nextDouble() < 0.3)
					new_utilities.append("-infinity:" + edge);
				else
					new_utilities.append(rnd.nextInt(100) + ":" + edge);

				if(i < tuples.length - 1)
					new_utilities.append("|");

			}
			e.setText(new_utilities.toString());
		}

		return problem;
	}
}
