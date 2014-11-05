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
import java.util.Arrays;

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
public class UTILvarsMsgWithDom < Val extends Addable<Val>, U extends Addable<U> > 
extends UTILvarsMsg<Val, U> {


	/** Used for serialization */
	private static final long serialVersionUID = -7059009412837311502L;

	/** Can contain the domain size information for the variables in this variable's separator */
	private int[] domainInfo;

	/** Empty constructor */
	public UTILvarsMsgWithDom () {
		super.type = ASODPOP.UTIL_MSG_DOM_VARS;
	}

	/**
	 * Constructor for a message with domain info
	 * 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 * @param domInfo		Info about the domain size of the variables in the agent's separator
	 */
	public UTILvarsMsgWithDom(String sender, String receiver, Good<Val, U> good, int[] domInfo) {
		super(ASODPOP.UTIL_MSG_DOM_VARS, sender, receiver, good);
		this.domainInfo = domInfo;
	}

	/** @see UTILvarsMsg#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);

		assert this.domainInfo.length < Short.MAX_VALUE;
		out.writeShort(this.domainInfo.length);
		for (int dom : this.domainInfo) {
			assert dom < Short.MAX_VALUE;
			out.writeShort(dom);
		}
	}				

	/** @see UTILvarsMsg#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);

		int domSize = in.readShort();
		this.domainInfo = new int [domSize];
		for (int i = 0; i < domSize; i++) 
			this.domainInfo[i] = in.readShort();
	}

	/** 
	 * @see UTILvarsMsg#toString() 
	 * @author Thomas Leaute
	 */
	@Override
	public String toString () {
		return super.toString() + "\n\t domainInfo =\t" + Arrays.toString(this.domainInfo);
	}

	/**
	 * @return the domain info
	 */
	public int[] getDomInfo() {
		return this.domainInfo;
	}
}