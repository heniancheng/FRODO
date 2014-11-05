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

package frodo2.algorithms.localSearch.dsa;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;

/**
 * Message used to convey the new value of the sender to a neighbouring variable
 * @author Brammert Ottens, 10 aug 2009
 * 
 * @param <Val>	type used for variable values
 */
public class VALUEmsg < Val extends Addable<Val> > extends MessageWith3Payloads<String, String, Val> 
implements Externalizable {
	
	/** Empty constructor */
	public VALUEmsg () {
		super.type = DSA.VALUE_MSG_TYPE;
	}
	
	/**
	 * 	Default constructor
	 * @param type		the type of this message
	 * @param sender	the sender of this message
	 * @param receiver	the receiver of this message
	 * @param payload	the value of the variable
	 */
	public VALUEmsg(String type, String sender, String receiver, Val payload) {
		super(type, sender, receiver, payload);
		assert(!sender.equals(receiver));
	}
	
	/**
	 * 	Default constructor
	 * @param sender	the sender of this message
	 * @param receiver	the receiver of this message
	 * @param payload	the value of the variable
	 */
	public VALUEmsg(String sender, String receiver, Val payload) {
		super(DSA.VALUE_MSG_TYPE, sender, receiver, payload);
		assert(!sender.equals(receiver));
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		out.writeObject(super.getPayload2());
		out.writeObject(super.getPayload3());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((String) in.readObject());
		super.setPayload3((Val) in.readObject());
	}

	/**
	 * @author Brammert Ottens, 10 aug 2009
	 * @return	the sender of this message
	 */
	public String getSender() {
		return this.getPayload1();
	}

	/**
	 * @author Brammert Ottens, 10 aug 2009
	 * @return	the receiver of this message
	 */
	public String getReceiver() {
		return this.getPayload2();
	}

	/**
	 * @author Brammert Ottens, 10 aug 2009
	 * @return the current value of the sender
	 */
	public Val getValue() {
		return this.getPayload3();
	}

	/** Used for serialisation */
	private static final long serialVersionUID = 6485548935259680831L;

}