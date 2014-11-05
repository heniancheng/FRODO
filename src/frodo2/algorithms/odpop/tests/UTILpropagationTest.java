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

package frodo2.algorithms.odpop.tests;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.odpop.UTILpropagation;
import frodo2.algorithms.odpop.UTILpropagationFullDomain;
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
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test for the ODPOP UTIL propagation module
 * @author Brammert Ottens, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 *
 */
public class UTILpropagationTest < V extends Addable<V>, U extends Addable<U> > extends TestCase implements IncomingMsgPolicyInterface<String> {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 10;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 15;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;
	
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
	
	/** The optimal utility reported by the UTILpropagation*/
	private U optUtil;

	/** Whether to use TCP or SharedMemory pipes */
	private boolean useTCP;

	/** Whether to test the XML-enabled constructor */
	private boolean useXML;

	/** The queue this class listens to */
	private Queue myQueue;

	/** The class of variable values */
	private Class<V> domClass;
	
	/** The class of utility values */
	private Class<U> utilClass;
	
	/** Constructor that instantiates a test only for the input method
	 * @param useTCP 		whether to use TCP pipes or shared memory pipes
	 * @param useXML 		whether to use the XML-based constructor
	 * @param domClass 		The class of variable values
	 * @param utilClass 	the class of utility values
	 */
	public UTILpropagationTest(boolean useTCP, boolean useXML, Class<V> domClass, Class<U> utilClass) {
		super ("test");
		this.useTCP = useTCP;
		this.useXML = useXML;
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
		problem = AllTests.generateProblem(graph, true, 0);
		parser = new XCSPparser<V, U> (problem);
		parser.setDomClass(domClass);
		this.parser.setUtilClass(this.utilClass);
		dfs = frodo2.algorithms.dpop.test.UTILpropagationTest.computeDFS(graph, parser);
		
		solver = new DPOPsolver<V, U> (this.domClass, this.utilClass);
		
		parameters = new Element ("module");

		optUtil = this.utilClass.newInstance().getZero();
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
		this.optUtil = null;
		this.parameters = null;
		this.parser = null;
		this.problem = null;
		this.solver = null;
	}
	
	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for UTILpropagation");
		
		TestSuite testTmp = new TestSuite ("Tests for the UTILpropagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger, AddableInteger> (false, true, AddableInteger.class, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTILpropagation protocol using shared memory pipes and integer utilities and real-valued variables");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableReal, AddableInteger> (false, true, AddableReal.class, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTILpropagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger, AddableInteger> (true, true, AddableInteger.class, AddableInteger.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTILpropagation protocol using shared memory pipes and integer utilities and the alternative constructor");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger, AddableInteger> (false, false, AddableInteger.class, AddableInteger.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTILpropagation protocol using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger, AddableReal> (false, true, AddableInteger.class, AddableReal.class), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** Runs a random test
	 * @throws IOException 					if the method fails to create pipes
	 * @throws NoSuchMethodException 		if the ADOPT class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws IllegalAccessException 		if the ADOPT class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InstantiationException 		if the instantiation of ADOPT failed
	 * @throws IllegalArgumentException 	if an error occurs in passing arguments to the constructor of ADOPT
	 * @throws ClassNotFoundException 		if the utility class is not found
	 * @throws InvocationTargetException 	if the UTILpropagation constructor throws an exception
	 */
	@SuppressWarnings("unchecked")
	public void test () 
	throws IOException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException {
		int nbrAgents = graph.clusters.size();
		
		nbrMsgsRemaining = graph.components.size(); // One OptUtilMessage per root
		
		// Create the queue network
		queues = new Queue [nbrAgents];
		QueueOutputPipeInterface[] pipes = AllTests.createQueueNetwork(queues, graph, useTCP);

		// Listen for statistics messages
		myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(this);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		
		// Create the listeners
		XCSPparser<V, U> parser = new XCSPparser<V, U> (problem);
		parser.setDomClass(domClass);
		parser.setUtilClass(this.utilClass);
		
		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];

			if (useXML) { // use the XML-based constructor

				// Instantiate the listener using reflection
				XCSPparser<V, U> subprob = parser.getSubProblem(agent);
				queue.setProblem(subprob);
				Class<?> parTypes[] = new Class[2];
				parTypes = new Class[2];
				parTypes[0] = DCOPProblemInterface.class;
				parTypes[1] = Element.class;
				Constructor<?> constructor = UTILpropagation.class.getConstructor(parTypes);
				Object[] args = new Object[2];
				args[0] = subprob;
				args[1] = parameters;
				queue.addIncomingMessagePolicy((UTILpropagation<V, U>) constructor.newInstance(args));
				
			} else { // use the alternative constructor 
				
				// Create the subproblem
				DCOPProblemInterface<V, U> subprobTmp = parser.getSubProblem(agent);
				Map<String, V[]> domains = new HashMap<String, V[]> ();
				for (String var : subprobTmp.getVariables()) 
					domains.put(var, subprobTmp.getDomain(var));
				List< ? extends UtilitySolutionSpace<V, U> > spaces = subprobTmp.getSolutionSpaces();
				Problem<V, U> subprob = new Problem<V, U> (agent, subprobTmp.getOwners(), domains, spaces, parser.maximize());

				queue.addIncomingMessagePolicy(new UTILpropagation<V, U> (subprob));

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
				} else if (System.currentTimeMillis() - start > 5000) {
					fail("Timeout");
				}
			}
		}
		
		// Check that the utility found by the protocol is indeed the optimal utility
		
		Solution<V, U> solution = solver.solve(problem);
		
		assertEquals(solution.getUtility(), optUtil);
		
		// Properly close the pipes
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		myQueue.end();
	}
	
	/** Sends messages to the queues to initiate O-DPOP
	 * @param graph 		the constraint graph
	 * @param dfs 			the corresponding DFS (for each node in the graph, the relationships of this node)
	 * @param queues 		the array of queues, indexed by the clusters in the graph
	 */
	public static < V extends Addable<V>, U extends Addable<U> > void startUTILpropagation(RandGraphFactory.Graph graph, Map< String, DFSview<V, U> > dfs, Queue[] queues) {
		
		// To every agent, send its part of the DFS and extract from the problem definition the constraint it is responsible for enforcing
		int nbrAgents = graph.clusters.size();
		for (int i = 0; i < nbrAgents; i++) {
			Queue queue = queues[i];
			// Send the start message to this agent
			Message msg = new Message (AgentInterface.START_AGENT);
			queue.sendMessageToSelf(msg);

			// Extract the agent's part of the DFS and the constraint it is responsible for enforcing
			List<String> variables = graph.clusters.get(i);
			for (String var : variables) {
				// Extract and send the relationships for this variable
				msg = new DFSgeneration.MessageDFSoutput<V, U> (var, dfs.get(var));
				queue.sendMessageToSelf(msg);
			}
		}
	}
	
	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (1);
		types.add(UTILpropagationFullDomain.OPT_UTIL_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		if(msg.getType().equals(UTILpropagationFullDomain.OPT_UTIL_MSG_TYPE)) {
			UTILpropagation.OptUtilMessage<U> msgCast = (UTILpropagation.OptUtilMessage<U>)msg;
			optUtil = optUtil.add(msgCast.getUtility());
			// Decrement the number of messages we are still waiting for
			synchronized (nbrMsgsRemaining_lock) {
				nbrMsgsRemaining--;
			}
		}
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) { }
	
}
