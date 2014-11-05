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

/** Defines the methods add() and also multiply(). 
 * 
 * @author Nacereddine Ouaret, Thomas Leaute, Radoslaw Szymanek, Brammert Ottens
 *
 * @warning when creating an Addable class, make sure there is a constructor that takes in no arguments. 
 * @param <T> the type of objects to be added to or multiplied with an object of this class
 */

public interface Addable< T extends Addable<T> > extends Comparable<T>, AddableLimited<T, T> {
	
	/** @return the value of this Addable as an int, when this makes sense */
	public int intValue ();
	
	/** @return the value of this Addable as a double, when this makes sense */
	public double doubleValue ();
	
	/** Parses a string to construct a new instance 
	 * @param str 	the string
	 * @return 		a new instance
	 */
	public T fromString (String str);
	
	/** Adds an object to this object
	 * @param o 	the object to be added
	 * @return 		object obtained by the sum of this addable with the input object o
	 */
	public T add(T o);
	
	/**
	 * Creates an object with the same value as this object, used to delay
	 * the resolution of a large number of additions
	 * @author Brammert Ottens, 30 mrt 2010
	 * @return an object that allows the addition to be delayed
	 */
	public AddableDelayed<T> addDelayed();
	
	/** Subtracts an object from this object
	 * @param o 	the object to be subtracted
	 * @return 		object obtained by the subtraction of the input object o from this addable
	 */
	
	public T subtract(T o);
	
	/** Multiplies an object with this object
	 * @param o 	the object to be multiplied with
	 * @return 		object obtained by the product of this Addable with the input object o
	 */
	public T multiply (T o);
	
	/**
	 * The equals function must be implemented so that the hashmap storing the steps 
	 * for each value can find the proper step for the provided value.
	 * @param o 	the object to be checked for equality
	 * @return true if they are equal.
	 */
	public boolean equals(Object o);

	/**
	 * The hashCode function must be implemented so that the hashmap storing the steps 
	 * for each value can find the proper step for the provided value.
	 * @return hash code for this object
	 */
	public int hashCode();
	
	/** Returns a neutral object for the addition operation ("zero")
	 * @return a zero object
	 */
	public T getZero();
	
	/** Returns the "+infinity" object
	 * 
	 * Adding +infinity to any object (except -infinity) should always return +infinity. 
	 * The +infinity object should be a singleton, so that one can use == to check if an object is +infinity. 
	 * @return the +infinity object
	 */
	public T getPlusInfinity();
	
	/** Returns the "-infinity" object
	 * 
	 * Adding -infinity to any object (except +infinity) should always return -infinity. 
	 * The -infinity object should be a singleton, so that one can use == to check if an object is -infinity. 
	 * @return the -infinity object
	 */
	public T getMinInfinity();
	
	/** @return the absolute value of this Addable */
	public T abs();
	
	/** @param o the number to divide with
	 * @return the division of this number by o*/
	public T divide(T o);
	
	/**
	 * Flip the sign of this number
	 * @author Brammert Ottens, 9 sep 2009
	 * @return -o
	 */
	public T flipSign();
	
	/**
	 * Returns an array containing all the values between
	 * \c begin and \c end
	 * @author Brammert Ottens, 3 feb 2010
	 * @param begin the smallest element of the range
	 * @param end   the biggest element of the range
	 * @return the range of values between begin and end
	 */
	public Addable<T>[] range(T begin, T end);
}
