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

package frodo2.algorithms.dpop.memory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;

/** A CONTEXT message sent by cluster nodes in MB-DPOP
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 */
public class ContextMsg < V extends Addable<V> > extends MessageWith3Payloads<String, String[], V[]> {
	
	/** The type of this message */
	public static final String CONTEXT_MSG_TYPE = "ContextMsg";

	/** Default constructor used for externalization */
	public ContextMsg () {
		super.type = CONTEXT_MSG_TYPE;
	}
	
	/** Constructor
	 * @param dest 		the destination variable
	 * @param ccs 		the cluster-cutset variables
	 * @param values 	the values for the cluster-cutset variables
	 */
	public ContextMsg (String dest, String[] ccs, V[] values) {
		super (CONTEXT_MSG_TYPE, dest, ccs, values);
	}
	
	/** @see MessageWith3Payloads#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeObject(this.getPayload1());
		out.writeObject(this.getCCs());
		
		// Write the values
		out.writeShort(this.getValues().length);
		if (this.getValues().length == 0) 
			out.writeObject(this.getValues().getClass());
		for (V val : this.getValues()) 
			out.writeObject(val); // each value
	}

	/** @see MessageWith3Payloads#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

		super.setPayload1((String) in.readObject());
		super.setPayload2((String[]) in.readObject());
		
		// Read the first value to get the class of V
		final int nbrCCs = in.readShort();
		if (nbrCCs == 0) 
			super.setPayload3((V[]) Array.newInstance(((Class<? extends V[]>) in.readObject()).getComponentType(), 0));
		else {
			V firstVal = (V) in.readObject();
			V[] values = (V[]) Array.newInstance(firstVal.getClass(), nbrCCs);
			values[0] = firstVal;
			for (short i = 1; i < nbrCCs; i++) // remaining values
				values[i] = (V) in.readObject();
			super.setPayload3(values);
		}
	}

	/** @return the destination variable */
	public String getDest () {
		return super.getPayload1();
	}
	
	/** @return the cluster-cutset variables */
	public String[] getCCs () {
		return super.getPayload2();
	}
	
	/** @return the values for the cluster-cutset variables */
	public V[] getValues () {
		return super.getPayload3();
	}
	
	/** @see MessageWith3Payloads#toString() */
	@Override
	public String toString () {
		return "Message(type = `" + super.type + "')\n\t dest: " + this.getDest() + "\n\t context: " + Arrays.toString(this.getCCs()) + " = " + Arrays.toString(this.getValues());
	}
	
}
