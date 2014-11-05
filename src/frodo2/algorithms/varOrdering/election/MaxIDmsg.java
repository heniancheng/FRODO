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

package frodo2.algorithms.varOrdering.election;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import frodo2.communication.MessageWith2Payloads;

/** Leader election message containing two payloads: the sender agent, and its current maxID 
 * @param <T> the type used for agent IDs
 */
public class MaxIDmsg < T extends Comparable <T> & Serializable > extends MessageWith2Payloads <String, T> implements Externalizable {
	
	/** Used for serialization */
	private static final long serialVersionUID = -3182721919850385243L;
	
	/** Empty constructor */
	public MaxIDmsg () {
		super();
	}

	/** Constructor 
	 * @param sender 	the ID of the sender agent
	 * @param maxID 	the current maxID
	 */
	public MaxIDmsg (String sender, T maxID) {
		super (LeaderElectionMaxID.LE_MSG_TYPE, sender, maxID);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getSender());
		out.writeObject(this.getMaxID());
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.setPayload1((String) in.readObject());
		this.setPayload2((T) in.readObject());
		
		super.type = LeaderElectionMaxID.LE_MSG_TYPE;
	}

	/** @return the ID of the sender agent */
	public String getSender () {
		return getPayload1();
	}
	
	/** @return the current maxID */
	public T getMaxID () {
		return getPayload2();
	}
	
	/** @see frodo2.communication.Message#toString() */
	public String toString () {
		return "Message " + LeaderElectionMaxID.LE_MSG_TYPE + "\n\tsender: " + getSender() + "\n\tmaxID: " + getMaxID();
	}
}