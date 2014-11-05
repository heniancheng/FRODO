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

import java.io.Externalizable;

/** A limited Addable that only declares a very small subset of the operations supported by an Addable
 * @author Thomas Leaute
 * @param <A> the type of what can be added to this limited Addable
 * @param <M> the type of objects one can compute the max or min with
 */
public interface AddableLimited< A extends Addable<A>, M extends AddableLimited<A, M> > extends Externalizable {

	/** Addition
	 * @param other 	the object to be added to this
	 * @return 			the sum
	 */
	public M add (A other);
	
	/** Minimum
	 * @param other 	another object
	 * @return 			the minimum of this and the input object
	 */
	public M min (M other);
	
	/** Maximum
	 * @param other 	another object
	 * @return 			the maximum of this and the input object
	 */
	public M max (M other);
	
	/** @return whether objects of this class should be serialized as Externalizables, or only as Serializables */
	public boolean externalize ();
	
	/** Method called just after deserialization
	 * @return this object, or a replacement 
	 */
	public Object readResolve ();
	
}
