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

package frodo2.algorithms.localSearch.mgm.mgm2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.localSearch.mgm.MGMsolution;
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
public class MGM2solver < V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, MGMsolution<V, U> > {
	
	/** The MGM module */
	private MGM2<V, U> mgmModule;

	/**
	 * Constructor
	 */
	public MGM2solver () {
		super ("/frodo2/algorithms/localSearch/mgm/MGM2agent.xml");
	}
	
	/** Constructor
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public MGM2solver (boolean useTCP) {
		super ("/frodo2/algorithms/localSearch/mgm/MGM2agent.xml", useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public MGM2solver (Class<V> domClass, Class<U> utilClass) {
		this();
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public MGM2solver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this(useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/**
	 * Constructor
	 * @param agentDesc path to the agent description file
	 */
	public MGM2solver (String agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor
	 * @param agentDesc path to the agent description file
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public MGM2solver (String agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public MGM2solver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		this (agentDescFile);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public MGM2solver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this (agentDescFile, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public MGM2solver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public MGM2solver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/**
	 * Sets the convergence parameter to the desired value
	 * @param convergence	\c true when convergence must be measured, and false otherwise
	 */
	public void setConvergence(boolean convergence) {
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(MGM2.class.getName())) 
				module.setAttribute("convergence", Boolean.toString(convergence));
	}
	
	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (1);
		
		mgmModule = new MGM2<V, U>((Element)null, problem);
		mgmModule.setSilent(true);
		solGatherers.add(mgmModule);
		
		return solGatherers;
	}
		
	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public MGMsolution<V, U> buildSolution() {
		
		HashMap<String, Long> timesNeeded = new HashMap<String, Long> ();
		
		return new MGMsolution<V, U> (problem.getNbrVars(), this.mgmModule.getFinalSolution(), this.mgmModule.getFinalSolution(), 
				mgmModule.getCurrentSolution(), factory.getNbrMsgs(), factory.getTotalMsgSize(), factory.getOverallMaxMsgSize(), factory.getNcccs(), factory.getTime(), timesNeeded, mgmModule.getAssignmentHistories());
	}

	/** @see AbstractDCOPsolver#clear() */
	@Override
	public void clear () {
		super.clear();
		this.mgmModule = null;
	}

}
