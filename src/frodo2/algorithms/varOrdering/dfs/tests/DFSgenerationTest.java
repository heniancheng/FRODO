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

package frodo2.algorithms.varOrdering.dfs.tests;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import frodo2.algorithms.heuristics.LeastConnectedHeuristic;
import frodo2.algorithms.heuristics.MostConnectedHeuristic;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.BlindScoringHeuristic;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.NextChildChoiceHeuristic;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.ScoreBroadcastingHeuristic;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.tests.LeaderElectionMaxIDTest;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test for the class DFSgeneration
 * @author Thomas Leaute
 */
public class DFSgenerationTest extends TestCase implements IncomingMsgPolicyInterface<String> {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 15;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 100;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;
	
	/** Number of output messages expected per variable */
	protected int nbrOutputMessagesPerVar = 2;

	/** Current number of variables that still need to send their output of the DFS generation protocol */
	protected int remainingOutputs;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** List of queues corresponding to the different agents */
	private Queue[] queues;
	
	/** Output of the DFS generation protocol
	 * 
	 * For each variable, stores its relationships with neighboring variables
	 */
	protected Map< String, DFSview<AddableInteger, AddableInteger> > dfs = new HashMap< String, DFSview<AddableInteger, AddableInteger> > (maxNbrVars);

	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;
	
	/** Parser for the random XCSP problem */
	private XCSPparser<AddableInteger, AddableInteger> parser;

	/** One output pipe used to send messages to each queue */
	private QueueOutputPipeInterface[] pipes;

	/** Whether to use TCP or SharedMemory pipes */
	private boolean useTCP;

	/** Whether to use the XML-based constructor */
	private boolean useXML;

	/** The ScoringHeuristic */
	private Class< ? extends ScoringHeuristic<?> > heuristicClass;
	
	/** Constructor
	 * @param useTCP 		\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param useXML 		whether we should use the DFSgeneration constructor that takes in XML elements
	 * @param heuristic 	the class of the ScoringHeuristic used
	 */
	public DFSgenerationTest(boolean useTCP, boolean useXML, Class< ? extends ScoringHeuristic<?> > heuristic) {
		super ("testRandom");
		this.useTCP = useTCP;
		this.useXML = useXML;
		this.heuristicClass = heuristic;
	}

	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for DFSgeneration");
		
