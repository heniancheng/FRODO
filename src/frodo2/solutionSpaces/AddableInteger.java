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
 * @author Radoslaw Szymanek, Thomas Leaute, Brammert Ottens
 *
 */

public class AddableInteger implements Addable<AddableInteger> {

	/** The value */
	private int integer;
	
	/**
	 * Empty constructor for creating zero, plus and minus infinity
	 */
	public AddableInteger() { 
		integer = 123;
	}
	
	/**
	 * @param integer 	the integer value
	 */
	public AddableInteger(int integer){
		this.integer = integer;
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput arg0) throws IOException {
		arg0.writeInt(this.integer);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput arg0) throws IOException, ClassNotFoundException {
		this.integer = arg0.readInt();
	}

	/** @see AddableLimited#externalize() */
	public final boolean externalize() {
		return true;
	}

	/** @see AddableLimited#readResolve() */
	public Object readResolve() {
		
		switch (this.integer) {
		
		case Integer.MAX_VALUE:
			return PlusInfinity.PLUS_INF;
			
		case Integer.MIN_VALUE:
			return MinInfinity.MIN_INF;
			
		default:
			return this;
		}
	}

	/** @see Addable#fromString(java.lang.String) */
	public AddableInteger fromString (final String str) {
		if (str.equals("infinity")) {
			return PlusInfinity.PLUS_INF;
		} else if (str.equals("-infinity")) {
			return MinInfinity.MIN_INF;
		} else {
			try {
				return new AddableInteger (Integer.parseInt(str));
				
			} catch (NumberFormatException e) { // does not look like an int
				
				// Try to parse it as a double, and truncate
				return new AddableInteger ((int) Double.parseDouble(str));
			}
		}
	}
	
	/** @return the value as an int */
	@Override
	public int intValue() {
		return integer;
	}
	
	/** @see Addable#doubleValue() */
	@Override
	public double doubleValue() {
		return this.integer;
	}
	
	/** @see java.lang.Object#toString() */
	public String toString() {
		return String.valueOf(this.integer);
	}
	
	/** Adds this AddableInteger with another AddableInteger
	 * @param o 	the AddableInteger to be added
	 * @return 		a new AddableInteger equal to the sum
	 * @see Addable#add(Addable) 
	 */
	public AddableInteger add(final AddableInteger o) {
		if(o == PlusInfinity.PLUS_INF) {
			return PlusInfinity.PLUS_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return MinInfinity.MIN_INF;
		}
		return new AddableInteger(integer + o.integer);
	}
	
	/** Adds an int to the value of this AddableInteger
	 * @param o 	the int value
	 * @return the resulting new AddableInteger
	 */
	public AddableInteger add(int o) {
		return new AddableInteger(integer + o);
	}
	
	/** Subtracts another AddableInteger from this AddableInteger
	 * @param o 	the other AddableInteger 
	 * @return 		the result of the subtraction
	 * @see Addable#subtract(Addable)
	 */
	public AddableInteger subtract(final AddableInteger o) {
		if(o == PlusInfinity.PLUS_INF) {
			return MinInfinity.MIN_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return PlusInfinity.PLUS_INF;
		}
		return new AddableInteger(integer - o.integer);
	}
	
	/** Subtracts an integer from this addable integer
	 * @param o 	the integer to subtract
	 * @return 		the result of the subtraction
	 */
	public AddableInteger subtract(int o) {
		return new AddableInteger(integer - o);
	}
	
