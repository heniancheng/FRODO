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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import frodo2.algorithms.StatsReporterWithConvergence.CurrentAssignment;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** An optimal solution to the problem
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @author Brammert Ottens
 */
public class Convergence <V extends Addable<V>, U extends Addable<U>> {

	/**
	 * Constructor
	 */
	public Convergence() {
		super();
	}
	
	/**
	 * Main function, used to merge different runs
	 * 
	 * @author Brammert Ottens, 3 dec 2010
	 * @param args input arguments
	 */
	public static void main(String[] args) {
		Convergence<AddableInteger, AddableReal> s = new Convergence<AddableInteger, AddableReal>();
		
		s.processRuns(args[0], args[1], args[2], Integer.parseInt(args[3]), Boolean.parseBoolean(args[4]));
		
	}
	
	/**
	 * This method looks at the assignment histories of the individual variables, and combines these to create a picture
	 * of the evolution of the global utility
	 * @param assignmentHistories	For each variable the assignment history 
	 * @param hypercubes 			list of local problems
	 * @param experiment_number		The experiment ID
	 * @param resultPath 			The path of the result file
	 * @param infeasibleUtil 		the value of the infeasible utility
	 * @param maximize 				\c true when the problem is a maximization problem, and \c false otherwise
	 * @param time					\c true when time stamps must be used, and \c false when cycles must be used
	 * @return the time at which the algorithm has converged to the optimal solution
	 * 
	 * @param <V>	type used for domain values
	 * @param <U> 	type used for utilities
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static <V extends Addable<V>, U extends Addable<U>> long processAssignmentHistories(HashMap<String, ArrayList<CurrentAssignment<V>>> assignmentHistories, List< ? extends UtilitySolutionSpace<V, U> > hypercubes, String experiment_number, String resultPath, U infeasibleUtil, boolean maximize, boolean time) {
		long finalConvergenceTimeStamp = -1;
		try {
			BufferedWriter timeWriterAll = new BufferedWriter(new FileWriter(resultPath + "timeConvergence-all-" + experiment_number + ".conv"));
			ArrayList<TimeStamp<U>> convergenceAll = new ArrayList<TimeStamp<U>>();
			int numberOfVariables = assignmentHistories.size();
			
			int[] currentIndexTime = new int[numberOfVariables];
			int[] maxIndex = new int[numberOfVariables];
			String[] variables = new String[numberOfVariables];
			HashMap<String, V> timeValues = new HashMap<String, V>(numberOfVariables);
			ArrayList<CurrentAssignment<V>>[] assHistory = (ArrayList<CurrentAssignment<V>>[])Array.newInstance((new ArrayList<CurrentAssignment<V>>(0)).getClass(), numberOfVariables);
			String[] indexToString = new String[numberOfVariables];
			HashMap<String, Integer> variablePointer = new HashMap<String, Integer>(numberOfVariables);

			// initilize datastructures
			// find the time that the first variabe obtained a value		- minTimeStamp
			// find the earliest time at which all variables have a value	- firstTimeStamp
			long firstTimeStamp = Long.MAX_VALUE;
			long minTimeStamp = -1;
			int minimalSize = -1;
			int index = 0; 
			for(Entry<String, ArrayList<CurrentAssignment<V>>> e : assignmentHistories.entrySet()) {
				int size = e.getValue().size();
				indexToString[index] = e.getKey();
				variablePointer.put(e.getKey(), index);
				variables[index] = e.getKey();
				maxIndex[index] = size;
				assHistory[index] = e.getValue();
				long nextTimeStamp = assHistory[index].get(0).getTimeStamp(time);
				if(minTimeStamp == -1 || nextTimeStamp >= minTimeStamp)  {
					minTimeStamp = nextTimeStamp;
				}
				if(firstTimeStamp > nextTimeStamp) {
					firstTimeStamp = nextTimeStamp;
				}
				if(minimalSize == -1 || minimalSize >size)
					minimalSize = size;
				index++;
			}
			
			// set the correct indices and set the current utilities
			// we start going through the histories from minTimeStamp
			for(int i = 0; i < numberOfVariables; i++) {
				while(currentIndexTime[i] + 1 < assHistory[i].size() && assHistory[i].get(currentIndexTime[i] + 1).getTimeStamp(time) < minTimeStamp)
					currentIndexTime[i] += 1;
				timeValues.put(indexToString[i], assHistory[i].get(currentIndexTime[i]).getAssignment());
			}
			
			U currentUtility = getUtility(timeValues, hypercubes);
			U previousUtil = null;
			U highestUtilSoFar = infeasibleUtil; 
			finalConvergenceTimeStamp = -1;
			
			
			// write the utilities to a file
			TimeStamp<U> stamp = new TimeStamp<U> (currentUtility, minTimeStamp);
			convergenceAll.add(stamp);
						
			boolean cont = true;

			while(cont) {
				cont = false;

				// find the next time index
				minTimeStamp = -1;
				int timeIndex = -1;
				for(int i = 0; i < numberOfVariables; i++) {
					if(assHistory[i].size() > (currentIndexTime[i] + 1)) {
						long nextTimeStamp = (assHistory[i].get(currentIndexTime[i] + 1).getTimeStamp(time));
						if(minTimeStamp == -1 || nextTimeStamp <= minTimeStamp) {
							minTimeStamp = nextTimeStamp;
							timeIndex = i;
						}
					}
				}
				
				// update the current indices
				if(timeIndex != -1) {
					currentIndexTime[timeIndex] += 1;
					cont = true;
				}

				if(cont) {
					// calculate the current utilities
					for(int i = 0; i < numberOfVariables; i++) {
						timeValues.put(indexToString[i], assHistory[i].get(currentIndexTime[i]).getAssignment());
					}
					
					currentUtility = getUtility(timeValues, hypercubes);
					stamp = new TimeStamp<U> (currentUtility, minTimeStamp);
					
					// compare the current utility with the highest utility so far
					int dif = currentUtility.compareTo(highestUtilSoFar); 
					if((maximize && dif > 0) || (!maximize && dif < 0)) { // if it is better, store it
						highestUtilSoFar = currentUtility;
						finalConvergenceTimeStamp = minTimeStamp;
					} else if ((maximize && dif < 0) || (!maximize && dif > 0)) { // else we have not yet converged
						finalConvergenceTimeStamp = -1;
					}

					// only write the current utility when it changes
					if((previousUtil == null || !previousUtil.equals(currentUtility))) {
						int lastIndex = convergenceAll.size() - 1;
						if(stamp.equals(convergenceAll.get(lastIndex)))
							convergenceAll.set(lastIndex, stamp);
						else
							convergenceAll.add(stamp);
						
						if(minTimeStamp < 0) {
							System.err.println("Encountered a negative timeStamp!!");
							System.exit(-1);
						}
						if(finalConvergenceTimeStamp == -1)
							finalConvergenceTimeStamp = minTimeStamp;

						previousUtil = currentUtility;
					}
				}
			}
			
			for(TimeStamp<U> s : convergenceAll) {
				timeWriterAll.write(s.time + "\t" + s.utility + "\n");
				timeWriterAll.flush();
			}
			timeWriterAll.close();
			
		} catch (IOException e) {
			System.out.println(e); /// @bug timeWriterAll will not be closed
		}
		
		return finalConvergenceTimeStamp;
	}
	
	/**
	 * Convenience class containing a timestamp and utility value
	 * @author Brammert Ottens, 3 dec 2010
	 * 
	 * @param <U> type used for utilities
	 */
	public static class TimeStamp <U extends Addable<U>> {
		
