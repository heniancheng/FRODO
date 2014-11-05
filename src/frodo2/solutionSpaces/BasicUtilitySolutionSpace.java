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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

/** This interface defines common functionalities in all utility solution spaces
 * 
 * A utility solution space is a solution space in which each solution has an associated value, called "utility". 
 * @author Radoslaw Szymanek, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */

public interface BasicUtilitySolutionSpace<V extends Addable<V>, U extends Serializable> 
extends SolutionSpace<V> {
	
	/** @see SolutionSpace#clone() */
	public BasicUtilitySolutionSpace<V, U> clone ();
	
	/** @return an explicit representation of this space if it is currently implicit, else returns itself */
	public BasicUtilitySolutionSpace<V, U> resolve ();
	
	/** A human-friendly version of toString()
	 * @param ignoredUtil 	do not display solutions with this utility
	 * @return 				a human-friendly representation of the space 
	 */
	public String prettyPrint (U ignoredUtil);
	
	
	/* First group of operations which operate on one BasicUtilitySolutionSpace */
	
	/** Returns an object representing the utility value corresponding to the provided
	 * variables values representing a solution.
	 * 
	 * This assumes the values are given in the same order as the variables in the BasicUtilitySolutionSpace. 
	 * @param variables_values   the values of the variables
	 * @return the utility value corresponding the provided variables values
	 * @warning The output is undefined if the input does not specify a value for each variable in the space. 
	 */
	public U getUtility( V[] variables_values );
	
	/** Returns an object representing the utility value corresponding to the provided
	 * variables values representing a solution.
	 * @param variables_names   the names of the variables
	 * @param variables_values  the values of the variables
	 * @return the utility value corresponding the provided variables values
	 * @warning The output is undefined if the inputs do not specify a value for each variable in the space. 
	 */

	public U getUtility( String[] variables_names, V[] variables_values );
	
	/** Returns the utility corresponding to the input assignments
	 * @param assignments 	an association of values to variables
	 * @return the utility
	 * @warning The output is undefined if the input does not specify a value for each variable in the space. 
	 */
	public U getUtility (Map<String, V> assignments);
	
	/** Returns the utility value corresponding to the solution at the provided index
	 * 
	 * It uses the internal order of the solutions in the solution space.
	 * @param index 	index of the solution in the internal representation of solutions
	 * @return utility value of the solution at the provided index, or \c null if this solution does not exist
	 */
	
	public U getUtility( long index );
		
	/** @return the class used for utility values */
	public Class<U> getClassOfU ();
	
	/** Sets the utility value corresponding to a given assignment to variables
	 * @param variables_values 	values for the variables, in the same order as in the BasicUtilitySolutionSpace
	 * @param utility 			the new utility value
	 * @return specifies if setting the utility value was possible. \c false is returned
	 * if the BasicUtilitySolutionSpace does not allow the given assignments to the variables.
	 */
	public boolean setUtility (V[] variables_values, U utility);

	/** Sets the utility value for the solution at the provided index
	 * @param index 	index of the solution
	 * @param utility 	new utility
	 */
	public void setUtility (long index, U utility);
	
	/** Sets the default utility value
	 * 
	 * The default utility value is the one that is used when augmenting the BasicUtilitySolutionSpace with a new solution
	 * without specifying the utility of this solution. 
	 * @param utility 	the new default utility
	 */
	public void setDefaultUtility (U utility);
	
	/** @return the default utility value */
	public U getDefaultUtility ();
	
	/** Sets the utility associated with infeasible solutions
	 * @param utility 	the infeasible utility
	 */
	public void setInfeasibleUtility (U utility);

	
			
	/** Augments this BasicUtilitySolutionSpace with a new combination of variables values associated with a given utility
	 * @note If the corresponding assignment already exists in BasicUtilitySolutionSpace, 
	 * this method will only modify the corresponding utility value. 
	 * @param variables_values assignments to the variables
	 * @param utility_value    corresponding utility value
	 */
	public void augment( V[] variables_values, U utility_value );
	
	/** Returns whether the input BasicUtilitySolutionSpace represents the same space as this BasicUtilitySolutionSpace
	 * @note This method uses == for testing the equality of utilities.
	 * @param space 	the BasicUtilitySolutionSpace to be tested for equivalence
	 * @return whether the input BasicUtilitySolutionSpace is equivalent to this one
	 */
	public boolean equivalent( BasicUtilitySolutionSpace< V, U > space );

	/** Checks if this BasicUtilitySolutionSpace is included in the provided BasicUtilitySolutionSpace 
	 * 
	 * "Inclusion" means that both spaces contain the same variables, and all assignments in this BasicUtilitySolutionSpace
	 * appear with the same utility values in the input BasicUtilitySolutionSpace.
	 * @note This function uses == for testing the equality of utilities.
	 * @param space 	BasicUtilitySolutionSpace object
	 * @return \c true if this BasicUtilitySolutionSpace is included in the provided BasicUtilitySolutionSpace object, and \c false otherwise
	 */
	public boolean isIncludedIn( BasicUtilitySolutionSpace< V, U > space );
	
	
	/* The second group of operations which create a solution space based 
	 * on the original one. */
	
	/** Returns a new BasicUtilitySolutionSpace by changing the order of the variables of this BasicUtilitySolutionSpace
	 * @param variables_order 	the new order of the variables
	 * @return BasicUtilitySolutionSpace object obtained by changing the order of the variables
	 * @warning The input variable order must contain only and all the variables that belong to this BasicUtilitySolutionSpace. 
	 */
	public BasicUtilitySolutionSpace< V, U > changeVariablesOrder( String[] variables_order );
		
	
	/** Returns a slice of this BasicUtilitySolutionSpace
	 * @return BasicUtilitySolutionSpace representing a slice of this BasicUtilitySolutionSpace
	 * @see SolutionSpace#slice(java.lang.String[], V[][])
	 */
	public BasicUtilitySolutionSpace< V, U > slice( String[] variables_names, V[][] sub_domains );

	
	/** Returns a slice of this BasicUtilitySolutionSpace
	 * @return BasicUtilitySolutionSpace representing a slice of this BasicUtilitySolutionSpace
	 * @see SolutionSpace#slice(java.lang.String[], V[])
	 */
	public BasicUtilitySolutionSpace< V, U > slice( String[] variables_names, V[] values );

	
	/** Slices this SolutionSpace over a single variable
	 * @return the BasicUtilitySolutionSpace resulting from this slice
	 * @see SolutionSpace#slice(java.lang.String, V[])
	 */
	public BasicUtilitySolutionSpace< V, U > slice ( String var, V[] subDomain );

		
	/** Slices this BasicUtilitySolutionSpace over a single variable-value assignment
	 * @return the BasicUtilitySolutionSpace resulting from this slice
	 * @see SolutionSpace#slice(java.lang.String, Addable)
	 */
	public BasicUtilitySolutionSpace< V, U > slice ( String var, V val );
	
	
	/** Returns a slice of this solution space
	 * @see SolutionSpace#slice(V[])
	 */
	public BasicUtilitySolutionSpace< V, U > slice( V[] variables_values );	
	
	
	/** Substitutes some of the variables in this utility space with functions
	 * 
	 * This operation is indeed a composition of spaces: if \a f is this utility space, and \a g is the input space, 
	 * then this operation computes \a f o \a g. 
	 * 
	 * @param vars 			the variables to be substituted
	 * @param substitution 	the new values for the substituted variables, as a function of some other variables
	 * @return a new utility space corresponding to this space, but with the variables in \a vars substituted according to \a substitution 
	 */
	public BasicUtilitySolutionSpace< V, U > compose (String[] vars, BasicUtilitySolutionSpace< V, ArrayList<V> > substitution);
	
		
	/* The fourth group is connected to a BasicUtilitySolutionSpace iterator */

	/** @see SolutionSpace#sparseIter() */
	@Override
	public SparseIterator<V, U> sparseIter(); 
	
	/** @see SolutionSpace#sparseIter(java.lang.String[]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] order); 

	/** Returns a sparse iterator
	 * @param variables 	The variables to iterate over, which may contain variables not in the space, but must contain all variables in the space
	 * @param domains		The domains of the variables over which to iterate
	 * @return an iterator which allows to iterate over the given variables and their utilities 
	 */
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains); 
	
	/** Returns a sparse iterator
	 * @param variables 	The variables to iterate over
	 * @param domains		The domains of the variables over which to iterate
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @return an iterator which allows to iterate over the given variables and their utilities 
	 */
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment); 
		
	/** @see SolutionSpace#iterator() */
	@Override
	public Iterator<V, U> iterator();
	
	/** @see SolutionSpace#iterator(java.lang.String[]) */
	@Override
	public Iterator<V, U> iterator(String[] order);
	
	/** Returns an iterator
	 * @param variables 	The variables to iterate over, which may contain variables not in the space, but must contain all variables in the space
	 * @param domains		The domains of the variables over which to iterate
	 * @return an iterator which allows to iterate over the given variables and their utilities 
	 */
	public Iterator<V, U> iterator(String[] variables, V[][] domains); 
	
	/** Returns an iterator
	 * @param variables 	The variables to iterate over
	 * @param domains		The domains of the variables over which to iterate
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @return an iterator which allows to iterate over the given variables and their utilities 
	 */
	public Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment); 
		
	/** A BasicUtilitySolutionSpace iterator that skips infeasible solutions
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public interface SparseIterator<V, U> extends SolutionSpace.SparseIterator<V> {

		/** @return the utility value of the next solution */
		public U nextUtility();
		
		/** @return the utility of the current solution */	
		public U getCurrentUtility();
		
		/** Sets the utility of the current solution
		 * @param util 	the new utility
		 */
		public void setCurrentUtility (U util);
	}
	
	/** A BasicUtilitySolutionSpace iterator that doest NOT skip infeasible solutions
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public interface Iterator<V, U> extends SparseIterator<V, U>, SolutionSpace.Iterator<V> {}
}
