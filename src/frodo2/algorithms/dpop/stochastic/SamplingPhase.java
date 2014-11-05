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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** Algorithm that generates samples for the random variables in the problem
 * 
 * The samples for a given random variable are chosen by the Lowest Common Ancestor of all decision variables 
 * responsible for enforcing a constraint involving that random variable. 
 * 
 * @author Thomas Leaute
 * @param <V> the type used for random variable values
 * @param <U> the type used for probabilities
 * @todo Improve garbage collection. 
 */
public class SamplingPhase < V extends Addable<V>, U extends Addable<U> > extends LowestCommonAncestors implements StatsReporter {
	
	/** The type of the start message */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** The type of the messages telling what random variables should be projected out at given decision variable */
	public static final String RAND_VARS_PROJ_MSG_TYPE = "Where to project random variables";
	
	/** Message telling what random variables should be projected out at given decision variable */
	public static class RandVarsProjMsg extends MessageWith2Payloads< String, HashSet<String> > {

		/** Empty constructor used for externalization */
		public RandVarsProjMsg () { }

		/** Constructor 
		 * @param variable 	the decision variable
		 * @param randVars 	the random variables that should be projected out at this decision variable
		 */
		public RandVarsProjMsg(String variable, HashSet<String> randVars) {
			super(RAND_VARS_PROJ_MSG_TYPE, variable, randVars);
		}
		
		/** @return the decision variable */
		public String getVariable () {
			return super.getPayload1();
		}
		
		/** @return the random variables */
		public HashSet<String> getRandVars () {
			return super.getPayload2();
		}
	}
	
	/** The problem */
	protected DCOPProblemInterface<V, U> problem;
	
	/** Whether the execution of the algorithm has started */
	protected boolean started = false;
		
	/** The number of samples for each random variable */
	protected int nbrSamples;
	
	/** For each random variable, its non-sampled probability law */
	protected HashMap< String, UtilitySolutionSpace<V, U> > probLaws = new HashMap< String, UtilitySolutionSpace<V, U> > ();

	/** The total number messages to expect in "statistics gatherer" mode */
	private int nbrStatsMsgs;

	/** For every variable this agent owns, its view of the DFS
	 * 
	 * Used only in stats gatherer mode. 
	 */
	private HashMap< String, DFSview<V, ?> > relationships;

	/** Whether the stats reporter should print its stats */
	private boolean silent = false;
	
	/** Renderer to display DOT code */
	private String dotRendererClass;

	/** For each variable, the random variables it is linked to (only used in stats gatherer mode) */
	private HashMap< String, HashSet<String> > randVars;
		
	/** For each variable, the random variables it must project out (only used in stats gatherer mode) */
	private HashMap< String, HashSet<String> > randVarsProj;
	
	/** Where the random variables should be projected out */
	protected enum WhereToProject {
		/** Projection at the leaves */
		LEAVES,
		/** Projection at the lcas */
		LCAS,
		/** Projection at the roots */
		ROOTS;
	}
	
	/** Where the random variables should be projected out */
	protected WhereToProject proj;
		
	/** Nullary constructor */
	protected SamplingPhase () { }
	
	/** Constructor
	 * @param problem 		this agent's problem
	 * @param parameters 	the parameters for SamplingPhase
	 */
	public SamplingPhase (DCOPProblemInterface<V, U> problem, Element parameters) {
		this.problem = problem;
		this.nbrSamples = Integer.parseInt(parameters.getAttributeValue("nbrSamples"));
		
		// Parse and record where to project
		String whereToProj = parameters.getAttributeValue("whereToProject");
		if (whereToProj != null) {
			if (whereToProj.equals("leaves")) {
				this.proj = WhereToProject.LEAVES;
			} else if (whereToProj.equals("lcas")) {
				this.proj = WhereToProject.LCAS;
			} else if (whereToProj.equals("roots")) {
				this.proj = WhereToProject.ROOTS;
			} else 
				System.err.println("Incorrect value `" + whereToProj + "' for the option `whereToProject' of module SamplingPhase");
		}
	}
	
