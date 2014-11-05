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

package frodo2.algorithms.varOrdering.election.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID.MessageLEoutput;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit test for the class LeaderElectionMaxID
 * @author Thomas Leaute
 * @param <S> type used for scores
 */
public class LeaderElectionMaxIDTest < S extends Comparable<S> > extends TestCase implements IncomingMsgPolicyInterface <String> {
	
	/** Maximum number of agents in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrAgents = 5;
	
	/** Maximum number of pipes (edges) in the random graph */
	private final int maxNbrPipes = 10;

	/** Number of agents in the test */
	protected int nbrAgents;

	/** List of queues corresponding to the different agents */
	protected Queue[] queues;

	/** For each agent, the output of LeaderElectionMaxID */
	protected Map< String, MessageLEoutput<String> > outputs;

	/** Current number of agents that still need to their output of the leader election protocol */
	protected Integer remainingOutputs;
	
	/** Used to make the test thread wait */
	protected final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	protected final Condition finished = finished_lock.newCondition();

	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;
	
	/** One output pipe used to send messages to each queue */
	protected QueueOutputPipeInterface[] pipes;
	
	/** Constructor that instantiates a test only for the input method
	 * @param method test method
	 */
	public LeaderElectionMaxIDTest(String method) {
		super (method);
	}

	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for LeaderElectionMaxID");
		
