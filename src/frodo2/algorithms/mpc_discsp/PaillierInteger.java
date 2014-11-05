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

package frodo2.algorithms.mpc_discsp;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;

/** A Paillier-encrypted integer
 * @author Thomas Leaute
 */
public class PaillierInteger implements Externalizable {
	
	/** The cyphertext */
	BigInteger value;
	
	/** The modulo */
	BigInteger nSquare;
	
	/** Empty constructor used for externalization */
	public PaillierInteger () { }
	
	/** Constructor
	 * @param value 	the cyphertext
	 * @param nSquare 	the modulo
	 */
	PaillierInteger (BigInteger value, BigInteger nSquare) {
		this.value = value;
		this.nSquare = nSquare;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		byte[] byteArray = this.value.toByteArray();
		out.writeInt(byteArray.length);
		out.write(byteArray);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		byte[] byteArray = new byte [in.readInt()];
		in.read(byteArray);
		this.value = new BigInteger (byteArray);
	}
	
	/** Computes the addition of the underlying plaintexts
	 * @param o 	the other cyphertext
	 * @return a cyphertext that decrypts to the sum of the two underlying plaintexts
	 */
	public PaillierInteger add (PaillierInteger o) {
		return new PaillierInteger (this.value.multiply(o.value).mod(this.nSquare), this.nSquare);
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return this.value.toString();
	}

}
