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

package frodo2.benchmarks.vehiclerouting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.XCSPparser;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableBigDecimal;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.vehiclerouting.Customer;
import frodo2.solutionSpaces.vehiclerouting.VehicleRoutingSpace;

/** A parser for XCSP files involving constraints of type "global:vehicle_routing"
 * @author Thomas Leaute
 * @param <U> the type used for utility values (default is AddableReal)
 */
public class XCSPparserVRP < U extends Addable<U> > extends XCSPparser<AddableInteger, U> {

	/** Used for serialization */
	private static final long serialVersionUID = -3510401322787049569L;
	
	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 	the JDOM Document in XCSP format
	 */
	public XCSPparserVRP (Document doc) {
		super(doc);
		assert ! super.maximize() : "The problem must be a minimization problem";
	}
	
	/** Constructor
	 * @param doc 		the JDOM Document in XCSP format
	 * @param params 	the parameters of the solver
	 */
	public XCSPparserVRP(Document doc, Element params) {
		super(doc, params);
		assert ! super.maximize() : "The problem must be a minimization problem";
	}
	
	/** Constructor from a JDOM root Element in XCSP format
	 * @param agent 	the name of the agent owning the input subproblem
	 * @param instance 	the JDOM root Element in XCSP format
	 */
	protected XCSPparserVRP(String agent, Element instance) {
		super (agent, instance, false);
	}
	
	/** Constructor from a JDOM root Element in XCSP format
	 * @param agent 						the name of the agent owning the input subproblem
	 * @param instance 						the JDOM root Element in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 * @param spacesToIgnoreNcccs			list of spaces for which NCCCs should NOT be counted
	 * @param mpc 							Whether to behave in MPC mode
	 */
	protected XCSPparserVRP(String agent, Element instance, boolean countNCCCs, boolean extendedRandNeighborhoods, HashSet<String> spacesToIgnoreNcccs, boolean mpc) {
		super (agent, instance, countNCCCs, extendedRandNeighborhoods, spacesToIgnoreNcccs, mpc);
	}
	
	/** @see XCSPparser#newInstance(java.lang.String, org.jdom2.Element) */
	@Override
	protected XCSPparserVRP<U> newInstance (String agent, Element instance) {
		return new XCSPparserVRP<U> (agent, instance, this.countNCCCs, super.extendedRandNeighborhoods, this.spacesToIgnoreNcccs, super.mpc);
	}

	/** @see XCSPparser#getSubProblem(java.lang.String) */
	@Override
	public XCSPparserVRP<U> getSubProblem (String agent) {
		return (XCSPparserVRP<U>) super.getSubProblem(agent);
	}

	/** @see XCSPparser#setUtilClass(java.lang.Class) */
	@Override
	public void setUtilClass (Class<U> utilClass) {
		
		if (! utilClass.equals(AddableReal.class) && ! utilClass.equals(AddableBigDecimal.class)) 
			System.err.println("Using " + utilClass + " instead of " + AddableReal.class + " in XCSPparserVRP. \nPotential truncations might lead to approximation errors.");
		
		super.setUtilClass(utilClass);
	}
	
	/** @see XCSPparser#foundUndefinedRelations(java.util.HashSet) */
	@Override
	protected void foundUndefinedRelations(HashSet<String> relationNames) {
		
		// Filter out "global:vehicle_routing"
		relationNames.remove("global:vehicle_routing");
		
		if (! relationNames.isEmpty()) 
			super.foundUndefinedRelations(relationNames);
	}

