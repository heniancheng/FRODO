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

package frodo2.algorithms.varOrdering.factorgraph;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A function node in a factor graph
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class FunctionNode < V extends Addable<V>, U extends Addable<U> > {

	/** The name of the constraint */
	private final String name;
	
	/** The constraint */
	private final UtilitySolutionSpace<V, U> space;
	
	/** The agent responsible for simulating this function node */
	private final String agent;
	
	/** Constructor
	 * @param name 		The name of the constraint
	 * @param space 	The constraint
	 * @param agent 	The agent responsible for simulating this function node
	 */
	public FunctionNode (String name, UtilitySolutionSpace<V, U> space, String agent) {
		this.name = name;
		this.space = space;
		this.agent = agent;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "FunctionNode:\n\t name: " + this.name + "\n\t agent: " + this.agent + "\n\t space: " + this.space;
	}

	/** @return The name of the constraint */
	public String getName() {
		return name;
	}

	/** @return The constraint */
	public UtilitySolutionSpace<V, U> getSpace() {
		return space;
	}

	/** @return The agent responsible for simulating this function node */
	public String getAgent() {
		return agent;
	}
	
}
