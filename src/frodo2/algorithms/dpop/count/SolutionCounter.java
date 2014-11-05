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

/** A utility to count the number of optimal solutions in a DCOP */
package frodo2.algorithms.dpop.count;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

import org.jdom2.Document;
import org.jdom2.JDOMException;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;

/** Counts the number of optimal solutions in a DCOP
 * @author Brammert Ottens
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class SolutionCounter< V extends Addable<V>, U extends Addable<U> > {

	/** Description of the agent to be used */
	protected Document agentDesc;
	
	/** The reported variables*/
	public HashMap<String, String[]> variablesReported;
	
	/** The reported values*/
	public HashMap<String, ArrayList<V[]>> valuesReported;
	
	/** The optimal utility*/
	public U optimalUtil;
	
	/** The ordered variables*/
	public ArrayList<String> orderedVars;
	
	/** A list of all possible solutions */
	public ArrayList<V[]> allSolutions;

	/** The infeasible utility/cost */
	private U infeasibleUtil;
	
	/** Default constructor */
	@SuppressWarnings("unchecked")
	public SolutionCounter () {
		this((Class<U>) AddableInteger.class);
	}
	
	/** Constructor 
	 * @param utilClass 	the class to use for utilities
	 */
	public SolutionCounter (Class<U> utilClass) {
		try {
			this.agentDesc = XCSPparser.parse(AgentFactory.class.getResourceAsStream("/frodo2/algorithms/dpop/count/CountAgent.xml"), false);
			this.agentDesc.getRootElement().getChild("parser").setAttribute("utilClass", utilClass.getName());
		} catch (JDOMException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Solves the input problem
	 * @param problem 	the problem
	 * @return 			the number of optimal solutions
	 */
	@SuppressWarnings("unchecked")
	public int count (Document problem) {
		
		// Instantiate the modules that listen for the solution
		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (2);
		XCSPparser<V, U> parser = new XCSPparser<V, U> (problem);
		CountSolutionsUTIL<V, U> utilModule = new CountSolutionsUTIL<V, U> (null, parser);
		utilModule.setSilent(true);
		solGatherers.add(utilModule);
		CountSolutionsVALUE<V, U> valueModule = new CountSolutionsVALUE<V, U> (null, parser);
		valueModule.setSilent(true);
		solGatherers.add(valueModule);
		
		// Solve the problem
		this.agentDesc.getRootElement().getChild("parser").setAttribute("displayGraph", "false");
		new AgentFactory<V> (problem, agentDesc, solGatherers).end();
		
		variablesReported = valueModule.getReportedVariables();
		valuesReported = valueModule.getReportedValues();
		this.optimalUtil = utilModule.getOptUtil();
		this.infeasibleUtil = (parser.maximize() ? optimalUtil.getMinInfinity() : optimalUtil.getPlusInfinity());
		if(optimalUtil == this.infeasibleUtil) {
			return 0;
		}
		ArrayList<V[]> currentSolutions = new ArrayList<V[]>();
		currentSolutions.add((V[])Array.newInstance((valuesReported.get(valuesReported.keySet().toArray(new String[0])[0]).get(0)[0]).getClass(), 0));
		
		return combineSolutions(0, valueModule.getReportedValues().keySet().toArray(new String[0]), new ArrayList<String>(variablesReported.size()),currentSolutions, variablesReported, valuesReported);
	}
	
	/** 
	 * Method used to combine the different partial solutions, by recursively walking through the different leafs
	 * @param index					the index of the current leaf in \c leafs
	 * @param leafs					the list of leafs 
	 * @param variablesEncountered	the variables encountered so far 
	 * @param currentSolutions		the solutions found so far
	 * @param reportedVariables		for each leaf, the variables it reported
	 * @param reportedValues		for each leaf, the value assignments it reported
	 * @return the total number of solutions
	 */
	@SuppressWarnings("unchecked")
	private int combineSolutions(int index, String[] leafs, ArrayList<String> variablesEncountered, ArrayList<V[]> currentSolutions, HashMap<String, String[]> reportedVariables, HashMap<String, ArrayList<V[]>> reportedValues) {
		String var = leafs[index];
		ArrayList<V[]> newSolutions = new ArrayList<V[]>();
		
		String[] variables = reportedVariables.get(var);
		ArrayList<V[]> partialSolutions = reportedValues.get(var);
		int[] varIndexArray = new int[variables.length];
		for(int i = 0; i < variables.length; i++) {
			int varIndex = variablesEncountered.indexOf(variables[i]);
			if(varIndex == -1) {
				varIndexArray[i] = variablesEncountered.size();
				variablesEncountered.add(variables[i]);
			} else {
				varIndexArray[i] = varIndex;
			}
		}
		for(int i = 0; i < currentSolutions.size(); i++) {
			V[] solution = currentSolutions.get(i);
			for(int k = 0; k < partialSolutions.size(); k++) {
				V[] partialSolution = partialSolutions.get(k);
				V[] newSolution = (V[])Array.newInstance((valuesReported.get(valuesReported.keySet().toArray(new String[0])[0]).get(0)[0]).getClass(), variablesEncountered.size());
				System.arraycopy(solution, 0, newSolution, 0, solution.length);
				boolean add = true;
				for(int j = 0; j < variables.length; j++) {
					if(varIndexArray[j] < solution.length && solution[varIndexArray[j]] != partialSolution[j]) {
						add = false;
					} else{
						newSolution[varIndexArray[j]] = partialSolution[j];
					}
				}
				if(add)
					newSolutions.add(newSolution);
			}
		}
		
		if(index == leafs.length - 1) {
			if(newSolutions.size() == 0)
				assert this.optimalUtil == this.infeasibleUtil;
			orderedVars = variablesEncountered;
			allSolutions = newSolutions;
			return newSolutions.size();
		} else {
			int numberOfSolutions = combineSolutions(index + 1, leafs, variablesEncountered, newSolutions, reportedVariables, reportedValues);
			if(newSolutions.size() == 0)
				assert this.optimalUtil == this.infeasibleUtil;
			return numberOfSolutions;
		}
	}
	
}
