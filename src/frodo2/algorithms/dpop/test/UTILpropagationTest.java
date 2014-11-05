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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
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
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.tests.DFSgenerationTest;
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
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** JUnit test for the class UTILpropagation
 * @author Thomas Leaute
 * @param <U> 	the type used for utility values
 */
public class UTILpropagationTest < U extends Addable<U> > extends TestCase {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	protected final int maxNbrVars = 10;
	
	/** Maximum number of edges in the random graph */
	protected final int maxNbrEdges = 40;

	/** Maximum number of agents */
	protected final int maxNbrAgents = 10;

	/** List of queues corresponding to the different agents */
	private Queue[] queues;
	
	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;
	
	/** The DFS corresponding to the random graph */
	protected Map< String, DFSview<AddableInteger, U> > dfs;
	
	/** For each variable, the list of variables in its separator */
	private Map< String, String[] > separators;
	
	/** The parameters for the module under test */
	protected Element parameters;

	/** Whether to use TCP pipes or shared memory pipes */
	private boolean useTCP;

	/** Whether to use the XML-based constructor */
	private boolean useXML;

	/** The class to use for utility values */
	private Class<U> utilClass;

	/** Whether to optimize runtime or constraint checks */
	private boolean minNCCCs;
	
	/** Whether we should maximize or minimize */
	protected boolean maximize;
	
	/** The desired sign for the utilities (if 0, utilities can be either sign) */
	protected int sign = 0;
	
	/** Constructor that instantiates a test only for the input method
	 * @param method 		test method
	 */
	public UTILpropagationTest(String method) {
		super (method);
	}

	/** Constructor 
	 * @param useTCP 				whether to use TCP pipes or shared memory pipes
	 * @param useXML 				whether to use the XML-based constructor
	 * @param utilClass 			the class to use for utility values
	 * @param minNCCCs 				whether to optimize runtime or constraint checks
	 */
	public UTILpropagationTest(boolean useTCP, boolean useXML, Class<U> utilClass, boolean minNCCCs) {
		super ("test");
		this.useTCP = useTCP;
		this.useXML = useXML;
		this.utilClass = utilClass;
		this.minNCCCs = minNCCCs;
		
		// Decide whether we should maximize or minimize
		maximize = (Math.random() < 0.5);
	}

	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for UTILpropagation");
		
