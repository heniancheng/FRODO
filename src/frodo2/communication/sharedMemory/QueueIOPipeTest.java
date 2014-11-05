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

package frodo2.communication.sharedMemory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageSerializedSimple;
import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.communication.QueueTest;
import frodo2.communication.QueueTest.ConstantMsgPolicy;
import frodo2.communication.QueueTest.QueueInputPipeTrivial;
import frodo2.communication.mailer.CentralMailer;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit class to test QueueIOPipe
 * @author Thomas Leaute
 */
public class QueueIOPipeTest extends TestCase {
	
	/** A listener that collects received messages */
	private static class Collector implements IncomingMsgPolicyInterface<String> {
		
		/** The messages received */
		private LinkedList<Message> messages = new LinkedList<Message> ();

		/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
		public void notifyIn(Message msg) {
			synchronized (this.messages) {
				this.messages.add(msg);
			}
		}

		/** @see IncomingMsgPolicyInterface#getMsgTypes() */
		public Collection<String> getMsgTypes() {
			ArrayList<String> msgTypes = new ArrayList<String> (1);
			msgTypes.add(Queue.ALLMESSAGES);
			return msgTypes;
		}

		/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
		public void setQueue(Queue queue) { }
		
		/** Returns the next message received, or \c null if there is none
		 * @return the next message received
		 */
		private Message getNextMsg() {
			synchronized (this.messages) {
				if (messages.isEmpty()) {
					return null;
				} else {
					return messages.removeFirst();
				}
			}
		}

		/** Waits until there is a message in the list and returns it, or times out. 
		 * @return the next message received 
		 */
		private Message getNextMsgTimed() {
			Message msg = null;
			for (int count = 0; msg == null; count++) {
				if (count > 20000000) {
					fail("Timeout");
					break;
				}
				msg = getNextMsg();
			}
			return msg;
		}
	}
	
	/** The QueueIOPipe under test */
	private QueueIOPipe pipe;
	
	/** Used to send a message to the first queue */
	private QueueInputPipeTrivial toQueue1;
	
	/** The first queue */
	private Queue queue1;
	
	/** The second queue */
	private Queue queue2;
	
	/** Listener collecting messages received by queue2 */
	private Collector collector;
	
	/** Used as a switch between the normal and the simulated queue */
	private boolean measureTime;

	/** The CentralMailer */
	private CentralMailer mailman;
	
	/** Constructor
	 * @param name 				name of the test method
	 * @param measureTime 		\c true when the queue measures time spent
	 */
	public QueueIOPipeTest(String name, boolean measureTime) {
		super(name);
		this.measureTime = measureTime;
	}
	
	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite masterSuite = new TestSuite ("QueueIOPipeTest");
		
		TestSuite testSuite = new TestSuite ("Tests for QueueIOPipe");
		
