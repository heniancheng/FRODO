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

package frodo2.algorithms.adopt.test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.adopt.ADOPT;
import frodo2.algorithms.adopt.Preprocessing;
import frodo2.algorithms.dpop.test.UTILpropagationTest;
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
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/**
 * This class is used to test the workings of the ADOPT listener
 * @author Brammert Ottens, Thomas Leaute
 */
public class testADOPT extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 7;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 25;

	/** Maximum number of agents */
	private final int maxNbrAgents = 7;
	
	/** The number of output messages remaining to be received */
	private int nbrMsgsRemaining;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** List of queues corresponding to the different agents */
	private Queue[] queues;
	
	/** The parameters for adopt*/
	private Element parameters;
	
	/** Random graph used to generate a constraint graph */
	private RandGraphFactory.Graph graph;
	
	/** The DFS corresponding to the random graph */
	private Map< String, DFSview<AddableInteger, AddableInteger> > dfs;
	
	/** For each variable, the constraint it is responsible for enforcing */
	private Map< String, UtilitySolutionSpace<AddableInteger, AddableInteger> > hypercubes;
	
	/** The parser */
	private XCSPparser<AddableInteger, AddableInteger> parser;

	/** The queue to which this class is registered as a listener */
	Queue myQueue;

	/** The class of utility values */
	private Class<? extends Addable<?>> utilClass;

	/** The version of ADOPT */
	private String version;

	/** Whether to use TCP pipes or shared memory pipes */
	private boolean useTCP;

	/** Whether to use the XML-based constructor */
	private boolean useXML;
	
	/** Constructor that instantiates a test only for the input method
	 * @param utilClass 	the class of utility values
	 * @param version 		the version of ADOPT
	 * @param useTCP 		whether to use TCP pipes or shared memory pipes
	 * @param useXML 		whether to use the XML-based constructor
	 */
	public testADOPT(Class< ? extends Addable<?> > utilClass, String version, boolean useTCP, boolean useXML) {
		super ("test");
		this.utilClass = utilClass;
		this.version = version;
		this.useTCP = useTCP;
		this.useXML = useXML;
	}
	
	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	@SuppressWarnings("unchecked")
	protected void setUp() throws Exception {
		super.setUp();
		
		// generate the problem graph and the structure
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		this.parser = new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, false, +1));
		parser.setUtilClass((Class<AddableInteger>) this.utilClass);		
		dfs = UTILpropagationTest.computeDFS(graph, this.parser);
		
