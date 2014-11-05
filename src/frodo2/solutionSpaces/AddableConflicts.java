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

package frodo2.solutionSpaces;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author Brammert Ottens, 13 apr. 2011
 * @param <U> type used for utility values
 * 
 */
public class AddableConflicts < U extends Addable<U>> implements Addable<AddableConflicts<U>> {

	/** Used for serialization */
	private static final long serialVersionUID = 9214213454805720095L;

	/** Utility value */
	private U utility;

	/** if utility is infeasible, the number of conflicts, i.e. constraints violated */
	private int conflicts;

	/**
	 * Empty Constructor
	 */
	public AddableConflicts() {};
	
	/** Constructor
	 * @param utility		the utility value 
	 * @param conflicts 	the number of conflicts
	 */
	public AddableConflicts(U utility, int conflicts) {
		this.utility = utility;
		this.conflicts = conflicts;
	}

	/** 
	 * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
	 */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(utility);
		out.writeInt(conflicts);
	}
	
	/** 
	 * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
	 */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		this.utility = (U)in.readObject();
		this.conflicts = in.readInt();		
	}

	/** 
	 * @see frodo2.solutionSpaces.AddableLimited#externalize()
	 */
	public boolean externalize() {
		return true;
	}

	/** 
	 * @see frodo2.solutionSpaces.AddableLimited#readResolve()
	 */
	public Object readResolve() {
		return this;
	}

	
	/** 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(AddableConflicts<U> o) {
		if(utility.equals(o.utility))
			return o.conflicts - conflicts;
		else
			return utility.compareTo(o.utility);
	}

	/**
	 * @author Brammert Ottens, 26 mei 2010
	 * @return the utility value
	 */
	public U getUtility() {
		return utility;
	}

	/** 
	 * @see frodo2.solutionSpaces.AddableLimited#min(frodo2.solutionSpaces.AddableLimited)
	 */
	public AddableConflicts<U> min(AddableConflicts<U> other) {
		return this.compareTo(other) < 0 ? this : other;
	}

	/** 
	 * @see frodo2.solutionSpaces.AddableLimited#max(frodo2.solutionSpaces.AddableLimited)
	 */
	public AddableConflicts<U> max(AddableConflicts<U> other) {
		return this.compareTo(other) > 0 ? this : other;
	}
	

	/** 
	 * @see frodo2.solutionSpaces.Addable#fromString(java.lang.String)
	 */
	public AddableConflicts<U> fromString(String str) {
		// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#add(frodo2.solutionSpaces.Addable)
	 */
	public AddableConflicts<U> add(AddableConflicts<U> o) {
		return new AddableConflicts<U>(this.utility.add(o.utility), this.conflicts + o.conflicts);
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#addDelayed()
	 */
	public AddableDelayed<AddableConflicts<U>> addDelayed() {
		// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#subtract(frodo2.solutionSpaces.Addable)
	 */
	public AddableConflicts<U> subtract(AddableConflicts<U> o) {
		return new AddableConflicts<U>(this.utility.subtract(o.utility), this.conflicts + o.conflicts);
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#multiply(frodo2.solutionSpaces.Addable)
	 */
	public AddableConflicts<U> multiply(AddableConflicts<U> o) {
		return new AddableConflicts<U>(this.utility.multiply(o.utility), this.conflicts + o.conflicts);
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#getZero()
	 */
	public AddableConflicts<U> getZero() {
		// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#getPlusInfinity()
	 */
	public AddableConflicts<U> getPlusInfinity() {
		// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#getMinInfinity()
	 */
	public AddableConflicts<U> getMinInfinity() {
		// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#abs()
	 */
	public AddableConflicts<U> abs() {
		return new AddableConflicts<U>(this.utility.abs(), this.conflicts);
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#divide(frodo2.solutionSpaces.Addable)
	 */
	public AddableConflicts<U> divide(AddableConflicts<U> o) {
		return new AddableConflicts<U>(this.utility.divide(o.utility), this.conflicts);
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#flipSign()
	 */
	public AddableConflicts<U> flipSign() {
		return new AddableConflicts<U>(this.utility.flipSign(), this.conflicts);
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#range(frodo2.solutionSpaces.Addable, frodo2.solutionSpaces.Addable)
	 */
	public Addable<AddableConflicts<U>>[] range(AddableConflicts<U> begin,
			AddableConflicts<U> end) {
		// @todo Auto-generated method stub
		assert false : "Not Implemented";
		return null;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "<" + this.utility + ", " + this.conflicts + ">";  
	}

	/** 
	 * @see frodo2.solutionSpaces.Addable#intValue()
	 */
	public int intValue() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see frodo2.solutionSpaces.Addable#doubleValue() */
	@Override
	public double doubleValue() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

}
