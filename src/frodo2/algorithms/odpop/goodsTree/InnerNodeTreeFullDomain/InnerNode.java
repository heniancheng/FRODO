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

import frodo2.solutionSpaces.Addable;

/**
 * The inner node of a tree
 * @author brammert
 * @param <U> the type used to represent a utility value
 * @param <L> the type of LeafNode that is to be used
 */
public class InnerNode< U extends Addable<U>, L extends LeafNode<U>> extends Node<U> {
	
	/** the branch from this node to the assignment that has the highest utility*/
    protected int maxChild;
    
    /** the branch from this node to the assignment that has the highest upper bound*/
    protected int maxUBChild;
    
    /** The utility stored in this node */
	protected L util;
	
	/** The UB stored in this node */
	protected L UB;
	
	/** The children of this node */
	public Node<U>[] children;
	
	/**
	 * A constructor
	 * @param nbrOfChildren 	the number of children of this node
	 */
	@SuppressWarnings("unchecked")
	public InnerNode(int nbrOfChildren) {
		super();
		maxChild = -1;
		maxUBChild = -1;
		children = (Node<U>[]) new Node[nbrOfChildren];
	}
	
	/**
	 * A constructor
	 * @param children		the number of children of this node
	 */
	public InnerNode(Node<U>[] children) {
		super();
		this.children = children;
		maxChild = -1;
		maxUBChild = -1;
	}
	
	/**
	 * @author Brammert Ottens, 19 nov 2009
	 * @return the node that should be used as an example when adding a new domain element
	 */
	public Node<U> getExample() {
		if(UB != null) {
			return this.children[maxUBChild];
		} else if(maxChild != -1){
			return this.children[maxChild];
		} else {
			for(int i = 0; i < this.children.length; i++) {
				if(children[i] != null)
					return children[i];
			}
		}
		
		return null;
	}
	
	/**
	 * @author Brammert Ottens, 19 nov 2009
	 * @return the maximal upperbound of the node
	 */
	public L getMaxUB() {
		return UB;
	}
	
	/**
	 * @author Brammert Ottens, 19 nov 2009
	 * @return the index of the child that contains the maximal upper bound
	 */
	public int getMaxUBChild() {
		return maxUBChild;
	}
	
	/**
	 * @author Brammert Ottens, 19 nov 2009
	 * @return the index of the child that contains the maximal utility
	 */
	public int getMaxChild() {
		return this.maxChild;
	}
	
	/**
	 * @author Brammert Ottens, 19 nov 2009
	 * @return the maximal utility of this node
	 */
	public L getMaxUtil() {
		return util;
	}
	
	/**
	 * Set the maximal utility for this node
	 * @author Brammert Ottens, 19 nov 2009
	 * @param index		the index of the child that contains this maximal utility
	 * @param direct \c true when all the children are leafnodes, and \c false otherwise
	 */
	@SuppressWarnings("unchecked")
	public void setUtil(int index, boolean direct) {
		maxChild = index;
		if(index == -1)
			util = null;
		else
			if(direct) {
				util = (L)children[index];
				assert util != null;
				assert util.isUpToDate();
			} else {
				util = ((InnerNode<U, L>)children[index]).util;
				assert children[index] != null && ((InnerNode<U, L>)children[index]).util != null;
				assert util.isUpToDate();
			}
	}
	
	/**
	 * Set the maximal upper bound for this node
	 * @author Brammert Ottens, 19 nov 2009
	 * @param index		the index of the child that contains this maximal utility
	 * @param direct \c true when all the children are leafnodes, and \c false otherwise
	 */
	@SuppressWarnings("unchecked")
	public void setUB(int index, boolean direct) {
		maxUBChild = index;
		if(index == -1)
			UB = null;
		else
			if(direct) {
				UB = (L)children[index];
				assert UB != null;
			} else {
				UB = ((InnerNode<U, L>)children[index]).UB;
				assert children[index] != null;
			}
	}
	
	/**
	 * If maxUBChild points to the dummy child, its value should be increased by 1
	 * @param enlargement	the number of children slots that need to be added
	 * @param dummy			\c true when this node contains a dummy value
	 */
	@SuppressWarnings("unchecked")
	public void enlargeChildrenArray(int enlargement, boolean dummy) {
		Node<U>[] newChildren = (Node<U>[]) new Node[children.length + enlargement];
		int length = children.length - 1;

		if(dummy) { // if this node contains a dummy, it should remain at the last position 
			System.arraycopy(children, 0, newChildren, 0, length);
			newChildren[newChildren.length - 1] = children[length];
		} else {
			System.arraycopy(children, 0, newChildren, 0, children.length);			
		}

		children = newChildren;
	}
	
