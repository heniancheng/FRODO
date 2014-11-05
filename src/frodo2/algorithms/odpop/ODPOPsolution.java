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

package frodo2.algorithms.odpop;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import frodo2.algorithms.Solution;

/** An optimal solution to the problem
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @author Brammert Ottens, Thomas Leaute
 */
public class ODPOPsolution<V, U> extends Solution<V, U> {

	/** The percentage of the total number of possible goods that has been sent*/
	private double percentageOfGoodsSent;
	
	/** The average percentage of the the separatorSpace being filled */
	private double averageTreeFillPercentage;
	
	/** The average percentage of the tree filled with dummy nodes*/
	private double dummyFillPercentage;
	
	/** The absolute number of dummy nodes in the tree */
	private double numberOfDummies;
	
	/** The maximal value with which a utility value has been cut */
	private U maximalCutSum;
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables occurring in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 					the optimal assignments
	 * @param nbrMsgs						The total number of messages sent
	 * @param msgNbrs						The number of messages sent per message type
	 * @param totalMsgSize					The total message size
	 * @param msgSizes 						The amount of information sent per message type
	 * @param maxMsgSize 					the size (in bytes) of the largest message
	 * @param maxMsgSizes 					for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 					the ncccs used
	 * @param timeNeeded 					the time needed to solve the problem
	 * @param moduleEndTimes 				each module's end time
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 * @param averageTreeFillPercentage 	the average percentage of the solutionspaces that are filled
	 */
	public ODPOPsolution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraints, double averageTreeFillPercentage) {
		super (nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, totalMsgSize, maxMsgSize, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints);
		this.averageTreeFillPercentage = averageTreeFillPercentage;
		this.msgNbrs = msgNbrs;
		this.msgSizes = msgSizes;
		this.maxMsgSizes = maxMsgSizes;
	}
	
	/** Constructor 
	 * @param nbrVariables					the total number of variables occurring in the problem
	 * @param reportedUtil 					the reported optimal utility
	 * @param trueUtil 						the true optimal utility
	 * @param assignments 					the optimal assignments
	 * @param nbrMsgs						The total number of messages sent
	 * @param msgNbrs						The number of messages sent per message type
	 * @param totalMsgSize					The total message size
	 * @param msgSizes 						The amount of information sent per message type
	 * @param maxMsgSize 					the size (in bytes) of the largest message
	 * @param maxMsgSizes 					for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 					the ncccs used
	 * @param timeNeeded 					the time needed to solve the problem
	 * @param moduleEndTimes 				each module's end time
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 * @param averageTreeFillPercentage 	the average percentage of the solutionspaces that are filled
	 * @param percentageOfGoodsSent			the percentage of possible goods that have been sent
	 * @param maximalCutSum 				The maximal value with which a utility value has been cut
	 */
	public ODPOPsolution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraints, double averageTreeFillPercentage, double percentageOfGoodsSent, U maximalCutSum) {
		this (nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints, averageTreeFillPercentage);
		this.percentageOfGoodsSent = percentageOfGoodsSent;
		this.maximalCutSum = maximalCutSum;
	}
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables occurring in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 					the optimal assignments
	 * @param nbrMsgs						The total number of messages sent
	 * @param msgNbrs						The number of messages sent per message type
	 * @param totalMsgSize					The total message size
	 * @param msgSizes 						The amount of information sent per message type
	 * @param maxMsgSize 					the size (in bytes) of the largest message
	 * @param maxMsgSizes 					for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 					the ncccs used
	 * @param timeNeeded 					the time needed to solve the problem
	 * @param moduleEndTimes 				each module's end time
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 * @param treeWidth 					the width of the tree on which the algorithm has run
	 * @param averageTreeFillPercentage 	the average percentage of the solutionspaces that are filled
	 * @param dummyFillPercentage 			the dummy fill percentage
	 * @param numberOfDummies 				the total number of dummy nodes
	 * @param percentageOfGoodsSent			the percentage of possible goods that have been sent
	 * @param maximalCutSum 				The maximal value with which a utility value has been cut
	 */
	public ODPOPsolution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraints, double averageTreeFillPercentage, double percentageOfGoodsSent, int treeWidth, double dummyFillPercentage, double numberOfDummies, U maximalCutSum) {
		this(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints, averageTreeFillPercentage, percentageOfGoodsSent, maximalCutSum);
		this.treeWidth = treeWidth;
		this.dummyFillPercentage = dummyFillPercentage;
		this.numberOfDummies = numberOfDummies;
	}
	
	/** 
	 * @return the average fill percentage of the solutionspaces 
	 */
	public double getAverageFillTree() {
		return this.averageTreeFillPercentage;
	}
	
	/** @return the dummy fill percentage */
	public double getAverageDummyFillPercentage() {
		return this.dummyFillPercentage;
	}
	
	/**@return the total number of dummy nodes visited */
	public double getAverageNumberOfDummies() {
		return this.numberOfDummies;
	}
	
	/**
	 * @author Brammert Ottens, 7 feb. 2011
	 * @return the percentage of possible goods that have been sent
	 */
	public double getPercentageOfGoodsSent() {
		return percentageOfGoodsSent;
	}
	
	/**
	 * @author Brammert Ottens, 7 feb. 2011
	 * @return the percentage of possible goods that have been sent
	 */
	public U getMaximalCutSum() {
		return this.maximalCutSum;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		StringBuilder builder = new StringBuilder (super.toString());
		
		if (this.averageTreeFillPercentage > 0) {
			builder.append("\n");
			builder.append("\n\t- averageTreeFillPercentage: \t" + this.averageTreeFillPercentage);
			builder.append("\n\t- average number of dummies: \t" + this.dummyFillPercentage);
		}
		
		if(this.percentageOfGoodsSent > 0) {
			builder.append("\n");
			builder.append("\n\t- percentage of possible goods sent: \t" + this.percentageOfGoodsSent);
		}
		
		if(this.maximalCutSum != null) {
			builder.append("\n");
			builder.append("\n\t- maximal cut sum: \t" + this.maximalCutSum);
		}
		return builder.toString();
	}

}
