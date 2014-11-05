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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.ProblemInterface;

/** A solver for a general problem
 * @author Thomas Leaute
 * @param <P> type used for the problem
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @param <S> type used for the solution
 */
public abstract class AbstractSolver < P extends ProblemInterface<V, U>, V extends Addable<V>, U extends Addable<U>, S extends Solution<V,U> > {

	/** Description of the agent to be used */
	protected Document agentDesc;
	
	/** The agent factory */
	protected AgentFactory<V> factory;

	/** The class of the parser to be used */
	protected Class< ? extends XCSPparser<V, U> > parserClass; /// @bug MAS solvers don't use the XCSPparser
	
	/** The list of modules that record statistics about problem solving */
	protected List<? extends StatsReporter> solGatherers;
	
	/** The problem */
	protected P problem;
	
	/** Whether to use TCP pipes or shared memory pipes */
	protected final boolean useTCP;
	
	/** \c true when the solver timed out, \c false otherwise */
	private boolean timedOut;
	
	/** \c true when the solver ran out of memory, \c false otherwise */
	private boolean outOfMem;
	
	/**
	 * Dummy constructor
	 */
	protected AbstractSolver() {
		this.useTCP = false;
	}
	
	/** Constructor from an agent configuration file
	 * @param agentDescFile 	the agent configuration file
	 */
	protected AbstractSolver (String agentDescFile) {
		this (agentDescFile, false);
	}
	
	/** Constructor from an agent configuration file
	 * @param agentDescFile 	the agent configuration file
	 * @param useTCP 			Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	protected AbstractSolver (String agentDescFile, boolean useTCP) {
		try {
			this.agentDesc = XCSPparser.parse(AgentFactory.class.getResourceAsStream(agentDescFile), false);
		} catch (JDOMException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.setParserClass();
		
		this.useTCP = useTCP;
		if (this.useTCP) // disable simulated time
			this.agentDesc.getRootElement().setAttribute("measureTime", "false");
	}
	
	/** Constructor
	 * @param agentDesc 	a JDOM Document for the agent description
	 */
	protected AbstractSolver (Document agentDesc) {
		this(agentDesc, false);
	}
	
	/** Constructor
	 * @param agentDesc 	a JDOM Document for the agent description
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	protected AbstractSolver (Document agentDesc, boolean useTCP) {
		this.agentDesc = agentDesc;
		this.setParserClass();
		
		this.useTCP = useTCP;
		if (this.useTCP) // disable simulated time
			this.agentDesc.getRootElement().setAttribute("measureTime", "false");
	}
	
	/** Sets the parser class as specified in the agent configuration file */
	@SuppressWarnings("unchecked")
	private void setParserClass () {
		Element parserElmt = agentDesc.getRootElement().getChild("parser");
		if (parserElmt != null) {
			try {
				this.parserClass = (Class<? extends XCSPparser<V, U>>) Class.forName(parserElmt.getAttributeValue("parserClass"));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			this.parserClass = (Class<? extends XCSPparser<V, U>>) XCSPparser.class;
			parserElmt = new Element ("parser");
			agentDesc.getRootElement().addContent(parserElmt);
			parserElmt.setAttribute("className", this.parserClass.getName());
		}
	}
	
	/** Sets the problem 
	 * @param problem 	the problem
	 */
	public void setProblem (P problem) {
		this.problem = problem;
	}
	
	/** Sets the agent factory
	 * @param factory 	the agent factory
	 */
	public void setFactory (AgentFactory<V> factory) {
		this.factory = factory;
	}
	
	/** Constructor
	 * @param agentDesc 	The agent description
	 * @param parserClass 	The class of the parser to be used
	 */
	protected AbstractSolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass) {
		this(agentDesc, parserClass, false);
	}
	
	/** Constructor
	 * @param agentDesc 	The agent description
	 * @param parserClass 	The class of the parser to be used
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	protected AbstractSolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass, boolean useTCP) {
		this.agentDesc = agentDesc;
		this.parserClass = parserClass;
		
		this.useTCP = useTCP;
		if (this.useTCP) // disable simulated time
			this.agentDesc.getRootElement().setAttribute("measureTime", "false");
	}
	
	/** Sets the class for variable values
	 * @param domClass 	the class for variable values
	 */
	public void setDomClass (Class<V> domClass) {
		this.agentDesc.getRootElement().getChild("parser").setAttribute("domClass", domClass.getName());
	}
	
