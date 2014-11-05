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
import java.util.Arrays;

import frodo2.communication.Message;

/** In MPC-DisCSP4, a message containing a vector of ElGamal-encrypted shares
 * @author Thomas Leaute
 */
public class EncrSharesMsg extends Message implements Externalizable {
	
	/** The type of this message */
	public final static String ENCR_SHARES_MSG_TYPE = "EncrSharesMsg";
	
	/** The encrypted shares */
	private PaillierInteger[] shares;
	
	/** The public key used to encrypt */
	private PaillierPublicKey publicKey;
	
	/** The index of the agent owning the shares */
	private int agentID;
	
	/** The step this message belongs to */
	private boolean step;

	/** Constructor
	 * @param shares 		the encrypted shares
	 * @param publicKey 	The public key used to encrypt
	 * @param agentID 		The index of the agent owning the shares
	 * @param step 			the step this message belongs to
	 */
	public EncrSharesMsg(PaillierInteger[] shares, PaillierPublicKey publicKey, int agentID, boolean step) {
		super(ENCR_SHARES_MSG_TYPE);
		this.shares = shares;
		this.publicKey = publicKey;
		this.agentID = agentID;
		this.step = step;
	}

	/** Empty constructor used for externalization */
	public EncrSharesMsg() {
		super.type = ENCR_SHARES_MSG_TYPE;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(this.publicKey);
		
		out.writeInt(this.shares.length);
		for (int i = this.shares.length - 1; i >= 0; i--) 
			this.shares[i].writeExternal(out);
		
		out.writeInt(this.agentID);
		
		out.writeBoolean(this.step);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		this.publicKey = (PaillierPublicKey) in.readObject();
		
		int size = in.readInt();
		this.shares = new PaillierInteger [size];
		PaillierInteger tmp;
		for (int i = size - 1; i >= 0; i--) {
			tmp = new PaillierInteger ();
			this.shares[i] = tmp;
			tmp.readExternal(in);
			tmp.nSquare = this.publicKey.nsquare;
		}
		
		this.agentID = in.readInt();
		
		this.step = in.readBoolean();
	}

	/** @return the encrypted shares */
	public PaillierInteger[] getShares() {
		return shares;
	}

	/** @return the public key used for encryption */
	public PaillierPublicKey getPublicKey() {
		return publicKey;
	}

	/** @return the agentID */
	public int getAgentID() {
		return agentID;
	}

	/** @return the step this message belongs to */
	public boolean step () {
		return this.step;
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString()
		+ "\n\t agentID: " + this.agentID
		+ "\n\t publicKey: " + this.publicKey
		+ "\n\t shares: " + Arrays.toString(this.shares)
		+ "\n\t step: " + this.step;
	}
	
}
