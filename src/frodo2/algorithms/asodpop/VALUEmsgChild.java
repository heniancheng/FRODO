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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

import frodo2.algorithms.odpop.VALUEmsgWithVars;
import frodo2.solutionSpaces.Addable;

/**
 * A VALUE message, which contains the value assignment of a variable
 * @author Brammert Ottens, Thomas Leaute
 *
 * @param <Val> type used for variable values
 */
public class VALUEmsgChild < Val extends Addable<Val> > 
extends VALUEmsgWithVars<Val> {

	/**
	 * If this message is confirmed, it also functions as a termination message
	 */
	protected boolean isConfirmed;

	/** Empty constructor */
	public VALUEmsgChild () {
		super.type = ASODPOP.VALUE_MSG_TYPE_CHILD;
	}

	/**
	 * A constructor
	 * @param dest			The recipient of the message
	 * @param values		An array of value assignments
	 * @param isConfirmed 	\c true when this message is a confirmed message
	 */
	public VALUEmsgChild(String dest, HashMap<String, Val> values, boolean isConfirmed) {
		super(ASODPOP.VALUE_MSG_TYPE_CHILD, dest, values);
		this.isConfirmed = isConfirmed;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		
		// Serialize the values manually
		HashMap<String, Val> values = super.getPayload2();
		assert values.size() < Short.MAX_VALUE;
		out.writeShort(values.size());
		for (Map.Entry<String, Val> entry : values.entrySet()) {
			out.writeObject(entry.getKey());
			out.writeObject(entry.getValue());
		}
		
		out.writeBoolean(this.isConfirmed);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		
		// Read the values
		short nbrValues = in.readShort();
		HashMap<String, Val> values = new HashMap<String, Val> (nbrValues);
		for (short i = 0; i < nbrValues; i++) {
			String var = (String) in.readObject();
			values.put(var, (Val) in.readObject());
		}
		super.setPayload2(values);
		
		this.isConfirmed = in.readBoolean();
	}

	/**
	 * @return Returns true if this message is confirmed, and false otherwise
	 */
	public boolean isConfirmed() {
		return isConfirmed;
	}
}