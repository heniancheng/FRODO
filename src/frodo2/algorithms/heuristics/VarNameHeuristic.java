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

/** A scoring heuristic that returns the variables' names
 * @author Thomas Leaute
 */
public class VarNameHeuristic implements ScoringHeuristic<String> {
	
	/** The problem */
	private DCOPProblemInterface<?, ?> problem;
	
	/** Constructor
	 * @param problem 	the problem
	 * @param unused 	unused parameters
	 */
	public VarNameHeuristic (DCOPProblemInterface<?, ?> problem, Element unused) {
		this.problem = problem;
	}

	/** @see ScoringHeuristic#getScores() */
	public Map<String, String> getScores() {
		
		HashMap<String, String> scores = new HashMap<String, String> (this.problem.getNbrVars());
		for (String var : this.problem.getVariables()) 
			scores.put(var, var);
		
		return scores;
	}

}
