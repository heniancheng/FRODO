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

package frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import frodo2.algorithms.odpop.Good;
import frodo2.algorithms.odpop.goodsTree.GoodsTree;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * This class is designed to store GOODs received by a node from its children, and is used in the O-DPOP algorithm.
 * A GOOD contains an assignment to a set of variables, and a utility. 
 * A GOOD is identified by its assignment, and in this class the GOODs are ordered using a 
 * tree based on these assignments.
 * 
 * The basic functionality of this class is to either add a received GOOD to the tree, or to obtain the assignment
 * that has the highest utility.
 * @author Brammert
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @param <L> type used for the leaf node
 * @todo write Unit test for this class
 */
public class InnerNodeTree < Val extends Addable<Val>, U extends Addable<U>, L extends LeafNode<U> > 
extends GoodsTree<Val, U, L> {

	/** Used for serialization */
	protected static final long serialVersionUID = 4206985864919963001L;

	/** links a variable to its depth in the tree. */
	protected HashMap<String, Integer> variableToDepth;

	/** Used to determine to which child a variable value corresponds to. */
	protected HashMap<String, HashMap<Val, Integer>> valuePointers; 

	/** Stores, for a level in the tree, the size of the domain of the corresponding variable
	 */
	protected int[] domainSize;

	/** Stores the final sizes of the domains of all the variables */
	protected int[] finalDomainSize;

	/** For every level of the tree, it stores the branching factor */
	protected int[] branchingFactor;

	/** Stores which variables are in a child's separator */
	protected boolean[][] childrenVariables;

	/** For each child it stores the order in which the variables are reported */
	protected String[][] childrenVariablesReportingOrder;

	/** Stores the number of reported variables per child */
	protected int[] separatorSizePerChild;

	/** Stores which are this variable's neighbours */
	protected boolean[] ownVariables;

	/** For each child a map that stores the utilities received from children. This is needed due to 
	 * the assumption that both domain and variable information can be incomplete */
	// @todo this can be removed as soon as information is complete
	protected ArrayList<HashMap<IntArrayWrapper, U>> goodsReceived;

	/** If domain or variable information is incomplete received goods must be stored, otherwise they
	 * can be discarded after processing */
	protected boolean storeReceivedGoods = true;

	/** \c true when this variable has a local problem, and false otherwise */
	protected boolean hasLocalProblem;

	/** The optimal utility of the local problem */
	protected U optimalLocalUtility;

	/** The path of the currently optimal local solution */
	protected int[] optimalLocalPath;

	/** The upperbound on all the completely unseen assignments */
	protected U localUpperBound;

	/** For each child it stores that last confirmed utility received */
	protected U[] upperBounds;

	/** For every combination of children, this array contains the sum of upperBounds belonging to the selected children.
	 * If there are n children, then this array contains n-1 elements (the empty set of children is 0) */
	protected U[] upperBoundSums;

	/** The total number of times a solution to a local problem can be used in the separator problem */
	protected int maxNumberLocalProblemOccurences;

	/** Pre-calculated powers of 2*/
	protected int[] powersOf2;

	/** Counts the number of children that have already sent at least one confirmed good */
	protected int upperBoundIsInfiniteCounter;

	/** COunts the number of children that have already reported a minInfinite value*/
	protected int upperBoundIsMinInfiniteCounter;

	/** The variable domains, where the position in the ArrayList denotes which child
	 *  corresponds to the value. */
	protected HashMap<String, ArrayList<Val>> domains;

	/** The number of children of the variable */
	protected int numberOfChildren;

	/** The root of the tree */
	protected InnerNode<U, L> root;

	/** True when the information on the final domain size is complete */
	protected boolean fullInfo;

	/**Counts the number of children from which one still needs to receive
	 * domain information */
	protected int fullInfoCounter;

	/** True if the domain information has not been requested yet */
	protected boolean stillToSend = true;

	/** An instance of a leaf node, used to create new instances */
	protected L leafNodeInstance;

	/** Stores the UB as it was before a new good has been received */
	protected U oldUB;

	/** The number of times the currently best local solution has NOT been used in the tree, i.e. it counts down to 0*/
	protected int localCounter;

	/** Per child the unpacked variables (different from the reported variables) */
	protected String[][] unpackedVariablesPerChild;

	/** Domains still to be added to \c domains*/
	protected HashMap<String, Val[]> toBeProcessedDomains;

	/**
	 * A constructor 
	 * @param ownVariable			The variable that owns this tree
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param space					The space controlled by this tree
	 * @param zero 					The zero utility
	 * @param numberOfChildren 		The number of children of this tree
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	protected InnerNodeTree(String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) { 
		super(ownVariable, ownVariableDomain, space, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
	}
	
	/**
	 * A constructor 
	 * @param ownVariable			The variable that owns this tree
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param spaces				The space controlled by this tree
	 * @param zero 					The zero utility
	 * @param numberOfChildren 		The number of children of this tree
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	public InnerNodeTree(String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, spaces, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
	}
	
	/** A dummy constructor to be used by extending classes 
	 * @param leafNodeInstance		an instance of the leaf node class
	 * @param ownVariable			The variable that owns this tree
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param space 				The local problem
	 * @param zero 					The zero utility
	 * @param numberOfChildren 		The number of children of this tree
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	protected InnerNodeTree(L leafNodeInstance, String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, space, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
		this.leafNodeInstance = leafNodeInstance;
		this.hasLocalProblem = localProblem != null;
	}
	
	/** A dummy constructor to be used by extending classes 
	 * @param leafNodeInstance		an instance of the leaf node class
	 * @param ownVariable			The variable that owns this tree
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param spaces 				The local problem
	 * @param zero 					The zero utility
	 * @param numberOfChildren 		The number of children of this tree
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	protected InnerNodeTree(L leafNodeInstance, String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, spaces, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
		this.leafNodeInstance = leafNodeInstance;
		this.hasLocalProblem = localProblem != null;
	}
	
	/**
	 * A constructor
	 * @warning we assume that the agent's own variable is put in the end of variables_order
	 * @param ownVariable 			The variable ID
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param space					The hypercube representing the local problem
	 * @param numberOfChildren 		The number of children
	 * @param zero 					The zero utility
	 * @param leafNodeInstance		an instance of the leaf node class
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	public InnerNodeTree( String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, int numberOfChildren, U zero, L leafNodeInstance, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, space, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
		this.hasLocalProblem = localProblem != null;		
		
		init(numberOfChildren, zero);
		this.leafNodeInstance = leafNodeInstance;
		
		if(hasLocalProblem) {
			
			root = createInnerNode(domainSize[0]);

			fullInfo = false;
			solveLocalProblem();
			this.updateLocalProblem();
		} else { // this might look double, but is is not!
			root = createInnerNode((Node<U>[])null);
		}
	}
	
	/**
	 * A constructor
	 * @warning we assume that the agents own variable is put in the end of variables_order
	 * @param ownVariable 			The variable ID
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param spaces				The hypercubes representing the local problem
	 * @param numberOfChildren 		The number of children
	 * @param zero 					The zero utility
	 * @param leafNodeInstance		an instance of the leaf node class
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	public InnerNodeTree(String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, int numberOfChildren, U zero, L leafNodeInstance, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(ownVariable, ownVariableDomain, spaces, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
		this.hasLocalProblem = localProblem != null;		
		assert numberOfVariables != 0;
		
		init(numberOfChildren, zero);
		this.domainElementClass = ownVariableDomain[0].getClass();
		this.leafNodeInstance = leafNodeInstance;
		
		if(numberOfVariables != 0) {
			root = createInnerNode(domainSize[0]);

			fullInfo = false;
			if(hasLocalProblem) {
				solveLocalProblem();
				this.updateLocalProblem();
			}
		} else { // this might look double, but is is not!
			root = createInnerNode((Node<U>[])null);
		}
	}
	
	/**
	 * Adds a good to the tree
	 * 
	 * @author Brammert Ottens, 10 nov 2009
	 * @param g			the good to be added
	 * @param sender	the child that reported the good
	 * @param domains	reported domains
	 * @return	\c true when a new variable has been added, and false otherwise 
	 */
	public boolean add(Good<Val, U> g, int sender, HashMap<String, Val[]> domains) {
		boolean newVariableAdded = false;

		if(numberOfVariables > 0) {
			assert setOldUB();
			U utility = g.getUtility();

			boolean[] relevantChildren = new boolean[numberOfChildren];
			Arrays.fill(relevantChildren, true);
			relevantChildren[sender] = false;

			String[] aVariables = g.getVariables();
			Val[] values = g.getValues();
			ArrayList<Val[]> reportedValues = new ArrayList<Val[]>(1);
			reportedValues.add(values);
			
			if(reportedValues.size() == 0 && domains != null) {
				this.toBeProcessedDomains.putAll(domains);
			}

			for(Val[] aValues : reportedValues) {
				boolean newVariable = false;
				boolean possibleInconsistencies = false;
				//				boolean newDomainElement = false;
				boolean initializeBounds = false;
				
				// First determine whether we need to update the information on our separator,
				// i.e. new variables or domain elements are reported
				HashMap<String, Val> newVariables = new HashMap<String, Val>();
				HashMap<String, Val[]> newDomains = new HashMap<String, Val[]>();
				
				int i = 0;
				for(String var : aVariables) {
					if(!variableToDepth.containsKey(var)) { 
						// the tree does not know this variable
						newVariables.put(var, aValues[i]);
						if(domains == null)
							newDomains.put(var, this.toBeProcessedDomains.remove(var));
						else
							newDomains.put(var, domains.get(var));
						newVariable = true;
						separatorSizePerChild[sender] += 1;
					} else { 
						// the tree does know the variable
						int varIndex = variableToDepth.get(var);
						if(!childrenVariables[sender][varIndex]) {
							separatorSizePerChild[sender] += 1;
							childrenVariables[sender][varIndex] = true;
						}
					}
					i++;
				}

				if(newVariable) {
					newVariableAdded = true;
					int nbrNewVariables = newVariables.size();
					int[] indexPath = addNewVariable(aVariables, newVariables, newDomains, sender);
					InnerNode<U, L> newRoot = createInnerNode(branchingFactor[0]); // added
					addVariableToTree(nbrNewVariables - 1, new IntArrayWrapper(numberOfVariables), indexPath, 0, root, newRoot, possibleInconsistencies, true, sender);
					root = newRoot;
				}

				// add the received good to the goodsReceivedStorage
				if(this.storeReceivedGoods) {
					IntArrayWrapper key = toKey(aValues, aVariables, sender).getPartialAssignment(this.childrenVariables[sender], this.separatorSizePerChild[sender]);
					assert !goodsReceived.get(sender).containsKey(key);
					if(goodsReceived.get(sender).containsKey(key))
						continue;
					goodsReceived.get(sender).put(key, utility);	
				} else {
					this.goodsReceived.clear();
				}		

				// update the upper bound
				assert upperBounds[sender] == null || greaterThanOrEqual(utility, upperBounds[sender]);
				if(this.upperBoundIsInfiniteCounter == 0 && upperBounds[sender] != infeasibleUtil)
					this.updateUpperBoundSums(sender, upperBounds[sender], utility);

				if(upperBounds[sender] == null) {
					upperBoundIsInfiniteCounter--;
					if(upperBoundIsInfiniteCounter == 0)
						initializeBounds = true;
				}

				upperBounds[sender] = utility;

				// create the partial path defined by the good
				int[] partialPath = new int[numberOfVariables];
				Arrays.fill(partialPath, -1);

				for(i = 0; i < aVariables.length; i++) {
					String var = aVariables[i];
					partialPath[variableToDepth.get(var)] = valuePointers.get(var).get(aValues[i]);
				}

				// add the good to the tree
				updatePath(0, new IntArrayWrapper(numberOfVariables), partialPath, root, g, zero, sender, true, !initializeBounds && upperBoundIsInfiniteCounter == 0);
				assert !root.hasUB() || oldUB == null || greaterThanOrEqual(root.getUB(), oldUB);
				assert this.checkTree(0, root, new IntArrayWrapper(numberOfVariables), this.upperBoundIsInfiniteCounter == 0 && !initializeBounds, true, true, true);

				if(initializeBounds) {
					// create the precalculated UBs
					initializeUpperBoundSums();
					relevantChildren = new boolean[numberOfChildren];
					initiateBounds(root, new IntArrayWrapper(numberOfVariables), 0, false, g, sender);
					int newMaxNumberOfOccurences = 1;
					i = 0;
					while(!this.ownVariables[i]) {
						newMaxNumberOfOccurences *= branchingFactor[i];
						i++;
					}
					int diff = newMaxNumberOfOccurences - this.maxNumberLocalProblemOccurences;
					this.maxNumberLocalProblemOccurences = newMaxNumberOfOccurences;
					this.localCounter += diff;
					assert localCounter >= 0;
				}
				assert this.checkTree(0, root, new IntArrayWrapper(numberOfVariables), this.upperBoundIsInfiniteCounter == 0, true, true, true);
			}

			if(this.storeReceivedGoods)
				this.storeReceivedGoods = !this.fullInfo;
		}
		return newVariableAdded;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getAmax()
	 */
	@SuppressWarnings("unchecked")
	public Good<Val, U> getAmax() {

		InnerNode<U, L> currentNode = root;

		if(currentNode.getMaxChild() < 0) { // there currently is no solution in the tree
			return null;
		}

		U UB = null;
		if(root.hasUB()) {
			UB = root.getUB();
		} else 
			return null;

		if(hasLocalProblem && greaterThanOrEqual(UB, localUpperBound))
			UB = localUpperBound;

		if(UB == infeasibleUtil) {
			root.setAlive(false);
			return null;
		}

		U util = root.getUtil();

		if(root.getMaxUtil().counter != 0 || greaterThan(util, UB)) 
			return null;

		Val[] values = (Val[]) Array.newInstance(domainElementClass,this.depthOfFirstToBeRemovedVariables);
		// Find the current aMax
		for(int i = 0; i < this.depthOfFirstToBeRemovedVariables; i++) {
			String var = depthToVariable[i];
			int maxChild = currentNode.getMaxChild();
			values[i] = domains.get(var).get(maxChild);
			currentNode = (InnerNode<U, L>)currentNode.getChild(maxChild);
		}
		assert domainSize[numberOfVariables - 1] <= branchingFactor[numberOfVariables - 1];

		// create the good and remove the path to aMax from the tree
		assert !root.hasUB() || root.getUB().equals(root.getMaxUB().calculateUBTest(upperBoundSums));

		if(this.COLLECT_STATISTICS)
			this.countGoodsProduced();
		return new Good<Val, U>(outsideVariables, values, util);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getBestAssignmentForOwnVariable(java.util.HashMap)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void getBestAssignmentForOwnVariable(HashMap<String, Val> contextMap) {

		Val[] context = (Val[]) new Addable[numberOfVariables];

		for(Entry<String, Val> e : contextMap.entrySet()) {
			context[variableToDepth.get(e.getKey())] = e.getValue();
		}

		int[] optimalPath = getOwnVariableOptions(context);

		for(int i = 0; i < numberOfVariables; i++) {
			int value = optimalPath[i];
			if(value != -1) {
				String var = depthToVariable[i];
				contextMap.put(var, this.domains.get(var).get(value));
			}
		}

		assert optimalPath[depthFinalVariable] != -1;
	}

	/**
	 * Returns the variables in the childs separator in the order it reported them
	 * 
	 * @author Brammert Ottens, 19 aug 2009
	 * @param child	the child who's separator we want to know
	 * @return	the separator variables of the child
	 */
	public String[] getChildSeparatorReportingOrder(int child) {
		return childrenVariablesReportingOrder[child];
	}

	/**
	 * @author Brammert Ottens, 8 sep 2009
	 * @param child the child who's separator size is requested
	 * @return	the separator size of the child
	 */
	public int getChildSeparatorSize(int child) {
		return this.separatorSizePerChild[child];
	}

	/**
	 * Given a map that maps variables to values, this method returns the values
	 * belonging to the variables in a child's separator
	 * @author Brammert Ottens, 19 aug 2009
	 * @param parentContext	the value map
	 * @param child	the child for who we want to know the separator values
	 * @return	an array containing the values of the child's separator variables
	 */
	public HashMap<String, Val> getChildValues(HashMap<String, Val> parentContext, int child) {

		String[] childrenSeparator = childrenVariablesReportingOrder[child];
		int length = childrenSeparator.length;
		HashMap<String, Val> childContext = new HashMap<String, Val>(length);

		for(String var : childrenSeparator) {
			assert var != null;
			childContext.put(var, parentContext.get(var));
		}

		return childContext;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getFinalDomainSize()
	 */
	public int[] getFinalDomainSize() {

		int[] info = new int[this.depthOfFirstToBeRemovedVariables];
		for(int i = 0; i < this.depthOfFirstToBeRemovedVariables; i++) {
			info[i] = finalDomainSize[i];
			assert info[i] != 0;
		}
		stillToSend = false;

		return info;
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getDomains()
	 */
	@SuppressWarnings("unchecked")
	public Val[][] getDomains() {
		Val[][] doms = (Val[][]) new Addable[this.numberOfVariables][];

		for(int i = 0; i < numberOfVariables; i++) {
			doms[i] = domains.get(depthToVariable[i]).toArray((Val[])new Addable[0]);
		}

		return doms;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#hasFullInfo()
	 */
	public boolean hasFullInfo() {
		return this.fullInfo;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#hasMore()
	 */
	public boolean hasMore() {
		return root.isAlive();
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#isValuationSufficient()
	 */
	public boolean isValuationSufficient() {
		L util = root.getMaxUtil();
		if(!root.hasUB())
			return false;

		U UB = root.getUB();

		if(hasLocalProblem && greaterThan(UB, localUpperBound))
			UB = localUpperBound;

		return util != null && ((root.getUB() == this.infeasibleUtil || util.counter == 0) && greaterThanOrEqual(UB, util.getUtil()));
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#knowsVariable(java.lang.String)
	 */
	public boolean knowsVariable(String variable) {
		return variableToDepth.containsKey(variable);
	}

	/**
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#notEnoughInfo()
	 */
	public boolean notEnoughInfo() {
		return numberOfVariables == 1;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#removeAMax()
	 */
	public void removeAMax() {
		boolean localRemoved = removePath(0, root, true);
		if(root.isAlive()) {
			if(hasLocalProblem) {
				if(localRemoved && this.localCounter == 0) {
					assert !this.pathAlive(optimalLocalPath, 0, root);
					this.updateLocalProblem();
					localCounter = maxNumberLocalProblemOccurences;
					assert localCounter >= 0;
				}
			} else {
				if(root.hasUB())
					localUpperBound = root.getUB();
			}
		}

		if(this.hasLocalProblem) { 
			if(this.localUpperBound == this.infeasibleUtil) {
				if(!root.hasUtil()) {
					if(!root.hasUB() || root.getUB() == this.infeasibleUtil)
						root.setAlive(false);
				} else if(root.getUB() == this.infeasibleUtil) {
					root.setAlive(false);
				}
			}
		} else {
			if(root.hasUB() && root.getUB() == this.infeasibleUtil)
				root.setAlive(false);
		}
	}

	/**
	 * Initializes the separator of a child. This method should only be called when one receives the first message from a particular
	 * child. After that, the process of adding new variables should take care of the rest.
	 * @param child			the child whose separator must be set
	 * @param variables		the variables in its separator
	 */
	public void setChildrenSeparator(int child, String[] variables) {
		assert variables[0] != null;
		childrenVariablesReportingOrder[child] = variables;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#stillToSend()
	 */
	public boolean stillToSend() {
		return stillToSend;
	}

	/**
	 * If a child has send a DONE message, the upper bound
	 * should be set to -infinity
	 * @author Brammert Ottens, 30 sep 2009
	 * @param child	the child that sent the DONE message
	 * @return \c true when the remaining local problem became infeasible, and \c false otherwise
	 */
	public boolean setChildDone(int child) {

		boolean confirmed = false;
		L rootUtil = root.getMaxUtil();

		if(rootUtil != null)
			confirmed = rootUtil.counter == 0;
		if(++this.upperBoundIsMinInfiniteCounter == this.numberOfChildren && this.upperBoundIsInfiniteCounter != 0) {
			return true;
		}

		U oldBound = this.upperBounds[child];
		this.upperBounds[child] = infeasibleUtil;
		LeafNode<U> rootUB = root.getMaxUB();

		if(this.upperBoundIsInfiniteCounter == 0) {
			this.updateUpperBoundSums(child, oldBound, infeasibleUtil);
			this.localUpperBound = infeasibleUtil;

			if(rootUB != null) {
				U UB = rootUB.calculateUB(this.upperBoundSums, maximize);
				U utilUB = this.infeasibleUtil;
				if(rootUtil != null)
					utilUB = rootUtil.calculateUB(this.upperBoundSums, maximize);
				boolean updateUB = !rootUB.getUB().equals(UB); 
				if(updateUB || (rootUtil != null && greaterThan(utilUB, rootUtil.getUtil()))) {
					this.recalculateUB(0, root, UB, true, true);
				}
			}

			assert !root.hasUtil() || root.getMaxUtil().isUpToDate();
			assert !confirmed || root.hasUtil();
		}

		rootUB = root.getMaxUB();

		if(this.localUpperBound == this.infeasibleUtil && (rootUB == null || rootUB.getUB() == this.infeasibleUtil)) {
			assert !root.hasUB() ||root.getUB() == this.infeasibleUtil;
			return true;
		}
		  
		return false;
	}

	/**
	 * Method used for debugging purposes. It prints the state of the tree
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		String s = "";
		s += "----------------------\n";
		s += "Variable " + depthToVariable[numberOfVariables - 1] + "\n";
		s += "----------------------\n";
		s += this.fullInfo + "\n";
		s += this.fullInfoCounter + "\n";
		s += this.upperBoundIsMinInfiniteCounter + " - " + this.numberOfChildren + "\n";
		s += "Alive: " + root.isAlive() + "\n";
		if(root.hasUtil())
			s += "max util is " + root.getUtil() +  " " + root.getMaxUtil().counter + "\n";
		if(root.hasUB())
			s += "max UB is   " + root.getUB() + "\n";
		s += "localUB is  " + this.localUpperBound + "\n";
		s += "localUtil is " + this.optimalLocalUtility + "\n";
		if(this.optimalLocalPath != null) {
			s += "[";
			int i = 0;
			for(; i < optimalLocalPath.length - 1; i++)
				s += optimalLocalPath[i] + ", ";
			s+= optimalLocalPath[i] + "]\n";
		}
		s += "\n";
		s += "\n";
		s += "variables to be removed:\n";
		for(int i = this.depthOfFirstToBeRemovedVariables; i < numberOfVariables; i++) {
			s += depthToVariable[i] + "\n";
		}
		s += "\n";
		if(this.storeReceivedGoods)
			for(int i = 0; i < numberOfChildren; i++) {
				s += "Received " + goodsReceived.get(i).size() + " messages from child " + i + "\n";
			}
		s += "\n---------------------\n";
		s += this.generateAssignments(0, new int[numberOfVariables], root);
		s += "\n---------------------\n";

		return s;


	}
	
	/**
	 * This method adds new variables to the data structure
	 * @param allVariables 	The current variables
	 * @param newVariables	The variables to be added
	 * @param newDomains	The received domain values
	 * @param sender 		The child that reported the variables
	 * @return the number of new variables
	 */
	protected int[] addNewVariable(String[] allVariables, HashMap<String, Val> newVariables, HashMap<String, Val[]> newDomains, int sender) {
		int numberOfNewVariables = newVariables.size();
		
		// First, update the HashMaps, Arrays and ArrayLists
		numberOfVariables += numberOfNewVariables;
		depthFinalVariable += numberOfNewVariables;
		depthOfFirstToBeRemovedVariables += numberOfNewVariables;
		String[] newDepthToVariable = new String[numberOfVariables];
		String[] newOutsideVariables = new String[outsideVariables.length + numberOfNewVariables];
		System.arraycopy(outsideVariables, 0, newOutsideVariables, numberOfNewVariables, outsideVariables.length);
		int[] newDomainSize = new int[numberOfVariables];
		int[] newFinalDomainSize = new int[numberOfVariables];
		int[] newBranchingFactor = new int[numberOfVariables];
		boolean[] newOwnVariables = new boolean[numberOfVariables];
		boolean[][] newChildrenVariables = new boolean[numberOfChildren][numberOfVariables];
		this.fullInfoCounter--;

		int i = 0;
		for(String var : depthToVariable) {
			int newIndex = numberOfNewVariables + i;
			variableToDepth.put(var, variableToDepth.get(var) + numberOfNewVariables);
			newDepthToVariable[newIndex] = var;
			newDomainSize[newIndex] = domainSize[i];
			newFinalDomainSize[newIndex] = finalDomainSize[i];
			newBranchingFactor[newIndex] = branchingFactor[i];
			newOwnVariables[newIndex] = ownVariables[i];
			for(int j = 0; j < numberOfChildren; j++) {
				newChildrenVariables[j][newIndex] = childrenVariables[j][i];
			}
			i++;
		}
		
		// Second, add the new variables to these maps
		int[] indexPath = new int[numberOfNewVariables];
		i = 0;
		for(Entry<String, Val> newEntry : newVariables.entrySet()) {
			String newVar = newEntry.getKey();
			Val newVal = newEntry.getValue();
			Val[] dom = newDomains.get(newVar);
			int size = dom.length;
			variableToDepth.put(newVar, i);
			newDepthToVariable[i] = newVar;
			newOutsideVariables[i] = newVar;
			newDomainSize[i] = size;
			newBranchingFactor[i] = size; // added changed from 1 to 2
			HashMap<Val, Integer> pointer = new HashMap<Val, Integer>();
			ArrayList<Val> newDomain = new ArrayList<Val>(dom.length);
			for(int j = 0; j < size; j++) {
				Val v = dom[j];
				newDomain.add(v);
				pointer.put(v, j);
			}
			valuePointers.put(newVar, pointer);
			domains.put(newVar, newDomain);
			indexPath[i] = pointer.get(newVal);
			i++;
		}
		
		depthToVariable = newDepthToVariable;
		outsideVariables = newOutsideVariables;
		domainSize = newDomainSize;
		finalDomainSize = newFinalDomainSize;
		branchingFactor = newBranchingFactor;
		
		if(hasLocalProblem && optimalLocalPath != null) {
			int[] newLocalOptimalPath = new int[numberOfVariables];
			Arrays.fill(newLocalOptimalPath, -1);
			System.arraycopy(optimalLocalPath, 0, newLocalOptimalPath, numberOfNewVariables, optimalLocalPath.length);
			optimalLocalPath = newLocalOptimalPath;
		}

		boolean[] myNewVars = newChildrenVariables[sender];
		for(i = 0; i < allVariables.length; i++) {
			myNewVars[variableToDepth.get(allVariables[i])] = true;
		}
		
		childrenVariables = newChildrenVariables;
		ownVariables = newOwnVariables;

		int newMaxNumberOfOccurences = 1;
		i = 0;
		while(!this.ownVariables[i]) {
			newMaxNumberOfOccurences *= branchingFactor[i];
			i++;
		}
		int diff = newMaxNumberOfOccurences - this.maxNumberLocalProblemOccurences;
		this.maxNumberLocalProblemOccurences = newMaxNumberOfOccurences;
		this.localCounter += diff;
		assert localCounter >= 0;
		finalDomainSizeReceiver();
		return indexPath;
	}

	/**
	 * This method is called when variables are received. The new variables are
	 * placed at the root
	 * @param nbrNewVariables 	The number of variables to add
	 * @param currentPath		The path currently taken through the tree
	 * @param indexPath 		The partial path defined by the new variables
	 * @param depth				The current depth
	 * @param oldRoot 			The old root
	 * @param currentNode 		The current node
	 * @param possibleInconsistencies \c true when the reception of a new variable can make certain solutions infeasible
	 * @param onIndexPath 		\c true when still following the index path
	 * @param sender 			The sender of the good
	 * @return \c true when a node has been added to the tree
	 */
	protected boolean addVariableToTree(int nbrNewVariables, IntArrayWrapper currentPath, int[] indexPath, int depth, InnerNode<U, L> oldRoot, InnerNode<U, L> currentNode, boolean possibleInconsistencies, boolean onIndexPath, int sender) {
		int index = indexPath[depth];
		int branching = branchingFactor[depth];
		int nextDepth = depth + 1;
		assert nbrNewVariables >= 0;
		boolean set = false;
		U maxUtil = null;
		int maxUtilIndex = -1;

		if(nbrNewVariables == 0) {
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				InnerNode<U, L> newNode = null;
				if(onIndexPath && i == index) {
					newNode = oldRoot;
					if(possibleInconsistencies) {
						IntArrayWrapper path = this.createIntArrayWrapper(this.numberOfVariables);
						for(int j = 0; j < depth + 1; j++)
							path.setValue(j, currentPath.getValue(j));
						removeInconsistencies(newNode, nextDepth, path);
					}
				} else {
					newNode = this.fillTree(oldRoot, nextDepth, currentPath, true, false, false, null, null, sender, true);
				}

				if(newNode != null) {
					set = true;
					currentNode.setChild(newNode, i);

					if(newNode.utilCandidate(maxUtil, maximize)) {
						maxUtil = newNode.getUtil();
						maxUtilIndex = i;
					}
				}

			}
		} else {
			nbrNewVariables--;
			int childBranching = branchingFactor[nextDepth];
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				InnerNode<U, L> newNode = createInnerNode(childBranching);
				if(addVariableToTree(nbrNewVariables, currentPath, indexPath, nextDepth, oldRoot, newNode, possibleInconsistencies, onIndexPath && i == index, sender)) {
					currentNode.setChild(newNode, i);
					set = true;

					
					if(newNode.utilCandidate(maxUtil, maximize)) {
						maxUtil = newNode.getUtil();
						maxUtilIndex = i;
					}

				}
			}

		}

		if(set)
			currentNode.setUtil(maxUtilIndex, false);
		return set;
	}
	
	/**
	 * Create an inner node with a specified number of children
	 * 
	 * @author Brammert Ottens, 22 apr 2010
	 * @param numberOfChildren the number of children this innernode should have
	 * @return	an InnerNode
	 */
	protected InnerNode<U, L> createInnerNode(int numberOfChildren) {
		return new InnerNode<U, L>(numberOfChildren);
	}
	
	/**
	 * Create an inner node with the specified number of children
	 * 
	 * @author Brammert Ottens, 22 apr 2010
	 * @param children an array of child nodes
	 * @return	an InnerNode
	 */
	protected InnerNode<U, L> createInnerNode(Node<U>[] children) {
		return new InnerNode<U, L>(children);
	}

	/**
	 * Method to create a new leaf node
	 * @param currentPath		The path to the leaf
	 * @param g					the received good
	 * @param child 			the child that reported it
	 * @param withUB			\c true when the upper bound must be set
	 * @return the new leaf node
	 */
	protected L createLeaf(IntArrayWrapper currentPath, Good<Val, U> g, int child, final boolean withUB) {
		
		L leaf = leafNodeInstance.newInstance(numberOfChildren, powersOf2);
		boolean support = false;
		
		U localUtil = this.getUtilityLocalProblem(currentPath);
		if(localUtil == infeasibleUtil)
			return null;
		assert localUtil.equals(this.getUtilityLocalProblem(currentPath));
		U confirmedUtil = localUtil;
		
		if(this.storeReceivedGoods) {
			AddableDelayed<U> confirmedUtilDelayed = confirmedUtil.addDelayed(); 
			for(int i = 0; i < numberOfChildren; i++) {
				U temp = goodsReceived.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
				if (temp != null) {
					support = true;
					confirmedUtilDelayed.addDelayed(temp);
					leaf.counter--;
					assert leaf.counter >= 0;
					leaf.updateUB[i] = false;	
				}
			}
			confirmedUtil = confirmedUtilDelayed.resolve();
		} else {
			confirmedUtil = confirmedUtil.add(g.getUtility());
			leaf.counter--;
			leaf.updateUB[child] = false;
			support = true;
		}
		
		int childrenCombination = LeafNode.fromBooleanArrayToInt(leaf.updateUB, powersOf2);
		leaf.setUbSum(childrenCombination);
		if(!support) {
			assert !this.storeReceivedGoods || !hasSupport(currentPath);
			return null;
		}
		
		if(withUB) {
			if(childrenCombination == -1)
				leaf.setUB(confirmedUtil);
			else
				leaf.setUB(confirmedUtil.add(upperBoundSums[childrenCombination]));
		}
		
		if(withUB && leaf.getUB() == infeasibleUtil) {
			leaf.setUtil(infeasibleUtil);
		} else {
			leaf.setUtil(confirmedUtil);
		}

		assert  !this.storeReceivedGoods ||  this.checkLeaf(leaf, currentPath, false, g.getUtility(), child);
		assert !this.storeReceivedGoods ||  hasSupport(currentPath);
		if(this.COLLECT_STATISTICS)
			this.countLeafNode();
		return leaf;
	}
	
	/**
	 * Method to create a new leaf node
	 * @param currentPath		The path to the leaf
	 * @param withUB			\c true when the upper bound must be set
	 * @return the new leaf node
	 */
	protected L createLeaf(IntArrayWrapper currentPath, final boolean withUB) {
		L leaf = leafNodeInstance.newInstance(numberOfChildren, powersOf2);
		boolean support = false;
		
		U localUtil = this.getUtilityLocalProblem(currentPath);
		if(localUtil == infeasibleUtil)
			return null;
		assert localUtil.equals(this.getUtilityLocalProblem(currentPath));
		U confirmedUtil = localUtil;
		
		if(this.storeReceivedGoods) {
			AddableDelayed<U> confirmedUtilDelayed = confirmedUtil.addDelayed();
			for(int i = 0; i < numberOfChildren; i++) {
				U temp = goodsReceived.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
				if (temp != null) {
					support = true;
					confirmedUtilDelayed.addDelayed(temp);
					leaf.counter--;
					assert leaf.counter >= 0;
					leaf.updateUB[i] = false;	
				}
			}
			confirmedUtil = confirmedUtilDelayed.resolve();
		}
		int childrenCombination = LeafNode.fromBooleanArrayToInt(leaf.updateUB, powersOf2);
		leaf.setUbSum(childrenCombination);
		if(!support)
			return null;
		
		if(withUB) {
			
			if(childrenCombination == -1)
				leaf.setUB(confirmedUtil);
			else
				leaf.setUB(confirmedUtil.add(upperBoundSums[childrenCombination]));
		}
		
		if(withUB && leaf.getUB() == infeasibleUtil) {
			leaf.setUtil(infeasibleUtil);
		} else {
			leaf.setUtil(confirmedUtil);
		}

		assert  !this.storeReceivedGoods ||  this.checkLeaf(leaf, currentPath, false, null, -1);
		assert !this.storeReceivedGoods || hasSupport(currentPath);
		if(this.COLLECT_STATISTICS)
			this.countLeafNode();
		return leaf;
	}
	
	/**
	 * Given a partial path, this method creates the path in the tree. This method
	 * should only be called after domain information is complete!
	 * 
	 * @param depth			the current depth
	 * @param currentPath	the path taken
	 * @param partialPath	the partial path defined by the received good
	 * @param real			\c true when we are on a real path, and false otherwise
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return a new InnerNode<U>
	 */
	protected InnerNode<U, L> createPathNoUB(int depth, IntArrayWrapper currentPath, int[] partialPath, boolean real, Good<Val, U> g, int sender) {
		int nextDepth = depth + 1;
		int childIndex = partialPath[depth];
		int branching = branchingFactor[depth];
		InnerNode<U, L> node = createInnerNode(branchingFactor[depth]);
		U maxUtil = null;
		int maxUtilIndex = -1;
		boolean set = false;
		boolean reachedFinalLayer = depth == depthFinalVariable;
		
		if(childIndex == -1) {
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				Node<U> child = null;
				if(reachedFinalLayer)
					child = createLeaf(currentPath, g, sender, false);
				else
					child = createPathNoUB(nextDepth, currentPath, partialPath, true, g, sender);

				if(child != null) {
					set = true;
					node.setChild(child, i);

					if(child.utilCandidate(maxUtil, maximize)) {
						maxUtil = child.getUtil();
						maxUtilIndex = i;
					}
				}
			}
		} else {
			currentPath.setValue(depth, childIndex);
			Node<U> child = null;
			if(reachedFinalLayer)
				child = createLeaf(currentPath, g, sender, false);
			else
				child = createPathNoUB(nextDepth, currentPath, partialPath, true, g, sender);
			if(child != null) {
				set = true;
				node.setChild(child, childIndex);

				maxUtil = child.getUtil();
				maxUtilIndex = childIndex;
			}
		}
		node.setUtil(maxUtilIndex, reachedFinalLayer);
		
		if(set) {
			assert node.getMaxChild() < domains.get(depthToVariable[depth]).size();
			return node;
		} else
			return null;
		
	}

	/**
	 * Given a partial path, this method creates the path in the tree. This method
	 * should only be called after domain information is complete!
	 * @param depth			the current depth
	 * @param currentPath	the path taking in the tree
	 * @param partialPath	the partial path defined by the received good
	 * @param real			\c true when we are on a real path, and false otherwise
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return a new InnerNode<U>
	 */
	private InnerNode<U, L> createPathWithUB(int depth, IntArrayWrapper currentPath, int[] partialPath, boolean real, Good<Val, U> g, int sender) {
		int nextDepth = depth + 1;
		int childIndex = partialPath[depth];
		int branching = branchingFactor[depth];
		InnerNode<U, L> node = createInnerNode(branchingFactor[depth]);
		U maxUtil = null;
		U maxUB = null;
		int maxUtilIndex = -1;
		int maxUBIndex = -1;
		boolean set = false;
		
		if(depth == depthFinalVariable) {
			currentPath.setValue(depth, childIndex);
			L leaf = createLeaf(currentPath, g, sender, true);
			if(leaf != null) {
				set = true;
				node.setChild(leaf, childIndex);
				assert  !this.storeReceivedGoods ||  checkLeaf(leaf, currentPath, true, g.getUtility(), sender);

				if(leaf.isUpToDate()) {
					maxUtil = leaf.getUtil();
					maxUtilIndex = childIndex;
				}
				node.setUB(childIndex, true);
				node.setUtil(maxUtilIndex, true);
			}
		} else {
			if(childIndex == -1) {
				int i = 0;
				for(; i < branching; i++) {
					currentPath.setValue(depth, i);
					InnerNode<U, L> child = createPathWithUB(nextDepth, currentPath, partialPath, true, g, sender);
					if(child != null) {
						set = true;
						node.setChild(child, i);

						if(child.utilCandidate(maxUtil, maximize)) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}

						if(child.ubCandidate(maxUB, maximize)) {
							maxUB = child.getUB();
							maxUBIndex = i;
						}
					}
				}

				node.setUB(maxUBIndex, false);
			} else {
				currentPath.setValue(depth, childIndex);
				InnerNode<U, L> child = createPathWithUB(nextDepth, currentPath, partialPath, true, g, sender);
				if(child != null) {
					set = true;
					node.setChild(child, childIndex);

					maxUtilIndex = childIndex;
					node.setUB(childIndex, false);
				}
			}
			node.setUtil(maxUtilIndex, false);
		}
		
		if(set) {
			assert node.getMaxChild() < domains.get(depthToVariable[depth]).size();
			return node;
		} else
			return null;
		
	}
	
	/**
	 * Fills a part of the tree that has been created by the reception of a new domain
	 * value
	 * 
	 * @author Brammert Ottens, 30 sep 2009
	 * @param example		the example that is to be copied
	 * @param depth 		the current depth
	 * @param currentPath	the current path taken through the tree. Is only up to date up to depth
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param withUB		\c true when the UB must be instatiated as well
	 * @param onPartialPath \c true then the followed path is the same as the path defined by the received good
	 * @param partialPath	the path defined by the received good
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @param ex @todo
	 * @return	a new node
	 */
	@SuppressWarnings("unchecked")
	private InnerNode<U, L> fillTree(InnerNode<U, L> example, int depth, IntArrayWrapper currentPath, boolean onLocalPath, final boolean withUB, boolean onPartialPath, int[] partialPath, Good<Val, U> g, int sender, boolean ex) {
		int nextDepth = depth + 1;
		int branching = branchingFactor[depth];
		InnerNode<U, L> node = createInnerNode(branching);
		boolean set = false;
		U maxUtil = null;
		U maxUB = null;
		int maxUtilIndex = -1;
		int maxUBIndex = -1;
		
		if(depth == depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				boolean fromGood = onPartialPath && (partialPath[depth] == i || partialPath[depth] == -1);
				if( fromGood || (example != null && (!example.isAlive() || example.getChild(i) != null))) {
					assert (onPartialPath && partialPath[depth] == i) || (example != null && (!example.isAlive() || example.getChild(i) != null));
					L child = null;
					if(fromGood)
						child = createLeaf(currentPath, g, sender, withUB);
					else
						child = createLeaf(currentPath, withUB);

					if(child == null) 
						continue;

					set = true;
					assert  !this.storeReceivedGoods || g == null ||  this.checkLeaf((L)child, currentPath, withUB, g.getUtility(), sender);
					node.setChild(child, i);

					if(child.utilCandidate(maxUtil, maximize)) {
						assert !withUB || greaterThanOrEqual(child.getUtil(), child.getUB());
						maxUtil = child.getUtil();;
						maxUtilIndex = i;
					}
					
					if(withUB && child.ubCandidate(maxUB, maximize)) {
						maxUB = child.getUB();
						maxUBIndex = i;
					}
				}
			}
			
			if(withUB && set) {
				node.setUB(maxUBIndex, true);
			}
			
			node.setUtil(maxUtilIndex, true);
			
		} else {
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				InnerNode<U, L> childExample = null;

				if(example != null)
					childExample = (InnerNode<U, L>)example.getChild(i);

				boolean fromGood = onPartialPath && (partialPath[depth] == i || partialPath[depth] == -1);

				if(fromGood || childExample != null) {
					InnerNode<U, L> child = fillTree(childExample, nextDepth, currentPath, fromGood, withUB, onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i), partialPath, g, sender, ex);
					if(child != null) {
						set = true;
						node.setChild(child, i);

						if(child.utilCandidate(maxUtil, maximize)) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}

						if(withUB && child.ubCandidate(maxUB, maximize)) {
							maxUB = child.getUB();
							maxUBIndex = i;
						}
					}
				}
			}
			
			if(withUB && set) {
				node.setUB(maxUBIndex, false);
			}
			node.setUtil(maxUtilIndex, false);
		}
		
		assert maxUB == null || greaterThanOrEqual(maxUB, oldUB);
		
		if(set) {
			assert node.getMaxChild() < domains.get(depthToVariable[depth]).size();
			assert node.check2(maximize);
			assert this.checkTree(depth, node, currentPath, withUB, false, true, true);
			return node;
		} else {
			assert this.checkTree(depth, null, currentPath, withUB, false, true, true);
			return null;
		}
	}

	/**
	 * logs the reception of a domain size info message
	 */
	protected void finalDomainSizeReceiver() {
		fullInfo = fullInfoCounter == 0;
		if(fullInfo) {
			this.totalSeparatorSpaceSize = 1;
			for(int i = 0; i < numberOfVariables; i++) {
				totalSeparatorSpaceSize *= this.finalDomainSize[i];
			}
		}

	}

	/**
	 * Given the assignment of variables in its separator, this method returns the utility
	 * for all domain elements of the own variable
	 * @param assignments collection of assignments of variables in the separator
	 * @return an array of utilities
	 */
	protected int[] getOwnVariableOptions(Val[] assignments) {
		int[] path = new int[depthFinalVariable];
		int[] optimalPath = new int[numberOfVariables];
		Arrays.fill(optimalPath, -1);

		for(int i = 0; i < depthFinalVariable; i++) {
			Val val = assignments[i];
			if(val == null)
				path[i] = -1;
			else {
				Integer index = valuePointers.get(depthToVariable[i]).get(val);
				if(index != null)
					path[i] = index;
				else
					path[i] = -1;
			}
		}

		getOwnVariableOptions(path, optimalPath, this.infeasibleUtil, 0, root);
		return optimalPath;
	}

	/**
	 * Given a possibly partial path, this method finds the maximal utility any
	 * option can provide.
	 * @todo also look at other things than max
	 * 
	 * @param path			the (possibly partial) path to be followed
	 * @param optimalPath	the path trough the tree that leads to the optimal leafnode
	 * @param maxUtil		the opimal utility
	 * @param depth			the current depth
	 * @param currentNode	the current node  
	 * @return for every option a utility
	 */
	@SuppressWarnings("unchecked")
	protected  U getOwnVariableOptions(int[] path, int[] optimalPath, U maxUtil, int depth, InnerNode<U, L> currentNode) {
		if(currentNode != null) {

			int branching = domainSize[depth];
			
			if(depth == depthFinalVariable) {
				for(int i = 0; i < branching; i++) {
					L leaf = (L)currentNode.getChild(i);
					if(leaf != null) {
						U util = leaf.getUtil();
						if(greaterThan(maxUtil, util)) {
							optimalPath[depth] = i;
							maxUtil = util;
						}
					}
				}
			} else {
				if(path[depth] == -1) {
					int nextDepth = depth + 1;

					for(int i = 0; i < branching; i++) {
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						if(child != null) {
							U util = getOwnVariableOptions(path, optimalPath, maxUtil, nextDepth, child);
							if(greaterThan(maxUtil, util)) {
								optimalPath[depth] = i;
								maxUtil = util;
							}
						}
					}
					if(optimalPath[depth] == -1)
						optimalPath[depth] = currentNode.getMaxChild();

				} else {
					currentNode = (InnerNode<U, L>) currentNode.getChild(path[depth]);
					depth++;

					while(currentNode != null && depth < depthFinalVariable && path[depth] != -1) {
						currentNode = (InnerNode<U, L>) currentNode.getChild(path[depth]);
						depth++;
					}

					if(currentNode != null) {
						U util = getOwnVariableOptions(path, optimalPath, maxUtil, depth, currentNode);
						if(greaterThan(maxUtil, util))
							maxUtil = util;
					}
				}
			}
		}

		return maxUtil;
	}
	
	/**
	 * Given an assignment to all the variables, represented
	 * as a path in the tree, this method returns the utility
	 * given by the variable's local problem
	 * 
	 * @author Brammert Ottens, 1 jul 2009
	 * @param currentPath	the variable assignment
	 * @return	the utility corresponding to the variable assignment
	 */
	@SuppressWarnings("unchecked")
	protected U getUtilityLocalProblem(IntArrayWrapper currentPath) {

		if(!hasLocalProblem)
			return zero;

		Val[] ass = (Val[])Array.newInstance(this.domainElementClass, numberOfVariables);
		for(int i = 0; i < numberOfVariables; i++) {
			int v = currentPath.getValue(i);
			if(v != -1)
				ass[i] = domains.get(depthToVariable[i]).get(v);
		}
		
		return localProblem.getUtility(depthToVariable, ass);
	}
	
	/**
	 * Initialize all the variables of the tree
	 * @param numberOfChildren 					The number of children
	 * @param zero 								The zero utility
	 */
	@SuppressWarnings("unchecked")
	protected void init(int numberOfChildren, U zero) {
		maxNumberLocalProblemOccurences = 1;
		localCounter = 1;
		
		if(numberOfVariables != 0) {
			upperBoundIsInfiniteCounter = numberOfChildren;
			upperBoundIsMinInfiniteCounter = 0;
			fullInfoCounter = numberOfChildren;

			this.numberOfChildren = numberOfChildren;
			
			/* First, create a map from variable value combinations to positions in the tree*/
			variableToDepth 		= new HashMap<String, Integer>(numberOfVariables);
			valuePointers 			= new HashMap<String, HashMap<Val, Integer>>(numberOfVariables);
			domains 				= new HashMap<String, ArrayList<Val>>(numberOfVariables);
			domainSize 				= new int[numberOfVariables];
			finalDomainSize 		= new int[numberOfVariables];
			branchingFactor 		= new int[numberOfVariables];
			childrenVariables 		= new boolean[numberOfChildren][numberOfVariables];
			childrenVariablesReportingOrder = new String[numberOfChildren][];
			ownVariables 			= new boolean [numberOfVariables];
			goodsReceived 			= new ArrayList<HashMap<IntArrayWrapper, U>>(numberOfChildren);
			upperBounds 			= (U[])new Addable[numberOfChildren];
			separatorSizePerChild 	= new int[numberOfChildren];
			unpackedVariablesPerChild = new String[numberOfChildren][];
			toBeProcessedDomains = new HashMap<String, Val[]>();
			
			for(int i = 0; i < numberOfVariables; i++) {
				String var = depthToVariable[i];
				
				Val[] dom = null;
				if(i < depthFinalVariable) {
					dom = this.localProblem.getDomain(var);
				} else {
					dom = ownVarDomain;
				}
				int size = dom.length;
				assert size != 0;
				
				domainSize[i] = size;
				finalDomainSize[i] = size;
				branchingFactor[i] = size;
			
				variableToDepth.put(var, i);
				domains.put(var, new ArrayList<Val>(Arrays.asList(dom)));
				HashMap<Val, Integer> pointer = new HashMap<Val, Integer>((int)Math.ceil(size/0.75));
				for(int j = 0; j < size; j++) {
					pointer.put(dom[j], j);
				}
				valuePointers.put(var, pointer);
				ownVariables[i] = true;
			}
			
			for(int i = 0; i < numberOfChildren; i++) {
				goodsReceived.add(new HashMap<IntArrayWrapper, U>());
				// @todo remove this when done!
				childrenVariablesReportingOrder[i] = new String[1];
				childrenVariablesReportingOrder[i][0] = this.ownVariable;
			}
		}
		
		// pre-calculate the powers of 2
		powersOf2 = new int[numberOfChildren];
		int power = 1;
		for(int i = 0; i < numberOfChildren; i++) {
			powersOf2[i] = power;
			power *= 2;
		}
	}

	/**
	 * Initializes both the sum of bounds array and the powersOf2 array
	 * @author Brammert Ottens, 25 jun 2009
	 */
	@SuppressWarnings("unchecked")
	protected void initializeUpperBoundSums() {
		int size = (int)Math.pow(2, numberOfChildren) - 1;
		upperBoundSums = (U[])new Addable[size];

		// initialize the upperBoundSums array by walking through
		// all possible combinations of children, ignoring the
		// empty combination
		boolean[] chosen = new boolean[numberOfChildren];
		chosen[0] = true;
		upperBoundSums[0] = upperBounds[0];
		for(int counter = 1; counter < size; counter++) {
			int n=0;
			AddableDelayed<U> sum = zero.addDelayed();
			chosen[0]=!chosen[0];
			boolean carry=!chosen[0];
			while(n+1<chosen.length)
			{
				n++;
				if(carry)
				{
					chosen[n]=!chosen[n];
					carry=!chosen[n];
				}
				else break;
			}

			for(int i = 0; i < numberOfChildren; i++) {
				if(chosen[i])
					sum.addDelayed(upperBounds[i]);
			}

			upperBoundSums[counter] = sum.resolve();
		}

		if(hasLocalProblem && optimalLocalUtility != null)
			localUpperBound = (U)this.optimalLocalUtility.add(this.upperBoundSums[upperBoundSums.length-1]);
	}

	/**
	 * This method instantiates the upper bounds
	 * @param currentNode	the node currently being visited
	 * @param currentPath	the path taken
	 * @param depth			the current depth
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return the upperBound for this node
	 */
	@SuppressWarnings("unchecked")
	protected U initiateBounds(InnerNode<U, L> currentNode, IntArrayWrapper currentPath, int depth, boolean onLocalPath, Good<Val, U> g, int sender) {
		int nextDepth = depth + 1;
		int branching = branchingFactor[depth];
		int localIndex = -2;
		if(optimalLocalPath != null)
			localIndex = optimalLocalPath[depth];
		U maxUB = null;
		int maxUBIndex = -1;
		U maxUtil = null;
		int maxUtilIndex = -1;

		if(depth == depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				L leaf = (L)currentNode.getChild(i);
				if(leaf != null) {
					currentPath.setValue(depth, i);

					U UB = leaf.calculateUB(upperBoundSums, maximize);
					leaf.setUB(UB);

					if(leaf.ubCandidate(maxUB, maximize)) {
						maxUB = UB;
						maxUBIndex = i;
					}


					if(leaf.utilCandidate(maxUtil, maximize)) {
						maxUtil = leaf.getUtil();
						maxUtilIndex = i;
					}
				}
			}

			currentNode.setUB(maxUBIndex, true);
			currentNode.setUtil(maxUtilIndex, true);
			assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
		} else {

			int i = 0;
			for(; i < branching; i++) {
				currentPath.setValue(depth, i);
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);

				U UB = null;
				if(child != null) {
					UB = initiateBounds(child, currentPath, nextDepth, onLocalPath && (localIndex == -1 || localIndex == i), g, sender); 
				}

				if(UB != null) {
					
					if(child.ubCandidate(maxUB, maximize)) {
						maxUB = UB;
						maxUBIndex = i;
					}

					if(child.utilCandidate(maxUtil, maximize)) {
						maxUtil = child.getUtil();
						maxUtilIndex = i;
					}
				}
			}

			currentNode.setUB(maxUBIndex, false);
			currentNode.setUtil(maxUtilIndex, false);
			assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
		} 
		assert this.checkTree(depth, currentNode, currentPath, true, true, true, true);
		return maxUB;
	}

	/**
	 * Used to check whether the local path chosen is still alive
	 * For debuggin purposes only
	 * @author Brammert Ottens, 16 nov 2009
	 * @param depth			The current depth
	 * @param currentNode	The current node being visisted
	 * @return	\c true when the current locally optimal solution is still allowed
	 */
	@SuppressWarnings("unchecked")
	protected boolean localPathAlive(int depth, InnerNode<U, L> currentNode) {
		if(!currentNode.isAlive())
			return false;

		if(depth != depthFinalVariable) {
			int index = optimalLocalPath[depth];
			int nextDepth = depth + 1;
			if(index == -1) {
				boolean alive = false;
				for(int i = 0; i < branchingFactor[depth]; i++) {
					Node<U> child = ((InnerNode<U, L>)currentNode).getChild(i);
					if(child == null)
						return true;
					else if(localPathAlive(nextDepth, (InnerNode<U, L>)child))
						alive = true;
				}
				return alive;
			} else {
				Node<U> child = ((InnerNode<U, L>)currentNode).getChild(index);
				if(child == null)
					return true;
				return localPathAlive(nextDepth, (InnerNode<U, L>)child);
			}
		}

		return true;

	}

	/**
	 * Method to check whether the (possibly) partial path
	 * is alive, i.e. all inner nodes on the path either are alive
	 * or do not exists
	 * @author Brammert Ottens, 12 nov 2009
	 * @param path			the path to be checked
	 * @param depth			the current depth
	 * @param currentNode	the currently visited node
	 * @return \c true when the path is alive or does not exist, and false otherwise
	 */
	@SuppressWarnings("unchecked")
	protected boolean pathAlive(int[] path, int depth, InnerNode<U, L> currentNode) {
		if(!currentNode.alive)
			return false;
		if(depth == depthFinalVariable) {
			if(currentNode.isAlive()) {
				return true;
			}
		} else {
			int childIndex = path[depth];
			int nextDepth = depth + 1;

			if(childIndex == -1) {
				boolean r = false;
				int branching = branchingFactor[depth];
				for(int i = 0; i < branching; i++) {
					Node<U> child = currentNode.getChild(i);
					if(child == null || pathAlive(path, nextDepth, (InnerNode<U, L>)child))
						r = true;
				}
				return r;
			} else {
				Node<U> child = currentNode.getChild(childIndex);
				if(child == null || pathAlive(path, nextDepth, (InnerNode<U, L>)child))
					return true;
			}

		}
		return false;
	}

	/**
	 * This method walks trough the tree according to the given partial path and finds all leaf nodes
	 * that correspond to assignments that are compatible with the good to be added
	 * @param depth			The current depth
	 * @param currentPath	The current path
	 * @param partialPath	The path dictated by the received good
	 * @param currentNode	The current node
	 * @param g 			the utility reported
	 * @param utilityDelta	The difference with the previously reported utility for the assignment belonging to the partial path
	 * @param sender 		The sender of the good
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param withUB		\c true when the UB must be updated as well
	 */
	@SuppressWarnings("unchecked")
	protected void updatePath(int depth, IntArrayWrapper currentPath, int[] partialPath, InnerNode<U, L> currentNode, Good<Val, U> g, U utilityDelta, int sender, boolean onLocalPath, final boolean withUB) {
		if(currentNode.isAlive()) {
			int branching = branchingFactor[depth];
			int childIndex = partialPath[depth];
			int localIndex = -2;
			if(optimalLocalPath != null)
				localIndex = optimalLocalPath[depth];
			int nextDepth = depth + 1;
			U maxUtil = null;
			int maxUtilIndex = -1;
			int maxUBIndex = -1;
			U maxUB = null;


			if(depth == depthFinalVariable) {
				// we have reached the last variable, all its children are leafnodes
				
				if(childIndex == -1) {
					// the received assignment did not specify the value for the trees' own variable
					for(int i = 0; i < branching; i++) {
						// set the current path
						currentPath.setValue(depth, i);
						
						// get the child
						L leaf = (L)currentNode.getChild(i);
						
						assert !withUB;
						if(leaf == null) {
							// the child should be created
							leaf = createLeaf(currentPath, g, sender, withUB);
							if(leaf == null)
								continue;
							currentNode.setChild(leaf, i);
						} else {
							// the child already exists, and must be updated
							if(withUB)
								if(this.storeReceivedGoods)
									leaf.updateLeafWithUB(g, utilityDelta, sender, upperBoundSums, powersOf2, goodsReceived.get(sender).containsKey(currentPath.getPartialAssignment(childrenVariables[sender], this.separatorSizePerChild[sender])), maximize);
								else
									leaf.updateLeafWithUB(g, utilityDelta, sender, upperBoundSums, powersOf2, false, maximize);
							
							else
								leaf.updateLeafNoUB(g, utilityDelta, sender, powersOf2, maximize);
							}
						assert  !this.storeReceivedGoods ||  checkLeaf(leaf, currentPath, withUB, g.getUtility(), sender);
						
						if(leaf.utilCandidate(maxUtil, maximize)) {
							// child is a candidate for max util
							maxUtil = leaf.getUtil();
							maxUtilIndex = i;
						}
						
						if(withUB && leaf.ubCandidate(maxUB, maximize)) {
							// child is a candidate for max UB
							maxUB = leaf.getUB();
							maxUBIndex = i;
						}
					}
					
					// update the current node
					currentNode.setUtil(maxUtilIndex, true);
					if(withUB) {
						currentNode.setUB(maxUBIndex, true);
					}
					
					assert currentNode.check5(upperBoundSums, maximize);
					assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
					assert currentNode.getMaxUtil().isUpToDate();
					assert currentNode.check4();
					assert currentNode.check2(maximize);
				} else {
					// the assignment did specify the variabes own assignment
					// get the child and specify the current path
					L leaf = (L)currentNode.getChild(childIndex);
					currentPath.setValue(depth, childIndex);

					if(leaf == null) {
						// the child must be created
						leaf = createLeaf(currentPath, g, sender, withUB);
						if(leaf != null) {
							currentNode.setChild(leaf, childIndex);
							assert  !this.storeReceivedGoods ||  checkLeaf(leaf, currentPath, withUB, g.getUtility(), sender);
						}
					} else {
						// the child already exists and must be updated
						if(withUB)
							if(this.storeReceivedGoods)
								leaf.updateLeafWithUB(g, utilityDelta, sender, upperBoundSums, powersOf2, goodsReceived.get(sender).containsKey(currentPath.getPartialAssignment(childrenVariables[sender], this.separatorSizePerChild[sender])), maximize);
							else
								leaf.updateLeafWithUB(g, utilityDelta, sender, upperBoundSums, powersOf2, false, maximize);
						else
							leaf.updateLeafNoUB(g, utilityDelta, sender, powersOf2, maximize);
						assert  !this.storeReceivedGoods ||  checkLeaf(leaf, currentPath, withUB, g.getUtility(), sender);
					}

					L currentUtil = currentNode.getMaxUtil();
					if(childIndex == currentNode.getMaxChild() || withUB || (currentUtil != null && currentUtil.getUtil() == infeasibleUtil)) {

						//						if(withUB && leaf.UB != this.minInfinite) {
						//							maxUB = leaf.UB;
						//							maxUBIndex = childIndex;
						//						}

						for(int i = 0; i < branching; i++) {
							L child = (L)currentNode.getChild(i);
							if(child != null) {

								if(withUB) {
									U UB = child.calculateUB(upperBoundSums, maximize);
									child.setUB(UB);

									if(child.ubCandidate(maxUB, maximize)) {
										maxUB = UB;
										maxUBIndex = i;
									}
								}

								if(child.utilCandidate(maxUtil, maximize)) {
									maxUtil = child.getUtil();
									maxUtilIndex = i;
								}
							} 
						}
						currentNode.setUtil(maxUtilIndex, true);
						if(withUB) {
							currentNode.setUB(maxUBIndex, true);
						}
						assert currentNode.check5(upperBoundSums, maximize);
					} else {
						if(leaf != null) {
							if(currentUtil == null) {
								currentNode.setUtil(childIndex, true);
								assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
							} else { 
								int diff = maximize ? currentUtil.getUtil().compareTo(leaf.getUtil()) : leaf.getUtil().compareTo(currentUtil.getUtil());
								if( diff < 0 || (diff == 0 && currentNode.getMaxChild() > childIndex))  {
									currentNode.setUtil(childIndex, true);
									assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
								}
							}
						}
					}
				}
			} else {

				if(childIndex == -1) { // all children must be updated
					for(int i = 0; i < branching; i++) {
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						currentPath.setValue(depth, i);

						if(child == null) {
							if(withUB)
								child = createPathWithUB(nextDepth, currentPath, partialPath, true, g, sender);
							else
								child = createPathNoUB(nextDepth, currentPath, partialPath, true, g, sender);
							
							if(child == null)
								continue;
							currentNode.setChild(child, i);
						} else {
							updatePath(nextDepth, currentPath, partialPath, child, g, utilityDelta, sender, onLocalPath && (localIndex == -1 || localIndex == i), withUB);
						} 
						
						if(child.isAlive()) {
							if(withUB && child.ubCandidate(maxUB, maximize)) {
								maxUB = child.getUB();
								maxUBIndex = i;
							}
							
							if(child.utilCandidate(maxUtil, maximize)) {
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}
						}
					} 
					
					currentNode.setUtil(maxUtilIndex, false);
					assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
					if(withUB)
						currentNode.setUB(maxUBIndex, false);
					
					assert currentNode.check5(upperBoundSums, maximize);
				} else {
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(childIndex);
					currentPath.setValue(depth, childIndex);
					boolean childExists = child != null;
					
					if(!childExists) { 
						if(withUB)
							child = createPathWithUB(nextDepth, currentPath, partialPath, true, g, sender);
						else
							child = createPathNoUB(nextDepth, currentPath, partialPath, true, g, sender);
						childExists = child != null;
						currentNode.setChild(child, childIndex);
					} else {
						updatePath(nextDepth, currentPath, partialPath, child, g, utilityDelta, sender, onLocalPath && (localIndex == -1 || localIndex == childIndex), withUB);
					} 

					L currentUtil = currentNode.getMaxUtil();
					if((childIndex == currentNode.getMaxChild() && childExists) || withUB || (currentUtil != null && (!currentUtil.isUpToDate() || currentUtil.getUtil() == infeasibleUtil))) {

						for(int i = 0; i < branching; i++) {
							InnerNode<U, L> node = (InnerNode<U, L>)currentNode.getChild(i);
							if(node != null && node.isAlive()) {
								if(withUB && node.hasUB()) {
									if(i != childIndex)											
										recalculateUB(nextDepth, node, maxUB, true, true);
									
									if(node.ubCandidate(maxUB, maximize)) {
										maxUB = node.getUB();
										maxUBIndex = i;
									}
								}

								if(node.utilCandidate(maxUtil, maximize)) {
									maxUtil = node.getUtil();
									maxUtilIndex = i;
								}
							}

						}
						currentNode.setUtil(maxUtilIndex, false);
						if(withUB) {
							currentNode.setUB(maxUBIndex, false);
						}

						assert currentNode.check5(upperBoundSums, maximize);
						assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
						assert currentNode.check3(maximize);
					} else if (childExists && currentNode.isAlive() && child.hasUtil()) {
						if(currentUtil == null) {
							currentNode.setUtil(childIndex, false);
							assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
						} else { 
							int diff = maximize ? currentUtil.getUtil().compareTo(((InnerNode<U, L>)currentNode.getChild(childIndex)).getUtil()) : ((InnerNode<U, L>)currentNode.getChild(childIndex)).getUtil().compareTo(currentUtil.getUtil()); 
							if( diff < 0 || (diff == 0 && currentNode.getMaxChild() > childIndex))  {
								currentNode.setUtil(childIndex, false);
								assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
							}
						}
					}
				}
			}
			assert !currentNode.hasUtil() || currentNode.getMaxUtil().isUpToDate();
			assert currentNode.check2(maximize);
			assert currentNode.check5(upperBoundSums, maximize);
			assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
			assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
			assert this.checkTree(depth, currentNode, currentPath, withUB, false, true, true);
		}
	}

	/**
	 * @author Brammert Ottens, 16 nov 2009
	 * Find the next best local solution that is still allowed
	 */
	protected void updateLocalProblem() {

		if(!localProblemIterator.hasNext()) {
			this.localUpperBound = this.infeasibleUtil;
			this.optimalLocalUtility = this.infeasibleUtil;
			optimalLocalPath = null;
			return;
		}

		int[] path = new int[this.numberOfVariables];
		Arrays.fill(path, -1);
		int startIndex = numberOfVariables - numberOfLocalVariables;

		Val[] assignment = null;
		U utility = null;
		while(localProblemIterator.hasNext()) {
			assignment = localProblemIterator.nextSolution();
			if(assignment != null) {
				utility = localProblemIterator.getCurrentUtility();
				int j = 0;
				for(int i = startIndex; i < numberOfVariables; i++, j++) {
					path[i] = valuePointers.get(depthToVariable[i]).get(assignment[j]);  
				}
				if(pathAlive(path, 0, root))
					break;
				else
					assignment = null;
			}
		}

		if(assignment == null)
			this.optimalLocalUtility = this.infeasibleUtil;
		else {
			this.optimalLocalUtility = utility;
			optimalLocalPath = path;
		}

		if(this.upperBoundIsInfiniteCounter == 0)
			localUpperBound = this.optimalLocalUtility.add(this.upperBoundSums[upperBoundSums.length-1]);
	}

	/**
	 * Updates the upperBoundSums array given the child that has changed its bound,
	 * the old bound and the new bound
	 * @author Brammert Ottens, 25 jun 2009
	 * @param child			The child whose bound has changed
	 * @param oldBound		The old bound
	 * @param newBound		The new bound
	 */
	protected void updateUpperBoundSums(int child, U oldBound, U newBound) {

		U difference = newBound;
		if(oldBound != null) {
			if(oldBound == infeasibleUtil)
				difference = zero;
			else
				difference = (U)difference.subtract(oldBound);
		}

		int size = (int)Math.pow(2, numberOfChildren) - 1;
		boolean[] chosen = new boolean[numberOfChildren];
		for(int counter = 0; counter < size; counter++) {
			int n=0;
			chosen[0]=!chosen[0];
			boolean carry=!chosen[0];
			while(n+1<chosen.length)
			{
				n++;
				if(carry)
				{
					chosen[n]=!chosen[n];
					carry=!chosen[n];
				}
				else break;
			}

			if(chosen[child])
				upperBoundSums[counter] = upperBoundSums[counter].add(difference);
		}

		assert this.checkUpperBoundSums(child, newBound);
		if(hasLocalProblem && optimalLocalUtility != null)
			localUpperBound = this.optimalLocalUtility.add(this.upperBoundSums[upperBoundSums.length-1]);
	}
	
	/**
	 * 
	 * @author Brammert Ottens, 22 apr 2010
	 * @param node	the node to be checked
	 * @param UB	its newly calculated upper bound
	 * @return	\c true when UB equals minInfinte
	 */
	protected boolean upperBoundChangesUtil(InnerNode<U, L> node, U UB) {
		return UB == infeasibleUtil;
	}
	

	/**
	 * This method recalculates the upper bound for a particular node. If the upper bound
	 * of this node is already up to date nothing happens. If not, the method
	 * searches further down the tree to find the actual current upper bound
	 * 
	 * @param depth			the current depth
	 * @param currentNodeUncast	the node that is currently visited
	 * @param UBtoBeat		the UB that needs to be beat
	 * @param recalculateUtil \c true when the recalculation of the utility can make a difference higher up
	 * @param recalculateUB 	\c true when the recalculation of the upperbound can make a difference higher up
	 * @return the new upper bound for currentNode
	 */
	@SuppressWarnings("unchecked")
	protected U recalculateUB(int depth, InnerNode<U, L> currentNodeUncast, U UBtoBeat, boolean recalculateUtil, boolean recalculateUB) {
		InnerNode<U, L> currentNode = (InnerNode<U, L>)currentNodeUncast;
		L currentUB = currentNode.getMaxUB();
		if(currentNode.isAlive() && currentUB != null) {
			L currentUtil = currentNode.getMaxUtil();
			boolean utilExists = currentUtil != null;
			boolean reachedLeaves = depth == depthFinalVariable;
			
			// If there is no UB to beat, than set it to min infinity
			if(UBtoBeat == null)
				UBtoBeat = infeasibleUtil;
			
			// recalculate the UB of the current node
			U newUB = currentUB.counter != 0 ? currentUB.calculateUB(upperBoundSums, maximize): currentUB.getUB();
			
			// recalculate the UB if there is a change of changing the higher upperbound and the UB is not up to date
			recalculateUB = recalculateUB && greaterThanOrEqual(UBtoBeat, currentUB.getUB()) && !currentUB.getUB().equals(newUB);
			
			// check whether the current max util is s till up to date!
			if(currentUtil != currentUB && utilExists && currentUtil.counter != 0) {
				currentUtil.calculateUB(upperBoundSums, maximize);
			}
			
			// recalculate the max util if the current max util is not up to date and the current node
			// is a real node
			recalculateUtil = recalculateUtil && currentNode.recalculateUtil();
			
			// if the current UB is set to minInfinite and the current Utility
			// is equal to minInfinite, then we want to recalculate the utility
			if(this.upperBoundChangesUtil(currentNode, newUB)) {
				if(reachedLeaves) {
					currentUB.setUpToDate(true);
					currentUB.setUtil(this.infeasibleUtil);	
				}
				
				recalculateUtil = recalculateUtil || (!utilExists || currentUtil.getUtil() == infeasibleUtil);
			}

			if(!recalculateUtil && !recalculateUB) { 
				// Recalculating the upper bound for this sub tree will not make
				// a difference, either because the bound is already up to date,
				// or because it is not better than UBtoBeat and can never be.
				assert currentNode.check2(maximize);
				assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
				return newUB;
			} else {
				int branching = branchingFactor[depth];
				int maxUBIndex = -1;
				int maxUtilIndex = -1;
				U maxUB = null;
				U maxUtil = null;
				if(recalculateUB)
					currentNode.setUB(-1, false);
				if(recalculateUtil)
					currentNode.setUtil(-1, false);
				
				if(reachedLeaves) {
					for(int i = 0; i < branching; i++) {
						L leaf = (L)currentNode.getChild(i);
						
						if(leaf != null) {
							// if the child exists, recalculate its upperbound and update the maxUB and maxUtil candidates
							U UB = leaf.counter != 0 ? leaf.calculateUB(upperBoundSums, maximize) : leaf.getUB();
							leaf.setUB(UB);
							if(leaf.getUB() == this.infeasibleUtil) {
								leaf.setUpToDate(true);
								leaf.setUtil(this.infeasibleUtil);
							}
							
							if(leaf.ubCandidate(maxUB, maximize)) {
								maxUB = UB;
								maxUBIndex = i;
							}
							
							if(recalculateUtil && leaf.utilCandidate(maxUtil, maximize)) {
								maxUtil = leaf.getUtil();
								maxUtilIndex = i;
							}
						}
					}
					
					if(maxUBIndex != -1) { // set max UB
						currentNode.setUB(maxUBIndex, true);
						assert maxUBIndex == -1 || currentNode.check5(upperBoundSums, maximize);
					}

					if(maxUtilIndex != -1) { // set max util
						currentNode.setUtil(maxUtilIndex, true);
					}
				} else {
					int nextDepth = depth + 1;
					for(int i = 0; i < branching; i++) {
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						
						if(child != null && child.isAlive()) {
							L childUtil = child.getMaxUtil();
							boolean childUtilExists = childUtil != null;
							boolean childUBExists = child.hasUB();
							
							if (childUtilExists && recalculateUtil && childUtil.counter != 0) // recalculate the util UB to check whether it is stil up to date
								childUtil.calculateUB(upperBoundSums, maximize);
							
							// if the child has an upperbound that might change the current upperbound when recomputed, or when
							// the child's utility is out of date, we want to recalculate the upperbound and util of the subtree
							// rooted at this child
							if((recalculateUB && childUBExists && greaterThanOrEqual(UBtoBeat, child.getUB()) && (maxUB == null || greaterThan(maxUB, child.getUB())) || (recalculateUtil && childUtilExists && !childUtil.isUpToDate()))) {
									recalculateUB(nextDepth, child, UBtoBeat, true, true);
							}
							
							if(child.ubCandidate(maxUB, maximize)) {
								maxUB = child.getUB();
								maxUBIndex = i;
							}
							
							if(recalculateUtil && child.utilCandidate(maxUtil, maximize)) {
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}
						}
					}

					if(maxUBIndex != -1) { // set max UB
						currentNode.setUB(maxUBIndex, false);
						assert !recalculateUB || currentNode.check5(upperBoundSums, maximize) || greaterThanOrEqual(currentNode.getUB(), UBtoBeat);
					}

					if(maxUtilIndex != -1) { // set max util
						currentNode.setUtil(maxUtilIndex, false);
					}

				}
			}
			assert currentNode.check5(upperBoundSums, maximize) || greaterThanOrEqual(currentNode.getUB(), UBtoBeat);
			assert currentNode.check4();
			return currentNode.getUB();
		}
		return null;
	}



	/**
	 * This method checks whether any of the leafnodes have become infeasible due to the addition of a new variable
	 * 
	 * @author Brammert Ottens, 27 jan 2010
	 * @param currentNode	the currently visited node
	 * @param depth			the depth of this node
	 * @param currentPath 	the path take through the tree to get here
	 */
	@SuppressWarnings("unchecked")
	protected void removeInconsistencies(InnerNode<U, L> currentNode, int depth, IntArrayWrapper currentPath) {
		int branching = this.branchingFactor[depth];
		U maxUtil = null;
		int maxUtilIndex = -1;
	
		if(depth == depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				L child = (L)currentNode.getChild(i); 
				if(child != null) {
					U util = this.getUtilityLocalProblem(currentPath);
					if(util.equals(this.infeasibleUtil)) {
						child.setInfeasable(this.infeasibleUtil);
					}
	
					if(child.utilCandidate(maxUtil, maximize)) {
						maxUtil = child.getUtil();
						maxUtilIndex = i;
					}
				}
			}
	
			currentNode.setUtil(maxUtilIndex, true);
		} else {
			int nextDepth = depth + 1;
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				if(child != null) {
					removeInconsistencies(child, nextDepth, currentPath);
					if(child.utilCandidate(maxUtil, maximize)) {
						maxUtil = child.getUtil();
						maxUtilIndex = i;
					}
				}
			}
			currentNode.setUtil(maxUtilIndex, false);
		}
	}

	/**
	 * This method finds the leaf node corresponding to the current best assignment,
	 * removes all its children and sets its parent node to a dead node.
	 * @param depth			the current depth
	 * @param currentNode	the current node being visited
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @return	\c true when the local path has been removed
	 */
	@SuppressWarnings("unchecked")
	protected boolean removePath(int depth, InnerNode<U, L> currentNode, boolean onLocalPath) {

		boolean local = false;
		int localIndex = -2;
		if(optimalLocalPath != null)
			localIndex = optimalLocalPath[depth];
		if(depth >= this.depthOfFirstToBeRemovedVariables) {
			assert currentNode.getMaxChild() != -1;
			currentNode.retainOnly(currentNode.getMaxChild());
			if(onLocalPath) {
				this.localCounter--;
				assert localCounter >= 0;
				assert localCounter < this.maxNumberLocalProblemOccurences;
				local = true;
			}
			currentNode.setUtil(-1, false);
			currentNode.setUB(-1, true);
			currentNode.setAlive(false);
		} else {
			onLocalPath = onLocalPath && (localIndex == -1 || localIndex == currentNode.getMaxChild());

			local = removePath(depth + 1, (InnerNode<U, L>)currentNode.getChild(currentNode.getMaxChild()), onLocalPath);
			boolean alive = false;

			U maxUB = null;
			U maxUtil = null;
			int maxUtilIndex = -1;
			int maxUBIndex = -1;
			for(int i = 0; i < currentNode.children.length; i++) {
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				if(child == null) {
					alive = true;
				} else if(child.isAlive()) {
					U UB = recalculateUB(depth + 1, child, maxUB, true, true);
					if(UB != null && child.ubCandidate(maxUB, maximize)) {
						maxUB = UB;
						maxUBIndex = i;
					}

					if(child.utilCandidate(maxUtil, maximize)) {
						maxUtil = child.getUtil();
						maxUtilIndex = i;
					}
					alive = true;
				}
			}

			if(alive) {
				currentNode.setUB(maxUBIndex, false);
				currentNode.setUtil(maxUtilIndex, false);
				assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
				assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
				assert currentNode.check1();
				assert currentNode.check2(maximize);
				//@todo fails very rarely ... find out why!
				assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
			} else {
				currentNode.setUtil(-1, false);
				currentNode.setUB(-1, false);
				currentNode.setAlive(false);
			}
		}

		return local;
	}
	
	/**
	 * Takes a good received by a child and transforms the assignment
	 * to a key.
	 * @param values	the values reported by the child
	 * @param variables the variables reported by the child
	 * @param sender 	the child that send the good
	 * @return The key corresponding to the assignment of the good
	 */
	protected IntArrayWrapper toKey(Val[] values, String[] variables, int sender) {
		int[] ordered_values = new int[numberOfVariables];


		for(int i = 0; i < values.length; i++) {
			String variable = variables[i];
			Integer depth = variableToDepth.get(variable);
			HashMap<Val, Integer> valuePointer = valuePointers.get(variable); 
			if(depth != null && valuePointer != null) {
				Integer pointer = valuePointer.get(values[i]);
				if(pointer != null)
					ordered_values[depth] = pointer; 
			}
		}

		return createIntArrayWrapper(ordered_values);
	}
	
	/**
	 * 
	 * @author Brammert Ottens, 22 apr 2010
	 * @param array an array to be wrapped
	 * @return	a wrapper around the array
	 */
	public IntArrayWrapper createIntArrayWrapper(int[] array) {
		return new IntArrayWrapper(array);
	}
	
	/**
	 * @author Brammert Ottens, 22 apr 2010
	 * @param size the size of an array
	 * @return	a wrapper around an array with size \c size
	 */
	public IntArrayWrapper createIntArrayWrapper(int size) {
		return new IntArrayWrapper(size);
	}
	
	/**
	 * 
	 * The IntArrayWrapper is used as a key for (partial) assignments.
	 * The hash function used is a java implementation of the Hsieh hash function
	 * 
	 * @author Brammert Ottens, 26 jun 2009
	 *
	 */
	public static class IntArrayWrapper {

		/** the array representing the variable assignment*/
		protected int[] array;

		/** the byte representation of array*/
		protected byte[] byteArray;

		/** \c true when the array is to be changed, \c false otherwise*/
		protected final boolean change;

		/**
		 * Constructor
		 * @param array the array to be stored. This array should be immutable
		 */
		public IntArrayWrapper(int[] array) {
			change = false;
			this.array = new int[array.length];
			this.byteArray = this.intArrayToByteArray(array);
			System.arraycopy(array, 0, this.array, 0, array.length);
		}
		
		/**
		 * Constructor
		 * @param array the array to be stored. This array should be immutable
		 * @param byteArray the byte representation of array
		 */
		public IntArrayWrapper(int[] array, byte[] byteArray) {
			change = false;
			this.array = array;
			this.byteArray = byteArray;
		}
		
		/**
		 * Constructor
		 * Constructs a new array of size \c size
		 * @param size	the size of the new array
		 */
		public IntArrayWrapper(int size) {
			this.array = new int[size];
			this.byteArray = new byte[size*4];
			change = true;
		}
		
		/**
		 * Sets the value in the array
		 * @author Brammert Ottens, 26 jun 2009
		 * @param index	the position in the array
		 * @param value	the value to be placed
		 */
		public void setValue(int index, int value) {
			if(change) {
				array[index] = value;
				int byteIndex = index*4;
				byteArray[byteIndex]   = (byte)(value >>> 24);
				byteArray[++byteIndex] = (byte)(value >>> 16);
				byteArray[++byteIndex] = (byte)(value >>> 8);
				byteArray[++byteIndex] = (byte)value;
			}
		}

		/**
		 * @author Brammert Ottens, 1 jul 2009
		 * @param index	the position in the array whose value we want to know
		 * @return returns the value of the array at \c index
		 */
		public int getValue(int index) {
			return array[index];
		}

		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			String str = "[";
			int i = 0;
			for(; i < array.length - 1; i++) {
				str += array[i] + ", ";			
			}
			str += array[i] + "]";
			return str;
		}

		/**
		 * Given a new set of variables and values, this
		 * array is to be extended. If it already is of the correct
		 * size, the values are simply updated
		 * 
		 * @author Brammert Ottens, 30 jun 2009
		 * @param newSize	the size of the new array
		 */
		public void extendArray(int newSize) {
			assert newSize >= array.length;
			if(array.length != newSize) {
				int additionalPlaces = newSize - array.length;
				int[] newArray = new int[newSize];
				byte[] newByteArray = new byte[newSize*4];
				int i2 = 0;
				for(int i = 0; i < additionalPlaces; i++) {
					newArray[i] = -1;
					byteArray[i2]   = (byte)(-1 >>> 24);
					byteArray[++i2] = (byte)(-1 >>> 16);
					byteArray[++i2] = (byte)(-1 >>> 8);
					byteArray[++i2] = (byte)-1;
					i2++;
				}
				System.arraycopy(array, 0, newArray, additionalPlaces, array.length);
				System.arraycopy(byteArray, 0, newByteArray, additionalPlaces*4, array.length);
				array = newArray;
			}
		}
		
		/**
		 * Given a set of new values, a new IntArrayWrapper
		 * is created. Note that there can be less new values than 
		 * there are positions in the original array
		 * 
		 * @author Brammert Ottens, 4 okt 2009
		 * @param newValues	an array of new values
		 * @param changable array that stores which values can be changed 
		 * @param newSize the size of the required array
		 * @return a new IntArrayWrapper with the new values placed in the appropriate positions
		 */
		public IntArrayWrapper addValues(int[] newValues, boolean[] changable, int newSize) {
			assert newSize == changable.length;
			int[] newArray = new int[newSize];
			byte[] newByteArry = new byte[newSize*4];
			int i2 = 0;
			int j = 0;
			int k = 0;
			int k2 = 0;
			for(int i = 0; i < newSize;) {
				if(changable[i]) {
					int value = newValues[j++];
					newArray[i] = value;
					newByteArry[i2]   = (byte)(value >>> 24);
					newByteArry[++i2] = (byte)(value >>> 16);
					newByteArry[++i2] = (byte)(value >>> 8);
					newByteArry[++i2] = (byte)value;
				}
				else {
					newArray[i] = array[k++];
					newByteArry[i2]   = byteArray[k2];
					newByteArry[++i2] = byteArray[++k2];
					newByteArry[++i2] = byteArray[++k2];
					newByteArry[++i2] = byteArray[++k2];
				}
				i++;
				i2++;
			}

			return new IntArrayWrapper(newArray);
		}
		
		/**
		 * Returns the partial assignment of the assignment stored in \c array. Only the positions
		 * that are \c true in \c neededVariables are used
		 * @author Brammert Ottens, 26 jun 2009
		 * @param neededVariables	the variables in the partial assignment
		 * @param size				the number of variables in the partial assignment
		 * @return	an array of int
		 */
		public IntArrayWrapper getPartialAssignment(boolean[] neededVariables, int size) {
			if(size == array.length) {
				return this;
			}

			int[] newArray = new int[size];
			byte[] newByteArray = new byte[size*4];
			int numberOfVariables = neededVariables.length;

			int index = 0;
			int i2 = 0;
			for(int i = 0; i < size; i++) {
				while(index < numberOfVariables && !neededVariables[index]) {
					index++;
				}
				if(index < numberOfVariables) {
					int value = array[index];
					int index2 = index*4;
					newArray[i] = value;
					newByteArray[i2] = byteArray[index2];
					newByteArray[++i2] = byteArray[++index2];
					newByteArray[++i2] = byteArray[++index2];
					newByteArray[++i2] = byteArray[++index2];
				}
				index++;
				i2++;
			}

			return new IntArrayWrapper(newArray, newByteArray);
		}

		/**
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object o) {
			int[] otherArray = ((IntArrayWrapper)o).array;
			int length = otherArray.length;
			if(array.length != length)
				return false;

			for(int i = 0; i < length;) {
				if(array[i] != otherArray[i])
					return false;
				i++;
			}

			return true;
		}


		/**
		 * Uses the superFastHash() function to compute the hash
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			//			byte[] byteArray = this.intArrayToByteArray(array);
			return superFastHash(byteArray, byteArray.length);
		}

		/**
		 * Transforms an array of int into an array of byte
		 * 
		 * @author Brammert Ottens, 26 jun 2009
		 * @param intArray the array of int
		 * @return	the byte representation of the input array
		 */
		public byte[] intArrayToByteArray(int[] intArray) {
			int length = intArray.length;
			byte[] byteArray = new byte[length * 4];

			int i2 = 0;
			for(int i = 0; i < length;) {
				int value = intArray[i];
				byteArray[i2]   = (byte)(value >>> 24);
				byteArray[++i2] = (byte)(value >>> 16);
				byteArray[++i2] = (byte)(value >>> 8);
				byteArray[++i2] = (byte)value;
				i++;
				i2++;
			}

			return byteArray;
		}

		/**
		 * Returns 16 bits stored in an int
		 * @author Brammert Ottens, 26 jun 2009
		 * @param d			the data
		 * @param index		the index from which 16 bits must be got
		 * @return	16 bits of \c d starting at \c index
		 */
		public int get16bits(byte[] d, int index) {
			return (d[index + 1] << 8) + d[index];
		}

		/**
		 * Implementation of the Hsieh hash function, based on the following c-code by Paul Hsieh
		 *
		 *		unsigned int get16bits (unsigned char *d) {
		 *		   return ((unsigned int) d[1] << 8) + ((unsigned int) d[0]);
		 *		}
		 *
		 *		unsigned int SuperFastHash (char * data, int len) {
		 *		   unsigned int hash = len, tmp;
		 *		   int rem;
		 *
		 *		     if (len <= 0 || data == NULL) return 0;
		 *
		 *		     rem = len & 3;
		 *		     len >>= 2;
		 *
		 *		     /* Main loop 
		 *		     for (;len > 0; len--) {
		 *		         hash  += get16bits (data);
		 *		         tmp    = (get16bits (data+2) << 11) ^ hash;
		 *		         hash   = (hash << 16) ^ tmp;
		 *		         data  += 2*2;
		 *		         hash  += hash >> 11;
		 *		     }
		 *
		 *		     /* Handle end cases 
		 *		     switch (rem) {
		 *		         case 3: hash += get16bits (data);
		 *		                 hash ^= hash << 16;
		 *		                 hash ^= data[2] << 18;
		 *		                 hash += hash >> 11;
		 *		                 break;
		 *		         case 2: hash += get16bits (data);
		 *		                 hash ^= hash << 11;
		 *		                 hash += hash >> 17;
		 *		                 break;
		 *		         case 1: hash += *data;
		 *		                 hash ^= hash << 10;
		 *		                 hash += hash >> 1;
		 *		     }
		 *
		 *		     /* Force "avalanching" of final 127 bits
		 *		     hash ^= hash << 3;
		 *		     hash += hash >> 5;
		 *		     hash ^= hash << 4;
		 *		     hash += hash >> 17;
		 *		     hash ^= hash << 25;
		 *		     hash += hash >> 6;
		 *
		 *		     return hash;
		 *		}
		 *
		 * @author Brammert Ottens, 26 jun 2009
		 * @param data	the data to be hashed
		 * @param len	the length of the array
		 * @return		the hashcode belonging to \c data
		 */
		private int superFastHash(byte[] data, int len) {
			int hash = len, tmp;
			int rem;

			if(len <= 0 || data == null)
				return 0;

			rem = len & 3;
			len >>= 2;
			int index = 0;

			/* Main loop */
			for(;len > 0; len--) {
				hash += get16bits(data, index);
				tmp   = (get16bits(data, index+2) << 11) ^ hash;
				hash  = (hash << 16) ^ tmp;
				index += 2*2;
				hash += hash >>> 11;
			}

			/* Handle end cases */
			switch (rem) {
			case 3: hash += get16bits (data, index);
			hash ^= hash << 16;
			hash ^= data[index + 2] << 18;
			hash += hash >>> 11;
			break;
			case 2: hash += get16bits (data, index);
			hash ^= hash << 11;
			hash += hash >>> 17;
			break;
			case 1: hash += data[index];
			hash ^= hash << 10;
			hash += hash >>> 1;
			}

			/* Force "avalanching" of final 127 bits */
			hash ^= hash << 3;
			hash += hash >>> 5;
			hash ^= hash << 4;
			hash += hash >>> 17;
			hash ^= hash << 25;
			hash += hash >>> 6;

			return hash;
		}
	}
	

	/* CODE USED FOR DEBUGGING */

		
	/**
	 * Method to check that the utility and UB are consistent with the available information.
	 * The utility should match exactly, but because UBs are only recomputed when necessary,
	 * and then should only decrease by such a recomputation, the stored UB should be at least as high
	 * as the real UB
	 * @param leaf			the leaf to be checked
	 * @param currentPath	the path to this leaf
	 * @param checkUB 		\c true when the UB must be checked
	 * @param utility 		the utility reported by the sender
	 * @param sender		the sender of the good
	 * @return always returns true
	 */
	public boolean checkLeaf(L leaf, IntArrayWrapper currentPath, boolean checkUB, U utility, int sender) {
		U UB = this.getUtilityLocalProblem(currentPath);

		U util = UB;
		U childUtil = zero;
		int counter = this.numberOfChildren;
		for(int i = 0; i < numberOfChildren; i++) {
			U temp = goodsReceived.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
			if(temp != null) {
				counter--;
				childUtil = childUtil.add(temp);
				UB = UB.add(temp);

			} else if(upperBounds[i] != null) { // this function should only be called when all the upperBounds are set!
				UB = UB.add(upperBounds[i]);
			}
		}

		util = util.add(childUtil);
		
		assert counter == leaf.counter;
		assert !checkUB || greaterThanOrEqual(UB, leaf.getUB()) : UB.toString() + " vs. " + leaf.getUB();
		assert UB == infeasibleUtil || leaf.getUtil().equals(util) : leaf.getUtil().toString() + " != " + util;	// the utility should be correct
		assert !checkUB || greaterThanOrEqual(leaf.getUB(), oldUB);							// the UB should not be higher than the old max UB
		assert !checkUB || leaf.counter != 0 || leaf.getUB().equals(leaf.getUtil());					// if the assignment is confirmed, UB should be equal to utility

		return true;
	}

	/**
	 * Method to check the initialization of the tree
	 * @param h	the hypercube to compare with
	 * @return true when this tree matches the hypercube
	 * @bug Never used!
	 */
	@SuppressWarnings("unchecked")
	public boolean compareWithHypercube(UtilitySolutionSpace<Val, U> h) {
		return compareWithHypercube(0, (Val[])Array.newInstance(domains.get(depthToVariable[0]).get(0).getClass(), numberOfVariables), root, h);
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#pathExists(Val[])
	 */
	@SuppressWarnings("unchecked")
	public boolean pathExists(Val[] values) {
		int[] path = new int[this.numberOfVariables];
		for(int i = 0; i < numberOfVariables; i++) {
			path[i] = this.valuePointers.get(depthToVariable[i]).get(values[i]);
		}

		Node<U> currentNode = root;
		int depth = 0;
		while(depth < numberOfVariables) {
			currentNode = ((InnerNode<U, L>)currentNode).getChild(path[depth]);
			if(currentNode == null)
				return false;
			depth++;
		}

		return true;
	}

	/**
	 * Test to see whether a leafnode used as upperbound
	 * actually exists, i.e. to check whether the UB is correctly
	 * being updated
	 * 
	 * @author Brammert Ottens, 5 okt 2009
	 * @param currentNode the current node in the tree being visited
	 * @param depth		  the current depth
	 * @return	\c true when the leafnode exists, and \c false otherwise
	 */
	@SuppressWarnings("unchecked")
	public boolean UBexists(InnerNode<U, L> currentNode, int depth) {
		int nextDepth = depth + 1;
		L UBnode = currentNode.getMaxUB();
		int branching = branchingFactor[depth];
		for(int i = 0; i < branching; i++) {
			if(depth == depthFinalVariable) {
				L child = (L)currentNode.getChild(i);
				if(child != null && child.getUB().equals(UBnode.getUB()));
				return true;
			} else{
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				if(child != null && child.hasUB() && (child.getMaxUB() == UBnode /*|| child.UB.UB.equals(UBnode.UB)*/)) {
					return UBexists(child, nextDepth);
				}
			}

		}

		return false;
	}

	/**
	 * Checks whether the maxUtil of a node actually exists.
	 * 
	 * @author Brammert Ottens, 12 okt 2009
	 * @param currentNode the current node
	 * @param depth	the current depth
	 * @return \c true when the util node exists, and false otherwise
	 */
	@SuppressWarnings("unchecked")
	public boolean Utilexists(InnerNode<U, L> currentNode, int depth) {
		int nextDepth = depth + 1;
		LeafNode<U> Utilnode = currentNode.getMaxUtil();
		int branching = branchingFactor[depth];
		for(int i = 0; i < branching; i++) {
			if(depth == depthFinalVariable) {
				L child = (L)currentNode.getChild(i);
				if(child != null && child == Utilnode/*child.util.equals(Utilnode.util)*/)
					return true;
			} else{
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				if(child != null && child.hasUtil() && (child.getMaxUtil() == Utilnode || child.getUtil().equals(Utilnode.getUtil()))) {
					return Utilexists(child, nextDepth);
				}
			}

		}

		return false;
	}

	/**
	 * Wrapper around treeSize(int, int, Node)
	 * @return the number of leaves in the tree
	 */
	public int treeSize() {
		return treeSize(0, 0, root);
	}

	/**
	 * Method to check the precomputation of the upper bound sums
	 * @author Brammert Ottens, 26 jun 2009
	 * @param child		the child that reported a new bound
	 * @param newBound	the new bound
	 * @return	\c true
	 */
	@SuppressWarnings("unchecked")
	private boolean checkUpperBoundSums(int child, U newBound) {

		U[] boundsCopy = (U[])new Addable[numberOfChildren];
		System.arraycopy(upperBounds, 0, boundsCopy, 0, numberOfChildren);
		boundsCopy[child] = newBound;

		boolean[] chosen = new boolean[numberOfChildren];
		chosen[0] = true;
		assert upperBoundSums[0].equals(boundsCopy[0]): upperBoundSums[0] + " != " + boundsCopy[0];
		for(int counter = 1; counter < upperBoundSums.length; counter++) {
			int n=0;
			U sum = zero;
			chosen[0]=!chosen[0];
			boolean carry=!chosen[0];
			while(n+1<chosen.length)
			{
				n++;
				if(carry)
				{
					chosen[n]=!chosen[n];
					carry=!chosen[n];
				}
				else break;
			}

			for(int i = 0; i < numberOfChildren; i++) {
				if(chosen[i])
					sum = (U)sum.add(boundsCopy[i]);
			}

			assert upperBoundSums[counter].equals(sum) : this.upperBoundSums[counter].toString() + " != " + sum;
		}

		return true;
	}

	/**
	 * This method checks the tree on the following properties
	 * 
	 * - the UB is properly propagated
	 * - the utility is properly propagated
	 * - every leaf node with support is present
	 * - no UB is greater than the old upper bound
	 * 
	 * @param depth			the current depth
	 * @param currentNode	the current node being visited
	 * @param currentPath 	the current path taken
	 * @param UB 			the current UB
	 * @param checkLeafs 	\c true when the leaves are to be checked
	 * @param checkSupport 	\c check whether existing leaf nodes actually have support
	 * @param checkUtil		\c true when utility values should be checked
	 * @return always returns true
	 */
	@SuppressWarnings("unchecked")
	protected boolean checkTree(int depth, InnerNode<U, L> currentNode, IntArrayWrapper currentPath, boolean UB, boolean checkLeafs, boolean checkSupport, boolean checkUtil) {
		int nextDepth = depth + 1;
		int branching = branchingFactor[depth];
		U maxUtil = null;
		U maxUB = null;
		int maxUBIndex = -1;
		int maxUtilIndex = -1;

		if(currentNode == null || currentNode.isAlive()) {
			if(depth == depthFinalVariable) {
				for(int i = 0; i < branching; i++) {
					if(currentPath != null)
						currentPath.setValue(depth, i);
					L leaf = null;
					if(currentNode != null)
						leaf = (L)currentNode.getChild(i);								// the leaf node should be consistent with the information

					if(leaf != null) {
						if(leaf.isUpToDate() && (maxUtil == null || greaterThan(maxUtil, leaf.getUtil()))) {
							maxUtil = leaf.getUtil();
							maxUtilIndex = i;
						}

						if(UB && leaf.getUB() != null && (maxUB == null || greaterThan(maxUB, leaf.getUB()))) {
							maxUB = leaf.getUB();
							maxUBIndex = i;
						}
						assert !checkSupport || !this.storeReceivedGoods || hasSupport(currentPath) || leaf.getUtil() == this.infeasibleUtil || !checkLeafs;
						assert !checkLeafs || !this.storeReceivedGoods || checkLeaf(leaf, currentPath, UB, null, -1);
					} else if(currentNode == null || currentNode.isAlive()) {
						assert !checkSupport || !this.storeReceivedGoods || !hasSupport(currentPath) || !checkLeafs;										// non-existing leafs should not have any support
					}
				}

				assert !checkUtil || currentNode == null || (maxUtilIndex == -1 && !currentNode.hasUtil()) || maxUtilIndex == currentNode.getMaxChild() || maxUtil.equals(currentNode.getMaxUtil());
				assert !UB || currentNode == null || maxUBIndex == currentNode.getMaxUBChild();
			} else {
				for(int i = 0; i < branching;  i++) {
					if(currentPath != null)
						currentPath.setValue(depth, i);
					InnerNode<U, L> child = null;
					if(currentNode != null)
						child = (InnerNode<U, L>)currentNode.getChild(i);

					//					if(child != null || i < domainSize)
					checkTree(nextDepth, child, currentPath, UB, checkLeafs, checkSupport, checkUtil && currentNode != null && i == currentNode.getMaxChild());
					if(child != null) {
						if(child.isAlive() && child.hasUtil() && child.getMaxUtil().isUpToDate() && (maxUtil == null || greaterThan(maxUtil, child.getUtil()))) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}

						if(UB && child.hasUB() && (maxUB == null || greaterThan(maxUB, child.getUB()))) {
							maxUB = child.getUB();
							maxUBIndex = i;
						}
					}
				}

				assert !checkUtil || currentNode == null || (maxUtilIndex == -1 && !currentNode.hasUtil()) || maxUtilIndex == currentNode.getMaxChild() || maxUtil.equals(currentNode.getUtil());
			}
		}
		return true;		
	}

	/**
	 * Method to check the initialization of the tree
	 * @param depth			the current depth
	 * @param currentPath	the current path
	 * @param currentNode	the current node
	 * @param h				the hypercube to compare with
	 * @return true when this tree matches the hypercube
	 */
	@SuppressWarnings("unchecked")
	private boolean compareWithHypercube(int depth, Val[] currentPath, InnerNode<U, L> currentNode, UtilitySolutionSpace<Val, U> h) {
		
		int branching = branchingFactor[depth];
		int nextDepth = depth + 1;
		String variable = depthToVariable[depth];
		ArrayList<Val> domain = domains.get(variable);

		if(depth == depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				currentPath[depth] = domain.get(i);
				L leaf = (L)currentNode.getChild(i);

				assert h == null || leaf.getUtil().equals(h.getUtility(depthToVariable, currentPath));

			}
		} else {
			for(int i = 0; i < branching; i++) {
				currentPath[depth] = domain.get(i);
				InnerNode<U, L> node = (InnerNode<U, L>)currentNode.getChild(i);
				compareWithHypercube(nextDepth, currentPath, node, h);
			}
		}

		return true;

	}

	/**
	 * Method to find the maximal upper bound in the tree. Used for debugging.
	 * @param depth			the current depth
	 * @param currentNode	the current node
	 * @return the maximal upper bound present in the tree
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	private U findMaxUB(int depth, InnerNode<U, L> currentNode) {
		U maxUB = infeasibleUtil;
		int branching = branchingFactor[depth];
		int nextDepth = depth + 1;

		if(depth == depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				L leaf = (L)currentNode.getChild(i);
				if(leaf != null) {
					U UB = leaf.calculateUBTest(upperBoundSums);
					if(greaterThanOrEqual(maxUB, UB))
						maxUB = UB;
				}
			}
		} else {
			for(int i = 0; i < branching; i++) {
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				if(child != null && child.isAlive()) {
					U UB = findMaxUB(nextDepth, child);
					if(greaterThanOrEqual(maxUB, UB))
						maxUB = UB;
				}
			}
		}

		return maxUB;
	}

	/**
	 * Method to check whether a particular leaf node has support, i.e. some child has reported
	 * an assignment compatible with currentPath
	 * @param currentPath	the path that leads to the leaf
	 * @return true if the path has support and false otherwise
	 */
	protected boolean hasSupport(IntArrayWrapper currentPath) {
		boolean hasSupport = false;
		ArrayList<IntArrayWrapper> list = new ArrayList<IntArrayWrapper>();
		for(int i = 0; i < numberOfChildren; i++) {
			list.add(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
			U temp = null;
			if(this.storeReceivedGoods)
				temp = goodsReceived.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
			if(temp != null) {
				hasSupport = true;
				break;
			} 
		}

		return hasSupport && this.getUtilityLocalProblem(currentPath) != infeasibleUtil;
	}

	/**
	 * Used to set the old upperbound
	 * 
	 * @author Brammert Ottens, 30 sep 2009
	 * @return always returns \c true
	 */
	protected boolean setOldUB() {
		if(maximize)
			oldUB = infeasibleUtil.getPlusInfinity();
		else
			oldUB = infeasibleUtil.getMinInfinity();
		if(root.isAlive() && root.getMaxUB() != null) {
			oldUB = root.getMaxUB().calculateUBTest(upperBoundSums);
			if(hasLocalProblem && greaterThan(oldUB, localUpperBound))
				oldUB = localUpperBound;
			assert root.getUB().equals(root.getMaxUB().calculateUBTest(upperBoundSums));
			assert root.getUB().equals(oldUB) || this.localUpperBound.equals(oldUB);
		}
		return true;
	}

	/**
	 * Counts the number of leaves in the tree
	 * @param depth 		the current depth
	 * @param visited		the number of leaves visited
	 * @param currentNode	the currently visited node
	 * @return the number of leaves in the tree
	 */
	@SuppressWarnings("unchecked")
	private int treeSize(int depth, int visited, Node<U> currentNode) {
		if(depth == numberOfVariables) {
			if(((LeafNode<U>)currentNode).getUtil() != this.infeasibleUtil)
				visited++;
			return visited;
		} else {
			depth++;
			for(Node<U> child : ((InnerNode<U, L>)currentNode).children) {
				if(child != null) {
					visited = treeSize(depth, visited, child);
				}
			}
		}

		return visited;
	}

	/**
	 * Used to print thte contents of the tree
	 * @author Brammert Ottens, 25 feb 2010
	 * @param depth			the current depth in the tree
	 * @param currentPath	the path taken through the tree
	 * @param currentNode	the curent node being visited
	 * @return	a string representation of the contents of the tree
	 */
	@SuppressWarnings("unchecked")
	private String generateAssignments(int depth, int[] currentPath, InnerNode<U, L> currentNode) {
		String str = "";
		int branching = branchingFactor[depth];
		if(depth == this.depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				L leaf = (L)currentNode.getChild(i);
				currentPath[depth] = i;
				if(leaf != null) {
					str += "[";
					int j = 0;
					for(;j < depthFinalVariable; j++) {
						if(currentPath[j] < domainSize[j])
							str += depthToVariable[j] + "=" + this.domains.get(depthToVariable[j]).get(currentPath[j]) + ", ";
						else
							str += depthToVariable[j] + "=" + "-1, ";
					}
					if(currentPath[j] < domainSize[j])
						str += depthToVariable[j] + "=" + this.domains.get(depthToVariable[j]).get(currentPath[j]) + "]";
					else
						str += depthToVariable[j] + "=" + "-1]";
					str += ": " + leaf.getUtil() + " " + leaf.getUB() + "\n";
				}
			}
		} else {
			for(int i = 0; i < branching; i++) {
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				if(child != null) {
					currentPath[depth] = i;
					str += generateAssignments(depth + 1, currentPath, child);
				}
			}
		}

		return str;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#setFinalDomainSize(java.lang.String[], int[])
	 */
	@Override
	public void setFinalDomainSize(String[] variables, int[] domainSize) {
		// @todo Auto-generated method stub

	}
}
