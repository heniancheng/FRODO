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

package frodo2.algorithms.synchbb;

import java.util.ArrayList;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.linear.LinearOrdering;
import frodo2.gui.DOTrenderer;
import frodo2.solutionSpaces.Addable;

/** A DCOP solver using SynchBB
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class SynchBBsolver< V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, SynchBBsolution<V, U> > {

	/** The SynchBB module */
	protected SynchBB<V, U> module;

	/** Default constructor 
	 * @param filename the name of the file containing the description of the algorithm*/
	public SynchBBsolver (String filename) {
		super (filename);
	}
	
	/** Default constructor 
	 * @param filename the name of the file containing the description of the algorithm
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public SynchBBsolver (String filename, boolean useTCP) {
		super (filename, useTCP);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public SynchBBsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
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
	public SynchBBsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this (agentDescFile, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Default constructor */
	public SynchBBsolver () {
		super ("/frodo2/algorithms/synchbb/SynchBBagent.xml");
	}
	
	/** Constructor 
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public SynchBBsolver (boolean useTCP) {
		super ("/frodo2/algorithms/synchbb/SynchBBagent.xml", useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public SynchBBsolver (Class<V> domClass, Class<U> utilClass) {
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
	public SynchBBsolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this(useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public SynchBBsolver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public SynchBBsolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (1);
		
		module = new SynchBB<V, U> (null, problem);
		module.setSilent(true);
		solGatherers.add(module);
		
		Element params = new Element ("module");
		params.setAttribute("DOTrenderer", DOTrenderer.class.getName()); // comment out to print to the console
		
		StatsReporter mod = new LinearOrdering<V, U> (params, problem);
		mod.setSilent(true); // set to false to see the variable ordering
		solGatherers.add(mod);
		
		return solGatherers;
	}
	
	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public SynchBBsolution<V, U> buildSolution () {
		return new SynchBBsolution<V, U> (0, module.getOptCost(), super.problem.getUtility(this.module.getOptAssignments(), true).getUtility(0), module.getOptAssignments(), 
				factory.getNbrMsgs(), factory.getMsgNbrs(), factory.getTotalMsgSize(), factory.getMsgSizes(), factory.getOverallMaxMsgSize(), factory.getMaxMsgSizes(), 
				factory.getNcccs(), factory.getTime(), null, module.getAssignmentHistories());
	}
	
	/** @see AbstractDCOPsolver#clear() */
	@Override
	protected void clear () {
		super.clear();
		this.module = null;
	}

}
