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

package frodo2.algorithms.odpop;

import frodo2.solutionSpaces.Addable;

/**
 * Class that represents a good being send from child to parent
 * It contains
 * 
 * - an assignment
 * - the corresponding utility the subtree rooted at the sender can obtain when the assignment is used
 * - a boolean value that states whether the good is confirmed or not
 * @author brammert
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 *
 */
public class Good < Val extends Addable<Val>, U extends Addable<U> > {

	/**
	 * The utility corresponding to this assignment
	 */
	protected final U utility;
	
	/** The variables for this assignment */
	protected final String[] variables;
	
	/** The value assignments of the variables in \c variables */
	protected final Val[] values;
	
	/**
	 * A constructor
	 *
	 * @param variables the variables in the assignment
	 * @param values	the values of the variables
	 * @param utility	The utility corresponding to the assignment
	 */
	public Good(String[] variables, Val[] values, U utility) {
		assert variables != null;
		assert utility != null;
		assert variables.length == values.length;
		this.variables = variables;
		this.values = values;
		this.utility = utility;
	}
	
	/**
	 * @author Brammert Ottens, 21 aug 2009
	 * @return the variables of the assignemnt
	 */
	public String[] getVariables() {
		return variables;
	}
	
	/**
	 * @author Brammert Ottens, 21 aug 2009
	 * @return the values of the variables in the assignment
	 */
	public Val[] getValues() {
		return values;
	}
	
	/**
	 * Getter method for the utility
	 * @return the utility 
	 */
	public U getUtility() {
		return utility;
	}
	
	/** 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) {
		Good<Val, U> g = (Good<Val, U>)o;
		if(utility.equals(g.getUtility())) {
			if(variables.length == g.getVariables().length) {
				Val[] values2 = g.getValues();
				for(int i = 0; i < values.length; i++) {
					if(!values[i].equals(values2[i]))
						return false;
				}
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		String str = "";
		str = "<[";
		if(variables.length > 0) {
			int i = 0;
			for(; i < variables.length-1; i++) {
				str += variables[i] + "=" + values[i] + ", ";
			}
			str += variables[i] + "=" + values[i] + "]";
		}
		str += ", " + this.utility + ">";
		return str;
	}
}
