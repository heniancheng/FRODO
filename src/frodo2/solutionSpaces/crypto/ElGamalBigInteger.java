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
package frodo2.solutionSpaces.crypto;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Arrays;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableLimited;

/** ElGamal encryption of an integer
 * @author Thomas Leaute, Eric Zbinden
 */
public class ElGamalBigInteger implements AddableLimited <AddableInteger, ElGamalBigInteger> {
	
	/** The (unencrypted) [alpha, beta] representation of 1 */
	private static BigInteger[] ONE = new BigInteger[] { BigInteger.ONE, BigInteger.ONE };

	/** The (unencrypted) [alpha, beta] representation of 2 */
	private static BigInteger[] TWO = new BigInteger[] { BigInteger.ONE.add(BigInteger.ONE), BigInteger.ONE };

	/** A vector of pairs that represents this ElGamalBigInteger as [[alpha1, beta1], [alpha2, beta2], ... ] */
	BigInteger[][] vector;
	
	/** Used for externalization only */
	public ElGamalBigInteger () { }
	
	/**
	 * Constructor
	 * @param value the cleartext value
	 * @param size  the size of the vector of this ElGamal number
	 * @note The result is not truly encrypted yet
	 */
	public ElGamalBigInteger(final AddableInteger value, final int size){
		
		assert size > 0 : "Should not create ElGamalBigIntegers of size 0";
		this.vector = new BigInteger [size][];
		
		// Each number is represented by a vector of pairs of BigIntegers: 3 is represented by [E(1), E(1), E(1), E(2), ...]
		// Note: we cannot use 0 in the vector because its encryption is recognizable (alpha = 0); we use 2 instead
		final int valueInt = Math.min(value.intValue(), size);
		Arrays.fill(this.vector, 0, valueInt, ONE);
		Arrays.fill(this.vector, valueInt, size, TWO);
	}
	
	/**
	 * Constructor
	 * @param vector the internal representation of an ElGamalBigInteger
	 */
	ElGamalBigInteger(BigInteger[][] vector){
		this.vector = vector;
	}
	
	/** 
	 * @see AddableLimited#add(Addable) 
	 * @note The result is not truly encrypted yet
	 */
	public ElGamalBigInteger add(final AddableInteger other) {
			
		final int size = this.vector.length;
		final int shift = Math.min(other.intValue(), size);
		
		if (shift == 0) 
			 return this;
		
		BigInteger[][] newVector = new BigInteger [size][];
		
		// Put "shift" times E(1) at the head of the vector
		Arrays.fill(newVector, 0, shift, ONE);
		System.arraycopy(this.vector, 0, newVector, shift, size - shift);
		
		return new ElGamalBigInteger(newVector);
	}

	/** @see AddableLimited#min(AddableLimited) */
	public ElGamalBigInteger min(final ElGamalBigInteger other) {
		
		final int size = this.vector.length;
		assert size == other.vector.length : "ElGamal vector sizes are not the same. This: "+size+", That: "+other.vector.length;
		
		final BigInteger[][] vector1 = this.vector;
		final BigInteger[][] vector2 = other.vector;
		BigInteger[][] newVector = new BigInteger [size][];
		
		for(int i=0;i<size;i++) {
			final BigInteger[] pair1 = vector1[i];
			final BigInteger[] pair2 = vector2[i];
			newVector[i] = new BigInteger[] { pair1[0].multiply(pair2[0]), pair1[1].multiply(pair2[1]) };
		}
		
		return new ElGamalBigInteger(newVector);
	}

	/** @see AddableLimited#max(AddableLimited) */
	public ElGamalBigInteger max(ElGamalBigInteger other) {
		/// @todo Auto-generated method stub
		assert false : "not yet implemented";
		return null;
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		final int size = in.readInt();
		final BigInteger[][] myVector = new BigInteger [size][];
		
		for(int i=0;i<size;i++) 
			myVector[i] = new BigInteger[] { (BigInteger)in.readObject(), (BigInteger)in.readObject() };
		this.vector = myVector;
		
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		final BigInteger[][] myVector = this.vector;
		final int size = myVector.length;
		out.writeInt(size);
		
		for(int i=0;i<size;i++){
			BigInteger[] pair = myVector[i];
			out.writeObject(pair[0]);
			out.writeObject(pair[1]);
		}
		
	}

	/** @see AddableLimited#readResolve() */
	public Object readResolve() {
		return this;
	}
	
	/** @see AddableLimited#externalize() */
	public final boolean externalize() {
		return true;
	}

	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		
		NumberFormat format = NumberFormat.getInstance();
		StringBuilder builder = new StringBuilder ("[[");

		final int sizeMin1 = this.vector.length - 1;
		for (int i = 0; i < sizeMin1; i++) {
			BigInteger[] pair = this.vector[i];
			builder.append(format.format(pair[0]));
			builder.append(", ");
			builder.append(format.format(pair[1]));
			builder.append("], [");
		}
		BigInteger[] pair = this.vector[sizeMin1];
		builder.append(format.format(pair[0]));
		builder.append(", ");
		builder.append(format.format(pair[1]));
		builder.append("]]");
		
		return builder.toString();
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals (Object o) {
		assert false : "ElGamalBigInteger.equals() should never be called";
		return false;
	}
	
}
