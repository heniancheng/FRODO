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

package frodo2.algorithms.heuristics;

import java.io.Serializable;

/** A pair of scores, the second being used for tiebreaking
 * @param <S1> the type used for the first score
 * @param <S2> the type used for the tiebreaking score
 */
public class ScorePair < S1 extends Comparable<S1> & Serializable, S2 extends Comparable<S2> & Serializable > 
	implements Comparable< ScorePair<S1, S2> >, Serializable {
	
	/** Used for serialization */
	private static final long serialVersionUID = -1626612391889900606L;

	/** The first score */
	private S1 score1;
	
	/** The tiebreaking score */
	private S2 score2;

	/** Constructor
	 * @param score1 	the first score
	 * @param score2 	the tiebreaking score
	 */
	public ScorePair(S1 score1, S2 score2) {
		super();
		this.score1 = score1;
		this.score2 = score2;
	}

	/** @see java.lang.Comparable#compareTo(java.lang.Object) */
	public int compareTo(ScorePair<S1, S2> other) {
		
		int diff1 = this.score1.compareTo(other.score1);
		if (diff1 != 0) 
			return diff1;
		else 
			return this.score2.compareTo(other.score2);
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals (Object object) {
		
		if (this == object) 
			return true;
		
		// Attempt to cast the input into a ScorePair
		ScorePair<S1, S2> other = null;
		try {
			other = (ScorePair<S1, S2>) object;
		} catch (ClassCastException e) {
			return false;
		}
		
		return (this.score1.equals(other.score1) && this.score2.equals(other.score2));
	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode () {
		return this.score1.hashCode() + this.score2.hashCode();
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return "[" + this.score1 + "; " + this.score2 + "]";
	}
}