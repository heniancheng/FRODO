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
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.StatsReporter;
import frodo2.solutionSpaces.Addable;

/** The solver for Comp-E[DPOP]
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class Complete_E_DPOPsolver < V extends Addable<V>, U extends Addable<U> > extends E_DPOPsolver<V, U> {
	
	/** The UTIL propagation module */
	private CompleteUTIL<V, U> utilModule;

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public Complete_E_DPOPsolver(Document agentDesc, boolean useTCP) {
		super(agentDesc, useTCP);
	}

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public Complete_E_DPOPsolver(Document agentDesc, Class<V> domClass, boolean useTCP) {
		super(agentDesc, domClass, useTCP);
	}

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 */
	public Complete_E_DPOPsolver(Document agentDesc, Class<V> domClass) {
		super(agentDesc, domClass);
	}

	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public Complete_E_DPOPsolver(Document agentDesc) {
		super(agentDesc);
	}

	/** @see E_DPOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (2);

		for (Element elmt : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
			String className = elmt.getAttributeValue("className");
			
			if (className.equals(CompleteUTIL.class.getName())) {
				utilModule = new CompleteUTIL<V, U> (elmt, problem);
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

	/** @see E_DPOPsolver#buildSolution() */
	@Override
	public StochSolution<V, U> buildSolution() {
		
		this.dfsString = (this.samplingModule == null ? "" : this.samplingModule.dfsToString());
		
		return new StochSolution<V, U> (problem.getNbrVars(), utilModule.getOptUtil(), utilModule.getExpectedUtil(), utilModule.getWorstUtil(), utilModule.getProbOfOptimality(), utilModule.getCentralization(), 
				utilModule.getSolution(), factory.getNbrMsgs(), factory.getMsgNbrs(), factory.getTotalMsgSize(), factory.getMsgSizes(), factory.getOverallMaxMsgSize(), factory.getMaxMsgSizes(), factory.getNcccs(), factory.getTime(), null, utilModule.getMaxMsgDim());
	}
	
	/** @see E_DPOPsolver#clear() */
	@Override
	public void clear () {
		super.clear();
		this.utilModule = null;
	}
	
}
