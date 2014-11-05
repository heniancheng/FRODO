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

package frodo2.communication;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/** Base class used for messages exchanged between queues
 * @author Thomas Leaute 
 * @todo Add the sender agent as a non-serialized member. 
 */
public class Message implements Externalizable {
	
	/** The type of this message */
	protected String type;
	
	/** Constructor 
	 * @param type type of this message
	 */
	public Message(String type) {
		this.type = type;
	}
	
	/** Empty constructor */
	public Message () { }
	
	/** @return the type of this message */
	public String getType() {
		return type;
	}
	
	/** @see java.lang.Object#toString() */
	public String toString () {
		return "Message(type = `" + type + "')";
	}
	
	/** Pretends to serialize the message 
	 * 
	 * This is useful for instance if the message contains a UtilitySolutionSpace on which resolve() must be called when serializing. 
	 */
	public void fakeSerialize () { }

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.type);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.type = (String) in.readObject();
	}
	
}
