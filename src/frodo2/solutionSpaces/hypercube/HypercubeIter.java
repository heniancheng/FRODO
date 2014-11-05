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

package frodo2.solutionSpaces.hypercube;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace.Iterator;

/** An iterator for a Hypercube
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class HypercubeIter < V extends Addable<V>, U extends Addable<U> > extends BasicHypercubeIter<V, U> implements Iterator<V, U> {

	/** Constructor */
	public HypercubeIter() {
		super ();
	}
	
	/** Constructor
	 * @param space 		the BasicHypercube to iterate over
	 * @param variables 	the variables to iterate over; may include variables not in the space
	 * @param domains 		the variables' domains
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value to skip, if any
	 * @warning The input array of variables must contain all of the space's variables, and the input domains must be sub-domains of the space's. 
	 */
	protected HypercubeIter(BasicHypercube<V, U> space, String[] variables, V[][] domains, V[] assignment, U skippedUtil) {
		super(space, variables, domains, assignment, skippedUtil);
	}

	/** Constructor
	 * @param space 		the BasicHypercube to iterate over
	 * @param varOrder 		the order of iteration of the variables
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value to skip, if any
	 * @warning The input array of variables must contain exactly all of the space's variables. 
	 */
	protected HypercubeIter(BasicHypercube<V, U> space, String[] varOrder, V[] assignment, U skippedUtil) {
		super(space, varOrder, assignment, skippedUtil);
	}

	/** Constructor 
	 * @param space 		the BasicHypercube to iterate over
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value to skip, if any
	 */
	protected HypercubeIter(BasicHypercube<V, U> space, V[] assignment, U skippedUtil) {
		super(space, assignment, skippedUtil);
	}

	/** @see Iterator#nextUtility(java.lang.Object, boolean) */
	public U nextUtility(U bound, final boolean minimize) {
		
		U util;
		
		if (minimize) {
			while ( (util = super.nextUtility()) != null) {
				if (util.compareTo(bound) < 0) {
					return util;
				}
			}
		} else  { // maximizing
			while ( (util = super.nextUtility()) != null) {
				if (util.compareTo(bound) > 0) {
					return util;
				}
			}
		}
		
		return null;
	}

	/** @see Iterator#getCurrentUtility(java.lang.Object, boolean) */
	public U getCurrentUtility(U bound, final boolean minimize) {
		return super.getCurrentUtility();
	}

}
