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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.AgentAddress;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.controller.messages.MessageAgentReporting;
import frodo2.controller.userIO.UserIO;
import frodo2.daemon.LocalWhitePages;

/**
 * @author Brammert Ottens, Thomas Leaute, Xavier Olive
 *
 * The white pages contains a list of the ip addresses of all
 * agents and all daemons
 */
public class WhitePages implements IncomingMsgPolicyInterface<String> {

	/** Message used by white pages to notified the interested that an agent reported itself*/
	public static final String AGENT_REPORTED = "Agent-Reported";
	
	/** Message used to tell the configuration Manager that all agents are killed*/
	public static final String ALL_AGENTS_KILLED = "All-Agents-Killed";
	
	/** The message containing the list of available daemons*/
	public static final String DEAMONS_CONFIG_MSG = "Daemon-List";
	
	/** Message containing the list of registered daemons for the user interface*/
	public static final String DEAMONS_LIST = "Daemon list";
	
	/** Message containing the list of registered agents for the user interface*/
	public static final String AGENTS_LIST = "Agent list";
	
	/** Message used to tell an agent to connect to its neighbours  
	 * @todo This should be defined in AgentInterface */
	public static final String CONNECT_AGENT = "Connect-Agent";	
	
	/** Message to signal to the daemon to kill agents*/
	public static final String KILL_AGENTS = "Kill-Agents";
	
	/** Message send to the local white pages that contains the address of an agent*/
	public static final String AGENT_ADDRESS = "Agent-Address";
	
	/** The list of available daemons*/
	HashMap<String, AgentAddress> daemons;
	
	/** The list of available agents*/
	HashMap<String, AgentAddress> agents;
	
	/** The queue this listener belongs to*/
	private Queue queue;
	
	/** Counts the number of daemons that killed their agents*/
	private int daemonCount;
	
	/** The list of messages types this listener wants to be notified of */
	private ArrayList <String> msgTypes = new ArrayList <String> ();
	
	/** The controllers IP address*/
	String localAddress;
	
