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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWith2Payloads;

/** Msg sent to transfer through the DFS in top-down way the total number of variables in this component */
public class VarNbrMsg extends MessageWith2Payloads<Integer,String> implements Externalizable {
	
	/** Empty constructor */
	public VarNbrMsg () {
		super.type = DFSgenerationWithOrder.VARIABLE_COUNT_TYPE;
	}

	/**
	 * Constructor
	 * @param total 	the total number of variables in this component
	 * @param dest 		the destination of this message
	 */
	protected VarNbrMsg(Integer total, String dest){
		super(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE, total, dest);
	}
	
	/** @return the total number of variables in this component */
	public int getTotal(){
		return this.getPayload1();
	}
	
	/** @return the destination of this msg */
	public String getDest(){
		return this.getPayload2();
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(this.getTotal());
		out.writeObject(this.getDest());
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.setPayload1(in.readInt());
		this.setPayload2((String) in.readObject());
	}	
	
}