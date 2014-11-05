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

import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.crypto.CryptoScheme;

/**
 * Message used to share the public key shares of the CryptoScheme
 * @author Eric Zbinden, Thomas Leaute
 *
 * @param <K> class used for PublicKeyShare of cryptoScheme
 */
public class KeyShareMsg<K extends CryptoScheme.PublicKeyShare> extends MessageWith2Payloads<K, String> implements Externalizable {

	/**
	 * Constructor used for Externalization
	 */
	public KeyShareMsg(){
		this.type = CollaborativeDecryption.KEY_SHARE_TYPE;
	}
	
	/**
	 * Constructor
	 * @param key 		the key to share 
	 * @param sender 	a codename identifying the original sender of this message
	 */
	public KeyShareMsg(K key, String sender){
		this.type = CollaborativeDecryption.KEY_SHARE_TYPE;
		this.setPayload1(key);
		this.setPayload2(sender);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		this.setPayload1((K)in.readObject());
		this.setPayload2((String) in.readObject());
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getPayload1());
		out.writeObject(this.getPayload2());		
	}
	
	/** @return the key contained in this message */
	public K getKey(){
		return this.getPayload1();
	}
	
	/** @return the codename identifying the original sender of this message */
	public String getSender (){
		return this.getPayload2();
	}
	
	/** @see MessageWith2Payloads#toString() */
	@Override
	public String toString () {
		return "Message (" + this.type + ")\n\t key: " + this.getKey() + "\n\t sender: " + this.getSender();
	}
	
}
