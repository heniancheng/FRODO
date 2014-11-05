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

/** Interface for classes used as an output of a Queue object
 * @author Thomas Leaute
 *
 * This interface has a pushMessage() method that a Queue can call to relay messages. 
 */
public interface QueueOutputPipeInterface {
	
	/** Pushes a message into the pipe
	 * @param msg the message to be pushed into the pipe
	 * @warning If your implementation of this method needs to read from the message's raw data stream, it \b MUST spawn a new thread
	 * and do it from within the thread. If this method attempts to read directly from the thread, this could cause a race condition. 
	 */
	public void pushMessage (MessageWrapper msg);

	/** Closes the pipe (if relevant) */
	public void close ();

	/** @return a unique ID for the pipe used by Queue#networkToDOT */
	public String toDOT ();
}
