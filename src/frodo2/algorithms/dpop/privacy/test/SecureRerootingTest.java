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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.privacy.CollaborativeDecryption;
import frodo2.algorithms.dpop.privacy.RerootingMsg;
import frodo2.algorithms.dpop.privacy.SecureCircularRouting;
import frodo2.algorithms.dpop.privacy.SecureRerooting;
import frodo2.algorithms.dpop.privacy.test.FakeCryptoScheme.FakeEncryptedInteger;
import frodo2.algorithms.dpop.privacy.test.FakeCryptoScheme.FakePublicKeyShare;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.tests.LeaderElectionMaxIDTest;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.AddableInteger;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit test for the SecureRerooting module
 * @author Eric Zbinden, Thomas Leaute
 */
public class SecureRerootingTest extends TestCase implements IncomingMsgPolicyInterface<String>{
	
	/** The maximum number of variables in this problem */
	private int maxVar = 9;
	
	/** The maximum number of agents in this problem */
	private int maxAgent = 5;
	
	/** The maximum number of constraints in this problem */
	private int maxEdge = 40;
	
	/** Parser for the random XCSP problem */
	private XCSPparser<AddableInteger, AddableInteger> parser;
	
	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;
	
	/** The components of the graph still to test */
	private List<List<String>> components;
	
	/** List of queues corresponding to the different agents */
	private Queue[] queues;

	/** One output pipe used to send messages to each queue */
	private QueueOutputPipeInterface[] pipes;

	/** Whether to use TCP or SharedMemory pipes */
	private boolean useTCP;
	
	/** number of remaining DFSoutput to wait for */
	private int remainingOutput;
	
	/** number of iterations of the algorithm on a variable */
	private Map<String, Integer> rounds;
	
	/** The marked roots */
	private Set<String> roots;
	
	/** The number of agents that have not yet sent their AGENT_FINISHED message */
	private int nbrAgents;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** The controller's queue */
	private Queue myQueue;
	
	/** DFS order messages whose processing must be postponed until all key shares have been exchanged */
	private ArrayList<Message> pendingMsgs;
	
	/**
	 * Constructor
	 * @param useTCP if TCP is used or shared memory pipe. 
	 */
	public SecureRerootingTest(boolean useTCP) {
		super("randomTest");
		this.useTCP = useTCP;
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for SecureRerooting module");
		
		TestSuite testTmp = new TestSuite ("Test the SecureRerooting module with shared memory pipes");
		testTmp.addTest(new RepeatedTest (new SecureRerootingTest (false), 50000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Test the SecureRerooting module with TCP");
		testTmp.addTest(new RepeatedTest (new SecureRerootingTest (true), 1000));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/**
	 * Test for SecureRerooting module 
	 * @throws IOException 				thrown when failed to create TCP pipes
	 * @throws ClassNotFoundException 	should never happen
	 * @throws NoSuchMethodException 	if the CryptoScheme does not have a constructor that takes in an Element
	 */
	public void randomTest() throws IOException, ClassNotFoundException, NoSuchMethodException {
		
		//Create new random problem
		graph = RandGraphFactory.getRandGraph(maxVar, maxEdge, maxAgent);
		parser = new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, true));
		for(String var : graph.nodes) {
			rounds.put(var, -1);
		}
		
		FakeCryptoScheme.resetCounter();
		
		// Before starting the rerooting tests, we need to wait for all crypto schemes
		this.remainingOutput = 0;
		for (List<String> component : graph.components) // in each component, 
			if (component.size() > 1) // if this component is not composed of a single isolated variable, 
				this.remainingOutput -= component.size(); // wait for each variable to receive its crypto scheme
		
		// Skip this test if all variables are isolated (no rerooting is necessary)
		if (this.remainingOutput == 0) 
			return;
	
		// Create the queue network
		this.nbrAgents = graph.clusters.size();
		
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, this.useTCP);
		
		// Listener 
		myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(this);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : this.queues) {
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		}
		DFSgeneration.ROOT_VAR_MSG_TYPE = SecureRerooting.OUTPUT;
		DFSgeneration<AddableInteger, AddableInteger> dfsModule = new DFSgeneration<AddableInteger, AddableInteger> (null, this.parser);
		dfsModule.setSilent(true); // set to false to see the DFS
		dfsModule.getStatsFromQueue(myQueue);
		SecureCircularRouting routingModule = new SecureCircularRouting (null, this.parser);
		routingModule.setSilent(true); // set to false to see the linear ordering
		routingModule.getStatsFromQueue(myQueue);
		
		// Choose variables as roots (one per connected component)
		Map<String, String> leaders = LeaderElectionMaxIDTest.computeLeaders (graph.nodes, graph.components);
		components = new ArrayList< List<String> > (graph.components);
		
		for (int i = 0; i < graph.clusters.size(); i++) {
			
			Queue queue = queues[i];
			String agent = Integer.toString(i);
	
			// Extract the subproblem for that agent
			XCSPparser<AddableInteger, AddableInteger> subProb = this.parser.getSubProblem(agent);
			queue.setProblem(subProb);
			
			// Instantiate modules
			queue.addIncomingMessagePolicy(this);
			
			Element parameters = new Element ("module");
			parameters.setAttribute("minIncr", "5");
			queue.addIncomingMessagePolicy(new DFSgenerationWithOrder<AddableInteger, AddableInteger> (subProb, parameters));
			
			queue.addIncomingMessagePolicy(new SecureCircularRouting(subProb, null));
			
			SecureRerooting <AddableInteger, FakeEncryptedInteger> module = new SecureRerooting <AddableInteger, FakeEncryptedInteger> (subProb, null);
			queue.addIncomingMessagePolicy(module);
			queue.addOutgoingMessagePolicy(module);
			
			queue.addIncomingMessagePolicy(new DFSgeneration<AddableInteger, AddableInteger> (subProb, new Element ("module")));
			parameters = new Element ("module");
			Element scheme = new Element ("cryptoScheme");
			parameters.addContent(scheme);
			scheme.setAttribute("className", FakeCryptoScheme.class.getName());
			queue.addIncomingMessagePolicy(new CollaborativeDecryption <AddableInteger, FakeEncryptedInteger, FakePublicKeyShare> (subProb, parameters));
			
			queue.sendMessageToSelf(new Message (AgentInterface.START_AGENT));
		}
		
