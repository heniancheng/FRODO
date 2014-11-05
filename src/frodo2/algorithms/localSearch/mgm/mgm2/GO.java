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

import frodo2.communication.MessageWithPayload;

/**
 * @author Brammert Ottens, 30 mrt. 2011
 * 
 */
public class GO extends MessageWithPayload<String> {

	/** Used for serialization */
	private static final long serialVersionUID = 1593860609800505075L;

	/** Default constructor used for externalization only */
	public GO () {
		super.type = MGM2.IMPROVE_MSG_TYPE;
	}
	
	/**
	 * Constructor
	 * @param receiver the receiver of the message
	 */
	public GO(String receiver) {
		super(MGM2.GO_MSG_TYPE, receiver);
	}
	
	/**
	 * @author Brammert Ottens, 11 apr. 2011
	 * @return the receiver of the message
	 */
	public String getReceiver() {
		return this.getPayload();
	}
}