		/** The utility at time \c time*/
		U utility;
		
		/** The time stamp */
		long time;
		
		/**
		 * Constructor
		 * 
		 * @param utility	The utility at time \c time
		 * @param time		The time stamp
		 */
		public TimeStamp(U utility, long time) {
			this.utility = utility;
			this.time = time;
		}
		
		/**
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@SuppressWarnings("unchecked")
		public boolean equals(Object o) {
			TimeStamp<U> stamp = (TimeStamp<U>)o;
			return time == stamp.time;
		}
		
		/** @see java.lang.Object#toString() */
		public String toString() {
			return "(" + time + ", " + utility + ")";
		}
	}
	

	/**
	 * Combines utility evolution of different runs into one median run
	 * 
	 * @author Brammert Ottens, 3 dec 2010
	 * @param resultPath	the path on which the runs can be found
	 * @param resultFile	the file in which the results should be written
	 * @param problemName	the name of the problem
	 * @param sampleSize	the sample size to be used
	 * @param maximize 		\c true when we should maximize, and \c false otherwise
	 */
	public void processRuns(String resultPath, String resultFile, String problemName, int sampleSize, boolean maximize) {
		
		/** Auxilirary variable, used to convert strings to addable integers*/
		AddableReal zero = new AddableReal(0);
		AddableReal one = new AddableReal(1);
		
		/** The result file*/
		BufferedWriter bws = null;
		
		try {
			bws = new BufferedWriter(new FileWriter(resultPath + resultFile));
		} catch(IOException e) {
			System.err.println(e);
		}

		// first read in the runs
		ArrayList<ArrayList<Double>> timeStamps = new ArrayList<ArrayList<Double>>();
		ArrayList<ArrayList<AddableReal>> globalUtility = new ArrayList<ArrayList<AddableReal>>();
		ArrayList<AddableReal> optimalUtility = new ArrayList<AddableReal>();
		boolean cont = true;
		int i = 0;

		while(cont) {
			try {
				String fileName;
				fileName = resultPath + "timeConvergence-" + problemName + "-" + i + ".conv";
				System.out.println(fileName);
				BufferedReader br = new BufferedReader(new FileReader(fileName));
				ArrayList<Double> timeStampEvolution = new ArrayList<Double>();
				ArrayList<AddableReal> utilityEvolution = new ArrayList<AddableReal>();
				String line = br.readLine();

				while(line != null) {
					String[] parts = line.split("\t");
					timeStampEvolution.add(Double.parseDouble(parts[0]));
					utilityEvolution.add((AddableReal)zero.fromString(parts[1]));
					line = br.readLine();
				}
				br.close();

				timeStamps.add(timeStampEvolution);
				globalUtility.add(utilityEvolution);
				optimalUtility.add(utilityEvolution.get(utilityEvolution.size() - 1));
				assert timeStampEvolution.size() == utilityEvolution.size() : "there should be as much time stamps as there are utility values!";

			} catch (IOException e) {
				cont = false; /// @bug br will not be closed
			}
			i++;
		}
		
		// sample the datasets, and for each sample calculate the median
		cont = true;
		int numberOfRuns = timeStamps.size();
		assert ((double)numberOfRuns)/2.0 != 0 : "The number of runs should be uneven!";
		int medianPosition = (int)Math.ceil(((double)numberOfRuns)/2.0);
		int leftConfidenceBoundryIndex = (int)Math.floor(0.5*numberOfRuns - 0.98*Math.sqrt((double)numberOfRuns));
		int rightConfidenceBoundryIndex = (int)Math.floor(0.5*numberOfRuns + 1 + 0.98*Math.sqrt((double)numberOfRuns));
		int currentTime = Integer.MAX_VALUE;
		int[] currentIndex = new int[timeStamps.size()];
		Arrays.fill(currentIndex, -1);
		AddableReal[] currentUtilities = new AddableReal[timeStamps.size()];
		AddableReal infinity = maximize ? ((AddableReal)globalUtility.get(0).get(0)).getMinInfinity() : ((AddableReal)globalUtility.get(0).get(0)).getPlusInfinity();

		// find the first timestamp 

		for(i = 0; i < numberOfRuns; i++) {
			double time = timeStamps.get(i).get(0);
			if(time < currentTime)
				currentTime = (int)time;
		}

		for(i = 0; i < numberOfRuns; i++) {
			double time = timeStamps.get(i).get(0);
			if(time <= currentTime)
				currentIndex[i] = 0;
		}


		for(i = 0; i < numberOfRuns; i++) {
			if(currentIndex[i] == -1)
				currentUtilities[i] = infinity;
			else
				currentUtilities[i] = (AddableReal)globalUtility.get(i).get(currentIndex[i]).divide(optimalUtility.get(i));
		}


		while(cont) {
			// sort the utilities
			Arrays.sort(currentUtilities);

			// find the median
			AddableReal median = currentUtilities[medianPosition];
			if(median == zero.getMinInfinity())
				median = new AddableReal(Integer.MIN_VALUE);
			
			// find the convidence interval
			AddableReal leftConfidenceBoundry = new AddableReal(Integer.MIN_VALUE);
			if(leftConfidenceBoundryIndex >= 0) {
				leftConfidenceBoundry = currentUtilities[leftConfidenceBoundryIndex];
				if(leftConfidenceBoundry == zero.getMinInfinity())
					leftConfidenceBoundry = new AddableReal(Integer.MIN_VALUE);
			}

			AddableReal rightConfidenceBoundry = new AddableReal(Integer.MAX_VALUE);
			if(rightConfidenceBoundryIndex < currentUtilities.length) {
				rightConfidenceBoundry = currentUtilities[rightConfidenceBoundryIndex];
				if(rightConfidenceBoundry == zero.getMinInfinity())
					rightConfidenceBoundry = new AddableReal(Integer.MIN_VALUE);
			}

			try {
				bws.write(currentTime + "\t" + median + "\t" + leftConfidenceBoundry + "\t" + rightConfidenceBoundry + "\n");
				bws.flush();
			} catch(IOException e) {
				System.err.println(e);
			}
			currentTime += sampleSize;

			cont = false;
			// update currentIndex and fill the utility array
			for(i = 0; i < numberOfRuns; i++) {
				while(currentIndex[i] < timeStamps.get(i).size() - 1 && timeStamps.get(i).get(currentIndex[i] + 1) < currentTime) {
					currentIndex[i] += 1;
				}
				if(currentIndex[i] < timeStamps.get(i).size() - 1)
					cont = true;
				if(currentIndex[i] == -1)
					currentUtilities[i] = infinity;
				else {
					if(globalUtility.get(i).get(currentIndex[i]).equals(optimalUtility.get(i)))
						currentUtilities[i] = one;
					else
						currentUtilities[i] = (AddableReal)globalUtility.get(i).get(currentIndex[i]).divide(optimalUtility.get(i));
				}
			}
		}
	}
	
