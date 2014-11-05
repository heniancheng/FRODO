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

package frodo2.controller;

import java.util.ArrayList;
import java.util.Collection;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.AgentAddress;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.controller.messages.MessageAgentReporting;
import frodo2.controller.userIO.UserIO;
import frodo2.daemon.LocalWhitePages;

import junit.framework.TestCase;

/**
 * A JUnit test class for the configuration manager
 * 
 * @author Brammert Ottens
 * @author Thomas Leaute
 */
public class TestConfigurationManager extends TestCase {
	
	/** Directory containing the test files. Must end with a slash. */
	private static String testDir = TestConfigurationManager.class.getResource("testFiles").getFile() + "/";

	
	/**
	 * The address of the agent to be used
	 */
	public static AgentAddress address = Controller.PipeFactoryInstance.getSelfAddress(4444);
	
	/** This controller is only used to be able to create a Configuration manager*/
	private Controller control;
	
	/** A configuration manager, it handles the configuration file*/
	private ConfigurationManager configManager;
	
	/** A simple daemon*/
	private SimpleDaemonListener daemonListener;
	
	/** The white pages*/
	private WhitePages whitePage;
	
	/** Queue that handles all the messages*/
	private Queue queue;
	
	/** An input pipe to test the sending of messages to the daemon*/
	private QueueInputPipeInterface input;

	
	/**
	 * A simple class that listens for messages sent to a daemon
	 * @author brammertottens
	 *
	 */
	private class SimpleDaemonListener implements IncomingMsgPolicyInterface <String> {
		
		/** the queue that handles the messages*/
		private Queue queue;
		
		/** number of agent definitions that have been received*/
		private int received = 0;
		
		/** The list of message types this listener wants to be notified of */
		private ArrayList <String> msgTypes = new ArrayList <String> ();

		/**
		 * A simple constructor
		 * @param countNCCCs 	whether we should count Non-Concurrent Constraint Checks
		 */
		public SimpleDaemonListener(boolean countNCCCs) {
			msgTypes.add(ConfigurationManager.AGENT_CONFIGURATION_MESSAGE);
			msgTypes.add(AgentInterface.START_AGENT);
			msgTypes.add(WhitePages.KILL_AGENTS);
			msgTypes.add(WhitePages.CONNECT_AGENT);
		}
		
		/**
		 * 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) {
			this.queue = queue;
		}
		
		/**
		 * 
		 * @see IncomingMsgPolicyInterface#notifyIn(Message)
		 */
		public void notifyIn(Message msg) {
			
			if(msg.getType().equals(ConfigurationManager.AGENT_CONFIGURATION_MESSAGE)) {
				received++;
				
				// report a dummy agent to the white pages
				MessageAgentReporting newMsg = new MessageAgentReporting(MessageAgentReporting.AGENT_REPORTING, "Agent1", address);
				queue.sendMessageToSelf(newMsg);
			}
			
			if(msg.getType().equals(AgentInterface.START_AGENT)) {
				// immediately report the agent to be finished
				Message newMsg = new Message(AgentInterface.AGENT_FINISHED);
				queue.sendMessageToSelf(newMsg);
				
			}
			
			if(msg.getType().equals(WhitePages.KILL_AGENTS)) {
				//report all agents killed
				Message newMsg = new Message(LocalWhitePages.AGENTS_KILLED);
				queue.sendMessageToSelf(newMsg);
			}
			
			if(msg.getType().equals(WhitePages.CONNECT_AGENT)) {
				// report connected
				Message newMsg = new Message(AgentInterface.AGENT_CONNECTED);
				queue.sendMessageToSelf(newMsg);
			}
		}

		/**
		 * 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
		 */
		public Collection<String> getMsgTypes() {
			return msgTypes;
		}

		/** @return the number of agent definitions received */
		public int getNbrReceived() {
			return received;
		}
		
	}
	
	/**
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp() throws Exception {
		control = new Controller(false, false, testDir);
		configManager = new ConfigurationManager(control, false, testDir);
		whitePage = new WhitePages();
		queue = new Queue(false);
		input = Controller.PipeFactoryInstance.inputPipe(queue, address);
		
		queue.addIncomingMessagePolicy(configManager);
		queue.addIncomingMessagePolicy(whitePage);
	}

	/**
	 * 
	 * @see junit.framework.TestCase#tearDown()
	 */
	public void tearDown() {
		input.close();
		whitePage.close();
		queue.end();
		control.exit(false);
	}
	
	/**
	 * Tests the parsing of the configuration file
	 * with certain parameters omitted. These should
	 * be set to default values.
	 * @throws Exception if an error occurs
	 */
	public void testParseConfigurationFile1() throws Exception {
		
		daemonListener = new SimpleDaemonListener(false);
		queue.addIncomingMessagePolicy(daemonListener);
		
		configManager.parseConfigurationFile(testDir + "testConfig.xml");
		
		assertTrue(configManager.getResultFile().equals(testDir + "resultFile.log"));
		assertTrue(configManager.getAgentDescription().equals(testDir + "description.xml"));
		assertTrue(configManager.getDebug() == false);
		assertTrue(configManager.getTimeOut() == -1);
		assertTrue(configManager.getLogClass().equals(""));
	}
	
	/**
	 * Tests the parsing of a configuration file
	 * with all the options specified
	 * @throws Exception if an error occurs
	 */
	public void testParseConfigurationFile2() throws Exception {

		daemonListener = new SimpleDaemonListener(false);
		queue.addIncomingMessagePolicy(daemonListener);

		configManager.parseConfigurationFile(testDir + "testConfig1.xml");
		
		assertTrue(configManager.getResultFile().equals(testDir + "resultFile.log"));
		assertTrue(configManager.getAgentDescription().equals(testDir + "description.xml"));
		assertTrue(configManager.getDebug() == true);
		assertTrue(configManager.getTimeOut() == 100);
		assertTrue(configManager.getLogClass().equals("logger.class"));
	}
	
	/** Tests starting an experiment
	 * @throws Exception 	if an error occurs
	 */
	public void testStartExperiment() throws Exception {
		testStartExperiment (false);
	}

	/** Tests starting an experiment with NCCCs
	 * @throws Exception 	if an error occurs
	 */
	public void testStartExperimentNCCCs() throws Exception {
		testStartExperiment (true);
	}

	/**
	 * Tests starting an experiment
	 * @param countNCCCs 	whether we should count NCCCs
	 * @throws Exception 	if an error occurs
	 */
	public void testStartExperiment(boolean countNCCCs) throws Exception {
		
		daemonListener = new SimpleDaemonListener(countNCCCs);
		queue.addIncomingMessagePolicy(daemonListener);

		String daemonName = "daemon";
		
		AgentAddress daemonAddress = address.newAddress(4444);
		
		// read in the configuration file
		configManager.parseConfigurationFile(testDir + "testConfig.xml");
		Thread.sleep(500);
		
		
		// report the daemon to the white pages
		MessageAgentReporting msg1 = new MessageAgentReporting(MessageAgentReporting.DEAMON_REPORTING, daemonName, daemonAddress);
		queue.sendMessageToSelf(msg1);
		
		Thread.sleep(500);
		
		// start the experiment
		Message msg2 = new Message(UserIO.START_MSG);
		queue.sendMessageToSelf(msg2);
		
		// check if the problem definition reached the daemon
		Thread.sleep(1000);
		assertTrue(daemonListener.getNbrReceived() == 2);
	}
}
