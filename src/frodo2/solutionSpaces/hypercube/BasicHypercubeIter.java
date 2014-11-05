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
import java.util.HashMap;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;

/** A solution iterator for BasicHypercubes
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class BasicHypercubeIter < V extends Addable<V>, U extends Serializable > 
implements Iterator<V, U> {
	
	/** The space's utility array */
	protected U[] utilities;
	
	/** Current utility value */
	protected U utility;
	
	/** The utility value that is skipped in sparse iterator mode */
	private U skippedUtil;
	
	/** The index of the current utility value in the utility array */
	protected int utilIndex;
	
	/** For each variable: 
	 * - the first entry is the incremental step when changing from the last value back to the first
	 * - entry \c i is the incremental step in the space's utility array when changing the variable's value from value at index \c i \c - \c 1 to value at index \c i (in the input domain array)
	 */
	protected int[][] steps;
	
	/** Current variable assignments */
	protected V[] solution;
	
	/** The BasicHypercube we are iterating over */
	protected BasicHypercube<V, U> space;
	
	/** The order of iteration over the variables */
	protected String[] variables;
	
	/** The number of variables */
	protected int nbrVars;

	/** The variables' domains */
	protected V[][] domains;
	
	/** For each variable, the index in its domain of the current assignment */
	protected int[] valIndexes;
	
	/** The number of solutions left to iterate over */
	protected long nbrSolLeft;
	
	/** The total number of solutions to iterate over */
	protected long nbrSols;
	
	/** Empty constructor */
	protected BasicHypercubeIter () { }
	
	/** Constructor 
	 * @param space 		the BasicHypercube to iterate over
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value to skip, if any
	 */
	@SuppressWarnings("unchecked")
	protected BasicHypercubeIter (BasicHypercube<V, U> space, V[] assignment, U skippedUtil) {
		
		this.space = space;
		this.utilities = space.values;
		this.variables = space.variables;
		this.domains = space.domains;
		this.nbrSolLeft = space.number_of_utility_values;
		this.nbrSols = this.nbrSolLeft;
		this.skippedUtil = skippedUtil;
		
		this.nbrVars = space.variables.length;
		this.solution = (assignment != null ? assignment : (V[]) Array.newInstance(space.classOfV, nbrVars));
		for (int i = 0; i < nbrVars; i++) 
			solution[i] = space.domains[i][0];
		
		this.valIndexes = new int [nbrVars];
		Arrays.fill(this.valIndexes, 0);
		valIndexes[nbrVars - 1] = -1;
		
		this.steps = new int [nbrVars][];
		int step = 1;
		for (int i = nbrVars - 1; i >= 0; i--) {
			int nbrVals = domains[i].length;
			int[] mySteps = new int [nbrVals];
			Arrays.fill(mySteps, 1, nbrVals, step);
			mySteps[0] = - step * (nbrVals - 1);
			steps[i] = mySteps;
			step *= nbrVals;
		}
		utilIndex = - steps[nbrVars - 1][0];
	}
	
	/** Constructor
	 * @param space 		the BasicHypercube to iterate over
	 * @param variables 	the variables to iterate over; may include variables not in the space
	 * @param domains 		the variables' domains
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value that should be skipped, if any
	 * @warning The input array of variables must contain all of the space's variables, and the input domains must be sub-domains of the space's. 
	 */
	@SuppressWarnings("unchecked")
	protected BasicHypercubeIter (final BasicHypercube<V, U> space, final String[] variables, final V[][] domains, final V[] assignment, U skippedUtil) {
		
		this.space = space;
		this.utilities = space.values;
		this.variables = variables;
		this.domains = domains;
		this.skippedUtil = skippedUtil;
		
		this.nbrVars = variables.length;
		this.solution = (assignment != null ? assignment : (V[]) Array.newInstance(space.classOfV, nbrVars));
		for (int i = this.nbrVars - 1; i >= 0; i--) 
			solution[i] = domains[i][0];
		
		this.valIndexes = new int [nbrVars];
//		Arrays.fill(this.valIndexes, 0);
		valIndexes[nbrVars - 1] = -1;
		
		// Compute the steps, knowing that the two variable orders may differ, and the input domains may be sub-domains of the space's domains, and in a different order
		this.steps = new int [nbrVars][];
		nbrSolLeft = 1;
		int domSize;
		for (int i = this.nbrVars - 1; i >= 0; i--) {
			domSize = domains[i].length;
			steps[i] = new int [domSize];
			nbrSolLeft *= domSize;
		}
		this.nbrSols = this.nbrSolLeft;
		
		// For each variable, compute its index in the input array
		HashMap<String, Integer> indexes = new HashMap<String, Integer> (nbrVars);
		for (int i = this.nbrVars - 1; i >= 0; i--) 
			indexes.put(variables[i], i);
		
		int step = 1;
		V[] spaceDom;
		int spaceDomSize;
		Integer index;
		V[] dom;
		int[] mySteps;
		int j;
		V val;
		int myStep;
		int lastStep;
		for (int i = space.variables.length - 1; i >= 0; i--) {
			spaceDomSize = (spaceDom = space.domains[i]).length;
			
			// Look up the index for this variable in the input variable array
			index = indexes.get(space.variables[i]);
			assert index != null : "The input array of variables " + Arrays.asList(variables) + " must contain all of the space's variables " + Arrays.asList(space.variables);
			
			// For each of this variable's values in the input domain array, compute its absolute incremental step in the space's utility array
			dom = domains[index];
			domSize = dom.length;
			mySteps = new int [domSize];
			for (j = 0; j < domSize; j++) {
				val = dom[j];
				
				// Go through the values in the space's domain for this variable
				assert Hypercube.sub(dom, spaceDom).length == 0 : 
					"The input domain " + Arrays.asList(dom) + " for variable " + space.variables[i] + " is not a sub-domain of the space's: " + Arrays.asList(spaceDom);
				myStep = 0;
				for ( ; myStep < spaceDomSize; myStep++) 
					if (val.equals(spaceDom[myStep])) 
						break;
				mySteps[j] = myStep * step;
			}
			utilIndex += mySteps[0];
			
			// Convert from absolute steps to relative steps
			lastStep = mySteps[domSize - 1];
			for (j = domSize - 1; j > 0; j--) 
				mySteps[j] -= mySteps[j - 1];
			mySteps[0] -= lastStep;
			
			steps[index] = mySteps;
			step *= spaceDomSize;
		}
		utilIndex -= steps[nbrVars - 1][0];
	}

	/** Constructor
	 * @param space 		the BasicHypercube to iterate over
	 * @param varOrder 		the order of iteration of the variables
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value to skip, if any
	 * @warning The input array of variables must contain exactly all of the space's variables. 
	 */
	@SuppressWarnings("unchecked")
	protected BasicHypercubeIter (BasicHypercube<V, U> space, String[] varOrder, V[] assignment, U skippedUtil) {
		
		assert Hypercube.sub(space.variables, varOrder).length == 0 && Hypercube.sub(varOrder, space.variables).length == 0 : 
			"Only the order of variables may differ between the input variable array " + Arrays.asList(varOrder) + " and the space's " + Arrays.asList(space.variables);
		
		this.space = space;
		this.utilities = space.values;
		this.variables = varOrder;
		this.nbrVars = space.variables.length;
		this.nbrSolLeft = space.number_of_utility_values;
		this.nbrSols = this.nbrSolLeft;
		this.skippedUtil = skippedUtil;
		
		// Re-order the domains 
		this.solution = (assignment != null ? assignment : (V[]) Array.newInstance(space.classOfV, nbrVars));
		this.domains = (V[][]) Array.newInstance(this.solution.getClass(), nbrVars);
		for (int i = 0; i < nbrVars; i++) 
			solution[i] = (this.domains[i] = space.getDomain(varOrder[i]))[0];
		
		this.valIndexes = new int [nbrVars];
		Arrays.fill(this.valIndexes, 0);
		valIndexes[nbrVars - 1] = -1;
		
		// Compute the steps, knowing that the two variable orders may differ
		this.steps = new int [nbrVars][];
		
		// For each variable, compute its index in the input array
		HashMap<String, Integer> indexes = new HashMap<String, Integer> (nbrVars);
		for (int i = 0; i < nbrVars; i++) 
			indexes.put(variables[i], i);
		
		// Reverse-iterate over the space's variables
		int step = 1;
		for (int i = nbrVars - 1; i >= 0; i--) {
			
			// Look up the index for this variable in the input variable array
			Integer index = indexes.get(space.variables[i]);
						
			int nbrVals = space.domains[i].length;
			int[] mySteps = new int [nbrVals];
			Arrays.fill(mySteps, 1, nbrVals, step);
			mySteps[0] = - step * (nbrVals - 1);
			steps[index] = mySteps;
			step *= nbrVals;
		}
		utilIndex -= steps[nbrVars - 1][0];
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
		
		int index = this.iter();
		
		final U inf = this.skippedUtil;
		if (inf != null) {
			final BasicHypercube<V, U> space = this.space;
			U[] utils = this.utilities;
			U util = null;
			
			space.incrNCCCs(1);
			while (inf.equals(util = utils[index]) && this.nbrSolLeft > 0) {
				index = this.iter();
				space.incrNCCCs(1);
			}

			if (inf.equals(util)) { // I have not found any next feasible solution
				this.utility = null;
				this.solution = null;
			} else 
				this.utility = util;
		}
		
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
		
		int index = this.iter();
		
		final U inf = this.skippedUtil;
		if (inf == null) {
			this.space.incrNCCCs(1);
			return this.utility = this.utilities[index];
			
		} else {
			final BasicHypercube<V, U> space = this.space;
			U[] utils = this.utilities;
			U util = null;
			
			space.incrNCCCs(1);
			while (inf.equals(util = utils[index]) && this.nbrSolLeft > 0) { //util==infinity&&nbrsoleft>0
				index = this.iter();
				space.incrNCCCs(1);
			}

			if (inf.equals(util)) { // I have not found any next feasible solution
				this.solution = null;
				return this.utility = null;
			} else 
				return this.utility = util;
		}
	}

	/** Moves to the next solution 
	 * @return the new utilIndex
	 */
	protected int iter () {
		
		final V[][] myDoms = this.domains;
		final int[] myValIndexes = this.valIndexes;
		final V[] mySol = this.solution;
		final int[][] mySteps = this.steps;
		int myUtilIndex = this.utilIndex;
		
		// Iterates over the variables (in reversed order) to find the next one(s) to be iterated
		V[] dom;
		int valIndex;
		for (int varIndex = this.nbrVars - 1 ; varIndex >= 0; varIndex--) {
			
			// Check if we have exhausted all values in the domain of the varIndex'th variable
			dom = myDoms[varIndex];
			valIndex = myValIndexes[varIndex];
			if (valIndex == dom.length - 1) {
				
				// Reset the variable to its first domain value
				myValIndexes[varIndex] = 0;
				mySol[varIndex] = dom[0];
				myUtilIndex += mySteps[varIndex][0];
				
				// Increment the previous variable
				continue;
			}
			
			else { // increment the value for this variable
				valIndex = ++myValIndexes[varIndex];
				mySol[varIndex] = dom[valIndex];
				myUtilIndex += mySteps[varIndex][valIndex];
				break;
			}
		}
		
		this.utility = null;
		this.nbrSolLeft--;
		
		return this.utilIndex = myUtilIndex;
	}
	
	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentSolution() 
	 * @warning Returns a pointer to an internal data structure that will be modified by subsequent calls to next(). 
	 */
	public V[] getCurrentSolution() {
		return this.solution;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentUtility() */
	public U getCurrentUtility() {
		
		if (this.getCurrentSolution() != null && this.utility == null) {
			this.utility = this.utilities[this.utilIndex];
			this.space.incrNCCCs(1);
		}
		
		return this.utility;
	}

	/** Sets the utility of the current solution
	 * @param util 	the new utility
	 */
	public void setCurrentUtility(U util) {
		this.utility = util;
		this.utilities[this.utilIndex] = util;
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
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		StringBuilder builder = new StringBuilder ("BasicHypercubeIter\n");
		builder.append("\tnbrVars: " + this.nbrVars + "\n");
		builder.append("\tvariables:\n");
		for (int i = 0; i < this.nbrVars; i++) 
			builder.append("\t\t" + this.variables[i] + ":\t" + Arrays.asList(this.domains[i]) + "\n");
		
		builder.append("\tnbrSols: " + this.nbrSols + "\n");
		builder.append("\tnbrSolLeft: " + this.nbrSolLeft + "\n");
//		builder.append("\tsteps: " + this.steps + "\n");
//		builder.append("\tvalIndexes: " + this.valIndexes + "\n");
		builder.append("\tsolution: " + (this.solution == null ? "null" : Arrays.asList(this.solution)) + "\n");
		builder.append("\tutilIndex: " + this.utilIndex + "\n");
		builder.append("\tutility: " + this.utility + "\n");
		
		builder.append("\tspace: " + this.space);
		return builder.toString();
	}
}
