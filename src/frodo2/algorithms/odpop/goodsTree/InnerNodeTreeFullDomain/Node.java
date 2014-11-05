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
 * Class for every node in the GoodsTree
 * 
 * @author brammert
 * @param <U>	the type used to represent a utility value
 */
public class Node<U extends Addable<U>> {

	/** False if this node leads to a path that has been deleted */
	public boolean alive;

	/**
	 * A constructor
	 */
	public Node() {
		this.alive = true;
	}
	
	/**
	 * Method used when creating a dummy part of the tree. We are only
	 * interested in the UB
	 * 
	 * @return a shallow copy of this node
	 */
	public Node<U> makeDummyCopy() {
		return null;
	}
	
	/**
	 * @author Brammert Ottens, 30 sep 2009
	 * @return \c true when this node is still alive, and false otherwise	
	 */
	public boolean isAlive() {
		return alive;
	}
	
	/**
	 * Change the alive status of this node
	 * @author Brammert Ottens, 30 sep 2009
	 * @param alive \c true when this node is alive, and \c false otherwise
	 */
	public void setAlive(boolean alive) {
		this.alive = alive;
	}
	
	/**
	 * @author Brammert Ottens, 20 apr 2010
	 * @param maxUtil the maximal utility found so far
	 * @param maximize \c when true we are maximizing, otherwise we are minimizing
	 * @return \c true when this node can be looked upon as a candidate for the maximal utility of the subtree rooted
	 *         at his parent
	 */
	public boolean utilCandidate(U maxUtil, final boolean maximize) {
		assert false : "Not Implemented";
		return true;
	}
	
	/**
	 * @author Brammert Ottens, 20 apr 2010
	 * @param maxUB the maximal upper bound found so far
	 * @param maximize \c when true we are maximizing, otherwise we are minimizing
	 * @return \c true when this node can be looked upon as a candidate for the maximal upper bound of the subtree rooted
	 *         at his parent
	 */
	public boolean ubCandidate(U maxUB, boolean maximize) {
		assert false : "Not Implemented";
		return true;
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @return the utility of this node
	 */
	public U getUtil() {
		assert false : "Not Implemented";
		return null;
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @return the upper bound of this node
	 */
	public U getUB() {
		assert false : "Not Implemented";
		return null;
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @return \c true when this node has a utility
	 */
	public boolean hasUtil() {
		assert false : "Not Implemented";
		return true;
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @return \c true when this node has an upper bound
	 */
	public boolean hasUB() {
		assert false : "Not Implemented";
		return true;
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
	public <L extends Node<U>> L newInstance(int confirmedCounter, int[] powersOf2) {
		assert false: "Not Implemented";
		return null;
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
	public <L extends Node<U>> L newInstance(int confirmedCounter, U util, int[] powersOf2) {
		assert false: "Not Implemented";
	return null;
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @return \c true when it makes sense to recalculate the utility of this node
	 */
	public boolean recalculateUtil() {
		assert false : "Not Implemented";
		return true;
	}
}
