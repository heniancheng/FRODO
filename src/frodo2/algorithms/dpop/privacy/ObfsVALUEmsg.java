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

package frodo2.algorithms.dpop.privacy;

import frodo2.algorithms.dpop.VALUEmsg;
import frodo2.solutionSpaces.Addable;

/** VALUE message sent and decoded by VariableObfuscation
 * @param <V> the type used for variable values
 * @author Eric Zbinden, Thomas Leaute
 */
public class ObfsVALUEmsg < V extends Addable<V> > extends VALUEmsg<V>{
	
	/** Used for serialization */
	private static final long serialVersionUID = 6644302154690237352L;

	/**
	 * Empty Constructor used for externalization
	 */
	public ObfsVALUEmsg(){
		super();
		super.type = VariableObfuscation.OBFUSCATED_VALUE_TYPE;
	}
	
	/** Constructor 
	 * @param dest 			destination variable
	 * @param variables 	array of variables in \a dest's separator
	 * @param values 		array of values for the variables in \a variables, in the same order
	 */
	ObfsVALUEmsg(String dest, String[] variables, V[] values){
		super(dest, variables, values);
		super.type = VariableObfuscation.OBFUSCATED_VALUE_TYPE;			
	}
}