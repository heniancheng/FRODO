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

package frodo2.algorithms.odpop;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;

/**
 * This message contains the util information a child reports to its parent when responding to an
 * ASK message. It contains the following utility information
 * - an assignment
 * - the utility corresponding to this assignment (be it speculative or not)
 * 
 * @author Brammert Ottens, Thomas Leaute
 *
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 */
public class UTILvarsMsg < Val extends Addable<Val>, U extends Addable<U> > 
extends UTILmsg<Val, U> {

	/** A list of variable IDs*/
	protected String[] variables;

	/** Empty constructor */
	public UTILvarsMsg () {
		super.type = UTILpropagationFullDomain.UTIL_MSG_VARS;
	}

	/**
	 * Constructor for a message without domain info
	 * 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 */
	public UTILvarsMsg(String sender, String receiver, Good<Val, U> good) {
		super(UTILpropagationFullDomain.UTIL_MSG_VARS, sender, receiver, good);
		variables = good.getVariables();
	}

	/**
	 * Constructor for a message without domain info
	 * @param type 			The type of the message 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 */
	public UTILvarsMsg(String type, String sender, String receiver, Good<Val, U> good) {
		super(type, sender, receiver, good);
		variables = good.getVariables();
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		assert this.variables.length < Short.MAX_VALUE;
		out.writeShort(this.variables.length);
		for (short i = 0; i < this.variables.length; i++) 
			out.writeObject(this.variables[i]);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.variables = new String [in.readShort()];
		for (short i = 0; i < this.variables.length; i++) 
			this.variables[i] = (String) in.readObject();
	}

	/** 
	 * @see MessageWith2Payloads#toString() 
	 * @author Thomas Leaute
	 */
	@Override
	public String toString () {
		return "Message (" + super.type + ")\n\t sender =\t" + this.getSender() + "\n\t dest = \t" + this.getReceiver()
			+ "\n\t variables =\t" + Arrays.toString(this.variables) + "\n\t values =\t" + Arrays.toString(this.values) + "\n\t utility =\t" + this.utility;
	}

	/**
	 * Returns the good
	 * @return Good
	 */
	public Good<Val, U> getGood() {
		return new Good<Val, U>(this.variables, this.values, this.utility);
	}

	/**
	 * Returns an array of variable IDs
	 * @author Brammert Ottens, 20 aug 2009
	 * @return an array of variable IDs
	 */
	public String[] getVariables() {
		return this.variables;
	}
}