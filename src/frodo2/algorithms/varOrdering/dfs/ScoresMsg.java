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
import java.util.HashMap;
import java.util.Map;

import frodo2.communication.Message;

/** A message containing the scores of given variables
 * @param <S> the type used for the scores
 */
public class ScoresMsg<S extends Serializable> extends Message implements Externalizable {
	
	/** The scores of some variables */
	HashMap<String, S> scores;
	
	/** Empty constructor used for externalization */
	public ScoresMsg () {
		super (DFSgeneration.ScoreBroadcastingHeuristic.SCORE_MSG_TYPE);
	}
	
	/** Constructor
	 * @param scores 	The scores of some variables
	 */
	public ScoresMsg (HashMap<String, S> scores) {
		super (DFSgeneration.ScoreBroadcastingHeuristic.SCORE_MSG_TYPE);
		this.scores = scores;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		
		assert this.scores.size() < Short.MAX_VALUE;
		out.writeShort(this.scores.size());
		for (Map.Entry<String, S> entry : this.scores.entrySet()) {
			out.writeObject(entry.getKey());
			out.writeObject(entry.getValue());
		}
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		int nbrVars = in.readShort();
		this.scores = new HashMap<String, S> (nbrVars);
		for (int i = 0; i < nbrVars; i++) {
			String var = (String) in.readObject();
			this.scores.put(var, (S) in.readObject());
		}
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\t scores: " + this.scores;
	}
}