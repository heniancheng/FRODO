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

package frodo2.algorithms.dpop;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A message containing a utility solution space 
 * @param <Val> the type used for variable values
 * @param <U> the type used for utility values
 */
public class UTILmsg <Val extends Addable<Val>, U extends Addable<U> > 
extends Message implements Externalizable {

	/** Used for serialization */
	private static final long serialVersionUID = -3118623960541912831L;

	/** The sender variable */
	private String sender;
	
	/** The sender agent */
	private String senderAgent;
	
	/** The destination variable */
	private String dest;
	
	/** The utility solution space */
	private UtilitySolutionSpace<Val, U> space;
	
	/** Empty constructor */
	public UTILmsg () {
		super.type = UTILpropagation.UTIL_MSG_TYPE;
	}

	/** Constructor
	 * @param senderVar 	the sender variable
	 * @param senderAgent 	the sender agent
	 * @param dest 			the destination variable
	 * @param space		 	the space
	 */
	public UTILmsg(String senderVar, String senderAgent, String dest, UtilitySolutionSpace<Val, U> space) {
		super(UTILpropagation.UTIL_MSG_TYPE);
		this.sender = senderVar;
		this.senderAgent = senderAgent;
		this.dest = dest;
		this.space = space;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.sender);
		out.writeObject(this.senderAgent);
		out.writeObject(this.dest);
		out.writeObject(this.space);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.sender = (String) in.readObject();
		this.senderAgent = (String) in.readObject();
		this.dest = (String) in.readObject();
		this.space = (UtilitySolutionSpace<Val, U>) in.readObject();
	}

	/** @return the sender variable */
	public String getSender () {
		return sender;
	}
	
	/** @return the sender agent */
	public String getSenderAgent () {
		return this.senderAgent;
	}
	
	/** @return the destination variable */
	public String getDestination () {
		return dest;
	}
	
	/** @return the space */
	public UtilitySolutionSpace<Val, U> getSpace () {
		return space;
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tsender: " + this.sender + "\n\tsender agent: " + this.senderAgent + 
				"\n\tdest: " + this.dest + "\n\tspace: " + this.space;
	}
	
	/** @see Object#equals(Object) */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals (Object o) {
		
		if (o == this)
			return true;
		
		if (!(o instanceof UTILmsg)) 
			return false;
		
		UTILmsg<Val, U> o2 = (UTILmsg<Val, U>) o;
		
		return this.dest.equals(o2.dest) && this.sender.equals(o2.sender) && 
				this.senderAgent.equals(o2.senderAgent) && this.space.equivalent(o2.space);
	}
	
	/** @see Message#fakeSerialize() */
	@Override
	public void fakeSerialize () {
		this.space = this.space.resolve();
	}
	
}