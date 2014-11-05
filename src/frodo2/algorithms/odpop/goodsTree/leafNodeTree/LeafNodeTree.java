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

package frodo2.algorithms.odpop.goodsTree.leafNodeTree;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;

import frodo2.algorithms.odpop.Good;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * @author Brammert Ottens, 9 nov 2009
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @param <L> type used for the leaf node
 * @todo write tests for this class
 */
public class LeafNodeTree < Val extends Addable<Val>, U extends Addable<U>, L extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node<U> >
extends GoodsTree<Val, U, L> implements Serializable {

	/** Used for serialisation */
	private static final long serialVersionUID = -4372313666234992912L;

	/** The root of the tree */
	protected Node root;
	
	/** \c true when orderedAssignments is not empty, and \c false otherwise */
	protected boolean hasMore;
	
	/** The variables in \c ownVariable's separator*/
	protected String[] separatorVariables;
	
	/** The index of every variable in \c variables*/
	private HashMap<String, Integer> variableToDepth;
	
	/** For each variable the size of its domain */
	protected int[] domainSize;
	
	/** for each variables, the child index of their values */
	protected HashMap<String, HashMap<Val, Integer>> valuePointers;
	
	/** \c true when the final domain size information has not yet been requested, and \c false otherwise */
	private boolean stillToSend;
	
	/** The currently optimal assignment to the local problem*/
	protected Val[] localAssignment;
	
	/** \c true when the variable that owns this tree is a singleton, i.e. has no parents or children */
	protected boolean singleton;
	
	/**
	 * A constructor
	 * @warning we assume that the agent's own variable is put in the end of variables_order
	 * @param ownVariable 		The variable ID
	 * @param ownVariableDomain The domain of \c ownVariable
	 * @param space				The hypercube representing the local problem
	 * @param zero 				The zero utility
	 * @param infeasible 		The infeasible utility
	 * @param maximize 			when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats		\c true when statistics should be collected, and \c false otherwise
	 */
	public LeafNodeTree( String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, U zero, U infeasible, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, space, zero, 0, infeasible, maximize, collectStats);
		
		numberOfSpaces = 1;
		init(zero);
		
		solveLocalProblem();
	}
	
	/**
	 * A constructor
	 * @warning we assume that the agents own variable is put in the end of variables_order
	 * @param ownVariable 		The variable ID
	 * @param ownVariableDomain The domain of \c ownVariable
	 * @param spaces			The hypercubes representing the local problem
	 * @param zero 				The zero utility
	 * @param infeasible 		The infeasible utility
	 * @param maximize 			when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats		\c true when statistics should be collected, and \c false otherwise
	 */
	public LeafNodeTree( String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, U zero, U infeasible, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, spaces, zero, 0, infeasible, maximize, collectStats);
		
		init(zero);
		
		if(numberOfSpaces > 0)
			solveLocalProblem();
	}
	
	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getAmax()
	 */
	@SuppressWarnings("unchecked")
	public Good<Val, U> getAmax() {
		
		Val[] assignment = null;
		U utility = null;
		
		// get the next aMax that is not compatible with an already sent assignment
		while(localProblemIterator.hasNext()) {
			assignment = this.localProblemIterator.nextSolution();
			utility = localProblemIterator.getCurrentUtility();
			
			if(singleton || (utility != null && !this.pathExists(assignment, depthFinalVariable)))
				break;
			else {
				assignment = null;
				utility = null;
			}
		}
		if(assignment == null) {
			hasMore = false;
			return null;
		}
		
		if(!singleton)
			// store the aMax in the tree
			addToTree(assignment, 0, root);
		else
			localAssignment = assignment;

		Val[] values = (Val[]) Array.newInstance(assignment.getClass().getComponentType(), depthFinalVariable);
		System.arraycopy(assignment, 0, values, 0, depthFinalVariable);
		
		if(this.COLLECT_STATISTICS)
			this.countGoodsProduced();
		return new Good<Val, U>(separatorVariables, values, utility);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getBestAssignmentForOwnVariable(java.util.HashMap)
	 */
	public void getBestAssignmentForOwnVariable(HashMap<String, Val> context) {
		this.getOwnAssignment(context, root, 0);
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getFinalDomainSize()
	 */
	public int[] getFinalDomainSize() {
		stillToSend = false;
		int[] output = new int[depthFinalVariable];
		System.arraycopy(domainSize, 0, output, 0, depthFinalVariable);
		return output;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getDomains()
	 */
	@SuppressWarnings("unchecked")
	public Val[][] getDomains() {
		Val[][] doms = (Val[][]) new Addable[this.numberOfVariables][];
		
		for(int i = 0; i < numberOfVariables; i++) {
			doms[i] = localProblem.getDomain(depthToVariable[i]);
		}
		
		return doms;
	}
	
	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#hasFullInfo()
	 */
	public boolean hasFullInfo() {
		return hasMore;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#hasMore()
	 */
	public boolean hasMore() {
		return hasMore;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#isValuationSufficient()
	 */
	public boolean isValuationSufficient() {
		return true;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#knowsVariable(java.lang.String)
	 */
	public boolean knowsVariable(String variable) {
		return valuePointers.containsKey(variable);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#notEnoughInfo()
	 */
	public boolean notEnoughInfo() {
		return numberOfVariables == 1;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#pathExists(Val[])
	 */
	public boolean pathExists(Val[] values) {
		Node currentNode = root;
		
		for(int depth = 0; depth < numberOfVariables; depth++) {
			int childIndex = valuePointers.get(depthToVariable[depth]).get(values[depth]);
			
			currentNode = currentNode.getChild(childIndex);
			
			if(currentNode == null)
				return false;
			
		}
				
		return true;
	}

	/** 
	 * This function is not used in this class
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#removeAMax()
	 */
	public void removeAMax() {}

	/** 
	 * This function is not used in this class
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#setFinalDomainSize(java.lang.String[], int[])
	 */
	public void setFinalDomainSize(String[] variables, int[] domainSize) {}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#stillToSend()
	 */
	public boolean stillToSend() {
		return stillToSend;
	}
	
	/**
	 * Adds a reported value to the goods tree
	 * 
	 * @author Brammert Ottens, 9 nov 2009
	 * @param assignment	The reported assignment
	 * @param depth			The current depth in the tree
	 * @param currentNode	The current node being visited
	 */
	protected void addToTree(Val[] assignment, int depth, Node currentNode) {
		int childIndex = valuePointers.get(depthToVariable[depth]).get(assignment[depth]);
		int nextDepth = depth + 1;
		
		if(depth == depthFinalVariable-1) {
			assert currentNode.getChild(childIndex) == null; // nothing should have been reported
			LeafNode<Val> leaf = new LeafNode<Val>(assignment[depth + 1]);
			if(this.COLLECT_STATISTICS)
				this.countLeafNode();
			currentNode.addChild(leaf, childIndex);
		} else {
			Node child = currentNode.getChild(childIndex);
			if(child == null) {
				child = new Node(domainSize[nextDepth]);
				currentNode.addChild(child, childIndex);
			}
			addToTree(assignment, nextDepth, child);
		}
	}
	
	/**
	 * Given an assignment to all the variables in the variables separator, this method
	 * finds and returns the optimal value of the tree's own variable
	 * 
	 * @author Brammert Ottens, 9 nov 2009
	 * @param assignment		The partial assignment
	 * @param currentNode		The current node being visited
	 * @param depth				The current depth in the tree
	 */
	@SuppressWarnings("unchecked")
	protected void getOwnAssignment(HashMap<String, Val> assignment, Node currentNode, int depth) {
		if(this.numberOfVariables == 1) {
			assignment.put(ownVariable, localAssignment[0]);
		} else {
			String var = depthToVariable[depth];
			int childIndex = valuePointers.get(var).get(assignment.get(var));

			if(depth == depthFinalVariable - 1) {
				LeafNode<Val> leaf = (LeafNode<Val>)currentNode.getChild(childIndex);
				assert leaf != null;
				assignment.put(ownVariable, leaf.getValue());
			} else {
				Node child = currentNode.getChild(childIndex);
				assert child != null;
				getOwnAssignment(assignment, child, depth + 1);
			}
		}
	}
	
	/**
	 * Method to initialize all the variables
	 * 
	 * @author Brammert Ottens, 9 nov 2009
	 * @param zero					The zero utility
	 */
	private void init(U zero) {
				
		hasMore = true;
		stillToSend = true;
		domainSize = new int[numberOfVariables];
		valuePointers = new HashMap<String, HashMap<Val, Integer>>(numberOfVariables);
		separatorVariables = new String[depthFinalVariable];
		System.arraycopy(depthToVariable, 0, separatorVariables, 0, depthFinalVariable);
		variableToDepth = new HashMap<String, Integer>(numberOfVariables);
		singleton = numberOfVariables == 1;
		
		for(int i = 0; i < numberOfVariables; i++) {
			String var = depthToVariable[i];
			variableToDepth.put(var, i);
			Val[] dom = null;
			if(i < depthFinalVariable)
				dom = localProblem.getDomain(var);
			else
				dom = ownVarDomain;
			
			int domainSize = dom.length;
			totalSeparatorSpaceSize *= domainSize;
			this.domainSize[i] = domainSize;
			HashMap<Val, Integer> pointer = new HashMap<Val, Integer>(domainSize);
			for(int j = 0; j < domainSize; j++) {
				pointer.put(dom[j], j);
			}
			valuePointers.put(var, pointer);
		}

		if(numberOfVariables == 1)
			root = new Node(ownVarDomain.length);
		else
			root = new Node(localProblem.getDomain(depthToVariable[0]).length);
	}
	
	
	/**
	 * Checks whether a path exists up until a certain depth
	 * 
	 * @author Brammert Ottens, 10 nov 2009
	 * @param values		the path to be checked
	 * @param finalDepth	the maximal depth
	 * @return	\c true when the path exists, and \c false otherwise
	 */
	protected boolean pathExists(Val[] values, int finalDepth) {
		Node currentNode = root;
		
		for(int depth = 0; depth < finalDepth; depth++) {
			int childIndex = valuePointers.get(depthToVariable[depth]).get(values[depth]);
			
			currentNode = currentNode.getChild(childIndex);
			
			if(currentNode == null)
				return false;
		}
		return true;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getChildSeparatorReportingOrder(int)
	 */
	@Override
	public String[] getChildSeparatorReportingOrder(int child) {
		assert false: "Not Implemented";
		return null;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#setChildrenSeparator(int, java.lang.String[])
	 */
	@Override
	public void setChildrenSeparator(int child, String[] variables) {
		assert false: "Not Implemented";
			
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#add(frodo2.algorithms.odpop.Good, int, java.util.HashMap)
	 */
	@Override
	public boolean add(Good<Val, U> g, int sender, HashMap<String, Val[]> domains) {
		assert false: "Not Implemented";
		return false;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#setChildDone(int)
	 */
	@Override
	public boolean setChildDone(int child) {
		// @todo Auto-generated method stub
		return false;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getChildValues(java.util.HashMap, int)
	 */
	@Override
	public HashMap<String, Val> getChildValues(
			HashMap<String, Val> parentContext, int child) {
		assert false : "NotImplemented";
		return null;
	}
}
