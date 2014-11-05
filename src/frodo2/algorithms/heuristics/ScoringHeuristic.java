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

/** Classes implementing heuristics that can be used, for instance, to guide the generation of variable orderings */
package frodo2.algorithms.heuristics;

import java.io.Serializable;
import java.util.Map;

/** Interface for a heuristic that associates a score to every variable
 * 
 * This heuristic can be used to construct variable orderings. 
 * @author Thomas Leaute
 * @param <S> the type used for the scores
 * @note All such heuristics should have a constructor that takes in a DCOPProblemInterface describing the agent's problem 
 * and an Element describing the parameters of the heuristic. 
 */
public interface ScoringHeuristic < S extends Comparable<S> & Serializable > {
	
	/** @return the scores for the variables */
	public Map<String, S> getScores ();

}
