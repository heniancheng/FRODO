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

package frodo2.algorithms.dpop.stochastic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEpropagation.AssignmentsMessage;
import frodo2.algorithms.dpop.stochastic.ExpectedUTIL.Method;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** Implements an adaptation of the QDisCSP approach by Yokoo (DCR'09)
 * 
 * Assigns each random variable to its lowest neighbor in the DFS, which is responsible for minimizing over it.
 * @author Thomas Leaute
 * @param <Val> type used for variable values
 * @param <U> 	type used for utility values
 */
public class CompleteUTIL < Val extends Addable<Val>, U extends Addable<U> > extends UTILpropagation<Val, U> {
	
	/** The type of the messages telling what random variables should be projected out at given decision variable */
	public static final String RAND_VARS_PROJ_MSG_TYPE = "Where to project random variables";
	
	/** Message telling what random variables should be projected out at given decision variable */
	public static class RandVarsProjMsg extends MessageWith2Payloads< String, ArrayList<String> > {

		/** Empty constructor used for externalization */
		public RandVarsProjMsg () { }

		/** Constructor 
		 * @param variable 	the decision variable
		 * @param randVars 	the random variables that should be projected out at this decision variable
		 */
		public RandVarsProjMsg(String variable, ArrayList<String> randVars) {
			super(RAND_VARS_PROJ_MSG_TYPE, variable, randVars);
		}
		
		/** @return the decision variable */
		public String getVariable () {
			return super.getPayload1();
		}
		
		/** @return the random variables */
		public ArrayList<String> getRandVars () {
			return super.getPayload2();
		}
	}
	
	/** What random variables each decision variable is responsible for projecting */
	private HashMap< String, ArrayList<String> > randVarsToProject = new HashMap< String, ArrayList<String> > ();

	/** What random variables each decision variable is linked to but not responsible for projecting */
	private HashMap< String, ArrayList<String> > randVarsToIgnore = new HashMap< String, ArrayList<String> > ();

	/** For each variable, its optimal value */
	private HashMap<String, Val> solution = new HashMap<String, Val> ();

	/** The number of variables owned by this agents that still have not sent VALUE messages to all their children */
	private int remainingVars;

	/** The worst-case utility */
	private U worstUtil;

	/** The expected utility */
	private U expectedUtil;
	
	/** A utility of 0 */
	private final U zero;
	
	/** A utility of 1 */
	private final U one;
	
	/** Whether to measure the probability of optimality */
	private boolean measureProbOfOpt = false;
	
	/** The total probability of the scenarios for which the chosen solution is optimal */
	private U probOfOptimality;
	
	/** The percentage of centralization imposed by the consistent DFS */
	private double centralization = -1.0;

	/** For every variable this agent owns, its view of the DFS
	 * 
	 * Used only in stats gatherer mode. 
	 */
	private HashMap< String, DFSview<Val, U> > relationships;

	/** The total number messages to expect in "statistics gatherer" mode */
	private int nbrStatsMsgs;

	/** Renderer to display DOT code */
	private String dotRendererClass = null;
	
	/** The method to use */
	private final Method method;
	
	/** The number of samples for the random variables */
	private final int nbrSamples;

	/** The constructor called in "statistics gatherer" mode
	 * @param parameters 	the description of what statistics should be reported
	 * @param problem 		the overall problem
	 */
	public CompleteUTIL (Element parameters, DCOPProblemInterface<Val, U> problem) {
		super (parameters, problem);
		this.remainingVars = problem.getNbrVars();
		this.problem = problem;
		this.zero = problem.getZeroUtility();
		this.one = this.zero.fromString("1");
		super.withAnonymVars = true;
		relationships = new HashMap< String, DFSview<Val, U> > ();
		this.nbrStatsMsgs = 2 * problem.getNbrVars(); // one DFS message and one RandVarsProjMsg message
		this.nbrSamples = 0;
		this.method = null;
		if (parameters != null) 
			this.measureProbOfOpt = Boolean.parseBoolean(parameters.getAttributeValue("probOfOptimality"));

		if(parameters != null) dotRendererClass = parameters.getAttributeValue("DOTrenderer");
		if (this.dotRendererClass == null) 
			this.dotRendererClass = "";
	}
	
	/** Constructor from XML descriptions
	 * @param problem 					description of the problem
	 * @param parameters 				description of the parameters of CompleteUTIL
	 * @throws ClassNotFoundException 	if the module parameters specify an unknown class for utility values
	 */
	public CompleteUTIL (DCOPProblemInterface<Val, U> problem, Element parameters) throws ClassNotFoundException {
		super (problem, parameters);
		this.zero = problem.getZeroUtility();
		this.one = this.zero.fromString("1");
		super.withAnonymVars = true;
		
		// Parse the method to use, if it is specified
		if (parameters == null) 
			this.method = Method.ROBUST;
		else {
			String methodName = parameters.getAttributeValue("method");
			if (methodName == null) 
				this.method = Method.ROBUST;
			else {
				if (methodName.equalsIgnoreCase("robust")) 
					this.method = Method.ROBUST;
				else if (methodName.equalsIgnoreCase("expectation")) 
					this.method = Method.EXPECTATION;
				else if (methodName.equalsIgnoreCase("expectationMonotone")) 
					this.method = Method.EXPECTATION_MONOTONE;
				else if (methodName.equalsIgnoreCase("consensus")) 
					this.method = Method.CONSENSUS;
				else if (methodName.equalsIgnoreCase("consensusAllSols")) 
					this.method = Method.CONSENSUS_ALL_SOLS;
				else {
					System.err.println("Unknown method type for CompleteUTIL; using ROBUST method by default");
					this.method = Method.ROBUST;
				}
			}
		}
		
		// Parse the number of samples
		if (parameters == null) 
			this.nbrSamples = 0;
		else {
			String nbrStr = parameters.getAttributeValue("nbrSamples");
			this.nbrSamples = (nbrStr == null ? 0 : Integer.parseInt(nbrStr));
		}
	}
	
	/** @see UTILpropagation#getStatsFromQueue(Queue) */
	@Override
	public void getStatsFromQueue(Queue queue) {
		super.getStatsFromQueue(queue);
		queue.addIncomingMessagePolicy(VALUEpropagation.OUTPUT_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(DFSgeneration.STATS_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(RAND_VARS_PROJ_MSG_TYPE, this);
	}
	
	/** @see UTILpropagation#init() */
	@Override
	protected void init () {
		super.init();
		
		// Sample all random variables I know
		if (this.nbrSamples > 0) 
			for (UtilitySolutionSpace<Val, U> probDist : this.problem.getProbabilitySpaces()) 
				this.problem.setProbSpace(probDist.getVariable(0), probDist.sample(nbrSamples));
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
	
	/** @return the level of centralization */
	public double getCentralization () {
		return this.centralization;
	}
	
	/** @return the solution */
	public HashMap<String, Val> getSolution() {
		return solution;
	}

	/** @see UTILpropagation#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	@Override
	public void notifyIn(Message msg) {

		String type = msg.getType();
		
		if (type.equals(DFSgeneration.STATS_MSG_TYPE)) { // statistics message
			
			// If we receive this message, it means we are actually running in "statistics gatherer" mode
			
			// Retrieve and store the information from the message
			MessageDFSoutput<Val, U> msgCast = (MessageDFSoutput<Val, U>) msg;
			relationships.put(msgCast.getVar(), msgCast.getNeighbors());
			
			// If all information has been received from all variables, print it
			if (--this.nbrStatsMsgs <= 0) {
				
				// Compute the level of centralization
				this.centralization();
				
				if (! this.silent) {
					if(dotRendererClass.equals("")) {
						System.out.println("Chosen DFS tree, with random variables:");
						System.out.println(dfsToString());
					}
					else {
						try {
							Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("DFS Tree, with random variables", dfsToString());
						} 
						catch(Exception e) {
							System.out.println("Could not instantiate given DOT renderer class: " + this.dotRendererClass);
						}
					}
				}
			}

			return;
		}
		
		else if (type.equals(RAND_VARS_PROJ_MSG_TYPE)) { // in stats gatherer mode
			
			RandVarsProjMsg msgCast = (RandVarsProjMsg) msg;
			this.randVarsToProject.put(msgCast.getVariable(), msgCast.getRandVars());
			
			// If all information has been received from all variables, print it
			if (--this.nbrStatsMsgs <= 0) {
				
				// Compute the level of centralization
				this.centralization();
				
				if (! this.silent) {
					if(dotRendererClass.equals("")) {
						System.out.println("Chosen DFS tree, with random variables:");
						System.out.println(dfsToString());
					}
					else {
						try {
							Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("DFS Tree, with random variables", dfsToString());
						} 
						catch(Exception e) {
							System.out.println("Could not instantiate given DOT renderer class: " + this.dotRendererClass);
						}
					}
				}
			}

			return;
		}
		
		if (type.equals(OPT_UTIL_MSG_TYPE)) { // we are in stats gatherer mode
			
			OptUtilMessage<U> msgCast = (OptUtilMessage<U>) msg;
			if (this.optUtil == null) {
				this.optUtil = msgCast.getUtility();
			} else 
				this.optUtil = this.optUtil.add(msgCast.getUtility());

			return;
		}
		
		if (type.equals(VALUEpropagation.OUTPUT_MSG_TYPE)) { // we are in stats gatherer mode
			
			AssignmentsMessage<Val> msgCast = (AssignmentsMessage<Val>) msg;
			String[] vars = msgCast.getVariables();
			ArrayList<Val> vals = msgCast.getValues();
			for (int i = 0; i < vars.length; i++) {
				String var = vars[i];
				Val val = vals.get(i);
				if (!silent) 
					System.out.println("var `" + var + "' = " + val);
				solution.put(var, val);
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
				this.worstUtil = paramUtil.blindProjectAll(! this.maximize);;
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

				if (!silent) 
					System.out.println("Level of centralization: " + (this.centralization * 100) + " %");
			}

			return;
		}
		
		else if (type.equals(this.getDFSMsgType())) {
			
			// Retrieve the information from the message about children, pseudo-children... 
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>) msg;
			String var = msgCast.getVar();
			DFSview<Val, U> myRelationships = msgCast.getNeighbors();

			// Compute the set of (pseudo-)children
			HashSet<String> below = new HashSet<String> (myRelationships.getChildren());
			below.addAll(myRelationships.getAllPseudoChildren());
			
			// Compute the list of neighboring random variables whose neighbors are all above myself in the DFS
			ArrayList<String> randVarsProjected = new ArrayList<String> ();
			ArrayList<String> randVarsIgnored = new ArrayList<String> ();
			nextRandVar: for (String randVar : this.problem.getAnonymNeighborhoods().get(var)) {
				for (String neigh : this.problem.getNeighborVars(randVar)) {
					if (below.contains(neigh)) {
						randVarsIgnored.add(randVar);
						continue nextRandVar;
					}
				}
				randVarsProjected.add(randVar);
			}
			this.randVarsToProject.put(var, randVarsProjected);
			queue.sendMessage(AgentInterface.STATS_MONITOR, new RandVarsProjMsg (var, new ArrayList<String> (randVarsProjected)));
			this.randVarsToIgnore.put(var, randVarsIgnored);
		}
		
		super.notifyIn(msg);
	}
	
	/** Computes the level of centralization */
	private void centralization() {
		
		// Compute the total number of utility values
		long total = 0;
		for (UtilitySolutionSpace<Val, U> space : this.problem.getSolutionSpaces(true)) 
			total += space.getNumberOfSolutions();
		
		// Compute the amount of knowledge of the most knowledgeable agent
		long baseline = 0;
		long max = 0;
		for (String agent : this.problem.getAgents()) {
			
			long newBaseline = 0;
			long newMax = 0;
			DCOPProblemInterface<Val, U> subProb = this.problem.getSubProblem(agent);
			
			// Loop over the spaces in the agent's subproblem
			spaceLoop: for (UtilitySolutionSpace<Val, U> space : subProb.getSolutionSpaces(true)) {
				
				// If the constraint involves any of my variables, then I am supposed to know the space
				List<String> scope = Arrays.asList(space.getVariables());
				Set<String> myVarsInScope = subProb.getMyVars();
				myVarsInScope.retainAll(scope);
				if (! myVarsInScope.isEmpty()) {
					newBaseline += space.getNumberOfSolutions();
					newMax += space.getNumberOfSolutions();
					continue;
				}
				
				// Check whether I am additionally required to know this constraint because it involves a random variable I must project out
				for (String var : subProb.getMyVars()) {
					if (! Collections.disjoint(this.randVarsToProject.get(var), scope)) {
						newMax += space.getNumberOfSolutions();
						continue spaceLoop;
					}
				}
			}
			
			baseline = Math.max(baseline, newBaseline);
			max = Math.max(max, newMax);
		}
		
		if (baseline == total) 
			this.centralization = 0.0;
		else // re-scale
			this.centralization = 1.0 - (total - max)/(double)(total - baseline);
	}

	/** @see UTILpropagation#record(String, UtilitySolutionSpace, ClusterInfo) */
	@Override
	protected void record(String senderVar, UtilitySolutionSpace<Val, U> space, ClusterInfo info) {

		if (info.vars != null) { // this is not a UTIL message received before the DFSoutput
			
			// Ignore this space if it involves a random variable that should be ignored because it will be projected out lower in the DFS
			assert info.vars.length == 1 : "Clusters not supported"; /// @todo Add support for clusters
			ArrayList<String> randVars = this.randVarsToIgnore.get(info.vars[0]);
			for (String var : space.getVariables()) 
				if (randVars.contains(var)) 
					return;
		}
		
		super.record(senderVar, space, info);
	}
	
	/** @see UTILpropagation#projectAndSend(ClusterInfo) */
	@SuppressWarnings("unchecked")
	@Override
	protected void projectAndSend(ClusterInfo info) {
		
		// Add to my spaces all spaces involving random variables I am responsible for projecting out
		assert info.vars.length == 1 : "Clusters not supported"; /// @todo Add support for clusters
		String self = info.vars[0];
		ArrayList<String> randVars = this.randVarsToProject.get(self);
		for (int i = 0; i < randVars.size(); i++) {
			String randVar = randVars.get(i);
			
			// Iterate through all spaces involving randVar and not involving my own variable 
			// (the spaces involving my own variable are already in info.spaces)
			spaceLoop: for (UtilitySolutionSpace<Val, U> space : this.problem.getSolutionSpaces(randVar, true, new HashSet<String> (Arrays.asList(self)))) {
				
				// Skip this space if it involves any of the random variables we have already got the spaces for
				for (int j = 0; j < i; j++) 
					if (space.getDomain(randVars.get(j)) != null) 
						continue spaceLoop;
				
				info.spaces.add(space);
			}
		}
		
		// Check if this variable is unconstrained
		if (info.spaces.isEmpty()) {
			super.projectAndSend(info);
			return;
		}
		
		// Join all resulting spaces
		UtilitySolutionSpace<Val, U> join = info.spaces.removeFirst().join(info.spaces.toArray(new UtilitySolutionSpace [info.spaces.size()]));
		info.spaces = null;
		
		// Project out the random variables and my own decision variable
		ProjOutput<Val, U> projOutput = this.project(join, randVars, self);
		join = null;

		// Send the optimal assignments to the VALUE propagation protocol
		queue.sendMessageToSelf(new SolutionMessage<Val> (projOutput.varsOut[0], projOutput.varsOut, projOutput.getAssignments()));

		// Send resulting space to parent (if any)
		if (info.parent != null) {
			this.sendToParent (info.vars[0], info.parent, info.parentAgent, projOutput.getSpace());
		} else  // the variable is a root
			this.sendOutput (projOutput.getSpace(), info.vars[0]);
	}
	
	/** Projects out the input variables
	 * @param space 	the space
	 * @param randVars 	the random variables
	 * @param myVar 	the decision variable
	 * @return the output of the projection
	 */
	private ProjOutput<Val, U> project (UtilitySolutionSpace<Val, U> space, ArrayList<String> randVars, String myVar) {
		
		if (! randVars.isEmpty()) {
			
			if (this.method == Method.ROBUST) 
				return space.blindProject(randVars.toArray(new String [randVars.size()]), ! this.maximize).project(myVar, this.maximize);
			
			// Retrieve the probability spaces
			HashMap< String, UtilitySolutionSpace<Val, U> > distributions = new HashMap< String, UtilitySolutionSpace<Val, U> > ();
			for (String randVar : randVars) 
				distributions.put(randVar, this.problem.getProbabilitySpaces(randVar).get(0));

			if (this.method == Method.CONSENSUS) 
				return space.consensusExpect(myVar, distributions, this.maximize);

			else if (this.method == Method.CONSENSUS_ALL_SOLS) 
				return space.consensusAllSolsExpect(myVar, distributions, this.maximize);

			else if (this.method == Method.EXPECTATION_MONOTONE) 
				return space.projExpectMonotone(myVar, distributions, this.maximize);

			else { // EXPECTATION 
				assert this.method == Method.EXPECTATION : "Unsupported method: " + this.method;

				space = space.expectation(distributions);
			}
		}
		
		// Project my own variable 
		return space.project(myVar, this.maximize);		
	}
	
	/** @return a DOT-formated representation of the DFS, including random variables */
	private String dfsToString() {
		StringBuilder out = new StringBuilder ("digraph {\n\tnode [shape = \"circle\"];\n\n");
		
		// For each variable:
		for (Map.Entry< String, DFSview<Val, U> > entry : this.relationships.entrySet()) {
			String var = entry.getKey();
			DFSview<Val, U> relationships = entry.getValue();
						
			// First print the variable
			ArrayList<String> myRandVarsProj = this.randVarsToProject.get(var);
			if (myRandVarsProj.isEmpty()) {
				out.append("\t" + var + " [label = \"" + var + "\" style=\"filled\"];\n");
			} else {
				
				// Create a cluster for this variable and the random variables it must project out
				out.append("\tsubgraph cluster_" + var + " {\n");
				out.append("\t\t" + var + " [label = \"" + var + "\" style=\"filled\"];\n");
				for (String randVar : myRandVarsProj) 
					out.append("\t\t" + randVar + ";\n");
				out.append("\t}\n");
			}

			if (relationships == null) {
				System.err.println("Empty relationships for variable " + var);
				System.exit(1);
			}
			
			// Print the edge with the parent, if any
			String parent = relationships.getParent();
			if (parent != null) 
				out.append("\t" + parent + " -> " + var + ";\n");
			
			// Print the edges with the pseudo-parents, if any
			for (String pseudo : relationships.getPseudoParents()) {
				out.append("\t" + pseudo + " -> " + var + " [style = \"dashed\" arrowhead = \"none\" weight=\"0.5\"];\n");
			}
			
			out.append("\n");
		}
		
		// Print the edges with the random variables
		for (String randVar : this.problem.getAnonymVars()) 
			for (String neighbor : this.problem.getNeighborVars(randVar, true)) 
				out.append("\t" + neighbor + " -> " + randVar + " [style = \"dashed\" arrowhead = \"none\" weight=\"0.5\"];\n");

		out.append("}");
		return out.toString();
	}

}
