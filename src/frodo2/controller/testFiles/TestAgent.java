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

package frodo2.controller.testFiles;

import org.jdom2.Document;

import frodo2.algorithms.SingleQueueAgent;
import frodo2.communication.Message;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.mailer.CentralMailer;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.ProblemInterface;

/** 
 * Does the following: 
 * - connects to its neighbours
 * - sends a test message to its neighbours when prompted by the start method
 * - sends a "finished" message to the controller when all its neighbours have responded to the test message
 * @author Brammert Ottens
 * @author Thomas Leaute
 * @param <V> type used for domain elements
 */
public class TestAgent <V extends Addable<V>> extends SingleQueueAgent<V> {

	/** A test message the agent send to a neighbour to test the connection*/
	public static final String TEST_MESSAGE = "Can you hear me?";
	
	/** The response to TEST_MESSAGE*/
	public static final String RESPONSE = "I received your message!";
	
	/** The number of neighbours that have responded */
	private int neighboursResponded;
	
	/** Constructor
	 * @param probDesc 			the description of the problem
	 * @param agentDesc 		JDOM document containing the description of the problem
	 * @param mailman			pointer to the central mailer (not used)
	 * @throws Exception 	if an error occurs
	 */
	public TestAgent(ProblemInterface<V, ?> probDesc, Document agentDesc, CentralMailer mailman) throws Exception {
		super (probDesc, agentDesc, null);
		queue.addIncomingMessagePolicy(RESPONSE, this);
		queue.addIncomingMessagePolicy(TEST_MESSAGE, this);
	}
	
	/** @see frodo2.algorithms.SingleQueueAgent#start() */
	public void start() {
		if(neighbours.isEmpty()) { // if the agent has no neighbours, it immediately finishes
			finished();
		} else {
			queue.sendMessageToMulti(neighbours, new MessageWithPayload<String>(TEST_MESSAGE, agentID));
		}
	}
	
	/** @see SingleQueueAgent#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		super.notifyIn(msg);
		
		if(msg.getType().equals(RESPONSE)) { // a neighbour responded
			neighboursResponded++;
			
			if(neighboursResponded == neighbours.size()) { // We are finished
				finished();
			}
		}
		
		if(msg.getType().equals(TEST_MESSAGE)) { // a neighbour tries to contact me
			Message newMsg = new Message(RESPONSE);
			queue.sendMessage(((MessageWithPayload<String>)msg).getPayload(), newMsg);
		}
	}

}
