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
import java.io.Serializable;

import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;

/** A message containing a DFS message for a particular candidate root, used by DFSgenerationParallel
 * @param <S> the type used for the root election scores
 */
public class ParallelDFSmsg < S extends Comparable <S> & Serializable > extends MessageWith2Payloads<S, Message> implements Externalizable {
	
	/** Used for externalization */
	public ParallelDFSmsg () {
		super.type = DFSgenerationParallel.PARALLEL_DFS_MSG_TYPE;
	}
	
	/** Constructor
	 * @param rootScore 	the score of the candidate root
	 * @param msg 			the message
	 */
	public ParallelDFSmsg (S rootScore, Message msg) {
		super (DFSgenerationParallel.PARALLEL_DFS_MSG_TYPE, rootScore, msg);
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		out.writeObject(this.getRoot());
		
		Message msg = this.getMessage();
		out.writeObject(msg.getClass());
		msg.writeExternal(out);
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		super.setPayload1((S) in.readObject());
		
		Class<? extends Message> msgClass = (Class<? extends Message>) in.readObject();
		Message msg = null;
		try {
			msg = msgClass.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		msg.readExternal(in);
		super.setPayload2(msg);
	}
	
	/** @return the score of the candidate root */
	public S getRoot() {
		return super.getPayload1();
	}
	
	/** @return the message contained in this wrapper */
	public Message getMessage() {
		return super.getPayload2();
	}
}