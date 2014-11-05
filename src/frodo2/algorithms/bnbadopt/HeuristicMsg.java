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

package frodo2.algorithms.bnbadopt;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * Message used to send the minimal utility of the agents local sub problem
 * @author Brammert Ottens, Thomas Leaute
 * 
 * @param <U> type used for utility values
 * @param <Val> type used for domain values 
 */
public class HeuristicMsg<Val extends Addable<Val>,  U extends Addable<U> > extends MessageWith3Payloads<String, String, UtilitySolutionSpace<Val, U>> {
	
	/** Empty constructor used for externalization */
	public HeuristicMsg () { }
	
	/**
	 * Constructor
	 * @param type 		the type of the message
	 * @param receiver	the receiver of this message
	 * @param sender 	the sender of this message
	 * @param h			the minimal utility of the variables sub problem
	 */
	public HeuristicMsg(String type, String receiver, String sender, UtilitySolutionSpace<Val, U> h) {
		super(type, receiver, sender, h);
	}
	
	/** @return space of this child */
	public UtilitySolutionSpace<Val, U> getSpace() {
		return this.getPayload3();
	}
	
	/**
	 * @author Brammert Ottens, 20 mei 2009
	 * @return the receiver of this message
	 */
	public String getReceiver() {
		return this.getPayload1();
	}
	
	/**
	 * @author Brammert Ottens, 20 mei 2009
	 * @return the sender of this message 
	 */
	public String getSender() {
		return this.getPayload2();
	}
	
	/** @see MessageWith3Payloads#fakeSerialize() */
	@Override
	public void fakeSerialize () {
		this.setPayload3(super.getPayload3().resolve());
	}
}