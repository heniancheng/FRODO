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

/** Contains classes implementing the S-DPOP algorithm with warm restarts */
package frodo2.algorithms.dpop.restart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.dpop.UTILmsg;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** UTIL message storage for warm restarts of S-DPOP
 * @author Jonas Helfer, Thomas Leaute
 */
public class UTILreuse implements IncomingMsgPolicyInterface<String>, OutgoingMsgPolicyInterface<String> {
	
	/** The queue on which it should call sendMessage() */
	private Queue queue;
	
	/** The problem */
	private DCOPProblemInterface<?, ?> problem;
	
	/** For each known variable, the name of the agent that owns it */
	private Map<String, String> owners;
	
	/** This agent's name */
	private String myID;
	
	/**
	 * For storing incoming messages
	 */
	private HashMap< String, UTILmsg<?, ?> > StorageIn = new HashMap< String, UTILmsg<?, ?> >();
	
	/**
	 * For storing outgoing messages, key is the sender
	 */
	private HashMap<String,Message> StorageOut = new HashMap<String,Message>();

	/** Whether the algorithm has started */
	private boolean started = false;
	
	/**
	 * Type used for empty util reuse messages
	 */
	public static String REUSE_MSG_TYPE = "UTILreuse";

	/** Constructor from XML descriptions
	 * @param problem 					description of the problem
	 * @param parameters 				description of the parameters of UTILpropagation
	 */
	public UTILreuse (DCOPProblemInterface<?, ?> problem, Element parameters) {
		this.problem = problem;
	}
	
	/** Parses the problem */
	private void init () {
		this.owners = problem.getOwners();
		this.myID = problem.getAgent();
		this.started  = true;
	}
	
	/** Forgets information about the problem, but not too much :) */
	private void reset () {
		this.owners = null;
		this.started = false;
	}

	/**
	 * @see OutgoingMsgPolicyInterface#notifyOut(Message)
	 */
	public Decision notifyOut(Message msg) {
		
		if (! this.started) 
			this.init();
		
		if(msg.getType().equals(UTILpropagation.UTIL_MSG_TYPE)){
			
			UTILmsg<?, ?> UtilMsg = (UTILmsg<?, ?>)msg;
			String destination = UtilMsg.getDestination();
			String sender = UtilMsg.getSender();
			String owner = owners.get(destination);

			//don't need to store if message is only virtual...
			if(owner.equals(this.myID))
			{
				return Decision.DONTCARE;
			}
			
			Message prevMsg = this.StorageOut.get(sender);
			if(UtilMsg.equals(prevMsg)){
				//send empty message and discard original one
				Token emsg = new Token(sender);
				queue.sendMessage(owner, emsg);

				return Decision.DISCARD;
			}
			else{
				StorageOut.put(sender, msg);
				return Decision.DONTCARE;
			}
		}
		return Decision.DONTCARE;
	}

	/**
	 * @see IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection <String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (4);
		types.add(AgentInterface.START_AGENT);
		types.add(UTILpropagation.UTIL_MSG_TYPE);
		types.add(REUSE_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/**
	 * @see IncomingMsgPolicyInterface#setQueue(Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/**
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	public void notifyIn(Message msg) {
		
		if (! this.started) 
			this.init();
		
		String msgType = msg.getType();
		
		if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}

		if(msgType.equals(REUSE_MSG_TYPE)){

			Token EmptyMsg = (Token)msg;
			String sender = EmptyMsg.getSender();
			
			UTILmsg<?, ?> UTILMsg = StorageIn.get(sender);
			assert UTILMsg != null : "I have forgotten a message!";
			queue.sendMessageToSelf(UTILMsg);
		}
		
		else if(msgType.equals(UTILpropagation.UTIL_MSG_TYPE)){
			UTILmsg<?, ?> UtilMsg = (UTILmsg<?, ?>)msg;
			
			
			if(owners.get(UtilMsg.getSender()).equals(this.myID)){
				return; //this was only a virtual message, don't store
			}
			
			String sender = UtilMsg.getSender();
			StorageIn.put(sender, UtilMsg);
			
		}
		
		//restart the util phase by sending messages to all parents:
		else if(msgType.equals("RESTART_UTIL")){
			///@todo: implement the restart util message!
		}
	}
	
}

