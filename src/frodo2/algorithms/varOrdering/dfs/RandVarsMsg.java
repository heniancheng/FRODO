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

package frodo2.algorithms.varOrdering.dfs;

import java.util.HashSet;
import java.util.Set;

import frodo2.communication.MessageWith2Payloads;

/** A message containing random variables */
public class RandVarsMsg extends MessageWith2Payloads< String, HashSet<String> > {

	/** Empty constructor used for externalization */
	public RandVarsMsg () { }

	/** Constructor
	 * @param var 		the variable concerned
	 * @param randVars 	a set of random variables 
	 */
	public RandVarsMsg(String var, HashSet<String> randVars) {
		super(LocalRandVarsDFS.RAND_VARS_MSG_TYPE, var, randVars);
	}
	
	/** @return the variable */
	public String getVar () {
		return super.getPayload1();
	}
	
	/** @return the set of random variables */
	public Set<String> getRandVars () {
		return super.getPayload2();
	}
}