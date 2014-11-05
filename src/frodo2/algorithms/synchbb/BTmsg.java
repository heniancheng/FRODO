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

package frodo2.algorithms.synchbb;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;

/** Backtrack message in SynchBB */
public class BTmsg extends Message implements Externalizable {
	
	/** The destination variable */
	protected String dest;
	
	/** Empty constructor used for externalization */
	public BTmsg () {
		super (SynchBB.BACKTRACK_MSG_TYPE);
	}
	
	/** Constructor
	 * @param dest 	the destination variable
	 */
	public BTmsg (String dest) {
		super (SynchBB.BACKTRACK_MSG_TYPE);
		this.dest = dest;
	}

	/** Constructor
	 * @param type 	The type of the message
	 * @param dest 	the destination variable
	 */
	protected BTmsg (String type, String dest) {
		super (type);
		this.dest = dest;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.dest);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.dest = (String) in.readObject();
	}

	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tdest: " + this.dest;
	}
}