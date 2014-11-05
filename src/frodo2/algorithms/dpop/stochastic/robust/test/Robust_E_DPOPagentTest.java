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

/** Unit tests for Robust-E[DPOP] */
package frodo2.algorithms.dpop.stochastic.robust.test;

import java.util.HashMap;
import java.util.List;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.stochastic.CompleteUTIL;
import frodo2.algorithms.dpop.stochastic.ExpectedUTIL;
import frodo2.algorithms.dpop.stochastic.ExpectedUTIL.Method;
import frodo2.algorithms.dpop.stochastic.SamplingPhase;
import frodo2.algorithms.dpop.stochastic.test.E_DPOPagentTest;
import frodo2.algorithms.test.AllTests;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.mailer.CentralMailer;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;

/** Unit tests for Robust-E[DPOP]
 * @param <V> the type used for variable values
 * @author Thomas Leaute
 * @bug There seem to be interferences between consecutive tests, producing timeouts, "Non-monotone problem" AssertionErrors and "address already in use" errors
 */
public class Robust_E_DPOPagentTest < V extends Addable<V> > extends E_DPOPagentTest<V> {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 6;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 6;

	/** The path to the agent configuration file */
	private final String agentFile;
	
	/** The method to use */
	private final Method method;
	
	/** @return a suite of random tests */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for Robust-E-DPOP");
		
		// Tests for Robust-Complete-E[DPOP]
		
		TestSuite subSuite = new TestSuite ("Tests for Robust-Complete-E-DPOP");
		suite.addTest(subSuite);
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, false, true, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, true, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, false, true, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.ROBUST, true, false, 2, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		
		// Tests for Exp-Complete-E[DPOP]
		
