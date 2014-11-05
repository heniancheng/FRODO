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

import frodo2.communication.MessageWith4Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.crypto.AddableBigInteger;

/** Message sent by VariableObfuscation to transmit codeNames 
 * @param <V> the type used for variable values
 * @author Eric Zbinden, Thomas Leaute
 */
public class CodeNameMsg < V extends Addable<V> > extends MessageWith4Payloads < String[], String , V[], V[] > implements Externalizable {
	
	/** List of obfuscation keys of the utility */
	private AddableBigInteger[] obfuscatedUtility;
	
	/** Used for externalization only */
	public CodeNameMsg () {
		super.type = VariableObfuscation.CODE_NAME_TYPE;
	}

	/** Constructor
	 * @param sender 			the sender of this message
	 * @param receiver 			the receiver of this message
	 * @param codeName 			the code name of the sender
	 * @param cleartextDomain 	the domain of the sender variable, in cleartext
	 * @param obfuscatedDomain 	the obfuscated domain
	 * @param obfuscatedUtility the obfuscation keys for the utility
	 */
	public CodeNameMsg(String sender, String receiver, String codeName,
			               V[] cleartextDomain, V[] obfuscatedDomain,
			               AddableBigInteger[] obfuscatedUtility){
		super(VariableObfuscation.CODE_NAME_TYPE, new String[]{sender,receiver}, codeName, cleartextDomain, obfuscatedDomain);
		
		this.obfuscatedUtility = obfuscatedUtility;
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		// Write sender and receiver
		String[] fromAndTo = super.getPayload1();
		out.writeObject(fromAndTo[0]);
		out.writeObject(fromAndTo[1]);
		
		// Write codename
		out.writeObject(super.getPayload2());
		
		// Write domains
		V[] dom = super.getPayload3();
		out.writeInt(dom.length);
		for (int i = dom.length - 1; i >= 0; i--) 
			out.writeObject(dom[i]);
		dom = super.getPayload4();
		out.writeInt(dom.length);
		for (int i = dom.length - 1; i >= 0; i--) 
			out.writeObject(dom[i]);
		
		// Write utility obfuscation key
		out.writeInt(this.obfuscatedUtility.length);
		for (int i = this.obfuscatedUtility.length - 1; i >= 0; i--) 
			out.writeObject(this.obfuscatedUtility[i]);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		// Read sender and receiver
		String[] fromAndTo = new String [2];
		fromAndTo[0] = (String) in.readObject();
		fromAndTo[1] = (String) in.readObject();
		super.setPayload1(fromAndTo);
		
		// Read codename
		super.setPayload2((String) in.readObject());
		
		// Read cleartext domain
		int size = in.readInt();
		V firstDomLastVal = (V) in.readObject();
		V[] clearTextDom = (V[]) Array.newInstance(firstDomLastVal.getClass(), size--);
		clearTextDom[size--] = firstDomLastVal;
		for (; size >= 0; size--) 
			clearTextDom[size] = (V) in.readObject();
		super.setPayload3(clearTextDom);
		
		// Read obfuscated domain
		size = in.readInt();
		V[] obfuscatedDom = (V[]) Array.newInstance(firstDomLastVal.getClass(), size--);
		for (; size >= 0; size--) 
			obfuscatedDom[size] = (V) in.readObject();
		super.setPayload4(obfuscatedDom);
		
		// Write utility obfuscation key
		size = in.readInt();
		this.obfuscatedUtility = new AddableBigInteger [size--];
		for (; size >= 0; size--) 
			this.obfuscatedUtility[size] = (AddableBigInteger) in.readObject();
	}

	/** @return the tab of all obfuscation keys for the utility */
	public AddableBigInteger[] getOfuscatedUtility(){
		return obfuscatedUtility;
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
		return this.getPayload4();
	}
	
	/** @return the cleartext domain */
	public V[] getCleartextDomain(){
		return this.getPayload3();
	}
	
	/** @see MessageWith4Payloads#toString() */
	public String toString(){
		return "Message(type = `" + super.type + "')"
		  + "\n\tsender: " + getSender()
		  + "\n\tdest: " + getReceiver()
	      + "\n\tcodeName: " + getCodeName()
	      + "\n\tcleartext domain: " + Arrays.asList(this.getCleartextDomain())
	      + "\n\tobfuscated domain: " + Arrays.asList(this.getOfuscatedDomain())
		  + "\n\tobfuscated utility: " +Arrays.asList(this.obfuscatedUtility);
	}
}