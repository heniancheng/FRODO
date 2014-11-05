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

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;

/** A solution iterator for a scalar space
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class ScalarBasicSpaceIter < V extends Addable<V>, U extends Serializable > 
implements Iterator<V, U> {
	
	/** Current utility value */
	protected U utility;
	
	/** Current variable assignments */
	protected V[] solution;
	
	/** The order of iteration over the variables */
	private String[] variables;
	
	/** The number of variables */
	private int nbrVars;

	/** The variables' domains */
	private V[][] domains;
	
	/** For each variable, the index in its domain of the current assignment */
	private int[] valIndexes;
	
	/** The number of solutions left to iterate over */
	protected long nbrSolLeft = 1;
	
	/** The total number of solutions to iterate over */
	private long nbrSols = 1;

	/** The utility value that should be skipped, if any */
	protected U skippedUtil;
	
	/** The infeasible utility */
	protected U inf;
	
	/** Empty constructor */
	protected ScalarBasicSpaceIter () { }
	
	/** Constructor 
	 * @param utility 	 		the utility value
	 * @param infeasibleUtil 	the infeasible utility
	 * @param skippedUtil 		the utility value that should be skipped, if any
	 */
	public ScalarBasicSpaceIter (U utility, U infeasibleUtil, U skippedUtil) {
		this.utility = utility;
		this.inf = infeasibleUtil;
		this.skippedUtil = skippedUtil;
	}
	
	/** Constructor
	 * @param utility 			the utility value
	 * @param variables 		the variables to iterate over; may include variables not in the space
	 * @param domains 			the variables' domains
	 * @param assignment 		An array that will be used as the output of nextSolution()
	 * @param infeasibleUtil 	the infeasible utility
	 * @param skippedUtil 		the utility value that should be skipped, if any
	 */
	protected ScalarBasicSpaceIter (U utility, String[] variables, V[][] domains, V[] assignment, U infeasibleUtil, U skippedUtil) {
		this.init(utility, variables, domains, assignment);
		this.inf = infeasibleUtil;
		this.skippedUtil = skippedUtil;
	}
	
	/** Helper method called by the constructor
	 * @param utility 		the utility value
	 * @param variables 	the variables to iterate over; may include variables not in the space
	 * @param domains 		the variables' domains
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 */
	@SuppressWarnings("unchecked")
	protected void init (U utility, String[] variables, V[][] domains, V[] assignment) {
		
		this.utility = utility;
		
		this.variables = variables;
		this.domains = domains;
		
		this.nbrVars = variables.length;
		this.solution = (assignment != null ? assignment : (V[]) Array.newInstance(domains.getClass().getComponentType().getComponentType(), nbrVars));
		for (int i = 0; i < nbrVars; i++) 
			solution[i] = domains[i][0];
		
		if (variables.length == 0) 
			return;
		
		this.valIndexes = new int [nbrVars];
		Arrays.fill(this.valIndexes, 0);
		valIndexes[nbrVars - 1] = -1;
		
		for (int i = 0; i < nbrVars; i++) {
			assert Math.log(this.nbrSolLeft) + Math.log(domains[i].length) < Math.log(Long.MAX_VALUE) : "Too many solutions";
			nbrSolLeft *= domains[i].length;
		}
		
		this.nbrSols = this.nbrSolLeft;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getNbrSolutions() */
	public long getNbrSolutions() {
		return this.nbrSols;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextSolution() 
	 * @warning Returns a pointer to an internal data structure that will be modified by subsequent calls to next(). 
	 */
	public V[] nextSolution() {
		
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		if (this.solution == null) {
			this.nbrSolLeft--;
			return null;
		}
		
		if (this.skippedUtil != null && this.skippedUtil.equals(this.utility)) {
			this.nbrSolLeft = 0;
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		this.iter();
		return this.solution;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextUtility() */
	public U nextUtility() {
		
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		if (this.solution == null) {
			this.nbrSolLeft--;
			return this.utility;
		}
		
		if (this.skippedUtil != null && this.skippedUtil.equals(this.utility)) {
			this.nbrSolLeft = 0;
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		this.iter();
		return this.utility;
	}

	/** Moves to the next solution */
	protected void iter () {
		
		// Iterates over the variables (in reversed order) to find the next one(s) to be iterated
		int varIndex = this.nbrVars - 1;
		for ( ; varIndex >= 0; varIndex--) {
			
			// Check if we have exhausted all values in the domain of the varIndex'th variable
			V[] dom = this.domains[varIndex];
			int valIndex = valIndexes[varIndex];
			if (valIndex == dom.length - 1) {
				
				// Reset the variable to its first domain value
				valIndexes[varIndex] = 0;
				solution[varIndex] = dom[0];
				
				// Increment the previous variable
				continue;
			}
			
			else { // increment the value for this variable
				valIndex = ++valIndexes[varIndex];
				solution[varIndex] = dom[valIndex];
				break;
			}
		}
		
		this.nbrSolLeft--;
	}
	
	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentSolution() 
	 * @warning Returns a pointer to an internal data structure that will be modified by subsequent calls to next(). 
	 */
	public V[] getCurrentSolution() {
		return this.solution;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentUtility() */
	public U getCurrentUtility() {
		return this.utility;
	}

	/** Sets the utility of the current solution
	 * @param util 	the new utility
	 */
	public void setCurrentUtility(U util) {
		this.utility = util;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getVariablesOrder() */
	public String[] getVariablesOrder() {
		return this.variables;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getDomains() */
	public V[][] getDomains() {
		return this.domains;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#hasNext() */
	public boolean hasNext() {
		return (this.nbrSolLeft > 0);
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#update() */
	public void update() {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}
}