		TestSuite testTmp = new TestSuite ("Tests for LeaderElectionMaxID using shared memory pipes");
		testTmp.addTest(new RepeatedTest (new LeaderElectionMaxIDTest<String> ("testRandomSharedMemory"), 1000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for LeaderElectionMaxID using TCP pipes");
		testTmp.addTest(new RepeatedTest (new LeaderElectionMaxIDTest<String> ("testRandomTCP"), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		graph = RandGraphFactory.getRandGraph(maxNbrAgents, maxNbrPipes);
	}
	
	/** Ends all queues 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		outputs = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		pipes = null;
		this.remainingOutputs = null;
	}
	
	/** Tests the DFS generation protocol on a random graph using QueueIOPipes
	 * @throws IOException thrown if the method fails to create pipes
	 */
	public void testRandomSharedMemory () throws IOException {
		testRandom(false);
	}
	
	/** Tests the DFS generation protocol on a random graph using TCP pipes
	 * @throws IOException thrown if the method fails to create pipes
	 */
	public void testRandomTCP () throws IOException {
		testRandom(true);
	}
	
	/** Tests the leader election protocol on a random graph of "agents"
	 * @param useTCP \c true whether TCP pipes should be used instead of QueueIOPipes
	 * @throws IOException thrown if the method fails to create pipes
	 */
	public void testRandom (boolean useTCP) throws IOException {
		
		// Create the queue network
		nbrAgents = graph.nodes.size();
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, useTCP);
		
		// Generate ID and the listeners
		Map<String, S> allUniqueIDs = this.initiatingIDandListener();
		
		// Initiate the output Var
		outputs = new HashMap< String, MessageLEoutput<String> > (nbrAgents);
				
		// Compute the expected number of messages exchanged
		this.remainingOutputs = getNbrMsgsNeeded();
	
		// Tell all listeners to start the protocol
		Message startMsg = new Message (LeaderElectionMaxID.START_MSG_TYPE);
		for (Queue queue : queues) {
			queue.sendMessageToSelf(startMsg);
		}	
		
		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.remainingOutputs == 0) {
					break;
				} else if (this.remainingOutputs < 0) {
					fail("Too many messages exchanged");
				} else if (! this.finished.await(10, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}

		//Check the output
		this.checkOutputs(allUniqueIDs);
		
	}
	
	/**
	 * Compute uniqueID, link them with variable name and generate listeners 
	 * @return the map name-id
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, S> initiatingIDandListener(){
		
		Map<String, S> uniqueID = new HashMap<String, S> (queues.length);
		
		for (int i = 0; i < queues.length; i++) {
			// Var name
			String iStr = graph.nodes.get(i);
			
			//Link name to id
			uniqueID.put(iStr, (S) iStr);
			
			// Generate the listeners
			queues[i].addIncomingMessagePolicy(new LeaderElectionMaxID<String> (iStr, iStr, graph.neighborhoods.get(iStr), nbrAgents - 1));
			queues[i].addIncomingMessagePolicy(this);
		}
		
		return uniqueID;

	}
	
	/**
	 * Called to verify output correctness
	 * @param allUniqueIDs a mapping between Names and IDs
	 */
	protected void checkOutputs(Map<String, S> allUniqueIDs){
		// Compute the correct leaders (one per connected component)
		Map<String, String> correctOutputs = (Map<String, String>) computeLeaders (graph.nodes, graph.components);
		
		// Compare the outputs with the correct outputs
		assertEquals (correctOutputs.size(), outputs.size());
		for (Map.Entry< String, MessageLEoutput<String> > entry : outputs.entrySet()) {
			String var = entry.getKey();
			MessageLEoutput<String> msg = entry.getValue();
			S id = allUniqueIDs.get(var);
			String leader = correctOutputs.get(var);
			assertEquals (id.equals(leader), (boolean) msg.getFlag());
			assertEquals (leader, msg.getLeader());
		}
		
	}
	
	/** @return the expected number of messages exchanged */
	protected int getNbrMsgsNeeded (){

		int remain = 0;
		for (Set<String> neighbors : graph.neighborhoods.values()) { // for each agent and its list of neighbors
			remain += 1 + neighbors.size() * (nbrAgents - 1); // one output message + (nbrAgents - 1) messages per neighbor
		}
		return remain;
	}

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 * 
	 * It listens to the output of the leader election protocol. 
	 */
	public Collection <String> getMsgTypes() {
		ArrayList <String> types = new ArrayList <String> (2);
		types.add(LeaderElectionMaxID.OUTPUT_MSG_TYPE);
		types.add(LeaderElectionMaxID.LE_MSG_TYPE);
		return types;
	}

	/** Keeps track of the output of the leader election protocol sent by each agent 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
				
		if (msg.getType().equals(LeaderElectionMaxID.OUTPUT_MSG_TYPE)) {
			LeaderElectionMaxID.MessageLEoutput<String> msg2 = (LeaderElectionMaxID.MessageLEoutput<String>) msg;

			// Record whether the sender of this message decided it was the leader
			synchronized (outputs) {
				outputs.put(msg2.getSender(), msg2);
			}
		}
		
		// Increment the counter of the number of messages received
		this.finished_lock.lock();
		if (--this.remainingOutputs <= 0) 
			this.finished.signal();
		this.finished_lock.unlock();
	}

	/** Does nothing in this case 
	 * @param queue the queue */
	public void setQueue(Queue queue) { }
	
	/** Compute the leaders 
	 * @param nodes list of nodes in the graph
	 * @param components components in the graph
	 * @return for each agent, its leader
	 */
	public static Map<String, String> computeLeaders (List<String> nodes, List <List <String>> components) {
		Map<String, String> uniqueIDs = new HashMap<String, String> (nodes.size());
		for (String node : nodes) 
			uniqueIDs.put(node, node);
		return computeLeaders (nodes.size(), components, uniqueIDs);
	}

	/** Compute the leaders 
	 * @param <S> the type used for the scores
	 * @param nbrAgents number of agents
	 * @param components components in the graph
	 * @param uniqueIDs for each variable, its unique ID
	 * @return for each agent, its leader
	 */
	public static < S extends Comparable<S> > Map<String, S> computeLeaders (int nbrAgents, List <List <String>> components, Map<String, S> uniqueIDs) {
		Map<String, S> out = new HashMap<String, S> (nbrAgents);
		for (List <String> component : components) {
			
			// Compute the maximum ID in this component
			S maxID = uniqueIDs.get(component.get(0));
			for (String id : component) {
				S uniqueID = uniqueIDs.get(id);
				if (uniqueID.compareTo(maxID) > 0) {
					maxID = uniqueID;
				}
			}
			
			// Store the leader of all agents in the component
			for (String id : component) {
				out.put(id, maxID);
			}
		}
		return out;
	}

}
