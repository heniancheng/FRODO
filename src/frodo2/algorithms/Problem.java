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

package frodo2.algorithms;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** A ProblemInterface that does not require the use of the XCSP format
 * @author Thomas Leaute
 * @param <V> the class used for variable values
 * @param <U> the class used for utility values
 * @todo Add support for NCCCs
 */
public class Problem < V extends Addable<V>, U extends Addable<U> > implements DCOPProblemInterface <V, U> {
	
	/** Used for serialization */
	private static final long serialVersionUID = -7670751554969143041L;

	/** For each variable, the name of its owner agent */
	private Map<String, String> owners;
	
	/** The list of solution spaces */
	private List< UtilitySolutionSpace<V, U> > spaces;

	/** The name of the agent owning this subproblem */
	private String agentName;

	/** The domain of each variable */
	private Map<String, V[]> domains;
	
	/** The class used for utility values */
	@SuppressWarnings("unchecked")
	private Class<U> utilClass = (Class<U>) AddableInteger.class;
	
	/** Whether this is a maximization or a minimization problem */
	private boolean maximize;
	
	/** Whether each agent knows the identities of all agents */
	private final boolean publicAgents;
	
	/** The NCCC count */
	private long ncccCount;

	/** Constructor
	 * @param maximize 	Whether this is a maximization or a minimization problem
	 */
	public Problem (boolean maximize) {
		this(maximize, false);
	}
	
	/** Constructor
	 * @param maximize 		Whether this is a maximization or a minimization problem
	 * @param publicAgents 	Whether each agent knows the identities of all agents
	 */
	public Problem (boolean maximize, boolean publicAgents) {
		this.spaces = new ArrayList< UtilitySolutionSpace<V, U> > ();
		this.domains = new HashMap<String, V[]> ();
		this.owners = new HashMap<String, String> ();
		this.maximize = maximize;
		this.publicAgents = publicAgents;
	}
	
	/** Constructor for a minimization problem
	 * @param agentName 	the name of the agent owning this subproblem
	 * @param owners 		for each variable, the name of its owner agent
	 * @param domains 		the domain of each variable
	 * @param spaces 		the list of solution spaces
	 */
	public Problem (String agentName, Map<String, String> owners, Map<String, V[]> domains, List< ? extends UtilitySolutionSpace<V, U> > spaces) {
		this(agentName, owners, domains, spaces, false);
	}
	
	/** Constructor 
	 * @param agentName 	the name of the agent owning this subproblem
	 * @param owners 		for each variable, the name of its owner agent
	 * @param domains 		the domain of each variable
	 * @param spaces 		the list of solution spaces
	 * @param maximize 		whether this is a maximization or a minimization problem
	 */
	public Problem (String agentName, Map<String, String> owners, Map<String, V[]> domains, List< ? extends UtilitySolutionSpace<V, U> > spaces, boolean maximize) {
		this.reset(agentName, owners, domains, spaces, maximize);
		this.publicAgents = false;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		
		StringBuilder builder = new StringBuilder ("Problem");
		
		if (this.agentName != null) 
			builder.append("\n\t agent: " + this.agentName);
		
		builder.append("\n\t maximize = " + this.maximize);
		
		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			builder.append("\n\t " + entry.getKey() + "\t is owned by \t" + entry.getValue());
		
		for (Map.Entry<String, V[]> entry : this.domains.entrySet()) 
			builder.append("\n\t " + entry.getKey() + "\t" + Arrays.toString(entry.getValue()));
		
		builder.append("\n\t " + this.spaces);
		
		return builder.toString();
	}
	
	/** @see DCOPProblemInterface#reset(ProblemInterface) */
	public void reset(ProblemInterface<V, U> newProblem) {
		
		if (newProblem instanceof Problem) {
			
			Problem<V, U> prob = (Problem<V, U>) newProblem;
			this.reset(prob.agentName, prob.owners, prob.domains, prob.spaces, prob.maximize);
			
		} else if (newProblem instanceof XCSPparser) {
			
			XCSPparser<V, U> prob = (XCSPparser<V, U>) newProblem;
			
			// Parse the domains
			HashMap<String, V[]> newDomains = new HashMap<String, V[]> ();
			for (String var : prob.getVariables()) 
				newDomains.put(var, prob.getDomain(var));
			
			this.reset(prob.agentName, prob.getOwners(), newDomains, prob.getSolutionSpaces(), prob.maximize());
			
			if (prob.isCountingNCCCs()) 
				for (UtilitySolutionSpace<V, U> space : this.spaces) 
					space.setProblem(this);
			
		} else 
			System.err.println("Unknown problem class: " + newProblem.getClass());
		
	}
	
