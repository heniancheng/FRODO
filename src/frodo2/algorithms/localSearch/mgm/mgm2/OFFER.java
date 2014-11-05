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

/**
 * 
 */
package frodo2.algorithms.localSearch.mgm.mgm2;

import java.util.ArrayList;

import frodo2.communication.MessageWith4Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableConflicts;

/**
 * @author Brammert Ottens, 29 mrt. 2011
 * @param <Val> type used for domain values
 * @param <U> type used for utilitie values
 * 
 */
public class OFFER <Val extends Addable<Val>, U extends Addable<U>>
		extends
		MessageWith4Payloads<String, String, ArrayList<BinaryAssignment<Val>>, ArrayList<AddableConflicts<U>>> {
	
	/** Used for serialization */
	private static final long serialVersionUID = -5921523661103120940L;

	/** Default constructor used for externalization only */
	public OFFER () {
		super.type = MGM2.OFFER_MSG_TYPE;
	}
	
	/**
	 * Constructor
	 * 
	 * @param sender		the sender of the message
	 * @param receiver		the receiver of the message
	 * @param assignments	the list of assignments
	 * @param utilities		the gain for each of the assignments
	 */
	public OFFER(String sender, String receiver, ArrayList<BinaryAssignment<Val>> assignments, ArrayList<AddableConflicts<U>> utilities) {
		super(MGM2.OFFER_MSG_TYPE, sender, receiver, assignments, utilities);
		assert !sender.equals(receiver);
	}
	
	/**
	 * @author Brammert Ottens, 29 mrt. 2011
	 * @return the sender of the message
	 */
	public String getSender() {
		return this.getPayload1();
	}
	
	/**
	 * @author Brammert Ottens, 29 mrt. 2011
	 * @return the receiver of the message
	 */
	public String getReceiver() {
		return this.getPayload2();
	}
	
	/**
	 * @author Brammert Ottens, 29 mrt. 2011
	 * @return the list of assignments
	 */
	public ArrayList<BinaryAssignment<Val>> getAssignments() {
		return this.getPayload3();
	}
	
	/**
	 * @author Brammert Ottens, 29 mrt. 2011
	 * @return the gain for each of the assignments
	 */
	public ArrayList<AddableConflicts<U>> getUtilities() {
		return this.getPayload4();
	}

}
