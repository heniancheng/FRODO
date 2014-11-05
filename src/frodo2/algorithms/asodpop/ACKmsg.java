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

package frodo2.algorithms.asodpop;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWithPayload;

/**
 * This message is used to ask a child for new information
 * @author brammert
 */
public class ACKmsg extends MessageWithPayload<String> implements Externalizable {

	/** Used for serialization */
	private static final long serialVersionUID = 6800731265392941706L;

	/** Empty constructor */
	public ACKmsg () {
		super.type = ASODPOP.ACK_MSG_TYPE;
	}

	/**
	 * A constructor
	 * @param receiver		The recipient of the message
	 */
	public ACKmsg(String receiver) {
		super(ASODPOP.ACK_MSG_TYPE, receiver);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload((String) in.readObject());
	}

	/**
	 * Returns the receiver of the message
	 * @return receiver
	 */
	public String getReceiver() {
		return this.getPayload();
	}
}