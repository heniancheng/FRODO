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

import frodo2.algorithms.dpop.UTILmsg;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.crypto.AddableBigInteger;
import frodo2.solutionSpaces.hypercube.Hypercube;

/** UTIL Message sent and decoded by VariableObfuscation
 * @param <V> the type used for variable values
 * @author Eric Zbinden, Thomas Leaute
 */
public class ObsfUTILmsg < V extends Addable<V> > extends UTILmsg<V, AddableBigInteger> {
	
	/** Used for serialization */
	private static final long serialVersionUID = -5524138436777783456L;
	
	/**
	 * Empty constructor for externalization
	 */
	public ObsfUTILmsg(){
		super();
		super.type = VariableObfuscation.OBFUSCATED_UTIL_TYPE;
		
	}

	/** Constructor
	 * @param senderVar 	the sender variable
	 * @param senderAgent 	the sender agent
	 * @param dest 			the destination variable
	 * @param space		 	the space
	 */
	public ObsfUTILmsg(String senderVar, String senderAgent, String dest, Hypercube<V, AddableBigInteger> space){
		super(senderVar, senderAgent, dest, space);
		super.type = VariableObfuscation.OBFUSCATED_UTIL_TYPE;			
	}
	
}