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

package frodo2.algorithms.dpop.count;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.UTILmsg;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** Classical UTIL propagation protocol
 * @author Thomas Leaute, Brammert Ottens
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @todo Extremely high code redundancy with UTILpropagation; we should be inheriting this class. 
 */
public class CountSolutionsUTIL < Val extends Addable<Val>, U extends Addable<U> > 
implements StatsReporter {
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** The type of the messages containing information about the DFS */
	public static String DFS_MSG_TYPE = DFSgeneration.OUTPUT_MSG_TYPE;
	
	/** The type of the messages containing utilities */
	public static final String UTIL_MSG_TYPE = UTILpropagation.UTIL_MSG_TYPE;
	
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
	protected Map<String, VariableInfo> infos = new HashMap<String, VariableInfo> ();
	
	/** For each known variable, the name of the agent that owns it */
	protected Map<String, String> owners = new HashMap<String, String> ();
	
	/** The optimal utility found to the problem */
	protected U optUtil;
	
	/** Whether the stats reporter should print its stats */
	protected boolean silent = false;
	
	/** In stats gatherer mode, the maximum number of variables in a UTIL message */
	private Integer maxMsgDim = 0;

	/** Empty constructor */
	protected CountSolutionsUTIL () { }
	
	/** Constructor
	 * @param problem 	the problem description
	 */
	public CountSolutionsUTIL (DCOPProblemInterface<Val, U> problem) {
		this.problem = problem;
	}
	
	/** Constructor from XML descriptions
	 * @param problem 					description of the problem
	 * @param parameters 				description of the parameters of UTILpropagation
	 * @throws ClassNotFoundException 	if the module parameters specify an unknown class for utility values
	 */
	public CountSolutionsUTIL (DCOPProblemInterface<Val, U> problem, Element parameters) throws ClassNotFoundException {
		this.problem = problem;
	}
	
	/** Parses the problem */
	protected void init () {
		this.maximize = problem.maximize();
		this.owners = problem.getOwners();
		this.started = true;
	}
	
	/** @see StatsReporter#reset() */
	public void reset() {
		this.owners = null;
		this.started = false;
		
		// Only useful in stats gatherer mode
		this.optUtil = null;
		this.maxMsgDim = 0;
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public CountSolutionsUTIL (Element parameters, DCOPProblemInterface<Val, U> problem) {
		this.maximize = problem.maximize();
	}
	
	/** A message holding the optimal assignments to a variable, conditioned on the assignments to the variables in its separator
	 * @param <Val> the type used for variable values
	 * @param <U> 	the type used for utility values
	 */
	public static class SolutionMessage <Val extends Addable<Val>, U extends Addable<U>> 
	extends MessageWith2Payloads < String, UtilitySolutionSpace < Val, U > > {

		/** Empty constructor used for externalization */
		public SolutionMessage () { }

		/** Constructor 
		 * @param variable 		the variable optimized over
		 * @param space		 	the optimal utility as a function of the variable
		 */ 
		public SolutionMessage(String variable, UtilitySolutionSpace < Val, U > space) {
			super(OUTPUT_MSG_TYPE, variable, space);
		}
		
		/** @return the variable optimized over */
		public String getVariable () {
			return getPayload1();
		}

		/** @return the optimal utility as a function of the variable */
		public UtilitySolutionSpace < Val, U > getSpace () {
			return this.getPayload2();
		}
		
		/** @see Message#fakeSerialize() */
		@Override
		public void fakeSerialize () {
			super.setPayload2(super.getPayload2().resolve());
		}
	}
	
	/** A message holding the separator of a given child variable */
	public static class SeparatorMessage extends MessageWith3Payloads<String, String, String[]> {
		
		/** Empty constructor used for externalization */
		public SeparatorMessage () { }

		/** Constructor
		 * @param child 		the child variable
		 * @param parent 		the parent variable
		 * @param separator 	the child's separator
		 */
		public SeparatorMessage(String child, String parent, String[] separator) {
			super(SEPARATOR_MSG_TYPE, child, parent, separator);
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
		
		/** @see frodo2.communication.Message#toString() */
		public String toString () {
			return "Message(type = `" + this.getType() + "')\n\tchild: " + super.getPayload1() + "\n\tparent: " + super.getPayload2() + 
			"\n\tseparator: " + Arrays.asList(super.getPayload3());
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
		types.add(START_MSG_TYPE);
		types.add(DFS_MSG_TYPE);
		types.add(UTIL_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** The algorithm
	 * 
	 * For each variable that it owns, the agent waits for the reception of all UTIL messages from all child variables, 
	 * plus one corresponding to a private constraint sent by the DPOP agent. 
	 * @param msg the message received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(OPT_UTIL_MSG_TYPE)) { // we are in stats gatherer mode
			
			OptUtilMessage<U> msgCast = (OptUtilMessage<U>) msg;
			if (!silent) 
				System.out.println("Optimal " + (this.maximize ? "utility" : "cost") + " for component rooted at `" + msgCast.getRoot() + "\': " + msgCast.getUtility());
			if (this.optUtil == null) {
				this.optUtil = msgCast.getUtility();
			} else 
				this.optUtil = this.optUtil.add(msgCast.getUtility());

			return;
		}
		
		else if (type.equals(UTIL_STATS_MSG_TYPE)) { // we are in stats gatherer mode 
			
			this.maxMsgDim = Math.max(this.maxMsgDim, ((StatsMessage) msg).getMsgDim());

			return;
		}
		
		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this hasn't been done yet
		if (! this.started) 
			init();
		
		if (type.equals(DFS_MSG_TYPE)) { // this is an output of the DFS generation phase
			
			// Retrieve the information from the message about children, pseudo-children... 
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>) msg;
			String var = msgCast.getVar();
			DFSview<Val, U> myRelationships = msgCast.getNeighbors();

			VariableInfo info = infos.get(var);
			if (info == null) { // first message ever received concerning this variable
				info = new VariableInfo ();
				infos.put(var, info);
			}
			info.self = var;

			// Record its parent, if any
			info.parent = myRelationships.getParent();

			// Record which constraints this variable is responsible for enforcing 
			info.nbrChildren = myRelationships.getChildren().size();
			for (UtilitySolutionSpace<Val, U> space : myRelationships.getSpaces()) 
				this.record(space, info);

			// Check if I have already received all UTIL messages from all children
			if (info.nbrUTIL >= info.nbrChildren) 
				projectAndSend(info);

		}
		
		else if (type.equals(UTIL_MSG_TYPE)) { // this is a UTIL message
			
			// Retrieve the information from the message
			UTILmsg<Val, U> msgCast = (UTILmsg<Val, U>) msg;
			UtilitySolutionSpace<Val, U> space = msgCast.getSpace();
			String sender = msgCast.getSender();
			String dest = msgCast.getDestination();

			// Send the sender variable's separator to the VALUE propagation protocol
			this.sendSeparator (sender, dest, space.getVariables());

			// Obtain the info on the destination variable
			VariableInfo info = infos.get(dest);
			if (info == null) { // first message ever received concerning this variable
				info = new VariableInfo ();
				infos.put(dest, info);
				info.space = space;
				info.nbrUTIL = 1;
				
				// We cannot do any more until we receive the info about this variable's parent and children
				return;
			}

			// Record the space 
			this.record(space, info);

			// Check if I have already received all UTIL messages from all children
			if (++info.nbrUTIL >= info.nbrChildren && info.self != null) 
				projectAndSend(info);
		}
		
	}

	/** Sends a message to the VALUEpropagation module containing the separator of a variable
	 * @param sender 		sender variable
	 * @param dest 			destination variable
	 * @param separator 	the separator of the destination variable
	 */
	protected void sendSeparator (String sender, String dest, String[] separator) {
		queue.sendMessageToSelf(new SeparatorMessage (sender, dest, separator));
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
	
	/** A convenience class used to store information about a variable */
	protected class VariableInfo {
		
		/** The variable this info corresponds to */
		public String self;
		
		/** The parent of this variable (or \c null if this variable is a root) */
		public String parent = null;
		
		/** The number of children variables */
		public int nbrChildren = 0;
		
		/** The number of UTIL messages received for this variable so far */
		public int nbrUTIL = 0;
		
		/** The join of all spaces received for this variable so far */
		public UtilitySolutionSpace<Val, U> space;
		
		/** @see java.lang.Object#toString() */
		public String toString () {
			return "Info on var `" + this.self + "':\n\tparent: " + this.parent + "\n\tnbrChildren: " + this.nbrChildren 
			+ "\n\tnbrUTIL: " + this.nbrUTIL + "\n\tspace: " + this.space;
		}
	}

	/** Records the input space until spaces from all children have been received
	 * @param space 	the space to be recorded
	 * @param info 		the variable info
	 */
	private void record(UtilitySolutionSpace<Val, U> space, VariableInfo info) {
		
		if (info.space == null) 
			info.space = space;
		else 
			info.space = info.space.join(space);
	}

	/** Projects out a variable and sends the result to its parent (if any) or to itself (to initiate VALUE propagation)
	 * @param info information about the variable to be projected out
	 */
	@SuppressWarnings("unchecked")
	private void projectAndSend(VariableInfo info) {
		
		// Check if this variable is unconstrained
		if (info.space == null) {
			
			assert info.parent == null : info.self + " is unconstrained but has a parent " + info.parent;
			
			// Send a one-dimensional hypercube to the VALUE propagation phase
			Val[] dom = problem.getDomain(info.self);
			Val[][] doms = (Val[][]) Array.newInstance(dom.getClass(), 1);
			doms[0] = dom;
			U zeroUtil = problem.getZeroUtility();
			U[] utils = (U[]) Array.newInstance(zeroUtil.getClass(), dom.length);
			Arrays.fill(utils, zeroUtil);
			U infeasibleUtil = (this.maximize ? problem.getMinInfUtility() : problem.getPlusInfUtility());
			Hypercube<Val, U> space = new Hypercube<Val, U> (new String[] { info.self }, doms, utils, infeasibleUtil);
			queue.sendMessageToSelf(new SolutionMessage<Val, U> (info.self, space));
			
			// Output a scalar hypercube 
			ScalarHypercube<Val, U> scalarSpace = new ScalarHypercube<Val, U> (zeroUtil, infeasibleUtil, (Class<? extends Val[]>) dom.getClass());
			this.sendOutput(scalarSpace, info.self);
			
			return;
		}
		
		// Project out the variable
		ProjOutput<Val, U> projOutput = this.project(info.space, info.self);
		
		// Send optimal assignments to the VALUE propagation protocol
		queue.sendMessageToSelf(new SolutionMessage<Val, U> (info.self, info.space));
		
		// Send resulting space to parent (if any)
		if (info.parent != null) {
			this.sendToParent (info.self, info.parent, projOutput.getSpace());
		} else  // the variable is a root
			this.sendOutput (projOutput.getSpace(), info.self);
	}
	
	/** Projects the input variable from the input space
	 * @param space 	the space
	 * @param var 		the variable to project out
	 * @return the result of the projection 
	 */
	protected ProjOutput<Val, U> project (UtilitySolutionSpace<Val, U> space, String var) {
		return space.project(var, maximize);
	}

	/** Sends a UTIL message
	 * @param var 		the sender of the message
	 * @param parent 	the destination of the message
	 * @param space 	the content of the message
	 */
	protected void sendToParent (String var, String parent, UtilitySolutionSpace<Val, U> space) {
		queue.sendMessage(owners.get(parent), new UTILmsg<Val, U> (var, this.problem.getAgent(), parent, space));
		queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage (space.getNumberOfVariables()));
		infos.remove(var);
	}

	/** Sends the output optimal utility to itself (i.e. the VALUEpropagation listener) and to the statistics monitor
	 * @param space 	the final frontier
	 * @param root 		the root of the DFS
	 */
	protected void sendOutput(UtilitySolutionSpace<Val, U> space, String root) {
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
}
