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

import java.io.IOException;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Unit tests for the MsgSizeMonitor
 * @author Thomas Leaute
 */
public class MsgSizeMonitorTest extends TestCase {
	
	/** @return a suite of unit tests */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("Tests for MsgSizeMonitor");
		suite.addTest(new RepeatedTest (new MsgSizeMonitorTest (), 10000));
		
		return suite;
	}
	
	/** Constructor */
	public MsgSizeMonitorTest () {
		super("test");
	}
	
	/** Creates a MsgSizeMonitor, and computes the sizes of a series of messages
	 * @throws IOException 	if an error occurs
	 */
	public void test () throws IOException {
		
		MsgSizeMonitor monitor = new MsgSizeMonitor ();
		
		Message msg = new Message ("Test");
		String dest = "dest";
		
		// The first time we send the message, its size must be positive
		assertTrue (monitor.getMsgSize(dest, msg) > 0);
		
		// The following times, the size must be 5 = 1 marker byte + an int reference
		assertEquals (5, monitor.getMsgSize(dest, msg));
		assertEquals (5, monitor.getMsgSize(dest, msg));
		assertEquals (5, monitor.getMsgSize(dest, msg));
		assertEquals (5, monitor.getMsgSize(dest, msg));
		
		monitor.close();
	}
	
}
