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

/**
 * @author Ouaret Nacereddine, Thomas Leaute, Radoslaw Szymanek
 */

package frodo2.solutionSpaces.hypercube;


import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** A hypercube that contains no variables, but a single utility value
 * @author Thomas Leaute
 * @param  < V >  type used for the variable values
 * @param  < U >  type used for the utility values
 * @todo Test this class
 * @note This class is \b NOT a subclass of BasicHypercube.ScalarBasicHypercube
 */
public class ScalarHypercube < V extends Addable<V>, U extends Addable<U> > 
extends Hypercube <V, U> {

	/** Empty constructor */
	public ScalarHypercube () { }
		
	/** Constructor 
	 * @param utility 			the single utility value of this hypercube
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @param classOfDom 		the class of V[]
	 */
	@SuppressWarnings("unchecked")
	public ScalarHypercube (U utility, U infeasibleUtil, Class<? extends V[]> classOfDom) {
		variables = new String[0];
		domains = (V[][]) Array.newInstance(classOfDom, 0);
		values = (U[]) Array.newInstance(utility.getClass(), 1);
		this.number_of_utility_values = 1;
		values[0] = utility;
		steps_hashmaps = null;
		this.infeasibleUtil = infeasibleUtil;
	}
	
	/** @see Hypercube#scalarHypercube(Addable) */
	@SuppressWarnings("unchecked")
	@Override
	protected ScalarHypercube<V, U> scalarHypercube(U utility) {
		return new ScalarHypercube<V, U> (utility, this.infeasibleUtil, (Class<? extends V[]>) this.domains.getClass().getComponentType());
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(this.infeasibleUtil);
		out.writeUTF(this.name);
		out.writeObject(this.values[0]);
		out.writeObject(this.domains.getClass().getComponentType());
		this.incrNCCCs(1);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.infeasibleUtil = (U) in.readObject();
		this.name = in.readUTF();
		U util = (U) in.readObject();
		this.values = (U[]) Array.newInstance(util.getClass(), 1);
		this.number_of_utility_values = 1;
		this.values[0] = util;
		
		Class<V[]> classOfDom = (Class<V[]>) in.readObject();
		this.domains = (V[][]) Array.newInstance(classOfDom, 0);
		this.variables = new String [0];
		this.classOfV = (Class<V>) classOfDom.getComponentType();
	}
	
	/** Does nothing
	 * @see BasicHypercube#setStepsHashmaps()
	 */
	@Override
	void setStepsHashmaps() { }
	
	/** Always returns 1
	 * @see BasicHypercube#getNumberOfSolutions()
	 */
	@Override
	public long getNumberOfSolutions () {
		return 1;
	}
	
	/** Always returns this ScalarHypercube's utility
	 * @see BasicHypercube#getUtility(V[])
	 */
	@Override
	public U getUtility( V[] variables_values ) {
		
		this.incrNCCCs(1);
		return values[0];
	}
	
	/** Always returns this ScalarHypercube's utility
	 * @see BasicHypercube#getUtility(java.lang.String[], V[])
	 */
	@Override
	public U getUtility( String[] variables_names, V[] variables_values ) {
		
		this.incrNCCCs(1);
		return values[0];
	}
	
	/** Always returns this ScalarHypercube's utility
	 * @see BasicHypercube#getUtility(long)
	 */
	@Override
	public U getUtility( long index ){
		
		this.incrNCCCs(1);
		return values[0];
	}
	
	/** Ignores \a variables_values and sets this scalar hypercube's utility to \a utility
	 * @see BasicHypercube#setUtility(V[], java.io.Serializable)
	 */
	@Override
	public boolean setUtility (V[] variables_values, U utility) {
		values[0] = utility;
		return true;
	}
	
	/** Ignores \a index and sets this scalar hypercube's utility to \a utility
	 * @see BasicHypercube#setUtility(long, java.io.Serializable)
	 */
	@Override
	public void setUtility(long index, U utility) {
		this.values[0] = utility;
	}
	
	/** Always returns 0
	 * @see BasicHypercube#getNumberOfVariables()
	 */
	@Override
	public int getNumberOfVariables () {
		return 0;
	}
	
	/** Always returns \c null
	 * @see BasicHypercube#getVariable(int)
	 */
	@Override
	public String getVariable( int index ) {
		return null;
	}
	
	/** Always returns -1
	 * @see BasicHypercube#getIndex(java.lang.String)
	 */
	@Override
	public int getIndex( String variable ) {
		return -1;
	}
	
	/** Always returns \c null
	 * @see BasicHypercube#getDomains()
	 */
	@Override
	public V[][] getDomains (){
		return super.domains;
	}
	
	/** Always returns \c null
	 * @see BasicHypercube#getDomain(java.lang.String)
	 */
	@Override
	public V[] getDomain( String variable ){
		return null;
	}
	
	/** Always returns \c null
	 * @see BasicHypercube#getDomain(int)
	 */
	@Override
	public V[] getDomain( int index ) {
		return null;
	}
	
	/** Always returns \c null
	 * @see BasicHypercube#getDomain(java.lang.String, int)
	 */
	@Override
	public V[] getDomain( String variable, int index ) {
		return null;
	}
	
	/** Does nothing
	 * @see BasicHypercube#setDomain(String, V[]) 
	 */
	@Override
	public void setDomain (String var, V[] dom) { }
	
	/** @see Hypercube#toString() */
	@Override
	public String toString() {
		return "ScalarHypercube of utility value " + values[0].toString();
	}
	
	/** @see BasicHypercube#prettyPrint(java.io.Serializable) */
	@Override
	public String prettyPrint (U unused) {
		return this.toString();
	}
	
	/** @see Hypercube#saveAsXML(java.lang.String) */
	@Override
	public void saveAsXML( String file ) {
		/// @bug To be implemented
		System.err.println("ScalarHypercube.saveAsXML not implemented!");
	}
	
	/** @see Hypercube#join(UtilitySolutionSpace, java.lang.String[], boolean, boolean) 
	 * @bug The output hypercube does not respect the input variable order 
	 */
	@Override
	protected UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U > space, String[] total_variables, final boolean addition, boolean minNCCCs ) {
		
		if (! this.knows(space.getClass()) && space.knows(this.getClass())) 
			return ((Hypercube< V, U >) space).join(this, total_variables, addition, minNCCCs);
		
		else if (minNCCCs && space.getNumberOfSolutions() == 1) 
			return this.scalarHypercube(addition ? this.getUtility(0).add(space.getUtility(0)) : this.getUtility(0).multiply(space.getUtility(0)));
		
		else
			return new JoinOutputHypercube<V, U> (this, space, space.getVariables(), space.getDomains(), addition, this.infeasibleUtil, space.getNumberOfSolutions());
	}
	
	/**Return a SolutionSpace object obtained by joining this solutionSpace object with 
	 * the one provided as a parameter.
	 * @param space       	   the solutionSpace to join this one with
	 * @param total_variables  the order of the variables in both solutionSpaces
	 * @return SolutionSpace object obtained by joining this solutionSpace with the one provided as a parameter
	 * Originally this function in the solutionSpaces assumes that order is not conflicting.
	 */
	
	@Override
	public SolutionSpace< V> join( SolutionSpace< V> space, String[] total_variables ) {
		//@todo auto-generated method
		assert false : "not implemented!";
	return null;
	}
	
	
	
	/** Returns a clone of this ScalarHypercube as the return hypercube, and NullHypercube.NULL as the optimal assignments
	 * @see Hypercube#consensus(java.lang.String, java.util.Map, boolean, boolean, boolean) 
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected ProjOutput< V, U > consensus (String varOut, Map< String, UtilitySolutionSpace<V, U> > distributions, 
			boolean maximum, boolean allSolutions, final boolean expect) {
		return new ProjOutput<V, U> (clone(), new String [0], NullHypercube.NULL);
	}
	
	/** Returns a clone of this ScalarHypercube as the return hypercube, and NullHypercube.NULL as the optimal assignments
	 * @see Hypercube#project(java.lang.String[], boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public ProjOutput< V, U > project( String[] variables_names, boolean maximum ) {
		return new ProjOutput<V, U> (clone(), new String [0], NullHypercube.NULL);
	}
	
	/** Returns a clone of this ScalarHypercube as the return hypercube, and NullHypercube.NULL as the optimal assignments
	 * @see Hypercube#project(java.lang.String, boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public ProjOutput< V, U > project( String variable_name, boolean maximum ) {
		return new ProjOutput<V, U> (clone(), new String [0], NullHypercube.NULL);
	}
	
	/** Returns a clone of this ScalarHypercube as the return hypercube, and NullHypercube.NULL as the optimal assignments
	 * @see Hypercube#project(int, boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public ProjOutput< V, U > project( int number_to_project, boolean maximum) {
		return new ProjOutput<V, U> (clone(), new String [0], NullHypercube.NULL);
	}
	
	/** Returns a clone of this ScalarHypercube as the return hypercube, and NullHypercube.NULL as the optimal assignments
	 * @see Hypercube#projectAll(boolean) 
	 */
	@SuppressWarnings("unchecked")
	public ProjOutput<V, U> projectAll(boolean maximum) {
		return new ProjOutput<V, U> (clone(), new String [0], NullHypercube.NULL);
	}
	
	/** @see Hypercube#blindProject(java.lang.String, boolean) */
	@Override
	public ScalarHypercube<V, U> blindProject (String varOut, boolean maximize) {
		return this.clone();
	}
	
	/** @see Hypercube#blindProject(java.lang.String[], boolean) */
	@Override
	public ScalarHypercube<V, U> blindProject (String[] varsOut, boolean maximize) {
		return this.clone();
	}
	
	/** @see Hypercube#blindProjectAll(boolean) */
	@Override
	public U blindProjectAll (boolean maximize) {
		return this.getUtility(0);
	}
	
	/** @see Hypercube#min(java.lang.String) */
	@Override
	public ScalarHypercube<V, U> min (String variable) {
		return this.clone();
	}
	
	/** @see Hypercube#max(java.lang.String) */
	@Override
	public ScalarHypercube<V, U> max (String variable) {
		return this.clone();
	}
	
	/** Returns a clone of this ScalarHypercube, without modifications
	 * @see Hypercube#slice(java.lang.String, Addable)
	 */
	@Override
	public ScalarHypercube< V, U > slice( String var, V val ) {
		return clone();
	}
	
	/** Returns a clone of this ScalarHypercube, without modifications
	 * @see Hypercube#slice(java.lang.String[], V[][])
	 */
	@Override
	public ScalarHypercube< V, U > slice( String[] variables_names, V[][] sub_domains ) {
		return clone();
	}
	
	/** Returns a clone of this ScalarHypercube, without modifications
	 *  @see Hypercube#slice(java.lang.String[], V[])
	 */
	@Override
	public Hypercube<V, U> slice(String[] variables_names, V[] values) {
		return clone();
	}
	
	/** Returns a clone of this ScalarHypercube, without modifications
	 * @see Hypercube#slice(V[])
	 */
	@Override
	public ScalarHypercube< V, U > slice( V[] variables_values ) {
		return clone();
	}
	
	/** @see BasicHypercube#compose(java.lang.String[], BasicUtilitySolutionSpace) */
	@SuppressWarnings("unchecked")
	@Override
	public Hypercube<V, U> compose(String[] vars, BasicUtilitySolutionSpace< V, ArrayList<V> > substitution) {
		
		/// @todo Implement the case when the substitution is not a BasicHypercube
		BasicHypercube< V, ArrayList<V> > substCast = (BasicHypercube< V, ArrayList<V> >) substitution;
		
		if (substCast.getNumberOfVariables() == 0) // the substitution is scalar
			return this.clone();
		
		U[] utilities = (U[]) Array.newInstance(this.values.getClass().getComponentType(), substCast.values.length);
		Arrays.fill(utilities, this.values[0]);
		this.incrNCCCs(1);
		return this.newInstance(substCast.variables.clone(), substCast.domains.clone(), utilities, this.infeasibleUtil);
	}
	
	/** Returns a clone of this SclararHypercube if its utility is higher that the threshold; \c null otherwise
	 * @see Hypercube#split(Addable, boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Hypercube< V, U > split( U threshold, boolean maximum ) {
		
		this.incrNCCCs(1);
		U utility = values[0];
		if ((maximum && utility.compareTo(threshold) > 0)
				|| (!maximum && utility.compareTo(threshold) < 0)) {
			return clone();
		} else {
			return NullHypercube.NULL;
		}
	}
	
	/** Returns a clone of this ScalarHypercube, without modifications
	 * @see Hypercube#changeVariablesOrder(java.lang.String[])
	 */
	@Override
	public ScalarHypercube< V, U > changeVariablesOrder( String[] variables_order ) {
		return clone();
	}
	
	/** @see Hypercube#equals(Object) */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals ( Object hypercube ) {
		
		this.incrNCCCs(1);
		return (hypercube instanceof ScalarHypercube && 
				((ScalarHypercube<V, U>)hypercube).getUtility(0).equals(values[0]));
		
	}
	
	/**@see Hypercube#clone() */
	@SuppressWarnings("unchecked")
	@Override
	public ScalarHypercube< V, U > clone () {
		return new ScalarHypercube< V, U > (values[0], this.infeasibleUtil, (Class<V[]>) this.domains.getClass().getComponentType());
	}
	
	
	/** 
	 * It returns the internal order of variables used within a solutionSpace. It
	 * is used for any function which assumes default/internal order of variables.
	 * 
	 * @return  String array containing the names of the variables of the solutionSpace
	 */
	@Override
	public String[] getVariables() {
		return new String [0];
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
	
	
	/**Augments solutionSpace (new combination of variables values) and the corresponding utility.
	 * @note If the corresponding assignment already exist in solutionSpace this method will only modify the corresponding utility value
	 * @param variables_values new variables variables
	 */
	@Override
	public void augment( V[] variables_values ) {
		//@todo auto-generated method
		assert false : "not implemented!";
	}
	
	
	/** Computes the join of this solutionSpace with the input solutionSpace. Order of variables
	 * is chosen as it fits.
	 */
	@Override
	public SolutionSpace< V> join( SolutionSpace< V > space)  {
		//@todo auto-generated method
		assert false : "not implemented!";
	return null;
	}
	
	/**Returns a SolutionSpace object obtained by joining the SolutionSpace for which this  method is called and the SolutionSpaces 
	 * present in the array of SolutionSpaces given to this method as a parameter.
	 * @param spaces             an array of the solutionSpaces to be added to this solutionSpace
	 * @param total_variables_order  the order of the variables in all solutionSpaces
	 * @return SolutionSpace object obtained by joining this SolutionSpace with all the SolutionSpaces in the array of SolutionSpaces
	 * Originally this function in the hypercubes assumes that order is not conflicting.
	 */
	@Override
	public SolutionSpace< V > join( SolutionSpace< V >[] spaces, String[] total_variables_order )  {
		//@todo auto-generated method
		assert false : "not implemented!";
	return null;
	}
	
	
	
	/* First group of operations which operate on one solutionSpace */
	
	
	
	/**Augments solutionSpace (new combination of variables values) and the corresponding utility.
	 * @note If the corresponding assignment already exist in solutionSpace this method will only modify the corresponding utility value
	 * @param variables_values new variables variables
	 * @param utility_value    corresponding utility values
	 */
	@Override
	public void augment( V[] variables_values, U utility_value ) {
		//@todo auto-generated method
		assert false : "not implemented!";
	}
	
	
	/**Checks if this solutionSpace is included in the provided SolutionSpace (i.e. both contain the same variables, 
	 * and all assignments in the utility diagram appear with the same utility values in the solutionSpace).
	 * @param space solutionSpace object
	 * @return true if this UtilityDiagram is included in the provided solutionSpace object, and false if else
	 */
	@Override
	public boolean isIncludedIn( UtilitySolutionSpace< V, U > space )  {
		//@todo auto-generated method
		assert false : "not implemented!";
	return false;
	}
	
	/** @see Hypercube#join(UtilitySolutionSpace, boolean, boolean) */
	@Override
	public UtilitySolutionSpace< V, U > join( UtilitySolutionSpace< V, U > space, boolean addition, boolean minNCCCs)  {
		return this.join(space, space.getVariables(), addition, minNCCCs);
	}


	/** @see Hypercube#expectation(java.util.Map) */
	@Override
	public UtilitySolutionSpace<V, U> expectation(Map< String, UtilitySolutionSpace<V, U> > distributions) {
		return this;
	}

	/** Returns an empty sample set
	 * @see Hypercube#sample(int) 
	 */
	@Override
	public Map<V, Double> sample(int nbrSamples) {
		return new HashMap<V, Double> (0);
	}
	
	/** @see Hypercube#iterator() */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator() {
		
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (this.values[0], this.infeasibleUtil, null);
	}
	
	/** @see BasicHypercube#iterator(String[]) */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator(String[] variables) {
		
		assert variables.length == 0;
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (values[0], this.infeasibleUtil, null);
	}

	/** @see BasicHypercube#iterator(String[], V[][]) */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator(String[] variables, V[][] domains) {
		
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (values[0], variables, domains, null, this.infeasibleUtil, null);
	}

	/** @see Hypercube#iterator(java.lang.String[], V[][], V[]) */
	@Override
	public UtilitySolutionSpace.Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment) {
		
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (values[0], variables, domains, assignment, this.infeasibleUtil, null);
	}

	/** @see Hypercube#sparseIter() */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter() {
		
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (this.values[0], this.infeasibleUtil, this.infeasibleUtil);
	}
	
	/** @see BasicHypercube#sparseIter(String[]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter(String[] variables) {
		
		assert variables.length == 0;
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (values[0], this.infeasibleUtil, this.infeasibleUtil);
	}

	/** @see BasicHypercube#sparseIter(String[], V[][]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter(String[] variables, V[][] domains) {
		
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (values[0], variables, domains, null, this.infeasibleUtil, this.infeasibleUtil);
	}

	/** @see Hypercube#sparseIter(java.lang.String[], V[][], V[]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment) {
		
		this.incrNCCCs(1);
		return new ScalarSpaceIter<V, U> (values[0], variables, domains, assignment, this.infeasibleUtil, this.infeasibleUtil);
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
