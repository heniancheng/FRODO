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

package frodo2.algorithms.varOrdering.dfs;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Element;

import frodo2.algorithms.heuristics.ScorePair;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** A DFS generation protocol with a heuristic for E[DPOP]
 * 
 * The heuristic attempts to avoid putting, on the path between a variable linked to random variable r and lca(r), 
 * a variable that is not linked to r. 
 * It is also a Variable Election heuristic, electing the variable with fewest neighboring random variables. 
 * @author Thomas Leaute
 * @todo When lca(r) can be a variable that is not linked to r, the implementation should be improved
 * by taking advantage of the random variables in backtracking CHILD tokens received from children. 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class LocalRandVarsDFS < V extends Addable<V>, U extends Addable<U> > extends DFSgeneration<V, U> {
	
	/** Heuristic used for variable election */
	public static class VarElectionHeuristic implements ScoringHeuristic<Short> {
		
		/** The agent's problem */
		private DCOPProblemInterface<?, ?> problem;
		
		/** Constructor
		 * @param problem 	the agent's problem
		 * @param params 	unused
		 */
		public VarElectionHeuristic (DCOPProblemInterface<?, ?> problem, Element params) {
			this.problem = problem;
		}
		
		/** Elects the variable with the smallest number of neighbor random variables
		 * @see ScoringHeuristic#getScores()
		 */
		public Map<String, Short> getScores() {
			
			HashMap<String, Short> scores = new HashMap<String, Short> ();
			for (Map.Entry< String, ? extends Collection<String> > entry : problem.getAnonymNeighborhoods().entrySet()) {
				assert entry.getValue().size() < - Short.MIN_VALUE;
				scores.put(entry.getKey(), (short) - entry.getValue().size());
			}
			
			return scores;
		}

	}

	/** The type of messages containing random variables */
	public static final String RAND_VARS_MSG_TYPE = "RandVarsMessage";
	
	/** For each of this agent's variables, its set of neighbor random variables */
	private Map< String, HashSet<String> > randNeighborhoods;
	
	/** For each variable owned by this agent, the set of agents that own a variable connected to this variable */
	private Map< String, Collection<String> > neighborAgents;
	
	/** Default constructor */
	public LocalRandVarsDFS () {
		super(true);
	}
	
	/** Constructor for the UniqueIDfactory
	 * @param params 	unused
	 */
	public LocalRandVarsDFS (Element params) {
		super(true);
	}
	
	/**
	 * @param problem 			this agent's problem
	 * @param parameters 		the parameters for LocalRandVarsDFS
	 * @throws ClassNotFoundException 		if the heuristic refers is of an unknown class
	 * @throws NoSuchMethodException 		if the heuristic does not have a constructor that takes in one ProblemInterface
	 * @throws InvocationTargetException 	if the heuristic constructor throws an exception
	 * @throws IllegalAccessException 		if the heuristic constructor is not accessible
	 * @throws InstantiationException 		if the heuristic class is abstract
	 */
	public LocalRandVarsDFS(DCOPProblemInterface<V, U> problem, Element parameters) 
	throws ClassNotFoundException, NoSuchMethodException, InstantiationException, 
	IllegalAccessException, InvocationTargetException {
		super (problem, parameters);
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported
	 */
	public LocalRandVarsDFS(Element parameters, DCOPProblemInterface<V, U> problem) {
		super(parameters, problem);
	}
	
	/** @see DFSgeneration#init() */
	protected void init () {
		super.init();
		this.randNeighborhoods = problem.getAnonymNeighborhoods();
		this.neighborAgents = problem.getAgentNeighborhoods();
	}

	/** @see DFSgeneration#getMsgTypes() */
	@Override 
	public Collection<String> getMsgTypes() {
		Collection<String> types = super.getMsgTypes();
		types.add(RAND_VARS_MSG_TYPE);
		types.add(START_MSG_TYPE);
		return types;
	}

	/** @see DFSgeneration#notifyIn(Message) */
	@Override 
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(START_MSG_TYPE)) {
			
			// Parse the problem if it has not been done yet
			if (! this.started) 
				init();
			
			// Go through the list of my own variables
			for (Map.Entry< String, Collection<String> > entry : this.neighborAgents.entrySet()) {
				String var = entry.getKey();

				// Send the set of neighboring random variables of this variable to its neighboring agents
				Collection<String> agents = entry.getValue();
				this.queue.sendMessageToMulti(agents, new RandVarsMsg (var, new HashSet<String> (this.randNeighborhoods.get(var))));
			}

			return;
		}
		
		else if (msgType.equals(RAND_VARS_MSG_TYPE)) { // message containing random variables
			
			// Parse the problem if it has not been done yet
			if (! this.started) 
				init();
			
			RandVarsMsg msgCast = (RandVarsMsg) msg;
			String var = msgCast.getVar();
			
			// Add the random variables in the message to the set of random variables for this variable
			HashSet<String> set = this.randNeighborhoods.get(var);
			if (set == null) {
				set = new HashSet<String> (msgCast.getRandVars());
				this.randNeighborhoods.put(var, set);
			} else 
				set.addAll(msgCast.getRandVars());
			
			return;
		}
		
		else if (msgType.equals(CHILD_MSG_TYPE)) {
			
			// Parse the problem if it has not been done yet
			if (! this.started) 
				init();
			
			CHILDmsg msgCast = (CHILDmsg) msg;
			String sender = msgCast.getSender();
			String myVar = msgCast.getDest();
			DFSview<V, U> myRelationships = dfsViews.get(myVar);

			if (myRelationships.getParent() == null && myRelationships.getChildren().isEmpty()) { // this is the first CHILD token received for this variable

				// Mark sender as parent
				openNeighbors.get(myVar).remove(sender);
				myRelationships.setParent(sender, this.problem.getOwner(sender));
				
				// Record the random variables 
				this.randNeighborhoods.get(msgCast.getDest()).addAll(((CHILDrandMsg)msgCast).getRandVars());
				
//				System.out.println("" + myVar + ":\t Got first CHILD token from parent " + sender);
			}

			super.notifyIn(msg);
		}
		
		else
			super.notifyIn(msg);
	}

	/** @see DFSgeneration#sendDownCHILDtoken(Serializable, java.lang.String, java.util.Collection, DFSview, Message)*/
	@Override 
	protected boolean sendDownCHILDtoken(Serializable rootID, String var, Collection<String> openList, DFSview<V, U> myDFSview, Message msg) {
		
		if (openList.isEmpty()) 
			return false;

		String child = this.popNextChild(var, myDFSview, openList);
		HashSet<String> randVars = this.randNeighborhoods.get(var);
		
		if (child == null || randVars == null) { // we cannot compute the next child yet; delay this decision
			queue.sendMessageToSelf(msg);
			return true;
		}

		// Send a CHILD message and mark recipient as child
		queue.sendMessage(this.owners.get(child), new CHILDrandMsg (var, child, rootID, randVars));
		myDFSview.addChild(child);
		
		// Add the child to the current partial path
		LinkedList<String> partialPath = this.partialPaths.get(rootID);
		if (partialPath == null) {
			partialPath = new LinkedList<String> ();
			this.partialPaths.put(rootID, partialPath);
		}
		partialPath.add(child);
		
//		System.out.println("" + var + ":\t Sending CHILD token to child " + child);
		
		return true;
	}
	
	/** Chooses the child with the most similar set of new neighboring random variables to that of the input variable
	 * 
	 * Breaks ties using the heuristic given as a parameter to the module constructor. 
	 * @param var 				the variable for which we are looking for the next child
	 * @param dfsView 			the current incomplete view that this variable has of its DFS neighbors
	 * @param openNeighbors 	the list of open neighbors for this variable
	 * @return the name of the chosen next child, or \c null if not all messages necessary to choose have been received
	 */
	private String popNextChild(String var, DFSview<V, U> dfsView, Collection<String> openNeighbors) {
		
		// Get the set of random variables for the input variable
		HashSet<String> myRandVars = new HashSet<String> (this.randNeighborhoods.get(var));
		
		// Classify the open neighbors based on the random variables they have
		// Choose the child we have the largest number of random variables in common with, 
		// breaking ties by choosing the child with the smallest number of new random variables
		TreeMap< ScorePair<Integer, Integer>, List<String> > classification = new TreeMap< ScorePair<Integer, Integer>, List<String> > ();
		for (String openNeighbor : openNeighbors) {
			HashSet<String> yourRandVars = this.randNeighborhoods.get(openNeighbor);
			if (yourRandVars == null) // we don't know the random variables for this open neighbor yet
				return null;
			
			// Compute how many random variables we have in common
			HashSet<String> common = new HashSet<String> (myRandVars);
			common.retainAll(yourRandVars);
			int nbrCommon = common.size();
			
			ScorePair<Integer, Integer> score = new ScorePair<Integer, Integer> (nbrCommon, nbrCommon - yourRandVars.size());
//			ScorePair<Integer, Integer> score = new ScorePair<Integer, Integer> (nbrCommon - yourRandVars.size(), nbrCommon);
			
			List<String> neighbors = classification.get(score);
			if (neighbors == null) {
				neighbors = new ArrayList<String> ();
				classification.put(score, neighbors);
			}
			neighbors.add(openNeighbor);
		}
		
		// Look up the set of open neighbors with highest score
		List<String> bestOpenNeighbors = classification.get(classification.lastKey());
		
		// Find the best next child in the set 
		String nextChild;
		if (bestOpenNeighbors.size() == 1) {
			nextChild = bestOpenNeighbors.get(0);
		} else {
			nextChild = super.heuristic.popNextChild(var, dfsView, bestOpenNeighbors);
			if (nextChild == null) // we can't decide yet
				return null;
		}
		
		openNeighbors.remove(nextChild);
		return nextChild;
	}

}
