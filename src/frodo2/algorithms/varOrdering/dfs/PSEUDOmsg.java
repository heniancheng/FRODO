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

import java.io.Serializable;

import frodo2.communication.MessageWith3Payloads;

/** A token indicating that the destination variable is a pseudo-child of the sender variable */
public class PSEUDOmsg extends MessageWith3Payloads <String, String, Serializable> {
	
	/** Empty constructor */
	public PSEUDOmsg () { }

	/** Constructor 
	 * @param type 		the type of the message
	 * @param sender 	sender variable
	 * @param dest 		recipient variable
	 * @param rootID 	the root ID
	 */
	public PSEUDOmsg (String type, String sender, String dest, Serializable rootID) {
		super (type, sender, dest, rootID);
	}
	
	/** @return the sender variable */
	public String getSender () {
		return getPayload1();
	}
	
	/** @return the recipient variable */
	public String getDest () {
		return getPayload2();
	}

	/** @return the root ID */
	public Serializable getRootID () {
		return super.getPayload3();
	}
}