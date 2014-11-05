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

/**
 * 
 */
package frodo2.daemon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.AgentAddress;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.controller.Controller;
import frodo2.controller.WhitePages;
import frodo2.controller.messages.MessageAgentReporting;
import frodo2.solutionSpaces.AddableInteger;
import junit.framework.TestCase;

/**
 * @author Brammert Ottens
 * @author Thomas Leaute
 */
public class TestLocalWhitePages extends TestCase {

	/** The local whitepages*/
	LocalWhitePagesExtension lwp;
	
	/** A queue*/
	Queue queue;
	
	/** A daemon*/
	Daemon d;
	
	/** The general queue's input pipe*/
	QueueIOPipe queueInputPipe;
	
	/** A simple agent for testing purposes */
	private class SimpleAgent implements IncomingMsgPolicyInterface<String>, AgentInterface<AddableInteger> {
		
		/** A message used to test the Pipes*/
		public static final String TEST = "test";

		/** The list of messages types this listener wants to be notified of */
		private ArrayList <String> msgTypes = new ArrayList <String> ();
		
		/** The queue */
		private Queue queue;
		
		/** Its non-TCP input pipe*/
		private QueueIOPipe inputPipe;
		
		/** A counter to count the number of received messages*/
		private int received;
		
		/** The agents ID*/
		private String Id;
		
		/** The agent's port number*/
		private int port;
		
		/** The constructor
		 * @param queue 	the queue whose incoming messages this simple agent listens to
		 * @param Id 		the name of the agent
		 * @param port 		the port of the agent
		 */
		public SimpleAgent(Queue queue, String Id, int port) {
			this.queue = queue;
			msgTypes.add(LocalWhitePages.AGENT_OUTPUT_PIPE);
			msgTypes.add(TEST);
			queue.addIncomingMessagePolicy(this);
			inputPipe = new QueueIOPipe(queue);
			received = 0;
			this.Id = Id;
			this.port = port;
		}

		/**
		 * Reports the agent to the global white pages
		 */
		public void agentReportGlobal() {
			// report to the global white pages
			MessageAgentReporting msg1 = new MessageAgentReporting(MessageAgentReporting.AGENT_REPORTING, Id, Controller.PipeFactoryInstance.getSelfAddress(port));
			queue.sendMessage(LocalWhitePages.CONTROLLER_ID, msg1);
		}
		
		/**
		 * Reports the agent to the local white pages
		 */
		public void agentReportLocal() {
			// report to the local white pages
			queue.sendMessage("WhitePages", new LocalAgentReport (Id, port, inputPipe));
		}
		
		/**
		 * 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
		 */
		public Collection<String> getMsgTypes() {
			return msgTypes;
		}

		/**
		 * 
		 * @see IncomingMsgPolicyInterface#notifyIn(Message)
		 */
		public void notifyIn(Message msg) {
			
			String type = msg.getType();
			
			if(type.equals(TEST)) {
				received++;
			}
		}
		
		/**
		 * Ask the address of agent agentName
		 * @param agentName 	name of the agent
		 */
		public void askAddress(String agentName) {
			MessageWith2Payloads<String, String> msg = new MessageWith2Payloads<String, String>(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST, Id, agentName);
			queue.sendMessage("WhitePages", msg);
		}
		
		/**
		 * Send a test message to agent agentName
		 * @param agentName 	name of the recipient
		 */
		public void sendTestMessage(String agentName) {
			Message msg = new Message(TEST);
//			System.out.println("Boe");
			queue.sendMessage(agentName, msg);
		}

		/** Does nothing
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) { }
		
		/**
		 * @return the number of received messages
		 */
		public int getReceived() {
			return received;
		}

		/** Does nothing
		 * @see AgentInterface#report()
		 */
		public void report() { }
		
		/** Does nothing
		 * @see frodo2.algorithms.AgentInterface#connect()
		 */
		public void connect() { }

		/** Does nothing
		 * @see frodo2.algorithms.AgentInterface#kill()
		 */
		public void kill() { }

		/** Does nothing
		 * @see frodo2.algorithms.AgentInterface#start()
		 */
		public void start() { }

		/**
		 * @see frodo2.algorithms.AgentInterface#getID()
		 */
		public String getID() {
			return Id;
		}

		/**
		 * 
		 * @see frodo2.algorithms.AgentInterface#addOutputPipe(java.lang.String, frodo2.communication.QueueOutputPipeInterface)
		 */
		public void addOutputPipe(String agent,	QueueOutputPipeInterface outputPipe) {
			queue.addOutputPipe(agent, outputPipe);
		}

