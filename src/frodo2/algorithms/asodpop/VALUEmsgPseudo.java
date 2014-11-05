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

package frodo2.algorithms.asodpop;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;

/**
 * A VALUE message, which contains the value assignment of a variable
 * @author Brammert Ottens, Thomas Leaute
 *
 * @param <Val> type used for variable values
 */
public class VALUEmsgPseudo < Val extends Addable<Val> > 
extends MessageWith3Payloads<String, String, Val> implements Externalizable {

	/**
	 * For serialization purposes
	 */
	private static final long serialVersionUID = 7684330564229591518L;

	/** Empty constructor */
	public VALUEmsgPseudo () {
		super.type = ASODPOP.VALUE_MSG_TYPE_PSEUDO;
	}

	/**
	 * A constructor
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param context 		The context
	 */
	public VALUEmsgPseudo(String sender, String receiver, Val context) {
		super(ASODPOP.VALUE_MSG_TYPE_PSEUDO, sender, receiver, context);
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
	 * @return	the sender of this message
	 */
	public String getSender() {
		return this.getPayload1();
	}

	/**
	 * @return the destination of this message
	 */
	public String getReceiver() {
		return this.getPayload2();
	}

	/**
	 * @return an array of value assignments
	 */
	public Val getContext() {
		return this.getPayload3();
	}
}