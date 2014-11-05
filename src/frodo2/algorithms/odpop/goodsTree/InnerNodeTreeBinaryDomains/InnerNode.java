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

package frodo2.algorithms.odpop.goodsTree.InnerNodeTreeBinaryDomains;

import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
import frodo2.solutionSpaces.Addable;

/**
 * The inner node of a tree
 * @author brammert
 * @param <U> the type used to represent a utility value
 * @param <L> the type of LeafNode that is to be used
 */
public class InnerNode< U extends Addable<U>, L extends LeafNode<U>> extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, LeafNode<U>> {
	
	/**
	 * A constructor
	 */
	public InnerNode() {
		super(2);
	}
	
	/**
	 * A constructor
	 * @param children		the number of children of this node
	 */
	public InnerNode(Node<U>[] children) {
		super(children);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#getExample()
	 */
	@Override
	public Node<U> getExample() {
		if(UB != null) {
			return this.children[maxUBChild];
		} else if(maxChild != -1){
			return this.children[maxChild];
		} else {
			for(int i = 0; i < 2; i++) {
				if(children[i] != null)
					return children[i];
			}
		}
		
		return null;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#getChild(int)
	 */
	@Override
	public Node<U> getChild(int i) {
		if (i < 2) {
			return children[i];
		} else {
			return null;
		}
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode#retainOnly(int)
	 */
	@Override
	public void retainOnly(int child) {
		for (int i = 0; i < 2; i++) {
			if (i != child) {
				children[i] = null;
			} else {
				children[i].alive = false;
			}
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

}
