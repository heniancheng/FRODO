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
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpaceLimited;
import frodo2.solutionSpaces.hypercube.Hypercube.NullHypercube;

/** A Hypercube whose utilities are AddableLimited's
 * @author Thomas Leaute, Eric Zbinden
 * @param <V> 	the type used for variable values
 * @param <U> 	the type used for Addable utilities
 * @param <UL> 	the type used for AddableLimited utilities
 */
public class HypercubeLimited < V extends Addable<V>, U extends Addable<U>, UL extends AddableLimited<U, UL> > 
extends BasicHypercube<V, UL> implements UtilitySolutionSpaceLimited<V, U, UL> {

	/** Empty constructor */
	public HypercubeLimited () { }

	/** Constructor
	 * @param variablesOrder 	the array containing the variables names ordered according to their order in the hypercube
	 * @param variablesDomains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utilityValues 	the utility values contained in a one-dimensional array. there should be a utility value for each 
	 * 							possible combination of values that the variables may take.
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 */
	public HypercubeLimited(String[] variablesOrder, V[][] variablesDomains, UL[] utilityValues, UL infeasibleUtil) {
		super (variablesOrder, variablesDomains, utilityValues, infeasibleUtil);
	}
	
	/** Constructor
	 * @param variablesOrder 	the array containing the variables names ordered according to their order in the hypercube
	 * @param variablesDomains 	the domains of the variables contained in the variables_order array and ordered in the same order.
	 * @param utilityValues 	the utility values contained in a one-dimensional array. there should be a utility value for each 
	 * 							possible combination of values that the variables may take.
	 * @param infeasibleUtil 	-INF if we are maximizing, +INF if we are minimizing
	 * @param problem 			the problem to be notified of constraint checks
	 */
	public HypercubeLimited(String[] variablesOrder, V[][] variablesDomains, UL[] utilityValues, UL infeasibleUtil, ProblemInterface<V, U> problem) {
		super (variablesOrder, variablesDomains, utilityValues, infeasibleUtil, problem);
	}
	
	/** @see BasicHypercube#resolve() */
	@Override
	public HypercubeLimited<V, U, UL> resolve() {
		return this;
	}

	/** @see BasicHypercube#writeUtilities(java.io.ObjectOutput) */
	@Override
	protected void writeUtilities (ObjectOutput out) throws IOException {
		
		final boolean externalize = this.infeasibleUtil.externalize();
		
		out.writeInt(this.number_of_utility_values); // number of utilities
		out.writeObject(this.getClassOfU()); // class of U
		for (int i = 0; i < this.number_of_utility_values; i++) { // each utility
			if (externalize) 
				this.values[i].writeExternal(out);
			else 
				out.writeObject(this.values[i]);
		}
		
		this.incrNCCCs(this.number_of_utility_values);
	}

	/** @see BasicHypercube#readUtilities(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	protected void readUtilities (ObjectInput in) throws ClassNotFoundException, IOException {
		
		final boolean externalize = this.infeasibleUtil.externalize();

		this.number_of_utility_values = in.readInt();
		Class<UL> classOfUL = (Class<UL>) in.readObject();
		this.values = (UL[]) Array.newInstance(classOfUL, number_of_utility_values);
		for (int i = 0; i < this.number_of_utility_values; i++) {
			if (externalize) {
				UL util = null;
				try {
					util = classOfUL.newInstance();
				} catch (InstantiationException e) { // should never happen
					e.printStackTrace();
				} catch (IllegalAccessException e) { // should never happen
					e.printStackTrace();
				}
				util.readExternal(in);
				this.values[i] = (UL) util.readResolve();
			} else 
				this.values[i] = (UL) in.readObject();
		}
	}

	/** @see BasicHypercube#clone() */
	@SuppressWarnings("unchecked")
	@Override
	public HypercubeLimited<V, U, UL> clone () {
		return (HypercubeLimited<V, U, UL>) super.clone();
	}
	
	/** @see UtilitySolutionSpaceLimited#joinMinNCCCs(UtilitySolutionSpace) */
	public UtilitySolutionSpaceLimited<V, U, UL> joinMinNCCCs (UtilitySolutionSpace< V, U > space) {
		return this.join(space, true);
	}
	
	/** @see UtilitySolutionSpaceLimited#join(UtilitySolutionSpace) */
	public UtilitySolutionSpaceLimited<V, U, UL> join (UtilitySolutionSpace<V, U> space) {
		return this.join(space, false);
	}

	/** The join operation
	 * @param space 	input space
	 * @param minNCCCs 	whether to optimize runtime or NCCC count
	 * @return 			the join of the two spaces
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpaceLimited<V, U, UL> join (UtilitySolutionSpace<V, U> space, final boolean minNCCCs) {
		
		if(space == NullHypercube.NULL)
			return NullHypercube.NULL;
		
		String[] outputVars = Hypercube.union(this.variables, space.getVariables());
		
		Class<?> domClass = this.domains.getClass().getComponentType();
		int nbrOutputVars = outputVars.length;
		V[][] outputDomains = (V[][]) Array.newInstance(domClass, nbrOutputVars);
		int nbrOutputUtils = 1;
		for (int i = 0; i < nbrOutputVars; i++) {
			String var = outputVars[i];
			
			// Look up the domain in the two input hypercubes
			V[] dom = this.getDomain(var);
			V[] dom2 = space.getDomain(var);
			
			// Compute the intersection if necessary
			if (dom == null) {
				dom = dom2;
			} else if (dom2 != null) { // the variable is in both hypercubes
				dom = intersection(dom, dom2);
				if( dom == null ) return NullHypercube.NULL;
			}
			
			outputDomains[i] = dom.clone();

			assert Math.log((double) nbrOutputUtils) + Math.log((double) dom.length) < Math.log(Integer.MAX_VALUE) : 
				"Size of utility array too big for an int";
			nbrOutputUtils *= dom.length;
		}
		
		// Instantiate the output, with an empty utility array
		UL[] outputUtils = (UL[]) Array.newInstance(this.getClassOfU(), nbrOutputUtils);
		UtilitySolutionSpaceLimited<V, U, UL> out = this.newInstance( (String[])outputVars.clone(), outputDomains, outputUtils, this.infeasibleUtil );
		
		if (! minNCCCs) {
			
			//@todo improve memory size of HypercubeLimited like in Hypercube
			/*
			 *	// If the explicit output would take more space than the sum of the inputs, return a JoinOutputHypercube instead
			 *if (this.approxSize() + space.approxSize() < nbrOutputUtils) 
			 *	return new JoinOutputHypercube<V, U> (this, utilitySpace, outputVars, outputDomains, addition, this.infeasibleUtil, nbrOutputUtils);
			 */
		
			// Iterate over all possible values of the output variables
			Iterator<V, UL> iter1 = this.iterator(outputVars, outputDomains);
			Iterator<V, U> iter2 = space.iterator(outputVars, outputDomains);

			for (int i = 0; iter1.hasNext(); i++)
				outputUtils[i] = iter1.nextUtility().add(iter2.nextUtility());

		} else { // minNCCCs
			
			// Build the list of spaces to join with the caller space
			List< UtilitySolutionSpace<V, U> > inputs = null;
			if (space instanceof JoinOutputHypercube) 
				inputs = ((JoinOutputHypercube<V, U>) space).inputs;
			else 
				inputs = Arrays.asList(space);
			
			// Initialize the output utilities with the caller hypercube's utilities
			Iterator<V, UL> outIter = out.iterator(this.variables, this.domains);
			Iterator<V, UL> myIter = this.iterator(this.variables, outIter.getDomains());
			long utilFactor = outIter.getNbrSolutions() / myIter.getNbrSolutions();
			while (myIter.hasNext()) {
				UL util = myIter.nextUtility();
				for (long j = 0; j < utilFactor; j++) {
					outIter.nextSolution();
					outIter.setCurrentUtility(util);
				}
			}
			
			// Add to the output utilities the utilities of each input space
			for (UtilitySolutionSpace<V, U> input : inputs) {
				String[] vars = input.getVariables();
				outIter = out.iterator(vars, input.getDomains());
				Iterator<V, U> iter = input.iterator(vars, outIter.getDomains());
				utilFactor = outIter.getNbrSolutions() / iter.getNbrSolutions();
				while (iter.hasNext()) {
					final U util = iter.nextUtility();
					for (long j = 0; j < utilFactor; j++) 
						outIter.setCurrentUtility(outIter.nextUtility().add(util));
				}
			}
		}

		return out;
	}

	/** @see UtilitySolutionSpaceLimited#blindProject(String, boolean) */
	public UtilitySolutionSpaceLimited<V, U, UL> blindProject(String varOut, boolean maximize) {
		return this.blindProject(new String[] {varOut}, maximize);
	}

	/** @see UtilitySolutionSpaceLimited#blindProject(String[], boolean) */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpaceLimited<V, U, UL> blindProject(String[] varsOut, final boolean maximize) {
		
		// Number of variables in this hypercube
		int myNbrVars = variables.length;
		
		// Only project variables that are actually contained in this space
		HashSet<String> varsOutSet = new HashSet<String> (varsOut.length);
		for (String varOut : varsOut) 
			if (this.getDomain(varOut) != null) 
				varsOutSet.add(varOut);
		int nbrVarsOut = varsOutSet.size();
		if( nbrVarsOut == 0 )
			return this.clone();
		if (nbrVarsOut < varsOut.length) 
			varsOut = varsOutSet.toArray(varsOut);
		
		//if all variables must be projected out
		if( nbrVarsOut == myNbrVars ) 
			return this.scalarHypercube(this.blindProjectAll(maximize));
		
		// Number of variables in the output
		int nbrVarsKept = myNbrVars - nbrVarsOut;

		// Generate a variable order for the iteration that puts last the variables to be projected out
		String[] varOrder = new String [myNbrVars];
		Class<?> domClass = this.domains.getClass().getComponentType();
		V[][] domsKept = (V[][]) Array.newInstance(domClass, nbrVarsKept);
		long nbrUtilsKept = 1;
		int i = 0;
		for (int j = 0 ; j < myNbrVars; j++) {
			String var = this.variables[j];
			
			if (! varsOutSet.contains(var)) {
				V[] dom = this.domains[j];
				domsKept[i] = dom;
				varOrder[i++] = var;
				assert Math.log(nbrUtilsKept) + Math.log(dom.length) < Math.log(Long.MAX_VALUE) : "Too many solutions to fit in a long";
				nbrUtilsKept *= dom.length;
			}
		}
		System.arraycopy(varsOut, 0, varOrder, i, nbrVarsOut);
		
		// The number of possible assignments to the variables being projected out
		long nbrUtilsOut = this.getNumberOfSolutions() / nbrUtilsKept;
		
		// Build the output array of kept variables
		String[] varsKept = new String [nbrVarsKept];
		System.arraycopy(varOrder, 0, varsKept, 0, nbrVarsKept);

		// Initialize the output array of utilities
		assert nbrUtilsKept < Integer.MAX_VALUE : "A Hypercube can only contain up to 2^31-1 solutions, but log_2(" + nbrUtilsKept + ") = " + Math.log(nbrUtilsKept) / Math.log(2);
		UL[] optUtils = (UL[]) Array.newInstance(this.getClassOfU(), (int) nbrUtilsKept);
		
		// Iterate over the solutions in the space
		Iterator<V, UL> iter = this.iterator(varOrder);
		for (i = 0; iter.hasNext(); i++) {
			
			// Look up the best assignment to the variables projected out for the current assignment to the variables kept
			// Iterate over all possible assignments to the variables projected out
			UL optUtil = iter.nextUtility();
			for (long j = 1; j < nbrUtilsOut; j++) {
				if (maximize) 
					optUtil = optUtil.max(iter.nextUtility());
				else 
					optUtil = optUtil.min(iter.nextUtility());
			}
			
			optUtils[i] = optUtil;
		}
		
		return this.newInstance(varsKept, domsKept, optUtils, this.infeasibleUtil);
	}

	/** @see UtilitySolutionSpaceLimited#blindProjectAll(boolean) */
	public UL blindProjectAll(final boolean maximize) {
		
		// Compute the optimum utility value
		SparseIterator<V, UL> iter = this.iterator();
		UL optimum = iter.nextUtility();
		UL util = null;
		while ( (util = iter.nextUtility()) != null) {
			if (maximize) 
				optimum = optimum.max(util);
			else 
				optimum = optimum.min(util);
		}
		
		return optimum;
	}

	/** @see UtilitySolutionSpaceLimited#min(String) */
	public UtilitySolutionSpaceLimited<V, U, UL> min(String variable) {
		return this.blindProject(variable, false);
	}
	
	/** @see UtilitySolutionSpaceLimited#max(String) */
	public UtilitySolutionSpaceLimited<V, U, UL> max (String variable) {
		return this.blindProject(variable, true);
	}
	
	/** @see BasicHypercube#newInstance(String[], V[][], Serializable[], Serializable) */
	@Override
	protected HypercubeLimited<V, U, UL> newInstance(String[] new_variables, V[][] new_domains, UL[] new_values, UL infeasibleUtil) {
		return new HypercubeLimited<V, U, UL> ( new_variables, new_domains, new_values, infeasibleUtil );
	}

	/** @see BasicHypercube#scalarHypercube(Serializable) */
	@SuppressWarnings("unchecked")
	@Override
	protected HypercubeLimited<V, U, UL> scalarHypercube(UL utility) {
		return new ScalarHypercubeLimited<V, U, UL> (utility, this.infeasibleUtil, (Class<? extends V[]>) this.assignment.getClass());
	}

	/** @see BasicHypercube#slice(String, Addable) */
	@SuppressWarnings("unchecked")
	@Override
	public HypercubeLimited<V, U, UL> slice (String var, V val) {
		return (HypercubeLimited<V, U, UL>) super.slice(var, val);
	}
	
}
