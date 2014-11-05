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

package frodo2.algorithms.odpop.goodsTree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import frodo2.algorithms.odpop.Good;
import frodo2.algorithms.odpop.goodsTree.InnerNodeTreeFullDomain.Node;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst;

/**
 * @author Brammert Ottens, 9 nov 2009
 * 
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * @param <L> type used for the leaf node
 */
public abstract class GoodsTree <Val extends Addable<Val>, U extends Addable<U>, L extends Node<U>> implements Serializable {

	/** Used for serialization */
	private static final long serialVersionUID = -4177378479261482364L;
	
	/** \c true when statistics should be collected, and \c false otherwise */
	public final boolean COLLECT_STATISTICS;

	/** The list of spaces this variable is responsible for */
	protected UtilitySolutionSpace<Val, U> localProblem;
	
	/** The number of local variables in the local problem */
	protected int numberOfLocalVariables;
	
	/** Iterator that returns solutions to the local problem in a best first order */
	protected IteratorBestFirst<Val, U> localProblemIterator;

	/** The -infinite utility */
	protected final U infeasibleUtil;

	/** The zero utility */
	protected final U zero;

	/** The number of spaces that together comprise the local problem */
	protected int numberOfSpaces;
	
	/** domain element used for reflection*/
	protected Class<?> domainElementClass;
	
	/** The number of variables in the local problem */
	protected int numberOfVariables;

	/** Links a level in the tree with its corresponding variable. */
	protected String[] depthToVariable;
	
	/** The variable that controls this LeafNodeTree */
	protected String ownVariable;
	
	/** The depth of the last variable in the tree */
	protected int depthFinalVariable;
	
	/** The domain of \c ownVariable */
	protected Val[] ownVarDomain;
	
	/** The variables as they should be communicated with the outside world.
	 *  Given a reported good, the parent should be able to calculate this
	 *  array as well! */
	protected String[] outsideVariables;
	
	/**
	 * Contains the indices of the values that need to be removed from the 
	 * assigment before being send upwards
	 */
	protected int[] valuesToBeRemoved;
	
	/** All variables below this depth must be removed when sending a good */
	protected int depthOfFirstToBeRemovedVariables;
	
	/** A mapping from outside variables to inside variables */
	protected HashMap<String, Integer> outsideVariablesMapping;
	
	/** \c true when we are maximizing, and false otherwise */
	protected final boolean maximize;
	
	// statistics
	
	/** Used to collect statistics about the sparsity of the tree */
	private long numberOfLeafNodes;
	
	/** Used to collect statistics about the number of dummynodes in the tree */
	private long numberOfDummyLeafNodes;
	
	/** The size of the separator space*/
	protected long totalSeparatorSpaceSize = 1;
	
	/** counts the number of goods that have been send */
	private long numberOfGoodsProduced;
	
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
	public GoodsTree(String ownVariable, Val[] ownVariableDomain, UtilitySolutionSpace<Val, U> space, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) {
		COLLECT_STATISTICS = collectStats;
		List<UtilitySolutionSpace<Val, U>> spaces = new ArrayList<UtilitySolutionSpace<Val, U>>(1);
		spaces.add(space);
		numberOfSpaces = 1;
		this.infeasibleUtil = infeasibleUtil;
		this.zero = zero;
		this.maximize = maximize;
		this.domainElementClass = ownVariableDomain[0].getClass();
		
		this.init(ownVariable, ownVariableDomain, zero, numberOfChildren, spaces);
	}
	
	/**
	 * A constructor 
	 * @param ownVariable			The variable that owns this tree
	 * @param ownVariableDomain 	The domain of \c ownVariable
	 * @param spaces				The space controlled by this tree
	 * @param zero 					The zero utility
	 * @param numberOfChildren 		The number of children of this tree
	 * @param infeasibleUtil 		The infeasible utility
	 * @param maximize 				when \c true we are maximizing, when \c false we are minimizing\
	 * @param collectStats			\c true when statistics should be collected, and \c false otherwise
	 */
	public GoodsTree(String ownVariable, Val[] ownVariableDomain, List<UtilitySolutionSpace<Val, U>> spaces, U zero, int numberOfChildren, U infeasibleUtil, boolean maximize, boolean collectStats) {
		COLLECT_STATISTICS = collectStats;
		numberOfSpaces = spaces.size();
		this.infeasibleUtil = infeasibleUtil;
		this.zero = zero;
		this.maximize = maximize;
		this.domainElementClass = ownVariableDomain[0].getClass();
		
		this.init(ownVariable, ownVariableDomain, zero, numberOfChildren, spaces);
	}
	
