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

import java.util.ArrayList;
import java.util.Arrays;

import frodo2.solutionSpaces.Addable;

/** A variable node in a factor graph
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class VariableNode < V extends Addable<V>, U extends Addable<U> > {

	/** The name of this variable */
	protected final String varName;
	
	/** The agent that controls this variable node */
	private final String agent;
	
	/** The domain of the variable */
	protected final V[] dom;
	
	/** The functions this variable is involved in */
	private final ArrayList< FunctionNode<V, U> > functions = new ArrayList< FunctionNode<V, U> > ();
	
	/** Constructor
	 * @param varName 	the variable name
	 * @param agent 	the agent controlling this variable node
	 * @param dom 		the variable domain
	 */
	public VariableNode (String varName, String agent, V[] dom) {
		this.varName = varName;
		this.agent = agent;
		this.dom = dom;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		
		StringBuilder builder = new StringBuilder ("VariableNode(");
		
		builder.append(this.varName + " in " + Arrays.toString(this.dom));
		builder.append(", agent: " + this.agent);
		builder.append(", functions:");
		for (FunctionNode<V, U> function : this.functions) 
			builder.append(" " + function.getName());
		builder.append(")");
		
		return builder.toString();
	}
	
	/** Adds a function in which this variable is involved
	 * @param function 	the function node
	 */
	public void addFunction (FunctionNode<V, U> function) {
		this.functions.add(function);
	}

	/** @return The name of this variable */
	public String getVarName() {
		return varName;
	}

	/** @return The domain of the variable */
	public V[] getDom() {
		return dom;
	}

	/** @return The functions this variable is involved in */
	public ArrayList<FunctionNode<V, U>> getFunctions() {
		return functions;
	}

	/** @return the agent controlling this variable node */
	public String getAgent() {
		return agent;
	}
	
}
