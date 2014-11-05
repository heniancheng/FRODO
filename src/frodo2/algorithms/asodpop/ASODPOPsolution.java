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

package frodo2.algorithms.asodpop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import frodo2.algorithms.ConvergenceInterface;
import frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment;
import frodo2.algorithms.odpop.ODPOPsolution;
import frodo2.solutionSpaces.Addable;

/** An optimal solution to the problem
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @author Brammert Ottens, Thomas Leaute
 */
public class ASODPOPsolution<V extends Addable<V>, U> extends ODPOPsolution <V, U> implements ConvergenceInterface<V> {

	/** The assignment history for all the agents */
	private HashMap<String, ArrayList<CurrentAssignment<V>>> assignmentHistories;
	
	/** The total number of speculative UTIL messages that have been sent*/
	int specUILmsgs;
	
	/** The total number of UTIL messages that have been sent*/
	int totalUTILmsgs;

	/** Constructor 
	 * @param nbrVariables		the total number of variables occuring in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 					the optimal assignments
	 * @param nbrMsgs						the total number of messages that have been sent
	 * @param msgNbrs						the number of messages per message type
	 * @param totalMsgSize					the total amount of information that has been exchanged (in bytes)
	 * @param msgSizes						the amount of information per message type
	 * @param maxMsgSize 					the size (in bytes) of the largest message
	 * @param maxMsgSizes 					for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 					the ncccs used
	 * @param timeNeeded 					the time needed to solve the problem
	 * @param moduleEndTimes 				each module's end time
	 * @param numberOfCoordinationConstraint the number of constraints that contain variables that are owned by different agents
	 * @param assignmentHistories 			the history of variable assignments
	 * @param averageTreeFillPercentage 	the average percentage of the tree filled
	 * @param treeWidth 					the tree width of the DFS tree
	 * @param dummyFillPercentage 			the average dummy fill percentage
	 * @param numberOfDummies 				the total number of dummies
	 * @param specUTILmsgs 					the total number of speculative UTIL messages that have been sent
	 * @param totalUTILmsgs 				the total number of UTIL messages that have been sent
	 */
	public ASODPOPsolution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraint, 
			HashMap< String, ArrayList< CurrentAssignment<V> > > assignmentHistories, double averageTreeFillPercentage, int treeWidth, double dummyFillPercentage, double numberOfDummies,
			int specUTILmsgs, int totalUTILmsgs) {
		super(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraint, averageTreeFillPercentage);
		this.assignmentHistories = assignmentHistories;
		this.specUILmsgs = specUTILmsgs;
		this.totalUTILmsgs = totalUTILmsgs;
	}

	/** @return the history of variable assignments */
	public HashMap<String, ArrayList<CurrentAssignment<V>>> getAssignmentHistories() {
		return assignmentHistories;
	}
	
	/** @return the total number of speculative UTIL messages that have been sent */
	public int getSpecUTILmsgs() {
		return this.specUILmsgs;
	}
	
	/** @return the total number of UTIL messages that have been sent */
	public int getUTILmsgs() {
		return this.totalUTILmsgs;
	}
}