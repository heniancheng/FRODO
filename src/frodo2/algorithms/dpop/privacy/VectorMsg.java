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
import java.util.ArrayList;

import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;

/**
 * Message that contains a vector of elements E and a counter. Message used to determine which variable will be the next root of a component
 * @author Eric Zbinden, Thomas Leaute
 * @param <C> Class used for clear text vector element
 * @param <E> class used for encrypted vector element
 */
public class VectorMsg<C extends Addable<C>, E extends AddableLimited<C,E>> extends MessageWith3Payloads<ArrayList<E>, String, Short> implements Externalizable{

	/**
	 * EmptyConstructor
	 */
	public VectorMsg(){
		this.type = SecureRerooting.VECTOR_TYPE;
	}
	
	/**
	 * Constructor
	 * @param vector 		the vector
	 * @param vectorOwner 	the owner of this vector 
	 * @param round 		the round
	 */
	public VectorMsg(ArrayList<E> vector, String vectorOwner, Short round){
		super (SecureRerooting.VECTOR_TYPE, vector, vectorOwner, round);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		
		// Read the vector
		final int size = in.readInt();
		ArrayList<E> vector = new ArrayList<E> (size);
		E elmt = (E) in.readObject();
		vector.add(elmt);
		Class<E> classOfE = (Class<E>) elmt.getClass();
		if (elmt.externalize()) {
			for (int i = 1; i < size; i++) {
				try {
					elmt = classOfE.newInstance();
				} catch (Exception e) {
					e.printStackTrace();
				}
				elmt.readExternal(in);
				vector.add(elmt);
			}
		} else 
			for (int i = 1; i < size; i++)
				vector.add((E) in.readObject());
		this.setPayload1(vector);
		
		this.setPayload2((String)in.readObject());
		this.setPayload3(in.readShort());
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		// Write the vector
		ArrayList<E> vector = this.getVector();
		final int size = vector.size();
		out.writeInt(size);
		E first = vector.get(0);
		out.writeObject(first);
		if (first.externalize()) 
			for (int i = 1; i < size; i++) 
				vector.get(i).writeExternal(out);
		else 
			for (int i = 1; i < size; i++)
				out.writeObject(vector.get(i));
		
		out.writeObject(this.getPayload2());
		out.writeShort(this.getPayload3());
	}
	
	/** @return return the vector of this message */
	public ArrayList<E> getVector(){
		return this.getPayload1();
	}
	
	/** @return the owner of this vector*/
	public String getOwner(){
		return this.getPayload2();
	}
	
	/** @return whether to shuffle the vector */
	public Short getRound () {
		return this.getPayload3();
	}
	
	/** @see MessageWith2Payloads#toString() */
	@Override
	public String toString () {
		return "Message (type = " + super.type + ")\n\t vector: " + super.getPayload1() 
			+ "\n\t owner: " + this.getOwner() + "\n\t round: " + this.getRound();
	}
}
