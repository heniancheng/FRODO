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

/** Class representing a real number
 * @author Thomas Leaute
 */
public class AddableReal implements Addable<AddableReal> {
	
	/** Used for serialization */
	private static final long serialVersionUID = -2877043522184206202L;
	
	/** The value of this real number */
	private double value;
	
	/** Default constructor */
	public AddableReal () {
		this.value = 3.14;
	}
	
	/** Constructor
	 * @param value 	the value for the real number
	 */
	public AddableReal (double value) {
		this.value = value;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput arg0) throws IOException {
		arg0.writeDouble(this.value);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput arg0) throws IOException, ClassNotFoundException {
		this.value = arg0.readDouble();
	}

	/** @see AddableLimited#externalize() */
	public final boolean externalize() {
		return true;
	}

	/** @see AddableLimited#readResolve() */
	public Object readResolve() {
		
		if (this.value == Double.POSITIVE_INFINITY) 
			return PlusInfinity.PLUS_INF;
			
		else if (this.value == Double.NEGATIVE_INFINITY) 
			return MinInfinity.MIN_INF;
			
		return this;
	}

	/** @see Addable#fromString(java.lang.String) */
	public AddableReal fromString(final String str) {
		if (str.equals("infinity")) {
			return PlusInfinity.PLUS_INF;
		} else if (str.equals("-infinity")) {
			return MinInfinity.MIN_INF;
		} else 
			return new AddableReal (Double.parseDouble(str));
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return String.valueOf(this.value);
	}

	/** Adds this real number with another
	 * @param o 	the other real number
	 * @return 		a new real number equal to the sum of the two
	 * @see Addable#add(Addable) 
	 */
	public AddableReal add(final AddableReal o) {
		if (o == PlusInfinity.PLUS_INF) {
			return PlusInfinity.PLUS_INF;
		} else if (o == MinInfinity.MIN_INF) {
			return MinInfinity.MIN_INF;
		} else 
			return new AddableReal (this.value + o.value);
	}

