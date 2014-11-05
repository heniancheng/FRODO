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

/** This interface describes a listener that is notified by a queue of outgoing messages
 * @author Thomas Leaute
 * @param <T> the class used for message types
 */
public interface OutgoingMsgPolicyInterface <T> extends MessageListener<T> {
	
	/** The decision made
	 * @author Thomas Leaute 
	 */
	public static enum Decision { 
		/** the message should be discarded */ DISCARD, 
		/** the message can be discarded */ DONTCARE }

	/** Notifies the object of an outgoing message
	 * @param msg 	outgoing message 
	 * @return The decision on what should be done with \a msg
	 */
	public Decision notifyOut (Message msg);
	
}
