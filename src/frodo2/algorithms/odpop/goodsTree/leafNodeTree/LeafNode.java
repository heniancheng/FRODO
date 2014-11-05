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

import frodo2.solutionSpaces.Addable;

/**
 * 
 * @author Brammert Ottens, 9 nov 2009
 *  @param <Val> type used for variable values
 */
public class LeafNode <Val extends Addable<Val> > extends Node {

	/** The value of the tree's own variable*/
	private Val value;
	
	/**
	 * Constructor 
	 * @param value the value of the trees own variable
	 */
	public LeafNode(Val value) {
		super(1);
		this.value = value;
	}
	
	/**
	 * @author Brammert Ottens, 10 nov 2009
	 * @return the value of the trees own variable
	 */
	public Val getValue() {
		return value;
	}
}
