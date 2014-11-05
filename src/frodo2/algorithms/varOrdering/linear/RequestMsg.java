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

package frodo2.algorithms.varOrdering.linear;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;

/** A message requesting a proposal for the next variable to put in the order */
public class RequestMsg extends Message implements Externalizable {
	
	/** The sender agent */
	transient String senderAgent;
	
	/** The ID of the connected component of the constraint graph */
	transient Comparable<?> componentID;
	
	/** The latest variable added to the order */
	transient String latestVar;
	
	/** Empty constructor used for externalization */
	public RequestMsg () {
		super(LinearOrdering.REQUEST_MSG_TYPE);
	}
	
	/** Constructor
	 * @param senderAgent 	the sender agent
	 * @param componentID 	the ID of the connected component of the constraint graph
	 * @param latestVar 	the latest variable added to the order
	 */
	public RequestMsg (String senderAgent, Comparable<?> componentID, String latestVar) {
		super(LinearOrdering.REQUEST_MSG_TYPE);
		this.senderAgent = senderAgent;
		this.componentID = componentID;
		this.latestVar = latestVar;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.senderAgent);
		out.writeObject(this.componentID);
		out.writeObject(this.latestVar);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.senderAgent = (String) in.readObject();
		this.componentID = (Comparable<?>) in.readObject();
		this.latestVar = (String) in.readObject();
	}

	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tcomponentID = " + this.componentID + "\n\tlatestVar = " + this.latestVar + "\n\tsenderAgent = " + this.senderAgent;
	}
}