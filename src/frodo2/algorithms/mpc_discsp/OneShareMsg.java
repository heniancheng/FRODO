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

package frodo2.algorithms.mpc_discsp;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;

import frodo2.communication.Message;

/** In MPC-DisCSP4, a message containing a single share
 * @author Thomas Leaute
 *
 */
public class OneShareMsg extends Message implements Externalizable {
	
	/** The type of this message */
	public final static String ONE_SHARE_MSG = "OneShareMsg";
	
	/** The sender agent */
	private int agent;
	
	/** The share */
	private BigInteger share;

	/** Boolean used to identify received messages that belong to the next phase and should be postponed */
	private boolean step;

	/** Empty constructor used for serialization */
	public OneShareMsg() {
		super.type = ONE_SHARE_MSG;
	}

	/** Constructor
	 * @param agent 	The sender agent
	 * @param share 	the share
	 * @param step 		Boolean used to identify received messages that belong to the next phase and should be postponed
	 */
	public OneShareMsg(int agent, BigInteger share, boolean step) {
		super(ONE_SHARE_MSG);
		this.agent = agent;
		this.share = share;
		this.step = step;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(this.agent);
		assert this.share.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 0;
		out.writeInt(this.share.intValue());
		out.writeBoolean(this.step);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.agent = in.readInt();
		this.share = BigInteger.valueOf(in.readInt());
		this.step = in.readBoolean();
	}

	/** @return the sender agent */
	public int getAgent () {
		return this.agent;
	}
	
	/** @return the share */
	public BigInteger getShare () {
		return this.share;
	}

	/** @return Boolean used to identify received messages that belong to the next phase and should be postponed */
	public boolean step () {
		return this.step;
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString()
		+ "\n\t agent: " + this.agent
		+ "\n\t share: " + this.share
		+ "\n\t step: " + this.step;
	}
	
}