	/** Multiplies this AddableInteger with another AddableInteger
	 * @param o 	the AddableInteger to be multiplied with
	 * @return 		a new AddableInteger equal to the product
	 * @see Addable#multiply(Addable) 
	 */
	public AddableInteger multiply(final AddableInteger o) {
		
		assert !(this.integer == 0 && (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF)) : "Cannot multiply infinity with 0";
		
		if (o == PlusInfinity.PLUS_INF) { 
			
			if (this.integer > 0) {
				return PlusInfinity.PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;

		} else if (o == MinInfinity.MIN_INF) {
			
			if (this.integer > 0) {
				return MinInfinity.MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;

		} else 
			return new AddableInteger(integer * o.integer);
	}
	
	/**
	 * @param o the integer to divide with
	 * @return the result of the division
	 * @see Addable#divide(Addable)
	 */
	public AddableInteger divide(final AddableInteger o) {
		assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && o.integer != 0 : "Division by " + o;
		return new AddableInteger(integer/o.integer);
	}

	/** Computes the min of two AddableIntegers
	 * @param o 	the AddableInteger to compare to
	 * @return 		the minimum of this and the input AddableInteger
	 * @see Addable#min(AddableLimited)
	 */
	public AddableInteger min(final AddableInteger o) {
		if(this.compareTo(o) <= 0) {
			return this;
		} else {
			return o;
		}
	}
	
	/** Computes the max of two AddableIntegers
	 * @param o 	the AddableInteger to compare to
	 * @return 		the maximum of this and the input AddableInteger
	 * @see Addable#max(AddableLimited)
	 */
	public AddableInteger max(final AddableInteger o) {
		if(this.compareTo(o) >= 0) {
			return this;
		} else {
			return o;
		}
	}
	
	/** @see Addable#abs() */
	public AddableInteger abs() {
		return new AddableInteger(Math.abs(integer));
	}
	
	/** Compares this AddableInteger with another
	 * @param o 	another AddableInteger
	 * @return 		0 if they are equal, a positive number if this AddableInteger is greater than the input
	 */
	public int compareTo(final AddableInteger o) {
		if(o == PlusInfinity.PLUS_INF) {
			return -1;
		} else if( o == MinInfinity.MIN_INF) {
			return 1;
		}
		return integer - o.integer;
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	public boolean equals(final Object o) {
		
		if (this == o) 
			return true;
		
		if (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF || o == null) 
			return false;
		
		AddableInteger o2;
		try {
			o2 = (AddableInteger) o;
		} catch (ClassCastException e) {
			// Throwing exceptions is expensive, but you should never compare AddableIntegers with non-AddableIntegers anyway
			return false;
		}

		return (this.integer == o2.integer);
	}

	/** @see java.lang.Object#hashCode() */
	public int hashCode() {
		return integer;
	}
	
	/**
	 * @see Addable#getZero()
	 */
	public AddableInteger getZero() {
		return new AddableInteger(0);
	}
	
	/**
	 * @see Addable#getPlusInfinity()
	 */
	public AddableInteger getPlusInfinity() {
		return PlusInfinity.PLUS_INF;
	}
	
	/**
	 * @see Addable#getMinInfinity()
	 */
	public AddableInteger getMinInfinity() {
		return MinInfinity.MIN_INF;
	}
	
	/**
	 * @see Addable#flipSign()
	 */
	public AddableInteger flipSign() {
		return new AddableInteger(-integer);
	}
	
	/**
	 * This class implements the plus infinity element. Adding or subtracting anything
	 * still results in the infinity element.
	 * @author brammert
	 *
	 */
	public static class PlusInfinity extends AddableInteger {
		
		/** Used for serialization */
		private static final long serialVersionUID = -8186521368014629929L;
		
		/** The singleton PlusInfinity */
		public static final PlusInfinity PLUS_INF = new PlusInfinity();
	
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance PlusInfinity.PLUS_INF */
		public PlusInfinity() {
			super(Integer.MAX_VALUE);
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString() {
			return "infinity";
		}
	
		/** @see AddableInteger#add(AddableInteger) */
		@Override
		public PlusInfinity add(final AddableInteger o) {
			
			assert o != MinInfinity.MIN_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return PLUS_INF;
		}
		
		/** Adds an int to the value of this AddableInteger
		 * @param o 	the int value
		 * @return the infinite value
		 */
		@Override
		public PlusInfinity add(int o) {
			return PLUS_INF;
		}
		
		/** @see AddableInteger#subtract(AddableInteger) */
		@Override
		public PlusInfinity subtract(final AddableInteger o)  {
			assert o != PLUS_INF : "Subtracting plus infinity from plus infinity is not defined";
			
			return PLUS_INF;
		}
		
		/** @see AddableInteger#subtract(int) */
		@Override
		public PlusInfinity subtract(int o)  {
			return PLUS_INF;
		}
		
		/** @see AddableInteger#multiply(AddableInteger) */
		@Override
		public AddableInteger multiply(final AddableInteger o) {
			
			assert o.integer != 0 : "Cannot multiply infinity with 0";
			
			if (o.integer > 0) {
				return PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;
		}
		
		/** @see AddableInteger#divide(AddableInteger) */
		@Override
		public AddableInteger divide(final AddableInteger o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && o.integer != 0 : "Division by " + o;
			return PLUS_INF;
		}
		
		/** @see AddableInteger#abs() */
		@Override
		public AddableInteger abs() {
			return this;
		}
		
		/** Compares this AddableInteger with another
		 * @param o 	another AddableInteger
		 * @return 		0 if they are equal, a positive number if this AddableInteger is greater than the input
		 */
		@Override
		public int compareTo(final AddableInteger o) {
			if(o == PLUS_INF) {
				return 0;
			} else {
				return 1;
			}
		}
		
		/**
		 * @see AddableInteger#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(final Object o) {
			return o == PLUS_INF;
		}
		
		/** @see java.lang.Object#hashCode() */
		@Override
		public int hashCode() {
			return "plusinfinity".hashCode();
		}
		
		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		public Object readResolve() {
            return PLUS_INF;
        }

		/**
		 * @see AddableInteger#flipSign()
		 */
		@Override
		public AddableInteger flipSign() {
			return MinInfinity.MIN_INF;
		}
		
		/** @see Addable#addDelayed() */
		@Override
		public AddableDelayed<AddableInteger> addDelayed() {
			return new AddableIntegerDelayed(1, true);
		}
		
	}
	
	/**
	 * This class implements the minus infinity element. Adding or subtracting anything
	 * still results in the infinity element.
	 * @author brammert
	 *
	 */
	public static class MinInfinity extends AddableInteger {
		
		/** Used for serialization */
		private static final long serialVersionUID = -8186521368014629929L;
		
		/** The singleton MinInfinity */
		public static final MinInfinity MIN_INF = new MinInfinity();
	
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance MinInfinity.MIN_INF */
		public MinInfinity() {
			super(Integer.MIN_VALUE);
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString() {
			return "-infinity";
		}
	
		/** @see AddableInteger#add(AddableInteger) */
		@Override
		public MinInfinity add(final AddableInteger o) {
			
			assert o != PlusInfinity.PLUS_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return MIN_INF;
		}
		
		/** Adds an int to the value of this AddableInteger
		 * @param o 	the int value
		 * @return the infinite value
		 */
		@Override
		public MinInfinity add(int o) {
			return MIN_INF;
		}
		
		/** @see AddableInteger#subtract(AddableInteger) */
		@Override
		public MinInfinity subtract(final AddableInteger o)  {
			assert o != MIN_INF : "Subtracting minus infinity from minus infinity is not defined";
			
			return MIN_INF;
		}
		
		/** @see AddableInteger#subtract(int) */
		@Override
		public MinInfinity subtract(int o)  {
			return MIN_INF;
		}
		
		/** @see AddableInteger#multiply(AddableInteger) */
		@Override
		public AddableInteger multiply(final AddableInteger o) {
			
			assert o.integer != 0 : "Cannot multiply infinity with 0";
			
			if (o.integer > 0) {
				return MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;
		}
		
		/** @see AddableInteger#divide(AddableInteger) */
		@Override
		public AddableInteger divide(final AddableInteger o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && o.integer != 0 : "Division by " + o;
			return MIN_INF;
		}
		
		/** @see AddableInteger#abs() */
		@Override
		public AddableInteger abs() {
			return PlusInfinity.PLUS_INF;
		}
		
		/** Compares this AddableInteger with another
		 * @param o 	another AddableInteger
		 * @return 		0 if they are equal, a positive number if this AddableInteger is greater than the input
		 */
		@Override
		public int compareTo(final AddableInteger o) {
			if(o == MIN_INF) {
				return 0;
			} else {
				return -1;
			}
		}
		
		/**
		 * @see AddableInteger#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(final Object o) {
			return o == MIN_INF;
		}
		
		/** @see java.lang.Object#hashCode() */
		@Override
		public int hashCode() {
			return "mininfinity".hashCode();
		}
		
		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		public Object readResolve() {
            return MIN_INF;
        }

		/**
		 * @see AddableInteger#flipSign()
		 */
		@Override
		public AddableInteger flipSign() {
			return PlusInfinity.PLUS_INF;
		}
		
		/** @see Addable#addDelayed() */
		@Override
		public AddableDelayed<AddableInteger> addDelayed() {
			return new AddableIntegerDelayed(-1, true);
		}

	}
	
	/**
	 * Class used to make the addition of a large number
	 * of integers more efficient
	 * @author Brammert Ottens, 30 mrt 2010
	 *
	 */
	public static class AddableIntegerDelayed implements AddableDelayed<AddableInteger> {

		/** contains the intermediate sum*/
		private int sum;
		
		/** \c true when the sum of the value is infinite */
		private boolean infinite;
		
		/**
		 * Constructor
		 * 
		 * @param sum the initial value of the sum variable
		 */
		public AddableIntegerDelayed(int sum) {
			this.sum = sum;
		}
		
		/**
		 * Constructor
		 * 
		 * @param sum				-1 if min infinite and 1 if plus infinite
		 * @param infinite			\c true when the sum is +/- infinite
		 */
		public AddableIntegerDelayed(int sum, boolean infinite) {
			this.sum = sum;
			this.infinite = infinite;
		}
		
		/** 
		 * @see AddableDelayed#addDelayed(Addable) 
		 * @author Brammert Ottens, Thomas Leaute
		 */
		public void addDelayed(AddableInteger a) {

			if (this.infinite) {
				assert (this.sum > 0 ? a != MinInfinity.MIN_INF : a != PlusInfinity.PLUS_INF) : "Adding -INF and +INF";

			} else if (a == PlusInfinity.PLUS_INF) {
				infinite = true;
				sum = 1;
				
			} else if (a == MinInfinity.MIN_INF) {
				infinite = true;
				sum = -1;
				
			} else {
				sum += a.integer;
			}
		}

		/** @see AddableDelayed#resolve() */
		public AddableInteger resolve() {
			if(infinite) {
				if(sum == 1)
					return PlusInfinity.PLUS_INF;
				else
					return MinInfinity.MIN_INF;
			} else
				return new AddableInteger(sum);
		}

		/** 
		 * @see AddableDelayed#multiplyDelayed(Addable)
		 * @author Thomas Leaute
		 */
		public void multiplyDelayed(AddableInteger a) {
			
			if (this.infinite) {
				assert a.integer != 0 : "Multiplying 0 with infinity";
				this.sum = ((this.sum > 0) == (a.integer > 0) ? +1 : -1);
				
			} else if (a == PlusInfinity.PLUS_INF) {
				assert this.sum != 0 : "Multiplying 0 with +INF";
				this.infinite = true;
				this.sum = (this.sum > 0 ? +1 : -1);
				
			} else if (a == MinInfinity.MIN_INF) {
				assert this.sum != 0 : "Multiplying 0 with -INF";
				this.infinite = true;
				this.sum = (this.sum > 0 ? -1 : +1);
				
			} else 
				this.sum *= a.integer;
		}

		/** @see AddableDelayed#isInfinite() */
		public boolean isInfinite() {
			return this.infinite;
		}
		
	}

	/** @see Addable#range(Addable, Addable) */
	public Addable<AddableInteger>[] range(AddableInteger begin, AddableInteger end) {
		AddableInteger[] range = new AddableInteger[(end.integer - begin.integer) + 1];
		for(int i = begin.integer, j = 0; i <= end.integer; i++, j++) {
			range[j] = new AddableInteger(i);
		}
		return range;
	}

	/** @see Addable#addDelayed() */
	public AddableDelayed<AddableInteger> addDelayed() {
		return new AddableIntegerDelayed(this.integer);
	}
	
}
