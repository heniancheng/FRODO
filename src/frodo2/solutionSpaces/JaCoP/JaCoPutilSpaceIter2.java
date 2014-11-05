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
import JaCoP.core.IntervalDomain;
import JaCoP.core.Store;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/** A solution iterator for JaCoPutilSpace that runs a JaCoP search in an extra thread
 * 
 * This iterator uses less space as all the solutions are not temporarily stored but are
 * computed one by one by pausing the JaCoP search. 
 * Additionally, the variables whose projection has been requested are projected in an optimization search
 * that is performed on top of the first search in the same way as a master/slave search combination
 * 
 * @author Arnaud Jutzeler, Thomas Leaute
 * @param <U> the type used for utility values
 */
public class JaCoPutilSpaceIter2 < U extends Addable<U> > implements SparseIterator<AddableInteger, U>{

	/** The JaCoPutilSpace we are iterating over */
	protected JaCoPutilSpace<U> space;

	/** The last bound passed to the nextUtility() method */
	private U bound;

	/** If \c true, nextUtility(bound) returns only solutions whose cost is strictly lower than the bound */
	private boolean minimize;

	/** Current utility value */
	protected U utility;

	/** Current variable assignments */
	protected AddableInteger[] solution;

	/** The order of iteration over the variables */
	protected String[] variables;

	/** The number of variables */
	protected int nbrVars;

	/** The variables' domains */
	protected AddableInteger[][] domains;

	/** The JaCoP Store */
	private Store store;

	/** The JaCoPSearch object that wraps the JaCoP search*/
	private JaCoPiterSearch search;

	/** The thread that carries out the JaCoP search */
	private Thread searchThread;
	
	/** Whether the search has already finished going through all solutions */
	private boolean searchTerminated = false;

	/** If \c true, the JaCoP search has already been initiated */
	private boolean searchInitiated;

	/** The lock used to ensure that only a single thread is running at a time  */
	private final Lock lock = new ReentrantLock();

	/** The condition used to signal the search to continue until a next solution is found */
	private final Condition nextAsked  = lock.newCondition(); 

	/** The condition used to signal this thread that a new solution has been found and has been delivered */
	private final Condition nextDelivered = lock.newCondition(); /// @todo One condition should actually be enough. 

	/** Constructor
	 * @param space 		the space over which to iterate
	 * @param variables 	the variable order for the iteration
	 * @param domains 		the domains of the variables
	 */
	public JaCoPutilSpaceIter2(JaCoPutilSpace<U> space, String[] variables, AddableInteger[][] domains){
		this.space = space;
		this.variables = variables;
		this.domains = domains;	
		this.nbrVars = variables.length;
		this.searchInitiated = false;
		this.minimize = !space.maximize();
		this.bound = space.infeasibleUtil;
	}
	
	/** @see SparseIterator#getCurrentUtility() */
	public U getCurrentUtility() {
		return this.utility;
	}
	
	/** @see SparseIterator#nextUtility() */
	public U nextUtility() {

		if (this.searchTerminated) 
			return null;
		
		else if(!this.searchInitiated)
			initSearch(null);

		else 
			getNextFromSearch(false);
		
		return this.utility;
	}
	
	/** @see SparseIterator#setCurrentUtility(java.lang.Object) */
	public void setCurrentUtility(U util) {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
	}
	
	/** @see SparseIterator#getCurrentSolution() */
	public AddableInteger[] getCurrentSolution() {
		return this.solution;
	}
	
	/** @see SparseIterator#getDomains() */
	public AddableInteger[][] getDomains() {
		return this.domains;
	}
	
	/** @see SparseIterator#getVariablesOrder() */
	public String[] getVariablesOrder() {
		return this.variables;
	}

	/** @see SparseIterator#nextSolution() */
	public AddableInteger[] nextSolution() {
		
		if (this.searchTerminated) 
			return null;
		
		else if (!this.searchInitiated)
			initSearch(null); // already looks for the first solution
		
		else 
			getNextFromSearch(false);
		
		return this.solution;
	}

	/** @see SparseIterator#update() */
	public void update() {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
	}


	/** @see SparseIterator#getCurrentUtility(java.lang.Object, boolean) */
	public U getCurrentUtility(U bound, boolean minimize) {
		/// @todo Auto-generated method stub
		assert false: "not implemented";
		return null;
	}

	/** @see SparseIterator#nextUtility(java.lang.Object, boolean) */
	public U nextUtility(U bound, boolean minimize) {
		
		if (this.searchTerminated) 
			return null;
		
		assert (minimize == this.minimize): "Changing of bound direction not currently supported";
		assert (minimize ? bound.compareTo(this.bound) <= 0 : bound.compareTo(this.bound) >= 0) : "Unsupported bound relaxation; new bound " + bound + " is worse than old bound: " + this.bound;
		
		// We initiate the search if it has not been done already
		if(!this.searchInitiated) {
			initSearch(bound);
			assert this.utility == null || (minimize? this.utility.compareTo(bound) < 0 : this.utility.compareTo(bound) > 0);
			return this.utility;
		}
		
		// Inform the search that the bound has changed
		if(this.bound.compareTo(bound) != 0){
			
			assert this.search != null;
			this.bound = bound;

			if (this.minimize)
				this.search.setNewBound(bound.subtract(this.space.defaultUtil).intValue());
				
			else // maximizing
				this.search.setNewBound(bound.flipSign().subtract(this.space.defaultUtil).intValue());
		}
		
		// Get the next solution from the search
		getNextFromSearch(false);
		
		assert this.utility == null || (minimize? this.utility.compareTo(bound) < 0 : this.utility.compareTo(bound) > 0);
		
		return this.utility;
	}

