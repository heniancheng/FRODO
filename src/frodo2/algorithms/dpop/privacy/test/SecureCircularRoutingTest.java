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

package frodo2.algorithms.dpop.privacy.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.dpop.privacy.DeliveryMsg;
import frodo2.algorithms.dpop.privacy.RoutingMsg;
import frodo2.algorithms.dpop.privacy.SecureCircularRouting;
import frodo2.algorithms.heuristics.MostConnectedHeuristic;
import frodo2.algorithms.heuristics.ScorePair;
import frodo2.algorithms.heuristics.ScoringHeuristicWithTiebreaker;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.algorithms.varOrdering.election.VariableElection;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.AddableInteger;

/** Test case for the module SecureCircularRouting
 * @author Thomas Leaute
 */
public class SecureCircularRoutingTest extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** The maximum number of variables in the random test problems */
	private final static int maxNbrVars = 50;
	
	/** The maximum number of edges in the random test problems */
	private final static int maxNbrEdges = 200;
	
	/** The maximum number of agents in the random test problems */
	private final static int maxNbrAgents = 10;
	
	/** @return the suite of tests */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("Tests for SecureCircularRouting");
		
		TestSuite tmp = new TestSuite ("Tests for SecureCircularRouting with shared memory pipes");
		tmp.addTest(new RepeatedTest (new SecureCircularRoutingTest ("test", false), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests for SecureCircularRouting with TCP pipes");
		tmp.addTest(new RepeatedTest (new SecureCircularRoutingTest ("test", true), 100));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** The type of the test forward message */
	private static final String TEST_FWD_MSG_TYPE = "Test forward message";
	
	/** The type of the test backward message */
	private static final String TEST_BACK_MSG_TYPE = "Test backward message";
	
	/** A message holding the sender variable and the number of variables visited so far
	 * @author Thomas Leaute
	 */
	public static class TestMessage extends MessageWith2Payloads<String, Integer> {

		/** Empty constructor used for externalization */
		public TestMessage () { }

		/** Constructor 
		 * @param type 		the type of the message
		 * @param sender 	the sender variable
		 * @param count 	the number of variables visited so far
		 */
		public TestMessage(String type, String sender, Integer count) {
			super(type, sender, count);
		}
		
		/** @return the sender variable */
		public String getSender () {
			return super.getPayload1();
		}
		
		/** @return the number of variables visited so far */
		public Integer getCount () {
			return super.getPayload2();
		}
	}
	
	/** A test listener that asks the SecureCircularRouting message, for each variable, 
	 * to forward a message forward all the way until it comes back, and same backwards. 
	 * @author Thomas Leaute
	 */
	private class Forwarder implements IncomingMsgPolicyInterface<String> {
		
		/** The listener's queue */
		private Queue queue;
		
		/** The agent's subproblem */
		private XCSPparser<AddableInteger, AddableInteger> subProb;
		
		/** Constructor
		 * @param subProb 	the agent's subproblem
		 */
		public Forwarder(XCSPparser<AddableInteger, AddableInteger> subProb) {
			this.subProb = subProb;
		}

		/** @see IncomingMsgPolicyInterface#getMsgTypes() */
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (2);
			types.add(AgentInterface.START_AGENT);
			types.add(SecureCircularRouting.DELIVERY_MSG_TYPE);
			return types;
		}

		/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			String msgType = msg.getType();
			
			if (msgType.equals(AgentInterface.START_AGENT)) {
				
				// Go through all my variables
				for (String var : subProb.getMyVars()) {
					if (subProb.getNbrNeighbors(var) != 0) { // not an isolated variable 
						
						// Send a message forward and a message backward
						queue.sendMessageToSelf(new RoutingMsg<TestMessage> (SecureCircularRouting.NEXT_MSG_TYPE, var, 
								new TestMessage (TEST_FWD_MSG_TYPE, var, 0)));
						queue.sendMessageToSelf(new RoutingMsg<TestMessage> (SecureCircularRouting.PREVIOUS_MSG_TYPE, var, 
								new TestMessage (TEST_BACK_MSG_TYPE, var, 0)));
					}
				}
			}
			
			else if (msgType.equals(SecureCircularRouting.DELIVERY_MSG_TYPE)) {
				
				DeliveryMsg<TestMessage> msgCast = (DeliveryMsg<TestMessage>) msg;
				String dest = msgCast.getDest();
				TestMessage payload = msgCast.getMessage();
				String sender = payload.getSender();
				
				if (dest.equals(sender)) { // the loop is completed
					
					// Check the counter
					int nbrVarsVisited = graph.components.get(graph.componentOf.get(dest)).size() - 1;
					assertTrue( (payload.getType().equals(TEST_FWD_MSG_TYPE) ? payload.getCount() == nbrVarsVisited : payload.getCount() == -nbrVarsVisited) );
					
				} else { // keep forwarding
					
					if (payload.getType().equals(TEST_FWD_MSG_TYPE)) 
						queue.sendMessageToSelf(new RoutingMsg<TestMessage> (SecureCircularRouting.NEXT_MSG_TYPE, dest, 
								new TestMessage (TEST_FWD_MSG_TYPE, sender, payload.getCount() + 1)));
					else 
						queue.sendMessageToSelf(new RoutingMsg<TestMessage> (SecureCircularRouting.PREVIOUS_MSG_TYPE, dest, 
								new TestMessage (TEST_BACK_MSG_TYPE, sender, payload.getCount() - 1)));
				}
			}
		}

		/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
		public void setQueue(Queue queue) {
			this.queue = queue;
		}
		
	}
	
	/** The random graph */
	private Graph graph;
	
	/** The parser for the whole problem */
	private XCSPparser<AddableInteger, AddableInteger> parser;

	/** The queue of each agent */
	private Queue[] queues;

	/** The pipe to send message to each agent */
	private QueueOutputPipeInterface[] pipes;
	
	/** Countdown until the test terminates */
	private int countdown;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** The stats gatherer's queue */
	private Queue myQueue;
	
	/** The underlying DFS */
	private HashMap< String, DFSview<AddableInteger, AddableInteger> > dfs;
	
	/** Whether to use TCP pipes */
	private final boolean useTCP;
	
	/** Constructor from a method name
	 * @param name 		the name of the test method
	 * @param useTCP 	whether to use TCP pipes
	 */
	public SecureCircularRoutingTest(String name, boolean useTCP) {
		super(name);
		this.useTCP = useTCP;
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (2);
		types.add(DFSgeneration.STATS_MSG_TYPE);
		types.add(SecureCircularRouting.STATS_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		// Parse the information in the DFS message
		@SuppressWarnings("unchecked")
		DFSgeneration.MessageDFSoutput<AddableInteger, AddableInteger> msgCast = (MessageDFSoutput<AddableInteger, AddableInteger>) msg;
		String var = msgCast.getVar();
		DFSview<AddableInteger, AddableInteger> view = msgCast.getNeighbors();
		
		// Check whether we have already received this from the other stats reporter
		DFSview<AddableInteger, AddableInteger> oldView = this.dfs.remove(var);
		if (oldView == null) 
			this.dfs.put(var, view);
		else // check that they match
			assertEquals(view, oldView);
		
		// Decrement the countdown
		finished_lock.lock();
		if (--countdown <= 0) // terminate
			finished.signal();
		finished_lock.unlock();
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) { }

	/** @see junit.framework.TestCase#setUp() */
	@Override
	protected void setUp() throws Exception {
		
		// Generate the random problem
		this.graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		dfs = new HashMap< String, DFSview<AddableInteger, AddableInteger> > ();
		this.parser = new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, true));
		
		// Instantiate and set up the queues
		int nbrAgents = graph.clusters.size();
		this.queues = new Queue [nbrAgents];
		this.pipes = AllTests.createQueueNetwork(queues, graph, useTCP);
		
		// Listen to stats messages
		this.myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(this);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : this.queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		DFSgeneration<AddableInteger, AddableInteger> statsGatherer = new DFSgeneration<AddableInteger, AddableInteger> (parser);
		statsGatherer.setSilent(true);
		statsGatherer.getStatsFromQueue(myQueue);
		
		Element params = new Element ("module");
		params.setAttribute("reportStats", "true");
		SecureCircularRouting router = new SecureCircularRouting (params, parser);
		router.setSilent(true);
		router.getStatsFromQueue(myQueue);
		
		// Create the listeners
		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];
			
			// Create the test listener
			XCSPparser<AddableInteger, AddableInteger> subProb = parser.getSubProblem(agent);
			queue.setProblem(subProb);
			queue.addIncomingMessagePolicy(new Forwarder (subProb));
			
			// Create the variable election module
			queue.addIncomingMessagePolicy(new VariableElection< ScorePair<Short, String> > (subProb, 
					new ScoringHeuristicWithTiebreaker<Short, String> (new MostConnectedHeuristic (subProb, null), new VarNameHeuristic (subProb, null)), 
					parser.getNbrVars() + 1));
			
			// Create the DFSgeneration module
			queue.addIncomingMessagePolicy(new DFSgeneration<AddableInteger, AddableInteger> (subProb, 
					new DFSgeneration.ScoreBroadcastingHeuristic<Short>(new MostConnectedHeuristic (subProb, null), subProb.getAgentNeighborhoods())));
			
			// Create the SecureCircularRouting module
			queue.addIncomingMessagePolicy(new SecureCircularRouting (subProb, params));
		}
	}

	/** @see junit.framework.TestCase#tearDown() */
	@Override
	protected void tearDown() {
		this.parser = null;
		for (QueueOutputPipeInterface pipe : this.pipes) 
			pipe.close();
		this.pipes = null;
		for (Queue queue : this.queues) 
			queue.end();
		this.queues = null;
		this.myQueue.end();
		this.myQueue = null;
		this.dfs = null;
	}
	
	/** Tests the forwarding of a message from the first variable to last, and back to the first */
	public void test () {
		
		// Wait for the reception of all TestMessages
		this.countdown = 2 * graph.nodes.size();
		
		// Tell each agent to start
		for (int i = 0; i < this.graph.clusters.size(); i++) 
			this.queues[i].sendMessageToSelf(new Message (AgentInterface.START_AGENT));
		
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.countdown <= 0) {
					break;
				} else if (! this.finished.await(10, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
	}

}
