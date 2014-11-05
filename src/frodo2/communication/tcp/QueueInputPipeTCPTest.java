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

package frodo2.communication.tcp;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueTest.ConstantMsgPolicy;
import frodo2.communication.QueueTest.QueueOutputPipeTrivial;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** A JUnit test class for QueueInputPipeTCP
 * @author Thomas Leaute
 */
public class QueueInputPipeTCPTest extends TestCase {
	
	/** Port number used */
	public static int port = 4444;
	
	/** IP address of the queue */
	public static String address = "localhost";
	
	/** The queue to which messages are passed */
	private Queue queue;
	
	/** The output pipe used for the tests */
	private QueueOutputPipeTrivial output;
	
	/** A client that connects to the queue and to send serialized messages through TCP */
	public static class Sender extends Thread {
		
		/** Messages to be sent to the queue */
		private final LinkedList <Message> messages = new LinkedList <Message> ();
		
		/** Constructor
		 * @param message message to be sent to the queue
		 */
		public Sender(Message message) {
			super ("QueueInputPipeTCPTest.Sender");
			this.messages.add(message);
			start();
		}

		/** Constructor
		 * @param messages messages to be sent to the queue
		 */
		public Sender(List<Message> messages) {
			super ("QueueInputPipeTCPTest.Sender");
			this.messages.addAll(messages);
			start();
		}

		/** @see java.lang.Thread#start() */
		@Override
		public void start () {
			this.setDaemon(true);
			super.start();
		}

		/** Connects to the pipe and sends messages to it */
		public void run () {

			try {
				Socket socket = new Socket (QueueInputPipeTCPTest.address, QueueInputPipeTCPTest.port);
				ObjectOutputStream out = new ObjectOutputStream (socket.getOutputStream());

				for (Message message: messages) {
					out.writeObject(message.getClass());
					message.writeExternal(out);
				}

				out.close();
				socket.close();
			} catch (Exception e) {
				e.printStackTrace();
				return; /// @bug socket and out will not be closed
			}
		}

	}
	
	/** Constructor that instantiates a test corresponding to the input test method */
	public QueueInputPipeTCPTest() {
		super ("testReceiveMultiple");
	}

	/** @return the suite of tests to be performed */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for QueueInputPipeTCPTest");
		
		TestSuite queueSuite = new TestSuite ("Test for Queue");
		queueSuite.addTest(new RepeatedTest (new QueueInputPipeTCPTest(), 1000));
		testSuite.addTest(queueSuite);
		
		return testSuite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	public void setUp () throws Exception {
		
		this.queue = new Queue(false);
		output = new QueueOutputPipeTrivial();
		queue.addOutputPipe("tester", output);
		queue.addIncomingMessagePolicy(new ConstantMsgPolicy("tester", false));
		new QueueInputPipeTCP(queue, port, 2);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	public void tearDown () {
		queue.end();
		queue = null;
		output = null;
	}
	
	/** Several senders connect to the pipe, and each sends several messages */
	public void testReceiveMultiple () {
		
		// Create messages and send them
		String type = "testReceiveMultiple";
		ArrayList<Message> messages1 = new ArrayList<Message> (3);
		messages1.add(new Message (type));
		messages1.add(new Message (type));
		messages1.add(new Message (type));
		ArrayList<Message> messages2 = new ArrayList<Message> (3);
		messages2.add(new Message (type));
		messages2.add(new Message (type));
		messages2.add(new Message (type));
		new Sender (messages1);
		new Sender (messages2);

		// Check that the messages were all received
		assertEquals(type, output.getNextMsgTimed().getMessage().getType());
		assertEquals(type, output.getNextMsgTimed().getMessage().getType());
		assertEquals(type, output.getNextMsgTimed().getMessage().getType());
		assertEquals(type, output.getNextMsgTimed().getMessage().getType());
		assertEquals(type, output.getNextMsgTimed().getMessage().getType());
		assertEquals(type, output.getNextMsgTimed().getMessage().getType());
	}
	
}
