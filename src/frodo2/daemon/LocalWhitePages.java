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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.communication.AgentAddress;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.controller.ConfigurationManager;
import frodo2.controller.Controller;
import frodo2.controller.WhitePages;
import frodo2.controller.messages.MessageAgentReporting;

/**
 * @author brammertottens
 * @todo implement the measure messages and measure nccc's also for the deamon 
 */
public class LocalWhitePages implements IncomingMsgPolicyInterface<String> {

	/** Message used to request to address of a specific agent*/
	public static final String AGENT_ADDRESS_REQUEST = "Agent-Address-Request";
	
	/** The ID of the controller*/
	public static final String CONTROLLER_ID = "Controller";
	
	/** Send an Output pipe to an agent*/
	public static final String AGENT_OUTPUT_PIPE = "Agent-Output-Pipe";
	
	/** Message send to agent to kill it*/
	public static final String DIE = "Die";
	
	/** Message send when an agent is killed by the daemon*/
	public static final String AGENTS_KILLED = "Agents-killed";
	
	/** Each agent has its own QueueIOPipe for JVM-internal messages*/
	HashMap<String, QueueOutputPipeInterface> externalAgents;
	
	/** A set of local agent IDs*/
	HashSet<String> localAgents;
	
	/** The queue used to send messages*/
	private Queue queue;
	
	/** The list of messages types this listener wants to be notified of */
	private ArrayList <String> msgTypes = new ArrayList <String> ();
	
	/** Pointer to the daemon*/
	private Daemon daemon;
	
	/** Pointer to the controller*/
	private Controller controller;
	
	/** global is true if the experiments run on one machine*/
	boolean global;
	
	/**
	 *  Constructor to be used when the lwp is a listener for the daemon
	 *  @param daemon 	the daemon
	 */
	public LocalWhitePages(Daemon daemon) {
		externalAgents = new HashMap<String, QueueOutputPipeInterface>();
		msgTypes.add(AGENT_OUTPUT_PIPE);
		msgTypes.add(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST);
		msgTypes.add(AgentInterface.LOCAL_AGENT_REPORTING);
		msgTypes.add(WhitePages.AGENT_ADDRESS);
		msgTypes.add(WhitePages.KILL_AGENTS);
		this.daemon = daemon;
		global = false;
	}
	
	/**
	 * Constructor to be used when the lwp is a listener for the controller
	 * @param controller 	the controller
	 */
	public LocalWhitePages(Controller controller) {
		externalAgents = new HashMap<String, QueueOutputPipeInterface>();
		localAgents = new HashSet<String>();
		msgTypes.add(AGENT_OUTPUT_PIPE);
		msgTypes.add(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST);
		msgTypes.add(AgentInterface.LOCAL_AGENT_REPORTING);
		msgTypes.add(WhitePages.AGENT_ADDRESS);
		msgTypes.add(WhitePages.KILL_AGENTS);
		msgTypes.add(ConfigurationManager.START);
		msgTypes.add(ConfigurationManager.CONNECT);
		msgTypes.add(ConfigurationManager.KILL_ALL_AGENTS);
		this.controller = controller;
		global = true;
	}

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		return msgTypes;
	}

	
	/** 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		String type = msg.getType();

		if(type.equals(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST)) { // an agent wants an address
			MessageWith2Payloads<String, String> msgR = (MessageWith2Payloads<String, String>)msg;
			String fromID = msgR.getPayload1();
			String toID   = msgR.getPayload2();
			
			QueueIOPipe pipe = (QueueIOPipe)queue.getOutputPipe(toID);
			
			// we assume that every agent that reports to the local whitepages also reports an QueueIOPipe
			// Hence, if there is none the agent we are looking for is not local
			if(pipe != null) {
				if(global) {
					controller.addOutputPipe(fromID, toID, pipe);
				} else {
					daemon.addOutputPipe(fromID, toID, pipe);
				}
			} else if (externalAgents.containsKey(toID)){
				if(global) {
					controller.addOutputPipe(fromID, toID, externalAgents.get(toID));
				} else {
					daemon.addOutputPipe(fromID, toID, externalAgents.get(toID));
				}
			} else {
				MessageWith3Payloads<String, String, String> newMsg = new MessageWith3Payloads<String, String, String>(AGENT_ADDRESS_REQUEST, this.daemon.daemonId, fromID, toID);
				queue.sendMessage(CONTROLLER_ID, newMsg);
			}
			return;
		}
		
		if(type.equals(WhitePages.AGENT_ADDRESS)) {
			MessageWith3Payloads<String, String, AgentAddress> msgR = (MessageWith3Payloads<String, String, AgentAddress>)msg;
			
			String fromID = msgR.getPayload1();
			String toID = msgR.getPayload2();
			AgentAddress address = msgR.getPayload3();
			
			// create a new TCPPipe
			QueueOutputPipeInterface pipe = null;
			
			try {
				pipe = Controller.PipeFactoryInstance.outputPipe(address);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			externalAgents.put(toID, pipe);
			
			if(global) {
				controller.addOutputPipe(fromID, toID, externalAgents.get(toID));
			} else {
				daemon.addOutputPipe(fromID, toID, externalAgents.get(toID));
			}
			return;
		}
		
		// A local agent reports in by sending a QueuIOPipe to the white pages
		if(type.equals(AgentInterface.LOCAL_AGENT_REPORTING)) {
			LocalAgentReport msgR = (LocalAgentReport) msg;
			String agentID = msgR.getAgentID();
			queue.addOutputPipe(agentID, msgR.getPayload3());
			
			if(global) {
				MessageWithPayload<String> newMsg = new MessageWithPayload<String>(WhitePages.AGENT_REPORTED, agentID);
				queue.sendMessageToSelf(newMsg);
				localAgents.add(agentID);
			} else {
				// report to the global white pages
				MessageAgentReporting msg2 = new MessageAgentReporting(MessageAgentReporting.AGENT_REPORTING, agentID, daemon.daemonAddress.newAddress(msgR.getPort()));
				queue.sendMessage(CONTROLLER_ID, msg2);
			}
			return;
		}
		
		if(type.equals(WhitePages.KILL_AGENTS)) { // All agents are to be killed
			queue.sendMessageToMulti(daemon.getAgents(), new Message(DIE));
			for(String agent : daemon.getAgents()) {
				queue.removeOutputPipe(agent);
			}
			daemon.clearAgents();
			externalAgents.clear();
						
			Message newMsg = new Message(AGENTS_KILLED);
			queue.sendMessage(LocalWhitePages.CONTROLLER_ID, newMsg);
			return;
		}
		
		if(type.equals(ConfigurationManager.CONNECT)) {
			synchronized (localAgents) {
				queue.sendMessageToMulti(localAgents, new Message(WhitePages.CONNECT_AGENT));
			}
			return;
		}

		if(type.equals(ConfigurationManager.START)) {
			synchronized (localAgents) {
				queue.sendMessageToMulti(localAgents, new Message(AgentInterface.START_AGENT));
			}
			return;
		}

		//@todo we assume here that no more daemons are added while running
		// the experiments!!
		if(type.equals(ConfigurationManager.KILL_ALL_AGENTS)) {
			queue.sendMessageToMulti(controller.getAgents(), new Message(DIE));
			for(String agent : controller.getAgents()) {
				queue.removeOutputPipe(agent);
			}
			controller.clearAgents();
			externalAgents.clear();
			localAgents.clear();

			Message newMsg = new Message(WhitePages.ALL_AGENTS_KILLED);
			queue.sendMessageToSelf(newMsg);
		}
	}

	
	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
}
