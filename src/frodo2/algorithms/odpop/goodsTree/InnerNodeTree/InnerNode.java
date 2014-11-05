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

import java.lang.reflect.Array;

import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
import frodo2.solutionSpaces.Addable;

/**
 * The inner node of a tree
 * @author brammert
 * @param <U> the type used to represent a utility value
 * @param <L> the type of LeafNode that is to be used
 */
public class InnerNode< U extends Addable<U>, L extends LeafNode<U>> extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> {
	
	/** True if this node is a dummy node, and false otherwise */
	public boolean real;
	
	/**
	 * A constructor
	 * @param nbrOfChildren 	the number of children of this node
	 */
	public InnerNode(int nbrOfChildren) {
		super(nbrOfChildren);
		real = true;
	}
	
	/**
	 * A constructor
	 * @param children		the number of children of this node
	 */
	public InnerNode(Node<U>[] children) {
		super(children);
		real = true;
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
				assert ((LeafNode<U>)children[index]).real;
			} else {
				util = ((InnerNode<U, L>)children[index]).util;
				assert children[index] != null && ((InnerNode<U, L>)children[index]).util != null;
				assert util.real;
			}
	}
	
	/**
	 * If maxUBChild points to the dummy child, its value should be increased by 1
	 * @param enlargement	the number of children slots that need to be added
	 * @param dummy			\c true when this node contains a dummy value
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void enlargeChildrenArray(int enlargement, boolean dummy) {
		Node<U>[] newChildren = (Node<U>[]) new Node[children.length + enlargement];
		int length = children.length - 1;

		if(dummy) { // if this node contains a dummy, it should remain at the last position 
			System.arraycopy(children, 0, newChildren, 0, length);
			newChildren[newChildren.length - 1] = children[length];
		} else {
			System.arraycopy(children, 0, newChildren, 0, children.length);			
		}

		if(this.maxUBChild == children.length - 1)
			maxUBChild += 1;
		children = newChildren;
	}
	
	/**
	 * Method to remove the dummy child
	 * @return if the dummy contained the upper bound
	 */
	@SuppressWarnings("unchecked")
	public boolean removeDummy() {
		int length = children.length - 1;
		Node<U>[] newChildren = (Node<U>[]) Array.newInstance(Node.class, length);
		
		System.arraycopy(children, 0, newChildren, 0, length);
		
		children = newChildren;
		
		return this.maxUBChild == length; /// if true then the UB has been removed and a new UB must be calculated
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#check1()
	 */
	@Override
	public boolean check1() {
		return util == null || UB != null;
	}
	
	/**
	 * @param maximize \c true when maximizing, and \c false when minimizing
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#check2(boolean)
	 */
	@Override
	public boolean check2(final boolean maximize) {
		return util == null || UB == null || !UB.real || !UB.isUpToDate() || (maximize && util.getUtil().compareTo(UB.getUtil()) >= 0) || (!maximize && util.getUtil().compareTo(UB.getUtil()) <= 0);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#check4()
	 */
	@Override
	public boolean check4() {
		return UB == null || !UB.real || !UB.isUpToDate() || util != null;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#recalculateUtil()
	 */
	@Override
	public boolean recalculateUtil() {
		return real && util != null && !util.isUpToDate();
	}
}
