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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.varOrdering.election.VariableElection;
import frodo2.communication.MessageListener;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** An abstract convenient class for solving DCOP instances
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @param <S> type used for the solution
 */
public abstract class AbstractDCOPsolver < V extends Addable<V>, U extends Addable<U>, S extends Solution<V, U> > 
	extends AbstractSolver<DCOPProblemInterface<V, U>, V, U, S> {

	/** Solves a problem and writes statistics to a file
	 * @param args 	[algoName, solverClassName, agentConfigFile, problemFile, timeout in seconds, outputFile]
	 * @throws Exception if an error occurs
	 */
	public static void main (String[] args) throws Exception {
		
		// Parse the input arguments
		String algoName = args[0];
		String solverClassName = args[1];
		Document agentConfig = XCSPparser.parse(args[2], false);
		Document problemFile = XCSPparser.parse(args[3], false);
		Long timeout = 1000 * Long.parseLong(args[4]); // *1000 to get it in ms
		String outputFilePath = args[5];
		
		// Instantiate the solver
		@SuppressWarnings("unchecked")
		Class<? extends AbstractDCOPsolver<?, ?, ?>> solverClass = 
				(Class<? extends AbstractDCOPsolver<?, ?, ?>>) Class.forName(solverClassName);
		@SuppressWarnings("unchecked")
		AbstractDCOPsolver< ?, ?, Solution<?, ?> > solver = 
				(AbstractDCOPsolver<?, ?, Solution<?, ?>>) solverClass.getConstructor(Document.class).newInstance(agentConfig);
		
		// Write a first "timeout" line to the output file that will be overwritten after the algorithm terminates (if it does)
		File outputFile = new File (outputFilePath);
		boolean newFile = ! outputFile.exists();
		BufferedWriter writer = new BufferedWriter (new FileWriter (outputFile, true));
		if (newFile) 
			writer.append(solver.getFileHeader(problemFile)).append("\n");
		writer.append(solver.getTimeoutLine(algoName, problemFile)).append("\n").flush();
		
		// Solve and record the stats
		Solution<?, ?> sol = solver.solve(problemFile, false, timeout);
		
		if (sol != null) {
			writer.append(algoName);
			writer.append("\t0"); // 0 = no timeout; 1 = timeout

			// First write statistics about the problem instance
			writer.append(solver.getProbStats(problemFile));
			
			// Write the statistics about the solution found
			writer.append(sol.toLineString()).append("\n");
		}
		
		writer.close();
	}
	
	/** Returns the header for the output CSV file
	 * @param problemFile 	the problem file
	 * @return the titles of the columns in the output CSV file 
	 */
	protected String getFileHeader(Document problemFile) {
		
		StringBuffer buf = new StringBuffer ("algorithm");

		buf.append("\ttimed out"); // 0 if the algorithm terminated, 1 if it timed out

		buf.append("\tproblem instance"); // the name of the problem instance, assumed unique

		// Write statistics about the problem instance, added by the problem generator to the XCSP file
		Element presElmt = problemFile.getRootElement().getChild("presentation");
		TreeSet<String> stats = new TreeSet<String> ();
		for (Element child : presElmt.getChildren()) 
			stats.add(child.getAttributeValue("name"));
		for (String name : stats) 
			buf.append("\t").append(name);
		
		// Continue with the statistics about the solution
		buf.append("\tNCCCs");
		buf.append("\tsimulated time (in ms)");
		
		buf.append("\tnumber of messages");
		buf.append("\ttotal message size (in bytes)");
		buf.append("\tmaximum message size (in bytes)");
		
		buf.append("\tinduced treewidth");
		
		if (Boolean.parseBoolean(presElmt.getAttributeValue("maximize"))) 
			buf.append("\treported utility").append("\ttrue utility");
		else 
			buf.append("\treported cost").append("\ttrue cost");
		
		return buf.toString();
	}
	
	/** Parses the statistics about the problem instance
	 * @param problemFile 	the problem instance
	 * @return the statistics
	 */
	protected String getProbStats (Document problemFile) {
		
		StringBuffer buf = new StringBuffer ();
		
		// Write the name of this problem instance (assuming it is unique)
		Element presElmt = problemFile.getRootElement().getChild("presentation");
		buf.append("\t").append(presElmt.getAttributeValue("name"));
		
		// Write statistics about the problem instance
		TreeMap<String, String> stats = new TreeMap<String, String> ();
		for (Element child : presElmt.getChildren()) 
			stats.put(child.getAttributeValue("name"), child.getText());
		for (String value : stats.values()) 
			buf.append("\t").append(value);
		
		return buf.toString();
	}

	/** Returns a timeout line for the output CSV file
	 * @param algoName 		the name of the algorithm
	 * @param problemFile 	the problem instance
	 * @return a line in the output CSV file that corresponds to a timeout 
	 */
	protected String getTimeoutLine(String algoName, Document problemFile) {
		
		StringBuffer buf = new StringBuffer (algoName);
		
		buf.append("\t1"); // 0 = no timeout; 1 = timeout
		
		buf.append(this.getProbStats(problemFile));
		
		// Continue with statistics about the solution
		buf.append("\t").append(Long.MAX_VALUE); // NCCCs
		buf.append("\t").append(Long.MAX_VALUE); // runtime
		
		buf.append("\t").append(Integer.MAX_VALUE); // nbrMessages
		buf.append("\t").append(Long.MAX_VALUE); // total msg size
		buf.append("\t").append(Long.MAX_VALUE); // max msg size
		
		buf.append("\t").append(Integer.MAX_VALUE); // treewidth
		
		buf.append("\tNaN"); // reported util
		buf.append("\tNaN"); // true util

		return buf.toString();
	}

	/**
	 * Dummy constructor
	 */
	protected AbstractDCOPsolver() {
		super();
		this.overrideMsgTypes();
	}
	
	/** Constructor from an agent configuration file
	 * @param agentDescFile 	the agent configuration file
	 */
	protected AbstractDCOPsolver (String agentDescFile) {
		super (agentDescFile);
		this.overrideMsgTypes();
	}
	
	/** Constructor from an agent configuration file
	 * @param agentDescFile 	the agent configuration file
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	protected AbstractDCOPsolver (String agentDescFile, boolean useTCP) {
		super (agentDescFile, useTCP);
		this.overrideMsgTypes();
	}
	
	/** Constructor
	 * @param agentDesc 	a JDOM Document for the agent description
	 */
	protected AbstractDCOPsolver (Document agentDesc) {
		super(agentDesc);
		this.overrideMsgTypes();
	}
	
	/** Constructor
	 * @param agentDesc 	a JDOM Document for the agent description
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	protected AbstractDCOPsolver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
		this.overrideMsgTypes();
	}
	
	/** Constructor
	 * @param agentDesc 	The agent description
	 * @param parserClass 	The class of the parser to be used
	 */
	protected AbstractDCOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass) {
		super(agentDesc, parserClass);
		this.overrideMsgTypes();
	}
	
	/** Constructor
	 * @param agentDesc 	The agent description
	 * @param parserClass 	The class of the parser to be used
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	protected AbstractDCOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass, boolean useTCP) {
		super(agentDesc, parserClass, useTCP);
		this.overrideMsgTypes();
	}
	
	/** @see AbstractSolver#solve(org.jdom2.Document, int, boolean, java.lang.Long, boolean) */
	@Override
	public S solve (Document problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, boolean cleanAfterwards) {
		
		agentDesc.getRootElement().setAttribute("measureMsgs", Boolean.toString(measureMsgs));
		this.setNbrElectionRounds(nbrElectionRounds);
		return this.solve(problem, cleanAfterwards, timeout);
	}
	
	/** @see AbstractSolver#solve(frodo2.solutionSpaces.ProblemInterface, int, boolean, java.lang.Long, boolean) */
	@Override
	public S solve (DCOPProblemInterface<V, U> problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, boolean cleanAfterwards) {
		
		agentDesc.getRootElement().setAttribute("measureMsgs", Boolean.toString(measureMsgs));
		this.setNbrElectionRounds(nbrElectionRounds);
		return this.solve(problem, cleanAfterwards, timeout);
	}
	
	/** Sets the number of rounds of VariableElection
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 */
	protected void setNbrElectionRounds (int nbrElectionRounds) {
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(VariableElection.class.getName())) 
				module.setAttribute("nbrSteps", Integer.toString(nbrElectionRounds));
	}
	
	/** Overrides message types if necessary */
	@SuppressWarnings("unchecked")
	private void overrideMsgTypes() {

		Element modsElmt = agentDesc.getRootElement().getChild("modules");
		
		try {
			if (modsElmt != null) {
				for (Element moduleElmt : (List<Element>) modsElmt.getChildren()) {

					String className = moduleElmt.getAttributeValue("className");
					Class< MessageListener<String> > moduleClass = (Class< MessageListener<String> >) Class.forName(className);
					Element allMsgsElmt = moduleElmt.getChild("messages");
					if (allMsgsElmt != null) {
						for (Element msgElmt : (List<Element>) allMsgsElmt.getChildren()) {

							// Look up the new value for the message type
							String newType = msgElmt.getAttributeValue("value");
							String ownerClassName = msgElmt.getAttributeValue("ownerClass");
							if (ownerClassName != null) { // the attribute "value" actually refers to a field in a class
								Class<?> ownerClass = Class.forName(ownerClassName);
								try {
									Field field = ownerClass.getDeclaredField(newType);
									newType = (String) field.get(newType);
								} catch (NoSuchFieldException e) {
									System.err.println("Unable to read the value of the field " + ownerClass.getName() + "." + newType);
									e.printStackTrace();
								}
							}

							// Set the message type to its new value
							try {
								SingleQueueAgent.setMsgType(moduleClass, msgElmt.getAttributeValue("name"), newType);
							} catch (NoSuchFieldException e) {
								System.err.println("Unable to find the field " + moduleClass.getName() + "." + msgElmt.getAttributeValue("name"));
								e.printStackTrace();
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
