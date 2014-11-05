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

/** Package containing various algorithms */
package frodo2.algorithms;

import java.util.HashMap;
import java.util.Map;

import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.solutionSpaces.Addable;

/** All agents corresponding to various algorithms should implement this interface
 * @author Brammert Ottens
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @warning Any class implementing this interface should have a constructor that takes in: 
 * a ProblemInterface containing the problem description, a Document containing the agent description, and a CentralMailer. 
 * @todo The methods connect(), start() and kill() are defined here so that the daemon can call them. 
 * Instead, the daemon currently sends messages to the agent. This should be clarified. If these methods
 * are never called except by the agent itself, they should be removed from the interface. 
 */
public interface AgentInterface < V extends Addable<V> > {
	
	/** Recipient ID to which statistics about algorithm execution should be sent */
	public static final String STATS_MONITOR = "Statistics Monitor";
	
	/** Message to be sent if an agent has a connection with all its neighbours */
	public static final String AGENT_CONNECTED = "Agent-Connected";
	
	/** The message sent when an agent has terminated */
	public static final String AGENT_FINISHED = "Agent-Ready";
	
	/** The message sent when it has been detected that all agents are waiting for messages, but there are no more messages on the way */
	public static final String ALL_AGENTS_IDLE = "ALL_AGENTS_IDLE";
	
	/** An AGENT_FINISHED message containing statistics about messages sent
	 * @author Thomas Leaute
	 */
	public static class AgentFinishedMessage extends MessageWith3Payloads< HashMap<String, Integer>, HashMap<String, Long>, HashMap<String, Long> > {

		/** Empty constructor used for externalization */
		public AgentFinishedMessage () { }

		/** Constructor
		 * @param msgNbrs 		for each message type, the number of messages sent of that type
		 * @param msgSizes 		for each message type, the total amount of information sent in messages of that type, in bytes
		 * @param maxMsgSizes 	for each message type, the size (in bytes) of the largest message of this type
		 */
		public AgentFinishedMessage(HashMap<String, Integer> msgNbrs, HashMap<String, Long> msgSizes, HashMap<String, Long> maxMsgSizes) {
			super(AGENT_FINISHED, msgNbrs, msgSizes, maxMsgSizes);
		}
		
		/** @return for each message type, the number of messages sent of that type */
		public HashMap<String, Integer> getMsgNbrs () {
			return this.getPayload1();
		}
		
		/** @return for each message type, the total amount of information sent in messages of that type, in bytes */
		public HashMap<String, Long> getMsgSizes () {
			return this.getPayload2();
		}
		
		/** @return for each message type, the size (in bytes) of the largest message of this type */
		public HashMap<String, Long> getMaxMsgSizes () {
			return this.getPayload3();
		}
		
		/** @see MessageWith3Payloads#toString() */
		@Override
		public String toString () {
			return "Message(" + this.getType() + ")\n\tmsgNbrs = " + this.getMsgNbrs() + "\n\tmsgSizes = " + this.getMsgSizes() + 
					"\n\tmaxMsgSizes = " + this.getMaxMsgSizes();
		}
	}
	
	/** The message an agent uses to ask the white pages for an address*/
	public static final String LOCAL_AGENT_ADDRESS_REQUEST = "local-address-request";
	
	/** an agent reports to its local white pages*/
	public static final String LOCAL_AGENT_REPORTING = "local-agent-reporting";

	/** Message used to tell an agent to start its algorithm */
	public static final String START_AGENT = "Start-Agent";
	
	/** Message used to tell an agent to stop */
	public static final String STOP_AGENT = "Stop-Agent";
	
	/** Tells the agent to report to the local white pages */
	public void report ();
	
	/** Tells the agent to start requesting connections to other agents from the white pages */
	public void connect ();
	
	/** Starts the algorithm */
	public void start ();
	
	/** Stops the algorithm */
	public void kill ();
	
	/** @return the agent's ID */
	public String getID();
	
	/** Returns the solution found by the algorithm upon termination 
	 * @return a global assignment
	 */
	public Map<String, V> getCurrentSolution();
	
	/**
	 * Adds an output pipe to the agent. If an agent is connected to all
	 * its neighbours, it should send an AGENT_CONNECTED message to the controller
	 * @param agent destination of the pipe
	 * @param outputPipe output pipe
	 */
	public void addOutputPipe(String agent, QueueOutputPipeInterface outputPipe);

	/** Sets up the agent to communicate with a daemon, a controller, and its neighbors
	 * @param toDaemonPipe output pipe to the daemon
	 * @param toControllerPipe the output pipe that should be used to communicate with the controller
	 * @param port the port the agent is listening on. If < 0, no TCP pipe should be created. 
	 */
	public void setup (QueueOutputPipeInterface toDaemonPipe, QueueOutputPipeInterface toControllerPipe, int port);
}
