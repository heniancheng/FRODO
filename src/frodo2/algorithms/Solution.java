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

package frodo2.algorithms;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** An optimal solution to the problem
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @todo Make all attributes public; get rid of cumbersome constructors, setters and getters 
 */
public class Solution<V, U> {

	/** The reported utility of the solution */
	protected U reportedUtil;

	/** The true utility of the solution */
	protected U trueUtil;

	/** For each variable, its assignment in the solution found to the problem */
	protected Map<String, V> assignments;

	/** The number of ncccs used */
	protected long ncccCount;

	/** The time needed to solve the problem*/
	protected long timeNeeded;
	
	/** The cumulative time needed for all agents to find a solution */
	protected long cumulativeTime;

	/** For each module in the algorithm, the time at which it finished */
	protected HashMap<String, Long> moduleEndTimes;
	
	/** The number of messages sent by the algorithm */
	protected int nbrMsgs;

	/** The total amount of information sent by the algorithm (in bytes) */
	protected long totalMsgSize;
	
	/** The size (in bytes) of the largest message */
	protected long maxMsgSize;
	
	/** The number of variables that occur in the problem */
	private int numberOfVariables;
	
	/** The number of constraints whose variables are owned by different agents */
	private int numberOfCoordinationConstraints;
	
	/** The total number of messages that has been sent per message type */
	protected TreeMap<String, Integer> msgNbrs;
	
	/** The total amount of information that has been sent per message type */
	protected TreeMap<String, Long> msgSizes;

	/** For each message type, the size (in bytes) of the largest message of that type */
	protected TreeMap<String, Long> maxMsgSizes;

	/** The tree width of the tree on which the algorithm has run */
	protected int treeWidth = -1;
	
