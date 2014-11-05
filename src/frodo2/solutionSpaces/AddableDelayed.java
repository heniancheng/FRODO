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

/**
 * @author Brammert Ottens, 30 mrt 2010
 * 
 * Class used to delay the creation of an object of type T
 * when a large number of additions must be performed
 * 
 * @param <T> the type of the Addable 
 * 
 */
public interface AddableDelayed < T extends Addable<T> >{

	/**
	 * Add an object of type T to this class
	 * @author Brammert Ottens, 30 mrt 2010
	 * @param a an object of type T
	 */
	public void addDelayed(T a);
	
	/**
	 * Multiply an object of type T with this class
	 * @author Brammert Ottens, 30 mrt 2010
	 * @param a an object of type T
	 */
	public void multiplyDelayed(T a);
	
	/**
	 * Create the object of type T
	 * @author Brammert Ottens, 30 mrt 2010
	 * @return an object of type T
	 */
	public T resolve();
	
	/**
	 * @author Brammert Ottens, 8 apr 2010
	 * @return \c true when the sum is infinite
	 */
	public boolean isInfinite();
}
