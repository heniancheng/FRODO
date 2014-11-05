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

/** Algorithms to produce pseudo-tree variable orderings */
package frodo2.algorithms.varOrdering.dfs;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** Distributed DFS generation protocol
 * @author Thomas Leaute, Jonas Helfer, Eric Zbinden
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class DFSgeneration < V extends Addable<V>, U extends Addable<U> > implements StatsReporter {

	/** The queue on which it should call sendMessage() */
	protected Queue queue;

	/** Whether the execution of the algorithm has started */
	protected boolean started = false;

	/** For each variable that this agent owns, a collection of open neighbor variables */
	protected Map < String, Collection <String> > openNeighbors;

	/** The type of the message telling the agent finished */
	public static String FINISH_MSG_TYPE = AgentInterface.AGENT_FINISHED;

	/** The view of the DFS from one variable 
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public static class DFSview < V extends Addable<V>, U extends Addable<U> > implements Serializable {
		
		/** Used for serialization */
		private static final long serialVersionUID = -4589293255218453960L;
		
		/** This cluster's ID */
		private final String id;
		
		/** The list of pseudo-parents */
		private ArrayList<String> pseudoParents = new ArrayList<String> ();
		
		/** The parent variable, if any */
		private String parent;
		
		/** The parent agent, if any */
		private String parentAgent;
		
		/** The list of children */
		private ArrayList<String> children = new ArrayList<String> ();
		
		/** For each child, the list of pseudo-children below that child */
		private HashMap< String, List<String> > pseudoChildren = new HashMap< String, List<String> > ();

		/** For each pseudo-child, a partial path that leads to it */
		private HashMap< String, List<String> > pathsToPseudoChildren = new HashMap< String, List<String> > ();
		
		/** The list of constraints this variable is responsible for enforcing */
		private List< ? extends UtilitySolutionSpace<V, U> > spaces;
		
		/** Constructor
		 * @param id 	this cluster's ID
		 */
		public DFSview (String id) {
			this.id = id;
		}

		/** @see java.lang.Object#clone() */
		@Override
		public DFSview<V, U> clone () {
			DFSview<V, U> out = new DFSview<V, U> (this.id);
			out.pseudoParents = new ArrayList<String> (this.pseudoParents);
			out.parent = this.parent;
			out.parentAgent = this.parentAgent;
			out.children = new ArrayList<String> (this.children);
			for (Map.Entry< String, List<String> > entry : this.pseudoChildren.entrySet()) 
				out.pseudoChildren.put(entry.getKey(), new ArrayList<String> (entry.getValue()));
			for (Map.Entry< String, List<String> > entry : this.pathsToPseudoChildren.entrySet()) 
				out.pathsToPseudoChildren.put(entry.getKey(), new ArrayList<String> (entry.getValue()));
			out.spaces = new ArrayList< UtilitySolutionSpace<V, U> > (this.spaces); /// @bug Do a deep clone instead?
			return out;
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			
			StringBuilder builder = new StringBuilder ("DFSview[");
			builder.append("ID: " + this.id);
			builder.append(", pseudoParents: " + this.pseudoParents);
			builder.append(", parentVar: " + this.parent);
			builder.append(", parentAgent: " + this.parentAgent);
			builder.append(", children: " + this.children);
			builder.append(", pseudoChildren: " + this.pseudoChildren);
			
			if (this.spaces != null) {
				builder.append(", spaces:");
				for (UtilitySolutionSpace<V, U> space : this.spaces)
					builder.append(" " + space.getName());
			}
			
			builder.append("]");

			return builder.toString();
		}
		
		/** @return this cluster's ID */
		public String getID () {
			return this.id;
		}
		
		/** Returns the (immutable) list of pseudo-children below the input child
		 * @param child 	the child variable
		 * @return 	the list of children below the input variable if it is a child; \c null if it is not a child
		 */
		public List<String> getPseudoChildren (String child) {
			return Collections.unmodifiableList(this.pseudoChildren.get(child));
		}
		
		/** Adds a child
		 * @param child 	the new child variable
		 */
		public void addChild (String child) {
			this.children.add(child);
			this.pseudoChildren.put(child, new ArrayList<String> ());
		}
		
		/** Removes a child
		 * @param child 	the new child variable
		 * @return the list of pseudo-children hanging from that child
		 */
		public List<String> removeChild (String child) {
			this.children.remove(child);
			return this.pseudoChildren.remove(child);
		}
		
		/** Changes one child into another
		 * @param oldChild 	the old child
		 * @param newChild 	the new child
		 */
		public void changeChild (String oldChild, String newChild) {
			
			List<String> newPseudos = this.removeChild(oldChild);
			this.addChild(newChild);
			List<String> oldPseudos = this.pseudoChildren.get(newChild);
			if (newPseudos != null) 
				for (String pseudo : newPseudos) 
					if (! oldPseudos.contains(pseudo)) 
						oldPseudos.add(pseudo);
		}
		
		/** Adds a pseudo-child below the last child
		 * @param pseudo 	the new pseudo-child
		 */
		public void addPseudoChild (String pseudo) {
		
			assert ! children.contains(pseudo) : pseudo + " is already a child";
			
			// Look up the last child added, i.e. the child whose subtree is being explored
			assert !children.isEmpty(); /// @todo: got a very rare assertion error when running ODPOP tests!
			String lastChild = children.get(children.size() - 1);
			
			// Add the input variable to its list of pseudo-children
			List<String> pseudos = this.pseudoChildren.get(lastChild);
			if (! pseudos.contains(pseudo)) 
				pseudos.add(pseudo);
		}
		
		/** Adds a pseudo child below the last child
		 * @param pseudo 		the new pseudo-child
		 * @param partialPath 	the partial path to that pseudo-child
		 */
		public void addPseudoChild (String pseudo, List<String> partialPath) {
			
			this.addPseudoChild(pseudo);
			this.pathsToPseudoChildren.put(pseudo, new ArrayList<String> (partialPath));
		}
		
		/** Adds a pseudo-child 
		 * @param child 	the child under which to add the new pseudo-child
		 * @param pseudo 	the new pseudo-child
		 */
		public void addPseudoChild (String child, String pseudo) {
			
			// Loop up the pseudo-children under the given child
			List<String> pseudos = this.pseudoChildren.get(child);
			assert pseudos != null : "Attempting to add a pseudo-child under a non-existing child " + child;
			
			// Add the pseudo-child, if it is not already in the list
			if (! pseudos.contains(pseudo)) 
				pseudos.add(pseudo);
		}
		
		/** Adds a pseudo child
		 * @param child 	the child under which to add the new pseudo-child
		 * @param pseudo 		the new pseudo-child
		 * @param partialPath 	the partial path to that pseudo-child
		 */
		public void addPseudoChild (String child, String pseudo, List<String> partialPath) {
			
			this.addPseudoChild(child, pseudo);
			this.pathsToPseudoChildren.put(pseudo, new ArrayList<String> (partialPath));
		}
		
		/** Removes a pseudo-child
		 * @param pseudo 	a variable
		 */
		public void removePseudoChild (String pseudo) {

			// Remove pseudo from the list of pseudo-children hanging from a particular child
			for (List<String> set : this.pseudoChildren.values()) 
				if (set.remove(pseudo)) 
					break;
		}

		/** Sets the parent
		 * @param var 		the parent variable
		 * @param agent 	the parent agent
		 */
		public void setParent(String var, String agent) {
			this.parent = var;
			this.parentAgent = agent;
		}

		/** Adds a pseudo-parent
		 * @param var 	the pseudo-parent variable
		 * @todo Also record the pseudo-parent agent?
		 */
		public void addPseudoParent(String var) {
			assert ! var.equals(this.parent) : var + " is already the parent";
			if(!this.pseudoParents.contains(var))
				this.pseudoParents.add(var);
		}

		/** @return the parent variable, if any */
		public String getParent() {
			return this.parent;
		}

		/** @return the parent agent, if any */
		public String getParentAgent() {
			return this.parentAgent;
		}

		/** @return the (immutable) list of children */
		public List<String> getChildren() {
			return Collections.unmodifiableList(this.children);
		}

		/** @return the (immutable) list of pseudo-parents */
		public List<String> getPseudoParents() {
			return Collections.unmodifiableList(this.pseudoParents);
		}

		/** @return the (immutable) list of all pseudo-children */
		public List<String> getAllPseudoChildren() {
			
			ArrayList<String> all = new ArrayList<String> ();
			for (String child : this.children) 
				all.addAll(this.getPseudoChildren(child));
			
			return Collections.unmodifiableList(all);
		}

		/** Removes a pseudo-parent
		 * @param var 	the old pseudo-parent variable
		 */
		public void removePseudoParent(String var) {
			this.pseudoParents.remove(var);
		}
		
		/** Returns the partial path to the input pseudo-child
		 * @param pseudo 	the pseudo-child
		 * @return the partial path to this pseudo-child
		 */
		public List<String> getPathToPseudoChild (String pseudo) {
			return Collections.unmodifiableList(this.pathsToPseudoChildren.get(pseudo));
		}
		
		/** @return The list of constraints this variable is responsible for enforcing */
		public List< ? extends UtilitySolutionSpace<V, U> > getSpaces() {
			return spaces;
		}

		/** Sets the list of constraints this variable is responsible for enforcing
		 * @param spaces the spaces
		 */
		public void setSpaces(List< ? extends UtilitySolutionSpace<V, U> > spaces) {
			this.spaces = spaces;
		}
	}

	/** For every variable this agent owns, its view of the DFS */
	protected HashMap< String, DFSview<V, U> > dfsViews;

	/** The variables for which a DFSoutput has already been sent */
	private HashSet<String> sentOutputs = new HashSet<String> ();
	
	/** For each known variable, the name of the agent that owns it */
	protected Map<String, String> owners;

	/** The total number of variables in the problem (used only in "statistics gatherer" mode) */
	protected int totalNbrVars;

	/** The heuristic used to choose a variable's next child from its list of open neighbors */
	protected NextChildChoiceHeuristic heuristic;

	/** Whether the stats reporter should print its stats */
	private boolean silent = false;

	/** Renderer to display DOT code */
	private String dotRendererClass = null;

	/** The problem */
	protected DCOPProblemInterface<V, U> problem;
	
	/** For each component, the current partial constraint graph traversal path */
	protected HashMap< Serializable, LinkedList<String> > partialPaths = new HashMap< Serializable, LinkedList<String> > ();

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of the messages telling whether a given variable is a root */
	public static String ROOT_VAR_MSG_TYPE = LeaderElectionMaxID.OUTPUT_MSG_TYPE;

	/** @return the types of the messages telling whether a given variable is a root */
	protected String getRootVarMsgType () {
		return ROOT_VAR_MSG_TYPE;
	}

	/** The type of the message used to tell the recipient that it is a child of the sender */
	public static final String CHILD_MSG_TYPE = "DFSchild";
	
	/** @return The type of the message used to tell the recipient that it is a child of the sender */
	protected String getChildMsgType () {
		return CHILD_MSG_TYPE;
	}

	/** The type of the message used to tell the recipient that it is a pseudo-child of the sender */
	public static String PSEUDO_MSG_TYPE = "DFSpseudo";
	
	/** @return The type of the message used to tell the recipient that it is a pseudo-child of the sender */
	protected String getPseudoMsgType () {
		return PSEUDO_MSG_TYPE;
	}

	/** The type of the output messages */
	public static String OUTPUT_MSG_TYPE = "DFSoutput";
	
	/** @return The type of the output messages */
	protected String getOutputMsgType () {
		return OUTPUT_MSG_TYPE;
	}

	/** The type of the messages containing statistics */
	public static final String STATS_MSG_TYPE = "DFSstats";

	/** The time at which the DFS procedure has finished*/
	private long finalTime = Long.MIN_VALUE;

	/** When parsing the constraints, whether to take into account variables with no specified owners */
	private final boolean withSharedVars;

	/** Message class used for the output of the protocol 
	 * @param <V> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public static class MessageDFSoutput < V extends Addable<V>, U extends Addable<U> > 
	extends MessageWith2Payloads< String[], DFSview<V, U> > {

		/** Empty constructor used for externalization */
		public MessageDFSoutput () { }
		
		/** Constructor 
		 * @param var 	the variable
		 * @param view	the variable's view of the DFS
		 */
		public MessageDFSoutput (String var, DFSview<V, U> view) {
			super (OUTPUT_MSG_TYPE, new String[]{var}, view);
		}

		/** Constructor 
		 * @param vars 	the variables
		 * @param view 	the variables' view of the DFS
		 */
		public MessageDFSoutput (String[] vars, DFSview<V, U> view) {
			super (OUTPUT_MSG_TYPE, vars, view);
		}
		
		/** Constructor 
		 * @param type 	the type of this message
		 * @param var	the variable
		 * @param view the variable's view of the DFS
		 */
		public MessageDFSoutput (String type, String var, DFSview<V, U> view) {
			super (type, new String[]{var}, view);
		}

		/** Constructor 
		 * @param type 	the type of this message
		 * @param vars	the variables
		 * @param view 	the variables' view of the DFS
		 */
		public MessageDFSoutput (String type, String[] vars, DFSview<V, U> view) {
			super (type, vars, view);
		}

		/** @return the first variable */
		public String getVar () {
			return getPayload1()[0];
		}
		
		/** @return all the variables */
		public String[] getVars () {
			return getPayload1();
		}

		/** @return the variables' view of the DFS */
		public DFSview<V, U> getNeighbors () {
			return getPayload2();
		}
		
		/** @see MessageWith2Payloads#toString() */
		@Override
		public String toString () {
			return "Message(type = `" + type + "')\n\t vars = " + Arrays.toString(this.getVars()) + "\n\t DFSview: " + this.getNeighbors();
		}
	}

	/** Interface for heuristics used to choose a variable's next child from its list of open neighbors 
	 * 
	 * All heuristic classes should have a constructor that takes in a DCOPProblemInterface describing the agent's problem 
	 * and an Element describing the parameters of the heuristic. 
	 * If the heuristic also implements IncomingMsgPolicyInterface, it will be automatically registered to the agent's queue. 
	 */
	public static interface NextChildChoiceHeuristic {

		/** Chooses an open neighbor as the next child for a variable, removes it from the list of open neighbors and returns it
		 * @param var 				the variable whose next child must be chosen
		 * @param dfsView 			the current incomplete view that this variable has of its DFS neighbors
		 * @param openNeighbors 	the list of open neighbors for a variable
		 * @return the neighbor chosen to become the variable's next child; \c null if not enough information is available yet to choose it
		 */
		public String popNextChild (String var, DFSview<?, ?> dfsView, Collection<String> openNeighbors);

	}

	/** A DFS heuristic based on a ScoringHeuristic that does not require message exchange between agents
	 * @param <S> the type used for the scores
	 */
	public static class BlindScoringHeuristic < S extends Comparable<S> & Serializable > implements NextChildChoiceHeuristic {

		/** The ScoringHeuristic used */
		protected ScoringHeuristic<S> heuristic;

		/** Constructor
		 * @param heuristic 	The ScoringHeuristic used
		 */
		public BlindScoringHeuristic (ScoringHeuristic<S> heuristic) {
			this.heuristic = heuristic;
		}

		/** Constructor
		 * @param problem 	the agent's problem
		 * @param params 	the parameters
		 * @throws ClassNotFoundException 		if the ScoringHeuristic class is not found
		 * @throws NoSuchMethodException 		if the ScoringHeuristic does not have a constructor with the signature (DCOPProblemInterface, Element)
		 * @throws InstantiationException 		if the ScoringHeuristic is abstract
		 * @throws IllegalAccessException 		if the ScoringHeuristic constructor is not accessible
		 * @throws InvocationTargetException 	if the ScoringHeuristic constructor throws an exception
		 */
		@SuppressWarnings("unchecked")
		public BlindScoringHeuristic (DCOPProblemInterface<?, ?> problem, Element params) 
		throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

			// Parse the ScoringHeuristic to be used
			if (params == null) 
				this.heuristic = (ScoringHeuristic<S>) new VarNameHeuristic (problem, null);
			else {
				Element elmt = params.getChild("scoringHeuristic");
				if (elmt == null) 
					this.heuristic = (ScoringHeuristic<S>) new VarNameHeuristic (problem, null);
				else {
					Class< ? extends ScoringHeuristic<S> > heuristicClass = (Class<? extends ScoringHeuristic<S>>) Class.forName(elmt.getAttributeValue("className"));
					Constructor< ? extends ScoringHeuristic<S> > constructor = heuristicClass.getConstructor(DCOPProblemInterface.class, Element.class);
					this.heuristic = constructor.newInstance(problem, elmt);
				}
			}
		}

		/** @see frodo2.algorithms.varOrdering.dfs.DFSgeneration.NextChildChoiceHeuristic#popNextChild(java.lang.String, DFSview, java.util.Collection) */
		public String popNextChild(String var, DFSview<?, ?> dfsView, Collection<String> openNeighbors) {

			if (openNeighbors.isEmpty()) 
				return null;

			// Find the open neighbor with the highest score according to the heuristic
			Map<String, S> scores = this.heuristic.getScores();
			Comparable<S> maxScore = new Comparable<S> () { public int compareTo(S o) { return -1; } }; // -infinity
			String next = null;
			for (String neighbor : openNeighbors) {
				S otherScore = scores.get(neighbor);
				assert otherScore != null : "The heuristic " + this.heuristic.getClass() + " doesn't know the score for variable " + neighbor + "; known scores: " + scores;
				if (maxScore.compareTo(otherScore) < 0) {
					maxScore = otherScore;
					next = neighbor;
				}
			}

			openNeighbors.remove(next);
			return next;
		}

	}

	/** Selects the next child as the one that has the highest score 
	 * @param <S> the type used for the scores
	 */
	public static class ScoreBroadcastingHeuristic < S extends Comparable<S> & Serializable > extends BlindScoringHeuristic<S> implements IncomingMsgPolicyInterface<String> {

		/** The type of messages containing the scores of given variables */
		static final String SCORE_MSG_TYPE = "HeuristicScores";

		/** The type of messages containing the scores of given variables */
		static final String SCORE_SINGLE_VAR_MSG_TYPE = "HeuristicScoreSingleVar";

		/** The problem */
		private DCOPProblemInterface<?, ?> problem;

		/** Whether the execution of the algorithm has started */
		private boolean started = false;

		/** For each variable owned by this agent, the set of agents that own a variable connected to this variable */
		private Map< String, Collection<String> > neighborAgents;

		/** For each neighboring agent, the set of my variables it knows */
		private HashMap< String, HashSet<String> > knownVars = new HashMap< String, HashSet<String> > ();

		/** For each known variable, its score */
		private Map<String, S> scores;

		/** The queue used to exchange information about scores */
		private Queue queue;

		/** Constructor
		 * @param problem 		description of the agent's problem
		 * @param params 		the heuristic parameters
		 * @throws Exception 	if the constructor of the superclass throws an exception
		 */
		public ScoreBroadcastingHeuristic (DCOPProblemInterface<?, ?> problem, Element params) throws Exception {
			super (problem, params);
			this.problem = problem;
		}

		/** Constructor
		 * @param heuristic 	The ScoringHeuristic used
		 * @param neighborAgents	for each variable owned by this agent, the set of agents that own a variable connected to this variable
		 */
		public ScoreBroadcastingHeuristic (ScoringHeuristic<S> heuristic, Map< String, Collection<String> > neighborAgents) {
			super (heuristic);
			this.neighborAgents = neighborAgents;
			this.buildKnownVars(neighborAgents);
		}

		/** Parses the problem */
		private void init () {
			this.scores = super.heuristic.getScores();
			if (this.problem != null) 
				this.neighborAgents = problem.getAgentNeighborhoods();
			this.buildKnownVars(neighborAgents);
			this.started = true;
		}

		/** Builds the knownVars map
		 * @param neighborAgents 	for each internal variable, its collection of neighboring agents
		 */
		private void buildKnownVars (Map< String, Collection<String> > neighborAgents) {

			// Loop over my variables
			for (Map.Entry< String, Collection<String> > entry : neighborAgents.entrySet()) {
				String myVar = entry.getKey();

				// Loop over the neighboring agents
				for (String agent : entry.getValue()) {

					HashSet<String> myVars = this.knownVars.get(agent);
					if (myVars == null) {
						myVars = new HashSet<String> ();
						this.knownVars.put(agent, myVars);
					}
					myVars.add(myVar);
				}
			}
		}

		/** @see frodo2.algorithms.varOrdering.dfs.DFSgeneration.BlindScoringHeuristic#popNextChild(java.lang.String, DFSview, java.util.Collection) */
		@Override
		public String popNextChild(String var, DFSview<?, ?> unused, Collection<String> openNeighbors) {

			// Parse the problem if this hasn't been done yet
			if (! this.started) 
				init();

			if (openNeighbors.isEmpty()) 
				return null;

			// Find the neighbor(s) with the highest score
			Comparable<S> maxScore = new Comparable<S> () { public int compareTo(S o) { return -1; } }; // -infinity
			String next = null;
			for (String neighbor : openNeighbors) {
				S otherScore = this.scores.get(neighbor);

				if (otherScore == null) // we don't know yet this variable's score
					return null;

				if (maxScore.compareTo(otherScore) < 0) {
					maxScore = otherScore;
					next = neighbor;
				}
			}

			openNeighbors.remove(next);
			return next;
		}

		/** @see IncomingMsgPolicyInterface#getMsgTypes() */
		public Collection<String> getMsgTypes() {
			ArrayList<String> types = new ArrayList<String> (3);
			types.add(SCORE_MSG_TYPE);
			types.add(SCORE_SINGLE_VAR_MSG_TYPE);
			types.add(START_MSG_TYPE);
			types.add(FINISH_MSG_TYPE);
			return types;
		}

		/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {

			String msgType = msg.getType();

			if (msgType.equals(FINISH_MSG_TYPE)) {
				this.reset();
				return;
			}

			// Parse the problem if this hasn't been done yet
			if (! this.started) 
				init();

			if (msgType.equals(START_MSG_TYPE)) {

				// Go through the list of neighboring agents
				for (Map.Entry< String, HashSet<String> > entry : this.knownVars.entrySet()) {
					String agent = entry.getKey();
					HashSet<String> vars = entry.getValue();

					// If there is a single variable, send a ScoreMsg
					if (vars.size() == 1) {
						String var = vars.iterator().next();
						this.queue.sendMessage(agent, new ScoreMsg<S> (var, this.scores.get(var)));
						continue;
					}

					// Construct the HashMap
					HashMap<String, S> scores = new HashMap<String, S> ();
					for (String var : vars) 
						scores.put(var, this.scores.get(var));

					this.queue.sendMessage(agent, new ScoresMsg<S> (scores));
				}

				this.neighborAgents.clear();
			}

			else if (msgType.equals(SCORE_MSG_TYPE)){ // this is a message containing the scores of variables

				ScoresMsg<S> msgCast = (ScoresMsg<S>) msg;
				this.scores.putAll(msgCast.scores);
			}

			else if (msgType.equals(SCORE_SINGLE_VAR_MSG_TYPE)){ // this is a message containing the score of a single variable

				ScoreMsg<S> msgCast = (ScoreMsg<S>) msg;
				this.scores.put(msgCast.var, msgCast.score);
			}
		}

		/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
		public void setQueue(Queue queue) {
			this.queue = queue;
		}

		/** Resets all problem-dependent fields (except the problem itself) */
		private void reset () {
			this.scores = null;
			this.neighborAgents = null;
			this.started = false;
		}

	}

	/** Constructor 
	 * @param problem 		the problem
	 * @param heuristic 	the heuristic
	 */
	public DFSgeneration (DCOPProblemInterface<V, U> problem, NextChildChoiceHeuristic heuristic) {
		this.problem = problem;
		this.heuristic = heuristic;
		this.withSharedVars = false;
	}

	/** Constructor
	 * @param problem 					this agent's problem
	 * @param parameters 				the parameters for DFSgeneration
	 * @throws ClassNotFoundException 	if the heuristic class is unknown
	 */
	@SuppressWarnings("unchecked")
	public DFSgeneration (DCOPProblemInterface<V, U> problem, Element parameters) throws ClassNotFoundException {
		this.problem = problem;

		// Create the heuristic
		Element heuristicParams = parameters.getChild("dfsHeuristic");
		if (heuristicParams == null) 
			this.heuristic = new BlindScoringHeuristic<String> (new VarNameHeuristic (problem, null));
		else 
			this.heuristic = this.createHeuristic((Class<? extends NextChildChoiceHeuristic>) Class.forName(heuristicParams.getAttributeValue("className")), heuristicParams);
		
		this.withSharedVars = Boolean.parseBoolean(parameters.getAttributeValue("withSharedVars"));
	}

	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public DFSgeneration (Element parameters, DCOPProblemInterface<V, U> problem)  {
		this.problem = problem;

		// Parse the number of variables in the problem
		this.totalNbrVars = problem.getNbrVars();

		dfsViews = new HashMap< String, DFSview<V, U> > ();

		if(parameters != null) dotRendererClass = parameters.getAttributeValue("DOTrenderer");
		if (this.dotRendererClass == null) 
			this.dotRendererClass = "";
		
		this.withSharedVars = false;
	}

	/** Manual constructor for the "statistics gatherer" mode
	 * @param problem 	the overall problem
	 */
	public DFSgeneration (DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.totalNbrVars = problem.getVariables().size();
		dfsViews = new HashMap< String, DFSview<V, U> > ();
		this.withSharedVars = false;
	}

	/** Empty constructor 
	 * @param withSharedVars 	When parsing the constraints, whether to take into account variables with no specified owners
	 */
	protected DFSgeneration (boolean withSharedVars) {
		this.withSharedVars = withSharedVars;
	}

	/** Parses the problem */
	protected void init () {

		this.owners = problem.getOwners();
		this.openNeighbors = new HashMap< String, Collection<String> > (problem.getNeighborhoods());
		dfsViews = new HashMap< String, DFSview<V, U> > (openNeighbors.size());

		for (String variable : openNeighbors.keySet()) {
			init(variable);			
		}	
		this.started = true;
	}

	/** Parses the problem for only one variable 
	 * @param var the specified variable
	 */
	protected void init(String var){

		this.openNeighbors.put(var, problem.getNeighborVars(var));

		// Send to myself a message that resets the DFS output in other modules if they previously received one
		if (this.sentOutputs.remove(var)) 
			this.queue.sendMessageToSelf(new MessageDFSoutput<V, U> (this.getOutputMsgType(), var, null));
		
		dfsViews.put(var, new DFSview<V, U> (var));
	}

	/** @return the DFS */
	public HashMap< String, DFSview<V, U> > getDFS () {
		return dfsViews;
	}

	/** Instantiates a heuristic using reflection, based on the class name and the XCSP problem description
	 * @param heuristicClass 	the class of the heuristic
	 * @param heuristicParams 	the XML description of the heuristic
	 * @return 					a new instance of the corresponding heuristic
	 */
	private NextChildChoiceHeuristic createHeuristic (Class<? extends NextChildChoiceHeuristic> heuristicClass, Element heuristicParams) {

		Constructor<? extends NextChildChoiceHeuristic> constructor;
		try {
			constructor = heuristicClass.getConstructor(DCOPProblemInterface.class, Element.class);
		} catch (NoSuchMethodException e) {
			System.err.println("The heuristic " + heuristicClass + " does not have a constructor that takes in a ProblemInterface and and Element");
			return null;
		}

		try {
			return constructor.newInstance(problem, heuristicParams);
		} catch (Exception e) {
			System.err.println("Failed to instantiate the heuristic " + heuristicClass);
			e.printStackTrace();
			return null;
		}
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection <String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList <String> (5);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(this.getRootVarMsgType());
		msgTypes.add(this.getChildMsgType());
		msgTypes.add(this.getPseudoMsgType());
		msgTypes.add(FINISH_MSG_TYPE);
		return msgTypes;
	}

	/** The algorithm
	 *
	 * The algorithm is triggered by the receipt of a messages of type LeaderElectionMaxID.OUTPUT_MSG_TYPE, telling the agent whether 
	 * each variable is a root. The actual implementation corresponds to the one described in the P-DPOP paper published in WI-IAT 2008. 
	 * @param msg the message received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {

		String msgType = msg.getType();

		if (msgType.equals(STATS_MSG_TYPE)) { // statistics message

			// If we receive this message, it means we are actually running in "statistics gatherer" mode

			// Retrieve and store the information from the message
			MessageDFSoutput<V, U> msgCast = (MessageDFSoutput<V, U>) msg;

			// If we already know the relationships for this variable, first reset all relationships for the corresponding component
			String var = msgCast.getVar();
			if (this.dfsViews.containsKey(var)) 
				this.resetComponent(var);

			dfsViews.put(var, msgCast.getNeighbors());

			long time = queue.getCurrentMessageWrapper().getTime();
			if(finalTime < time)
				finalTime = time;

			// If all information has been received from all variables, print it
			if (!silent && dfsViews.size() >= this.totalNbrVars) {
				if(dotRendererClass.equals("")) {
					System.out.println("Chosen DFS tree:");
					System.out.println(dfsToString());
				}
				else {
					try {
						Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("DFS Tree", dfsToString());
					} 
					catch(Exception e) {
						System.out.println("Could not instantiate given DOT renderer class: " + this.dotRendererClass);
						e.printStackTrace();
					}
				}
			}

			return;
		}

		else if (msgType.equals(FINISH_MSG_TYPE)) {
			this.reset();
			return;
		}

		// Parse the problem if it has not been done yet
		if (! this.started) 
			init();

		//		System.out.println(queue + " receives a message at " + queue.getCurrentTime()/1000000);

		if (msgType.equals(this.getRootVarMsgType())) { // this is the output of the leader election protocol
			// Cast the message to its true type
			LeaderElectionMaxID.MessageLEoutput<?> msgCast = (LeaderElectionMaxID.MessageLEoutput<?>) msg;
			
			Serializable rootID = msgCast.getLeader();
			if (this.partialPaths.get(rootID) == null) 
				this.partialPaths.put(rootID, new LinkedList<String> ());

			if (msgCast.getFlag()) { // the sender variable is a root

				// Get the sender variable
				String root = msgCast.getSender();
				
				LinkedList<String> partialPath = this.partialPaths.get(rootID);
				if (partialPath.isEmpty()) {
					partialPath.add(root);

//					System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + partialPath);
				}
				
				// Restart if necessary
				if(!openNeighbors.containsKey(root)) init(root);
				
				DFSview<V, U> myDFSview = dfsViews.get(root);

				//				System.out.println("" + root + ":\t I am a root");

				// Choose one of its neighbors as its first child
				Collection<String> openList = openNeighbors.get(root);
				if (openList.isEmpty()) { // isolated variable; send a message with its categories of neighbors
					openNeighbors.remove(root);
					this.parseSpaces(root, myDFSview);
					queue.sendMessageToSelf(new MessageDFSoutput<V, U> (this.getOutputMsgType(), root, myDFSview));
					this.sentOutputs.add(root);
					queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageDFSoutput<V, U> (STATS_MSG_TYPE, root, myDFSview));
					this.sendAdditionalDFSoutput(rootID, root);
				}

				else  // the variable has at least one open neighbor
					this.sendDownCHILDtoken (rootID, root, openList, myDFSview, msg);
			}
		}

		else if (msgType.equals(this.getChildMsgType()) || msgType.equals(this.getPseudoMsgType())) { // this message is either a CHILD token or a PSEUDO token			
			// Extract the information from the message
			MessageWith3Payloads <String, String, Serializable> msgCast = (MessageWith3Payloads <String, String, Serializable>) msg;
			String sender = msgCast.getPayload1();
			String myVar = msgCast.getPayload2();
			Serializable rootID = msgCast.getPayload3();

			// Restart if necessary
			if(!openNeighbors.containsKey(myVar)) init(myVar);

			// Get the lists of open and closed neighbors for this variable
			Collection <String> openList = openNeighbors.get(myVar);
			DFSview<V, U> myDFSview;
			myDFSview = dfsViews.get(myVar);

			processAdditionalMsgInformation(msg, myVar, myDFSview);

			if (msgType.equals(this.getChildMsgType())) { // this message is a CHILD token

				if (myDFSview.getParent() == null && myDFSview.getChildren().isEmpty()) { // this is the first CHILD token received for this variable

					// Mark sender as parent
					openList.remove(sender);
					myDFSview.setParent(sender, this.owners.get(sender));
					
					// Add myself to the current partial path
					LinkedList<String> partialPath = this.partialPaths.get(rootID);
					if (partialPath == null) {
						partialPath = new LinkedList<String> ();
						this.partialPaths.put(rootID, partialPath);
					}
					if (! myVar.equals(partialPath.peekLast())) 
						partialPath.add(myVar);

//					System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + partialPath);
					
					//					System.out.println("" + myVar + ":\t Got first CHILD token from parent " + sender);
				}

				else if (openList.contains(sender)) { // CHILD token received from open neighbor, which becomes a pseudo-child

					// Update the current partial path
					LinkedList<String> partialPath = this.partialPaths.get(rootID);
					if (partialPath.peekLast().equals(myVar)) // I own the pseudo-child
						partialPath.removeLast();
					else 
						partialPath.add(sender);

//					System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + partialPath);

					// Mark sender as pseudo-child
					openList.remove(sender);
					myDFSview.addPseudoChild(sender, partialPath);

					//					System.out.println("" + myVar + ":\t Got CHILD token from pseudo-child " + sender);

					// Reply with a PSEUDO token
					String owner = this.owners.get(sender);
					queue.sendMessage(owner, new PSEUDOmsg (this.getPseudoMsgType(), myVar, sender, rootID));

					//					System.out.println("" + myVar + ":\t Replying with a PSEUDO token to " + sender);

					return;
					
				} else if (! sender.equals(myDFSview.getParent())) {
					// Else, the CHILD token was sent by one of my children, which means it finished traversing its subtree;
					// I must now forward the token to my next open neighbor (which becomes my child), if any.
					
					// Remove all variables following the sender in the current partial path
					LinkedList<String> partialPath = this.partialPaths.get(rootID);
					for (ListIterator<String> iter = partialPath.listIterator(); iter.hasNext(); ) {
						String var2 = iter.next();
						if (var2.equals(sender)) {
							iter.remove();
							while (iter.hasNext()) {
								iter.next();
								iter.remove();
							}
						}
					}

//					System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + partialPath);
				}
			}

			else if (msgType.equals(this.getPseudoMsgType())) { // this message is a PSEUDO token

				// I just sent a CHILD token to this agent and marked it as a child, 
				// but it replied with a PSEUDO token, which means it is actually a pseudo-parent of mine
				// First cancel the marking of this agent as a child
				myDFSview.removeChild(sender);
				
				// Remove variables from the current partial path
				LinkedList<String> partialPath = this.partialPaths.get(rootID);
				if (partialPath.peekLast().equals(sender)) 
					partialPath.removeLast();

//				System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + partialPath);

				// Then mark sender as pseudo-parent
				myDFSview.addPseudoParent(sender);

				//				System.out.println("" + myVar + ":\t Got PSEUDO token from pseudo-parent " + sender);
			}

			// Forward the token to the next open neighbor
			if (! this.sendDownCHILDtoken(rootID, myVar, openList, myDFSview, msg)) { // no more open neighbors for this variable

				// All of this variable's neighbors are closed; send message with its DFSview
				openNeighbors.remove(myVar);
				this.parseSpaces(myVar, myDFSview);
				queue.sendMessageToSelf(new MessageDFSoutput<V, U> (this.getOutputMsgType(), myVar, myDFSview));
				this.sentOutputs.add(myVar);
				queue.sendMessage(AgentInterface.STATS_MONITOR, new MessageDFSoutput<V, U> (STATS_MSG_TYPE, myVar, myDFSview));

				this.sendAdditionalDFSoutput(rootID, myVar);

				// Return a CHILD token to the parent of this variable (if it has one)
				String parent = myDFSview.getParent();
				if (parent != null) {
					String owner = this.owners.get(parent);
					queue.sendMessage(owner, this.makeChildToken(rootID, myVar, parent, openList));
					
					// Remove myself from the current partial path
					LinkedList<String> partialPath = this.partialPaths.get(rootID);
					if (partialPath != null && !partialPath.isEmpty() && partialPath.peekLast().equals(myVar)) 
						partialPath.removeLast();

//					System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + this.partialPaths.get(rootID));

					//					System.out.println("" + myVar + ":\t Returning CHILD token to parent " + parent);
				}
			}
		} 
	}

	/** Parses and records the spaces for the input variable
	 * @param myVar 		the variable
	 * @param myDFSview 	the variable's view of the DFS
	 */
	private void parseSpaces(String myVar, DFSview<V, U> myDFSview) {
		
		HashSet<String> varsBelow = new HashSet<String> (myDFSview.getChildren());
		varsBelow.addAll(myDFSview.getAllPseudoChildren());
		myDFSview.spaces = this.problem.getSolutionSpaces(myVar, this.withSharedVars, varsBelow);
	}

	/** Resets all entries in this.dfsViews corresponding to variables in the component of the input variable
	 * @param var 	a variable in the component to be reset
	 */
	private void resetComponent(String var) {

		// First find the root of this component
		String root = var;
		String parent = this.dfsViews.get(root).getParent();
		while (parent != null) {
			root = parent;
			parent = this.dfsViews.get(root).getParent();
		}

		// Iteratively remove all descendants of the root from this.dfsViews
		ArrayList<String> openList = new ArrayList<String> ();
		openList.add(root);
		while (! openList.isEmpty()) 
			openList.addAll(this.dfsViews.remove(openList.remove(0)).getChildren());
	}

	/** allows to process information included in messages which extend CHILDmsg 
	 * @param msg 		The message received
	 * @param var 		The variable concerned
	 * @param dfsView 	The dfsView of myVar
	 * */
	protected void processAdditionalMsgInformation(Message msg, String var, DFSview<V, U> dfsView)
	{
		//VOID;
	}

	/** makes the ChildToken Message with all the information required by the module 
	 * @param rootID 			The ID of the root
	 * @param var 				The variable concerned
	 * @param dest 				The destination variable
	 * @param openNeighbors 	The list of open neighbors of the current variable
	 * @return The CHILDmsg to be sent
	 * */
	protected CHILDmsg makeChildToken(Serializable rootID, String var, String dest, Collection<String> openNeighbors)
	{
		return new CHILDmsg (var, dest, rootID);
	}

	/** Use this method to send additional output from DFS generation.
	 * @param rootID 			The ID of the root
	 * @param myVar Variable name
	 */
	protected void sendAdditionalDFSoutput(Serializable rootID, String myVar)
	{
		//VOID
	}

	/** Attempts to choose the next child and sends it a CHILD token
	 * @param rootID 		the ID of the root
	 * @param var 			the variable whose next child must be chosen
	 * @param openList 		the list of open neighbors
	 * @param myDFSview 	the known dfsView for the input variable
	 * @param msg 			the message just received
	 * @return false if and only if no CHILD token was sent because the open list is empty; true otherwise
	 */
	protected boolean sendDownCHILDtoken(Serializable rootID, String var, Collection<String> openList, DFSview<V, U> myDFSview, Message msg) {

		String child = this.heuristic.popNextChild(var, myDFSview, openList);
		
		if (child == null) {
			if (openList.isEmpty()) 
				return false;
			
			else { // we cannot compute the next child yet; delay this decision
				queue.sendMessageToSelf(msg);
				return true;
			}
		}

		// Send a CHILD message and mark recipient as child
		String owner = this.owners.get(child);
		queue.sendMessage(owner, this.makeChildToken(rootID, var, child, openList));
		myDFSview.addChild(child);
		
		// Add the child to the current partial path
		LinkedList<String> partialPath = this.partialPaths.get(rootID);
		if (partialPath == null) {
			partialPath = new LinkedList<String> ();
			this.partialPaths.put(rootID, partialPath);
		}
		partialPath.add(child);

//		System.out.println(this.problem.getAgent() + "\t" + rootID + "\t" + this.partialPaths.get(rootID));

		//		System.out.println("" + var + ":\t Sending CHILD token to child " + child);
		
		return true;
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue) */
	@SuppressWarnings("unchecked")
	public void setQueue(Queue queue) {
		this.queue = queue;
		if (this.heuristic instanceof IncomingMsgPolicyInterface) 
			queue.addIncomingMessagePolicy((IncomingMsgPolicyInterface<String>) this.heuristic);
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(STATS_MSG_TYPE, this);
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent  = silent;
	}

	/** @return a DOT-formated representation of the dfs */
	public String dfsToString() {
		return dfsToString(this.dfsViews);
	}

	/** Prints the input dfs in DOT format
	 * @param dfs for each variable, a map associating a list of neighbors to each type of relationship
	 * @return a String representation of the DFS in DOT format
	 */
	public static < V extends Addable<V>, U extends Addable<U> > String dfsToString (Map< String, DFSview<V, U> > dfs) {
		StringBuilder out = new StringBuilder ("digraph {\n\tnode [shape = \"circle\"];\n\n");

		// For each variable:
		for (Map.Entry< String, DFSview<V, U> > entry : dfs.entrySet()) {
			String var = entry.getKey();
			DFSview<V, U> dfsView = entry.getValue();
			assert dfsView != null : "Empty DFSview for variable " + var;

			// First print the variable
			out.append("\t" + var + " [style=\"filled\"];\n");

			// Print the edge with the parent, if any
			String parent = dfsView.getParent();
			if (parent != null) {
				out.append("\t" + parent + " -> " + var + ";\n");
				
				assert dfs.get(parent).getChildren().contains(var) : 
					parent + " is a parent of " + var + " but " + var + " is not a child of " + parent;
				assert ! dfsView.getChildren().contains(parent) : 
					parent + " is both a parent and a child of " + var;
				assert ! dfsView.getPseudoParents().contains(parent) : 
					parent + " is both a parent and a pseudo-parent of " + var;
				assert ! dfsView.getAllPseudoChildren().contains(parent) : 
					parent + " is both a parent and a pseudo-child of " + var;
			}

			// Print the edges with the pseudo-parents, if any
			for (String pseudo : dfsView.getPseudoParents()) {
				out.append("\t" + pseudo + " -> " + var + " [style = \"dashed\" arrowhead = \"none\" weight=\"0.5\"];\n");
				
				assert dfs.get(pseudo).getAllPseudoChildren().contains(var) : 
					pseudo + " is a pseudo-parent of " + var + " but " + var + " is not a pseudo-child of " + pseudo;
				assert ! dfsView.getChildren().contains(pseudo) : 
					pseudo + " is both a pseudo-parent and a child of " + var;
				assert ! dfsView.getAllPseudoChildren().contains(pseudo) : 
					pseudo + " is both a pseudo-parent and a pseudo-child of " + var;
			}
			
			// Check the children, if any
			for (String child : dfsView.getChildren()) {
				assert var.equals(dfs.get(child).getParent()) : 
					child + " is a child of " + var + " but " + var + " is not a parent of " + child;
				assert ! dfsView.getAllPseudoChildren().contains(child) : 
					child + " is both a child and a pseudo-child of " + var;
			}

			// Check the pseudo-children, if any
			for (String pseudo : dfsView.getAllPseudoChildren()) {
				assert dfs.get(pseudo).getPseudoParents().contains(var) : 
					pseudo + " is a pseudo-child of " + var + " but " + var + " is not a pseudo-parent of " + pseudo;
			}

			out.append("\n");
		}

		out.append("}");
		return out.toString();
	}

	/** @see StatsReporter#reset() */
	public void reset () {
		this.openNeighbors = null;
		this.owners = null;
		this.dfsViews = new HashMap< String, DFSview<V, U> > ();
		this.sentOutputs.clear();
		this.started = false;

		// Useful only in stats gatherer mode
		this.totalNbrVars = this.problem.getVariables().size();
	}

	/**
	 * Returns the time at which the DFS phase has finished, 
	 * determined by looking at the timestamp of the stat messages
	 * 
	 * @author Brammert Ottens, 22 feb 2010
	 * @return the time at which the DFS phase has finished
	 */
	public long getFinalTime() {
		return finalTime;
	}

}
