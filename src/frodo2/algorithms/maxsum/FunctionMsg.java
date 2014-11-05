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

package frodo2.algorithms.maxsum;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;


/** A message sent by a function node
 * 
 * @author Thomas Leaute, remotely based on a preliminary contribution by Sokratis Vavilis and George Vouros (AI-Lab of the University of the Aegean)
 *
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class FunctionMsg< V extends Addable<V>, U extends Addable<U> >  extends MessageWith2Payloads< String, UtilitySolutionSpace<V, U> > {

	/** The type of this message */
	public static final String FUNCTION_MSG_TYPE = "FunctionToVar";

	/** Empty constructor used for externalization */
	public FunctionMsg () {
		super.type = FUNCTION_MSG_TYPE;
	}

	/** Constructor
	 * @param functionNode	the sender of this message
	 * @param marginalUtil	the marginal utility as a function of the destination variable
	 */
	public FunctionMsg(String functionNode, UtilitySolutionSpace<V, U> marginalUtil) {
		super(FUNCTION_MSG_TYPE, functionNode, marginalUtil);
	}

	/** @see MessageWithPayload#fakeSerialize() */
	@Override
	public void fakeSerialize () {
		super.setPayload2(this.getMarginalUtil().resolve());
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.getPayload1());
		out.writeObject(super.getPayload2());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.setPayload1((String) in.readObject());
		super.setPayload2((UtilitySolutionSpace<V, U>) in.readObject());
	}

	/** @return the name of the sender function node */
	public String getFunctionNode () {
		return this.getPayload1();
	}

	/** @return the marginal utility as a function of the destination variable */
	public UtilitySolutionSpace<V, U> getMarginalUtil () {
		return this.getPayload2();
	}

}