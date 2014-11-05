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
import java.math.BigDecimal;
import java.math.BigInteger;

/** An arbitrary-precision real number that implements Addable
 * @author Thomas Leaute, Eric Zbinden
 */
public class AddableBigDecimal implements Addable<AddableBigDecimal> {

	/** ZERO */
	private static final AddableBigDecimal ZERO = new AddableBigDecimal(BigDecimal.ZERO);
	
	/** The value of this real number */
	private BigDecimal val;
	
	/** Empty constructor */
	public AddableBigDecimal () {
		this.val = BigDecimal.ZERO;
	}

	/** Constructor
	 * @param value 	the value for the real number
	 */
	public AddableBigDecimal (double value) {
		this.val = new BigDecimal (value);
	}
	
	/** Constructor
	 * @param val The value in a BigInteger form
	 */
	protected AddableBigDecimal(BigDecimal val) {
		this.val = val;
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput arg0) throws IOException {
		arg0.writeObject(this.val);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput arg0) throws IOException, ClassNotFoundException {
		this.val = (BigDecimal) arg0.readObject();
	}

	/** @see AddableLimited#readResolve() */
	public Object readResolve() {
		return this;
	}

	/** @see AddableLimited#externalize() */
	public final boolean externalize() {
		return false;
	}

	/** @see Addable#abs() */
	public AddableBigDecimal abs() {
		if (this.val.signum() >= 0) return this;
		else return new AddableBigDecimal (this.val.abs()); 
	}

	/** @see Addable#add(Addable) */
	public AddableBigDecimal add(AddableBigDecimal o) {
		if(o == PlusInfinity.PLUS_INF) {
			return PlusInfinity.PLUS_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return MinInfinity.MIN_INF;
		}
		return new AddableBigDecimal (this.val.add(o.val));
	}

	/** @see Addable#addDelayed() */
	public AddableDelayed<AddableBigDecimal> addDelayed() {
		return new AddableBigDecimalDelayed (this.val);
	}

	/** @see java.lang.Comparable#compareTo(java.lang.Object) */
	public int compareTo(AddableBigDecimal o) {
		if(o == PlusInfinity.PLUS_INF) {
			return -1;
		} else if( o == MinInfinity.MIN_INF) {
			return 1;
		}
		return this.val.compareTo(o.val);
	}

	/** Compares this real number with another one with some error margin
	 * @param o 		the other real number
	 * @param error 	the (non-negative) error margin
	 * @return 			+1 if this number is greater than \a o, -1 if it is smaller, and 0 if they are equal
	 * @see java.lang.Comparable#compareTo(java.lang.Object) 
	 */
	public int compareTo(final AddableBigDecimal o, double error) {
		
		if (o == PlusInfinity.PLUS_INF) {
			return -1;
		} else if (o == MinInfinity.MIN_INF) {
			return +1;
		} else {

			double diff = this.val.subtract(o.val).doubleValue();
			if (diff <= error && diff >= -error) {
				return 0;
			} else if (diff < 0) {
				return -1;
			} else 
				return 1;
		}
	}

