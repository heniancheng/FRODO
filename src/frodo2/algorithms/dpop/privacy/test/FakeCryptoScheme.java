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

package frodo2.algorithms.dpop.privacy.test;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.math.BigInteger;
import java.text.NumberFormat;

import org.jdom2.Element;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.crypto.CryptoScheme;

/** A fake CryptoScheme used to test and debug P2-DPOP
 * @author Eric Zbinden, Thomas Leaute
 */
public class FakeCryptoScheme implements CryptoScheme<AddableInteger, FakeCryptoScheme.FakeEncryptedInteger, FakeCryptoScheme.FakePublicKeyShare> {
	
	/** A share of the (unused) public key */
	@SuppressWarnings("serial")
	public static class FakePublicKeyShare implements CryptoScheme.PublicKeyShare { }
	
	/** A wrapper around a BigInteger so that it implements AddableLimited */
	public static class FakeEncryptedInteger implements AddableLimited<AddableInteger, FakeEncryptedInteger>, Comparable<FakeEncryptedInteger> {
		
		/** The underlying BigInteger */
		private BigInteger bigInt;
		
		/** Empty constructor only used for externalization */
		public FakeEncryptedInteger () { }
		
		/** Constructor
		 * @param bigInt 	a BigInteger
		 */
		public FakeEncryptedInteger (BigInteger bigInt) {
			this.bigInt = bigInt;
		}