		TestSuite testTmp = new TestSuite ("Tests for DFS generation using shared memory pipes");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (false, false, VarNameHeuristic.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using shared memory pipes and Most Connected heuristicClass");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (false, false, MostConnectedHeuristic.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (true, false, VarNameHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using shared memory pipes with XML constructor");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (false, true, VarNameHeuristic.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes with XML constructor");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (true, true, VarNameHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes with XML constructor and Most Connected heuristicClass");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (true, true, MostConnectedHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes with XML constructor and Least Connected heuristicClass");
		testTmp.addTest(new RepeatedTest (new DFSgenerationTest (true, true, LeastConnectedHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		parser = new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, graph.nodes.size(), true));
	}
	
	/** Ends all queues
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		dfs.clear();
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		pipes = null;
		parser = null;
	}
	
	
	/** @return the class of the DFSgeneration module under test */
	protected Class<?> getDFSclass(){
		return DFSgeneration.class;
	}	
	
	
	/** Tests the DFS generation protocol on a random graph 
	 * @throws IOException 					if the method fails to create pipes
	 * @throws NoSuchMethodException 		if the DFSgeneration class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InvocationTargetException 	if the DFSgeneration constructor throws an exception
	 * @throws IllegalAccessException 		if the DFSgeneration class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InstantiationException 		if the heuristicClass class is abstract
	 * @throws IllegalArgumentException 	if the DFSgeneration constructor does not take the proper arguments
	 */
	@SuppressWarnings("unchecked")
	public void testRandom () 
	throws IOException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {

		int nbrVars = graph.nodes.size();
		int nbrAgents = graph.clusters.size();

		// Create the queue network
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, useTCP);
		
		// Listen for statistics messages
		Queue myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(DFSgeneration.STATS_MSG_TYPE, this);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : this.queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		DFSgeneration<AddableInteger, AddableInteger> statsGatherer = new DFSgeneration<AddableInteger, AddableInteger> (parser);
		statsGatherer.setSilent(true);
		statsGatherer.getStatsFromQueue(myQueue);
		
		// Prepare the heuristics
		Class<? extends NextChildChoiceHeuristic> dfsHeuristic = ScoreBroadcastingHeuristic.class;
		if (this.heuristicClass == VarNameHeuristic.class) // does not need to exchange scores
			dfsHeuristic = BlindScoringHeuristic.class;
		Element heuristicParams = new Element ("dfsHeuristic");
		heuristicParams.setAttribute("className", dfsHeuristic.getName());
		Element scoringHeuristicElmt = new Element ("scoringHeuristic");
		heuristicParams.addContent(scoringHeuristicElmt);
		scoringHeuristicElmt.setAttribute("className", this.heuristicClass.getName());

		// Create the listeners
		if (useXML) { // use the constructor that takes in an XML description of the problem
			
			// Create a description of the parameters of DFSgeneration
			Element parameters = this.createDFSparams (heuristicParams);
			
			// Go through the list of agents
			for (String agent : parser.getAgents()) {
				Queue queue = queues[Integer.parseInt(agent)];
				
				XCSPparser<AddableInteger, AddableInteger> subProb = parser.getSubProblem(agent);
				queue.setProblem(subProb);
				
				// Instantiate the listener using reflection
				Class<?> parTypes[] = new Class[2];
		        parTypes[0] = DCOPProblemInterface.class;
		        parTypes[1] = Element.class;
		        Constructor<?> constructor =getDFSclass().getConstructor(parTypes);
		        Object[] args = new Object[2];
		        args[0] = subProb;
		        args[1] = parameters;
				queue.addIncomingMessagePolicy((IncomingMsgPolicyInterface<String>) constructor.newInstance(args));
				queue.addIncomingMessagePolicy(this);
			}
			
		} else { // use the constructor that does not take in an XML description of the problem
			
			// Create the map associating to each variable the ID of its owner agent
			HashMap<String, String> owners = new HashMap<String, String> (graph.nodes.size());
			for (Map.Entry<String, Integer> entry : graph.clusterOf.entrySet()) 
				owners.put(entry.getKey(), entry.getValue().toString());
			
			// Create the map containing the domains
			HashMap<String, AddableInteger[]> domains = new HashMap<String, AddableInteger[]> ();
			for (String var : parser.getVariables()) 
				domains.put(var, parser.getDomain(var));
			
			for (String agent : parser.getAgents()) {
				Queue queue = queues[Integer.parseInt(agent)];
				
				// Extract the subproblem
				DCOPProblemInterface<AddableInteger, AddableInteger> subproblem = parser.getSubProblem(agent);
				List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = subproblem.getSolutionSpaces(false);
				Problem<AddableInteger, AddableInteger> subProb = new Problem<AddableInteger, AddableInteger> (agent, owners, domains, spaces);

				// Instantiate the heuristic
				NextChildChoiceHeuristic heuristic = dfsHeuristic.getConstructor(DCOPProblemInterface.class, Element.class).newInstance(subProb, heuristicParams);

				// Create the listener
				queue.addIncomingMessagePolicy(new DFSgeneration<AddableInteger, AddableInteger> (subProb, heuristic));
				queue.addIncomingMessagePolicy(this);
			}
		}
		
		// Choose variables as roots (one per connected component)
		Map<String, String> rootForVar = (Map<String, String>) LeaderElectionMaxIDTest.computeLeaders (graph.nodes, graph.components);

		// Tell all listeners to start the protocol
		this.remainingOutputs = this.nbrOutputMessagesPerVar * nbrVars; // 1 output message and 1 stats message per var
		for (int i = 0; i < nbrAgents; i++) { // for each agent
			
			// Send the START_AGENT message needed by the Most Connected heuristicClass
			Message msg = new Message (AgentInterface.START_AGENT);
			queues[i].sendMessageToSelf(msg);
			
			// Send the LEoutput message
			this.sendLEoutputs(i, rootForVar);
		}
		
		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.remainingOutputs == 0) {
					break;
				} else if (remainingOutputs < 0) {
					fail("At least one variable sent more than " + this.nbrOutputMessagesPerVar + " outputs");
				} else if (! this.finished.await(20, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
				
		// Check that it is indeed a DFS tree
		checkDFS(dfs, graph.neighborhoods, null);
		
		// Check that the heuristicClass is properly followed
		if (this.heuristicClass == MostConnectedHeuristic.class) 
			this.checkMostConnected();
		
		myQueue.end();
	}

	/** Sends the LEoutput messages
	 * @param i 			the index of the current agent
	 * @param rootForVar 	for each variable, its root
	 */
	protected void sendLEoutputs(int i, Map<String, String> rootForVar) {
		
		for (String var : graph.clusters.get(i)) { // for each variable owned by the agent

			// Create and send the message saying that variable i is a root (or not)
			String root = rootForVar.get(var);
			LeaderElectionMaxID.MessageLEoutput<String> startMsg = new LeaderElectionMaxID.MessageLEoutput<String> (var, root.equals(var), root);
			queues[i].sendMessageToSelf(startMsg);
		}
	}

	/** Create the XML parameters for the DFS module
	 * @param heuristicParams 	the parameters for the DFS heuristic
	 * @return the XML parameters for the DFS module
	 */
	protected Element createDFSparams(Element heuristicParams) {
		
		Element parameters = new Element ("module");
		parameters.addContent(heuristicParams);
		return parameters;
	}

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 * 
	 * It listens to the output of the DFS generation protocol. 
	 */
	public Collection<String> getMsgTypes() {
		ArrayList <String> types = new ArrayList <String> ();
		types.add(this.getOutputMsgType());
		return types;
	}
	
	/** @return the type of the output messages */
	protected String getOutputMsgType () {
		return DFSgeneration.OUTPUT_MSG_TYPE;
	}

	/** Keeps track of the output of the DFS generation protocol sent by each variable 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(this.getOutputMsgType()) || msgType.equals(DFSgeneration.STATS_MSG_TYPE)) {

			@SuppressWarnings("unchecked")
			DFSgeneration.MessageDFSoutput<AddableInteger, AddableInteger> msgCast = (DFSgeneration.MessageDFSoutput<AddableInteger, AddableInteger>) msg;
			String var = msgCast.getVar();
			DFSview<AddableInteger, AddableInteger> view = msgCast.getNeighbors();

			// Store the result in the variable dfs
			synchronized (dfs) {
				DFSview<AddableInteger, AddableInteger> previousView = dfs.get(var);
				if (previousView != null) { // compare the DFSoutput message with the stats message
					assertEquals (previousView, view);
				} else 
					dfs.put(var, view);
			}
		}

		this.finished_lock.lock();
		if (--this.remainingOutputs <= 0) 
			this.finished.signal();
		this.finished_lock.unlock();
	}

	/** Does nothing in this case 
	 * @param queue the queue */
	public void setQueue(Queue queue) { }
	
	/** Checks that the input is indeed a DFS tree
	 * @param dfs for each variable, its view of the DFS
	 * @param neighborhoods for each node, the list of its neighbors in the graph based on which the DFS was built
	 * @param path a collection of variables that belong to the current path/branch
	 */
	public static void checkDFS(Map< String, ? extends DFSview<?, ?> > dfs, Map< String, Set<String> > neighborhoods, LinkedList<String> path) {
		
		if (path == null) { // initial call to the method
			
			// Check that all incoming and outgoing edges are consistent
			// Go through every variable and its DFSview
			for (Map.Entry< String, ? extends DFSview<?, ?> > entry : dfs.entrySet()) {
				
				String var = entry.getKey();
				DFSview<?, ?> dfsView = entry.getValue();
				
				// Check the consistency of the incoming tree edge
				String parent = dfsView.getParent();
				if (parent != null) { // the variable has one parent
					
					// Check that the parent sees a consistent relationship 
					assertTrue(dfs.get(parent).getChildren().contains(var));
				}
				
				// Check the consistency of incoming backedges
				List<String> pseudos = dfsView.getPseudoParents();
				for (String pseudo : pseudos) {
					
					// Check that the pseudo-parent sees a consistent relationship 
					assertTrue(pseudo + " is a pseudo-parent of " + var + " but " + pseudo + "'s list of pseudo-children does not contain " + var 
							+ ": " + dfs.get(pseudo).getAllPseudoChildren(), 
							dfs.get(pseudo).getAllPseudoChildren().contains(var));
				}
				
				// Check that every neighbor appears exactly once in the variable's relationships
				for (String neighbor : neighborhoods.get(var)) {
					
					// Number of times the neighbor appears
					int count = 0;
					
					// Go through the list of (pseudo-)parents and (pseudo-)children
					if (dfsView.getPseudoParents().contains(neighbor)) 
						count++;
					if (neighbor.equals(dfsView.getParent())) 
						count++;
					if (dfsView.getChildren().contains(neighbor)) 
						count++;
					if (dfsView.getAllPseudoChildren().contains(neighbor)) 
						count++;
					
					assertTrue(neighbor + " appears more than once in " + var + "'s DFSview: " + dfsView, count == 1);
				}
			}
			
			// For each root variable, check that its subtree is a DFS tree
			for (Map.Entry< String, ? extends DFSview<?, ?> > entry : dfs.entrySet()) {
				if (entry.getValue().getParent() == null) { // the variable has no parent
					path = new LinkedList<String>();
					path.addLast(entry.getKey());
					checkDFS(dfs, neighborhoods, path);
				}
			}
			
		} else { // the path is not null
			
			// The current variable
			String curr = path.getLast();
			
			// Check that all pseudo-parents belong to the current path
			for (String pseudo : dfs.get(curr).getPseudoParents()) {
				if (! path.contains(pseudo)) {
					fail("Variable " + curr + " has pseudo-parent " + pseudo + " that is not higher in the current branch");
				}
				
				// Also check that the pseudo-parent sees the current variable as a pseudo-child below its correct child
				String child = path.get(path.indexOf(pseudo) + 1);
				assertTrue (curr + " is not below " + child + " in " + pseudo + "'s DFSview: " + dfs.get(pseudo), 
						dfs.get(pseudo).getPseudoChildren(child).contains(curr));
			}			
			
			// For each child of the last variable in the path, check that it does not already belong to the path
			for (String child : dfs.get(curr).getChildren()) {
				if (path.contains(child)) {
					fail("Variable " + curr + " has child " + child + " that is higher in the current branch");
				} else {
					path.addLast(child);
					checkDFS(dfs, neighborhoods, path);
					path.removeLast();
				}
			}
			
		}
		
	}

	/** Checks that the DFS implements the Most Connected heuristicClass
	 * 
	 * The idea of the Most Connected heuristicClass is that, for any variable, 
	 * its least connected child is more connected than any of its pseudo-children. 
	 */
	private void checkMostConnected() {
		
		// Go through the list of variables
		for (DFSview<AddableInteger, AddableInteger> dfsView : this.dfs.values()) {
			
			// Compute the number of neighbors of the least connected child
			List<String> children = dfsView.getChildren();
			if (children.isEmpty()) 
				continue;
			Iterator<String> iterator = children.iterator();
			int maxDeg = graph.neighborhoods.get(iterator.next()).size();
			while (iterator.hasNext()) {
				int deg = graph.neighborhoods.get(iterator.next()).size();
				if (deg > maxDeg) 
					maxDeg = deg;
			}
			
			// Check that all pseudo-children have fewer children than maxDeg
			for (String pseudo : dfsView.getAllPseudoChildren()) 
				assertTrue (this.graph.neighborhoods.get(pseudo).size() <= maxDeg);
		}
		
	}
	
}
