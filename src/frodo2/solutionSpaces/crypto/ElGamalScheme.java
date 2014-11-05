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

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;

import org.jdom2.Element;

import frodo2.solutionSpaces.AddableInteger;

/** The ElGamal crypto scheme
 * @author Eric Zbinden, Thomas Leaute
 */
public class ElGamalScheme implements CryptoScheme<AddableInteger, ElGamalBigInteger, ElGamalScheme.ElGamalPublicKeyShare> {
	
	/** Creates a safe prime and a generator for use with the ElGamal scheme
	 * @param args 	minimum bit length, and prime certainty
	 * @note This implementation is an improvement over the one by David Bishop, "Introduction to Cryptography with Java Applets"
	 */
	public static void main (String[] args) {
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. Use the option -license to display the license.\n");
		
		//Parse minBitLength
		if(args.length >= 1){
			try{
				minBitLength = Integer.valueOf(args[0]);
			} catch (NumberFormatException e){
				System.err.println("Argument error. Minimum bit lenght must be a number higher than 64\n" +
								   "MinBitLength put to 512");
				minBitLength = 512;
			}
			if(minBitLength < 64) minBitLength = 64;

		} else minBitLength = 512;
		
		System.out.println("Bit length: " + minBitLength);

		//Parse certainty
		if(args.length >= 2){
			try{
				certainty = Integer.valueOf(args[1]);
			} catch (NumberFormatException e){
				System.err.println("Argument error. Certainty must be a number. Certainty put to 1000.");
				certainty = 1000;
			}
		} else certainty = 1000;
		
		getSafePrimeAndGenerator();	
	}
	
	/** A public key share */
	public static class ElGamalPublicKeyShare implements CryptoScheme.PublicKeyShare {
		
		/** Constructor
		 * @param y 	one share g^x mod p
		 */
		public ElGamalPublicKeyShare(BigInteger y) {
			this.y = y;
		}

		/** Used for serialization */
		private static final long serialVersionUID = 7630065135467464668L;
		
