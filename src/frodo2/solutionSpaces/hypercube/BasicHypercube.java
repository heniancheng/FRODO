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

/** Classes implementing hypercubes, which are explicit table spaces */
package frodo2.solutionSpaces.hypercube;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** A basic hypercube that stores one utility per combination of assigments to variables.
 * 
 * @author Nacereddine Ouaret, Thomas Leaute, Radoslaw Szymanek, Stephane Rabie
 * 
 * The difference with its subclass Hypercube is that a BasicHypercube can contain utilities that are not Addable.
 * The consequence is that BasicHypercube does not provide any method that requires adding or comparing utilities.
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */

public class BasicHypercube < V extends Addable<V>, U extends Serializable > 
implements BasicUtilitySolutionSpace<V, U>, Externalizable {

	/** The names of the variables of the hypercube ordered according to their order in the hypercube */
	protected String[] variables;
	
	/** The domains of the variables of the hypercube */
	protected V[][] domains;
	
	/** The class of V */
	protected transient Class<V> classOfV;
	
	/** An array of size variables.length that can be reused, instead of creating a new array each time we need one */
	protected transient V[] assignment;
	
	/** The utility values */
	protected U[] values;
	
	/** The problem that should be notified of constraint checks */
	protected ProblemInterface<V, ?> problem;
	
	/** The actual number of utilities values in the array **/
	protected int number_of_utility_values;
	
	/** For each variable, a hashmap that maps every value
	 *  in the domain of this variable to a step in the utility values array.
	 *  
	 *  This is used to speed up access to the utility value of a given combination of variable-value assignments, 
	 *  by avoiding to recompute the position of that utility value in the utility array from scratch every time. 
	 */
	protected transient HashMap< V, Integer >[] steps_hashmaps;
	
	/** The types of spaces that we know how to handle */
	private static HashSet< Class<?> > knownSpaces;
	
	static {
		knownSpaces = new HashSet< Class<?> > ();
		knownSpaces.add(BasicHypercube.class);
		knownSpaces.add(ScalarBasicHypercube.class);
		knownSpaces.add(Hypercube.class);
		knownSpaces.add(Hypercube.NullHypercube.class);
		knownSpaces.add(ScalarHypercube.class);
		knownSpaces.add(BlindProjectOutput.class);
		knownSpaces.add(ExpectationOutput.class);
	}
	
	/** The name of this space, if any */
	protected String name = "";
	
	/** The name of the underlying relation */
	private String relationName = "";
	
	/** The owner of this space */
	private String owner = null;
	
	/** -INF if we are maximizing, +INF if we are minimizing */
	protected U infeasibleUtil;
	
	/**Construct a new BasicHypercube with provided variables names, the domains of these variables and the utility values
	 * @param variables_order 		the array containing the variables names ordered according to their order in the hypercube
	 * @param variables_domains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utility_values 		the utility values contained in a one-dimensional array. there should be a utility value for each 
	 * 								possible combination of values that the variables may take.
	 * @param infeasibleUtil 		-INF if we are maximizing, +INF if we are minimizing
	 * @warning variables_domains parameter needs to be sorted in ascending order.
	 * @warning utility_values needs to be properly ordered, the first utility corresponds to the 
	 * assignment in which each variable is assigned its smallest value.
	 */
	public BasicHypercube ( String[] variables_order, V[][] variables_domains, U[] utility_values, U infeasibleUtil ) {
		this(variables_order, variables_domains, utility_values, infeasibleUtil, null);
	}
	
	/**Construct a new BasicHypercube with provided variables names, the domains of these variables and the utility values
	 * @param variables_order 		the array containing the variables names ordered according to their order in the hypercube
	 * @param variables_domains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utility_values 		the utility values contained in a one-dimensional array. there should be a utility value for each 
	 * 								possible combination of values that the variables may take.
	 * @param infeasibleUtil 		-INF if we are maximizing, +INF if we are minimizing
	 * @param problem 				the problem to be notified of constraint checks
	 * @warning variables_domains parameter needs to be sorted in ascending order.
	 * @warning utility_values needs to be properly ordered, the first utility corresponds to the 
	 * assignment in which each variable is assigned its smallest value.
	 */
	@SuppressWarnings("unchecked")
	public BasicHypercube ( String[] variables_order, V[][] variables_domains, U[] utility_values, U infeasibleUtil, ProblemInterface<V, ?> problem ) {
		
		assert variables_order.length > 0  : "A hypercube must contain at least one variable";
		
		assert variables_order.length == variables_domains.length : "A hypercube must specify a domain for each of its variables";
		
		this.variables = variables_order;
		this.domains = variables_domains;
		this.values = utility_values;
		this.number_of_utility_values = values.length;
		this.classOfV = (Class<V>) variables_domains.getClass().getComponentType().getComponentType();
	    this.assignment = (V[]) Array.newInstance(this.classOfV, this.variables.length);
	    this.infeasibleUtil = infeasibleUtil;
	    this.problem = problem;
		
		//fill the steps hashmaps
		setStepsHashmaps();
	}
	
	/** Empty constructor that does nothing */
	public BasicHypercube () { }
	
	/**Based on the hypercube parameters this method fills the steps Hashmap that maps each variable of the hypercube
	 * to another Hashmaps that maps each possible value of this variable to a step in the utility values array
	 */
	void setStepsHashmaps() {
		this.setStepsHashmaps(this.variables, this.domains, this.number_of_utility_values);
	}
	
	/** Fills the step hashMap of the hypercube
	 * @param variables2                list of variables
	 * @param domains2                  list of domains
	 * @param number_of_utility_values2 number of utility values
	 */
	@SuppressWarnings("unchecked")
	void setStepsHashmaps(String[] variables2, V[][] domains2, int number_of_utility_values2) {
		//construct the steps hashmaps.
		HashMap< V, Integer > steps;
		V[] domain;
		int domain_size;
		int number_of_variables = variables2.length;
		steps_hashmaps = new HashMap [ number_of_variables ];
		
		//init the step integer
		int step = number_of_utility_values2;
		
		//for every variable in the list of variables.
		for(int i = 0; i < number_of_variables; i++) {
			//the domain of the ith variable in the list of variables
			domain = domains2[ i ];
			//size of the domain of the ith variable in the array of variables
			domain_size = domain.length;
			
			//the smallest step of the current variable is equivalent to the smallest step of the previous variable 
			//divided by the size of the domain of the current variable
			step = step / domain_size;
			
			//hashmap that maps a value of a variable to a step in the utility values "values" array
			steps = new HashMap<V, Integer>(domain_size);
			for( int j = 0, step_tmp = 0;  j < domain_size;  j++, step_tmp += step )
				steps.put(domain[ j ], step_tmp);
			
			steps_hashmaps[ i ] = steps;
		}
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(this.name);
		out.writeObject(this.relationName);
		
		// Write the variables
		assert this.variables.length < Short.MAX_VALUE : "Too many variables to fit in a short";
		out.writeShort(this.variables.length);
		for (int i = 0; i < this.variables.length; i++) 
			out.writeObject(this.variables[i]);
		
		// Write the domains
		assert this.domains.length < Short.MAX_VALUE : "Too many domains to fit in a short";
		out.writeShort(this.domains.length); // number of domains
		V[] dom = this.domains[0];
		assert dom.length < Short.MAX_VALUE : "Too many values to fit in a short";
		out.writeShort(dom.length); // size of first domain
		out.writeObject(dom[0]); // first value of first domain
		final boolean externalize = dom[0].externalize();
		for (int i = 1; i < dom.length; i++) { // remaining values in first domain
			if (externalize) 
				dom[i].writeExternal(out);
			else 
				out.writeObject(dom[i]);
		}
		for (int i = 1; i < this.domains.length; i++) { // remaining domains
			dom = this.domains[i];
			assert dom.length < Short.MAX_VALUE : "Too many values to fit in a short";
			out.writeShort(dom.length); // size of domain
			for (int j = 0; j < dom.length; j++) { // each value in the domain
				if (externalize) 
					dom[j].writeExternal(out);
				else 
					out.writeObject(dom[j]);
			}
		}
		
		out.writeObject(this.infeasibleUtil);
		
		// Write the utilities
		this.writeUtilities(out);
	}
	
	/** Serializes the utilities
	 * @param out 			the output stream
	 * @throws IOException 	if an I/O error occurs
	 */
	protected void writeUtilities (ObjectOutput out) throws IOException {
		
		out.writeInt(this.number_of_utility_values); // number of utilities
		out.writeObject(this.values.getClass().getComponentType()); // class of U
		for (int i = 0; i < this.number_of_utility_values; i++) 
			out.writeObject(this.values[i]); // each utility
		
		this.incrNCCCs(this.number_of_utility_values);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		this.name = (String) in.readObject();
		this.relationName = (String) in.readObject();
		
		// Read the variables
		this.variables = new String [in.readShort()];
		for (int i = 0; i < this.variables.length; i++) 
			this.variables[i] = (String) in.readObject();
		
		// Read the domains
		final int nbrDoms = in.readShort(); // number of domains
		int domSize = in.readShort(); // size of first domain
		V val = (V) in.readObject(); // first value of first domain
		final boolean externalize = val.externalize();
		V[] dom = (V[]) Array.newInstance(val.getClass(), domSize);
		this.domains = (V[][]) Array.newInstance(dom.getClass(), nbrDoms);
		this.domains[0] = dom;
		dom[0] = val;
		for (int i = 1; i < domSize; i++) { // read the remaining values in the first domain
			if (externalize) {
				val = val.getZero();
				val.readExternal(in);
				dom[i] = (V) val.readResolve();
			} else 
				dom[i] = (V) in.readObject();
		}
		for (int i = 1; i < nbrDoms; i++) { // read the remaining domains
			domSize = in.readShort(); // domain size
			dom = (V[]) Array.newInstance(val.getClass(), domSize);
			this.domains[i] = dom;
			for (int j = 0; j < domSize; j++) { // each value in the domain
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					dom[j] = (V) val.readResolve();
				} else 
					dom[j] = (V) in.readObject();
			}
		}
		
		this.infeasibleUtil = (U) in.readObject();
		
		// Read the utilities
		this.readUtilities(in);

		// Now restore the transient fields
		this.classOfV = (Class<V>) this.domains.getClass().getComponentType().getComponentType();
		this.assignment = (V[]) Array.newInstance(this.classOfV, this.variables.length);
		setStepsHashmaps();
	}
	
	/** Deserializes the utilities
	 * @param in 						the input stream
	 * @throws ClassNotFoundException 	should never happen
	 * @throws IOException 				if an I/O error occurs
	 */
	@SuppressWarnings("unchecked")
	protected void readUtilities (ObjectInput in) throws ClassNotFoundException, IOException {
		
		this.number_of_utility_values = in.readInt();
		this.values = (U[]) Array.newInstance((Class<U>) in.readObject(), this.number_of_utility_values);
		for (int i = 0; i < this.number_of_utility_values; i++) 
			this.values[i] = (U) in.readObject();
	}

	/** Returns the number of utility values in the hypercube
	 * @return integer representing the number of utility values in the hypercube
	 */
	public long getNumberOfSolutions() {
		return number_of_utility_values;
	}
	
	/** Modifies the number of utility values in the hypercube (in case the utility array is bigger then the actual number of elements)
	 * @param new_number_of_utilities new number of utilities of the hypercube
	 */
	protected void setNumberOfSolutions(int new_number_of_utilities) {
		number_of_utility_values = new_number_of_utilities;
	}
	
	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getUtility(V[]) 
	 * @warning returns null if the input does not specify a value for each variable in the space
	 */
	public U getUtility( V[] variables_values ) {
		
		if (variables_values.length < this.variables.length) 
			return null;
		
		this.incrNCCCs(1);
		
		int index = getIndexOfUtilityValue( variables_values );
		if (index < 0) 
			return this.infeasibleUtil;
		
		return values[ index ];
	}
	
	/** Returns the utility of the input assignment, without incrementing the NCCC counter 
	 * @param variables_values 	assignment
	 * @return corresponding utility
	 */
	private U getUtilityNoNCCCs( V[] variables_values ) {
		
		if (variables_values.length < this.variables.length) 
			return null;
		
		int index = getIndexOfUtilityValue( variables_values );
		if (index < 0) 
			return this.infeasibleUtil;
		
		return values[ index ];
	}
	
	/** 
	 * @see BasicUtilitySolutionSpace#getUtility(java.lang.String[], V[]) 
	 * @warning returns \c null if not all variables in the space are assigned values
	 */
	public U getUtility( String[] variables_names, V[] variables_values ) {

		//Note: "variables_names" and "variables_values" may contain variables that are not present in this hypercube but must 
		//provide a value for each variable of this Hypercube otherwise a null is returned.
		
		final int variables_size = variables_names.length;
		final int variables_size2 = variables.length;
		
		// loop over all the variables present in the array "variables_names"
		String var;
		ext: for(int i = 0; i < variables_size2; i++){
			var = variables[ i ];
			for(int j = 0; j < variables_size; j++){
				if( var.equals( variables_names[ j ] ) ) {
					this.assignment[ i ] = variables_values[ j ];
					continue ext;
				}
			}
			
			// No value found for variable i
			return null;
		}
		
		return getUtility( this.assignment );
	}
	
	/** 
	 * @see BasicUtilitySolutionSpace#getUtility(java.util.Map) 
	 * @warning returns \c null if not all variables in the space are assigned values
	 */
	public U getUtility(Map<String, V> assignments) {
		
		V val;
		for (int i = this.variables.length - 1; i >= 0; i--) {
			val = assignments.get(this.variables[i]);
			if (val == null) 
				return null;
			this.assignment[i] = val;
		}
		
		return this.getUtility(this.assignment);
	}

	/** Returns an object representing the utility value corresponding to the provided
	 * variables values
	 * 
	 * This method assumes that the order of the variables in the input array of variables 
	 * is consistent with the order used in the hypercube.
	 * @param variables_names   the names of the variables
	 * @param variables_values  the values of the variables
	 * @return the utility value corresponding the provided variables values. It 
	 *         returns \c null if no utility variable was found. 
	 */
	public U getUtilityValueSameOrder( String[] variables_names, V[] variables_values ) {

		//Note: "variables_names" and "variables_values" may contain variables that are not present in this hypercube but must 
		//provide a value for each variable of this Hypercube otherwise a null is returned.
		
		int variables_size = variables_names.length;
		int variables_size2 = variables.length;
		
		// loop over all the variables present in the array "variables_names"
		int j = 0;
		String var;
		ext: for(int i = 0; i < variables_size2; i++){
			var = variables[ i ];
			for( ; j < variables_size; j++){
				if( var.equals( variables_names[ j ] ) ) {
					this.assignment[ i ] = variables_values[ j ];
					continue ext;
				}
			}
			
			// No value found for variable i
			return null;
		}
		
		return getUtility( this.assignment );
	}
	
	/** @see BasicUtilitySolutionSpace#getUtility(long) */
	public U getUtility( long index ){
		
		if (index >= values.length) 
			return null;
		
		this.incrNCCCs(1);
		return values[(int) index];
	}
	
	/** Increments the number of constraint checks
	 * @param incr 	the increment
	 */
	protected void incrNCCCs (long incr) {
		if (this.problem != null) {
			
			problem.incrNCCCs(incr);
		}
	}
	
	/** @return the class used for utility values */
	@SuppressWarnings("unchecked")
	public Class<U> getClassOfU () {
		return (Class<U>) this.values.getClass().getComponentType();
	}
	
	/** Returns the index of the utility value corresponding to the provided variables values
	 * @param variables_values the variables values
	 * @return Integer representing the index of the utility value corresponding to the provided variables values. 
	 * 	       If given assignment is not present then index of utility value for that assignment is equal to -1. 
	 */
	protected int getIndexOfUtilityValue( V[] variables_values ) {
		//index of the utility value that will be returned
		int utility_index = 0;
		//number of variables in the hypercube
		int number_of_variables = variables.length;
		
		//loop over all the variables in this hypercube
		for( int i = 0 ;i < number_of_variables; i++ ) {
			//find the step corresponding to the variable taking the associated value in the "variables_values"
			HashMap<V, Integer> steps_hashmap = steps_hashmaps[i];
			if ( steps_hashmap == null )
				return -1;
			Integer step = steps_hashmap.get( variables_values[i] );
			if (step == null) 
				return -1;
			utility_index += step;
		}
		
		return utility_index;
	}
	
	/** Sets the utility value corresponding to a given assignment to variables
	 * @param variables_values values for the variables, in the same order as in the hypercube
	 * @param utility the new utility value
	 * @see BasicUtilitySolutionSpace#setUtility(V[], java.io.Serializable)
	 */
	public boolean setUtility (V[] variables_values, U utility) {
		
		int index = getIndexOfUtilityValue( variables_values );
		if (index == -1)
			return false;
		
		values[ index ] = utility;
		
		return true;
	
	}

	/** @see BasicUtilitySolutionSpace#setUtility(long, java.io.Serializable) */
	public void setUtility(long index, U utility) {
		assert index < Integer.MAX_VALUE : "A hypercube can only contain up to 2^31-1 solutions";
		this.values[(int) index] = utility;
	}

	/** Returns the names of the variables of the hypercube
	 * @return  String array containing the names of the variables of the hypercube
	 */
	public String[] getVariables() {
		return variables;
	}
	
	/** Computes the variable assignments corresponding to a given utility value
	 * @param variables_values 	the array to be filled
	 * @param index 			index of the value in the utility array
	 * @param steps 			array storing the step for each variable
	 */
	private void fillVariablesValues(V[] variables_values, int index, int[] steps) {

		int number_of_variables = variables.length;
		for(int i = 0 ; i < number_of_variables ; i++) {
			int step = steps[i];
			variables_values[i] = domains[i][index/step];
			index %= step;
		}
	}
	
	/** Returns the number of variables in the hypercube
	 * @return integer representing the number of variables in the hypercube
	 */
	public int getNumberOfVariables() {
		return variables.length;
	}
	
	/** Returns the variable corresponding to the provided index
	 * @param index index of the variable in this hypercube
	 * @return String representing the variable corresponding to the provided index
	 */
	public String getVariable( int index ) {
		return variables[index];
	}
	
	/** @see SolutionSpace#renameVariable(String, String) */
	public void renameVariable(String oldName, String newName) {
		
		int index = this.getIndex(oldName);
		if (index >= 0) 
			this.variables[index] = newName;
	}

	/** @see SolutionSpace#renameAllVars(java.lang.String[]) */
	public BasicHypercube<V, U> renameAllVars(String[] newVarNames) {
		assert newVarNames.length == this.variables.length : "Incorrect number of variables in input array";
		return this.newInstance(newVarNames, domains, values, infeasibleUtil);
	}

	/** Return the index of the input variable in this hypercube
	 * @param variable the name of the variable
	 * @return integer representing the index of the variable in the hypercube
	 */
	public int getIndex( String variable ) {
		int number_of_variables = variables.length;
		for( int i = 0; i < number_of_variables; i++ )
			if( variables[ i ].equals(variable))
				return i;
		return -1;
	}
	
	/** Returns the array containing the domains of the variables
	 * @return  two-dimensional array containing the domains of the variables
	 */
	public V[][] getDomains() {
		return domains;
	}
	
	/** Returns an array all the possible values that the variable provided as a parameter
	 * can take in this hypercube
	 * @param variable the name of the variable
	 * @return  the variable domain
	 */
	public V[] getDomain( String variable ){
		//number of  variables in this hypercube
		int number_of_variables = variables.length;
		
		//find the index of the variable in the ordered array of the variables of this hypercube
		for( int i = 0; i < number_of_variables; i++ ){
			if( variable.equals( variables[ i ] ) )
				 //return the corresponding array in the domains array
				return domains[ i ];
		}
		// return null if the variable was not found in the ordered array of the variables of this hypercube
		return null; 
	}
	
	/** Returns the domain of the variable that corresponds to the provided index
	 * @param index index of the variable
	 * @return Array containing all the possible values of the variable corresponding to the provided variable
	 */
	public V[] getDomain( int index ) {
		if( index < variables.length)
			return domains[index];
		
		return null;
	}
	
	/** Returns the variable's domain if <code>index</code> is the variables index in the array of variables order
	 * @param variable the name of the variable
	 * @param index    the index of the variable
	 * @return         Array containing the domain of the variable or null if the variable doesn't exist in this hypercube
	 *                 at the provided index 
	 */
	public V[] getDomain( String variable, int index ) {
		if( index < variables.length)
			if( variables[ index ].equals( variable ) )
				return domains[ index ];
		
		return null;
	}
	
	/** @see SolutionSpace#setDomain(String, V[]) */
	public void setDomain(String var, V[] dom) {
		
		int index = this.getIndex(var);
		if (index >= 0) {
			
			// Adapt the steps_hashmaps
			HashMap<V, Integer> oldSteps = this.steps_hashmaps[index];
			HashMap<V, Integer> newSteps = new HashMap<V, Integer> (oldSteps.size());
			V[] oldDom = this.domains[index];
			for (int i = oldDom.length - 1; i >= 0; i--) 
				newSteps.put(dom[i], oldSteps.get(oldDom[i]));
			this.steps_hashmaps[index] = newSteps;
			
			// Set the domain
			this.domains[index] = dom;
		}
	}

	/** @see java.lang.Object#toString() */
	public String toString() {
		StringBuilder hypercube = new StringBuilder();
		
		hypercube.append("- " + this.getClass().getSimpleName());
		
		// Display the name of the hypercube
		if (this.name != null) 
			hypercube.append(" (" + this.name + ")");
		
		hypercube.append(":\n");
		
		int length = variables.length;
		
		for( int i=0; i < length; i++) {
			hypercube.append( variables[ i ] );
			hypercube.append( " : " );
			hypercube.append((domains == null || domains[i] == null ? null : Arrays.asList(domains[i])));
			hypercube.append( "\n" );
		}
		
//		hypercube.append("steps_hashmaps: " + Arrays.toString(this.steps_hashmaps) + "\n");
		
		if(values != null) 
			hypercube.append(Arrays.toString(this.values) + "\n");

		return hypercube.toString();
	}
	
	/** @see BasicUtilitySolutionSpace#prettyPrint(java.io.Serializable) */
	public String prettyPrint (U ignoredUtil) {
		StringBuilder builder = new StringBuilder ("Hypercube\n");
		
		if (ignoredUtil != null) 
			builder.append("\t(ignoring solutions with utility " + ignoredUtil + ")\n");
		
		builder.append("\t" + Arrays.asList(this.variables) + "\n");
		
		SparseIterator<V, U> iter = this.sparseIter();
		U util = null;
		while ( (util = iter.nextUtility()) != null ) 
			if (! util.equals(ignoredUtil)) 
				builder.append("\t" + Arrays.asList(iter.getCurrentSolution()) + " -> " + util + "\n");
		
		return builder.toString();
	}
	
	/**Check if this hypercube is the NULL hypercube
	 * @return true if the hypercube is the NULL hypercube and false if else
	 */
	public boolean isNull() {
		return this == Hypercube.NullHypercube.NULL;
	}
	


	/** @see BasicUtilitySolutionSpace#augment(V[], java.io.Serializable) */
	public void augment(V[] variables_values, U utility_value) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		
	}

	/** Computes the subtraction of two arrays
	 * @param <T> 		the arrays' component type
	 * @param array1 	the first array
	 * @param array2 	the second array
	 * @return substraction
	 */
	@SuppressWarnings("unchecked")
	public static <T> T[] sub( T[] array1, T[] array2 ) {
		int array1_size = array1.length;
		
		int index = 0;
		
		boolean not_found;
		
		Class<?> tClass = array1.getClass().getComponentType();
		T[] sub_tmp = (T[]) Array.newInstance(tClass, array1_size);
		
		for( T v1 : array1 ) {
			not_found = true;
			for( T v2 : array2 ) {
				if( v1.equals(v2) ) {
					not_found = false;
					break;
				}
			}
			
			if( not_found ) {
				sub_tmp[ index ] = v1;
				index ++;
			}	
		}
		
		if(index == 0) return (T[]) Array.newInstance(tClass, 0);
		
		T[] sub = (T[]) Array.newInstance(tClass, index);
		System.arraycopy(sub_tmp, 0, sub, 0, index);
		return sub;
	}
	
	/**Computes the intersection between two arrays
	 * @param <V> the type of the entries in the arrays
	 * @param array1 the first array
	 * @param array2 the second array
	 * @return Array containing the intersection of array1 and array2; \c null (NOT an empty array) if the intersection is empty
	 * @warning This assumes that the two arrays are ordered. 
	 */ 
	@SuppressWarnings("unchecked")
	public static < V extends Addable<V> > V[] intersection( V[] array1, V[] array2 ) {
		
		if (array1.length == 0 || array2.length == 0)
			return null;
		
		if (Arrays.equals(array1, array2)) 
			return array1;
		
		ArrayList<V> out = new ArrayList<V> (array1.length);
		ext: for (V val1 : array1) {
			for (V val2 : array2) {
				if (val1.equals(val2)) {
					out.add(val1);
					continue ext;
				}
			}
		}
		final int outSize = out.size();
		
		if (outSize == array1.length) 
			return array1;
		else if (outSize == array2.length) 
			return array2;
		else if (outSize == 0)
			return null;
		else
			return out.toArray((V[]) Array.newInstance(array1[0].getClass(), outSize));
	}
	
	/**
	 * Return a new BasicHypercube by changing the order of the variables of the hypercube
	 * @param variables_order 	the new order of the variables
	 * @return BasicHypercube object obtained by changing the order of the variables
	 * @warning The input variable order must contain only and all the variables that belong to the hypercube. 
	 * @see BasicUtilitySolutionSpace#changeVariablesOrder(java.lang.String[])
	 */
	@SuppressWarnings("unchecked")
	public BasicHypercube< V, U > changeVariablesOrder( String[] variables_order ) {
		
		assert sub(variables, variables_order).length == 0 && sub(variables_order, variables).length == 0 : 
			Arrays.asList(variables).toString() + " does not match input " + Arrays.asList(variables_order).toString();
		
		//number of the variables in the hypercube
		int number_of_variables = variables.length;
		//the array that will contain the the domains of the variables in the new hypercube
		Class<?> domainClass = this.domains.getClass().getComponentType();
		V[][] new_domains = (V[][])  Array.newInstance(domainClass, number_of_variables);
		
		//this array will contain the index of each variable in the new order
		int [] variables_indexes = new int[ number_of_variables ];
		
		String variable;
		for(int i = 0; i < number_of_variables; i++) {
			variable = variables[ i ];
			for(int j = 0; j < number_of_variables; j++)
				if( variable.equals( variables_order[ j ] ) ) {
					variables_indexes[ i ] = j;
					new_domains[ j ] = domains[ i ];
					break;
				}
		}
		
		//this array will contain the utility values of the hypercube order according to the new order of variables
		U[] new_values = (U[]) Array.newInstance(this.values.getClass().getComponentType(), number_of_utility_values);
		//an array of indexes used the go through the array of the domains of the variables of the hypercube
		int[] indexes = new int[ number_of_variables ];
		//this array will contain the variables values ordered according the current order of variables
		//this array will contain the variables values ordered according the new order of variables
		V[] variables_values_tmp = (V[]) Array.newInstance(this.classOfV, number_of_variables);
		
		int index_to_increment = number_of_variables - 1;
		int index;
		V[] domain;
		for( int i = 0; i < number_of_utility_values; i++ ) {
			for( int j = number_of_variables - 1; j >= 0; j--) {
				index = indexes[ j ];
				domain = new_domains[ j ];
				//the current value of the variable
				variables_values_tmp[ j ] = domain[ index ];
				
				//increment or not this value index of this variable
				if( j == index_to_increment ) {
					index = ( index + 1) % domain.length;
					indexes[j] = index;
					// when a whole loop over all values of this variable is done increment also the next variable which 
					//is previous to this one in order
					if(index == 0)  index_to_increment--;
					else index_to_increment = number_of_variables - 1;
				}
			}
			
			//change the order of the variables values to correspond the current order
			for(int j = 0; j < number_of_variables; j++)
				this.assignment[ j ] = variables_values_tmp[ variables_indexes[ j ] ];
			
			//get the corresponding utility value and insert it in the new array of utility values
			new_values[ i ] = getUtilityNoNCCCs( this.assignment ); // re-ordering the variables in a space should not require constraint checks; it is syntactic sugar
		}
		
		BasicHypercube< V, U > out = this.newInstance( variables_order, new_domains, new_values, this.infeasibleUtil );
		out.problem = this.problem; // the output should still count constraint checks if I do
		
		return out;
	}
	
	/** Changes the variable order of the hypercube by reordering the current utility array
	 * @param variables_order the new variables order
	 */
	@SuppressWarnings("unchecked")
	public void applyChangeVariablesOrder( String[] variables_order ) {
		
		variables_order = variables_order.clone();
		
		assert sub(variables, variables_order).length == 0 && sub(variables_order, variables).length == 0 : 
			Arrays.asList(variables).toString() + " does not match " + Arrays.asList(variables_order).toString();
		
		//number of the variables in the hypercube
		int number_of_variables = variables.length;
		//the array that will contain the the domains of the variables in the new hypercube
		Class<?> domainClass = this.domains.getClass().getComponentType();
		V[][] new_domains = (V[][])  Array.newInstance(domainClass, number_of_variables);
		
		//this array will contain the index of each variable in the new order
		int [] variables_indexes = new int[ number_of_variables ];
		
		String variable;
		for(int i = 0; i < number_of_variables; i++) {
			variable = variables[ i ];
			for(int j = 0; j < number_of_variables; j++)
				if( variable.equals( variables_order[ j ] ) ) {
					variables_indexes[ i ] = j;
					new_domains[ j ] = domains[ i ];
					break;
				}
		}
		
		int[] steps = new int[number_of_variables];
		for(int i = 0 ; i < number_of_variables ; i++)
			steps[i] = steps_hashmaps[i].get(domains[i][1]);
		
		//set steps_hashmaps according to the new domains
		this.setStepsHashmaps(variables_order, new_domains, number_of_utility_values);
		
		this.variables = variables_order;
		
		/*
		* There are two possible implementations for keeping track of already reordered utilities :
		* a boolean array or a HashSet
		* experimental results showed that boolean array is faster and actually consumes less memory
		*/
		//HashSet<Integer> already_reordered = new HashSet<Integer>();
		boolean[] already_reordered = new boolean[number_of_utility_values];
		
		//this array will contain the variables values ordered according the current order of variables
		V[] variables_values = (V[]) Array.newInstance(domainClass.getComponentType(), number_of_variables);
		
		//this array will contain the variables values ordered according the new order of variables
		V[] variables_values_tmp = (V[]) Array.newInstance(domainClass.getComponentType(), number_of_variables);
		
		U utility, utility_tmp;
		
		for( Integer i = new Integer(0); i < number_of_utility_values; i++ ) {
			
			if ( !already_reordered[i] /*!already_reordered.remove(i)*/ ) {
				fillVariablesValues(variables_values, i, steps);
			
				//change the order of the variables values to correspond the current order
				for(int j = 0; j < number_of_variables; j++)
					variables_values_tmp[ variables_indexes[ j ] ] = variables_values[ j ];
				
				int new_index = getIndexOfUtilityValue(variables_values_tmp);
				utility = values[i];
				
				while(new_index != i) {
					utility_tmp = values[new_index];
					values[new_index] = utility;
					utility = utility_tmp;
					//already_reordered.add(new_index);
					already_reordered[new_index] = true;
					
					fillVariablesValues(variables_values, new_index, steps);
					for(int j = 0; j < number_of_variables; j++)
						variables_values_tmp[ variables_indexes[ j ] ] = variables_values[ j ];
					new_index = getIndexOfUtilityValue(variables_values_tmp);
				}
				
				values[i] = utility;
				
			}
		}
		
		this.domains = new_domains;
	}
	
	/** Augments the hypercube by adding new variables at the beginning of its variables list
	 * @param new_variables new variables to augment the hypercube
	 * @param new_domains   domains of the new variables
	 * @return the augmented hypercube
	 */
	@SuppressWarnings("unchecked")
	public BasicHypercube< V, U > applyAugment(String[] new_variables, V[][] new_domains ) {
		
		int number_of_new_variables = new_variables.length;
		int number_of_variables = variables.length;
		
		assert number_of_new_variables == new_domains.length : "A domains must be specified for each new variable.";
		/// @todo check none of the new variables already belong to this hypercube 
		
		// number of variables in the augmented hypercubes
		int augmented_number_of_variables = number_of_new_variables + number_of_variables;
		
		// create the variables list of the augmented hypercube
		String[] augmented_variables = new String[augmented_number_of_variables];
		System.arraycopy(new_variables, 0, augmented_variables, 0, number_of_new_variables);
		System.arraycopy(variables, 0, augmented_variables, number_of_new_variables, number_of_variables);
		
		// create the domains list of the augmented hypercube
		Class<?> domainClass = this.domains.getClass().getComponentType();
		V[][] augmented_domains = (V[][])  Array.newInstance(domainClass, augmented_number_of_variables);
		System.arraycopy(new_domains, 0, augmented_domains, 0, number_of_new_variables);
		System.arraycopy(domains, 0, augmented_domains, number_of_new_variables, number_of_variables);
		
		int augmentation_factor = 1;
		
		for (int i = 0 ; i < number_of_new_variables ; i++) {
			assert Math.log((double) augmentation_factor) + Math.log((double) new_domains[i].length) < Math.log(Integer.MAX_VALUE) : 
				"Size of utility array too big for an int";
			augmentation_factor *= new_domains[i].length;
		}
		
		assert Math.log((double) augmentation_factor) + Math.log((double) number_of_utility_values) < Math.log(Integer.MAX_VALUE) : 
			"Size of utility array too big for an int";
		int augmented_number_of_utility_values = augmentation_factor*number_of_utility_values;
		
		if(augmented_number_of_utility_values > values.length) {
			//there is not enough space in the utility array, so we have to create a new hypercube
			
			U[] new_values = (U[]) Array.newInstance(this.values.getClass().getComponentType(), augmented_number_of_utility_values);
			
			for(int i = 0 ; i < augmentation_factor ; i++)
				System.arraycopy(values, 0, new_values, i*number_of_utility_values, number_of_utility_values);
			
			return this.newInstance(augmented_variables, augmented_domains, new_values, this.infeasibleUtil);
		}
		else {
			//there is enough space in the utility array, so we can reuse this hypercube
			
			for(int i = 1 ; i < augmentation_factor ; i++)
				System.arraycopy(values, 0, values, i*number_of_utility_values, number_of_utility_values);
			
			this.variables = augmented_variables;
			this.domains = augmented_domains;
			this.setNumberOfSolutions(augmented_number_of_utility_values);
			this.setStepsHashmaps();
			return this;
		}
	}

	/** @see BasicUtilitySolutionSpace#equivalent(BasicUtilitySolutionSpace) */
	public boolean equivalent(final BasicUtilitySolutionSpace<V, U> space) {
		
		if (space == null) 
			return false;
		
		// Check the number of variables
		int nbrVars = this.variables.length;
		if (nbrVars != space.getNumberOfVariables()) 
			return false;
		
		// Check that the input space has the same domains for my variables
		for (int i = 0; i < nbrVars; i++) 
			if (! Arrays.equals(this.domains[i], space.getDomain(this.variables[i]))) 
				return false;
		
		// Create two iterators with the same variable orders
		Iterator<V, U> myIter = this.iterator();
		Iterator<V, U> urIter = space.iterator(this.variables, this.domains);
		
		// Check that all utilities are the same
		U myUtil, urUtil;
		while (myIter.hasNext()) {
			urUtil = urIter.nextUtility();
			
			if ((myUtil = myIter.nextUtility()) == null) {
				if (urUtil != null) 
					return false;
			} else if (! myUtil.equals(urUtil)) 
				return false;
		}
		
		return true;
	}

	/** @see BasicUtilitySolutionSpace#isIncludedIn(BasicUtilitySolutionSpace) */
	public boolean isIncludedIn(BasicUtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#iterator() */
	@Override
	public Iterator<V, U> iterator() {
		return this.newIter(null, null, null, null);
	}
	
	/** @see BasicUtilitySolutionSpace#sparseIter() */
	@Override
	public SparseIterator<V, U> sparseIter() {
		return this.sparseIter(this.infeasibleUtil);
	}
	
	/** Creates a sparse iterator that skips a specific utility
	 * @param inf 	the utility to skip
	 * @return a sparse iterator
	 */
	protected SparseIterator<V, U> sparseIter(U inf) {
		return this.newIter(this.getVariables(), this.getDomains(), null, inf);
	}
	
	/** @see SolutionSpace#iterator(java.lang.String[]) */
	@Override
	public Iterator<V, U> iterator(String[] order) {
		return this.newIter(order, null, null, null);
	}
	
	/** @see SolutionSpace#sparseIter(java.lang.String[]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] order) {
		return this.newIter(order, null, null, this.infeasibleUtil);
	}
	
	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], V[][]) */
	@Override
	public Iterator<V, U> iterator(String[] variables, V[][] domains) {
		return this.iterator(variables, domains, null, null);
	}
	
	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], V[][]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains) {
		return this.iterator(variables, domains, null, this.infeasibleUtil);
	}
	
	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], V[][], V[]) */
	@Override
	public Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment) {
		return this.iterator(variables, domains, assignment, null);
	}
	
	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], V[][], V[]) */
	@Override
	public SparseIterator<V, U> sparseIter(String[] variables, V[][] domains, V[] assignment) {
		return this.iterator(variables, domains, assignment, this.infeasibleUtil);
	}
	
	/** Returns an iterator
	 * @param variables 	The variables to iterate over
	 * @param domains		The domains of the variables over which to iterate
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	The utility value that the sparse iterator should skip (if any)
	 * @return an iterator which allows to iterate over the given variables and their utilities 
	 */
	@SuppressWarnings("unchecked")
	private Iterator<V, U> iterator(String[] variables, V[][] domains, V[] assignment, final U skippedUtil) {
		
		// We want to allow the input list of variables not to contain all this space's variables
		final int nbrInputVars = variables.length;
		ArrayList<String> vars = new ArrayList<String> (nbrInputVars);
		ArrayList<V[]> doms = new ArrayList<V[]> (nbrInputVars);
		boolean correctInputs = true;
		
		// Go through the list of input variables
		for (int i = 0; i < nbrInputVars; i++) {
			
			// Record the variable
			String var = variables[i];
			vars.add(var);
			
			// Record the domain, as the intersection of the input domain with the space's domain, if any
			V[] myDom = this.getDomain(var);
			if (myDom == null) // unknown variable
				doms.add(domains[i]);
			else {
				myDom = intersection(myDom, domains[i]);
				doms.add(myDom);
				if (! Arrays.equals(myDom, domains[i])) // the domain has changed
					correctInputs = false;
			}
		}
		
		// Add the variables that are in this hypercube and not in the input list
		final int myNbrVars = this.variables.length;
		for (int i = 0; i < myNbrVars; i++) {
			String var = this.variables[i];
			if (! vars.contains(var)) {
				vars.add(var);
				doms.add(this.domains[i]);
				correctInputs = false;
			}
		}
		
		if (correctInputs) 
			return this.newIter(variables, domains, assignment, skippedUtil);
		
		final int nbrVarsIter = vars.size();
		return this.newIter(vars.toArray(new String [nbrVarsIter]), 
				doms.toArray((V[][]) Array.newInstance(domains.getClass().getComponentType(), nbrVarsIter)), 
				assignment, skippedUtil);
	}
	
	/** Creates a new iterator for this space
	 * @param variables 	The variables to iterate over
	 * @param domains		The domains of the variables over which to iterate
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param skippedUtil	A utility value that should be skipped (if \c null, nothing is skipped)
	 * @return a new iterator
	 */
	protected Iterator<V, U> newIter (String[] variables, V[][] domains, V[] assignment, final U skippedUtil) {
		
		if (variables == null) 
			return new BasicHypercubeIter<V, U> (this, assignment, skippedUtil);
		else if (domains == null) 
			return new BasicHypercubeIter<V, U> (this, variables, assignment, skippedUtil);
		else 
			return new BasicHypercubeIter<V, U> (this, variables, domains, assignment, skippedUtil);
	}
	
	/** @see SolutionSpace#augment(V[]) */
	public void augment(V[] variables_values) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		
	}

	/** @see SolutionSpace#join(SolutionSpace, java.lang.String[]) */
	public SolutionSpace<V> join( SolutionSpace<V> space, String[] total_variables) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}
	
	/** @see SolutionSpace#join(SolutionSpace) */
	public SolutionSpace< V> join( SolutionSpace< V > space) {
//		 @todo Auto-generated method stub
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
	public SolutionSpace< V > join( SolutionSpace< V >[] spaces, String[] total_variables_order ) {
//		 @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}
	
	/** @see SolutionSpace#join(SolutionSpace[]) */
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}
	

	/** Return a slice of this hypercube
	 * @param variables_names the variables to slice
	 * @param sub_domains     the domains of the slice
	 * @return Hypercube representing a slice of this hypercube
	 * @warning Returns itself if the slice has no effect
	 * @author Thomas Leaute
	 */
	@SuppressWarnings("unchecked")
	public BasicHypercube<V, U> slice( String[] variables_names, V[][] sub_domains ) {
		
		assert variables_names.length == sub_domains.length : "Must provide a domain for each provided variable";
		
		assert notEmptyDomains(sub_domains) : "Must provide a non-empty sub-domain for each provided variable";
		
		assert contains(variables_names, sub_domains) : 
			"Cannot slice one of the hypercube's variables over a subdomain that is not a subset of its original domain \n" +
			"sliced domains:  " + Arrays.toString(variables_names) + " = " + Arrays.deepToString(sub_domains) + "\n" +
			"initial domains: " + Arrays.toString(this.variables) + " = " + Arrays.deepToString(this.domains);
		
		List<String> inputVars = Arrays.asList(variables_names);
		int myNbrVars = this.variables.length;
		Class<?> domClass = sub_domains.getClass().getComponentType();
		V[][] iterDoms = (V[][]) Array.newInstance(domClass, myNbrVars);
		ArrayList<String> remainingVars = new ArrayList<String> (myNbrVars);
		ArrayList< V[] > remainingDoms = new ArrayList< V[] > (myNbrVars);
		
		// Go through the list of my variables
		long nbrRemainingUtils = 1;
		for (int i = 0; i < myNbrVars; i++) {
			String var = this.variables[i];
			
			// Look for this variable in the input list
			int inputIndex = inputVars.indexOf(var);
			if (inputIndex == -1) { // not in the input list
				V[] dom = this.domains[i];
				iterDoms[i] = dom;
				remainingVars.add(var);
				remainingDoms.add(dom);
				nbrRemainingUtils *= dom.length;
			} else {
				V[] dom = sub_domains[inputIndex];
				iterDoms[i] = dom;
				if (dom.length != 1) { // the variable should remain
					remainingVars.add(var);
					remainingDoms.add(dom);
					nbrRemainingUtils *= dom.length;
				}
			}
		}
		
		// Return itself if the slice has no effect
		if (nbrRemainingUtils == this.getNumberOfSolutions() && remainingDoms.size() == myNbrVars) { // same number of solutions and variables
			
			// Check that the domains use the same orders
			boolean same = true;
			for (int i = 0; i < myNbrVars; i++) {
				if (! Arrays.equals(this.domains[i], remainingDoms.get(i))) {
					same = false;
					break;
				}
			}
			
			if (same) 
				return this;
		}
		
		// Check if the output must be a scalar hypercube
		if (remainingVars.isEmpty()) {
			
			// Retrieve the single utility of the output hypercube
			V[] assignments = (V[]) Array.newInstance(this.classOfV, myNbrVars);
			for (int i = 0; i < myNbrVars; i++) 
				assignments[i] = iterDoms[i][0];
			U utility = getUtility(this.variables, assignments);
			
			// Return a ScalarHypercube
			return scalarHypercube (utility);
		}
		
		return this.slice(remainingVars.toArray(new String [remainingVars.size()]), 
				remainingDoms.toArray((V[][]) Array.newInstance(domClass, remainingDoms.size())), iterDoms, nbrRemainingUtils, domClass);
	}
	
	/** Slice method used internally 
	 * @param remainingVars 		the remaining variables
	 * @param remainingDoms 		the domains of the remaining variables
	 * @param iterDoms 				the domain slices for this space's variables
	 * @param nbrRemainingUtils 	the number of remaining utilities
	 * @param domClass 				the class of V[]
	 * @return 						the corresponding sliced space
	 */
	@SuppressWarnings("unchecked")
	protected BasicHypercube<V, U> slice (String[] remainingVars, V[][] remainingDoms, V[][] iterDoms, long nbrRemainingUtils, Class<?> domClass) {
		
		// Compute the new array of utilities
		Iterator<V, U> iter = this.iterator(this.variables, iterDoms); /// @bug Don't count NCCCs
		assert nbrRemainingUtils < Integer.MAX_VALUE : "A BasicHypercube can only contain up to 2^31-1 solutions";
		U[] newUtils = (U[]) Array.newInstance(this.values.getClass().getComponentType(), (int) nbrRemainingUtils);
		for (int i = 0; i < nbrRemainingUtils; i++) 
			newUtils[i] = iter.nextUtility();
		
		BasicHypercube<V, U> out = this.newInstance(remainingVars, remainingDoms, newUtils, this.infeasibleUtil);
//		out.problem = this.problem; /// @bug Keep counting NCCCs if I do
		
		return out;
	}	
	
	/** Return a slice of this hypercube
	 *  this version of slice does not create a new hypercube but directly modifies the current utility array
	 * @param variables_names the variables to slice
	 * @param sub_domains     the domains of the slice
	 * @return the new hypercube
	 */
	@SuppressWarnings("unchecked")
	public BasicHypercube<V, U> applySlice( String[] variables_names, V[][] sub_domains ) {
		
		assert variables_names.length == sub_domains.length : "Must provide a domain for each provided variable";
		
		assert notEmptyDomains(sub_domains) : "Must provide a non-empty sub-domain for each provided variable";
		
		assert this.consistentOrder(variables_names) : 
			"The input list of variables must use an order that is consistent with the order used by the hypercube";
		
		assert contains(variables_names, sub_domains) : 
			"Cannot slice one of the hypercube's variables over a subdomain that is not a subset of its original domain";
		
		//number of variables in the hypercube
		int number_of_variables = variables.length;
		//number of provided variables
		int number_of_variables2 = variables_names.length;
		//this object is used to compute the number of utility values that the resulting hypercube will contain
		int number_of_values = 1;
		//this object is used to compute the size of the blocks which will be kept in the resulting hypercube
		int block_size = 1;
		
		// The class of V[]
		Class<?> domClass = sub_domains.getClass().getComponentType();
		
		//the array that will contain the variables of the resulting hypercube
		ArrayList<String> new_variables = new ArrayList<String> (number_of_variables);
		
		//the array that will contain the domains of the variables of the resulting hypercube
		ArrayList< V[] > new_domains = new ArrayList< V[] > (number_of_variables);
		
		V[][] new_domains_tmp = (V[][]) Array.newInstance(domClass, number_of_variables);
		
		int index = number_of_variables - 1;	// index of a variable in this hypercube
		int index2 = number_of_variables2 - 1;	// index of a variable in the input list
		
		boolean sameDomains = true;
		int lastSameDomain = 0;
		
		// Loop over all variables in the hypercube
		for(int i = number_of_variables - 1 ; i >= 0 ; i--){
			
			index = -1;
			// Find the next slicing variable that is contained in this hypercube
			for ( ; index2 >= 0 ; index2--) {
				String var = variables_names[index2];
				
				// Find the position index3 of this variable in this hypercube
				for (index = i; index >= 0 ; index--) {
					if (this.variables[index].equals(var)) 
						break;
				}
				
				if (index >= 0) // the variable has been found at index3
					break;
			}
			
			// None of the variables in this hypercube with indexes between i and index3-1 is in the input list of variables
			// We can keep the current domain
			for ( ; i > index && i >= 0 ; i--) {
				new_variables.add(0, variables[ i ]);
				V[] dom = domains[ i ];
				new_domains.add(0, dom);
				new_domains_tmp[i] = dom;
				number_of_values *= dom.length;
				if (sameDomains)
					block_size *= dom.length;
			}
			
			if (i >= 0) { // now we have i == index3 == index2 and the two corresponding variables are equal
			
				// Check if variable i should remain in the resulting hypercube
				V[] subdom = sub_domains[ index2 ];
				int length_tmp = subdom.length;
				if( length_tmp != 1 ) {
					new_variables.add(0, variables[ i ]);
					new_domains.add(0, subdom);
					number_of_values *= length_tmp;
				}
				new_domains_tmp[i] = subdom;
				if (sameDomains) {
					if (length_tmp == domains[i].length)
						block_size *= length_tmp;
					else {
						lastSameDomain = i + 1;
						sameDomains = false;
					}
				}
				index2--;
			}
		}
		
		// Check if the resulting hypercube will contain no variable
		if (new_variables.isEmpty()) {
			
			// Retrieve the single utility of the output hypercube
			V[] assignments = (V[]) Array.newInstance(domClass.getComponentType(), number_of_variables2);
			for (int i = 0; i < number_of_variables2; i++) {
				assignments[i] = sub_domains[i][0];
			}
			U utility = getUtility(variables_names, assignments);
			
			// Return a ScalarHypercube
			return scalarHypercube (utility);
		}
		
		// Check if the resulting hypercube is not modified
		if (sameDomains)
			return this;
		
		//number of blocks to copy in order to update the utility array
		int number_of_blocks = number_of_values / block_size;
		
		//this array will contain a possible combination of variables values
		V[] variables_values = (V[]) Array.newInstance(domClass.getComponentType(), number_of_variables);
		//index of the current values of the variables
		int[] indexes = new int[ lastSameDomain ];
		//index of the variable to be incremented
		int index_to_increment = lastSameDomain - 1;
		
		for(int i = lastSameDomain ; i < number_of_variables ; i++)
			variables_values[i] = new_domains_tmp[i][0];
		
		V[] domain;
		for( int i = 0 ; i < number_of_blocks ; i++ ) {
			for( int j = lastSameDomain - 1; j >= 0; j-- ) {
				index = indexes[ j ];
				
				domain = new_domains_tmp[ j ];
				
				//the current value of the jth variable
				variables_values[ j ] = domain[ index ];
				
				//increment or not this value index of this variable
				if( index_to_increment == j ) {
					index = ( index + 1 ) % domain.length;
					indexes[ j ] = index;
					
					 // when a whole loop is done increment also the next variable which is previous to this one in order
					if( index == 0 )  index_to_increment--; 
					else index_to_increment = lastSameDomain - 1;
				}
			}
			// copy the current block to its right position in the updated utility array
			System.arraycopy(values, getIndexOfUtilityValue(variables_values), values, i*block_size, block_size);
		}
		
		this.variables = new_variables.toArray(new String [new_variables.size()]);
		this.domains = new_domains.toArray((V[][]) Array.newInstance(domClass, new_domains.size()));
		this.setNumberOfSolutions(number_of_values);
		this.setStepsHashmaps();
		return this;
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) {
		
		if (o == this)
			return true;
		
		if (!(o instanceof BasicUtilitySolutionSpace))
			return false;
		
		BasicUtilitySolutionSpace<V, U> space = (BasicUtilitySolutionSpace<V, U>) o;
		
		// Check that the two spaces agree on the variables and their orders
		if (! Arrays.equals(this.variables, space.getVariables())) 
			return false;
		
		return this.equivalent(space);
	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode () {
		int hash = 0;
		
		for (V[] dom : this.domains) 
			for (V val : dom) 
				hash += val.hashCode();
		
		hash += name.hashCode();
		
		for (int i = 0; i < this.number_of_utility_values; i++) 
			hash += this.values[i].hashCode();
		
		for (String var : this.variables) 
			hash += var.hashCode();
		
		return hash;
	}
	
	/** 
	 * @return a shallow clone of this BasicHypercube
	 * @see SolutionSpace#clone() 
	 */
	@Override
	@SuppressWarnings("unchecked")
	public BasicHypercube<V, U> clone () {
		
		// Clone the domains
		V[][] domains2 = (V[][]) Array.newInstance(this.domains.getClass().getComponentType(), this.domains.length);
		for (int i = 0; i < domains2.length; i++) {
			domains2[i] = domains[i].clone();
		}
		
		return this.newInstance(variables.clone(), domains2, values.clone(), this.infeasibleUtil);
	}
	
	/** @see BasicUtilitySolutionSpace#resolve() */
	public BasicHypercube<V, U> resolve() {
		
		if (this.problem != null) {
			this.incrNCCCs(this.getNumberOfSolutions());
			return this.clone(); // no longer counting NCCCs
		}
		
		return this;
	}

	/** Creates a new instance of a BasicHypercube
	 * @param new_variables 	list of variables
	 * @param new_domains		list of domains
	 * @param new_values		array of utility values
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @return a new BasicHypercube
	 */
	protected BasicHypercube<V, U> newInstance(String[] new_variables, V[][] new_domains, U[] new_values, U infeasibleUtil) {
		return new BasicHypercube<V, U> ( new_variables, new_domains, new_values, infeasibleUtil );
	}

	/** Creates a new instance of a ScalarBasicHypercube
	 * @param utility 	the utility of the scalar hypercube
	 * @return a new instance of a ScalarBasicHypercube
	 */
	protected BasicHypercube<V, U> scalarHypercube(U utility) {
		return new ScalarBasicHypercube<V, U> (utility, this.infeasibleUtil);
	}

	/** @see BasicUtilitySolutionSpace#slice(java.lang.String[], V[]) */
	@SuppressWarnings("unchecked")
	public BasicUtilitySolutionSpace<V, U> slice(String[] variables_names, V[] values) {
		Class<?> domClass = values.getClass(); // class of V[]
		V[][] doms = (V[][]) Array.newInstance(domClass, variables_names.length);
		for (int i = 0; i < variables_names.length; i++) {
			V[] dom = (V[]) Array.newInstance(this.classOfV, 1);
			dom[0] = values[i];
			doms[i] = dom;
		}
		return this.slice(variables_names, doms);
	}

	/** @see BasicUtilitySolutionSpace#slice(java.lang.String, V[]) */
	@SuppressWarnings("unchecked")
	public BasicUtilitySolutionSpace<V, U> slice(String var, V[] subDomain) {
		String[] vars = { var };
		V[][] doms = (V[][]) Array.newInstance(subDomain.getClass(), 1);
		doms[0] = subDomain.clone();
		return slice(vars, doms);
	}
	
	/** Slices this hypercube over a single variable-value assignment
	 * @param var the variable to be assigned a value
	 * @param val the value to assign to the variable 
	 * @return the hypercube resulting from this slice
	 */
	@SuppressWarnings("unchecked")
	public BasicUtilitySolutionSpace<V, U> slice ( String var, V val ) {
		String[] vars = { var };
		V[] dom = (V[]) Array.newInstance(val.getClass(), 1);
		dom[0] = val;
		V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
		doms[0] = dom;
		return slice(vars, doms);
	}
	

	
	/**Return a slice of this hypercube
	 * @param variables_values array containing values of the last variables of the hypercube
	 * @return Hypercube object obtained by associating fixed values to the last variables of the hypercube. the number of
	 * this variables depends on the length of the provided array of variables values
	 */
	@SuppressWarnings("unchecked")
	public BasicUtilitySolutionSpace< V, U > slice( V[] variables_values ) {
		
		assert variables_values.length <= variables.length : 
			"number of provided variables is greator than the number of variables in the Hypercube";
		
		//number of variables in this hypercube
		int number_of_variables = variables.length;
		 
		//number of variables of the new hypercube
		int number_of_variables2 = number_of_variables - variables_values.length;
		 
		//variables of the new hypercube
		String[] new_variables = new String[number_of_variables2];
		System.arraycopy(variables, 0, new_variables, 0, number_of_variables2);
		//domains of the variables of the new hypercube
		V[][] new_domains = (V[][]) Array.newInstance(variables_values.getClass(), number_of_variables2);
		System.arraycopy(domains, 0, new_domains, 0, number_of_variables2);
		 
		//compute the number of utility values in the new hypercube
		int number_of_values = number_of_utility_values;
		//the fixed step resulting from the last variables taking fixed values
		int fixed_step = 0;
		int domain_size;
		int index = 0;
		for ( int i = number_of_variables2; i < number_of_variables; i++, index++ ) {
			//size of the domain of the current variable
			domain_size = domains[ i ].length;
			//reduction of the number of utility values due to the current variable taking a fixed value
			number_of_values = number_of_values / domain_size;
			
			fixed_step += steps_hashmaps[ i ].get(variables_values[ index ]);
		}
		
		//create the array of utility values of the new hypercube
		U[] new_values = (U[]) Array.newInstance(this.values.getClass().getComponentType(), number_of_values);
		int[] indexes = new int[ number_of_variables2 ];
		V[] domain;
		int index_to_increment = number_of_variables2 - 1;
		
		for( int i = 0; i < number_of_values; i++ ) {
			index = fixed_step;
			//compute the index of the next utility value
			for( int j = 0; j < number_of_variables2; j++ )
				index += steps_hashmaps[ j ].get( domains[ j ][ indexes[ j ]] );
			
			// add the utility value to the array of utility values of the new Hypercube
			new_values[ i ] = values[ index ];
			
			//the next array of indexes pointing to variables values corresponding to the next utility value of the new Hypercube
			for( int j = number_of_variables2 - 1; j >= 0; j-- ) {
				domain = domains[ j ];
				if(j == index_to_increment){
					indexes[ j ] = ( indexes[ j ] + 1 ) % domain.length;
					// when a whole loop is done increment also the next variable which is previous to this one in order
					if( indexes[ j ] == 0 )  index_to_increment--;
						
					else index_to_increment = number_of_variables2 - 1;
				}
			}
		}
		return newInstance (new_variables, new_domains, new_values, this.infeasibleUtil);
	}

	/** The composition operation
	 * @see BasicUtilitySolutionSpace#compose(java.lang.String[], BasicUtilitySolutionSpace) 
	 * @author Thomas Leaute
	 */
	@SuppressWarnings("unchecked")
	public BasicUtilitySolutionSpace<V, U> compose(final String[] varsOut, final BasicUtilitySolutionSpace< V, ArrayList<V> > subst) {
		
		// If subst is a NullHypercube, return a clone
		if (subst == Hypercube.NullHypercube.NULL) 
			return this.clone();
		
		// Return a clone of myself if no variable is being substituted 
		if (Collections.disjoint(Arrays.asList(this.getVariables()), Arrays.asList(varsOut))) 
			return this.clone();
		
		final int nbrVarsInSubst = subst.getNumberOfVariables();
		assert sub(subst.getVariables(), varsOut).length == nbrVarsInSubst : 
			"The substitution space is expressed over variables that are being substituted";
		
		// Variable order for the output: 1) varsInSubst; 2) remaining variables in this space but not in varsOut
		ArrayList<String> order = new ArrayList<String> (Arrays.asList(subst.getVariables()));
		HashSet<String> varsOutSet = new HashSet<String> (Arrays.asList(varsOut));
		for (String var : this.getVariables()) 
			if (! order.contains(var) && ! varsOutSet.contains(var)) 
				order.add(var);
		String[] varsKept = order.toArray(new String [order.size()]);
		final int nbrVarsKept = varsKept.length;
		final int nbrVarsOut = varsOut.length;
		
		// If 2) is empty
		if (nbrVarsKept == nbrVarsInSubst) {
			
			// If the input substitution is a ScalarBasicHypercube
			if (nbrVarsInSubst == 0) 
				return this.scalarHypercube(this.getUtility(varsOut, subst.getUtility(0).toArray((V[]) Array.newInstance(this.classOfV, nbrVarsOut))));
			
			// Variable order for this.iterator(): 1) varsInSubst; 2) varsOut
			order = new ArrayList<String> (Arrays.asList(subst.getVariables()));
			order.addAll(Arrays.asList(varsOut));
			String[] vars = order.toArray(new String [order.size()]);
			V[] vals = (V[]) Array.newInstance(this.classOfV, order.size());
			V[][] domsKept = (V[][]) Array.newInstance(this.getDomain(0).getClass(), nbrVarsKept);
			int nbrUtilsKept = 1;
			for (int i = nbrVarsInSubst - 1; i >= 0; i--) { // varsInSubst
				domsKept[i] = subst.getDomain(vars[i]); /// @bug I might disagree with this domain; compute the intersection
				nbrUtilsKept *= domsKept[i].length;
			}
			
			// Iterate over the substitution
			U[] utilsKept = (U[]) Array.newInstance(getClassOfU(), nbrUtilsKept);
			int i = 0;
			for (BasicUtilitySolutionSpace.Iterator< V, ArrayList<V> > iter = subst.iterator(varsKept, domsKept, vals); iter.hasNext(); ) {
				
				// Check the values for varsOut
				ArrayList<V> varsOutVals = iter.nextUtility();
				assert varsOutVals != null;
				for (int j = vars.length - 1; j >= nbrVarsInSubst; j--) 
					vals[j] = varsOutVals.get(j - nbrVarsInSubst);
				
				// Iterate over myself
				utilsKept[i++] = this.getUtility(vars, vals);
			}
			
			return this.newInstance(varsKept, domsKept, utilsKept, this.infeasibleUtil);
		}
		
		// Variable order for this.iterator(): 1) varsOut; 2) varsInSubst; 3) remaining variables in this space but not in varsOut
		order.addAll(0, Arrays.asList(varsOut));
		String[] vars = order.toArray(new String [order.size()]);
		V[][] doms = (V[][]) Array.newInstance(this.getDomain(0).getClass(), order.size());
		V[][] domsKept = (V[][]) Array.newInstance(this.getDomain(0).getClass(), nbrVarsKept);
		int nbrUtilsKept = 1;
		int nbrRemainingUtils = 1;
		for (int i = nbrVarsOut - 1; i >= 0; i--) // varsOut
			doms[i] = (V[]) Array.newInstance(classOfV, 1);
		for (int i = 0; i < nbrVarsInSubst; i++) { // varsInSubst
			doms[i + nbrVarsOut] = (V[]) Array.newInstance(classOfV, 1);
			domsKept[i] = subst.getDomain(vars[i + nbrVarsOut]); /// @bug I might disagree with this domain; compute the intersection
			nbrUtilsKept *= domsKept[i].length;
		}
		for (int i = nbrVarsInSubst; i < nbrVarsKept; i++) { // my other remaining vars
			domsKept[i] = doms[i + nbrVarsOut] = this.getDomain(varsKept[i]);
			nbrUtilsKept *= domsKept[i].length;
			nbrRemainingUtils *= domsKept[i].length;
		}
		
		// Iterate over the substitution
		U[] utilsKept = (U[]) Array.newInstance(getClassOfU(), nbrUtilsKept);
		V[] assignment = (V[]) Array.newInstance(classOfV, vars.length);
		int i = 0;
		substLoop: for (BasicUtilitySolutionSpace.Iterator< V, ArrayList<V> > iter = subst.iterator(); iter.hasNext(); ) { /// @bug iterate over the intersected domains
			
			// Check the values for varsInSubst
			V[] varsInSubstVals = iter.nextSolution();
			for (int j = nbrVarsInSubst - 1; j >= 0; j--) 
				doms[j + nbrVarsOut][0] = varsInSubstVals[j];
			
			// Check the values for varsOut
			ArrayList<V> varsOutVals = iter.getCurrentUtility();
			assert varsOutVals != null;
			for (int j = nbrVarsOut - 1; j >= 0; j--) {
				V val = varsOutVals.get(j);
				
				// Check if this value is not in my domain
				V[] myDom = this.getDomain(vars[j]);
				if (myDom != null) {
					if (! Arrays.asList(myDom).contains(val)) { // not in my domain
						for (int k = nbrRemainingUtils - 1; k >= 0; k--) 
							utilsKept[i++] = this.infeasibleUtil;
						continue substLoop;
					}
				}
				
				doms[j][0] = val;
			}
			
			// Iterate over myself
			for (BasicUtilitySolutionSpace.Iterator<V, U> myIter = this.iterator(vars, doms, assignment); myIter.hasNext(); ) 
				utilsKept[i++] = myIter.nextUtility();
		}
		
		return this.newInstance(varsKept, domsKept, utilsKept, this.infeasibleUtil);
	}
	
	/**Checks if this hypercube contains the provided variables
	 * @param variables_names array of variables
	 * @return true, if this hypercube contains the provided variables. false, if else
	 * @note This method is only called from within an assert, therefore it is OK if it is not very efficient. 
	 */
	protected boolean contains( String[] variables_names) {
		
		List<String> myVars = Arrays.asList(this.variables);
		for (String var : variables_names) 
			if (! myVars.contains(var)) 
				return false;
		
		return true;
	}
	
	/** If any of the input variables is in this hypercube, check that the corresponding input domain is a subset of this variable's domain. 
	 * @param variables_names    array of variables
	 * @param variables_domains  array of variables domains
	 * @return \c true iff any input variable that is contained in the hypercube has a slicing domain that is a subset of its original domain
	 * @note This method is currently only called from within an assert statement. 
	 */
	protected boolean contains( String[] variables_names, V[][] variables_domains ) {
		
		List<String> myVars = Arrays.asList(this.variables);
		
		// Go through the list of input variables that are contained in this hypercube
		for (int i = 0; i < variables_names.length; i++) {
			String var = variables_names[i];
			if (myVars.contains(var)) {
				
				// Check that all values in the slicing domain are in the original domain
				List<V> dom = Arrays.asList(this.getDomain(var));
				for (V val : variables_domains[i]) 
					if (! dom.contains(val)) 
						return false;
			}
		}
		
		return true;
	}
	
	/**Checks that there is no empty domain in an array of domains
	 * @param domains array containing domains
	 * @return true if provided array of domains contains no empty domain
	 */
	protected boolean notEmptyDomains( V[][] domains ) {
		for ( V[] domain : domains)
			if( domain.length == 0 )
				return false;
		return true;
	}
	
	/** Checks that the variables order is consistent in all hypercubes
	 * @param hypercubes space whose variables order is checked
	 * @return true is variables order in all hypercubes is consistent
	 */
	protected boolean consistentOrder(BasicHypercube<V, U> [] hypercubes) {
		boolean consistent = true;
		int number_of_hypercubes = hypercubes.length;
		BasicHypercube<V, U> hypercube;
		
		for(int i = 0 ; i < number_of_hypercubes ; i++) {
			consistent = consistent && this.consistentOrder(hypercubes[i].variables);
			if (!consistent)
				break; /// @todo Why do a break when you could just directly return false? (applies everywhere)
		}
		
		for(int i = 0 ; i < number_of_hypercubes - 1 ; i++) {
			if (!consistent)
				break;
			hypercube = hypercubes[i];
			for(int j = i + 1 ; j < number_of_hypercubes ; j++) {
				consistent = consistent && hypercube.consistentOrder(hypercubes[j].variables);
				if (!consistent)
					break;
			}
		}
		
		return consistent;
	}
	
	/** Checks that input variable order is consistent with the order in this hypercube
	 * @param variables1 	variable order
	 * @return true if variables order in both hypercubes is consistent
	 */
	protected boolean consistentOrder(String[] variables1) {
		boolean consistent = true;
		int number_of_variables = variables.length;
		int number_of_variables1 = variables1.length;
		int index = 0;
		String variable;
		
		for(int i = 0 ; i < number_of_variables ; i++) {
			if (!consistent)
				break;
			variable = variables[i];
			for(int j = 0 ; j < number_of_variables1 ; j++){
				if (variable.equals(variables1[j])){
					if (j < index){
						consistent = false;
						break;
					}
					else
						index = j;
				}				
			}
		}
		return consistent;
	}
	
	/** checks that the domains of the hypercube are ordered
	 * @return true if the values of the variables in the domains of the hypercube are ordered
	 */
	public boolean orderedDomains() {
		int number_of_variables = variables.length;
		int domain_size;
		V[] domain;
		V value;
		for (int i = 0 ; i < number_of_variables ; i++){
			domain = domains[i];
			domain_size = domain.length;
			value = domain[0];
			for (int j = 1 ; j < domain_size ; j++) {
				if (value.compareTo(domain[j]) > 0)
					return false;
				else
					value = domain[j];
			}
		}
		return true;	
	}

	/** @see BasicUtilitySolutionSpace#getDefaultUtility() */
	public U getDefaultUtility() {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#setDefaultUtility(java.io.Serializable) */
	public void setDefaultUtility(U utility) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}

	/** @see BasicUtilitySolutionSpace#setInfeasibleUtility(java.io.Serializable) */
	public void setInfeasibleUtility(U utility) {
		this.infeasibleUtil = utility;
	}

	/** @see SolutionSpace#knows(Class) */
	public boolean knows(Class<?> spaceClass) {
		return knownSpaces.contains(spaceClass);
	}

	/** @see SolutionSpace#getName() */
	public String getName() {
		return this.name;
	}

	/** @see SolutionSpace#setName(String) */
	public void setName(String name) {
		this.name = name;
	}

	/** @see SolutionSpace#getRelationName() */
	public String getRelationName() {
		return this.relationName;
	}

	/** @see SolutionSpace#setRelationName(String) */
	public void setRelationName(String name) {
		this.relationName = name;
	}

	/** @see SolutionSpace#getOwner() */
	public String getOwner() {
		return this.owner;
	}
	
	/** Sets the owner
	 * @param owner 	the owner
	 */
	public void setOwner (String owner) {
		this.owner = owner;
	}

}