		TestSuite testTmp = new TestSuite ("Tests for forwarding one message");
		testTmp.addTest(new RepeatedTest (new QueueIOPipeTest ("test2QueuesInSeries", false), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for forwarding one raw message");
		testTmp.addTest(new RepeatedTest (new QueueIOPipeTest ("test2QueuesInSeriesSerialized", false), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for forwarding several messages in sequence");
		testTmp.addTest(new RepeatedTest (new QueueIOPipeTest ("test2QueuesInSeriesMultiple", false), 100));
		testSuite.addTest(testTmp);
		
		masterSuite.addTest(testSuite);
		
		testSuite = new TestSuite ("Tests for QueueIOPipe with simulated time");
		
		testTmp = new TestSuite ("Tests for forwarding one message");
		testTmp.addTest(new RepeatedTest (new QueueIOPipeTest ("test2QueuesInSeries", true), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for forwarding one raw message");
		testTmp.addTest(new RepeatedTest (new QueueIOPipeTest ("test2QueuesInSeriesSerialized", true), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for forwarding several messages in sequence");
		testTmp.addTest(new RepeatedTest (new QueueIOPipeTest ("test2QueuesInSeriesMultiple", true), 100));
		testSuite.addTest(testTmp);
		
		masterSuite.addTest(testSuite);
		
		return masterSuite;
	}
	
	/**
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp () throws Exception {
		
		if (this.measureTime) 
			mailman = new CentralMailer (false, false, null);

		// Build the second queue
		if (this.measureTime) 
			this.queue2 = mailman.newQueue("queue2");
		else 
			this.queue2 = new Queue(false);
		this.collector = new Collector ();
		queue2.addIncomingMessagePolicy(this.collector);
		pipe = new QueueIOPipe (queue2);
		
		// Build the first queue
		if (this.measureTime) 
			this.queue1 = mailman.newQueue("queue1");
		else 
			this.queue1 = new Queue(false);
		queue1.addIncomingMessagePolicy(new ConstantMsgPolicy("queue2", false));
		queue1.addOutputPipe("queue2", pipe);
		toQueue1 = new QueueTest.QueueInputPipeTrivial (queue1);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	public void tearDown () {
		mailman = null;
		queue1.end();
		queue2.end();
		this.collector = null;
	}
	
	/** This test connects two queues in series using a QueueIOPipe 
	 * @throws InterruptedException 	if interrupted while sleeping
	 */
	public void test2QueuesInSeries () throws InterruptedException {
		
		// Send a message to the first queue
		Message msg = new Message ("2 queues in series");
		MessageWrapper wrap = new MessageWrapper (msg);
		wrap.setDestination("queue1");
		toQueue1.addMessage(wrap);
		
		if (this.measureTime) {
			// Give some time to the sender before starting the CentralMailer
			Thread.sleep(100);
			mailman.execute();
		}
		
		// Get the message from the second queue and check for equality
		assertEquals(msg, this.collector.getNextMsgTimed());

		if(this.measureTime)
			mailman.end();
	}

	/** This test connects two queues in series using a QueueIOPipe and sends a serialized message 
	 * @throws InterruptedException 	if interrupted while sleeping
	 */
	@SuppressWarnings("unchecked")
	public void test2QueuesInSeriesSerialized () throws InterruptedException {
		
		// Send a message to the first queue
		String data = "raw data for 2 queues in series serialized";
		MessageSerializedSimple <String> msg = new MessageSerializedSimple <String> ("2 queues in series serialized", data);
		MessageWrapper wrap = new MessageWrapper (msg);
		wrap.setDestination("queue1");
		toQueue1.addMessage(wrap);
		
		if (this.measureTime) {
			// Give some time to the sender before starting the CentralMailer
			Thread.sleep(100);
			mailman.execute();
		}
		
		// Get the message from the second queue and check for equality
		MessageSerializedSimple <String> msg2 = (MessageSerializedSimple <String>) this.collector.getNextMsgTimed();
		assertEquals(msg.getType(), msg2.getType());
		msg2.deserializeRawData();
		assertEquals(data, msg2.getData());
		
		if (this.measureTime) 
			mailman.end();
	}

	/** This test connects two queues in series using a QueueIOPipe and sends multiple messages 
	 * @throws InterruptedException 	if interrupted while sleeping
	 */
	public void test2QueuesInSeriesMultiple () throws InterruptedException {
		
		// Send messages to the first queue
		Message msg1 = new Message ("2 queues in series - message 1");
		MessageWrapper wrap = new MessageWrapper (msg1);
		wrap.setDestination("queue1");
		wrap.setTime(1);
		toQueue1.addMessage(wrap);
		
		Message msg2 = new Message ("2 queues in series - message 2");
		wrap = new MessageWrapper (msg2);
		wrap.setDestination("queue1");
		wrap.setTime(2);
		toQueue1.addMessage(wrap);
		
		Message msg3 = new Message ("2 queues in series - message 3");
		wrap = new MessageWrapper (msg3);
		wrap.setDestination("queue1");
		wrap.setTime(3);
		toQueue1.addMessage(wrap);
		
		if (this.measureTime) {
			// Give some time to the sender before starting the CentralMailer
			Thread.sleep(100);
			mailman.execute();
		}
		
		// Get the messages from the second queue and check for equality and order
		assertEquals(msg1, this.collector.getNextMsgTimed());
		assertEquals(msg2, this.collector.getNextMsgTimed());
		assertEquals(msg3, this.collector.getNextMsgTimed());
		
		if (this.measureTime) 
			mailman.end();
	}

}
