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

package frodo2.algorithms.dpop.param.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;
import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.param.ParamUTIL;
import frodo2.algorithms.dpop.param.ParamVALUE;
import frodo2.algorithms.dpop.test.DPOPagentTest;
import frodo2.algorithms.test.AllTests;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.mailer.CentralMailer;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test for Param-DPOP
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class ParamDPOPtest < V extends Addable<V>, U extends Addable<U> > extends DPOPagentTest<V, U> {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;
	
	/** The module listening for the optimal utility to the problem */
	private ParamUTIL<V, U> paramUtilModule;
	
	/** The module listening for the optimal assignment to the problem */
	private ParamVALUE<V> paramValueModule;
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param useXCSP 			whether to use XCSP
	 * @param useTCP 			whether TCP pipes should be used for communication between agents
	 * @param allProbs 			if \c true, all agents know about all random variables
	 * @param useCentralMailer 	\c true when the central mailer should be tested
	 * @param startMsgType 		the type of the start message
	 * @param domClass 			the class used for variable values
	 * @param utilClass 		the class used for utility values
	 */
	public ParamDPOPtest(boolean useXCSP, boolean useTCP, boolean allProbs, boolean useCentralMailer, String startMsgType, Class<V> domClass, Class<U> utilClass) {
		super(useXCSP, useTCP, useCentralMailer, false, domClass, utilClass, startMsgType);
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for Param-DPOP");
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableInteger> (true, false, false, false, null, AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes without XCSP");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableInteger> (false, false, false, false, null, AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableReal, AddableInteger> (true, false, false, false, null, AddableReal.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued utilities");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableReal> (true, false, false, false, null, AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued utilities without XCSP");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableReal> (false, false, false, false, null, AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableInteger> (true, false, false, true, null, AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableInteger> (true, true, false, false, null, AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with a different type for the start message");
		tmp.addTest(new RepeatedTest (new ParamDPOPtest<AddableInteger, AddableInteger> (true, false, false, false, "START NOW!", AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** @see DPOPagentTest#setUp() */
	@Override
	public void setUp () throws Exception {
		
		agentConfig = XCSPparser.parse("src/frodo2/algorithms/dpop/param/Param-DPOP.xml", false);
		super.setStartMsgType(startMsgType);

		nbrMsgsReceived = 0;
		this.nbrAgentsFinished = 0;
		
		// Create the queue
		if (this.useCentralMailer) {
			mailman = new CentralMailer (false, false, null);
			this.queue = mailman.newQueue(AgentInterface.STATS_MONITOR);
		} else 
			queue = new Queue (false);
		
		queue.addIncomingMessagePolicy(this);
		pipes = new HashMap<Object, QueueOutputPipeInterface> ();
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problemDoc = AllTests.generateProblem(graph, 3, true);
		
		problem = new XCSPparser<V, U> (problemDoc);
		problem.setDomClass(domClass);
		problem.setUtilClass(super.utilClass);
		if (! super.useXCSP) {
			Problem<V, U> prob = new Problem<V, U> (this.maximize);
			prob.reset(this.problem);
			this.problem = prob;
			problem.setDomClass(domClass);
			problem.setUtilClass(super.utilClass);
		}
		
		paramUtilModule = new ParamUTIL<V, U> (null, problem);
		paramUtilModule.setSilent(true);
		paramUtilModule.getStatsFromQueue(queue);
		paramValueModule = new ParamVALUE<V> (null, problem);
		paramValueModule.setSilent(true);
		paramValueModule.getStatsFromQueue(queue);
	}
	
	/** @see DPOPagentTest#tearDown() */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		this.paramUtilModule = null;
		this.paramValueModule = null;
	}
	
	/** @see DPOPagentTest#checkOutput() */
	@SuppressWarnings("unchecked")
	@Override
	protected void checkOutput()  {

		// Check that the optimal assignments computed indeed have the declared optimal utility. 
		UtilitySolutionSpace<V, U> optUtil = paramUtilModule.getOptParamUtil();
		Map< String[], BasicUtilitySolutionSpace< V, ArrayList<V> > > optValues = paramValueModule.getParamSolution();
		UtilitySolutionSpace<V, U> realUtil = problem.getParamUtility(optValues);
		assertTrue (optUtil.equivalent(realUtil));

		// Now check that the optimal utility found is indeed optimal
		
		// Join all spaces
		List< ? extends UtilitySolutionSpace<V, U> > spaces = this.problem.getSolutionSpaces(true);
		if (spaces.isEmpty()) 
			return;
		UtilitySolutionSpace<V, U> param = spaces.remove(0);
		if (! spaces.isEmpty()) 
			param = param.join(spaces.toArray(new UtilitySolutionSpace [spaces.size()]));
		
		// Project all decision variables
		Set<String> vars = this.problem.getVariables();
		param = param.blindProject(vars.toArray(new String [vars.size()]), maximize);
		
		assertTrue (optUtil + " != " + param, optUtil.equivalent(param));
	}

}
