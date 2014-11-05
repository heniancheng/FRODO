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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;

import frodo2.solutionSpaces.DCOPProblemInterface;

/** A scoring heuristic that supports tiebreaking
 * @author Thomas Leaute
 * @param <S1> the type used for the first score
 * @param <S2> the type used for the tiebreaking score
 */
public class ScoringHeuristicWithTiebreaker < S1 extends Comparable<S1> & Serializable, S2 extends Comparable<S2> & Serializable > 
	implements ScoringHeuristic< ScorePair<S1, S2> > {
	
	/** The first heuristic */
	private ScoringHeuristic<S1> heuristic1;
	
	/** The tiebreaking heuristic */
	private ScoringHeuristic<S2> heuristic2;
	
	/** Constructor
	 * @param heuristic1 	The first heuristic
	 * @param heuristic2 	The tiebreaking heuristic
	 */
	public ScoringHeuristicWithTiebreaker (ScoringHeuristic<S1> heuristic1, ScoringHeuristic<S2> heuristic2) {
		this.heuristic1 = heuristic1;
		this.heuristic2 = heuristic2;
	}
	
	/** Constructor
	 * @param problem 	The agent's problem
	 * @param params 	The parameters
	 * @throws ClassNotFoundException 		if a ScoringHeuristic classe is not found
	 * @throws NoSuchMethodException 		if a ScoringHeuristic does not have a constructor with the signature (DCOPProblemInterface, Element)
	 * @throws InstantiationException 		if a ScoringHeuristic is abstract
	 * @throws IllegalAccessException 		if a ScoringHeuristic's constructor is inaccessible
	 * @throws InvocationTargetException 	if a ScoringHeuristic's constructor throws an exception
	 */
	@SuppressWarnings("unchecked")
	public ScoringHeuristicWithTiebreaker (DCOPProblemInterface<?, ?> problem, Element params) 
	throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		// Create the first heuristic
		Element elmt = params.getChild("heuristic1");
		String className = elmt.getAttributeValue("className");
		Class< ? extends ScoringHeuristic<S1> > heuristicClass = (Class<? extends ScoringHeuristic<S1>>) Class.forName(className);
		Constructor< ? extends ScoringHeuristic<S1> > constructor = heuristicClass.getConstructor(DCOPProblemInterface.class, Element.class);
		this.heuristic1 = constructor.newInstance(problem, elmt);
		
		// Create the tiebreaking heuristic
		elmt = params.getChild("heuristic2");
		if (elmt == null) 
			this.heuristic2 = (ScoringHeuristic<S2>) new VarNameHeuristic (problem, elmt);
		else {
			className = elmt.getAttributeValue("className");
			Class< ? extends ScoringHeuristic<S2> > heuristicClass2 = (Class<? extends ScoringHeuristic<S2>>) Class.forName(className);
			Constructor< ? extends ScoringHeuristic<S2> > constructor2 = heuristicClass2.getConstructor(DCOPProblemInterface.class, Element.class);
			this.heuristic2 = constructor2.newInstance(problem, elmt);
		}
	}

	/** @see ScoringHeuristic#getScores() */
	public Map< String, ScorePair<S1, S2> > getScores() {
		
		Map<String, S1> scores1 = this.heuristic1.getScores();
		Map<String, S2> scores2 = this.heuristic2.getScores();
		
		HashMap< String, ScorePair<S1, S2> > scores = new HashMap< String, ScorePair<S1, S2> > (scores1.size());
		for (Map.Entry<String, S1> entry1 : scores1.entrySet()) {
			String var = entry1.getKey();
			scores.put(var, new ScorePair<S1, S2> (entry1.getValue(), scores2.get(var)));
		}
		
		return scores;
	}

}
