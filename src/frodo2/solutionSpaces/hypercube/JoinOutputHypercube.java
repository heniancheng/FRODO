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
import java.util.HashSet;
import java.util.Map;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** The special output of a join that remembers its inputs rather than computing itself explicitly
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class JoinOutputHypercube < V extends Addable<V>, U extends Addable<U> > 
extends Hypercube<V, U> {
	
	/** An iterator for a JoinOutputHypercube
	 * @author Thomas Leaute
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	private static class JoinOutputIterator < V extends Addable<V>, U extends Addable<U> > implements UtilitySolutionSpace.Iterator<V, U> {
		
		/** The underlying iterators 
		 * @todo Order the iterators by putting the ones that are cheap to check first. 
		 */
		private UtilitySolutionSpace.Iterator<V, U>[] iters;
		
		/** Whether we are adding or multiplying */
		private final boolean addition;
		
		/** The infeasible utility */
		private final U infeasibleUtil;
		
		/** The utility value to skip, if any */
		private final U skippedUtil;
		
		/** Constructor 
		 * @param iters 			the underlying iterators
		 * @param addition 			Whether we are adding or multiplying
		 * @param infeasibleUtil 	The infeasible utility
		 * @param skippedUtil 		The utility value to skip, if any
		 */
		private JoinOutputIterator (UtilitySolutionSpace.Iterator<V, U>[] iters, boolean addition, U infeasibleUtil, U skippedUtil) {
			this.iters = iters;
			this.addition = addition;
			this.infeasibleUtil = infeasibleUtil;
			this.skippedUtil = skippedUtil;
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			return "JoinOutputIterator over the following iterators:\n" + Arrays.toString(iters);
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentUtility() */
		public U getCurrentUtility() {
			
			U util = this.iters[0].getCurrentUtility();
			if (util == null) 
				return null;
			else if (this.addition && util == this.infeasibleUtil) // whatever we add to the infeasible utility will remain infeasible
				return util;
			
			AddableDelayed<U> sum = util.addDelayed(); 
				
			final int nbrIters = iters.length;
			for (int i = 1; i < nbrIters; i++) {
				
				if (this.addition) {
					U otherUtil = this.iters[i].getCurrentUtility();
					if (otherUtil == this.infeasibleUtil) // whatever we add to the infeasible utility will remain infeasible
						return otherUtil;
					else 
						sum.addDelayed(otherUtil);
					
				} else // multiplying
					sum.multiplyDelayed(this.iters[i].getCurrentUtility()); // multiplying the infeasible utility by a negative number makes it become feasible, so we can't prune
			}
			
			return sum.resolve();
		}

		/** @see frodo2.solutionSpaces.UtilitySolutionSpace.Iterator#getCurrentUtility(java.lang.Object, boolean) */
		public U getCurrentUtility(U bound, final boolean minimize) {
			
			U util = this.iters[0].getCurrentUtility();
			if (util == null) 
				return null;
			else if (this.addition && util == this.infeasibleUtil) // whatever we add to the infeasible utility will remain infeasible
				return util;
			
			AddableDelayed<U> sum = util.addDelayed(); 
			
			if (this.addition) {
				
				final int nbrItersMin1 = iters.length - 1;
				for (int i = 1; i < nbrItersMin1; i++) {

					U otherUtil = this.iters[i].getCurrentUtility();
					if (otherUtil == this.infeasibleUtil) // whatever we add to the infeasible utility will remain infeasible
						return otherUtil;
					else 
						sum.addDelayed(otherUtil);
				}
				
				// Handle the last iterator in a special fashion
				final U sumMinLast = sum.resolve();
				return sumMinLast.add(this.iters[nbrItersMin1].getCurrentUtility(bound.subtract(sumMinLast), minimize));
				
			} else { // multiplication
				
				final int nbrIters = iters.length;
				for (int i = 1; i < nbrIters; i++) 
					sum.multiplyDelayed(this.iters[i].getCurrentUtility()); // multiplying the infeasible utility by a negative number makes it become feasible, so we can't prune
			}
			
			return sum.resolve();
		}
		
		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextUtility() */
		@Override
		public U nextUtility() {
			
			U util = this.nextUtilBlind();
			
			final U inf = this.skippedUtil;
			if (inf != null) 
				while (inf.equals(util)) 
					util = this.nextUtilBlind();
			
			return util;
		}
		
		/** @return the next utility, regardless of whether it is feasible or not */
		private U nextUtilBlind () {
			
			final UtilitySolutionSpace.Iterator<V, U>[] myIters = this.iters;
			
			U util = myIters[0].nextUtility();
			if (util == null) 
				return null;
			
			if (! this.addition) { // multiplication
				AddableDelayed<U> mul = util.addDelayed();
				
				for (int i = myIters.length - 1; i >= 1; i--) 
					mul.multiplyDelayed(myIters[i].nextUtility());
				
				return mul.resolve();
				
			} else { // addition
				
				// Loop until util becomes infeasible
				final int nbrIters = myIters.length;
				int i = 0;
				final AddableDelayed<U> sum = util.addDelayed();
				while (!sum.isInfinite() && ++i < nbrIters)
					sum.addDelayed(myIters[i].nextUtility());
				
				// As soon as the util is infeasible, spare the calls to add()
				while (++i < nbrIters) 
					myIters[i].nextSolution();
				
				return sum.resolve();
			}
		}

		/** @see frodo2.solutionSpaces.UtilitySolutionSpace.Iterator#nextUtility(java.lang.Object, boolean) */
		public U nextUtility(U bound, final boolean minimize) {
			
			final UtilitySolutionSpace.Iterator<V, U>[] myIters = this.iters;
			final UtilitySolutionSpace.Iterator<V, U> firstIter = myIters[0];
			
			if (! firstIter.hasNext()) 
				return null;
			
			if (! this.addition) { // multiplication
				
				while (firstIter.hasNext()) {
					AddableDelayed<U> mul = firstIter.nextUtility().addDelayed();
					for (int i = myIters.length - 1; i >= 1; i--) 
						mul.multiplyDelayed(myIters[i].nextUtility());
					
					U util = mul.resolve();
					if ((minimize ? util.compareTo(bound) < 0 : util.compareTo(bound) > 0)) 
						return util;
				}
				
				return null;
				
			} else { // addition
				
				final int nbrIters = myIters.length;
				final int nbrItersMin1 = nbrIters - 1;
				
				while (firstIter.hasNext()) {
					
					// Loop until util becomes infeasible
					int i = 0;
					AddableDelayed<U> sum = firstIter.nextUtility().addDelayed();
					while (!sum.isInfinite() && ++i < nbrItersMin1)
						sum.addDelayed(myIters[i].nextUtility());
					U util = sum.resolve();
					
					// Treat the last iterator in a special way
					if (! util.equals(infeasibleUtil)) {
						myIters[i].nextSolution();
						util = util.add(myIters[i++].getCurrentUtility(bound.subtract(util), minimize));
					}
					
					// As soon as the util is infeasible, spare the calls to add()
					while (++i < nbrIters) 
						myIters[i].nextSolution();
					
					if ((minimize ? util.compareTo(bound) < 0 : util.compareTo(bound) > 0)) 
						return util;
				}
				
				return null;
			}
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#setCurrentUtility(java.lang.Object) */
		public void setCurrentUtility(U util) {
			/// @todo Auto-generated method stub
			assert false : "Not yet implemented";
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentSolution() */
		public V[] getCurrentSolution() {
			return this.iters[0].getCurrentSolution();
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getDomains() */
		public V[][] getDomains() {
			return this.iters[0].getDomains();
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getNbrSolutions() */
		public long getNbrSolutions() {
			return this.iters[0].getNbrSolutions();
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getVariablesOrder() */
		public String[] getVariablesOrder() {
			return this.iters[0].getVariablesOrder();
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#hasNext() */
		public boolean hasNext() {
			return this.iters[0].hasNext();
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextSolution() */
		public V[] nextSolution() {
			
			if (this.skippedUtil == null) {
				
				V[] sol = this.iters[0].nextSolution();
				for (int i = this.iters.length - 1; i >= 1; i--) 
					this.iters[i].nextSolution();
				return sol;
				
			} else { // sparse
				if (this.nextUtility() == null) 
					return null;
				else 
					return this.iters[0].getCurrentSolution();
			}
		}

		/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#update() */
		public void update() {
			/// @todo Auto-generated method stub
			assert false : "Not yet implemented";
		}
	}
	
	/** The input spaces to the join */
	protected ArrayList< UtilitySolutionSpace<V, U> > inputs = new ArrayList< UtilitySolutionSpace<V, U> > ();
	
	/** Number of solutions */
	protected long nbrUtils;
	
	/** Whether we are adding or multiplying */
	protected final boolean addition;
	
	/** The types of spaces that we know how to handle */
	private static HashSet< Class<?> > knownSpaces;
	
	static {
		knownSpaces = new HashSet< Class<?> > ();
		knownSpaces.add(BasicHypercube.class);
		knownSpaces.add(ScalarBasicHypercube.class);
		knownSpaces.add(Hypercube.class);
		knownSpaces.add(Hypercube.NullHypercube.class);
		knownSpaces.add(ScalarHypercube.class);
		knownSpaces.add(JoinOutputHypercube.class);
	}
	
	/** Constructor for a binary join
	 * @param space1 			the first space
	 * @param space2 			the second space
	 * @param vars 				the array of variables
	 * @param doms 				the array of domains
	 * @param addition 			Whether we are adding or multiplying
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @param nbrUtils 			the number of solutions
	 */
	@SuppressWarnings("unchecked")
	public JoinOutputHypercube (UtilitySolutionSpace<V, U> space1, UtilitySolutionSpace<V, U> space2, String[] vars, V[][] doms, boolean addition, U infeasibleUtil, long nbrUtils) {
		
		assert vars.length == doms.length : "A hypercube must specify a domain for each of its variables";
		
		this.variables = vars;
		this.domains = doms;
		this.classOfV = (Class<V>) doms.getClass().getComponentType().getComponentType();
	    this.assignment = (V[]) Array.newInstance(this.classOfV, vars.length);
	    this.infeasibleUtil = infeasibleUtil;
	    
	    // Expand the input spaces if they are JoinOutputHypercubes and they agree on this.addition
	    this.addition = addition;
	    if (space1 instanceof JoinOutputHypercube) {
	    	JoinOutputHypercube<V, U> space1Cast = (JoinOutputHypercube<V, U>) space1;
	    	if (space1Cast.addition == this.addition) 
	    		this.inputs.addAll(space1Cast.inputs);
	    	else 
		    	this.inputs.add(space1);
	    } else 
	    	this.inputs.add(space1);
	    if (space2 instanceof JoinOutputHypercube) {
	    	JoinOutputHypercube<V, U> space2Cast = (JoinOutputHypercube<V, U>) space2;
	    	if (space2Cast.addition == this.addition) 
	    		this.inputs.addAll(space2Cast.inputs);
	    	else 
		    	this.inputs.add(space2);
	    } else 
	    	this.inputs.add(space2);
	    
		this.nbrUtils = nbrUtils;
	}

	/** Constructor for an n-ary join
	 * @param space1 			the first space
	 * @param others 			the other spaces
	 * @param vars 				the array of variables
	 * @param doms 				the array of domains
	 * @param addition 			Whether we are adding or multiplying
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @param nbrUtils 			the number of solutions
	 */
	@SuppressWarnings("unchecked")
	JoinOutputHypercube (UtilitySolutionSpace<V, U> space1, UtilitySolutionSpace<V, U>[] others, String[] vars, V[][] doms, boolean addition, U infeasibleUtil, long nbrUtils) {
		
		assert vars.length == doms.length : "A hypercube must specify a domain for each of its variables";
		
		this.variables = vars;
		this.domains = doms;
		this.classOfV = (Class<V>) doms.getClass().getComponentType().getComponentType();
	    this.assignment = (V[]) Array.newInstance(this.classOfV, vars.length);
	    this.infeasibleUtil = infeasibleUtil;
		
	    // Expand the input spaces if they are JoinOutputHypercubes and they agree with this.addition
		this.addition = addition;
	    if (space1 instanceof JoinOutputHypercube) {
	    	JoinOutputHypercube<V, U> space1Cast = (JoinOutputHypercube<V, U>) space1;
	    	if (space1Cast.addition == this.addition) 
	    		this.inputs.addAll(space1Cast.inputs);
	    	else 
		    	this.inputs.add(space1);
	    } else 
	    	this.inputs.add(space1);
		for (int i = others.length - 1; i >= 0; i--) {
			space1 = others[i];
		    if (space1 instanceof JoinOutputHypercube) {
		    	JoinOutputHypercube<V, U> space1Cast = (JoinOutputHypercube<V, U>) space1;
		    	if (space1Cast.addition == this.addition) 
		    		this.inputs.addAll(space1Cast.inputs);
		    	else 
			    	this.inputs.add(space1);
		    } else 
		    	this.inputs.add(space1);
		}
	    
		this.nbrUtils = nbrUtils;
	}
	
	/** Constructor
	 * @param inputs 			the array of input spaces to the join
	 * @param vars 				the array of variables
	 * @param doms 				the array of domains
	 * @param addition 			Whether we are adding or multiplying
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @param nbrUtils 			the number of solutions
	 */
	@SuppressWarnings("unchecked")
	protected JoinOutputHypercube (ArrayList< UtilitySolutionSpace<V, U> > inputs, String[] vars, V[][] doms, boolean addition, U infeasibleUtil, long nbrUtils) {
		
		assert vars.length > 0  : "A hypercube must contain at least one variable";
		assert vars.length == doms.length : "A hypercube must specify a domain for each of its variables";
		
		this.variables = vars;
		this.domains = doms;
		this.classOfV = (Class<V>) doms.getClass().getComponentType().getComponentType();
	    this.assignment = (V[]) Array.newInstance(this.classOfV, vars.length);
	    this.infeasibleUtil = infeasibleUtil;

	    this.inputs = inputs;
	    assert this.inputs != null;
	    this.addition = addition;
		this.nbrUtils = nbrUtils;
	}
	
	/** Constructor 
	 * 
	 * @param addition	Whether we are adding or multiplying
	 * 
	 */
	protected JoinOutputHypercube(boolean addition) {
		this.addition = addition;
	}
	
	/** Empty constructor only to be used for externalization */
	public JoinOutputHypercube () {
		this.addition = false;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.variables);
		out.writeObject(this.domains);
		out.writeObject(this.infeasibleUtil);
		
		// Write the utilities
		assert this.nbrUtils < Integer.MAX_VALUE : "Cannot resolve a JoinOutputHypercube that contains more than 2^31-1 solutions";
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
	@SuppressWarnings("unchecked")
	private Object readResolve() throws ObjectStreamException {
		
		if (this.getNumberOfVariables() == 0) 
			return new ScalarHypercube<V, U> (this.values[0], this.infeasibleUtil, (Class<? extends V[]>) this.domains.getClass().getComponentType());
		else 
			return new Hypercube<V, U> (this.variables, this.domains, this.values, this.infeasibleUtil);
	}
	
	/** @see Hypercube#resolve() */
	@SuppressWarnings("unchecked")
	@Override
	public Hypercube<V, U> resolve () {
		
		if (this.getNumberOfVariables() == 0) 
			return this.scalarHypercube(this.getUtility(0));

		// Resolve the utilities
		assert this.nbrUtils < Integer.MAX_VALUE : "Cannot resolve a JoinOutputHypercube that contains more than 2^31-1 solutions";
		U[] values = (U[]) Array.newInstance(this.getClassOfU(), (int) nbrUtils);
		int i = 0;
		for (UtilitySolutionSpace.Iterator<V, U> iter = this.iterator(); iter.hasNext(); ) 
			values[i++] = iter.nextUtility();

		return new Hypercube<V, U> (this.variables, this.domains, values, this.infeasibleUtil);
	}

	/** @see Hypercube#clone() */
	@Override
	public Hypercube<V, U> clone() {
		
		// Manually clone the underlying spaces
		ArrayList< UtilitySolutionSpace<V, U> > spaces = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs.size());
		for (UtilitySolutionSpace<V, U> space : this.inputs) 
			spaces.add(space.clone());
		
		return new JoinOutputHypercube<V, U> (spaces, this.variables.clone(), this.domains.clone(), this.addition, this.infeasibleUtil, this.nbrUtils);
	}

	/** @see BasicHypercube#knows(java.lang.Class) */
	@Override
	public boolean knows(Class<?> spaceClass) {
		return knownSpaces.contains(spaceClass);
	}

	/** @see BasicHypercube#getClassOfU() */
	@Override
	public Class<U> getClassOfU () {
		return this.inputs.get(0).getClassOfU();
	}
	
	/** @see Hypercube#newIter(java.lang.String[], V[][], V[], Addable) */
	@SuppressWarnings("unchecked")
	@Override
	protected UtilitySolutionSpace.Iterator<V, U> newIter (String[] order, V[][] domains, V[] assignment, U skippedUtil) {
		
		if (order == null) 
			return this.newIter(this.variables, this.domains, assignment, skippedUtil);
		
		else if (domains == null) {
			assert order.length == this.variables.length : "The input order does not contain all of the space's variables";

			V[][] doms = (V[][]) Array.newInstance(this.domains.getClass().getComponentType(), this.domains.length);
			for (int i = order.length - 1; i >= 0; i--) {
				String var = order[i]; 

				// Look up this variable's domain
				V[] dom = null;
				for (int j = this.variables.length - 1; j >=0; j--) {
					if (var.equals(this.variables[j])) {
						dom = this.domains[j];
						break;
					}
				}
				assert dom != null : "The space does not contain the variable " + var;

				doms[i] = dom;
			}

			return this.newIter(order, doms, assignment, skippedUtil);
			
		} else {
			UtilitySolutionSpace.Iterator<V, U>[] iters = new UtilitySolutionSpace.Iterator [inputs.size()];
			final int nbrInputs = this.inputs.size();
			for (int i = 0; i < nbrInputs; i++) 
				iters[i] = this.inputs.get(i).iterator(order, domains, assignment);
			
			return new JoinOutputIterator<V, U> (iters, this.addition, this.infeasibleUtil, skippedUtil);
		}
	}
	
	/** @see BasicHypercube#getUtility(long) */
	@Override
	public U getUtility( long index ){
		return this.getUtility(this.getAssignment(index));
	}
	
	/** Computes the assignment of values to variables corresponding to a specific index
	 * @param index 	the index
	 * @return 			the corresponding variable assignment
	 */
	private V[] getAssignment (long index) {
		
		long step = this.nbrUtils;
		int nbrVars = this.variables.length;
		for (int i = 0; i < nbrVars; i++) {
			
			V[] dom = this.domains[i];
			step /= dom.length;
			int pos = (int) (index / step);
			this.assignment[i] = dom[pos];
			index -= pos * step;
		}
		
		return this.assignment;
	}
	
	/** @see BasicHypercube#getUtility(V[]) */
	@Override
	public U getUtility( V[] variables_values ) {
		
		U util = this.inputs.get(0).getUtility(this.variables, variables_values);
		if (this.addition && util.equals(this.infeasibleUtil)) 
			return this.infeasibleUtil;
		
		final int nbrInputs = this.inputs.size();
		
		if (this.addition) {
			AddableDelayed<U> delayed = util.addDelayed();
			
			for (int i = 1; i < nbrInputs; i++) {
				U util2 = this.inputs.get(i).getUtility(this.variables, variables_values);
				
				if (util2.equals(this.infeasibleUtil)) 
					return this.infeasibleUtil;
				
				delayed.addDelayed(util2);
			}
			
			util = delayed.resolve();

		} else // multiplication
			for (int i = 1; i < nbrInputs; i++) 
				util = util.multiply(this.inputs.get(i).getUtility(this.variables, variables_values));

		return util;
	}
	
	/** @see BasicHypercube#toString() */
	@Override
	public String toString () {
		
		StringBuilder builder = new StringBuilder( "JoinOutputHypercube:\n" );
		
		for( int i=0; i < variables.length; i++) {
			builder.append( variables[ i ] );
			builder.append( " : " );
			builder.append((domains == null || domains[i] == null ? null : Arrays.asList(domains[i])));
			builder.append( "\n" );
		}
		
		builder.append(this.inputs);
		builder.append("\n");
		
		return builder.toString();
	}

	/** @see Hypercube#changeVariablesOrder(java.lang.String[]) */
	@Override
	public Hypercube<V, U> changeVariablesOrder(String[] variablesOrder) {
		
		return new JoinOutputHypercube<V, U> (this.inputs, variablesOrder, 
				this.reorderDomains(variablesOrder, variablesOrder.length), this.addition, this.infeasibleUtil, this.nbrUtils);
	}

	/** @see Hypercube#isIncludedIn(UtilitySolutionSpace) */
	@Override
	public boolean isIncludedIn(UtilitySolutionSpace<V, U> space) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see Hypercube#iteratorBestFirst(boolean) */
	@Override
	public UtilitySolutionSpace.IteratorBestFirst<V, U> iteratorBestFirst(
			boolean maximize) {
		return this.resolve().iteratorBestFirst(maximize); /// @todo This is highly inefficient. 
	}

	/** @see Hypercube#joinMinNCCCs(UtilitySolutionSpace) */
	@Override
	public UtilitySolutionSpace<V, U> joinMinNCCCs(
			UtilitySolutionSpace<V, U> space) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#joinMinNCCCs(UtilitySolutionSpace[]) */
	@SuppressWarnings("unchecked")
	@Override
	public UtilitySolutionSpace<V, U> joinMinNCCCs(UtilitySolutionSpace<V, U>[] spaces) {
		
		UtilitySolutionSpace<V, U> callerSpace = this.inputs.get(0);
		
		UtilitySolutionSpace<V, U>[] otherSpaces = new UtilitySolutionSpace [this.inputs.size() + spaces.length - 1];
		final int nbrInputs = this.inputs.size();
		for (int i = 1; i < nbrInputs; i++) 
			otherSpaces[i - 1] = this.inputs.get(i);
		if (spaces.length > 0) 
			System.arraycopy(spaces, 0, otherSpaces, this.inputs.size() - 2, spaces.length);
		
		return callerSpace.joinMinNCCCs(otherSpaces);
	}
	
	/** @see Hypercube#blindProject(java.lang.String[], boolean) */
	@SuppressWarnings("unchecked")
	@Override
	public UtilitySolutionSpace<V, U> blindProject (String[] varsOut, boolean maximize) {
		
		if (varsOut.length > 1) { // project the variables one by one
			
			String[] remainingOut = new String [varsOut.length - 1];
			System.arraycopy(varsOut, 1, remainingOut, 0, varsOut.length - 1);
			
			return this.blindProject(varsOut[0], maximize).blindProject(remainingOut, maximize);
		}
		
		// Isolate the spaces that do not involve the input variables
		HashSet<String> varsOutSet = new HashSet<String> (Arrays.asList(varsOut));
		ArrayList< UtilitySolutionSpace<V, U> > toProject = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs); // contain outVars
		ArrayList< UtilitySolutionSpace<V, U> > toJoin = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs.size()); // do not contain outVars
		spaceLoop: for (java.util.Iterator< UtilitySolutionSpace<V, U> > iter = toProject.iterator(); iter.hasNext(); ) {
			UtilitySolutionSpace<V, U> space = iter.next();
			
			for (String var : space.getVariables()) 
				if (varsOutSet.contains(var)) 
					continue spaceLoop;
			
			// Does not contain any of the vars in varsOut
			iter.remove();
			toJoin.add(space);
		}
		
		if (toJoin.isEmpty()) // all input spaces are subject to projection
			return super.blindProject(varsOut, maximize);
		
		if (toProject.isEmpty()) // this join actually does not contain any of the input variables
			return this;
		
		// Perform the blind projection on the filtered spaces
		UtilitySolutionSpace<V, U> out = toProject.remove(0);
		if (! toProject.isEmpty()) {
			if (this.addition) 
				out = out.join(toProject.toArray(new UtilitySolutionSpace [toProject.size()]));
			else 
				out = out.multiply(toProject.toArray(new UtilitySolutionSpace [toProject.size()]));
		}
		out = out.blindProject(varsOut, maximize);
		
		// Join the remaining spaces together
		UtilitySolutionSpace<V, U> join = toJoin.remove(0);
		if (! toJoin.isEmpty()) {
			if (this.addition) 
				join = join.join(toJoin.toArray(new UtilitySolutionSpace [toJoin.size()]));
			else 
				join = join.multiply(toJoin.toArray(new UtilitySolutionSpace [toJoin.size()]));
		}
		
		// Join the remaining spaces back in
		return (this.addition ? join.join(out) : join.multiply(out));
	}

	/** @see Hypercube#projectAll(boolean, String[]) */
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
		UtilitySolutionSpace.SparseIterator<V, U> iter = this.sparseIter(order, newDoms);
		U util = null;
		while ( (util = iter.nextUtility(optUtil, !maximum)) != null) {
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

	/** @see Hypercube#project(int, boolean) */
	@Override
	public UtilitySolutionSpace.ProjOutput<V, U> project(
			int numberToProject, boolean maximum) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}
	
	/** @see Hypercube#project(java.lang.String[], boolean) */
	@SuppressWarnings("unchecked")
	@Override
	public ProjOutput< V, U > project( String[] varsOut, final boolean maximum ) {
		
		// Look for the spaces that contain at least one of the input variables
		final int myNbrSpaces = this.inputs.size();
		ArrayList< UtilitySolutionSpace<V, U> > spaces = new ArrayList< UtilitySolutionSpace<V, U> > (myNbrSpaces);
		ArrayList< UtilitySolutionSpace<V, U> > otherSpaces = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs);
		spaceLoop: for (UtilitySolutionSpace<V, U> space : this.inputs) {
			for (String varOut : varsOut) {
				if (space.getDomain(varOut) != null) {
					spaces.add(space);
					otherSpaces.remove(space);
					continue spaceLoop;
				}
			}
		}
		
		// Check if all my spaces are subject to projection
		final int nbrSpaces = spaces.size();
		if (nbrSpaces == myNbrSpaces) 
			return super.project(varsOut, maximum);
		
		// Join all spaces subject to projection
		UtilitySolutionSpace<V, U> join;
		switch (nbrSpaces) {
		case 0:
			return new ProjOutput<V, U> (this.clone(), new String [0], NullHypercube.NULL);
		case 1: 
			join = spaces.get(0);
			break;
		case 2: 
			join = spaces.get(0).join(spaces.get(1));
			break;
		default: 
			join = spaces.remove(0).join(spaces.toArray(new UtilitySolutionSpace [nbrSpaces - 1]));
		}
		spaces.clear();
		
		// Also join all other spaces that do not contain any projected out variable, but whose scope is a subset of the current join's
		HashSet<String> scope = new HashSet<String> (Arrays.asList(join.getVariables()));
		otherLoop: for (java.util.Iterator< UtilitySolutionSpace<V, U> > iter = otherSpaces.iterator(); iter.hasNext(); ) {
			UtilitySolutionSpace<V, U> other = iter.next();
			
			// Check whether all variables in this space are in the current join
			for (String var : other.getVariables()) 
				if (! scope.contains(var)) 
					continue otherLoop;
			
			iter.remove();
			join = join.join(other);
		}
		
		// Check if all my spaces are subject to projection
		if (otherSpaces.isEmpty()) 
			return super.project(varsOut, maximum); /// @todo Improvement: call .getCurrentUtility(bound) on the last space
		
		// Compute the projection
		ProjOutput< V, U > projOutput = join.project(varsOut, maximum);
		return new ProjOutput<V, U> (projOutput.space.join(otherSpaces.toArray(new UtilitySolutionSpace [otherSpaces.size()])), 
					varsOut, projOutput.assignments);
	}
	
	/** @see Hypercube#projExpectMonotone(java.lang.String, java.util.Map, boolean) */
	@SuppressWarnings("unchecked")
	@Override
	public ProjOutput<V, U> projExpectMonotone(final String varOut, 
			Map< String, UtilitySolutionSpace<V, U> > distributions, final boolean maximum) {
		
		if (! this.addition)
			return super.projExpectMonotone(varOut, distributions, maximum); /// @todo Performance improvements

		/// @todo Check whether all spaces involve varOut
		
		// Variable iteration order on the expected input spaces: 1) varsKept; 2) varOut
		ArrayList<String> varsKept = new ArrayList<String> (Arrays.asList(this.getVariables()));
		varsKept.remove(varOut);
		varsKept.removeAll(distributions.keySet());
		String[] order = varsKept.toArray(new String [varsKept.size() + 1]);
		order[varsKept.size()] = varOut;
		V[][] domsIter = (V[][]) Array.newInstance(this.getDomain(0).getClass(), order.length);
		V[][] domsKept = (V[][]) Array.newInstance(this.getDomain(0).getClass(), order.length - 1);
		int nbrUtilsKept = 1;
		for (int i = order.length - 2; i >= 0; i--) // varsKept
			nbrUtilsKept *= (domsKept[i] = domsIter[i] = this.getDomain(order[i])).length;
		final int nbrUtilsOut = (domsIter[order.length - 1] = this.getDomain(order[order.length - 1])).length; // varOut
		
		// Construct iterators over the expected input spaces
		final int nbrIters = this.inputs.size();
		ArrayList< UtilitySolutionSpace.Iterator<V, U> > iters = new ArrayList< UtilitySolutionSpace.Iterator<V, U> > (nbrIters);
		for (UtilitySolutionSpace<V, U> space : this.inputs) 
			iters.add(space.expectation(distributions).iterator(order, domsIter));
		
		// Iterate
		U[] optUtils = (U[]) Array.newInstance(this.getClassOfU(), nbrUtilsKept);
		U optUtil;
		ArrayList<V>[] optSols = new ArrayList [nbrUtilsKept];
		V optSol;
		int i = 0;
		AddableDelayed<U> sumDelayed;
		U sum;
		int j, k;
		for (UtilitySolutionSpace.Iterator<V, U> firstIter = iters.get(0); firstIter.hasNext(); i++) {
			
			// Look up the utility for the first value of varOut
			optSol = firstIter.nextSolution()[order.length - 1]; // value of varOut
			sumDelayed = firstIter.getCurrentUtility().addDelayed();
			for (j = 1; ! sumDelayed.isInfinite() && j < nbrIters; j++) 
				sumDelayed.addDelayed(iters.get(j).nextUtility());
			while (j < nbrIters) // the sum has become infinite; finish stepping the remaining iterators
				iters.get(j++).nextSolution();
			optUtil = sumDelayed.resolve();
			
			// Iterate over the remaining possible values for varOut
			for (k = 1; k < nbrUtilsOut; k++) {
				
				// Loop over the iterators
				sum = this.infeasibleUtil.getZero();
				for (j = -1; (maximum ? sum.compareTo(optUtil) > 0 : sum.compareTo(optUtil) < 0) && ++j < nbrIters; ) {
					iters.get(j).nextSolution();
					sum = sum.add(iters.get(j).getCurrentUtility(optUtil.subtract(sum), ! maximum));
				}
				if (j++ < nbrIters) // the sum has become sub-optimal; finish stepping the remaining iterators
					while (j < nbrIters) 
						iters.get(j++).nextSolution();
				else { // we found a better assignment to varOut
					optSol = firstIter.getCurrentSolution()[order.length - 1];
					optUtil = sum;
				}
			}
			
			optUtils[i] = optUtil;
			(optSols[i] = new ArrayList<V> ()).add(optSol); // the value of varOut
		}
		
		if (nbrUtilsKept == 1) 
			return new ProjOutput<V, U> (new ScalarHypercube<V, U> (optUtils[0], this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass()), 
					new String[] { varOut }, 
					new ScalarBasicHypercube< V, ArrayList<V> > (optSols[0], null));
		else {
			String[] varsKeptArray = varsKept.toArray(new String [varsKept.size()]);
			return new ProjOutput<V, U> (new Hypercube<V, U> (varsKeptArray, domsKept, optUtils, this.infeasibleUtil), 
					new String[] { varOut }, 
					new BasicHypercube< V, ArrayList<V> > (varsKeptArray, domsKept, optSols, null));
		}
	}
	
	/** @see Hypercube#sample(int) */
	@Override
	public Map<V, Double> sample(int nbrSamples) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicHypercube#slice(java.lang.String[], V[][], V[][], long, java.lang.Class) */
	@Override
	protected BasicHypercube<V, U> slice (String[] remainingVars, V[][] remainingDoms, V[][] iterDoms, long nbrRemainingUtils, Class<?> domClass) {
		
		ArrayList< UtilitySolutionSpace<V, U> > slicedSpaces = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs.size());
		for (UtilitySolutionSpace<V, U> space : this.inputs) 
			slicedSpaces.add(space.slice(this.variables, iterDoms));

		return new JoinOutputHypercube<V, U> (slicedSpaces, remainingVars, remainingDoms, this.addition, this.infeasibleUtil, nbrRemainingUtils);
	}

	/** @see Hypercube#slice(java.lang.String, V[]) */
	@Override
	public Hypercube<V, U> slice(String var, V[] subDomain) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#slice(V[]) */
	@Override
	public Hypercube<V, U> slice(V[] variablesValues) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#split(Addable, boolean) */
	@Override
	public Hypercube<V, U> split(U threshold, boolean maximum) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#augment(V[], java.io.Serializable) */
	@Override
	public void augment(V[] variablesValues, U utilityValue) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see Hypercube#getDefaultUtility() */
	@Override
	public U getDefaultUtility() {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#isIncludedIn(BasicUtilitySolutionSpace) */
	@Override
	public boolean isIncludedIn(BasicUtilitySolutionSpace<V, U> space) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see Hypercube#prettyPrint(java.io.Serializable) */
	@Override
	public String prettyPrint(U ignoredUtil) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#setDefaultUtility(java.io.Serializable) */
	@Override
	public void setDefaultUtility(U utility) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see Hypercube#setUtility(V[], java.io.Serializable) */
	@Override
	public boolean setUtility(V[] variablesValues, U utility) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see Hypercube#setUtility(long, java.io.Serializable) */
	@Override
	public void setUtility(long index, U utility) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see Hypercube#augment(V[]) */
	@Override
	public void augment(V[] variablesValues) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see Hypercube#getDomain(java.lang.String, int) */
	@Override
	public V[] getDomain(String variable, int index) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#getNumberOfSolutions() */
	@Override
	public long getNumberOfSolutions() {
		return this.nbrUtils;
	}

	/** @see Hypercube#join(SolutionSpace, java.lang.String[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space, String[] totalVariables) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#join(SolutionSpace) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V> space) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#join(SolutionSpace[], java.lang.String[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces,
			String[] totalVariablesOrder) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#join(SolutionSpace[]) */
	@Override
	public SolutionSpace<V> join(SolutionSpace<V>[] spaces) {
		
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#renameVariable(java.lang.String, java.lang.String) */
	@Override
	public void renameVariable(String oldName, String newName) {
		super.renameVariable(oldName, newName);
		
		// Rename in each underlying space
		for (UtilitySolutionSpace<V, U> space : this.inputs) 
			space.renameVariable(oldName, newName);
	}
	
	/** @see BasicHypercube#renameAllVars(String[]) */
	@Override
	public BasicHypercube<V, U> renameAllVars(String[] newVarNames) {

		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see Hypercube#setDomain(java.lang.String, V[]) */
	@Override
	public void setDomain(String var, V[] dom) {

		int index = this.getIndex(var);
		if (index >= 0) {
			this.domains[index] = dom;
			
			for (UtilitySolutionSpace<V, U> space : this.inputs) 
				space.setDomain(var, dom);
		}
	}

	
	/** @see Hypercube#expectation(java.util.Map) */
	@SuppressWarnings("unchecked")
	@Override
	public UtilitySolutionSpace<V, U> expectation(Map< String, UtilitySolutionSpace<V, U> > distributions) {

		// Distributed the expectation over the sum
		UtilitySolutionSpace<V, U> first = this.inputs.get(0).expectation(distributions);
		
		UtilitySolutionSpace<V, U>[] others = new UtilitySolutionSpace [this.inputs.size() - 1];
		for (int i = this.inputs.size() - 1; i > 0; i--) 
			others[i - 1] = this.inputs.get(i).expectation(distributions);
		
		return (this.addition ? first.join(others) : first.multiply(others));
	}
	
	/** @see Hypercube#compose(java.lang.String[], BasicUtilitySolutionSpace) */
	@SuppressWarnings("unchecked")
	@Override
	public UtilitySolutionSpace<V, U> compose(final String[] varsOut, final BasicUtilitySolutionSpace< V, ArrayList<V> > subst) {
		
		// Isolate the spaces that do not involve the input variables
		HashSet<String> varsOutSet = new HashSet<String> (Arrays.asList(varsOut));
		ArrayList< UtilitySolutionSpace<V, U> > toCompose = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs); // contain outVars
		ArrayList< UtilitySolutionSpace<V, U> > toJoin = new ArrayList< UtilitySolutionSpace<V, U> > (this.inputs.size()); // do not contain outVars
		spaceLoop: for (java.util.Iterator< UtilitySolutionSpace<V, U> > iter = toCompose.iterator(); iter.hasNext(); ) {
			UtilitySolutionSpace<V, U> space = iter.next();
			
			for (String var : space.getVariables()) 
				if (varsOutSet.contains(var)) 
					continue spaceLoop;
			
			// Does not contain any of the vars in varsOut
			iter.remove();
			toJoin.add(space);
		}
		
		if (toJoin.isEmpty()) // all input spaces are subject to composition
			return super.compose(varsOut, subst);
		
		if (toCompose.isEmpty()) // this join actually does not contain any of the input variables
			return this;
		
		// Perform the composition on the filtered spaces
		UtilitySolutionSpace<V, U> out = toCompose.remove(0);
		if (! toCompose.isEmpty()) {
			if (this.addition) 
				out = out.join(toCompose.toArray(new UtilitySolutionSpace [toCompose.size()]));
			else 
				out = out.multiply(toCompose.toArray(new UtilitySolutionSpace [toCompose.size()]));
		}
		out = out.compose(varsOut, subst);
		
		// Join the remaining spaces back in
		return (this.addition ? out.join(toJoin.toArray(new UtilitySolutionSpace [toJoin.size()])) : 
								out.multiply(toJoin.toArray(new UtilitySolutionSpace [toJoin.size()])));
	}
	
}
