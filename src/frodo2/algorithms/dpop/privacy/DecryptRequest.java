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

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;

/** Decryption Request Message 
 * @param <C> class used for clear-text utility
 * @param <E> class used for encrypted utility
 */
public class DecryptRequest<C extends Addable<C>, E extends AddableLimited<C,E>> extends MessageWith3Payloads<E,E,String> implements Externalizable {
	
	/** Initial value of min1*/
	private E initialMin1;
	/** Initial value of min2*/
	private E initialMin2;
	
	/**
	 * Constructor
	 * @param min1 		  an encrypted local minimum
	 * @param initialMin1 the initial value of the local minimum to decrypt
	 * @param min2 		  an encrypted local minimum
	 * @param initialMin2 the initial value of the local minimum to decrypt
	 * @param decryptFor  the variable for whom it's decrypted
	 */
	public DecryptRequest(E min1, E initialMin1, E min2, E initialMin2, String decryptFor){
		this.type = CollaborativeDecryption.REQUEST_TYPE;
		this.setPayload1(min1);
		this.setPayload2(min2);
		this.setPayload3(decryptFor);
		this.initialMin1 = initialMin1;
		this.initialMin2 = initialMin2;
	}
	
	/**
	 * empty constructor
	 */
	public DecryptRequest(){
		this.type = CollaborativeDecryption.REQUEST_TYPE;
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.setPayload1((E)in.readObject());
		this.initialMin1 = (E)in.readObject();
		this.setPayload2((E)in.readObject());
		this.initialMin2 = (E)in.readObject();
		this.setPayload3((String)in.readObject());
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getPayload1());
		out.writeObject(initialMin1);
		out.writeObject(this.getPayload2());	
		out.writeObject(initialMin2);
		out.writeObject(this.getPayload3());	
	}
	
	/** @return the variable for whom it's decrypted */
	public String decryptFor(){
		return getPayload3();
	}
	
	/** @return the initial value to decrypt of min1*/
	public E initialMin1(){
		return this.initialMin1;
	}
	
	/** @return the initial value to decrypt of min2*/
	public E initialMin2(){
		return this.initialMin2;
	}
	
	/** @see frodo2.communication.MessageWith3Payloads#toString() */
	public String toString(){
		return "DECRYPTION REQUEST:\n" +
				"\tdecryptFor: "+decryptFor()+"\n\tmin1: "+this.getPayload1()+"\tinitialMin1: "+this.initialMin1
				+"\n\tmin2: "+this.getPayload2()+"\tinitialMin2: "+this.initialMin2;
	}
}