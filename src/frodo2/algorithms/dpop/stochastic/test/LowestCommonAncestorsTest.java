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

package frodo2.algorithms.dpop.stochastic.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.stochastic.LCAmsg2;
import frodo2.algorithms.dpop.stochastic.LowestCommonAncestors;
import frodo2.algorithms.dpop.test.UTILpropagationTest;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.AddableInteger;

/** A JUnit test case for LowestCommonAncestors
 * @author Thomas Leaute
 */
public class LowestCommonAncestorsTest extends TestCase implements IncomingMsgPolicyInterface<String> {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	protected final int maxNbrVars = 20;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 200;

	/** Maximum number of agents */
	private final int maxNbrAgents = 20;
	
	/** The number of flag types */
	private final int nbrFlags = 20;
	
	/** For each flag type and each node, the probability that the node has that flag */
	private final double flagProb = 0.2;

	/** Random graph */
	protected RandGraphFactory.Graph graph;
	
	/** For each node, its set of flags */
	protected HashMap< String, HashSet<String> > allFlags;
	
	/** For each node, the set of flags it is the lca of */
	protected HashMap< String, HashSet<String> > lcas = new HashMap< String, HashSet<String> > ();
	
	/** List of queues corresponding to the different agents */
	protected Queue[] queues;
	
	/** One output pipe used to send messages to each queue */
	protected QueueOutputPipeInterface[] pipes;
	
	/** Current number of outputs yet to be received from the LowestCommonAncestors module */
	protected int remainingOutputs;
	
	/** Used to make the test thread wait */
	protected final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	protected final Condition finished = finished_lock.newCondition();

	/** The chosen DFS
	 * 
	 * For each variable, stores its relationships with neighboring variables
	 */
	protected Map< String, ? extends DFSview<?, ?> > dfs;

