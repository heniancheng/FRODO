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
import frodo2.solutionSpaces.Addable;


/** The FB_CPA message in AFB
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @author Alexandra Olteanu, Thomas Leaute
 */
public class FbCpaMsg < V extends Addable<V>, U extends Addable<U> > extends AFBBaseMsg<V, U>
implements Externalizable
{
	/** The sender of the message.*/
	String sender;
					
	/** Empty constructor used for externalization */
	public FbCpaMsg () {
		super.type = AFB.FB_CPA_TYPE;
	}

	/** Constructor
	 * @param dest 				The destination variable
	 * @param sender			The sender variable 
	 * @param pa 				The current PA
	 * @param timestamp 		Timestamp associated to the PA
	 */
	public FbCpaMsg (String dest, String sender, PA<V, U> pa, Timestamp timestamp) 
	{
		super (AFB.FB_CPA_TYPE, dest, pa,timestamp);
		this.sender = sender;
	}
	
	/** @see AFBBaseMsg#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\t sender: " + this.sender;
	}

	/** @see AFBBaseMsg#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException 
	{
		super.writeExternal(out);
		out.writeObject(this.sender);
	}
				
	/** @see AFBBaseMsg#readExternal(java.io.ObjectInput) */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException 
	{
		super.readExternal(in);
		this.sender = (String) in.readObject();
	}
}
