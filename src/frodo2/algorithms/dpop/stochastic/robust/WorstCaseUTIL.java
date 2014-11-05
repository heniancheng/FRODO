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

/** StochDCOP algorithms that maximize worst-case rather than expected utility */
package frodo2.algorithms.dpop.stochastic.robust;

import java.util.ArrayList;
import java.util.HashSet;

import org.jdom2.Element;

import frodo2.algorithms.dpop.stochastic.ExpectedUTIL;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;

/** A UTIL propagation phase that maximizes worst-case utility with respect to the random variables
 * @author Thomas Leaute
 * @param <Val> the type used for variable values
 * @param <U> 	the type used for utility values
 */
public class WorstCaseUTIL < Val extends Addable<Val>, U extends Addable<U> > extends ExpectedUTIL<Val, U> {
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported
	 */
	public WorstCaseUTIL(Element parameters, DCOPProblemInterface<Val, U> problem) {
		super(parameters, problem);
	}

	/** Constructor 
	 * @param problem 		the agent's subproblem
	 * @param parameters 	the parameters for the module
	 */
	public WorstCaseUTIL(DCOPProblemInterface<Val, U> problem, Element parameters) {
		super(problem, parameters);
	}
	
	/** @see ExpectedUTIL#parseMethod(Element) */
	@Override
	protected void parseMethod (Element parameters) {
		super.method = null;
	}

	/** @see ExpectedUTIL#project(UtilitySolutionSpace, String[]) */
	@Override 
	protected ProjOutput<Val, U> project (UtilitySolutionSpace<Val, U> space, String[] vars) {
		
		// Look up all random variables, and check whether they must all be projected out here
		ArrayList<String> allRandVars = new ArrayList<String> (space.getNumberOfVariables());
		assert vars.length == 1 : "Clusters not supported"; /// @todo Add support for clusters
		HashSet<String> projHere = this.randVarsProj.get(vars[0]);
		boolean projectAll = true;
		for (String randVar : space.getVariables()) {
			if (this.problem.isRandom(randVar)) {
				allRandVars.add(randVar);
				
				if (! projHere.contains(randVar)) 
					projectAll = false;
			}
		}
		
		// Then compute the worst case for the space over all random variables
		UtilitySolutionSpace<Val, U> worst = space;
		switch (allRandVars.size()) {
		case 0: 
			return space.project(vars, this.maximize); // no random variable; we can simply project
		case 1: 
			worst = worst.blindProject(allRandVars.get(0), ! this.maximize);
			break;
		default:
			worst = worst.blindProject(allRandVars.toArray(new String [allRandVars.size()]), ! this.maximize);
		}
		
		// If all random variables must be projected here, just return the projection
		if (projectAll) 
			return worst.project(vars, maximize);
		
		// Else, compute the optimal assignments for the projected out variable
		BasicUtilitySolutionSpace< Val, ArrayList<Val> > assignments = worst.project(vars, maximize).assignments; /// @todo Implement an argProject() method
		worst = null;

		// Report the corresponding true utilities, as a function of all random variables
		return new ProjOutput<Val, U> (space.compose(vars, assignments), vars, assignments);
	}
	
	/** @see ExpectedUTIL#sendToParent(String, String, String, UtilitySolutionSpace) */
	@Override 
	protected void sendToParent (String var, String parentVar, String parentAgent, UtilitySolutionSpace<Val, U> space) {
		
		// Before sending the UTIL message, we need to project out the random variables
		ArrayList<String> randVars = new ArrayList<String> ();
		for (String randVar : this.randVarsProj.get(var)) 
			if (space.getDomain(randVar) != null) // the space contains this random variable
				randVars.add(randVar);
		if (! randVars.isEmpty()) 
			space = space.blindProject(randVars.toArray(new String [randVars.size()]), ! this.maximize);

		super.sendToParent(var, parentVar, parentAgent, space);
	}
	
	/** @see ExpectedUTIL#sendOutput(UtilitySolutionSpace, java.lang.String) */
	@Override
	protected void sendOutput(UtilitySolutionSpace<Val, U> space, String root) {
		
		// First project out all random variables
		ArrayList<String> randVars = new ArrayList<String> (space.getNumberOfVariables());
		for (String randVar : space.getVariables()) 
			if (this.problem.isRandom(randVar)) 
				randVars.add(randVar);
		if (! randVars.isEmpty()) 
			space = space.blindProject(randVars.toArray(new String [randVars.size()]), ! this.maximize);

		super.sendOutput(space, root);
	}
	
}
