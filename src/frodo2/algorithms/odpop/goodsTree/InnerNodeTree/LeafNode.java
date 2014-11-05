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

package frodo2.algorithms.odpop.goodsTree.InnerNodeTree;

import frodo2.algorithms.odpop.Good;
import frodo2.solutionSpaces.Addable;

/**
 * The leaf node of a tree
 * @author brammert
 * @param <U> the type used to represent a utility value
 */
public class LeafNode<U extends Addable<U>> extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode<U> {

	/** True if this node is a dummy node, and false otherwise */
	public boolean real;

	/**
	 * Empty constructor
	 */
	public LeafNode() {
		super();
		real = true;
	}

	/**
	 * A constructor
	 * @param counter 	the number of assignments to be received
	 * @param powersOf2 precomputed powers of 2
	 */
	public LeafNode(int counter, int[] powersOf2) {
		super(counter, powersOf2);
		real = true;
	}

	/**
	 * A constructor
	 * @param counter 	the number of assignments to be received
	 * @param util				the already receive utility
	 * @param powersOf2 precomputed powers of 2
	 */
	public LeafNode(int counter, U util, int[] powersOf2) {
		super(counter, util, powersOf2);
		real = true;
	}

	/**
	 * Method to calculate the current upper bound. Note that the UB
	 * field is not updated. That should only happen when this method
	 * is called from a leaf node!
	 * 
	 * @param upperBoundsSum	A list of all possible sums of the upper bounds
	 * @param maximize 			\c true if we are maximizing utility
	 * @return 				the current upper bound
	 */
	@Override
	public U calculateUB(U[] upperBoundsSum, final boolean maximize) {
		U bound = util;
		if(counter > 0) {
			bound = bound.add(upperBoundsSum[ubSum]);
		} 

		if(real)
			upToDate = maximize ? util.compareTo(bound) <= 0 : util.compareTo(bound) >= 0;
		return bound;
	}
	
	/**
	 * Method to calculate the current upper bound. Note that the UB
	 * field is not updated. That should only happen when this method
	 * is called from a leaf node!
	 * 
	 * @param upperBoundsSum	A list of all possible sums of the upper bounds
	 * @return 				the current upper bound
	 */
	@Override
	public U calculateUBTest(U[] upperBoundsSum) {
		U bound = util;
		if(counter > 0) {
			bound = bound.add(upperBoundsSum[ubSum]);
		} 

		return bound;
	}

	/**
	 * Update both the utility and the UB information in this leaf node
	 * @author Brammert Ottens, 3 July 2009
	 * 
	 * @param g					the good currently being processed
	 * @param utilityDelta		the difference with the previous utility receive from \c sender for this node
	 * @param sender			the sender of the good
	 * @param upperBoundSums	precomputed sums of upperbounds 
	 * @param powersOf2 		precomputed powers of 2
	 * @param compatibleAssignmentReceived = goodsReceived.get(sender).containsKey(currentPath.getPartialAssignment(childrenVariables[sender], this.separatorSizePerChild[sender]))
	 * @param maximize 			\c true if we are maximizing utility
	 */
	@Override
	public void updateLeafWithUB(Good<?, U> g, U utilityDelta, int sender, U[] upperBoundSums, int[] powersOf2, boolean compatibleAssignmentReceived, final boolean maximize) {
		U utility = g.getUtility();
		if(real) {
			util = util.add(utility);
			counter--;
			assert counter >= 0;
			updateUB[sender] = false;
		} else if(compatibleAssignmentReceived) {
			util = util.add(utility);
			counter--;
			assert counter >= 0;
			updateUB[sender] = false;
		}

		// @todo this should always be calculated with a simple subtraction and addition
		ubSum = fromBooleanArrayToInt(updateUB, powersOf2);
		UB = calculateUB(upperBoundSums, maximize);
	}

	/**
	 * @param maxUtil the maximal utility found so far
	 * @param maximize 			\c true if we are maximizing utility
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode#utilCandidate(frodo2.solutionSpaces.Addable, boolean)
	 */
	@Override
	public boolean utilCandidate(U maxUtil, final boolean maximize) {
		return this.real && this.upToDate && (maxUtil == null || (maximize && maxUtil.compareTo(util) < 0) || (!maximize && maxUtil.compareTo(util) > 0));
	}

	/**
	 * Create a new instance of the leaf node 
	 * 
	 * @author Brammert Ottens, 1 july 2009
	 * @param <L> the class used for leaf nodes
	 * @param confirmedCounter	the number of children of this leaf node
	 * @param powersOf2 precomputed powers of 2
	 * @return	a new leaf node
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U>> L newInstance(int confirmedCounter, int[] powersOf2) {
		return (L)new LeafNode<U>(confirmedCounter, powersOf2);
	}

	/**
	 * Create a new instance of the leaf node 
	 * 
	 * @author Brammert Ottens, 1 july 2009
	 * @param <L> the class used for leaf nodes
	 * @param confirmedCounter	the number of children of this leaf node
	 * @param util the utility of the local problem
	 * @param powersOf2 precomputed powers of 2
	 * @return	a new leaf node
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U>> L newInstance(int confirmedCounter, U util, int[] powersOf2) {
		return (L)new LeafNode<U>(confirmedCounter, util, powersOf2);
	}

}
