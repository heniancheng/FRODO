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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** The output of expectation(), computed on the fly
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class ExpectationOutput < V extends Addable<V>, U extends Addable<U> > extends Hypercube<V, U> {
	
	/** This class' special iterator */
	private class MyIter extends ScalarSpaceIter<V, U> {
		
		/** The iterator over the space that called expectation() */
		private UtilitySolutionSpace.Iterator<V, U> iter;
		
		/** Whether nextSolution() has been called but the corresponding utility hasn't yet been computed */
		private boolean delayed = false;
		
		/** The number of possible assignments to the random variables */
		private long nbrUtilsOut;

		/** The assignment array used by the probSpace iterator */
		private final V[] randAssignment;

		/** The domains of the random variables */
		private final V[][] randDoms;

		/** Constructor
		 * @param order 		the variable iteration order
		 * @param assignment 	An array that will be used as the output of nextSolution()
		 * @param skippedUtil 	The utility value to be skipped, if any
		 */
		@SuppressWarnings("unchecked")
		public MyIter(String[] order, V[] assignment, U skippedUtil) {
			
			// Iteration order: 1) input order; 2) random variables
			String[] fullOrder = new String [order.length + randVars.length];
			System.arraycopy(order, 0, fullOrder, 0, order.length);
			System.arraycopy(randVars, 0, fullOrder, order.length, randVars.length);
			this.iter = space.iterator(fullOrder);
			this.skippedUtil = skippedUtil;
			
			this.randAssignment = (V[]) Array.newInstance(classOfV, randVars.length);
			this.randDoms = (V[][]) Array.newInstance(this.randAssignment.getClass(), randVars.length);
			for (int i = randVars.length - 1; i >= 0; i--) 
				this.randDoms[i] = probSpace.getDomain(randVars[i]);

			// Compute this.nbrUtilsOut
			this.nbrUtilsOut = 1;
			for (String randVar : randVars) 
				this.nbrUtilsOut *= space.getDomain(randVar).length;
			
			// Set up the domains of the iterated variables
			V[][] iterDoms = (V[][]) Array.newInstance(ExpectationOutput.this.assignment.getClass(), order.length);
			for (int i = order.length - 1; i >= 0; i--) 
				iterDoms[i] = space.getDomain(order[i]);
			
			super.init(null, order, iterDoms, assignment);
		}

		/** Constructor
		 * @param order 		the variable iteration order
		 * @param iterDoms 		the domains of the iterated variables
		 * @param assignment 	An array that will be used as the output of nextSolution()
		 * @param skippedUtil 	The utility value to be skipped, if any
		 */
		@SuppressWarnings("unchecked")
		public MyIter(String[] order, V[][] iterDoms, V[] assignment, U skippedUtil) {
			
			// Iteration order: 1) input order; 2) random variables
			String[] fullOrder = new String [order.length + randVars.length];
			this.randAssignment = (V[]) Array.newInstance(classOfV, randVars.length);
			this.randDoms = (V[][]) Array.newInstance(this.randAssignment.getClass(), randVars.length);
			V[][] fullDoms = (V[][]) Array.newInstance(this.randAssignment.getClass(), fullOrder.length);
			System.arraycopy(order, 0, fullOrder, 0, order.length);
			System.arraycopy(iterDoms, 0, fullDoms, 0, iterDoms.length);
			System.arraycopy(randVars, 0, fullOrder, order.length, randVars.length);
			for (int i = randVars.length - 1; i >= 0; i--) 
				this.randDoms[i] = fullDoms[order.length + i] = probSpace.getDomain(randVars[i]);
			this.iter = space.iterator(fullOrder, fullDoms);
			this.skippedUtil = skippedUtil;
			
			// Compute this.nbrUtilsOut
			this.nbrUtilsOut = 1;
			for (String randVar : randVars) 
				this.nbrUtilsOut *= probSpace.getDomain(randVar).length;
			
			super.init(null, order, iterDoms, assignment);
		}
		
		/** @see ScalarSpaceIter#toString() */
		@Override
		public String toString () {
			
			StringBuilder builder = new StringBuilder ("Iterator");
			builder.append("\n\t nbrUtilsOut: " + this.nbrUtilsOut);
			builder.append("\n\t iterator: " + this.iter);
			builder.append("\n\t space: " + ExpectationOutput.this);
			return builder.toString();
		}

		/** @see ScalarBasicSpaceIter#nextUtility() */
		@Override
		public U nextUtility() {
			if (this.nextSolution() != null) 
				return this.getCurrentUtility();
			else 
				return null;
		}

		/** @see ScalarBasicSpaceIter#getCurrentUtility() */
		@Override
		public U getCurrentUtility() {
			
			if (! this.delayed) 
				return super.utility;

			// The computation of the utility has been delayed; compute it now
			AddableDelayed<U> expect = infeasibleUtil.getZero().addDelayed();
			UtilitySolutionSpace.Iterator<V, U> probIter = probSpace.iterator(randVars, this.randDoms, this.randAssignment);
			long i = this.nbrUtilsOut;
			for ( ; i > 0; i--) {
				assert this.iter.hasNext();
				assert probIter.hasNext();
				
				U util = this.iter.nextUtility();
				
				// If the utility is infinite, we can skip the remaining utilities since the expectation will remain infinite
				if (util == infeasibleUtil) {
					expect = infeasibleUtil.addDelayed();
					break;
				}
				
				expect.addDelayed(util.multiply(probIter.nextUtility()));
			}
			for (i-- ; i > 0; i--) // Skip the remaining utilities if the expectation is infinite
				this.iter.nextSolution();
			
			this.delayed = false;
			super.utility = expect.resolve();
			return super.utility;
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#setCurrentUtility(java.lang.Object) */
		public void setCurrentUtility(U util) {
			/// @todo Auto-generated method stub
			assert false : "Not yet implemented";
			
		}

		/** @see ScalarBasicSpaceIter#nextSolution() */
		@Override
		public V[] nextSolution() {
			
			V[] sol = this.nextSolBlind();
			
			U inf = this.skippedUtil;
			if (inf != null) 
				while (inf.equals(this.getCurrentUtility())) 
					sol = this.nextSolBlind();
			
			return sol;
		}
		
		/** @return the next solution, regardless of whether it is feasible or not */
		private V[] nextSolBlind () {
			
			// Return null if there are no more solutions
			if (this.nbrSolLeft <= 0) {
				this.utility = null;
				this.solution = null;
				return null;
			}
			
			if (this.delayed) // the computation of the utility of the previous solution was skipped
				for (long i = this.nbrUtilsOut; i > 0; i--) 
					this.iter.nextSolution();
			
			else // delay the computation of the new solution's utility
				this.delayed = true;
			
			super.iter();
			assert this.solution != null;
			return this.solution;
		}

		/** @see frodo2.solutionSpaces.SolutionSpace.Iterator#getVariablesOrder() */
		public String[] getVariablesOrder() {
			/// @todo Auto-generated method stub
			assert false : "Not yet implemented";
			return null;
		}

		/** @see frodo2.solutionSpaces.SolutionSpace.Iterator#getDomains() */
		public V[][] getDomains() {
			/// @todo Auto-generated method stub
			assert false : "Not yet implemented";
			return null;
		}

		/** @see frodo2.solutionSpaces.SolutionSpace.Iterator#update() */
		public void update() {
			/// @todo Auto-generated method stub
			assert false : "Not yet implemented";
			
		}

		/** @see ScalarSpaceIter#nextUtility(Addable, boolean) */
		@Override
		public U nextUtility(U bound, final boolean minimize) {
			
			/// @todo Improve?
			
			U util;
			do {
				util = this.nextUtility();
			} while (util != null && (minimize ? util.compareTo(bound) >= 0 : util.compareTo(bound) <= 0));
			
			return util;
		}

		/** @see ScalarSpaceIter#getCurrentUtility(Addable, boolean) */
		@Override
		public U getCurrentUtility(U bound, boolean minimize) {
			/// @todo Improve?
			return this.getCurrentUtility();
		}
		
	}

	/** The space whose expectation we want to compute */
	private UtilitySolutionSpace<V, U> space;
	
	/** The list of random variables */
	private String[] randVars;
	
	/** The joint probability distribution for the random variables */
	private UtilitySolutionSpace<V, U> probSpace;
	
	/** Number of solutions */
	private long nbrUtils;
	
	/** Empty constructor used for externalization */
	public ExpectationOutput () { }

	/** Constructor
	 * @param space 			the space whose expectation we want to compute
	 * @param distributions		the probability distributions for the random variables
	 * @param infeasibleUtil 	the infeasible utility
	 */
	@SuppressWarnings("unchecked")
	public ExpectationOutput(UtilitySolutionSpace<V, U> space, Map< String, UtilitySolutionSpace<V, U> > distributions, U infeasibleUtil) {
		
		// Remove the input random variables from the list of variables in this space
		ArrayList<String> vars = new ArrayList<String> (Arrays.asList(space.getVariables()));
		vars.removeAll(distributions.keySet());
		this.variables = vars.toArray(new String [vars.size()]);
		
		// Look up the domains
		this.classOfV = (Class<V>) space.getDomain(0).getClass().getComponentType();
	    this.assignment = (V[]) Array.newInstance(this.classOfV, this.variables.length);
	    this.domains = (V[][]) Array.newInstance(this.assignment.getClass(), this.variables.length);
	    this.nbrUtils = 1;
	    for (int i = this.variables.length - 1; i >= 0; i--) {
	    	this.domains[i] = space.getDomain(this.variables[i]);
	    	this.nbrUtils *= this.domains[i].length;
	    }
	    
	    this.infeasibleUtil = infeasibleUtil;
		
	    this.space = space;
	    
	    // Join all probability spaces
	    java.util.Iterator< UtilitySolutionSpace<V, U> > distIter = distributions.values().iterator();
		this.probSpace = distIter.next();
		while (distIter.hasNext()) 
			this.probSpace = this.probSpace.multiply(distIter.next());
		this.randVars = this.probSpace.getVariables();
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		/// @todo Improve externalization
		
		out.writeObject(this.variables);
		out.writeObject(this.domains);
		out.writeObject(this.infeasibleUtil);
		
		// Write the utilities
		assert this.nbrUtils < Integer.MAX_VALUE : "Cannot resolve a space that contains more than 2^31-1 solutions";
		out.writeInt((int) this.nbrUtils);
		out.writeObject(this.getClassOfU());
		for (UtilitySolutionSpace.Iterator<V, U> iter = this.iterator(); iter.hasNext(); ) 
			out.writeObject(iter.nextUtility());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

		this.variables = (String[]) in.readObject();
		this.domains = (V[][]) in.readObject();
		this.infeasibleUtil = (U) in.readObject();
		
		// Read the utilities
		this.nbrUtils = in.readInt();
		Class<U> classOfU = (Class<U>) in.readObject();
		this.values = (U[]) Array.newInstance(classOfU, (int) nbrUtils);
		this.number_of_utility_values = this.values.length;
		for (int i = 0; i < nbrUtils; i++) 
			this.values[i] = (U) in.readObject();
	}
	
	/** Replaces a de-serialized JoinOutputHypercube with a pure Hypercube
	 * @return 	a Hypercube corresponding to the explicit representation of this JoinOutputHypercube
	 * @throws ObjectStreamException 	if an error occurs
	 */
	private Object readResolve() throws ObjectStreamException {
		return new Hypercube<V, U> (this.variables, this.domains, this.values, this.infeasibleUtil);
	}
	
	/** @see BasicHypercube#toString() */
	@Override
	public String toString () {
		
		StringBuilder builder = new StringBuilder ("ExpectationOutput");
		builder.append("\n\t randVars: " + Arrays.toString(this.randVars));
		builder.append("\n\t space: " + this.space);
		builder.append("\n\t probSpace: " + this.probSpace);
		return builder.toString();
	}

	/** @see java.lang.Object#clone() */
	@Override
	public ExpectationOutput<V, U> clone () {
		return this; /// @bug I'm too lazy to implement this method
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#prettyPrint(java.io.Serializable) */
	public String prettyPrint(U ignoredUtil) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getUtility(V[]) */
	public U getUtility(V[] variables_values) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#getUtility(java.lang.String[], V[]) */
	@Override
	public U getUtility(String[] variables_names, V[] variables_values) {
		
		assert this.getNumberOfVariables() == 0 : "Not yet implemented";
		
		return this.resolve().getUtility(0);
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getUtility(java.util.Map) */
	public U getUtility(Map<String, V> assignments) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicHypercube#getUtility(long) */
	@Override
	public U getUtility(long index) {
		
		assert index == 0 && this.getNumberOfVariables() == 0 : "Not yet implemented";
		
		return this.resolve().getUtility(0);
	}

	/** @see BasicHypercube#getClassOfU() */
	@Override
	public Class<U> getClassOfU() {
		return this.space.getClassOfU();
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setUtility(V[], java.io.Serializable) */
	public boolean setUtility(V[] variables_values, U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setUtility(long, java.io.Serializable) */
	public void setUtility(long index, U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setDefaultUtility(java.io.Serializable) */
	public void setDefaultUtility(U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getDefaultUtility() */
	public U getDefaultUtility() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setInfeasibleUtility(java.io.Serializable) */
	public void setInfeasibleUtility(U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#augment(V[], java.io.Serializable) */
	public void augment(V[] variables_values, U utility_value) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#isIncludedIn(frodo2.solutionSpaces.BasicUtilitySolutionSpace) */
	public boolean isIncludedIn(BasicUtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getName() */
	public String getName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#setName(java.lang.String) */
	public void setName(String name) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getRelationName() */
	public String getRelationName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#setRelationName(java.lang.String) */
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

	/** @see BasicHypercube#getNumberOfSolutions() */
	@Override
	public long getNumberOfSolutions() {
		return this.nbrUtils;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#renameVariable(java.lang.String, java.lang.String) */
	public void renameVariable(String oldName, String newName) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.SolutionSpace#renameAllVars(java.lang.String[]) */
	public BasicHypercube<V, U> renameAllVars(String[] newVarNames) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getIndex(java.lang.String) */
	public int getIndex(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getDomain(java.lang.String, int) */
	public V[] getDomain(String variable, int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#setDomain(java.lang.String, V[]) */
	public void setDomain(String var, V[] dom) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.SolutionSpace#augment(V[]) */
	public void augment(V[] variables_values) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace, java.lang.String[]) */
	public SolutionSpace<V> join(SolutionSpace<V> space,
			String[] total_variables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace) */
	public SolutionSpace<V> join(SolutionSpace<V> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace[], java.lang.String[]) */
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces,
			String[] total_variables_order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace[]) */
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#resolve() */
	@SuppressWarnings("unchecked")
	@Override
	public Hypercube<V, U> resolve() {
		
		if (this.getNumberOfVariables() == 0) { // return a scalar space
			
			// Compute the scalar expectation
			AddableDelayed<U> expect = this.infeasibleUtil.getZero().addDelayed();
			UtilitySolutionSpace.Iterator<V, U> probIter = this.probSpace.iterator();
			UtilitySolutionSpace.Iterator<V, U> spaceIter = this.space.iterator(this.probSpace.getVariables(), this.probSpace.getDomains());
			while (probIter.hasNext()) {
				U util = spaceIter.nextUtility();
				
				if (util == this.infeasibleUtil) 
					return new ScalarHypercube<V, U> (util, this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass());
				
				expect.addDelayed(util.multiply(probIter.nextUtility()));
			}
			
			return new ScalarHypercube<V, U> (expect.resolve(), this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass());
		}

		// Resolve the utilities
		assert this.nbrUtils < Integer.MAX_VALUE : "Cannot resolve a space that contains more than 2^31-1 solutions";
		U[] values = (U[]) Array.newInstance(this.getClassOfU(), (int) nbrUtils);
		int i = 0;
		for (UtilitySolutionSpace.Iterator<V, U> iter = this.iterator(); iter.hasNext(); ) 
			values[i++] = iter.nextUtility();

		return new Hypercube<V, U> (this.variables, this.domains, values, this.infeasibleUtil);
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#toHypercube() */
	public Hypercube<V, U> toHypercube() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#isIncludedIn(frodo2.solutionSpaces.UtilitySolutionSpace) */
	public boolean isIncludedIn(UtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#changeVariablesOrder(java.lang.String[]) */
	public ExpectationOutput<V, U> changeVariablesOrder(
			String[] variables_order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#join(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<V, U> join(UtilitySolutionSpace<V, U> space,
			String[] total_variables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String[], V[][]) */
	public ExpectationOutput<V, U> slice(String[] variables_names,
			V[][] sub_domains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String[], V[]) */
	public ExpectationOutput<V, U> slice(String[] variables_names, V[] values) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String, V[]) */
	public ExpectationOutput<V, U> slice(String var, V[] subDomain) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String, frodo2.solutionSpaces.Addable) */
	public ExpectationOutput<V, U> slice(String var, V val) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(V[]) */
	public ExpectationOutput<V, U> slice(V[] variables_values) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#joinMinNCCCs(frodo2.solutionSpaces.UtilitySolutionSpace) */
	public UtilitySolutionSpace<V, U> joinMinNCCCs(
			UtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#joinMinNCCCs(frodo2.solutionSpaces.UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<V, U> joinMinNCCCs(
			UtilitySolutionSpace<V, U>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<V, U> multiply(
			UtilitySolutionSpace<V, U> space, String[] total_variables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace) */
	public UtilitySolutionSpace<V, U> multiply(UtilitySolutionSpace<V, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<V, U> multiply(
			UtilitySolutionSpace<V, U>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusAllSols(java.lang.String, java.util.Map, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<V, U> consensusAllSols(
			String varOut,
			Map<String, UtilitySolutionSpace<V, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusExpect(java.lang.String, java.util.Map, boolean) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<V, U> consensusExpect(
			String varOut,
			Map<String, UtilitySolutionSpace<V, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusAllSolsExpect(java.lang.String, java.util.Map, boolean) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<V, U> consensusAllSolsExpect(
			String varOut,
			Map<String, UtilitySolutionSpace<V, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#project(int, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<V, U> project(
			int number_to_project, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#projectAll(boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<V, U> projectAll(
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#projectAll(boolean, java.lang.String[]) */
	@SuppressWarnings("unchecked")
	@Override
	public ProjOutput<V, U> projectAll(boolean maximum, String[] order) {
		
		// Build up the domains corresponding to the input variable order
		final int nbrVars = order.length;
		V[][] newDoms = this.reorderDomains(order, nbrVars);
		
		// Initialize the list of assignments, in case no feasible assignment is found
		for (int i = nbrVars - 1; i >= 0; i--) 
			this.assignment[i] = newDoms[i][0];
		
		// Look for the optimal utility and assignments
		U optUtil = null;
		if (maximum) 
			optUtil = this.infeasibleUtil.getMinInfinity();
		else 
			optUtil = this.infeasibleUtil.getPlusInfinity();
		for (UtilitySolutionSpace.Iterator<V, U> iter = this.iterator(order, newDoms); iter.hasNext(); ) {
			U util = iter.nextUtility(optUtil, !maximum);
			if (util == null) 
				break;
			optUtil = util;
			System.arraycopy(iter.getCurrentSolution(), 0, this.assignment, 0, nbrVars);
		}
		
		return new ProjOutput<V, U> (new ScalarHypercube<V, U>(optUtil, this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass()), order, 
				new ScalarBasicHypercube< V, ArrayList<V> > (new ArrayList<V> (Arrays.asList(this.assignment)), null));
	}

	/** Reorders the domains according to the input variable order
	 * @param varOrder 	new variable order
	 * @param nbrVars 	number of variables
	 * @return 			the reordered array of domains
	 */
	private V[][] reorderDomains (String[] varOrder, int nbrVars) {
		
		V[][] newDoms = this.domains.clone();
		for (int i = 0; i < nbrVars; i++) {
			String var = varOrder[i];
			for (int j = 0; j < nbrVars; j++) {
				if (this.variables[j].equals(var)) {
					newDoms[i] = this.domains[j];
					break;
				}
			}
		}
		return newDoms;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#blindProject(java.lang.String, boolean) */
	public Hypercube<V, U> blindProject(String varOut,
			boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#blindProject(java.lang.String[], boolean) */
	public UtilitySolutionSpace<V, U> blindProject(String[] varsOut,
			boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#blindProjectAll(boolean) */
	public U blindProjectAll(boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#min(java.lang.String) */
	public Hypercube<V, U> min(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#max(java.lang.String) */
	public Hypercube<V, U> max(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#split(frodo2.solutionSpaces.Addable, boolean) */
	public Hypercube<V, U> split(U threshold, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#sample(int) */
	public Map<V, Double> sample(int nbrSamples) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#setProblem(frodo2.solutionSpaces.ProblemInterface) */
	public void setProblem(ProblemInterface<V, U> problem) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#iteratorBestFirst(boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst<V, U> iteratorBestFirst(
			boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#iteratorBestFirst(boolean, java.lang.String[], V[]) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst<V, U> iteratorBestFirst(
			boolean maximize, String[] fixedVariables, V[] fixedValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#iterator() */
	@Override
	public MyIter iterator() {
		return this.iterator(this.getVariables(), this.getDomains(), null);
	}

	/** @see Hypercube#sparseIter() */
	@Override
	public MyIter sparseIter() {
		return this.sparseIter(this.getVariables(), this.getDomains(), null);
	}

	/** @see Hypercube#iterator(java.lang.String[]) */
	@Override
	public MyIter iterator(String[] order) {
		return new MyIter (order, null, null);
	}

	/** @see Hypercube#sparseIter(java.lang.String[]) */
	@Override
	public MyIter sparseIter(String[] order) {
		return new MyIter (order, null, this.infeasibleUtil);
	}

	/** @see Hypercube#iterator(java.lang.String[], V[][]) */
	@Override
	public MyIter iterator(String[] variables, V[][] domains) {
		return new MyIter (variables, domains, null, null);
	}

	/** @see Hypercube#sparseIter(java.lang.String[], V[][]) */
	@Override
	public MyIter sparseIter(String[] variables, V[][] domains) {
		return new MyIter (variables, domains, null, this.infeasibleUtil);
	}

	/** @see Hypercube#iterator(java.lang.String[], V[][], V[]) */
	@Override
	public MyIter iterator(String[] variables, V[][] domains, V[] assignment) {
		return new MyIter (variables, domains, assignment, null);
	}

	/** @see Hypercube#sparseIter(java.lang.String[], V[][], V[]) */
	@Override
	public MyIter sparseIter(String[] variables, V[][] domains, V[] assignment) {
		return new MyIter (variables, domains, assignment, this.infeasibleUtil);
	}

}
