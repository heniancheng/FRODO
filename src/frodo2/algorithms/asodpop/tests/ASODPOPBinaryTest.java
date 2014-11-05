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

package frodo2.algorithms.asodpop.tests;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.asodpop.ASODPOPBinaryDomains;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.odpop.VALUEpropagation;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test for the class ASODPOP
 * @author Brammert Ottens, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @todo REUSE CODE FROM ASODPOPTest!!!
 */
public class ASODPOPBinaryTest < V extends Addable<V>, U extends Addable<U> > extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 10;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;
	
	/** The number of output messages remaining to be received from the UTIL propagation protocol */
	private int nbrMsgsRemaining;
	
	/** Used to synchronize the access to \a nbrMsgsRemaining
	 * 
	 * We cannot synchronize directly over \a nbrMsgsRemaining because it will be decremented, and hence the object will change. 
	 */
	private final Object nbrMsgsRemaining_lock = new Object ();
	
	/** List of queues corresponding to the different agents */
	private Queue[] queues;
	
	/** The parameters for adopt*/
	private Element parameters;
	
	/** Random graph used to generate a constraint graph */
	private RandGraphFactory.Graph graph;
	
	/** The DFS corresponding to the random graph */
	private Map< String, DFSview<V, U> > dfs;
	
	/** The problem to be solved*/
	private Document problem;
	
	/** Solver used to calculate the optimal utility*/
	private DPOPsolver<V, U> solver;
	
	/** The XCSP parser */
	protected XCSPparser<V, U> parser;
	
	/** The assignments reported by the variables*/
	private HashMap<String, V> assignments;
	
	/** The class of variable values */
	private Class<V> domClass;
	
	/** The class of utility values */
	private Class<U> utilClass;
	
	/** Constructor that instantiates a test only for the input method
	 * @param method 		test method
	 * @param domClass 		the class of variable values
	 * @param utilClass 	the class of utility values
	 */
	public ASODPOPBinaryTest(String method, Class<V> domClass, Class<U> utilClass) {
		super (method);
		this.domClass = domClass;
		this.utilClass = utilClass;
	}
	
	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		
		// generate the problem graph and the structure
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problem = AllTests.generateProblem(graph, true, +1, true);
		parser = new XCSPparser<V, U> (problem);
		parser.setDomClass(domClass);
		parser.setUtilClass(utilClass);
		dfs = frodo2.algorithms.dpop.test.UTILpropagationTest.computeDFS(graph, parser);
		
		solver = new DPOPsolver<V, U> (this.domClass, this.utilClass);
		assignments = new HashMap<String, V>();
		
		parameters = new Element ("module");
	}

	/** 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		graph = null;
		dfs = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		this.assignments = null;
		this.parameters  = null;
		this.parser = null;
		this.problem = null;
		this.solver = null;
	}
	
	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for ASODPOP Binary");
		
		TestSuite testTmp = new TestSuite ("Tests for the ASODPOP propagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ASODPOPBinaryTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", AddableInteger.class, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ASODPOP propagation protocol using shared memory pipes and integer utilities and real-valued variables");
		testTmp.addTest(new RepeatedTest (new ASODPOPBinaryTest<AddableReal, AddableInteger> ("testRandomSharedMemory", AddableReal.class, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ASODPOP propagation protocol using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new ASODPOPBinaryTest<AddableInteger, AddableReal> ("testRandomSharedMemory", AddableInteger.class, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ASODPOP propagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new ASODPOPBinaryTest<AddableInteger, AddableInteger> ("testRandomTCP", AddableInteger.class, AddableInteger.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ASODPOP propagation protocol using shared memory pipes and integer utilities and the alternative constructor");
		testTmp.addTest(new RepeatedTest (new ASODPOPBinaryTest<AddableInteger, AddableInteger> ("testRandomSharedMemoryNoXML", AddableInteger.class, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** Tests the ADOPT protocol on a random graph using QueueIOPipes
	 * @throws Exception 	if an error occurs
	 */
	public void testRandomSharedMemory () throws Exception {
		test (ASODPOPBinaryDomains.DetermineAssignmentMax.class.getName(), false, true);
	}
	
	/** Tests the ADOPT protocol on a random graph using TCP pipes
	 * @throws Exception 	if an error occurs
	 */
	public void testRandomTCP () throws Exception {
		test (ASODPOPBinaryDomains.DetermineAssignmentMax.class.getName(), true, true);
	}
	
	/** Tests the ADOPT protocol on a random graph using QueueIOPipes
	 * @throws Exception 	if an error occurs
	 */
	public void testRandomSharedMemoryNoXML () throws Exception {
		test (ASODPOPBinaryDomains.DetermineAssignmentMax.class.getName(), false, false);
	}
	
	/** Runs a random test
	 * @param combination 	the version of ADOPT
	 * @param useTCP 		whether to use TCP pipes or shared memory pipes
	 * @param useXML 		whether to use the XML-based constructor
	 * @throws Exception 	if an error occurs
	 */
	@SuppressWarnings("unchecked")
	private void test (String combination, boolean useTCP, boolean useXML) throws Exception {
		
		int nbrAgents = graph.clusters.size();
		int nbrVars = graph.nodes.size();
		
		nbrMsgsRemaining = nbrVars; // One AssignmentMessage per variable
		
		// Create the queue network
		queues = new Queue [nbrAgents];
		QueueOutputPipeInterface[] pipes = AllTests.createQueueNetwork(queues, graph, useTCP);

		// Listen for statistics messages
		Queue myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(this);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		
		// Create the listeners
		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];

			if (useXML) { // use the XML-based constructor
				// Create the description of the parameters
				parameters = new Element ("module");
				parameters.setAttribute("combination", combination);
				
				// Instantiate the listener using reflection
				XCSPparser<V, U> subprob = parser.getSubProblem(agent);
				queue.setProblem(subprob);
				Class<?> parTypes[] = new Class[2];
				parTypes = new Class[2];
				parTypes[0] = DCOPProblemInterface.class;
				parTypes[1] = Element.class;
				Constructor<?> constructor = ASODPOPBinaryDomains.class.getConstructor(parTypes);
				Object[] args = new Object[2];
				args[0] = subprob;
				args[1] = parameters;
				queue.addIncomingMessagePolicy((ASODPOPBinaryDomains<V, U>) constructor.newInstance(args));
				
			} else { // use the alternative constructor 
				
				// Create the subproblem
				DCOPProblemInterface<V, U> subprobTmp = parser.getSubProblem(agent);
				Map<String, V[]> domains = new HashMap<String, V[]> ();
				for (String var : subprobTmp.getVariables()) 
					domains.put(var, subprobTmp.getDomain(var));
				List< ? extends UtilitySolutionSpace<V, U> > spaces = subprobTmp.getSolutionSpaces();
				Problem<V, U> subprob = new Problem<V, U> (agent, subprobTmp.getOwners(), domains, spaces, true);

				// Instantiate the ADOPT module
				queue.addIncomingMessagePolicy(new ASODPOPBinaryDomains<V, U> (subprob));
			}
		}
		
		startUTILpropagation(graph, dfs, queues);
		
		// Wait until all agents have sent their outputs
		long start = System.currentTimeMillis();
		while (true) {
			synchronized (nbrMsgsRemaining_lock) {
				if (nbrMsgsRemaining == 0) {
					break;
				} else if (nbrMsgsRemaining < 0) {
					fail("Received too many output messages from the protocol");
				} else if (System.currentTimeMillis() - start > 60000) {
					fail("Timeout");
				}
			}
		}
		
		// Check that the assignments found by the protocol are indeed optimal
		
		Solution<V, U> solution = solver.solve(problem);
		UtilitySolutionSpace<V, U> realUtil = parser.getUtility(assignments);
		assertEquals (solution.getUtility(), realUtil.getUtility(0));
		
		// Properly close the pipes
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		myQueue.end();
	}
	
	/** Sends messages to the queues to initiate ASODPOP
	 * @param graph 		the constraint graph
	 * @param dfs 			the corresponding DFS (for each node in the graph, the relationships of this node)
	 * @param queues 		the array of queues, indexed by the clusters in the graph
	 */
	public static < V extends Addable<V>, U extends Addable<U> > void 
	startUTILpropagation(RandGraphFactory.Graph graph, Map< String, DFSview<V, U> > dfs, Queue[] queues) {
		
		// To every agent, send its part of the DFS and extract from the problem definition the constraint it is responsible for enforcing
		int nbrAgents = graph.clusters.size();
		for (int i = 0; i < nbrAgents; i++) {
			Queue queue = queues[i];
			// Send the start message to this agent
			queue.sendMessageToSelf(new Message (AgentInterface.START_AGENT));

			// Extract the agent's part of the DFS and the constraint it is responsible for enforcing
			List<String> variables = graph.clusters.get(i);
			for (String var : variables) {
				// Extract and send the relationships for this variable
				queue.sendMessageToSelf(new DFSgeneration.MessageDFSoutput<V, U> (var, dfs.get(var)));
			}
		}
	}
	
	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (1);
		types.add(ASODPOPBinaryDomains.OUTPUT_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		if(msg.getType().equals(ASODPOPBinaryDomains.OUTPUT_MSG_TYPE)) {
			VALUEpropagation.AssignmentMessage<V, U> msgCast = (VALUEpropagation.AssignmentMessage<V, U>)msg;
			assignments.put(msgCast.getVariable(), msgCast.getValue());
			// Decrement the number of messages we are still waiting for
			synchronized (nbrMsgsRemaining_lock) {
				nbrMsgsRemaining--;
			}
		}
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) { }
}
