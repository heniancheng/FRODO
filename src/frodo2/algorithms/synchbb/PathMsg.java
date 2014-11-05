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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.solutionSpaces.Addable;

/** The message containing the current partial assignment in SynchBB
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class PathMsg < V extends Addable<V>, U extends Addable<U> > extends BTmsg {
	
	/** The current assignments to variables */
	V[][] assignments;
	
	/** The first "offset" values of the solution are not sent because they have not changed since the last PATH message */
	int offset;
	
	/** The number of assignments to send (ignoring the offset) */
	int nbrAssignments;
	
	/** The cost of the current partial assignment */
	U cost;
	
	/** Empty constructor used for externalization */
	public PathMsg () {
		super.type = SynchBB.PATH_MSG_TYPE;
	}
	
	/** Constructor
	 * @param dest 				The destination variable
	 * @param assignments 		The current assignments to variables
	 * @param offset 			The first "offset" values of the solution are not sent because they have not changed since the last PATH message
	 * @param nbrAssignments 	The number of assignments to send
	 * @param cost 				The cost of the current partial assignment
	 */
	public PathMsg (String dest, V[][] assignments, int offset, int nbrAssignments, U cost) {
		super (SynchBB.PATH_MSG_TYPE, dest);
		this.assignments = assignments;
		this.offset = offset;
		this.nbrAssignments = nbrAssignments;
		this.cost = cost;
	}

	/** @see BTmsg#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		
		assert this.offset < Short.MAX_VALUE;
		out.writeShort(this.offset);
		assert this.nbrAssignments < Short.MAX_VALUE;
		out.writeShort(this.nbrAssignments);
		V val = this.assignments[this.offset][0];
		out.writeObject(val); // first assignment
		final boolean externalize = val.externalize();
		
		// Write the assignments one by one
		for (int i = this.offset; i < this.nbrAssignments; i++) { // the assignments
			V[] assignment = this.assignments[i];
			assert assignment.length < Short.MAX_VALUE;
			out.writeShort(assignment.length);
			for(int j = assignment.length - 1; j >= 0; j--){
				if (externalize) 
					assignment[j].writeExternal(out);
				else 
					out.writeObject(assignment[j]);
				}
		}
		
		out.writeObject(this.cost);
	}
	
	/** @see BTmsg#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		
		// Read the number of assignments and the assignment to the first variable 
		// so that we can instantiate the array of assignments
		this.offset = in.readShort();
		this.nbrAssignments = in.readShort();
		V val = (V) in.readObject();
		this.assignments = (V[][]) Array.newInstance(Array.newInstance(val.getClass(), 0).getClass(), nbrAssignments);
		final boolean externalize = val.externalize();
		
		// Read the assignments one by one
		for (int i = this.offset; i < this.nbrAssignments; i++) {
			short nbrVars = in.readShort();
			V[] assignment = (V[]) Array.newInstance(val.getClass(), nbrVars);
			this.assignments[i] = assignment;
			for(int j = nbrVars - 1; j >= 0; j--){
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					assignment[j] = (V) val.readResolve();
				} else 
					assignment[j] = (V) in.readObject();
			}
		}
		
		this.cost = (U) in.readObject();
	}

	/** @see BTmsg#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tassignment: " + 
		Arrays.deepToString(this.assignments) + 
		"\n\toffset: " + this.offset +
		"\n\tnbrAssignments: " + this.nbrAssignments + 
		"\n\tcost: " + this.cost;
	}
}