		for (int i = 0; i < nbrAgents; i++) { // for each agent
			
			for (String var : graph.clusters.get(i)) { // for each variable owned by the agent

				// Create and send the message saying that variable i is a root (or not)
				String leader = leaders.get(var);
				queues[i].sendMessageToSelf(new LeaderElectionMaxID.MessageLEoutput<String> (var, leader.equals(var), leader));
			}
		}
		
		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.nbrAgents == 0) {
					break;
				} else if (this.nbrAgents < 0) {
					fail("Too many output messages");
				} else if (! this.finished.await(60, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		myQueue.end();
		
		testResult();
	}
	
	/**
	 * Send a rerooting msg to a not tested component of the graph
	 * @return true if the complete graph has already been tested, else send a rerooting msg to each variable 
	 * of the next component and return false. 
	 */
	private synchronized boolean testNextComponent(){
				
		if(components.isEmpty())
			return true;
		
		else {
			
			List<String> comp = components.get(0);			
			remainingOutput = comp.size();
			
			if (remainingOutput > 1) 
				for(String var : comp) 
					sendReroot(var);				
			else {
				components.remove(0);
				return testNextComponent(); //lonely var won't send anything, so test next component
			}
			
			return false;
		}	
	}
	
	/**
	 * Send a Reroot request to the destination variable dest
	 * @param dest the destination of the reroot request message
	 */
	private void sendReroot(String dest){
		this.queues[graph.clusterOf.get(dest)].sendMessageToSelf(new RerootingMsg(dest));
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public synchronized void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(CollaborativeDecryption.CRYPTO_SCHEME_TYPE)) { // the crypto scheme for a given variable

			if (++this.remainingOutput == 0) {

				// Start processing DFS output messages
				remainingOutput = graph.nodes.size();
				for (Message pendingMsg : this.pendingMsgs) 
					this.notifyIn(pendingMsg);
				this.pendingMsgs = null;
			}
		}
		
		else if (msgType.equals(DFSgenerationWithOrder.OUTPUT_MSG_TYPE) || msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // the DFS output for one variable
			
			// Postpone the processing of this message if not all key shares have been exchanged yet
			if (this.remainingOutput < 0) {
				this.pendingMsgs.add(msg);
				return;
			}
			
			@SuppressWarnings("unchecked")
			DFSgeneration.MessageDFSoutput<AddableInteger, AddableInteger> msgCast = (MessageDFSoutput<AddableInteger, AddableInteger>) msg;
			String myVar = msgCast.getVar();
			
			if (msgCast.getNeighbors() == null) // DFS output reset
				return;
			
			rounds.put(myVar, rounds.get(myVar)+1);	
			int round = rounds.get(myVar);
			
			int total = this.graph.components.get(this.graph.componentOf.get(myVar)).size();
			if(total == 1) assertTrue(myVar+" is elected root twice.", roots.add(myVar));
			
			remainingOutput--;
			
			// Check if the variable is the root
			if (round > 0 && msgCast.getNeighbors().getParent() == null) 
				assertTrue(myVar+" is elected root twice.", roots.add(myVar));
								
			if(remainingOutput == 0){
				
				if (round == 0){ //first iteration	
					if (this.testNextComponent()) end();
					
				} else {
					
					if(round >= total){
						components.remove(0);
						if (this.testNextComponent()) end();
						
					} else {
						remainingOutput = components.get(0).size();
						for(String var : components.get(0))
							this.sendReroot(var);
					}		
				}			
			}
			
		} else if (msgType.equals(AgentInterface.AGENT_FINISHED)) { 
			
			this.finished_lock.lock();
			if (--this.nbrAgents <= 0) 
				this.finished.signal();
			this.finished_lock.unlock();
		}		
	}
	
	/** Send to all agent an AgentFinish message */
	private void end(){
		
		for (Queue queue : queues){
			queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));
		}
	}
	
	/**
	 * Test if the result is correct
	 */
	private synchronized void testResult(){
		
		for(List<String> comp : graph.components){
			for (String var : comp){
				
				assertFalse(var+" hasn't been elected root", roots.add(var)); //if false => var have been root
			}
		}		
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (4);
		types.add(CollaborativeDecryption.CRYPTO_SCHEME_TYPE);
		types.add(DFSgenerationWithOrder.OUTPUT_MSG_TYPE);
		types.add(DFSgeneration.OUTPUT_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		// no use		
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		roots = new HashSet<String>();
		rounds = new HashMap<String, Integer>();
		pendingMsgs = new ArrayList<Message> ();
	}
	
	/** Ends all queues 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		this.parser = null;
		if (this.queues != null) 
			for (Queue queue : queues) 
				queue.end();
		queues = null;
		if (this.pipes != null) 
			for (QueueOutputPipeInterface pipe : pipes) 
				pipe.close();
		pipes = null;		
		roots = null;
		rounds = null;
		this.myQueue = null;
	}
}
