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

package frodo2.algorithms.odpop.goodsTree.leafNodeTree;

/**
 * @author Brammert Ottens, 9 nov 2009
 * 
 */
public class Node {

	/** A list of children of this node*/
	private Node[] children;
	
	/**
	 * A constructor
	 * @param numberOfChildren the number of children of this node
	 */
	Node(int numberOfChildren) {
		children = new Node[numberOfChildren];
	}
	
	/**
	 * @author Brammert Ottens, 10 nov 2009
	 * @param i the position
	 * @return the child at position \c i
	 */
	public Node getChild(int i) {
		assert i < children.length;
		return children[i];		
	}
	
	/**
	 * Add a child at position \c index
	 * @author Brammert Ottens, 10 nov 2009
	 * @param child the child to be added
	 * @param index the index at which the child is to be added
	 */
	public void addChild(Node child, int index) {
		children[index] = child;
	}
}
