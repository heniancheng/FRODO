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

package frodo2.algorithms.asodpop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.solutionSpaces.Addable;

/**
 * This is a solver that reads in the problem, creates the agents, runs the problem and then collects the
 * statistics .
 * 
 * @author Brammert Ottens, Thomas Leaute
 * @param <V>  type used for variable values
 * @param <U> 	type used for utility values
 *
 */
public class ASODPOPsolver < V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, ASODPOPsolution<V, U> > {
	
	/** The ASODPOP module */
	protected ASODPOP<V, U> asodpopModule;
	
	/** The DFSgeneration module */
	protected DFSgeneration<V, U> dfsModule;

	/** Default constructor */
	public ASODPOPsolver () {
		super ("/frodo2/algorithms/asodpop/ASODPOPagent.xml");
	}
	
	/** Constructor 
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPsolver (boolean useTCP) {
		super ("/frodo2/algorithms/asodpop/ASODPOPagent.xml", useTCP);
	}
	
	/** Constructor
	 * @param agentDescFile path to the agent description file
	 */
	public ASODPOPsolver (String agentDescFile) {
		super (agentDescFile);
	}
	
	/** Constructor
	 * @param agentDescFile path to the agent description file
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPsolver (String agentDescFile, boolean useTCP) {
		super (agentDescFile, useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public ASODPOPsolver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPsolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public ASODPOPsolver (Class<V> domClass, Class<U> utilClass) {
		this(domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPsolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this(useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public ASODPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		this(agentDescFile, domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super (agentDescFile, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/**
	 * Set the convergence parameter to the desired value
	 * @param convergence	\c true when convergence must be measured, and false otherwise
	 */
	public void setConvergence(boolean convergence) {
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(ASODPOP.class.getName())) 
				module.setAttribute("convergence", Boolean.toString(convergence));
	}
	
	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers () {
		
		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (3);
		
		asodpopModule = new ASODPOP<V, U>(null, problem);
		asodpopModule.setSilent(true);
		solGatherers.add(asodpopModule);
		
		dfsModule = new DFSgeneration<V, U>(null, problem);
		dfsModule.setSilent(true);
		solGatherers.add(dfsModule);
		
		return solGatherers;
	}
	
	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public ASODPOPsolution<V, U> buildSolution () {
	
		Map<String, V> assignment = asodpopModule.getOptAssignments();
		U reportedUtil = this.asodpopModule.getTotalOptUtil();
		U utility = problem.getUtility(assignment).getUtility(0);
		int nbrMsgs = factory.getNbrMsgs();
		TreeMap<String, Integer> msgNbrs = factory.getMsgNbrs();
		long msgSize = factory.getTotalMsgSize();
		TreeMap<String, Long> msgSizes = factory.getMsgSizes();
		long maxMsgSize = factory.getOverallMaxMsgSize();
		TreeMap<String, Long> maxMsgSizes = factory.getMaxMsgSizes();
		double averageTreeFillPercentage = asodpopModule.getAverageFillTreePercentage();
		double averageDummyFullPercentage = asodpopModule.getAverageDummyFillTreePercentage();
		double averageNumberOfDummies = asodpopModule.getAverageNumberOfDummies();
		int maxMsgDim = asodpopModule.getMaxMsgDim();
		int specUTILmsgs = asodpopModule.getCumulativeNumberOfSpeculativeUTILmsgs();
		int totalUTILmsgs = asodpopModule.getNumberOfUTILmessages();
		HashMap< String, ArrayList< CurrentAssignment<V> > > assignmentHistories = asodpopModule.getAssignmentHistories();
		int numberOfCoordinationConstraint = problem.getNumberOfCoordinationConstraints();
		int nbrVariables = problem.getNbrVars();
		
		HashMap<String, Long> timesNeeded = new HashMap<String, Long> ();
		timesNeeded.put(dfsModule.getClass().getName(), dfsModule.getFinalTime());
		timesNeeded.put(asodpopModule.getClass().getName(), asodpopModule.getFinalTime());
		asodpopModule = null;
		
		return new ASODPOPsolution<V, U> (nbrVariables, reportedUtil, utility, assignment, nbrMsgs, msgNbrs, msgSize, msgSizes, maxMsgSize, maxMsgSizes, this.factory.getNcccs(), factory.getTime(), timesNeeded, 
				numberOfCoordinationConstraint, assignmentHistories, averageTreeFillPercentage, maxMsgDim, averageDummyFullPercentage, averageNumberOfDummies, specUTILmsgs, totalUTILmsgs);
	}
	
	/** @see AbstractDCOPsolver#clear() */
	@Override
	protected void clear () {
		super.clear();
		this.asodpopModule = null;
		this.dfsModule = null;
	}

}
