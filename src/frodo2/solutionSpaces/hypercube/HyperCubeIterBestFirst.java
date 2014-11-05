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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst;

/** An iterator in best-first order
 * @author Brammert Ottens, 20 nov 2009
 * @param <V> type used for domain values
 * @param <U> type used for utility values
 */
public class HyperCubeIterBestFirst < V extends Addable<V>, U extends Addable<U> > extends HypercubeIter<V, U> implements IteratorBestFirst<V, U>{

	/** Ordered list of assignment */
	private PriorityQueue<Assignment<U>> orderedAssignments;
	
	/** Array used to calculate assignment based on index in utility array */
	private int[] indexSteps;
	
	/** Constructor 
	 * @param space 	the BasicHypercube to iterate over
	 * @param maximize \c true when values are to be ordered decreasingly, and \c false otherwise
	 */
	@SuppressWarnings("unchecked")
	public HyperCubeIterBestFirst (BasicHypercube<V, U> space, boolean maximize) {
		super();
		
		U infeasibleUtil = null;
		if(maximize)
			infeasibleUtil = space.getUtility(0).getMinInfinity();
		else
			infeasibleUtil = space.getUtility(0).getPlusInfinity();
		
		this.space = space;
		this.utilities = space.values;
		this.variables = space.variables;
		this.domains = space.domains;
		this.nbrSolLeft = space.number_of_utility_values;
		this.nbrSols = this.nbrSolLeft;
		
		this.nbrVars = space.variables.length;
		this.solution = (V[]) Array.newInstance(space.classOfV, nbrVars);
		for (int i = 0; i < nbrVars; i++) 
			solution[i] = space.domains[i][0];
		
		this.valIndexes = new int [nbrVars];
		Arrays.fill(this.valIndexes, 0);
		valIndexes[nbrVars - 1] = -1;
		
		this.steps = new int [nbrVars][];
		this.indexSteps = new int[nbrVars];
		int step = 1;
		for (int i = nbrVars - 1; i >= 0; i--) {
			int nbrVals = domains[i].length;
			int[] mySteps = new int [nbrVals];
			Arrays.fill(mySteps, 1, nbrVals, step);
			mySteps[0] = - step * (nbrVals - 1);
			steps[i] = mySteps;
			indexSteps[i] = step;
			step *= nbrVals;
		}
		utilIndex = - steps[nbrVars - 1][0];
			
		
		if(maximize)
			orderedAssignments = new PriorityQueue<Assignment<U>>(1, new MaximizeComp<U>());
		else
			orderedAssignments = new PriorityQueue<Assignment<U>>(1, new MinimizeComp<U>());
		
		int index = 0;
		while(index < this.nbrSols) {
			U utility = space.getUtility(index);
			if(utility != infeasibleUtil)
				orderedAssignments.add(new Assignment<U>(index, utility));
			index++;
		}
		
		nbrSolLeft = orderedAssignments.size();
		nbrSols = nbrSolLeft;
	}
	
	
	/**
	 * Class containing a single assignment with its utility
	 * 
	 * @author Brammert Ottens, 20 nov 2009
	 * @param <U> type used for utility values
	 *
	 */
	private static class Assignment < U extends Addable<U>> {
		
		/** the index of the utility in the space utility array. Based on this the assignment can be determined*/
		private int assignment;
		
		/** The utility corresponding to the assignment */
		private U util;
		
		/**
		 * Constructor
		 * 
		 * @param assignment the assignment
		 * @param utilIndex the index to the corresponding utility
		 */
		public Assignment(int assignment, U utilIndex) {
			this.assignment = assignment;
			this.util = utilIndex;
		}
	}
	
	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextSolution() 
	 * @warning Returns a pointer to an internal data structure that will be modified by subsequent calls to next(). 
	 */
	@Override
	public V[] nextSolution() {
		
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		Assignment<U> ass = this.orderedAssignments.poll();
		this.nbrSolLeft--;
		this.indexToAssignment(ass.assignment);
		this.utility = ass.util;
		return this.solution;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextUtility() */
	@Override
	public U nextUtility() {
		
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}
		
		Assignment<U> ass = this.orderedAssignments.poll();
		this.nbrSolLeft--;
		this.indexToAssignment(ass.assignment);
		this.utility = ass.util;
		return this.utility;
	}
	
	/**
	 * @see BasicHypercubeIter#setCurrentUtility(java.io.Serializable)
	 */
	@Override
	public void setCurrentUtility(U util) {}

	/**
	 * Given the index in the utility array, this method computes the corresponding
	 * value assignment
	 * @author Brammert Ottens, 23 nov 2009
	 * @param index the index in the utility array
	 */
	private void indexToAssignment(int index) {
		int i = 0;
		for(; i < nbrVars - 1; i++) {
			int step = indexSteps[i];
			int valIndex = index/step;
			index -= valIndex*step;
			solution[i] = this.domains[i][valIndex];
		}
		solution[i] = this.domains[i][index];
	}
	
	/**
	 * Comparator used when maximizing
	 * @author Brammert Ottens, 23 nov 2009
	 * 
	 * @param <U>	type used for utility values
	 */
	private static class MaximizeComp < U extends Addable<U> > implements Comparator<Assignment<U>> {
		
		/**
		 * @param a1 assignment 1
		 * @param a2 assignment 2
		 * @return a2.util - a1.util
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Assignment<U> a1, Assignment<U> a2) {
			return a2.util.compareTo(a1.util);
		}
	}
	
	/**
	 * Comparator used when minimizing
	 * @author Brammert Ottens, 23 nov 2009
	 * 
	 * @param <U>	type used for utility values
	 */
	private static class MinimizeComp < U extends Addable<U> > implements Comparator<Assignment<U>> {
		/** 
		 * @param a1 assignment 1
		 * @param a2 assignment 2
		 * @return a1.util - a2.util
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Assignment<U> a1, Assignment<U> a2) {
			return a1.util.compareTo(a2.util);
		}
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst#maximalCut() */
	public U maximalCut() {
		return space.infeasibleUtil.getZero();
	}
	
}
