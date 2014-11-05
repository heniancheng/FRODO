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

package frodo2.algorithms.odpop.goodsTree.InnerNodeTreeBinaryDomains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import frodo2.algorithms.odpop.Good;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.LeafNode;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
import frodo2.solutionSpaces.Addable;
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
extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree<Val, U, L> {

	/** Used for serialization */
	protected static final long serialVersionUID = 4206985864919963001L;

	/** A dummy constructor to be used by extending classes 
	 * @param leafNodeInstance	an instance of the leaf node class
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
	 * @param leafNodeInstance	an instance of the leaf node class
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
		this.domainElementClass = ownVarDomain[0].getClass();
		this.leafNodeInstance = leafNodeInstance;
		
		if(hasLocalProblem) {
			
			root = createInnerNode(2);

			fullInfo = false;
			solveLocalProblem();
			this.updateLocalProblem();
		} else { // this might look double, but is is not!
			root = createInnerNode(null);
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
			root = createInnerNode(2);

			fullInfo = false;
			if(hasLocalProblem) {
				solveLocalProblem();
				this.updateLocalProblem();
			}
		} else { // this might look double, but is is not!
			root = createInnerNode(null);
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
					frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> newRoot = createInnerNode(branchingFactor[0]); // added
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
						newMaxNumberOfOccurences *= 2;
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
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#createInnerNode(int)
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> createInnerNode(int numberOfChildren) {
		return (frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L>)new InnerNode<U, L>();
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#createInnerNode(frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node[])
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> createInnerNode(Node<U>[] children) {
		return (frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L>)new InnerNode<U, L>(children);
	}

	/**
	 * @author Brammert Ottens, 16 nov 2009
	 * Find the next best local solution that is still allowed
	 */
	protected void updateLocalProblem() {

		if(!localProblemIterator.hasNext()) {
			this.localUpperBound = this.infeasibleUtil;
			this.optimalLocalUtility = this.infeasibleUtil;
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

		assert !localProblemIterator.hasNext() || optimalLocalPath != null;
		if(this.upperBoundIsInfiniteCounter == 0)
			localUpperBound = this.optimalLocalUtility.add(this.upperBoundSums[upperBoundSums.length-1]);
	}

	/**
	 * This method recalculates the upper bound for a particular node. If the upper bound
	 * of this node is already up to date nothing happens. If not, the method
	 * searches further down the tree to find the actual current upper bound
	 * 
	 * @param depth				the current depth
	 * @param currentNode	the node that is currently visited
	 * @param UBtoBeat			the UB that needs to be beat
	 * @param recalculateUtil 	\c true when the recalculation of the utility can make a difference higher up
	 * @param recalculateUB 	\c true when the recalculation of the upperbound can make a difference higher up
	 * @return the new upper 	bound for currentNode
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected U recalculateUB(int depth, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNode, U UBtoBeat, boolean recalculateUtil, boolean recalculateUB) {
		L currentUB = (L)currentNode.getMaxUB();
		if(currentNode.isAlive() && currentUB != null) {
			L currentUtil = (L)currentNode.getMaxUtil();
			boolean utilExists = currentUtil != null;
			boolean reachedLeaves = depth == depthFinalVariable;
			
			// If there is no UB to beat, than set it to min infinity
			if(UBtoBeat == null)
				UBtoBeat = infeasibleUtil;
			
			// recalculate the UB of the current node
			U newUB = currentUB.counter != 0 ? currentUB.calculateUB(upperBoundSums, maximize): currentUB.getUB();
			
			// recalculate the UB if there is a change of changing the higher upperbound and the UB is not up to date
			recalculateUB = recalculateUB && greaterThanOrEqual(UBtoBeat, currentUB.getUB()) && !currentUB.getUB().equals(newUB);
			
			// check whether the current max util is still up to date!
			if(currentUtil != currentUB && utilExists && currentUtil.counter != 0) {
				currentUtil.calculateUB(upperBoundSums, maximize);
			}
			
			// recalculate the max util if the current max util is not up to date and the current node
			// is a real node
			recalculateUtil = recalculateUtil && currentNode.recalculateUtil();
			
			// if the current UB is set to minInfinite and the current Utility
			// is equal to minInfinite, then we want to recalculate the utility
			if(newUB == infeasibleUtil) {
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
				int nextDepth = depth + 1;
				if(recalculateUB)
					currentNode.setUB(-1, false);
				if(recalculateUtil)
					currentNode.setUtil(-1, false);

				if(depth == depthFinalVariable) {
					// we reached the last variable, all children are leaf nodes
					
					L leaf1 = (L)currentNode.getChild(0);
					L leaf2 = (L)currentNode.getChild(1);
					
					if(leaf1 == null) {
						if(leaf2 != null) {
							leaf2.setUB(leaf2.counter != 0 ? leaf2.calculateUB(upperBoundSums, maximize) : leaf2.getUB());
							if(leaf2.getUB() == this.infeasibleUtil) {
								leaf2.setUpToDate(true);
								leaf2.setUtil(this.infeasibleUtil);
							}
							
							if(recalculateUtil && leaf2.isUpToDate())
								currentNode.setUtil(1, true);
							currentNode.setUB(1, true);
							
							assert !recalculateUB || currentNode.check5(upperBoundSums, maximize) || greaterThan(currentNode.getUB(), UBtoBeat);
							assert currentNode.check2(maximize);
							assert currentNode.check3(maximize);
							assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
							assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
							assert currentNode.check4();
							assert this.checkTree(depth, currentNode, null, false, false, false, recalculateUtil);
						}
					} else {
						leaf1.setUB(leaf1.counter != 0 ? leaf1.calculateUB(upperBoundSums, maximize) : leaf1.getUB());
						if(leaf1.getUB() == this.infeasibleUtil) {
							leaf1.setUpToDate(true);
							leaf1.setUtil(this.infeasibleUtil);
						}
					
						if(leaf2 == null) {
							if(recalculateUtil && leaf1.isUpToDate())
								currentNode.setUtil(0, true);
							currentNode.setUB(0, true);
						} else {
							leaf2.setUB(leaf2.counter != 0 && greaterThan(leaf1.getUB(), leaf2.getUB()) ? leaf2.calculateUB(upperBoundSums, maximize) : leaf2.getUB());
							if(leaf2.getUB() == this.infeasibleUtil) {
								leaf2.setUpToDate(true);
								leaf2.setUtil(this.infeasibleUtil);
								recalculateUtil = true; 
							}
						
							if(greaterThan(leaf1.getUB(), leaf2.getUB())){
								currentNode.setUB(1, true);
							} else {
								currentNode.setUB(0, true);
							}

							if(recalculateUtil) {
								if(leaf1.isUpToDate()) {
									if(leaf2.isUpToDate()) {
										if(greaterThan(leaf1.getUtil(), leaf2.getUtil()))
											currentNode.setUtil(1, true);
										else
											currentNode.setUtil(0, true);
									} else
										currentNode.setUtil(0, true);
								} else if(leaf2.isUpToDate())
									currentNode.setUtil(1, true);
							}
						}
						
						assert !recalculateUB || (currentNode.check5(upperBoundSums, maximize) || greaterThan(currentNode.getUB(), UBtoBeat));
						assert currentNode.check2(maximize);
						assert currentNode.check3(maximize) || greaterThan(currentNode.getUB(), UBtoBeat);
						assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
						assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
						assert currentNode.check4();
						assert this.checkTree(depth, currentNode, null, false, false, false, recalculateUtil);
					}
				} else {

					frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> child1 = (frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L>)currentNode.getChild(0);
					frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> child2= (frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L>)currentNode.getChild(1);
					
					if(child1 == null || !child1.hasUB()) {
						if(child2 != null && child2.hasUB()) {
							U newUB2 = child2.getUB();
							L child2Util = (L)child2.getMaxUtil();
							boolean child2UtilExists = child2Util != null;
							
							if(child2UtilExists && recalculateUtil && child2Util.counter != 0)
								child2Util.calculateUB(upperBoundSums, maximize);
							
							if(greaterThanOrEqual(UBtoBeat, newUB2) || (recalculateUtil && child2UtilExists && !child2Util.isUpToDate()))
								newUB2 = recalculateUB(nextDepth, child2, UBtoBeat, true, true);
							child2Util = (L)child2.getMaxUtil();
							
							currentNode.setUB(1, false);
							
							if(recalculateUtil && child2Util != null && child2Util.isUpToDate())
								currentNode.setUtil(1, false);
							
							assert !recalculateUB || currentNode.check5(upperBoundSums, maximize) || greaterThan(currentNode.getUB(), UBtoBeat);
							assert currentNode.check2(maximize);
							assert currentNode.check3(maximize) || greaterThan(currentNode.getUB(), UBtoBeat);
							assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
							assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
							assert currentNode.check4();
							assert this.checkTree(depth, currentNode, null, false, false, false, recalculateUtil);
						}
					} else {
						U newUB1 = child1.getUB();
						L child1Util = (L)child1.getMaxUtil();
						boolean child1UtilExists = child1Util != null;
						
						if(child1UtilExists && recalculateUtil && child1Util.counter != 0)
							child1Util.calculateUB(upperBoundSums, maximize);
						
						if(greaterThanOrEqual(UBtoBeat, newUB1) || (recalculateUtil && child1UtilExists && !child1Util.isUpToDate()))
							newUB1 = recalculateUB(nextDepth, child1, null, true, true);
						
						child1Util = (L)child1.getMaxUtil();
						child1UtilExists = child1Util != null;
						
						if(child2 == null || !child2.hasUB()) {
							currentNode.setUB(0, false);

							if(recalculateUtil && child1Util != null && child1Util.isUpToDate())
								currentNode.setUtil(0, false);
						} else {
							U newUB2 = child2.getUB();
							L child2Util = (L)child2.getMaxUtil();
							boolean child2UtilExists = child2Util != null;
							
							if(child2UtilExists && recalculateUtil && child2Util.counter != 0)
								child2Util.calculateUB(upperBoundSums, maximize);
							
							if((greaterThanOrEqual(UBtoBeat, newUB2) && greaterThan(newUB1, newUB2)) || recalculateUtil && child2UtilExists && !child2Util.isUpToDate())
								newUB2 = recalculateUB(nextDepth, child2, newUB1, true, true);
							
							child2Util = (L)child2.getMaxUtil();
							child2UtilExists = child2Util != null;
							
							if(greaterThan(newUB1, newUB2))
								currentNode.setUB(1, false);
							else
								currentNode.setUB(0, false);
							
							if(recalculateUtil) {
								if(child1Util != null && child1Util.isUpToDate()) {
									if(child2Util != null && child2Util.isUpToDate()) {
										if(greaterThanOrEqual(child1Util.getUtil(), child2Util.getUtil()))
											currentNode.setUtil(1, false);
										else
											currentNode.setUtil(0, false);
									} else {
										currentNode.setUtil(0, false);
									}
								} else if (child2Util != null && child2Util.isUpToDate())
									currentNode.setUtil(1, false);
							}
						}
						
						assert !recalculateUB || (currentNode.check5(upperBoundSums, maximize) || greaterThan(currentNode.getUB(), UBtoBeat));
						assert currentNode.check2(maximize);
						assert currentNode.check3(maximize) || greaterThan(currentNode.getUB(), UBtoBeat);
						assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
						assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
						assert currentNode.check4();
						assert this.checkTree(depth, currentNode, null, false, false, false, recalculateUtil);
					}
				}
			}
			
			return currentNode.getUB();
		}
		assert currentNode.check5(this.upperBoundSums, maximize);
		assert currentNode.check2(maximize);
		assert currentNode.check4();
		assert this.checkTree(depth, currentNode, null, false, false, false, recalculateUtil);
		return null;
	}
}
