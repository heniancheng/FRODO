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

/** Contains classes used for communication between agents running within the same JVM */
package frodo2.communication.sharedMemory;

import java.io.Serializable;

import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.communication.QueueOutputPipeInterface;

/** Input-output pipe between two queues
 * @author Thomas Leaute 
 */
public class QueueIOPipe implements QueueInputPipeInterface, QueueOutputPipeInterface, Serializable {
	
	/** Used for serialization */
	private static final long serialVersionUID = -4232985879625166953L;
	
	/** This input pipe's queue */
	private Queue queue;
	
	/** Whether this pipe is between an agent and itself */
	private final boolean virtual;
	
	/** Constructor. Notifies the queue. 
	 * @param queue the queue to which messages should be passed
	 */
	public QueueIOPipe (Queue queue) {
		this.queue = queue;
		this.virtual = false;
		queue.toBeClosedUponKill(this);
	}
	
	/** Constructor. Notifies the queue. 
	 * @param queue 	the queue to which messages should be passed
	 * @param virtual 	whether this pipe is between an agent and itself
	 */
	public QueueIOPipe (Queue queue, boolean virtual) {
		this.queue = queue;
		this.virtual = virtual;
		queue.toBeClosedUponKill(this);
	}
	
	/**
	 * Passes a message to this pipe's queue
	 * @see frodo2.communication.QueueOutputPipeInterface#pushMessage(frodo2.communication.MessageWrapper)
	 */
	public void pushMessage(MessageWrapper msg) {
		
		if (! this.virtual) 
			msg.getMessage().fakeSerialize();
		
		queue.addToInbox(msg);
	}

	/** Does nothing
	 * @see frodo2.communication.QueueInputPipeInterface#close()
	 */
	public void close() { }

	/** @see frodo2.communication.QueueInputPipeInterface#toDOT() */
	public String toDOT() {
		return "QueueIOPipe" + this.hashCode();
	}

}
