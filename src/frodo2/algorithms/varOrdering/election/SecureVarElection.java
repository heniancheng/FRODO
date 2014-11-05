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

package frodo2.algorithms.varOrdering.election;

import java.util.Collection;

import org.jdom2.Element;

import frodo2.algorithms.heuristics.RandScoringHeuristic;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** A version of VariableElection that uses random numbers as scores and lies in order to protect topology privacy
 * @author Eric Zbinden, Thomas Leaute
 */
public class SecureVarElection extends VariableElection<Integer> {
	
	/** The minimum number of times the agents should lie 
	 * 
	 * It must be greater than the diameter of the largest component of the constraint graph. 
	 */
	private int minNbrLies;
	
	/** Constructor from XML descriptions
	 * @param problem description of the problem
	 * @param parameters description of the parameters of this protocol
	 * @warning \a minNbrLies must be an upper bound the diameter of the largest component in the constraint graph for the algorithm to work properly. 
	 */
	public SecureVarElection (DCOPProblemInterface<?, ?> problem, Element parameters) {
		super(problem);
		
		// Extract the parameters
		minNbrLies = Integer.parseInt(parameters.getAttributeValue("minNbrLies"));
		nbrSteps = 3 * minNbrLies;
		heuristic = new RandScoringHeuristic (problem, null);
	}
	
	/** Alternate constructor
	 * @param problem 		the problem
	 * @param minNbrLies 	the minimum number of lies
	 */
	public SecureVarElection (DCOPProblemInterface<?, ?> problem, int minNbrLies) {
		super (problem);
		
		this.minNbrLies = minNbrLies;
		nbrSteps = 3 * minNbrLies;
		heuristic = new RandScoringHeuristic (problem, null);
	}
	
	/** Instantiates a new VarElectionMessenger
	 * @param var 			the variable
	 * @param score 		the score
	 * @param neighbors 	the variable's neighbors
	 * @return a new LeaderElectionMaxID
	 * @see VariableElection#newListener(String, Comparable, Collection) 
	 */
	@Override
	protected VarElectionMessenger newListener (String var, Integer score, Collection<String> neighbors) {
		return new VarElectionMessenger (var, score, neighbors, minNbrLies);
	}

}
