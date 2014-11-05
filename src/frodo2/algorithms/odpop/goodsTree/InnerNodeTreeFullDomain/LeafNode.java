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

package frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain;

import frodo2.algorithms.odpop.Good;
import frodo2.solutionSpaces.Addable;

/**
 * The leaf node of a tree
 * @author brammert
 * @param <U> the type used to represent a utility value
 */
public class LeafNode<U extends Addable<U>> extends Node<U> {
	
	/** Number of children that have not yet reported a value for the current assignment */
	public int counter;
	
	/** Contains the sum of the received utilities for this leaf. Used for recalculating the upper bound*/
	protected U util;
	
	/** Contains the last upper bound to have been calculated*/
	protected U UB;
	
	/** for each child, it stores whether a good has been received or not*/
	public boolean[] updateUB;
	
	/** \c true when the upperbound is still greater than the utility value, and false otherwise. This can occur when the UB is -infinity */
	protected boolean upToDate;
	
	/** The index to the sum of all upperbounds */
	protected int ubSum;
	
	/**
	 * Empty constructor
	 */
	public LeafNode() {
		super();
	}

	/**
	 * A constructor
	 * @param counter 	the number of assignments to be received
	 * @param powersOf2 precomputed powers of 2
	 */
	public LeafNode(int counter, int[] powersOf2) {
		super();
		upToDate = true;
		this.counter = counter;
		updateUB = new boolean[counter];
		ubSum = -1;
		for(int i = 0; i < counter; i++) {
			updateUB[i] = true;
			ubSum += powersOf2[i];
		}
	}

	/**
	 * A constructor
	 * @param counter 	the number of assignments to be received
	 * @param util				the already receive utility
	 * @param powersOf2 precomputed powers of 2
	 */
	public LeafNode(int counter, U util, int[] powersOf2) {
		super();
		upToDate = true;
		this.counter = counter;
		UB = util;
		this.util = util;
		updateUB = new boolean[counter];
		ubSum = -1;
		for(int i = 0; i < counter; i++) {
			updateUB[i] = true;
			ubSum += powersOf2[i];
		}
	}

	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @param sum the upper bound sum
	 */
	public void setUbSum(int sum) {
		this.ubSum = sum;
	}
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @param upperBoundsSum precomputed sums of the upperbounds
	 * @param maximize	when \c true we are maximizing, when \c false we are minimizing
	 * @return the upperbound
	 */
	public U calculateUB(U[] upperBoundsSum, final boolean maximize) {
		U bound = util;
		if(counter > 0) {
			bound = bound.add(upperBoundsSum[ubSum]);
		} 

		upToDate = maximize ? util.compareTo(bound) <= 0 : util.compareTo(bound) >= 0;
		return bound;
	}
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @param upperBoundsSum precomputed sums of the upperbounds
	 * @return the upperbound
	 */
	public U calculateUBTest(U[] upperBoundsSum) {
		U bound = util;
		if(counter > 0) {
			bound = bound.add(upperBoundsSum[ubSum]);
		} 

		return bound;
	}
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @param g				the good that initiated the update
	 * @param utilityDelta	the difference between the reported util and the previous util
	 * @param sender		the sender of the good
	 * @param powersOf2		precomputed powers of 2
	 * @param maximize	when \c true we are maximizing, when \c false we are minimizing
	 */
	public void updateLeafNoUB( Good<?, U> g, U utilityDelta, int sender, int[] powersOf2, final boolean maximize) {
		util = util.add(g.getUtility());
		counter--;
		assert counter >= 0;
		updateUB[sender] = false;
		ubSum = fromBooleanArrayToInt(updateUB, powersOf2);
	}
	