	/**
	 * Return a specific child of this node
	 * 
	 * @param i		the id of the child
	 * @return Node
	 */
	public Node<U> getChild(int i) {
		if (i < children.length) {
			return children[i];
		} else {
			return null;
		}
	}
	
	/**
	 * Add a child in a particular position
	 * 
	 * @param child		the child to be updated
	 * @param position	the position of the child
	 * @warning this method overrides the previous Node in this position
	 */
	public void setChild(Node<U> child, int position) {
		children[position] = child;
	}

	/**
	 * This method is used when the assignment belonging to leaf child has been
	 * sent as a confirmed good. All other children must be removed from the
	 * tree.
	 * 
	 * @param child	remove all children but \c child
	 */
	public void retainOnly(int child) {
		for (int i = 0; i < children.length; i++) {
			if (i != child) {
				children[i] = null;
			} else {
				children[i].alive = false;
			}
		}
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#utilCandidate(frodo2.solutionSpaces.Addable, boolean)
	 */
	@Override
	public boolean utilCandidate(U maxUtil, final boolean maximize) {
		return util != null && util.utilCandidate(maxUtil, maximize);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#ubCandidate(frodo2.solutionSpaces.Addable, boolean)
	 */
	@Override
	public boolean ubCandidate(U maxUB, final boolean maximize) {
		return UB != null && UB.ubCandidate(maxUB, maximize);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#getUtil()
	 */
	@Override
	public U getUtil() {
		return util.getUtil();
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#getUB()
	 */
	@Override
	public U getUB() {
		return UB.getUB();
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#hasUtil()
	 */
	@Override
	public boolean hasUtil() {
		return util != null;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#hasUB()
	 */
	@Override
	public boolean hasUB() {
		return UB != null;
	}
	
	/**
	 * If there is a util, there must be an upperbound as well
	 * 
	 * @author Brammert Ottens, 20 apr 2010
	 * @return \c true if the test succeeds, and \c false otherwise
	 */
	public boolean check1() {
		return util == null || UB != null;
	}
	
	/**
	 * Either the utility is lower than the upperbound, or the utility is not up to date
	 * @author Brammert Ottens, 20 apr 2010
	 * @param maximize when \c true we are maximizing, when \c false we are minimizing
	 * @return \c true if the test succeeds, and \c false otherwise
	 */
	public boolean check2(final boolean maximize) {
		return util == null || UB == null || !UB.isUpToDate() || (maximize && util.getUtil().compareTo(UB.getUtil()) >= 0) || (!maximize && util.getUtil().compareTo(UB.getUtil()) <= 0);
	}
	
	/**
	 * Either the utility is not up to date or it is not greater than the upper bound
	 * @author Brammert Ottens, 22 apr 2010
	 * @param maximize when \c true we are maximizing, when \c false we are minimizing
	 * @return \c true if the test succeeds and \c false otherwise
	 */
	public boolean check3(final boolean maximize) {
//		return util == null || UB == null || !util.isUpToDate() || (maximize && util.getUtil().compareTo(UB.getUB()) <= 0) || (!maximize && util.getUtil().compareTo(UB.getUB()) >= 0);
		return true;
	}
	
	/**
	 * If there is an upper bound, and its utility is up to date, then there must
	 * also be a utility
	 * @author Brammert Ottens, 22 apr 2010
	 * @return \c true if the test succeeds and \c false otherwise
	 */
	public boolean check4() {
		return UB == null || !UB.isUpToDate() || util != null;
	}
	
	/**
	 * Check whether the upperbound is up to date
	 * @author Brammert Ottens, 22 apr 2010
	 * @param upperBoundSums	the precalculated upper bound sums
	 * @param maximize when \c true we are maximizing, when \c false we are minimizing
	 * @return \c true if the test succeeds and \c false otherwise
	 */
	public boolean check5(U[] upperBoundSums, final boolean maximize) {
		return UB == null || UB.counter == 0 || UB.getUB().equals(UB.calculateUBTest(upperBoundSums));
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node#recalculateUtil()
	 */
	public boolean recalculateUtil() {
		return util != null && !util.isUpToDate();
	}

}