		/** Does nothing
		 * @see AgentInterface#setup(QueueOutputPipeInterface, QueueOutputPipeInterface, int)
		 */
		public void setup(QueueOutputPipeInterface toDaemonPipe, QueueOutputPipeInterface toControllerPipe, int port) { }

		/** 
		 * @see AgentInterface#getCurrentSolution()
		 */
		public Map<String, AddableInteger> getCurrentSolution() {
			/// @todo Auto-generated method stub
			assert false : "Not implemented";
			return null;
		}
		
	}
	
	/** An extension of the local white pages that is able to return the list of agents*/
	private class LocalWhitePagesExtension extends LocalWhitePages {

		/**
		 * Constructor
		 * @param daemon 	the daemon
		 */
		public LocalWhitePagesExtension(Daemon daemon) {
			super(daemon);
		}
		
		/** 
		 * Return the set of agents
		 * @return HashMap<String, QueueOutputPipeTCP>
		 */
		public HashMap<String, QueueOutputPipeInterface> getAgents() {
			return externalAgents;
		}
		
	}
	/**
	 * Constructor
	 */
	public TestLocalWhitePages() {
		
	}
	
	/**
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp() {
		d = new Daemon(false);
		lwp = new LocalWhitePagesExtension(d);
		queue = new Queue(false);
		queueInputPipe = new QueueIOPipe(queue);
		queue.addIncomingMessagePolicy(lwp);
		
		// Create an output pipe for the controller
		queue.addOutputPipe(LocalWhitePages.CONTROLLER_ID, new QueueIOPipe(new Queue (false)));
	}
	
	/**
	 * 
	 * @see junit.framework.TestCase#tearDown()
	 */
	public void tearDown() {
		d.exit(false);
		queue.end();
	}
	
	/**
	 * Test the reception of an AGENT_ADDRESS
	 * message
	 * @throws Exception 	if an error occurs
	 */
	public void testAgentAddress() throws Exception {
		
		String agent1ID = "agent1";
		String agent2ID = "agent2";
		int port1 = 4454;
		AgentAddress address2 = Controller.PipeFactoryInstance.getSelfAddress(4454);
		QueueInputPipeInterface externalAgent = null;
		
		// this input pipe is there so that the local white pages can create a tcp output pipe
		externalAgent = Controller.PipeFactoryInstance.inputPipe(new Queue(false), address2);
		
		SimpleAgent agent1 = new SimpleAgent(queue, agent1ID, port1);
				
		d.addAgent(agent1ID, agent1);
		
		MessageWith3Payloads<String, String, AgentAddress> msg = new MessageWith3Payloads<String, String, AgentAddress>(WhitePages.AGENT_ADDRESS, agent1ID, agent2ID, address2);
		queue.sendMessageToSelf(msg);
		
		Thread.sleep(500);
		
		HashMap<String, QueueOutputPipeInterface> agents = lwp.getAgents();
		assertEquals(1, agents.size());
		assertTrue(agents.containsKey(agent2ID));
		
		// close the socket
		externalAgent.close();
	}
	
	/**
	 * Test the reception of LOCAL_AGENT_REPORTING
	 * @throws InterruptedException 	if the thread is interrupted while sleeping
	 */
	public void testLocalAgentReporting() throws InterruptedException {
		// create an extra queue
		Queue agentQueue = new Queue(false);
		
		// connect the queues
		agentQueue.addOutputPipe("WhitePages", queueInputPipe);
		
		// Create the agent
		String name = "agent";

		SimpleAgent agent = new SimpleAgent(agentQueue, name, 4454);
		
		// Allow the agent to report to the local white pages
		agent.agentReportLocal();
		
		Thread.sleep(500);
		
		// A new OutputPipe should now have been added to the queue
		Message msg = new Message(SimpleAgent.TEST);
		queue.sendMessage(name, msg);
		
		Thread.sleep(500);
		
		assertEquals(1, agent.getReceived());
		agentQueue.end();
	}
	
	/**
	 * Test the reception of KILL_AGENTS
	 * @throws InterruptedException 	if the thread is interrupted while sleeping
	 */
	public void testKillAgents() throws InterruptedException {
		
		LocalAgentReport msg = new LocalAgentReport ("agent", 4454, null);
		queue.sendMessageToSelf(msg);
		
		Message msg2 = new Message(WhitePages.KILL_AGENTS);
		queue.sendMessageToSelf(msg2);
		
		Thread.sleep(500);
		
		HashMap<String, QueueOutputPipeInterface> agents = lwp.getAgents();
		assertEquals(0, agents.size());
	}
	
