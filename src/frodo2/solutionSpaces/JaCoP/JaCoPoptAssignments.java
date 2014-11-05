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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import JaCoP.core.IntVar;
import JaCoP.core.Store;
import JaCoP.search.DepthFirstSearch;
import JaCoP.search.IndomainMin;
import JaCoP.search.InputOrderSelect;
import JaCoP.search.Search;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarSpaceIter;

/** Conditional optimal assignment(s) to one or more variable(s)
 * @author Thomas Leaute
 */
public class JaCoPoptAssignments implements BasicUtilitySolutionSpace< AddableInteger, ArrayList<AddableInteger> > {

	/** Used for serialization */
	private static final long serialVersionUID = 2713289252460722989L; 
	
	/** The space from which variables were projected out */
	private final JaCoPutilSpace<?> space;
	
	/** The names of the variables in the separator */
	private final String[] varNames;
	
	/** The names of the projected variables for which we want to search the values given an assignment */
	private final String[] projectedVarNames;
	
	/** The JaCoP store*/
	private Store store;
	
	/** The consistency of the JaCoP Store */
	private boolean isConsistent;
	
	/** The variable names, including the projected out and sliced out variables, but excluding the utility variable */
	HashMap<String, AddableInteger[]> allVars;

	/** Constructor 
	 * @param space 	The space from which variables were projected out
	 * @param varNames 	The names of the variables in the separator
	 * @param varsOut 	The variables that were projected
	 */
	JaCoPoptAssignments (JaCoPutilSpace<?> space, String[] varNames, String[] varsOut) {
		this.space = space;
		this.varNames = varNames;
		this.projectedVarNames = varsOut;
		this.allVars = space.allVars;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "JaCoPoptAssignments" +
				"\n\t vars: " + Arrays.toString(this.varNames) +
				"\n\t proj: " + Arrays.toString(this.projectedVarNames) +
				"\n\t space: " + this.space;
	}

