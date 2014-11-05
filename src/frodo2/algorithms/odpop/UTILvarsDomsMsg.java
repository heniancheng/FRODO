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
import java.lang.reflect.Array;

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
public class UTILvarsDomsMsg < Val extends Addable<Val>, U extends Addable<U> > 
extends UTILvarsMsg<Val, U> {

	/** Domains of the variables */
	protected Val[][] domains;

	/** Empty constructor */
	public UTILvarsDomsMsg () {
		super.type = UTILpropagationFullDomain.UTIL_MSG_VARS;
	}

	/**
	 * Constructor for a message without domain info
	 * 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 * @param domains		Variable domains
	 */
	public UTILvarsDomsMsg(String sender, String receiver, Good<Val, U> good, Val[][] domains) {
		super(UTILpropagationFullDomain.UTIL_MSG_VARS, sender, receiver, good);
		this.domains = domains;
		assert domains != null;
	}

	/**
	 * Constructor for a message without domain info
	 * @param type 			The type of the message 
	 * @param sender		The sender of the message
	 * @param receiver		The recipient of the message
	 * @param good			The good to be send
	 * @param domains		Variable domains
	 */
	public UTILvarsDomsMsg(String type, String sender, String receiver, Good<Val, U> good, Val[][] domains) {
		super(type, sender, receiver, good);
		this.domains = domains;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);

		// Write the domains
		assert this.domains.length < Short.MAX_VALUE : "Too many domains to fit in a short";
		out.writeShort(this.domains.length); // number of domains
		Val[] dom = this.domains[0];
		assert dom.length < Short.MAX_VALUE : "Too many values to fit in a short";
		out.writeShort(dom.length); // size of first domain
		out.writeObject(dom[0]); // first value of first domain
		final boolean externalize = dom[0].externalize();
		for (int i = 1; i < dom.length; i++) { // remaining values in first domain
			if (externalize) 
				dom[i].writeExternal(out);
			else 
				out.writeObject(dom[i]);
		}
		for (int i = 1; i < this.domains.length; i++) { // remaining domains
			dom = this.domains[i];
			assert dom.length < Short.MAX_VALUE : "Too many values to fit in a short";
			out.writeShort(dom.length); // size of domain
			for (int j = 0; j < dom.length; j++) { // each value in the domain
				if (externalize) 
					dom[j].writeExternal(out);
				else 
					out.writeObject(dom[j]);
			}
		}
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);

		// Read the domains
		final int nbrDoms = in.readShort(); // number of domains
		int domSize = in.readShort(); // size of first domain
		Val val = (Val) in.readObject(); // first value of first domain
		final boolean externalize = val.externalize();
		Val[] dom = (Val[]) Array.newInstance(val.getClass(), domSize);
		this.domains = (Val[][]) Array.newInstance(dom.getClass(), nbrDoms);
		this.domains[0] = dom;
		dom[0] = val;
		for (int i = 1; i < domSize; i++) { // read the remaining values in the first domain
			if (externalize) {
				val = val.getZero();
				val.readExternal(in);
				dom[i] = (Val) val.readResolve();
			} else 
				dom[i] = (Val) in.readObject();
		}
		for (int i = 1; i < nbrDoms; i++) { // read the remaining domains
			domSize = in.readShort(); // domain size
			dom = (Val[]) Array.newInstance(val.getClass(), domSize);
			this.domains[i] = dom;
			for (int j = 0; j < domSize; j++) { // each value in the domain
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					dom[j] = (Val) val.readResolve();
				} else 
					dom[j] = (Val) in.readObject();
			}
		}
	}

	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @return the domains of the variables
	 */
	public Val[][] getDomains() {
		return this.domains;
	}
}