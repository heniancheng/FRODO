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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.solutionSpaces.Addable;

/**
 * This message contains the util information a child reports to its parent when responding to an
 * ASK message. It contains the following utility information
 * - an assignment
 * - the utility corresponding to this assignment (be it speculative or not)
 * 
 * @author brammert
 *
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 */
public class UTILvarsDomsMsg < Val extends Addable<Val>, U extends Addable<U> > extends frodo2.algorithms.odpop.UTILvarsDomsMsg<Val, U> {

	/** \c true when this is a confirmed util message and \c false otherwise */
	private boolean confirmed;

	/** Empty constructor */
	public UTILvarsDomsMsg () {
		super.type = ASODPOPBinaryDomains.UTIL_MSG_VARS;
	}

	/**
	 * Constructor for a message without domain info
	 * 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 * @param domains		The domains of the reported variables
	 */
	public UTILvarsDomsMsg(String sender, String receiver, Good<Val, U> good, Val[][] domains) {
		super(sender, receiver, good, domains);
		confirmed = good.isConfirmed();
		assert variables.length > 0;
	}

	/**
	 * Constructor for a message without domain info
	 * @param type 			The type of the message 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 * @param domains		The domains of the reported variables
	 */
	public UTILvarsDomsMsg(String type, String sender, String receiver, Good<Val, U> good, Val[][] domains) {
		super(type, sender, receiver, good, domains);
		confirmed = good.isConfirmed();
		assert variables.length > 0;
	}

	/** @see frodo2.algorithms.odpop.UTILvarsDomsMsg#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeBoolean(this.confirmed);
	}

	/** @see frodo2.algorithms.odpop.UTILvarsDomsMsg#readExternal(java.io.ObjectInput) */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.confirmed = in.readBoolean();
	}

	/**
	 * Returns the good
	 * @return Good
	 */
	@Override
	public Good<Val, U> getGood() {
		return new Good<Val, U>(this.variables, this.values, this.utility, this.confirmed);
	}
}