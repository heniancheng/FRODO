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

package frodo2.algorithms.synchbb.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.Problem;
import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.test.DPOPagentTest;
import frodo2.algorithms.reformulation.ProblemRescaler;
import frodo2.algorithms.synchbb.SynchBB;
import frodo2.algorithms.synchbb.SynchBBsolver;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.election.VariableElection;
import frodo2.algorithms.varOrdering.linear.LinearOrdering;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;

/** JUnit tests for SynchBB
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @author Thomas Leaute
 */
public class SynchBBagentTest <V extends Addable<V>, U extends Addable<U> > extends DPOPagentTest<V, U> {
	
	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for SynchBBagent");
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", true, false, false, false, AddableInteger.class, AddableInteger.class, false, +1), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes without XCSP");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", false, false, false, false, AddableInteger.class, AddableInteger.class, false, +1), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on minimization problems with unrestricted cost signs");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", true, false, false, false, AddableInteger.class, AddableInteger.class, false, 0), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on minimization problems with unrestricted cost signs without XCSP");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", false, false, false, false, AddableInteger.class, AddableInteger.class, false, 0), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on maximization problems with unrestricted utility signs");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", true, false, false, false, AddableInteger.class, AddableInteger.class, true, 0), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on maximization problems with unrestricted utility signs without XCSP");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", false, false, false, false, AddableInteger.class, AddableInteger.class, true, 0), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableReal, AddableInteger> ("SynchBBagent.xml", true, false, false, false, AddableReal.class, AddableInteger.class, false, +1), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued utilities");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableReal> ("SynchBBagent.xml", true, false, false, false, AddableInteger.class, AddableReal.class, false, +1), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued utilities without XCSP");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableReal> ("SynchBBagent.xml", false, false, false, false, AddableInteger.class, AddableReal.class, false, +1), 5000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with the central mailer");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", true, false, true, false, AddableInteger.class, AddableInteger.class, false, +1), 10000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes counting NCCCs");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", true, false, false, true, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes counting NCCCs without XCSP");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", false, false, false, true, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBagent.xml", true, true, false, false, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and CentralLinearOrdering");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBcentralOrder.xml", true, false, false, false, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and CentralLinearOrdering on minimization problems with unrestricted cost signs");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBcentralOrder.xml", true, false, false, false, AddableInteger.class, AddableInteger.class, false, 0), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and CentralLinearOrdering on maximization problems with unrestricted cost signs");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBcentralOrder.xml", true, false, false, false, AddableInteger.class, AddableInteger.class, true, 0), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with the central mailer and CentralLinearOrdering");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBcentralOrder.xml", true, false, true, false, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes counting NCCCs and CentralLinearOrdering");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBcentralOrder.xml", true, false, false, true, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes and CentralLinearOrdering");
		tmp.addTest(new RepeatedTest (new SynchBBagentTest<AddableInteger, AddableInteger> ("SynchBBcentralOrder.xml", true, true, false, false, AddableInteger.class, AddableInteger.class, false, +1), 1000));
		suite.addTest(tmp);
		
		return suite;
	}

	/** The stats gatherer */
	private SynchBB<V, U> synchBBmodule;
	
	/** The name of the agent configuration file */
	private String agentFile;
	
	/** The restriction on the sign of the costs/utilities */
	private final int sign;
	
	/** Constructor
	 * @param agentFile 		the name of the agent configuration file
	 * @param useXCSP 			whether to use XCSP
	 * @param useTCP 			whether TCP pipes should be used for communication between agents
	 * @param useCentralMailer	\c true when the central mailer should be tested
	 * @param countNCCCs 		whether to count NCCCs
	 * @param domClass 			class used for variable values
	 * @param utilClass 		class used for utility values
	 * @param maximize 			Whether we should maximize or minimize
	 * @param sign 				The restriction on the sign of the costs/utilities
	 */
	@SuppressWarnings("unchecked")
	public SynchBBagentTest (String agentFile, boolean useXCSP, boolean useTCP, boolean useCentralMailer, boolean countNCCCs, Class<V> domClass, Class<U> utilClass, boolean maximize, int sign) {
		super (useXCSP, useTCP, useCentralMailer, false, domClass, utilClass, null, 
				(Class<? extends XCSPparser<V, U>>) XCSPparser.class, false, false, countNCCCs, false, false);
		
		super.maximize = maximize;
		this.sign = sign;
		this.agentFile = agentFile;
	}
	
	/** @see DPOPagentTest#setUp() */
	@Override
	public void setUp () throws Exception {
		super.setUp();

		problemDoc = AllTests.generateProblem(graph, this.maximize, this.sign);
		problem = new XCSPparser<V, U> (problemDoc, this.countNCCCs, false, true);
		problem.setDomClass(super.domClass);
		problem.setUtilClass(utilClass);
		if (! super.useXCSP) {
			Problem<V, U> prob = new Problem<V, U> (this.maximize, true);
			prob.reset(this.problem);
			this.problem = prob;
			problem.setDomClass(super.domClass);
			problem.setUtilClass(utilClass);
		}
		
		super.agentConfig = XCSPparser.parse("src/frodo2/algorithms/synchbb/" + agentFile, false);
		
		// Set the number of rounds of VariableElection, and whether we should count NCCCs
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
			String className = module.getAttributeValue("className");
			
			if (className.equals(VariableElection.class.getName()))
				module.setAttribute("nbrSteps", Integer.toString(problem.getNbrVars()));
			
			else if (className.equals(SynchBB.class.getName())) {
				module.setAttribute("convergence", "true");
			}
			
			else if ((this.maximize || this.sign <= 0) && className.equals(ProblemRescaler.class.getName())) 
				module.setAttribute("shift", "1000000");
		}
		
		// Create the stats gatherer
		new SynchBBsolver<V, U> (super.agentConfig); // this is a hack to enforce message type overriding
		this.synchBBmodule = new SynchBB<V, U> (null, problem);
		this.synchBBmodule.setSilent(true);
		this.synchBBmodule.getStatsFromQueue(super.queue);
		
		// Listen for the linear order on variables
		LinearOrdering<V, U> module = new LinearOrdering<V, U> (null, problem);
		module.setSilent(true);
		module.getStatsFromQueue(queue);
	}
	
	/** @see DPOPagentTest#tearDown() */
	@Override
	protected void tearDown () throws Exception {
		super.tearDown();
		this.synchBBmodule = null;
	}

	/** @see DPOPagentTest#checkOutput() */
	@Override
	protected void checkOutput() {
		Solution<V, U> sol = new DPOPsolver<V, U> (super.domClass, super.utilClass).solve(super.problem);
		
		// Check the utility
		U util = sol.getUtility();
		assertEquals (util, this.synchBBmodule.getOptCost());
		
		// If the problem is feasible, check the assignments
		if (util != this.problem.getPlusInfUtility() && util != this.problem.getMinInfUtility()) {
			assertEquals (util, this.problem.getUtility(this.synchBBmodule.getOptAssignments()).getUtility(0));

			// Check that each variable has an assignment history
			HashMap< String, ArrayList< CurrentAssignment<V> > > histories = this.synchBBmodule.getAssignmentHistories();
			for (String var : super.problem.getVariables()) 
				assertFalse ("Variable " + var + " has no assignment history:\n" + histories, histories.get(var).isEmpty());
		}
	}
	
}
