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

import java.io.Serializable;

/** Defines a way to encrypt and decrypt information
 * @author Thomas Leaute
 * @param <C> the type of cleartext information
 * @param <E> the type of encrypted information
 * @param <K> the class used for public key shares
 */
public interface CryptoScheme <C extends Serializable, E extends Serializable, K extends CryptoScheme.PublicKeyShare> extends Serializable {
	
	/** One share of the overall public key */
	public static interface PublicKeyShare extends Serializable { }
	
	/** @return a new share of the public key */
	public K newPublicKeyShare ();
	
	/** Records another share of the public key
	 * @param share 	another share of the public key
	 */
	public void addPublicKeyShare (K share);

	/** Encrypts a cleartext
	 * @param cleartext 	the cleartext
	 * @return an encryption of the cleartext
	 */
	public E encrypt (C cleartext);
	
	/** Encrypts a cleartext
	 * @param cleartext 	the cleartext
	 * @param bound 		bound used to limit the number of values allowed for a cleartext
	 * @return an encryption of the cleartext
	 */
	public E encrypt (C cleartext, C bound);
	
	/** Re-encrypts a cyphertext
	 * @param cyphertext 	the cyphertext
	 * @return a re-encryption of the cyphertext
	 * @warning Re-encrypting with the same key as before must not mean we then need to decrypt twice!
	 */
	public E reencrypt (E cyphertext);
	
	/** Partially decrypts a cyphertext
	 * @param initialCypherText 	the initial cyphertext
	 * @param partialDecryption 	the partially decrypted cyphertext (\c null if this is the first decryption)
	 * @return a partial decryption of the cyphertext
	 */
	public E partialDecrypt (E initialCypherText, E partialDecryption);
	
	/** Decrypts a cyphertext
	 * @param initialCypherText 	the initial cyphertext
	 * @param partialDecryption 	the partially decrypted cyphertext (\c null if this is the first decryption)
	 * @return a decryption of the cyphertext
	 */
	public C decrypt (E initialCypherText, E partialDecryption);
	
}
