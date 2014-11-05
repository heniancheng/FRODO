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

package frodo2.algorithms.dpop.stochastic.test;

import java.util.HashMap;
import java.util.List;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.dpop.param.ParamDPOPsolver;
import frodo2.algorithms.dpop.stochastic.E_DPOPsolver;
import frodo2.algorithms.dpop.stochastic.SamplingPhase;
import frodo2.algorithms.dpop.stochastic.E_DPOPsolver.StochSolution;
import frodo2.algorithms.dpop.stochastic.ExpectedUTIL;
import frodo2.algorithms.dpop.test.DPOPagentTest;
import frodo2.algorithms.test.AllTests;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.mailer.CentralMailer;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A JUnit test case for E[DPOP]
 * @param <V> the type used for variable values
 * @author Thomas Leaute
 */
public class E_DPOPagentTest < V extends Addable<V> > extends DPOPagentTest<V, AddableReal> {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 6;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 6;

	/** The number of samples */
	protected int nbrSamples;

	/** Either "expectation", "consensus" or "consensusAllSols" */
	private String method;

	/** Either "AtRoots", "AtLCAs" or "AtLeaves" */
	protected String whereToSample;

	/** Either "roots", "lcas" or "leaves" */
	protected String whereToProject;
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param whereToSample 		either "AtRoots", "AtLCAs" or "AtLeaves"
	 * @param whereToProject 		either "roots", "lcas" or "leaves"
	 * @param method 				either "expectation", "consensus" or "consensusAllSols"
	 * @param useTCP 				whether TCP pipes should be used for communication between agents
	 * @param useCentralMailer 		\c true when the central mailer should be used
	 * @param nbrSamples 			the desired number of samples; if zero, no sampling is performed
	 * @param startMsgType 			the type of the start message
	 * @param domClass 				the class used for variable values
	 */
	public E_DPOPagentTest(String whereToSample, String whereToProject, String method, boolean useTCP, boolean useCentralMailer, int nbrSamples, String startMsgType, Class<V> domClass) {
		super(true, useTCP, useCentralMailer, false, domClass, AddableReal.class, startMsgType);
		this.nbrSamples = nbrSamples;
		this.method = method;
		this.whereToSample = whereToSample;
		this.whereToProject = whereToProject;
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for E-DPOP");
		
		// Tests for Local-E[DPOP] (sampling at the lcas, projecting at the leaves)
		
		TestSuite subSuite = new TestSuite ("Tests for Local-E-DPOP");
		suite.addTest(subSuite);
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectationMonotone", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtLCAs", "leaves", "expectation", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, true, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", true, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectationMonotone", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, true, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", true, false, 2, null, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectationMonotone", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, true, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", true, false, 100000, null, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and a different type for the start message");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "expectation", false, false, 0, "START NOW!", AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		
		// Tests for Simple-E[DPOP] (sampling and projecting at the leaves)
		
