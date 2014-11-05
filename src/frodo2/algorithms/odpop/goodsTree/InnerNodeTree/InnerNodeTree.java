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

package frodo2.algorithms.odpop.goodsTree.InnerNodeTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import frodo2.algorithms.odpop.Good;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
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
			 extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree<Val, U, L> {
	

	/** Used for serialization*/
	private static final long serialVersionUID = 5527333118050747769L;

	/** the final domain sizes of as yet unknown variables */
	protected HashMap<String, Integer> finalDomainSizeUnknownVariables;
	
	/** Used to determine whether a variable has a dummy element or not. */
	protected boolean[] dummy;
	
	/** The depth until where one can find dummy variables */
	protected int dummyDepth;
	
	/** Counts the number of dummy variables in the tree */
	protected int numberOfDummies;
	
	/** Stores the number of higher priority neighbours */
	protected int numberOfAncestors;

	/** The size of the array containing precalculated upperbounds */
	protected int upperboundArraySize;
	
	/** For each child a map that stores the utilities received from children. This is needed due to 
	 * the assumption that both domain and variable information can be incomplete */
	protected ArrayList<HashMap<IntArrayWrapper, U>> goodsReceived;
	
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
	protected InnerNodeTree(String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) {
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
		this.domainElementClass = ownVarDomain[0].getClass();
		this.leafNodeInstance = leafNodeInstance;
		
		if(hasLocalProblem) {
			
			root = createInnerNode(branchingFactor[0]);

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
	 * @return	\c true when a new variable has been added, and false otherwise 
	 */
	public boolean add(Good<Val, U> g, int sender) {
		boolean newVariableAdded = false;
//		int nbrNewVariables = 0;
		
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
			
			for(Val[] aValues : reportedValues) {
				boolean newVariable = false;
				boolean newDomainElement = false;
				boolean initializeBounds = false;
				boolean fill = true;
				int fillCounter = 1;
				boolean possibleInconsistencies = false;

				// First determine whether we need to update the information on our separator,
				// i.e. new variables or domain elements are reported
				HashMap<String, Val> newVariables = new HashMap<String, Val>();
				HashMap<String, Val> newValues = new HashMap<String, Val>();
				
				int i = 0;
				for(String var : aVariables) {
					if(!variableToDepth.containsKey(var)) { 
						// the tree does not know this variable
						newVariables.put(var, aValues[i]);
						newVariable = true;
						separatorSizePerChild[sender] += 1;
					} else { 
						// the tree does know the variable
						int varIndex = variableToDepth.get(var);
						if(!childrenVariables[sender][varIndex]) {
							separatorSizePerChild[sender] += 1;
							childrenVariables[sender][varIndex] = true;
						}

						if(!valuePointers.get(var).containsKey(aValues[i])) { 
							// the reported value for this variable is unknown
							newValues.put(var, aValues[i]);
							newDomainElement = true;
							for(int j = 0; j < numberOfChildren; j++) {
								if(j != sender && this.childrenVariables[j][varIndex]) {
									if(relevantChildren[j]) {
										relevantChildren[j] = false;
										fillCounter++;
									}
								}
							}
						}
					}
					i++;
				}

				if(newVariable) {
					newVariableAdded = true;
					int nbrNewVariables = newVariables.size();
					addNewVariable(aVariables, newVariables, sender);
					InnerNode<U, L> newRoot = createInnerNode(1);
					addVariableToTree(nbrNewVariables - 1, nbrNewVariables, (InnerNode<U, L>)root, newRoot, possibleInconsistencies);
					root = newRoot;
				}
				
				if(newDomainElement) {
					fill = newValues.size() > 1 || fillCounter != numberOfChildren;
					addNewDomainElement(newValues);
				}

				// add the received good to the goodsReceivedStorage
				if(this.storeReceivedGoods) {
					IntArrayWrapper key = ((IntArrayWrapper)toKey(aValues, aVariables, sender)).getPartialAssignment(this.childrenVariables[sender], this.separatorSizePerChild[sender]);
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
				if(newDomainElement) {
					// create the partial path defined by the good
					int[] newDomainPath = new int[numberOfVariables];
					Arrays.fill(newDomainPath, -1);

					int horizon = 0;
					for(Entry<String, Val> e : newValues.entrySet()) {
						String var = e.getKey();
						int varDepth = variableToDepth.get(var);
						newDomainPath[varDepth] = valuePointers.get(var).get(e.getValue());
						if(horizon < varDepth)
							horizon = varDepth;
					}

					if(!initializeBounds && upperBoundIsInfiniteCounter == 0) {
						addNewDomainElementWithUB(0, horizon, createIntArrayWrapper(numberOfVariables), partialPath, newDomainPath, root, g, zero, sender, true, fill);
						assert !root.hasUB() || oldUB == null || greaterThanOrEqual(root.getUB(), oldUB);
						assert this.checkTree(0, root, createIntArrayWrapper(numberOfVariables), this.upperBoundIsInfiniteCounter == 0, true, true, true);
					} else {
						this.addNewDomainElementNoUB(0, horizon, createIntArrayWrapper(numberOfVariables), partialPath, newDomainPath, root, g, zero, sender, true, fill);
					}
					assert !root.hasUB() || root.getUB().equals(root.getMaxUB().calculateUBTest(upperBoundSums));
				} else {
					updatePath(0, new IntArrayWrapper(numberOfVariables), partialPath, root, g, zero, sender, true, !initializeBounds && upperBoundIsInfiniteCounter == 0);
					assert !root.hasUB() || oldUB == null || greaterThanOrEqual(root.getUB(), oldUB);
					assert this.checkTree(0, root, new IntArrayWrapper(numberOfVariables), this.upperBoundIsInfiniteCounter == 0 && !initializeBounds, true, true, true);
				}

				if(initializeBounds) {
					for(i = 0; i < numberOfVariables; i++) {
						if(finalDomainSize[i] != domainSize[i]) {
							branchingFactor[i] += 1;
							dummy[i] = true;
							this.numberOfDummies++;
						}
					}
					// create the precalculated UBs
					initializeUpperBoundSums();
					relevantChildren = new boolean[numberOfChildren];
					initiateBounds((InnerNode<U, L>)root, new IntArrayWrapper(numberOfVariables), 0, false, g, sender);
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
				this.storeReceivedGoods = !this.fullInfo || this.numberOfDummies != 0;
		}
		return newVariableAdded;
	}
	
	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#setFinalDomainSize(java.lang.String[], int[])
	 */
	public void setFinalDomainSize(String[] variables, int[] domainSize) {
		int removalDepth = 0;
		boolean[] change = new boolean[numberOfVariables];
		for(int i = 0; i < domainSize.length; i++) {
			String var = variables[i];
			Integer pointer = this.variableToDepth.get(var);
			int size = domainSize[i];
			int depth = setFinalDomainSize(change, variables[i], size, pointer);
			if(removalDepth < depth) 
				removalDepth = depth;
		}
		fullInfoCounter--;
		finalDomainSizeReceiver();
		if(removalDepth != this.depthFinalVariable)
			removeDummies(removalDepth, change, 0, root);
	}

	/**
	 * Method used for debugging purposes. It prints the state of the tree
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String s = "";
		s += "----------------------\n";
		s += "Variable " + depthToVariable[numberOfVariables - 1] + "\n";
		s += "----------------------\n";
		s += this.fullInfo + "\n";
		s += this.fullInfoCounter + "\n";
		s += this.upperBoundIsMinInfiniteCounter + " - " + this.numberOfChildren + "\n";
		s += "Alive: " + root.isAlive() + "\n";
		if(root.hasUtil()) {
			s += "max util is " + root.getUtil() +  " " + root.getMaxUtil().counter + "\n";
			s += "confirmed: " + (root.hasUB() && root.getMaxUtil().counter == 0 && ((hasLocalProblem && greaterThanOrEqual(maximize ? root.getUB().max(localUpperBound) : root.getUB().min(localUpperBound), root.getUtil())) || (!hasLocalProblem && greaterThanOrEqual(root.getUB(), root.getUtil())))) + "\n";
		}
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
		
		s += "tree\n-------------------------\n\n";
		s += "Branching: [";
		for(int i = 0; i < this.numberOfVariables; i++) {
			s += this.branchingFactor[i] + ", ";
		}
		s += "]\n";
		s += "\n---------------------\n";

		return s;
		
		
	}
	
	/**
	 * This method adds a new variable to the data structure. Note that it does not need to
	 * update the tree, this automatically happens when adding the compatible paths to the
	 * tree
	 * @param newValues	The new domain elements that must be added
	 */
	protected void addNewDomainElement(HashMap<String, Val> newValues) {
		
		boolean change = false;
		
		for(Entry<String, Val> newEntry : newValues.entrySet()) {
			String newVar = newEntry.getKey();
			Val newVal = newEntry.getValue();
			
			int depth = variableToDepth.get(newVar);
			
			domainSize[depth]++;
			if(domainSize[depth] == finalDomainSize[depth]) {
				branchingFactor[depth] = finalDomainSize[depth];
				assert upperBoundIsInfiniteCounter != 0 || dummy[depth];
				if(dummy[depth])
					this.numberOfDummies--;
				dummy[depth] = false;
				change = true;
			} else {
				branchingFactor[depth]++;
			}
			
			assert domainSize[depth] <= branchingFactor[depth];
			
			HashMap<Val, Integer> pointer = valuePointers.get(newVar);
			
			if(pointer.containsKey(null)) {// move the dummy value to the end
				pointer.put(null, pointer.get(null) + 1);
				pointer.put(newVal, domainSize[depth] - 1);
			}
			
			domains.get(newVar).add(newVal);
		}
		
		if(change && upperBoundIsInfiniteCounter == 0) {
			changeDummyToReal(0, (InnerNode<U, L>)root, false);
		}
		
		int newMaxNumberOfOccurences = 1;
		int i = 0;
		while(!this.ownVariables[i]) {
			newMaxNumberOfOccurences *= branchingFactor[i];
			i++;
		}
		int diff = newMaxNumberOfOccurences - this.maxNumberLocalProblemOccurences;
		this.maxNumberLocalProblemOccurences = newMaxNumberOfOccurences;
		this.localCounter += diff;
		assert localCounter >= 0;
	}

	/**
	 * This method is called when a new domain element is discovered before the UBs can be calculated. It searches the tree up to
	 * the horizon depth for new branches to add. A new branch is only added when it represents a new domain element found in this
	 * round
	 * 
	 * @param depth			the current depth
	 * @param horizon		the maximal depth at which a dummy node can be found
	 * @param currentPath	the current path taken
	 * @param partialPath	the partial path defined by the received good
	 * @param newDomainPath the partial path defined by the new domain values
	 * @param currentNodeUncast	the node currently visited
	 * @param g				the received good
	 * @param utilityDelta  the difference with the previously reported utility value for this particular good
	 * @param sender		the sender of the good
	 * @param onPartialPath	\c true when the partial path is still being followed, and \c false otherwise
	 * @param fill			\c true when the tree should be extended by copying an already existing branch
	 */
	@SuppressWarnings("unchecked")
	protected void addNewDomainElementNoUB(int depth, int horizon, IntArrayWrapper currentPath, int[] partialPath, int[] newDomainPath, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNodeUncast, Good<Val, U> g, U utilityDelta, int sender, boolean onPartialPath, boolean fill) {
		InnerNode<U, L> currentNode = (InnerNode<U, L>)currentNodeUncast;
		int branching = branchingFactor[depth];
		int domainSize = this.domainSize[depth];
		int nextDepth = depth + 1;
		U maxUtil = null;
		int maxUtilIndex = -1;
		boolean real = currentNode.real;
		fill = fill || !onPartialPath;
		
		int difference = branching - currentNode.children.length;
		if(difference != 0 ) { // some children need to be added, so update the internal array
			currentNode.enlargeChildrenArray(difference, false);
		}

		InnerNode<U, L> example = (InnerNode<U, L>)currentNode.getExample();
		
		if(depth < horizon) {
			int childIndex = newDomainPath[depth];
			if(onPartialPath)
				childIndex = partialPath[depth];
			for(int i = 0; i < branching; i++) {
				// set the current path and check if the child is real or dummy
				boolean isReal = i < domainSize;
				currentPath.setValue(depth, i, isReal);
				
				// get the child and check if it exists
				InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
				boolean childExists = child != null;
				
				if(childIndex == i || childIndex == -1) {
					if(!childExists) {
						boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && isReal;
						if(/* cont || */example != null) {
							// a new child should be created
							child = fillTree(example, nextDepth, currentPath, real && isReal, true, false, cont, partialPath, g, sender);
							currentNode.setChild(child, i);
							childExists = child != null;
						} else if(cont) {
							child = (InnerNode<U, L>)this.createPathNoUB(nextDepth, currentPath, partialPath, isReal, g, sender);
							childExists = true;
							currentNode.setChild(child, i);
						}
					} else if(newDomainPath[depth] == i) {
						// there is a dummy on the position of the new child
						this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, currentNode.real, true, false, onPartialPath, fill);
					} else {
						addNewDomainElementNoUB(nextDepth, horizon, currentPath, partialPath, newDomainPath, child, g, utilityDelta, sender, onPartialPath, fill);
					}
				} else if(childExists) {
					addNewDomainElementNoUB(nextDepth, horizon, currentPath, partialPath, newDomainPath, child, g, utilityDelta, sender, false, fill);
				}

				if(childExists) {
					if(child.utilCandidate(maxUtil, maximize)) {
						// child is max util candidate
						maxUtil = child.getUtil();
						maxUtilIndex = i;
					}
				}
			}
		} else {
			if(onPartialPath) {
				int childIndex = partialPath[depth];
				for(int i = 0; i < branching; i++) {
					// set the current path
					boolean isReal = i < domainSize;
					currentPath.setValue(depth, i, isReal);
					
					// get the child and check if it exists
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
					boolean childExists = child != null;
					
					if(childIndex == i) {
						if(!childExists) {
							boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && isReal;
							if(fill && (/* cont || */example != null)) {
								// A new child should be created
								child = fillTree(example, nextDepth, currentPath, real && isReal, true, false, onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i), partialPath, g, sender);
								currentNode.setChild(child, i);
								childExists = child != null;
							} else if(cont) {
								child = (InnerNode<U, L>)this.createPathNoUB(nextDepth, currentPath, partialPath, isReal, g, sender);
								childExists = child != null;
								currentNode.setChild(child, i);
							}
						} else {
							// a dummy might be converted to a new node
							assert !child.real;
							this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, currentNode.real, true, false, true, fill);
						}
					} 

					if(childExists) { 
						if(child.utilCandidate(maxUtil, maximize)) {
							// child is a candidate for max util
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}
					}
				}
			} else {
				int childIndex = newDomainPath[depth];
				for(int i = 0; i < branching; i++) {
					// set the current path
					boolean isReal = i < domainSize;
					currentPath.setValue(depth, i, isReal);
					
					// get the chid and check if it exists
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
					boolean childExists = child != null;
					
					if(childIndex == i && !childExists) {
						boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && isReal;
						if(fill && (cont || example != null)) {
							// a new child should be created
							child = fillTree(example, nextDepth, currentPath, real && isReal, true, false, onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i), partialPath, g, sender);
							currentNode.setChild(child, i);
							childExists = child != null;
						} else if(cont) {
							child = (InnerNode<U, L>)this.createPathNoUB(nextDepth, currentPath, partialPath, isReal, g, sender);
							childExists = true;
							currentNode.setChild(child, i);
						}
					}

					if(childExists) {
						if(child.utilCandidate(maxUtil, maximize)) {
							// the child is a candidate for max util
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}
					}
				}
			}
		}
		
		assert currentNode.getMaxChild() < domainSize; // check we haven't selected a dummy
		currentNode.setUtil(maxUtilIndex, false);
//		assert currentNode.getUtil() == null || this.Utilexists(currentNode, depth);
		assert this.checkTree(depth, currentNode, currentPath, false, true, true, true);
	}
	
	/**
	 * This method is called when a new domain element is discovered while the UB is already set. It searches the tree up to
	 * the horizon depth for new branches to add. A new branch is only added when it represents a new domain element found. If
	 * the upper bound must be recalculated this is done.
	 * 
	 * @param depth			The current depth
	 * @param horizon		The depth until which the search for non-existing children must continue
	 * @param currentPath	The current path
	 * @param partialPath	The path dictated by the received good
	 * @param newDomainPath the partial path defined by the new domain values
	 * @param currentNodeUncast	the node currently visited
	 * @param g				the received good
	 * @param utilityDelta  the difference between the already received utility and the new utility defined by the received good. Is only used in ASODPOP
	 * @param sender		the sender of the good
	 * @param onPartialPath	\c true when the partial path is still being followed, and \c false otherwise
	 * @param fill			\c true when the tree should be extend by copying an already existing branch
	 */
	@SuppressWarnings("unchecked")
	protected void addNewDomainElementWithUB(int depth, int horizon, IntArrayWrapper currentPath, int[] partialPath, int[] newDomainPath, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNodeUncast, Good<Val, U> g, U utilityDelta, int sender, boolean onPartialPath, boolean fill) {
		InnerNode<U, L> currentNode = (InnerNode<U, L>)currentNodeUncast;
		if(currentNode.isAlive()) {
			int branching = branchingFactor[depth];
			int domainSize = this.domainSize[depth];
			int domainIndex = newDomainPath[depth];
			int nextDepth = depth + 1;
			U maxUtil = null;
			int maxUtilIndex = -1;
			int maxUBIndex = -1;
			U maxUB = null;
			boolean real = currentNode.real;
			fill = fill || !onPartialPath;
			
			int difference = branching - currentNode.children.length;
			if(difference != 0 ) { // some children need to be added
				currentNode.enlargeChildrenArray(difference, dummy[depth]);
			}
			
			InnerNode<U, L> example = (InnerNode<U, L>)currentNode.getExample();
			
			if(depth < horizon) {
				// we have not yet reached the depth of the last variable who's domain has been enlarged
				if(onPartialPath) {
					// we are still on the path determined by the received assignment
					int childIndex = partialPath[depth];
					for(int i = 0; i < branching; i++) {
						// set the current path
						boolean isReal = i < domainSize;
						currentPath.setValue(depth, i, isReal);
						
						// get the child and check whether it exists
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						boolean childExists = child != null;
						
						if(childIndex == i || childIndex == -1) {
							if(!childExists) {
								boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && isReal;
								if(/* cont || */example != null) {
									// the child does not yet exist and must be created
									child = fillTree(example, nextDepth, currentPath, real && isReal, true, true, onPartialPath && partialPath[depth] == i, partialPath, g, sender);
									currentNode.setChild(child, i);
									childExists = child != null;
								} else if(cont) {
									child = this.createPathWithUB(nextDepth, currentPath, partialPath, isReal, g, sender);
									childExists = true;
									currentNode.setChild(child, i);
								}
							} else if (domainIndex == i) {
								// if child is a dummy, then it should be converted to a real part of the tree
								this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, currentNode.real, true, true, true, fill);
							} else {
								// simply continue on the partial path
								addNewDomainElementWithUB(nextDepth, horizon, currentPath, partialPath, newDomainPath, child, g, utilityDelta, sender, true, fill);
							}
						} else if(childExists) {
							// we are leaving the partial path
							addNewDomainElementWithUB(nextDepth, horizon, currentPath, partialPath, newDomainPath, child, g, utilityDelta, sender, false, fill);
						}

						if(childExists) {
							if(child.utilCandidate(maxUtil, maximize)) {
								// child is max util candidate
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}
							
							if(child.ubCandidate(maxUB, maximize)) {
								// child is max UB candidate
								maxUB = child.getUB();
								maxUBIndex = i;
							}
						}
					}
				} else {
					// we have left the partial path
					for(int i = 0; i < branching; i++) {
						// set the current path
						currentPath.setValue(depth, i, i < domainSize);
						
						// get the child and check whether it exists
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						boolean childExists = child != null;
						
						if(domainIndex == i) {
							// the current variables domain has been enlarged
							if(!childExists) {
								boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && i < domainSize;
								if(fill && (/* cont || */example != null)) {
									// the new child does not yet exist
									child = fillTree(example, nextDepth, currentPath, real, true, true, onPartialPath && partialPath[depth] == i, partialPath, g, sender);
									currentNode.setChild(child, i);
									childExists = child != null;
								} else if(cont) {
									child = this.createPathWithUB(nextDepth, currentPath, partialPath, real, g, sender);
									childExists = true;
									currentNode.setChild(child, i);
								}
							} else {
								// the child already exists but is a dummy. Convert it to a real part of the tree
								this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, real, true, true, false, fill);
							}
						} else if(childExists) {
							// there are still variables who's domain shoulc be enlarged, so continue down the tree
							addNewDomainElementWithUB(nextDepth, horizon, currentPath, partialPath, newDomainPath, child, g, utilityDelta, sender, false, fill);
						}

						if(childExists) {
							if(child.utilCandidate(maxUtil, maximize)) {
								// child is max util candidate
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}
							
							if(child.ubCandidate(maxUB, maximize)) {
								// child is max UB candidate
								maxUB = child.getUB();
								maxUBIndex = i;
							}
						}
					}
				}
			} else { 
				// we are at the last variable who's domain is to be enlarged, i.e. newDomain[depth] != partialPath[depth]
				if(onPartialPath) {
					// we are still on the path determined by the received assignment
					int childIndex = partialPath[depth];
					for(int i = 0; i < branching; i++) {
						// setting the current path
						currentPath.setValue(depth, i, i < domainSize);
						
						// getting the child and checking that it still exists
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						boolean childExists = child != null;
						
						if(childIndex == i) {
							if(!childExists) {
								boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && i < domainSize;
								if(fill && (/* cont || */example != null)) {
									// a new child should be created
									child = fillTree(example, nextDepth, currentPath, real, true, true, onPartialPath && partialPath[depth] == i, partialPath, g, sender);
									currentNode.setChild(child, i);
									childExists = child != null;
								} else if(cont) {
									child = this.createPathWithUB(nextDepth, currentPath, partialPath, real, g, sender);
									childExists = child != null;
									currentNode.setChild(child, i);
								}
							} else  {
								// there is a dummy node on the position of the new domain element
								this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, currentNode.real, true, true, true, fill);
							} 
						} else if(childExists) {
							// since new information is to be added, the upperbounds should be updated
							// shouldn't we only do this when the utility of the received assignment
							// is confirmed? Only in the case of ASODPOP
							/// @todo copy this function into the ASODPOp goodstree and change it!
							if(maxUB == null)
								this.recalculateUB(nextDepth, child, infeasibleUtil, true, true);
							else
								this.recalculateUB(nextDepth, child, maxUB, true, true);
						}

						if(childExists) {
							if(child.utilCandidate(maxUtil, maximize)) {
								/// child is max util candidate
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}

							if(child.ubCandidate(maxUB, maximize)) {
								// child is UB candidate
								maxUB = child.getUB();
								maxUBIndex = i;
							}
						}
					}
				} else {
					// we are off the partial path
					for(int i = 0; i < branching; i++) {
						// set the current path
						currentPath.setValue(depth, i);
						
						// get the child and check if it exists
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						boolean childExists = child != null;
						
						if(!childExists) {
							if(domainIndex == i) {
								boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && i < domainSize;
								if(/* cont || */example != null) {
									// a new child should be created
									child = fillTree(example, nextDepth, currentPath, real, true, true, false, partialPath, g, sender);
									currentNode.setChild(child, i);
									childExists = child != null;
								} else if(cont) {
									child = this.createPathWithUB(nextDepth, currentPath, partialPath, real, g, sender);
									childExists = true;
									currentNode.setChild(child, i);
								}
							}
						} else {
							if(domainIndex == i) {
								this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, currentNode.real, true, true, false, fill);
							} else {
								/// @todo: again, in the case of ASODPOP this should only be done when the good is confirmed
								if(maxUB == null) 
									this.recalculateUB(nextDepth, child, infeasibleUtil, true, true);
								else
									this.recalculateUB(nextDepth, child, maxUB, true, true);
							}
						}

						if(childExists) {
							if(child.utilCandidate(maxUtil, maximize)) {
								// child is max util candidate
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}

							if(child.ubCandidate(maxUB, maximize)) {
								// child is max UB candidate
								maxUB = child.getUB();
								maxUBIndex = i;
							}
						}
					}
				}
			}

			currentNode.setUtil(maxUtilIndex, false);
			currentNode.setUB(maxUBIndex, false);
			assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size(); // check that the utility has been chosen from a real node
			assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
			assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
			assert this.checkTree(depth, currentNode, currentPath, true, false, true, true);
		}
	}

	/**
	 * This method adds new variables to the data structure
	 * @param allVariables 	The current variables
	 * @param newVariables	The variables to be added
	 * @param sender 		The child that reported the variables
	 * @return the number of new variables
	 */
	protected int[] addNewVariable(String[] allVariables, HashMap<String, Val> newVariables, int sender) {
		int numberOfNewVariables = newVariables.size();
		
		// First, update the HashMaps, Arrays and ArrayLists
		numberOfVariables += numberOfNewVariables;
		depthFinalVariable += numberOfNewVariables;
		depthOfFirstToBeRemovedVariables += numberOfNewVariables;
		dummyDepth += numberOfNewVariables;
		String[] newDepthToVariable = new String[numberOfVariables];
		String[] newOutsideVariables = new String[outsideVariables.length + numberOfNewVariables];
		System.arraycopy(outsideVariables, 0, newOutsideVariables, numberOfNewVariables, outsideVariables.length);
		int[] newDomainSize = new int[numberOfVariables];
		int[] newFinalDomainSize = new int[numberOfVariables];
		int[] newBranchingFactor = new int[numberOfVariables];
		boolean[] newDummy = new boolean[numberOfVariables];
		boolean[] newOwnVariables = new boolean[numberOfVariables];
		boolean[][] newChildrenVariables = new boolean[numberOfChildren][numberOfVariables];
		
		int i = 0;
		for(String var : depthToVariable) {
			int newIndex = numberOfNewVariables + i;
			variableToDepth.put(var, variableToDepth.get(var) + numberOfNewVariables);
			newDepthToVariable[newIndex] = var;
			newDomainSize[newIndex] = domainSize[i];
			newFinalDomainSize[newIndex] = finalDomainSize[i];
			newBranchingFactor[newIndex] = branchingFactor[i];
			newDummy[newIndex] = dummy[i];
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
			if(finalDomainSizeUnknownVariables.containsKey(newVar))
				newFinalDomainSize[i] = finalDomainSizeUnknownVariables.remove(newVar);
			Val newVal = newEntry.getValue();
			variableToDepth.put(newVar, i);
			newDepthToVariable[i] = newVar;
			newOutsideVariables[i] = newVar;
			newDomainSize[i] = 1;
			newBranchingFactor[i] = 1;
			newDummy[i] = false;
			HashMap<Val, Integer> pointer = new HashMap<Val, Integer>();
			ArrayList<Val> newDomain = new ArrayList<Val>();
			if(newVal != null) {
				pointer.put(newVal, 0);
				pointer.put(null, 1); // add the dummy element
				newDomain.add(newVal);
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
		dummy = newDummy;
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
	 * @param depth				The current depth
	 * @param oldRoot 			The old root
	 * @param currentNode 		The current node
	 * @param possibleInconsistencies \c true when the reception of a new variable can make certain solutions infeasible
	 */
	private void addVariableToTree(int nbrNewVariables, int depth, InnerNode<U, L> oldRoot, InnerNode<U, L> currentNode, boolean possibleInconsistencies) {
		InnerNode<U, L> nodeL;
		assert nbrNewVariables >= 0;
		if(nbrNewVariables == 0) { // no more variables to add, put two copies under this node
			nodeL = oldRoot;
			if(possibleInconsistencies) {
				IntArrayWrapper path = new IntArrayWrapper(this.numberOfVariables);
				for(int i = 0; i < depth; i++)
					path.setValue(i, 0);
				removeInconsistencies(nodeL, depth, path);
			}
		} else {
				nodeL = createInnerNode(1);
			nbrNewVariables--;
			
			addVariableToTree(nbrNewVariables, depth, oldRoot, nodeL, possibleInconsistencies);
		}

		currentNode.setChild(nodeL, 0);

		if(nodeL.getMaxChild() == -1)
			currentNode.setUtil(-1, false);
		else
			currentNode.setUtil(0, false);
		if(nodeL.getMaxUBChild() == -1)
			currentNode.setUB(-1, false);
		else
			currentNode.setUB(0, false);
		
	}
	
	/**
	 * Changes all dummy children of the variables in dummyToReal to real nodes
	 * @param depth			the current depth
	 * @param currentNode	the node currently being visited
	 * @param change		\c true when we still want to change things
	 */
	@SuppressWarnings("unchecked")
	protected void changeDummyToReal(int depth, InnerNode<U, L> currentNode, boolean change) {
		if((depth != dummyDepth || change) && currentNode.real && currentNode.isAlive()) {
			int branching;
	
			if(depth == numberOfVariables - 1) {
				if(change) {
					branching = currentNode.children.length;
					U maxUtil = null;
					int maxUtilIndex = -1;
					
					for(int i = 0; i < branching; i++) {
						L child = (L)currentNode.getChild(i);
						if(child != null && child.isUpToDate()) {
							child.real = true;
							if(child.utilCandidate(maxUtil, maximize)) {
								maxUtil = child.getUtil();
								maxUtilIndex = i;
							}
						}
					}
					
					currentNode.setUtil(maxUtilIndex, true);
					assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
				}
			} else {
				if(domainSize[depth] == finalDomainSize[depth]) {
					branching = currentNode.children.length;
					change = true;
				} else {
					branching = currentNode.children.length - 1;
				}
				
				U maxUtil = null;
				int maxUtilIndex = -1;
				for(int i = 0; i < branching; i++) {
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
					if(child != null && child.isAlive()) {
						if(child.real) {
							changeDummyToReal(depth + 1, child, change);
						} else if(change) {
							child.real = true;
							changeDummyToReal(depth + 1, child, true);
						}
						
						if(child.utilCandidate(maxUtil, maximize)) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}
					}
				}
				
				currentNode.setUtil(maxUtilIndex, false);
			}
			assert currentNode.check2(maximize);
		}
	}
	
	/**
	 * Changes all dummy children of the variables in dummyToReal to real nodes
	 * @param depth			the current depth
	 * @param currentPath	the path taken through the tree to get here
	 * @param currentNode	the node currently being visited
	 * @param change		\c true when we still want to change things
	 */
	@SuppressWarnings("unchecked")
	protected void changeDummyToReal(int depth, IntArrayWrapper currentPath, InnerNode<U, L> currentNode, boolean change) {
		if(currentNode.isAlive()) {
			int branching;
			
			if(depth == depthFinalVariable) {
				branching = currentNode.children.length;
				U maxUtil = null;
				int maxUtilIndex = -1;

				for(int i = 0; i < branching; i++) {
					L child = (L)currentNode.getChild(i);
					currentPath.setValue(depth, i);
					if(child != null) {
						if(this.getUtilityLocalProblem(currentPath).equals(infeasibleUtil))
							child.setInfeasable(infeasibleUtil);
						if(change)
							child.real = true;
						if(child.utilCandidate(maxUtil, maximize)) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}
					}
				}

				currentNode.setUtil(maxUtilIndex, true);
				assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
			} else {
				boolean dummy = false;
				
				if(domainSize[depth] == finalDomainSize[depth]) {
					branching = currentNode.children.length;
					change = currentNode.real && (change || (depth < dummyDepth));
				} else {
					branching = currentNode.children.length - 1;
					dummy = true;
				}
				
				U maxUtil = null;
				int maxUtilIndex = -1;
				for(int i = 0; i < branching; i++) {
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
					if(child != null && child.isAlive()) {
						currentPath.setValue(depth, i);
						if(change && !child.real) {
							assert currentNode.real;
							child.real = true;
						}
						
						changeDummyToReal(depth + 1, currentPath, child, change);
						
						if(child.real && child.utilCandidate(maxUtil, maximize)) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}
					}
				}
				
				if(dummy) {
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(branching);
					if(child != null && child.isAlive()) {
						assert !child.real;
						currentPath.setValue(depth, -1);

						changeDummyToReal(depth + 1, currentPath, child, false);						
					}
				}
				
				currentNode.setUtil(maxUtilIndex, false);
			}
			assert currentNode.check2(maximize);
		}
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#createInnerNode(int)
	 */
	protected InnerNode<U, L> createInnerNode(int numberOfChildren) {
		return new InnerNode<U, L>(numberOfChildren);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#createInnerNode(frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node[])
	 */
	protected InnerNode<U, L> createInnerNode(Node<U>[] children) {
		return new InnerNode<U, L>(children);
	}

	/**
	 * Method to create a new leaf node
	 * @param currentPath		The path to the leaf
	 * @param real				\c true when the leaf points to a real assignment
	 * @param g					the received good
	 * @param child 			the child that reported it
	 * @param withUB			\c true when the upper bound must be set
	 * @return the new leaf node
	 */
	protected L createLeaf(IntArrayWrapper currentPath, boolean real, Good<Val, U> g, int child, final boolean withUB) {
		
		L leaf = leafNodeInstance.newInstance(numberOfChildren, powersOf2);
		boolean support = false;
		
		U localUtil = getUtilityLocalProblem(currentPath);
		if(localUtil == infeasibleUtil)
			return null;
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
		
		leaf.real = real;
		if(withUB && leaf.getUB() == infeasibleUtil) {
			leaf.setUtil(infeasibleUtil);
		} else {
			leaf.setUtil(confirmedUtil);
		}

		assert  !this.storeReceivedGoods ||  this.checkLeaf(leaf, currentPath, false, g.getUtility(), child);
		assert !this.storeReceivedGoods ||  hasSupport(currentPath);
		if(this.COLLECT_STATISTICS)
			this.countLeafNode(real);
		return leaf;
	}
	
	/**
	 * Method to create a new leaf node
	 * @param currentPath		The path to the leaf
	 * @param real				\c true when the leaf points to a real assignment
	 * @param withUB			\c true when the upper bound must be set
	 * @return the new leaf node
	 */
	protected L createLeaf(IntArrayWrapper currentPath, boolean real, final boolean withUB) {
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
		
		leaf.real = real;
		if(withUB && leaf.getUB() == infeasibleUtil) {
			leaf.setUtil(infeasibleUtil);
		} else {
			leaf.setUtil(confirmedUtil);
		}

		assert  !this.storeReceivedGoods ||  this.checkLeaf(leaf, currentPath, false, null, -1);
		assert !this.storeReceivedGoods || hasSupport(currentPath);
		if(this.COLLECT_STATISTICS)
			this.countLeafNode(real);
		return leaf;
	}
	
	/**
	 * Given a partial path, this method creates the path in the tree. This method
	 * should only be called after domain information is complete!
	 * @param depth			the current depth
	 * @param currentPathUncast	the path taken
	 * @param partialPath	the partial path defined by the received good
	 * @param real \c true when still in the real part of the tree
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return a new InnerNode<U>
	 */
	@Override
	protected frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> createPathNoUB(int depth, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree.IntArrayWrapper currentPathUncast, int[] partialPath, boolean real, Good<Val, U> g, int sender) {
		IntArrayWrapper currentPath = (IntArrayWrapper)currentPathUncast;
		int nextDepth = depth + 1;
		int childIndex = partialPath[depth];
		int branching = branchingFactor[depth];
		int domainSize = this.domainSize[depth];
		InnerNode<U, L> node = createInnerNode(branchingFactor[depth]);
		U maxUtil = null;
		int maxUtilIndex = -1;
		boolean set = false;
		boolean reachedFinalLayer = depth == depthFinalVariable;
		
		if(childIndex == -1) {
			for(int i = 0; i < branching; i++) {
				Node<U> child = null;
				if(reachedFinalLayer) {
					currentPath.setValue(depth, i);
					child = createLeaf(currentPath, true, g, sender, false);
				} else {
					currentPath.setValue(depth, i, i < domainSize);
					child = createPathNoUB(nextDepth, currentPath, partialPath, real, g, sender);
				}
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
				child = createLeaf(currentPath, true, g, sender, false);
			else
				child = createPathNoUB(nextDepth, currentPath, partialPath, real, g, sender);
			
			if(child != null) {
				set = true;
				node.setChild(child, childIndex);
				
				if(real) {
					maxUtil = child.getUtil();
					maxUtilIndex = childIndex;
				}
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
	 * @param currentPathUncast	the path taking in the tree
	 * @param partialPath	the partial path defined by the received good
	 * @param real 			\c true if the parent is a real node
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return a new InnerNode<U>
	 */
	private InnerNode<U, L> createPathWithUB(int depth, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree.IntArrayWrapper currentPathUncast, int[] partialPath, boolean real, Good<Val, U> g, int sender) {
		IntArrayWrapper currentPath = (IntArrayWrapper)currentPathUncast;
		int nextDepth = depth + 1;
		int childIndex = partialPath[depth];
		int branching = branchingFactor[depth];
		int domainSize = this.domainSize[depth];
		InnerNode<U, L> node = createInnerNode(branchingFactor[depth]);
		node.real = real;
		U maxUtil = null;
		U maxUB = null;
		int maxUtilIndex = -1;
		int maxUBIndex = -1;
		boolean set = false;
		
		if(depth == depthFinalVariable) {
			currentPath.setValue(depth, childIndex);
			L leaf = createLeaf(currentPath, real, g, sender, true);
			if(leaf != null) {
				set = true;
				node.setChild(leaf, childIndex);
				assert  !this.storeReceivedGoods ||  checkLeaf(leaf, currentPath, true, g.getUtility(), sender);

				if(real && leaf.isUpToDate()) {
					maxUtil = leaf.getUtil();
					maxUtilIndex = childIndex;
				}
				node.setUB(childIndex, true);
				node.setUtil(maxUtilIndex, true);
			}
		} else {
			if(childIndex == -1) {
				boolean createDummy = dummy[depth];
				
				if(createDummy)
					branching -= 1;
				
				int i = 0;
				for(; i < branching; i++) {
					currentPath.setValue(depth, i, i < domainSize);
					InnerNode<U, L> child = createPathWithUB(nextDepth, currentPath, partialPath, real, g, sender);
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
				
				if(createDummy) {
					currentPath.setValue(depth, -1);
					InnerNode<U, L> child = createPathWithUB(nextDepth, currentPath, partialPath, false, g, sender);
					if(child != null) {
						set = true;
						node.setChild(child, i);

						if(child.ubCandidate(maxUB, maximize)) {
							maxUB = child.getUB();
							maxUBIndex = i;
						}
					}
				}
				node.setUB(maxUBIndex, false);
			} else {
				currentPath.setValue(depth, childIndex);
				InnerNode<U, L> child = createPathWithUB(nextDepth, currentPath, partialPath, real, g, sender);
				if(child != null) {
					set = true;
					node.setChild(child, childIndex);

					if(real)
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
	 * @param depth 		the current depth
	 * @param currentPath	the current path taken through the tree. Is only up to date up to depth
	 * @param real			\c true when the current node should be a real node, and \c false when it should be a dummy
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param withUB		\c true when the UB must be instatiated as well
	 * @param example		the example that is to be copied
	 * @param onPartialPath \c true then the followed path is the same as the path defined by the received good
	 * @param partialPath	the path defined by the received good
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return	a new node
	 */
	@SuppressWarnings("unchecked")
	private InnerNode<U, L> fillTree(InnerNode<U, L> example, int depth, IntArrayWrapper currentPath, boolean real, boolean onLocalPath, final boolean withUB, boolean onPartialPath, int[] partialPath, Good<Val, U> g, int sender) {
		int nextDepth = depth + 1;
		int branching = branchingFactor[depth];
		int localIndex = -2;
		if(optimalLocalPath != null)
			localIndex = optimalLocalPath[depth];
		InnerNode<U, L> node = createInnerNode(branching);
		node.real = real;
		boolean set = false;
		U maxUtil = null;
		U maxUB = null;
		int maxUtilIndex = -1;
		int maxUBIndex = -1;
		
		if(depth == depthFinalVariable) {
			for(int i = 0; i < branching; i++) {
				currentPath.setValue(depth, i);
				if((onPartialPath && (partialPath[depth] == i || partialPath[depth] == -1)) || (example != null && (!example.isAlive() || example.getChild(i) != null))) {
					L child = null;
					if(onPartialPath && (partialPath[depth] == i || partialPath[depth] == -1))
						child = createLeaf(currentPath, real, g, sender, withUB);
					else
						child = createLeaf(currentPath, real, withUB);
					if(child == null) {
						assert !hasSupport(currentPath);
						continue;
					}
					set = true;
					assert  !this.storeReceivedGoods || g == null ||  this.checkLeaf((L)child, currentPath, withUB, g.getUtility(), sender);
					node.setChild(child, i);

					if(real && child.utilCandidate(maxUtil, maximize)) {
						maxUtil = child.getUtil();
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
			int domainSize = this.domainSize[depth];
			
			for(int i = 0; i < branching; i++) {
				boolean newReal = real && i < domainSize;
				currentPath.setValue(depth, i, i < domainSize);
				InnerNode<U, L> childExample = null;
				if(example != null)
					childExample = (InnerNode<U, L>)example.getChild(i);
				if((onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) || childExample != null)) {
					InnerNode<U, L> child = fillTree(childExample, nextDepth, currentPath, newReal, onLocalPath && (localIndex == -1 || localIndex == i), withUB, onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i), partialPath, g, sender);
					if(child != null) {
						assert onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) || example.getChild(i) != null;
						set = true;
						node.setChild(child, i);

						if(newReal && child.utilCandidate(maxUtil, maximize)) {
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
		} else
			assert this.checkTree(depth, null, currentPath, withUB, false, true, true);
			return null;
	}

	/**
	 * Fills a part of the tree that should be converted from dummy to real by the reception of a new domain
	 * value.
	 * 
	 * @author Brammert Ottens, 30 sep 2009
	 * @param depth 		the current depth
	 * @param partialPath	the partial path defined by the received good
	 * @param currentNode 	the current node
	 * @param currentPath	the current path taken through the tree. Is only up to date up to depth
	 * @param g 			the received good
	 * @param utilityDelta  the difference with the previously reported utility for this particlar good
	 * @param sender 		the sender of the good
	 * @param real			\c true when the current node should be a real node, and \c false when it should be a dummy
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param withUB		\c true when the UB must be instantiated as well
	 * @param onPartialPath	\c true when we are still on the partial path
	 * @param fill			\c true when the tree should be extended by copying an already existing branch
	 */	
	@SuppressWarnings("unchecked")
	private void fillTree(int depth, int[] partialPath, InnerNode<U, L> currentNode, IntArrayWrapper currentPath, Good<Val, U> g, U utilityDelta, int sender, boolean real, boolean onLocalPath, final boolean withUB, boolean onPartialPath, boolean fill) {
		if(currentNode.isAlive()) {
			int nextDepth = depth + 1;
			int branching = branchingFactor[depth];
			int localIndex = -2;
			if(optimalLocalPath != null)
				localIndex = optimalLocalPath[depth];
			currentNode.real = real;
			boolean set = false;
			U maxUtil = null;
			U maxUB = null;
			int maxUtilIndex = -1;
			int maxUBIndex = -1;
			
			if(depth == depthFinalVariable) {
				for(int i = 0; i < branching; i++) {
					currentPath.setValue(depth, i);
					L leaf = (L)currentNode.getChild(i);
					boolean childExists = leaf != null;
					if(!childExists) {
						if(onPartialPath)
							leaf = createLeaf(currentPath, real, g, sender, withUB);
						else
							leaf = createLeaf(currentPath, real, withUB);
						childExists = leaf != null;
						if(childExists) {
							currentNode.setChild(leaf, i);
							assert  !this.storeReceivedGoods ||  this.checkLeaf((L)leaf, currentPath, withUB, g.getUtility(), sender);
						}
					} else if(onPartialPath && partialPath[depth] == i){
						leaf.real = real;
						if(withUB) {
							if(this.storeReceivedGoods)
								leaf.updateLeafWithUB(g, utilityDelta, sender, upperBoundSums, powersOf2, goodsReceived.get(sender).containsKey(currentPath.getPartialAssignment(childrenVariables[sender], this.separatorSizePerChild[sender])), maximize);
							else
								leaf.updateLeafWithUB(g, utilityDelta, sender, upperBoundSums, powersOf2, false, maximize);
						} else {
							leaf.updateLeafNoUB(g, utilityDelta, sender, powersOf2, maximize);
						}
						assert  !this.storeReceivedGoods ||  this.checkLeaf((L)leaf, currentPath, withUB, g.getUtility(), sender);
					} else if(withUB){
						leaf.real = real;
						leaf.setUB(leaf.calculateUB(upperBoundSums, maximize));
						if(leaf.getUB() == infeasibleUtil) {
							leaf.setUtil(infeasibleUtil);
							leaf.setUpToDate(true);
						}
					}

					if(childExists) {
						set = true;
						if(real && leaf.utilCandidate(maxUtil, maximize)) {
							maxUtil = leaf.getUtil();
							maxUtilIndex = i;
						}

						if(withUB && leaf.ubCandidate(maxUB, maximize)) {
							maxUB = leaf.getUB();
							maxUBIndex = i;
						}
					}
				}

				if(withUB && set && maxUBIndex != -1) {
					currentNode.setUB(maxUBIndex, true);
				}

				currentNode.setUtil(maxUtilIndex, true);
				
			} else {
				int domainSize = this.domainSize[depth];
				
				InnerNode<U, L> example = (InnerNode<U, L>)currentNode.getExample();
				
				int difference = branching - currentNode.children.length;
				if(difference != 0) { // some children need to be added
					currentNode.enlargeChildrenArray(difference, dummy[depth]);
				}

				for(int i = 0; i < branching; i++) {
					boolean newReal = real && i < domainSize;
					currentPath.setValue(depth, i, i < domainSize);
					InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
					boolean childExists = child != null;
					if( !childExists) {
						boolean cont = onPartialPath && (partialPath[depth] == -1 || partialPath[depth] == i) && i < domainSize;
						if(fill && (cont || example != null)) {
							child = fillTree(example, nextDepth, currentPath, newReal, onLocalPath && (localIndex == -1 || localIndex == i), withUB, cont, partialPath, g, sender);
							assert child == null || (cont || example != null);
							childExists = child != null;
						} else if (cont) {
							if(withUB)
								child = this.createPathWithUB(nextDepth, currentPath, partialPath, newReal, g, sender);
							else
								child = (InnerNode<U, L>)this.createPathNoUB(nextDepth, currentPath, partialPath, newReal, g, sender);
							childExists = child != null;
						}
					} else
						this.fillTree(nextDepth, partialPath, child, currentPath, g, utilityDelta, sender, newReal, onLocalPath && (localIndex == -1 || localIndex == i), withUB, onPartialPath && (partialPath[depth] == i || partialPath[depth] == -1), fill);
					
					if(childExists) {
						assert child.real == newReal;
						set = true;
						currentNode.setChild(child, i);

						if(newReal && child.utilCandidate(maxUtil, maximize)) {
							maxUtil = child.getUtil();
							maxUtilIndex = i;
						}

						if(withUB && child.ubCandidate(maxUB, maximize)) {
							maxUB = child.getUB();
							maxUBIndex = i;
						}
					}
				}

				if(withUB  && set && maxUBIndex != -1) {
					currentNode.setUB(maxUBIndex, false);
				}
				currentNode.setUtil(maxUtilIndex, false);
			}

			assert maxUB == null || greaterThanOrEqual(maxUB, oldUB);
			assert !set || currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
			assert currentNode.check2(maximize);
			assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
			assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
			assert this.checkTree(depth, currentNode, currentPath, withUB, false, true, true);
		}
	}
	
	/**
	 * Check whether the assignment to the local problem, represented
	 * by localPath, can still be used somewhere. Is only called from removeAmax()!!
	 * 
	 * @author Brammert Ottens, 23 sep 2009
	 * @param depth			the current depth
	 * @param localPath		the part of the assignment to the local problem that has just been sent as a good
	 * @param currentNode	the current node being visited
	 * @return	\c true when there still is some use for the local problem assignment, and \c false otherwise 
	 */
	@SuppressWarnings("unchecked")
	protected boolean findUnused(int depth, int[] localPath, InnerNode<U, L> currentNode) {
		if(!currentNode.isAlive()) // none of the assignments will be used
			return false;
		
		if(depth == depthFinalVariable)
			return true;
		
		int nextDepth = depth + 1;
		if(localPath[depth] == -1) {
			int branching = branchingFactor[depth];
			
			for(int i = 0; i < branching; i++) {
				Node<U> child = currentNode.getChild(i);
				if(child == null)
					return true;
				else
					if(findUnused(nextDepth, localPath, (InnerNode<U,L>)child))
						return true;
			}
			
			return false;
		} else {
			Node<U> child = currentNode.getChild(localPath[depth]);
			if(child == null || !((InnerNode<U, L>)child).real)
				return true;
			
			return findUnused(nextDepth, localPath, (InnerNode<U, L>)child);
		}
	}
	
	/**
	 * Given the assignment of variables in its separator, this method returns the utility
	 * for all domain elements of the own variable
	 * @param optimalPath	the path trough the tree that leads to the optimal leafnode
	 * @param assignments collection of assignments of variables in the separator
	 * @return an array of utilities
	 */
	protected U getOwnVariableOptions(int[] optimalPath, Val[] assignments) {
		int[] path = new int[depthFinalVariable];
		 
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
		
		return getOwnVariableOptions(path, optimalPath, this.infeasibleUtil, 0, root);
	}
	
	/**
	 * Initialize all the variables of the tree
	 * @param numberOfChildren 					The number of children
	 * @param zero 								The zero utility
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void init(int numberOfChildren, U zero) {
		numberOfAncestors = numberOfVariables;
		maxNumberLocalProblemOccurences = 1;
		localCounter = 1;
		
		if(numberOfVariables != 0) {
			upperBoundIsInfiniteCounter = numberOfChildren;
			upperBoundIsMinInfiniteCounter = 0;
			fullInfoCounter = numberOfChildren;
			dummyDepth = 0;
			upperboundArraySize = (int)Math.pow(2, numberOfChildren) - 1;;
			
			this.numberOfChildren = numberOfChildren;
			
			/* First, create a map from variable value combinations to positions in the tree*/
			variableToDepth 		= new HashMap<String, Integer>(numberOfVariables);
			valuePointers 			= new HashMap<String, HashMap<Val, Integer>>(numberOfVariables);
			domains 				= new HashMap<String, ArrayList<Val>>(numberOfVariables);
			domainSize 				= new int[numberOfVariables];
			finalDomainSize 		= new int[numberOfVariables];
			finalDomainSizeUnknownVariables = new HashMap<String, Integer>();
			branchingFactor 		= new int[numberOfVariables];
			dummy 					= new boolean[numberOfVariables];
			childrenVariables 		= new boolean[numberOfChildren][numberOfVariables];
			childrenVariablesReportingOrder = new String[numberOfChildren][];
			ownVariables 			= new boolean [numberOfVariables];
			goodsReceived 			= new ArrayList<HashMap<IntArrayWrapper, U>>(numberOfChildren);
			upperBounds 			= (U[])new Addable[numberOfChildren];
			separatorSizePerChild 	= new int[numberOfChildren];
			unpackedVariablesPerChild = new String[numberOfChildren][]; 
			
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
	 * This method adds the dummy part to the tree. It searches through the tree from
	 * left to right. Every time it needs to add a dummy node, it copies the tree below the 
	 * left most child of the current node into the dummy node. Concurrently it also
	 * instantiates the upper bounds
	 * @param currentNodeUncast	the node currently being visited
	 * @param currentPathUncast	the path taken
	 * @param depth			the current depth
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param g				the received good
	 * @param sender 		the sender of the good
	 * @return the upperBound for this node
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected U initiateBounds(frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNodeUncast, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree.IntArrayWrapper currentPathUncast, int depth, boolean onLocalPath, Good<Val, U> g, int sender) {
		InnerNode<U, L> currentNode = (InnerNode<U, L>)currentNodeUncast;
		IntArrayWrapper currentPath = (IntArrayWrapper)currentPathUncast;
		int nextDepth = depth + 1;
		int branching = branchingFactor[depth];
		int domainSize = this.domainSize[depth];
		int localIndex = -2;
		if(optimalLocalPath != null)
			localIndex = optimalLocalPath[depth];
		U maxUB = null;
		int maxUBIndex = -1;
		U maxUtil = null;
		int maxUtilIndex = -1;
		
		if(currentNode.real) {
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
				boolean createDummy = false;
				
				if(dummy[depth]) { // add the dummy position
					currentNode.enlargeChildrenArray(branching - currentNode.children.length, false);
					branching -= 1;
					createDummy = true;
				}

				int i = 0;
				for(; i < branching; i++) {
					currentPath.setValue(depth, i, i < domainSize);
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

				if(createDummy) {
					currentPath.setValue(depth, -1);
					InnerNode<U, L> example = (InnerNode<U, L>)currentNode.getExample();
					InnerNode<U, L> child = null;
					if(example != null)
						child = this.fillTree(example, nextDepth, currentPath, false, onLocalPath && (localIndex == -1 || localIndex == branching), true, false, null, g, sender);
					if(child != null) {
						currentNode.setChild(child, branching);
						
						if(child.ubCandidate(maxUB, maximize)) {
							maxUB = child.getUB();
							maxUBIndex = branching;
						}
					}
				}

				currentNode.setUB(maxUBIndex, false);
				currentNode.setUtil(maxUtilIndex, false);
				assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
			} 
		} else {
			if(depth == depthFinalVariable) {
				for(int i = 0; i < branching; i++) {
					currentPath.setValue(depth, i);
					L leaf = null;
					leaf = createLeaf(currentPath, false, true);
				if(leaf != null) {
						U UB = leaf.calculateUB(upperBoundSums, maximize);
						currentNode.setChild(leaf, i);

						if(leaf.ubCandidate(maxUB, maximize)) {
							maxUB = UB;
							maxUBIndex = i;
						}
					}
				}
				currentNode.setUB(maxUBIndex, true);
			} else {
				int childBranching = branchingFactor[nextDepth];
				for(int i = 0; i < branching; i++) {
					currentPath.setValue(depth, i, i < domainSize);
					InnerNode<U, L> child = createInnerNode(childBranching);
					child.real = false;
					U UB = initiateBounds(child, currentPath, nextDepth, onLocalPath && localIndex == i, g, sender);

					if(UB != null) {
						currentNode.setChild(child, i);

						if(child.ubCandidate(maxUB, maximize)) {
							maxUB = UB;
							maxUBIndex = i;
						}
					}
				}

				currentNode.setUB(maxUBIndex, false);
			}
		}

		assert this.checkTree(depth, currentNode, currentPath, true, true, true, true);
		return maxUB;
	}

	/**
	 * This method walks trough the tree according to the given partial path and finds all leaf nodes
	 * that correspond to assignments that are compatible with the good to be added
	 * @param depth			The current depth
	 * @param currentPathUncast	The current path
	 * @param partialPath	The path dictated by the received good
	 * @param currentNodeUncast	The current node
	 * @param g 			the utility reported
	 * @param utilityDelta	The difference with the previously reported utility for the assignment belonging to the partial path
	 * @param sender 		The sender of the good
	 * @param onLocalPath	\c true when we are still following the path of the currently best local solution
	 * @param withUB		\c true when the UB must be updated as well
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void updatePath(int depth, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree.IntArrayWrapper currentPathUncast, int[] partialPath, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNodeUncast, Good<Val, U> g, U utilityDelta, int sender, boolean onLocalPath, final boolean withUB) {
		IntArrayWrapper currentPath = (IntArrayWrapper)currentPathUncast;
		InnerNode<U, L> currentNode = (InnerNode<U, L>) currentNodeUncast;
		if(currentNode.isAlive()) {
			int branching = branchingFactor[depth];
			int childIndex = partialPath[depth];
			int localIndex = -2;
			if(optimalLocalPath != null)
				localIndex = optimalLocalPath[depth];
			int nextDepth = depth + 1;
			U maxUtil = null;
			int maxUtilIndex = -1;
			boolean real = currentNode.real;
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
						
						assert !withUB && real;
						if(leaf == null) {
							// the child should be created
							leaf = createLeaf(currentPath, real, g, sender, withUB);
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
					assert currentNode.check3(maximize);
				} else {
					// the assignment did specify the variabes own assignment
					// get the child and specify the current path
					L leaf = (L)currentNode.getChild(childIndex);
					currentPath.setValue(depth, childIndex);

					if(leaf == null) {
						// the child must be created
						leaf = createLeaf(currentPath, real, g, sender, withUB);
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

								if(real && child.utilCandidate(maxUtil, maximize)) {
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
					} else if (currentNode.real) {
						if(leaf != null) {
							if(currentUtil == null) {
								currentNode.setUtil(childIndex, true);
								assert currentNode.getMaxChild() < domains.get(depthToVariable[depth]).size();
							} else /* if(leaf != null ) */{ 
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
				int domainSize = this.domainSize[depth];
				
				int difference = branching - currentNode.children.length;
				if(difference != 0) { // some children need to be added
					currentNode.enlargeChildrenArray(difference, dummy[depth]);
				}
				
				if(childIndex == -1) { // all children must be updated
					for(int i = 0; i < branching; i++) {
						InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
						boolean isDummy = i == domainSize;
						currentPath.setValue(depth, i, !isDummy);
						
						if(child == null) {
							if(isDummy) {
								InnerNode<U, L> example = null;
								if(maxUBIndex != -1)
									example = (InnerNode<U, L>)currentNode.getChild(maxUBIndex);
								else if (maxUtilIndex != -1)
									example = (InnerNode<U, L>)currentNode.getChild(maxUtilIndex);
								if(example != null /* || this.hasAggregateSpace */) {
									child = this.fillTree(example, nextDepth, currentPath, false, onLocalPath, withUB, true, partialPath, g, sender);
								}
								assert this.checkTree(nextDepth, child, currentPath, withUB, false, true, true);
							} else {
								if(withUB)
									child = createPathWithUB(nextDepth, currentPath, partialPath, currentNode.real && (!dummy[depth] || i < domainSize), g, sender);
								else
									child = (InnerNode<U, L>)createPathNoUB(nextDepth, currentPath, partialPath, currentNode.real && (!dummy[depth] || i < domainSize), g, sender);
							}
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
							child = createPathWithUB(nextDepth, currentPath, partialPath, currentNode.real, g, sender);
						else
							child = (InnerNode<U, L>)createPathNoUB(nextDepth, currentPath, partialPath, currentNode.real, g, sender);
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
						assert currentNode.check2(maximize);
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
			assert currentNode.check3(maximize);
			assert currentNode.check5(upperBoundSums, maximize);
			assert currentNode.check2(maximize);
			assert !currentNode.hasUB() || this.UBexists(currentNode, depth);
			assert !currentNode.hasUtil() || this.Utilexists(currentNode, depth);
			assert this.checkTree(depth, currentNode, currentPath, withUB, false, true, true);
		}
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#upperBoundChangesUtil(frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode, frodo2.solutionSpaces.Addable)
	 */
	@Override
	protected boolean upperBoundChangesUtil(frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> node, U UB) {
		return ((InnerNode<U, L>)node).real && UB == infeasibleUtil;
	}
	

	/**
	 * Remove the dummy element for the variables marked in change.
	 * 
	 * @param removalDepth		the maximal depth until where dummy elements are to be found
	 * @param change			for each variable whether the dummy elements must be removed
	 * @param depth				the current depth
	 * @param currentNodeUncast	the current node that is being visited
	 */
	@SuppressWarnings("unchecked")
	protected void removeDummies(int removalDepth, boolean[] change, int depth, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNodeUncast) {
		InnerNode<U, L> currentNode = (InnerNode<U, L>) currentNodeUncast;
		boolean goDeeper = depth < removalDepth; 
		int nextDepth = depth + 1;
		if(change[depth])
			currentNode.removeDummy();
		
		U maxUB = null;
		int maxUBIndex = -1;
		boolean alive = false;

		for(int i = 0; i < branchingFactor[depth]; i++) {
			InnerNode<U, L> child = (InnerNode<U, L>)currentNode.getChild(i);
			boolean childExists = child != null;
			if(goDeeper && childExists) {
				removeDummies(removalDepth, change, nextDepth, child);
			}
			
			if(child == null || child.isAlive())
				alive = true;
				
			if(this.upperBoundIsInfiniteCounter == 0 && childExists && child.hasUB()) { 
				U newUB = child.getUB();

				if(maxUB == null || greaterThan(maxUB, newUB)) {
					maxUB = newUB;
					maxUBIndex = i;
				}
			}
		}
	
		currentNode.setAlive(alive);
		if(maxUBIndex != -1) {
			currentNode.setUB(maxUBIndex, false);
		}
	
		assert branchingFactor[depth] == currentNode.children.length;
	}
	
	/**
	 * Sets the final size of the domain of a variable
	 * @param change 	boolean to store the variables whose size has been set
	 * @param variable	the variable whose final domain size is to be set
	 * @param size		the final domain size
	 * @param depth		the depth of \c variable
	 * @return the depth of the variable to be changed
	 */
	private int setFinalDomainSize(boolean[] change, String variable, int size, int depth) {
		int returnDepth = 0;
		assert size != 0;
		finalDomainSize[depth] = size;
		
		if(size == domainSize[depth]) {
			branchingFactor[depth] = size;
			change[depth] = dummy[depth];
			if(change[depth]) {
				returnDepth = depth;
				this.numberOfDummies--;
			}
			dummy[depth] = false;
		}
		
		return returnDepth;
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#createIntArrayWrapper(int[])
	 */
	@Override
	public IntArrayWrapper createIntArrayWrapper(int[] array) {
		return new IntArrayWrapper(array);
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree#createIntArrayWrapper(int)
	 */
	@Override
	public IntArrayWrapper createIntArrayWrapper(int size) {
		return new IntArrayWrapper(size);
	}
	
	/**
	 * 
	 * The IntArrayWrapper is used as a key for (partial) assignments.
	 * The hash function used is a java implementation of the Hsieh hash function
	 * 
	 * @author Brammert Ottens, 22 apr 2010
	 *
	 */
	public static class IntArrayWrapper extends frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree.IntArrayWrapper {
		
		/**
		 * Constructor
		 * @param array	the array to be wrapped
		 */
		public IntArrayWrapper(int[] array) {
			super(array);
		}
		
		/**
		 * Constructor
		 * @param size the size of the array
		 */
		public IntArrayWrapper(int size) {
			super(size);
		}
		
		/**
		 * Constructor
		 * @param array			The array
		 * @param byteArray		The byte representation of the array
		 */
		public IntArrayWrapper(int[] array, byte[] byteArray) {
			super(array, byteArray);
		}
		
		/**
		 * Sets the value in the array
		 * @author Brammert Ottens, 26 jun 2009
		 * @param index	the position in the array
		 * @param value	the value to be placed
		 * @param real	\c true when the value points to a domain value, \c false otherwise 
		 */

		public void setValue(int index, int value, boolean real) {
			if(change) {
				if(!real)
					value = -1;
				this.setValue(index, value);
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
			for(int i = 0; i < numberOfChildren; i++) {
				U temp = goodsReceived.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
				if(temp != null) {
					childUtil = childUtil.add(temp);
					UB = UB.add(temp);
	
				} else if(upperBounds[i] != null) { // this function should only be called when all the upperBounds are set!
					UB = UB.add(upperBounds[i]);
				}
			}
		
		util = util.add(childUtil);
		
//		assert !checkUB || greaterThanOrEqual(UB, leaf.getUB()) : UB.toString() + " vs. " + leaf.getUB();
//		assert !leaf.real || UB == infeasibleUtil || leaf.getUtil().equals(util) : leaf.getUtil().toString() + " != " + util;	// the utility should be correct
//		assert !checkUB || greaterThanOrEqual(leaf.getUB(), oldUB);							// the UB should not be higher than the old max UB
//		assert !checkUB || leaf.counter != 0 || leaf.getUB().equals(leaf.getUtil());					// if the assignment is confirmed, UB should be equal to utility

		return true;
	}

	/**
	 * Check whether only the parts of the tree that should be dummy are dummy
	 * 
	 * @author Brammert Ottens, 30 sep 2009
	 * @param depth			The current depth
	 * @param currentNode	the current node being visited
	 * @return	\c true
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	private boolean checkDummy(int depth, InnerNode<U, L> currentNode) {
		if(currentNode.isAlive()) {
			int nextDepth = depth + 1;
			int branching = branchingFactor[depth];
			
			for(int i = 0; i < branching; i++) {
				Node<U> child = currentNode.getChild(i);
				if(child != null) {
					if(i == branching - 1) {
						assert !currentNode.real || (dummy[depth] && !((InnerNode<U, L>)child).real) || (!dummy[depth] && ((InnerNode<U, L>)child).real);
					} else {
						assert currentNode.real == ((InnerNode<U, L>)child).real;
					}
					
					if(depth < depthFinalVariable)
						checkDummy(nextDepth, (InnerNode<U, L>)child);
				}
			}
		}
		return true;		
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

		return hasSupport && (this.getUtilityLocalProblem(currentPath) != infeasibleUtil);
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
		 * @param currentNodeUncast	the current node being visited
		 * @param currentPathUncast 	the current path taken
		 * @param UB 			the current UB
		 * @param checkLeafs 	\c true when the leaves are to be checked
		 * @param checkSupport 	\c check whether existing leaf nodes actually have support
		 * @param checkUtil		\c true when utility values should be checked
		 * @return always returns true
		 */
		@SuppressWarnings("unchecked")
		@Override
		protected boolean checkTree(int depth, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNode<U, L> currentNodeUncast, frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.InnerNodeTree.IntArrayWrapper currentPathUncast, boolean UB, boolean checkLeafs, boolean checkSupport, boolean checkUtil) {
			IntArrayWrapper currentPath = (IntArrayWrapper)currentPathUncast;
			InnerNode<U, L> currentNode = (InnerNode<U, L>)currentNodeUncast;
			int nextDepth = depth + 1;
			int branching = branchingFactor[depth];
			int domainSize = this.domainSize[depth];
			U maxUtil = null;
			U maxUB = null;
			int maxUBIndex = -1;
//			int maxUtilIndex = -1;
			
			if(currentNode == null || currentNode.isAlive()) {
				assert currentNode == null || currentNode.children.length == branching;
				if(dummy[depth])
					assert currentNode == null|| !currentNode.real || currentNode.getChild(currentNode.children.length - 1) == null || (depth == depthFinalVariable && !((LeafNode<U>)currentNode.getChild(currentNode.children.length - 1)).real) || (depth != depthFinalVariable && !((InnerNode<U, L>)currentNode.getChild(currentNode.children.length - 1)).real);
				else
					assert currentNode == null || !currentNode.real || currentNode.getChild(currentNode.children.length - 1) == null || (depth == depthFinalVariable && ((LeafNode<U>)currentNode.getChild(currentNode.children.length - 1)).real) || (depth != depthFinalVariable && ((InnerNode<U, L>)currentNode.getChild(currentNode.children.length - 1)).real);
				
				if(depth == depthFinalVariable) {
					for(int i = 0; i < branching; i++) {
						if(currentPath != null)
							currentPath.setValue(depth, i);
						L leaf = null;
						if(currentNode != null)
							leaf = (L)currentNode.getChild(i);								// the leaf node should be consistent with the information
						
						if(leaf != null) {
							assert currentNode.real == leaf.real;
							if(leaf.real && leaf.isUpToDate() && (maxUtil == null || greaterThan(maxUtil, leaf.getUtil()))) {
								maxUtil = leaf.getUtil();
//								maxUtilIndex = i;
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
					
//					assert !checkUtil || currentNode == null || !currentNode.real || maxUtilIndex == currentNode.getMaxChild() || currentNode.getUtil().equals(maxUtil);
					assert !UB || currentNode == null || maxUBIndex == currentNode.getMaxUBChild();
				} else {
					for(int i = 0; i < branching;  i++) {
						if(currentPath != null)
							currentPath.setValue(depth, i, i < domainSize);
						InnerNode<U, L> child = null;
						if(currentNode != null)
							child = (InnerNode<U, L>)currentNode.getChild(i);
	
						//					if(child != null || i < domainSize)
						checkTree(nextDepth, child, currentPath, UB, checkLeafs, checkSupport, checkUtil && currentNode != null && i == currentNode.getMaxChild());
						if(child != null) {
							if(currentNode.real && child.isAlive() && child.hasUtil() && child.getMaxUtil().isUpToDate() && (maxUtil == null || greaterThan(maxUtil, child.getUtil()))) {
								maxUtil = child.getUtil();
//								maxUtilIndex = i;
							}
	
							if(UB && child.hasUB() && (maxUB == null || greaterThan(maxUB, child.getUB()))) {
								maxUB = child.getUB();
								maxUBIndex = i;
							}
						}
	
						assert child == null || dummy[depth] || i < domainSize;
					}
	
//					assert !checkUtil || currentNode == null || !currentNode.real || maxUtilIndex == currentNode.getMaxChild() || maxUtil.equals(currentNode.getUtil());
	//				assert !UB || currentNode == null || maxUBIndex == currentNode.getMaxUBChild();
					assert currentNode == null || !dummy[depth] || upperBoundIsInfiniteCounter != 0 || currentNode.getChild(currentNode.children.length - 1) == null || !((InnerNode<U, L>)currentNode.getChild(currentNode.children.length - 1)).real;
				}
			}
			return true;		
		}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#add(frodo2.algorithms.odpop.Good, int, java.util.HashMap)
	 */
	@Override
	public boolean add(Good<Val, U> g, int sender,
			HashMap<String, Val[]> domains) {
		assert false : "Not Implemented";
		return false;
	}

	/** 
	 * @see frodo2.algorithms.odpop.goodsTree.GoodsTree#getDomains()
	 */
	@Override
	public Val[][] getDomains() {
		assert false : "Not Implemented";
		return null;
	}
}
