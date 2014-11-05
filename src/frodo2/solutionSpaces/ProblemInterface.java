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

import java.io.Serializable;
import java.util.Set;

/** A general problem
 * @author Brammert Ottens, Thomas Leaute, 8 jun 2010
 * @param <V> type used for domain values
 * @param <U> type used for utility values
 * 
 */
public interface ProblemInterface <V extends Addable<V>, U extends Addable<U>> extends Serializable {

	/** Resets this problem to be the same as the input one
	 * @param newProblem 	the problem 
	 */
	public void reset (ProblemInterface<V, U> newProblem);
	
	/** Sets the class to be used for variable values
	 * @param domClass 	the class for variable values
	 */
	public void setDomClass (Class<V> domClass);
	
	/** @return the class used for domain values */
	public Class<V> getDomClass ();
	
	/** Sets the class to be used for utility values
	 * @param utilClass 	the class for utility values
	 */
	public void setUtilClass (Class<U> utilClass);
	
	/** @return a utility of value 0 */
	public U getZeroUtility ();
	
	/** @return the infinite positive utility value */
	public U getPlusInfUtility ();
	
	/** @return the infinite negative utility value */
	public U getMinInfUtility ();
	
	/** @return the name of the agent corresponding to this subproblem */
	public String getAgent ();
	
	/** @return the set of all agents mentioned in the problem */
	public Set<String> getAgents();
	
	/** Builds the subproblem description for a given agent by extracting it from the overall problem description
	 * @param agent 	the name of the agent
	 * @return 			the subproblem corresponding to \a agent, or \c null if \a agent owns no variable
	 */
	public ProblemInterface<V, U> getSubProblem (String agent);
	
	/** @return \c true when agents can be of different types, and \c false otherwise */
	public boolean multipleTypes();
	
	/** Increments the number of constraint checks
	 * @param incr 	the increment
	 */
	public void incrNCCCs (long incr);
	
	/** @return the number of constraint checks */
	public long getNCCCs ();
	
	/** Sets the NCCC count
	 * @param ncccs 	the NCCC count
	 */
	public void setNCCCs (long ncccs);
	
	
}
