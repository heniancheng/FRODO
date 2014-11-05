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

/** Classes implementing the P-DPOP and P2-DPOP algorithms that preserve privacy */
package frodo2.algorithms.dpop.privacy;

import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.varOrdering.election.SecureVarElection;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;


/**
 * A DCOP solver using P-DPOP
 * @author Eric Zbinden, Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 */
public class P_DPOPsolver< V extends Addable<V>, U extends Addable<U> > extends DPOPsolver<V, U> {

	/**
	 * Default Constructor
	 */
	@SuppressWarnings("unchecked")
	public P_DPOPsolver(){
		super("/frodo2/algorithms/dpop/privacy/P-DPOPagent.xml", (Class<V>) AddableInteger.class, (Class<U>) AddableInteger.class);
	}
	
	/** Constructor
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	@SuppressWarnings("unchecked")
	public P_DPOPsolver(boolean useTCP){
		super("/frodo2/algorithms/dpop/privacy/P-DPOPagent.xml", (Class<V>) AddableInteger.class, (Class<U>) AddableInteger.class, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public P_DPOPsolver (Class<V> domClass, Class<U> utilClass) {
		this(domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public P_DPOPsolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super ("/frodo2/algorithms/dpop/privacy/P-DPOPagent.xml", useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public P_DPOPsolver(String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		super(agentDescFile, domClass, utilClass);
	}

	/** Constructor
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param useTCP 		whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public P_DPOPsolver(String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		super(agentDescFile, domClass, utilClass, useTCP);
	}

	/**
	 * Constructor
	 * @param filename	the location of the agent description file
	 */
	public P_DPOPsolver(String filename) {
		this(filename, false);
	}
	
	/**
	 * Constructor
	 * @param filename	the location of the agent description file
	 * @param useTCP 	whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	@SuppressWarnings("unchecked")
	public P_DPOPsolver(String filename, boolean useTCP) {
		super(filename, (Class<V>) AddableInteger.class, (Class<U>) AddableInteger.class, useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public P_DPOPsolver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 	whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public P_DPOPsolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param parserClass	the class used to parse problems	
	 */
	public P_DPOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass) {
		super (agentDesc, parserClass);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param parserClass	the class used to parse problems	
	 * @param useTCP 	whether to use TCP pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public P_DPOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass, boolean useTCP) {
		super (agentDesc, parserClass, useTCP);
	}
	
	/** @see DPOPsolver#setNbrElectionRounds(int) */
	@Override
	protected void setNbrElectionRounds (int nbrElectionRounds) {
		
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(SecureVarElection.class.getName())) 
				module.setAttribute("minNbrLies", Integer.toString(nbrElectionRounds));
	}

}
