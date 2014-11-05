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

package frodo2.communication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import frodo2.communication.OutgoingMsgPolicyInterface.Decision;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** A JUnit class to test Queue
 * @author Thomas Leaute
 * @todo Test the counting of messages. 
 */

public class QueueTest extends TestCase {
	
	/** This pipe has a list of messages ready to be pulled using pullMessage()
	 * @author Thomas Leaute
	 */
	public static class QueueInputPipeTrivial extends Thread implements QueueInputPipeInterface {
		
		/** Messages waiting to be sent to the queue */
		private LinkedList <MessageWrapper> messages = new LinkedList <MessageWrapper> ();
		
		/** Whether this pipe's thread should keep going or stop */
		private boolean keepGoing = true;

		/** This pipe's queue */
		private Queue queue;
		
		/** Constructor 
		 * @param queue the queue messages should be passed to 
		 */
		public QueueInputPipeTrivial(Queue queue) {
			super ("QueueInputPipeTrivial");
			this.queue = queue;
			queue.toBeClosedUponKill(this);
			start();
		}
		
		/** @see java.lang.Thread#start() */
		@Override
		public void start () {
			this.setDaemon(true);
			super.start();
		}

		/** Constantly passes messages to the queue
		 * @see java.lang.Thread#run()
		 */
		public void run () {
			while (keepGoing) {
				synchronized (messages) {
					if (! messages.isEmpty()) 
						queue.addToInbox(this.messages.removeFirst());
				}
			}
		}

		/**
		 * @param msg the message to set
		 */
		public void addMessage (Message msg) {
			synchronized (messages) {
				messages.addLast(new MessageWrapper (msg));
			}
		}

		/** Adds a message wrapper 
		 * @param wrap 	message wrapper
		 */
		public void addMessage (MessageWrapper wrap) {
			synchronized (messages) {
				messages.addLast(wrap);
			}
		}

		/** Terminates this pipe's thread
		 * @see QueueInputPipeInterface#close()
		 */
		public void close() { 
			keepGoing = false;
		}

		/** @see frodo2.communication.QueueInputPipeInterface#toDOT() */
		public String toDOT() {
			return "QueueInputPipeTrivial" + this.hashCode();
		}
	}
	
	/** This pipe stores the messages passed to it using pushObject()
	 * @author Thomas Leaute
	 */
	public static class QueueOutputPipeTrivial implements QueueOutputPipeInterface {
		
		/** The messages received */
		private LinkedList <MessageWrapper> messages = new LinkedList <MessageWrapper> ();

		/**
		 * @see frodo2.communication.QueueOutputPipeInterface#pushMessage(frodo2.communication.MessageWrapper)
		 */
		public void pushMessage(MessageWrapper msgWrap) {
			synchronized (messages) {
				messages.add(msgWrap);
			}
		}

