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

import JaCoP.core.IntDomain;
import JaCoP.core.IntVar;
import JaCoP.core.Store;
import JaCoP.search.DepthFirstSearch;
import JaCoP.search.IndomainMin;
import JaCoP.search.InputOrderSelect;
import JaCoP.search.Search;
import JaCoP.search.SolutionListener;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.UtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst;

/** A solution iterator in best first order for JaCoPutilSpace
 * @author Arnaud Jutzeler, Thomas Leaute
 * @param <U> the type used for utility values
 */
public class JaCoPutilSpaceIterBestFirst < U extends Addable<U> > implements IteratorBestFirst<AddableInteger, U>{

	/** The JaCoP Store */
	private Store store;

	/** the order or the iteration */
	private boolean maximize;
	
	/** The infeasible utility */
	public U infeasibleUtil;

	/** The JaCoPutilSpace we are iterating over */
	protected JaCoPutilSpace<U> space;

	/** The solution listener for solutions with current utility **/
	protected SolutionListener<IntVar> solListener;

	/** The index of the current solution in the solutions listener **/
	protected int solListenerIndex;

	/** The number of solutions left to iterate over */
	protected long nbrSolLeft;

	/** The total number of solutions to iterate over */
	protected long nbrSols;

	/** Current variable assignments */
	protected AddableInteger[] solution;

	/** Current utility value */
	protected U utility;

	/** Constructor
	 * @param space		the JaCoPutilSpace to iterate over
	 * @param maximize 	\c true when values are to be ordered decreasingly, and \c false otherwise
	 */
	public JaCoPutilSpaceIterBestFirst(JaCoPutilSpace<U> space, boolean maximize){
		this.space = space;
		this.maximize = maximize;
		
		if(maximize)
			infeasibleUtil = space.getDefaultUtility().getMinInfinity();
		else
			infeasibleUtil = space.getDefaultUtility().getPlusInfinity();

		assert space.getVariables().length > 0: "The space contains no variables";

		this.nbrSols = 0;
		this.nbrSolLeft = this.nbrSols;

		boolean isConsistent = true;
		this.store = space.getStore();
		if(this.store == null) {
			this.store = space.createStore();
			isConsistent = store.consistency();
		}

		if(isConsistent){

			this.solListenerIndex = 1;

			this.utility = searchBestUtility(null);

			this.solution = new AddableInteger[space.getNumberOfVariables()];

			if(!utility.equals(this.space.infeasibleUtil)){
				this.nbrSols = getNumberOfFeasibleSolutions();
				this.nbrSolLeft = this.nbrSols;
				this.solListener = searchAllSolutions(utility);
			}
		}
	}

	/** Moves to the next solution */
	private void iter(){
		//Scalar space
		if(space.getNumberOfVariables() == 0){
			this.utility = space.getUtility(0);
			
		}else{

			// There are no more solutions in the current solution listener
			if(solListenerIndex > solListener.solutionsNo()){
				solListenerIndex = 1;
				// We search for the next best utility
				this.utility = searchBestUtility(utility);
				if(!utility.equals(this.space.infeasibleUtil)){
					this.solListener = searchAllSolutions(utility);
				}
			}
			
			assert solListener.solutionsNo() > 0: "There is no solution";
			for(int i = 0; i < space.getNumberOfVariables(); i++){
				assert solListener.getSolution(solListenerIndex)[i].singleton(): "The domain of the solution is not a singleton";
				this.solution[i] = new AddableInteger(solListener.getSolution(solListenerIndex)[i].valueEnumeration().nextElement());
			}
			solListenerIndex++;
		}
		nbrSolLeft--;
	}