	/** Sets the class for utility values
	 * @param utilClass 	the class for utility values
	 */
	public void setUtilClass(Class<U> utilClass) {
		this.agentDesc.getRootElement().getChild("parser").setAttribute("utilClass", utilClass.getName());
	}
	
	/** @return the agent description file */
	public Document getAgentDesc () {
		return this.agentDesc;
	}

	/** @return The list of modules that record statistics about problem solving */
	public abstract List<? extends StatsReporter> getSolGatherers ();
	
	/** @return the solution as seen by the solution gatherers */
	public abstract S buildSolution ();
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @return 				an optimal solution
	 */
	public S solve (Document problem) {
		return solve(problem, true);
	}
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @return 				an optimal solution
	 */
	public S solve (P problem) {
		return solve(problem, true);
	}
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @param timeout 		timeout in ms, if \c null, no timeout is used
	 * @return 				an optimal solution
	 */
	public S solve (Document problem, Long timeout) {
		return solve(problem, true, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @param timeout 		timeout in ms, if \c null, no timeout is used
	 * @return 				an optimal solution
	 */
	public S solve (P problem, Long timeout) {
		return solve(problem, true, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 			the problem
	 * @param cleanAfterwards 	if \c true, cleans all the agents and the queue when they're done
	 * @return 					an optimal solution
	 */
	public S solve (Document problem, boolean cleanAfterwards) {
		return solve(problem, cleanAfterwards, null);
	}
	
	/** Solves the input problem
	 * @param problem 			the problem
	 * @param cleanAfterwards 	if \c true, cleans all the agents and the queue when they're done
	 * @return 					an optimal solution
	 */
	public S solve (P problem, boolean cleanAfterwards) {
		return solve(problem, cleanAfterwards, null);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @return 						an optimal solution
	 */
	public S solve (Document problem, int nbrElectionRounds) {
		return solve (problem, nbrElectionRounds, false);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @return 						an optimal solution
	 */
	public S solve (P problem, int nbrElectionRounds) {
		return solve (problem, nbrElectionRounds, false);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @return 						an optimal solution
	 */
	public S solve (Document problem, int nbrElectionRounds, boolean measureMsgs) {
		return solve (problem, nbrElectionRounds, measureMsgs, null);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @return 						an optimal solution
	 */
	public S solve (P problem, int nbrElectionRounds, boolean measureMsgs) {
		return solve (problem, nbrElectionRounds, measureMsgs, null);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @return 						an optimal solution
	 */
	public S solve (Document problem, int nbrElectionRounds, boolean measureMsgs, Long timeout) {
		return solve(problem, nbrElectionRounds, measureMsgs, timeout, true);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @return 						an optimal solution
	 */
	public S solve (P problem, int nbrElectionRounds, boolean measureMsgs, Long timeout) {
		return solve(problem, nbrElectionRounds, measureMsgs, timeout, true);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @return 						an optimal solution
	 */
	public S solve (Document problem, int nbrElectionRounds, Long timeout) {
		return solve(problem, nbrElectionRounds, false, timeout, true);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @return 						an optimal solution
	 */
	public S solve (P problem, int nbrElectionRounds, Long timeout) {
		return solve(problem, nbrElectionRounds, false, timeout, true);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param cleanAfterwards 		if \c true, cleans all the agents and the queue when they're done
	 * @return 						an optimal solution
	 */
	public S solve (Document problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, boolean cleanAfterwards) {
		
		agentDesc.getRootElement().setAttribute("measureMsgs", Boolean.toString(measureMsgs));
		return solve(problem, cleanAfterwards, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param cleanAfterwards 		if \c true, cleans all the agents and the queue when they're done
	 * @return 						an optimal solution
	 */
	public S solve (P problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, boolean cleanAfterwards) {
		
		agentDesc.getRootElement().setAttribute("measureMsgs", Boolean.toString(measureMsgs));
		return solve(problem, cleanAfterwards, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 			the problem
	 * @param cleanAfterwards 	if \c true, cleans all the agents and the queue when they're done
	 * @param timeout 			timeout in ms, if \c null, no timeout is used
	 * @return 					an optimal solution
	 */
	@SuppressWarnings("unchecked")
	public S solve (Document problem, boolean cleanAfterwards, Long timeout) {
		
		// Instantiate the parser
		Element parserElmt = this.agentDesc.getRootElement().getChild("parser");
		parserElmt.setAttribute("displayGraph", "false");
		try {
			return solve ((P) this.parserClass.getConstructor(Document.class, Element.class).newInstance(problem, parserElmt), cleanAfterwards, timeout);
		} catch (InstantiationException e) {
			System.err.println("The parser class " + this.parserClass + " is abstract");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			System.err.println("The constructor for " + this.parserClass + " is inaccessible");
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			System.err.println("The constructor for " + this.parserClass + " threw an exception");
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			System.err.println("The parser class " + this.parserClass + " does not have a constructor that takes in a Document and an Element");
			e.printStackTrace();
		}
		
		return null;
	}
	
	/** Solves the input problem
	 * @param problem 			the problem
	 * @param cleanAfterwards 	if \c true, cleans all the agents and the queue when they're done
	 * @param timeout 			timeout in ms, if \c null, no timeout is used
	 * @return 					an optimal solution
	 * @throws OutOfMemoryError thrown when an out of memory exception is encountered in the agent factory
	 */
	public S solve (P problem, boolean cleanAfterwards, Long timeout) throws OutOfMemoryError {
		
		this.problem = problem;
		timedOut = false;
		outOfMem = false;
		
		// Check whether it is necessary to instantiate the agent factory
		if (this.factory == null) {

			// Instantiate the modules that listen for the solution
			this.solGatherers = this.getSolGatherers();

			// Solve the problem
			this.factory = new AgentFactory<V> (problem, agentDesc, solGatherers, timeout, this.useTCP);
			
		} else { // we already have an agent factory; just restart it
			
			for (StatsReporter module : this.solGatherers) 
				module.reset();
			this.factory.restart(problem);
		}
		
		if(factory.timedOut() || factory.outOfMemory()) {
			outOfMem = factory.outOfMemory();
			timedOut = factory.timedOut();
			if (cleanAfterwards)
				this.clear();
			return null;
		}
		
		S solution = this.buildSolution();
		
		if (cleanAfterwards) 
			this.clear();

		return solution;
	}
	
	/**
	 * Puts the statistics in a format that can easily be processed after the experiments
	 * @author Brammert Ottens, Dec 29, 2011
	 * @param sol the solution to be printed
	 * @return string representation of the statistics
	 */
	public String plotStats(Solution<V, U> sol) {
		String str = sol.getTimeNeeded() + "\t" + sol.getUtility() + "\t" + sol.getNcccCount() + "\t" + sol.getNbrMsgs() + "\t" + sol.getTotalMsgSize() + "\t" + sol.getTreeWidth(); 
		return str;
	}
	
	/**
	 * Used when the solver was not able to solve the problem
	 * 
	 * @author Brammert Ottens, Dec 29, 2011
	 * @param maximize	\c true when maximizing, \c false otherwise
	 * @return dummy stats
	 */
	public String plotDummyStats(boolean maximize) {
		return Long.MAX_VALUE + "\t" + (maximize ? Integer.MIN_VALUE : Integer.MAX_VALUE) + "\t" + Integer.MAX_VALUE + "\t" + Integer.MAX_VALUE + "\t" + Integer.MAX_VALUE + "\t" + -1; 
	}

	/** Clears the parser */
	protected void clear() {
		for (StatsReporter module : this.solGatherers) 
			module.reset();
		this.solGatherers = null;
		factory.end();
		factory = null;
	}
	
	/**
	 * @author Brammert Ottens, 28 nov. 2011
	 * @return \c true when the solver timed out, \c false otherwise 
	 */
	public boolean timedOut() {
		return this.timedOut;
	}
	
	/**
	 * @author Brammert Ottens, 28 nov. 2011
	 * @return \c true when the solver ran out of memory, \c false otherwise
	 */
	public boolean outOfMem() {
		return this.outOfMem;
	}
}