//		System.out.println(graph);
//		System.out.println(DFSgeneration.dfsToString(dfs));

		parameters = new Element ("module");
		parameters.setAttribute("version", ADOPT.Original.class.getName());
	}

	/** 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		graph = null;
		dfs = null;
		this.parser = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		this.hypercubes = null;
		this.parameters = null;
	}
	
	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for ADOPT");
		
		TestSuite testTmp = new TestSuite ("Tests for the ADOPT propagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new testADOPT (AddableInteger.class, ADOPT.Original.class.getName(), false, true), 20000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ADOPT propagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new testADOPT (AddableInteger.class, ADOPT.Original.class.getName(), true, true), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the ADOPT propagation protocol using shared memory pipes and integer utilities and the alternative constructor");
		testTmp.addTest(new RepeatedTest (new testADOPT (AddableInteger.class, ADOPT.Original.class.getName(), false, false), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** Runs a random test
	 * @throws Exception 	if an error occurs
	 */
	@SuppressWarnings("unchecked")
	public void test () throws Exception {
		
		int nbrAgents = graph.clusters.size();
		int nbrVars = graph.nodes.size();
		nbrMsgsRemaining = nbrVars; // one AssignmentsMessage per var
		
		// Create the queue network
		queues = new Queue [nbrAgents];
		QueueOutputPipeInterface[] pipes = AllTests.createQueueNetwork(queues, graph, useTCP);

		// Listen for statistics messages
		myQueue = new Queue (false);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		ADOPT<AddableInteger, AddableInteger> statsGatherer = new ADOPT<AddableInteger, AddableInteger> (null, parser);
		statsGatherer.setSilent(true);
		statsGatherer.getStatsFromQueue(myQueue);
		myQueue.addIncomingMessagePolicy(this);
		
		// Create the listeners
		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];

			if (useXML) { // use the XML-based constructor
				
				// Create the parameters for the preprocessing phase
				Element parameters = new Element ("module");
				parameters.setAttribute("heuristic", Preprocessing.SimpleHeuristic.class.getName());
				
				// Create the preprocessing module
				XCSPparser<AddableInteger, AddableInteger> subprob = parser.getSubProblem(agent);
				queue.setProblem(subprob);
				Class<?> parTypes[] = new Class[2];
				parTypes[0] = DCOPProblemInterface.class;
				parTypes[1] = Element.class;
				Constructor<?> constructor = Preprocessing.class.getConstructor(parTypes);
				Object[] args = new Object[2];
				args[0] = subprob;
				args[1] = parameters;
				queue.addIncomingMessagePolicy((Preprocessing<AddableInteger, AddableInteger>) constructor.newInstance(args));

				// Create the description of the parameters
				parameters = new Element ("module");
				parameters.setAttribute("version", version);

				// Instantiate the listener using reflection
				parTypes = new Class[2];
				parTypes[0] = DCOPProblemInterface.class;
				parTypes[1] = Element.class;
				constructor = ADOPT.class.getConstructor(parTypes);
				args = new Object[2];
				args[0] = subprob;
				args[1] = parameters;
				queue.addIncomingMessagePolicy((ADOPT<AddableInteger, AddableInteger>) constructor.newInstance(args));
				
			} else { // use the alternative constructor 
				
				// Create the subproblem
				DCOPProblemInterface<AddableInteger, AddableInteger> subprobTmp = (DCOPProblemInterface<AddableInteger, AddableInteger>)parser.getSubProblem(agent);
				Map<String, AddableInteger[]> domains = new HashMap<String, AddableInteger[]> ();
				for (String var : subprobTmp.getVariables()) 
					domains.put(var, subprobTmp.getDomain(var));
				List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = subprobTmp.getSolutionSpaces();
				Problem<AddableInteger, AddableInteger> subprob = new Problem<AddableInteger, AddableInteger> (agent, subprobTmp.getOwners(), domains, spaces);

				// Create the preprocessing module
				queue.addIncomingMessagePolicy(new Preprocessing<AddableInteger, AddableInteger> (subprob, Preprocessing.SimpleHeuristic.class.getName()));

				// Instantiate the ADOPT module
				queue.addIncomingMessagePolicy(new ADOPT<AddableInteger, AddableInteger> (subprob, version, false));

			}
		}
		
		List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = parser.getSolutionSpaces();
		hypercubes = startADOPT(graph, dfs, queues, spaces, parser);
		
		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (nbrMsgsRemaining == 0) {
					break;
				} else if (nbrMsgsRemaining < 0) {
					fail("Received too many output messages from the protocol");
				} else if (! this.finished.await(240, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		// Check that the assignments found by the protocol are indeed optimal
		
		// Compute optimal utility value of each connected component while checking separators
		AddableInteger optUtil = new AddableInteger (0);
		for (String var : graph.nodes) {
			
			// Check if the variable is a root
			if (dfs.get(var).getParent() == null) {
				
				// Simulate the UTIL propagation to compute the optimal utility value for this root's DFS tree
				Hypercube<AddableInteger, AddableInteger> hypercube = simulateUTIL (var);
				optUtil = optUtil.add(hypercube.getUtility(0));
			}
		}
		UtilitySolutionSpace<AddableInteger, AddableInteger> sum = parser.getUtility(statsGatherer.getOptAssignments());
//		System.out.println("real opt util = " + optUtil + " and ADOPT found " + optTotalUtil);
//		System.out.println("Number of messages sent = " + messageCounter);
//		if(!optUtil.equals(sum.getUtility(0)) || !optUtil.equals(optTotalUtil)) {
//			System.out.println("CHECK : " + sum.getUtility(0));
//		}
		assertEquals(optUtil, sum.getUtility(0));
		assertEquals(optUtil, statsGatherer.getTotalOptUtil());
		
		// Properly close the pipes
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		myQueue.end();
		
	}
	
	/** Sends messages to the queues to initiate ADOPT
	 * @param <U> 			the type used for utility values
	 * @param graph 		the constraint graph
	 * @param dfs 			the corresponding DFS (for each node in the graph, the relationships of this node)
	 * @param queues 		the array of queues, indexed by the clusters in the graph
	 * @param constraints 	the hypercubes in the problem definition
	 * @param parser 		the parser for the random problem
	 * @return for each variable, the constraint it is responsible for enforcing
	 */
	public static <U extends Addable<U> > Map < String, UtilitySolutionSpace<AddableInteger, U> > startADOPT (RandGraphFactory.Graph graph, 
			Map< String, DFSview<AddableInteger, U> > dfs, Queue[] queues, 
			List< ? extends UtilitySolutionSpace<AddableInteger, U> > constraints, XCSPparser<AddableInteger, U> parser) {
		
		Map< String, UtilitySolutionSpace<AddableInteger, U> > hypercubes = 
			new HashMap< String, UtilitySolutionSpace<AddableInteger, U> > (graph.nodes.size());
		
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
				
				DFSview<AddableInteger, U> relationships = dfs.get(var);

				List<String> children = relationships.getChildren();
				List<String> pseudo = relationships.getAllPseudoChildren();
				HashSet<String> varsBelow = new HashSet<String> (children);
				varsBelow.addAll(pseudo);
				relationships.setSpaces(parser.getSolutionSpaces(var, varsBelow));

				// Extract and send the relationships for this variable
				queue.sendMessageToSelf(new DFSgeneration.MessageDFSoutput<AddableInteger, U> (var, relationships));
				
				// Extract the constraint this variable is responsible for enforcing
				UtilitySolutionSpace<AddableInteger, U> join = null;
				for (ListIterator< ? extends UtilitySolutionSpace<AddableInteger, U> > iterator = constraints.listIterator(); iterator.hasNext(); ) {
					UtilitySolutionSpace<AddableInteger, U> space = iterator.next();
					
					// Disregard the space if it does not involve var
					if (! Arrays.asList(space.getVariables()).contains(var)) 
						continue;
					
					// Disregard the space if it involves any another variable lower than var in the DFS
					boolean isLowestVar = true;
					for (String otherVar : space.getVariables()) {
						if (children.contains(otherVar) || pseudo.contains(otherVar)) {
							isLowestVar = false;
							break;
						}
					}

					if (isLowestVar) { // record the space
						if (join == null) {
							join = space;
						} else {
							join = join.join(space);
						}
						iterator.remove();
					}
				}
				hypercubes.put(var, join);
				
			}
		}
		
		return hypercubes;
	}
	
	/** Simulates UTIL propagation on the subtree rooted at the input variable, while checking separators 
	 * @param var root variable
	 * @return the UTIL message that the input variable sends to its parent (if any)
	 */
	private Hypercube<AddableInteger, AddableInteger> simulateUTIL (String var) {
		
		List<String> children = dfs.get(var).getChildren();
		
		if (children.size() == 0) { // leaf variable
			
			// Check if the variable is unconstrained
			UtilitySolutionSpace<AddableInteger, AddableInteger> space = hypercubes.get(var);
			if (space == null) 
				return new ScalarHypercube<AddableInteger, AddableInteger> (new AddableInteger (0), AddableInteger.PlusInfinity.PLUS_INF, new AddableInteger[0].getClass());
			
			// Project variable out of its hypercube and return result
			return (Hypercube<AddableInteger, AddableInteger>) space.blindProject(var, false);
			
		} else { // non-leaf variable

			// Compute the hypercube received from each child and process it to compute the join
			UtilitySolutionSpace<AddableInteger, AddableInteger> join = hypercubes.get(var);
			for (String child : children) {
				Hypercube<AddableInteger, AddableInteger> hypercube = simulateUTIL(child);
				
				// Check that the separator matches the one computed by the protocol
				String[] separator = hypercube.getVariables().clone();
				Arrays.sort(separator);
//				assertTrue(Arrays.equals(separator, separators.get(child)));
				
				// Incrementally compute the join
				if (join == null) {
					join = hypercube;
				} else 
					join = (Hypercube<AddableInteger, AddableInteger>) join.join(hypercube);
			}
			
			// Project out the variable and return the result
			return (Hypercube<AddableInteger, AddableInteger>) join.blindProject(var, false);
		}
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return Arrays.asList(ADOPT.OUTPUT_MSG_TYPE);
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		// Decrement the number of messages we are still waiting for
		this.finished_lock.lock();
		if (--nbrMsgsRemaining <= 0) 
			this.finished.signal();
		this.finished_lock.unlock();

	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) { }
	
}
