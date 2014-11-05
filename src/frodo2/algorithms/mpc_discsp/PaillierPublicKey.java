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
import java.security.SecureRandom;

/** A Paillier public key
 * @author Thomas Leaute
 */
public class PaillierPublicKey implements Externalizable {
	
	/** The first part of the public encryption key */
	BigInteger n;

	/** n^2 */
	BigInteger nsquare;

	/** Number of bits of n */
	private int bitLength;

	/** The second part of the public encryption key */
	private BigInteger g;
	
	/** A source of randomness */
	private final SecureRandom rand;

	/** Empty constructor used for externalization */
	public PaillierPublicKey () {
		this.rand = new SecureRandom ();
	}
	
	/** Constructor
	 * @param n 			The first part of the public encryption key
	 * @param nsquare 		n^2
	 * @param bitLength 	Number of bits of n
	 * @param g 			The second part of the public encryption key
	 */
	public PaillierPublicKey (BigInteger n, BigInteger nsquare, int bitLength, BigInteger g) {
		this.n = n;
		this.nsquare = nsquare;
		this.bitLength = bitLength;
		this.g = g;
		this.rand = new SecureRandom ();
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.n);
		out.writeObject(this.g);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.n = (BigInteger) in.readObject();
		this.nsquare = this.n.multiply(this.n);
		this.g = (BigInteger) in.readObject();
	}

	/** Encrypts an integer
	 * @param plaintext 	plaintext integer
	 * @return cyphertext
	 */
	public PaillierInteger encrypt (BigInteger plaintext) {
		
		// Generate a random r (!= 0) mod n
		BigInteger r = new BigInteger(bitLength, this.rand);
		while (r.equals(BigInteger.ZERO) || r.compareTo(n) >= 0)
			r = new BigInteger(bitLength, this.rand);

		return new PaillierInteger (g.modPow(plaintext, nsquare).multiply(r.modPow(n, nsquare)).mod(nsquare), this.nsquare);
	}
	
	/** Fakes the encryption of an integer
	 * @param plaintext 	plaintext integer
	 * @return cyphertext
	 */
	public PaillierInteger fakeEncrypt (BigInteger plaintext) {
		
		return new PaillierInteger (g.modPow(plaintext, nsquare), this.nsquare);
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "[" + this.n + ", " + this.g + "]";
	}
	
}