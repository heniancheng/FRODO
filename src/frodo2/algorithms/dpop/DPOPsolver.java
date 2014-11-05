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

/** Classes implementing the DPOP algorithm and its variants */
package frodo2.algorithms.dpop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.gui.DOTrenderer;
import frodo2.solutionSpaces.Addable;

/** A DCOP solver using DPOP
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class DPOPsolver< V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, Solution<V, U> > {

	/** The UTIL propagation module */
	protected UTILpropagation<V, U> utilModule;

	/** The VALUE propagation module */
	protected VALUEpropagation<V> valueModule;

	/** The DFSgeneration module */
	protected DFSgeneration<V, U> dfsModule;
	
	/** Default constructor */
	public DPOPsolver () {
		super ("/frodo2/algorithms/dpop/DPOPagent.xml");
	}
	
	/** Constructor 
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver (boolean useTCP) {
		super ("/frodo2/algorithms/dpop/DPOPagent.xml", useTCP);
	}
	
	/**
	 * Constructor
	 * 
	 * @param agentDescription	Description of the DPOP agent
	 */
	public DPOPsolver(String agentDescription) {
		super(agentDescription);
	}
	
	/**
	 * Constructor
	 * 
	 * @param agentDescription	Description of the DPOP agent
	 * @param useTCP 			whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver(String agentDescription, boolean useTCP) {
		super(agentDescription, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public DPOPsolver (Class<V> domClass, Class<U> utilClass) {
		this(domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super ("/frodo2/algorithms/dpop/DPOPagent.xml", useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/**
	 * Constructor
	 * @param agentDesc the agent description
	 */
	public DPOPsolver(Document agentDesc) {
		super(agentDesc);
	}
	
	/**
	 * Constructor
	 * @param agentDesc the agent description
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver(Document agentDesc, boolean useTCP) {
		super(agentDesc, useTCP);
	}
	
	/** Constructor
	 * @param agentDesc the agent description
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public DPOPsolver(Document agentDesc, Class<V> domClass, Class<U> utilClass) {
		this(agentDesc, domClass, utilClass, false);
	}
	
	/** Constructor
	 * @param agentDesc the agent description
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver(Document agentDesc, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super(agentDesc, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param parserClass	the class used to parse problems	
	 */
	public DPOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass) {
		super (agentDesc, parserClass);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param parserClass	the class used to parse problems	
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass, boolean useTCP) {
		super (agentDesc, parserClass, useTCP);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public DPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		this(agentDescFile, domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public DPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super (agentDescFile, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (3);
		
		utilModule = new UTILpropagation<V, U>(null, problem);
		utilModule.setSilent(true);
		solGatherers.add(utilModule);
		
		valueModule = new VALUEpropagation<V> (null, problem);
		valueModule.setSilent(true);
		solGatherers.add(valueModule);
		
		Element params = new Element ("module");
		params.setAttribute("DOTrenderer", DOTrenderer.class.getName());
		dfsModule = new DFSgeneration<V, U> (params, problem);
		dfsModule.setSilent(true);
		solGatherers.add(dfsModule);
		
		return solGatherers;
	}
	
	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public Solution<V, U> buildSolution() {

		U optUtil = utilModule.getOptUtil();
		if (optUtil == null) 
			optUtil = super.problem.getZeroUtility();
		
		Map<String, V>  solution = valueModule.getSolution();
		int nbrMsgs = factory.getNbrMsgs();
		TreeMap<String, Integer> msgNbrs = factory.getMsgNbrs();
		long totalMsgSize = factory.getTotalMsgSize();
		TreeMap<String, Long> msgSizes = factory.getMsgSizes();
		long maxMsgSize = factory.getOverallMaxMsgSize();
		TreeMap<String, Long> maxMsgSizes = factory.getMaxMsgSizes();
		long ncccs = factory.getNcccs();
		int maxMsgDim = utilModule.getMaxMsgDim();
		int numberOfCoordinationConstraints = problem.getNumberOfCoordinationConstraints();
		int nbrVariables = problem.getNbrVars();
		
		HashMap<String, Long> timesNeeded = new HashMap<String, Long> ();
		timesNeeded.put(dfsModule.getClass().getName(), dfsModule.getFinalTime());
		timesNeeded.put(utilModule.getClass().getName(), utilModule.getFinalTime());
		timesNeeded.put(valueModule.getClass().getName(), valueModule.getFinalTime());
		long totalTime = factory.getTime();
		
		return new Solution<V, U> (nbrVariables, optUtil, super.problem.getUtility(solution, true).getUtility(0), solution, nbrMsgs, msgNbrs, 
				totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccs, totalTime, timesNeeded, maxMsgDim, numberOfCoordinationConstraints);
	}
	
	/** @see AbstractDCOPsolver#clear() */
	@Override
	public void clear () {
		super.clear();
		this.utilModule = null;
		this.valueModule = null;
		this.dfsModule = null;
	}
	
	/** @return the DFS used by the previous call to solve() */
	public HashMap< String, DFSview<V, U> > getDFS () {
		return this.dfsModule.getDFS();
	}
	
}
