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

package frodo2.algorithms.dpop.stochastic;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import frodo2.solutionSpaces.Addable;

/** Phase 1 LCA message containing proposed samples for the random variables
 * @param <V> 	the type used for variable values
 */
public class SamplesMsg1 < V extends Addable<V> > extends LCAmsg1 {
	
	/** For each random variable, its proposed samples */
	HashMap< String, Map<V, Double> > samples;
	
	/** Empty constructor used for externalization */
	public SamplesMsg1 () { }

	/** @see LCAmsg1#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(this.samples);
	}

	/** @see LCAmsg1#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.samples = (HashMap<String, Map<V, Double>>) in.readObject();
	}

	/** Constructor
	 * @param sender 	the sender of the message
	 * @param dest 		the destination of the message
	 * @param samples 	for each random variable, its proposed samples
	 */
	public SamplesMsg1(String sender, String dest, HashMap< String, Map<V, Double> > samples) {
		super(sender, dest, new HashSet<String> (samples.keySet()));
		this.samples = samples;
	}

	/** @return for each random variable, its proposed samples */
	public HashMap< String, Map<V, Double> > getSamples () {
		return this.samples;
	}

	/** @see LCAmsg1#toString() */
	public String toString () {
		return super.toString() + "\n\tsamples = " + this.samples;
	}
}