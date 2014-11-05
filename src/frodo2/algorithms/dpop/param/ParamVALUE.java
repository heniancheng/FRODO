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

package frodo2.algorithms.dpop.param;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.dpop.UTILpropagation.SolutionMessage;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.hypercube.Hypercube;

/** VALUE propagation protocol
 * @author Thomas Leaute
 * @param <Val> type used for variable values
 * @todo Improve the implementation by reasoning on groups of variables to be projected together. 
 */
public class ParamVALUE < Val extends Addable<Val> > 
extends VALUEpropagation<Val> {

	/** The type of the VALUE messages */
	public static final String PARAM_VALUE_MSG_TYPE = "ParamVALUEmessage";
	
	/** The type of the output messages containing the optimal assignment to a variable */
	public static final String PARAM_OUTPUT_MSG_TYPE = "OutputMessageParamVALUE";
	
	/** For each variable, the VALUE message received containing its separator's optimal assignments */
	private HashMap< String, VALUEmsg<Val> > valueMessages = new HashMap< String, VALUEmsg<Val> > ();
	
	/** For an array of variables, their optimal values, conditioned on the values of the parameters (if any) 
	 * @todo It might be more efficient to store a single BasicUtilitySolutionSpace holding the information about all variables. 
	 */
	private HashMap< String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > solution = new HashMap< String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > ();
	
	/** The subset of this agent's variables that are roots */
	private HashSet<String> roots = new HashSet<String> ();

	/** A message holding the optimal assignment to some variables, conditioned on the values of the parameters (if any)
	 * @param <Val> type used for variable values
	 */
	public static class AssignmentMessage < Val extends Addable<Val> >
	extends MessageWith2Payloads < String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > {

		/** Empty constructor used for externalization */
		public AssignmentMessage () { }

		/** Constructor 
		 * @param vars 		the variables
		 * @param val 		the optimal value assigned to the variable \a var, conditioned on the values of the parameters (if any)
		 */
		public AssignmentMessage (String[] vars, BasicUtilitySolutionSpace< Val, ArrayList<Val> > val) {
			super (PARAM_OUTPUT_MSG_TYPE, vars, val);
		}
		
		/** @return the variables */
		public String[] getVariables () {
			return this.getPayload1();
		}
		
		/** @return the optimal values, conditioned on the values of the parameters (if any) */
		public BasicUtilitySolutionSpace< Val, ArrayList<Val> > getValues () {
			return this.getPayload2();
		}
		
		/** @see Message#fakeSerialize() */
		@Override
		public void fakeSerialize () {
			super.setPayload2(super.getPayload2().resolve());
		}
	}
	
	/** Manual constructor that does not use XML elements
	 * @param problem 		the problem
	 * @param swap 			if \c true, conditional optimal assignments are swapped until the VALUE message is received
	 */
	public ParamVALUE (DCOPProblemInterface< Val, ? extends Addable<?> > problem, Boolean swap) {
		super (problem, swap);
	}
	
	/** Constructor from XML elements
	 * @param problem 		description of the problem
	 * @param parameters 	not used because ParamVALUE cannot be parameterized
	 */
	public ParamVALUE (DCOPProblemInterface< Val, ? extends Addable<?> > problem, Element parameters) {
		super (problem, parameters);
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public ParamVALUE (Element parameters, DCOPProblemInterface<Val, ?> problem) { 
		super (parameters, problem);
	}
	
	/** @see VALUEpropagation#reset() */
	public void reset () {
		super.reset();
		roots = new HashSet<String> ();
		solution = new HashMap< String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > ();
		valueMessages = new HashMap< String, VALUEmsg<Val> > ();
	}
	
	/** @see frodo2.algorithms.dpop.VALUEpropagation#getMsgTypes() */
	@Override
	public Collection <String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (6);
		types.add(START_MSG_TYPE);
		types.add(ParamUTIL.OUTPUT_MSG_TYPE);
		types.add(ParamUTIL.SEPARATOR_MSG_TYPE);
		types.add(PARAM_VALUE_MSG_TYPE);
		types.add(DFSgeneration.OUTPUT_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** @see VALUEpropagation#notifyIn(Message) */
	@Override
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(PARAM_OUTPUT_MSG_TYPE)) { // we are in stats gatherer mode
			
			AssignmentMessage<Val> msgCast = (AssignmentMessage<Val>) msg;
			if (!silent) 
				System.out.println("variables " + Arrays.asList(msgCast.getVariables()) + " = " + msgCast.getValues());
			solution.put(msgCast.getVariables(), msgCast.getValues());

			return;
		}
		
		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this hasn't been done yet
		if (! this.started) 
			init();
		
		if (type.equals(ParamUTIL.OUTPUT_MSG_TYPE)) { // the message contains the conditional optimal value assignments to a variable
			
			// Extract the information from the message
			ParamUTIL.SolutionMessage<Val> msgCast = (ParamUTIL.SolutionMessage<Val>) msg;
			String[] vars = msgCast.getVariables();
			String myVar = vars[0];
			
			// Check if this variable is a root
			if (roots.contains(myVar)) {
				
				// Simulate the receipt of an empty VALUE message for this root variable
				valueMessages.put(myVar, new VALUEmsg<Val> (myVar, new String[0], null));
			}
			
			// Check whether the VALUE message for this variable has been received
			VALUEmsg<Val> valueMsg = valueMessages.remove(myVar);
			if (valueMsg != null) {
				
				// Compute the optimal value for this variable, and send VALUE messages to its children (if possible)
				computeOptValAndSendVALUEmsgs(vars, msgCast.getCondOptAssignments(), valueMsg);
			}
			
			else { // the VALUE message for this variable has not been received yet
				
				// Store the conditional optimal assignments until we receive the VALUE message
				condAssignments.put(myVar, msgCast);
			}
		}

		else if (type.equals(ParamUTIL.SEPARATOR_MSG_TYPE)) { // the message contains the separator for a given child variable
			
			// Extract the information from the message
			ParamUTIL.SeparatorMessage msgCast = (ParamUTIL.SeparatorMessage) msg;
			String parent = msgCast.getParent();
			
			// Check whether we have already computed the optimal assignment to this variable
			for (Map.Entry< String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > entry : this.solution.entrySet()) {
				String[] vars = entry.getKey();
				if (vars[0].equals(parent)) { // found the optimal assignment
					String child = msgCast.getChild();
					
					// Compute and send the VALUE message
					sendVALUEmessage(child, msgCast.getSeparator(), vars, entry.getValue(), valueMessages.remove(parent));
					
					// Remove the child from the list of children
					List<String> children = allChildren.get(parent);
					children.remove(child);
					
					// Check if the agent can terminate
					if (children.isEmpty() && ++super.nbrVarsDone >= super.problem.getNbrIntVars()) 
						this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
					
					return;
				}
			}
			
			// Store the separator until we have enough information to compute the corresponding VALUE message
			separators.put(msgCast.getChild(), msgCast.getSeparator());
		}
		
		else if (type.equals(PARAM_VALUE_MSG_TYPE)) { // the VALUE message
			
			// Extract the information from the message
			VALUEmsg<Val> msgCast = (VALUEmsg<Val>) msg;
			String var = msgCast.getDest();
			
			// Check whether we have received the conditional optimal assignments for this variable
			SolutionMessage<Val> solMsg = condAssignments.remove(var);
			if (solMsg != null) {
				
				// Compute the optimal value for this variable, and send VALUE messages to its children (if possible)
				computeOptValAndSendVALUEmsgs(solMsg.getVariables(), solMsg.getCondOptAssignments(), msgCast);
			}
			
			else { // we haven't received the conditional optimal assignments for this variable yet
				
				// Store the VALUE message until we can make use of it
				valueMessages.put(var, msgCast);
			}
		}
		
		else if (type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // the message contains information about the children of one of my variables
			
			// Extract and record the information from the message
			DFSgeneration.MessageDFSoutput<Val, ?> msgCast = (DFSgeneration.MessageDFSoutput<Val, ?>) msg;
			String var = msgCast.getVar();
			DFSview<Val, ?> relationships = msgCast.getNeighbors();
			allChildren.put(var, new ArrayList<String> (relationships.getChildren()));
			
			// If the variable is a root, record it as such
			if (relationships.getParent() == null) 
				roots.add(var);
		}
		
	}

	/** @see frodo2.algorithms.dpop.VALUEpropagation#getStatsFromQueue(frodo2.communication.Queue) */
	@Override
	public void getStatsFromQueue(Queue queue) {
		ArrayList <String> msgTypes = new ArrayList <String> (1);
		msgTypes.add(PARAM_OUTPUT_MSG_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}

	/** Instantiates a VALUE message and sends it
	 * @param child 		destination variable of the message
	 * @param separator 	separator of \a child, parameters included
	 * @param vars 			the variables whose optimal values have just been computed
	 * @param opt 			optimal values for variables in \a vars, conditioned on the values of the parameters (if any)
	 * @param valueMsg 		VALUE message received from parent 
	 */
	private void sendVALUEmessage(String child, String[] separator, String[] vars, BasicUtilitySolutionSpace< Val, ArrayList<Val> > opt, VALUEmsg<Val> valueMsg) {
		
		String[] variablesIn = valueMsg.getVariables();
		BasicUtilitySolutionSpace< Val, ArrayList<Val> > valuesIn = valueMsg.getValues();
		
		if (valuesIn == null) { // we are at the root
			queue.sendMessage(this.problem.getOwner(child), new VALUEmsg<Val>(child, new String[] { vars[0] }, opt));
			return;
		}
		
		BasicUtilitySolutionSpace< Val, ArrayList<Val> > valuesOut = valuesIn.clone();
		String[] parameters = valuesIn.getVariables();
		opt = opt.changeVariablesOrder(parameters);
		
		// Remove the parameters from the separator
		/// @todo Remove dependency on Hypercube. 
		separator = Hypercube.sub(separator, parameters);
		
		// For each assignment to the parameters
		long nbrParamCases = valuesOut.getNumberOfSolutions();
		for (long k = 0; k < nbrParamCases; k++) {
			
			// Get the optimal assignments to the parent's separator for the current parametric values
			ArrayList<Val> optVarSep = valuesIn.getUtility(k);
			
			// Fill in the array of assignments
			ArrayList<Val> assign = new ArrayList<Val> (separator.length);
			loopI: for (int i = 0; i < separator.length; i++) {
				String sepVar = separator[i];
				
				// Look for this variable in vars
				/// @todo Performance improvement: do this before entering the for loop on k
				for (int j = vars.length - 1; j >= 0; j--) {
					if (sepVar.equals(vars[j])) {
						ArrayList<Val> best = opt.getUtility(k);
						assign.add(best != null ? best.get(j) : null);
						continue loopI;
					}
				}
				
				// Look for this variable in the parent's separator
				for (int j = variablesIn.length - 1; j >= 0; j--) {
					if (variablesIn[j].equals(sepVar)) {
						assign.add(optVarSep.get(j));
						break;
					}
				}
			}
			
			valuesOut.setUtility(k, assign);
		}
		
		// Send the corresponding new VALUE message
		queue.sendMessage(this.problem.getOwner(child), new VALUEmsg<Val>(child, separator, valuesOut));
	}

	/** Compute the optimal assignment to some variables, and sends VALUE messages to children accordingly
	 * @param vars the variables to be optimized
	 * @param optAssignments the conditional optimal assignments to variable \a var
	 * @param valueMsg the VALUE message received for variable \a var
	 */
	private void computeOptValAndSendVALUEmsgs(String[] vars, BasicUtilitySolutionSpace< Val, ArrayList<Val> > optAssignments, 
			VALUEmsg<Val> valueMsg) {
		
		// Compute the optimal assignments to the variables (conditioned on the values of the parameters), record them and output them
		BasicUtilitySolutionSpace< Val, ArrayList<Val> > optVals;
		BasicUtilitySolutionSpace< Val, ArrayList<Val> > substitution = valueMsg.getValues();
		String[] separator = valueMsg.getVariables();
		if (substitution == null) { // var is a root 
			optVals = optAssignments;
		} else 
			optVals = optAssignments.compose(separator, substitution);
		solution.put(vars, optVals);
		queue.sendMessage(AgentInterface.STATS_MONITOR, new AssignmentMessage<Val> (vars, optVals));
		
		// Go through the list of children of the first variable 
		// For each child, check if we already know its separator 
		List<String> children = allChildren.get(vars[0]);
		for (Iterator<String> iterator = children.iterator(); iterator.hasNext(); ) {
			String child = iterator.next();
			separator = separators.remove(child);
			if (separator != null) {

				// Send VALUE message to this child
				sendVALUEmessage(child, separator, vars, optVals, valueMsg);

				// Remove the child from the list of children
				iterator.remove();
			}
		}
		
		// Check if the agent can terminate
		if (children.isEmpty() && ++this.nbrVarsDone >= this.problem.getNbrIntVars()) 
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
	}

	/** @return for each variable, its assignment in the optimal solution found to the problem conditioned on the parameters */
	public Map< String[], BasicUtilitySolutionSpace< Val, ArrayList<Val> > > getParamSolution () {
		return this.solution;
	}

}
