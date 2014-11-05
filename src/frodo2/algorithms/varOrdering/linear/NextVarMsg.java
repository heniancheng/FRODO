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

package frodo2.algorithms.varOrdering.linear;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;

import frodo2.communication.Message;

/** A message containing the next variable chosen for a given component  */
public class NextVarMsg extends Message implements Externalizable {
	
	/** The set of agents that still have variables to propose */
	HashSet<String> agents;

	/** The next variable chosen for the given component */
	String nextVar;
	
	/** Empty constructor used for externalization */
	public NextVarMsg () {
		super (LinearOrdering.NEXT_VAR_MSG_TYPE);
	}
	
	/** Constructor
	 * @param agents 		The set of agents that still have variables to propose
	 * @param nextVar 		The next Variable
	 */
	public NextVarMsg(HashSet<String> agents, String nextVar) {
		super(LinearOrdering.NEXT_VAR_MSG_TYPE);
		this.agents = agents;
		this.nextVar = nextVar;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		assert this.agents.size() < Short.MAX_VALUE;
		out.writeShort(this.agents.size());
		for (String agent : this.agents) 
			out.writeObject(agent);
		out.writeObject(this.nextVar);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int nbrAgents = in.readShort();
		this.agents = new HashSet<String> (nbrAgents);
		for (int i = 0; i < nbrAgents; i++) 
			this.agents.add((String) in.readObject());
		this.nextVar = (String) in.readObject();
	}
}