	/**
	 * Given an assignment and a problem, this method calculates the globalutility of the assignment
	 * @param assignments	the current variable assignments
	 * @param hypercubes	the problem encoded in a list of hypercubes
	 * @return the global utility
	 * 
	 * @param <V>	type used for domain values 
	 * @param <U> 	type used for utilities
	 * 
	 */
	@SuppressWarnings("unchecked")
	public static <V extends Addable<V>, U extends Addable<U>> U getUtility(Map<String, V> assignments, List< ? extends UtilitySolutionSpace<V, U> > hypercubes) {
		
		U sum = null;
		Class<V> domainClass = (Class<V>) hypercubes.get(0).getDomain(0)[0].getClass();
		// Go through the list of hypercubes
		for (UtilitySolutionSpace<V, U> hypercube : hypercubes) {
			// Slice the hypercube over the input assignments
			ArrayList<String> vars = new ArrayList<String> (hypercube.getNumberOfVariables());
			for (String var : hypercube.getVariables()) 
				if (assignments.containsKey(var)) 
					vars.add(var);
			int nbrVars = vars.size();
			V[] values = (V[])Array.newInstance(domainClass, nbrVars);
			for (int i = 0; i < nbrVars; i++) 
				values[i] = assignments.get(vars.get(i));
			U temp = hypercube.getUtility(vars.toArray(new String[0]), values);
			if(sum == null)
				sum = temp;
			else
				sum = sum.add(temp);
		}
		
		
		assert sum != null;
		return sum;
	}
}