	/**
	 * Test the reception of LOCAL_AGENT_ADDRESS_REQUEST for a known agent
	 * @throws InterruptedException 	if the thread is interrupted while sleeping
	 */
	public void testLocalAgentAddressRequestKnown() throws InterruptedException {
		String name1 = "agent1";
		String name2 = "agent2";

		// Create a new queue for each agent and connect it to the main queue
		Queue agentQueue1 = new Queue(false);
		Queue agentQueue2 = new Queue(false);
		
		QueueIOPipe localInput = new QueueIOPipe(queue);
		agentQueue1.addOutputPipe("WhitePages", localInput);
		agentQueue2.addOutputPipe("WhitePages", localInput);
		
		// create two agents
		SimpleAgent agent1 = new SimpleAgent(agentQueue1, name1, 4454);
		SimpleAgent agent2 = new SimpleAgent(agentQueue2, name2, 4464);
		d.addAgent(name1, agent1);
		d.addAgent(name2, agent2);
		
		// Report the agents to the local whitepages
		agent1.agentReportLocal();
		agent2.agentReportLocal();
		
		Thread.sleep(500);

		// Let agent 1 ask the address of agent 2;
		agent1.askAddress(name2);
		
		Thread.sleep(500);
		
		// Agent 1 should now be able to send a message to Agent 2
		agent1.sendTestMessage(name2);
		
		Thread.sleep(500);
		
		assertEquals(1, agent2.getReceived());
		agentQueue1.end();
		agentQueue2.end();
	}
	
	/**
	 * Test the reception of LOCAL_AGENT_ADDRESS_REQUEST for an unknown agent, i.e. the central white pages is contacted
	 * @throws InterruptedException 	if the thread is interrupted while sleeping
	 * @throws IOException 				if an I/O error occurred
	 */
	public void testLocalAgentAddressRequestUnknown() throws InterruptedException, IOException {
		String name1 = "agent1";
		String name2 = "agent2";

		int port1 = 44054;
		int port2 = 44155;
		int daemonPort = 44255;
				
		// Create a new queue for each agent and connect it to the main queue
		Queue agentQueue1 = new Queue(false);
		Controller.PipeFactoryInstance.inputPipe(agentQueue1, Controller.PipeFactoryInstance.getSelfAddress(port1));
		Queue agentQueue2 = new Queue(false);
		Controller.PipeFactoryInstance.inputPipe(agentQueue2, Controller.PipeFactoryInstance.getSelfAddress(port2));
		Queue controllerQueue = new Queue(false);
		QueueIOPipe controlInput = new QueueIOPipe(controllerQueue);
		
		Controller.PipeFactoryInstance.inputPipe(queue, Controller.PipeFactoryInstance.getSelfAddress(daemonPort));
		agentQueue1.addOutputPipe("WhitePages", queueInputPipe);
		queue.addOutputPipe(LocalWhitePages.CONTROLLER_ID, controlInput);
		agentQueue1.addOutputPipe(LocalWhitePages.CONTROLLER_ID, controlInput);
		agentQueue2.addOutputPipe(LocalWhitePages.CONTROLLER_ID, controlInput);
		
		// create the global white pages
		WhitePages wp = new WhitePages();
		controllerQueue.addIncomingMessagePolicy(wp);
		
		// create two agents
		SimpleAgent agent1 = new SimpleAgent(agentQueue1, name1, port1);
		SimpleAgent agent2 = new SimpleAgent(agentQueue2, name2, port2);
		
		d.addAgent(name1, agent1);
		
		// Report to global white pages
		MessageAgentReporting msg = new MessageAgentReporting(MessageAgentReporting.DEAMON_REPORTING, d.daemonId, Controller.PipeFactoryInstance.getSelfAddress(daemonPort));
		queue.sendMessage(LocalWhitePages.CONTROLLER_ID, msg);
		agent1.agentReportGlobal();
		agent2.agentReportGlobal();
		
		Thread.sleep(500);
		// Report the agent to the local whitepages
		agent1.agentReportLocal();
		
		Thread.sleep(500);

		// Let agent 1 ask the address of agent 2;
		agent1.askAddress(name2);
		
		Thread.sleep(1000);
		
		// Agent 1 should now be able to send a message to Agent 2
		agent1.sendTestMessage(name2);
		
		Thread.sleep(1000);
		
		assertEquals(1, agent2.getReceived());
		agentQueue1.end();
		agentQueue2.end();
		wp.close();
	}
	
}