	/** Multiplies this AddableReal with another AddableReal
	 * @param o 	the AddableReal to be multiplied with
	 * @return 		a new AddableReal equal to the product
	 * @see Addable#multiply(Addable) 
	 */
	public AddableReal multiply(final AddableReal o) {
		
		assert !(this.value == 0 && (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF)) : "Cannot multiply infinity with 0";
		
		if (o == PlusInfinity.PLUS_INF) { 
			
			if (this.value > 0) {
				return PlusInfinity.PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;

		} else if (o == MinInfinity.MIN_INF) {
			
			if (this.value > 0) {
				return MinInfinity.MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;

		} else 
			return new AddableReal(value * o.value);
	}
	
	/** @see Addable#getMinInfinity() */
	public AddableReal getMinInfinity() {
		return MinInfinity.MIN_INF;
	}

	/** @see Addable#getPlusInfinity() */
	public AddableReal getPlusInfinity() {
		return PlusInfinity.PLUS_INF;
	}

	/** @see Addable#getZero() */
	public AddableReal getZero() {
		return new AddableReal (0);
	}

	/** Computes the minimum of two AddableReals
	 * @param o 	the AddableReal to compare to 
	 * @return 		the minimum of the two
	 * @see Addable#min(AddableLimited)
	 */
	public AddableReal min(final AddableReal o) {
		if(this.compareTo(o) <= 0) {
			return this;
		} else {
			return o;
		}
	}
	
	/**
	 * @param o The maximum of this value and the input
	 * @return the max of this addable real and \c o
	 * @see Addable#max(AddableLimited)
	 */
	public AddableReal max(final AddableReal o) {
		if(this.compareTo(o) >= 0) {
			return this;
		} else {
			return o;
		}
	}
	
	/** @see Addable#abs() */
	public AddableReal abs() {
		return new AddableReal(Math.abs(value));
	}
	
	/** Compares this real number with another one
	 * @param o 	the other real number
	 * @return 		+1 if this number is greater than \a o, -1 if it is smaller, and 0 if they are equal
	 * @see java.lang.Comparable#compareTo(java.lang.Object) 
	 */
	public int compareTo(final AddableReal o) {
		if(o == PlusInfinity.PLUS_INF) {
			return -1;
		} else if(o == MinInfinity.MIN_INF) {
			return 1;
		}
		double diff = this.value - o.value;
		if (diff == 0) {
			return 0;
		} else if (diff < 0) {
			return -1;
		} else 
			return 1;
	}

	/** Compares this real number with another one with some error margin
	 * @param o 		the other real number
	 * @param error 	the (non-negative) error margin
	 * @return 			+1 if this number is greater than \a o, -1 if it is smaller, and 0 if they are equal
	 * @see java.lang.Comparable#compareTo(java.lang.Object) 
	 */
	public int compareTo(final AddableReal o, double error) {
		
		if (o == PlusInfinity.PLUS_INF) {
			return -1;
		} else if (o == MinInfinity.MIN_INF) {
			return +1;
		} else {

			double diff = this.value - o.value;
			if (diff <= error && diff >= -error) {
				return 0;
			} else if (diff < 0) {
				return -1;
			} else 
				return 1;
		}
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals (final Object o) {
		
		if (this == o) 
			return true;
		
		if (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF || o == null) 
			return false;
		
		AddableReal o2;
		try {
			o2 = (AddableReal) o;
		} catch (ClassCastException e) {
			// Throwing exceptions is expensive, but you should never compare AddableReals with non-AddableReals anyway
			return false;
		}

		return (this.value == o2.value);
	}
	
	/** Compares two AddableReals within some error margin
	 * @param that 		the other AddableReal
	 * @param error 	the (non-negative) error margin
	 * @return whether the two AddableReals are equal within the given error margin
	 */
	public boolean equals(final AddableReal that, double error) {
		
		if (that == PlusInfinity.PLUS_INF || that == MinInfinity.MIN_INF || that == null)
			return false;
		
		double diff = this.value - that.value;
		return (diff <= error && diff >= -error);
	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode () {
		 long v = Double.doubleToLongBits(this.value);
		 return (int)(v^(v>>>32));
	}

	/** @see Addable#intValue() */
	public int intValue() {
		return (int) this.value;
	}
	
	/** @see Addable#doubleValue() */
	@Override
	public double doubleValue() {
		return this.value;
	}
	
	/**
	 * @see Addable#flipSign()
	 */
	public AddableReal flipSign() {
		return new AddableReal(-value);
	}
	
	/** @return the natural logarithm of this number */
	public AddableReal log () {
		
		assert this.value >= 0 : "Cannot take the log of " + this.value;
		
		if (this.value == 0.0) 
			return AddableReal.MinInfinity.MIN_INF;
		else 
			return new AddableReal (Math.log(this.value));
	}
	
	/** A singleton class representing the real number +infinity */
	public static class PlusInfinity extends AddableReal {
		
		/** The unique instance of the class */
		public static final PlusInfinity PLUS_INF = new PlusInfinity ();
		
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance PlusInfinity.PLUS_INF */
		public PlusInfinity () {
			super (Double.POSITIVE_INFINITY);
		}
		
		/** @see AddableReal#toString() */
		@Override
		public String toString () {
			return "infinity";
		}
		
		/** @see AddableReal#add(AddableReal) */
		@Override
		public PlusInfinity add (final AddableReal o) {
			
			assert o != MinInfinity.MIN_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return PLUS_INF;
		}
		
		/** @see AddableReal#subtract(AddableReal) */
		@Override
		public PlusInfinity subtract(final AddableReal o)  {
			assert o != PLUS_INF : "Subtracting plus infinity from plus infinity is not defined";
			
			return PLUS_INF;
		}
		
		/** @see AddableReal#multiply(AddableReal) */
		@Override
		public AddableReal multiply(final AddableReal o) {
			
			assert o.value != 0 : "Cannot multiply infinity with 0";
			
			if (o.value > 0) {
				return PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;
		}
		
		/** @see AddableReal#divide(AddableReal) */
		@Override
		public PlusInfinity divide(final AddableReal o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && o.value != 0 : "Division by " + o;
			return PLUS_INF;
		}
		
		/** @see AddableReal#abs() */
		@Override
		public PlusInfinity abs() {
			return this;
		}
		
		/** @see AddableReal#compareTo(AddableReal) */
		@Override
		public int compareTo (final AddableReal o) {
			if(o == PLUS_INF) {
				return 0;
			} else 
				return 1;
		}
		
		/** @see AddableReal#compareTo(AddableReal, double) */
		@Override
		public int compareTo(final AddableReal o, double error) {
			return this.compareTo(o);
		}
		
		/** @see AddableReal#equals(java.lang.Object) */
		@Override
		public boolean equals(final Object o) {
			return o == PLUS_INF;
		}
		
		/** @see AddableReal#equals(AddableReal, double) */
		@Override
		public boolean equals(final AddableReal that, double error) {
			return that == PLUS_INF;
		}

		/** @see AddableReal#hashCode() */
		@Override
		public int hashCode() {
			return "plusinfinityReal".hashCode();
		}
		
		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		public Object readResolve() {
            return PLUS_INF;
        }

		/**
		 * @see AddableReal#flipSign()
		 */
		@Override
		public MinInfinity flipSign() {
			return MinInfinity.MIN_INF;
		}

		/** @see AddableReal#addDelayed() */
		@Override
		public AddableDelayed<AddableReal> addDelayed() {
			return new AddableRealDelayed(1, true);
		}
		
		/** @see AddableReal#log() */
		@Override
		public AddableReal log () {
			return this;
		}
	}

	/** A singleton class representing the real number -infinity */
	public static class MinInfinity extends AddableReal {
		
		/** The unique instance of the class */
		public static final MinInfinity MIN_INF = new MinInfinity ();
		
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance MinInfinity.MIN_INF */
		public MinInfinity () {
			super (Double.NEGATIVE_INFINITY);
		}
		
		/** @see AddableReal#toString() */
		@Override
		public String toString () {
			return "-infinity";
		}
		
		/** @see AddableReal#add(AddableReal) */
		@Override
		public MinInfinity add (final AddableReal o) {
			
			assert o != PlusInfinity.PLUS_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return MIN_INF;
		}
		
		/** @see AddableReal#subtract(AddableReal) */
		@Override
		public MinInfinity subtract(final AddableReal o)  {
			assert o != MIN_INF : "Subtracting minus infinity from minus infinity is not defined";
			
			return MIN_INF;
		}
		
		/** @see AddableReal#multiply(AddableReal) */
		@Override
		public AddableReal multiply(final AddableReal o) {
			
			assert o.value != 0 : "Cannot multiply infinity with 0";
			
			if (o.value > 0) {
				return MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;
		}
		
		/** @see AddableReal#divide(AddableReal) */
		@Override
		public MinInfinity divide(final AddableReal o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && o.value != 0 : "Division by " + o;
			return MIN_INF;
		}
		
		/** @see AddableReal#abs() */
		@Override
		public PlusInfinity abs() {
			return PlusInfinity.PLUS_INF;
		}
		
		/** @see AddableReal#compareTo(AddableReal) */
		@Override
		public int compareTo (final AddableReal o) {
			if(o == MIN_INF) {
				return 0;
			} else 
				return -1;
		}
		
		/** @see AddableReal#compareTo(AddableReal, double) */
		@Override
		public int compareTo(final AddableReal o, double error) {
			return this.compareTo(o);
		}
		
		/** @see AddableReal#equals(java.lang.Object) */
		@Override
		public boolean equals(final Object o) {
			return o == MIN_INF;
		}

		/** @see AddableReal#equals(AddableReal, double) */
		@Override
		public boolean equals(final AddableReal that, double error) {
			return that == MIN_INF;
		}

		/** @see AddableReal#hashCode() */
		@Override
		public int hashCode() {
			return "mininfinityReal".hashCode();
		}
		
		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		public Object readResolve() {
            return MIN_INF;
        }

		/**
		 * @see AddableReal#flipSign()
		 */
		@Override
		public PlusInfinity flipSign() {
			return PlusInfinity.PLUS_INF;
		}

		/** @see Addable#addDelayed() */
		@Override
		public AddableDelayed<AddableReal> addDelayed() {
			return new AddableRealDelayed(-1, true);
		}
	}
	
	/**
	 * Class used to make the addition of a large number
	 * of reals more efficient
	 * @author Brammert Ottens, 30 mrt 2010
	 *
	 */
	public static class AddableRealDelayed implements AddableDelayed<AddableReal> {

		/** contains the intermediate sum*/
		private double sum;
		
		/** \c true when the sum is infinite */
		private boolean infinite;
		
		/**
		 * Constructor
		 * 
		 * @param sum the initial value of the sum variable
		 */
		public AddableRealDelayed(double sum) {
			this.sum = sum;
		}
		
		/**
		 * Constructor
		 * 
		 * @param sum				-1 if min infinite and 1 if plus infinite
		 * @param infinite			\c true when the sum is +/- infinite
		 */
		public AddableRealDelayed(double sum, boolean infinite) {
			this.sum = sum;
			this.infinite = infinite;
		}
		
		/** 
		 * @see AddableDelayed#addDelayed(Addable) 
		 * @author Brammert Ottens, Thomas Leaute
		 */
		public void addDelayed(AddableReal a) {

			if (this.infinite) {
				assert (this.sum > 0 ? a != MinInfinity.MIN_INF : a != PlusInfinity.PLUS_INF) : "Adding -INF and +INF";

			} else if (a == PlusInfinity.PLUS_INF) {
				infinite = true;
				sum = 1;
				
			} else if (a == MinInfinity.MIN_INF) {
				infinite = true;
				sum = -1;
				
			} else {
				sum += a.value;
			}
		}

		/** @see AddableDelayed#resolve() */
		public AddableReal resolve() {
			if(infinite) {
				if(sum == 1)
					return PlusInfinity.PLUS_INF;
				else
					return MinInfinity.MIN_INF;
			} else
				return new AddableReal(sum);
		}

		/** 
		 * @see AddableDelayed#multiplyDelayed(Addable)
		 * @author Thomas Leaute
		 */
		public void multiplyDelayed(AddableReal a) {
			
			if (this.infinite) {
				assert a.value != 0 : "Multiplying 0 with infinity";
				this.sum = ((this.sum > 0) == (a.value > 0) ? +1 : -1);
				
			} else if (a == PlusInfinity.PLUS_INF) {
				assert this.sum != 0 : "Multiplying 0 with +INF";
				this.infinite = true;
				this.sum = (this.sum > 0 ? +1 : -1);
				
			} else if (a == MinInfinity.MIN_INF) {
				assert this.sum != 0 : "Multiplying 0 with -INF";
				this.infinite = true;
				this.sum = (this.sum > 0 ? -1 : +1);
				
			} else 
				this.sum *= a.value;
		}

		/** @see AddableDelayed#isInfinite() */
		public boolean isInfinite() {
			return this.infinite;
		}
	}
	
	/** Subtracts o from this AddableReal
	 * @param o 	the value that is subtracted
	 * @return 		the resulting value
	 * @see Addable#subtract(Addable)
	 */
	public AddableReal subtract(final AddableReal o) {
		if(o == PlusInfinity.PLUS_INF) {
			return MinInfinity.MIN_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return PlusInfinity.PLUS_INF;
		}
		return new AddableReal(value - o.value);
	}

	/**
	 * @param o The real to divide with
	 * @return the result of the division
	 * @see Addable#divide(Addable)
	 */
	public AddableReal divide(final AddableReal o) {
		assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && o.value != 0 : "Division by " + o;
		return new AddableReal(value / o.value);
	}

	/** @see Addable#range(Addable, Addable) */
	public Addable<AddableReal>[] range(AddableReal begin, AddableReal end) {
		assert false : "Not implemented";
		return null;
	}

	/** @see Addable#addDelayed() */
	public AddableDelayed<AddableReal> addDelayed() {
		return new AddableRealDelayed(value);
	}
}
