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

package frodo2.algorithms.afb;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;

import frodo2.solutionSpaces.Addable;


/** Class holding a partial assignment for AFB
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @author Alexandra Olteanu, Thomas Leaute
 */
public class PA < V extends Addable<V>, U extends Addable<U> > implements Externalizable
{
	/**current assignments*/
	V[][] assignments;
	
	/**total cost given the assignments up to and including index.*/
	U c;
	
	/**The zero utility.*/
	U zero;
	
	/**position in the assignments vector of the last assignment*/
	int index;	/// @todo Remove this field if it is no longer necessary. 
	
	/** Class of the type used for variable values */
	@SuppressWarnings("rawtypes")
	private Class classOfV;  
		
	/** Class of the type used for variable values */
	@SuppressWarnings("rawtypes")
	private Class classOfU; 
	
	
	/**
	 * @param nbClusters	The number of clusters in the component.
	 * @param classOfV		Class of the type used for variable values 
	 * @param classOfU		Class of the type used for variable values 
	 * @param zero			The zero value for type U.
	 */
	@SuppressWarnings("unchecked")
	public PA(int nbClusters, @SuppressWarnings("rawtypes") Class classOfV,@SuppressWarnings("rawtypes") Class classOfU, U zero) 
	{
		this.assignments = (V[][])Array.newInstance(((V[])Array.newInstance(classOfV, 0)).getClass(), nbClusters);
		this.c = zero;
		this.index = -1; // not started assigning yet
		this.classOfV = classOfV;
		this.classOfU = classOfU;
		this.zero = zero;
	}

	/** Empty constructor used for externalization */
	public PA () { 
	}
	
	/** 
	 * @see java.lang.Object#clone()
	 */
	@SuppressWarnings("unchecked")
	public PA<V, U> clone()
	{
		PA<V, U> res;
		if (this.assignments != null)
		{
			res = new PA<V, U> (this.assignments.length, this.classOfV, this.classOfU, this.zero);
			res.assignments =  (V[][])Array.newInstance(((V[])Array.newInstance(classOfV, 0)).getClass(), this.assignments.length);
			for (int i=0; i<this.assignments.length; i++)
				if (this.assignments[i]!=null)
					res.assignments[i] = this.assignments[i].clone();
		}
		else
		{
			res  = new PA<V, U> (0, this.classOfV, this.classOfU, this.zero);
		}
		res.c = this.c;
		res.index = this.index;
		res.zero = this.zero;
		return res;
	}
	
	/** 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		String res="Assignments: ";
		res += Arrays.deepToString(this.assignments);
		res+=" index: "+this.index+" ";
		res+=" Cost: "+ this.c;
		return res;
	}
	
	/** @see Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException 
	{
		// Read the number of assignments and the assignment to the first variable 
		// so that we can instantiate the array of assignments
		int n = in.readShort();
		V val = (V) in.readObject();
		this.assignments = (V[][]) Array.newInstance(Array.newInstance(val.getClass(),0).getClass(), n);
		final boolean externalize = val.externalize();
		this.classOfV = val.getClass();
		
		// Read the assignments one by one
		for (int i = 0; i < n; i++) {
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
		
		this.c = (U) in.readObject();
		this.zero = c.getZero();
		this.classOfU = this.c.getClass();
		this.index = in.readShort();
	}

	
	/** @see Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException 
	{
		assert this.assignments.length < Short.MAX_VALUE;
		out.writeShort(this.assignments.length);
		
		V val = this.assignments[0][0];
		assert (val!=null);
		out.writeObject(val); // first assignment //@bug is it possible that assignment[0] is null? 
		final boolean externalize = val.externalize();
		
		// Write the assignments one by one
		for (int i = 0; i < this.assignments.length; i++) { // the assignments
			V[] assignment = this.assignments[i];
			int len=0;
			if (assignment != null)
				len = assignment.length;
			assert len < Short.MAX_VALUE;
			out.writeShort(len);
			for(int j = len - 1; j >= 0; j--){
				if (externalize) 
					assignment[j].writeExternal(out);
				else 
					out.writeObject(assignment[j]);
				}
		}
		out.writeObject(this.c);
		out.writeShort(this.index);
	}
}