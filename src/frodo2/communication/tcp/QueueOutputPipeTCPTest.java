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

import java.io.IOException;

import frodo2.communication.Message;
import frodo2.communication.MessageSerializedSimple;
import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.communication.QueueTest.ConstantMsgPolicy;
import frodo2.communication.QueueTest.QueueOutputPipeTrivial;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** A JUnit test class for QueueOutputPipeTCP, that uses QueueInputPipeTCP
 * @author Thomas Leaute
 */
public class QueueOutputPipeTCPTest extends TestCase {
	
	/** The QueueOutputPipeTCP under test */
	private QueueOutputPipeTCP out;
	
	/** The queue to which messages are passed */
	private Queue queue;
	
	/** The output pipe used for the tests */
	private QueueOutputPipeTrivial output;
	
	/** Generates a test case for the given method name
	 * @param string 			name of the method
	 */
	public QueueOutputPipeTCPTest(String string) {
		super (string);
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Tests for QueueOutputPipeTCP");
		
		TestSuite queueSuite = new TestSuite("Tests for Queue");
		
		TestSuite tmp = new TestSuite ("Tests using messages without raw data");
		tmp.addTest(new RepeatedTest (new QueueOutputPipeTCPTest ("testNonSerialized"), 1000));
		queueSuite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using messages with raw data");
		tmp.addTest(new RepeatedTest (new QueueOutputPipeTCPTest ("testSerialized"), 1000));
		queueSuite.addTest(tmp);
		
		suite.addTest(queueSuite);
		
		return suite;
	}

	/**
	 * @throws IOException thrown if an I/O error occurs
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp () throws Exception {
		
		this.queue = new Queue(false);
		output = new QueueOutputPipeTrivial();
		queue.addOutputPipe("tester", output);
		queue.addIncomingMessagePolicy(new ConstantMsgPolicy("tester", false));
		new QueueInputPipeTCP (queue, QueueInputPipeTCPTest.port, 1);
		out = new QueueOutputPipeTCP (QueueInputPipeTCPTest.address, QueueInputPipeTCPTest.port);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	public void tearDown () {
		out.close();
		queue.end();
		queue = null;
		output = null;
	}
	
	/** Pushes multiple non-serialized messages */
	public void testNonSerialized () {
		
		Message msg1 = new Message ("testNonSerialized1");
		Message msg2 = new Message ("testNonSerialized2");
		Message msg3 = new Message ("testNonSerialized3");
		
		out.pushMessage(new MessageWrapper(msg1));
		out.pushMessage(new MessageWrapper(msg2));
		out.pushMessage(new MessageWrapper(msg3));
		
		assertEquals(msg1.getType(), output.getNextMsgTimed().getMessage().getType());
		assertEquals(msg2.getType(), output.getNextMsgTimed().getMessage().getType());
		assertEquals(msg3.getType(), output.getNextMsgTimed().getMessage().getType());
	}

	/** Pushes multiple messages with associated raw data
	 * @throws IOException thrown if an I/O error occurs
	 */
	@SuppressWarnings("unchecked")
	public void testSerialized () throws IOException {
		
		// Create the serialized data
		String rawData1 = "rawData1";
		MessageSerializedSimple <String> msg1 = new MessageSerializedSimple <String> ("testSerialized1", rawData1);
		String rawData2 = "rawData2";		
		MessageSerializedSimple <String> msg2 = new MessageSerializedSimple <String> ("testSerialized2", rawData2);

		out.pushMessage(new MessageWrapper(msg1));
		out.pushMessage(new MessageWrapper(msg2));
		
		// Read the messages and compare with originals
		MessageSerializedSimple <String> msgReceived = (MessageSerializedSimple <String>) output.getNextMsgTimed().getMessage();
		assertEquals (msg1.getType(), msgReceived.getType());
		msgReceived.deserializeRawData();
		assertEquals (rawData1, msgReceived.getData());
		
		msgReceived = (MessageSerializedSimple <String>) output.getNextMsgTimed().getMessage();
		assertEquals (msg2.getType(), msgReceived.getType());
		msgReceived.deserializeRawData();
		assertEquals (rawData2, msgReceived.getData());

	}
	
}
