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
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

/** A message that has a single object of generic type as a payload. 
 * @author Thomas Leaute
 * @param <T> the type of the payload
 */
public class MessageWithPayload < T extends Serializable > extends Message {

	/** Empty constructor */
	public MessageWithPayload () { }

	/** @see Message#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(this.payload);
	}

	/** @see Message#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.payload = (T) in.readObject();
	}

	/** Constructor
	 * @param type the type of this message
	 * @param payload the payload of this message
	 */
	public MessageWithPayload(String type, T payload) {
		super(type);
		this.payload = payload;
	}

	/** The payload of this message */
	private T payload;
	
	/** @return a shallow clone of this message */
	public MessageWithPayload <T> clone () {
		return new MessageWithPayload <T> (getType(), payload);
	}

	/** @return this message's payload */
	public T getPayload() {
		return payload;
	}

	/** @param payload the payload to set */
	public void setPayload(T payload) {
		this.payload = payload;
	}
	
	/** @see Message#toString() */
	public String toString () {
		return super.toString() + "\n\tpayload = " + payload;
	}
}
