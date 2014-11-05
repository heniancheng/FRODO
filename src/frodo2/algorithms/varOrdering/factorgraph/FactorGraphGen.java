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

package frodo2.algorithms.varOrdering.factorgraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A factor graph generator
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values in stats gatherer mode
 */
public class FactorGraphGen < V extends Addable<V>, U extends Addable<U> > implements StatsReporter {
	
	/** The type of the output message */
	public static final String OUTPUT_MSG_TYPE = "FactorGraph";
	
	/** The problem */
	private DCOPProblemInterface<V, U> problem;
	
	/** This agent's queue */
	private Queue queue;
	
	/** Whether the agent must compile all its function nodes into one */
	private final boolean compile;

	/** Constructor
	 * @param problem 	this agent's problem
	 * @param params 	the parameters for this module
	 */
	public FactorGraphGen (DCOPProblemInterface<V, U> problem, Element params) {
		this.problem = problem;
		this.compile = Boolean.parseBoolean(params.getAttributeValue("compile"));
	}
	
	/** Constructor in stats gatherer mode
	 * @param params 	the parameters
	 * @param problem 	the overall problem
	 */
	public FactorGraphGen (Element params, DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.compile = Boolean.parseBoolean(params.getAttributeValue("compile"));

		// Display the factor graph if required
		if (params != null && Boolean.parseBoolean(params.getAttributeValue("reportStats"))) {
			
			String dotRendererClass = params.getAttributeValue("DOTrenderer");
			if(dotRendererClass == null || dotRendererClass.equals("")) {
				System.out.println("Factor graph:");
				System.out.println(factorGraphToDOT());
			}
			else {
				try {
					Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("Factor graph", factorGraphToDOT());
				} 
				catch(Exception e) {
					System.out.println("Could not instantiate given DOT renderer class: " + dotRendererClass);
					e.printStackTrace();
				}
			}
		}
	}

	/** @return a DOT-formatted representation of the factor graph */
	private String factorGraphToDOT() {
		
		List< ? extends UtilitySolutionSpace<V, U> > allSpaces = this.problem.getSolutionSpaces(true);
		
		StringBuilder out = new StringBuilder ("strict graph {\n");
		
		// Print the agents, with their respective variable nodes and function nodes
		for (String agent : this.problem.getAgents()) {
			
			out.append("\tsubgraph cluster_" + agent + " {\n");
			out.append("\t\tlabel = " + agent + ";\n");
			
			// Print the agent's variable nodes
			Map< String, Collection<String> > agentNeighborhoods = this.problem.getAgentNeighborhoods();
			ArrayList<String> myVars = new ArrayList<String> ();
			for (Map.Entry< String, Collection<String> > entry : agentNeighborhoods.entrySet()) {
				String var = entry.getKey();
				String owner = this.problem.getOwner(var);
				if (agent.equals(owner) || owner == null && agent.equals(new TreeSet<String> (entry.getValue()).iterator().next())) {
					out.append("\t\t" + var + " [shape = \"circle\"];\n");
					myVars.add(var);
				}
			}
			out.append("\n");
					
			// Go through all constraints to find the ones this agent is responsible for 
			boolean noFunction = true;
			for (UtilitySolutionSpace<V, U> space : allSpaces) {
				if (agent.equals(space.getOwner()) || space.getOwner() == null && agent.equals(this.problem.getOwner(space.getVariable(0)))) {
					if (this.compile) 
						space.setName(agent);
					out.append("\t\t" + space.getName() + " [shape = \"square\"];\n");
					noFunction = false;
				}
			}

			// Add an invisible variable if the agent owns no variable node and no function node, so that it is still displayed
			if (noFunction && myVars.isEmpty()) 
				out.append("\t\t" + new Object().hashCode() + " [shape=\"none\", label=\"\"];\n");
			
			out.append("\t}\n");
		}
		out.append("\n");
		
		// Print the edges between variable nodes and function nodes
		for (UtilitySolutionSpace<V, U> space : allSpaces) {
			for (String var : space.getVariables()) 
				out.append("\t" + space.getName() + "--" + var + ";\n");
		}
		out.append("\n");
				
		out.append("}\n");
		
		return out.toString();
	}

