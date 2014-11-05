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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import frodo2.solutionSpaces.hypercube.Hypercube;

/** This interface defines common functionalities in utility solution spaces in which the utilities are Addable
 * @author Radoslaw Szymanek, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */

public interface UtilitySolutionSpace< V extends Addable<V>, U extends Addable<U> > 
extends UtilitySolutionSpaceLimited<V, U, U> {
	
	/** @see BasicUtilitySolutionSpace#clone() */
	public UtilitySolutionSpace<V, U> clone ();

	/** @return an explicit representation of this space if it is currently implicit, else returns itself */
	public UtilitySolutionSpace<V, U> resolve ();
	
	/** @return a Hypercube representation of this space */
	public Hypercube<V, U> toHypercube ();
	
	
	/** Checks if this UtilitySolutionSpace is included in the provided UtilitySolutionSpace 
	 * 
	 * "Inclusion" means that both spaces contain the same variables, and all assignments in this UtilitySolutionSpace
	 * appear with the same utility values in the input UtilitySolutionSpace.
	 * @note This function uses the compareTo() functionality of the type U.
	 * @param space 	UtilitySolutionSpace object
	 * @return \c true if this UtilitySolutionSpace is included in the provided UtilitySolutionSpace object, and \c false otherwise
	 * @see BasicUtilitySolutionSpace#isIncludedIn(BasicUtilitySolutionSpace)
	 */
	public boolean isIncludedIn( UtilitySolutionSpace< V, U > space );	
	
	
	/** @see BasicUtilitySolutionSpace#changeVariablesOrder(java.lang.String[]) */
	public UtilitySolutionSpace< V, U > changeVariablesOrder( String[] variables_order );

	
	/** Returns a UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace object with 
	 * the one provided as a parameter. Utilities are added to each other. 
	 * @param space        		the UtilitySolutionSpace to join with this one
	 * @param total_variables  	the order of the variables to be used in the resulting UtilitySolutionSpace
	 * @return UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace with the one provided as a parameter
	 */

	public UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U > space, String[] total_variables );

	
	/** Returns a slice of this UtilitySolutionSpace
	 * 
	 * Slicing corresponds to reducing the domains of some of the variables. 
	 * @param variables_names the variables to slice
	 * @param sub_domains     the new domains for the variables
	 * @return a UtilitySolutionSpace representing a slice of this UtilitySolutionSpace
	 */
	public UtilitySolutionSpace< V, U > slice( String[] variables_names, V[][] sub_domains );

	/** Returns a slice of this UtilitySolutionSpace
	 * 
	 * Slicing corresponds to grounding some of the variables. 
	 * @param variables_names 	the variables to slice
	 * @param values     		the values for the variables
	 * @return a UtilitySolutionSpace representing a slice of this UtilitySolutionSpace
	 */
	public UtilitySolutionSpace< V, U > slice( String[] variables_names, V[] values );

	/** Slices this UtilitySolutionSpace over a single variable
	 * @param var 			the variable to be assigned a value
	 * @param subDomain 	the new domain for this variable (must be a subset of the original domain) 
	 * @return 				the UtilitySolutionSpace resulting from this slice
	 */
	public UtilitySolutionSpace< V, U > slice ( String var, V[] subDomain );

	/** Slices this UtilitySolutionSpace over a single variable-value assignment
	 * @param var 	the variable to be assigned a value
	 * @param val 	the value to assign to the variable 
	 * @return 		the UtilitySolutionSpace resulting from this slice
	 */
	public UtilitySolutionSpace< V, U > slice ( String var, V val );
	
	/** Returns a slice of this UtilitySolutionSpace
	 * @param variables_values 		array containing values of the last variables of the UtilitySolutionSpace
	 * @return 	UtilitySolutionSpace object obtained by associating given values to the last variables of the UtilitySolutionSpace. 
	 * 			The number of these variables depends on the length of the provided array of variable values. 
	 */
	public UtilitySolutionSpace< V, U > slice( V[] variables_values );	

	
	/** Computes the join of this UtilitySolutionSpace with the input UtilitySolutionSpace. Utilities are added to each other. 
	 * 
	 * The order of the variables in the resulting UtilitySolutionSpace is not defined. 
	 * @param space 	the UtilitySolutionSpace to join with this one
	 * @return The UtilitySolutionSpace resulting from the join of the input one with this one
	 */
	public UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U > space);

	/** A version of the join method that minimizes the utility lookups in the caller space and the input spaces
	 * @param space 	the UtilitySolutionSpace to join with this one
	 * @return The UtilitySolutionSpace resulting from the join of the input one with this one
	 * @see UtilitySolutionSpace#join(UtilitySolutionSpace)
	 */
	public UtilitySolutionSpace< V, U > joinMinNCCCs ( UtilitySolutionSpace< V, U > space);

	
	/** Returns a UtilitySolutionSpace object obtained by joining the UtilitySolutionSpace for which this method is called 
	 * and the UtilitySolutionSpace present in the array of UtilitySolutionSpace given to this method as a parameter. 
	 * Utilities are added to each other. 
	 * 
	 * The order of the variables in the resulting UtilitySolutionSpace is not defined. 
	 * @param spaces 	an array of the UtilitySolutionSpaces to be added to this UtilitySolutionSpace
	 * @return 	UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace with all the UtilitySolutionSpaces 
	 * 			in the array of UtilitySolutionSpaces
	 * @todo Implement joinMutable(), which modifies one of its inputs if it is big enough to contain the output. 
	 */
	public UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U >[] spaces );

	/** A version of the join method that minimizes the utility lookups in the caller space and the input spaces
	 * @param spaces 	an array of the UtilitySolutionSpaces to be added to this UtilitySolutionSpace
	 * @return 	UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace with all the UtilitySolutionSpaces 
	 * 			in the array of UtilitySolutionSpaces
	 * @see UtilitySolutionSpace#join(UtilitySolutionSpace[])
	 */
	public UtilitySolutionSpace< V, U > joinMinNCCCs ( UtilitySolutionSpace< V, U >[] spaces );
	
	/** Returns a UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace object with 
	 * the one provided as a parameter. Utilities are multiplied with each other. 
	 * @param space        		the UtilitySolutionSpace to join with this one
	 * @param total_variables  	the order of the variables to be used in the resulting UtilitySolutionSpace
	 * @return UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace with the one provided as a parameter
	 */

	public UtilitySolutionSpace< V, U > multiply ( UtilitySolutionSpace< V, U > space, String[] total_variables );

	
	/** Computes the join of this UtilitySolutionSpace with the input UtilitySolutionSpace. Utilities are multiplied with each other. 
	 * 
	 * The order of the variables in the resulting UtilitySolutionSpace is not defined. 
	 * @param space 	the UtilitySolutionSpace to join with this one
	 * @return The UtilitySolutionSpace resulting from the join of the input one with this one
	 */
	public UtilitySolutionSpace< V, U > multiply ( UtilitySolutionSpace< V, U > space);

	/** Returns a UtilitySolutionSpace object obtained by joining the UtilitySolutionSpace for which this method is called 
	 * and the UtilitySolutionSpace present in the array of UtilitySolutionSpace given to this method as a parameter. 
	 * Utilities are multiplied to each other. 
	 * 
	 * The order of the variables in the resulting UtilitySolutionSpace is not defined. 
	 * @param spaces 	an array of the UtilitySolutionSpaces to be multiplied with this UtilitySolutionSpace
	 * @return 	UtilitySolutionSpace object obtained by joining this UtilitySolutionSpace with all the UtilitySolutionSpaces 
	 * 			in the array of UtilitySolutionSpaces
	 */
	public UtilitySolutionSpace< V, U > multiply ( UtilitySolutionSpace< V, U >[] spaces );

	
	

	/** The result of a projection
	 * 
	 * The result of a projection is a pair:
	 * - the resulting UtilitySolutionSpace
	 * - the conditional optimal assignments to the projected variables
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 * @author Thomas Leaute
	 */
	public static class ProjOutput < V extends Addable<V>, U extends Addable<U> > {
		
		/** The UtilitySolutionSpace resulting from the projection */
		public UtilitySolutionSpace< V, U > space;
		
		/** The list of variables that have been projected out */
		public String[] varsOut;
		
		/** The conditional optimal assignments to the projected variables */
		public BasicUtilitySolutionSpace< V, ArrayList<V> > assignments;

		/** Constructor
		 * @param space 		the space resulting from the projection
		 * @param varsOut 		the list of variables that have been projected out
		 * @param assignments 	the conditional optimal assignments to the projected variables
		 */
		public ProjOutput(UtilitySolutionSpace<V, U> space, String[] varsOut, BasicUtilitySolutionSpace< V, ArrayList<V> > assignments) {
			this.space = space;
			this.varsOut = varsOut;
			this.assignments = assignments;
		}

		/** @return the UtilitySolutionSpace that results from the projection */
		public UtilitySolutionSpace<V, U> getSpace() {
			return space;
		}
		
		/** @return the list of variables that have been projected out */
		public String[] getVariables () {
			return this.varsOut;
		}
		
		/** @return the conditional optimal assignment to the projected variables */
		public BasicUtilitySolutionSpace<V, ArrayList<V> > getAssignments() {
			return assignments;
		}
		
		/** @see java.lang.Object#toString() */
		public String toString() {
			return "ProjOutput:\n\tspace: " + this.space + "\n\tvariables out: " + (varsOut == null ? null : Arrays.asList(varsOut)) + "\n\tassignments: " + this.assignments;
		}
		
	}
	
	/** A projection operation that uses the consensus approach
	 * 
	 * When choosing the "best" value for the variable that is projected out, 
	 * instead of choosing, for each case, the optimal value,
	 * this method chooses the value that is optimal in most cases, 
	 * when the values of the random variables are allowed to vary. 
	 * 
	 * @param varOut 			the variable that is projected out
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 */
	public ProjOutput< V, U > consensus (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum);
	
	/** The composition of the consensus and expectation operations
	 * 
	 * @see UtilitySolutionSpace#consensus(String, Map, boolean)
	 * 
	 * @param varOut 			the variable that is projected out
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 */
	public ProjOutput< V, U > consensusExpect (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum);
	
	/** A projection operation that uses the advanced consensus approach
	 * 
	 * The normal consensus approach computes one optimal solution per scenario, risking to miss very promising optimal solutions. 
	 * This advanced consensus approach compute ALL optimal solutions for each scenario. 
	 * 
	 * @param varOut 			the variable that is projected out
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 * @see UtilitySolutionSpace#consensus(String, Map, boolean)
	 */
	public ProjOutput< V, U > consensusAllSols (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum);
	
	/** The composition of the consensusAllSols and expectation operations
	 * 
	 * @see UtilitySolutionSpace#consensusAllSols(String, Map, boolean)
	 * 
	 * @param varOut 			the variable that is projected out
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 */
	public ProjOutput< V, U > consensusAllSolsExpect (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum);
	
	
	/** Projects variables out of this UtilitySolutionSpace
	 * 
	 * "Projecting" a variable means that this variable is removed, by optimizing over its domain.
	 * The projection methods actually have two outputs: the UtilitySolutionSpace that results from projecting over the given variables, 
	 * and the conditional optimal assignments to the projected variables, which can be represented using a BasicUtilitySolutionSpace 
	 * whose utility values are optimal assignments. 
	 * @param variables_names 	the variables to be projected out
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 */
	public ProjOutput< V, U > project( String[] variables_names, boolean maximum );
		

	/** Projects a given number of variables out of this UtilitySolutionSpace
	 * 
	 * The variables projected out are the last \a number_to_project variables in the UtilitySolutionSpace
	 * @param number_to_project 	number of variables to project out
	 * @param maximum 				\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 * @see UtilitySolutionSpace#project(String[], boolean)
	 */
	public ProjOutput< V, U > project( int number_to_project, boolean maximum);
	
	/** Projects all variables
	 * @param maximum 	whether to maximize or minimize
	 * @return 			the ProjOutput
	 */
	public ProjOutput<V, U> projectAll (boolean maximum);
	
	/** Projects all variables
	 * @param maximum 	whether to maximize or minimize
	 * @param order 	the desired order on variables
	 * @return 			the ProjOutput
	 */
	public ProjOutput<V, U> projectAll (boolean maximum, String[] order);
	
	/** Projects a single variable out of this UtilitySolutionSpace
	 * @param variable_name 	the variable to project out
	 * @param maximum 			\c true if we should maximize the utility; \c false if it should be minimized
	 * @return a ProjOutput object that represents the pair resulting space - conditional optimal assignments
	 * @see UtilitySolutionSpace#project(String[], boolean)
	 * @todo When a single variable is projected out, it is more efficient to represent the conditional optimal assignments as a BasicUtilitySolutionSpace<V, V>
	 * rather than a BasicUtilitySolutionSpace<V, ArrayList<V>>. 
	 */
	public ProjOutput< V, U > project( String variable_name, boolean maximum );
	
	/** @see UtilitySolutionSpaceLimited#blindProject(String, boolean) */
	public UtilitySolutionSpace<V, U> blindProject (String varOut, boolean maximize);
	
	/** @see UtilitySolutionSpaceLimited#blindProject(String[], boolean) */
	public UtilitySolutionSpace<V, U> blindProject (String[] varsOut, boolean maximize);
	
	/** @see UtilitySolutionSpaceLimited#blindProjectAll(boolean) */
	public U blindProjectAll (boolean maximize);
	
	/** @see UtilitySolutionSpaceLimited#min(java.lang.String) */
	public UtilitySolutionSpace<V, U> min (String variable);
	
	/** @see UtilitySolutionSpaceLimited#max(java.lang.String) */
	public UtilitySolutionSpace<V, U> max (String variable);
	
	
	/** Returns a UtilitySolutionSpace containing all solutions corresponding to utility values bigger/smaller than the provided threshold
	 * @param threshold 	the threshold
	 * @param maximum   	\c true if we should keep solutions with utility values higher than the threshold; \c false if lower
	 * @return the resulting UtilitySolutionSpace
	 */
	public UtilitySolutionSpace< V, U > split( U threshold, boolean maximum );
	
	
	/** @see BasicUtilitySolutionSpace#compose(java.lang.String[], BasicUtilitySolutionSpace) */
	public UtilitySolutionSpace< V, U > compose (String[] vars, BasicUtilitySolutionSpace< V, ArrayList<V> > substitution);
	
	
	/** Computes the expectation of this utility space over the input random variables, conditioned on the input probability space
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @return 					the expected utilities 
	 */
	public UtilitySolutionSpace< V, U > expectation (Map< String, UtilitySolutionSpace<V, U> > distributions);
	
	/** An optimize expectation().project() operator that assumes that all costs are non-negative (or all utilities non-positive)
	 * @param varOut			the variable to project out
	 * @param distributions 	for each random variable, its weighted samples/probability distribution
	 * @param maximum 			whether to maximize of minimize
	 * @return the output of the projection
	 */
	public ProjOutput< V, U > projExpectMonotone (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, boolean maximum);

	/** Samples this single-variable probability space 
	 * @param nbrSamples 	desired number of samples
	 * @return weighted samples for this space's single variable 
	 */
	public Map<V, Double> sample (int nbrSamples);
	
	/**
	 * Rescales the utilities in this space
	 * 
	 * @author Brammert Ottens, 9 mrt. 2013
	 * @param add		add this value to all utilities
	 * @param multiply	multiply all utilities with this value
	 * @return a rescaled utility space
	 * @note The multiplication is performed after the addition. 
	 */
	public UtilitySolutionSpace<V, U> rescale(U add, U multiply);
	
	/** Sets the problem that should be notified of constraint checks
	 * @param problem 	the problem
	 */
	public void setProblem (ProblemInterface<V, U> problem);
	
	/**
	 *  @param maximize \c true when the order is from high to low utility, and \c false when the order is from low to high cost
	 *  @return an iterator which allows to iterate over the solutions and their utilities in best first order
	 */
	public IteratorBestFirst<V, U> iteratorBestFirst(boolean maximize);
	
	/**
	 * @param maximize 		\c true when the order is from high to low utility, and \c false when the order is from low to high cost
	 * @param fixedVariables	variables whose values are fixed
	 * @param fixedValues 		the values to which the variables are fixed
	 * @return an iterator which allows to iterate over the solutions and their utilities in best first order
	 */
	public IteratorBestFirst<V, U> iteratorBestFirst(boolean maximize, String[] fixedVariables, V[] fixedValues);
	
	/** @see BasicUtilitySolutionSpace#sparseIter() */
	@Override
	public SparseIterator<V, U> sparseIter();
	
	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] order);
	
	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], V[][]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains); 
		
	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], V[][], V[]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment); 
		
	/** @see BasicUtilitySolutionSpace#iterator() */
	@Override
	public Iterator<V, U> iterator();
	
	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[]) */
	@Override
	public Iterator<V, U> iterator(String[] order);
	
	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], V[][]) */
	@Override
	public Iterator<V, U> iterator(String[] variables, V[][] domains); 
		
	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], V[][], V[]) */
	@Override
	public Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment); 
		
	/** A UtilitySolutionSpace iterator that skips infeasible solutions
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public interface SparseIterator<V, U> extends BasicUtilitySolutionSpace.SparseIterator<V, U> {

		/** Returns the next solution strictly better than the input bound
		 * @param bound 		a bound on the desired utility
		 * @param minimize 		\c true if the utility must be lower than the bound
		 * @return the utility value of the next better solution, \c null if none was found
		 * @warning This method skips solutions if they are not strictly better than the bound, and therefore can return \c null even if this.hasNext() returns \c true. 
		 */
		public U nextUtility(U bound, boolean minimize);
		
		/** 
		 * @param bound 		a bound on the desired utility
		 * @param minimize 		\c true if the utility must be lower than the bound
		 * @return the utility value of the next solution, or a bound on it if the utility value is worse than the input bound
		 */	
		public U getCurrentUtility(U bound, boolean minimize);
	}
	
	/** A UtilitySolutionSpace iterator that does NOT skip infeasible solutions
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public interface Iterator<V, U> extends SparseIterator<V, U>, BasicUtilitySolutionSpace.Iterator<V, U> {}
	
	/**
	 * A BasicUtilitySolutionSpace iterator that returns items in a 
	 * best first order
	 * @author Brammert Ottens, 27 jan. 2011
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public interface IteratorBestFirst<V, U> extends Iterator<V, U> {
		
		/**
		 * @author Brammert Ottens, 20 jan. 2011
		 * @return the maximal amount with which a utility value has been cut.
		 */
		public U maximalCut();
	}
	
}
