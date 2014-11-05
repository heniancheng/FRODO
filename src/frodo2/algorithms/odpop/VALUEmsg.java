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

package frodo2.algorithms.odpop;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;

/** VALUE message
 * @param <Val> the type used for variable values
 * @author Brammert Ottens, Thomas Leaute
 */
public class VALUEmsg < Val extends Addable<Val> > 
extends MessageWith2Payloads<String, Val[]> implements Externalizable {
	
	/** Empty constructor */
	public VALUEmsg () {
		super.type = VALUEpropagation.VALUE_MSG_TYPE;
	}

	/** Constructor 
	 * @param dest 			destination variable
	 * @param values 		array of values for the variables in \a variables, in the same order
	 */
	public VALUEmsg(String dest, Val[] values) {
		super(VALUEpropagation.VALUE_MSG_TYPE, dest, values);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		
		// Write the values
		Val[] values = this.getValues();
		if (values == null) 
			out.writeShort(-1);
		else {
			assert values.length < Short.MAX_VALUE;
			out.writeShort(values.length); // number of values
			out.writeObject(values[0]); // first value
			final boolean externalize = values[0].externalize();
			for (short i = 1; i < values.length; i++) { // remaining values
				if (externalize) 
					values[i].writeExternal(out);
				else 
					out.writeObject(values[i]);
			}
		}
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		
		// Read the values
		final int nbrValues = in.readShort(); // number of values
		if (nbrValues > -1) {
			Val val = (Val) in.readObject(); // first value
			Val[] values = (Val[]) Array.newInstance(val.getClass(), nbrValues);
			values[0] = val;
			super.setPayload2(values);
			final boolean externalize = val.externalize();
			for (short i = 1; i < nbrValues; i++) { // remaining values
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					val = (Val) val.readResolve();
				} else 
					val = (Val) in.readObject();
				values[i] = val;
			}
		}
	}

	/** @return the destination variable */
	public String getDest() {
		return super.getPayload1();
	}

	/** @return the values for the variables in the separator */
	public Val[] getValues() {
		return super.getPayload2();
	}
	
	/** @see frodo2.communication.Message#toString() */
	public String toString () {
		if(super.getPayload2() == null)
			return "Message(type = `" + this.getType() + "')\n\tdest: " + super.getPayload1() + "\n\tvals: null)";
		else
			return "Message(type = `" + this.getType() + "')\n\tdest: " + super.getPayload1() + "\n\tvals: " + Arrays.asList(super.getPayload2());
	}
}