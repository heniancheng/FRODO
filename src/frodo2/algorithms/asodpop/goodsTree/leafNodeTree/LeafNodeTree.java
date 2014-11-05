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

package frodo2.algorithms.asodpop.goodsTree.leafNodeTree;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;

import frodo2.algorithms.asodpop.Good;
import frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNode;
import frodo2.algorithms.odpop.goodsTree.leafNodeTree.Node;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * @author Brammert Ottens, 9 nov 2009
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @param <L> type used for the leaf node
 */
public class LeafNodeTree < Val extends Addable<Val>, U extends Addable<U>, L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U> >
extends
frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNodeTree<Val, U, L> {

	/** Used for serialisation */
	private static final long serialVersionUID = -6041904137491347535L;
	
	/**
	 * A constructor
	 * @warning we assume that the agent's own variable is put in the end of variables_order
	 * @param ownVariable 		The variable ID
	 * @param ownVariableDomain The domain of \c ownVariable
	 * @param space				The hypercube representing the local problem
	 * @param zero 				The zero utility
	 * @param infeasibleUtil 	The infeasible utility
	 * @param maximize 			when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats		\c true when statistics should be collected, and \c false otherwise
	 */
	public LeafNodeTree( String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, U zero, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, space,  zero, infeasibleUtil, maximize, collectStats);
	}

	/**
	 * A constructor
	 * @warning we assume that the agents own variable is put in the end of variables_order
	 * @param ownVariable 		The variable ID
	 * @param ownVariableDomain The domain of \c ownVariable
	 * @param spaces			The hypercubes representing the local problem
	 * @param zero 				The zero utility
	 * @param infeasibleUtil 	The infeasible utility
	 * @param maximize 			when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats		\c true when statistics should be collected, and \c false otherwise
	 */
	public LeafNodeTree( String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, U zero, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, spaces,  zero, infeasibleUtil, maximize, collectStats);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNodeTree#getAmax()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Good<Val, U> getAmax() {

		Val[] assignment = null;
		U utility = null;
		
		// get the next aMax that is not compatible with an already sent assignment
		while(localProblemIterator.hasNext()) {
			assignment = this.localProblemIterator.nextSolution();
			utility = localProblemIterator.getCurrentUtility();
			if(singleton || (utility != null && !this.pathExists(assignment, depthFinalVariable)))
				break;
			else {
				assignment = null;
				utility = null;
			}
		}
		
		if(assignment == null) {
			hasMore = false;
			return null;
		}

		if(!singleton)
			// store the aMax in the tree
			addToTree(assignment, 0, root);
		else
			localAssignment = assignment;

		Val[] values = (Val[]) Array.newInstance(assignment.getClass().getComponentType(), depthFinalVariable);
		System.arraycopy(assignment, 0, values, 0, depthFinalVariable);
		
 		return new Good<Val, U>(separatorVariables, values, utility, true);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNodeTree#getOwnAssignment(java.util.HashMap, frodo2.algorithms.odpop.goodsTree.leafNodeTree.Node, int)
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void getOwnAssignment(HashMap<String, Val> assignment, Node currentNode, int depth) {
		boolean known = true;
		currentNode = root;
		String varID = null;
		Val ass = null;
		Val[] assignmentArray = (Val[])Array.newInstance(this.domainElementClass, numberOfVariables);
		for(depth = 0; depth < this.depthFinalVariable - 1; depth++) {
			varID = depthToVariable[depth];
			ass = assignment.get(varID);
			if(ass == null)
				known = false;
			else
				assignmentArray[depth] = ass;
			
			if(known) {
				currentNode = currentNode.getChild(valuePointers.get(varID).get(ass));
				if(currentNode == null)
					known = false;
			}
		}
		varID = depthToVariable[depth];
		ass = assignment.get(varID);
		assignmentArray[depth] = ass;
		
		
		if(known && ass != null) {
			LeafNode<Val> leaf = (LeafNode<Val>)currentNode.getChild(valuePointers.get(varID).get(ass));
			if(leaf != null) {
				assignment.put(this.ownVariable, leaf.getValue());
				assert leaf.getValue() != null;
				return;
			}
		}
		
//		assert !isConfirmed;

		int optimalAssignmentIndex = -1;
		
		optimalAssignmentIndex = (int)(Math.random()*this.ownVarDomain.length);

		assignment.put(ownVariable, this.ownVarDomain[optimalAssignmentIndex]);
		assert this.ownVarDomain[optimalAssignmentIndex] != null;
	}
	
	/**
	 * Method used to fill the ownOptions array, that contains the utility for every decision this variable
	 * can make
	 *  
	 * @author Brammert Ottens, 27 jan 2010
	 * @param ownOptions		the utility array to be filled
	 * @param currentVariable	the current variable
	 * @param localVariables	the variables that occur in the local problem
	 * @param assignment		the assignment to the local variables
	 */
	@SuppressWarnings("unused") /// @todo This method is actually used, but the compiler incorrectly complains it isn't
	private void fillOwnOptions(U[] ownOptions, int currentVariable, String[] localVariables, Val[] assignment) {
		int nextVariable = currentVariable + 1;
		if(currentVariable == depthFinalVariable) {
			int length = this.ownVarDomain.length;
			for(int i = 0; i < length; i++) {
				assignment[currentVariable] = ownVarDomain[i];
				U util = localProblem.getUtility(localVariables, assignment);
				U currentUtil = ownOptions[i];
				if(currentUtil == null || greaterThan(currentUtil, util))
					ownOptions[i] = util;
			}
			assignment[currentVariable] = null;
		} else if(assignment[currentVariable] == null) {
			Val[] domain = localProblem.getDomain(depthToVariable[currentVariable]);
			int domainSize = domain.length;
			for(int i = 0; i < domainSize; i++) {
				assignment[currentVariable] = domain[i];
				fillOwnOptions(ownOptions, nextVariable, localVariables, assignment);
			}
			assignment[currentVariable] = null;
		} else {
			fillOwnOptions(ownOptions, nextVariable, localVariables, assignment);
		}
	}
}
