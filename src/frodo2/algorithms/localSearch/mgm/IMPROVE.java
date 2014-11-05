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

package frodo2.algorithms.localSearch.mgm;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableConflicts;

/**
 * @author Brammert Ottens, Thomas Leaute
 * @param <U> type used for utility values
 * 
 */
public class IMPROVE <U extends Addable<U>> extends
		MessageWith3Payloads<String, String, AddableConflicts<U>> implements Externalizable {
	
	/** Default constructor used for externalization only */
	public IMPROVE () {
		super.type = MGM.IMPROVE_MSG_TYPE;
	}

	/**
	 * Constructor
	 * 
	 * @param sender				the sender of the message
	 * @param receiver				the receiver of the message
	 * @param improve				the value with which the variable can improve
	 */
	public IMPROVE(String sender, String receiver, AddableConflicts<U> improve) {
		super(MGM.IMPROVE_MSG_TYPE, sender, receiver, improve);
		assert improve != null;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getPayload1());
		out.writeObject(this.getPayload2());
		out.writeObject(this.getPayload3());
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((String) in.readObject());
		super.setPayload3((AddableConflicts<U>) in.readObject());
	}

	/**
	 * @author Brammert Ottens, 21 feb. 2011
	 * @return	the sender of the message
	 */
	public String getSender() {
		return this.getPayload1();
	}
	
	/**
	 * @author Brammert Ottens, 21 feb. 2011
	 * @return the receiver of the message
	 */
	public String getReceiver() {
		return this.getPayload2();
	}
	
	/**
	 * @author Brammert Ottens, 21 feb. 2011
	 * @return the value with which the variable can improve
	 */
	public AddableConflicts<U> getImprove() {
		return this.getPayload3();
	}
}