	/**	Search the next best cost/utility after the one given in argument
	 * @param bound		the cost/utility bound
	 * @return the best utility below the given bound
	 * @warning the domain of the utility variable in the store is pruned in a persistent way.
	 */
	@SuppressWarnings("unchecked")
	private U searchBestUtility(U bound){
		IntVar[] allVars = new IntVar[space.getNumberOfVariables() + space.getProjectedVars().length];

		int n = 0;
		for(String var: space.getVariables()){
			allVars[n] = (IntVar) store.findVariable(var);
			assert allVars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}

		for(String var: space.getProjectedVars()){
			allVars[n] = (IntVar) store.findVariable(var);
			assert var != null: "Variable " + var + " not found in the store!";
			n++;
		}

		IntVar utilVar = (IntVar) store.findVariable("util_total"); /// @bug Name clash in case the user defined a variable with this name
		assert utilVar != null: "Variable " + "util_total" + " not found in the store!";

		if(bound != null){
			bound = bound.subtract(this.space.defaultUtil);
			int util = (bound.intValue());
			if(maximize){
				util *= -1;
			}

			//We adjust the domain of the utility variable to skip the solutions as good as or better than the bound
			/// @todo This only removes the value "util"; remove ALL worst values instead
			IntDomain newDom;
			newDom = utilVar.dom().subtract(util, util);

			try{
				utilVar.domain.in(store.level, utilVar, newDom); /// @todo Changes are made to the current store level, and are never backtracked
			}catch (JaCoP.core.FailException e){
				return this.space.infeasibleUtil;
			}
		}

		Search<IntVar> search = new DepthFirstSearch<IntVar> ();
		search.getSolutionListener().recordSolutions(false);
		search.getSolutionListener().searchAll(false);
		search.setAssignSolution(false);

		// Debug information
		search.setPrintInfo(false);
		boolean result = search.labeling(store, new InputOrderSelect<IntVar> (store, allVars, new IndomainMin<IntVar>()), utilVar);

		if(!result){
			return this.space.infeasibleUtil;
		}

		int cost = search.getCostValue();

		// If it is a maximization problem
		if(maximize == true){
			cost = cost * -1;
		}

		try {
			return (U) this.space.defaultUtil.getClass().getConstructor(int.class).newInstance(cost).add(this.space.defaultUtil);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null; // should never be reached
	}

	/**	Search the number of feasible solutions in this space
	 * @return the number of feasible solutions
	 */
	private int getNumberOfFeasibleSolutions(){
		IntVar[] allVars = new IntVar[space.getNumberOfVariables() + space.getProjectedVars().length];

		int n = 0;
		for(String var: space.getVariables()){
			allVars[n] = (IntVar) store.findVariable(var);
			assert allVars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}

		for(String var: space.getProjectedVars()){
			allVars[n] = (IntVar) store.findVariable(var);
			assert var != null: "Variable " + var + " not found in the store!";
			n++;
		}

		IntVar utilVar = (IntVar) store.findVariable("util_total"); /// @bug Name clash in case the user defined a variable with this name
		assert utilVar != null: "Variable " + "util_total" + " not found in the store!";

		Search<IntVar> search = new DepthFirstSearch<IntVar> ();
		search.getSolutionListener().recordSolutions(false);
		search.getSolutionListener().searchAll(true);
		search.setAssignSolution(false);

		// Debug information
		search.setPrintInfo(false);
		boolean result = search.labeling(store, new InputOrderSelect<IntVar> (store, allVars, new IndomainMin<IntVar>()));

		if(!result){
			return 0;
		}
		
		return search.getSolutionListener().solutionsNo();
	}


	/** Search for all solutions with the given utility
	 * @param utility	the utility
	 * @return	a JaCoP SolutionListener that contains all the solutions with the specified utility
	 */
	private SolutionListener<IntVar> searchAllSolutions(U utility){
		IntVar[] allVars = new IntVar[space.getNumberOfVariables() + space.getProjectedVars().length + 1];

		int n = 0;
		for(String var: space.getVariables()){
			allVars[n] = (IntVar) store.findVariable(var);
			assert allVars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}

		for(String var: space.getProjectedVars()){
			allVars[n] = (IntVar) store.findVariable(var);
			assert var != null: "Variable " + var + " not found in the store!";
			n++;
		}

		IntVar utilVar = (IntVar) store.findVariable("util_total");
		assert utilVar != null: "Variable " + "util_total" + " not found in the store!";

		allVars[space.getNumberOfVariables() + space.getProjectedVars().length] = utilVar;


		int lvlReminder = store.level;

		// Change the store level to be able to ground the variable in an reversible manner
		store.setLevel(lvlReminder+1);

		utility = utility.subtract(this.space.defaultUtil);
		int util = (utility.intValue());
		if(maximize){
			util *= -1;
		}

		// We ground the utility variable
		try{
			utilVar.domain.in(lvlReminder+1, utilVar,util, util);
		}catch (JaCoP.core.FailException e){
			
			for(int k = store.level; k > lvlReminder; k--){
				store.removeLevel(k);
			}

			store.setLevel(lvlReminder);

			return null;	
		}

		Search<IntVar> search = new DepthFirstSearch<IntVar> ();
		search.getSolutionListener().recordSolutions(true);
		search.getSolutionListener().searchAll(true);
		search.setAssignSolution(false);

		// Debug information
		search.setPrintInfo(false);
		
		/// @bug Projected variables are not being projected; the resulting solution list might contain two solutions that only differ on the projected variables, which should not happen
		assert this.space.getProjectedVars().length == 0 : "Iteration over a space with projected variables is currently unsupported";
		
		boolean result = search.labeling(store, new InputOrderSelect<IntVar> (store, allVars, new IndomainMin<IntVar>()));

		if(!result){
			// No solution
			return null;
		}

		for(int k = store.level; k > lvlReminder; k--){
			store.removeLevel(k);
		}

		store.setLevel(lvlReminder);
		return search.getSolutionListener();
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst#maximalCut() */
	public U maximalCut() {
		return space.infeasibleUtil.getZero();
	}

	/** @see Iterator#getCurrentUtility(java.lang.Object, boolean) */
	public U getCurrentUtility(U bound,
			boolean minimize) {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
	return null;
	}

	/** @see Iterator#nextUtility(java.lang.Object, boolean) */
	public U nextUtility(U bound, boolean minimize) {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
	return null;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentUtility() */
	public U getCurrentUtility() {
		return utility;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextUtility() */
	public U nextUtility() {
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}

		iter();
		return utility;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#setCurrentUtility(java.lang.Object) */
	public void setCurrentUtility(U util) {
		/// @todo Auto-generated method stub
		assert false: "not implemented";

	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentSolution() 
	 * @warning Returns a pointer to an internal data structure that will be modified by subsequent calls to next(). 
	 */
	public AddableInteger[] getCurrentSolution() {
		return solution;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getDomains() */
	public AddableInteger[][] getDomains() {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
	return null;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getNbrSolutions() */
	public long getNbrSolutions() {
		return nbrSols;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getVariablesOrder() */
	public String[] getVariablesOrder() {
		return space.getVariables();
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#hasNext() */
	public boolean hasNext() {
		return (this.nbrSolLeft > 0);
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextSolution() 
	 * @warning Returns a pointer to an internal data structure that will be modified by subsequent calls to next(). 
	 */
	public AddableInteger[] nextSolution() {
		// Return null if there are no more solutions
		if (this.nbrSolLeft <= 0) {
			this.utility = null;
			this.solution = null;
			return null;
		}
		iter();
		return solution;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#update() */
	public void update() {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
	}
}
