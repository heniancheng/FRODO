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

/** A message that has three objects of generic types as a payload.
 * @author Thomas Leaute
 * @author Brammert Ottens
 * @param <T1> type of the first payload
 * @param <T2> type of the second payload
 * @param <T3> type of the third payload
 */
public class MessageWith3Payloads < T1 extends Serializable, T2 extends Serializable, T3 extends Serializable > extends Message {
	
	/** First payload */
	private T1 payload1;
	
	/** Second payload */
	private T2 payload2;
	
	/** Third payload */
	private T3 payload3;
	
	/** Empty constructor */
	public MessageWith3Payloads () { }

	/** @see Message#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(this.payload1);
		out.writeObject(this.payload2);
		out.writeObject(this.payload3);
	}

	/** @see Message#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.payload1 = (T1) in.readObject();
		this.payload2 = (T2) in.readObject();
		this.payload3 = (T3) in.readObject();
	}

	/** Constructor
	 * @param type the type of this message
	 * @param payload1 first payload
	 * @param payload2 second payload
	 * @param payload3 third payload
	 */
	public MessageWith3Payloads(String type, T1 payload1, T2 payload2, T3 payload3) {
		super(type);
		this.payload1 = payload1;
		this.payload2 = payload2;
		this.payload3 = payload3;
	}
	
	/** @return a shallow clone of this message */
	public MessageWith3Payloads <T1, T2, T3> clone () {
		return new MessageWith3Payloads <T1, T2, T3> (getType(), payload1, payload2, payload3);
	}

	/** @return the first payload */
	public T1 getPayload1() {
		return payload1;
	}

	/** @param payload1 the first payload to set */
	public void setPayload1(T1 payload1) {
		this.payload1 = payload1;
	}

	/** @return the second payload */
	public T2 getPayload2() {
		return payload2;
	}
	
	/** @param payload2 the second payload to set */
	public void setPayload2(T2 payload2) {
		this.payload2 = payload2;
	}

	/** @return the third payload */
	public T3 getPayload3() {
		return payload3;
	}

	/** @param payload3 the third payload to set */
	public void setPayload3(T3 payload3) {
		this.payload3 = payload3;
	}

	/** @see Message#toString() */
	public String toString () {
		return super.toString() + "\n\tpayload1 = " + payload1 + "\n\tpayload2 = " + payload2 + "\n\tpayload3 = " + payload3;
	}
}
