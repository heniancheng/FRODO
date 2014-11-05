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

/** A message containing the score of a single variable
 * @param <S> the type used for the scores
 */
public class ScoreMsg<S extends Serializable> extends Message implements Externalizable {
	
	/** The variable */
	String var;
	
	/** The score associated with the given variable */
	S score;
	
	/** Empty constructor used for externalization */
	public ScoreMsg () {
		super (DFSgeneration.ScoreBroadcastingHeuristic.SCORE_SINGLE_VAR_MSG_TYPE);
	}
	
	/** Constructor
	 * @param var 		The variable
	 * @param score 	The score associated with the given variable
	 */
	public ScoreMsg (String var, S score) {
		super (DFSgeneration.ScoreBroadcastingHeuristic.SCORE_SINGLE_VAR_MSG_TYPE);
		this.var = var;
		this.score = score;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(this.var);
		out.writeObject(this.score);
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.var = (String) in.readObject();
		this.score = (S) in.readObject();
	}
	
	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\t var: " + this.var + "\n\t score: " + this.score;
	}
}