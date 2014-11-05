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
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.communication.MessageWith3Payloads;

/** Message sent by EncryptedUTIL to transmit codeNames 
 * @param <V> Class used for value */
public class VarCodenameMsg<V>	extends MessageWith3Payloads < String[], String , V[][] > implements Externalizable {
	
	/** Used for serialization */
	private static final long serialVersionUID = -893201749257599489L;
	
	/** Constructor
	 * @param sender 			the sender of this message
	 * @param receiver 			the receiver of this message
	 * @param codeName 			the code name of the sender
	 * @param domains		 	the domain of the sender variable, in cleartext AND the obfuscated domain
	 */
	public VarCodenameMsg(String sender, String receiver, String codeName,
			               V[][] domains){
		super(EncryptedUTIL.CODENAME_TYPE, new String[]{sender,receiver}, codeName, domains);
	}
	
	/**
	 * Empty constructor used for Externalization
	 */
	public VarCodenameMsg(){
		this.type = EncryptedUTIL.CODENAME_TYPE;
	}
	
	 /** @return the sender of this message  */
	public String getSender(){
		return this.getPayload1()[0];
	}
	
	/** @return the receiver of this message */
	public String getReceiver(){
		return this.getPayload1()[1];
	}
	
	/** @return the code name of the sender */
	public String getCodeName(){
		return this.getPayload2();
	}
	
	/** @return the obfuscated domain */
	public V[] getOfuscatedDomain(){
		return this.getPayload3()[1];
	}
	
	/** @return the cleartext domain */
	public V[] getCleartextDomain(){
		return this.getPayload3()[0];
	}
	
	/** @return both cleartext and obfuscated domain. Clear in [0] and obfuscated in [1] */
	public V[][] getDomains(){
		return this.getPayload3();
	}
	
	/**
	 * @see frodo2.communication.MessageWith3Payloads#toString()
	 */
	public String toString(){
		return "Message(type = `" + super.type + "')"
		  + "\n\tsender: " + getSender()
		  + "\n\tdest: " + getReceiver()
	      + "\n\tcodeName: " + getCodeName()
	      + "\n\tcleartext domain: " + Arrays.asList(this.getCleartextDomain())
	      + "\n\tobfuscated domain: " + Arrays.asList(this.getOfuscatedDomain());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked" )
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		
		String sender = (String) in.readObject();
		String receiver = (String) in.readObject();
		this.setPayload1(new String[]{sender,receiver});
		this.setPayload2((String)in.readObject());
		
		int size = in.readInt();
		
		V v = (V) in.readObject();
		V[] vv = (V[]) Array.newInstance(v.getClass(), size);
		V[][] array = (V[][]) Array.newInstance(vv.getClass(), 2) ;
		array[0] = vv;
		array[1] = (V[]) Array.newInstance(v.getClass(), size);
		array[0][0] = v; //first object of clearText domain
		if(size > 1)
			for(int i=1;i<size;i++){ //clearText domain
				array[0][i] = (V) in.readObject();
			}
		for(int i=0;i<size;i++){ //Obfuscated domain
			array[1][i] = (V) in.readObject();
		}
		this.setPayload3(array);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getSender());
		out.writeObject(this.getReceiver());
		out.writeObject(this.getCodeName());
		out.writeInt(this.getCleartextDomain().length);
		for(V val : this.getCleartextDomain()){
			out.writeObject(val);
		}
		for(V val : this.getOfuscatedDomain()){
			out.writeObject(val);
		}
	}
}