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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;

/** A wrapper message containing a message to be delivered to a given variable by SecureCircularRouting
 * @param <M> the Message class of the payload
 */
public class DeliveryMsg <M extends Message> extends MessageWith2Payloads<String, M> implements Externalizable {
	
	/** Empty constructor used for externalization */
	public DeliveryMsg () {
		super.type = SecureCircularRouting.DELIVERY_MSG_TYPE;
	}
	
	/** Constructor 
	 * @param dest 			destination variable
	 * @param payloadMsg 	payload message
	 */
	public DeliveryMsg(String dest, M payloadMsg) {
		super(SecureCircularRouting.DELIVERY_MSG_TYPE, dest, payloadMsg);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(this.getDest());
		
		Message msg = this.getMessage();
		out.writeObject(msg.getClass());
		msg.writeExternal(out);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		super.setPayload1((String) in.readObject());
		
		Class<? extends M> msgClass = (Class<? extends M>) in.readObject();
		M msg = null;
		try {
			msg = msgClass.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		msg.readExternal(in);
		super.setPayload2(msg);
	}

	/** @return the destination variable */
	public String getDest () {
		return super.getPayload1();
	}
	
	/** @return the message being delivered */
	public M getMessage () {
		return super.getPayload2();
	}
	
	/** @see MessageWith2Payloads#toString() */
	@Override
	public String toString () {
		StringBuilder builder = new StringBuilder ("Message (type = `" + super.type + "')");
		builder.append("\n\tdest    = " + super.getPayload1());
		builder.append("\n\tmessage = " + super.getPayload2());
		return builder.toString();
	}
}