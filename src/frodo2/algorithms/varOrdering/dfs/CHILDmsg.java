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

package frodo2.algorithms.varOrdering.dfs;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import frodo2.communication.MessageWith3Payloads;

/** A token indicating that the destination variable is a child of the sender variable */
public class CHILDmsg extends MessageWith3Payloads <String, String, Serializable> {
	
	/** Used for serialization */
	private static final long serialVersionUID = -6158434988997871871L;
	
	/** Empty constructor */
	public CHILDmsg () {
		super.type = DFSgeneration.CHILD_MSG_TYPE;
	}

	/** Constructor 
	 * @param sender 	sender variable
	 * @param dest 		recipient variable
	 * @param rootID 	the root ID
	 */
	public CHILDmsg (String sender, String dest, Serializable rootID) {
		super (DFSgeneration.CHILD_MSG_TYPE, sender, dest, rootID);
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getSender());
		out.writeObject(this.getDest());
		out.writeObject(this.getRootID());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((String) in.readObject());
		super.setPayload3((Serializable) in.readObject());
	}
	
	/** @return the root ID */
	public Serializable getRootID () {
		return super.getPayload3();
	}

	/** @return the sender variable */
	public String getSender () {
		return getPayload1();
	}
	
	/** @return the recipient variable */
	public String getDest () {
		return getPayload2();
	}
}