	/** Constructor that instantiates a test only for the input method
	 * @param method test method
	 */
	public LowestCommonAncestorsTest (String method) {
		super (method);
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Random tests for LowestCommonAncestors");
		
		testSuite.addTest(new RepeatedTest (new LowestCommonAncestorsTest ("test"), 100));
		
		return testSuite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		lcas = new HashMap< String, HashSet<String> > ();
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		allFlags = new HashMap< String, HashSet<String> > ();
		for (String node : graph.nodes) {
			HashSet<String> flags = new HashSet<String> ();
			for (int flag = 0; flag < nbrFlags; flag++) 
				if (Math.random() < flagProb) 
					flags.add(String.valueOf(flag));
			allFlags.put(node, flags);
		}
		dfs = UTILpropagationTest.computeDFS(graph, new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, true)));
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		allFlags = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		pipes = null;
		dfs = null;
		lcas = null;
	}
	
	/** Tests the output of the LowestCommonAncestors module
	 * @throws Exception 	if an error occurs
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void test () throws Exception {
		
		this.remainingOutputs = graph.nodes.size();
		int nbrAgents = graph.clusters.size();

		// Create the queue network
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, false);
		
		// Set the output pipe for the DFSgeneration module's statistics
		Queue myQueue = new Queue (false);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : this.queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		
		this.setModules();
		
		// Tell all listeners to start the protocol
		for (int i = 0; i < nbrAgents; i++) { // for each agent
			for (String var : graph.clusters.get(i)) { // for each variable owned by the agent
				Queue queue = queues[i];
				
				queue.sendMessageToSelf(new Message (AgentInterface.START_AGENT));

				// Send the DFS message for this variable
				queue.sendMessageToSelf(new DFSgeneration.MessageDFSoutput (var, this.dfs.get(var)));
			}
		}
		
		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.remainingOutputs == 0) {
					break;
				} else if (this.remainingOutputs < 0) {
					fail("At least one node sent more than two outputs");
				} else if (! this.finished.await(20, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		this.checkOutput();
		
		myQueue.end();
	}
	
	/** Checks the output of the module */
	protected void checkOutput() {

		// Test the correctness of the outputs: go through the list of nodes and their lcas
		HashSet<String> allLCAs = new HashSet<String> ();
		for (Map.Entry< String, HashSet<String> > entry : lcas.entrySet()) {
			String node = entry.getKey();
			HashSet<String> myLCAs = entry.getValue();
			allLCAs.addAll(myLCAs);
			
			// Go through the list of lcas for that node
			for (String flag : myLCAs) {
				
				// First check that no other node higher in the DFS has that flag
				DFSview<?, ?> dfsView = dfs.get(node);
				String parent = dfsView.getParent();
				while (parent != null) {
					Collection<String> flags = allFlags.get(parent);
					assertTrue (flags == null || ! flags.contains(flag));
					parent = dfs.get(parent).getParent();
				}
				
				// If the node itself has that flag, we are good; test the next flag
				Collection<String> flags = allFlags.get(node);
				if (flags != null && flags.contains(flag)) 
					continue;
				
				// Check that more than one of my subtrees has that flag
				int nbrSubtrees = 0;
				for (String child : dfsView.getChildren()) {
					if (subtreeHasFlag (child, flag) && ++nbrSubtrees > 1) 
						break;
				}
				assertTrue (nbrSubtrees > 1);
			}
		}
		
		// Check that all lcas were computed
		for (HashSet<String> flags : this.allFlags.values()) 
			assertTrue (allLCAs.containsAll(flags));
		
	}

	/** Creates the modules 
	 * @throws Exception if an error occurs
	 */
	protected void setModules() throws Exception {

		int nbrNodes = graph.nodes.size();
		int nbrAgents = graph.clusters.size();

		// Create the map associating to each node the ID of its owner agent
		HashMap<String, String> owners = new HashMap<String, String> (graph.nodes.size());
		for (Map.Entry<String, Integer> entry : graph.clusterOf.entrySet()) 
			owners.put(entry.getKey(), entry.getValue().toString());
		
		for (int i = 0; i < nbrAgents; i++) {
			Queue queue = queues[i];
			queue.addIncomingMessagePolicy(this);

			// Create the list of neighborhoods for this agent
			Map < String, Collection <String> > neighborhoods = new HashMap < String, Collection <String> > (nbrNodes);
			for (String node : graph.clusters.get(i)) 
				neighborhoods.put(node, graph.neighborhoods.get(node));
			
			// Create the LowestCommonAncestors module
			HashMap< String, Set<String> > flags = new HashMap< String, Set<String> > ();
			for (String node : graph.clusters.get(i)) 
				flags.put(node, allFlags.get(node));
			queue.addIncomingMessagePolicy(new LowestCommonAncestors (flags, owners));
		}
		
	}
	
	/** Checks whether a subtree contains a given flag
	 * @param root 	the root of the subtree
	 * @param flag 		the flag
	 * @return \c true iff the subtree contains the flag
	 */
	private boolean subtreeHasFlag(String root, String flag) {
		
		Collection<String> flags = allFlags.get(root);
		if (flags != null && flags.contains(flag)) 
			return true;
		
		for (String child : dfs.get(root).getChildren()) 
			if (subtreeHasFlag (child, flag)) 
				return true;
		
		return false;
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList <String> types = new ArrayList <String> (1);
		types.add(LowestCommonAncestors.OUTPUT_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		if (msg.getType().equals(LowestCommonAncestors.OUTPUT_MSG_TYPE)) {
			
			LCAmsg2 msgCast = (LCAmsg2) msg;
			synchronized (lcas) {
				lcas.put(msgCast.getNode(), msgCast.getFlags());
			}
			
			// Increment the counter of the number of messages received
			this.finished_lock.lock();
			if (--this.remainingOutputs <= 0) 
				this.finished.signal();
			this.finished_lock.unlock();
		}
	}

	/** Does nothing in this case 
	 * @param queue the queue */
	public void setQueue(Queue queue) { }

}