	/** Parses the problem */
	protected void init () {
		super.owners = problem.getOwners();
		
		// Initialize the information about each variable
		for (String var : problem.getMyVars()) 
			super.infos.put(var, new NodeInfo (new HashSet<String> ()));
		
		// Parse all non-sampled probability spaces
		List< ? extends UtilitySolutionSpace<V, U> > probSpaces = problem.getProbabilitySpaces();
		for (UtilitySolutionSpace<V, U> probSpace : probSpaces) 
			this.probLaws.put(probSpace.getVariable(0), probSpace);
		
		this.started = true;
	}
	
	/** @see StatsReporter#reset() */
	public void reset () {
		super.owners = null;
		super.infos = new HashMap<String, NodeInfo> ();
		probLaws = new HashMap< String, UtilitySolutionSpace<V, U> > ();
		this.started = false;
		
		// Only used in stats gatherer mode
		this.nbrStatsMsgs = 2 * problem.getVariables().size(); // one DFS message and one RandVarsProjMsg message
		relationships = new HashMap< String, DFSview<V, ?> > ();
		this.randVarsProj = new HashMap< String, HashSet<String> > ();
		this.randVars = new HashMap< String, HashSet<String> > ();
		for (String agent : problem.getAgents()) 
			this.randVars.putAll(problem.getAnonymNeighborhoods(agent));
	}
	
	/** Constructor in stats gatherer mode
	 * @param problem 		the overall problem
	 * @param parameters 	parameters of the stats gatherer
	 */
	public SamplingPhase (Element parameters, DCOPProblemInterface<V, U> problem)  {
		this.problem = problem;
		
		// Parse the number of variables in the problem
		this.nbrStatsMsgs = 2 * problem.getNbrVars(); // one DFS message and one RandVarsProjMsg message
		
		relationships = new HashMap< String, DFSview<V, ?> > ();
		this.randVarsProj = new HashMap< String, HashSet<String> > ();
		
		// Record which variable is linked to which random variable
		this.randVars = new HashMap< String, HashSet<String> > ();
		for (String agent : problem.getAgents()) 
			this.randVars.putAll(problem.getAnonymNeighborhoods(agent));
		
		// Record where random variables should be projected out
		String where = parameters.getAttributeValue("whereToProject");
		if (where != null) {
			if (where.equals("roots")) {
				this.proj = WhereToProject.ROOTS;
			} else if (where.equals("lcas")) {
				this.proj = WhereToProject.LCAS;
			} else if (where.equals("leaves")) 
				this.proj = WhereToProject.LEAVES;
		}
		
		this.dotRendererClass = parameters.getAttributeValue("DOTrenderer");
		if (this.dotRendererClass == null) 
			this.dotRendererClass = "";
	}

