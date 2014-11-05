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
import java.util.ArrayList;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpaceLimited;

/** A HypercubeLimited that contains no variable and a single utility
 * @author Thomas Leaute
 * @param <V> 	the type used for variable values
 * @param <U> 	the type used for Addable utilities
 * @param <UL> 	the type used for AddableLimited utilities
 */
public class ScalarHypercubeLimited < V extends Addable<V>, U extends Addable<U>, UL extends AddableLimited<U, UL> > 
	extends HypercubeLimited<V, U, UL> {

	/** Constructor 
	 * @param utility 			the single utility value of this hypercube
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @param classOfDom 		the class of V[]
	 */
	@SuppressWarnings("unchecked")
	public ScalarHypercubeLimited (UL utility, UL infeasibleUtil, Class<? extends V[]> classOfDom) {
		variables = new String[0];
		domains = (V[][]) Array.newInstance(classOfDom, 0);
		values = (UL[]) Array.newInstance(utility.getClass(), 1);
		this.number_of_utility_values = 1;
		values[0] = utility;
		steps_hashmaps = null;
		this.infeasibleUtil = infeasibleUtil;
	}

	/** @see HypercubeLimited#blindProject(String, boolean) */
	@Override
	public ScalarHypercubeLimited<V, U, UL> blindProject(String varOut, boolean maximize) {
		return this.clone();
	}

	/** @see HypercubeLimited#blindProject(String[], boolean) */
	@Override
	public ScalarHypercubeLimited<V, U, UL> blindProject(String[] varsOut, boolean maximize) {
		return this.clone();
	}

	/** @see HypercubeLimited#blindProjectAll(boolean) */
	@Override
	public UL blindProjectAll(boolean maximize) {
		return this.getUtility(0);
	}
	
	/** @see HypercubeLimited#clone() */
	@SuppressWarnings("unchecked")
	@Override
	public ScalarHypercubeLimited<V, U, UL> clone () {
		
		this.incrNCCCs(1);
		return new ScalarHypercubeLimited<V, U, UL> (values[0], this.infeasibleUtil, (Class<V[]>) this.domains.getClass().getComponentType());
	}

	/** @see HypercubeLimited#join(UtilitySolutionSpace) */
	@Override
	public UtilitySolutionSpaceLimited<V, U, UL> join(
			UtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#max(String) */
	@Override
	public UtilitySolutionSpaceLimited<V, U, UL> max(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#min(String) */
	@Override
	public UtilitySolutionSpaceLimited<V, U, UL> min(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#slice(String, Addable) */
	@Override
	public ScalarHypercubeLimited<V, U, UL> slice(String var, V val) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#augment(V[], java.io.Serializable) */
	@Override
	public void augment(V[] variablesValues, UL utilityValue) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#changeVariablesOrder(String[]) */
	@Override
	public ScalarHypercubeLimited<V, U, UL> changeVariablesOrder(
			String[] variablesOrder) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#compose(String[], BasicUtilitySolutionSpace) */
	@Override
	public BasicHypercube<V, UL> compose(String[] vars,
			BasicUtilitySolutionSpace<V, ArrayList<V>> substitution) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#equivalent(BasicUtilitySolutionSpace) */
	@Override
	public boolean equivalent(BasicUtilitySolutionSpace<V, UL> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see HypercubeLimited#getClassOfU() */
	@Override
	public Class<UL> getClassOfU() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getDefaultUtility() */
	@Override
	public UL getDefaultUtility() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getUtility(V[]) */
	@Override
	public UL getUtility(V[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getUtility(String[], V[]) */
	@Override
	public UL getUtility(String[] variablesNames, V[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getUtility(long) */
	@Override
	public UL getUtility(long index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#isIncludedIn(BasicUtilitySolutionSpace) */
	@Override
	public boolean isIncludedIn(BasicUtilitySolutionSpace<V, UL> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see HypercubeLimited#iterator() */
	@Override
	public BasicHypercube.Iterator<V, UL> iterator() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#iterator(String[], V[][]) */
	@Override
	public BasicHypercube.Iterator<V, UL> iterator(
			String[] variables, V[][] domains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#sparseIter() */
	@Override
	public BasicHypercube.SparseIterator<V, UL> sparseIter() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#sparseIter(String[], V[][]) */
	@Override
	public BasicHypercube.SparseIterator<V, UL> sparseIter(
			String[] variables, V[][] domains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#prettyPrint(java.io.Serializable) */
	@Override
	public String prettyPrint(UL ignoredUtil) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#resolve() */
	@Override
	public ScalarHypercubeLimited<V, U, UL> resolve() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#setDefaultUtility(java.io.Serializable) */
	@Override
	public void setDefaultUtility(UL utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#setUtility(V[], java.io.Serializable) */
	@Override
	public boolean setUtility(V[] variablesValues, UL utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see HypercubeLimited#setUtility(long, java.io.Serializable) */
	@Override
	public void setUtility(long index, UL utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#slice(String[], V[][]) */
	@Override
	public ScalarHypercubeLimited<V, U, UL> slice(String[] variablesNames,
			V[][] subDomains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#slice(String[], V[]) */
	@Override
	public BasicUtilitySolutionSpace<V, UL> slice(String[] variablesNames,
			V[] values) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#slice(String, V[]) */
	@Override
	public BasicUtilitySolutionSpace<V, UL> slice(String var, V[] subDomain) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#slice(V[]) */
	@Override
	public BasicUtilitySolutionSpace<V, UL> slice(V[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#augment(V[]) */
	@Override
	public void augment(V[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#getDomain(String) */
	@Override
	public V[] getDomain(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getDomain(int) */
	@Override
	public V[] getDomain(int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getDomain(String, int) */
	@Override
	public V[] getDomain(String variable, int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getDomains() */
	@Override
	public V[][] getDomains() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getIndex(String) */
	@Override
	public int getIndex(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see HypercubeLimited#getName() */
	@Override
	public String getName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getNumberOfSolutions() */
	@Override
	public long getNumberOfSolutions() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see HypercubeLimited#getNumberOfVariables() */
	@Override
	public int getNumberOfVariables() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see HypercubeLimited#getRelationName() */
	@Override
	public String getRelationName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getVariable(int) */
	@Override
	public String getVariable(int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#getVariables() */
	@Override
	public String[] getVariables() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#iterator(String[]) */
	@Override
	public Iterator<V, UL> iterator(String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#sparseIter(String[]) */
	@Override
	public SparseIterator<V, UL> sparseIter(String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#join(SolutionSpace, String[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space, String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#join(SolutionSpace) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#join(SolutionSpace[], String[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces,
			String[] totalVariablesOrder) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#join(SolutionSpace[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#knows(Class) */
	@Override
	public boolean knows(Class<?> spaceClass) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see HypercubeLimited#renameAllVars(String[]) */
	@Override
	public ScalarHypercubeLimited<V, U, UL> renameAllVars(String[] newVarNames) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see HypercubeLimited#renameVariable(String, String) */
	@Override
	public void renameVariable(String oldName, String newName) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#setDomain(String, V[]) */
	@Override
	public void setDomain(String var, V[] dom) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#setName(String) */
	@Override
	public void setName(String name) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see HypercubeLimited#setRelationName(String) */
	@Override
	public void setRelationName(String name) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

}
