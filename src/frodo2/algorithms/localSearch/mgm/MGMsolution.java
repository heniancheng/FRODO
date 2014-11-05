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

package frodo2.algorithms.localSearch.mgm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import frodo2.algorithms.ConvergenceInterface;
import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment;
import frodo2.solutionSpaces.Addable;

/** An optimal solution to the problem
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @author Brammert Ottens, Thomas Leaute
 */
public class MGMsolution<V extends Addable<V>, U> extends Solution <V, U> implements ConvergenceInterface<V> {

	/** The assignment history for all the agents */
	private HashMap<String, ArrayList<CurrentAssignment<V>>> assignmentHistories;
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables occurring in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 					the optimal assignments
	 * @param nbrMsgs						the total number of messages that have been sent
	 * @param totalMsgSize					the total amount of information that has been exchanged (in bytes)
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param ncccCount 					the ncccs used
	 * @param timeNeeded 					the time needed to solve the problem
	 * @param moduleEndTimes 				each module's end time
	 * @param assignmentHistories 			the history of variable assignments
	 */
	public MGMsolution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, long totalMsgSize, long maxMsgSize, 
			long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, 
			HashMap< String, ArrayList< CurrentAssignment<V> > > assignmentHistories) {
		super(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, totalMsgSize, maxMsgSize, ncccCount, timeNeeded, moduleEndTimes, 0, 0);
		this.assignmentHistories = assignmentHistories;
	}

	/** @return the history of variable assignments */
	public HashMap<String, ArrayList<CurrentAssignment<V>>> getAssignmentHistories() {
		return assignmentHistories;
	}
}