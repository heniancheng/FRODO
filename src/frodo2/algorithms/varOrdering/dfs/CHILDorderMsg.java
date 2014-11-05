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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

/** Child token including the order of visit of the recipient variable in the graph */
public class CHILDorderMsg extends CHILDmsg{
	
	/** Used for serialization */
	private static final long serialVersionUID = -6158434988997871871L;
	
	/** Order of visit of the recipient variable in the graph */
	protected int order;
	
	/** Empty constructor */
	public CHILDorderMsg () {
		super.type = DFSgenerationWithOrder.CHILD_ORDER_MSG_TYPE;
	}

	/** Constructor 
	 * @param sender 	sender variable
	 * @param dest 		recipient variable
	 * @param rootID 	the root ID
	 * @param order		order of recipient variable
	 */
	public CHILDorderMsg (String sender, String dest, Serializable rootID, int order) {
		super (sender, dest, rootID);
		this.order = order;
		super.type = DFSgenerationWithOrder.CHILD_ORDER_MSG_TYPE;
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		assert this.getOrder() < Short.MAX_VALUE;
		out.writeShort(this.getOrder());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.order = in.readShort();
	}
	
	/** @return the order */
	public Integer getOrder () {
		return this.order;
	}
	
	/** @see CHILDmsg#toString()  */
	@Override
	public String toString(){
		return "DFSwithOrderChildToken:\n"+
				"\tSender: "+this.getSender()+"\n"+
				"\tReceiver: "+this.getDest()+"\n"+
				"\tOrder: "+this.getOrder();
	}
}