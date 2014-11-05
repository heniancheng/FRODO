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

package frodo2.algorithms.adopt;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;

/**
 * The message used to send a terminate message to a variable's children
 * 
 * @param <Val> the type used for variable values
 */
public class ENDmsg< Val extends Addable<Val> >
		extends Message implements Externalizable {

	/** The sending variable */
	private String sender;

	/** The receiving variable */
	String receiver;

	/** The context in which this message was created */
	private HashMap<String, Val> context;
	
	/** Empty constructor */
	public ENDmsg () {
		super (ADOPT.Original.TERMINATE_MSG_TYPE);
	}

	/**
	 * Constructor
	 * 
	 * @param sender the sender variable
	 * @param receiver the recipient variable
	 * @param context the context
	 */
	@SuppressWarnings("unchecked")
	public ENDmsg(String sender, String receiver,	HashMap<String, Val> context) {
		super(ADOPT.Original.TERMINATE_MSG_TYPE);
		this.sender = sender;
		this.receiver = receiver;
		this.context = (HashMap<String, Val>) context.clone();
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.sender);
		out.writeObject(this.receiver);
		
		// Serialize the context manually
		assert this.context.size() < Short.MAX_VALUE;
		out.writeShort(this.context.size());
		for (Map.Entry<String, Val> entry : this.context.entrySet()) {
			out.writeObject(entry.getKey());
			out.writeObject(entry.getValue());
		}
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.sender = (String) in.readObject();
		this.receiver = (String) in.readObject();
		
		// Read the context
		short nbrVars = in.readShort();
		this.context = new HashMap<String, Val> (nbrVars);
		for (short i = 0; i < nbrVars; i++) {
			String var = (String) in.readObject();
			this.context.put(var, (Val) in.readObject());
		}
	}

	/** Used for serialization */
	private static final long serialVersionUID = 2153239219905600645L;

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
	 * @return the context
	 */
	public HashMap<String, Val> getContext() {
		return context;
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\t sender:\t" + this.sender + "\n\t receiver:\t" + this.receiver + "\n\t context:\t" + this.context;
	}

}