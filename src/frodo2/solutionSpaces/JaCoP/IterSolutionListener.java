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

import java.util.IdentityHashMap;

import java.util.Iterator;
import java.util.concurrent.locks.Condition;

import JaCoP.core.IntVar;
import JaCoP.core.Store;
import JaCoP.search.DepthFirstSearch;
import JaCoP.search.IndomainMin;
import JaCoP.search.InputOrderSelect;
import JaCoP.search.Search;
import JaCoP.search.SelectChoicePoint;
import JaCoP.search.SimpleSolutionListener;


/** This solution listener is a part of the JaCoPutilSpace iterator that allows us to simulate a JaCoP master/slave search combination
 * where the slave is an optimization search while the master is not.
 * 
 * @author Arnaud Jutzeler, Thomas Leaute
 *
 */
public class IterSolutionListener extends SimpleSolutionListener<IntVar> {

	/** The master search */
	private JaCoPiterSearch search;

	/** The JaCoP store */
	private Store store;

	/** The JaCoP variables whose projection has been requested */
	private IntVar[] projectedVars;

	/** The utility variable */
	private IntVar utilVar;

	/** The condition used to signal this thread to continue the search until a next solution is found */
	private Condition nextAsked;

	/** The condition used to signal the main thread that a new solution has been found and has been delivered */
	private Condition nextDelivered;

	/** Constructor
	 * @param search			The JaCoPSearch that start the search
	 * @param store				The JaCoP store in which the search is performed
	 * @param projectedVars		The projected variables that we need to optimize in a slave search
	 * @param utilVar			The utility variable
	 * @param nextAsked			The condition used to signal the search to continue the search
	 * @param nextDelivered		The condition used to signal the iterator thread that a new solution has been found
	 */
	public IterSolutionListener(JaCoPiterSearch search, Store store, IntVar[] projectedVars, IntVar utilVar, Condition nextAsked, Condition nextDelivered){
		this.search = search;
		this.store = store;
		this.projectedVars = projectedVars;
		this.utilVar = utilVar;
		this.nextAsked = nextAsked;
		this.nextDelivered = nextDelivered;
	}
	
	/**
	 * @see JaCoP.search.SimpleSolutionListener#executeAfterSolution(JaCoP.search.Search, JaCoP.search.SelectChoicePoint)
	 */
	@Override
	public boolean executeAfterSolution(Search<IntVar> search,
			SelectChoicePoint<IntVar> select) {
		
		boolean returnCode = super.executeAfterSolution(search, select);
		
		IdentityHashMap<IntVar, Integer> position = select.getVariablesMapping();
		IntVar[] vars = new IntVar[position.size()];
		for (Iterator<IntVar> itr = position.keySet().iterator(); itr.hasNext();) {
			IntVar current = itr.next();	
			vars[position.get(current)] = current;
		}
		
		parentSolutionNo = new int[1];
		
		// Increase the store level for the slave search
		int level = store.level;
		store.setLevel(level+1);
		
		Search<IntVar> slaveSearch = new DepthFirstSearch<IntVar> ();
		slaveSearch.setSolutionListener(new SimpleSolutionListener<IntVar>());
		slaveSearch.getSolutionListener().recordSolutions(false);
		slaveSearch.getSolutionListener().searchAll(false);
		slaveSearch.setAssignSolution(true);
		slaveSearch.setPrintInfo(false);
		
		// We need to project some variables
		if(projectedVars.length != 0){
			
			slaveSearch.labeling(store, new InputOrderSelect<IntVar> (store, this.projectedVars, new IndomainMin<IntVar>()), utilVar);	
			
		// There is no delayed projection to perform
		}else{
			
			/// @todo If there are no variables to project, then using a slave search is an overkill. The utilVar should already have been grounded by the master search. 
			
			IntVar[] utilVars = {utilVar};
			slaveSearch.labeling(store, new InputOrderSelect<IntVar> (store, utilVars, new IndomainMin<IntVar>()), utilVar);
		}		
		
		assert utilVar.singleton(): "The utility variable is not grounded in the solution";
		
		// Record the current solution, and inform the main thread
		assert utilVar.dom().min() < this.search.getCurrentBound() : utilVar.dom().min() + " < " + this.search.getCurrentBound();
		int[] currentSolution = new int[vars.length];

		for (int i = 0; i < vars.length; i++) {
			assert vars[i].singleton(): "Variable " + vars[i].id + " is not grounded in the solution";
			currentSolution[i] = vars[i].min();
		}

		// Give the next solution to the iterator
		this.search.setSolution(currentSolution, utilVar.dom().min());

		// Wake up the iterator
		this.nextDelivered.signal();

		try{
			// Freeze the search
			this.nextAsked.await(); /// @bug handle spurious wakeups

		// The thread was interrupted, we want to exit it
		}catch(InterruptedException e){
			throw new RuntimeException("suicide");
		}
		
		// Restore the store level
		for(int k = store.level; k > level; k--){
			store.removeLevel(k);
		}
		
		store.setLevel(level);
		
		// Reset JaCoP's cost bound to the one passed to the iterator
		this.search.search.costValue = this.search.getCurrentBound();
		
		return returnCode;
	}
}