	/** Resets the problem 
	 * @param agentName 	the name of the agent owning this subproblem
	 * @param owners 		for each variable, the name of its owner agent
	 * @param domains 		the domain of each variable
	 * @param spaces 		the list of solution spaces
	 * @param maximize 		whether this is a maximization or a minimization problem
	 */
	public void reset (String agentName, Map<String, String> owners, Map<String, V[]> domains, List< ? extends UtilitySolutionSpace<V, U> > spaces, boolean maximize) {
		this.agentName = agentName;
		this.owners = owners;
		this.domains = domains;
		this.spaces = new ArrayList< UtilitySolutionSpace<V, U> > (spaces);
		this.maximize = maximize;
	}
	
	/** @see ProblemInterface#setDomClass(java.lang.Class) */
	public void setDomClass(Class<V> domClass) {
		assert domClass == AddableInteger.class : "Unsupported domain class: " + domClass;
	}
	
	/** @see ProblemInterface#getDomClass() */
	@SuppressWarnings("unchecked")
	@Override
	public Class<V> getDomClass() {
		return (Class<V>) AddableInteger.class;
	}
	
	/** @see DCOPProblemInterface#setUtilClass(java.lang.Class) */
	public void setUtilClass (Class<U> utilClass) {
		this.utilClass = utilClass;
	}

