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

/** Tests for the package frodo2.communication.mailer */
package frodo2.communication.mailer.tests;

import java.util.ArrayList;
import java.util.Collection;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.communication.mailer.CentralMailer;
import frodo2.communication.sharedMemory.QueueIOPipe;

/** Test suite for the CentralMailer
 * @author Brammert Ottens, Thomas Leaute
 * 
 */
public class testCentralMailer extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** The type of a random message */
	public static final String RANDOM_MSG_TYPE  = "Random message";
	
	/** The type of a causal message */
	public static final String CAUSAL_MSG_TYPE = " Causal message";

	/** The last message received */
	private MessageWrapper last;
	
	/** Count the number of messages received */
	private int messagesReceived;
	
	/** The listener's queue */
	private Queue queue;

	/** The CentralMailer */
	private CentralMailer mailman;
	
	/** Constructor */
	public testCentralMailer() {
		super("testNext");
	}
	
	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for Central Mailer");
		
		testSuite.addTest(new RepeatedTest (new testCentralMailer (), 100000));
		
		return testSuite;
	}

	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		mailman = new CentralMailer (false, false, null);
		messagesReceived = 0;
	}

	/** 
	 * @see junit.framework.TestCase#tearDown()
	 */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		mailman.end();
		mailman = null;
		this.last = null;
	}
	
	/**
	 * Tests whether a set of messages received in random order
	 * are released in the correct order, using the Next message
	 * 
	 * @author Brammert Ottens, 15 jun 2009
	 */
	public void testNext() {
		
		String destination = "me";
		
		Queue queue2 = mailman.newQueue(destination);
		QueueIOPipe pipe2 = new QueueIOPipe(queue2);
		queue2.addIncomingMessagePolicy(this);
		
		
		// generate a set of random messages
		final int nbrMsgs = 25;
		for(int i = 0; i < nbrMsgs; i++) {
			MessageWrapper rand = new MessageWrapper(new Message(RANDOM_MSG_TYPE));
			rand.setTime((long)(Math.random() * 1000));
			rand.setDestination(destination);
			pipe2.pushMessage(rand);
		}
		
		// Start the CentralMailer
		assertTrue("Timeout after " + this.messagesReceived + " messages", this.mailman.execute(2000));
		assertEquals(nbrMsgs, this.messagesReceived);
	}
	
	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		ArrayList<String> msgTypes = new ArrayList<String>(1);
		msgTypes.add(RANDOM_MSG_TYPE);
		return msgTypes;
	}

	/** 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	public void notifyIn(Message msg) {
		MessageWrapper msgWrap = queue.getCurrentMessageWrapper();
		String type = msg.getType();
		
		if(type.equals(RANDOM_MSG_TYPE)) {
			
			assertTrue(msgWrap.getTime() >= 0);
			
			if(last != null)
				assertTrue(last.getTime() <= msgWrap.getTime());

			++messagesReceived;
			last = msgWrap;
		} 
	}

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
	
}
