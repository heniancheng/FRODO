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
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A BasicHypercube that contains only one utility, and no variables
 * 
 * @author Thomas Leaute
 * @param <V> 	the type used for the variable values
 * @param <U> 	the type used for the utility values
 * @todo Test this class
 */
public class ScalarBasicHypercube<V extends Addable<V>, U extends Serializable>
		extends BasicHypercube<V, U> {

	/** The utility of this scalar hypercube */
	private U utility;
	
	/** Empty constructor */
	public ScalarBasicHypercube () { }

	/**
	 * Constructor
	 * 
	 * @param utility 			the utility of the scalar hypercube
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 */
	public ScalarBasicHypercube(U utility, U infeasibleUtil) {
		this.utility = utility;
		this.infeasibleUtil = infeasibleUtil;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.infeasibleUtil);
		out.writeUTF(this.name);
		out.writeObject(this.utility);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.infeasibleUtil = (U) in.readObject();
		this.name = in.readUTF();
		this.utility = (U) in.readObject();
	}

	/**
	 * Does nothing
	 * 
	 * @see BasicHypercube#setStepsHashmaps()
	 */
	@Override
	void setStepsHashmaps() {
	}

	/**
	 * Always returns 1
	 * 
	 * @see BasicHypercube#getNumberOfSolutions()
	 */
	@Override
	public long getNumberOfSolutions() {
		return 1;
	}

	/**
	 * Ignores the input and always returns the utility of this scalar hypercube
	 * 
	 * @see BasicHypercube#getUtility(V[])
	 */
	@Override
	public U getUtility(V[] variables_values) {
		return utility;
	}

	/**
	 * Ignores the inputs and always returns the utility of this scalar
	 * hypercube
	 * 
	 * @see BasicHypercube#getUtility(java.lang.String[], V[])
	 */
	@Override
	public U getUtility(String[] variables_names, V[] variables_values) {
		return utility;
	}

	/**
	 * Always returns the single utility value of this scalar hypercube
	 * 
	 * @see BasicHypercube#getUtility(long)
	 */
	@Override
	public U getUtility(long index) {
		return utility;
	}

	/** @see BasicHypercube#getClassOfU() */
	@SuppressWarnings("unchecked")
	@Override
	public Class<U> getClassOfU () {
		return (Class<U>) this.utility.getClass();
	}
	
	/**
	 * Ignores \a variables_values, and sets its single utility to \a utility
	 * 
	 * @see BasicHypercube#setUtility(V[], java.io.Serializable)
	 */
	@Override
	public boolean setUtility(V[] variables_values, U utility) {
		this.utility = utility;
		return true;
	}

	/** Ignores \a index and sets this scalar hypercube's utility to \a utility
	 * @see BasicHypercube#setUtility(long, java.io.Serializable) 
	 */
	@Override
	public void setUtility(long index, U utility) {
		this.utility = utility;
	}

	/**
	 * Returns the names of the variables of the hypercube
	 * 
	 * @return String array containing the names of the variables of the
	 *         hypercube
	 */
	@Override
	public String[] getVariables() {
		return new String[0];
	}

	/**
	 * Always returns 0
	 * 
	 * @see BasicHypercube#getNumberOfVariables()
	 */
	@Override
	public int getNumberOfVariables() {
		return 0;
	}

	/**
	 * Always returns \c null
	 * 
	 * @see BasicHypercube#getVariable(int)
	 */
	@Override
	public String getVariable(int index) {
		return null;
	}

	/** Does nothing
	 * @see BasicHypercube#renameVariable(String, String) 
	 */
	@Override
	public void renameVariable (String oldName, String newName) { }
	
	/** @see BasicHypercube#renameAllVars(String[]) */
	@Override
	public BasicHypercube<V, U> renameAllVars(String[] newVarNames) {
		assert newVarNames.length == 0 : "A ScalarHypercube does not contain any variable";
		return this;
	}
	
	/**
	 * Always returns -1
	 * 
	 * @see BasicHypercube#getIndex(java.lang.String)
	 */
	@Override
	public int getIndex(String variable) {
		return -1;
	}

	/**
	 * Always returns \c null
	 * 
	 * @see BasicHypercube#getDomains()
	 */
	@Override
	public V[][] getDomains() {
		return null;
	}

	/**
	 * Always returns \c null
	 * 
	 * @see BasicHypercube#getDomain(java.lang.String)
	 */
	@Override
	public V[] getDomain(String variable) {
		return null;
	}

	/**
	 * Always returns \c null
	 * 
	 * @see BasicHypercube#getDomain(int)
	 */
	@Override
	public V[] getDomain(int index) {
		return null;
	}

	/**
	 * Always returns \c null
	 * 
	 * @see BasicHypercube#getDomain(java.lang.String, int)
	 */
	@Override
	public V[] getDomain(String variable, int index) {
		return null;
	}

	/** Does nothing
	 * @see BasicHypercube#setDomain(String, V[]) 
	 */
	@Override
	public void setDomain (String var, V[] dom) { }
	
	/** @see BasicHypercube#toString() */
	@Override
	public String toString() {
		return "ScalarBasicHypercube of utility value " + utility;
	}
	
	/** @see BasicHypercube#prettyPrint(java.io.Serializable) */
	@Override
	public String prettyPrint (U unused) {
		return this.toString();
	}

	/** @see BasicHypercube#augment(V[]) */
	@Override
	public void augment(V[] variables_values) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}

	/** @see BasicHypercube#augment(V[], java.io.Serializable) */
	@Override
	public void augment(V[] variables_values, U utility_value) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}

	/** Returns a clone
	 * @see BasicHypercube#changeVariablesOrder(java.lang.String[]) 
	 */
	@Override
	public ScalarBasicHypercube<V, U> changeVariablesOrder(String[] variables_order) {
		return this.clone();
	}

	/** @see BasicHypercube#equivalent(BasicUtilitySolutionSpace) */
	@Override
	public boolean equivalent(BasicUtilitySolutionSpace<V, U> space) {
		return equals(space);
	}

	/** @see BasicHypercube#iterator() */
	@Override
	public Iterator<V, U> iterator() {
		return new ScalarBasicSpaceIter<V, U> (this.utility, this.infeasibleUtil, null);
	}

	/** @see BasicHypercube#iterator(String[]) */
	@Override
	public Iterator<V, U> iterator(String[] variables) {
		assert variables.length == 0;
		return new ScalarBasicSpaceIter<V, U> (this.utility, this.infeasibleUtil, null);
	}

	/** @see BasicHypercube#iterator(String[], V[][]) */
	@Override
	public Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment) {
		return new ScalarBasicSpaceIter<V, U> (this.utility, variables, domains, assignment, this.infeasibleUtil, null);
	}

	/** @see BasicHypercube#sparseIter() */
	@Override
	public SparseIterator<V, U> sparseIter() {
		return new ScalarBasicSpaceIter<V, U> (this.utility, this.infeasibleUtil, this.infeasibleUtil);
	}

	/** @see BasicHypercube#sparseIter(String[]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] variables) {
		assert variables.length == 0;
		return new ScalarBasicSpaceIter<V, U> (this.utility, this.infeasibleUtil, this.infeasibleUtil);
	}

	/** @see BasicHypercube#sparseIter(String[], V[][]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment) {
		return new ScalarBasicSpaceIter<V, U> (this.utility, variables, domains, assignment, this.infeasibleUtil, this.infeasibleUtil);
	}

	/** @see BasicHypercube#isIncludedIn(BasicUtilitySolutionSpace) */
	@Override
	public boolean isIncludedIn(BasicUtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return false;
	}

	/** @see BasicHypercube#join(SolutionSpace, java.lang.String[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space,
			String[] total_variables) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/**
	 * Computes the join of this solutionSpace with the input solutionSpace.
	 * Order of variables is chosen as it fits.
	 */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/**
	 * Returns a SolutionSpace object obtained by joining the SolutionSpace for
	 * which this method is called and the SolutionSpaces present in the array
	 * of SolutionSpaces given to this method as a parameter.
	 * 
	 * @param spaces 					an array of the solutionSpaces to be added to this solutionSpace
	 * @param total_variables_order 	the order of the variables in all solutionSpaces
	 * @return SolutionSpace object obtained by joining this SolutionSpace with
	 *         all the SolutionSpaces in the array of SolutionSpaces Originally
	 *         this function in the hypercubes assumes that order is not
	 *         conflicting.
	 */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces,
			String[] total_variables_order) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/**
	 * Slices this hypercube over a single variable-value assignment
	 * 
	 * @param var 	the variable to be assigned a value
	 * @param val 	the value to assign to the variable
	 * @return the hypercube resulting from this slice
	 */
	@Override
	/// @todo implement it
	public BasicUtilitySolutionSpace<V, U> slice(String var, V val) {
		assert false : "not implemented!";
		return null;
	}

	/** Returns a clone
	 * @see BasicHypercube#slice(java.lang.String[], V[][])
	 */
	@Override
	public ScalarBasicHypercube<V, U> slice(String[] variables_names, V[][] sub_domains) {
		return this.clone();
	}

	/**
	 * Return a slice of this hypercube
	 * 
	 * @param variables_values 	array containing values of the last variables of the hypercube
	 * @return Hypercube object obtained by associating fixed values to the last
	 *         variables of the hypercube. the number of this variables depends
	 *         on the length of the provided array of variables values
	 */
	@Override
	/// @todo implement it.
	public BasicUtilitySolutionSpace<V, U> slice(V[] variables_values) {
		assert false : "not implemented!";
		return null;
	}

	/** Returns a constant hypercube
	 * @see BasicHypercube#compose(java.lang.String[], BasicUtilitySolutionSpace) 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public BasicHypercube<V, U> compose(String[] vars, BasicUtilitySolutionSpace< V, ArrayList<V> > substitution) {
		
		/// @todo Implement the case when the substitution is not a BasicHypercube
		BasicHypercube<V, U> substCast = (BasicHypercube<V, U>) substitution;
		
		if (substCast.getNumberOfVariables() == 0) // the substitution is scalar
			return this.clone();
		
		U[] utilities = (U[]) Array.newInstance(substCast.values.getClass().getComponentType(), substCast.number_of_utility_values);
		Arrays.fill(utilities, this.utility);
		return this.newInstance(substCast.variables.clone(), substCast.domains.clone(), utilities, this.infeasibleUtil);
	}
	
	/** @see BasicHypercube#equals(java.lang.Object) */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) {

		if (o == this)
			return true;

		if (o instanceof ScalarBasicHypercube) {

			ScalarBasicHypercube<V, U> compareTo = (ScalarBasicHypercube<V, U>) o;
			return compareTo.getUtility(0).equals(utility);
		}

		return false;
	}
	
	/**@see BasicHypercube#clone() */
	@Override
	public ScalarBasicHypercube< V, U > clone () {
		return new ScalarBasicHypercube< V, U > (this.utility, this.infeasibleUtil);
	}
	
	/** @see BasicHypercube#getDefaultUtility() */
	@Override
	public U getDefaultUtility() {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/** @see BasicHypercube#setDefaultUtility(java.io.Serializable) */
	@Override
	public void setDefaultUtility(U utility) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}

}