	/** @see DCOPProblemInterface#getZeroUtility() */
	public U getZeroUtility() {
		try {
			return (U) utilClass.newInstance().getZero();
			
		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
	}

	/** @see DCOPProblemInterface#getMinInfUtility() */
	public U getMinInfUtility() {
		try {
			return (U) utilClass.newInstance().getMinInfinity();
			
		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
	}

	/** @see DCOPProblemInterface#getPlusInfUtility() */
	public U getPlusInfUtility() {
		try {
			return (U) utilClass.newInstance().getPlusInfinity();
			
		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
	}

	/** @see DCOPProblemInterface#getAgent() */
	public String getAgent() {
		return this.agentName;
	}
	
	/** Sets the name of the agent
	 * @param agent 	the name of the agent
	 */
	public void setAgent (String agent) {
		this.agentName = agent;
	}

	/** @see DCOPProblemInterface#getAgents() */
	public Set<String> getAgents() {
		
		return new HashSet<String> (this.owners.values());
	}

	/** @see DCOPProblemInterface#getAllVars() */
	public Set<String> getAllVars() {
		HashSet<String> out = new HashSet<String> (this.getVariables());
		out.addAll(this.getAnonymVars());
		return out;
	}

	/** @see DCOPProblemInterface#getVariables() */
	public Set<String> getVariables() {
		
		HashSet<String> vars = new HashSet<String> (this.owners.size());

		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			if (entry.getValue() != null) 
				vars.add(entry.getKey());
		
		return vars;
	}
	
	/** @see DCOPProblemInterface#getVariables(java.lang.String) */
	public Set<String> getVariables (final String owner) {
		
		HashSet<String> vars = new HashSet<String> (this.owners.size());
		
		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			if ((owner == null && entry.getValue() == null) 
					|| (owner != null && owner.equals(entry.getValue())))
				vars.add(entry.getKey());
		
		return vars;
	}
	
	/** @see DCOPProblemInterface#getAnonymVars() */
	public Set<String> getAnonymVars() {
		
		HashSet<String> out = new HashSet<String> ();
		
		// Go through all variables in all spaces
		for (UtilitySolutionSpace<V, U> space : this.spaces) 
			for (String var : space.getVariables()) 
				if (this.owners.get(var) == null) 
					out.add(var);
		
		return out;
	}

	/** @see DCOPProblemInterface#getExtVars() */
	public Set<String> getExtVars() {
		
		HashSet<String> out = new HashSet<String> ();
		
		if (this.agentName == null) 
			return out;

		for (Map.Entry<String, String> entry : this.owners.entrySet()) {
			String owner = entry.getValue();
			if (owner != null && ! this.agentName.equals(owner)) 
				out.add(entry.getKey());
		}
		
		return out;
	}

	/** @see DCOPProblemInterface#getMyVars() */
	public Set<String> getMyVars() {
		
		Set<String> myVars = new HashSet<String> ();
		
		if (this.agentName == null) 
			return myVars;
		
		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			if (this.agentName.equals(entry.getValue()))
				myVars.add(entry.getKey());
		
		return myVars;
	}

	/** @see DCOPProblemInterface#addVariable(java.lang.String, java.lang.String, java.lang.String) */
	public boolean addVariable(String name, String owner, String domain) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see DCOPProblemInterface#addVariable(java.lang.String, java.lang.String, V[]) */
	public boolean addVariable(String name, String owner, V[] domain) {
		
		// Check if a variable with the same name already exists
		if (this.owners.containsKey(name)) 
			return false;
		
		this.owners.put(name, owner);
		this.domains.put(name, domain);
		
		return true;
	}

	/** @see DCOPProblemInterface#getNbrIntVars() */
	public int getNbrIntVars() {
		return this.getMyVars().size();
	}

	/** @see DCOPProblemInterface#getNbrVars() */
	public int getNbrVars() {
		return this.getVariables().size();
	}
	
	/** @see DCOPProblemInterface#getOwner(java.lang.String) */
	public String getOwner(String var) {
		return this.owners.get(var);
	}

	/** @see DCOPProblemInterface#setOwner(java.lang.String, java.lang.String) */
	public boolean setOwner(String var, String owner) {
		
		if (! this.owners.containsKey(var)) 
			return false;
		
		this.owners.put(var, owner);
		return true;
	}
	
	/** @see DCOPProblemInterface#getOwners() */
	public Map<String, String> getOwners() {
		return this.owners;
	}

	/** @see DCOPProblemInterface#isRandom(java.lang.String) */
	public boolean isRandom(String var) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return false;
	}

	/** @see DCOPProblemInterface#getDomain(java.lang.String) */
	public V[] getDomain(String var) {
		return this.domains.get(var);
	}

	/** @see DCOPProblemInterface#getDomainSize(java.lang.String) */
	public int getDomainSize(String var) {
		
		V[] dom = this.getDomain(var);
		if (dom == null) 
			return -1;
		else 
			return dom.length;
	}

	/** @see DCOPProblemInterface#setDomain(java.lang.String, V[]) */
	public void setDomain(String var, V[] dom) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}

	/** @see DCOPProblemInterface#getNeighborhoods() */
	public Map<String, ? extends Collection<String>> getNeighborhoods() {
		
		// Initialize the output
		Map< String, Set<String> > out = new HashMap< String, Set<String> > ();
		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			if (this.agentName == null || this.agentName.equals(entry.getValue())) // internal variable
				out.put(entry.getKey(), new HashSet<String> ());
		
		// Go through all spaces
		for (UtilitySolutionSpace<V, U> space : this.spaces) {
			
			// Go through the list of internal variables in the scope of this space
			for (String intVar : space.getVariables()) {
				if (this.agentName != null && ! this.agentName.equals(this.owners.get(intVar))) 
					continue; 
				
				// Add to the list of neighbors all variables in the scope of this space that are owned by a known agent
				Set<String> neighbors = out.get(intVar);
				for (String var : space.getVariables()) 
					if (this.getOwner(var) != null) 
						neighbors.add(var);
				
				// Remove the variable itself
				neighbors.remove(intVar);
			}
		}
		
		return out;
	}

	/** @see DCOPProblemInterface#getAnonymNeighborhoods() */
	public Map<String, HashSet<String>> getAnonymNeighborhoods() {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/** @see DCOPProblemInterface#getAnonymNeighborhoods(String) */
	public Map<String, HashSet<String>> getAnonymNeighborhoods(String agent) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/** @see DCOPProblemInterface#getAgentNeighborhoods() */
	public Map< String, Collection<String> > getAgentNeighborhoods() {
		
		// Initialize the output
		Map< String, Collection<String> > out = new HashMap< String, Collection<String> > ();
		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			if (this.agentName == null || this.agentName.equals(entry.getValue())) // internal variable
				out.put(entry.getKey(), new HashSet<String> ());
		
		// Go through the list of variables in all spaces owned by this agent
		for (UtilitySolutionSpace<V, U> space : this.spaces) {
			for (String var : space.getVariables()) {
				if (this.agentName != null && ! this.agentName.equals(this.getOwner(var))) 
					continue;
				
				// Add to this variable's set of neighboring agents the owners of the other variables in this space
				Collection<String> agents = out.get(var);
				for (String var2 : space.getVariables()) {
					String agent2 = this.owners.get(var2);
					if (this.agentName == null || (agent2 != null && ! agent2.equals(this.agentName))) 
						agents.add(agent2);
				}
			}
		}
		
		return out;
	}

	/** @see DCOPProblemInterface#getNeighborhoodSizes() */
	public Map<String, Integer> getNeighborhoodSizes() {
		
		HashMap<String, Integer> out = new HashMap<String, Integer> ();
		
		for (Map.Entry< String, ? extends Collection<String> > entry : this.getNeighborhoods().entrySet()) 
			out.put(entry.getKey(), entry.getValue().size());
		
		return out;
	}

	/** @see DCOPProblemInterface#getNeighborVars(java.lang.String) */
	public Collection<String> getNeighborVars(String var) {
		
		HashSet<String> out = new HashSet<String> (); 
		
		// Go through the list of spaces
		for (UtilitySolutionSpace<V, U> space : this.spaces) 
			if (space.getDomain(var) != null) // this space contains the desired variable; add all the space's variables to the output
				for (String neigh : space.getVariables()) 
					out.add(neigh);
		
		// Remove the variable itself from its list of neighbors
		out.remove(var);
		
		return out;
	}
	
	/** @see DCOPProblemInterface#getNeighborVars(java.lang.String, boolean) */
	public HashSet<String> getNeighborVars(String var, boolean withAnonymVars) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}
	
	/** @see DCOPProblemInterface#getNbrNeighbors(java.lang.String) */
	public int getNbrNeighbors(String var) {
		return this.getNeighborhoodSizes().get(var);
	}
	
	/** @see DCOPProblemInterface#getSolutionSpaces() */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces() {
		return this.getSolutionSpaces(false);
	}

	/** @see DCOPProblemInterface#getSolutionSpaces(boolean) */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces(boolean withAnonymVars) {
		return this.getSolutionSpaces((String) null, withAnonymVars, null);
	}

	/** @see DCOPProblemInterface#getSolutionSpaces(String, boolean) */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces(String var, boolean withAnonymVars) {
		return this.getSolutionSpaces(var, withAnonymVars, null);
	}
	
	/** @see DCOPProblemInterface#getSolutionSpaces(java.lang.String, java.util.Set) */
	public List<? extends UtilitySolutionSpace<V, U>> getSolutionSpaces(String var, Set<String> forbiddenVars) {
		return this.getSolutionSpaces(var, false, forbiddenVars);
	}

	/** @see DCOPProblemInterface#getSolutionSpaces(java.lang.String, boolean, java.util.Set) */
	public List<? extends UtilitySolutionSpace<V, U>> getSolutionSpaces(String var, final boolean withAnonymVars, Set<String> forbiddenVars) {
		
		HashSet<String> vars = null;
		if(var != null) {
			vars = new HashSet<String>();
			vars.add(var);
		}
		return this.getSolutionSpaces(vars, withAnonymVars, forbiddenVars);
	}
	
	/** @see DCOPProblemInterface#getSolutionSpaces(java.util.Set, boolean, java.util.Set) */
	public List<? extends UtilitySolutionSpace<V, U>> getSolutionSpaces(Set<String> vars, boolean withAnonymVars, Set<String> forbiddenVars) {
		
		// Return null if not all domains are known yet
		for (V[] dom : this.domains.values()) 
			if (dom == null) 
				return null;
		
		// Get rid of all undesired spaces
		List< UtilitySolutionSpace<V, U> > out = new ArrayList< UtilitySolutionSpace<V, U> > ();
		spaceLoop: for (UtilitySolutionSpace<V, U> space : this.spaces) {
			
			// Skip this space if it does not include any of the input variables
			if (vars != null && Collections.disjoint(vars, Arrays.asList(space.getVariables()))) 
				continue;
			
			// Skip this space if it involves a variable with unknown owner and if we don't want such variables
			if (! withAnonymVars) 
				for (String var2 : space.getVariables()) 
					if (this.owners.get(var2) == null) 
						continue spaceLoop;
			
			// Skip this space if it involves any of the forbidden variables
			if (forbiddenVars != null) 
				for (String var2: space.getVariables()) 
					if (forbiddenVars.contains(var2)) 
						continue spaceLoop;
			
			out.add(space);
		}		
		return out;
	}
	
	/** @see DCOPProblemInterface#getProbabilitySpaces() */
	public List<Hypercube<V, U> > getProbabilitySpaces() {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/** @see DCOPProblemInterface#getProbabilitySpaces(java.lang.String) */
	public List< Hypercube<V, U> > getProbabilitySpaces(String var) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}
	
	/** @see DCOPProblemInterface#setProbSpace(java.lang.String, java.util.Map) */
	public void setProbSpace(String var, Map<V, Double> prob) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
	}

