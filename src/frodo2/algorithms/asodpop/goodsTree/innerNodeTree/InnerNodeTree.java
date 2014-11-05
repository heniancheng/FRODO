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

package frodo2.algorithms.asodpop.goodsTree.innerNodeTree;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import frodo2.algorithms.odpop.goodsTree.InnerNodeTree.InnerNode;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
import frodo2.algorithms.asodpop.Good;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.UtilitySolutionSpace;


/**
 * This class is designed to store GOODs received by a node from its children, and is used in the ASODPOP algorithm.
 * A GOOD contains an assignment to a set of variables, a utility and a variable denoting whether it is a confirmed
 * or speculative good. A GOOD is identified by its assignment, and in this class the GOODs are ordered using a 
 * tree based on these assignments.
 * 
 * The basic functionality of this class is to either add a received GOOD to the tree, or to obtain the assignment
 * that has the highest utility.
 * @author Brammert
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @todo write Unit test for this class
 */
public class InnerNodeTree < Val extends Addable<Val>, U extends Addable<U> > 
extends frodo2.algorithms.odpop.goodsTree.InnerNodeTree.InnerNodeTree<Val, U, LeafNode<U>> {

	/** Used for serialization */
	private static final long serialVersionUID = 4206985864919963001L;

	/** For each child a map that stores the utilities received from children */
	private ArrayList<HashMap<IntArrayWrapper, Boolean>> goodsConfirmed;
	
	/** The optimal solution of the local problem */
	private Val[] optimalLocalSolution;
		
	/** For each option the optimal utility your own local problem provides*/
	protected U[] localOptions;
	
	/** Counts the number of speculative UTIL messages*/
	private int speculativeUTILcounter;
	
	/** Counts the total number of UTIl messages received */
	private int UTILcounter = 0;
	
	/**
	 * A constructor (Only used in testing the datastructure)
	 * @warning we assume that the agent's own variable is put in the end of variables_order
	 * @param ownVariable 		The variable ID
	 * @param ownVariableDomain The domain of \c ownVariable
	 * @param space				A list of utility values for the different value combinations
	 * @param numberOfChildren 	The number of children
	 * @param zero 				The zero utility
	 * @param infeasibleUtil 	The infeasible utility
	 * @param maximize 			when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats		\c true when statistics should be collected, and \c false otherwise
	 */
	public InnerNodeTree( String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, int numberOfChildren, U zero, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(new LeafNode<U>(), ownVariable, ownVariableDomain, space, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
		this.domainElementClass = ownVarDomain[0].getClass();
		init(numberOfChildren, zero);

		if(numberOfVariables != 0) {
			
			root = new InnerNode<U, LeafNode<U>>(domainSize[0]);
			
			fullInfo = false;
			solveLocalProblem();
			this.updateLocalProblem();
		} else { // this might look double, but is is not!
			root = new InnerNode<U, LeafNode<U>>(null);
		}
	}

	/**
	 * A constructor
	 * @warning we assume that the agents own variable is put in the end of variables_order
	 * @param ownVariable 		The variable ID
	 * @param ownVariableDomain The domain of \c ownVariable
	 * @param spaces			The hypercubes representing the local problem
	 * @param numberOfChildren 	The number of children
	 * @param zero 				The zero utility
	 * @param infeasibleUtil 	The infeasible utility
	 * @param maximize 			when \c true we are maximizing, when \c false we are minimizing
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	public InnerNodeTree( String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, int numberOfChildren, U zero, U infeasibleUtil, boolean maximize, boolean collectStats) {
		super(new LeafNode<U>(), ownVariable, ownVariableDomain, spaces, zero, numberOfChildren, infeasibleUtil, maximize, collectStats);
		this.domainElementClass = ownVarDomain[0].getClass();
		init(numberOfChildren, zero);

		if(numberOfVariables != 0) {
			root = new InnerNode<U, LeafNode<U>>(domainSize[0]);

			fullInfo = false;
			if(hasLocalProblem) {
				solveLocalProblem();
				this.updateLocalProblem();
			}
		} else { // this might look double, but it is not!
			root = new InnerNode<U, LeafNode<U>>(null);
		}
	}

	/**
	 * This method adds a received GOOD to the tree
	 * @param g 		The good to be added
	 * @param sender 	The sender of the good
	 * @return \c true when a new variable has been discovered
	 */
	public int add(Good<Val, U> g, int sender) {
		int nbrNewVariables = 0;
		
		if(numberOfVariables > 0) {
			assert this.setOldUB();
			U utility = g.getUtility();
			
			boolean isConfirmed = g.isConfirmed();
			boolean[] relevantChildren = new boolean[numberOfChildren];
			Arrays.fill(relevantChildren, true);
			relevantChildren[sender] = false;
			
			this.UTILcounter++;
			if(!isConfirmed)
				this.speculativeUTILcounter++;
			
			if(isConfirmed && utility == infeasibleUtil)
				this.upperBoundIsMinInfiniteCounter++;

			String[] aVariables = g.getVariables();
			Val[] values = g.getValues();
			ArrayList<Val[]> reportedValues = new ArrayList<Val[]>(1);
			reportedValues.add(values);
			
			for(Val[] aValues : reportedValues) {
				boolean newVariable = false, newDomainElement = false;
				boolean initializeBounds = false;
				boolean updateInfo = false;
				boolean fill = true;
				int fillCounter = 1;
				boolean possibleInconsistencies = false;
				
				ArrayList<String> additionalVariables = new ArrayList<String>();
				ArrayList<ArrayList<Val>> additionalValues = new ArrayList<ArrayList<Val>>();
				HashMap<String, Val> newVariables = new HashMap<String, Val>();
				HashMap<String, Val> newValues = new HashMap<String, Val>();
				
				
				int i = 0;
				for(String var : aVariables) {
					ArrayList<Val> vals = new ArrayList<Val>();
					if(!variableToDepth.containsKey(var)) {
						newVariables.put(var, aValues[i]);
						newVariable = true;
						updateInfo = true;
						additionalVariables.add(var);
						vals.add(aValues[i]);
						additionalValues.add(vals);
						separatorSizePerChild[sender] += 1;
					} else if(!valuePointers.get(var).containsKey(aValues[i])) {
						int varIndex = variableToDepth.get(var);
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
						if(!childrenVariables[sender][varIndex]) {
							updateInfo = true;
							additionalVariables.add(var);
							vals.add(aValues[i]);
							ArrayList<Val> domain = domains.get(var);
							for(int k = 0; k < domain.size(); k++) 
								vals.add(domain.get(k));
							additionalValues.add(vals);
							separatorSizePerChild[sender] += 1;
							childrenVariables[sender][varIndex] = true;
						}
					} else {
						int varIndex = variableToDepth.get(var);
						if(!childrenVariables[sender][varIndex]) {
							updateInfo = true;
							additionalVariables.add(var);
							ArrayList<Val> domain = domains.get(var);
							for(int k = 0; k < domain.size(); k++) 
								vals.add(domain.get(k));
							additionalValues.add(vals);
							separatorSizePerChild[sender] += 1;
							childrenVariables[sender][varIndex] = true;
						}
					}
					i++;
				}

				int[] indexPath = null;
				if(newVariable) {
					nbrNewVariables = newVariables.size();
					indexPath = addNewVariable(aVariables, newVariables, sender);
				}
				
				fill = fillCounter != numberOfChildren || updateInfo || !this.childrenVariables[sender][depthFinalVariable];


				if(newDomainElement) {
					addNewDomainElement(newValues);
				}

				IntArrayWrapper key = ((IntArrayWrapper)toKey(aValues, aVariables, sender)).getPartialAssignment(this.childrenVariables[sender], this.separatorSizePerChild[sender]);

				if(updateInfo) {
					HashMap<IntArrayWrapper, U> received = goodsReceived.get(sender);
					HashMap<IntArrayWrapper, U> newReceived = new HashMap<IntArrayWrapper, U>(received.size());
					HashMap<IntArrayWrapper, Boolean> confirmed = goodsConfirmed.get(sender);
					Iterator<Entry<IntArrayWrapper, U>> it = received.entrySet().iterator();
					int size = additionalValues.size();

					boolean[] childVariables = this.childrenVariables[sender];
					boolean[] changableAll = new boolean[numberOfVariables];
					int[] maxAll = new int[numberOfVariables];
					int newCombinations = 1;

					for(i = 0; i < size; i++) {
						String var = additionalVariables.get(i);
						int s = additionalValues.get(i).size();
						int index = variableToDepth.get(var);
						changableAll[index] = true;
						maxAll[index] = s;
						newCombinations *= s;
					}

					int newSize = this.separatorSizePerChild[sender];
					int[] max = new int[size];
					boolean[] changable = new boolean[newSize];
					int j = 0;
					int k = 0;
					for(i = 0; i < numberOfVariables; i++) {
						if(childVariables[i]) {
							boolean change = changableAll[i];
							changable[j++] = change;
							if(change)
								max[k++] = maxAll[i];

						}
					}

					int[] vals = new int[size];
					while(it.hasNext()) {
						Entry<IntArrayWrapper, U> entry = it.next();
						it.remove();
						IntArrayWrapper array = entry.getKey();
						U util = entry.getValue();
						Boolean value = confirmed.remove(array);
						Arrays.fill(vals, 0);
						for(j = 0; j < newCombinations; j++) {
							IntArrayWrapper newArray = array.addValues(vals, changable, newSize);
							newReceived.put(newArray, util);
							confirmed.put(newArray, value);
							this.nextVariableAssignment(max, vals);
						}
					}
					goodsReceived.set(sender, newReceived);
					goodsConfirmed.set(sender, confirmed);
				}
				
				if(newVariable) {
					InnerNode<U, LeafNode<U>> newRoot = createInnerNode(branchingFactor[0]); // added
					addVariableToTree(nbrNewVariables - 1, new IntArrayWrapper(numberOfVariables), indexPath, 0, root, newRoot, possibleInconsistencies, true, sender);
					root = newRoot;
				}
				


				// add the received good to the goodsReceivedStorage
				assert !goodsReceived.get(sender).containsKey(key) || !goodsConfirmed.get(sender).get(key); 
				U old = goodsReceived.get(sender).get(key);
				U utilityDelta;
				if(old == null) {
					utilityDelta = utility;
				} else if(old == infeasibleUtil) {
					utilityDelta = zero;
				} else {
				utilityDelta = utility.subtract(old);
				}

				if(!isConfirmed || this.storeReceivedGoods) {
					goodsReceived.get(sender).put(key, utility);
				}
				goodsConfirmed.get(sender).put(key, isConfirmed);

				if(isConfirmed) {
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
				}

				// add the good to all the compatible leaves
				// create the partial path defined by the good
				int[] partialPath = new int[numberOfVariables];
				Arrays.fill(partialPath, -1);

				for(i = 0; i < aVariables.length; i++) {
					String var = aVariables[i];
					partialPath[variableToDepth.get(var)] = valuePointers.get(var).get(aValues[i]);
				}

				// add the good to the tree
				if(newDomainElement) {
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
						addNewDomainElementWithUB(0, horizon, new IntArrayWrapper(numberOfVariables), partialPath, newDomainPath, root, g, utilityDelta, sender, true, fill);
						assert !root.hasUB() || oldUB == null || greaterThanOrEqual(root.getUB(), oldUB);
					} else {
						addNewDomainElementNoUB(0, horizon, new IntArrayWrapper(numberOfVariables), partialPath, newDomainPath, root, g, utilityDelta, sender, true, fill);
					}
				} else {
					
					updatePath(0, new IntArrayWrapper(numberOfVariables), partialPath, root, g, utilityDelta, sender, true, !initializeBounds && upperBoundIsInfiniteCounter == 0);
					assert !root.isAlive() || !root.hasUB() || oldUB == null || this.greaterThanOrEqual(root.getUB(), oldUB);
					assert !root.isAlive() || !root.hasUB() || root.getUB().equals(root.getMaxUB().calculateUBTest(upperBoundSums));
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

					initiateBounds(root, new IntArrayWrapper(numberOfVariables), 0, true, g, sender);
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

				if(this.storeReceivedGoods)
					this.storeReceivedGoods = this.upperBoundIsInfiniteCounter != 0 || this.numberOfDummies != 0;

				assert !root.hasUB() || !root.hasUtil() || greaterThanOrEqual(root.getUtil(), root.getUB());
				assert !root.hasUtil() || this.Utilexists(root, 0);
				assert this.checkTree(0, root, new IntArrayWrapper(numberOfVariables), this.upperBoundIsInfiniteCounter == 0, true, true, true);
				assert !root.hasUtil() || root.getMaxUtil().isUpToDate();
				assert !root.hasUtil() || !root.hasUB() || !root.getMaxUB().real || !root.getMaxUB().isUpToDate() || greaterThanOrEqual(root.getMaxUB().getUtil(), root.getUtil());;
			}
		}
		
		return nbrNewVariables;
	}
	
	/**
	 * This method obtains the aMax. If aMax is a confirmed assignment, i.e. no other assignment
	 * will ever have a higher utility, then the path belonging to aMax must be removed. 
	 * @return Good
	 */
	@SuppressWarnings("unchecked")
	public Good<Val, U> getAmax() {
		if(!root.isAlive())
			return null;
		
		String[] variables = new String[depthOfFirstToBeRemovedVariables];
		Val[] values = (Val[]) Array.newInstance(domainElementClass, depthOfFirstToBeRemovedVariables);
		System.arraycopy(depthToVariable, 0, variables, 0, variables.length);
		boolean isConfirmed = false;
		U util = null;
		
		if(this.hasLocalProblem) { 
			if(this.localUpperBound == this.infeasibleUtil) {
				if(!root.hasUtil()) {
					if(!root.hasUB() || root.getUB() == this.infeasibleUtil) {
						root.setAlive(false);
						return null;
					}
				} else if(!root.hasUB() || root.getUB() == this.infeasibleUtil) {
					root.setAlive(false);
					return null;
				}
			}
		} else {
			if(root.hasUB() && root.getUB() == this.infeasibleUtil) {
				root.setAlive(false);
				return null;
			}
		}
		
		if(this.upperBoundIsMinInfiniteCounter == 0 && /*this.hasLocalProblem &&*/ optimalLocalPath != null && (!root.hasUtil() || (this.numberOfChildren > 0 && ((maximize && greaterThan(root.getUtil(), this.optimalLocalUtility)) || (!maximize && this.localUpperBound != null && greaterThan(root.getUtil(), this.localUpperBound)))  ))) {

			if(!this.ownVariables[0]) {// find the first non-supported leaf
				int[] valuesPath = new int[depthFinalVariable];
				for(int i = 0; i < depthFinalVariable; i++) {
					int path = optimalLocalPath[i];
					if(path == -1)
						valuesPath[i] = 0;
					else
						valuesPath[i] = path;
				}
				if(!this.findUnused(0, optimalLocalPath, valuesPath, (InnerNode<U, LeafNode<U>>)root))
					return null;
				
				for(int i = 0; i < depthOfFirstToBeRemovedVariables; i++) {
					values[i] = domains.get(depthToVariable[i]).get(valuesPath[i]);
					assert values[i] != null;
				}
				assert checkGood(values);
			} else if(optimalLocalSolution != null){
				System.arraycopy(optimalLocalSolution, 0, values, 0, depthOfFirstToBeRemovedVariables);
				assert checkGood(values);
			} else
				return null;
			
			util = optimalLocalUtility;
		} else if (root.hasUtil()) {
			InnerNode<U, LeafNode<U>> currentNode = (InnerNode<U, LeafNode<U>>)root;
			util = root.getUtil();
			
			for(int i = 0; i < depthOfFirstToBeRemovedVariables; i++) {
				assert domainSize[i] <= branchingFactor[i];
				String var = depthToVariable[i];
				int maxChild = currentNode.getMaxChild();
				assert maxChild < domains.get(var).size();
				variables[i] = var;
				values[i] = domains.get(var).get(maxChild);
				assert values[i] != null;
				currentNode = (InnerNode< U, LeafNode<U> >)currentNode.getChild(maxChild);
			}
			
			isConfirmed = root.hasUB() && root.getMaxUtil().counter == 0 && ((hasLocalProblem && greaterThanOrEqual(maximize ? root.getUB().max(localUpperBound) : root.getUB().min(localUpperBound), util)) || (!hasLocalProblem && greaterThanOrEqual(root.getUB(), util)));
			assert root.getMaxUtil().isUpToDate();
			assert !isConfirmed || root.getMaxUtil().counter == 0 || (root.getUB() == this.infeasibleUtil && root.getUtil() == this.infeasibleUtil);
		} else
			return null;
		
		
		
		// create the good and remove the path to aMax from the tree
		if(isConfirmed) {
			assert greaterThanOrEqual(root.getUB(), root.getUtil());
			boolean localRemoved = removePath(0, root, true);
			if(root.isAlive()) {
				if(hasLocalProblem) {
//					assert !ownVariables[0] || localRemoved || this.checkGood(optimalLocalSolution); 
					assert numberOfLocalVariables != numberOfVariables || !localRemoved || this.localCounter == 0;
					if(localRemoved && this.localCounter == 0) {
						this.updateLocalProblem();
						localCounter = maxNumberLocalProblemOccurences;
						assert localCounter >= 0;
					}
				} else {
					if(root.hasUB())
						localUpperBound = root.getUB();
				}
			}
		} else if(variables.length < 1)
			return null;
		
		assert !root.hasUB() || !root.hasUtil() || root.getUB() != this.infeasibleUtil || root.getUtil() != this.infeasibleUtil || this.localUpperBound != this.infeasibleUtil || isConfirmed; 
		assert !root.hasUB() || this.UBexists(root, 0);
		assert !root.hasUB() || root.getUB().equals(root.getMaxUB().calculateUBTest(upperBoundSums));
		root.setAlive(root.isAlive() && (root.hasUB() || root.hasUtil() || this.upperBoundIsMinInfiniteCounter == 0));
		assert !(!root.isAlive() && root.hasUB());
		assert !root.hasUtil() || root.getMaxUtil().isUpToDate();
		
		return new Good<Val, U>(variables, values, util, isConfirmed);

	}

	/**
	 * Given the assignment of variables in its separator, this method returns the best
	 * assignment possible.
	 * @param assignments 	the, possible partial, assignment to the variables in the variables separator
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void getBestAssignmentForOwnVariable(HashMap<String, Val> assignments) {

		Val[] context = (Val[]) new Addable[numberOfVariables];
		int[] optimalPath = new int[numberOfVariables];

		for(Entry<String, Val> e : assignments.entrySet()) {
			String var = e.getKey();
			if(!var.equals(ownVariable) && variableToDepth.containsKey(var))
				context[variableToDepth.get(var)] = e.getValue();
		}

		Arrays.fill(optimalPath, -1);
		getOwnVariableOptions(optimalPath, context);

		for(int i = 0; i < numberOfVariables; i++) {
			int value = optimalPath[i];
			if(value != -1) {
				String var = depthToVariable[i];
				assignments.put(var, this.domains.get(var).get(value));
			}
		}
		
		//		assert assignments.get(this.ownVariable) != null;
		
	}
	
	/**
	 * @author Brammert Ottens, 26 mei 2010
	 * @return a random value in the domain of the owning variable 
	 */
	public Val getRandomValue() {
		return this.ownVarDomain[(int)(Math.random()*this.ownVarDomain.length)];
	}
	
	/**
	 * Given a map that maps variables to values, this method returns the values
	 * belonging to the variables in a child's separator
	 * @author Brammert Ottens, 19 aug 2009
	 * @param parentContext	the value map
	 * @param child	the child for who we want to know the separator values
	 * @return	an array containing the values of the child's separator variables
	 */
	@Override
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
	 * @param variable variable ID
	 * @return	the depth of the variable in the array
	 */
	public int getVariableIndex(String variable) {
		return variableToDepth.get(variable);
	}
	
	/** @return total number of speculative UTIl messages received */
	public int getSpeculativeUTILcounter() {
		return this.speculativeUTILcounter;
	}
	
	/** @return total number of UTIl messages received */
	public int getUTILcounter() {
		return this.UTILcounter;
	}

	/**
	 * @param variable 		The variable for which we want to know whether this tree is aware of it
	 * @return \c true if this tree is familier with this variable
	 */
	public boolean knowsVariable(String variable) {
		return variableToDepth.containsKey(variable);
	}

	/**
	 * If there is only one variable in this tree, that means that there is not
	 * enough information to send a good
	 * @return boolean
	 */
	public boolean notEnoughInfo() {
		return numberOfVariables == 1;
	}
	
	/**
	 * @return the number of variables stored in this goods tree
	 */
	public int getNbrVariables() {
		return this.numberOfVariables;
	}

	/**
	 * If this good reports a value for an already confirmed assignment, the information in the good can be
	 * considered outdated and should be ignored
	 * @author Brammert Ottens, 4 sep 2009
	 * @param g			the good to be checked
	 * @param sender	the sender of the good
	 * @return	\c true when the assignment in the good already has a confirmed utility
	 */
	public boolean ignoreGood(Good<Val, U> g, int sender) {
		if(g.isConfirmed())
			return false;
		IntArrayWrapper key = ((IntArrayWrapper)toKey(g.getValues(), g.getVariables(), sender)).getPartialAssignment(this.childrenVariables[sender], this.separatorSizePerChild[sender]);
		Boolean b = goodsConfirmed.get(sender).get(key);
		return b != null && b; 
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTree.InnerNodeTree#addNewVariable(java.lang.String[], java.util.HashMap, int)
	 */
	@Override
	protected int[] addNewVariable(String[] allVariables, HashMap<String, Val> newVariables, int sender) {
		return super.addNewVariable(allVariables, newVariables, sender);
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
	@Override
	protected LeafNode<U> createLeaf(IntArrayWrapper currentPath, boolean real, frodo2.algorithms.odpop.Good<Val, U> g, int child, final boolean withUB) {

		LeafNode<U> leaf = leafNodeInstance.newInstance(numberOfChildren, powersOf2);
		boolean support = false;
		
		U localUtil = getUtilityLocalProblem(currentPath);
		if(localUtil == infeasibleUtil)
			return null;
		AddableDelayed<U> util = localUtil.addDelayed();
		U utility = g.getUtility();
		U confirmedUtil = localUtil;
		AddableDelayed<U> confirmedUtilDelayed = localUtil.addDelayed();

		if(this.storeReceivedGoods) {
			for(int i = 0; i < numberOfChildren; i++) {
				IntArrayWrapper array = currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]);
				U temp = goodsReceived.get(i).get(array);
				if (temp != null) {
					support = true;
					util.addDelayed(temp);

					if(goodsConfirmed.get(i).get(array)) {
						confirmedUtilDelayed.addDelayed(temp);
						leaf.counter--;
						assert leaf.counter >= 0;
						leaf.updateUB[i] = false;
					} 
				} 
			}
		} else {
			util.addDelayed(utility);
			if(((Good<Val, U>)g).isConfirmed()) {
				confirmedUtilDelayed.addDelayed(utility);
				leaf.counter--;
				leaf.updateUB[child] = false;
			}
			support = true;
		}
		
		confirmedUtil = confirmedUtilDelayed.resolve();
		
		if(!support)
			return null;

		int childrenCombination = LeafNode.fromBooleanArrayToInt(leaf.updateUB, powersOf2);
		leaf.setUbSum(childrenCombination);
		
		if(withUB) {
			if(childrenCombination == -1)
				leaf.setUB(confirmedUtil);
			else {
				leaf.setUB(confirmedUtil.add(upperBoundSums[childrenCombination]));
			}
		}

		leaf.real = real;
		if(withUB && leaf.getUB() == infeasibleUtil) {
			leaf.setUtil(infeasibleUtil);
		} else {
			leaf.setUtil(util.resolve());
		}

		leaf.confirmedUtil = confirmedUtil;
		leaf.setUpToDate(!withUB || greaterThanOrEqual(leaf.getUtil(), leaf.getUB()));
		
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
	@Override
	protected LeafNode<U> createLeaf(IntArrayWrapper currentPath, boolean real, final boolean withUB) {

		LeafNode<U> leaf = leafNodeInstance.newInstance(numberOfChildren, powersOf2);
		boolean support = false;
		
		U localUtil = getUtilityLocalProblem(currentPath);
		if(localUtil == infeasibleUtil)
			return null;
		AddableDelayed<U> util = localUtil.addDelayed();
		U confirmedUtil = localUtil;
		AddableDelayed<U> confirmedUtilDelayed = confirmedUtil.addDelayed();
		
		for(int i = 0; i < numberOfChildren; i++) {
			IntArrayWrapper array = currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]);
			U temp = goodsReceived.get(i).get(array);
			if (temp != null) {
				support = true;
				util.addDelayed(temp);

				if(goodsConfirmed.get(i).get(array)) {
					confirmedUtilDelayed.addDelayed(temp);
					leaf.counter--;
					assert leaf.counter >= 0;
					leaf.updateUB[i] = false;
				} 
			} 
		}
		confirmedUtil = confirmedUtilDelayed.resolve();
		if(!support)
			return null;

		int childrenCombination = LeafNode.fromBooleanArrayToInt(leaf.updateUB, powersOf2);
		leaf.setUbSum(childrenCombination);
		
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
			leaf.setUtil(util.resolve());
		}

		leaf.confirmedUtil = confirmedUtil;
		leaf.setUpToDate(!withUB || greaterThanOrEqual(leaf.getUtil(), leaf.getUB()));
		
		if(this.COLLECT_STATISTICS)
			this.countLeafNode(real);
		return leaf;
	}
	
	/**
	 * logs the reception of a domain size info message
	 */
	@Override
	protected void finalDomainSizeReceiver() {
		fullInfo = fullInfoCounter == 0 && this.finalDomainSizeUnknownVariables.size() == 0;
		if(fullInfo) {
			this.totalSeparatorSpaceSize = 1;
			for(int i = 0; i < numberOfVariables; i++) {
				totalSeparatorSpaceSize *= this.finalDomainSize[i];
				assert this.finalDomainSize[i] != 0;
			}
		}

	}
	
	/**
	 * Looks for parts of the tree where the local solution as defined by localPath can still be used
	 * to create new leafs
	 * 
	 * @author Brammert Ottens, 5 okt 2009
	 * @param depth			the current depth in the tree
	 * @param localPath		the path representing the local solution
	 * @param unusedPath	a path where the local solution has not yet been used
	 * @param currentNode	the current node being visited
	 * @return	\c true when there are unused parts of the tree, and \c false otherwise
	 */
	@SuppressWarnings("unchecked")
	protected boolean findUnused(int depth, int[] localPath, int[] unusedPath, InnerNode<U, LeafNode<U>> currentNode) {
		if(!currentNode.isAlive()) // none of the assignments will be used
			return false;
		
		if(depth == depthFinalVariable)
			return true;
		
		int localPathIndex = localPath[depth];
		int nextDepth = depth + 1;
		
		if(localPathIndex == -1) {
			int size = domainSize[depth];
			for(int i = 0; i < size; i++) {
				Node<U> node = currentNode.getChild(i);
				unusedPath[depth] = i;
				if(node == null || findUnused(nextDepth, localPath, unusedPath, (InnerNode<U, LeafNode<U>>)node))
					return true;
			}
			return false;
		} else {
			Node<U> node = currentNode.getChild(localPathIndex);
			if(node == null || findUnused(nextDepth, localPath, unusedPath, (InnerNode<U, LeafNode<U>>)node))
				return true;
			else
				return false;
		}
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
		if(hasLocalProblem)
			numberOfLocalVariables = numberOfVariables;
		
		if(numberOfVariables != 0) {
			upperBoundIsInfiniteCounter = numberOfChildren;
			upperBoundIsMinInfiniteCounter = 0;
			fullInfoCounter = numberOfChildren;
			dummyDepth = 0;
			upperboundArraySize = (int)Math.pow(2, numberOfChildren) - 1;;

			this.numberOfChildren = numberOfChildren;
			
			/* First, create a map from variable value combinations to positions in the tree*/
			int initialHashMapSize = (int)Math.ceil(numberOfVariables/0.75);
			variableToDepth = new HashMap<String, Integer>(initialHashMapSize);
			valuePointers = new HashMap<String, HashMap<Val, Integer>>(initialHashMapSize);
			domains = new HashMap<String, ArrayList<Val>>(initialHashMapSize);
			domainSize = new int[numberOfVariables];
			finalDomainSize = new int[numberOfVariables];
			finalDomainSizeUnknownVariables = new HashMap<String, Integer>();
			branchingFactor = new int[numberOfVariables];
			dummy = new boolean[numberOfVariables];
			childrenVariables = new boolean[numberOfChildren][numberOfVariables];
			childrenVariablesReportingOrder = new String[numberOfChildren][];
			ownVariables = new boolean [numberOfVariables];
			goodsConfirmed = new ArrayList<HashMap<IntArrayWrapper, Boolean>>(numberOfChildren);
			goodsReceived = new ArrayList<HashMap<IntArrayWrapper, U>>(numberOfChildren);
			upperBounds = (U[])Array.newInstance(zero.getClass(), numberOfChildren);
			separatorSizePerChild = new int[numberOfChildren];
			optimalLocalSolution = (Val[])new Addable[numberOfVariables];
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
			
			localOptions =(U[])new Addable[domainSize[depthFinalVariable]];
  			
			for(int i = 0; i < numberOfChildren; i++) {
				goodsConfirmed.add(new HashMap<IntArrayWrapper, Boolean>());
				goodsReceived.add(new HashMap<IntArrayWrapper, U>());
				childrenVariablesReportingOrder[i] = new String[1];
				childrenVariablesReportingOrder[i][0] = this.ownVariable;
				separatorSizePerChild[i] = 0;
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
	 * Given a point in the domain space of value combinations between 0 and max, the next point
	 * is calculated and position is set to this new point
	 * 
	 * @author Brammert Ottens, 19 jul 2009
	 * @param max			for each entry the maximal value
	 * @param position		the position in the domain space
	 */
	protected void nextVariableAssignment(int[] max, int[] position) {
		int length = position.length;
		int i = 0;
		position[0] = (position[0] + 1)%max[0];
		boolean carry = position[0] == 0;

		while(i + 1 < length) {
			i++;
			if(carry) {
				position[i] = (position[i] + 1)%max[i];
				carry = position[i] == 0;
			} else break;
		}
	}
	
	/**
	 * Returns the value of the trees own variable
	 * for the current next best assignment
	 *  
	 * @author Brammert Ottens, 10 nov 2009
	 * @param currentContext The current assignment of all variables in \c ownVariable's separator
	 */
	@SuppressWarnings("unchecked")
	public void ownVariableValue(HashMap<String, Val> currentContext) {
		InnerNode<U, LeafNode<U>> currentNode = (InnerNode<U, LeafNode<U>>)root;
		int i = 0;
		for(; i < this.depthOfFirstToBeRemovedVariables; i++) {
			currentNode = (InnerNode<U, LeafNode<U>>)currentNode.getChild(currentNode.getMaxChild());
		}
		
		for(; i < depthFinalVariable; i++) {
			int index = currentNode.getMaxChild();
			String var = depthToVariable[i];
			currentNode = (InnerNode<U, LeafNode<U>>)currentNode.getChild(index);
			currentContext.put(var, domains.get(var).get(index));
		}
		
		int index = currentNode.getMaxChild();
		String var = depthToVariable[i];
		currentContext.put(var, domains.get(var).get(index));
	}
	
	/**
	 * @see frodo2.algorithms.odpop.goodsTree.InnerNodeTree.InnerNodeTree#updateLocalProblem()
	 */
	@Override
	protected void updateLocalProblem() {
		if(!localProblemIterator.hasNext()) {
			this.localUpperBound = this.infeasibleUtil;
			this.optimalLocalUtility = this.infeasibleUtil;
			optimalLocalPath = null;
			optimalLocalSolution = null;
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
		
		if(assignment == null) {
			this.optimalLocalUtility = this.infeasibleUtil;
			optimalLocalPath = null;
			optimalLocalSolution = null;
		} else {
			this.optimalLocalUtility = utility;
			optimalLocalPath = path;
			System.arraycopy(assignment, 0, optimalLocalSolution, 0, numberOfLocalVariables);
		}
		
		assert optimalLocalPath == null || this.localPathAlive(0, root);
		assert optimalLocalSolution == null || !ownVariables[0] || checkGood(optimalLocalSolution);
		
		
		
		if(this.upperBoundIsInfiniteCounter == 0)
			localUpperBound = this.optimalLocalUtility.add(this.upperBoundSums[upperBoundSums.length-1]);
	}
	
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
	@Override
	public boolean checkLeaf(LeafNode<U> leaf, IntArrayWrapper currentPath,  boolean checkUB, U utility, int sender) {
		U UB = this.getUtilityLocalProblem(currentPath);
		U util = UB;
		U childUtil = zero;
		for(int i = 0; i < numberOfChildren; i++) {
			U temp = goodsReceived.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i]));
			if(temp != null) {
				childUtil = childUtil.add(temp);
				if(goodsConfirmed.get(i).get(currentPath.getPartialAssignment(childrenVariables[i], this.separatorSizePerChild[i])))
					UB = UB.add(temp);
				else if(upperBounds[i] != null)
					UB = UB.add(upperBounds[i]);				
			} else if(upperBounds[i] != null) { // this function should only be called when all the upperBounds are set!
				UB = UB.add(upperBounds[i]);
			}
		}
		
		util = util.add(childUtil);
		
//		assert !checkUB || greaterThanOrEqual(UB, leaf.getUB()) : UB.toString() + " vs. " + leaf.getUB();
//		assert !leaf.real || greaterThan(UB, util) || leaf.getUtil().equals(util) : UB.toString() + " vs. " + util + " OR " + leaf.getUtil() + " != " + util;	// the utility should be correct
//		assert !checkUB || leaf.counter != 0 || leaf.getUB().equals(leaf.getUtil());					// if the assignment is confirmed, UB should be equal to utility
		
		return true;
	}
	
	/**
	 * Method used to check whether the assignment to be reported by the good
	 * point to an existing assignment
	 * @author Brammert Ottens, 1 nov 2009
	 * @param values the values constituting the assigment
	 * @return \c true when the assignment exists and false otherwise
	 */
	@SuppressWarnings("unchecked")
	private boolean checkGood(Val[] values) {
		int[] path = new int[values.length];
		for(int i = 0; i < values.length; i++) {
			path[i] = this.valuePointers.get(depthToVariable[i]).get(values[i]);
		}

		Node<U> currentNode = root;
		int depth = 0;
		while(depth < this.depthOfFirstToBeRemovedVariables) {
			assert currentNode.isAlive();
			currentNode = ((InnerNode< U, LeafNode<U> >)currentNode).getChild(path[depth]);
			if(currentNode == null)
				return true;
			depth++;
		}

		assert currentNode.isAlive();
		return true;
	}
}
