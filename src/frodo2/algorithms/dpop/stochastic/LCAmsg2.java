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

import frodo2.communication.MessageWith2Payloads;

/** A message with flags, used both for phase 2 and as an output message of LowestCommonAncestors */
public class LCAmsg2 extends MessageWith2Payloads< String, HashSet<String> > {
	
	/** Empty constructor used for externalization */
	public LCAmsg2 () { }

	/** Constructor 
	 * @param type 		the type of the message
	 * @param node 		a corresponding node in the DFS
	 * @param flags 	a set of flags
	 */
	public LCAmsg2 (String type, String node, HashSet<String> flags) {
		super (type, node, flags);
	}
	
	/** @return the node */
	public String getNode () {
		return super.getPayload1();
	}
	
	/** @return the set of flags */
	public HashSet<String> getFlags() {
		return super.getPayload2();
	}
}