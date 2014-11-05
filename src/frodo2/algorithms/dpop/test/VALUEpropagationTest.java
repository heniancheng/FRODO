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

package frodo2.algorithms.dpop.test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** JUnit test for the class VALUEpropagation
 * @author Thomas Leaute
 * @param <U> the type used for utility values
 */
public class VALUEpropagationTest < U extends Addable<U> > extends TestCase {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 10;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 40;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;

	/** List of queues corresponding to the different agents */
	protected Queue[] queues = new Queue[0];
	
	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;
	
	/** The DFS corresponding to the random graph */
	protected Map< String, DFSview<AddableInteger, U> > dfs;
	
	/** Whether to use TCP pipes or shared memory pipes */
	private boolean useTCP;

	/** Whether to use the XML-based constructor */
	private boolean useXML;

	/** The class to use for utility values */
	private Class<U> utilClass;

	/** Whether we should maximize or minimize */
	protected boolean maximize;
	
	/** The number of output messages remaining to be received from the UTIL propagation protocol */
	protected Integer nbrMsgsRemaining;
	
	/** Used to make the test thread wait */
	protected final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	protected final Condition finished = finished_lock.newCondition();

	/** The optimal assignment to each variable */
	private Map <String, AddableInteger> optAssignments;

	/** Constructor 
	 * @param useTCP 				whether to use TCP pipes or shared memory pipes
	 * @param useXML 				whether to use the XML-based constructor
	 * @param utilClass 			the class to use for utility values
	 */
	public VALUEpropagationTest(boolean useTCP, boolean useXML, Class<U> utilClass) {
		super ("test");
		this.useTCP = useTCP;
		this.useXML = useXML;
		this.utilClass = utilClass;
		
		// Decide whether we should maximize or minimize
		maximize = (Math.random() < 0.5);
	}

	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for VALUEpropagation");
		
