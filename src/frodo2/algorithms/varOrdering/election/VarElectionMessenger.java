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

package frodo2.algorithms.varOrdering.election;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Random;



/** A version of LeaderElectionMaxID that uses random numbers as IDs and lies in order to protect topology privacy
 * @author Eric Zbinden, Thomas Leaute
 */
public class VarElectionMessenger extends LeaderElectionMaxID<Integer> {
	
	/** The number of last steps during which the variable tells the truth */
	private int nbrTruthfulSteps;
	
	/** A random stream */
	private Random rand;
	

	/** Constructor 
	 * @param comID communication ID used to identify this agent as the sender of messages
	 * @param myID ID that uniquely identifies this agent
	 * @param neighbors the neighbors of this agent
	 * @param minNbrLies Minimal authorized number of lies. 
	 * @note \a minNbrLies must be an upper bound the diameter of the largest component of the constraint graph for the algorithm to work properly. 
	 */
	public VarElectionMessenger (String comID, int myID, Collection <String> neighbors, int minNbrLies) {
		super(comID, myID, neighbors, 3*minNbrLies);
		
		this.rand = new SecureRandom();
		this.nbrTruthfulSteps = 3 * minNbrLies - nbLies(minNbrLies);
		this.maxID = Integer.MIN_VALUE;
	}


	/**
	 * Compute the number of times the variable will lie. 
	 * @param minNbLies the minimal number of lies.
	 * @return value between minNbLies and (2*minNbLies)
	 */
	private int nbLies(int minNbLies){
		int lie = rand.nextInt(minNbLies+1);
		return lie+minNbLies;
		
	}
	
	/** @return If the var lies it will return a random number between the last maxID sent and its current maxID
	 *  else, it will return maxID
	 */
	@Override
	protected Integer getMaxID (){
		
		// Check whether we should keep lying
		if (this.stepCountdown > this.nbrTruthfulSteps) { // keep lying
			
			if (maxID >= myID) return maxID;
			else {
				
				//get the range, casting to long to avoid overflow problems
			    long range = (long)myID - (long)maxID + 1;
			    // compute a fraction of the range, 0 <= frac < range
			    long fraction = (long)(range * rand.nextDouble());
			    int computedID =  (int)(fraction + maxID);
			    
			    return computedID;
				
			}
			
		//Var is no longer authorized to lie
		} else {
			return Math.max(maxID, myID);
		}
	}

}
