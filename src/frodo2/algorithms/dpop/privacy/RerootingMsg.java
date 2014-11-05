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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.MessageWithPayload;

/** Message used to send a reroot request */
public class RerootingMsg extends MessageWithPayload<String> implements Externalizable{
	
	/** Constructor
	 * @param dest 	the destination of this msg
	 */
	public RerootingMsg(String dest){
		this.type = SecureRerooting.REROOTING_REQUEST_TYPE;
		this.setPayload(dest);
	}
	
	/**
	 * Empty constructor used for externalization
	 */
	public RerootingMsg(){	
		this.type = SecureRerooting.REROOTING_REQUEST_TYPE;
	}
	
	/** @return the destination*/
	public String getDest(){
		return this.getPayload();
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		this.setPayload((String)in.readObject());	
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.getPayload());
	}	
	

	/** @see frodo2.communication.MessageWithPayload#toString() */
	public String toString(){
		return "RerootingMsg:\n\tdest: "+getPayload();
	}
}