	/** Constructor 
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param totalMsgSize		The total message size
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param moduleEndTimes 	each module's end time
	 */
	public Solution (U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, long totalMsgSize, long maxMsgSize, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes) {
		this(0, reportedUtil, trueUtil, assignments, nbrMsgs, totalMsgSize, maxMsgSize, ncccCount, timeNeeded, moduleEndTimes, 0);
	}
	/** Constructor 
	 * @param nbrVariables		the total number of variables in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param totalMsgSize		The total message size
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param moduleEndTimes 	each module's end time
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 */
	public Solution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, long totalMsgSize, 
			long maxMsgSize, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraints) {
		this.numberOfVariables = nbrVariables;
		this.reportedUtil = reportedUtil;
		this.trueUtil = trueUtil;
		this.assignments = assignments;
		this.ncccCount = ncccCount;
		this.timeNeeded = timeNeeded;
		this.moduleEndTimes = moduleEndTimes;
		this.nbrMsgs = nbrMsgs;
		this.totalMsgSize = totalMsgSize;
		this.maxMsgSize = maxMsgSize;
		this.numberOfCoordinationConstraints = numberOfCoordinationConstraints;
	}
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param totalMsgSize		The total message size
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param moduleEndTimes 	each module's end time
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 * @param treeWidth 		the width of the tree on which the algorithm has run 
	 */	
	public Solution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, long totalMsgSize, long maxMsgSize, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraints, int treeWidth) {
		this(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, totalMsgSize, maxMsgSize, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints);
		this.treeWidth = treeWidth;
	}
	
	/** Constructor 
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param msgNbrs			The number of messages sent per message type
	 * @param totalMsgSize		The total message size
	 * @param msgSizes 			The amount of information sent per message type
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param maxMsgSizes 		for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param moduleEndTimes 	each module's end time
	 */
	public Solution (U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes) {
		this(0, reportedUtil, trueUtil, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, 0);
	}
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param msgNbrs			The number of messages sent per message type
	 * @param totalMsgSize		The total message size
	 * @param msgSizes 			The amount of information sent per message type
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param maxMsgSizes 		for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param moduleEndTimes 	each module's end time
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 */
	public Solution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int numberOfCoordinationConstraints) {
		this(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, totalMsgSize, maxMsgSize, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints);
		this.msgNbrs = msgNbrs;
		this.msgSizes = msgSizes;
		this.maxMsgSizes = maxMsgSizes;
	}
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param msgNbrs			The number of messages sent per message type
	 * @param totalMsgSize		The total message size
	 * @param msgSizes 			The amount of information sent per message type
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param maxMsgSizes 		for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param moduleEndTimes 	each module's end time
	 * @param treeWidth 		the width of the tree on which the algorithm has run
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 */
	public Solution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, HashMap<String, Long> moduleEndTimes, int treeWidth, int numberOfCoordinationConstraints) {
		this(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints);
		this.treeWidth = treeWidth;
		assert reportedUtil != null;
	}
	
	/** Constructor 
	 * @param nbrVariables		the total number of variables in the problem
	 * @param reportedUtil 		the reported optimal utility
	 * @param trueUtil 			the true optimal utility
	 * @param assignments 		the optimal assignments
	 * @param nbrMsgs			The total number of messages sent
	 * @param msgNbrs			The number of messages sent per message type
	 * @param totalMsgSize		The total message size
	 * @param msgSizes 			The amount of information sent per message type
	 * @param maxMsgSize 		the size (in bytes) of the largest message
	 * @param maxMsgSizes 		for each message type, the size (in bytes) of the largest message of that type
	 * @param ncccCount 		the ncccs used
	 * @param timeNeeded 		the time needed to solve the problem
	 * @param cumulativelTime	the cumulative time needed by all the agents to terminate
	 * @param moduleEndTimes 	each module's end time
	 * @param treeWidth 		the width of the tree on which the algorithm has run
	 * @param numberOfCoordinationConstraints the number of constraints that contain variables that are owned by different agents
	 */
	public Solution (int nbrVariables, U reportedUtil, U trueUtil, Map<String, V> assignments, int nbrMsgs, TreeMap<String, Integer> msgNbrs, long totalMsgSize, TreeMap<String, Long> msgSizes, 
			long maxMsgSize, TreeMap<String, Long> maxMsgSizes, long ncccCount, long timeNeeded, long cumulativelTime, HashMap<String, Long> moduleEndTimes, int treeWidth, int numberOfCoordinationConstraints) {
		this(nbrVariables, reportedUtil, trueUtil, assignments, nbrMsgs, msgNbrs, totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccCount, timeNeeded, moduleEndTimes, numberOfCoordinationConstraints);
		this.treeWidth = treeWidth;
		this.cumulativeTime = cumulativelTime;
		assert reportedUtil != null;
		assert trueUtil != null;
	}
	
	/** @return the reported utility of the solution */
	public U getReportedUtil () {
		return reportedUtil;
	}

	/** @return the true utility of the solution */
	public U getUtility () {
		return trueUtil;
	}

	/** Sets the utility
	 * @param util 	the utility
	 */
	public void setUtility (U util) {
		this.trueUtil = util;
	}

	/** @return the optimal assignments found */
	public Map<String, V> getAssignments() {
		return assignments;
	}

	/** @return the NCCC count */
	public long getNcccCount() {
		return ncccCount;
	}

	/** @return the number of messages sent */
	public int getNbrMsgs() {
		return nbrMsgs;
	}
	
	/** Sets the total number of messages
	 * @param nbrMsgsNew 	new total number of messages
	 */
	public void setNbrMsgs(int nbrMsgsNew) {
		nbrMsgs = nbrMsgsNew;
	}

	/** @return the total amount of information sent (in bytes) */
	public long getTotalMsgSize() {
		return totalMsgSize;
	}
	
	/** @return the size (in bytes) of the largest message */
	public long getMaxMsgSize() {
		return this.maxMsgSize;
	}
	
	/** Sets the total amount of information exchanged
	 * @param totalMsgSizeNew 	new total amount of information exchanged
	 */
	public void setTotalMsgSize(long totalMsgSizeNew) {
		totalMsgSize = totalMsgSizeNew;
	}

	/**
	 * @author Brammert Ottens, 22 jun 2009
	 * @return the time needed to solve the problem
	 */
	public long getTimeNeeded() {
		return this.timeNeeded;
	}
	
	/**
	 * @author Brammert Ottens, 17 jan. 2011
	 * @return the cumulative time needed to solve the problem
	 */
	public long getTotalTime() {
		return this.cumulativeTime;
	}
	
	/** Sets the total runtime
	 * @param timeNeededNew 	new total runtime
	 */
	public void setTimeNeeded(long timeNeededNew) {
		timeNeeded = timeNeededNew;
	}
	
	/**
	 * @author Brammert Ottens, 22 jun 2009
	 * @return the end time of each of the modules 
	 */
	public HashMap<String, Long> getTimesNeeded() {
		return this.moduleEndTimes;
	}
	
	/**
	 * @author Brammert Ottens, 24 aug 2009
	 * @return the total number of messages per message type
	 */
	public TreeMap<String, Long> getMsgSizes() {
		return this.msgSizes;
	}
	
	/** @return for each message type, the size (in bytes) of the largest message */
	public TreeMap<String, Long> getMaxMsgSizes() {
		return this.maxMsgSizes;
	}
	
	/**
	 * @author Brammert Ottens, 24 aug 2009
	 * @return the total amount of information per message type
	 */
	public TreeMap<String, Integer> getMsgNbrs() {
		return this.msgNbrs;
	}
	
	/**
	 * @author Brammert Ottens, 8 sep 2009
	 * @return the tree width of the tree on which the algorithm ran
	 */
	public int getTreeWidth() {
		return this.treeWidth;
	}
	
	/**
	 * @author Brammert Ottens, 6 mrt 2010
	 * @return the number of constraints that contain variables that are owned by different agents
	 */
	public int getNumberOfCoordinationConstraints() {
		return this.numberOfCoordinationConstraints;
	}
	
	/**
	 * @author Brammert Ottens, 7 mrt 2010
	 * @return the number of variables in the problem
	 */
	public int getNbrVariables() {
		return this.numberOfVariables;
	}

	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		StringBuilder builder = new StringBuilder ("Solution:");
		
		NumberFormat formatter = NumberFormat.getInstance();
		
		builder.append("\n\t- assignments: " + this.assignments);
		builder.append("\n\t- reported utility: \t" + this.reportedUtil);
		builder.append("\n\t- true utility: \t" + this.trueUtil);
		builder.append("\n");
		
		if (this.nbrMsgs > 0) {
			builder.append("\n\t- msgNbrs: \t" + this.msgNbrs);
			builder.append("\n\t- nbrMsgs: \t" + formatter.format(this.nbrMsgs));
			builder.append("\n");
			builder.append("\n\t- msgSizes: \t" + this.msgSizes);
			builder.append("\n\t- totalMsgSize:\t" + formatter.format(this.totalMsgSize));
		}
		
		if (this.treeWidth > 0) 
			builder.append("\n\t- maxUTILdim: \t" + this.treeWidth);
		
		if (this.ncccCount > 0) {
			builder.append("\n");
			builder.append("\n\t- ncccCount: \t" + formatter.format(this.ncccCount));
		}
		
		if (this.timeNeeded > 0) {
			builder.append("\n");
			builder.append("\n\t- timeNeeded: \t" + formatter.format(this.timeNeeded));
		}
		
		if (this.cumulativeTime > 0) {
			builder.append("\n");
			builder.append("\n\t- cumulative time: \t" + this.cumulativeTime);
		}
		
		if (this.moduleEndTimes != null) {
			builder.append("\n");
			builder.append("\n\t- moduleEndTimes: \t" + this.moduleEndTimes);
		}
		
		return builder.toString();
	}
	
	/** @return a representation of this solution that fits on one line (for the output CSV file) */
	public String toLineString() {
		
		StringBuffer buf = new StringBuffer ();
		
		buf.append("\t").append(this.ncccCount);
		buf.append("\t").append(this.timeNeeded);
		
		buf.append("\t").append(this.nbrMsgs);
		buf.append("\t").append(this.totalMsgSize);
		buf.append("\t").append(this.maxMsgSize);
		
		buf.append("\t").append(this.treeWidth);
		
		buf.append("\t").append(this.reportedUtil);
		buf.append("\t").append(this.trueUtil);
		
		return buf.toString();
	}

}
