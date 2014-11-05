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

package frodo2.algorithms.synchbb;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;

/** A message containing the optimal solution found so far 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class SolutionMsg < V extends Addable<V>, U extends Addable<U> > extends Message implements Externalizable {
	
	/** ID of the component in the constraint graph */
	transient Comparable<?> componentID;
	
	/** The chosen assignments to variables */
	transient V[][] solution;
	
	/** The optimal cost */
	transient U cost;
	
	/** Empty constructor used for externalization */
	public SolutionMsg () { }
	
	/** Constructor
	 * @param type 		the type of  this message
	 * @param componentID 	The ID of the component in the constraint graph
	 * @param solution 	the chosen assignments to variables
	 * @param cost 		the optimal cost
	 */
	public SolutionMsg (String type, Comparable<?> componentID, V[][] solution, U cost) {
		super (type);
		this.componentID = componentID;
		this.solution = solution;
		this.cost = cost;
	}

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.type);
		out.writeObject(this.componentID);
		out.writeObject(this.cost);
		
		// Don't serialize the assignments if the problem is infeasible
		if (this.cost.compareTo(this.cost.getPlusInfinity()) < 0) {
			assert this.solution.length < Short.MAX_VALUE;
			out.writeShort(this.solution.length);
			V val = this.solution[0][0];
			out.writeObject(val); // first value
			final boolean externalize = val.externalize();
			for (short i = 0; i < this.solution.length; i++) { // all the values
				V[] assignment = this.solution[i];
				assert assignment.length < Short.MAX_VALUE;
				out.writeShort(assignment.length);
				for(short j = 0; j < assignment.length; j++){
					if (externalize) 
						assignment[j].writeExternal(out);
					else 
						out.writeObject(assignment[j]);
				}
			}
		}
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.type = (String) in.readObject();
		this.componentID = (Comparable<?>) in.readObject();
		this.cost = (U) in.readObject();
		
		// Don't read the assignments if the problem is infeasible
		if (this.cost.equals(this.cost.getPlusInfinity())) 
			return;
		
		// Read the total number of variables and the first assignment 
		// in order to initialize the array of assignments
		final int nbrClusters = in.readShort();
		V val = (V) in.readObject();
		
		this.solution = (V[][]) Array.newInstance(Array.newInstance(val.getClass(), 0).getClass(), nbrClusters);
		
		final boolean externalize = val.externalize();
		
		// Read the assignments
		for (int i = 0; i < nbrClusters; i++) {
			final int nbrVars = in.readShort();
			V[] assignment = (V[]) Array.newInstance(val.getClass(), nbrVars);
			this.solution[i] = assignment;
			for (int j = 0; j < nbrVars; j++) {
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					assignment[j] = (V) val.readResolve();
				} else 
					assignment[j] = (V) in.readObject();
			}
		}
	}
	
	/** @return the ID of the component in the constraint graph */
	public Comparable<?> getCompID () {
		return this.componentID;
	}

	/** @return optimal assignments */
	public V[][] getSolution () {
		return this.solution;
	}
	
	/** @return the optimal cost */
	public U getCost () {
		return this.cost;
	}
	
	/** @see MessageWith2Payloads#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tcompID: " + this.componentID + "\n\tassignment: " + 
		(this.getSolution() == null ? null : Arrays.deepToString(this.getSolution())) + 
		"\n\tcost: " + this.getCost();
	}
}