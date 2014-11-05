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

package frodo2.algorithms.varOrdering.election;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;

/** This distributed leader election protocol chooses a leader agent as the one with highest unique ID
 * @author Thomas Leaute
 * @param <T> the type used for agent IDs
 * @warning This protocol elects an \b agent, not a \b variable. Therefore, we should assume that all agents own non-disconnected 
 * subproblems, otherwise we take the risk of having a component of the global constraint graph without a leader... 
 */
public class LeaderElectionMaxID < T extends Comparable <T> & Serializable > 
implements IncomingMsgPolicyInterface <String> {
	
	/** This agent's ID */
	protected T myID;
	
	/** Current computed maximum ID */
	protected T maxID;
	
	/** The last MaxIDmsg sent */
	private MaxIDmsg<T> lastSentMsg;
	
	/** The number of neighbors of the agent */
	private int nbrNeighbors;
	
	/** IDs of the agents from which we have received messages since the beginning of the current step */
	private HashSet<String> thisStepSenders;
	
	/** Messages belonging to the next step that have already been received 
	 * 
	 * Maps a sender ID to the maxID value it sent. 
	 */
	private HashMap<String, T> nextStepIDs = new HashMap<String, T> ();
	
	/** Index of the current step in the protocol, decremented until it reaches 0 */
	protected int stepCountdown = 0;
	
	/** The queue on which it should call sendMessage() */
	private Queue queue;
	
	/** Communication ID
	 * 
	 * This is used to identify the agent as the sender of messages. It can be different from \a myID. 
	 */
	private String comID;

	/** The neighbors of this agent */
	private Collection<String> neighbors;
	
	/** Whether this agent has already received the message that tells it to start the protocol */
	private boolean started = false;
	
	/** The type of the message used to tell the protocol to start */
	public static final String START_MSG_TYPE = "LEstart";

	/** The type of the messages used to carry agent IDs */
	public static final String LE_MSG_TYPE = "ELECT";

	/** The type of the output message */
	public static final String OUTPUT_MSG_TYPE = "LEoutput";
	
	/** Message class used for the output of the protocol 
	 * @param <T> the type used to identify the leader
	 */
	public static class MessageLEoutput < T extends Comparable <T> & Serializable > 
	extends MessageWith3Payloads <String, Boolean, T> {
		
		/** Empty constructor used for externalization */
		public MessageLEoutput () { }

		/** Constructor 
		 * @param sender 	the sender of the message
		 * @param isLeader 	whether the agent is the leader
		 * @param leader 	the leader
		 */
		public MessageLEoutput (String sender, boolean isLeader, T leader) {
			super (OUTPUT_MSG_TYPE, sender, isLeader, leader);
		}
		
		/** Constructor 
		 * @param type 		the type of the message
		 * @param sender 	the sender of the message
		 * @param isLeader 	whether the agent is the leader
		 * @param leader 	the leader
		 */
		protected MessageLEoutput (String type, String sender, boolean isLeader, T leader) {
			super (type, sender, isLeader, leader);
		}
		
		/** @return the sender of this message */
		public String getSender () {
			return getPayload1();
		}
		
		/** @return whether the agent is the leader */
		public Boolean getFlag () {
			return getPayload2();
		}
		
		/** @return the leader */
		public T getLeader () {
			return super.getPayload3();
		}

		/** @see frodo2.communication.Message#toString() */
		public String toString () {
			return "Message " + super.type + "\n\tsender: " + getSender() + "\n\tflag: " + getFlag() + "\n\tleader: " + this.getLeader();
		}
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection <String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList <String> (2);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(LE_MSG_TYPE);
		return msgTypes;
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
	
	/** Constructor 
	 * @param comID communication ID used to identify this agent as the sender of messages
	 * @param myID ID that uniquely identifies this agent
	 * @param neighbors the neighbors of this agent
	 * @param nbrSteps Number of steps the protocol should run before it terminates
	 * 
	 * @note \a nbrSteps must be an upper bound on the total number of agents for the algorithm to work properly. 
	 */
	public LeaderElectionMaxID (String comID, T myID, Collection <String> neighbors, int nbrSteps) {
		this.comID = comID;
		this.myID = myID;
		this.maxID = myID;
		this.neighbors = neighbors;
		this.nbrNeighbors = neighbors.size();
		this.stepCountdown = nbrSteps;
		thisStepSenders = new HashSet<String> (nbrNeighbors);
	}

	/** The actual algorithm
	 * 
	 * The algorithm is triggered by the reception of a message of type either this.startMsgType or this.LEmsgType. 
	 * At each step, the protocol computes the max of its ID and of the IDs in all received messages, and forwards it. 
	 * After a specified number of steps, the algorithm terminates, 
	 * and returns a message of type this.outputMsgType if its ID matches the last computed max ID (it is then the leader). 
	 * @param msg the message that was just received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
//		System.out.println("" + comID + " got " + msg);
		
		if (msgType.equals(START_MSG_TYPE)) { // This is the message that initiates the protocol
			
			started = true;
			
			if (nbrNeighbors > 0) {
				// Send ID to all neighbors
				maxID = this.getMaxID();
				this.lastSentMsg = new MaxIDmsg<T> (comID, maxID);
				queue.sendMessageToMulti(neighbors, lastSentMsg);
				
//				System.out.println("" + comID + " sent " + maxID);
			}
			
			else { // I'm alone in my connected component
				
				// Send the message indicating that I am the leader
				queue.sendMessageToSelf( new MessageLEoutput<T> (comID, true, this.maxID) );
				this.thisStepSenders = null;
				this.nextStepIDs = null;
				this.neighbors = null;
				return;
			}
		}
		
		else if (msgType.equals(LE_MSG_TYPE)) { // This message contains a value for maxID
			
			// Extract the message's payload
			MaxIDmsg<T> msg2 = (MaxIDmsg<T>) msg;
			String sender = msg2.getSender();
			T newMax = msg2.getMaxID();
			
			// Does this message belong to this step or to the next one?
			if (! thisStepSenders.add(sender)) { // I have already received a message from this sender
				
				// Store it to be processed in the next step, and return
				nextStepIDs.put(sender, newMax);
				return;
			}
			
			// Update the currently known max ID
			if (newMax.compareTo(maxID) > 0) 
				maxID = newMax;
		}
		
		if (started && thisStepSenders.size() >= nbrNeighbors) { // I got a message from all neighbors for this step
			
			if (--stepCountdown > 0) { // I must go to the next step
				
				// Send current maxID to all neighbors
				maxID = this.getMaxID();
				if (! this.lastSentMsg.getMaxID().equals(maxID)) // the maxID has changed
					this.lastSentMsg = new MaxIDmsg<T> (comID, maxID);
				queue.sendMessageToMulti(neighbors, lastSentMsg);
				
//				System.out.println("" + comID + " sent " + maxID);
				
				// Process the messages belonging to the next step that have already been received

				// Remember I received messages from these senders already
				thisStepSenders.clear();
				thisStepSenders.addAll(nextStepIDs.keySet());
				
				// Update the currently known max ID
				for (T newMax2 : nextStepIDs.values()) 
					if (newMax2.compareTo(maxID) > 0) 
						maxID = newMax2;
				
				// Reset the list of next step messages
				nextStepIDs.clear();
			}
			
			else { // I must terminate
				
				if (myID.compareTo(maxID) == 0) { // I am the leader
					// Send the message indicating that I am the leader
					queue.sendMessageToSelf( new MessageLEoutput<T> (comID, true, this.maxID) );
				}
				
				else { // I am not the leader
					// Send the message indicating that I am NOT the leader
					queue.sendMessageToSelf( new MessageLEoutput<T> (comID, false, this.maxID) );
				}

				this.thisStepSenders = null;
				this.nextStepIDs = null;
				this.neighbors = null;
			}
		}

	}
	
	/** @return the current maxID */
	protected T getMaxID () {
		return this.maxID;
	}
	
}
