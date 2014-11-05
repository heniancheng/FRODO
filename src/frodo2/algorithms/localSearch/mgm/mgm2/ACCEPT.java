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


package frodo2.algorithms.localSearch.mgm.mgm2;

import frodo2.communication.MessageWith4Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableConflicts;

/**
 * @author Brammert Ottens, 30 mrt. 2011
 * @param <Val> type used for domain values 
 * @param <U> type used for utility values
 */
public class ACCEPT <Val extends Addable<Val>, U extends Addable<U>> extends
		MessageWith4Payloads<String, String, BinaryAssignment<Val>, AddableConflicts<U>> {

	/** Used for serialization */
	private static final long serialVersionUID = 948080796668176895L;

	/** Default constructor used for externalization only */
	public ACCEPT () {
		super.type = MGM2.ACCEPT_MSG_TYPE;
	}
	
	/**
	 * Constructor
	 * 
	 * @param sender		the sender of the message
	 * @param receiver		the destination of the message
	 * @param ass			the accepted assignment
	 * @param gain			the total gain obtained when accepting the assignment
	 */
	public ACCEPT(String sender, String receiver, BinaryAssignment<Val> ass, AddableConflicts<U> gain) {
		super(MGM2.ACCEPT_MSG_TYPE, sender, receiver, ass, gain);
	}
	
	/**
	 * @author Brammert Ottens, 1 apr. 2011
	 * @return the sender of the message
	 */
	public String getSender() {
		return this.getPayload1();
	}
	/**
	 * @author Brammert Ottens, 30 mrt. 2011
	 * @return the destination of the message
	 */
	public String getReceiver() {
		return this.getPayload2();
	}
	
	/**
	 * @author Brammert Ottens, 30 mrt. 2011
	 * @return the accepted assignment
	 */
	public BinaryAssignment<Val> getAssignment() {
		return this.getPayload3();
	}
	
	/**
	 * @author Brammert Ottens, 1 apr. 2011
	 * @return the reported gain
	 */
	public AddableConflicts<U> getGain() {
		return this.getPayload4();
	}
}
