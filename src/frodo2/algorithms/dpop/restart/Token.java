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

package frodo2.algorithms.dpop.restart;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;

/** Message sent and received by UTILreuse module during warm restarts */
public class Token extends Message{

	/** The sender variable */
	private String sender;
	
	/** Empty constructor */
	public Token () {
		super.type = UTILreuse.REUSE_MSG_TYPE;
	}

	/** @see Message#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.sender);
	}

	/** @see Message#readExternal(java.io.ObjectInput) */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.sender = (String) in.readObject();
	}

	/** Constructor
	 * @param sender 		the sender variable
	 */
	public Token(String sender) {
		super(UTILreuse.REUSE_MSG_TYPE);
		this.sender = sender;
	}
	

	/** @return the sender variable */
	public String getSender () {
		return sender;
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tsender: " + this.sender;
	}
}