	/** @see Addable#divide(Addable) */
	public AddableBigDecimal divide(AddableBigDecimal o) {
		assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && ! o.equals(ZERO) : "Division by " + o;
		return new AddableBigDecimal (this.val.divide(o.val));
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals(Object o){
		
		if (this == o) 
			return true;
		
		if (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF || o == null) 
			return false;
		
		AddableBigDecimal o2;
		try {
			o2 = (AddableBigDecimal) o;
		} catch (ClassCastException e) {
			// Throwing exceptions is expensive, but you should never compare AddableBigDecimals with non-AddableBigDecimals anyway
			return false;
		}

		return val.equals(o2.val);
	}
	
	/** Compares two AddableBigDecimals within some error margin
	 * @param that 		the other AddableBigDecimal
	 * @param error 	the (non-negative) error margin
	 * @return whether the two AddableBigDecimals are equal within the given error margin
	 */
	public boolean equals(final AddableBigDecimal that, double error) {
		
		if (that == PlusInfinity.PLUS_INF || that == MinInfinity.MIN_INF || that == null)
			return false;
		
		double diff = this.val.subtract(that.val).doubleValue();
		return (diff <= error && diff >= -error);
	}

	/** @see Addable#flipSign() */
	public AddableBigDecimal flipSign() {
		return new AddableBigDecimal (this.val.negate());
	}

	/** @see Addable#fromString(java.lang.String) */
	public AddableBigDecimal fromString(String str) {
		if (str.equals("infinity")) {
			return PlusInfinity.PLUS_INF;
		} else if (str.equals("-infinity")) {
			return MinInfinity.MIN_INF;
		} else 
			return new AddableBigDecimal (Double.parseDouble(str));
	}

	/** @see Addable#getMinInfinity() */
	public AddableBigDecimal getMinInfinity() {
		return MinInfinity.MIN_INF;
	}

	/** @see Addable#getPlusInfinity() */
	public AddableBigDecimal getPlusInfinity() {
		return PlusInfinity.PLUS_INF;
	}

	/** @see Addable#getZero() */
	public AddableBigDecimal getZero() {
		return ZERO;
	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode () {
		return this.val.hashCode();
	}

	/** @see Addable#intValue() */
	@Override
	public int intValue() {
		return this.val.intValue();
	}

	/** @see Addable#doubleValue() */
	@Override
	public double doubleValue() {
		return this.val.doubleValue();
	}

	/** @see AddableLimited#max(AddableLimited) */
	public AddableBigDecimal max(AddableBigDecimal o) {
		if (this.compareTo(o) >=0) return this;
		else return o;
	}

	/** @see AddableLimited#min(AddableLimited) */
	public AddableBigDecimal min(AddableBigDecimal o) {
		if (this.compareTo(o) >=0) return o;
		else return this;
	}

	/** @see Addable#multiply(Addable) */
	public AddableBigDecimal multiply(AddableBigDecimal o) {
		assert !(this.equals(ZERO) && (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF)) : "Cannot multiply infinity with 0";
		
		if (o == PlusInfinity.PLUS_INF) { 
			
			if (this.val.signum() >= 0) {
				return PlusInfinity.PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;

		} else if (o == MinInfinity.MIN_INF) {
			
			if (this.val.signum() >= 0) {
				return MinInfinity.MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;

		} else 
		return new AddableBigDecimal(this.val.multiply(o.val));
	}

	/** @see Addable#subtract(Addable) */
	public AddableBigDecimal subtract(AddableBigDecimal o) {
		if(o == PlusInfinity.PLUS_INF) {
			return MinInfinity.MIN_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return PlusInfinity.PLUS_INF;
		}
		return new AddableBigDecimal(this.val.subtract(o.val));
	}

	/** @see Addable#range(Addable, Addable) */
	public Addable<AddableBigDecimal>[] range(AddableBigDecimal begin, AddableBigDecimal end) {
		// @todo Auto-generated method stub
		assert false: "Not Implemented";
		return null;
	}
	
	/** @see Object#toString() */
	@Override
	public String toString(){
		return this.val.toEngineeringString();
	}
	
	/**
	 * This class implements the minus infinity element. Adding or subtracting anything
	 * still results in the infinity element.
	 */
	public static class MinInfinity extends AddableBigDecimal {
		
		/** The singleton MinInfinity */
		public static final MinInfinity MIN_INF = new MinInfinity();
	
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance MinInfinity.MIN_INF */
		public MinInfinity() {
			super();
		}
	
		/** @see AddableBigDecimal#intValue() */
		@Override
		public int intValue() {
			return Integer.MIN_VALUE;
		}

		/** @see AddableBigDecimal#doubleValue() */
		@Override
		public double doubleValue() {
			return Double.NEGATIVE_INFINITY;
		}

		/** @see AddableBigDecimal#add(AddableBigDecimal) */
		@Override
		public MinInfinity add(final AddableBigDecimal o) {
			
			assert o != PlusInfinity.PLUS_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return MIN_INF;
		}
		
		/** @see AddableBigDecimal#addDelayed() */
		@Override
		public AddableDelayed<AddableBigDecimal> addDelayed() {
			return new AddableBigDecimalDelayed(false);
		}

		/** @see AddableBigDecimal#subtract(AddableBigDecimal) */
		@Override
		public MinInfinity subtract(final AddableBigDecimal o)  {
			assert o != MIN_INF : "Subtracting minus infinity from minus infinity is not defined";
			
			return MIN_INF;
		}
		
		/** @see AddableBigDecimal#multiply(AddableBigDecimal) */
		@Override
		public AddableBigDecimal multiply(final AddableBigDecimal o) {
			
			assert ! o.equals(ZERO) : "Cannot multiply infinity with 0";
			
			if (this.compareTo(o) > 0) {
				return MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;
		}
		
		/** @see AddableBigDecimal#divide(AddableBigDecimal) */
		@Override
		public MinInfinity divide(final AddableBigDecimal o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && ! o.equals(ZERO) : "Division by " + o;
			return MIN_INF;
		}
		
		/** @see AddableBigDecimal#abs() */
		@Override
		public PlusInfinity abs() {
			return PlusInfinity.PLUS_INF;
		}
		
		/** @see AddableBigDecimal#compareTo(AddableBigDecimal) */
		@Override
		public int compareTo(final AddableBigDecimal o) {
			if(o == MIN_INF) {
				return 0;
			} else {
				return -1;
			}
		}
		
		/** @see AddableBigDecimal#compareTo(AddableBigDecimal, double) */
		@Override
		public int compareTo(final AddableBigDecimal o, double error) {
			return this.compareTo(o);
		}
		
		/** @see AddableBigDecimal#equals(java.lang.Object) */
		@Override
		public boolean equals(final Object o) {
			return o == MIN_INF;
		}
		
		/** @see AddableBigDecimal#equals(AddableBigDecimal, double) */
		@Override
		public boolean equals(final AddableBigDecimal that, double error) {
			return that == MIN_INF;
		}
		
		/** @see AddableBigDecimal#hashCode() */
		@Override
		public int hashCode() {
			return "-INF_BigDecimal".hashCode();
		}
		
		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		public Object readResolve() {
            return MIN_INF;
        }

		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException { }

		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException { }
		
		/**
		 * @see AddableBigDecimal#flipSign()
		 */
		@Override
		public PlusInfinity flipSign() {
			return PlusInfinity.PLUS_INF;
		}

		/** @see AddableBigDecimal#toString() */
		@Override
		public String toString () {
			return "-infinity";
		}

	}

	/**
	 * This class implements the plus infinity element. Adding or subtracting anything
	 * still results in the infinity element.
	 */
	public static class PlusInfinity extends AddableBigDecimal {
		
		/** The singleton PlusInfinity */
		public static final PlusInfinity PLUS_INF = new PlusInfinity ();
	
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance PlusInfinity.PLUS_INF */
		public PlusInfinity() {
			super();
		}
	
		/** @see AddableBigDecimal#intValue() */
		@Override
		public int intValue() {
			return Integer.MAX_VALUE;
		}

		/** @see AddableBigDecimal#doubleValue() */
		@Override
		public double doubleValue() {
			return Double.POSITIVE_INFINITY;
		}

		/** @see AddableBigDecimal#add(AddableBigDecimal) */
		@Override
		public PlusInfinity add(final AddableBigDecimal o) {		
			assert o != MinInfinity.MIN_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return PLUS_INF;
		}
		
		/** @see AddableBigDecimal#addDelayed() */
		@Override
		public AddableDelayed<AddableBigDecimal> addDelayed() {
			return new AddableBigDecimalDelayed(true);
		}

		/** @see AddableBigDecimal#subtract(AddableBigDecimal) */
		@Override
		public PlusInfinity subtract(final AddableBigDecimal o)  {
			assert o != PLUS_INF : "Subtracting plus infinity from plus infinity is not defined";
			
			return PLUS_INF;
		}
		
		/** @see AddableBigDecimal#multiply(AddableBigDecimal) */
		@Override
		public AddableBigDecimal multiply(final AddableBigDecimal o) {
			assert ! o.equals(ZERO) : "Cannot multiply infinity with 0";
			
			if (this.compareTo(o) > 0) {
				return PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;
		}
		
		/** @see AddableBigDecimal#divide(AddableBigDecimal) */
		@Override
		public PlusInfinity divide(final AddableBigDecimal o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && ! o.equals(ZERO) : "Division by " + o;
			return PLUS_INF;
		}
		
		/** @see AddableBigDecimal#abs() */
		@Override
		public PlusInfinity abs() {
			return this;
		}
		
		/** @see AddableBigDecimal#compareTo(AddableBigDecimal) */
		@Override
		public int compareTo(final AddableBigDecimal o) {
			if(o == PLUS_INF) {
				return 0;
			} else {
				return 1;
			}
		}
		
		/** @see AddableBigDecimal#compareTo(AddableBigDecimal, double) */
		@Override
		public int compareTo(final AddableBigDecimal o, double error) {
			return this.compareTo(o);
		}
		
		/** @see AddableBigDecimal#equals(java.lang.Object) */
		@Override
		public boolean equals(final Object o) {
			return o == PLUS_INF;
		}
		
		/** @see AddableBigDecimal#equals(AddableBigDecimal, double) */
		@Override
		public boolean equals(final AddableBigDecimal that, double error) {
			return that == PLUS_INF;
		}
		
		/** @see AddableBigDecimal#hashCode() */
		@Override
		public int hashCode() {
			return "+INF_BigDecimal".hashCode();
		}
		
		/** Method to deserialize the object in such a way that the singleton property is retained.
		 * @return singleton object*/
		public Object readResolve() {
	        return PLUS_INF;
	    }
	
		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException { }
	
		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException { }
		
		/**
		 * @see AddableBigDecimal#flipSign()
		 */
		@Override
		public MinInfinity flipSign() {
			return MinInfinity.MIN_INF;
		}
		
		/** @see AddableBigDecimal#toString() */
		@Override
		public String toString () {
			return "infinity";
		}

	}

	/** Class used to speed up n-ary additions and multiplications
	 * @author Thomas Leaute
	 */
	public static class AddableBigDecimalDelayed implements AddableDelayed<AddableBigDecimal> {
		
		/** The intermediate value */
		private BigDecimal val;
		
		/** \c true if positive. 
		 * Only used for -INF and +INF, which corresponds to when \a val is \c null
		 */
		private boolean infSign;

		/** Constructor
		 * @param val	The initial BigInteger value
		 */
		public AddableBigDecimalDelayed(BigDecimal val) {
			this.val = val;
		}
		
		/** Constructor for -INF and +INF
		 * @param infSign 	\c true if positive
		 */
		public AddableBigDecimalDelayed (boolean infSign) {
			this.infSign = infSign;
		}

		/** @see AddableDelayed#addDelayed(Addable) */
		public void addDelayed(AddableBigDecimal a) {
			
			if (this.val == null) { // I am infinite
				assert (this.infSign ? a != MinInfinity.MIN_INF : a != PlusInfinity.PLUS_INF) : "Adding -INF and +INF";
				
			} else if (a == MinInfinity.MIN_INF) {
				this.val = null;
				this.infSign = false;
				
			} else if (a == PlusInfinity.PLUS_INF) {
				this.val = null;
				this.infSign = true;
				
			} else 
				this.val = this.val.add(a.val);
		}

		/** @see AddableDelayed#isInfinite() */
		public boolean isInfinite() {
			return (this.val == null);
		}

		/** @see AddableDelayed#multiplyDelayed(Addable) */
		public void multiplyDelayed(AddableBigDecimal a) {
			
			if (this.val == null) { // I am infinite
				assert ! a.val.equals(BigInteger.ZERO) : "Multiplying 0 with infinity";
				this.infSign = (this.infSign == (a.val.signum() >= 0));
				
			} else if (a == PlusInfinity.PLUS_INF) {
				assert ! this.val.equals(BigInteger.ZERO) : "Multiplying 0 with +INF";
				this.infSign = (this.val.signum() > 0);
				this.val = null;
				
			} else if (a == MinInfinity.MIN_INF) {
				assert ! this.val.equals(BigInteger.ZERO) : "Multiplying 0 with -INF";
				this.infSign = (this.val.signum() < 0);
				this.val = null;
				
			} else 
				this.val = this.val.multiply(a.val);
		}

		/** @see AddableDelayed#resolve() */
		public AddableBigDecimal resolve() {
			if (this.val == null) // infinite number
				return (this.infSign ? PlusInfinity.PLUS_INF : MinInfinity.MIN_INF);
			return new AddableBigDecimal (this.val);
		}
		
	}

}
