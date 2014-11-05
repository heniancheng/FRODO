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

package frodo2.algorithms.dpop.memory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.dpop.UTILmsg;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEmsg;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.dpop.memory.LabelingPhase.OutputMsg;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarSpaceIter;

/** MB-DPOP's memory-bounded UTIL propagation phase
 * 
 * Adrian Petcu and Boi Faltings. MB-DPOP: A new memory-bounded algorithm for distributed optimization. 
 * In Manuela M. Veloso, editor, Proceedings of the Twentieth International Joint Conference on Artificial Intelligence (IJCAI'07), 
 * pages 1452-1457, Hyderabad, India, January 6-12 2007.
 * 
 * @author Thomas Leaute
 * 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class MemoryBoundedUTIL < V extends Addable<V>, U extends Addable<U> > extends UTILpropagation<V, U> {
	
	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;
	
	/** @return the type of the start message */
	@Override
	public String getStartMsgType () {
		return START_MSG_TYPE;
	}

	/** The type of the messages containing information about the DFS */
	public static String DFS_MSG_TYPE = DFSgeneration.OUTPUT_MSG_TYPE;
	
	/** @return the type of the DFS output */
	public String getDFSMsgType () {
		return DFS_MSG_TYPE;
	}
	
	/** The information about a given variable */
	private class VarInfo extends ClusterInfo {
		
		/** The set of cycle-cutset variables (with their domains) in the cluster rooted at the variable; if empty, the variable is not the CR; if \c null, the variable is not part of any cluster */
		private HashMap<String, V[]> ccs;
		
		/** The iterator over the cycle-cutset variables */
		private Iterator<V, U> iter;
		
		/** The CC list sent by each child variable */
		private HashMap< String, HashSet<String> > ccsByChild = new HashMap< String, HashSet<String> > ();

		/** The ordered CC variables */
		private String[] context;

		/** The current values of the CC variables */
		private V[] contextValues;
		
		/** The join of all sliced UTIL messages revieved from cluster children */
		private UtilitySolutionSpace<V, U> sliceIn;
		
		/** A CR's "cache" that stores the CR's constantly updated output UTIL message */
		private ProjOutput<V, U> projOutput;
		
		/** An assignment to my separator */
		private V[] sepValues;
		
		/** Whether this is the last UTIL propagation phase, and optimal conditional assignments must be computed instead of performing a blind projection */
		private boolean lastUTIL = false;
	}
	
	/** Constructor
	 * @param problem 		the agent's subproblem
	 * @param parameters 	the module parameters
	 */
	public MemoryBoundedUTIL(DCOPProblemInterface<V, U> problem, Element parameters) {
		super(problem, parameters);
	}

	/** Constructor in stats gatherer mode
	 * @param parameters 	the module parameters
	 * @param problem 		the overall problem
	 */
	public MemoryBoundedUTIL(Element parameters, DCOPProblemInterface<V, U> problem) {
		super(parameters, problem);
	}

	/** @see UTILpropagation#getMsgTypes() */
	@Override
	public Collection<String> getMsgTypes() {
		
		Collection<String> types = super.getMsgTypes();
		types.add(LabelingPhase.OUTPUT_MSG_TYPE);
		types.add(LabelMsg.LABEL_MSG_TYPE);
		types.add(ContextMsg.CONTEXT_MSG_TYPE);
		types.add(VALUEpropagation.VALUE_MSG_TYPE);
		return types;
	}
	
	/** @return a new VarInfo */
	@Override
	protected VarInfo newClusterInfo() {
		
		VarInfo out = new VarInfo ();
		out.nbrUTIL--; // count the output of the labeling phase as a UTIL message to expect
		
		return out;
	}

	/** @see UTILpropagation#notifyIn(Message) */
	@Override
	public void notifyIn (Message msg) {
		
		String msgType = msg.getType();
		
//		System.out.println(msg);
		
		if (msgType.equals(LabelMsg.LABEL_MSG_TYPE)) {
			
			@SuppressWarnings("unchecked")
			LabelMsg<V> msgCast = (LabelMsg<V>) msg;
			String var = msgCast.getDest();
			
			if (! msgCast.getCCs().isEmpty()) { // the sender variable belongs to my cluster
				
				@SuppressWarnings("unchecked")
				VarInfo varInfo = (VarInfo) this.infos.get(var);
				if (varInfo == null) { // first message ever received concerning this variable
					varInfo = this.newClusterInfo();
					infos.put(var, varInfo);
				}
				HashSet<String> ccList = new HashSet<String> (msgCast.getCCs().keySet());
				ccList.addAll(msgCast.getSep().keySet());
				varInfo.ccsByChild.put(msgCast.getSender(), ccList);
			}
			
			return;
			
		
		} else if (msgType.equals(LabelingPhase.OUTPUT_MSG_TYPE)) {
			
			@SuppressWarnings("unchecked")
			OutputMsg<V> msgCast = (OutputMsg<V>) msg;
			@SuppressWarnings("unchecked")
			VarInfo varInfo = (VarInfo) super.infos.get(msgCast.getPayload1());
			
			assert varInfo != null : "No info for variable " + msgCast.getPayload1() + "; infos = " + super.infos.keySet();
			varInfo.ccs = msgCast.getPayload2();
			
			if (varInfo.ccs == null) { // I am not involved in any cluster
				if (++varInfo.nbrUTIL == varInfo.nbrChildren) 
					this.projectAndSend(varInfo);
				return;
			}
			
			if (varInfo.ccs.isEmpty()) // if I am not a CR, wait for a context message and/or UTIL messages
				return;
			
			// Create the iterator over the CC variables, putting my variable first in the order
			String[] ccs = new String [varInfo.ccs.size()];
			assert varInfo.vars.length == 1 : "Clustered pseudo-trees unsupported"; /// @todo Add support for clustered pseudo-trees
			String self = varInfo.vars[0];
			ccs[0] = self;
			V[] dom = varInfo.ccs.get(self);
			assert dom != null : self + " is a CR but not a CC";
			@SuppressWarnings("unchecked")
			V[][] doms = (V[][]) Array.newInstance(dom.getClass(), varInfo.ccs.size());
			doms[0] = dom;
			int i = 1;
			for (java.util.Iterator< Map.Entry<String, V[]> > ccIter = varInfo.ccs.entrySet().iterator(); ccIter.hasNext(); ) {
				Map.Entry<String, V[]> ccEntry = ccIter.next();
				String var = ccEntry.getKey();
				if (!var.equals(self)) { // my variable has already been put first
					ccs[i] = var;
					doms[i++] = ccEntry.getValue();
				}
			}
			varInfo.iter = new ScalarSpaceIter<V, U> (null, ccs, doms, null, null);
						
			// Get the first context, and send it to self to initiate context propagation
			this.queue.sendMessageToSelf(new ContextMsg<V> (self, varInfo.iter.getVariablesOrder(), varInfo.iter.nextSolution()));
			
			return;
			
			
		} else if (msgType.equals(ContextMsg.CONTEXT_MSG_TYPE)) {
			
			@SuppressWarnings("unchecked")
			ContextMsg<V> msgCast = (ContextMsg<V>) msg;
			@SuppressWarnings("unchecked")
			VarInfo varInfo = (VarInfo) this.infos.get(msgCast.getDest());
			assert varInfo != null : "No info for " + msgCast.getDest();
			String[] context = msgCast.getCCs();
			if (varInfo.context == null) {
				varInfo.nbrUTIL++;
				varInfo.context = context;
				assert context != null;
			} else if (context != null && context.length == 0) 
				varInfo.lastUTIL = true;
			varInfo.contextValues = msgCast.getValues();
			assert varInfo.contextValues != null;
			
			// Send down the context to my children
			for (Map.Entry< String, HashSet<String> > entry : varInfo.ccsByChild.entrySet()) {
				String child = entry.getKey();
				HashSet<String> ccList = entry.getValue();
				
				// Only send the sub-context that is part of the CC list sent by the child
				ArrayList<Integer> indexes = new ArrayList<Integer> (varInfo.context.length);
				for (int i = varInfo.context.length - 1; i >= 0; i--) 
					if (ccList.contains(varInfo.context[i])) 
						indexes.add(0, i);
				String[] subContext = null;
				@SuppressWarnings("unchecked")
				V[] subContextValues = (V[]) Array.newInstance(varInfo.contextValues.getClass().getComponentType(), indexes.size());
				
				if (context == null || context.length == 0) { // not the first UTIL propagation
					
					subContext = context;
					for (int i = indexes.size() - 1; i >= 0; i--) 
						subContextValues[i] = varInfo.contextValues[indexes.get(i)];
					
				} else { // first UTIL propagation
					
					subContext = new String [indexes.size()];
					for (int i = indexes.size() - 1; i >= 0; i--) {
						int index = indexes.get(i);
						subContext[i] = varInfo.context[index];
						subContextValues[i] = varInfo.contextValues[index];
					}
				}
				
				this.queue.sendMessage(this.problem.getOwner(child), new ContextMsg<V> (child, subContext, subContextValues));
			}
						
			// Send up the UTIL message if ready
			if (varInfo.nbrUTIL >= varInfo.nbrChildren) 
				projectAndSend(varInfo);

			return;
			
			
		} else if (msgType.equals(VALUEpropagation.VALUE_MSG_TYPE)) {
			
//			System.out.println(msg);
			
			@SuppressWarnings("unchecked")
			VALUEmsg<V> msgCast = (VALUEmsg<V>) msg;
			@SuppressWarnings("unchecked")
			VarInfo varInfo = (VarInfo) this.infos.get(msgCast.getDest());
			
			// Trigger one last UTIL propagation phase if I am a CR
			if (varInfo != null && varInfo.ccs != null && ! varInfo.ccs.isEmpty() && ! varInfo.lastUTIL) {
				
				String[] sep = msgCast.getVariables();
				V[] sepOptVals = msgCast.getValues();
				
				this.queue.sendMessageToSelf(new ContextMsg<V> (varInfo.vars[0], new String [0], varInfo.projOutput.assignments.getUtility(sep, sepOptVals).toArray(varInfo.contextValues)));
			}
		}
		
		super.notifyIn(msg);
	}
	
	/** @see UTILpropagation#projectAndSend(ClusterInfo) */
	@SuppressWarnings("unchecked")
	@Override
	protected void projectAndSend(ClusterInfo info) {
		
		VarInfo varInfo = (VarInfo) info;
		assert info.vars.length == 1 : "Clustered pseudo-trees unsupported"; /// @todo Add support for clustered pseudo-trees
		String self = info.vars[0];
		
		if (varInfo.ccs == null) { // the variable is not involved in any cluster
			super.projectAndSend(varInfo);
			return;
		}
		
		// Join all UTIL messages
		assert varInfo.spaces.isEmpty() || varInfo.spaces.size() == 1 : "Received full UTIL messages haven't been joined: " + varInfo.spaces;
		UtilitySolutionSpace<V, U> join = (varInfo.spaces.isEmpty() ? varInfo.sliceIn 
																	: (varInfo.sliceIn == null 	? varInfo.spaces.getFirst() 
																								: varInfo.spaces.getFirst().join(varInfo.sliceIn)));
		
		// Slice over the current context
		assert varInfo.context != null;
		assert varInfo.contextValues != null;
		join = join.slice(varInfo.context, varInfo.contextValues);
		
		if (varInfo.ccs.isEmpty()) { // I am not a CR
			
			if (! varInfo.lastUTIL) { // blindly project out my variable
				join = join.blindProject(self, super.maximize);
				this.queue.sendMessage(this.problem.getOwner(info.parent), new UTILmsg<V, U> (self, super.myID, info.parent, join));
				
			} else { // last UTIL; compute optimal conditional assignments
				
				// Check whether I already know my optimal value because I am a CC
				V optVal = null;
				for (int i = varInfo.context.length - 1; i >= 0; i--) {
					if (self.equals(varInfo.context[i])) {
						optVal = varInfo.contextValues[i];
						break;
					}
				}
				
				if (optVal == null) { // not a CC; perform projection
					ProjOutput<V, U> projOutput = this.project(join, info.vars);
					assert projOutput.varsOut.length > 0;
					assert info.parent != null;
					String owner = this.problem.getOwner(info.parent);
					if (! owner.equals(this.myID)) 
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage (projOutput.space.getNumberOfVariables()));
					queue.sendMessage(owner, new UTILmsg<V, U> (self, super.myID, info.parent, projOutput.space));
					queue.sendMessageToSelf(new SolutionMessage<V> (projOutput.varsOut[0], projOutput.varsOut, projOutput.getAssignments()));
					
				} else { // CC; no projection needed
					assert info.parent != null;
					String owner = this.problem.getOwner(info.parent);
					if (! owner.equals(this.myID)) 
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage (join.getNumberOfVariables()));
					queue.sendMessage(owner, new UTILmsg<V, U> (self, this.myID, info.parent, join));
					
					// Construct the optimal assignment space
					if (join.getNumberOfVariables() == 0) 
						queue.sendMessageToSelf(new SolutionMessage<V> (self, new String[] { self }, new ScalarBasicHypercube< V, ArrayList<V> > (new ArrayList<V> (Arrays.asList(optVal)), null)));
						
					else {
						ArrayList<V>[] optUtils = new ArrayList [(int) join.getNumberOfSolutions()];
						Arrays.fill(optUtils, new ArrayList<V> (Arrays.asList(optVal)));
						BasicHypercube< V, ArrayList<V> > optVals = new BasicHypercube< V, ArrayList<V> > (join.getVariables(), join.getDomains(), optUtils, null);
						queue.sendMessageToSelf(new SolutionMessage<V> (self, new String[] { self }, optVals));
					}
				}
			}
						
		} else { // I am a CR; update my ProjOutput
			
			if (varInfo.projOutput == null) {

				varInfo.projOutput = new ProjOutput<V, U> (join.resolve(), varInfo.context, null);
				ArrayList<V> opt = new ArrayList<V> (Arrays.asList(varInfo.contextValues));
				
				// Check whether I am also the root of the pseudo-tree
				if (varInfo.parent == null) 
					varInfo.projOutput.assignments = new ScalarBasicHypercube< V, ArrayList<V> > (opt, null);
					
				else { // not the root of the pseudo-tree

					varInfo.sepValues = (V[]) Array.newInstance(join.getDomain(0).getClass().getComponentType(), join.getNumberOfVariables());

					// Store the current context values as the best found so far
					assert join.getNumberOfSolutions() < Long.MAX_VALUE : "Too many solutions in the following space: " + join;
					int nbrSols = (int) join.getNumberOfSolutions();
					ArrayList<V>[] optValues = new ArrayList [nbrSols];
					Arrays.fill(optValues, opt);
					BasicHypercube< V, ArrayList<V> > optAssignments = new BasicHypercube< V, ArrayList<V> > (join.getVariables(), join.getDomains(), optValues, null);
					varInfo.projOutput.assignments = optAssignments;
				}

			} else { // improve the ProjOutput if possible
				
				// Loop over the possible assignments to my separator
				UtilitySolutionSpace<V, U> outSpace = varInfo.projOutput.space;
				Iterator<V, U> outIter = outSpace.iterator(outSpace.getVariables(), outSpace.getDomains(), varInfo.sepValues);
				Iterator<V, U> joinIter = join.iterator(outSpace.getVariables(), outSpace.getDomains(), varInfo.sepValues);
				ArrayList<V> newOpt = new ArrayList<V> (Arrays.asList(varInfo.contextValues));
				while (outIter.hasNext()) {
					U newUtil = joinIter.nextUtility();
					if (super.maximize ? newUtil.compareTo(outIter.nextUtility()) > 0 : newUtil.compareTo(outIter.nextUtility()) < 0) {
						varInfo.projOutput.space.setUtility(outIter.getCurrentSolution(), newUtil);
						varInfo.projOutput.assignments.setUtility(outIter.getCurrentSolution(), newOpt);
					}
				}
			}
			
//			System.out.println(varInfo.projOutput);

			if (varInfo.iter.hasNext()) // keep iterating over the possible context values
				this.queue.sendMessageToSelf(new ContextMsg<V> (self, null, varInfo.iter.nextSolution()));
				
			else { // I am ready to send up my UTIL message
				
				if (varInfo.parent != null) {
					String owner = this.problem.getOwner(info.parent);
					if (! owner.equals(this.myID)) 
						queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage (varInfo.projOutput.space.getNumberOfVariables()));
					this.queue.sendMessage(owner, new UTILmsg<V, U> (self, this.myID, info.parent, varInfo.projOutput.space));
				
				} else { // root of pseudo-tree
					assert varInfo.projOutput.space.getNumberOfVariables() == 0 : "Space output by the root " + self + " is not scalar:\n" + varInfo.projOutput.space;
					OptUtilMessage<U> output = new OptUtilMessage<U> (varInfo.projOutput.space.getUtility(0), self);
					queue.sendMessageToSelf(output);
					queue.sendMessage(AgentInterface.STATS_MONITOR, output);
				}
				
				// Send optimal assignments to the VALUE propagation protocol
				queue.sendMessageToSelf(new SolutionMessage<V> (varInfo.projOutput.varsOut[0], varInfo.projOutput.varsOut, varInfo.projOutput.assignments));
			}
		}
		
		// Forget the sliced spaces received from cluster children
		varInfo.sliceIn = null;
		info.nbrUTIL -= varInfo.ccsByChild.size();
	}
	
	/** @see UTILpropagation#sendSeparator(java.lang.String, String, java.lang.String, java.lang.String[]) */
	@Override
	protected void sendSeparator (String senderVar, String senderAgent, String dest, String[] separator) {
		return; // the separator is already sent by the LabelingPhase
	}
	
	/** @see UTILpropagation#record(java.lang.String, UtilitySolutionSpace, UTILpropagation.ClusterInfo) */
	@Override
	protected void record(String senderVar, UtilitySolutionSpace<V, U> space, ClusterInfo info) {

		// Only treat this space differently if it was sent by a cluster child
		if (senderVar == null) { // local constraint
			super.record(senderVar, space, info);
			return;
		}
		@SuppressWarnings("unchecked")
		VarInfo varInfo = (VarInfo) info;
		if (! varInfo.ccsByChild.containsKey(senderVar)) { // sent by a child that is not part of my cluster
			super.record(senderVar, space, info);
			return;
		}
		
		if (varInfo.sliceIn == null) 
			varInfo.sliceIn = space;
		else 
			varInfo.sliceIn = varInfo.sliceIn.join(space);
	}

}
