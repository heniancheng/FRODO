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

package frodo2.algorithms.dpop.privacy;

import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;

/** A wrapper message around a payload message that must be routed through the DFS 
 * @param <M> the Message class of the payload
 */
public class RoutingMsg <M extends Message> extends MessageWith3Payloads<String, M, String> {

	/** Empty constructor used for externalization */
	public RoutingMsg () { }

	/** Public constructor
	 * @param type 		the type of this message
	 * @param sender 	the sender variable
	 * @param msg 		the payload message
	 */
	public RoutingMsg(String type, String sender, M msg) {
		super(type, sender, msg, sender);
	}
	
	/** Protected constructor
	 * @param type 		the type of this message
	 * @param sender 	the sender variable
	 * @param msg 		the payload message
	 * @param dest 		the destination variable
	 */
	protected RoutingMsg(String type, String sender, M msg, String dest) {
		super(type, sender, msg, dest);
	}
	
	/** @return the sender variable */
	public String getSender () {
		return super.getPayload1();
	}
	
	/** @return the payload message */
	public M getPayload () {
		return super.getPayload2();
	}
	
	/** @return the destination variable */
	protected String getDest () {
		return super.getPayload3();
	}
	
	/** @see MessageWith3Payloads#toString() */
	@Override
	public String toString () {
		StringBuilder builder = new StringBuilder ("Message (type = `" + super.type + "')");
		builder.append("\n\tsender  = " + super.getPayload1());
		builder.append("\n\tdest    = " + super.getPayload3());
		builder.append("\n\tmessage = " + super.getPayload2());
		return builder.toString();
	}
}