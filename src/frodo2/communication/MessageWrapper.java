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

import java.util.Collection;
import java.util.HashSet;

/**
 * Message wrapper, used to store information on
 * - the nccc count
 * - the time stamp
 * - the set of destinations of the message
 * @author Brammert Ottens, 26 aug 2009
 * 
 */
public class MessageWrapper {

	/** The message being wrapped */
	private Message message;
	
	/** Used to count Non-Concurrent Constraint Checks; -1 if we do not need to count them */
	private long ncccs = -1;
	
	/** Used to store the time stamp of this message */
	private long timeStamp = Long.MIN_VALUE;
	
	/** Contains the IDs of the destinations of this message */
	private Collection<Object> destinations;
	
	/** Every queue has a message counter that counts the number of message being send, this field
	 * contains the value of that counter at the time the message was sent*/
	private int messageCounter = 0;
	
	/**
	 * Constructor
	 * @param message to message to be wrapped
	 */
	public MessageWrapper(Message message) {
		this.message = message;
	}
	
	/** Constructor
	 * @param message 			the message
	 * @param ncccs 			the NCCC counter
	 * @param timeStamp 		the timestamp
	 * @param destinations 		the destinations
	 * @param messageCounter 	the message counter
	 */
	public MessageWrapper(Message message, long ncccs, long timeStamp,
			Collection<Object> destinations, int messageCounter) {
		this.message = message;
		this.ncccs = ncccs;
		this.timeStamp = timeStamp;
		this.destinations = destinations;
		this.messageCounter = messageCounter;
	}

	/**
	 * @return the wrapped message 
	 */
	public Message getMessage() {
		return this.message;
	}
	
	/** @return the first destination of this message*/
	public Object getDestination() {
		Object dest = destinations.iterator().next();
		return dest;
	}
	
	/** @return the destinations of this message*/
	public Collection<Object> getDestinations() {
		return destinations;
	}

	/** @return the number of Non-Concurrent Constraint Checks associated with this message; -1 if we haven't been counting them */
	public long getNCCCs () {
		return this.ncccs;
	}
	
	/** @return the time stamp of this message (in nanoseconds) */
	public long getTime() {
		return this.timeStamp;
	}
	
	/** Sets the number of Non-Concurrent Constraint Checks associated with this message
	 * @param ncccs 	the number of NCCCs
	 */
	public void setNCCCs (long ncccs) {
		this.ncccs = ncccs;
	}
	
	/**
	 * Set the time stamp of this message
	 * @author Brammert Ottens, 11 jun 2009
	 * @param time	the time stamp (in nanoseconds)
	 */
	public void setTime(long time) {
		this.timeStamp = time;
	}
	
	/**
	 * Add a delay to this message
	 * @author Brammert Ottens, 1 sep 2009
	 * @param delay the delay to be added (in nanoseconds)
	 */
	public void addDelay(long delay) {
		this.timeStamp += delay;
	}
	
	/**
	 * Set the destination of this message
	 * @author Brammert Ottens, 11 jun 2009
	 * @param destination	the destination of this message
	 */
	public void setDestination(Object destination) {
		this.destinations = new HashSet<Object>();
		this.destinations.add(destination);
	}
	
	/**
	 * Set the destination of this message
	 * @author Brammert Ottens, 11 jun 2009
	 * @param destinations	the destinations of this message
	 */
	public void setDestinations(Collection<Object> destinations) {
		this.destinations = destinations;
	}
	
	/**
	 * Sets the message counter
	 * @author Brammert Ottens, 6 nov 2009
	 * @param messageCounter the messageCounter of the queue sending the message
	 */
	public void setMessageCounter(int messageCounter) {
		this.messageCounter = messageCounter;
	}
	
	/**
	 * @author Brammert Ottens, 6 nov 2009
	 * @return the message counter of this message 
	 */
	public int getMessageCounter() {
		return this.messageCounter;
	}
	
	/** @see java.lang.Object#toString() */
	public String toString () {
		StringBuilder builder = new StringBuilder (message.toString());
		builder.append("\n\tdestinations:\t" + this.destinations);
		builder.append("\n\tncccs:\t" + this.ncccs);
		builder.append("\n\ttimeStamp:\t" + this.timeStamp);
		builder.append("\n\tmessage counter:\t" + this.messageCounter);
		return builder.toString();
	}
}
