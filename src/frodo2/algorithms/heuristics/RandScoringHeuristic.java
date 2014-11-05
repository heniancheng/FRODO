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

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

import org.jdom2.Element;

import frodo2.solutionSpaces.DCOPProblemInterface;

/** A ScoringHeuristic that assigns random scores to variables
 * @author Thomas Leaute
 */
public class RandScoringHeuristic implements ScoringHeuristic<Integer> {

	/** The problem */
	private DCOPProblemInterface<?, ?> problem;
	
	/** Constructor
	 * @param problem 	the problem
	 * @param unused 	unused parameters
	 */
	public RandScoringHeuristic (DCOPProblemInterface<?, ?> problem, Element unused) {
		this.problem = problem;
	}

	/** @see ScoringHeuristic#getScores() */
	public Map<String, Integer> getScores() {
		
		// Create a random stream
		Random rand = new SecureRandom();
		
		int nbrVars = this.problem.getNbrVars();
		HashMap<String, Integer> scores = new HashMap<String, Integer> (nbrVars);
		HashSet<Integer> allScores = new HashSet<Integer> (nbrVars);

		for (String var : this.problem.getVariables()) {
			
			// Pick a random score for this variable, avoiding collisions
			int score = rand.nextInt();
			while (! allScores.add(score)) {
				score = rand.nextInt();
			}
			
			scores.put(var, score);
		}
		
		return scores;
	}

}
