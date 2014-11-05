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

/** This package contains interfaces for various representations of solution spaces */
package frodo2.solutionSpaces;

import java.io.Serializable;


/** This interface defines common functionalities in all solution spaces. 
 * 
 * A solution space is a representation of combinations of assignments to variables that are the solution to some problem.
 * @author Radoslaw Szymanek, Thomas Leaute
 * @param <V> the type used for variable values
 * @todo Define an isIncludedIn() method?
 */

public interface SolutionSpace<V extends Addable<V> > extends Serializable, Cloneable {
	
	/** @return the name of this space, if any */
	public String getName ();
	
	/** Sets the name of this space
	 * @param name 	the name of this space
	 */
	public void setName (String name);

	/** @return the name of the underlying relation for this space, if any */
	public String getRelationName ();
	
	/** Sets the name of the underlying relation for this space
	 * @param name 	the name of the underlying relation for this space
	 */
	public void setRelationName (String name);
	
	/** @return the agent that owns this space, or "PUBLIC" if it is public, or \c null if the space is owns by all agents involved */
	public String getOwner ();
	
	/** Sets the owner
	 * @param owner 	the owner of this space
	 */
	public void setOwner (String owner);
	
	/* First group of operations which operate on one SolutionSpace */
	
	/** Returns the number of solutions in the SolutionSpace
	 * @return integer representing the number of possible assignments in the SolutionSpace
	 */
	public long getNumberOfSolutions();	
	
	/** Returns the internal order of variables used within the SolutionSpace
	 * 
	 * It is used for any function which assumes default/internal order of variables.
	 * @return  String array containing the names of the variables of the SolutionSpace
	 */
	public String[] getVariables();
	
	/** Returns the number of variables in the SolutionSpace
	 * @return integer representing the number of variables in the SolutionSpace
	 */
	public int getNumberOfVariables();
	
	/** Returns the variable corresponding to the provided index
	 * @param index 	index of the variable in this SolutionSpace
	 * @return 			String representing the variable corresponding to the provided index
	 */
	public String getVariable( int index );
	
	/** Renames a variable
	 * @param oldName 	the current name of the variable
	 * @param newName 	the new name for the variable
	 */
	public void renameVariable (String oldName, String newName);
	
	/** Creates a shallow clone of this space, with different variable names
	 * @param newVarNames 	the new variable names
	 * @return a shallow clone of this space, with the specified variable names
	 */
	public SolutionSpace<V> renameAllVars (String[] newVarNames);
	
	/** Returns the index of the input variable in this SolutionSpace
	 * @param variable 		the name of the variable
	 * @return 				integer representing the index of the variable in the SolutionSpace
	 */
	public int getIndex( String variable );
	
	/** Returns the array containing the domains of the variables
	 * @return  two-dimensional array containing the domains of the variables
	 */
	public V[][] getDomains();
	
	/** Returns an array of all possible values that the variable provided as a parameter 
	 * can take in this SolutionSpace
	 * @param variable 		the name of the variable
	 * @return  			the variable's domain
	 */
	public V[] getDomain( String variable );
	
	/** Returns the domain of the variable that corresponds to the provided index
	 * @param index 	index of the variable
	 * @return 			Array containing all the possible values of the variable corresponding to the provided index
	 */
	public V[] getDomain( int index );
	
	/** Returns the variable's domain if <code>index</code> is the variable's index in the array of variables order
	 * @param variable the name of the variable
	 * @param index    the index of the variable
	 * @return         Array containing the domain of the variable or \c null if the variable doesn't exist in this SolutionSpace
	 *                 at the provided index 
	 */
	public V[] getDomain( String variable, int index );	
	
	/** Sets the domain of a variable
	 * @param var 	the variable
	 * @param dom 	the new domain for the variable
	 */
	public void setDomain (String var, V[] dom);
	
		
	/** Augments the SolutionSpace with a new combination of variable values which constitutes a new solution
	 * @param variables_values 		the assignments to the solution space's variables
	 * @note If the corresponding solution already exists then nothing will change.
	 */
	public void augment( V[] variables_values );

	
	/** Returns a slice of this SolutionSpace
	 * 
	 * Slicing corresponds to reducing the domains of some of the variables. 
	 * @param variables_names the variables to slice
	 * @param sub_domains     the new domains for the variables
	 * @return a SolutionSpace representing a slice of this SolutionSpace
	 */
	public SolutionSpace<V> slice( String[] variables_names, V[][] sub_domains );

	
	/** Returns a slice of this SolutionSpace
	 * 
	 * Slicing corresponds to grounding some of the variables. 
	 * @param variables_names 	the variables to slice
	 * @param values     		the values for the variables
	 * @return a SolutionSpace representing a slice of this SolutionSpace
	 */
	public SolutionSpace<V> slice( String[] variables_names, V[] values );

	
	/** Slices this SolutionSpace over a single variable
	 * @param var 			the variable to be assigned a value
	 * @param subDomain 	the new domain for this variable (must be a subset of the original domain) 
	 * @return 				the SolutionSpace resulting from this slice
	 */
	public SolutionSpace<V> slice ( String var, V[] subDomain );

		
	/** Slices this SolutionSpace over a single variable-value assignment
	 * @param var 	the variable to be assigned a value
	 * @param val 	the value to assign to the variable 
	 * @return 		the SolutionSpace resulting from this slice
	 */
	public SolutionSpace<V> slice ( String var, V val );
	

	
	/** Returns a slice of this SolutionSpace
	 * @param variables_values 		array containing values of the last variables of the SolutionSpace
	 * @return 	SolutionSpace object obtained by associating given values to the last variables of the SolutionSpace. 
	 * 			The number of these variables depends on the length of the provided array of variable values. 
	 */
	public SolutionSpace< V > slice( V[] variables_values );	
	
