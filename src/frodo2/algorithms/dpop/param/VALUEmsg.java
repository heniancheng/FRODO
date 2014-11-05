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

package frodo2.algorithms.dpop.param;

import java.util.ArrayList;
import java.util.Arrays;

import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;

/** VALUE message
 * @param <Val> the type used for variable values
 */
public class VALUEmsg < Val extends Addable<Val> > 
extends MessageWith3Payloads< String, String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > {
	
	/** Empty constructor used for externalization */
	public VALUEmsg () { }

	/** Constructor 
	 * @param dest 			destination variable
	 * @param variables 	array of variables in \a dest's separator
	 * @param values 		values for the variables in \a variables, in the same order, conditioned on the values of the parameters (if any)
	 */
	public VALUEmsg(String dest, String[] variables, BasicUtilitySolutionSpace< Val, ArrayList<Val> > values) {
		super(ParamVALUE.PARAM_VALUE_MSG_TYPE, dest, variables, values);
	}

	/** @return the destination variable */
	public String getDest() {
		return super.getPayload1();
	}

	/** @return the separator */
	public String[] getVariables() {
		return super.getPayload2();
	}

	/** @return the values for the variables in the separator, conditioned on the values of the parameters (if any) */
	public BasicUtilitySolutionSpace< Val, ArrayList<Val> > getValues() {
		return super.getPayload3();
	}
	
	/** @see frodo2.communication.Message#toString() */
	public String toString () {
		return "Message(type = `" + this.getType() + "')\n\tdest: " + super.getPayload1() + "\n\tvars: " + Arrays.asList(super.getPayload2()) + 
		"\n\tvals: " + super.getPayload3();
	}
	
	/** @see MessageWith3Payloads#fakeSerialize() */
	@Override
	public void fakeSerialize () {
		super.setPayload3(super.getPayload3().resolve());
	}
}