		subSuite = new TestSuite ("Tests for Simple-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "expectation", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "expectationMonotone", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtLeaves", "leaves", "expectation", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "expectation", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "expectationMonotone", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "expectation", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "expectationMonotone", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Global-E[DPOP] (sampling and projecting at the lcas)
		
		subSuite = new TestSuite ("Tests for Global-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "expectation", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "expectationMonotone", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtLCAs", "lcas", "expectation", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "expectation", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "expectationMonotone", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "expectation", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "expectationMonotone", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Central-E[DPOP] (sampling and projecting at the roots)
		
		subSuite = new TestSuite ("Tests for Central-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "expectation", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "expectationMonotone", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtRoots", "roots", "expectation", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "expectation", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling and the expectationMonotone method");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "expectationMonotone", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "expectation", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the expectationMonotone");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "expectationMonotone", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Cons-Local-E[DPOP] (sampling at the lcas, projecting at the leaves)
		
		subSuite = new TestSuite ("Tests for Cons-Local-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "consensus", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtLCAs", "leaves", "consensus", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "consensus", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "leaves", "consensus", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Cons-Simple-E[DPOP] (sampling and projecting at the leaves)
		
		subSuite = new TestSuite ("Tests for Cons-Simple-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "consensus", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtLeaves", "leaves", "consensus", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "consensus", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLeaves", "leaves", "consensus", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Cons-Global-E[DPOP] (sampling and projecting at the lcas)
		
		subSuite = new TestSuite ("Tests for Cons-Global-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "consensus", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtLCAs", "lcas", "consensus", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "consensus", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtLCAs", "lcas", "consensus", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Cons-Central-E[DPOP] (sampling and projecting at the roots)
		
		subSuite = new TestSuite ("Tests for Cons-Central-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "consensus", false, false, 0, null, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableReal> ("AtRoots", "roots", "consensus", false, false, 0, null, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and low sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "consensus", false, false, 2, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new E_DPOPagentTest<AddableInteger> ("AtRoots", "roots", "consensus", false, false, 100000, null, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		return suite;
	}
	
	/** @see DPOPagentTest#setUp() */
	@Override
	public void setUp () throws Exception {

		agentConfig = XCSPparser.parse("src/frodo2/algorithms/dpop/stochastic/E-DPOP.xml", false);
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
			
			String className = module.getAttributeValue("className");
			if (className.startsWith(SamplingPhase.class.getName())) {
				module.setAttribute("className", SamplingPhase.class.getName() + "$" + whereToSample);
				module.setAttribute("nbrSamples", String.valueOf(nbrSamples));
				module.setAttribute("whereToProject", whereToProject);
				
			} else if (className.equals(ExpectedUTIL.class.getName())) {
				module.setAttribute("method", method);
			}
		}
		
		super.setStartMsgType(startMsgType);
		
		nbrMsgsReceived = 0;
		nbrAgentsFinished = 0;
		
		// Create the queue
		if (this.useCentralMailer) {
			mailman = new CentralMailer (false, false, null);
			this.queue = mailman.newQueue(AgentInterface.STATS_MONITOR);
		} else 
			queue = new Queue (false);
		
		queue.addIncomingMessagePolicy(this);
		pipes = new HashMap<Object, QueueOutputPipeInterface> ();
		if (this.nbrSamples < 3) { // low sampling
			// If an agent's variables can be in two different constraint graph components, 
			// the components may choose inconsistent samples for random variables, which we want to avoid
			graph = RandGraphFactory.getNiceRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		} else 
			graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problemDoc = AllTests.generateProblem(graph, maxNbrVars, true, (this.method.equals("expectationMonotone") ? -1 : 0));
		problem = new XCSPparser<V, AddableReal> (problemDoc);
		problem.setDomClass(super.domClass);
		problem.setUtilClass(AddableReal.class);
		utilModule = new ExpectedUTIL<V, AddableReal> (null, problem);
		utilModule.setSilent(true);
		utilModule.getStatsFromQueue(queue);
		valueModule = new VALUEpropagation<V> (null, problem);
		valueModule.setSilent(true);
		valueModule.getStatsFromQueue(queue);
	}
	
	/** Checks that the optimal expected utility computed by E[DPOP] is lower
	 * than the expectation of the optimal conditional utility computed by Param-DPOP. 
	 * 
	 * Also checks that all versions (expectation-based) E[DPOP] without sampling agree. 
	 * @see DPOPagentTest#checkOutput() 
	 */
	@Override
	protected void checkOutput() throws Exception {
		
		if (! this.method.equals("expectation")) 
			return; /// @todo Test Consensus
		
		AddableReal optUtil = this.utilModule.getOptUtil();
		
		// If we are not doing any sampling, we can compare exactly against an equivalent algorithm
		if (this.nbrSamples == 0) {
			
			E_DPOPsolver<V, AddableReal> solver = 
				new E_DPOPsolver<V, AddableReal> (XCSPparser.parse("src/frodo2/algorithms/dpop/stochastic/E-DPOP.xml", false), super.domClass);
			AddableReal otherUtil = solver.solve(this.problemDoc).getReportedUtil();
			assertTrue(optUtil + " != " + otherUtil, optUtil.equals(otherUtil, 1E-6)); /// @bug Very rarely fails. 
		}
		
		else if (this.nbrSamples < 3) // low sampling; we cannot expect good solutions
			return;
		
		// Compute the expectation of the optimal parametric utility 
		ParamDPOPsolver<V, AddableReal> solver = new ParamDPOPsolver<V, AddableReal> (super.domClass, AddableReal.class);
		UtilitySolutionSpace<V, AddableReal> expOptUtil = solver.getExpectedOptUtil(problemDoc);
		
		// Check if they are equal
		if (! (expOptUtil.getUtility(0).compareTo(optUtil, 1E-6) >= 0)) { // the solutions differ; resolve with a much larger number of samples

			// Setup the agent description
			Document agentConfig2 = XCSPparser.parse("src/frodo2/algorithms/dpop/stochastic/E-DPOP.xml", false);
			for (Element module : (List<Element>) agentConfig2.getRootElement().getChild("modules").getChildren()) 
				if (module.getAttributeValue("className").equals(SamplingPhase.AtLCAs.class.getName())) 
					module.setAttribute("nbrSamples", "50000000");

			// Solve the problem again
			E_DPOPsolver<V, AddableReal> solver2 = new E_DPOPsolver<V, AddableReal> (agentConfig2, super.domClass);
			StochSolution<V, AddableReal> sol2 = solver2.solve(problemDoc);

			assertTrue (expOptUtil.getUtility(0) + " != " + sol2.getReportedUtil(), 
					expOptUtil.getUtility(0).compareTo(sol2.getReportedUtil(), 1E-2) >= 0); // 1E-2 to account for rare floating point errors
		}
				
	}
	
}
