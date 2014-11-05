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
import java.io.Serializable;

import frodo2.communication.Message;

/** A message containing a proposal for the next variable to put in the order 
 * @param <S> type used for scores 
 */
public class ProposalMsg < S extends Comparable<S> & Serializable >extends Message implements Externalizable {
	
	/** The sender agent */
	transient String senderAgent;
	
	/** The ID of the connected component of the constraint graph */
	transient Comparable<?> componentID;
	
	/** The proposed variable to put next in the order */
	transient String nextVar;

	/** The score for the proposed variable */
	transient S score;

	/** Empty constructor used for externalization */
	public ProposalMsg () {
		super (LinearOrdering.PROPOSAL_MSG_TYPE);
	}
	
	/** Constructor
	 * @param senderAgent 	the sender agent
	 * @param componentID 	The ID of the connected component of the constraint graph
	 * @param nextVar 		The proposed variable to put next in the order
	 * @param score 		The score for the proposed variable
	 */
	public ProposalMsg (String senderAgent, Comparable<?> componentID, String nextVar, S score) {
		super (LinearOrdering.PROPOSAL_MSG_TYPE);
		this.senderAgent = senderAgent;
		this.componentID = componentID;
		this.nextVar = nextVar;
		this.score = score;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.senderAgent);
		out.writeObject(this.componentID);
		out.writeObject(this.nextVar);
		out.writeObject(this.score);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.senderAgent = (String) in.readObject();
		this.componentID = (S) in.readObject();
		this.nextVar = (String) in.readObject();
		this.score = (S) in.readObject();
	}

	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tcomponentID = " + this.componentID + "\n\tnextVar = " + this.nextVar + "\n\tscore = " + this.score + "\n\tsenderAgent = " + this.senderAgent;
	}
}