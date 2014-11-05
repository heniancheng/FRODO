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

package frodo2.algorithms.localSearch.mgm;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;

/**
 * @author Brammert Ottens, Thomas Leaute
 * @param <V> type used for domain values
 * 
 */
public class OK <V extends Addable<V> > extends MessageWith3Payloads<String, String, V> implements Externalizable {

	/** Default constructor used for externalization only */
	public OK () {
		super.type = MGM.OK_MSG_TYPE;
	}

	/**
	 * Constructor
	 * 
	 * @param sender		the sender of the message
	 * @param receiver		the receiver of the message
	 * @param value			the value of the variable
	 */
	public OK(String sender, String receiver, V value) {
		super(MGM.OK_MSG_TYPE, sender, receiver, value);
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getPayload1());
		out.writeObject(this.getPayload2());
		out.writeObject(this.getPayload3());
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((String) in.readObject());
		super.setPayload3((V) in.readObject());
	}

	/**
	 * @author Brammert Ottens, 21 feb. 2011
	 * @return the sender of the message
	 */
	public String getSender() {
		return this.getPayload1();
	}
	
	/**
	 * @author Brammert Ottens, 21 feb. 2011
	 * @return the receiver of the message
	 */
	public String getReceiver() {
		return this.getPayload2();
	}
	
	/**
	 * @author Brammert Ottens, 21 feb. 2011
	 * @return the value of the variable
	 */
	public V getValue() {
		return this.getPayload3();
	}
	
}
