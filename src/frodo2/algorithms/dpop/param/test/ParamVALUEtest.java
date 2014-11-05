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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.dpop.param.ParamUTIL;
import frodo2.algorithms.dpop.param.ParamVALUE;
import frodo2.algorithms.dpop.param.ParamUTIL.OptUtilMessage;
import frodo2.algorithms.dpop.test.VALUEpropagationTest;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** JUnit test for the class ParamVALUE
 * @author Thomas Leaute
 * @param <U> the type used for utility values
 */
public class ParamVALUEtest < U extends Addable<U> > extends VALUEpropagationTest<U> {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;

	/** The optimal assignment to variables, conditioned on the values of the parameters */
	private Map< String[], BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger> > > optAssignments;

	/** Constructor 
	 * @param useTCP 		whether to use TCP pipes or shared memory pipes
	 * @param useXML 		whether to use the XML-based constructor
	 * @param utilClass 	the class to use for utility values
	 */
	public ParamVALUEtest(boolean useTCP, boolean useXML, Class<U> utilClass) {
		super (useTCP, useXML, utilClass);
	}

	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for ParamVALUE");
		
		TestSuite testTmp = new TestSuite ("Tests for the VALUE propagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableInteger> (false, false, AddableInteger.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableInteger> (false, true, AddableInteger.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableInteger> (true, false, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableInteger> (true, true, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableReal> (false, false, AddableReal.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableReal> (false, true, AddableReal.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableReal> (true, false, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamVALUEtest<AddableReal> (true, true, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}

	/** @see VALUEpropagationTest#setUp() */
	@Override
	protected void setUp() {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		maximize = (Math.random() < 0.5);
		optAssignments = new HashMap< String[], BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger> > > (graph.nodes.size());
	}
	
	/** @see VALUEpropagationTest#tearDown() */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		this.optAssignments = null;
	}

	/** @see VALUEpropagationTest#newListenerInstance(boolean, boolean) */
	@Override
	protected Listener newListenerInstance(boolean useTCP, boolean useXML) 
	throws Exception {
		return new Listener (useTCP, useXML);
	}

	/** The listener that checks the messages sent by the ParamUTIL and ParamVALUE listeners */
	protected class Listener extends VALUEpropagationTest<U>.Listener {
		
		/** Optimal utility of each connected component (identified by its root), conditioned on the values of the parameters (if any) */
		private HashMap< String, UtilitySolutionSpace<AddableInteger, U> > optUtils = new HashMap< String, UtilitySolutionSpace<AddableInteger, U> > ();
		
		/** Used to synchronize access to optUtils, which can be \c null */
		private final Object optUtils_lock = new Object ();
		
		/** Constructor that tests the UTIL and VALUE propagation protocols on a random DFS 
		 * @param useTCP 						\c true whether TCP pipes should be used instead of QueueIOPipes
		 * @param useXML 						whether we should use the constructor that takes in XML elements, or the manual constructor
		 * @throws IOException 					if the method fails to create pipes
		 * @throws NoSuchMethodException 		if the ParamVALUE class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
		 * @throws InvocationTargetException 	if the ParamVALUE constructor throws an exception
		 * @throws IllegalAccessException 		if the ParamVALUE class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
		 * @throws InstantiationException 		would be thrown if ParamVALUE were abstract
		 * @throws IllegalArgumentException 	if the ParamVALUE constructor does not take the proper arguments
		 */
		public Listener (boolean useTCP, boolean useXML) 
		throws IOException, NoSuchMethodException, IllegalArgumentException, 
		InstantiationException, IllegalAccessException, InvocationTargetException {
			super (useTCP, useXML, ParamUTIL.class, ParamVALUE.class, true);
		}
		
