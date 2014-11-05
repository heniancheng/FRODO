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

import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

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
public class ASODPOPBinarysolver < V extends Addable<V>, U extends Addable<U> > extends ASODPOPsolver<V, U> {
	
	/** Default constructor */
	public ASODPOPBinarysolver () {
		super ("/frodo2/algorithms/asodpop/ASODPOPBinaryagent.xml");
	}
	
	/** Constructor 
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPBinarysolver (boolean useTCP) {
		super ("/frodo2/algorithms/asodpop/ASODPOPBinaryagent.xml", useTCP);
	}
	
	/** Constructor
	 * @param agentDescFile path to the agent description file
	 */
	public ASODPOPBinarysolver (String agentDescFile) {
		super (agentDescFile);
	}
	
	/** Constructor
	 * @param agentDescFile path to the agent description file
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPBinarysolver (String agentDescFile, boolean useTCP) {
		super (agentDescFile, useTCP);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 */
	public ASODPOPBinarysolver (Document agentDesc) {
		super (agentDesc);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPBinarysolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 */
	public ASODPOPBinarysolver (Class<V> domClass, Class<U> utilClass) {
		this(domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPBinarysolver (Class<V> domClass, Class<U> utilClass, boolean useTCP) {
		this(useTCP);
		this.setDomClass(domClass);
		this.setUtilClass(utilClass);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 */
	public ASODPOPBinarysolver (String agentDescFile, Class<V> domClass, Class<U> utilClass) {
		this(agentDescFile, domClass, utilClass, false);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to be used for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public ASODPOPBinarysolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, boolean useTCP) {
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
			if (module.getAttributeValue("className").equals(ASODPOPBinaryDomains.class.getName())) 
				module.setAttribute("convergence", Boolean.toString(convergence));
	}
	
}
