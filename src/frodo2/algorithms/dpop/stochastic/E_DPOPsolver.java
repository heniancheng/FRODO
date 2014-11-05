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

package frodo2.algorithms.dpop.stochastic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.stochastic.robust.WorstCaseUTIL;
import frodo2.solutionSpaces.Addable;

/** A StochDCOP solver using E[DPOP]
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class E_DPOPsolver < V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, E_DPOPsolver.StochSolution<V, U> > {
	
	/** A solution to a StochDCOP, including the expected and worst-case utilities
	 * @author Thomas Leaute
	 * @param <V> type used for variable values
	 * @param <U> type used for utility values
	 */
	public static class StochSolution <V, U> extends Solution <V, U> {
		
		/** The worst-case utility */
		private U worstUtil;
		
		/** The expected utility */
		private U expectedUtil;
		
		/** The total probability of the scenarios for which this solution is optimal */
		private U probOfOptimality;
		
		/** The level of centralization */
		private double centralization;

		/** Constructor 
		 * @param nbrVariables		the total number of variables in the problem
		 * @param reportedUtil 		the total utility reported by the roots
		 * @param expectedUtil 		the expected utility
		 * @param worstUtil 		the worst-case utility
		 * @param probOfOptimality 	the total probability of the scenarios for which this solution is optimal
		 * @param centralization 	the level of centralization incurred by using a consistent DFS (for the Comp- approach)
		 * @param assignments 		the optimal assignments
		 * @param nbrMsgs			the total number of messages sent
		 * @param msgNbrs			The number of messages sent per message type
		 * @param totalMsgSize		the total message size
		 * @param msgSizes 			The amount of information sent per message type
		 * @param maxMsgSize 		the size (in bytes) of the largest message
		 * @param maxMsgSizes 		for each message type, the size (in bytes) of the largest message of that type
		 * @param ncccCount 		the ncccs used
		 * @param timeNeeded 		the time needed to solve the problem
		 * @param moduleEndTimes 	each module's end time
		 * @param treeWidth 		the width of the tree on which the algorithm has run
		 */
		public StochSolution (int nbrVariables, U reportedUtil, U expectedUtil, U worstUtil, U probOfOptimality, double centralization, Map<String, V> assignments, 
				int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, long maxMsgSize, TreeMap<String, Long> maxMsgSizes, 
				long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int treeWidth) {
			super(nbrVariables, reportedUtil, null, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, treeWidth, 0);
			this.worstUtil = worstUtil;
			this.expectedUtil = expectedUtil;
			this.probOfOptimality = probOfOptimality;
			this.centralization = centralization;
		}
		
		/** @return the expected utility */
		public U getExpUtil () {
			return this.expectedUtil;
		}
		
		/** @return the worst-case utility */
		public U getWorstUtil () {
			return this.worstUtil;
		}
		
		/** @return the total probability of the scenarios for which this solution is optimal */
		public U getProbOfOptimality () {
			return this.probOfOptimality;
		}
		
		/** @return the level of centralization */
		public double getCentralization () {
			return this.centralization;
		}
		
		/** @see Solution#toString() */
		@Override
		public String toString () {
			StringBuilder builder = new StringBuilder (super.toString());
			builder.append("\n");
			builder.append("\n\t- expectedUtil:     " + this.expectedUtil);
			builder.append("\n\t- worstUtil:        " + this.worstUtil);
			builder.append("\n\t- probOfOptimality: " + this.probOfOptimality);
			builder.append("\n\t- level of centralization: " + (this.centralization * 100) + " %");
			return builder.toString();
		}
	}

	/** The ExpectedUTIL module */
	private ExpectedUTIL<V, U> utilModule;
	
	/** The SamplingPhase module, used to display the DFS */
	protected SamplingPhase<V, U> samplingModule;
	
	/** A DOT representation of the DFS used */
	protected String dfsString;

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public E_DPOPsolver(Document agentDesc) {
		super(agentDesc);
	}

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public E_DPOPsolver(Document agentDesc, boolean useTCP) {
		super(agentDesc, useTCP);
	}

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 */
	public E_DPOPsolver(Document agentDesc, Class<V> domClass) {
		super(agentDesc);
		super.setDomClass(domClass);
	}

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public E_DPOPsolver(Document agentDesc, Class<V> domClass, boolean useTCP) {
		super(agentDesc, useTCP);
		super.setDomClass(domClass);
	}

	/** Constructor
	 * @param agentDesc 	the path to the agent description file
	 */
	public E_DPOPsolver(String agentDesc) {
		super(agentDesc);
	}

	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (2);

		for (Element elmt : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
			String className = elmt.getAttributeValue("className");
			
			if (className.equals(UTILpropagation.class.getName()) || className.equals(ExpectedUTIL.class.getName()) || className.equals(WorstCaseUTIL.class.getName()) || className.equals(CompleteUTIL.class.getName())) {
				utilModule = new ExpectedUTIL<V, U> (elmt, problem);
				utilModule.setSilent(true);
				solGatherers.add(utilModule);
							
			} else if (className.endsWith("AtLCAs")) { // to display the DFS
//				elmt.setAttribute("DOTrenderer", DOTrenderer.class.getName());
				samplingModule = new SamplingPhase<V, U> (elmt, problem);
				samplingModule.setSilent(true); // comment this to display the DFS
				solGatherers.add(samplingModule);
			}
		}
		
		return solGatherers;
	}

		
	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public StochSolution<V, U> buildSolution() {
		
		this.dfsString = (this.samplingModule == null ? "" : this.samplingModule.dfsToString());
		
		return new StochSolution<V, U> (problem.getNbrVars(), utilModule.getOptUtil(), utilModule.getExpectedUtil(), utilModule.getWorstUtil(), utilModule.getProbOfOptimality(), 0.0, utilModule.getSolution(), 
				factory.getNbrMsgs(), factory.getMsgNbrs(), factory.getTotalMsgSize(), factory.getMsgSizes(), factory.getOverallMaxMsgSize(), factory.getMaxMsgSizes(), factory.getNcccs(), factory.getTime(), null, utilModule.getMaxMsgDim());
	}
	
	/** @return a DOT representation of the DFS used */
	public String dfsToString() {
		return this.dfsString;
	}
	
	/** @see AbstractDCOPsolver#clear() */
	@Override
	public void clear () {
		super.clear();
		this.utilModule = null;
		this.samplingModule = null;
	}
	
}
