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
 * Class to hold an assignment of variables. This class is only used when sending goods. In between a more succinct representation
 * is used.
 * @author brammert
 *
 * @param <Val>	type of the class used to store domain values
 */
public class Assignment < Val extends Addable<Val> > {

	/** The variables in the assignment*/
	private final String[] variables;
	
	/** The values to the variables*/
	private final Val[] values;
	
	/**
	 * A constructor
	 * 
	 * @param variables	the variables in the assignment
	 * @param values	the values of the variables
	 */
	public Assignment(String[] variables, Val[] values) {
		this.variables = variables;
		this.values = values;
	}
	
	/**
	 * @return the variables in this assignment
	 */
	public String[] getVariables() {
		return variables;
	}
	
	/**
	 * @return the values of the variables in this assignment
	 */
	public Val[] getValues() {
		return values;
	}
	
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) { // we assume that the order of variables in two assignments is the same
		Assignment<Val> a = (Assignment<Val>)o;
		for(int i = 0; i < values.length; i++) {
			if(!values[i].equals(a.values[i])) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Checks whether this assignment is compatible with a. Two assignments are
	 * compatible if they agree on all variables
	 * @param a	another assignment
	 * @return \c true if the two assignments are compatible
	 */
	public boolean compatible(Assignment<Val> a) {
		if(variables.length < a.variables.length) {
			for(int i = 0; i < variables.length; i++) {
				String var = variables[i];
				Val val = values[i];
				boolean noMatch = false;
				for(int j = i; j < a.variables.length; j++) {
					if(var.equals(a.variables[j]) && !val.equals(a.values[j])) {
						j = a.variables.length;
						noMatch = true;
					}
				}

				if(noMatch) {
					return false;
				}
			}
		} else {
			for(int i = 0; i < a.variables.length; i++) {
				String var = a.variables[i];
				Val val = a.values[i];
				boolean noMatch = false;
				for(int j = i; j < variables.length; j++) {
					if(var.equals(variables[j]) && !val.equals(values[j])) {
						j = variables.length;
						noMatch = true;
					}
				}

				if(noMatch) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		String s = "";
		
		for(int i = 0; i < variables.length; i++) {
			s += ":" + variables[i] + "=" + values[i];
		}
		
		return s;
	}
}
