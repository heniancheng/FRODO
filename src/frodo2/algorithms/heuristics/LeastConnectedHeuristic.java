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

import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;

import frodo2.solutionSpaces.DCOPProblemInterface;

/** A ScoringHeuristic in which the score for a variable is MINUS its number of neighbors
 * @author Thomas Leaute
 */
public class LeastConnectedHeuristic implements ScoringHeuristic<Short> {
	
	/** The agent's problem */
	private DCOPProblemInterface<?, ?> problem;

	/** Constructor
	 * @param problem 	The agent's problem
	 * @param params 	unused
	 */
	public LeastConnectedHeuristic (DCOPProblemInterface<?, ?> problem, Element params) {
		this.problem = problem;
	}

	/** @see ScoringHeuristic#getScores() */
	public Map<String, Short> getScores() {
		
		Map<String, Short> scores = new HashMap<String, Short> ();
		for (Map.Entry<String, Integer> entry : this.problem.getNeighborhoodSizes().entrySet()) {
			assert entry.getValue() < - Short.MIN_VALUE : "Too many neighbors for variable " + entry.getKey();
			scores.put(entry.getKey(), (short) - entry.getValue().shortValue());
		}
		
		return scores;
	}

}
