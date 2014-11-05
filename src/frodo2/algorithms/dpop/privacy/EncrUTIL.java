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

import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.UtilitySolutionSpaceLimited;

/** Encrypted UTIL msg 
 * @param <V> The type of variable values
 * @param <U> The type of the utilities 
 * @param <E> The type of the encrypted utilities
 */
public class EncrUTIL <V extends Addable<V>, U extends Addable<U>, E extends AddableLimited<U,E>> extends Message implements Externalizable {
	
	/** The solution space*/
	private UtilitySolutionSpaceLimited<V, U, E> space;
	
	/**
	 * Constructor
	 * @param space 	the utilitySolutionSpace
	 */
	public EncrUTIL(UtilitySolutionSpaceLimited<V, U, E> space){
		super(EncryptedUTIL.ENCRYPTED_UTIL_TYPE);
		
		this.space = space;			
	}
	
	/** Empty Constructor used for externalization */
	public EncrUTIL(){
		super.type = EncryptedUTIL.ENCRYPTED_UTIL_TYPE;		
	}
	
	/** @return the space */
	public UtilitySolutionSpaceLimited<V,U,E> getSpace(){
		return space;
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */		
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {		
		this.space = (UtilitySolutionSpaceLimited<V,U,E>) in.readObject();
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(space);			
	}		
	
	/** @see Message#fakeSerialize() */
	@Override
	public void fakeSerialize () {
		this.space = this.space.resolve();
	}
	
	/** @see frodo2.communication.Message#toString() */
	public String toString(){
		return "EncryptedUTIL:\n" +space.toString();
	}
	
}