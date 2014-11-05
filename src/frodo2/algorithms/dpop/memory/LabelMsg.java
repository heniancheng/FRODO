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

package frodo2.algorithms.dpop.memory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import frodo2.communication.MessageWith4Payloads;
import frodo2.solutionSpaces.Addable;

/** The class of the LABEL messages sent by MB-DPOP's labeling phase
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 */
public class LabelMsg < V extends Addable<V> > extends MessageWith4Payloads< String, String, HashMap<String, V[]>, HashMap<String, V[]> > {
	
	/** The type of the message */
	public static final String LABEL_MSG_TYPE = "LabelMsg";
	
	/** Default constructor used for externalization */
	public LabelMsg () {
		super.type = LABEL_MSG_TYPE;
	}
	
	/** Constructor
	 * @param sender 	the sender variable
	 * @param dest 		the destination variable
	 * @param sep 		the separator of the sender variable, with the corresponding domains
	 * @param ccs 		cycle-cutset variables and their respective domains
	 */
	public LabelMsg (String sender, String dest, HashMap<String, V[]> sep, HashMap<String, V[]> ccs) {
		super (LABEL_MSG_TYPE, sender, dest, sep, ccs);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(this.getSender());
		out.writeObject(this.getDest());
		
		// Serialize the separator
		HashMap<String, V[]> map = this.getSep();
		assert map.size() < Long.MAX_VALUE : "Separator too large: " + map.entrySet();
		short mapSize = (short) map.size();
		out.writeShort(mapSize); // nbr of variables
		if (mapSize > 0) {
			for (Iterator< Map.Entry< String, V[] > > iter = map.entrySet().iterator(); iter.hasNext(); ) {
				Map.Entry< String, V[] > entry = iter.next();
				
				out.writeObject(entry.getKey()); // name of the variable
				
				V[] dom = entry.getValue();
				assert dom.length < Long.MAX_VALUE : "Domain too large for variable " + entry.getKey() + ": " + Arrays.toString(dom);
				out.writeShort(dom.length); // domain size
				
				for (V val : dom) // domain values
					out.writeObject(val);
			}
		}
		
		// Serialize the CCs
		Set<String> sep = map.keySet();
		map = this.getCCs();
		assert map.size() < Long.MAX_VALUE : "Set of CC nodes too large: " + map.entrySet();
		mapSize = (short) map.size();
		out.writeShort(mapSize); // nbr of CCs
		if (mapSize > 0) {
			for (Iterator< Map.Entry< String, V[] > > iter = map.entrySet().iterator(); iter.hasNext(); ) {
				Map.Entry< String, V[] > entry = iter.next();
				
				String var = entry.getKey();
				out.writeObject(var); // name of CC
				
				// No need to re-send the domain if the CC is already in the separator
				if (sep.contains(var)) 
					out.writeShort(-1); // fake domain size
				
				else {
					V[] dom = entry.getValue();
					assert dom.length < Long.MAX_VALUE : "Domain too large for variable " + entry.getKey() + ": " + Arrays.toString(dom);
					out.writeShort(dom.length); // domain size

					for (V val : dom) // domain values
						out.writeObject(val);
				}
			}
		}
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		super.setPayload1((String) in.readObject());
		super.setPayload2((String) in.readObject());
		
		// Parse the separator
		short size = in.readShort();
		HashMap<String, V[]> sep = new HashMap<String, V[]> (size);
		super.setPayload3(sep);

		// Process the first variable separately in order to obtain the class of V
		String var = (String) in.readObject(); // name of first variable
		short domSize = in.readShort(); // domain size
		V firstVal = (V) in.readObject(); // first domain value
		Class<V> classOfV = (Class<V>) firstVal.getClass();
		V[] dom = (V[]) Array.newInstance(classOfV, domSize);
		sep.put(var, dom);
		dom[0] = firstVal;
		for (int i = 1; i < domSize; i++) // remaining domain values
			dom[i] = (V) in.readObject();

		// Parse the remaining variables
		while (--size > 0) {
			var = (String) in.readObject(); // name of CC
			domSize = in.readShort(); // domain size
			dom = (V[]) Array.newInstance(classOfV, domSize);
			sep.put(var, dom);
			for (int i = 0; i < domSize; i++) // domain values
				dom[i] = (V) in.readObject();
		}

		
		// Parse the CCs
		size = in.readShort();
		HashMap<String, V[]> ccs = new HashMap<String, V[]> (size);
		super.setPayload4(ccs);
		while (--size >= 0) {
			var = (String) in.readObject(); // name of CC
			domSize = in.readShort(); // domain size
			
			if (domSize < 0) // domain has been sent with the separator
				dom = sep.get(var);
			else {
				dom = (V[]) Array.newInstance(classOfV, domSize);
				for (int i = 0; i < domSize; i++) // domain values
					dom[i] = (V) in.readObject();
			}
			ccs.put(var, dom);
		}
	}
	
	/** @return the sender variable */
	public String getSender () {
		return super.getPayload1();
	}
	
	/** @return the destination variable */
	public String getDest () {
		return super.getPayload2();
	}
	
	/** @return the separator of the sender variable, with the corresponding domains */
	public HashMap<String, V[]> getSep () {
		return super.getPayload3();
	}
	
	/** @return cycle-cutset variables and their respective domains */
	public HashMap<String, V[]> getCCs () {
		return super.getPayload4();
	}
	
	/** @see MessageWith4Payloads#toString() */
	@Override
	public String toString () {
		return "Message(type = `" + super.type + "')\n\t dest = " + this.getDest() + "\n\t sep = " + this.getSep() + "\n\t CCs = " + this.getCCs().keySet();
	}

}
