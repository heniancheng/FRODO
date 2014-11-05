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

package frodo2.algorithms.asodpop.goodsTree.innerNodeTree;

import frodo2.algorithms.asodpop.Good;
import frodo2.solutionSpaces.Addable;

/**
 * The leaf node of a tree
 * @author brammert
 * @param <U> the type used to represent a utility value
 */

public class LeafNode < U extends Addable<U>> extends
		frodo2.algorithms.odpop.goodsTree.InnerNodeTree.LeafNode<U> {
	
	/** The amount of confirmed utility stored in this leaf */
	public U confirmedUtil;
	
	/**
	 * Empty constructor
	 */
	public LeafNode() {
		super();
	}
	
	/**
	 * A constructor
	 * @param confirmedCounter 	the number of confirmed assignments to be received
	 * @param powersOf2			precomputed powers of 2
	 */
	public LeafNode(int confirmedCounter, int[] powersOf2) {
		super(confirmedCounter, powersOf2);
	}

	/**
	 * A constructor
	 * @param confirmedCounter 	the number of confirmed assignments to be received
	 * @param util				the already receive utility
	 * @param powersOf2			precomputed powers of 2
	 */
	public LeafNode(int confirmedCounter, U util, int[] powersOf2) {
		super(confirmedCounter, util, powersOf2);
		this.confirmedUtil = util;
	}
	
	/**
	 * Method to calculate the current upper bound. Note that the UB
	 * field is not updated. That should only happen when this method
	 * is called from a leaf node!
	 * 
	 * @param upperBoundsSum	A list of all possible sums of the upper bounds
	 * @param maximize @todo
	 * @return 				the current upper bound
	 */
	@Override
	public U calculateUB(U[] upperBoundsSum, boolean maximize) {
		U bound = confirmedUtil;
		
		if(counter > 0) {
			bound = bound.add(upperBoundsSum[ubSum]);
		} 
		
		if(real)
			upToDate = (maximize && util.compareTo(bound) <= 0) || (!maximize && util.compareTo(bound) >= 0);
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
		U bound = confirmedUtil;
		
		if(counter > 0) {
			bound = bound.add(upperBoundsSum[ubSum]);
		} 
		
		return bound;
	}
	
	/**
	 * Update the utility information in this leaf node
	 * 
	 * @author Brammert Ottens, 4 July 2009
	 * 
	 * @param g				the good currently being processed
	 * @param utilityDelta	the difference with the previous utility received from \c sender for this assignment
	 * @param sender		the sender of the good
	 * @param powersOf2 	precomputed powers of 2
	 * @param maximize		when \c true we are maximizing, when \c false we are minimizing
	 */
	@SuppressWarnings("static-access" )
	@Override
	public void updateLeafNoUB( frodo2.algorithms.odpop.Good<?, U> g, U utilityDelta, int sender, int[] powersOf2, final boolean maximize) {
		Good<?, U> gCast = (Good<?, U>)g;
		util = util.add(utilityDelta);
		if(real) {
			U u = g.getUtility();
			if(gCast.isConfirmed()) {
				confirmedUtil = confirmedUtil.add(u);
				counter--;
				assert counter >= 0;
				updateUB[sender] = false;
			}
		}
		
		ubSum = this.fromBooleanArrayToInt(updateUB, powersOf2);
		upToDate = UB == null || (maximize && util.compareTo(UB) <= 0) || (!maximize && util.compareTo(UB) >= 0);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTree.LeafNode#updateLeafWithUB(frodo2.algorithms.odpop.Good, frodo2.solutionSpaces.Addable, int, U[], int[], boolean, boolean)
	 */
	@SuppressWarnings("static-access")
	@Override
	public void updateLeafWithUB(frodo2.algorithms.odpop.Good<?, U> g, U utilityDelta, int sender, U[] upperBoundSums, int[] powersOf2, boolean compatibleAssignmentReceived, final boolean maximize) {
		Good<?, U> gCast = (Good<?, U>)g;
		U utility = g.getUtility();
		if(real) {
			util = util.add(utilityDelta);
			if(gCast.isConfirmed()) {
				confirmedUtil = confirmedUtil.add(utility);
				counter--;
				assert counter >= 0;
				updateUB[sender] = false;
			}
		} else if(compatibleAssignmentReceived) {
			util = util.add(utilityDelta);
			if(gCast.isConfirmed()) {
				confirmedUtil = confirmedUtil.add(utility);
				counter--;
				assert counter >= 0;
				updateUB[sender] = false;
			}
		}
		
		ubSum = this.fromBooleanArrayToInt(updateUB, powersOf2);
		if(counter == 0)
			UB = confirmedUtil;
		else
			UB = calculateUB(upperBoundSums, maximize);
		
		if(UB == UB.getMinInfinity())
			util = util.getMinInfinity();
		
		upToDate = (maximize && util.compareTo(UB) <= 0) || (!maximize && util.compareTo(UB) >= 0);
		
		assert counter != 0 || util.equals(confirmedUtil);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode#setInfeasable(frodo2.solutionSpaces.Addable)
	 */
	@Override
	public void setInfeasable(U infeasibleUtil) {
		util = infeasibleUtil;
		this.confirmedUtil = util;
	}
	
	/**
	 * Create a new instance of the leaf node 
	 * 
	 * @author Brammert Ottens, 1 july 2009
	 * @param confirmedCounter	the number of children of this leaf node
	 * @param powersOf2 		precomputed powers of 2
	 * @return	a new leaf node
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U>> L newInstance(int confirmedCounter, int[] powersOf2) {
		return (L)new LeafNode<U>(confirmedCounter, powersOf2);
	}
	
	/**
	 * Create a new instance of the leaf node 
	 * 
	 * @author Brammert Ottens, 1 july 2009
	 * @param confirmedCounter	the number of children of this leaf node
	 * @param util 				the utility of the local problem
	 * @param powersOf2 		precomputed powers of 2
	 * @return	a new leaf node
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U>> L newInstance(int confirmedCounter, U util, int[] powersOf2) {
		return (L)new LeafNode<U>(confirmedCounter, util, powersOf2);
	}
}
