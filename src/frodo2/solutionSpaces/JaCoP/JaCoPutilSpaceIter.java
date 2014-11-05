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

package frodo2.solutionSpaces.JaCoP;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.HypercubeIter;


/** A solution iterator for JaCoPutilSpace
 * @author Arnaud Jutzeler, Thomas Leaute
 * @param <U> the type used for utility values
 */
public class JaCoPutilSpaceIter < U extends Addable<U> > extends HypercubeIter<AddableInteger, U>{
	
	/** The JaCoPutilSpace we are iterating over */
	protected JaCoPutilSpace<U> space;
	
	/** Constructor
	 * @param space 		the space over which to iterate
	 * @param variables 	the variable order for the iteration
	 * @param domains 		the domains of the variables
	 */
	public JaCoPutilSpaceIter(JaCoPutilSpace<U> space, String[] variables, AddableInteger[][] domains){
		this(space, variables, domains, (AddableInteger[]) Array.newInstance(AddableInteger.class, variables.length));
	}
	
	/** Constructor
	 * @param space 		the space over which to iterate
	 * @param variables 	the variable order for the iteration
	 * @param domains 		the domains of the variables
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 */
	public JaCoPutilSpaceIter(JaCoPutilSpace<U> space, String[] variables, AddableInteger[][] domains, AddableInteger[] assignment){
		this.space = space;
		this.variables = variables;
		this.domains = domains;
		
		this.nbrVars = variables.length;
		this.solution = assignment;
		for (int i = 0; i < nbrVars; i++)
			solution[i] = domains[i][0];
		
		this.valIndexes = new int [nbrVars];
		Arrays.fill(this.valIndexes, 0);
		valIndexes[nbrVars - 1] = -1;
		
		// Compute the steps, knowing that the two variable orders may differ, and the input domains may be sub-domains of the space's domains, and in a different order
		this.steps = new int [nbrVars][];
		nbrSolLeft = 1;
		for (int i = 0; i < nbrVars; i++) {
			int domSize = domains[i].length;
			steps[i] = new int [domSize];
			nbrSolLeft *= domSize;
		}
		this.nbrSols = this.nbrSolLeft;
		
		// For each variable, compute its index in the input array
		HashMap<String, Integer> indexes = new HashMap<String, Integer> (nbrVars);
		for (int i = 0; i < nbrVars; i++) 
			indexes.put(variables[i], i);
		
		int nbrSpaceVars = space.getVariables().length;
		int step = 1;
		for (int i = nbrSpaceVars - 1; i >= 0; i--) {
			AddableInteger[] spaceDom = space.getDomain(i);
			int spaceDomSize = spaceDom.length;
			
			// Look up the index for this variable in the input variable array
			Integer index = indexes.get(space.getVariable(i));
			assert index != null : "The input array of variables " + Arrays.asList(variables) + " must contain all of the space's variables " + Arrays.asList(space.getVariables());
			
			// For each of this variable's values in the input domain array, compute its absolute incremental step in the space's utility array
			AddableInteger[] dom = domains[index];
			int domSize = dom.length;
			int[] mySteps = new int [domSize];
			for (int j = 0; j < domSize; j++) {
				AddableInteger val = dom[j];
				
				// Go through the values in the space's domain for this variable
				assert Hypercube.sub(dom, spaceDom).length == 0 : 
					"The input domain " + Arrays.asList(dom) + " for variable " + space.getVariable(i) + " is not a sub-domain of the space's: " + Arrays.asList(spaceDom);
				int myStep = 0;
				for ( ; myStep < spaceDomSize; myStep++) 
					if (val.equals(spaceDom[myStep])) 
						break;
				mySteps[j] = myStep * step;
			}
			utilIndex += mySteps[0];
			
			// Convert from absolute steps to relative steps
			int lastStep = mySteps[domSize - 1];
			for (int j = domSize - 1; j > 0; j--) 
				mySteps[j] = mySteps[j] - mySteps[j - 1];
			mySteps[0] = mySteps[0] - lastStep;
			
			steps[index] = mySteps;
			step *= spaceDomSize;
		}
		utilIndex -= steps[nbrVars - 1][0];
		
	}
	
	/** @see HypercubeIter#iter() */
	@Override
	protected int iter(){
		
		final AddableInteger[][] myDoms = this.domains;
		final int[] myValIndexes = this.valIndexes;
		final AddableInteger[] mySol = this.solution;
		final int[][] mySteps = this.steps;
		int myUtilIndex = this.utilIndex;
		
		// Iterates over the variables (in reversed order) to find the next one(s) to be iterated
		int varIndex = this.nbrVars - 1;
		for ( ; varIndex >= 0; varIndex--) {
			
			// Check if we have exhausted all values in the domain of the varIndex'th variable
			AddableInteger[] dom = myDoms[varIndex];
			int valIndex = myValIndexes[varIndex];
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
		
		this.utility = space.getUtility(myUtilIndex);
		this.nbrSolLeft--;
		
		return this.utilIndex = myUtilIndex;
	}
	
	/** @see HypercubeIter#setCurrentUtility(java.lang.Object) */
	@Override
	public void setCurrentUtility(U util) {
		utility = util;
		space.setUtility(this.utilIndex, util);
	}
	
	/** @see HypercubeIter#getCurrentUtility() */
	@Override
	public U getCurrentUtility() {
		return this.utility;
	}

	/** @see HypercubeIter#nextUtility() */
	@Override
	public U nextUtility() {
		
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		this.iter();
		
		return this.utility;
	}
}