	/** Returns whether this space knows how to handle other spaces of the input class
	 * @param spaceClass 	class of the other spaces
	 * @return whether this space knows how to handle the input type of spaces
	 */
	public boolean knows (Class<?> spaceClass);
	
	/** Returns a SolutionSpace object obtained by joining this SolutionSpace object with 
	 * the one provided as a parameter.
	 * @param space        		the SolutionSpace to join with this one
	 * @param total_variables  	the order of the variables to be used in the resulting SolutionSpace
	 * @return SolutionSpace object obtained by joining this SolutionSpace with the one provided as a parameter
	 */

	public SolutionSpace< V> join( SolutionSpace< V> space, String[] total_variables );
	
	/** Computes the join of this SolutionSpace with the input SolutionSpace. 
	 * 
	 * Order of variables in the resulting SolutionSpace is not specified. 
	 * @param space 	the SolutionSpace to join with this one
	 * @return 			the result of the join of the two SolutionSpaces
	 */
	public SolutionSpace< V> join( SolutionSpace< V > space);

	
	/** Returns a SolutionSpace object obtained by joining the SolutionSpace for which this method is called and the SolutionSpaces 
	 * present in the array of SolutionSpaces given to this method as a parameter.
	 * @param spaces             		an array of SolutionSpaces to be added to this SolutionSpace
	 * @param total_variables_order 	the order of variables to be used in the resulting SolutionSpace
	 * @return SolutionSpace object obtained by joining this SolutionSpace with all the SolutionSpaces in the array of SolutionSpaces
	 */
	public SolutionSpace< V > join( SolutionSpace< V >[] spaces, String[] total_variables_order );
	
		
	/** Returns a SolutionSpace object obtained by joining the SolutionSpace for which this method is called 
	 * and the SolutionSpaces present in the array of SolutionSpaces given to this method as a parameter
	 * 
	 * The order of the variables in the resulting SolutionSpace is not defined. 
	 * @param spaces 	an array of the SolutionSpaces to be added to this SolutionSpace
	 * @return 	SolutionSpace object obtained by joining this SolutionSpace with all the SolutionSpaces 
	 * 			in the array of SolutionSpaces
	 */
	public SolutionSpace< V > join( SolutionSpace< V >[] spaces );

	
	/** Checks for equality
	 * @param o 	an Object to be compared with this SolutionSpace
	 * @return whether the input Object equals this SolutionSpace
	 * @todo Also define an equivalent() method. 
	 */
	public boolean equals(Object o);
	
	/** @return a clone of this solution space */
	public SolutionSpace< V > clone ();
	
	/** @return an explicit representation of this space if it is currently implicit, else returns itself */
	public SolutionSpace<V> resolve ();
	
	
	/* The fourth group is connected to a SolutionSpace iterator */
	
	/** @return a sparse iterator which can be used to iterate through feasible solutions */
	public SparseIterator<V> sparseIter(); 
	
	/** Returns a sparse iterator with a specific variable order
	 * @param order 	the order of iteration of the variables
	 * @return 			an iterator which can be used to iterate through solutions 
	 * @warning The input array of variables must contain exactly all of the space's variables. 
	 */
	public SparseIterator<V> sparseIter(String[] order); 
	
	/** A SolutionSpace iterator that skips infeasible solutions
	 * @param <V> type used for variable values
	 */
	public interface SparseIterator<V> {
		
		/**
		 * @return the next assignment in the solution space. 
		 * @warning The array can be later reused by the iterator, so do not assume you can store it and use it. 
		 * Do not change the array as it is used internally by the iterator.
		 */	
		public V[] nextSolution();
		
		/**
		 * @return the current assignment of values to variables. 
		 * @warning The array can be later reused by the iterator, so do not assume you can store it and use it. 
		 * Do not change the array as it is used internally by the iterator.
		 */
		public V[] getCurrentSolution();
		
		/**
		 * @return the order of variables according to which the iteration is performed.
		 * @warning The array can be later reused by the iterator, so do not assume you can store it and use it. 
		 * Do not change the array as it is used internally by the iterator.
		 */
		public String[] getVariablesOrder();
		
		/**
		 * @return the domains for the variables iterated over. 
		 * @warning The array can be later reused by the iterator, so do not assume you can store it and use it. 
		 * Do not change the array as it is used internally by the iterator.
		 */
		public V[][] getDomains ();
		
		/**
		 * It is supposed to be called if the solution space upon which the iterator is based
		 * has changed. The iterator will adjust its data structures to be able to
		 * provide the next solution right after the recent current solution. 
		 */
		public void update();
	}
	
	/** @return an iterator which can be used to iterate through solutions */
	public Iterator<V> iterator(); 
	
	/** Returns an iterator with a specific variable order
	 * @param order 	the order of iteration of the variables
	 * @return 			an iterator which can be used to iterate through solutions 
	 * @warning The input array of variables must contain exactly all of the space's variables. 
	 */
	public Iterator<V> iterator(String[] order); 
	
	/** An iterator that does NOT skip infeasible solutions
	 * @param <V> the type used for variable values
	 */
	public interface Iterator<V> extends SparseIterator<V> {
		
		/** @return the total number of solutions iterated over */
		public long getNbrSolutions ();
		
		/** @return \c true if there is a next assignment in the SolutionSpace */
		public boolean hasNext();
	}
}
