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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
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
public class UTILmsg < Val extends Addable<Val>, U extends Addable<U> > 
extends MessageWith2Payloads<String, String> implements Externalizable {

	/** The variable values for a particular assignment */
	protected Val[] values;

	/** The utility for this particular assignment */
	protected U utility;

	/** Empty constructor */
	public UTILmsg () {
		super.type = UTILpropagationFullDomain.UTIL_MSG;
	}

	/**
	 * Constructor for a message without domain info
	 * 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 */
	public UTILmsg(String sender, String receiver, Good<Val, U> good) {
		super(UTILpropagationFullDomain.UTIL_MSG, sender, receiver);
		values = good.getValues();
		utility = good.getUtility();
	}

	/**
	 * Constructor for a message without domain info
	 * @param type 			The type of this message 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 */
	public UTILmsg(String type, String sender, String receiver, Good<Val, U> good) {
		super(type, sender, receiver);
		values = good.getValues();
		utility = good.getUtility();
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		out.writeObject(super.getPayload2());

		// Write the values
		assert this.values.length < Short.MAX_VALUE;
		out.writeShort(this.values.length); // number of values
		Val val = this.values[0];
		out.writeObject(val); // first value
		if (this.values.length > 1) {
			final boolean externalize = val.externalize();
			for (short i = 1; i < this.values.length; i++) { // all remaining values
				if (externalize) 
					this.values[i].writeExternal(out);
				else 
					out.writeObject(this.values[i]);
			}
		}
		
		out.writeObject(this.utility);
	}				

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((String) in.readObject());

		// Read the values
		final int nbrVals = in.readShort(); // number of values
		Val val = (Val) in.readObject(); // first value
		this.values = (Val[]) Array.newInstance(val.getClass(), nbrVals);
		this.values[0] = val;
		if (nbrVals > 1) {
			final boolean externalize = val.externalize();
			for (short i = 1; i < nbrVals; i++) { // all remaining values
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					val = (Val) val.readResolve();
				} else 
					val = (Val) in.readObject();
				this.values[i] = val;
			}
		}
		
		this.utility = (U) in.readObject();
	}
	
	/** @see MessageWith2Payloads#toString() */
	@Override
	public String toString () {
		return "Message (" + super.type + ")\n\t sender =\t" + this.getSender() + "\n\t dest = \t" + this.getReceiver()
			+ "\n\t values =\t" + Arrays.toString(this.values) + "\n\t utility =\t" + this.utility;
	}

	/**
	 * Returns the sender of the message
	 * @return sender
	 */
	public String getSender() {
		return this.getPayload1();
	}

	/**
	 * Returns the receiver of the message
	 * @return receiver
	 */
	public String getReceiver() {
		return this.getPayload2();
	}

	/**
	 * Returns the good
	 * @param variables the variables whos value assignments this message contains
	 * @return Good
	 */
	public Good<Val, U> getGood(String[] variables) {
		return new Good<Val, U>(variables, values, utility);
	}
}