	/**
	 * Adds a good to the tree
	 * 
	 * @author Brammert Ottens, 10 nov 2009
	 * @param g			the good to be added
	 * @param sender	the child that reported the good
	 * @param domains	reported variable domains
	 * @return	\c true when a new variable has been added, and false otherwise 
	 */
	public abstract boolean add(Good<Val, U> g, int sender, HashMap<String, Val[]> domains);

	/**
	 * This method obtains the aMax. If aMax is a confirmed assignment, i.e. no other assignment
	 * will ever have a higher utility, then the path belonging to aMax must be removed. 
	 * @return the aMax
	 */
	public abstract Good<Val, U> getAmax();

	/**
	 * Given the assignment of variables in its separator, this method updates the context with
	 * the best value for the variable that owns the tree
	 * 
	 * @param context A collection of assignments of variables in the separator
	 */
	public abstract void getBestAssignmentForOwnVariable(HashMap<String, Val> context);

	/**
	 * Initializes the separator of a child. This method should only be called when one receives the first message from a particular
	 * child. After that, the process of adding new variables should take care of the rest.
	 * @param child			the child whose separator must be set
	 * @param variables		the variables in its separator
	 */
	public abstract void setChildrenSeparator(int child, String[] variables);
	
	/**
	 * Returns the variables in the childs separator in the order it reported them
	 * 
	 * @author Brammert Ottens, 19 aug 2009
	 * @param child	the child who's separator we want to know
	 * @return	the separator variables of the child
	 */
	public abstract String[] getChildSeparatorReportingOrder(int child);
	
	/**
	 * Given a map that maps variables to values, this method returns the values
	 * belonging to the variables in a child's separator
	 * @author Brammert Ottens, 19 aug 2009
	 * @param parentContext	the value map
	 * @param child	the child for who we want to know the separator values
	 * @return	an array containing the values of the child's separator variables
	 */
	public abstract HashMap<String, Val> getChildValues(HashMap<String, Val> parentContext, int child);
	
	/**
	 * Method to obtain the information on the final domain size
	 * @return an array containing domain size information per variable
	 */
	public abstract int[] getFinalDomainSize();
	
	/**
	 * @author Brammert Ottens, 25 feb 2010
	 * @return the domains of the variables known to this variable
	 */
	public abstract Val[][] getDomains();

	/**
	 * @author Brammert Ottens, 4 dec 2009
	 * @return the percentage of the tree that has been filled
	 */
	public double getTreeFillPercentage() {
		return ((double)this.numberOfLeafNodes)/this.totalSeparatorSpaceSize;
	}

	/** @return thte total number of dummies created*/
	public long getNumberOfDummies() {
		return this.numberOfDummyLeafNodes;
	}
	
	/** @return the percentage of dummies created */
	public double getDummiesFillPercentage() {
		return ((double)this.numberOfDummyLeafNodes)/this.totalSeparatorSpaceSize;
	}
	
	/**
	 * Compares two values
	 * @author Brammert Ottens, 30 apr 2010
	 * @param u1	value 1
	 * @param u2	value 2
	 * @return if we are maximizing \c true when u1 <= u2, if we are minimizing \c true when u2 <= u1
	 */
	public boolean greaterThanOrEqual(U u1, U u2) {
		if(maximize)
			return u1.compareTo(u2) <= 0;
		else
			return u2.compareTo(u1) <= 0;
	}
	
	/**
	 * Compares two values
	 * @author Brammert Ottens, 30 apr 2010
	 * @param u1	value 1
	 * @param u2	value 2
	 * @return if we are maximizing \c true when u1 <== u2, if we are minimizing \c true when u2 < u1
	 */
	public boolean greaterThan(U u1, U u2) {
		if(maximize)
			return u1.compareTo(u2) < 0;
		else
			return u2.compareTo(u1) < 0;
	}
	
	/**
	 * @return returns true when domain information has been received from all children
	 */
	public abstract boolean hasFullInfo();

	/**
	 * Used to determine whether there are any unsent assignments in the tree
	 * @return true when there are assignments left, and false otherwise
	 */
	public abstract boolean hasMore();

	/**
	 * @return Returns true when valuation sufficient and false otherwise
	 */
	public abstract boolean isValuationSufficient();

