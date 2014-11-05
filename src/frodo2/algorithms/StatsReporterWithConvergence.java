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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import frodo2.communication.MessageWith2Payloads;
import frodo2.solutionSpaces.Addable;

/** A StatsReporter that monitors the convergence of the algorithm
 * @author Brammert Ottens
 * @param <Val> type used for variable values
 * @warning All modules implementing this interface must have a constructor that takes in two parameters: 
 * an instance of DCOPProblemInterface describing the agent's subproblem, and a JDOM Element specifying the module's parameters. 
 * @warning All modules implementing this class should have a constructor that takes in two arguments: 
 * a JDOM Element describing the StatsReporter and its parameters, and a DCOPProblemInterface for the overall problem. 
 */
public interface StatsReporterWithConvergence < Val extends Addable<Val> > 
				extends StatsReporter {
	
	/**
	 * A convenience class to store an assignment. Used
	 * to determine convergence speed of an algorithm
	 * 
	 * @author brammert
	 *
	 * @param <Val> type used for variable values
	 */
	public static class CurrentAssignment <Val> {
		
		/** The time stamp (in nanoseconds) */
		private final long timeStamp;
		
		/** When dealing with synchronous algorithms, the cycle count can be stored in this field*/
		private final long cycleCount;
		
		/** The current assignment */
		private final Val assignment;
		
		/** 
		 * A constructor
		 * @param timeStamp		The absolute time at which this assignment was set (in nanoseconds)
		 * @param assignment	The assignment
		 */
		public CurrentAssignment(long timeStamp, Val assignment) {
			this.timeStamp = timeStamp;
			this.assignment = assignment;
			this.cycleCount = -1;
		}
		
		/** 
		 * A constructor
		 * @param timeStamp		The absolute time at which this assignment was set (in nanoseconds)
		 * @param cycleCount	The cycle count time stamp
		 * @param assignment	The assignment
		 */
		public CurrentAssignment(long timeStamp, long cycleCount, Val assignment) {
			this.timeStamp = timeStamp;
			this.assignment = assignment;
			this.cycleCount = cycleCount;
		}
		
		/**
		 * @param time \c true when the time stamp must be returned, and \c false when the cycle count must be returned
		 * @return the time stamp
		 */
		public long getTimeStamp(boolean time) {
			if(time)
				return this.timeStamp/1000000;
			else
				return cycleCount;
		}
		
		
		/**
		 * @return the assignment
		 */
		public Val getAssignment() {
			return this.assignment;
		}
		
		/**
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return "Ass = " + assignment + " time = {" + timeStamp + "}";
		}
		
	}
	
	/**
	 * A message containing information about the convergence of the algorithm
	 * 
	 * @author brammert
	 *
	 * @param <Val> type used for variable values
	 */
	public static class ConvStatMessage <Val> extends MessageWith2Payloads<String, ArrayList<CurrentAssignment<Val>>> {
		
		/** Empty constructor used for externalization */
		public ConvStatMessage () { }

		/**
		 * A constructor
		 * @param type					The message type
		 * @param variableID			The ID of the sending variable
		 * @param assignmentHistory		The assignment history of the sending variable
		 */
		public ConvStatMessage(String type, String variableID, ArrayList<CurrentAssignment<Val>> assignmentHistory) {
			super(type, variableID, assignmentHistory);
		}
		
		/**
		 * @return the ID of the sending variable
		 */
		public String getVar() {
			return this.getPayload1();
		}
		
		/**
		 * @return the assignment history of the sending variable
		 */
		public ArrayList<CurrentAssignment<Val>> getAssignmentHistory() {
			return this.getPayload2();
		}
	}
	
	/**
	 * Return the assignment history, chronologically ordered, for all the variables
	 * 
	 * @return the assignment history
	 */
	public HashMap<String, ArrayList<CurrentAssignment<Val>>> getAssignmentHistories();
	
	/**
	 * Method that returns the current solution of the agents
	 * @author Brammert Ottens, Thomas Leaute
	 * @return for every variable in the problem an assignment
	 */
	public Map<String, Val> getCurrentSolution();

}