	/**
	 * Constructor
	 * 
	 * Initialize both lists
	 */
	public WhitePages() {
		daemons = new HashMap<String, AgentAddress>();
		agents = new HashMap<String, AgentAddress>();
		msgTypes.add(ConfigurationManager.REQUEST_DAEMONS_CONFIG_MSG);
		msgTypes.add(MessageAgentReporting.AGENT_REPORTING);
		msgTypes.add(LocalWhitePages.AGENTS_KILLED);
		msgTypes.add(MessageAgentReporting.DEAMON_REPORTING);
		msgTypes.add(ConfigurationManager.START);
		msgTypes.add(ConfigurationManager.CONNECT);
		msgTypes.add(ConfigurationManager.KILL_ALL_AGENTS);
		msgTypes.add(LocalWhitePages.AGENT_ADDRESS_REQUEST);
		msgTypes.add(UserIO.DEAMON_LIST_REQUEST);
		msgTypes.add(UserIO.AGENTS_LIST_REQUEST);
		
		try {
			localAddress = InetAddress.getLocalHost().getHostName();	
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
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
		String msgType = msg.getType();
		
		if(msgType.equals(ConfigurationManager.REQUEST_DAEMONS_CONFIG_MSG)) {
			MessageWithPayload<HashMap<String, AgentAddress>> newMsg = new MessageWithPayload<HashMap<String, AgentAddress>>(DEAMONS_CONFIG_MSG, daemons);
			queue.sendMessageToSelf(newMsg);
		}
		
		if(msgType.equals(UserIO.DEAMON_LIST_REQUEST)) {
			MessageWithPayload<HashMap<String, AgentAddress>> newMsg = new MessageWithPayload<HashMap<String, AgentAddress>>(DEAMONS_LIST, daemons);
			queue.sendMessageToSelf(newMsg);
		}
		
		if(msgType.equals(UserIO.AGENTS_LIST_REQUEST)) {
			MessageWithPayload<HashMap<String, AgentAddress>> newMsg = new MessageWithPayload<HashMap<String, AgentAddress>>(AGENTS_LIST, agents);
			queue.sendMessageToSelf(newMsg);
		}
		
		if(msgType.equals(MessageAgentReporting.AGENT_REPORTING)) {
			MessageAgentReporting msgR = (MessageAgentReporting)msg;
				
			synchronized (agents) {
				agents.put(msgR.getID(), msgR.getAddress());
			}
			
			MessageWithPayload<String> newMsg = new MessageWithPayload<String>(AGENT_REPORTED, msgR.getID());
			try {
				queue.addOutputPipe(msgR.getID(), Controller.PipeFactoryInstance.outputPipe(msgR.getAddress()));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}	
			queue.sendMessageToSelf(newMsg);
		}
		
		/// @todo Make sure that, as soon as the experiments are running
		//       no new daemons can be added
		if(msgType.equals(MessageAgentReporting.DEAMON_REPORTING)) {
			MessageAgentReporting msgR = (MessageAgentReporting)msg;
			
			daemons.put(msgR.getID(), msgR.getAddress());
			try {
				queue.addOutputPipe(msgR.getID(), Controller.PipeFactoryInstance.outputPipe(msgR.getAddress()));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			/** If the WhitePages know when to start the solving process, let it start */
			if (Controller.daemonNb!=null) {
				if (0==--Controller.daemonNb) {
					MessageWithPayload<HashMap<String, AgentAddress>> newMsg = new MessageWithPayload<HashMap<String, AgentAddress>>(DEAMONS_CONFIG_MSG, daemons);
					queue.sendMessageToSelf(newMsg);
				}
			}
		}
		
		if(msgType.equals(ConfigurationManager.CONNECT)) {
			synchronized (agents) {
				queue.sendMessageToMulti(agents.keySet(), new Message (CONNECT_AGENT));
			}
		}
		
		if(msgType.equals(ConfigurationManager.START)) {
			synchronized (agents) {
				Message newMsg = new Message(AgentInterface.START_AGENT);
				queue.sendMessageToMulti(agents.keySet(), newMsg);
			}
		}
		
		//@todo we assume here that no more daemons are added while running
		// the experiments!!
		if(msgType.equals(ConfigurationManager.KILL_ALL_AGENTS)) {
			// remove agent outputPipes
			synchronized (agents) {
				for(String agentId : agents.keySet()) {
					queue.getOutputPipe(agentId).close();
					queue.removeOutputPipe(agentId);
				}
				agents.clear();
			}
			daemonCount = 0;
			queue.sendMessageToMulti(daemons.keySet(), new Message(KILL_AGENTS));
		}
		
		if(msgType.equals(LocalWhitePages.AGENTS_KILLED)) {
			daemonCount++;
			if(daemonCount == daemons.size()) {
				Message newMsg = new Message(ALL_AGENTS_KILLED);
				queue.sendMessageToSelf(newMsg);
			}
			daemonCount = 0;
		}
		
		// An agent requests an address of an agent
		if(msgType.equals(LocalWhitePages.AGENT_ADDRESS_REQUEST)) {
			
			MessageWith3Payloads<String, String, String> msgCast = (MessageWith3Payloads<String, String, String>) msg;
			String daemonID = msgCast.getPayload1();
			String fromID = msgCast.getPayload2();
			String toID = msgCast.getPayload3();
			
			if(agents.containsKey(toID)) {
				MessageWith3Payloads<String, String, AgentAddress> newMsg = new MessageWith3Payloads<String, String, AgentAddress>(AGENT_ADDRESS, fromID, toID, agents.get(toID));
				queue.sendMessage(daemonID, newMsg);
			}
		}
		
	}

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;

	}
	
	/**
	 * This function needs to be called to make sure that all the sockets are properly closed
	 */
	public void close() {
		for(String daemonId : daemons.keySet()) {
//			((QueueOutputPipeTCP)queue.getOutputPipe(daemonId)).close();
			queue.removeOutputPipe(daemonId);
		}
		synchronized (agents) {
			for(String agentId : agents.keySet()) {
//				((QueueOutputPipeTCP)queue.getOutputPipe(agentId)).close();
				queue.removeOutputPipe(agentId);
			}
		}
	}
}