		TestSuite testTmp = new TestSuite ("Tests for the VALUE propagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableInteger> (false, false, AddableInteger.class), 500));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableInteger> (false, true, AddableInteger.class), 500));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableInteger> (true, false, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableInteger> (true, true, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableReal> (false, false, AddableReal.class), 500));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableReal> (false, true, AddableReal.class), 500));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableReal> (true, false, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the VALUE propagation protocol with XML support using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new VALUEpropagationTest<AddableReal> (true, true, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}

	/** @see junit.framework.TestCase#setUp() */
	protected void setUp() {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		maximize = (Math.random() < 0.5);
		optAssignments = new HashMap<String, AddableInteger> (graph.nodes.size());
	}

	/** Ends all queues
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		dfs = null;
		optAssignments = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		this.nbrMsgsRemaining = null;
	}

	/** Creates a new Listener
	 * @param useTCP 		\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param useXML 		whether we should use the constructor that takes in XML elements or the manual constructor
	 * @return 				the new listener
	 * @throws Exception 	if an error occurs
	 */
	protected Listener newListenerInstance(boolean useTCP, boolean useXML) 
	throws Exception {
		return new Listener (useTCP, useXML, UTILpropagation.class, VALUEpropagation.class, false);
	}

	/** Tests the UTIL and VALUE propagation protocols on a random graph
	 * @throws Exception if an error occurs
	 */
	public void test () throws Exception {
		Listener listener = newListenerInstance(useTCP, useXML);
		listener.waitForOutputs();
	}
	
	/** The listener that checks the messages sent by the UTILpropagation and VALUEpropagation listeners */
	protected class Listener implements StatsReporter {
		
		/** Optimal, total utility across all connected components */
		private U optTotalUtil;
		
		/** Used to synchronize access to optTotalUtil, which can be \c null */
		private Object optTotalUtil_lock = new Object ();
		
		/** For each variable, the constraint it is responsible for enforcing */
		protected Map< String, UtilitySolutionSpace<AddableInteger, U> > hypercubes;
		
		/** The pipes of the queue network */
		private QueueOutputPipeInterface[] pipes; 
		
		/** The Listener's own queue */
		private Queue myQueue;
		
		/** The constraints in the problem */
		private List< ? extends UtilitySolutionSpace<AddableInteger, U> > spaces;

		/** The statistics gatherer */
		private VALUEpropagation<AddableInteger> statsGatherer;
		
		/** The parser for the problem */
		protected XCSPparser<AddableInteger, U> parser;

		/** Constructor that tests the UTIL and VALUE propagation protocols on a random DFS 
		 * @param useTCP 						\c true whether TCP pipes should be used instead of QueueIOPipes
		 * @param useXML 						whether we should use the constructor that takes in XML elements, or the manual constructor
		 * @param UTILpropClass 				the class of the UTIL propagation module under test
		 * @param VALUEpropClass 				the class of the VALUE propagation module under test
		 * @param withAnonymVars 				if \c true, variables without owners are taken into account
		 * @throws IOException 					if the method fails to create pipes
		 * @throws NoSuchMethodException 		if the VALUEpropagation class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
		 * @throws InvocationTargetException 	if the VALUEpropagation constructor throws an exception
		 * @throws IllegalAccessException 		if the VALUEpropagation class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
		 * @throws InstantiationException 		would be thrown if VALUEpropagation were abstract
		 * @throws IllegalArgumentException 	if the VALUEpropagation constructor does not take the proper arguments
		 */
		public Listener (boolean useTCP, boolean useXML, 
				Class< ? extends StatsReporter > UTILpropClass, Class< ? extends StatsReporter > VALUEpropClass, boolean withAnonymVars) 
		throws IOException, NoSuchMethodException, IllegalArgumentException, 
		InstantiationException, IllegalAccessException, InvocationTargetException {
			
			int nbrAgents = graph.clusters.size();
			int nbrVars = graph.nodes.size();
			
			// Count the number of messages received from the modules: 
			// one AssignmentsMessage per variable + one OptUtilMessage per root
			nbrMsgsRemaining = nbrVars + graph.components.size();

			// Create the queue network
			queues = new Queue [nbrAgents];
			pipes = AllTests.createQueueNetwork(queues, graph, useTCP);

			// Create the map associating to each variable the ID of its owner agent
			HashMap<String, String> owners = new HashMap<String, String> (graph.nodes.size());
			for (Map.Entry<String, Integer> entry : graph.clusterOf.entrySet()) 
				owners.put(entry.getKey(), entry.getValue().toString());
			
			// Listen for statistics messages
			myQueue = new Queue (false);
			this.getStatsFromQueue(myQueue);
			QueueIOPipe myPipe = new QueueIOPipe (myQueue);
			for (Queue queue : queues) 
				queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
			parser = new XCSPparser<AddableInteger, U> (AllTests.generateProblem(graph, (withAnonymVars ? graph.nodes.size() : 0), maximize));
			parser.setUtilClass(utilClass);
			dfs = UTILpropagationTest.computeDFS(graph, parser, withAnonymVars);
			statsGatherer = new VALUEpropagation<AddableInteger> (null, parser);
			statsGatherer.setSilent(true);
			statsGatherer.getStatsFromQueue(myQueue);
			
			// Create the listeners
			spaces = parser.getSolutionSpaces(withAnonymVars);
			if (useXML) { // use the XML-based constructor
				for (String agent : parser.getAgents()) {
					Queue queue = queues[Integer.parseInt(agent)];

					XCSPparser<AddableInteger, U> subproblem = parser.getSubProblem(agent);
					subproblem.setUtilClass(utilClass);
					queue.setProblem(subproblem);

					// Instantiate the UTIL propagation module
					Constructor<?> constructor = UTILpropClass.getConstructor(DCOPProblemInterface.class);
					queue.addIncomingMessagePolicy((StatsReporter) constructor.newInstance(subproblem));

					// Instantiate the VALUE propagation module
					constructor = VALUEpropClass.getConstructor(DCOPProblemInterface.class, Element.class);
					queue.addIncomingMessagePolicy((StatsReporter) constructor.newInstance(subproblem, null));

					queue.addIncomingMessagePolicy(this);
				}

			} else { // use the manual constructor not based on XML
				for (String agent : parser.getAgents()) {
					Queue queue = queues[Integer.parseInt(agent)];
					
					DCOPProblemInterface<AddableInteger, U> subproblem = parser.getSubProblem(agent);
					subproblem.setUtilClass(utilClass);

					// Instantiate the UTIL propagation module
					Constructor<?> constructor = UTILpropClass.getConstructor(DCOPProblemInterface.class);
					queue.addIncomingMessagePolicy((StatsReporter) constructor.newInstance(subproblem));
					
					// Instantiate the VALUE propagation module
					constructor = VALUEpropClass.getConstructor(DCOPProblemInterface.class, Boolean.class);
					queue.addIncomingMessagePolicy((StatsReporter) constructor.newInstance(subproblem, false));
					
					queue.addIncomingMessagePolicy(this);
				}
			}
			
		}
		
		/** Waits for the outputs of the module and checks their validity */
		private void waitForOutputs () {
			
			// Start UTIL propagation
			hypercubes = UTILpropagationTest.startUTIL(graph, dfs, queues, spaces);
			
			// Wait until all agents have sent their outputs
			while (true) {
				finished_lock.lock();
				try {
					if (nbrMsgsRemaining == 0) {
						break;
					} else if (nbrMsgsRemaining < 0) {
						fail("Received too many output messages from the protocol");
					} else if (! finished.await(20, TimeUnit.SECONDS)) {
						fail("Timeout");
					}
				} catch (InterruptedException e) {
					break;
				}
				finished_lock.unlock();
			}
			
			this.checkOutput();
					
			// Properly close the pipes
			/// @bug Sometimes, the pipes are closed while the VALUEpropagation listeners are still sending messages;
			/// this throws a SocketException, but the unit tests still pass and are still valid. 
			for (QueueOutputPipeInterface pipe : pipes) {
				pipe.close();
			}
			myQueue.end();
		}
		
		/** Checks that the output of the module is correct */
		protected void checkOutput() {

			// Check that the assignments found by the protocol are indeed optimal
			U optUtil = null;
			for (String var : graph.nodes) {
				
				// Check if the variable is a root
				if (dfs.get(var).getParent() == null) {
					
					// Simulate the UTIL propagation with slices instead of projections 
					// to compute the utility value for this root's DFS tree corresponding to 
					// the optimal assignments computed by the VALUE propagation protocol
					Hypercube<AddableInteger, U> hypercube = simulateUTILslice (var);
					if (optUtil == null) {
						optUtil = hypercube.getUtility(0);
					} else 
						optUtil = optUtil.add(hypercube.getUtility(0));
				}
			}
			
			assertEquals(optUtil, optTotalUtil);
		}
		
		/** Listens to the outputs of the UTIL and VALUE propagation protocols 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
		 */
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (2);
			types.add(VALUEpropagation.OUTPUT_MSG_TYPE);
			types.add(UTILpropagation.OPT_UTIL_MSG_TYPE);
			return types;
		}

		/** @see StatsReporter#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {

			String type = msg.getType();
			
			if (type.equals(UTILpropagation.OPT_UTIL_MSG_TYPE)) { // message sent by a root containing the optimal utility value
				UTILpropagation.OptUtilMessage<U> msgCast = (UTILpropagation.OptUtilMessage<U>) msg;
				synchronized (optTotalUtil_lock) {
					if (optTotalUtil == null) {
						optTotalUtil = msgCast.getUtility();
					} else 
						optTotalUtil = optTotalUtil.add(msgCast.getUtility());
				}
			}
			
			else if (type.equals(VALUEpropagation.OUTPUT_MSG_TYPE)) { // optimal assignment to a variable
				VALUEpropagation.AssignmentsMessage<AddableInteger> msgCast = (VALUEpropagation.AssignmentsMessage<AddableInteger>) msg;
				synchronized (optAssignments) {
					String[] vars = msgCast.getVariables();
					ArrayList<AddableInteger> vals = msgCast.getValues();
					for (int i = 0; i < vars.length; i++) 
						optAssignments.put(vars[i], vals.get(i));
				}
			}
			
			finished_lock.lock();
			if (--nbrMsgsRemaining <= 0) 
				finished.signal();
			finished_lock.unlock();
		}

		/** Does nothing
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) { }

		/** @see StatsReporter#getStatsFromQueue(Queue) */
		public void getStatsFromQueue(Queue queue) {
			queue.addIncomingMessagePolicy(VALUEpropagation.OUTPUT_MSG_TYPE, this);
		}

		/** @see StatsReporter#setSilent(boolean) */
		public void setSilent(boolean silent) { }
		
		/** Simulates UTIL propagation on the subtree rooted at the input variable, doing slices instead of projections
		 * @param var root variable
		 * @return the UTIL message that the input variable sends to its parent (if any)
		 */
		protected Hypercube<AddableInteger, U> simulateUTILslice (String var) {
			
			List<String> children = dfs.get(var).getChildren();
			
			if (children.size() == 0) { // leaf variable
				
				// Check if the variable is unconstrained
				UtilitySolutionSpace<AddableInteger, U> space = hypercubes.get(var);
				if (space == null) 
					return new ScalarHypercube<AddableInteger, U> (parser.getZeroUtility(), (parser.maximize() ? parser.getMinInfUtility() : parser.getPlusInfUtility()), new AddableInteger [0].getClass());
				
				// Slice variable out of its private hypercube and return result
				return (Hypercube<AddableInteger, U>) space.slice(var, optAssignments.get(var));
				
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
				return (Hypercube<AddableInteger, U>) join.slice(var, optAssignments.get(var));
			}
		}

		/** @see StatsReporter#reset() */
		public void reset() { }
		
	}
}
