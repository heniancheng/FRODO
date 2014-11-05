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

package frodo2.algorithms.dpop.stochastic;

import java.util.HashSet;

import frodo2.communication.MessageWith3Payloads;

/** A phase 1 message containing flags */
public class LCAmsg1 extends MessageWith3Payloads< String, String, HashSet<String> > {
	
	/** Empty constructor used for externalization */
	public LCAmsg1 () { }

	/** Constructor
	 * @param sender 	the sender of the message
	 * @param dest 		the destination of the message
	 * @param flags 	the set of flags
	 */
	public LCAmsg1 (String sender, String dest, HashSet<String> flags) {
		super (LowestCommonAncestors.PHASE1_MSG_TYPE, sender, dest, flags);
	}
	
	/** @return the sender of the message */
	public String getSender () {
		return this.getPayload1();
	}
	
	/** @return the destination of the message */
	public String getDest () {
		return this.getPayload2();
	}
	
	/** @return the set of flags */
	public HashSet<String> getFlags () {
		return this.getPayload3();
	}
}