	/**
	 * @param variable 		The variable for which we want to know whether this tree is aware of it
	 * @return \c true if this tree is familier with this variable
	 */
	public abstract boolean knowsVariable(String variable);

	/**
	 * If there is only one variable in this tree, that means that there is not
	 * enough information to send a good
	 * @return boolean
	 */
	public abstract boolean notEnoughInfo();

	/**
	 * Removes Amax from the tree
	 * @author Brammert Ottens, 30 sep 2009
	 */
	public abstract void removeAMax();

	/**
	 * If a child has send a DONE message, the upper bound
	 * should be set to -infinity
	 * @author Brammert Ottens, 30 sep 2009
	 * @param child	the child that sent the DONE message
	 * @return \c true when the remaining local problem became infeasible, and \c false otherwise
	 */
	public abstract boolean setChildDone(int child);
	
	/**
	 * Sets the final domain size of the given variables
	 * 
	 * @author Brammert Ottens, 26 jun 2009
	 * @param variables		the variables whose final domain sizes are known
	 * @param domainSize	the domain sizes of the variables given in \c variables
	 */
	public abstract void setFinalDomainSize(String[] variables, int[] domainSize);

	/**
	 * @return returns true when domain information must still be send
	 */
	public abstract boolean stillToSend();

	/**
	 * Tests whether a path, given by an array of value assignments,
	 * exists in the tree. Note that this method assumes that position in the array
	 * corresponds to the depth of the corresponding variable
	 * 
	 * @author Brammert Ottens, 29 jun 2009
	 * @param values	the value assignments
	 * @return			\c true when the path exists, and \c false otherwise
	 */
	public abstract boolean pathExists(Val[] values);
	
	/**
	 * @author Brammert Ottens, 28 jan. 2011
	 * @return the maximal value with which the utility had to be cut to guarantee best first order
	 */
	public U getMaximalCut() {
		if(localProblem != null)
			return this.localProblemIterator.maximalCut();
		else
			return this.infeasibleUtil.getZero();
	}
	
	/**
	 * Method to initialize all the variables
	 * 
	 * @author Brammert Ottens, 12 nov 2009
	 * @param ownVariable			The variable that owns this tree
	 * @param ownVariableDomain 	The domain of the own variable
	 * @param zero					The zero utility
	 * @param numberOfChildren		The number of children of this tree
	 * @param spaces				The local problem
	 */
	@SuppressWarnings("unchecked")
	private void init(String ownVariable, Val[] ownVariableDomain, U zero, int numberOfChildren, List<UtilitySolutionSpace<Val, U>> spaces) {
		this.ownVariable = ownVariable;
		this.totalSeparatorSpaceSize = ownVariableDomain.length;
		
		ArrayList<String> outsideVariables = new ArrayList<String>(1);
		HashSet<String> addedVariables = new HashSet<String>();
		ArrayList<String> insideVariables = new ArrayList<String>(1);
		HashSet<String> variablesToBeRemoved = new HashSet<String>(1);
		
		if(this.numberOfSpaces > 0) {
			UtilitySolutionSpace<Val, U>[] array = new UtilitySolutionSpace[numberOfSpaces - 1];
			localProblem = spaces.remove(0);
						
			for(String v : localProblem.getVariables())
				if(!v.equals(ownVariable) && !addedVariables.contains(v)) {
					addedVariables.add(v);
					insideVariables.add(v);
					outsideVariables.add(v);
				}
			
			int i = 0;
			for(UtilitySolutionSpace<Val, U> space : spaces) {
				array[i] = space;
				for(String v : space.getVariables())
					if(!v.equals(ownVariable) && !addedVariables.contains(v)) {
						addedVariables.add(v);
						insideVariables.add(v);
						outsideVariables.add(v);
					}

				i++;
			}
			
			insideVariables.addAll(variablesToBeRemoved);
			insideVariables.add(ownVariable);
			depthToVariable = insideVariables.toArray(new String[0]);
			this.outsideVariables = outsideVariables.toArray(new String[0]);
			numberOfVariables = depthToVariable.length;
			numberOfLocalVariables = numberOfVariables;
			
			if(numberOfVariables == 1 && numberOfChildren == 0) {
				depthFinalVariable = 1;
				depthOfFirstToBeRemovedVariables = 1;
			} else {
				depthFinalVariable = numberOfVariables - 1;
				depthOfFirstToBeRemovedVariables = numberOfVariables - variablesToBeRemoved.size() - 1;
			}
			
			localProblem = localProblem.join(array);
			if(localProblem == null) {
				numberOfVariables = 1;
				depthToVariable = new String[1];
				depthToVariable[0] = ownVariable;
				ownVarDomain = ownVariableDomain;
				this.outsideVariables = new String[0];				
			} else {
				localProblem = localProblem.changeVariablesOrder(depthToVariable);
				this.ownVarDomain = ownVariableDomain;
			}
		} else {
			numberOfVariables = 1;
			depthToVariable = new String[1];
			depthToVariable[0] = ownVariable;
			ownVarDomain = ownVariableDomain;
			this.outsideVariables = new String[0];
		}
	}

