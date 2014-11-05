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

/** Classes implementing the O-DPOP algorithm */
package frodo2.algorithms.odpop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.jdom2.Document;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.solutionSpaces.Addable;

/** A DCOP solver using O-DPOP
 * @author Brammert Ottens, Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class ODPOPsolver< V extends Addable<V>, U extends Addable<U> > 
extends AbstractDCOPsolver< V, U, ODPOPsolution<V, U> > {

	/** The util propagation phase listener*/
	protected UTILpropagation<V, U> utilModule;
	
	/** The value propagation phase listener */
	protected VALUEpropagation<V, U> valueModule;
	
	/** The DFSgeneration module */
	protected DFSgeneration<V, U> dfsModule;

	/** Default constructor */
	public ODPOPsolver () {
		super ("/frodo2/algorithms/odpop/ODPOPagent.xml");
	}
	
	/** Constructor 
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ODPOPsolver (boolean useTCP) {
		super ("/frodo2/algorithms/odpop/ODPOPagent.xml", useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	the agent description
	 */
	public ODPOPsolver (String agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	the agent description
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ODPOPsolver (String agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	the agent description
	 */
	public ODPOPsolver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	the agent description
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ODPOPsolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public ODPOPsolver (Class<V> domClass, Class<U> utilClass) {
		super ("/frodo2/algorithms/odpop/ODPOPagent.xml");
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ODPOPsolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super ("/frodo2/algorithms/odpop/ODPOPagent.xml", useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/**
	 * Constructor
	 *  
	 * @param agentDescFile location of the agent description file
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass		the class to use for utilities
	 */
	public ODPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		super (agentDescFile);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor
	 *  
	 * @param agentDescFile location of the agent description file
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass		the class to use for utilities
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ODPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super (agentDescFile, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {
		
		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (3);
		
		dfsModule = new DFSgeneration<V, U> (null, problem);
		dfsModule.setSilent(true);
		solGatherers.add(dfsModule);
		
		utilModule = new UTILpropagation<V, U>(null, problem);
		utilModule.setSilent(true);
		solGatherers.add(utilModule);
		
		valueModule = new VALUEpropagation<V, U> (null, problem);
		valueModule.setSilent(true);
		solGatherers.add(valueModule);
		
		return solGatherers;
	}
	
	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public ODPOPsolution<V, U> buildSolution() {
		
		HashMap<String, V> assignment = valueModule.getOptAssignments();
		U utility = problem.getUtility(assignment).getUtility(0);
		int nbrMsgs = factory.getNbrMsgs();
		TreeMap<String, Integer> msgNbrs = factory.getMsgNbrs();
		long msgSize = factory.getTotalMsgSize();
		TreeMap<String, Long> msgSizes = factory.getMsgSizes();
		long maxMsgSize = factory.getOverallMaxMsgSize();
		TreeMap<String, Long> maxMsgSizes = factory.getMaxMsgSizes();
		double averageTreeFillPercentage = valueModule.getAverageFillTreePercentage();
		double averageDummyFullPercentage = valueModule.getAverageDummyFillTreePercentage();
		double averageNumberOfDummies = valueModule.getAverageNumberOfDummies();
		int maxMsgDim = utilModule.getMaxMsgDim();
		int numberOfCoordinationConstraint = problem.getNumberOfCoordinationConstraints();
		int nbrVariables = problem.getNbrVars();
		long runningTime = factory.getTime();
		double percentageOfGoodsSent = valueModule.getPercentageOfGoodsSent();
		
		HashMap<String, Long> timesNeeded = new HashMap<String, Long> ();
		timesNeeded.put(dfsModule.getClass().getName(), dfsModule.getFinalTime());
		timesNeeded.put(utilModule.getClass().getName(), utilModule.getFinalTime());
		timesNeeded.put(valueModule.getClass().getName(), valueModule.getFinalTime());
		
		return new ODPOPsolution<V, U> (nbrVariables, this.utilModule.getOptUtil(), utility, assignment, nbrMsgs, msgNbrs, msgSize, msgSizes, maxMsgSize, maxMsgSizes, this.factory.getNcccs(), 
				runningTime, timesNeeded, numberOfCoordinationConstraint, averageTreeFillPercentage, percentageOfGoodsSent, maxMsgDim, averageDummyFullPercentage, averageNumberOfDummies, valueModule.getMaximalCutSum());
	}
	
	/** Clear this class' member attributes */
	public void clear () {
		super.clear();
		this.utilModule = null;
		this.valueModule = null;
	}

}
