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

/** A wrapper message containing a payload message that must be forwarded 
 * to the last leaf in the sub-tree rooted at the destination variable
 */
public class ToLastLeafMsg extends MessageWith2Payloads<String, Message> implements Externalizable {
	
	/** Empty constructor used for externalization */
	public ToLastLeafMsg () {
		super.type = SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE;
	}

	/** Constructor
	 * @param dest 			the destination variable
	 * @param payloadMsg 	the payload message to be forwarded to the last leaf in the sub-tree rooted at \a dest
	 */
	public ToLastLeafMsg(String dest, Message payloadMsg) {
		super(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE, dest, payloadMsg);
	}
	
	/** @return the destination variable */
	public String getVar () {
		return super.getPayload1();
	}
	
	/** @return the payload message */
	public Message getPayload () {
		return super.getPayload2();
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(super.getPayload1());
		
		Message msg = super.getPayload2();
		out.writeObject(msg.getClass());
		msg.writeExternal(out);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		super.setPayload1((String) in.readObject());
		
		Class<? extends Message> msgClass = (Class<? extends Message>) in.readObject();
		Message msg = null;
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

}