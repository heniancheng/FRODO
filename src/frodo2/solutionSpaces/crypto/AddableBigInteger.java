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
package frodo2.solutionSpaces.crypto;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Random;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.AddableLimited;

/** An Addable number based on a BigInteger
 * @author Thomas Leaute, Eric Zbinden
 */
public class AddableBigInteger implements Addable<AddableBigInteger> {

	/** ZERO */
	private static final AddableBigInteger ZERO = new AddableBigInteger(BigInteger.ZERO);
	
	/** the BigIntger */
	private BigInteger val;
	
	/** Empty constructor */
	public AddableBigInteger () {
		this.val = BigInteger.ZERO;
	}

	/** Constructor from a String
	 * @param val 	String representation of an AddableBigInteger, in which "-infinity" and "infinity" are allowed
	 */
	public AddableBigInteger(String val) {
		
		if (val.equals("-infinity")){
			this.val = BigInteger.valueOf(Long.MIN_VALUE);
		} else if (val.equals("infinity")){
			this.val = BigInteger.valueOf(Long.MAX_VALUE);
		} else {
			this.val = new BigInteger(val);
		}
	}

	/** Random constructor
	 * @param numBits 	number of bits of this AddableBigInteger
	 * @param rnd 		random number generator
	 */
	public AddableBigInteger(int numBits, Random rnd) {
		val = new BigInteger(numBits, rnd);
	}
	
	/** Constructor
	 * @param val The value in a BigInteger form
	 */
	public AddableBigInteger(BigInteger val){
		this.val = val;
	}
	
