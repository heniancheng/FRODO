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

/** Classes implementing the E[DPOP] family of algorithms for Stochastic DCOP */
package frodo2.algorithms.dpop.stochastic;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jdom2.Element;

import frodo2.algorithms.dpop.UTILmsg;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEpropagation.AssignmentsMessage;
import frodo2.algorithms.dpop.stochastic.SamplingPhase.RandVarsProjMsg;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** E[DPOP]'s UTIL propagation phase
 * 
 * The difference with the normal UTILpropagation phase is that it "projects out" the random variables by computing the expectation over these variables.
 * @author Thomas Leaute
 * @param <Val> the type used for variable values
 * @param <U> 	the type used for utility values
 * @warning This implementation currently assumes all variables owned by any given agent belong to the same component of the overall constraint graph, 
 * and might not work properly if this assumption is violated. 
 */
public class ExpectedUTIL < Val extends Addable<Val>, U extends Addable<U> > 
extends UTILpropagation<Val, U> {
	
	/** The type of the messages containing information about the where random variables should be projected out */
	public static String RAND_VARS_PROJ_MSG_TYPE = SamplingPhase.RAND_VARS_PROJ_MSG_TYPE;
	
	/** For each variable, the random variables that should be projected out of its UTIL message */
	protected HashMap< String, HashSet<String> > randVarsProj = new HashMap< String, HashSet<String> > ();
	
	/** The method to use to choose the optimal value for a variable */
	public static enum Method {
		/** The expectation method */
		EXPECTATION,
		/** The expectation method, assuming that all costs/utilities have the same sign */
		EXPECTATION_MONOTONE,
		/** The consensus method */
		CONSENSUS, 
		/** The advanced consensus method for all solutions */
		CONSENSUS_ALL_SOLS,
		/** The robust, worst-case approach */
		ROBUST
	}
	
	/** The method to use */
	protected Method method = Method.EXPECTATION;
	
	/** For each variable, its optimal value */
	private HashMap<String, Val> solution = new HashMap<String, Val> ();

	/** The number of variables owned by this agents that still have not sent VALUE messages to all their children */
	private int remainingVars;

	/** The worst-case utility */
	private U worstUtil;

	/** The expected utility */
	private U expectedUtil;
	
	/** The utility 0 */
	private U zero;
	
	/** The utility 1 */
	private U one;
	
	/** Whether to measure the probability of optimality */
	private boolean measureProbOfOpt = false;
	
	/** The total probability of the scenarios for which the chosen solution is optimal */
	private U probOfOptimality;

	/** Constructor 
	 * @param problem 		the agent's subproblem
	 * @param parameters 	the parameters for the module
	 */
	public ExpectedUTIL (DCOPProblemInterface<Val, U> problem, Element parameters) {
		super (problem, parameters);
		this.zero = problem.getZeroUtility();
		this.one = this.zero.fromString("1");
		withAnonymVars = true;
		this.parseMethod(parameters);
	}
	
	/** Parses the method to use
	 * @param parameters 	the parameters for the module
	 */
	protected void parseMethod (Element parameters) {
		
		// Parse the method to use, if it is specified
		String methodName = parameters.getAttributeValue("method");
		if (methodName != null) {
			if (methodName.equalsIgnoreCase("expectation")) 
				this.method = Method.EXPECTATION;
			else if (methodName.equalsIgnoreCase("expectationMonotone")) 
				this.method = Method.EXPECTATION_MONOTONE;
			else if (methodName.equalsIgnoreCase("consensus")) 
				this.method = Method.CONSENSUS;
			else if (methodName.equalsIgnoreCase("consensusAllSols")) 
				this.method = Method.CONSENSUS_ALL_SOLS;
			else /// @todo Add support for ROBUST method, by merging code from WorstCaseUTIL
				System.err.println("Unknown method type for ExpectedUTIL; using EXPECTATION method by default");
		}
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported
	 */
	public ExpectedUTIL (Element parameters, DCOPProblemInterface<Val, U> problem) {
		super (parameters, problem);
		this.remainingVars = problem.getNbrVars();
		this.problem = problem;
		this.zero = problem.getZeroUtility();
		this.one = this.zero.fromString("1");
		if (parameters != null) 
			this.measureProbOfOpt = Boolean.parseBoolean(parameters.getAttributeValue("probOfOptimality"));
	}
	
	/** @see UTILpropagation#reset() */
	@Override
	public void reset () {
		super.reset();
		this.remainingVars = problem.getVariables().size();
		this.solution = new HashMap<String, Val> ();
		this.expectedUtil = null;
		this.worstUtil = null;
	}
	
	/** @see UTILpropagation#getMsgTypes() */
	@Override 
	public Collection<String> getMsgTypes () {
		Collection<String> types = super.getMsgTypes();
		types.add(RAND_VARS_PROJ_MSG_TYPE);
		return types;
	}
	
	/** @see UTILpropagation#getStatsFromQueue(Queue) */
	@Override
	public void getStatsFromQueue(Queue queue) {
		super.getStatsFromQueue(queue);
		queue.addIncomingMessagePolicy(VALUEpropagation.OUTPUT_MSG_TYPE, this);
	}

	/** @return the worst-case utility */
	public U getWorstUtil() {
		return worstUtil;
	}

	/** @return the expected utility */
	public U getExpectedUtil() {
		return this.expectedUtil;
	}

	/** @return the total probability of the scenarios for which the chosen solution is optimal */
	public U getProbOfOptimality () {
		return this.probOfOptimality;
	}
	
	/** @see UTILpropagation#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	@Override 
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(OPT_UTIL_MSG_TYPE)) { // we are in stats gatherer mode
			
			OptUtilMessage<U> msgCast = (OptUtilMessage<U>) msg;
			if (this.optUtil == null) {
				this.optUtil = msgCast.getUtility();
			} else 
				this.optUtil = (U) this.optUtil.add(msgCast.getUtility());

			return;
		}
		
		if (type.equals(VALUEpropagation.OUTPUT_MSG_TYPE)) { // we are in stats gatherer mode
			
			AssignmentsMessage<Val> msgCast = (AssignmentsMessage<Val>) msg;
			String[] vars = msgCast.getVariables();
			ArrayList<Val> vals = msgCast.getValues();
			for (int i = 0; i < vars.length; i++) {
				String var = vars[i];
				Val val = vals.get(i);
				if (val != null && solution.put(var, val) == null && !silent) 
					System.out.println("var `" + var + "' = " + val);
			}
			
			// When we have received all messages, print out the corresponding utility. 
			if (--this.remainingVars <= 0) {
				
				if (! this.silent) 
					System.out.println("Total reported " + (this.maximize ? "utility: " : "cost: ") + this.optUtil);
				
				this.expectedUtil = this.problem.getExpectedUtility(this.solution).getUtility(0);
				if (! this.silent) 
					System.out.println("Total expected " + (this.maximize ? "utility: " : "cost: ") + this.expectedUtil);

				// Compute the worst-case utility
				UtilitySolutionSpace<Val, U> paramUtil = this.problem.getUtility(this.solution, true);
				this.worstUtil = paramUtil.blindProjectAll(! this.maximize);
				if (! this.silent) 
					System.out.println("Total worst-case " + (this.maximize ? "utility: " : "cost: ") + this.worstUtil);
				
				if (this.measureProbOfOpt) { // Compute the probability of optimality

					List< ? extends UtilitySolutionSpace<Val, U> > allSpaces = this.problem.getSolutionSpaces(true);
					if (! allSpaces.isEmpty()) {

						// Join all spaces
						Class<? extends Val[]> classOfDom = (Class<? extends Val[]>) this.problem.getDomain(vars[0]).getClass();
						UtilitySolutionSpace<Val, U> join = new ScalarHypercube<Val, U> (this.zero, 
								this.problem.maximize() ? this.problem.getMinInfUtility() : this.problem.getPlusInfUtility(), classOfDom);
						for (UtilitySolutionSpace<Val, U> space : allSpaces) 
							join = join.join(space);

						// Go through all scenarios, summing up the probabilities of those for which the solution found is optimal
						this.probOfOptimality = this.zero;
						UtilitySolutionSpace<Val, U> probSpace = new ScalarHypercube<Val, U> (this.one, null, classOfDom);
						for (UtilitySolutionSpace<Val, U> space : this.problem.getProbabilitySpaces()) 
							probSpace = probSpace.multiply(space);

						if (probSpace.getVariables().length == 0) { // no random variables; only one scenario

							// Check whether we can find a better solution than the one output by the algorithm
							U utilFound = paramUtil.getUtility(0);
							U betterUtil = join.sparseIter().nextUtility(utilFound, ! this.problem.maximize());
							if (betterUtil == null) // no better solution found
								this.probOfOptimality = this.one;

						} else { // more than one scenario

							UtilitySolutionSpace.Iterator<Val, U> probIter = probSpace.iterator();
							UtilitySolutionSpace.Iterator<Val, U> utilIter = paramUtil.iterator(probSpace.getVariables(), probSpace.getDomains());
							while (probIter.hasNext()) {

								// Check whether we can find a better solution than the one output by the algorithm
								U utilFound = utilIter.nextUtility();
								UtilitySolutionSpace<Val, U> candidates = join.slice(probSpace.getVariables(), probIter.nextSolution());
								U betterUtil = candidates.sparseIter().nextUtility(utilFound, ! this.problem.maximize());
								if (betterUtil == null) // no better solution found
									this.probOfOptimality = this.probOfOptimality.add(probIter.getCurrentUtility());
							}
						}
					}
					if (!silent) 
						System.out.println("Probability of optimality: " + this.probOfOptimality);
				}
			}

			return;
		}
		
		if (type.equals(RAND_VARS_PROJ_MSG_TYPE)) { // where to project out some random variables
			
			// Parse the problem if this hasn't been done yet
			if (! this.started) 
				init();
			
			// Extract the information from the message
			RandVarsProjMsg msgCast = (RandVarsProjMsg) msg;
			String var = msgCast.getVariable();
			HashSet<String> randVars = msgCast.getRandVars();
			this.randVarsProj.put(var, randVars);
			
			// Obtain the info on the destination variable
			ClusterInfo varInfo = super.infos.get(var);
			if (varInfo == null) { // first message ever received concerning this variable
				varInfo = new ClusterInfo ();
				infos.put(var, varInfo);
				
				// We cannot do anything more until we get the DFS info for this variable
				return;
			}
			
			// Go through the list of stored spaces and slice each space according to the random variables' sampled domains
			this.sliceSpaces(varInfo);
			
			// Check if I have already received the DFS info and all UTIL messages from all children
			if (++varInfo.nbrUTIL >= varInfo.nbrChildren && varInfo.vars != null) 
				projectAndSend(varInfo);
			
			return;
		}
		
		else if (type.equals(UTIL_MSG_TYPE)) { // this is a UTIL message
			
			// Parse the problem if this hasn't been done yet
			if (! this.started) 
				init();
			
			// Retrieve the information from the message
			UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>) msg;
			String dest = msgCast.getDestination();

			// Obtain the info on the destination variable
			ClusterInfo info = infos.get(dest);
			if (info == null) { // first message ever received concerning this variable
				info = new ClusterInfo ();
				infos.put(dest, info);
				info.nbrUTIL = -1; // counting the RAND_VARS_PROJ message as a UTIL message
			}

			super.notifyIn(msg);
			return;
		}
		
		else if (type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // this is an output of the DFS generation phase

			// Parse the problem if this hasn't been done yet
			if (! this.started) 
				init();
			
			// Retrieve the information from the message 
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>) msg;
			String dest = msgCast.getVar();

			// Obtain the info on the destination variable
			ClusterInfo info = infos.get(dest);
			if (info == null) { // first message ever received concerning this variable
				info = new ClusterInfo ();
				infos.put(dest, info);
				info.nbrUTIL = -1; // counting the RAND_VARS_PROJ message as a UTIL message
			}

			super.notifyIn(msg);
			return;
		}
		
		super.notifyIn(msg);
	}
	
	/** Slices the spaces stored for the input variable according to the sampled domains
	 * @param varInfo 	the var info
	 */
	@SuppressWarnings("unchecked")
	private void sliceSpaces(ClusterInfo varInfo) {
		
		// Look up the neighboring random variables 
		HashSet<String> randNeigh = new HashSet<String> ();
		for (UtilitySolutionSpace<Val, U> space : varInfo.spaces) 
			for (String neigh : space.getVariables()) 
				if (this.problem.isRandom(neigh)) 
					randNeigh.add(neigh);
		int nbrRandNeigh = randNeigh.size();
		
		if (nbrRandNeigh == 0) 
			return;
		
		// Look up the sampled domains
		String[] randNeighArray = randNeigh.toArray(new String [nbrRandNeigh]);
		Val[] randDom = this.problem.getDomain(randNeighArray[0]);
		Val[][] randDoms = (Val[][]) Array.newInstance(randDom.getClass(), nbrRandNeigh);
		randDoms[0] = randDom;
		for (int i = 1; i < nbrRandNeigh; i++) 
			randDoms[i] = this.problem.getDomain(randNeighArray[i]);
		
		// Slice all spaces along the random variables
		LinkedList< UtilitySolutionSpace<Val, U> > slicedSpaces = new LinkedList< UtilitySolutionSpace<Val, U> > ();
		for (Iterator< UtilitySolutionSpace<Val, U> > iter = varInfo.spaces.iterator(); iter.hasNext(); ) {
			UtilitySolutionSpace<Val, U> space = iter.next();
			iter.remove();
			slicedSpaces.add(space.slice(randNeighArray, randDoms));
		}
		
		varInfo.spaces = slicedSpaces;
	}

	/** @return the solution */
	public HashMap<String, Val> getSolution() {
		return solution;
	}

	/** @see UTILpropagation#project(UtilitySolutionSpace, java.lang.String[]) */
	@Override 
	protected ProjOutput<Val, U> project (UtilitySolutionSpace<Val, U> space, String[] vars) {
		
		/// @todo Add support for clusters
		assert vars.length == 1 : "Clusters unsupported";
		String var = vars[0];
		
		/// @todo Add support for Method.ROBUST
		
		// Check if we should use the consensus method
		if (this.method == Method.CONSENSUS) {
			
			// Parse the probability distributions for the random variables in the input space
			HashMap< String, UtilitySolutionSpace<Val, U> > distributions = 
				new HashMap< String, UtilitySolutionSpace<Val, U> > ();
			for (String randVar : space.getVariables()) {
				if (! this.problem.isRandom(randVar)) // skip non-random variables 
					continue;
				
				List< ? extends UtilitySolutionSpace<Val, U> > probSpaces = this.problem.getProbabilitySpaces(randVar);
				distributions.put(randVar, probSpaces.get(0));
			}
			
			// Check whether all the random variables in the space are going to be projected out immediately hereafter
			if (this.randVarsProj.get(var).containsAll(distributions.keySet())) 
				return space.consensusExpect(var, distributions, maximize);
			else 
				return space.consensus(var, distributions, maximize);
		}
		
		else if (this.method == Method.CONSENSUS_ALL_SOLS) { // advanced consensus for all solutions
			
			// Parse the probability distributions for the random variables in the input space
			HashMap< String, UtilitySolutionSpace<Val, U> > distributions = 
				new HashMap< String, UtilitySolutionSpace<Val, U> > ();
			for (String randVar : space.getVariables()) {
				if (! this.problem.isRandom(randVar)) // skip non-random variables 
					continue;
				
				List< ? extends UtilitySolutionSpace<Val, U> > probSpaces = this.problem.getProbabilitySpaces(randVar);
				distributions.put(randVar, probSpaces.get(0));
			}
			
			// Check whether all the random variables in the space are going to be projected out immediately hereafter
			if (this.randVarsProj.get(var).containsAll(distributions.keySet())) 
				return space.consensusAllSolsExpect(var, distributions, maximize);
			else 
				return space.consensusAllSols(var, distributions, maximize);
		}
		
		// Else, use the expectation or expectationMonotone methods
		assert this.method == Method.EXPECTATION || this.method == Method.EXPECTATION_MONOTONE;
		
		// Retrieve the relevant probability spaces, and check whether all random variables must be projected out here
		HashMap< String, UtilitySolutionSpace<Val, U> > distributions = 
			new HashMap< String, UtilitySolutionSpace<Val, U> > ();
		HashSet<String> projHere = this.randVarsProj.get(var);
		boolean projectAll = true;
		for (String randVar : space.getVariables()) {
			if (this.problem.isRandom(randVar)) {
				List< ? extends UtilitySolutionSpace<Val, U> > thisVarProbSpaces = super.problem.getProbabilitySpaces(randVar);
				distributions.put(randVar, thisVarProbSpaces.get(0));
				if (! projHere.contains(randVar)) 
					projectAll = false;
			}
		}
		
		if (distributions.isEmpty()) 
			return space.project(var, this.maximize); // no random variable; we can simply project
		
//		System.out.println(var + ": Projecting out ");
		
		// Project the variable
		if (projectAll) {
			if (this.method == Method.EXPECTATION_MONOTONE) 
				return space.projExpectMonotone(var, distributions, this.maximize);
			
			else if (this.method == Method.EXPECTATION) 
				return space.expectation(distributions).project(var, maximize);
		}
		
		// Else, compute the optimal assignments for the projected out variable
		BasicUtilitySolutionSpace< Val, ArrayList<Val> > assignments = null;
				
		if (this.method == Method.EXPECTATION_MONOTONE) 
			assignments = space.projExpectMonotone(var, distributions, this.maximize).assignments; /// @todo call argProjExpectMonotone() instead
		
		else if (this.method == Method.EXPECTATION) 
			assignments = space.expectation(distributions).project(var, maximize).assignments; /// @todo call argProject() instead
		
		// Report the corresponding true utilities, as a function of all random variables
		String[] varsOut = new String[] {var};
		return new ProjOutput<Val, U> (space.compose(varsOut, assignments), varsOut, assignments);

	}
	
	/** Before sending the UTIL message, projects out the relevant random variables
	 * @see UTILpropagation#sendToParent(java.lang.String, java.lang.String, String, UtilitySolutionSpace)
	 */
	@Override 
	protected void sendToParent (String var, String parentVar, String parentAgent, UtilitySolutionSpace<Val, U> space) {
		
		// Before sending the UTIL message, we need to project out the random variables
		HashMap< String, UtilitySolutionSpace<Val, U> > distributions = 
			new HashMap< String, UtilitySolutionSpace<Val, U> > ();
		for (String randVar : this.randVarsProj.get(var)) 
			if (space.getDomain(randVar) != null) 
				distributions.put(randVar, this.problem.getProbabilitySpaces(randVar).get(0));
		if (! distributions.isEmpty()) 
			space = space.expectation(distributions);
		
		super.sendToParent(var, parentVar, parentAgent, space);
	}
	
	/** @see UTILpropagation#sendOutput(UtilitySolutionSpace, java.lang.String) */
	@Override
	protected void sendOutput(UtilitySolutionSpace<Val, U> space, String root) {

		// Then compute the expectation of the space over all random variables
		HashMap< String, UtilitySolutionSpace<Val, U> > distributions = 
			new HashMap< String, UtilitySolutionSpace<Val, U> > ();
		for (String randVar : space.getVariables()) 
			if (this.problem.isRandom(randVar)) 
				distributions.put(randVar, this.problem.getProbabilitySpaces(randVar).get(0));
		if (! distributions.isEmpty()) 
			space = space.expectation(distributions);
		
		super.sendOutput(space, root);
	}
	
	/** @see UTILpropagation#sendSeparator(java.lang.String, String, java.lang.String, java.lang.String[]) */
	@Override 
	protected void sendSeparator (String sender, String senderAgent, String dest, String[] separator) {
		
		// First remove the random variables from the separator 
		ArrayList<String> nonRandSep = new ArrayList<String> (separator.length);
		for (String var : separator) 
			if (! this.problem.isRandom(var)) 
				nonRandSep.add(var);
		
		queue.sendMessageToSelf(new SeparatorMessage (sender, dest, nonRandSep.toArray(new String [nonRandSep.size()]), senderAgent));
	}
	
}