	/** @see DCOPProblemInterface#removeSpace(java.lang.String) */
	public boolean removeSpace(String name) {
		
		assert name != null : "The provided name is null";
		
		for (Iterator< ? extends UtilitySolutionSpace<V, U> > iter = this.spaces.iterator(); iter.hasNext(); ) {
			if (name.equals(iter.next().getName())) {
				iter.remove();
				return true;
			}
		}
		
		return false;
	}
	
	/** @see DCOPProblemInterface#addSolutionSpace(UtilitySolutionSpace) */
	public boolean addSolutionSpace(UtilitySolutionSpace<V, U> space) {
		this.spaces.add(space);
		return true;
	}

	/** @see DCOPProblemInterface#incrNCCCs(long) */
	public void incrNCCCs (long incr) {
		this.ncccCount += incr;
	}
	
	/** @see DCOPProblemInterface#setNCCCs(long) */
	public void setNCCCs (long ncccs) {
		this.ncccCount = ncccs;
	}
	
	/** @see DCOPProblemInterface#getNCCCs() */
	public long getNCCCs () {
		return this.ncccCount;
	}
	
	/** @see DCOPProblemInterface#maximize() */
	public boolean maximize() {
		return this.maximize;
	}

	/** @see DCOPProblemInterface#setMaximize(boolean) */
	public void setMaximize(final boolean maximize) {
		
		if (this.maximize != maximize) {
			final U inf = (maximize ? this.getMinInfUtility() : this.getPlusInfUtility());
			for (UtilitySolutionSpace<V, U> space : this.spaces) 
				space.setInfeasibleUtility(inf);
		}
		
		this.maximize = maximize;
	}

