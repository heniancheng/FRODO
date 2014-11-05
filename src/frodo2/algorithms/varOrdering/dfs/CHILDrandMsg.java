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

package frodo2.algorithms.varOrdering.dfs;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.HashSet;


/** A CHILD token containing a set of random variables */
public class CHILDrandMsg extends CHILDmsg {
	
	/** A set of random variables */
	private HashSet<String> randVars;
	
	/** Empty constructor used for externalization */
	public CHILDrandMsg () { }

	/** Constructor 
	 * @param sender 	sender variable
	 * @param dest 		recipient variable
	 * @param rootID 	the root ID
	 * @param randVars 	a set of random variables
	 */
	public CHILDrandMsg(String sender, String dest, Serializable rootID, HashSet<String> randVars) {
		super(sender, dest, rootID);
		this.randVars = randVars;
	}
	
	/** @see CHILDmsg#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		
		assert this.randVars.size() < Long.MAX_VALUE;
		out.writeShort(randVars.size());
		for (String var : this.randVars) 
			out.writeObject(var);
	}

	/** @see CHILDmsg#readExternal(java.io.ObjectInput) */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		
		final short nbrVars = in.readShort();
		this.randVars = new HashSet<String> (nbrVars);
		for (short i = 0; i < nbrVars; i++) 
			this.randVars.add((String) in.readObject());
	}

	/** @return the set of random variables */
	public HashSet<String> getRandVars () {
		return this.randVars;
	}
}