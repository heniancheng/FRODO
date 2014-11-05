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

package frodo2.algorithms.adopt;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A message containing the heuristics for the lower bound
 *
 * @param <Val> 	the type used for variable values
 * @param <U> 		the type used for utility values
 */
public class BoundsMsg <Val extends Addable<Val>, U extends Addable<U> >
extends MessageWith3Payloads<String, String, UtilitySolutionSpace<Val, U>> {
	
	/** Empty constructor used for externalization */
	public BoundsMsg () { }

	/** Constructor
	 * @param type 			the type of the message
	 * @param variableId 	variable name
	 * @param parent		the parent of the variable
	 * @param lb 			lower bound
	 */
	public BoundsMsg(String type, String variableId, String parent, UtilitySolutionSpace<Val, U> lb) {
		super(type, variableId, parent, lb);
	}
	
	/** 
	 * @author Brammert Ottens, 19 mei 2009
	 * @return the receiving variable
	 */
	public String getReceiver() {
		return this.getPayload2();
	}
	
	/**
	 * @author Brammert Ottens, 19 mei 2009
	 * @return the sending variable
	 */
	public String getSender() {
		return this.getPayload1();
	}
	
	/**
	 * @author Thomas Leaute
	 * @return	the bounds for the sending variable
	 */
	public UtilitySolutionSpace<Val, U> getBounds() {
		return this.getPayload3();
	}
	
	/** @see MessageWith3Payloads#fakeSerialize() */
	@Override
	public void fakeSerialize () {
		super.setPayload3(super.getPayload3().resolve());
	}
	
}