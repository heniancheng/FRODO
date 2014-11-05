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

package frodo2.solutionSpaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The subproblem to be solved by a given agent
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public interface DCOPProblemInterface < V extends Addable<V>, U extends Addable<U> > extends ProblemInterface<V, U> {
	
	/** @return all variables, including the ones with no specified owner */
	public Set<String> getAllVars();

	/** @return all variables with a known owner */
	public Set<String> getVariables();
	
	/** Returns the set of variables owned by a given agent
	 * @param owner 	the name of the agent; if \c null, returns all variables with no specified owner
	 * @return 			a set of variables owned by \a owner
	 */
	public Set<String> getVariables(String owner);
	
	/** @return the internal variables */
	public Set<String> getMyVars ();
	
	/** @return the number of internal variables */
	public int getNbrIntVars ();
	
	/** @return the total number of variables */
	public int getNbrVars ();
	
	/** @return the variables that are owned by a different agent */
	public Set<String> getExtVars ();
	
	/** @return the variables with no specified owner */
	public Set<String> getAnonymVars ();
	
	/** Adds a new variable
	 * @param name 		variable name
	 * @param owner 	name of the variable's agent
	 * @param domain 	reference to the variable's domain
	 * @return 			\c true if the variable was added, \c false if a variable with the same name already exists or no domain with given name exists
	 */
	public boolean addVariable (String name, String owner, String domain);
	
	/** Adds a new variable
	 * @param name 		variable name
	 * @param owner 	name of the variable's agent
	 * @param domain 	variable domain
	 * @return 			\c true if the variable was added, \c false if a variable with the same name already exists
	 */
	public boolean addVariable (String name, String owner, V[] domain);
	
	/** Looks up the domain of a variable
	 * @param var 	the name of the variable
	 * @return 		the domain for the input variable, or \c null if the variable or its domain is unknown
	 */
	public V[] getDomain (String var);
	
	/** Returns the size of the domain of the input variable
	 * @param var 	the variable
	 * @return 		the size of the domain of \a var
	 */
	public int getDomainSize (String var);
	
	/** Returns whether the input variable is defined as a random variable
	 * @param var 	the name of the variable
	 * @return 		\c true if the input variable is a random variable, \c false if not or if the variable is unknown
	 */
	public boolean isRandom (String var);
	
	/** @return \c true if this is a maximization problem, \c false otherwise */
	public boolean maximize ();
	
	/** Sets whether utility should be maximized, or cost minimized
	 * @param maximize 	\c true iff this should be a maximization problem
	 */
	public void setMaximize (boolean maximize);
	
	/** Rescales the problem
	 * @param multiply 	multiplies all costs/utilities by \a multiply
	 * @param add 		after multiplying all costs/utilities by \a multiply (if required), adds \a add
	 */
	public void rescale (U multiply, U add);
	
	/** Sets the domain of a variable
	 * @param var 	the name of the variable
	 * @param dom 	the domain; if empty, does nothing
	 */
	public void setDomain (String var, V[] dom);
	
	/** @return for each variable, the name of its owner agent */
	public Map<String, String> getOwners ();
	
	/** Returns the name of the agent owning the input variable
	 * @param var 	the name of the variable
	 * @return 		the owner of the input variable
	 */
	public String getOwner(String var);
	
	/** Sets the owner agent for the input variable
	 * @param var 		the variable
	 * @param owner 	the owner
	 * @return \c false if the owner was not changed because the variable does not exist 
	 */
	public boolean setOwner (String var, String owner);
	
	/** @return for each internal variable, the collection of neighboring agents */
	public Map< String, Collection<String> > getAgentNeighborhoods ();
	
	/** Gets the agent neighborhoods
	 * @param owner 	the owner agent, or null if we want all variables
	 * @return for each variable owned by the input agent (or for each variable if the input is null), the collection of neighboring agents 
	 */
	public Map< String, Collection<String> > getAgentNeighborhoods (String owner);
	
	/** Returns the neighborhood of each internal variable
	 * @return for each of the agent's variables, its collection of neighbors 
	 * @warning Ignores variables with no specified owner. 
	 */
	public Map< String, ? extends Collection<String> > getNeighborhoods ();
	
	/** @return for each internal variable, its collection of neighbors with no specified owner */
	public Map< String, HashSet<String> > getAnonymNeighborhoods ();
	
	/** For each variable owned by the input agent, return its collection of neighbors with no specified owner 
	 * @param agent 	the agent
	 * @return 			for each internal variable, its set of neighbors with no specified owner
	 */
	public Map< String, HashSet<String> > getAnonymNeighborhoods (String agent);
	
	/** Returns the number of neighboring variables of all internal variables
	 * @return for each internal variable, its number of neighboring variables
	 * @warning Ignores variables with no specified owner. 
	 */
	public Map<String, Integer> getNeighborhoodSizes ();
	
	/** Returns the neighbors of the given variable
	 * @param var 	the variable
	 * @return 		the neighbors
	 */
	public Collection<String> getNeighborVars (String var);
	
	/** Returns the collection of neighbors of a given variable
	 * @param var 				the name of the variable
	 * @param withAnonymVars 	if \c false, ignores variables with no specified owner
	 * @return 					a collection of neighbor variables of \a var
	 */
	public HashSet<String> getNeighborVars (String var, final boolean withAnonymVars);
	
	/** Extracts the number of neighbors of an input variable
	 * @param var 	the variable
	 * @return 		the number of neighbor variables of \a var
	 * @warning Ignores variables with no specified owner. 
	 */
	public int getNbrNeighbors (String var);
	
	/** Returns the solution spaces in the problem
	 * @return 			a list of spaces, or \c null if some information is missing
	 * @warning Ignores variables with unknown owners. 
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces ();

	/** Returns the solution spaces in the problem
	 * @param withAnonymVars 	whether spaces involving variables with unknown owners should be taken into account
	 * @return 					a list of spaces, or \c null if some information is missing
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (final boolean withAnonymVars);
	
	/** Returns the solution spaces involving the input variable and none of the forbidden variables
	 * @param var 				the variable of interest
	 * @param forbiddenVars 	any space involving any of these variables will be ignored
	 * @return 					a list of spaces, or \c null if some information is missing
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (String var, Set<String> forbiddenVars);

	/** Extracts solution spaces involving the input variable from the constraints in the problem
	 * @return 					a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param var 				the variable of interest
	 * @param withAnonymVars 	whether hypercubes involving variables with unknown owners should be taken into account
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (String var, final boolean withAnonymVars);
	
	/** Extracts solution spaces involving the input variable from the constraints in the problem
	 * @return 					a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param var 				the variable of interest
	 * @param withAnonymVars 	whether hypercubes involving variables with unknown owners should be taken into account
	 * @param forbiddenVars 	any space involving any of these variables will be ignored
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (String var, final boolean withAnonymVars, Set<String> forbiddenVars);
	
	/** Extracts solution spaces involving the input variables from the constraints in the problem
	 * @return 					a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param vars 				the variables of interest
	 * @param withAnonymVars 	whether hypercubes involving variables with unknown owners should be taken into account
	 * @param forbiddenVars 	any space involving any of these variables will be ignored
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (Set<String> vars, final boolean withAnonymVars, Set<String> forbiddenVars);
	
	/** Returns the probability spaces in the problem
	 * @return 		a list of spaces, or \c null if some information is missing
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getProbabilitySpaces ();
	
	/** Returns the probability spaces involving the input variable
	 * @param var 	the variable of interest
	 * @return 		a list of spaces, or \c null if some information is missing
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getProbabilitySpaces (String var);

	/** Adds to the problem a probability space for the input random variable
	 * @param var 	random variable
	 * @param prob 	weighted samples 
	 */
	public void setProbSpace (String var, Map<V, Double> prob);
	
	/** Removes the space with the given name
	 * @param name 	the name of the space
	 * @return 		\c true if the space was present and had been removed
	 */
	public boolean removeSpace (String name);
	
	/** Adds a solution space to the problem
	 * @param space 	the solution space
	 * @return \c true if the space was added, \c false if the space's name is null or is already taken
	 * @note Ignores the relation name of this space, if any
	 */
	public boolean addSolutionSpace (UtilitySolutionSpace<V, U> space);
	
	/** Computes the total utility of the input assignment to variables, ignoring variables with no specified owner
	 * 
	 * This methods actually returns a UtilitySolutionSpace. If not all variables in the problem are assigned a value, 
	 * the space will represent the utility of the assignment, conditioned on the free variables. 
	 * If all variables are grounded, the method returns a scalar UtilitySolutionSpace. 
	 * 
	 * @param assignments 	values for variables
	 * @return the optimal (possibly conditional) utility corresponding to the input assignment 
	 */
	public UtilitySolutionSpace<V, U> getUtility (Map<String, V> assignments);
	
	/** Computes the total utility of the input assignment to variables
	 * 
	 * This methods actually returns a UtilitySolutionSpace. If not all variables in the problem are assigned a value, 
	 * the space will represent the utility of the assignment, conditioned on the free variables. 
	 * If all variables are grounded, the method returns a scalar UtilitySolutionSpace. 
	 * 
	 * @param assignments 		values for variables
	 * @param withAnonymVars 	if \c false, ignores variable with no specified owner
	 * @return the optimal (possibly conditional) utility corresponding to the input assignment 
	 */
	public UtilitySolutionSpace<V, U> getUtility (Map<String, V> assignments, final boolean withAnonymVars);
	
	/** Computes the expectation over the random variables of the utility for the input assignments
	 * @param assignments 	values for variables
	 * @return the expectation of the utility for the input assignments
	 */
	public UtilitySolutionSpace<V, U> getExpectedUtility (Map<String, V> assignments);
	
	/** Computes the total utility of the input assignment to variables, conditioned on the values of parameters
	 * @param assignments 	values for variables
	 * @return the optimal conditional utility corresponding to the input assignment 
	 */
	public UtilitySolutionSpace<V, U> getParamUtility (Map< String[], BasicUtilitySolutionSpace< V, ArrayList<V> > > assignments);
	
	/**
	 * Returns the number of spaces that are shared between different agents
	 * @author Brammert Ottens, 6 mrt 2010
	 * @return	the number of spaces that are shared between different agents
	 */
	public int getNumberOfCoordinationConstraints();
	
	/** @see ProblemInterface#getSubProblem(java.lang.String) */
	public DCOPProblemInterface<V, U> getSubProblem (String agent);
	
}