	/** The transaction between the current thread and the search thread
	 * 
	 * The control is given to the search thread that will search for the next solution. 
	 * When the next solution is found, the search records it in its fields and gives back the control to the current thread. 
	 * Finally, we get the next solution and its utility using the corresponding getter of the search. 
	 * 
	 * @param first 	whether the search thread must be created and started
	 */
	@SuppressWarnings("unchecked")
	private void getNextFromSearch(final boolean first){

		assert searchInitiated;

		lock.lock();

		try{
			if (first) { // start the search thread
				Thread thread = new Thread(this.search);
				thread.start();
				this.searchThread = thread;
				
			} else // give the control to the search until the next solution is found
				nextAsked.signal();
			
			// We wait for the first solution (or the end of the search if there is none)
			nextDelivered.await(); /// @bug handle spurious wakeups

			int[] nextSol = search.getSolution();
			int nextUtil = search.getUtility();

			// No more solutions
			if(nextSol == null){
				this.utility = null;
				this.solution = null;
				this.searchTerminated = true;
				
			// The search has not terminated yet
			}else{

				try{
					this.utility = (U) this.space.defaultUtil.getClass().getConstructor(int.class).newInstance(nextUtil);
					if (!this.minimize) 
						this.utility = this.utility.flipSign();
					this.utility = this.utility.add(this.space.defaultUtil);
				}catch (Exception e){
					e.printStackTrace();
				}

				// Update the fields
				assert solution != null;
				for(int i = 0; i < this.nbrVars; i++){
					solution[i] = new AddableInteger(nextSol[i]);
				}
			}

		}catch(InterruptedException e){
			e.printStackTrace();
		}finally{
			lock.unlock();
		}
	}


	/**
	 * As long as the search has not ended its thread is kept alive. 
	 * We might want to kill this thread if we do not need any longer the iterator. 
	 * This method can be used for that purpose. 
	 */
	private void terminateSearch(){
		searchThread.interrupt();
		this.searchTerminated = true;
	}
	
	/** @see java.lang.Object#finalize() */
	@Override
	protected void finalize () throws Throwable {
		if (! this.searchTerminated) 
			this.terminateSearch();
		super.finalize();
	}

	/**
	 * This method starts the search thread
	 * @param bound		the initial utility bound, \c null if we do not want to set one
	 */
	private void initSearch(U bound){
		
		// Get the JaCoP store
		this.store = this.space.getStore();
		if (store == null) {
			store = this.space.createStore();
			if (! store.consistency()){ // no feasible solution exists
				this.searchTerminated = true;
				return;
			}
		}
		
		IntVar[] normalVars = new IntVar[nbrVars];
		IntVar[] projectedVars = new IntVar[space.getProjectedVars().length];
		
		// Find all normal variables
		String[] orderedVars = this.variables;
		for(int i = orderedVars.length - 1; i >= 0; i--){
			
			// Construct the domain
			IntervalDomain jacopDom;
			AddableInteger[] dom = this.domains[i];
			jacopDom = new IntervalDomain (dom.length);
			for (AddableInteger val : dom){
				jacopDom.addDom(new IntervalDomain (val.intValue(), val.intValue()));
			}
			
			normalVars[i] = (IntVar) store.findVariable(orderedVars[i]);
			
			// This variable does not exist in the space, we need to create it in the store
			if(normalVars[i] == null){
				// Construct the JaCoP variable
				normalVars[i] = new IntVar (store, orderedVars[i], jacopDom);
				
			}else{
				// We update the domain of the variable
				IntDomain newDom = normalVars[i].dom().intersect(jacopDom);

				// No solution for this variable
				if(newDom.isEmpty()){
					this.searchTerminated = true;
					return;
				}else{
					normalVars[i].dom().in(store.level, normalVars[i], newDom);
				}
			}
		}
		
		// Find all projected variables
		int n = 0;
		for(String var: space.getProjectedVars()){
			projectedVars[n] = (IntVar) store.findVariable(var);
			assert var != null: "Variable " + var + " not found in the store!";
			n++;
		}
		
		// Find the utility variable
		IntVar utilVar = (IntVar) store.findVariable("util_total"); /// @bug Potential name clash with a user-specified variable name
		assert utilVar != null: "Variable " + "util_total" + " not found in the store!";
		
		// We update the utility variable's domain according to the initial bound
		if(bound != null){
			try{
				if (this.minimize){
					utilVar.dom().inMax(store.level, utilVar, bound.subtract(this.space.defaultUtil).intValue() - 1);
				}else{
					utilVar.dom().inMin(store.level, utilVar, bound.flipSign().subtract(this.space.defaultUtil).intValue() + 1);
				}
				
			}catch (JaCoP.core.FailException e){
				// The utility variable's domain does not contain any better value than the new bound
				this.searchInitiated = true;
				this.searchTerminated = true;
				return;
			}
		}
		
		this.bound = bound;		
		this.search = new JaCoPiterSearch(store, normalVars, projectedVars, utilVar, lock, nextAsked, nextDelivered);
		this.solution = new AddableInteger[nbrVars];
		this.searchInitiated = true;
		this.getNextFromSearch(true);
	}
}
