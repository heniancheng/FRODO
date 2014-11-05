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

/** A UtilitySolutionSpace whose utilities implement AddableLimited
 * @author Thomas Leaute
 * @param <V> 	the type used for variable values
 * @param <U> 	the type used for Addable utilities
 * @param <UL> 	the type used for AddableLimited utilities
 */
public interface UtilitySolutionSpaceLimited < V extends Addable<V>, U extends Addable<U>, UL extends AddableLimited<U, UL> > 
extends BasicUtilitySolutionSpace<V, UL> {

	/** Binary join operation
	 * @param space 	a UtilitySolutionSpace
	 * @return 			the joint UtilitySolutionSpaceLimited
	 */
	public UtilitySolutionSpaceLimited<V, U, UL> join (UtilitySolutionSpace< V, U > space);
	
	/** A version of the join method that minimizes the utility lookups in the caller space and the input space
	 * @param space 	the UtilitySolutionSpace to join with this one
	 * @return 			the joint UtilitySolutionSpaceLimited
	 * @see UtilitySolutionSpaceLimited#join(UtilitySolutionSpace)
	 */
	public UtilitySolutionSpaceLimited<V, U, UL> joinMinNCCCs (UtilitySolutionSpace< V, U > space);

	/** Projects out the input variable without computing the corresponding optimal assignments
	 * @param varOut 	the variable to project out
	 * @param maximize 	whether to minimize or maximize
	 * @return 			the optimized UtilitySolutionSpaceLimited
	 */
	public UtilitySolutionSpaceLimited<V, U, UL> blindProject (String varOut, boolean maximize);

	/** Projects out the input variables without computing the corresponding optimal assignments
	 * @param varsOut 	the variables to project out
	 * @param maximize 	whether to minimize or maximize
	 * @return 			the optimized UtilitySolutionSpaceLimited
	 */
	public UtilitySolutionSpaceLimited<V, U, UL> blindProject (String[] varsOut, boolean maximize);
	
	/** Projects out all variables without computing the corresponding optimal assignments
	 * @param maximize 	whether to minimize or maximize
	 * @return 			the optimized utility
	 */
	public UL blindProjectAll (boolean maximize);

	/** Project out a variable by minimizing over it, without computing the argmin
	 * @param variable 	the variable to be projected out
	 * @return 			the optimized UtilitySolutionSpaceLimited
	 */
	public UtilitySolutionSpaceLimited<V, U, UL> min (String variable);
	
	/** Project out a variable by maximizing over it, without computing the argmax
	 * @param variable 	the variable to be projected out
	 * @return 			the optimized UtilitySolutionSpaceLimited
	 */
	public UtilitySolutionSpaceLimited<V, U, UL> max (String variable);
	
	/** @see BasicUtilitySolutionSpace#slice(String, Addable) */
	public UtilitySolutionSpaceLimited<V, U, UL> slice (String var, V val);

	/** @see BasicUtilitySolutionSpace#resolve() */
	public UtilitySolutionSpaceLimited<V, U, UL> resolve ();

}
