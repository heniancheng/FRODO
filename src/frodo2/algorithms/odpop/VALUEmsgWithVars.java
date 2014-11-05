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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;

/**
 * Value message with variables
 * @author Brammert Ottens, Thomas Leaute
 * 
 * @param <Val> the type used for domain values
 */
public class VALUEmsgWithVars <Val extends Addable<Val>> 
extends MessageWith2Payloads<String, HashMap<String, Val>> implements Externalizable {
	
	/** Empty constructor */
	public VALUEmsgWithVars () {
		super.type = VALUEpropagation.VALUE_MSG_TYPE;
	}

	/** Constructor 
	 * @param dest 			destination variable
	 * @param values 		array of values for the variables in \a variables, in the same order
	 */
	public VALUEmsgWithVars(String dest, HashMap<String, Val> values) {
		super(VALUEpropagation.VALUE_MSG_TYPE, dest, values);
	}

	/** Constructor 
	 * @param type 			the type of the message
	 * @param dest 			destination variable
	 * @param values 		array of values for the variables in \a variables, in the same order
	 */
	protected VALUEmsgWithVars(String type, String dest, HashMap<String, Val> values) {
		super(type, dest, values);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		
		// Write the assignments
		HashMap<String, Val> values = this.getValues();
		if (values == null) 
			out.writeShort(-1);
		else {
			assert values.size() < Short.MAX_VALUE;
			out.writeShort(values.size()); // number of values
			
			// Write the first entry
			Iterator< Map.Entry<String, Val> > iter = values.entrySet().iterator();
			Map.Entry<String, Val> entry = iter.next();
			out.writeObject(entry.getKey());
			out.writeObject(entry.getValue());
			final boolean externalize = entry.getValue().externalize();
			
			// Write the remaining entries
			while (iter.hasNext()) {
				entry = iter.next();
				out.writeObject(entry.getKey());
				if (externalize) 
					entry.getValue().writeExternal(out);
				else 
					out.writeObject(entry.getValue());
			}
		}
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		
		// Read the assignments
		final short nbrValues = in.readShort(); // number of values
		if (nbrValues > -1) {
			HashMap<String, Val> values = new HashMap<String, Val> (nbrValues);
			
			// Read the first entry
			String var = (String) in.readObject();
			Val val = (Val) in.readObject();
			values.put(var, val);
			final boolean externalize = val.externalize();
			
			// Read the remaining entries
			for (short i = 1; i < nbrValues; i++) {
				var = (String) in.readObject();
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					val = (Val) val.readResolve();
				} else 
					val = (Val) in.readObject();
				values.put(var, val);
			}
			
			super.setPayload2(values);
		}
	}

	/** @return the destination variable */
	public String getDest() {
		return super.getPayload1();
	}

	/** @return the values for the variables in the separator */
	public HashMap<String, Val> getValues() {
		return super.getPayload2();
	}
	
	/** @see frodo2.communication.Message#toString() */
	public String toString () {
		if(super.getPayload2() == null)
			return "Message(type = `" + this.getType() + "')\n\tdest: " + super.getPayload1() + "\n\tvals: null)";
		else
			return "Message(type = `" + this.getType() + "')\n\tdest: " + super.getPayload1() + "\n\tvals: " + super.getPayload2();
	}
	
	
}