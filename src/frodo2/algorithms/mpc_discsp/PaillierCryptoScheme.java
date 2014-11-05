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

import java.math.BigInteger;
import java.security.SecureRandom;

/** The Paillier crypto scheme
 * @author Thomas Leaute
 */
public class PaillierCryptoScheme {
	
	/** The first part of the private decryption key */
	private final BigInteger lambda;

	/** The second part of the private decryption key */
	private final BigInteger mu;
	
	/** The public key */
	final PaillierPublicKey publicKey;

	/** Source of randomness */
	private final SecureRandom rand;

	/** Constructor */
	public PaillierCryptoScheme () {
		this (512);
	}

	/** Constructor
	 * @param bitLength 	the desired number of bits
	 */
	public PaillierCryptoScheme (int bitLength) {

		rand = new SecureRandom();
		
		// Compute n
		final int certaintyOfPrimality = 1000;
		BigInteger p = new BigInteger(bitLength / 2, certaintyOfPrimality, rand);
		BigInteger q = new BigInteger(bitLength / 2, certaintyOfPrimality, rand);
		BigInteger n = p.multiply(q);
		BigInteger nsquare = n.multiply(n);
		
		// Compute lambda
		BigInteger pMin1 = p.subtract(BigInteger.ONE);
		BigInteger qMin1 = q.subtract(BigInteger.ONE);
		lambda = pMin1.multiply(qMin1).divide(pMin1.gcd(qMin1)); // = lcm (p-1, q-1)

		// Find a random g (mod n^2) such that n divides the order of g
		int nbrBitsNsquare = n.bitCount();
		BigInteger muInv = null;
		BigInteger g;
		do {
			g = new BigInteger (nbrBitsNsquare, rand);
			if (g.equals(BigInteger.ZERO) || g.compareTo(nsquare) >= 0) 
				continue;
			
			muInv = g.modPow(lambda, nsquare).subtract(BigInteger.ONE).divide(n);
		} while (! muInv.gcd(n).equals(BigInteger.ONE));
		
		// Compute mu
		this.mu = muInv.modInverse(n);
		
		this.publicKey = new PaillierPublicKey (n, nsquare, bitLength, g);
	}
	
	/** Decrypts a cyphertext
	 * @param cypher 	an encrypted integer
	 * @return a plaintext integer
	 */
	public BigInteger decrypt (PaillierInteger cypher) {
		return cypher.value.modPow(lambda, this.publicKey.nsquare)
			.subtract(BigInteger.ONE)
			.divide(this.publicKey.n)
			.multiply(mu)
			.mod(this.publicKey.n);
	}

}