		/** @see frodo2.algorithms.dpop.test.VALUEpropagationTest.Listener#checkOutput() */
		@Override
		protected void checkOutput() {
			
			// Check that the assignments found by the protocol are indeed optimal
			HashMap< String, BasicHypercube<AddableInteger, U> > optUtils2 = new HashMap< String, BasicHypercube<AddableInteger, U> > ();
			for (String var : graph.nodes) {
				
				// Check if the variable is a root
				if (dfs.get(var).getParent() == null) {
					
					// Simulate the UTIL propagation with slices instead of projections 
					// to compute the utility value for this root's DFS tree corresponding to 
					// the optimal assignments computed by the VALUE propagation protocol
					optUtils2.put(var, simulateUTILslice (var));
				}
			}
			
			// Check that the optimal utility values are the same
			assertEquals (optUtils2.size(), optUtils.size());
			for (Map.Entry< String, UtilitySolutionSpace<AddableInteger, U> > entry : optUtils.entrySet()) 
				assertTrue (optUtils2.get(entry.getKey()) + " != " + entry.getValue(), optUtils2.get(entry.getKey()).equivalent(entry.getValue()));

		}
		
		/** @see frodo2.algorithms.dpop.test.VALUEpropagationTest.Listener#getMsgTypes() */
		@Override
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (2);
			types.add(ParamVALUE.PARAM_OUTPUT_MSG_TYPE);
			types.add(ParamUTIL.OPT_PARAM_UTIL_MSG_TYPE);
			return types;
		}

		/** @see frodo2.algorithms.dpop.test.VALUEpropagationTest.Listener#notifyIn(Message) */
		@Override
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {

			String type = msg.getType();
			
			if (type.equals(ParamUTIL.OPT_PARAM_UTIL_MSG_TYPE)) { // message sent by a root containing the optimal utility value
				OptUtilMessage<AddableInteger, U> msgCast = (OptUtilMessage<AddableInteger, U>) msg;
				synchronized (optUtils_lock) {
					optUtils.put(msgCast.getRoot(), msgCast.getUtility());
				}
			}
			
			else if (type.equals(ParamVALUE.PARAM_OUTPUT_MSG_TYPE)) { // optimal assignment to a variable
				ParamVALUE.AssignmentMessage<AddableInteger> msgCast = (ParamVALUE.AssignmentMessage<AddableInteger>) msg;
				synchronized (optAssignments) {
					optAssignments.put(msgCast.getVariables(), msgCast.getValues());
				}
			}
			
			finished_lock.lock();
			if (--nbrMsgsRemaining <= 0) 
				finished.signal();
			finished_lock.unlock();
		}

		/** @see frodo2.algorithms.dpop.test.VALUEpropagationTest.Listener#getStatsFromQueue(frodo2.communication.Queue) */
		@Override
		public void getStatsFromQueue(Queue queue) {
			queue.addIncomingMessagePolicy(ParamVALUE.PARAM_OUTPUT_MSG_TYPE, this);
		}

		/** @see frodo2.algorithms.dpop.test.VALUEpropagationTest.Listener#simulateUTILslice(java.lang.String) */
		@Override
		protected Hypercube<AddableInteger, U> simulateUTILslice (String var) {
			
			List<String> children = dfs.get(var).getChildren();
			
			// Look up the optimal assignments to var
			String[] vars = null;
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger> > assignments = null;
			for (Map.Entry< String[], BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger> > > entry : optAssignments.entrySet()) {
				vars = entry.getKey();
				if (vars[0].equals(var)) {
					assignments = entry.getValue();
					break;
				}
			}
			
			if (children.size() == 0) { // leaf variable
				
				// Check if the variable is unconstrained
				UtilitySolutionSpace<AddableInteger, U> space = hypercubes.get(var);
				if (space == null) 
					return new ScalarHypercube<AddableInteger, U> (parser.getZeroUtility(), (parser.maximize() ? parser.getMinInfUtility() : parser.getPlusInfUtility()), new AddableInteger [0].getClass());
				
				// Slice variable out of its private hypercube and return result
				return (Hypercube<AddableInteger, U>) space.compose(vars, assignments);
				
			} else { // non-leaf variable

				// Compute the hypercube received from each child and process it to compute the join
				UtilitySolutionSpace<AddableInteger, U> join = hypercubes.get(var);
				for ( String child : children ) {
					if (join == null) {
						join = simulateUTILslice(child);
					} else 
						join = join.join(simulateUTILslice(child));
				}
				
				// Slice out the variable and return the result
				return (Hypercube<AddableInteger, U>) join.compose(vars, assignments);
			}
		}
		
	}
}
