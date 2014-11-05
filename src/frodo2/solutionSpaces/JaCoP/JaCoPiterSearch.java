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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import JaCoP.core.IntVar;
import JaCoP.core.Store;
import JaCoP.search.DepthFirstSearch;
import JaCoP.search.IndomainMin;
import JaCoP.search.InputOrderSelect;

/** A wrapper around a JaCoP DepthFirstSearch that is used to run this search on a new thread
 * 
 * @author Arnaud Jutzeler, Thomas Leaute
 *
 */
public class JaCoPiterSearch implements Runnable {

	/** The JaCoP store */
	private Store store;
	
	/** The JaCoP variables */
	private IntVar[] vars;
	
	/** The JaCoP variables whose projection has been requested */
	private IntVar[] projectedVars;
	
	/** The utility variable */
	private IntVar utilVar;
	
	/** The last solution found */
	private int[] solution;
	
	/** The utility of the last solution found */
	private int utility;
	
	/** The current bound */
	private int currentBound;
	
	/** The lock used to ensure that only a single thread is running at a time  */
	private Lock lock;
	
	/** The condition used to signal this thread to continue the search until a next solution is found */
	private Condition nextAsked;
	
	/** The condition used to signal the main thread that a new solution has been found and has been delivered */
	private Condition nextDelivered;
	
	/** JaCoP's search strategy */
	DepthFirstSearch<IntVar> search;


	/**	Constructor
	 * @param store				The JaCoP store
	 * @param vars				The variables over which we iterate
	 * @param projectedVars		The variable whose projection has been requested
	 * @param utilVar			The utility variable
	 * @param lock				The lock that guarantees the mutual exclusion in the execution of the search thread and the iterator thread
	 * @param nextAsked			The condition used to signal the search to continue the search
	 * @param nextDelivered		The condition used to signal the iterator thread that a new solution has been found
	 */
	public JaCoPiterSearch(Store store, IntVar[] vars, IntVar[] projectedVars, IntVar utilVar, Lock lock, Condition nextAsked, Condition nextDelivered){
		this.store = store;
		this.vars = vars;
		this.projectedVars = projectedVars;
		this.utilVar = utilVar;
		this.lock = lock;
		this.nextAsked = nextAsked;
		this.nextDelivered = nextDelivered;
		this.currentBound = Integer.MAX_VALUE;
		this.solution = null;
		this.utility = 0;
	}
	
	/** @see java.lang.Runnable#run() */
	public void run() {
		IterSolutionListener solListener = new IterSolutionListener(this, store, projectedVars, utilVar, nextAsked, nextDelivered);
		
		// Search for all solutions strictly better than the bound
		search = new DepthFirstSearch<IntVar> ();
		search.setSolutionListener(solListener);
		search.getSolutionListener().recordSolutions(false);
		search.getSolutionListener().searchAll(true);
		search.setAssignSolution(false);
		search.setPrintInfo(false);
		
		// Acquire the lock
		lock.lock();
		
		try{
			
			// Start the depth first search
			search.labeling(store, new InputOrderSelect<IntVar> (store, this.vars, new IndomainMin<IntVar>()), utilVar);
			
			// We set null as the current solution to inform that the search has finished
			this.solution = null;
			this.utility = 0;
			
			// Wake up the iterator
			this.nextDelivered.signal();
			
		// A RuntimeException can be used to exit the run() method and then kill the thread
		}catch(RuntimeException e){
			 // Do nothing, just exit the run function
		}finally{
			
			// Release the lock
			lock.unlock();
		}
		
		search = null;
	}
	
	/** The method used by the SolutionListener to record the last solution found
	 * @param lastSolution	The last solution
	 * @param lastUtility	The utility of the last solution
	 */
	public void setSolution(int[] lastSolution, int lastUtility){
		this.solution = lastSolution;
		this.utility = lastUtility;
	}
	
	/** The method used by the iterator to get the last solution found
	 * @return the last solution found
	 */
	public int[] getSolution() {
		return solution;
	}
	
	/** The method used by the iterator to get the utiltiy of last solution found
	 * @return the utility of the last solution found
	 */
	public int getUtility() {
		return utility;
	}
	
	/** The method used by the iterator to set the new bound
	 * @param newBound	The new bound
	 */
	public void setNewBound(int newBound) {
		this.currentBound = newBound;
	}
	
	/** The method used by the SolutionLIstener to get the current bound
	 * @return the current bound
	 */
	public int getCurrentBound() {
		return currentBound;
	}
}