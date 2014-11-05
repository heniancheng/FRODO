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

import java.io.Serializable;

import frodo2.solutionSpaces.Addable;

/**
 * @author Brammert Ottens, 29 mrt. 2011
 * @param <Val> type used for domain values
 * 
 */
public class BinaryAssignment <Val extends Addable<Val>> implements Serializable {
	
	/** Used for serialization */
	private static final long serialVersionUID = 250394009304944886L;

	/** The value of the sending variable */
	private Val ownValue;
	
	/** The value of the receiving variable */
	private Val neighborValue;
	
	/**
	 * Constructor
	 * 
	 * @param ownValue			The value of the sending variable
	 * @param neighborValue		The value of the receiving variable
	 */
	public BinaryAssignment(Val ownValue, Val neighborValue) {
		this.ownValue = ownValue;
		this.neighborValue = neighborValue;
	}
	
	/**
	 * @author Brammert Ottens, 11 apr. 2011
	 * @return the value of the sending variable
	 */
	public Val getOwnValue() {
		return ownValue;
	}
	
	/**
	 * @author Brammert Ottens, 11 apr. 2011
	 * @return the value of the receiving variable
	 */
	public Val neighborValue() {
		return neighborValue;
	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		return ( ownValue.hashCode() << 16) | ( neighborValue.hashCode() << 7 );
	}
}