	/** @see DCOPProblemInterface#rescale(Addable, Addable) */
	public void rescale(U multiply, U add) {
		
		for (UtilitySolutionSpace<V, U> space : this.spaces) 
			for (UtilitySolutionSpace.Iterator<V, U> iter = space.iterator(); iter.hasNext(); ) 
				iter.setCurrentUtility(iter.nextUtility().multiply(multiply).add(add));
	}
	
	/** 
	 * @see frodo2.solutionSpaces.DCOPProblemInterface#getUtility(java.util.Map) 
	 * @todo Test this method. 
	 */
	public UtilitySolutionSpace<V, U> getUtility (Map<String, V> assignments) {
		return this.getUtility(assignments, false);
	}
	
	/** 
	 * @see DCOPProblemInterface#getUtility(Map, boolean) 
	 * @todo Test this method 
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace<V, U> getUtility(Map<String, V> assignments, boolean withAnonymVars) {
		
		Class<? extends V[]> classOfDom = (Class<? extends V[]>) Array.newInstance(assignments.values().iterator().next().getClass(), 0).getClass();
		U zero = this.getZeroUtility();
		UtilitySolutionSpace<V, U> output = new ScalarHypercube<V, U> (zero, this.getInfeasibleUtil(), classOfDom);
		
		// Extract all hypercubes
		List< ? extends UtilitySolutionSpace<V, U> > hypercubes = this.getSolutionSpaces(withAnonymVars);
		
		// Go through the list of hypercubes
		for (UtilitySolutionSpace<V, U> hypercube : hypercubes) {
			
			// Slice the hypercube over the input assignments
			ArrayList<String> vars = new ArrayList<String> (hypercube.getNumberOfVariables());
			for (String var : hypercube.getVariables()) 
				if (assignments.containsKey(var)) 
					vars.add(var);
			int nbrVars = vars.size();
			V[] values = (V[]) Array.newInstance(classOfDom.getComponentType(), nbrVars);
			for (int i = 0; i < nbrVars; i++) 
				values[i] = assignments.get(vars.get(i));
			UtilitySolutionSpace<V, U> slice = hypercube.slice(vars.toArray(new String[nbrVars]), values);
			
			// Join the slice with the output
			output = output.join(slice);
		}
		
		return output;
	}
	
	/** @return -INF if we are maximizing, +INF if we are minimizing */
	private U getInfeasibleUtil () {
		
		// Check whether we are minimizing or maximizing
		if (maximize) 
			return this.getMinInfUtility();
		else 
			return this.getPlusInfUtility();
	}

