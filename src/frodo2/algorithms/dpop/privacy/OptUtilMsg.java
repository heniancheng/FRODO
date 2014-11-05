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

package frodo2.algorithms.dpop.privacy;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;

/** A message containing the optimal utility found for a given component
 * @author Thomas Leaute
 * @param <U> the type used for the utility
 */
public class OptUtilMsg < U extends Addable<U> > extends MessageWith2Payloads<String, U> implements Externalizable {
	
	/** The type of this message */
	public static final String COMP_OPT_UTIL_MSG_TYPE = "CompOptUtilMsg";

	/** Constructor
	 * @param dest 		the destination variable
	 * @param utility 	the optimal utility
	 */
	public OptUtilMsg(String dest, U utility) {
		super(COMP_OPT_UTIL_MSG_TYPE, dest, utility);
	}

	/** Used for externalization only */
	public OptUtilMsg() {
		super.type = COMP_OPT_UTIL_MSG_TYPE;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		out.writeObject(super.getPayload2());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((U) in.readObject());
	}
	
	/** @return the destination variable */
	public String getDest () {
		return super.getPayload1();
	}

	/** @return the utility */
	public U getUtil () {
		return super.getPayload2();
	}
	
}
