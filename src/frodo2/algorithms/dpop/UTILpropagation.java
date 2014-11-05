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

package frodo2.algorithms.dpop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.MessageWith4Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** Classical UTIL propagation protocol
 * @author Thomas Leaute
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 */
public class UTILpropagation < Val extends Addable<Val>, U extends Addable<U> > 
implements StatsReporter {
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** @return the type of the start message */
	public String getStartMsgType () {
		return START_MSG_TYPE;
	}

	/** The type of the message telling the agent finished */
	public static String FINISH_MSG_TYPE = AgentInterface.AGENT_FINISHED;
	
	/** The type of the messages containing information about the DFS */
	public static String DFS_MSG_TYPE = DFSgeneration.OUTPUT_MSG_TYPE;
	
	/** @return the type of the DFS output */
	public String getDFSMsgType () {
		return DFS_MSG_TYPE;
	}

	/** The type of the messages containing utilities */
	public static final String UTIL_MSG_TYPE = "UTIL";
	
	/** The type of the messages containing conditional optimal assignments */
	public static final String OUTPUT_MSG_TYPE = "UTILoutputMessage";
	
	/** The type of the messages containing separators */
	public static final String SEPARATOR_MSG_TYPE = "SeparatorMessage";
	
	/** The type of the messages containing optimal utility values sent by roots */
	public static final String OPT_UTIL_MSG_TYPE = "OptUtilMessage";
	
	/** The type of messages sent to the statistics monitor */
	public static final String UTIL_STATS_MSG_TYPE = "UTILstatsMessage";

	/** Whether the parser should consider variables with no specified owner */
	protected boolean withAnonymVars = false;
	
	/** The problem */
	protected DCOPProblemInterface<Val, U> problem;
	
	/** Whether the execution of the algorithm has been started */
	protected boolean started = false;
	
	/** \c true if we want to maximize utility, \c false if we want to minimize cost */
	protected boolean maximize = true;

	/** The queue on which it should call sendMessage() */
	protected Queue queue;
	
	/** For each variable this agent owns, its relevant information */
	protected Map<String, ClusterInfo> infos;
	
	/** This agent's name */
	protected String myID;
	
	/** The optimal utility found to the problem */
	protected U optUtil;
	
	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;
	
	/** The time when the last stat message has been received */
	private long finalTime;
	
	/** In stats gatherer mode, the maximum number of variables in a UTIL message */
	private Integer maxMsgDim = 0;
	
	/** Whether to minimize the NCCC count, at the expense of an increase in runtime */
	private final boolean minNCCCs;
	
	/** Constructor
	 * @param problem 	the problem description
	 */
	public UTILpropagation (DCOPProblemInterface<Val, U> problem) {
		this.problem = problem;
		this.minNCCCs = false;
	}
	
	/** Constructor from XML descriptions
	 * @param problem 					description of the problem
	 * @param parameters 				description of the parameters of UTILpropagation
	 */
	public UTILpropagation (DCOPProblemInterface<Val, U> problem, Element parameters) {
		this.problem = problem;
		
		// Parse whether to optimize runtime or NCCC count
		String minNCCCs = parameters.getAttributeValue("minNCCCs");
		if (minNCCCs != null) 
			this.minNCCCs = Boolean.parseBoolean(minNCCCs);
		else 
			this.minNCCCs = false;
	}
	
	/** Parses the problem */
	protected void init () {
		this.infos = new HashMap<String, ClusterInfo> ();
		this.maximize = problem.maximize();
		this.myID = problem.getAgent();
		this.started = true;
	}
	
	/** @see StatsReporter#reset() */
	public void reset () {
		this.infos = new HashMap<String, ClusterInfo> ();
		this.optUtil = null;
		this.myID = null;
		this.started = false;
		this.maxMsgDim = 0;
	}

	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public UTILpropagation (Element parameters, DCOPProblemInterface<Val, U> problem) {
		this.minNCCCs = false;
		this.problem = problem;
		this.maximize = problem.maximize();
	}
	
	/** A message holding the optimal assignments to variables, conditioned on the assignments to the variables in its separator
	 * @param <Val> the type used for variable values
	 */
	public static class SolutionMessage < Val extends Addable<Val> > 
	extends MessageWith3Payloads < String, String[], BasicUtilitySolutionSpace < Val, ArrayList <Val> > > {

		/** Empty constructor used for externalization */
		public SolutionMessage () { }

		/** Constructor 
		 * @param clusterID 	the cluster ID
		 * @param variables 	the variables optimized over
		 * @param space		 	the conditional optimal assignments to \a variable
		 */
		public SolutionMessage(String clusterID, String[] variables, BasicUtilitySolutionSpace < Val, ArrayList <Val> > space) {
			super(OUTPUT_MSG_TYPE, clusterID, variables, space);
		}
		
		/** @return the cluster ID */
		public String getClusterID () {
			return this.getPayload1();
		}
		
		/** @return the variable optimized over */
		public String[] getVariables () {
			return getPayload2();
		}

		/** @return the conditional optimal assignments to the variable */
		public BasicUtilitySolutionSpace < Val, ArrayList <Val> > getCondOptAssignments () {
			return getPayload3();
		}
		
		/** @see MessageWith2Payloads#toString() */
		@Override
		public String toString () {
			return "Message(type = `" + this.getType()  + "')\n\tclusterID = " + this.getClusterID() + 
					"\n\tvars = " + Arrays.asList(this.getVariables()) + "\n\thypercube = " + this.getCondOptAssignments(); 
		}
		
		/** @see Message#fakeSerialize() */
		@Override
		public void fakeSerialize () {
			super.setPayload3(super.getPayload3().resolve());
		}
	}
	
	/** A message holding the separator of a given child variable */
	public static class SeparatorMessage extends MessageWith4Payloads<String, String, String[], String> {
		
		/** Empty constructor used for externalization */
		public SeparatorMessage () { }

		/** Constructor
		 * @param child 		the child variable
		 * @param parent 		the parent variable
		 * @param separator 	the child's separator
		 * @param agent 		the lower agent
		 */
		public SeparatorMessage(String child, String parent, String[] separator, String agent) {
			super(SEPARATOR_MSG_TYPE, child, parent, separator, agent);
		}
		
		/** @return the child variable */
		public String getChild () {
			return super.getPayload1();
		}
		
		/** @return the parent variable */
		public String getParent () {
			return super.getPayload2();
		}
		
		/** @return the child variable's separator */
		public String[] getSeparator () {
			return super.getPayload3();
		}
		
		/** @return the lower agent */
		public String getLowerAgent () {
			return this.getPayload4();
		}
		
		/** @see frodo2.communication.Message#toString() */
		public String toString () {
			return "Message(type = `" + this.getType() + "')\n\tchild: " + super.getPayload1() + "\n\tparent: " + super.getPayload2() + 
			"\n\tseparator: " + Arrays.asList(super.getPayload3()) + "\n\tlower agent: " + this.getLowerAgent();
		}
	}
	
	/** Message sent by roots containing the optimal utility value of their DFS tree 
	 * @param <U> the type used for utility values
	 */
	public static class OptUtilMessage < U extends Addable<U> > extends MessageWith2Payloads <U, String> {

		/** Empty constructor used for externalization */
		public OptUtilMessage () { }

		/** Constructor
		 * @param value 	optimal utility value
		 * @param rootVar 	the name of the root variable reporting its optimal utility value
		 */
		public OptUtilMessage(U value, String rootVar) {
			super(OPT_UTIL_MSG_TYPE, value, rootVar);
		}
		
		/** @return the optimal utility value */
		public U getUtility () {
			return this.getPayload1();
		}
		
		/** @return the name of the root variable reporting its optimal utility value */
		public String getRoot() {
			return this.getPayload2();
		}
	}
	
	/** Message containing statistics */
	public static class StatsMessage extends MessageWithPayload<Integer> {

		/** Empty constructor used for externalization */
		public StatsMessage () { }

		/** Constructor 
		 * @param msgDim 	the number of variables in the UTIL message sent to the parent
		 */
		public StatsMessage(Integer msgDim) {
			super(UTIL_STATS_MSG_TYPE, msgDim);
		}
		
		/** @return the number of variables in the UTIL message sent to the parent */
		public Integer getMsgDim () {
			return super.getPayload();
		}
		
	}
	
	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection <String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (4);
		types.add(this.getStartMsgType());
		types.add(this.getDFSMsgType());
		types.add(UTIL_MSG_TYPE);
		types.add(FINISH_MSG_TYPE);
		return types;
	}

	/** The algorithm
	 * 
	 * For each variable that it owns, the agent waits for the reception of all UTIL messages from all child variables. 
	 * @param msg the message received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(OPT_UTIL_MSG_TYPE)) { // we are in stats gatherer mode
			
			OptUtilMessage<U> msgCast = (OptUtilMessage<U>) msg;
			if (!silent) {
				if (this.maximize) {
					System.out.println("Optimal utility for component rooted at `" + msgCast.getRoot() + "\': " + msgCast.getUtility());
				} else 
					System.out.println("Optimal cost for component rooted at `" + msgCast.getRoot() + "\': " + msgCast.getUtility());
			}
			
			// Going through a String to get the utility in case the agents have used a different class from the stats gatherer
			if (this.optUtil == null) {
				this.optUtil = this.problem.getZeroUtility().fromString(msgCast.getUtility().toString());
			} else 
				this.optUtil = this.optUtil.add(this.problem.getZeroUtility().fromString(msgCast.getUtility().toString()));

			Long time = queue.getCurrentMessageWrapper().getTime();
			if(finalTime < time)
				finalTime = time;
			
			return;
		}
		
		else if (type.equals(UTIL_STATS_MSG_TYPE)) { // we are in stats gatherer mode 
			
			this.maxMsgDim = Math.max(this.maxMsgDim, ((StatsMessage) msg).getMsgDim());

			return;
		}
		
		else if (type.equals(FINISH_MSG_TYPE)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this hasn't been done yet
		if (! this.started) 
			init();
		
		if (type.equals(this.getDFSMsgType())) { // this is an output of the DFS generation phase
			
			// Retrieve the information from the message about children, pseudo-children... 
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>) msg;
			String[] vars = msgCast.getVars();
			DFSview<Val, U> cluster = msgCast.getNeighbors();
			
			if (cluster == null) // DFS reset message
				return;

			String clusterID = cluster.getID();
			ClusterInfo info = infos.get(clusterID);
			if (info == null) { // first message ever received concerning this cluster
				info = this.newClusterInfo();
				infos.put(clusterID, info);
			}
			for (int i = vars.length - 1; i >= 0; i--) { // gather the UTIL messages already received for the variables in this cluster
				ClusterInfo info2 = this.infos.get(vars[i]);
				if (info2 != null && ! vars[i].equals(clusterID)) {
					info.spaces.addAll(info2.spaces);
					info.nbrUTIL += info2.nbrUTIL;
				}
				this.infos.put(vars[i], info);
			}
			info.vars = vars;
			info.id = clusterID;

			// Record its parent, if any
			info.parent = cluster.getParent();
			info.parentAgent = cluster.getParentAgent();

			// Record which constraints this variable is responsible for enforcing 
			info.nbrChildren = cluster.getChildren().size();
			for (UtilitySolutionSpace<Val, U> space : cluster.getSpaces()) 
				this.record(null, space, info);
			
			// If we are minimizing NCCCs, we should already compute the join of all local spaces while waiting for the UTIL messages from the children
			if (this.minNCCCs && !info.spaces.isEmpty()) {
				UtilitySolutionSpace<Val, U> firstSpace = info.spaces.removeFirst();
				UtilitySolutionSpace<Val, U>[] otherSpaces = info.spaces.toArray(new UtilitySolutionSpace [info.spaces.size()]);
				UtilitySolutionSpace<Val, U> join = firstSpace.joinMinNCCCs(otherSpaces);
				info.spaces.clear();
				firstSpace = null;
				info.spaces.add(join);
			}

			// Check if I have already received all UTIL messages from all children
			if (info.nbrUTIL >= info.nbrChildren) 
				projectAndSend(info);

		}
		
		else if (type.equals(UTIL_MSG_TYPE)) { // this is a UTIL message
			// Retrieve the information from the message
			UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>) msg;
			UtilitySolutionSpace<Val, U> space = msgCast.getSpace();
			String sender = msgCast.getSender();
			String senderAgent = msgCast.getSenderAgent();
			String dest = msgCast.getDestination();

			// Send the sender variable's separator to the VALUE propagation protocol
			this.sendSeparator (sender, senderAgent, dest, space.getVariables());

			// Obtain the info on the destination variable
			ClusterInfo info = infos.get(dest);
			if (info == null) { // first message ever received concerning this variable
				info = this.newClusterInfo();
				infos.put(dest, info);
				this.record(sender, space, info);
				info.nbrUTIL += 1;
				
				// We cannot do any more until we receive the info about this variable's parent and children
				return;
			}
			
			// Record the space
			this.record(sender, space, info);

			// Check if I have already received all UTIL messages from all children
			if (++info.nbrUTIL >= info.nbrChildren && info.vars != null) 
				projectAndSend(info);
		}
		
	}

	/** @return a new ClusterInfo */
	protected ClusterInfo newClusterInfo() {
		return new ClusterInfo ();
	}

	/** Sends a message to the VALUEpropagation module containing the separator of a variable
	 * @param senderVar 	sender variable
	 * @param senderAgent 	sender agent
	 * @param dest 			destination variable
	 * @param separator 	the separator of the destination variable
	 */
	protected void sendSeparator (String senderVar, String senderAgent, String dest, String[] separator) {
		queue.sendMessageToSelf(new SeparatorMessage (senderVar, dest, separator, senderAgent));
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
	
	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		ArrayList <String> msgTypes = new ArrayList <String> (2);
		msgTypes.add(OPT_UTIL_MSG_TYPE);
		msgTypes.add(UTIL_STATS_MSG_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent  = silent;
	}
	
	/** @return the maximum number of variables in a UTIL message (in stats gatherer mode only) */
	public Integer getMaxMsgDim () {
		return this.maxMsgDim;
	}
	
	/** A convenience class used to store information about a cluster */
	protected class ClusterInfo {
		
		/** This cluster's ID */
		public String id;
		
		/** The variables this info corresponds to */
		public String[] vars;
		
		/** The parent variable of this cluster (or \c null if this variable is a root) */
		public String parent = null;
		
		/** The parent agent of this cluster (or \c null if this variable is a root) */
		public String parentAgent = null;
		
		/** The number of children variables */
		public int nbrChildren = 0;
		
		/** The number of UTIL messages received for this cluster so far */
		public int nbrUTIL = 0;
		
		/** A list of spaces received for this cluster so far */
		public LinkedList< UtilitySolutionSpace<Val, U> > spaces = new LinkedList< UtilitySolutionSpace<Val, U> > ();
		
		/** Constructor */
		public ClusterInfo () { }
		
		/** @see java.lang.Object#toString() */
		public String toString () {
			
			StringBuilder builder = new StringBuilder ("Info on cluster:");
			
			builder.append("\n\t ID: " + this.id);
			builder.append("\n\t vars: " + Arrays.toString(this.vars));
			builder.append("\n\t parentVar: " + this.parent);
			builder.append("\n\t parentAgent: " + this.parentAgent);
			builder.append("\n\t nbrChildren: " + this.nbrChildren);
			builder.append("\n\t nbrUTIL: " + this.nbrUTIL);
			builder.append("\n\t spaces: " + this.spaces);
			
			return builder.toString();
		}
	}

	/** Records the input space until spaces from all children have been received
	 * @param senderVar the sender variable; \c null if the space is a local constraint
	 * @param space 	the space to be recorded
	 * @param info 		the information on the variable responsible for the space
	 * @todo Use the MessageWithRawData paradigm to deserialize the biggest space first, 
	 * and then deserialize the smaller spaces on-the-fly into the biggest space (when they are subspaces of it) to save memory
	 */
	protected void record(String senderVar, UtilitySolutionSpace<Val, U> space, ClusterInfo info) {
		
		if (info.spaces.isEmpty()) {
			info.spaces.add(space);
			return;
		}
		
		if (this.minNCCCs) 
			info.spaces.add(space); // delay the join to save memory
		else 
			info.spaces.add(info.spaces.removeFirst().join(space));
	}

	/** Projects out a variable and sends the result to its parent (if any) or to itself (to initiate VALUE propagation)
	 * @param info information about the variable to be projected out
	 */
	@SuppressWarnings("unchecked")
	protected void projectAndSend(ClusterInfo info) {
		
		// Check if this variable is unconstrained
		if (info.spaces.isEmpty()) {
			assert info.vars.length == 1;
			String var = info.vars[0];
			
			assert info.parent == null : var + " is unconstrained but has a parent " + info.parent;
			
			// Choose the first value in the domain as the optimal one and send output messages
			Val[] dom = problem.getDomain(var);
			ArrayList<Val> opt = new ArrayList<Val> (1);
			opt.add(dom[0]);
			queue.sendMessageToSelf(new SolutionMessage<Val> (info.id, info.vars, new ScalarBasicHypercube< Val, ArrayList<Val> > (opt, null)));
			U infeasibleUtil = (this.maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility());
			this.sendOutput(new ScalarHypercube<Val, U> (problem.getZeroUtility(), infeasibleUtil, (Class<? extends Val[]>) dom.getClass()), var);
			
			return;
		}
		
		// Join all spaces
		UtilitySolutionSpace<Val, U> join = info.spaces.removeFirst();
		UtilitySolutionSpace<Val, U>[] others = (UtilitySolutionSpace<Val, U>[]) info.spaces.toArray(new UtilitySolutionSpace[0]);
		if (this.minNCCCs) 
			join = join.join(others); // all local spaces have already been minNCCC-joined, so we can use the (more efficient) normal join for the received UTIL messages, whose constraint checks don't count
			
		else // not using the minNCCCs option
			join = join.join(others);
		
		// Garbage-collect the spaces
		info.spaces = null;
		others = null;
		
		// Project out the variable
		ProjOutput<Val, U> projOutput = this.project(join, info.vars);

		// Garbage-collect the join
		join = null;
		
		// Send resulting space to parent (if any)
		if (info.parentAgent != null) {
			this.sendToParent (info.id, info.parent, info.parentAgent, projOutput.getSpace());
		} else  // the variable is a root
			this.sendOutput (projOutput.getSpace(), info.id);
		
		// Send optimal assignments to the VALUE propagation protocol
		queue.sendMessageToSelf(new SolutionMessage<Val> (info.id, projOutput.varsOut, projOutput.getAssignments()));
	}
	
	/** Projects the input variable from the input space
	 * @param space 	the space
	 * @param vars 		the variables to project out
	 * @return the result of the projection 
	 */
	protected ProjOutput<Val, U> project (UtilitySolutionSpace<Val, U> space, String[] vars) {
		return space.project(vars, maximize);
	}

	/** Sends a UTIL message
	 * @param var 			the sender of the message
	 * @param parentVar 	the destination variable of the message
	 * @param parentAgent 	the destination agent of the message
	 * @param space 		the content of the message
	 * @todo First try to prune variable domains, and notify other branches of the pseudo-tree of the pruning. 
	 */
	protected void sendToParent (String var, String parentVar, String parentAgent, UtilitySolutionSpace<Val, U> space) {
		queue.sendMessage(parentAgent, new UTILmsg<Val, U> (var, this.myID, parentVar, space));
		if (! parentAgent.equals(this.myID)) 
			queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage (space.getNumberOfVariables()));
		infos.remove(var);
	}

	/** Sends the output optimal utility to itself (i.e. the VALUEpropagation listener) and to the statistics monitor
	 * @param space 	the final frontier
	 * @param root 		the root of the DFS
	 */
	protected void sendOutput(UtilitySolutionSpace<Val, U> space, String root) {
		assert space.getNumberOfVariables() == 0 : "Space output by the root " + root + " is not scalar:\n" + space;
		OptUtilMessage<U> output = new OptUtilMessage<U> (space.getUtility(0), root);
		queue.sendMessageToSelf(output);
		queue.sendMessage(AgentInterface.STATS_MONITOR, output);
		infos.remove(root);
	}

	/**
	 * @return 	the optimal utility found to the problem
	 * @warning Only works if the module is instantiated in stats gatherer mode. 
	 */
	public U getOptUtil () {
		return this.optUtil;
	}
	
	/**
	 * Returns the time at which this module has finished, 
	 * determined by looking at the timestamp of the stat messages
	 * 
	 * @author Brammert Ottens, 22 feb 2010
	 * @return the time at which this module has finished
	 */
	public long getFinalTime() {
		return finalTime;
	}
}