	/** @see DCOPProblemInterface#getExpectedUtility(Map) */
	public UtilitySolutionSpace<V, U> getExpectedUtility(Map<String, V> assignments) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return null;
	}

	/** 
	 * @see DCOPProblemInterface#getParamUtility(java.util.Map) 
	 * @todo Test this method.
	 */
	public UtilitySolutionSpace<V, U> getParamUtility (Map< String[], BasicUtilitySolutionSpace< V, ArrayList<V> > > assignments) {

		@SuppressWarnings("unchecked")
		Class<? extends V[]> classOfDom = (Class<? extends V[]>) Array.newInstance(assignments.values().iterator().next().getUtility(0).get(0).getClass(), 0).getClass();
		U zero = this.getZeroUtility();
		UtilitySolutionSpace<V, U> output = new ScalarHypercube<V, U> (zero, this.getInfeasibleUtil(), classOfDom);

		// Go through the list of spaces
		for (UtilitySolutionSpace<V, U> space : this.spaces) {

			// Compose the space with each input assignment
			UtilitySolutionSpace<V, U> composition = space;
			for (Map.Entry< String[], BasicUtilitySolutionSpace< V, ArrayList<V> > > entry : assignments.entrySet()) 
				composition = composition.compose(entry.getKey(), entry.getValue());

			// Join the composition with the output
			output = output.join(composition);
		}

		return output;
	}

	/** @see DCOPProblemInterface#getNumberOfCoordinationConstraints() */
	public int getNumberOfCoordinationConstraints() {
		
		int count = 0;
		
		spaceLoop: for (UtilitySolutionSpace<V, U> space : this.spaces) {
			
			String[] vars = space.getVariables();
			
			if (vars.length <= 1) 
				continue;
			
			String firstAgent = null;
			for (String var : vars) {
				String agent = this.owners.get(var);
				
				if (agent == null) 
					continue;
				else if (firstAgent == null) 
					firstAgent = agent;
				else if (! firstAgent.equals(agent)) {
					count++;
					continue spaceLoop;
				}
			}
		}
		
		return count;
	}

	/** 
	 * @see ProblemInterface#getSubProblem(java.lang.String) 
	 * @todo Test this method. 
	 */
	@SuppressWarnings("unchecked")
	public Problem<V, U> getSubProblem(String agent) {
		
		Problem<V, U> out = new Problem<V, U> (this.maximize);
		out.setAgent(agent);
		out.setUtilClass(this.utilClass);

		// Look up the agent's variables
		HashSet<String> vars = new HashSet<String> (this.owners.size());
		for (Map.Entry<String, String> entry : this.owners.entrySet()) 
			if (agent.equals(entry.getValue())) 
				vars.add(entry.getKey());
		
		// Compile the list of spaces involving one of the agent's variables
		ArrayList< UtilitySolutionSpace<V, U> > newSpaces = new ArrayList< UtilitySolutionSpace<V, U> > (this.spaces.size());
		for (UtilitySolutionSpace<V, U> space : this.spaces) {
			if (! Collections.disjoint(vars, Arrays.asList(space.getVariables()))) {
				UtilitySolutionSpace<V, U> clone = space.clone();
				clone.setProblem(out);
				newSpaces.add(clone);
				out.addSolutionSpace(clone);
			}
		}
		
		// Add the neighboring variables
		for (UtilitySolutionSpace<V, U> space : newSpaces) 
			vars.addAll(Arrays.asList(space.getVariables()));
		
		// Add the variables to the output subproblem
		for (String var : vars) 
			out.addVariable(var, this.owners.get(var), this.domains.get(var));
		
		if (this.publicAgents) { // Add foo variables for missing agents
			
			Set<String> missingAgents = this.getAgents();
			missingAgents.removeAll(out.getAgents());
			if (! missingAgents.isEmpty()) {
				Random rand = new Random ();
				for (String missing : missingAgents) 
					out.addVariable("foo_agent_" + missing + "_" + rand.nextInt(Integer.MAX_VALUE), missing, (V[]) new AddableInteger [0]);
			}
		}
		
		return out;
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#multipleTypes()
	 */
	public boolean multipleTypes() {
		return false;
	}

	/** @see frodo2.solutionSpaces.DCOPProblemInterface#getAgentNeighborhoods(java.lang.String) */
	@Override
	public Map<String, Collection<String>> getAgentNeighborhoods(String owner) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}
	
}
