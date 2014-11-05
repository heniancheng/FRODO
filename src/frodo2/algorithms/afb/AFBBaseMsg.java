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

import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;

/** All AFB messages containing a CPA subclass this class 
 * @author Alexandra Olteanu, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public abstract class AFBBaseMsg < V extends Addable<V>, U extends Addable<U> >  extends Message implements Externalizable 
{
		/** The destination variable */
		public String dest;
		
		/** The partial assignment that is passed over to dest.*/
		protected PA<V, U> pa; /// @todo Backtrack messages don't need to contain the CPA. 
		
		/**Timestamp for the PA the message carries*/
		protected Timestamp timestamp;
				
		/** Empty constructor used for externalization */
		public AFBBaseMsg () 
		{
			super ();
		}
		
		/** Constructor
		 * @param type 			The type of the message
		 * @param dest 			The destination variable
		 * @param pa		 	The current PA
		 * @param timestamp 	Timestamp for this PA
		 */
		protected AFBBaseMsg (String type, String dest, PA<V, U> pa, Timestamp timestamp) {
			super (type);
			this.dest = dest;
			this.pa=pa;
			this.timestamp=timestamp;
		}
		
		/** @see Message#toString() */
		@Override
		public String toString () {
			return super.toString() + 
				"\n\t dest: " + dest + 
				"\n\t pa: " + pa + 
				"\n\t timestamp: " + this.timestamp;
		}

		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException 
		{
			super.writeExternal(out);
			out.writeObject(this.dest);
			this.pa.writeExternal(out);
			this.timestamp.writeExternal(out);
		}
		
		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			this.dest = (String) in.readObject();
			this.pa = new PA<V, U> ();
			this.pa.readExternal(in);
			this.timestamp = new Timestamp ();
			this.timestamp.readExternal(in);
		}
	}
