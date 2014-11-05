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
import java.util.Arrays;


/** Timestamp mechanism for AFB.
 * @author Alexandra Olteanu, Thomas Leaute
 */
public class Timestamp implements Externalizable  
{ 
	/** The assignment counter for each variable*/
	int[] counters;

	/** Constructor
	 * @param n		 		the number of assignment counters
	 */
	public Timestamp(int n)
	{
		this.counters = new int[n];
	}
	
	/** Default constructor used for externalization.*/
	public Timestamp() { }
	
	/** @see java.lang.Object#clone() */
	@Override
	public Timestamp clone()
	{
		if (this.counters!=null)
			return new Timestamp(this.counters.clone());
		else 
			return new Timestamp (0);
	}
	
	
	/** Constructor
	 * @param counters		the assignment counters
	 */
	public Timestamp(int[] counters)
	{
		this.counters = counters;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		return Arrays.toString(this.counters);
	}

	/** Method to assign a value to a given counter.
	 * @param pos						the index position of the counter
	 * @param counterValue				the value to assign to counter at position pos
	 */
	public void setCounter(int pos, int counterValue) 
	{
		assert pos >= 0 && pos < this.counters.length : "The index pos should be between 0 and couters.length-1.";
		this.counters[pos]=counterValue;
	}
		
	/** Method to lexicographically compare the current timestamp with another, up to but excluding a given position
	 * @param timestampToCompare		the timestamp to compare to
	 * @param pos						the index position. Comparison would be done up to position pos-1
	 * @return							-1 if the current timestamp is less than the timestamp to compare to
	 * 									0 if the current timestamp is equal to the timestamp to compare to
	 * 									+1 if the current timestamp is greater than the timestamp to compare to
	 */
	int compare(Timestamp timestampToCompare, final int pos) 
	{
		assert this.counters.length == timestampToCompare.counters.length : "The two timestamps are not compatible: the length of the assignment vector should be the same for both timestamps.";
		assert pos >= 0 && pos < this.counters.length : "The index pos should be between 0 and n-1 inclusive.";
		
		for (int i=0; i<=pos; i++)
		{
			if (this.counters[i] < timestampToCompare.counters[i])
				return -1;
			if (this.counters[i] > timestampToCompare.counters[i])
				return 1;			
		}
		return 0;
	}

	/** @see Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException 
	{	
		int n = in.readShort();
		this.counters = new int[n];

		for (int i=0; i<n; i++)
			counters[i] = in.readInt();
	}
	
	/** @see Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput out) throws IOException 
	{
		// Write the counters one by one
		int n = this.counters.length;
		assert n < Short.MAX_VALUE;
		out.writeShort(n);
		for (int i = 0; i < n; i++)
			out.writeInt(this.counters[i]);
	}
}