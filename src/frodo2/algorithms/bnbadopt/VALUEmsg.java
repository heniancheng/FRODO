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

package frodo2.algorithms.bnbadopt;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;

/**
 * The message used to send a VALUE message to a variable's
 * pseudochildren
 * 
 * @param <Val> the type used for variable values
 * @param <U> the type used for utility values
 */
public class VALUEmsg<Val extends Addable<Val>, U extends Addable<U>>
		extends Message implements Externalizable {

	/** The sending variable */
	private String sender;

	/** The receiving variable */
	String receiver;

	/** The context in which this message was created */
	private Val value;

	/** The threshold belonging to this value */
	private U threshold;
	
	/** the mark to the current assignment */
	private long ID;
	
	/** Empty constructor */
	public VALUEmsg () {
		super (BNBADOPT.Original.VALUE_MSG_TYPE);
	}

	/**
	 * Constructor
	 * 
	 * @param sender the sender variable
	 * @param receiver the recipient variable
	 * @param value the context
	 * @param threshold the threshold
	 */
	public VALUEmsg(String sender, String receiver, Val value, U threshold,long ID) {
		super(BNBADOPT.Original.VALUE_MSG_TYPE);
		this.sender = sender;
		this.receiver = receiver;
		this.value = value;
		this.threshold = threshold;
		this.ID=ID;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.sender);
		out.writeObject(this.receiver);
		out.writeObject(this.value);
		out.writeObject(this.threshold);
		out.writeObject(this.ID);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.sender = (String) in.readObject();
		this.receiver = (String) in.readObject();
		this.value = (Val) in.readObject();
		this.threshold = (U) in.readObject();
		this.ID=(int)in.readObject();
	}

	/** Used for serialization */
	private static final long serialVersionUID = 2153239219905600645L;

	/** @see java.lang.Object#equals(java.lang.Object) */
	@SuppressWarnings("unchecked")
	public boolean equals(Object msg) {
		if (msg == null)
			return false;
		VALUEmsg<Val, U> msgCast = (VALUEmsg<Val, U>) msg;
		U threshold2 = msgCast.threshold;
		return sender.equals(msgCast.sender)
				&& receiver.equals(msgCast.receiver)
				&& value.equals(msgCast.value)
				&& (threshold == threshold2 || (threshold != null && threshold.equals(threshold2)))
				&& (ID==msgCast.ID);
	}

	/**
	 * Getter function
	 * 
	 * @return the variable that sent this message
	 */
	public String getSender() {
		return sender;
	}

	/**
	 * Getter function
	 * 
	 * @return the variable that is to receive this message
	 */
	public String getReceiver() {
		return receiver;
	}

	/**
	 * Getter function
	 * 
	 * @return the value
	 */
	public Val getValue() {
		return value;
	}

	/**
	 * Getter function
	 * 
	 * @return the threshold
	 */
	public U getThreshold() {
		return threshold;
	}
	
	/**Getter ID
	 * 
	 */
	public long getID(){
		return ID;
	}
}