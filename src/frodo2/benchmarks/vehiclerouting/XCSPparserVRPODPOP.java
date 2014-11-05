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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.vehiclerouting.CompoundSpace;

/**
 * 
 * Wrapper around the XCSPparserVRP that inserts CompoundSpaces for all the variables that occur in the problem
 * 
 * @author Brammert Ottens, 28 apr 2010
 * @param <U> type of utility values
 * 
 */
public class XCSPparserVRPODPOP <U extends Addable<U>> extends XCSPparserVRP<U> {

	/** Used for serialization */
	private static final long serialVersionUID = 8667005740460947817L;

	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 	the JDOM Document in XCSP format
	 */
	public XCSPparserVRPODPOP (Document doc) {
		super(doc);
	}
	
	/** Constructor
	 * @param doc 		the JDOM Document in XCSP format
	 * @param params 	the parameters of the solver
	 */
	public XCSPparserVRPODPOP(Document doc, Element params) {
		super(doc, params);
	}
	
	/** Constructor from a JDOM root Element in XCSP format
	 * @param agent 						the name of the agent owning the input subproblem
	 * @param instance 						the JDOM root Element in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 * @param spacesToIgnoreNcccs			list of spaces for which NCCCs should NOT be counted
	 * @param mpc 							Whether to behave in MPC mode
	 */
	protected XCSPparserVRPODPOP(String agent, Element instance, boolean countNCCCs, boolean extendedRandNeighborhoods, HashSet<String> spacesToIgnoreNcccs, boolean mpc) {
		super (agent, instance, countNCCCs, extendedRandNeighborhoods, spacesToIgnoreNcccs, mpc);
	}

	/**
	 * @see frodo2.algorithms.XCSPparser#getSolutionSpaces()
	 */
	@Override
	public List< ? extends UtilitySolutionSpace<AddableInteger, U> > getSolutionSpaces () {
		List< ? extends UtilitySolutionSpace<AddableInteger, U>> spaces = super.getSolutionSpaces();
		Set<String> variables = super.getVariables();
		List<UtilitySolutionSpace<AddableInteger, U>> compoundSpaces = new ArrayList<UtilitySolutionSpace<AddableInteger, U>>(variables.size());
		
			for(String var2 : variables)
				compoundSpaces.add(new CompoundSpace<U>(var2, super.getDomain(var2), this.getInfeasibleUtil()));
	
		compoundSpaces.addAll(spaces);
		return compoundSpaces;
	}
	
	/**
	 * @see frodo2.algorithms.XCSPparser#getSolutionSpaces(java.lang.String)
	 */
	@Override
	public List< ? extends UtilitySolutionSpace<AddableInteger, U> > getSolutionSpaces (String var) {
		List< ? extends UtilitySolutionSpace<AddableInteger, U>> spaces = super.getSolutionSpaces(var);
		Set<String> variables = super.getVariables();
		List<UtilitySolutionSpace<AddableInteger, U>> compoundSpaces = new ArrayList<UtilitySolutionSpace<AddableInteger, U>>(variables.size());
		
		if(var == null) {
			for(String var2 : variables)
				compoundSpaces.add(new CompoundSpace<U>(var2, super.getDomain(var2), this.getInfeasibleUtil()));
		} else
			compoundSpaces.add(new CompoundSpace<U>(var, super.getDomain(var), this.getInfeasibleUtil()));

		compoundSpaces.addAll(spaces);
		return compoundSpaces;
	}
	
	/**
	 * @see frodo2.benchmarks.vehiclerouting.XCSPparserVRP#newInstance(java.lang.String, org.jdom2.Element)
	 */
	@Override
	protected XCSPparserVRPODPOP<U> newInstance (String agent, Element instance) {
		return new XCSPparserVRPODPOP<U> (agent, instance, this.countNCCCs, false, new HashSet<String>(), super.mpc);
	}
	
}
