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

package frodo2.algorithms.odpop;

import java.util.List;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeBinaryDomains.InnerNodeTree;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode;
import frodo2.algorithms.odpop.goodsTree.leafNodeTree.LeafNodeTree;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** The UTIL propagation phase for O-DPOP
 * @author brammert
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * 
 * @todo make it work for minimisation problems as well!
 *
 */
public class UTILpropagationBinaryDomains < Val extends Addable<Val>, U extends Addable<U> > extends UTILpropagationFullDomain<Val, U, LeafNode<U>> {

	/**
	 * Constructor for the statsreporter
	 * 
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported (currently unused)
	 */
	public UTILpropagationBinaryDomains (Element parameters, DCOPProblemInterface<Val, U> problem) {
		super(parameters, problem);
	}

	/**
	 * Constructor
	 * 
	 * @param problem		The problem description
	 * @param parameters	The parameters
	 */
	public UTILpropagationBinaryDomains (DCOPProblemInterface<Val, U> problem, Element parameters) {
		super(problem, parameters);
	}

	/**
	 * Alternative constructor not using XML
	 * 
	 * @param problem		The problem description
	 */
	public UTILpropagationBinaryDomains (DCOPProblemInterface<Val, U> problem) {
		super(problem);
	}

	/** @see frodo2.algorithms.odpop.UTILpropagationFullDomain#newVariableInfoInstanceInnerNode(java.lang.String, java.util.List, java.lang.String, Val[], java.util.List, int, frodo2.solutionSpaces.Addable) */
	public VariableInfo newVariableInfoInstanceInnerNode(String var, List<String> children, String parent, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> usedSpaces, int numberOfChildren, U zeroUtility) {
		return new VariableInfo(var, parent, children.toArray(new String[0]), new InnerNodeTree<Val, U, LeafNode<U>>(var, ownVariableDomain, usedSpaces, numberOfChildren, problem.getZeroUtility(), leafNodeInstance(), infeasibleUtil, maximize, collectStats));
	}
	
	/** @see frodo2.algorithms.odpop.UTILpropagationFullDomain#newVariableInfoInstanceLeafNode(java.lang.String, java.util.List, java.lang.String, Val[], java.util.List, int, frodo2.solutionSpaces.Addable) */
	public VariableInfo newVariableInfoInstanceLeafNode(String var, List<String> children, String parent, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> usedSpaces, int numberOfChildren, U zeroUtility) {
		return new VariableInfo(var, parent, children.toArray(new String[0]), new LeafNodeTree<Val, U, LeafNode<U>>(var, ownVariableDomain, usedSpaces, problem.getZeroUtility(), infeasibleUtil, maximize, collectStats));
	}

	/** @see frodo2.algorithms.odpop.UTILpropagationFullDomain#utilPropagationFinished(java.lang.String, frodo2.algorithms.odpop.goodsTree.GoodsTree, frodo2.solutionSpaces.Addable, int) */
	@Override
	protected void utilPropagationFinished(String variableID, GoodsTree<Val, U, LeafNode<U>> tree, U utility, int domainSize) {
		queue.sendMessage(AgentInterface.STATS_MONITOR, new OptUtilMessage<U>(utility, variableID));
		// start the VALUE propagation
		queue.sendMessageToSelf(new GoodsTreeMessage<Val, U, LeafNode<U>>(tree, variableID));
	}
}