	/**
	 * Method used to solve the local problem and create an ordered list of all
	 * feasible assignments
	 * 
	 * @author Brammert Ottens, 9 nov 2009
	 */
	protected void solveLocalProblem() {
		localProblemIterator = localProblem.iteratorBestFirst(maximize);
		assert localProblemIterator != null;
	}
	
	/**
	 * Method used to count the number of leaf nodes created (includes the dummy nodes)
	 * @author Brammert Ottens, 21 okt 2010
	 */
	protected void countLeafNode() {
		assert this.COLLECT_STATISTICS;
		this.numberOfLeafNodes++;
	}
	
	/**
	 * Method used to count the number of leaf nodes created (includes the dummy nodes)
	 * @author Brammert Ottens, 21 okt 2010
	 * @param real if \c true the leaf is a real leafnode, otherwise it is a dummy node
	 * 
	 */
	protected void countLeafNode(boolean real) {
		assert this.COLLECT_STATISTICS;
		this.numberOfLeafNodes++;
		if(!real)
			this.numberOfDummyLeafNodes++;
	}
	
	/**
	 * Count rhe number of goods produced
	 * @author Brammert Ottens, 8 dec 2010
	 */
	protected void countGoodsProduced() {
		assert this.COLLECT_STATISTICS;
		this.numberOfGoodsProduced++;
	}
	
	/**
	 * @author Brammert Ottens, 8 dec 2010
	 * @return the number of goods that have been sent
	 */
	public long getNumberOfGoodsSent() {
		return this.numberOfGoodsProduced;
	}
	
	/**
	 * @author Brammert Ottens, 8 dec 2010
	 * @return the size of the separator space
	 */
	public long getSizeOfSpace() {
		return this.totalSeparatorSpaceSize;
	}

	/**
	 * Convenience class to store assignment/utility combinations in a priority queue
	 * 
	 * @author Brammert Ottens, 9 nov 2009
	 *
	 * @param <Val> type used for variable values
	 * @param <U> type used for utility values
	 */
	protected static class Assignment <Val extends Addable<Val>, U extends Addable<U>> implements Comparable<Assignment<Val, U>> {

		/** The assignment */
		public Val[] assignment;

		/** The utility belonging to the assignment */
		public U utility;
		
		/** when \c true we are maximizing, when \c false we are minimizing */
		private final boolean maximize;

		/**
		 * Constructor 
		 * @param assignment The assignment
		 * @param utility	 The utility belonging to the assignment
		 * @param maximize	 when \c true we are maximizing, when \c false we are minimizing
		 */
		public Assignment(Val[] assignment, U utility, boolean maximize) {
			assert assignment[0] != null;
			assert utility != null;
			this.assignment = assignment;
			this.utility = utility;
			this.maximize = maximize;
		}

		/**
		 * Compares to assignments with each other, based on the utilty belonging to the assignment
		 * @param a the assignment to which this assignment must be compared
		 * @return the difference between a.utility and this assignments utility
		 */
		public int compareTo(Assignment<Val, U> a) {
			return maximize ? a.utility.compareTo(utility) : utility.compareTo(a.utility);
		}

		/**
		 * Prints the information in the assignment
		 *  
		 * @author Brammert Ottens, 10 nov 2009
		 * @param vars the variables to which this assignment assigns values
		 * @return the string representation of this class
		 */
		public String toString(String[] vars) {
			String str = "[";
			int i = 0;
			for(; i < assignment.length - 1; i++)
				str += vars[i] + " = " + assignment[i] + ", ";
			str += vars[i] + " = " + assignment[i] + "]";
			str += " = " + utility;

			return str;
		}
	}

}