	/** Constructor
	 * @param val 	The value 
	 */
	public AddableBigInteger(int val){
		this.val = BigInteger.valueOf(val);
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		byte[] byteArray = this.val.toByteArray();
		out.writeInt(byteArray.length);
		out.write(byteArray);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		byte[] byteArray = new byte [in.readInt()];
		in.read(byteArray);
		this.val = new BigInteger (byteArray);
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
	public AddableBigInteger abs() {
		if (this.val.signum() >= 0) return this;
		else return new AddableBigInteger (this.val.abs()); 
	}

	/** @see Addable#add(Addable) */
	public AddableBigInteger add(AddableBigInteger o) {
		if(o == PlusInfinity.PLUS_INF) {
			return PlusInfinity.PLUS_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return MinInfinity.MIN_INF;
		}
		return new AddableBigInteger(this.val.add(o.val));
	}

	/** @see Addable#addDelayed() */
	public AddableDelayed<AddableBigInteger> addDelayed() {
		return new AddableBigIntegerDelayed (this.val);
	}

	/** @see java.lang.Comparable#compareTo(java.lang.Object) */
	public int compareTo(AddableBigInteger o) {
		if(o == PlusInfinity.PLUS_INF) {
			return -1;
		} else if( o == MinInfinity.MIN_INF) {
			return 1;
		}
		return this.val.compareTo(o.val);
	}

	/** @see Addable#divide(Addable) */
	public AddableBigInteger divide(AddableBigInteger o) {
		assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && ! o.equals(ZERO) : "Division by " + o;
		return new AddableBigInteger(this.val.divide(o.val));
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals(Object o){
		
		if (this == o) 
			return true;
		
		if (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF || o == null) 
			return false;
		
		AddableBigInteger o2;
		try {
			o2 = (AddableBigInteger) o;
		} catch (ClassCastException e) {
			// Throwing exceptions is expensive, but you should never compare AddableBigIntegers with non-AddableBigIntegers anyway
			return false;
		}

		return val.equals(o2.val);
	}

	/** @see Addable#flipSign() */
	public AddableBigInteger flipSign() {
		return new AddableBigInteger (this.val.negate());
	}

	/** @see Addable#fromString(java.lang.String) */
	public AddableBigInteger fromString(String str) {
		try {
			return new AddableBigInteger(str);
			
		} catch (NumberFormatException e) { // does not look like a BigInteger
			
			// Try to parse it as a double, and truncate
			return new AddableBigInteger (Integer.toString((int) Double.parseDouble(str)));
		}
	}

	/** @see Addable#getMinInfinity() */
	public AddableBigInteger getMinInfinity() {
		return MinInfinity.MIN_INF;
	}

	/** @see Addable#getPlusInfinity() */
	public AddableBigInteger getPlusInfinity() {
		return PlusInfinity.PLUS_INF;
	}

	/** @see Addable#getZero() */
	public AddableBigInteger getZero() {
		return ZERO;
	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode () {
		return this.val.hashCode();
	}

	/** @see Addable#max(AddableLimited) */
	public AddableBigInteger max(AddableBigInteger o) {
		if (this.compareTo(o) >=0) return this;
		else return o;
	}

	/** @see AddableLimited#min(AddableLimited) */
	public AddableBigInteger min(AddableBigInteger o) {
		if (this.compareTo(o) >=0) return o;
		else return this;
	}
	
	/** @see Addable#intValue() */
	@Override
	public int intValue() {
		return this.val.intValue();
	}

	/** @see Addable#doubleValue() */
	@Override
	public double doubleValue() {
		return this.intValue();
	}

	/**
	 * @return true if this AddableBigInteger is strictly greater than zero
	 */
	public boolean isPositive (){
		if (this.compareTo(ZERO) > 0) return true;
		else return false;
	}

	/** @see Addable#multiply(Addable) */
	public AddableBigInteger multiply(AddableBigInteger o) {
		assert !(this.equals(ZERO) && (o == PlusInfinity.PLUS_INF || o == MinInfinity.MIN_INF)) : "Cannot multiply infinity with 0";
		
		if (o == PlusInfinity.PLUS_INF) { 
			
			if (this.isPositive()) {
				return PlusInfinity.PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;

		} else if (o == MinInfinity.MIN_INF) {
			
			if (this.isPositive()) {
				return MinInfinity.MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;

		} else 
		return new AddableBigInteger(this.val.multiply(o.val));
	}

	/** @see Addable#subtract(Addable) */
	public AddableBigInteger subtract(AddableBigInteger o) {
		if(o == PlusInfinity.PLUS_INF) {
			return MinInfinity.MIN_INF;
		} else if(o == MinInfinity.MIN_INF) {
			return PlusInfinity.PLUS_INF;
		}
		return new AddableBigInteger(this.val.subtract(o.val));
	}

	/** @see Addable#range(Addable, Addable) */
	public Addable<AddableBigInteger>[] range(AddableBigInteger begin, AddableBigInteger end) {
		// @todo Auto-generated method stub
		assert false: "Not Implemented";
		return null;
	}
	
	/** @see Object#toString() */
	@Override
	public String toString(){
		return NumberFormat.getInstance().format(this.val);
	}
	
	/**
	 * This class implements the minus infinity element. Adding or subtracting anything
	 * still results in the infinity element.
	 */
	public static class MinInfinity extends AddableBigInteger {
		
		/** The singleton MinInfinity */
		public static final MinInfinity MIN_INF = new MinInfinity();
	
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance MinInfinity.MIN_INF */
		public MinInfinity() {
			super("-infinity");
		}
	
		/** @see AddableBigInteger#intValue() */
		@Override
		public int intValue() {
			return Integer.MIN_VALUE;
		}

		/** @see AddableBigInteger#doubleValue() */
		@Override
		public double doubleValue() {
			return Double.NEGATIVE_INFINITY;
		}

		/** @see AddableBigInteger#add(AddableBigInteger) */
		@Override
		public MinInfinity add(final AddableBigInteger o) {
			
			assert o != PlusInfinity.PLUS_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return MIN_INF;
		}
		
		/** @see AddableBigInteger#addDelayed() */
		@Override
		public AddableDelayed<AddableBigInteger> addDelayed() {
			return new AddableBigIntegerDelayed (false);
		}

		/** @see AddableBigInteger#subtract(AddableBigInteger) */
		@Override
		public MinInfinity subtract(final AddableBigInteger o)  {
			assert o != MIN_INF : "Subtracting minus infinity from minus infinity is not defined";
			
			return MIN_INF;
		}
		
		/** @see AddableBigInteger#multiply(AddableBigInteger) */
		@Override
		public AddableBigInteger multiply(final AddableBigInteger o) {
			
			assert ! o.equals(ZERO) : "Cannot multiply infinity with 0";
			
			if (this.compareTo(o) > 0) {
				return MIN_INF;
			} else 
				return PlusInfinity.PLUS_INF;
		}
		
		/** @see AddableBigInteger#divide(AddableBigInteger) */
		@Override
		public MinInfinity divide(final AddableBigInteger o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && ! o.equals(ZERO) : "Division by " + o;
			return MIN_INF;
		}
		
		/** @see AddableBigInteger#abs() */
		@Override
		public PlusInfinity abs() {
			return PlusInfinity.PLUS_INF;
		}
		
		/** Compares this AddableInteger with another
		 * @param o 	another AddableInteger
		 * @return 		0 if they are equal, a positive number if this AddableInteger is greater than the input
		 */
		@Override
		public int compareTo(final AddableBigInteger o) {
			if(o == MIN_INF) {
				return 0;
			} else {
				return -1;
			}
		}
		
		/** @see AddableBigInteger#equals(java.lang.Object) */
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

		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException { }

		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException { }
		
		/**
		 * @see AddableBigInteger#flipSign()
		 */
		@Override
		public PlusInfinity flipSign() {
			return PlusInfinity.PLUS_INF;
		}

		/** @see AddableBigInteger#toString() */
		@Override
		public String toString () {
			return "-infinity";
		}

	}

	/**
	 * This class implements the plus infinity element. Adding or subtracting anything
	 * still results in the infinity element.
	 */
	public static class PlusInfinity extends AddableBigInteger {
		
		/** The singleton PlusInfinity */
		public static final PlusInfinity PLUS_INF = new PlusInfinity();
	
		/** @warning DO NOT USE THIS CONSTRUCTOR! Use the instance PlusInfinity.PLUS_INF */
		public PlusInfinity() {
			super("infinity");
		}
	
		/** @see AddableBigInteger#intValue() */
		@Override
		public int intValue() {
			return Integer.MAX_VALUE;
		}

		/** @see AddableBigInteger#doubleValue() */
		@Override
		public double doubleValue() {
			return Double.POSITIVE_INFINITY;
		}

		/** @see AddableBigInteger#add(AddableBigInteger) */
		@Override
		public PlusInfinity add(final AddableBigInteger o) {		
			assert o != MinInfinity.MIN_INF : "Adding plus infinity and minus infinity is not defined!";
			
			return PLUS_INF;
		}
		
		/** @see AddableBigInteger#addDelayed() */
		@Override
		public AddableDelayed<AddableBigInteger> addDelayed() {
			return new AddableBigIntegerDelayed (true);
		}

		/** @see AddableBigInteger#subtract(AddableBigInteger) */
		@Override
		public PlusInfinity subtract(final AddableBigInteger o)  {
			assert o != PLUS_INF : "Subtracting plus infinity from plus infinity is not defined";
			
			return PLUS_INF;
		}
		
		/** @see AddableBigInteger#multiply(AddableBigInteger) */
		@Override
		public AddableBigInteger multiply(final AddableBigInteger o) {
			assert ! o.equals(ZERO) : "Cannot multiply infinity with 0";
			
			if (this.compareTo(o) > 0) {
				return PLUS_INF;
			} else 
				return MinInfinity.MIN_INF;
		}
		
		/** @see AddableBigInteger#divide(AddableBigInteger) */
		@Override
		public PlusInfinity divide(final AddableBigInteger o) {
			assert o != PlusInfinity.PLUS_INF && o != MinInfinity.MIN_INF && ! o.equals(ZERO) : "Division by " + o;
			return PLUS_INF;
		}
		
		/** @see AddableBigInteger#abs() */
		@Override
		public PlusInfinity abs() {
			return this;
		}
		
		/** Compares this AddableInteger with another
		 * @param o 	another AddableInteger
		 * @return 		0 if they are equal, a positive number if this AddableInteger is greater than the input
		 */
		@Override
		public int compareTo(final AddableBigInteger o) {
			if(o == PLUS_INF) {
				return 0;
			} else {
				return 1;
			}
		}
		
		/** @see AddableBigInteger#equals(java.lang.Object) */
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
	
		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException { }
	
		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException { }
		
		/**
		 * @see AddableBigInteger#flipSign()
		 */
		@Override
		public MinInfinity flipSign() {
			return MinInfinity.MIN_INF;
		}
		
		/** @see AddableBigInteger#toString() */
		@Override
		public String toString () {
			return "infinity";
		}

	}

	/** Class used to speed up n-ary additions and multiplications
	 * @author Thomas Leaute
	 */
	public static class AddableBigIntegerDelayed implements AddableDelayed<AddableBigInteger> {
		
		/** The intermediate value */
		private BigInteger val;
		
		/** \c true if positive. 
		 * Only used for -INF and +INF, which corresponds to when \a val is \c null
		 */
		private boolean infSign;

		/** Constructor
		 * @param val	The initial BigInteger value
		 */
		public AddableBigIntegerDelayed(BigInteger val) {
			this.val = val;
		}
		
		/** Constructor for -INF and +INF
		 * @param infSign 	\c true if positive
		 */
		public AddableBigIntegerDelayed (boolean infSign) {
			this.infSign = infSign;
		}

		/** @see AddableDelayed#addDelayed(Addable) */
		public void addDelayed(AddableBigInteger a) {
			
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
		public void multiplyDelayed(AddableBigInteger a) {
			
			if (this.val == null) { // I am infinite
				assert ! a.val.equals(BigInteger.ZERO) : "Multiplying 0 with infinity";
				this.infSign = (this.infSign == (a.isPositive()));
				
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
		public AddableBigInteger resolve() {
			if (this.val == null) // infinite number
				return (this.infSign ? PlusInfinity.PLUS_INF : MinInfinity.MIN_INF);
			return new AddableBigInteger (this.val);
		}
		
	}

}
