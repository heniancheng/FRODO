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
package frodo2.controller;

import java.util.HashMap;

import frodo2.communication.AgentAddress;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.controller.messages.MessageAgentReporting;
import junit.framework.TestCase;

/**
 * @author Brammert Ottens
 * @author Thomas Leaute
 *
 * A JUnit test class for the white pages
 */
public class TestWhitePages extends TestCase {

	/** The white pages*/
	WhitePagesExtention whitePage;
	
	/** Queue that handles all the messages*/
	Queue queue;
	
	/** An input pipe to test the sending of message to the daemon*/
	QueueInputPipeInterface input;
	
	/**
	 * Extention of the white pages that enables one to get the list of daemons, the list of agents
	 * and the local address of the white pages
	 * @author brammertottens
	 *
	 */
	private class WhitePagesExtention extends WhitePages {
		
		
		/**
		 * Function used for testing
		 * @return daemons list of daemons
		 */
		public HashMap<String, AgentAddress> getDaemons() {
			return daemons;
		}
		
		/**
		 * Function used for testing
		 * @return agents list of agents
		 */
		public HashMap<String, AgentAddress> getAgents() {
			return agents;
		}
		
	}
	/**
	 * a simple constructor
	 */
	public TestWhitePages() {
		/// @todo Auto-generated constructor stub
		assert false : "not implemented!";
	}

	/**
	 * @param name 	 the name of the test method
	 */
	public TestWhitePages(String name) {
		super(name);
		/// @todo Auto-generated constructor stub
	}
	
	/**
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp() throws Exception {
//		control = new Controller();
		whitePage = new WhitePagesExtention();
		queue = new Queue(false);
		input = Controller.PipeFactoryInstance.inputPipe(queue, Controller.PipeFactoryInstance.getSelfAddress(4444));
		queue.addIncomingMessagePolicy(whitePage);
	}

	/**
	 * 
	 * @see junit.framework.TestCase#tearDown()
	 */
	public void tearDown() {
		whitePage.close();
		input.close();
		queue.end();
	}
	
	/**
	 * Test that a daemon that reports is handeled correctly
	 * @throws Exception 	if an error occurs
	 */
	@SuppressWarnings("unused")
	public void testDaemonReporting() throws Exception {
		String daemonName = "daemon";
		AgentAddress daemonAddress = Controller.PipeFactoryInstance.getSelfAddress(4445); 
		
		// create a inputPipe so that reporting to the daemon does not cause any errors
		QueueInputPipeInterface pipe = Controller.PipeFactoryInstance.inputPipe(new Queue (false), daemonAddress);
		
		MessageAgentReporting msg1 = new MessageAgentReporting(MessageAgentReporting.DEAMON_REPORTING, daemonName, daemonAddress);
		queue.sendMessageToSelf(msg1);
		
		// make sure the message is received
		Thread.sleep(500);
		
		HashMap<String, AgentAddress> daemons = whitePage.getDaemons();
		assertEquals(daemonAddress,daemons.get(daemonName));
	}
	
	/**
	 * Test that an agent that reports is handeled correctly
	 * @throws InterruptedException 	if the thread is interrupted while sleeping
	 */
	public void testAgentReporting() throws InterruptedException {
		String agentName = "agent";
		AgentAddress agentAddress = Controller.PipeFactoryInstance.getSelfAddress(4445);
		
		MessageAgentReporting msg1 = new MessageAgentReporting(MessageAgentReporting.AGENT_REPORTING, agentName, agentAddress);
		queue.sendMessageToSelf(msg1);
		
		// make sure the message is received
		Thread.sleep(500);
		
		HashMap<String, AgentAddress> agents = whitePage.getAgents();
		assertEquals(agentAddress, agents.get(agentName));
	}
	
	/**
	 * Test that when all agents should be killed they are removed from the list of
	 * agents
	 * @throws InterruptedException 	if the thread is interrupted while sleeping
	 */
	public void testKillAllAgents() throws InterruptedException {
		String agentName = "agent";
		AgentAddress agentAddress = Controller.PipeFactoryInstance.getSelfAddress(4445); 
		String daemonName = "daemon";
		AgentAddress daemonAddress = Controller.PipeFactoryInstance.getSelfAddress(4445);
		
		// add a daemon and an agent to the whitepages
		MessageAgentReporting msg1 = new MessageAgentReporting(MessageAgentReporting.DEAMON_REPORTING, daemonName, daemonAddress);
		queue.sendMessageToSelf(msg1);
		
		MessageAgentReporting msg2 = new MessageAgentReporting(MessageAgentReporting.AGENT_REPORTING, agentName, agentAddress);
		queue.sendMessageToSelf(msg2);
		
		Thread.sleep(500);
		
		Message msg = new Message(ConfigurationManager.KILL_ALL_AGENTS);
		queue.sendMessageToSelf(msg);
		
		Thread.sleep(500);
		
		assertNull(queue.getOutputPipe(agentName));
		assertTrue(whitePage.getAgents().size() == 0);
	}

}