	/** @see BasicUtilitySolutionSpace#augment(Addable[], java.io.Serializable) */
	public void augment(AddableInteger[] variablesValues,
			ArrayList<AddableInteger> utilityValue) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see BasicUtilitySolutionSpace#changeVariablesOrder(java.lang.String[]) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> changeVariablesOrder(
			String[] variablesOrder) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Object#clone() */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> clone() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#compose(java.lang.String[], BasicUtilitySolutionSpace) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> compose(
			String[] vars,
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> substitution) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#equivalent(BasicUtilitySolutionSpace) */
	public boolean equivalent(
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#getClassOfU() */
	public Class<ArrayList<AddableInteger>> getClassOfU() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#getDefaultUtility() */
	public ArrayList<AddableInteger> getDefaultUtility() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#getUtility(Addable[]) */
	public ArrayList<AddableInteger> getUtility(AddableInteger[] variablesValues) {
		// The input does not specify a value for each variable
		if(varNames.length != 0 && variablesValues.length < varNames.length){ ///@todo the condition varNames.length != 0 is necessary as we do not use explicit ScalarHypercube
			return null;
		}
		
		ArrayList<AddableInteger> out = new ArrayList<AddableInteger>();

		// If the store does not exist yet, we create it
		if(this.store == null){
			this.store = space.createStore();
			isConsistent = store.consistency();
		}
		
		// The problem is infeasible, we just choose any assignment to each projected variable
		if(!isConsistent){

			AddableInteger[] dom;
			for(int i = 0; i < projectedVarNames.length; i++){
				// We take the first value of the domain
				dom = space.getDomain(projectedVarNames[i]);
				if(dom != null){
					out.add(this.space.getDomain(projectedVarNames[i])[0]);
				}else{
					out.add(this.space.getProjVarDomain(projectedVarNames[i])[0]);
				}
			}
			
			return out;
			
		}

		int lvlReminder = store.level;
		
		// Change the store level to be able to ground variables in an reversible manner
		store.setLevel(lvlReminder+1);
		
		for(int i = 0; i < varNames.length; i++){
			// Find the variable in the store
			IntVar var = (IntVar)store.findVariable(varNames[i]);
			assert var != null: "Variable " + varNames[i] + " not found in the store!";
			
			// We ground the variables in the separator
			try{
				var.domain.in(store.level, var, variablesValues[i].intValue(), variablesValues[i].intValue());
			}catch (JaCoP.core.FailException e){
				
				for(int k = store.level; k > lvlReminder; k--){
					store.removeLevel(k);
				}
				
				store.setLevel(lvlReminder);
			
				// The problem is infeasible, we just choose any assignment to each projected variable
				
				AddableInteger[] dom;
				for(int j = 0; j < projectedVarNames.length; j++){
					// We take the first value of the domain
					dom = space.getDomain(projectedVarNames[j]);
					if(dom != null){
						out.add(this.space.getDomain(projectedVarNames[j])[0]);
					}else{
						out.add(this.space.getProjVarDomain(projectedVarNames[j])[0]);
					}
				}
				
				return out;	
			}
		}
		
		IntVar[] searchedVars = new IntVar[projectedVarNames.length + space.getProjectedVars().length];
		int n = 0;
		for(String var : projectedVarNames){
			// Find the JaCoP variable
			searchedVars[n] = (IntVar) store.findVariable(var);
			assert searchedVars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}
		
		for(String var : space.getProjectedVars()){
			// Find the JaCoP variable
			searchedVars[n] = (IntVar) store.findVariable(var);
			assert searchedVars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}

		IntVar utilVar = (IntVar) store.findVariable("util_total");
		assert utilVar != null: "Variable " + "util_total" + " not found in the store!";

				
		// Optimization search
		Search<IntVar> search = new DepthFirstSearch<IntVar> ();
		search.getSolutionListener().recordSolutions(true);
			
		// Debug information
		search.setPrintInfo(false);
			
		boolean result = search.labeling(store, new InputOrderSelect<IntVar> (store, searchedVars, new IndomainMin<IntVar>()), utilVar);
			
		if(!result){
			// The problem is infeasible, we can choose any assignment to each projected variable
			
			AddableInteger[] dom;
			for(int i = 0; i < projectedVarNames.length; i++){
				// We take the first value of the domain
				dom = space.getDomain(projectedVarNames[i]);
				if(dom != null){
					out.add(this.space.getDomain(projectedVarNames[i])[0]);
				}else{
					out.add(this.space.getProjVarDomain(projectedVarNames[i])[0]);
				}
			}
			
			return out;
			
		}
			
		//search.getSolution();
		
		for (int j=0; j < (projectedVarNames.length); j++){
			assert search.getSolution()[j].singleton(): "In a solution, all the variables must be grounded";
			out.add(new AddableInteger(search.getSolution()[j].valueEnumeration().nextElement()));
		}
		
		
		// Store backtrack
		for(int k = store.level; k > lvlReminder; k--){
			store.removeLevel(k);
		}
		
		store.setLevel(lvlReminder);

		return out;
		
	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.lang.String[], Addable[]) */
	public ArrayList<AddableInteger> getUtility(String[] variablesNames,
			AddableInteger[] variablesValues) {
		
		AddableInteger[] assignment = null;
		
		if(varNames.length == 0){
			return getUtility(assignment);
		}
		
		assert variablesNames.length >= this.varNames.length;
		assert variablesNames.length == variablesValues.length;
		
		//Note: "variables_names" and "variables_values" may contain variables that are not present in this hypercube but must 
		//provide a value for each variable of this space otherwise a null is returned.

		assignment = new AddableInteger[varNames.length];
		final int variables_size = variablesNames.length;
		final int variables_size2 = varNames.length;

		// loop over all the variables present in the array "variablesNames"
		String var;
		ext: for(int i = 0; i < variables_size2; i++){
			var = this.varNames[i];
			for(int j = 0; j < variables_size; j++){
				if( var.equals(variablesNames[j])) {
					assignment[i] = variablesValues[j];
					continue ext;
				}
			}

			// No value found for variable i
			return null;
		}

		return getUtility(assignment);
		
	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.util.Map) */
	public ArrayList<AddableInteger> getUtility(Map<String, AddableInteger> assignments) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}
	
	/** @see BasicUtilitySolutionSpace#getUtility(long) */
	public ArrayList<AddableInteger> getUtility(long index) {

		// obtain the correct values array that corresponds to the index
		AddableInteger[] values = new AddableInteger[varNames.length];
		AddableInteger[] domain;
		long location = this.getNumberOfSolutions();
		int indice;
		for(int i = 0; i < varNames.length; i++){

			domain = allVars.get(varNames[i]);
			location = location/domain.length;

			indice = (int) (index/location);
			index = index % location;

			values[i] = domain[indice];
		}
		return getUtility(values);
	}

	/** @see BasicUtilitySolutionSpace#isIncludedIn(BasicUtilitySolutionSpace) */
	public boolean isIncludedIn(
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#iterator() */
	public BasicUtilitySolutionSpace.Iterator<AddableInteger, ArrayList<AddableInteger>> iterator() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], Addable[][]) */
	public BasicUtilitySolutionSpace.Iterator<AddableInteger, ArrayList<AddableInteger>> iterator(
			String[] variables, AddableInteger[][] domains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], Addable[][], Addable[]) */
	public Iterator<AddableInteger, ArrayList<AddableInteger>> iterator(
			String[] variables, AddableInteger[][] domains,
			AddableInteger[] assignment) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#sparseIter() */
	@Override
	public SparseIterator<AddableInteger, ArrayList<AddableInteger>> sparseIter() {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[]) */
	@Override
	public SparseIterator<AddableInteger, ArrayList<AddableInteger>> sparseIter(
			String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][]) */
	@Override
	public SparseIterator<AddableInteger, ArrayList<AddableInteger>> sparseIter(
			String[] variables, AddableInteger[][] domains) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][], Addable[]) */
	@Override
	public SparseIterator<AddableInteger, ArrayList<AddableInteger>> sparseIter(
			String[] variables, AddableInteger[][] domains,
			AddableInteger[] assignment) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}
		/** @see BasicUtilitySolutionSpace#prettyPrint(java.io.Serializable) */
	public String prettyPrint(ArrayList<AddableInteger> ignoredUtil) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#resolve() */
	public BasicUtilitySolutionSpace< AddableInteger, ArrayList<AddableInteger> > resolve() {
		
		if (this.varNames.length == 0) 
			return new ScalarBasicHypercube< AddableInteger, ArrayList<AddableInteger> > (this.getUtility(0), null);
		
		// Compute the utilities for all combinations of assignments to the variables
		assert this.getNumberOfSolutions() < Integer.MAX_VALUE : "Cannot resolve a space that contains more than 2^32 solutions";
		@SuppressWarnings("unchecked")
		ArrayList<AddableInteger>[] utilities = new ArrayList [(int) this.getNumberOfSolutions()];
		int i = 0;
		for (ScalarSpaceIter<AddableInteger, AddableInteger> iter = 
				new ScalarSpaceIter<AddableInteger, AddableInteger> (null, this.getVariables(), this.getDomains(), null, null); iter.hasNext(); i++) 
			utilities[i] = this.getUtility(iter.nextSolution());
		
		return new BasicHypercube< AddableInteger, ArrayList<AddableInteger> > (this.getVariables(), this.getDomains(), utilities, null);
	}

	/** @see BasicUtilitySolutionSpace#setDefaultUtility(java.io.Serializable) */
	public void setDefaultUtility(ArrayList<AddableInteger> utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see BasicUtilitySolutionSpace#setInfeasibleUtility(java.io.Serializable) */
	public void setInfeasibleUtility(ArrayList<AddableInteger> utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	}
	
	/** @see BasicUtilitySolutionSpace#setUtility(Addable[], java.io.Serializable) */
	public boolean setUtility(AddableInteger[] variablesValues,
			ArrayList<AddableInteger> utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#setUtility(long, java.io.Serializable) */
	public void setUtility(long index, ArrayList<AddableInteger> utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see BasicUtilitySolutionSpace#slice(java.lang.String[], Addable[][]) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> slice(
			String[] variablesNames, AddableInteger[][] subDomains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#slice(java.lang.String[], Addable[]) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> slice(
			String[] variablesNames, AddableInteger[] values) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#slice(java.lang.String, Addable[]) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> slice(
			String var, AddableInteger[] subDomain) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#slice(java.lang.String, Addable) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> slice(
			String var, AddableInteger val) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#slice(Addable[]) */
	public BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> slice(
			AddableInteger[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#augment(Addable[]) */
	public void augment(AddableInteger[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#getDomain(java.lang.String) */
	public AddableInteger[] getDomain(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#getDomain(int) */
	public AddableInteger[] getDomain(int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#getDomain(java.lang.String, int) */
	public AddableInteger[] getDomain(String variable, int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#getDomains() */
	public AddableInteger[][] getDomains() {
		
		AddableInteger[][] doms = new AddableInteger [this.varNames.length][];
		
		for (int i = this.varNames.length - 1; i >= 0; i--) 
			doms[i] = this.allVars.get(this.varNames[i]);
		
		return doms;
	}

	/** @see SolutionSpace#getIndex(java.lang.String) */
	public int getIndex(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see SolutionSpace#getName() */
	public String getName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#getNumberOfSolutions() */
	public long getNumberOfSolutions() {
		long nbrUtils = 1;

		for(String var: this.varNames){
			assert Math.log(nbrUtils) + Math.log(allVars.get(var).length) < Math.log(Long.MAX_VALUE) : "Long overflow: too many solutions in an explicit space";
			nbrUtils *= allVars.get(var).length;
		}
		return nbrUtils;
	}

	/** @see SolutionSpace#getNumberOfVariables() */
	public int getNumberOfVariables() {
		return this.varNames.length;
	}

	/** @see SolutionSpace#getVariable(int) */
	public String getVariable(int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#getVariables() */
	public String[] getVariables() {
		return this.varNames;
	}

	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[]) */
	public BasicUtilitySolutionSpace.Iterator< AddableInteger, ArrayList<AddableInteger> > iterator(
			String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#join(SolutionSpace, java.lang.String[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space, String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#join(SolutionSpace) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#join(SolutionSpace[], java.lang.String[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger>[] spaces, String[] totalVariablesOrder) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#join(SolutionSpace[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#knows(java.lang.Class) */
	public boolean knows(Class<?> spaceClass) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see SolutionSpace#renameVariable(java.lang.String, java.lang.String) */
	public void renameVariable(String oldName, String newName) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#setDomain(java.lang.String, Addable[]) */
	public void setDomain(String var, AddableInteger[] dom) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#setName(java.lang.String) */
	public void setName(String name) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#getRelationName() */
	public String getRelationName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#renameAllVars(java.lang.String[]) */
	public SolutionSpace<AddableInteger> renameAllVars(String[] newVarNames) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#setRelationName(java.lang.String) */
	public void setRelationName(String name) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getOwner() */
	public String getOwner() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#setOwner(java.lang.String) */
	public void setOwner(String owner) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

}