	/** @see XCSPparser#parseConstraint(ArrayList, Element, HashMap, HashMap, Set, boolean, boolean, Addable, Set) */
	@Override
	protected void parseConstraint(ArrayList< UtilitySolutionSpace<AddableInteger, U> > spaces, Element constraint, 
			HashMap<String, AddableInteger[]> variablesHashMap, HashMap< String, Relation<AddableInteger, U> > relationInfos, 
			Set<String> vars, final boolean getProbs, final boolean withAnonymVars, U infeasibleUtil, Set<String> forbiddenVars) {
		
		String reference = constraint.getAttributeValue("reference");
		
		// Call the superclass if the constraint isn't of type "global:vehicle_routing"
		if (! reference.equals("global:vehicle_routing")) {
			super.parseConstraint(spaces, constraint, variablesHashMap, relationInfos, vars, getProbs, withAnonymVars, infeasibleUtil, forbiddenVars);
			return;
		}
		
		// This constraint is not a probability space; skip it if we are only looking for probability spaces
		if (getProbs) 
			return;
		
		// Parse the constraint
		String name = constraint.getAttributeValue("name");
		String owner = constraint.getAttributeValue("agent");
		int minSplit = 0;
		String minSplitString = constraint.getAttributeValue("minSplit");
		if (minSplitString != null) 
			minSplit = Integer.parseInt(minSplitString);
		String[] consVars = constraint.getAttributeValue("scope").trim().split("\\s+");
		if (vars != null && Collections.disjoint(vars, Arrays.asList(consVars)))
			return;
		if (forbiddenVars != null) 
			for (String var2 : consVars) 
				if (forbiddenVars.contains(var2)) 
					return;
		assert consVars.length == Integer.parseInt(constraint.getAttributeValue("arity")) : 
			"arity == " + constraint.getAttributeValue("arity") + " for constraint " + name + " but the variables are " + Arrays.toString(consVars);
		
		// Parse the depot
		constraint = constraint.getChild("parameters");
		Element depotElmt = constraint.getChild("depot");
		int nbrVehicles = Integer.parseInt(depotElmt.getAttributeValue("nbVehicles"));
		float maxDist = Float.parseFloat(depotElmt.getAttributeValue("maxDist"));
		int maxLoad = Integer.parseInt(depotElmt.getAttributeValue("maxLoad"));
		float depotX = Float.parseFloat(depotElmt.getAttributeValue("xCoordinate"));
		float depotY = Float.parseFloat(depotElmt.getAttributeValue("yCoordinate"));
		
		// Parse the customers
		HashMap<String, Customer> customers = new HashMap<String, Customer> ();
		HashSet<Customer> selectedCustomers = new HashSet<Customer> ();
		HashMap<String, Customer> uncertainties = new HashMap<String, Customer> ();
		for (Element customerElmt : (List<Element>) constraint.getChild("customers").getChildren()) {
			
			// Parse the description of the customer
			String varName = customerElmt.getAttributeValue("varName");
			int id = Integer.parseInt(customerElmt.getAttributeValue("id"));
			int demand = Integer.parseInt(customerElmt.getAttributeValue("demand"));
			float x = Float.parseFloat(customerElmt.getAttributeValue("xCoordinate"));
			float y = Float.parseFloat(customerElmt.getAttributeValue("yCoordinate"));
			String randVar = customerElmt.getAttributeValue("uncertaintyAngleVar");
			double radius = (randVar == null ? 0.0 : Double.parseDouble(customerElmt.getAttributeValue("uncertaintyRadius")));
			
			// Instantiate and record the customer
			Customer cust = new Customer (id, demand, x, y, radius, variablesHashMap.get(randVar));
			if (varName != null) 
				customers.put(varName, cust);
			else 
				selectedCustomers.add(cust);
			if (randVar != null) 
				uncertainties.put(randVar, cust);
		}
		
		spaces.add(new VehicleRoutingSpace<U> (nbrVehicles, maxDist, maxLoad, depotX, depotY, consVars, new HashMap<String, AddableInteger[]> (), 
											customers, selectedCustomers, uncertainties, 
											name, owner, super.getPlusInfUtility(), minSplit, (this.countNCCCs && !this.ignore(VehicleRoutingSpace.class.getName()) ? this : null)));
	}
	
	/** @see XCSPparser#rescale(Addable, Addable) */
	@Override
	public void rescale(U multiply, U add) {
		super.rescale(multiply, add);
		assert false : "Not implemented";
	}
	
}
