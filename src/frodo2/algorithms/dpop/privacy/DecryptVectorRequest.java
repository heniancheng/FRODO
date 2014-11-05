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

import frodo2.algorithms.dpop.privacy.CollaborativeDecryption;
import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;

/**
 * Vector decryption request 
 * @author Eric Zbinden
 * @param <C> Class used for clear text vector element
 * @param <E> class used for encrypted vector element
 */
public class DecryptVectorRequest<C extends Addable<C>, E extends AddableLimited<C,E>> extends MessageWith2Payloads<E, String>
		implements Externalizable {

	/** The initial element to decrypt */
	private E initial;
	
	/**
	 * Empty Constructor
	 */
	public DecryptVectorRequest(){
		this.type = CollaborativeDecryption.VECTOR_REQUEST_TYPE;
	}
	
	/**
	 * Constructor
	 * @param elem the head element of the vector to decrypt
	 * @param initial the initial element to decrypt
	 * @param decryptFor the variable for which it's decrypted
	 */
	public DecryptVectorRequest(E elem, E initial, String decryptFor){
		this.type = CollaborativeDecryption.VECTOR_REQUEST_TYPE;
		this.setPayload1(elem);
		this.setPayload2(decryptFor);
		this.initial = initial;
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		this.setPayload1((E)in.readObject());
		this.initial = (E)in.readObject();
		this.setPayload2((String)in.readObject());
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getPayload1());
		out.writeObject(this.initial);
		out.writeObject(this.getPayload2());
	}
	
	/** @return the element to decrypt */
	public E getElem(){
		return this.getPayload1();
	}
	
	/** @return the destination of this message */
	public String getDecryptFor(){
		return this.getPayload2();
	}
	
	/** @return the initial element to decrypt*/
	public E getInitial(){
		return this.initial;
	}

}
