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
import java.util.HashMap;
import java.util.Map;

import frodo2.communication.Message;

/** A message containing shares of optimal values for some variables
 * @author Thomas Leaute
 */
public class SolShareMsg extends Message implements Externalizable {
	
	/** The type of the messages containing shares of optimal values for some variables */
	public static final String SOL_SHARE_MSG_TYPE = "SolutionShare";
	
	/** The ID of the sender agent */
	private int sender;
	
	/** For each variable, a share of its optimal value */
	private HashMap<String, BigInteger> shares;
	
	/** The step this message belongs to */
	private boolean step;
	
	/** Empty constructor used for externalization */
	public SolShareMsg() {
		super(SOL_SHARE_MSG_TYPE);
	}
	
	/** Constructor 
	 * @param sender 	The ID of the sender agent
	 * @param shares 	For each variable, a share of its optimal value
	 * @param step 		The step this message belongs to
	 */
	public SolShareMsg (int sender, HashMap<String, BigInteger> shares, boolean step) {
		super(SOL_SHARE_MSG_TYPE);
		this.sender = sender;
		this.shares = shares;
		this.step = step;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeInt(this.sender);
		
		out.writeInt(this.shares.size());
		for (Map.Entry<String, BigInteger> entry : this.shares.entrySet()) {
			out.writeObject(entry.getKey());
			assert entry.getValue().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 0;
			out.writeInt(entry.getValue().intValue());
		}
		
		out.writeBoolean(this.step);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		this.sender = in.readInt();
		
		final int nbr = in.readInt();
		this.shares = new HashMap<String, BigInteger> (nbr);
		for (int i = 0; i < nbr; i++) 
			this.shares.put((String) in.readObject(), BigInteger.valueOf(in.readInt()));
		
		this.step = in.readBoolean();
	}

	/** @return The ID of the sender agent */
	public int getSender() {
		return sender;
	}

	/** @return For each variable, a share of its optimal value */
	public HashMap<String, BigInteger> getShares() {
		return shares;
	}
	
	/** @return The step this message belongs to */
	public boolean step () {
		return this.step;
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString()
		+ "\n\t sender: " + this.sender
		+ "\n\t shares: " + this.shares
		+ "\n\t step: " + this.step;
	}

}