		/** A share of the overall y */
		private BigInteger y;
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			return y.toString();
		}
	}

	/** Used for serialization */
	private static final long serialVersionUID = -4409105662996332812L;
	
	/** Parameter used to create P. Represents the certainty that p is prime */
	private static int certainty;
	
	/** Parameter used to create P. Represents the minimum bit length of p */
	private static int minBitLength;
	
	/** Prime number */
	private final BigInteger p;
	
	/** p - 3 */
	private final BigInteger pMinus3;
	
	/** The number of bits of p - 3 */
	private final int nbrBitsOfPminus3;
	
	/** Number such that gcd(g,p) = 1 */
	private final BigInteger g;
	
	/** The overall public key, as the product of all the (g^x mod p) shares */
	private BigInteger y;
	
	/** private key in [1, p-2] */
	private BigInteger x;
	
	/** The default size of the ElGamal vector*/
	private final int size;
	
	/** Random generator */
	private final SecureRandom rand;
	
	/** Constructor
	 * @param params 	the publicly known parameters
	 */
	public ElGamalScheme (Element params) {
		
		p = new BigInteger(params.getAttributeValue("modulus"));
		this.pMinus3 = p.subtract(BigInteger.valueOf(3));
		this.nbrBitsOfPminus3 = pMinus3.bitLength();
				
		g = new BigInteger(params.getAttributeValue("generator"));
		size = Integer.valueOf(params.getAttributeValue("infinity"));

		rand = new SecureRandom();

		// Initialize the private/public key pair to the trivial one; 
		// this will change after the first call to newPublicKeyShare(). 
		this.x = BigInteger.ZERO;
		this.y = BigInteger.ONE;
	}
	
	/**
	 * @return a random BigInteger in the range of [1,p-2]
	 */
	private BigInteger randomBigInteger(){
		
		BigInteger out = new BigInteger (this.nbrBitsOfPminus3, this.rand);
		while (out.compareTo(this.pMinus3) >= 0)
			out = new BigInteger (this.nbrBitsOfPminus3, this.rand);
		
		return out.add(BigInteger.ONE);
	}

	/** @see CryptoScheme#decrypt(java.io.Serializable, java.io.Serializable) */
	public AddableInteger decrypt(ElGamalBigInteger initialCypherText, ElGamalBigInteger partialDecryption) {
		
		final BigInteger[][] initVector = initialCypherText.vector;
		final int inputSize = initVector.length;

		// Decrypt each pair until it decrypts to something other than ONE
		if (partialDecryption == null){ //first decryption. Bi' = Bi^x mod p 
			for(int i =0; i<inputSize;i++){
				BigInteger[] initPair = initVector[i];
				
				BigInteger newBi = initPair[1].modPow(x, p);
				BigInteger decrypted = initPair[0].multiply(newBi.modInverse(p)).mod(p);
				
				if (! decrypted.equals(BigInteger.ONE)) {
					assert this.isPowerOf2(decrypted) : decrypted.toString() + " is not a power of 2";
					return new AddableInteger (i);
				}
			}
			
		} else { // a second decryption. Bi' = ( Bi * Bi_initial^x ) mod p 
			final BigInteger[][] partialVector = partialDecryption.vector;		
			
			for(int i =0; i<inputSize;i++){
				BigInteger[] partialPair = partialVector[i];
				BigInteger initialBi = initVector[i][1];
				
				BigInteger newBi = partialPair[1].multiply(initialBi.modPow(x, p)).mod(p);
				BigInteger decrypted = partialPair[0].multiply(newBi.modInverse(p)).mod(p);
				
				if (! decrypted.equals(BigInteger.ONE)) {
					assert this.isPowerOf2(decrypted) : decrypted.toString() + " is not a power of 2";
					return new AddableInteger (i);
				}
			}
		}		
		// The vector is full of ONEs
		return AddableInteger.PlusInfinity.PLUS_INF;
	}
	
	/** Tests whether a number is a power of 2
	 * @param nbr 	the number to test
	 * @return whether the input number is a power of 2
	 */
	private boolean isPowerOf2 (BigInteger nbr) {
		
		final BigInteger TWO = BigInteger.ONE.add(BigInteger.ONE);
		
		while (! nbr.equals(BigInteger.ONE)) {
			BigInteger[] pair = nbr.divideAndRemainder(TWO);
			if (! pair[1].equals(BigInteger.ZERO)) 
				return false;
			nbr = pair[0];
		}
		
		return true;
	}

	/** 
	 * @see CryptoScheme#encrypt(java.io.Serializable) 
	 * @warning THE OUTPUT IS NOT REALLY ENCRYPTED! One has to call reencrypt() to encrypt it. 
	 */
	public ElGamalBigInteger encrypt(AddableInteger cleartext) {
		return new ElGamalBigInteger(cleartext, this.size);
	}

	/** 
	 * @see CryptoScheme#encrypt(java.io.Serializable, java.io.Serializable) 
	 * @note \a bound + 1 = \c infinity
	 */
	public ElGamalBigInteger encrypt(AddableInteger cleartext, AddableInteger bound) {
		int nbrValues = (bound == AddableInteger.PlusInfinity.PLUS_INF ? 2 : bound.intValue() + 2);
		assert nbrValues > 1 : nbrValues + " <= 1";
		return new ElGamalBigInteger(cleartext, nbrValues - 1);
	}
	
	/** @see CryptoScheme#reencrypt(java.io.Serializable) */
	public ElGamalBigInteger reencrypt(ElGamalBigInteger cyphertext) {
		
		final BigInteger[][] inVector = cyphertext.vector;
		final int inputSize = inVector.length;
		BigInteger r = randomBigInteger();
		BigInteger[][] encrypted = new BigInteger[inputSize][];
		
		for(int i=0; i<inputSize;i++){
			BigInteger[] pair = inVector[i];		
			
			encrypted[i] = new BigInteger[] { pair[0].multiply(y.modPow(r, p)).mod(p), 
											  pair[1].multiply(g.modPow(r, p)).mod(p) };
		}
		
		return new ElGamalBigInteger(encrypted);
	}

	/** @see CryptoScheme#partialDecrypt(java.io.Serializable, java.io.Serializable) */
	public ElGamalBigInteger partialDecrypt(ElGamalBigInteger initialCypherText, ElGamalBigInteger partialDecryption) {
		
		final BigInteger[][] initVector = initialCypherText.vector;		
		final int inputSize = initVector.length;
		BigInteger[][] decrypted = new BigInteger[inputSize][];
		
		if (partialDecryption == null){ //first partial decryption. Bi' = Bi^x mod p 
			for(int i =0; i<inputSize;i++){
				
				BigInteger[] initPair = initVector[i];
				decrypted[i] = new BigInteger[] { initPair[0], initPair[1].modPow(x, p) };
			}
			
		} else {
			
			final BigInteger[][] partialVector = partialDecryption.vector;
			
			for(int i =0; i<inputSize;i++){ // a second partial decryption. Bi' = ( Bi * Bi_initial^x ) mod p 
				
				BigInteger[] partialPair = partialVector[i];
				BigInteger initialBi = initVector[i][1];
				decrypted[i] = new BigInteger[] { partialPair[0], partialPair[1].multiply(initialBi.modPow(x, p)).mod(p) };
			}
		}
		
		return new ElGamalBigInteger(decrypted);
	}

	/** @see CryptoScheme#addPublicKeyShare(CryptoScheme.PublicKeyShare) */
	public void addPublicKeyShare(ElGamalPublicKeyShare share) {
		this.y = this.y.multiply(share.y).mod(p);
	}

	/** @see CryptoScheme#newPublicKeyShare() */
	public ElGamalPublicKeyShare newPublicKeyShare() {
		
		// Create a new private key and merge it with the current one
		BigInteger newX = randomBigInteger();
		this.x = this.x.add(newX);
		
		// Create a corresponding public key and merge it with the current one
		BigInteger newY = g.modPow(newX, p);
		this.y = this.y.multiply(newY).mod(p);
		
		return new ElGamalPublicKeyShare (newY);
	}
	
	/**
	 * Return a safe prime number
	 * @param r a large random number
	 * @param t a large prime number
	 * @return a prime number in the form 2rt+1
	 * @note r might be modified in a way to find p
	 */
	private static BigInteger getSafePrime(BigInteger r, BigInteger t){
		
	    BigInteger two = BigInteger.valueOf(2);
		BigInteger prime;
		
		//Generate a safe prime of the form 2rt+1 where t is prime
	    //p is the first prime in the sequence 2rt+1, 2*2rt+1, 2*3rt+1,...
		do {
	    	r = r.add(BigInteger.ONE);
	        prime = two.multiply(r).multiply(t).add(BigInteger.ONE);
	         
	      } while (!prime.isProbablePrime(certainty));
		
		return prime;
	}
	
	/** Generate a safe prime, its generator and print them out */
	private static void getSafePrimeAndGenerator(){
		
	    BigInteger r = BigInteger.valueOf(0x7fffffff); // 2 147 483 647
	    SecureRandom rand = new SecureRandom();
	   
	    //T a prime number
	    BigInteger t = new BigInteger(minBitLength-30,certainty, rand); //why 30 ? Because 2r = 2^30 - 1
	    //P a safe prime
	    BigInteger prime = getSafePrime(r,t);
	    //Prime factors of prime-1
		ArrayList<BigInteger> factors = getFactors(r,t);
	    //Generate generator
	    BigInteger generator = getGenerator(factors, prime, rand);
	    
	    System.out.println("Prime number p: "+prime+"\n"+
	    				   "Associated generator g: "+generator);
	      
	}
	
	/** Returns the list of prime factors of 2rt
	 * @param r a large random number
	 * @param t a prime number
	 * @return the list of factor of 2rt
	 */
	private static ArrayList<BigInteger> getFactors(BigInteger r, BigInteger t){
		
		BigInteger two = BigInteger.valueOf(2);
		ArrayList<BigInteger> factors = new ArrayList<BigInteger>();
		
	    factors.add(t);
	    factors.add(two);
	    
	    if (r.isProbablePrime(10)) factors.add(r); //Put r in factors if r is prime
	    else {
	    	while (r.mod(two).equals(BigInteger.ZERO)) r=r.divide(two); // make r odd
	    	
	    	//Search for all factors of r smaller than square root of r
	    	BigInteger divisor = BigInteger.valueOf(3);
	    	BigInteger square = divisor.multiply(divisor);
	                  
	    	while (square.compareTo(r)<=0) {  //Test divisor until squareRoot of r
				
				//if mod(divisor) == 0, then divisor divides r. 
				if (r.mod(divisor).equals(BigInteger.ZERO)) {
					factors.add(divisor);
					while (r.mod(divisor).equals(BigInteger.ZERO)) r=r.divide(divisor); //remove all powers of this divisor
				}
				
				divisor=divisor.add(two); //next potential divisor (+2 because no need to check even numbers)
				square=divisor.multiply(divisor);
	    	}
		}
	    
	    return factors;
	}
	
	/**
	 * Return a generator
	 * @param factors the list of the factor of the safe prime minus one (2rt-1)
	 * @param prime a safe prime 
	 * @param rand an instance of SecureRandom
	 * @return a generator for this safe prime
	 */
	private static BigInteger getGenerator(ArrayList<BigInteger> factors, BigInteger prime, SecureRandom rand){
		
	    int bitLength = prime.bitLength()-1;  
	    BigInteger pMinusOne = prime.subtract(BigInteger.ONE);
		BigInteger z, lnr; //Z = (p-1)/factor(i). LNR = x.modPow(z,p)
		       
	    BigInteger x = new BigInteger(bitLength,rand); //Pick a random integer x smaller than the safe prime p
	    int size = factors.size();
	         
		// Loop while x is not a generator
		ext: while (true) {
			
			// Test all prime factors
			for (int i=0;i<size;i++) {
				
				z = pMinusOne.divide(factors.get(i));
				lnr = x.modPow(z,prime);
				
				//If == 1, x is not a generator
				if (lnr.equals(BigInteger.ONE)) { 
					x = new BigInteger(bitLength,rand); //Pick a random integer x smaller than the safe prime p
					continue ext; }
			}
			
			//If we are here, this mean x is a generator !
			return x;
		}
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "ElGamalScheme (public key: " + this.y + ")";
	}
	
}
