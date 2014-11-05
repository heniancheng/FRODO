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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.dpop.param.ParamUTIL;
import frodo2.algorithms.dpop.param.ParamUTIL.OptUtilMessage;
import frodo2.algorithms.dpop.test.UTILpropagationTest;
import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test for the class ParamUTIL
 * @author Thomas Leaute
 * @param <U> the type used for utility values
 */
public class ParamUTILtest < U extends Addable<U> > extends UTILpropagationTest<U> {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;

	/** Constructor 
	 * @param useTCP 		whether to use TCP pipes or shared memory pipes
	 * @param useXML 		whether to use the XML-based constructor
	 * @param utilClass 	the class to use for utility values
	 */
	public ParamUTILtest(boolean useTCP, boolean useXML, Class<U> utilClass) {
		super (useTCP, useXML, utilClass, false);
	}

	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for ParamUTIL");
		
		TestSuite testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableInteger> (false, false, AddableInteger.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol with XML support using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableInteger> (false, true, AddableInteger.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableInteger> (true, false, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol with XML support using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableInteger> (true, true, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableReal> (false, false, AddableReal.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol with XML support using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableReal> (false, true, AddableReal.class), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableReal> (true, false, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ParamUTIL propagation protocol with XML support using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ParamUTILtest<AddableReal> (true, true, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}

	/** @see UTILpropagationTest#setUp() */
	@Override
	protected void setUp () {
		super.setUp();
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
	}
	
	/** Creates a new Listener
	 * @param useTCP 				\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param useXML 				whether we should use the constructor that takes in XML elements or the manual constructor
	 * @param parameters 			the parameters for the module under test
	 * @return 						the new listener
	 * @throws Exception 			if an error occurs
	 */
	@Override
	protected Listener newListenerInstance(boolean useTCP, boolean useXML, Element parameters) 
	throws Exception {
		return new Listener (useTCP, useXML, parameters);
	}

	/** The listener that checks the messages sent by the ParamUTIL listeners */
	protected class Listener extends UTILpropagationTest<U>.Listener {
		
		/** Optimal utility of each connected component (identified by its root), conditioned on the values of the parameters (if any) */
		private HashMap< String, UtilitySolutionSpace<AddableInteger, U> > optUtils = new HashMap< String, UtilitySolutionSpace<AddableInteger, U> > ();
		
		/** Used to synchronize access to optUtils, which can be \c null */
		private final Object optUtils_lock = new Object ();
		
		/** Constructor that tests the ParamUTIL propagation protocol on a random DFS 
		 * @param useTCP 						\c true whether TCP pipes should be used instead of QueueIOPipes
		 * @param useXML 						whether we should use the constructor that takes in XML elements or the manual constructor
		 * @param parameters 					the parameters for the module under test
		 * @throws Exception 	if an error occurs
		 */
		public Listener (boolean useTCP, boolean useXML, Element parameters) throws Exception {
			super (useTCP, useXML, parameters, ParamUTIL.class, true);
		}

		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#checkOutput() */
		@Override
		protected void checkOutput() {
			
			// Compute optimal utility value of each connected component while checking separators
			HashMap< String, UtilitySolutionSpace<AddableInteger, U> > optUtils2 = new HashMap< String, UtilitySolutionSpace<AddableInteger, U> > ();
			for (String var : graph.nodes) {
				
				// Check if the variable is a root
				if (dfs.get(var).getParent() == null) {
					
					// Simulate the UTIL propagation to compute the optimal utility value for this root's DFS tree
					optUtils2.put(var, simulateUTIL (var));
				}
			}
			
			// Check that the optimal utility values are the same
			assertEquals (optUtils2.size(), optUtils.size());
			for (Map.Entry< String, UtilitySolutionSpace<AddableInteger, U> > entry : optUtils.entrySet()) 
				assertTrue (optUtils2.get(entry.getKey()).equivalent(entry.getValue()));
			
		}

		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#getMsgTypes() */
		@Override
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (2);
			types.add(ParamUTIL.SEPARATOR_MSG_TYPE);
			types.add(ParamUTIL.OPT_PARAM_UTIL_MSG_TYPE);
			return types;
		}

		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#notifyIn(Message) */
		@Override
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			if (msg.getType().equals(ParamUTIL.OPT_PARAM_UTIL_MSG_TYPE)) { // message sent by a root containing the optimal utility value
				OptUtilMessage<AddableInteger, U> msgCast = (OptUtilMessage<AddableInteger, U>) msg;
				synchronized (optUtils_lock) {
					optUtils.put(msgCast.getRoot(), msgCast.getUtility());
				}
			}
			
			super.notifyIn(msg);
		}

	}
	
}