	/**
	 * @author Brammert Ottens, 30 apr 2010
	 * @param infeasibleUtil the infeasible utility
	 */
	public void setInfeasable(U infeasibleUtil) {
		util = infeasibleUtil;
	}
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @param g				the good that initiated the update
	 * @param utilityDelta	the difference between the reported util and the previous util
	 * @param sender		the sender of the good
	 * @param upperBoundSums	the sums of the upperbounds
	 * @param powersOf2		precomputed powers of 2
	 * @param compatibleAssignmentReceived \c true when an assignment compatible to this assignment has been received
	 * @param maximize	when \c true we are maximizing, when \c false we are minimizing
	 */
	public void updateLeafWithUB(Good<?, U> g, U utilityDelta, int sender, U[] upperBoundSums, int[] powersOf2, boolean compatibleAssignmentReceived, final boolean maximize) {
		U utility = g.getUtility();
		util = util.add(utility);
		counter--;
		assert counter >= 0;
		updateUB[sender] = false;

		// @todo this should always be calculated with a simple subtraction and addition
		ubSum = fromBooleanArrayToInt(updateUB, powersOf2);
		UB = calculateUB(upperBoundSums, maximize);
	}
	
	/**
	 * Given a boolean array, representing a boolean value, 
	 * this method calculates the corresponding integer value.
	 * @author Brammert Ottens, 25 jun 2009
	 * @param chosen	the boolean value represented as an array
	 * @param powersOf2 TODO
	 * @return			the integer value of chosen
	 */
	public static int fromBooleanArrayToInt(boolean[] chosen, int[] powersOf2) {
		int sum = -1;
		
		for(int i = 0; i < chosen.length;) {
			if(chosen[i])
				sum += powersOf2[i];
			i++;
		}
		
		return sum;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#utilCandidate(frodo2.solutionSpaces.Addable, boolean)
	 */
	@Override
	public boolean utilCandidate(U maxUtil, final boolean maximize) {
		return this.upToDate && (maxUtil == null || (maximize && maxUtil.compareTo(util) < 0) || (!maximize && maxUtil.compareTo(util) > 0));
	}
	
	/**
	 * @param maxUB the maximal upper bound found so far
	 * @param maximize 			\c true if we are maximizing utility
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#ubCandidate(frodo2.solutionSpaces.Addable, boolean)
	 */
	@Override
	public boolean ubCandidate(U maxUB, final boolean maximize) {
		return maxUB == null || (maximize && maxUB.compareTo(UB) < 0) || (!maximize && maxUB.compareTo(UB) > 0);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#getUtil()
	 */
	@Override
	public U getUtil() {
		return util;
	}
	
	/**
	 * Set the utility of this leaf node
	 * @author Brammert Ottens, 22 apr 2010
	 * @param util the new utility value
	 */
	public void setUtil(U util) {
		this.util = util;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#getUB()
	 */
	@Override
	public U getUB() {
		return UB;
	}
	
	/**
	 * Set the upper bound on the utility of this leaf node
	 * @author Brammert Ottens, 22 apr 2010
	 * @param UB	the new upper bound
	 */
	public void setUB(U UB) {
		this.UB = UB;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#hasUtil()
	 */
	@Override
	public boolean hasUtil() {
		return util != null;
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @return \c true when the utility is nor greate than the upper bound, and false otherwise
	 */
	public boolean isUpToDate() {
		return this.upToDate;
	}
	
	/**
	 * Set whether the utility is up to date
	 * 
	 * @author Brammert Ottens, 22 apr 2010
	 * @param upToDate \c true when the utility is up to date, and false otherwise
	 */
	public void setUpToDate(boolean upToDate) {
		this.upToDate = upToDate;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#newInstance(int, int[])
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U>> L newInstance(int confirmedCounter, int[] powersOf2) {
		return (L)new LeafNode<U>(confirmedCounter, powersOf2);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#newInstance(int, frodo2.solutionSpaces.Addable, int[])
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U>> L newInstance(int confirmedCounter, U util, int[] powersOf2) {
		return (L)new LeafNode<U>(confirmedCounter, util, powersOf2);
	}

}