	/** @see LowestCommonAncestors#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		Collection<String> types = super.getMsgTypes();
		types.add(START_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** @see LowestCommonAncestors#notifyIn(Message) */
	@Override
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(DFSgeneration.STATS_MSG_TYPE)) { // statistics message
			
			// If we receive this message, it means we are actually running in "statistics gatherer" mode
			
			// Retrieve and store the information from the message
			@SuppressWarnings("unchecked")
			MessageDFSoutput<V, ?> msgCast = (MessageDFSoutput<V, ?>) msg;
			relationships.put(msgCast.getVar(), msgCast.getNeighbors());
			
			// If all information has been received from all variables, print it
			if (--this.nbrStatsMsgs <= 0 && !silent) {
				if(dotRendererClass.length() == 0) {
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

			return;
		}
		
		else if (msgType.equals(RAND_VARS_PROJ_MSG_TYPE) && randVars != null) { // in stats gatherer mode
			
			RandVarsProjMsg msgCast = (RandVarsProjMsg) msg;
			this.randVarsProj.put(msgCast.getVariable(), msgCast.getRandVars());
			
			// If all information has been received from all variables, print it
			if (--this.nbrStatsMsgs <= 0 && !silent) {
				if(dotRendererClass.length() == 0) {
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

			return;
		}
		
		else if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this hasn't been done yet
		if (! this.started) 
			init();
		
		// Don't even compute the LCA if we are sampling at the leaves
		if (this.getClass().getName().equals(SamplingPhase.AtLeaves.class.getName())) 
			return;
		
		if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // DFS information about a variable
			
			// Retrieve the information from the message
			@SuppressWarnings("unchecked")
			MessageDFSoutput<V, U> msgCast = (MessageDFSoutput<V, U>) msg;
			String var = msgCast.getVar();
			DFSview<V, U> neighbors = msgCast.getNeighbors();
			HashSet<String> allChildren = new HashSet<String> (neighbors.getChildren());
			allChildren.addAll(neighbors.getAllPseudoChildren());
			
			// For each space that var is responsible for enforcing, add the random variables in the scope to var's flags
			HashSet<String> flags = new HashSet<String> ();
			for (UtilitySolutionSpace<V, U> space : neighbors.getSpaces()) {
				
				// Add all random variables in the scope to var's flags
				for (String randVar : space.getVariables()) 
					if (this.problem.isRandom(randVar)) 
						flags.add(randVar);
			}
			
			super.infos.get(var).addFlags(flags);
		}
		
		super.notifyIn(msg);
	}
	
	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		ArrayList <String> msgTypes = new ArrayList <String> (2);
		msgTypes.add(DFSgeneration.STATS_MSG_TYPE);
		msgTypes.add(RAND_VARS_PROJ_MSG_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent  = silent;
	}
	
	/** The version of the SamplingPhase that samples at the leaves
	 * @param <V> 	the type used for variable values
	 * @param <U> 	the type used for utility values
	 */
	public static class AtLeaves < V extends Addable<V>, U extends Addable<U> > extends SamplingPhase<V, U> {
		
		/** The set of random variables that have already been sampled */
		private HashSet<String> sampledVars = new HashSet<String> ();

		/** Constructor in statistics gatherer mode
		 * @param problem 		the overall problem
		 * @param parameters 	the parameters
		 */
		public AtLeaves (Element parameters, DCOPProblemInterface<V, U> problem) {
			super(parameters, problem);
			if (this.proj != null && this.proj != WhereToProject.LEAVES) 
				System.err.println("Warning! Incorrect value for option `whereToProject' for module SamplingPhase$AtLeaves being overridden with default value `leaves'");
			this.proj = WhereToProject.LEAVES;
		}

		/** Constructor 
		 * @param problem 		the problem
		 * @param parameters 	the parameters
		 */
		public AtLeaves (DCOPProblemInterface<V, U> problem, Element parameters) {
			super(problem, parameters);
			if (this.proj != null && this.proj != WhereToProject.LEAVES) 
				System.err.println("Warning! Incorrect value for option `whereToProject' for module SamplingPhase$AtLeaves being overridden with default value `leaves'");
			this.proj = WhereToProject.LEAVES;
		}
		
		/** @see SamplingPhase#init() */
		@SuppressWarnings("unchecked")
		@Override
		protected void init () {
			super.init();
			super.setFlags(problem.getAnonymNeighborhoods());
			
			// For each variable, sample its neighboring random variables 
			for (String var : problem.getMyVars()) {
				
				// Sample the probability law of each random variable connected to var (if not already sampled)
				HashSet<String> randVars = super.infos.get(var).myFlags;
				for (String randVar : randVars) {
					if (! this.sampledVars.contains(randVar)) { // not already sampled
						problem.setProbSpace(randVar, this.probLaws.get(randVar).sample(this.nbrSamples));
						this.sampledVars.add(randVar);
					}
				}

				// Send the message telling where to project random variables
				queue.sendMessageToSelf(new RandVarsProjMsg (var, randVars));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new RandVarsProjMsg (var, (HashSet<String>) randVars.clone()));
			}
		}
		
		/** @see SamplingPhase#notifyIn(Message) */
		@Override
		public void notifyIn (Message msg) {
			
			if (msg.getType().equals(DFS_MSG_TYPE)) {
				
				// Parse the problem if this hasn't been done yet
				if (! this.started) 
					init();
				
			} else 
				super.notifyIn(msg);
		}
	}
	
	/** The version of the SamplingPhase that samples at the lcas
	 * @param <V> 	the type used for variable values
	 * @param <U> 	the type used for utility values
	 */
	public static class AtLCAs < V extends Addable<V>, U extends Addable<U> > extends SamplingPhase<V, U> {
		
		/** The random variables in the problem */
		protected Collection<String> allRandVars;

		/** Where random variables are being projected out */
		protected WhereToProject whereToProject = WhereToProject.LCAS;
		
		/** For each decision variable, for each random variable, a set of proposed sample values */
		protected HashMap< String, HashMap< String, Map<V, Double> > > samples = new HashMap< String, HashMap< String, Map<V, Double> > > ();
		
		/** For each random variable, the final set of samples chosen */
		protected HashMap< String, Map<V, Double> > finalSamples = new HashMap< String, Map<V, Double> > ();

		/** Nullary constructor */
		protected AtLCAs () { }
		
		/** Constructor in statistics gatherer mode
		 * @param problem 		the overall problem
		 * @param parameters 	the parameters
		 */
		public AtLCAs (Element parameters, DCOPProblemInterface<V, U> problem) {
			this(parameters, problem, true);
		}

		/** Constructor in statistics gatherer mode
		 * @param problem 		the overall problem
		 * @param parameters 	the parameters
		 * @param parseProj 	whether to parse where random variables should be projected out
		 */
		protected AtLCAs (Element parameters, DCOPProblemInterface<V, U> problem, boolean parseProj) {
			super(parameters, problem);
			
			if (! parseProj) 
				return;
			
			if (this.proj != null && this.proj == WhereToProject.ROOTS) {
				System.err.println("Warning! Incorrect value `roots' for option `whereToProject' for module SamplingPhase$AtLCAs being overridden with default value `lcas'");
				this.proj = WhereToProject.LCAS;
			}
			if (this.proj == null) 
				this.proj = WhereToProject.LCAS;
		}

		/** Constructor 
		 * @param problem 		the problem
		 * @param parameters 	the parameters
		 */
		public AtLCAs (DCOPProblemInterface<V, U> problem, Element parameters) {
			super(problem, parameters);
			if (this.proj != null && this.proj == WhereToProject.ROOTS) {
				System.err.println("Warning! Incorrect value `roots' for option `whereToProject' for module SamplingPhase$AtLCAs being overridden with default value `lcas'");
				this.proj = WhereToProject.LCAS;
			}
			if (this.proj == null) 
				this.proj = WhereToProject.LCAS;
		}
		
		/** @see SamplingPhase#init() */
		@Override
		protected void init () {
			super.init();
			
			for (String var : super.infos.keySet()) 
				this.samples.put(var, new HashMap< String, Map<V, Double> > ());
			
			this.allRandVars = problem.getAnonymVars();
		}
		
		/** @see SamplingPhase#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		public void notifyIn (Message msg) {
			
			String msgType = msg.getType();
			
			if (msgType.equals(PHASE1_MSG_TYPE)) { // bottom-up message containing proposed samples
				
				// Parse the problem if this hasn't been done yet
				if (! this.started) 
					init();
				
				SamplesMsg1<V> msgCast = (SamplesMsg1<V>) msg;
				String var = msgCast.getDest();
				NodeInfo nodeInfo = this.infos.get(var);

				// Check if we have received the DFSoutput message for that node yet
				if (nodeInfo.allFlags == null) { // we must wait for the DFSoutput message
					nodeInfo.phase1msgs.add(msgCast);
					return;
				}
				
				// Record the proposed samples
				for (Map.Entry< String, Map<V, Double> > entry : msgCast.samples.entrySet()) {
					String randVar = entry.getKey();
					Map<V, Double> inProposed = entry.getValue();
					
					// Add the proposed samples to the ones that may have already been received
					HashMap< String, Map<V, Double> > mySamples = this.samples.get(var);
					Map<V, Double> proposed = mySamples.get(randVar);
					if (proposed != null) {
						mySamples.put(randVar, this.combineSamples(proposed, inProposed));
						
					} else // very first samples for this variable
						mySamples.put(randVar, inProposed);
				}
				
				super.notifyIn(msgCast);
				return;
			}
			
			else if (msgType.equals(PHASE2_MSG_TYPE)) { // top-down message containing samples
				
				// Parse the problem if this hasn't been done yet
				if (! this.started) 
					init();
				
				SamplesMsg2<V> msgCast = (SamplesMsg2<V>) msg;
				String node = msgCast.getNode();
				NodeInfo nodeInfo = this.infos.get(node);
				Set<String> flags = msgCast.getFlags();
				HashMap< String, Map<V, Double> > samples = msgCast.getSamples();
				
				// Add my own flags to the list of my lcas
				nodeInfo.lcas.addAll(nodeInfo.myFlags);
				
				// Remove from the list of my lcas the flags that are not in the set received from my parent
				for (Iterator<String> iter = nodeInfo.lcas.iterator(); iter.hasNext(); ) 
					if (! flags.contains(iter.next())) 
						iter.remove();
				
				// Record the samples
				this.finalSamples.putAll(samples);
				
				// Terminate phase 2
				terminatePhase2 (node, nodeInfo, flags, samples);

				return;
			}
			
			super.notifyIn(msg);
		}
		
		/** Merges two sample sets and then reduces the result
		 * @param samples1 	first sample set
		 * @param samples2 	second sample set
		 * @return the reduced combination of the two input sample sets
		 */
		protected Map<V, Double> combineSamples (Map<V, Double> samples1, Map<V, Double> samples2) {
			
			HashMap<V, Double> out = new HashMap<V, Double> (samples1);
			for (Map.Entry<V, Double> entry : samples2.entrySet()) {
				V val = entry.getKey();
				Double weight = out.get(val);
				if (weight == null) {
					out.put(val, entry.getValue());
				} else 
					out.put(val, weight + entry.getValue());
			}
			
			return out;
		}
		
		/** @see LowestCommonAncestors#newPhase1msg(java.lang.String, LowestCommonAncestors.NodeInfo) */
		@Override
		protected LCAmsg1 newPhase1msg (String node, NodeInfo nodeInfo) {
			
			// First propose samples for my random variables
			this.proposeSamples(node, nodeInfo);
			
			// Gather the samples to be sent to my parent
			HashMap< String, Map<V, Double> > sentSamples = new HashMap< String, Map<V, Double> > ();
			HashMap< String, Map<V, Double> > mySamples = this.samples.get(node);
			for (String randVar : nodeInfo.allFlags) 
				sentSamples.put(randVar, mySamples.get(randVar));
			
			return new SamplesMsg1<V> (node, nodeInfo.parent, sentSamples);
		}
		
		/** Sample all random variables linked to the current variable, and add them to the proposed samples for this variable
		 * @param var 		the current variable
		 * @param nodeInfo 	information about the current variable
		 */
		protected void proposeSamples (String var, NodeInfo nodeInfo) {
			
			HashMap< String, Map<V, Double> > mySamples = this.samples.get(var);
			
			for (String randVar : nodeInfo.myFlags) {
				
				// Get the proposed samples that may have already been received, and add new proposed samples
				Map<V, Double> proposed = mySamples.get(randVar);
				Map<V, Double> newProposed;
				UtilitySolutionSpace<V, U> probLaw = this.probLaws.get(randVar);
				assert probLaw != null : "Unknown probability distribution for variable `" + randVar + "'";
				if (proposed != null) {
					newProposed = this.combineSamples(probLaw.sample(this.nbrSamples), proposed);
				} else 
					newProposed = probLaw.sample(this.nbrSamples);

				mySamples.put(randVar, newProposed);
			}
		}

		/** @see LowestCommonAncestors#terminatePhase2(java.lang.String, LowestCommonAncestors.NodeInfo, java.util.Set) */
		@Override
		protected void terminatePhase2 (String node, NodeInfo nodeInfo, Set<String> pendingFlags) {
			this.terminatePhase2(node, nodeInfo, pendingFlags, null);
		}
		
		/** Sends messages to children and the output message
		 * @param node 					the current node
		 * @param nodeInfo 				information about the current node
		 * @param pendingFlags 			a set of flags whose lca has not yet been computed
		 * @param samplesFromParent 	the samples received from the parent
		 */
		@SuppressWarnings("unchecked")
		protected void terminatePhase2(String node, NodeInfo nodeInfo, Set<String> pendingFlags, HashMap< String, Map<V, Double> > samplesFromParent) {
			
			// If I am the root, first propose samples for my random variables
			if (samplesFromParent == null)
				this.proposeSamples(node, nodeInfo);
			
			// Combine the proposed samples for each random variable for which I am the lca
			this.chooseSamples(node, nodeInfo);
			
			// Go through the list of children and the flags received from them
			for (Map.Entry< String, HashSet<String> > entry : nodeInfo.childFlags.entrySet()) {
				String child = entry.getKey();
				HashSet<String> flags = entry.getValue();
				
				// Put together the samples, and only keep flags that are still pending  
				HashMap< String, Map<V, Double> > samples = new HashMap< String, Map<V, Double> > ();
				for (Iterator<String> iter = flags.iterator(); iter.hasNext(); ) {
					String randVar = iter.next();
					
					if (! pendingFlags.contains(randVar)) {
						iter.remove();
						samples.put(randVar, finalSamples.get(randVar));
					}
				}
				
				// Remove from the set of flags the ones for which I am the lca
				for (Iterator<String> iter = flags.iterator(); iter.hasNext(); ) {
					String randVar = iter.next();
					if (nodeInfo.lcas.contains(randVar)) {
						iter.remove();
						samples.put(randVar, finalSamples.get(randVar));
					}
				}
				
				// Send the set of flags to the child
				String owner = owners.get(child);
				queue.sendMessage(owner, new SamplesMsg2<V> (child, flags, samples));
			}
			
			// Sample the random variables
			for (String randVar : nodeInfo.lcas)
				problem.setProbSpace(randVar, finalSamples.get(randVar));
			if (samplesFromParent != null) 
				for (Map.Entry< String, Map<V, Double> > entry : samplesFromParent.entrySet()) 
					problem.setProbSpace(entry.getKey(), entry.getValue());
			
			// Send the message telling where to project random variables
			if (this.proj == this.whereToProject) { // project at lcas
				queue.sendMessageToSelf(new RandVarsProjMsg (node, nodeInfo.lcas));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new RandVarsProjMsg (node, (HashSet<String>) nodeInfo.lcas.clone()));
			}
			else if (this.proj == WhereToProject.LEAVES) { // project at leaves
				queue.sendMessageToSelf(new RandVarsProjMsg (node, nodeInfo.myFlags));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new RandVarsProjMsg (node, (HashSet<String>) nodeInfo.myFlags.clone()));
			}
		}
		
		/** Combines the proposed samples for each random variable for which the input variable is the lca
		 * @param var 		the current variable
		 * @param nodeInfo 	information about the current variable
		 */
		private void chooseSamples (String var, NodeInfo nodeInfo) {
			
			HashMap< String, Map<V, Double> > mySamples = this.samples.get(var);
			
			for (String randVar : nodeInfo.lcas) {
				Map<V, Double> proposed = mySamples.get(randVar);
				finalSamples.put(randVar, downSample(proposed));
			}
		}
		
		/** Down-samples the input sample set to make it have the proper size
		 * @param samples 	proposed samples
		 * @return new sample set of the appropriate size
		 * @todo Test this method separately? 
		 */
		private Map<V, Double> downSample (Map<V, Double> samples) {
			
			if (this.nbrSamples == 0) 
				return samples;
			
			// Compute the cumulative weights and store them in a map that associates a sample to each cumulative weight
			TreeMap<Double, V> cumul = new TreeMap<Double, V> ();
			Double sum = 0.0;
			for (Map.Entry<V, Double> entry : samples.entrySet()) {
				sum += entry.getValue();
				cumul.put(sum, entry.getKey());
			}
			
			// Check whether we already have an acceptable number of samples
			HashMap<V, Double> out;
			if (samples.size() <= this.nbrSamples) {
				 out = new HashMap<V, Double> (samples);
			}
			else { // too many samples
				
				// Re-sample, with recast, until we reach the desired number of samples
				out = new HashMap<V, Double> ();
				Double oldSum = sum;
				sum = 0.0;
				for (int nbr = 0; nbr < this.nbrSamples; sum++) {
					
					// Pick a sample at random, according to the weights
					double rand = oldSum * Math.random();
					V sample = null;
					for (Map.Entry<Double, V> entry : cumul.entrySet()) { // loop until we find the first sample with a cumulated weight higher than rand
						if (entry.getKey() >= rand) {
							sample = entry.getValue();
							break;
						}
					}
					
					// Check if we already have drawn this sample
					Double weight = out.get(sample);
					if (weight == null) { // first time we draw this sample
						nbr++;
						out.put(sample, 1.0);
					} else 
						out.put(sample, weight++);
					
				}
			}
			
			// Normalize the weights
			for (Map.Entry<V, Double> entry : out.entrySet()) 
				entry.setValue(entry.getValue() / sum);
			
			return out;
		}
		
		/** @see LowestCommonAncestors#sendOutput(java.lang.String, LowestCommonAncestors.NodeInfo) */
		@SuppressWarnings("unchecked")
		@Override 
		protected void sendOutput (String node, NodeInfo nodeInfo) {
			
			// First propose samples for my random variables
			this.proposeSamples(node, nodeInfo);
			
			// Combine the proposed samples for each random variable for which I am the lca
			this.chooseSamples(node, nodeInfo);
			
			// Sample the random variables
			for (String randVar : nodeInfo.myFlags)
				problem.setProbSpace(randVar, finalSamples.get(randVar));
			
			// Send the message telling where to project random variables
			if (this.proj == this.whereToProject) { // project at lcas
				queue.sendMessageToSelf(new RandVarsProjMsg (node, nodeInfo.lcas));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new RandVarsProjMsg (node, (HashSet<String>) nodeInfo.lcas.clone()));
			}
			else if (this.proj == WhereToProject.LEAVES) { // project at leaves
				queue.sendMessageToSelf(new RandVarsProjMsg (node, nodeInfo.myFlags));
				queue.sendMessage(AgentInterface.STATS_MONITOR, new RandVarsProjMsg (node, (HashSet<String>) nodeInfo.myFlags.clone()));
			}
		}
		
	}
	
	/** A version of the SamplingPhase in which the sampling for all random variables is performed at the roots
	 * @param <V> 	the type used for variable values
	 * @param <U> 	the type used for utility values
	 */
	public static class AtRoots < V extends Addable<V>, U extends Addable<U> > extends AtLCAs<V, U> {
		
		/** Constructor in statistics gatherer mode
		 * @param problem 		the overall problem
		 * @param parameters 	the parameters
		 */
		public AtRoots(Element parameters, DCOPProblemInterface<V, U> problem) {
			super(parameters, problem, false);
			String whereToProj = parameters.getAttributeValue("whereToProject");
			if (whereToProj != null && ! whereToProj.equals("roots")) 
				System.err.println("Warning! Unsupported value `" + whereToProj + 
						"' for the option `whereToProject' of module SamplingPhase$AtRoots being overridden with default value `roots'");
			this.proj = WhereToProject.ROOTS;
			this.whereToProject = WhereToProject.ROOTS;
		}

		/** Constructor
		 * @param problem 		this agent's problem
		 * @param parameters 	the parameters
		 */
		public AtRoots(DCOPProblemInterface<V, U> problem, Element parameters) {
			this.problem = problem;
			this.nbrSamples = Integer.parseInt(parameters.getAttributeValue("nbrSamples"));
			
			// Parse and record where to project
			String whereToProj = parameters.getAttributeValue("whereToProject");
			if (whereToProj != null && ! whereToProj.equals("roots")) 
				System.err.println("Warning! Unsupported value `" + whereToProj + 
						"' for the option `whereToProject' of module SamplingPhase$AtRoots being overridden with default value `roots'");
			this.proj = WhereToProject.ROOTS;
			this.whereToProject = WhereToProject.ROOTS;
		}
		
		/** @see SamplingPhase.AtLCAs#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		@Override
		public void notifyIn(Message msg) {
			
			String type = msg.getType();
			
			if (type.equals(PHASE1_MSG_TYPE)) { // phase 1 message received from a child
				
				// Parse the problem if this hasn't been done yet
				if (! this.started) 
					init();
				
				SamplesMsg1<V> msgCast = (SamplesMsg1<V>) msg;
				String node = msgCast.getDest();
				NodeInfo nodeInfo = this.infos.get(node);
				
				// Check if we have received the DFSoutput message for that node yet
				if (nodeInfo.allFlags == null) { // we must wait for the DFSoutput message
					nodeInfo.phase1msgs.add(msgCast);
					return;
				}
				
				HashSet<String> flags = msgCast.getFlags();
							
				// If I'm a root, simulate that I am linked to all received random variables
				if (nodeInfo.parent == null) {
					nodeInfo.myFlags.addAll(flags);
					nodeInfo.lcas.addAll(flags);
				}
			}
			
			super.notifyIn(msg);
		}

		/** @see SamplingPhase.AtLCAs#proposeSamples(java.lang.String, LowestCommonAncestors.NodeInfo) */
		@Override
		protected void proposeSamples (String var, NodeInfo nodeInfo) {
			
			HashMap< String, Map<V, Double> > mySamples = this.samples.get(var);
			
			for (String randVar : nodeInfo.myFlags) {
				
				// Skip this random variable if I don't know its probability distribution
				if (! this.allRandVars.contains(randVar)) 
					continue;
				
				// Get the proposed samples that may have already been received, and add new proposed samples
				Map<V, Double> proposed = mySamples.get(randVar);
				Map<V, Double> newProposed;
				if (proposed != null) {
					newProposed = this.combineSamples(this.probLaws.get(randVar).sample(this.nbrSamples), proposed);
				} else 
					newProposed = this.probLaws.get(randVar).sample(this.nbrSamples);

				mySamples.put(randVar, newProposed);
			}
		}

	}
	
	/** @return a DOT-formated representation of the DFS, including random variables */
	public String dfsToString () {
		synchronized (this.relationships) {
			return dfsToString (this.relationships, this.randVars, this.randVarsProj, (this.proj == WhereToProject.LEAVES));
		}
	}

	/** Prints the input dfs in DOT format
	 * @param dfs 				for each variable, a map associating a list of neighbors to each type of relationship
	 * @param randVars 			for each variable, the set of random variables it is linked to
	 * @param randVarsProj 		for each variable, which random variables it must project out
	 * @param projAtLeaves 		whether projection of random variables is performed at the leaves
	 * @return a DOT-formated representation of the DFS, including random variables
	 */
	public static String dfsToString (Map< String, ? extends DFSview<?, ?> > dfs, 
			HashMap< String, HashSet<String> > randVars, HashMap< String, HashSet<String> > randVarsProj, final boolean projAtLeaves) {
		StringBuilder out = new StringBuilder ("digraph {\n\tnode [shape = \"circle\"];\n\n");
		
		// For each variable:
		for (Map.Entry< String, ? extends DFSview<?, ?> > entry : dfs.entrySet()) {
			String var = entry.getKey();
			DFSview<?, ?> relationships = entry.getValue();
						
			// First print the variable
			HashSet<String> myRandVarsProj = randVarsProj.get(var);
			if (myRandVarsProj.isEmpty()) {
				out.append("\t" + var + " [label = \"" + var + "\" style=\"filled\"];\n");
			} else {
				
				// Create a cluster for this variable and the random variables it must project out
				out.append("\tsubgraph cluster_" + var + " {\n");
				out.append("\t\t" + var + " [label = \"" + var + "\" style=\"filled\"];\n");
				for (String randVar : myRandVarsProj) {
					if (projAtLeaves) {
						out.append("\t\t" + randVar + "_" + var + " [label = \"" + randVar + "\"];\n");
					} else 
						out.append("\t\t" + randVar + " [label = \"" + randVar + "\"];\n");
				}
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
			
			// Print the edges with the random variables
			for (String randVar : randVars.get(var)) {
				if (projAtLeaves) {
					if (myRandVarsProj.contains(randVar)) // don't show the link to randVar if it is not actually projected out by var
						out.append("\t" + var + " -> " + randVar + "_" + var + " [style = \"dashed\" arrowhead = \"none\" weight=\"0.5\"];\n");
					
				} else {
					
					// Only show the link to randVar if randVar is projected out by an ancestor (or by itself)
					String ancestor = var;
					while (ancestor != null) {
						if (randVarsProj.get(ancestor).contains(randVar)) {
							out.append("\t" + randVar + " -> " + var + " [style = \"dashed\" arrowhead = \"none\" weight=\"0.5\"];\n");
							break;
						}
						ancestor = dfs.get(ancestor).getParent();
					}
					
				}
			}

			out.append("\n");
		}

		out.append("}");
		return out.toString();
	}

}