	/** @see StatsReporter#notifyIn(Message) */
	@Override
	public void notifyIn(Message msg) {
		
		// Look for this agent's internal and neighboring variable nodes
		Map< String, Collection<String> > agentNeighborhoods = this.problem.getAgentNeighborhoods(null);
		HashMap< String, VariableNode<V, U> > variables = new HashMap< String, VariableNode<V, U> > (agentNeighborhoods.size());
		final String myName = this.problem.getAgent();
		Map< String, TreeSet<String> > sharedVarsScopes = new HashMap< String, TreeSet<String> > ();
		for (Map.Entry< String, Collection<String> > entry : agentNeighborhoods.entrySet()) {
			String var = entry.getKey();
			String owner = this.problem.getOwner(var);
			if (owner == null) { // shared variable
				TreeSet<String> scope = new TreeSet<String> (entry.getValue());
				owner = scope.iterator().next(); // assign this shared variable to its first neighboring agent
				sharedVarsScopes.put(var, scope);
			}
			variables.put(var, new VariableNode<V, U> (var, owner, this.problem.getDomain(var)));
		}
				
		// Look for this agent's internal and neighboring function nodes
		HashMap< String, LinkedList< UtilitySolutionSpace<V, U> > > newSpaces = new HashMap< String, LinkedList< UtilitySolutionSpace<V, U> > > ();
		for (UtilitySolutionSpace<V, U> space : this.problem.getSolutionSpaces(true)) {
			
			// Check which agent must simulate this factor node
			String owner = space.getOwner();
			if (! myName.equals(owner)) // I am not the exclusive owner of this space
				owner = this.problem.getOwner(space.getVariable(0)); // by convention, the function node gets simulated by the owner of the first variable in the scope
			
			LinkedList< UtilitySolutionSpace<V, U> > spaces = newSpaces.get(owner);
			if (spaces == null) {
				spaces = new LinkedList< UtilitySolutionSpace<V, U> > ();
				newSpaces.put(owner, spaces);
			}
			
			if (this.compile) {
				if (! spaces.isEmpty()) 
					space = space.join(spaces.removeLast());
				space.setOwner(owner);
				space.setName(owner);
			}
			
			spaces.add(space);
		}
		
		// Terminate immediately if I control no variable node and no function node
		if (variables.isEmpty() && newSpaces.isEmpty()) {
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			return;
		}

		// Go through all constraints in this agent's subproblem
		HashMap< String, FunctionNode<V, U> > functions = new HashMap< String, FunctionNode<V, U> > ();
		for (Map.Entry< String, LinkedList< UtilitySolutionSpace<V, U> > > entry : newSpaces.entrySet()) {
			String owner = entry.getKey();
			for (UtilitySolutionSpace<V, U> space : entry.getValue()) {

				FunctionNode<V, U> function = new FunctionNode<V, U> (space.getName(), (myName.equals(owner) ? (UtilitySolutionSpace<V, U>) space : null), owner);
				functions.put(function.getName(), function);

				// Check which variables are involved in this function
				for (String var : space.getVariables()) {
					VariableNode<V, U> varNode = variables.get(var);
					if (varNode != null) {
						varNode.addFunction(function);
						TreeSet<String> scope = sharedVarsScopes.get(var);
						if (scope != null) 
							scope.remove(function.getName());
					}
				}
			}
		}
		
		// Add function nodes for the scopes of shared variables
		for (Map.Entry< String, TreeSet<String> > entry : sharedVarsScopes.entrySet()) {
			VariableNode<V, U> varNode = variables.get(entry.getKey());
			for (String agent : entry.getValue()) 
				varNode.addFunction(new FunctionNode<V, U> (agent, null, agent));
		}
		
		this.queue.sendMessageToSelf(new MessageWith2Payloads < HashMap< String, VariableNode<V, U> >, HashMap< String, FunctionNode<V, U> > > (
				OUTPUT_MSG_TYPE, variables, functions));
	}

	/** @see StatsReporter#setQueue(Queue) */
	@Override
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getMsgTypes() */
	@Override
	public Collection<String> getMsgTypes() {
		return Arrays.asList(AgentInterface.START_AGENT);
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	@Override
	public void getStatsFromQueue(Queue queue) { }

	/** @see frodo2.algorithms.StatsReporter#setSilent(boolean) */
	@Override
	public void setSilent(boolean silent) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see StatsReporter#reset() */
	@Override
	public void reset() { }

}