		subSuite = new TestSuite ("Tests for Exp-Complete-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, false, false, 0, AddableInteger.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, false, false, 0, AddableReal.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, false, true, 0, AddableInteger.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, true, false, 0, AddableInteger.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, false, true, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION, true, false, 2, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		
		// Tests for Exp-Complete-E[DPOP] with expectationMonotone
		
		subSuite = new TestSuite ("Tests for Exp-Complete-E-DPOP with expectationMonotone");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, false, false, 0, AddableInteger.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, false, false, 0, AddableReal.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, false, true, 0, AddableInteger.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, true, false, 0, AddableInteger.class), 1000));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, false, true, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.EXPECTATION_MONOTONE, true, false, 2, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		
		// Tests for Cons-Complete-E[DPOP]
		
		subSuite = new TestSuite ("Tests for Cons-Complete-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, false, true, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, true, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, false, true, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS, true, false, 2, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		
		// Tests for Cons-Complete-E[DPOP] with the "consensusAllSols" method
		
		subSuite = new TestSuite ("Tests for Cons-Complete-E-DPOP with the consensusAllSols method");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, false, true, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, true, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, false, true, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml", 
				null, null, Method.CONSENSUS_ALL_SOLS, true, false, 2, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		
		// Tests for Robust-Local-E[DPOP] (sampling at the lcas, projecting at the leaves)
		
		subSuite = new TestSuite ("Tests for Robust-Local-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, false, true, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, true, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling and the central mailer");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, false, true, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "leaves", Method.ROBUST, true, false, 2, AddableInteger.class), 50));
		subSuite.addTest(tmp);
		
		
		// Tests for Robust-Simple-E[DPOP] (sampling and projecting at the leaves)
		
		subSuite = new TestSuite ("Tests for Robust-Simple-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLeaves", "leaves", Method.ROBUST, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLeaves", "leaves", Method.ROBUST, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLeaves", "leaves", Method.ROBUST, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Robust-Global-E[DPOP] (sampling and projecting at the lcas)
		
		subSuite = new TestSuite ("Tests for Robust-Global-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "lcas", Method.ROBUST, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "lcas", Method.ROBUST, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtLCAs", "lcas", Method.ROBUST, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		// Tests for Robust-Central-E[DPOP] (sampling and projecting at the roots)
		
		subSuite = new TestSuite ("Tests for Robust-Central-E-DPOP");
		suite.addTest(subSuite);
		
		tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtRoots", "roots", Method.ROBUST, false, false, 0, AddableInteger.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableReal> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtRoots", "roots", Method.ROBUST, false, false, 0, AddableReal.class), 100));
		subSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and sampling");
		tmp.addTest(new RepeatedTest (new Robust_E_DPOPagentTest<AddableInteger> ("src/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml", 
				"AtRoots", "roots", Method.ROBUST, false, false, 2, AddableInteger.class), 200));
		subSuite.addTest(tmp);
		
		
		return suite;
	}

	/** Constructor
	 * @param agentFile 			the path to the agent configuration file
	 * @param whereToSample 		either "AtRoots", "AtLCAs" or "AtLeaves"
	 * @param whereToProject 		either "roots", "lcas" or "leaves"
	 * @param method 				the projection method for random variables
	 * @param useTCP 				whether TCP pipes should be used for communication between agents
	 * @param useCentralMailer 		\c true when the central mailer should be used
	 * @param nbrSamples 			the desired number of samples; if zero, no sampling is performed
	 * @param domClass 				the class used for variable values
	 */
	public Robust_E_DPOPagentTest(String agentFile, String whereToSample, String whereToProject, Method method, 
			boolean useTCP, boolean useCentralMailer, int nbrSamples, Class<V> domClass) {
		super(whereToSample, whereToProject, "foo", useTCP, useCentralMailer, nbrSamples, null, domClass);
		this.agentFile = agentFile;
		this.method = method;
	}

	/** @see E_DPOPagentTest#setUp() */
	@Override
	public void setUp () throws Exception {
		
		agentConfig = XCSPparser.parse(this.agentFile, false);
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
			
			String className = module.getAttributeValue("className");
			if (className.startsWith(SamplingPhase.class.getName())) {
				module.setAttribute("className", SamplingPhase.class.getName() + "$" + whereToSample);
				module.setAttribute("nbrSamples", String.valueOf(nbrSamples));
				module.setAttribute("whereToProject", whereToProject);
				
			}
			
			else if (className.equals(CompleteUTIL.class.getName())) {
				if (this.method == Method.CONSENSUS)
					module.setAttribute("method", "consensus");
				else if (this.method == Method.CONSENSUS_ALL_SOLS)
					module.setAttribute("method", "consensusAllSols");
				else if (this.method == Method.EXPECTATION) 
					module.setAttribute("method", "expectation");
				else if (this.method == Method.EXPECTATION_MONOTONE) 
					module.setAttribute("method", "expectationMonotone");
				else if (this.method == Method.ROBUST) 
					module.setAttribute("method", "robust");
				else 
					fail ("Unsupported method: " + this.method);
				
				module.setAttribute("nbrSamples", String.valueOf(nbrSamples));
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
		graph = RandGraphFactory.getNiceRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		boolean maximize = Math.random() < 0.5;
		problemDoc = AllTests.generateProblem(graph, maxNbrVars, maximize, 
				this.method != Method.EXPECTATION_MONOTONE ? 0 : (maximize ? -1 : +1));
		problem = new XCSPparser<V, AddableReal> (problemDoc, false, this.agentFile.contains("Complete-"));
		problem.setDomClass(domClass);
		problem.setUtilClass(AddableReal.class);

		utilModule = new ExpectedUTIL<V, AddableReal> (null, problem);
		utilModule.setSilent(true);
		utilModule.getStatsFromQueue(queue);
	}
	
	/** @see E_DPOPagentTest#checkOutput() */
	@Override
	protected void checkOutput() {
		
		if (this.agentFile.contains("Complete-") && this.nbrSamples == 0) {
			
			if (this.method == Method.EXPECTATION) 
				assertTrue (this.utilModule.getOptUtil() + " != " + ((ExpectedUTIL<V, AddableReal>) this.utilModule).getExpectedUtil() + 
						(this.problem.maximize() ? " (maximizing)" : " (minimizing)"), 
						this.utilModule.getOptUtil().compareTo(((ExpectedUTIL<V, AddableReal>) this.utilModule).getExpectedUtil(), 1E-6) == 0);
			
			else if (this.method == Method.ROBUST) 
				assertEquals (this.utilModule.getOptUtil(), ((ExpectedUTIL<V, AddableReal>) this.utilModule).getWorstUtil());
		}
	}
	
}
