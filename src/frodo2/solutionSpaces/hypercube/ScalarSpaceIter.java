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

/** A solution iterator for a scalar space
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class ScalarSpaceIter < V extends Addable<V>, U extends Addable<U> > extends ScalarBasicSpaceIter<V, U>
implements Iterator<V, U> {
	
	/** Empty constructor */
	protected ScalarSpaceIter () { }
	
	/** Constructor 
	 * @param utility 	 		the utility value
	 * @param infeasibleUtil 	the infeasible utility
	 * @param skippedUtil 		the utility value to skip, if any
	 */
	public ScalarSpaceIter (U utility, U infeasibleUtil, U skippedUtil) {
		super (utility, infeasibleUtil, skippedUtil);
	}
	
	/** Constructor
	 * @param utility 			the utility value
	 * @param variables 		the variables to iterate over; may include variables not in the space
	 * @param domains 			the variables' domains
	 * @param infeasibleUtil 	the infeasible utility
	 * @param skippedUtil 		the utility value to skip, if any
	 */
	public ScalarSpaceIter (U utility, String[] variables, V[][] domains, U infeasibleUtil, U skippedUtil) {
		super (utility, variables, domains, null, infeasibleUtil, skippedUtil);
	}

	/** Constructor
	 * @param utility 			the utility value
	 * @param variables 		the variables to iterate over; may include variables not in the space
	 * @param domains 			the variables' domains
	 * @param assignment 		An array that will be used as the output of nextSolution()
	 * @param infeasibleUtil 	the infeasible utility
	 * @param skippedUtil 		the utility value to skip, if any
	 */
	protected ScalarSpaceIter (U utility, String[] variables, V[][] domains, V[] assignment, U infeasibleUtil, U skippedUtil) {
		super (utility, variables, domains, assignment, infeasibleUtil, skippedUtil);
	}

	/** @see Iterator#nextUtility(java.lang.Object, boolean) */
	public U nextUtility(U bound, final boolean minimize) {
		
		if (! this.hasNext()) 
			return null;
		
		// Check whether the scalar utility is better than the bound
		if ((minimize ? super.utility.compareTo(bound) < 0 : super.utility.compareTo(bound) > 0))
			return this.nextUtility();
		
		// No more better solutions
		super.nbrSolLeft = 0;
		super.utility = null;
		super.solution = null;
		return null;
	}

	/** @see Iterator#getCurrentUtility(java.lang.Object, boolean) */
	public U getCurrentUtility(U bound, final boolean minimize) {
		return this.getCurrentUtility();
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "ScalarSpaceIter for utility " + super.utility;
	}

}
