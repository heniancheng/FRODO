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

/** Message used by the SamplingPhase module in phase 2, to which samples have been added
 * @param <V> the type used for random variable values
 */
public class SamplesMsg2 < V extends Addable<V> > extends LCAmsg2 {
	
	/** Used for serialization */
	private static final long serialVersionUID = 6185595975417830789L;
	
	/** For each random variables, its samples */
	private HashMap< String, Map<V, Double> > samples;
	
	/** Empty constructor used for externalization */
	public SamplesMsg2 () { }

	/** @see LCAmsg2#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(this.samples);
	}

	/** @see LCAmsg2#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.samples = (HashMap<String, Map<V, Double>>) in.readObject();
	}

	/** Constructor 
	 * @param node 		a variable in the DFS
	 * @param flags 	a set of flags
	 * @param samples 	for each random variable, its samples
	 */
	public SamplesMsg2(String node, HashSet<String> flags, HashMap< String, Map<V, Double> > samples) {
		super(SamplingPhase.AtLCAs.PHASE2_MSG_TYPE, node, flags);
		this.samples = samples;
	}
	
	/** @return for each random variable, its samples */
	public HashMap< String, Map<V, Double> > getSamples () {
		return this.samples;
	}
	
	/** @see LCAmsg2#toString() */
	public String toString () {
		return super.toString() + "\n\tsamples = " + this.samples;
	}
}