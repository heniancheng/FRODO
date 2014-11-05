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

package frodo2.algorithms.adopt;

import java.util.ArrayList;
import java.util.HashMap;

import org.jdom2.Document;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.solutionSpaces.Addable;

/** A DCOP solver using ADOPT
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class ADOPTsolver< V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, ADOPTsolution<V, U> > {

	/** The ADOPT module */
	private ADOPT<V, U> adoptModule;
	
	/** The DFSgeneration module */
	private DFSgeneration<V, U> dfsModule;

	/** Default constructor */
	public ADOPTsolver () {
		super ("/frodo2/algorithms/adopt/ADOPTagent.xml");
	}
	
	/** Constructor 
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ADOPTsolver (boolean useTCP) {
		super ("/frodo2/algorithms/adopt/ADOPTagent.xml", useTCP);
	}
	
	/** Constructor from a specific agent configuration file 
	 * @param filename the agent configuration file
	 */
	public ADOPTsolver (String filename) {
		super (filename);
	}
	
	/** Constructor from a specific agent configuration file 
	 * @param filename the agent configuration file
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ADOPTsolver (String filename, boolean useTCP) {
		super (filename, useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public ADOPTsolver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ADOPTsolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public ADOPTsolver (Class<V> domClass, Class<U> utilClass) {
		this(domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ADOPTsolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this(useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public ADOPTsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		this(agentDescFile, domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ADOPTsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super (agentDescFile, useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (2);
		
		adoptModule = new ADOPT<V, U>(null, problem);
		adoptModule.setSilent(true);
		solGatherers.add(adoptModule);
		
		Preprocessing<V, U> preprocessingModule = new Preprocessing<V, U> (null, problem);
		preprocessingModule.setSilent(true);
		solGatherers.add(preprocessingModule);
		
		dfsModule = new DFSgeneration<V, U> (null, problem);
		dfsModule.setSilent(true);
		solGatherers.add(dfsModule);
		
		return solGatherers;
	}

	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public ADOPTsolution<V, U> buildSolution () {

		HashMap<String, Long> times = new HashMap<String, Long> ();
		times.put(dfsModule.getClass().toString(), dfsModule.getFinalTime());
		times.put(adoptModule.getClass().toString(), adoptModule.getFinalTime());
		
		return new ADOPTsolution<V, U> (0, adoptModule.getTotalOptUtil(), super.problem.getUtility(this.adoptModule.getOptAssignments()).getUtility(0), adoptModule.getOptAssignments(), 
				factory.getNbrMsgs(), factory.getMsgNbrs(), factory.getTotalMsgSize(), factory.getMsgSizes(), factory.getOverallMaxMsgSize(), factory.getMaxMsgSizes(), 
				factory.getNcccs(), factory.getTime(), times, adoptModule.getAssignmentHistories());
	}

	/** @see AbstractDCOPsolver#clear() */
	@Override
	protected void clear () {
		super.clear();
		this.adoptModule = null;
		this.dfsModule = null;
	}
	
}