		/** Constructor from a string representation
		 * @param string 	a string representation
		 */
		public FakeEncryptedInteger (String string) {
			if (string.equals("infinity")) 
				this.bigInt = cleartextPlusInf.bigInt;
			else 
				this.bigInt = new BigInteger (string);
		}

		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput arg0) throws IOException {
			arg0.writeObject(this.bigInt);
		}
		
		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput arg0) throws IOException, ClassNotFoundException {
			this.bigInt = (BigInteger) arg0.readObject();
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
			return NumberFormat.getInstance().format(this.bigInt);
		}

		/** @see AddableLimited#add(Addable) */
		public FakeEncryptedInteger add(AddableInteger other) {
			if (other == AddableInteger.PlusInfinity.PLUS_INF) 
				return new FakeEncryptedInteger (this.bigInt.add(cleartextPlusInf.bigInt));
			return new FakeEncryptedInteger (this.bigInt.add(BigInteger.valueOf(other.intValue())));
		}

		/** @see AddableLimited#max(AddableLimited) */
		public FakeEncryptedInteger max(FakeEncryptedInteger other) {
			return new FakeEncryptedInteger (this.bigInt.max(other.bigInt));
		}

		/** @see AddableLimited#min(AddableLimited) */
		public FakeEncryptedInteger min(FakeEncryptedInteger other) {
			return new FakeEncryptedInteger (this.bigInt.min(other.bigInt));
		}

		/** Multiplication
		 * @param other 	another number
		 * @return 			the product of the two numbers
		 */
		public FakeEncryptedInteger multiply(FakeEncryptedInteger other) {
			return new FakeEncryptedInteger (this.bigInt.multiply(other.bigInt));
		}

		/** @see java.lang.Comparable#compareTo(java.lang.Object) */
		public int compareTo(FakeEncryptedInteger o) {
			return this.bigInt.compareTo(o.bigInt);
		}
		
		/** @see java.lang.Object#equals(java.lang.Object) */
		@Override
		public boolean equals (Object o) {
			
			if (this == o) 
				return true;
			
			FakeEncryptedInteger o2 = null;
			try {
				o2 = (FakeEncryptedInteger) o;
			} catch (ClassCastException e) {
				return false;
			}
			
			return (this.bigInt.equals(o2.bigInt));
		}

		/** Subtraction
		 * @param other 	another number
		 * @return 			the difference of the two numbers
		 */
		public FakeEncryptedInteger subtract(FakeEncryptedInteger other) {
			return new FakeEncryptedInteger (this.bigInt.subtract(other.bigInt));
		}

		/** Addition
		 * @param other 	another number
		 * @return 			the sum of the two numbers
		 */
		public FakeEncryptedInteger add(FakeEncryptedInteger other) {
			return new FakeEncryptedInteger (this.bigInt.add(other.bigInt));
		}

		/** Modulo
		 * @param other 	the base of the modulo
		 * @return 			this mod base
		 */
		public FakeEncryptedInteger mod(FakeEncryptedInteger other) {
			return new FakeEncryptedInteger (this.bigInt.mod(other.bigInt));
		}
	}

	/** Used for serialization */
	private static final long serialVersionUID = 5205003208535233191L;

	/** Key used for FakeCryptoScheme */
	private final FakeEncryptedInteger key;
	
	/** CONSTANT 10 */
	private static final FakeEncryptedInteger TEN = new FakeEncryptedInteger("10");
	
	/** +INF is replaced with this very large number, so that we can track its successive encryptions/decryptions */
	private static FakeEncryptedInteger cleartextPlusInf = new FakeEncryptedInteger("100000000000000000"); 
																			  
	
	/** Lock to call before creating new key */
	private static final Object lock = new Object ();
	
	/** Initial value of the counter */ 
	private final static String startCounter = "10000"; 
	
	/** counter */
	private static FakeEncryptedInteger counter = new FakeEncryptedInteger(startCounter);
	
	/** Constructor
	 * @param params 	unused
	 */
	public FakeCryptoScheme (Element params) {
		
		synchronized(lock){
			key = counter;
			counter = key.multiply(TEN);
			
			while (key.compareTo(cleartextPlusInf) >= 0) 
				cleartextPlusInf = cleartextPlusInf.multiply(TEN);
		}
	}
	
	/** Resets the counter */
	public static synchronized void resetCounter () {
		counter = new FakeEncryptedInteger(startCounter);
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "FakeCryptoScheme \t key = " + this.key;
	}

	/** 
	 * @see CryptoScheme#decrypt(Serializable, Serializable) 
	 * @warning this decryption cheats. It removes all encryption, including encryption from other keys 
	 */
	public AddableInteger decrypt(FakeEncryptedInteger initialCypherText, FakeEncryptedInteger partialDecryption) {
		
		if (partialDecryption == null) 
			partialDecryption = initialCypherText;
		
		// If very large number, then it should decrypt to +INF
		if (partialDecryption.compareTo(cleartextPlusInf) >= 0) 
			return AddableInteger.PlusInfinity.PLUS_INF;
		
		else {						
			FakeEncryptedInteger temp = partialDecryption.mod(new FakeEncryptedInteger(startCounter)); 
			return new AddableInteger(temp.bigInt.intValue());
		}
	}

	/** @see CryptoScheme#encrypt(Serializable) */
	public FakeEncryptedInteger encrypt(AddableInteger cleartext) {
		
		FakeEncryptedInteger tmp = new FakeEncryptedInteger (cleartext.toString());
		
		// Replace +INF with a very large number
		if(cleartext.equals(AddableInteger.PlusInfinity.PLUS_INF))
			tmp = cleartextPlusInf;
		
		return tmp.add(key);
	}

	/** @see CryptoScheme#encrypt(Serializable, Serializable) */
	public FakeEncryptedInteger encrypt(AddableInteger cleartext, AddableInteger bound) {
		return this.encrypt(cleartext);
	}

	/** Partially decrypts the input cyphertext
	 * @param cyphertext 	the cyphertext to be partially decrypted
	 * @return the partially decrypted cyphertext
	 */
	public FakeEncryptedInteger partialDecrypt(FakeEncryptedInteger cyphertext) {
		//cypher = xxxxxxxxKxxx : x = random number, K the key (may be encrypted more than 1 time, but max 9 times for this particular key)
		
		//mod = Kxxxx
		FakeEncryptedInteger mod = cyphertext.mod(key.multiply(TEN));		
		//big = xxxxxxxx0000
		FakeEncryptedInteger big = cyphertext.subtract(mod);
		//small = xxx
		FakeEncryptedInteger small = mod.mod(key);
		//return xxxxxxxx0xxx
		return big.add(small);
	}

	/** @see CryptoScheme#reencrypt(Serializable) */
	public FakeEncryptedInteger reencrypt(FakeEncryptedInteger cyphertext) {
		return cyphertext.add(key);
	}

	/** @see CryptoScheme#partialDecrypt(java.io.Serializable, java.io.Serializable) */
	public FakeEncryptedInteger partialDecrypt(FakeEncryptedInteger initialCypherText, FakeEncryptedInteger partialDecryption) {
		if (partialDecryption == null) 
			partialDecryption = initialCypherText;
		return partialDecrypt(partialDecryption);
	}

	/** @see CryptoScheme#addPublicKeyShare(CryptoScheme.PublicKeyShare) */
	public void addPublicKeyShare(FakePublicKeyShare share) { }

	/** @see CryptoScheme#newPublicKeyShare() */
	public FakePublicKeyShare newPublicKeyShare() {
		return null;
	}

}