		TestSuite testTmp = new TestSuite ("Tests for the method computeDFS");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger> ("testComputeDFS"), 1000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger> (false, false, AddableInteger.class, false), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol with XML support using shared memory pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger> (false, true, AddableInteger.class, false), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger> (true, false, AddableInteger.class, false), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol with XML support using TCP pipes and integer utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger> (true, true, AddableInteger.class, false), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableReal> (false, false, AddableReal.class, false), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol with XML support using shared memory pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableReal> (false, true, AddableReal.class, false), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableReal> (true, false, AddableReal.class, false), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol with XML support using TCP pipes and real utilities");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableReal> (true, true, AddableReal.class, false), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for the UTIL propagation protocol with XML support using shared memory pipes and integer utilities and the minNCCCs option");
		testTmp.addTest(new RepeatedTest (new UTILpropagationTest<AddableInteger> (false, true, AddableInteger.class, true), 200));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}

	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		
		parameters = new Element ("module");
		
		assertTrue (useXML || !minNCCCs); // the alternative constructor currently does not support the minNCCCs feature
		parameters.setAttribute("minNCCCs", Boolean.toString(minNCCCs));
		
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
	}
	
	/** Ends all queues
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		dfs = null;
		separators = null;
		if (queues != null) 
			for (Queue queue : queues) 
				queue.end();
		queues = null;
		parameters = null;
	}
	
	/** A test method that tests the test helper method \a computeDFS() */
	public void testComputeDFS () {
		XCSPparser<AddableInteger, U> parser = new XCSPparser<AddableInteger, U> (AllTests.generateProblem(graph, graph.nodes.size(), maximize, sign));
		dfs = computeDFS(graph, parser);
		DFSgenerationTest.checkDFS(dfs, graph.neighborhoods, null);
	}
	
	/** Tests the UTIL propagation protocol on a random graph
	 * @throws Exception if an error occurs
	 */
	public void test () throws Exception {
		Listener listener = newListenerInstance(useTCP, useXML, this.parameters);
		listener.waitForOutputs();
	}
	
	/** Computes a DFS tree for the input graph
	 * 
	 * The DFS tree is a represented by a list, with one entry per node. 
	 * Each entry is a map that associates lists of nodes to various possible relationships. 
	 * For instance, the list for the relationship Relationship.CHILDREN will contain the node's child nodes. 
	 * @param graph 	a graph
	 * @param problem 	the problem
	 * @return for each node in the graph, the lists of nodes corresponding to the different possible relationships
	 */
	public static < V extends Addable<V>, U extends Addable<U> > Map< String, DFSview<V, U> > 
	computeDFS (Graph graph, DCOPProblemInterface<V, U> problem) {
		return computeDFS (graph, problem, false);
	}
	
	/** Computes a DFS tree for the input graph
	 * 
	 * The DFS tree is a represented by a list, with one entry per node. 
	 * Each entry is a map that associates lists of nodes to various possible relationships. 
	 * For instance, the list for the relationship Relationship.CHILDREN will contain the node's child nodes. 
	 * @param graph 			a graph
	 * @param problem 			the problem
	 * @param withSharedVars 	whether to take shared variables into account when parsing the spaces
	 * @return for each node in the graph, the lists of nodes corresponding to the different possible relationships
	 */
	public static < V extends Addable<V>, U extends Addable<U> > Map< String, DFSview<V, U> > 
	computeDFS (Graph graph, DCOPProblemInterface<V, U> problem, boolean withSharedVars) {
		
		int nbrNodes = graph.nodes.size();
		
		// Initialize the output
		Map< String, DFSview<V, U> > out = new HashMap< String, DFSview<V, U> > (nbrNodes);
		for (String node : graph.nodes) 
			out.put(node, new DFSview<V, U> (node));
		
		// Initialize the lists of open neighbors
		Map< String, List<String> > open = new HashMap< String, List<String> > (nbrNodes);
		for (String node : graph.nodes) {
			open.put(node, new ArrayList<String> (graph.neighborhoods.get(node)));
		}
		
		// The current path in the DFS traversal
		LinkedList<String> path = new LinkedList<String> ();
		
		// DFS-traverse each component of the graph
		for (List<String> component : graph.components) {
						
			// Choose the first node of the component as its root
			path.add(component.get(0));
			
			while (! path.isEmpty()) {
								
				// Expand the last node in the path
				String curr = path.getLast();
				DFSview<V, U> currRel = out.get(curr);
				
				// For each of the node's open neighbors:
				Iterator<String> iterator = open.get(curr).iterator();
				while ( true ) {
					
					if (! iterator.hasNext()) {
						// We have expanded all open neighbors; backtrack
						path.removeLast();
						
						// Compute the spaces this variable is responsible for enforcing
						HashSet<String> varsBelow = new HashSet<String> (currRel.getChildren());
						varsBelow.addAll(currRel.getAllPseudoChildren());
						currRel.setSpaces(problem.getSolutionSpaces(curr, withSharedVars, varsBelow)); /// @bug the spaces must come for the agent's subproblem to correctly count NCCCs
						
						break;
					}

					String neighbor = iterator.next();
					DFSview<V, U> neighRel = out.get(neighbor);

					// Check whether it is a pseudo-parent of curr
					if (path.contains(neighbor)) {
						currRel.addPseudoParent(neighbor);
						neighRel.addPseudoChild(curr);

						// Update the lists of open neighbors
						iterator.remove();
						open.get(neighbor).remove(curr);
					}

					else { // it must be a new child of curr
						currRel.addChild(neighbor);
						neighRel.setParent(curr, problem.getOwner(curr));
						
						// Update the lists of open neighbors
						iterator.remove();
						open.get(neighbor).remove(curr);
						
						// Expand the neighbor
						path.add(neighbor);
						break;
					}
				}
			}
		}
				
		return out;
	}

	/** Sends messages to the queues to initiate the UTIL propagation
	 * @param <U> 			the type used for utility values
	 * @param graph 		the constraint graph
	 * @param dfs 			the corresponding DFS (for each node in the graph, the relationships of this node)
	 * @param queues 		the array of queues, indexed by the clusters in the graph
	 * @param constraints 	the hypercubes in the problem definition
	 * @return for each variable, the constraint it is responsible for enforcing
	 */
	public static < U extends Addable<U> > Map < String, UtilitySolutionSpace<AddableInteger, U> > startUTIL (RandGraphFactory.Graph graph, 
			Map< String, DFSview<AddableInteger, U> > dfs, Queue[] queues, 
			List< ? extends UtilitySolutionSpace<AddableInteger, U> > constraints) {
		
		Map< String, UtilitySolutionSpace<AddableInteger, U> > hypercubes = 
			new HashMap< String, UtilitySolutionSpace<AddableInteger, U> > (graph.nodes.size());
		
		// To every agent, send its part of the DFS and extract from the problem definition the constraint it is responsible for enforcing
		int nbrAgents = graph.clusters.size();
		for (int i = 0; i < nbrAgents; i++) {
			Queue queue = queues[i];
			
			// Tell the DomainsSharing to start 
			Message msg = new Message (AgentInterface.START_AGENT);
			queue.sendMessageToSelf(msg);
			
			// Extract the agent's part of the DFS and the constraints it is responsible for enforcing
			List<String> variables = graph.clusters.get(i);
			for (String var : variables) {
				
				// Extract and send the relationships for this variable
				DFSview<AddableInteger, U> relationships = dfs.get(var);
				msg = new DFSgeneration.MessageDFSoutput<AddableInteger, U> (var, relationships);
				queue.sendMessageToSelf(msg);
				
				// Extract the constraint this variable is responsible for enforcing
				UtilitySolutionSpace<AddableInteger, U> join = null;
				List<String> children = relationships.getChildren();
				List<String> pseudo = relationships.getAllPseudoChildren();
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

	/** Creates a new Listener
	 * @param useTCP 		\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param useXML 		whether we should use the constructor that takes in XML elements or the manual constructor
	 * @param parameters 	the parameters for the module under test
	 * @return 				the new listener
	 * @throws Exception 	if an error occurs
	 */
	protected Listener newListenerInstance(boolean useTCP, boolean useXML, Element parameters) 
	throws Exception {
		return new Listener (useTCP, useXML, parameters, UTILpropagation.class, false);
	}

	/** The listener that checks the messages sent by the UTILpropagation listeners */
	protected class Listener implements IncomingMsgPolicyInterface<String> {
		
		/** The number of output messages remaining to be received from the UTIL propagation protocol */
		protected Integer nbrMsgsRemaining;
		
		/** Used to make the test thread wait */
		protected final ReentrantLock finished_lock = new ReentrantLock ();
		
		/** Used to wake up the test thread when all agents have finished */
		protected final Condition finished = finished_lock.newCondition();

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

		/** The stats gatherer */
		protected UTILpropagation<AddableInteger, U> statsGatherer;
		
		/** The parser for the problem */
		protected XCSPparser<AddableInteger, U> parser;

		/** Constructor that tests the UTIL propagation protocol on a random DFS 
		 * @param useTCP 						\c true whether TCP pipes should be used instead of QueueIOPipes
		 * @param useXML 						whether we should use the constructor that takes in XML elements or the manual constructor
		 * @param parameters 					the parameters for the module under test
		 * @param listenerClass 				the class of the listener under test
		 * @param withAnonymVars 				\c true if anonymous variables should be taken into account when parsing the problem
		 * @throws IOException 					if the method fails to create pipes
		 * @throws NoSuchMethodException 		if the UTILpropagation class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
		 * @throws InvocationTargetException 	if the UTILpropagation constructor throws an exception
		 * @throws IllegalAccessException 		if the UTILpropagation class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
		 * @throws InstantiationException 		if the instantiation of UTILpropagation failed
		 * @throws IllegalArgumentException 	if an error occurs in passing arguments to the constructor of UTILpropagation
		 * @throws ClassNotFoundException 		if the utility class is unknown
		 */
		public Listener (boolean useTCP, boolean useXML, Element parameters, Class< ? extends StatsReporter > listenerClass, boolean withAnonymVars) 
		throws IOException, NoSuchMethodException, IllegalArgumentException, 
		InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
			
			int nbrAgents = graph.clusters.size();
			int nbrVars = graph.nodes.size();
			
			// Compute the number of messages to expect: 
			// one SeparatorMessage per non-root var + one OptUtilMessage per root
			nbrMsgsRemaining = nbrVars;
			
			// Initialize the separators
			separators = new HashMap< String, String[] > (nbrVars);
			
			// Create the queue network
			queues = new Queue [nbrAgents];
			pipes = AllTests.createQueueNetwork(queues, graph, useTCP);
			
			// Create the parser
			parser = new XCSPparser<AddableInteger, U> (AllTests.generateProblem(graph, (withAnonymVars ? graph.nodes.size() : 0), maximize, sign));
			parser.setUtilClass((Class<U>) utilClass);
			dfs = computeDFS(graph, parser, withAnonymVars);

			// Listen for statistics messages
			myQueue = new Queue (false);
			myQueue.addIncomingMessagePolicy(UTILpropagation.OPT_UTIL_MSG_TYPE, this);
			QueueIOPipe myPipe = new QueueIOPipe (myQueue);
			for (Queue queue : queues) 
				queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
			statsGatherer = new UTILpropagation<AddableInteger, U> (null, parser);
			statsGatherer.setSilent(true);
			statsGatherer.getStatsFromQueue(myQueue);
			
			// Create the listeners
			spaces = parser.getSolutionSpaces(withAnonymVars);
			if (useXML) {
				for (String agent : parser.getAgents()) {
					Queue queue = queues[Integer.parseInt(agent)];
					
					XCSPparser<AddableInteger, U> subProblem = parser.getSubProblem(agent);
					queue.setProblem(subProblem);
					
					// Set up the DomainsSharing module
					this.setUpPrelimModules(queue, subProblem);

					// Instantiate the listener using reflection
					Class<?> parTypes[] = new Class[2];
					parTypes[0] = DCOPProblemInterface.class;
					parTypes[1] = Element.class;
					Constructor<?> constructor = listenerClass.getConstructor(parTypes);
					Object[] args = new Object[2];
					args[0] = subProblem;
					args[1] = parameters;
					queue.addIncomingMessagePolicy((StatsReporter) constructor.newInstance(args));

					queue.addIncomingMessagePolicy(this);
				}
			}
			
			else { // use the manual constructor that does not take in XML elements
				
				for (String agent : parser.getAgents()) {
					Queue queue = queues[Integer.parseInt(agent)];
					
					// Generate the information about the subproblem
					DCOPProblemInterface<AddableInteger, U> subproblem = parser.getSubProblem(agent);
					List< ? extends UtilitySolutionSpace<AddableInteger, U> > spaces = subproblem.getSolutionSpaces(withAnonymVars);
					Map<String, AddableInteger[]> domains = new HashMap<String, AddableInteger[]> ();
					for (String var : subproblem.getAllVars()) 
						domains.put(var, subproblem.getDomain(var));
					
					Constructor<?> constructor = listenerClass.getConstructor(DCOPProblemInterface.class);
					Problem<AddableInteger, U> newSubProb = new Problem<AddableInteger, U> (agent, subproblem.getOwners(), domains, spaces, parser.maximize());
					newSubProb.setUtilClass((Class<U>) utilClass);
					queue.addIncomingMessagePolicy((StatsReporter) constructor.newInstance(newSubProb));
					
					queue.addIncomingMessagePolicy(this);
				}
			}
			
		}
		
		/** Sets up modules that are necessary for the module under test to work
		 * @param queue 		the queue to which the modules should be added
		 * @param subProblem 	the corresponding agent's subproblem
		 */
		protected void setUpPrelimModules(Queue queue, DCOPProblemInterface<AddableInteger, U> subProblem) { }

		/** Waits for the outputs of the module and checks their validity */
		public void waitForOutputs () {
			
			hypercubes = startUTIL(graph, dfs, queues, spaces);
			
			// Wait until all agents have sent their outputs
			while (true) {
				this.finished_lock.lock();
				try {
					if (nbrMsgsRemaining == 0) {
						break;
					} else if (nbrMsgsRemaining < 0) {
						fail("Received too many output messages from the protocol");
					} else if (! this.finished.await(10, TimeUnit.SECONDS)) {
						fail("Timeout");
					}
				} catch (InterruptedException e) {
					break;
				}
				this.finished_lock.unlock();
			}
			
			this.checkOutput();

			// Properly close the pipes
			for (QueueOutputPipeInterface pipe : pipes) {
				pipe.close();
			}
			myQueue.end();
		}

		/** Checks that the output of the module is correct */
		protected void checkOutput() {
			
			// Compute optimal utility value of each connected component while checking separators
			U optUtil = null;
			for (String var : graph.nodes) {
				
				// Check if the variable is a root
				if (dfs.get(var).getParent() == null) {
					
					// Simulate the UTIL propagation to compute the optimal utility value for this root's DFS tree
					Hypercube<AddableInteger, U> hypercube = simulateUTIL (var);
					if (optUtil == null) {
						optUtil = hypercube.getUtility(0);
					} else 
						optUtil = optUtil.add(hypercube.getUtility(0));
				}
			}
			assertEquals(optUtil, optTotalUtil);
			
			// Check that the assignments found by the protocol are indeed optimal: this is done in VALUEpropagationTest
		}

		/** Listens to the outputs of the UTIL propagation protocol 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
		 */
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (2);
			types.add(UTILpropagation.SEPARATOR_MSG_TYPE);
//			types.add(UTILpropagation.OPT_UTIL_MSG_TYPE);
			return types;
		}

		/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			String type = msg.getType();
			
			if (type.equals(UTILpropagation.SEPARATOR_MSG_TYPE)) { // the message contains information about a variable's separator
				UTILpropagation.SeparatorMessage msgCast = (UTILpropagation.SeparatorMessage) msg;
				String[] separator = msgCast.getSeparator();
				String[] sepCopy = new String [separator.length];
				System.arraycopy(separator, 0, sepCopy, 0, separator.length);
				Arrays.sort(sepCopy);
				synchronized (separators) {
					separators.put(msgCast.getChild(), sepCopy);
				}
			}
			
			else if (type.equals(UTILpropagation.OPT_UTIL_MSG_TYPE)) { // message sent by a root containing the optimal utility value
				
				UTILpropagation.OptUtilMessage<U> msgCast = (UTILpropagation.OptUtilMessage<U>) msg;
				
				synchronized (optTotalUtil_lock) {
					if (optTotalUtil == null) {
						optTotalUtil = msgCast.getUtility();
					} else 
						optTotalUtil = optTotalUtil.add(msgCast.getUtility());
				}
			}
			
			this.finished_lock.lock();
			if (--nbrMsgsRemaining <= 0) 
				this.finished.signal();
			this.finished_lock.unlock();
		}

		/** Does nothing
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) { }
		
		/** Simulates UTIL propagation on the subtree rooted at the input variable, while checking separators 
		 * @param var root variable
		 * @return the UTIL message that the input variable sends to its parent (if any)
		 */
		protected Hypercube<AddableInteger, U> simulateUTIL (String var) {
			
			List<String> children = dfs.get(var).getChildren();
			
			if (children.size() == 0) { // leaf variable
				
				// Check if the variable is unconstrained
				UtilitySolutionSpace<AddableInteger, U> space = hypercubes.get(var);
				if (space == null) 
					return new ScalarHypercube<AddableInteger, U> (parser.getZeroUtility(), (maximize ? parser.getMinInfUtility() : parser.getPlusInfUtility()), new AddableInteger [0].getClass());
				
				// Project variable out of its hypercube and return result
				return (Hypercube<AddableInteger, U>) space.blindProject(var, maximize);
				
			} else { // non-leaf variable

				// Compute the hypercube received from each child and process it to compute the join
				UtilitySolutionSpace<AddableInteger, U> join = hypercubes.get(var);
				for (String child : children) {
					Hypercube<AddableInteger, U> hypercube = simulateUTIL(child);
					
					// Check that the separator matches the one computed by the protocol
					String[] separator = hypercube.getVariables().clone();
					Arrays.sort(separator);
					assertTrue(Arrays.equals(separator, separators.get(child)));
					
					// Incrementally compute the join
					if (join == null) {
						join = hypercube;
					} else 
						join = (Hypercube<AddableInteger, U>) join.join(hypercube);
				}
				
				// Project out the variable and return the result
				return (Hypercube<AddableInteger, U>) join.blindProject(var, maximize);
			}
		}
		
	}
	
}