		/** Returns the next message received, or \c null if there is none
		 * @return the next message received
		 */
		public MessageWrapper getNextMsg() {
			synchronized (messages) {
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
		public MessageWrapper getNextMsgTimed() {
			MessageWrapper msg = null;
			for (int count = 0; msg == null; count++) {
				if (count > 20000000) {
					fail("Timeout");
					break;
				}
				msg = getNextMsg();
			}
			return msg;
		}

		/** Does nothing
		 * @see frodo2.communication.QueueOutputPipeInterface#close()
		 */
		public void close() { }

		/** @see frodo2.communication.QueueOutputPipeInterface#toDOT() */
		public String toDOT() {
			return "QueueOutputPipeTrivial" + this.hashCode();
		}
	}
	
	/** A message policy that always returns the same decision
	 * @author Thomas Leaute 
	 *
	 */
	public static class ConstantMsgPolicy implements IncomingMsgPolicyInterface <String>, OutgoingMsgPolicyInterface<String> {
		
		/** Where to forward messages; no forwarding if \c null */
		private String forward;
		
		/** Whether the policy should forward messages to the queue itself */
		private Boolean forwardToSelf;
		
		/** The output of notifyOut() */
		private Decision outDecision = Decision.DONTCARE;

		/** Queue from which to call the sendMessage() method when the message must be forwarded */
		private Queue queue;

		/** Lock for \a queue used for synchronization
		 * 
		 * We cannot synchronize directly on \a queue because it can be \c null. 
		 */
		private Object queue_lock = new Object();
		
		/** The list of messages types this listener wants to be notified of */
		private ArrayList <String> msgTypes = new ArrayList <String> ();

		/**
		 * @see IncomingMsgPolicyInterface#notifyIn(Message)
		 */
		public void notifyIn(Message msg) {
			
			if (forward != null) 
				queue.sendMessage(forward, msg);

			if (forwardToSelf) 
				queue.sendMessageToSelf(msg);
		}

		/** 
		 * @see OutgoingMsgPolicyInterface#notifyOut(Message) 
		 * @return always DISCARD. 
		 */
		public Decision notifyOut(Message msg) {
			return this.outDecision;
		}
		
		/** Constructor
		 * 
		 * By default, it listens to messages of type Queue.ALLMESSAGES. 
		 * @param forward 			Where to forward messages; no forwarding if \c null
		 * @param forwardToSelf 	Whether the policy should forward messages to the queue itself
		 */
		public ConstantMsgPolicy(String forward, boolean forwardToSelf) {
			this.forward = forward;
			this.forwardToSelf = forwardToSelf;
			msgTypes.add(Queue.ALLMESSAGES);
		}

		/** Constructor
		 * @param forward 			Where to forward messages; no forwarding if \c null
		 * @param forwardToSelf 	Whether the policy should forward messages to the queue itself
		 * @param msgType 			the type of messages this listens to
		 */
		public ConstantMsgPolicy(String forward, boolean forwardToSelf, String msgType) {
			this.forward = forward;
			this.forwardToSelf = forwardToSelf;
			msgTypes.add(msgType);
		}

		/**
		 * @param outDecision the desired output of notifyOut()
		 */
		public void setOutDecision(Decision outDecision) {
			synchronized (this.outDecision) {
				this.outDecision = outDecision;
			}
		}
		
		/**
		 * @param forward 	Where to forward messages; no forwarding if \c null
		 */
		public void setForward (String forward) {
			synchronized (this.forward) {
				this.forward = forward;
			}
		}

		/**
		 * @param forwardToSelf Whether the policy should forward messages to the queue itself or not
		 */
		public void setForwardToSelfFlag (boolean forwardToSelf) {
			synchronized (this.forwardToSelf) {
				this.forwardToSelf = forwardToSelf;
			}
		}

		/**
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) {
			synchronized (queue_lock) {
				this.queue = queue;
			}
		}

		/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
		public Collection<String> getMsgTypes() {
			return msgTypes;
		}
		
	}
	
	/** The output pipe used for the tests */
	protected QueueOutputPipeTrivial output;
	
	/** The input pipe used for the tests */
	protected QueueInputPipeTrivial input;

	/** The queue under test */
	protected Queue queue;
	
	/** The incoming object policy used for the tests */
	protected ConstantMsgPolicy decider;

	/** Generates a test using only the input test method
	 * @param method the test method
	 */
	public QueueTest(String method) {
		super (method);
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for Queue");
		
		testSuite.addTest(new QueueTest ("testForward"));
		testSuite.addTest(new QueueTest ("testDiscard"));
		testSuite.addTest(new QueueTest ("testForwardByType"));
		
		testSuite.addTest(new QueueTest ("testForwardSerialized"));

		TestSuite testTmp = new TestSuite ("Tests for forwarding several messages in sequence");
		testTmp.addTest(new RepeatedTest (new QueueTest ("testMultipleForward"), 5000));
		testSuite.addTest(testTmp);
		
		testSuite.addTest(new QueueTest ("testMultipleInputPipes"));
		testSuite.addTest(new QueueTest ("testSendToSelf"));
		testSuite.addTest(new QueueTest ("testSendToMultiple"));

		testTmp = new TestSuite ("Tests that sendMessage respects the order");
		testTmp.addTest(new RepeatedTest (new QueueTest ("testSendMessageOrder"), 10));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests that sendMessage does not ignore duplicate messages");
		testTmp.addTest(new RepeatedTest (new QueueTest ("testSendMessageMultiple"), 10));
		testSuite.addTest(testTmp);
		
		testSuite.addTest(new QueueTest ("testOutgoingListener"));
		
		return testSuite;
	}

	/**
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp () {
		queue = new Queue(true);	
		output = new QueueOutputPipeTrivial();
		queue.addOutputPipe("tester", output);
		input = new QueueInputPipeTrivial(queue);
		
		decider = new ConstantMsgPolicy("tester", false);
		queue.addIncomingMessagePolicy(decider);
		queue.addOutgoingMessagePolicy(decider);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	public void tearDown () {
		queue.end();
		queue = null;
		decider = null;
		output = null;
		input = null;
	}
	
	/** Tests the forwarding of a message */
	public void testForward() {
		
		Message msg = new Message("forward");
		input.addMessage(msg);
		
		// Test that it was forwarded
		assertEquals(msg, output.getNextMsgTimed().getMessage());
	}

	/** Tests the discarding of a message
	 * @throws InterruptedException Necessary because it uses Thread.sleep()
	 */
	public void testDiscard() throws InterruptedException {
		
		Message msg = new Message("don't care");
		this.decider.setForward(null);
		input.addMessage(msg);
		
		// Test that it was not forwarded
		Thread.sleep(500); // wait in case the queue has not yet forwarded the message
		assertNull(output.getNextMsg());
	}

	/** Tests the forwarding of a message of a specific type 
	 * @throws InterruptedException Necessary because it uses Thread.sleep()
	 */
	public void testForwardByType() throws InterruptedException {
		
		Message msg = new Message("forward and store by type");
		this.decider.setForward(null);
		ConstantMsgPolicy decider2 = new ConstantMsgPolicy("tester", false, msg.getType());
		queue.addIncomingMessagePolicy(decider2);
		input.addMessage(msg);
		
		// Test that it was forwarded
		assertEquals(msg, output.getNextMsgTimed().getMessage());
		
		// Test that no other message type is forwarded or stored
		msg = new Message ("other");
		input.addMessage(msg);
		
		// Test that it was not forwarded
		Thread.sleep(500); // wait in case the queue has not yet forwarded the message
		assertNull(output.getNextMsg());		
	}
	
	/** Tests the forwarding of a serialized message */
	public void testForwardSerialized() {
		
		MessageSerializedSimple <String> msg = new MessageSerializedSimple <String> ("forward serialized", "forward serialized");
		decider.setForward("tester");
		input.addMessage(msg);
		
		// Test that it was forwarded
		assertEquals(msg, output.getNextMsgTimed().getMessage());
	}

	/** Tests the forwarding of multiple messages */
	public void testMultipleForward() {
		
		Message msg1 = new Message(String.valueOf(Math.random()));
		input.addMessage(msg1);
		Message msg2 = new Message(String.valueOf(Math.random()));
		input.addMessage(msg2);
		Message msg3 = new Message(String.valueOf(Math.random()));
		input.addMessage(msg3);
		
		// Test that messages were forwarded, and in the proper order
		assertEquals(msg1, output.getNextMsgTimed().getMessage());
		assertEquals(msg2, output.getNextMsgTimed().getMessage());
		assertEquals(msg3, output.getNextMsgTimed().getMessage());
	}
	
	/** Tests a queue with two input pipes */
	public void testMultipleInputPipes () {
		
		queue.end();

		// Create the queue
		queue = new Queue (false);
		queue.addOutputPipe("tester", output);
		queue.addIncomingMessagePolicy(decider);
		
		// Create the input pipes
		input = new QueueInputPipeTrivial(queue);
		QueueInputPipeTrivial input2 = new QueueInputPipeTrivial (queue);
		
		// Create the messages
		Message msg = new Message ("testMultipleInputPipes");
		input.addMessage(msg);
		input.addMessage(msg);
		input2.addMessage(msg);
		input2.addMessage(msg);
	
		// Check that the messages were all forwarded
		for (int i = 0; i < 4; i++) 
			assertEquals(msg, output.getNextMsgTimed().getMessage());
	}
	
	/** Tests the sending of a message to itself. */
	public void testSendToSelf () {
		Message msg = new Message("forwardToSelf");
		decider.setForwardToSelfFlag(true);
		input.addMessage(msg);
		
		// Test that it was forwarded several times (the queue keeps forwarding the message to itself and to the tester forever)
		assertEquals(msg, output.getNextMsgTimed().getMessage());
		assertEquals(msg, output.getNextMsgTimed().getMessage());
	}
	
	/** Tests the sending of a message to all known recipients (except the queue itself) */
	public void testSendToMultiple () {
		
		// Add an output pipe to the queue
		String recipient2 = "output2";
		QueueOutputPipeTrivial output2 = new QueueOutputPipeTrivial ();
		queue.addOutputPipe(recipient2, output2);
		ArrayList <String> recipients = new ArrayList <String> (2);
		recipients.add("tester");
		recipients.add(recipient2);
		
		// Directly tell the queue to send the message
		Message msg = new Message("sendToMultiple");
		queue.sendMessageToMulti(recipients, msg);
		
		// Test that the message was received by all output pipes
		assertEquals(msg, output.getNextMsgTimed().getMessage());
		assertEquals(msg, output2.getNextMsgTimed().getMessage());
	}
	
	/** Tests that sequential calls to sendMessage() result in the message being sent in the correct order */
	public void testSendMessageOrder () {
		
		Message msg1 = new Message (String.valueOf(Math.random()));
		Message msg2 = new Message (String.valueOf(Math.random()));
		
		queue.sendMessageToSelf(msg1);
		queue.sendMessageToSelf(msg2);
		
		assertEquals(msg1, output.getNextMsgTimed().getMessage());
		assertEquals(msg2, output.getNextMsgTimed().getMessage());
	}
	
	/** Tests that sequential calls to sendMessage() on the same Message object actually send the message multiple times, not just once. */
	public void testSendMessageMultiple () {
		
		Message msg = new Message ("testSendMessageOrder");
		
		queue.sendMessageToSelf(msg);
		queue.sendMessageToSelf(msg);
		
		assertEquals(msg, output.getNextMsgTimed().getMessage());
		assertEquals(msg, output.getNextMsgTimed().getMessage());
	}
	
	/** Tests that outgoing message listeners get notified
	 * @throws InterruptedException Necessary because it uses Thread.sleep()
	 */
	public void testOutgoingListener() throws InterruptedException {
		
		Message msg = new Message("hello queue");
		decider.setForwardToSelfFlag(true);
		decider.setOutDecision(Decision.DISCARD);
		input.addMessage(msg);
		
		// Test that it was not forwarded
		Thread.sleep(500); // wait in case the queue has not yet forwarded the message
		assertNull(output.getNextMsg());
	}

}
