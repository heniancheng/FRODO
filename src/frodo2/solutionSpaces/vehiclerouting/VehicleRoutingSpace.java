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

package frodo2.solutionSpaces.vehiclerouting;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpaceLimited;
import frodo2.solutionSpaces.UtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.BlindProjectOutput;
import frodo2.solutionSpaces.hypercube.ExpectationOutput;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.JoinOutputHypercube;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;
import frodo2.solutionSpaces.hypercube.ScalarSpaceIter;
import frodo2.solutionSpaces.hypercube.Hypercube.NullHypercube;
import com.orllc.orobjects.lib.graph.DuplicateVertexException;
import com.orllc.orobjects.lib.graph.PointGraph;
import com.orllc.orobjects.lib.graph.VertexNotFoundException;
import com.orllc.orobjects.lib.graph.tsp.TwoOpt;
import com.orllc.orobjects.lib.graph.vrp.BestOf;
import com.orllc.orobjects.lib.graph.vrp.ClarkeWright;
import com.orllc.orobjects.lib.graph.vrp.Composite;
import com.orllc.orobjects.lib.graph.vrp.GillettMiller;
import com.orllc.orobjects.lib.graph.vrp.ImproveI;
import com.orllc.orobjects.lib.graph.vrp.ImproveWithTSP;
import com.orllc.orobjects.lib.graph.vrp.SolutionNotFoundException;
import com.orllc.orobjects.lib.graph.vrp.VRPException;

/** A solution space for Vehicle Routing Problems
 * @author Thomas Leaute
 * @param <U> the type used for utility values (default is AddableReal)
 */
public class VehicleRoutingSpace < U extends Addable<U> > implements UtilitySolutionSpace<AddableInteger, U> {

	/** Used for serialization */
	private static final long serialVersionUID = 6651845834019201776L;
	
	/** Iterator */
	private class VRPiterator extends ScalarSpaceIter<AddableInteger, U> {
		
		/** The optimal cost for serving only the already selected customers */
		final private U minCost;
		
		/** Constructor
		 * @param variables 	variables over which to iterate
		 * @param domains 		the domains for the iteration variables
		 * @param skippedUtil 	the utility value to skip, if any
		 */
		public VRPiterator(String[] variables, AddableInteger[][] domains, U skippedUtil) {
			super (infeasibleUtil, variables, domains, infeasibleUtil, skippedUtil);

			// Check which customers must be served
			HashSet<Customer> toBeServed = new HashSet<Customer> (selectedCustomers);
			for (int i = variables.length - 1; i >= 0; i--) {
				String var = variables[i];
				
				// Check if this is a decision variable for a known customer
				Customer customer = customers.get(var);
				if (customer != null) {
					
					// Skip this customer if her position is unknown
					if (uncertainties.values().contains(customer)) 
						continue;
					
					// Skip this customer if we don't know whether we must serve her
					AddableInteger firstChoice = domains[i][0];
					if (firstChoice.equals(zero)) 
						continue;
					
					if (splitDeliveries) 
						customer.demand = firstChoice.intValue();
					toBeServed.add(customer);
					
				}
			}

			this.minCost = getUtility (toBeServed);
		}

		/** @see ScalarSpaceIter#nextSolution() */
		@Override
		public AddableInteger[] nextSolution() {
			
			AddableInteger[] sol = this.nextSolBlind();
			
			final U inf = this.skippedUtil;
			if (inf != null) 
				while (inf.equals(this.getCurrentUtility())) 
					this.nextSolBlind();
			
			return sol;
		}
		
		/** @return the next solution, regardless of whether it is feasible or not */
		private AddableInteger[] nextSolBlind () {
			
			// Return null if there are no more solutions
			if (this.nbrSolLeft <= 0) {
				this.utility = null;
				this.solution = null;
				return null;
			}
			
			this.iter();
			
			// Don't compute the new utility; only compute it if it is needed, i.e. inside getCurrentUtility() 
			super.setCurrentUtility(null);
			
			return this.solution;
		}
		
		/** @see ScalarSpaceIter#nextUtility() */
		@Override
		public U nextUtility() {
			
			AddableInteger[] solution = this.nextSolution();
			
			// Check if there are no more solutions
			if (solution == null) 
				return null;
			
			return this.getCurrentUtility();
		}
		
		/** @see Iterator#nextUtility(java.lang.Object, boolean) */
		@Override
		public U nextUtility(U bound, final boolean minimize) {
			
			// Skip all remaining solutions if the cost must be lower than the minCost
			if (minimize && bound.compareTo(minCost) <= 0) {
				super.nbrSolLeft = 0;
				super.utility = null;
				super.solution = null;
				return null;
			}
			
			U util;
			
			if (minimize) {
				while ( (util = this.nextUtility()) != null) {
					if (util.compareTo(bound) < 0) {
						return util;
					}
				}
			} else  { // maximizing
				while ( (util = this.nextUtility()) != null) {
					if (util.compareTo(bound) > 0) {
						return util;
					}
				}
			}
			
			return null;
		}

		/** @see ScalarSpaceIter#getCurrentUtility() */
		@Override
		public U getCurrentUtility() {
			
			// Return the utility if it has already been computed
			U util = super.getCurrentUtility();
			if (util != null) 
				return util;
			
			// Return null if we ran out of solutions
			AddableInteger[] solution = super.getCurrentSolution();
			if (solution == null) 
				return null;

			// Compute and return the utility for the current solution
			util = getUtility(super.getVariablesOrder(), solution);
			super.setCurrentUtility(util);
			return util;
		}

		/** @see Iterator#getCurrentUtility(java.lang.Object, boolean) */
		@Override
		public U getCurrentUtility(U bound, final boolean minimize) {
			
			// We can spare a constraint check if the cost must be lower than minCost
			if (minimize && bound.compareTo(minCost) <= 0) 
				return minCost;
			
			return this.getCurrentUtility();
		}
	}
	
	/**
	 * Best first iterator. The best solution is the one
	 * where no customer is served. 
	 * 
	 * The generator is based on the idea that a solution serving
	 * a set of customers A will always be better than any solution
	 * whose set of customers is a proper superset of A. Thus, whenever
	 * a solution s is released, a new set of solutions is generated, based
	 * on s, where a customer is added.
	 * 
	 * @author Brammert Ottens, 28 apr 2010
	 *
	 */
	private class VRPiteratorBestFirst implements IteratorBestFirst<AddableInteger, U> {

		/** Variables over which to iterate */
		private String[] variables;
		
		/** For each variable, when true, it's value is fixed */
		private boolean[] fixed;
		
		/** The domains for the iteration variables */
		private AddableInteger[][] domains;
		
		/** The current solution */
		private AddableInteger[] currentSolution;
		
		/** The utility of the current solution */
		private U currentUtility;
		
		/** The set of assignments already */
		private PriorityQueue<CustomerAssignment<U>> orderedAssignments;
		
		/** Map of already generated assignments */
		private HashSet<CustomerAssignment<U>> alreadyGenerated;
		
		/** All variables are binary, and will be either 0 or 1*/
		private AddableInteger one = new AddableInteger(1);
		
		/** The space over which we are iterating*/
		private VehicleRoutingSpace<U> space;
		
		/** The number of solutions left to iterate over*/
		private long numberOfSolutionsLeft;
		
		/** The total number of solutions */
		private final long nbrSolutions;
		
		/**
		 * Constructor
		 * 
		 * @param variables		variables over which to iterate
		 * @param domains		domains for the iteration variables
		 * @param space			the space over which to iterate
		 */
		public VRPiteratorBestFirst(String[] variables, AddableInteger[][] domains, VehicleRoutingSpace<U> space) {
			this.variables = variables;
			this.domains = domains;
			this.space = space;
			numberOfSolutionsLeft = (long)(Math.pow(2, variables.length));
			this.nbrSolutions = this.numberOfSolutionsLeft;
			fixed = new boolean[variables.length];
			alreadyGenerated = new HashSet<CustomerAssignment<U>>();
			
			
			// the best assignment is the assignment in which none of the customers are
			// served
			AddableInteger[] emptyAssignment = new AddableInteger[variables.length];
			Arrays.fill(emptyAssignment, new AddableInteger(0));
			CustomerAssignment<U> emptyCustomerAssignment = new CustomerAssignment<U>(emptyAssignment, space.getUtility(emptyAssignment));
			
			// add the empty assignment to the priority queue
			orderedAssignments = new PriorityQueue<CustomerAssignment<U>>();
			orderedAssignments.add(emptyCustomerAssignment);
		}
		/**
		 * Constructor
		 * 
		 * @param variables			variables over which to iterate
		 * @param domains			domains for the iteration variables
		 * @param space				the space over which to iterate
		 * @param fixedVariables 	the variables who's values should be fixed
		 * @param fixedValues 		the values of the fixed variables
		 */
		public VRPiteratorBestFirst(String[] variables, AddableInteger[][] domains, VehicleRoutingSpace<U> space, String[] fixedVariables, AddableInteger[] fixedValues) {
			this.variables = variables;
			this.domains = domains;
			this.space = space;
			numberOfSolutionsLeft = (long)(Math.pow(2, variables.length));
			fixed = new boolean[variables.length];
			alreadyGenerated = new HashSet<CustomerAssignment<U>>();
			
			// the best assignment is the assignment in which none of the customers are
			// served
			AddableInteger[] emptyAssignment = new AddableInteger[this.variables.length];
			Arrays.fill(emptyAssignment, new AddableInteger(0));
			int counter = 0;
			
			for(int i = 0; i < fixedVariables.length; i++) {
				String var = fixedVariables[i];
				for(int j = 0; j < this.variables.length; j++) {
					if(var.equals(this.variables[j])) {
						emptyAssignment[j] = fixedValues[i];
						fixed[j] = true;
						counter++;
					}
				}
			}
			
			this.numberOfSolutionsLeft = (long)(Math.pow(2, this.variables.length - counter));
			this.nbrSolutions = this.numberOfSolutionsLeft;
			
			CustomerAssignment<U> emptyCustomerAssignment = new CustomerAssignment<U>(emptyAssignment, space.getUtility(emptyAssignment));
			// add the empty assignment to the priority queue
			orderedAssignments = new PriorityQueue<CustomerAssignment<U>>();
			orderedAssignments.add(emptyCustomerAssignment);
		}
		
		
		/** 
		 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#getCurrentUtility()
		 */
		public U getCurrentUtility() {
			return currentUtility;
		}

		/** @see frodo2.solutionSpaces.UtilitySolutionSpace.Iterator#getCurrentUtility(java.lang.Object, boolean) */
		public U getCurrentUtility(U bound, final boolean minimize) {
			return this.getCurrentUtility();
		}
		
		/** 
		 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#nextUtility()
		 */
		public U nextUtility() {
			iter();
			assert currentSolution != null;
			return currentUtility;
		}

		/** @see frodo2.solutionSpaces.UtilitySolutionSpace.Iterator#nextUtility(java.lang.Object, boolean) */
		public U nextUtility(U bound, final boolean minimize) {
			
			if (minimize) {
				while (this.hasNext()) {
					if (this.nextUtility().compareTo(bound) < 0) {
						return this.getCurrentUtility();
					}
				}
			} else  { // maximizing
				while (this.hasNext()) {
					if (this.nextUtility().compareTo(bound) > 0) {
						return this.getCurrentUtility();
					}
				}
			}
			
			return null;
		}
		
		/** 
		 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator#setCurrentUtility(java.lang.Object)
		 */
		public void setCurrentUtility(U util) {
			// @todo Auto-generated method stub
			assert false : "Not Implemented";
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#getCurrentSolution()
		 */
		public AddableInteger[] getCurrentSolution() {
			return currentSolution;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#getDomains()
		 */
		public AddableInteger[][] getDomains() {
			return domains;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#getNbrSolutions()
		 */
		public long getNbrSolutions() {
			return this.nbrSolutions;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#getVariablesOrder()
		 */
		public String[] getVariablesOrder() {
			return variables;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return this.numberOfSolutionsLeft > 0;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#nextSolution()
		 */
		public AddableInteger[] nextSolution() {
			iter();
			
			return currentSolution;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#update()
		 */
		public void update() {
			// @todo Auto-generated method stub
			assert false : "Not Implemented";			
		}
		
		/**
		 * Generates the next solution
		 * 
		 * @author Brammert Ottens, 28 apr 2010
		 */
		private void iter() {
			numberOfSolutionsLeft--;
			CustomerAssignment<U> newAssignment = orderedAssignments.poll();
			
			currentSolution = newAssignment.getAssignment();
			currentUtility = newAssignment.getUtility();
			
			ArrayList<CustomerAssignment<U>> newAssignments = newAssignment.generateAssignmentList(zero, one, fixed);
			
			for(CustomerAssignment<U> ass : newAssignments) {
				if(alreadyGenerated.add(ass)) {
					ass.setUtility(space);
					orderedAssignments.add(ass);
				}
			}
		}

		/** @see frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst#maximalCut() */
		public U maximalCut() {
			return this.space.infeasibleUtil.getZero();
		}
	}
	
	/**
	 * Convenience class used by the iterator.
	 * It is outside and static to reduce memory consumption.
	 *
	 * @author Brammert Ottens, 27 apr 2010
	 * 
	 * @param <U>	the type of the utility values
	 */
	private static class CustomerAssignment < U extends Addable<U> > implements Comparable<CustomerAssignment<U>> {
		
		/** A customer assignment */
		private AddableInteger[] assignment;
		
		/** The utility of the assignment */
		private U utility;
		
		/**
		 * Constructor
		 * 
		 * @param assignment	a customer assignment
		 */
		public CustomerAssignment(AddableInteger[] assignment) {
			this(assignment, null);
		}
		
		/**
		 * Constructor
		 * 
		 * @param assignment	a customer assignment
		 * @param utility 		the corresponding utility
		 */
		public CustomerAssignment(AddableInteger[] assignment, U utility) {
			this.assignment = assignment;
			this.utility = utility;
		}
		
		/**
		 * Calculate the utility of the assignment
		 * 
		 * @author Brammert Ottens, 4 mei 2010
		 * @param space the space used to calculate the utility
		 */
		public void setUtility(VehicleRoutingSpace<U> space) {
			assert utility == null;
			utility = space.getUtility(assignment);
		}

		/**
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object o) {
			
			if (this == o) 
				return true;
			
			CustomerAssignment<U> a = (CustomerAssignment<U>)o;
			if(assignment.length != a.assignment.length)
				return false;
			
			for(int i = 0; i < assignment.length; i++)
				if(!assignment[i].equals(a.assignment[i]))
					return false;
			
			return true;
		}
		
		/**
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			int code = 0;
			int powerOf2 = 1;
			for(int i = 0; i < assignment.length; i++, powerOf2 *= 2)
				code += powerOf2 * assignment[i].hashCode();
			return code;
		}
		
		/** 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(CustomerAssignment<U> o) {
			return utility.compareTo(o.utility);
		}
		
		/**
		 * @author Brammert Ottens, 28 apr 2010
		 * @return the assignment
		 */
		public AddableInteger[] getAssignment() {
			return this.assignment;
		}
		
		/**
		 * @author Brammert Ottens, 28 apr 2010
		 * @return the utility of the assignment
		 */
		public U getUtility() {
			return this.utility;
		}
			
		/**
		 * Method to generate a batch of new customer assignments
		 * based on this assignment. Every new assignment is equal 
		 * to the old one, safe that one previously unserved customer
		 * is added
		 * 
		 * @author Brammert Ottens, 28 apr 2010
		 * @param zero		the domain value zero
		 * @param one		the domain value one
		 * @param fixed		tells this method which variables are not to be changed
		 * 
		 * @return a set of new customer assignments
		 */
		public ArrayList<CustomerAssignment<U>> generateAssignmentList(AddableInteger zero, AddableInteger one, boolean[] fixed) {
			
			// determine the number of customers that are not yet serviced
			/// @todo This seems useless. 
			int countZeros = 0;
			int size = assignment.length;
			boolean[] flippable = new boolean[size];
			for(int i = 0; i < size; i++) {
				if(!fixed[i] && assignment[i].equals(zero)) {
					countZeros++;
					flippable[i] = true;
				}
			}
			
			// generate the new assignments
			ArrayList<CustomerAssignment<U>> newAssignments = new ArrayList<CustomerAssignment<U>>(countZeros);
			int index = 0;
			for(int i = 0; i < countZeros; i++) {
				AddableInteger[] newAssignment = new AddableInteger[size];
				System.arraycopy(assignment, 0, newAssignment, 0, size);
				while(!flippable[index])
					index++;
				
				// generate a new assignment by adding an extra customer.
				newAssignment[index++] = one;
				newAssignments.add(new CustomerAssignment<U>(newAssignment));
			}
			
			return newAssignments;
		}
		
		/**
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			String str = Arrays.toString(this.assignment);
			str += " " + utility + " " + this.hashCode();
			return str;
		}
		
	}
	
	/** The name of this constraint */
	protected String name;
	
	/** The number of vehicles available to this depot */
	final protected int nbrVehicles;
	
	/** The maximum distance each vehicle is allowed to travel */
	final protected float maxDist;
	
	/** The maximum load each vehicle is allowed to carry */
	final protected int maxLoad;
	
	/** The total maximum load across all vehicles */
	protected final int totalMaxLoad;

	/** The X coordinate of the depot */
	protected final float depotX;
	
	/** The Y coordinate of the depot */
	protected final float depotY;
	
	/** The customers, indexed by their variable name */
	private HashMap<String, Customer> customers;
	
	/** Customers that the depot is required to serve */
	private HashSet<Customer> selectedCustomers;
	
	/** The total load of the selected customers */
	private int selectedLoad;
	
	/** The constraint's variables */
	private String[] vars;

	/** The infeasible utility */
	protected U infeasibleUtil;

	/** The VRP solver */
	protected Composite solver;

	/** Whether orders can be split among depots */
	private final boolean splitDeliveries;
	
	/** The domain value indicating that the customer should not be served */
	static final private AddableInteger zero = new AddableInteger (0);
	
	/** The domain value indicating that the customer should be served */
	static final private AddableInteger one = new AddableInteger (1);
	
	/** The domains of the variables */
	private AddableInteger[][] doms;

	/** For each random variable, the customer it refers to */
	private HashMap<String, Customer> uncertainties;

	/** The minimum split size (no split if <= 0) */
	private final int minSplit;

	/** The problem to be notified of constraint checks */
	protected ProblemInterface<AddableInteger, U> problem;

	/** The owner of this space */
	private String owner;

	/** The types of spaces that we know how to handle */
	private static HashSet< Class<?> > knownSpaces;
	
	static {
		knownSpaces = new HashSet< Class<?> > ();
		knownSpaces.add(ScalarHypercube.class);
		knownSpaces.add(Hypercube.class);
		knownSpaces.add(VehicleRoutingSpace.class);
	}
	
	/** Constructor
	 * @param nbrVehicles 		The number of vehicles available to this depot
	 * @param maxDist 			The maximum distance each vehicle is allowed to travel
	 * @param maxLoad 			The maximum load each vehicle is allowed to carry
	 * @param depotX 			The X coordinate of the depot
	 * @param depotY 			The Y coordinate of the depot
	 * @param vars 				The constraint's variables
	 * @param domsHashMap 		The desired domains for some of the variables
	 * @param customers 		The customers, indexed by their variable names
	 * @param selectedCustomers Customers that the depot is required to serve
	 * @param uncertainties 	For each random variable, the customer it refers to
	 * @param name 				The name of this constraint
	 * @param owner 			The owner
	 * @param infeasibleUtil 	The infeasible utility
	 * @param minSplit 			The minimum split size (no split if <= 0)
	 * @param problem 			The problem to be notified of constraint checks
	 */
	public VehicleRoutingSpace(int nbrVehicles, float maxDist, int maxLoad,
			float depotX, float depotY, String[] vars, HashMap<String, AddableInteger[]> domsHashMap, 
			HashMap<String, Customer> customers, HashSet<Customer> selectedCustomers, HashMap<String, Customer> uncertainties, 
			String name, String owner, U infeasibleUtil, final int minSplit, ProblemInterface<AddableInteger, U> problem) {
		this.nbrVehicles = nbrVehicles;
		this.maxDist = maxDist;
		this.maxLoad = maxLoad;
		this.totalMaxLoad = this.maxLoad * this.nbrVehicles;
		this.depotX = depotX;
		this.depotY = depotY;
		this.vars = vars;
		this.customers = customers;
		this.selectedCustomers = selectedCustomers;
		this.selectedLoad = 0;
		for (Customer cust : this.selectedCustomers) 
			this.selectedLoad += cust.demand;
		this.uncertainties = uncertainties;
		this.name = name;
		this.owner = owner;
		this.infeasibleUtil = infeasibleUtil;
		this.minSplit = minSplit;
		this.splitDeliveries = (minSplit > 0);
		this.problem = problem;
		
		// Build the variable domains
		this.doms = new AddableInteger [customers.size() + uncertainties.size()][];
		for (int i = 0; i < this.vars.length; i++) {
			String var = this.vars[i];
			
			// Check if the domain for this variable has been provided as an input
			AddableInteger[] dom = domsHashMap.get(var);
			if (dom != null) {
				this.doms[i] = dom;
				continue;
			}
			
			// Check if this is a decision variable
			Customer cust = this.customers.get(var);
			if (cust != null) { // decision variable
				
				if (! this.splitDeliveries) 
					this.doms[i] = new AddableInteger[] { zero, one };
				
				else { // {0, minSplit, minSplit+1, ..., cust.demand-minSplit, cust.demand} (provided demand >= 2*minSplit)
					if (cust.demand >= 2*this.minSplit) {
						dom = new AddableInteger [cust.demand - 2*this.minSplit + 3];
						dom[0] = new AddableInteger (0);
						for (int val = minSplit; val <= cust.demand-minSplit; val++) 
							dom[val - minSplit + 1] = new AddableInteger (val);
						dom[dom.length - 1] = new AddableInteger (cust.demand);
					} else {
						dom = new AddableInteger[] {new AddableInteger (0), new AddableInteger (cust.demand)};
					}
					this.doms[i] = dom;
				}
				
			} else { // random variable
				cust = this.uncertainties.get(var);
				assert cust != null : "Variable " + var + " has no associated customer in either following maps:\n" + this.customers + "\n" + this.uncertainties;
				this.doms[i] = cust.angles;
			}
		}
		
		// Construct the VRP solver
		try {
			/// @todo Choose the algorithm more carefully
			
			// Choose the TSP sub-algorithm
//			com.orllc.orobjects.lib.graph.tsp.ImproveI subalgo = null;
			com.orllc.orobjects.lib.graph.tsp.ImproveI subalgo = new TwoOpt ();
//			com.orllc.orobjects.lib.graph.tsp.ImproveI subalgo = new ThreeOpt ();
//			com.orllc.orobjects.lib.graph.tsp.ImproveI subalgo = new Us(5);
			
			// Choose the number of restarts of the randomized construction algorithm
			int nbrRestarts = 0;
			
			// Choose the strength of the construction algorithm (useless if nbrRestarts == 0)
			int strength = 5;
			
			// Choose the construction algorithm
//			ClarkeWright construct = new ClarkeWright (nbrRestarts, strength, subalgo); // Clarke & Wright - savings list
//			GillettMiller construct = new GillettMiller (nbrRestarts, strength, subalgo); // Gillett & Miller - polar sweep
			// Best of Clarke & Wright vs. Gillett & Miller
			BestOf construct = new BestOf ();
			construct.addConstruct(new ClarkeWright (nbrRestarts, strength, subalgo));
			construct.addConstruct(new GillettMiller (nbrRestarts, strength, subalgo));
			
			// Choose the VRP improvement algorithm
//			ImproveI improve = null;
			ImproveI improve = new ImproveWithTSP(new TwoOpt ());
//			ImproveI improve = new ImproveWithTSP(new ThreeOpt ());
//			ImproveI improve = new ImproveWithTSP((com.orllc.orobjects.lib.graph.tsp.ImproveI) new Us(5));
			
			// Construct the solver
			solver = new Composite (construct, improve);
			if (maxDist > 0) 
				solver.setCostConstraint(maxDist);
			solver.setCapacityConstraint(maxLoad);
			
		} catch (VRPException e) {
			e.printStackTrace();
		}
	}
	
	/** Constructs a new instance of this class
	 * @param nbrVehicles 		The number of vehicles available to this depot
	 * @param maxDist 			The maximum distance each vehicle is allowed to travel
	 * @param maxLoad 			The maximum load each vehicle is allowed to carry
	 * @param depotX 			The X coordinate of the depot
	 * @param depotY 			The Y coordinate of the depot
	 * @param vars 				The constraint's variables
	 * @param domsHashMap 		The desired domains for some of the variables
	 * @param customers 		The customers, indexed by their variable names
	 * @param selectedCustomers Customers that the depot is required to serve
	 * @param uncertainties 	For each random variable, the customer it refers to
	 * @param name 				The name of this constraint
	 * @param infeasibleUtil 	The infeasible utility
	 * @param minSplit 			The minimum split size (no split if <= 0)
	 * @param problem 			The problem to be notified of constraint checks
	 * @return a new instance of this class
	 */
	protected VehicleRoutingSpace<U> newInstance (int nbrVehicles, float maxDist, int maxLoad,
			float depotX, float depotY, String[] vars, HashMap<String, AddableInteger[]> domsHashMap, 
			HashMap<String, Customer> customers, HashSet<Customer> selectedCustomers, HashMap<String, Customer> uncertainties, 
			String name, U infeasibleUtil, final int minSplit, ProblemInterface<AddableInteger, U> problem) {
		return new VehicleRoutingSpace<U> (nbrVehicles, maxDist, maxLoad, depotX, depotY, vars, domsHashMap, customers, selectedCustomers, uncertainties, name, null, infeasibleUtil, minSplit, problem);
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString () {
		StringBuilder builder = new StringBuilder ("VehicleRoutingSpace" + 
				(this.name == null ? ":" : " (" + this.name + "):"));
		
		builder.append("\n\tnbrVehicles: " + this.nbrVehicles);
		builder.append("\n\tmaxDist: " + this.maxDist);
		builder.append("\n\tmaxLoad: " + this.maxLoad);
		builder.append("\n\tdepotX: " + this.depotX);
		builder.append("\n\tdepotY: " + this.depotY);
		builder.append("\n\tvars: " + Arrays.toString(this.vars));
		builder.append("\n\tdomains: " + Arrays.deepToString(this.doms));
		
		if (! this.customers.isEmpty()) {
			builder.append("\n\tcustomers: ");
			for (Map.Entry<String, Customer> entry : this.customers.entrySet()) 
				builder.append("\n\t\t" + entry.getKey() + " -> " + entry.getValue());
		}
		
		if (! this.selectedCustomers.isEmpty()) {
			builder.append("\n\tselected customers: ");
			for (Customer cust : this.selectedCustomers) 
				builder.append("\n\t\t" + cust);
		}
		
		if (! this.uncertainties.isEmpty()) {
			builder.append("\n\trandom variables: ");
			for (Map.Entry<String, Customer> entry : this.uncertainties.entrySet()) 
				builder.append("\n\t\t" + entry.getKey() + " -> " + entry.getValue());
		}
		
		builder.append("\n\tminSplit: " + this.minSplit);

		return builder.toString();
	}

	/** @see UtilitySolutionSpace#changeVariablesOrder(String[]) */
	public VehicleRoutingSpace<U> changeVariablesOrder(String[] variablesOrder) {
		
		assert variablesOrder.length == this.vars.length && new HashSet<String> (Arrays.asList(variablesOrder)).equals(new HashSet<String> (Arrays.asList(this.vars))) : 
			"Input to changeVariablesOrder is inconsistent: " + Arrays.toString(variablesOrder) + " vs. " + Arrays.toString(this.vars);
		
		return this.newInstance(nbrVehicles, maxDist, maxLoad, depotX, depotY, variablesOrder, new HashMap<String, AddableInteger[]> (), 
										   customers, selectedCustomers, this.uncertainties, name, infeasibleUtil, this.minSplit, this.problem);
	}
	
	/** @see java.lang.Object#clone() */
	@Override
	public VehicleRoutingSpace<U> clone () {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#compose(java.lang.String[], frodo2.solutionSpaces.BasicUtilitySolutionSpace) */
	public UtilitySolutionSpace<AddableInteger, U> compose(
			String[] vars,
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> substitution) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensus(java.lang.String, java.util.Map, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensus(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusAllSols(java.lang.String, java.util.Map, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensusAllSols(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusExpect(java.lang.String, java.util.Map, boolean) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensusExpect(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusAllSolsExpect(java.lang.String, java.util.Map, boolean) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensusAllSolsExpect(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#expectation(java.util.Map) */
	public UtilitySolutionSpace<AddableInteger, U> expectation(Map< String, UtilitySolutionSpace<AddableInteger, U> > distributions) {
		
		if (distributions.isEmpty())
			return this.clone();
		
		// Ignore all random variables not present in the this space
		Map< String, UtilitySolutionSpace<AddableInteger, U> > myDist = new HashMap< String, UtilitySolutionSpace<AddableInteger, U> > ();
		for (Map.Entry< String, UtilitySolutionSpace<AddableInteger, U> > entry : distributions.entrySet()) 
			if (this.getDomain(entry.getKey()) != null) 
				myDist.put(entry.getKey(), entry.getValue());
		
		if (myDist.isEmpty())
			return this.clone();
		
		return new ExpectationOutput<AddableInteger, U> (this, myDist, this.infeasibleUtil);
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#isIncludedIn(frodo2.solutionSpaces.UtilitySolutionSpace) */
	public boolean isIncludedIn(UtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean) */
	public IteratorBestFirst<AddableInteger, U> iteratorBestFirst(boolean maximize) {
		assert ! this.splitDeliveries : "Not supported yet";
		return new VRPiteratorBestFirst(this.vars, this.getDomains(), this);
	}
	
	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean, java.lang.String[], Addable[]) */
	public IteratorBestFirst<AddableInteger, U> iteratorBestFirst(boolean maximize, String[] fixedVariables, AddableInteger[] fixedValues) {
		assert ! this.splitDeliveries : "Not supported yet";
		return new VRPiteratorBestFirst(this.vars, this.getDomains(), this, fixedVariables, fixedValues);
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#join(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<AddableInteger, U> join(
			UtilitySolutionSpace<AddableInteger, U> space,
			String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace) */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace<AddableInteger, U> join(UtilitySolutionSpace<AddableInteger, U> space) {
		
		if (space == NullHypercube.NULL) 
			return NullHypercube.NULL;
		
		// Compute the domains of the output space and the number of utilities
		String[] outputVars = Hypercube.union(this.vars, space.getVariables());
		int nbrOutputVars = outputVars.length;
		AddableInteger[][] outputDomains = new AddableInteger [nbrOutputVars][];
		int nbrOutputUtils = 1;
		for (int i = 0; i < nbrOutputVars; i++) {
			String var = outputVars[i];

			// Look up the domain in the input space
			AddableInteger[] dom = space.getDomain(var);
			if (dom == null) {
				dom = this.getDomain(var);
				outputDomains[i] = dom;
			} else {
				outputDomains[i] = dom.clone();

				// Check that the domain in the input space is consistent
				assert this.splitDeliveries || ! customers.containsKey(var) || /// @todo Check also when split deliveries are allowed
				(dom.length > 2 || dom.length <= 0 ? false : 
					(dom.length == 2 ? Arrays.equals(dom, new AddableInteger[] {zero, one}) :
						dom[0].equals(zero) || dom[0].equals(one))) :
							"Incorrect domain " + Arrays.toString(dom) + " for variable " + var;
			}

			assert Math.log((double) nbrOutputUtils) + Math.log((double) dom.length) < Math.log(Integer.MAX_VALUE) : 
				"Number of solutions too big for an int";
			nbrOutputUtils *= dom.length;
		}
		
		return new JoinOutputHypercube<AddableInteger, U> (space, this, outputVars, outputDomains, true, this.infeasibleUtil, nbrOutputUtils);
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<AddableInteger, U> join(UtilitySolutionSpace<AddableInteger, U>[] spaces) {
		
		/// @todo Implement more efficiently
		UtilitySolutionSpace<AddableInteger, U> out = this;
		for (UtilitySolutionSpace<AddableInteger, U> space : spaces) 
			out = out.join(space);
		
		return out;
	}

	/** @see UtilitySolutionSpace#joinMinNCCCs(UtilitySolutionSpace) */
	public UtilitySolutionSpace<AddableInteger, U> joinMinNCCCs(UtilitySolutionSpace<AddableInteger, U> space) {
		
		assert !(space instanceof VehicleRoutingSpace) : "not yet fully implemented";
		
		return space.joinMinNCCCs(this);
	}

	/** @see UtilitySolutionSpace#joinMinNCCCs(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<AddableInteger, U> joinMinNCCCs(UtilitySolutionSpace<AddableInteger, U>[] spaces) {
		
		if (spaces.length == 0) 
			return this.resolve();
		
		UtilitySolutionSpace<AddableInteger, U> firstSpace = spaces[0];
		assert !(firstSpace instanceof VehicleRoutingSpace) : "not yet fully implemented";
		spaces[0] = this;
		UtilitySolutionSpace<AddableInteger, U> out = firstSpace.joinMinNCCCs(spaces);
		spaces[0] = firstSpace;
		
		return out;
	}

	/** @see UtilitySolutionSpace#blindProject(String, boolean) */
	public UtilitySolutionSpace<AddableInteger, U> blindProject(String varOut, boolean maximize) {
		return this.blindProject(new String[] {varOut}, maximize);
	}

	/** @see UtilitySolutionSpaceLimited#blindProject(String[], boolean) */
	public UtilitySolutionSpace<AddableInteger, U> blindProject(String[] varsOut, final boolean maximize) {

		// Only project variables that are actually contained in this space
		HashSet<String> varsOutSet = new HashSet<String> (varsOut.length);
		for (String varOut : varsOut) 
			if (this.getDomain(varOut) != null) 
				varsOutSet.add(varOut);
		int nbrVarsOut = varsOutSet.size();
		if( nbrVarsOut == 0 )
			return this;
		if (nbrVarsOut == this.getNumberOfVariables()) 
			return new ScalarHypercube<AddableInteger, U> (this.blindProjectAll(maximize), this.infeasibleUtil, new AddableInteger [0].getClass());
		if (nbrVarsOut < varsOut.length) 
			varsOut = varsOutSet.toArray(new String [nbrVarsOut]);
		
		return new BlindProjectOutput<AddableInteger, U> (this, varsOut, maximize, this.infeasibleUtil);
	}

	/** @see UtilitySolutionSpaceLimited#blindProjectAll(boolean) */
	public U blindProjectAll(final boolean maximize) {
		
		// Compute the optimum utility value
		VRPiterator iter = this.iterator();
		U optimum = iter.nextUtility();
		while (iter.hasNext()) {
			if (maximize) 
				optimum = optimum.max(iter.nextUtility());
			else 
				optimum = optimum.min(iter.nextUtility());
		}
		
		return optimum;
	}

	/** @see UtilitySolutionSpaceLimited#min(String) */
	public UtilitySolutionSpace<AddableInteger, U> min(String variable) {
		return this.blindProject(variable, false);
	}
	
	/** @see UtilitySolutionSpaceLimited#max(String) */
	public UtilitySolutionSpace<AddableInteger, U> max (String variable) {
		return this.blindProject(variable, true);
	}
	
	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U> space,
			String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace) */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#multiply(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#project(java.lang.String[], boolean) */
	@SuppressWarnings("unchecked")
	public ProjOutput<AddableInteger, U> project(String[] varsToProj, final boolean maximum) {
		
		assert this.containsAllVars(varsToProj) : "Not yet implemented";
		
		// Check if we are projecting all variables
		final int nbrVarsToProj = varsToProj.length;
		final int myNbrVars = this.vars.length;
		if (nbrVarsToProj == myNbrVars) 
			return this.projectAll(maximum);
		
		// Look up the variables that must remain in the output space
		ArrayList<String> varsKept = new ArrayList<String> ();
		int nbrUtilsKept = 1;
		int nbrUtilsOut = 1;
		myVar: for (String var : this.vars) {
			
			// Look up the domain size of this variable
			int domSize = this.getDomain(var).length;
			
			// Check whether the variable must be projected out
			for (String varOut : varsToProj) {
				if (var.equals(varOut)) { // this variable is projected out
					nbrUtilsOut *= domSize;
					continue myVar;
				}
			}
			varsKept.add(var);
			nbrUtilsKept *= domSize;
		}
		final int nbrVarsKept = varsKept.size();
		
		// Choose the variable order for the iterator: 1) varsKept, 2) varsToProj
		String[] iterOrder = varsKept.toArray(new String [myNbrVars]);
		System.arraycopy(varsToProj, 0, iterOrder, nbrVarsKept, nbrVarsToProj);
		
		// Initialize the (empty for now) output
		String[] varsKeptArray = varsKept.toArray(new String [nbrVarsKept]);
		AddableInteger[][] domsKept = new AddableInteger [nbrVarsKept][];
		for (int i = 0; i < nbrVarsKept; i++) 
			domsKept[i] = this.getDomain(varsKeptArray[i]);
		Class<U> utilClass = (Class<U>) this.infeasibleUtil.fromString("0").getClass();
		Hypercube<AddableInteger, U> space = new Hypercube<AddableInteger, U> (varsKeptArray, domsKept, (U[]) Array.newInstance(utilClass, nbrUtilsKept), this.infeasibleUtil);
		BasicHypercube< AddableInteger, ArrayList<AddableInteger> > assignments = 
			new BasicHypercube< AddableInteger, ArrayList<AddableInteger> > (varsKeptArray, domsKept, (ArrayList<AddableInteger>[]) new ArrayList [nbrUtilsKept], null);
		ProjOutput<AddableInteger, U> output = new ProjOutput<AddableInteger, U> (space, varsToProj, assignments);
		
		// Go through all assignments to the variables kept
		int i = 0;
		for (VRPiterator iter = this.iterator(iterOrder); iter.hasNext(); i++) {
			
			// Go through all assignments to the projected variables, looking for the best one
			U optUtil = (maximum ? this.infeasibleUtil.getMinInfinity() : this.infeasibleUtil.getPlusInfinity());
			AddableInteger[] optVals = new AddableInteger [nbrVarsToProj];
			for (int j = 0; j < nbrUtilsOut; j++) {
				if ((maximum 	&& iter.nextUtility().compareTo(optUtil) >= 0) ||
					(!maximum 	&& iter.nextUtility().compareTo(optUtil) <= 0)) {
					
					optUtil = iter.getCurrentUtility();
					System.arraycopy(iter.getCurrentSolution(), nbrVarsKept, optVals, 0, nbrVarsToProj);
				}
			}
			
			// Record the optimal assignment to the projected variables and the corresponding utility
			space.setUtility(i, optUtil);
			assignments.setUtility(i, new ArrayList<AddableInteger> (Arrays.asList(optVals)));
		}
		
		return output;
	}
	
	/** Checks whether this space contains all the input variables
	 * @param vars2 	the input variables
	 * @return whether this space contains all the input variables
	 */
	private boolean containsAllVars (String[] vars2) {
		for (String var : vars2) 
			if (this.getDomain(var) == null) 
				return false;
		return true;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#project(int, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(
			int numberToProject, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#project(String, boolean) */
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(String variableName, boolean maximum) {
		return this.project(new String[] {variableName}, maximum);
	}

	/** @see UtilitySolutionSpace#projectAll(boolean) */
	public ProjOutput<AddableInteger, U> projectAll(final boolean maximum) {
		
		// Iterate through all solutions to find the optimal one
		U optUtil = (maximum ? this.infeasibleUtil.getMinInfinity() : this.infeasibleUtil.getPlusInfinity());
		String[] varsOut = this.vars;
		int nbrVars = varsOut.length;
		AddableInteger[] optSol = new AddableInteger [nbrVars];
		for (VRPiterator iter = this.iterator(); iter.hasNext(); ) {
			if ((maximum 	&& iter.nextUtility().compareTo(optUtil) >= 0) ||
				(!maximum 	&& iter.nextUtility().compareTo(optUtil) <= 0)) {
				
				optUtil = iter.getCurrentUtility();
				System.arraycopy(iter.getCurrentSolution(), 0, optSol, 0, nbrVars);
			}
		}
		
		ScalarHypercube<AddableInteger, U> space = new ScalarHypercube<AddableInteger, U> (optUtil, this.infeasibleUtil, new AddableInteger [0].getClass());
		ScalarBasicHypercube< AddableInteger, ArrayList<AddableInteger> > assignments = 
			new ScalarBasicHypercube< AddableInteger, ArrayList<AddableInteger> > (
					new ArrayList<AddableInteger> (Arrays.asList(optSol)), null);
		
		return new ProjOutput<AddableInteger, U> (space, varsOut, assignments);
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#projectAll(boolean, java.lang.String[]) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> projectAll(
			boolean maximum, String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#resolve() */
	@SuppressWarnings("unchecked")
	public Hypercube<AddableInteger, U> resolve() {
		
		// Compute the utilities for all combinations of assignments to the variables
		assert this.getNumberOfSolutions() < Integer.MAX_VALUE : "Cannot resolve a VehicleRoutingSpace that contains more than 2^32 solutions";
		U[] utilities = (U[]) Array.newInstance(this.infeasibleUtil.getZero().getClass(), (int) this.getNumberOfSolutions());
		int i = 0;
		for (Iterator<AddableInteger, U> iter = this.iterator(); iter.hasNext(); i++) 
			utilities[i] = iter.nextUtility();
		
		return new Hypercube<AddableInteger, U> (this.vars, this.doms, utilities, this.infeasibleUtil);
	}

	/** @see UtilitySolutionSpace#toHypercube() */
	public Hypercube<AddableInteger, U> toHypercube() {
		return this.resolve();
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#sample(int) */
	public Map<AddableInteger, Double> sample(int nbrSamples) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String[], Addable[][]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(String[] varNames, AddableInteger[][] subDomains) {
		
		ArrayList<String> newVars = new ArrayList<String> (Arrays.asList(this.vars));
		HashMap<String, Customer> newCustomers = new HashMap<String, Customer> (this.customers);
		HashSet<Customer> newSelectedCustomers = new HashSet<Customer> (this.selectedCustomers);
		HashMap<String, Customer> newUncertainties = new HashMap<String, Customer> (this.uncertainties);
		HashMap<String, AddableInteger[]> newDoms = new HashMap<String, AddableInteger[]> (varNames.length);
		
		// Check whether some of the customers have been selected or ignored, 
		// and whether some random variable domains have been sliced
		for (int i = varNames.length - 1; i >= 0; i--) {
			String var = varNames[i];
			
			// Check if this is a decision variable for a known customer
			Customer customer = this.customers.get(var);
			if (customer != null) {
			
				AddableInteger[] dom = subDomains[i];
				if (dom.length == 1) {
					
					// Select or ignore the corresponding customer
					AddableInteger val = dom[0];
					newVars.remove(var);
					if (val.equals(zero)) { // ignore the customer
						newCustomers.remove(var);
						
						// Remove the corresponding random variable too, if any
						for (java.util.Iterator< Map.Entry<String, Customer> > iter = newUncertainties.entrySet().iterator(); iter.hasNext(); ) {
							Map.Entry<String, Customer> entry = iter.next();
							if (entry.getValue().equals(customer)) {
								iter.remove();
								newVars.remove(entry.getKey());
								break;
							}
						}
						
					} else { // select the customer
						Customer cust = newCustomers.remove(var);
						if (this.splitDeliveries) 
							cust.demand = val.intValue();
						newSelectedCustomers.add(cust);
					}

				} else { // dom.length >= 2
					
					newDoms.put(var, dom);
					
					if (!this.splitDeliveries) 
						assert Arrays.equals(dom, new AddableInteger[] {zero, one}) : "Incorrect domain " + Arrays.toString(dom) + " for variable " + var; /// @todo Check also when split deliveries are allowed
				}

			} else { // not a decision variable for a known customer

				// Check if this is a random variable for a known customer
				customer = this.uncertainties.get(var);
				if (customer == null) 
					continue;

				AddableInteger[] newAngles = subDomains[i];
				assert Arrays.equals(customer.angles, newAngles) : "Slicing of random variable domains not supported yet";
			}
		}
		
		// Reset the demands of the new customers and clone them
		HashMap<String, Customer> newCustomers2 = new HashMap<String, Customer> (newCustomers.size());
		HashMap<Customer, Customer> clones = new HashMap<Customer, Customer> (newCustomers.size());
		for (Map.Entry<String, Customer> entry : newCustomers.entrySet()) {
			String var = entry.getKey();
			AddableInteger[] dom = this.getDomain(var);
			Customer cust = entry.getValue();
			if (this.splitDeliveries) 
				cust.demand = dom[dom.length - 1].intValue();
			Customer clone = cust.clone();
			newCustomers2.put(var, clone);
			clones.put(cust, clone);
		}
		HashMap<String, Customer> newUncertainties2 = new HashMap<String, Customer> (newUncertainties.size());
		for (Map.Entry<String, Customer> entry : newUncertainties.entrySet()) {
			Customer cust = entry.getValue();
			Customer clone = clones.get(cust);
			if (clone == null) {
				clone = cust.clone();
				clones.put(cust, clone);
			}
			newUncertainties2.put(entry.getKey(), clone);
		}
		HashSet<Customer> newSelectedCustomers2 = new HashSet<Customer> (newSelectedCustomers.size());
		for (Customer cust : newSelectedCustomers) {
			Customer clone = clones.get(cust);
			if (clone == null) 
				clone = cust.clone();
			newSelectedCustomers2.add(clone);
		}
		
		// Check if we should return a ScalarHypercube because all variables have been removed
		if (newVars.isEmpty()) {
			
			// Construct the variable assignments
			AddableInteger[] assignments = new AddableInteger [this.vars.length];
			for (int i = this.vars.length - 1; i >= 0; i--) {
				Customer cust = this.customers.get(this.vars[i]);
				assignments[i] = (newSelectedCustomers.contains(cust) ? 
										(this.splitDeliveries ? 
												new AddableInteger (cust.demand) : 
												one) : 
										zero);
			}
			
			return new ScalarHypercube<AddableInteger, U> (this.getUtility(assignments), this.infeasibleUtil, new AddableInteger [0].getClass());
		}
		
		VehicleRoutingSpace<U> out = this.newInstance(this.nbrVehicles, this.maxDist, this.maxLoad, this.depotX, this.depotY, 
				newVars.toArray(new String [newVars.size()]), newDoms, newCustomers2, newSelectedCustomers2, newUncertainties2, null, this.infeasibleUtil, this.minSplit, this.problem);
		return out;
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String[], Addable[]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(String[] varNames, AddableInteger[] values) {
		
		AddableInteger[][] doms = new AddableInteger [values.length][];
		for (int i = values.length - 1; i >= 0; i--) 
			doms[i] = new AddableInteger[] {values[i]};
		
		return this.slice(varNames, doms);
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String, Addable[]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(String var,
			AddableInteger[] subDomain) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String, frodo2.solutionSpaces.Addable) */
	public UtilitySolutionSpace<AddableInteger, U> slice(String var,
			AddableInteger val) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#slice(Addable[]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			AddableInteger[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#split(frodo2.solutionSpaces.Addable, boolean) */
	public UtilitySolutionSpace<AddableInteger, U> split(U threshold,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#augment(Addable[], java.io.Serializable) */
	public void augment(AddableInteger[] variablesValues, U utilityValue) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#equivalent(frodo2.solutionSpaces.BasicUtilitySolutionSpace) */
	public boolean equivalent(BasicUtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#getClassOfU() */
	@SuppressWarnings("unchecked")
	public Class<U> getClassOfU() {
		return (Class<U>) this.infeasibleUtil.getZero().getClass();
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getDefaultUtility() */
	public U getDefaultUtility() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see BasicUtilitySolutionSpace#getUtility(Addable[]) */
	public U getUtility(AddableInteger[] values) {
		return this.getUtility(this.vars, values);
	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.lang.String[], Addable[]) */
	public U getUtility(String[] varNames, AddableInteger[] values) {
		
		// Check which customers must be served
		HashSet<Customer> toBeServed = new HashSet<Customer> (this.selectedCustomers);
		for (int i = varNames.length - 1; i >= 0; i--) {
			String var = varNames[i];
			
			// Check if this is a decision variable for a known customer
			Customer customer = this.customers.get(var);
			if (customer != null) {
				
				// Skip it if it corresponds to a customer that must be ignored
				AddableInteger choice = values[i];
				if (choice.equals(zero)) 
					continue;
				
				if (this.splitDeliveries) 
					customer.demand = choice.intValue();
				toBeServed.add(customer);
				
			} else { // not a decision variable for a known customer
				
				// Check if this is a random variable for a known customer
				customer = this.uncertainties.get(var);
				if (customer == null) 
					continue; 
				
				// Compute the exact position of the customer
				customer.setPosition(values[i].intValue());
			}
		}
		
		return this.getUtility(toBeServed);
	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.util.Map) */
	public U getUtility(Map<String, AddableInteger> assignments) {
		
		// Check which customers must be served
		HashSet<Customer> toBeServed = new HashSet<Customer> (this.selectedCustomers);
		for (Map.Entry<String, AddableInteger> entry : assignments.entrySet()) {
			String var = entry.getKey();
			
			// Check if this is a decision variable for a known customer
			Customer customer = this.customers.get(var);
			if (customer != null) {
				
				// Skip it if it corresponds to a customer that must be ignored
				AddableInteger choice = entry.getValue();
				if (choice.equals(zero)) 
					continue;
				
				if (this.splitDeliveries) 
					customer.demand = choice.intValue();
				toBeServed.add(customer);
				
			} else { // not a decision variable for a known customer
				
				// Check if this is a random variable for a known customer
				customer = this.uncertainties.get(var);
				if (customer == null) 
					continue; 
				
				// Compute the exact position of the customer
				customer.setPosition(entry.getValue().intValue());
			}
		}
		
		return this.getUtility(toBeServed);
	}
	
	/** Computes the optimal cost of serving a set of customers
	 * @param toBeServed 	the customers
	 * @return the optimal cost of the routes to serve the input customers
	 */
	protected U getUtility (final HashSet<Customer> toBeServed) {
		
		
		if (this.problem != null) 
			this.problem.incrNCCCs(1);
		
		// The cost is 0 if no customer needs to be served
		if (toBeServed.isEmpty()) 
			return this.infeasibleUtil.getZero();
		
		// The cost is +INF if the total load to deliver is higher than the total capacity
		int totalLoad = 0;
		for (Customer cust : toBeServed) {
			if (cust.demand > this.maxLoad) // too much for any vehicle
				return this.infeasibleUtil;
			totalLoad += cust.demand;
		}
		if (totalLoad > this.totalMaxLoad) 
			return this.infeasibleUtil;
		
		// Construct the VRP graph
		PointGraph pointGraph = new PointGraph();
		
		// Construct the vertex for the depot
		String depotName = "Depot";
		try {
			pointGraph.addVertex(depotName, new Customer (-1, 0, this.depotX, this.depotY, 0.0, null));
		} catch (DuplicateVertexException e1) { } // cannot happen since the graph is empty
		
		// Construct the vertex for each customer
		for (Customer customer : toBeServed) {
			try {
				pointGraph.addVertex(customer.id, customer.clone()); /// @todo cloning is a hack because setPosition() is buggy
			} catch (DuplicateVertexException e) {
				e.printStackTrace();
			}
		}
		
		solver.setGraph(pointGraph);
		
		// Solve the problem
		try {
			
			double optCost = solver.constructClosedTours (depotName);
			
			// Check whether the solution uses more vehicles than allowed
			if (solver.getTours().length > this.nbrVehicles) 
				return this.infeasibleUtil;
			
			U util = this.infeasibleUtil.fromString(Double.toString(optCost));
			return util;
			
		} catch (SolutionNotFoundException e) {
			return this.infeasibleUtil;
			
		} catch (VertexNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	/** @see UtilitySolutionSpace#setProblem(ProblemInterface) */
	public void setProblem(ProblemInterface<AddableInteger, U> problem) {
		this.problem = problem;
	}

	/** @see BasicUtilitySolutionSpace#getUtility(long) */
	public U getUtility(long index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#isIncludedIn(frodo2.solutionSpaces.BasicUtilitySolutionSpace) */
	public boolean isIncludedIn(
			BasicUtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#iterator() */
	public VRPiterator iterator() {
		return new VRPiterator (this.vars, this.getDomains(), null);
	}

	/** @see BasicUtilitySolutionSpace#sparseIter() */
	public VRPiterator sparseIter() {
		return new VRPiterator (this.vars, this.getDomains(), this.infeasibleUtil);
	}

	/** @see BasicUtilitySolutionSpace#iterator(java.lang.String[], Addable[][]) */
	public VRPiterator iterator(String[] variables, AddableInteger[][] domains) {
		return new VRPiterator (variables, domains, null);
	}

	/** @see BasicUtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][]) */
	public VRPiterator sparseIter(String[] variables, AddableInteger[][] domains) {
		return new VRPiterator (variables, domains, this.infeasibleUtil);
	}

	/** @see UtilitySolutionSpace#iterator(java.lang.String[], Addable[][], Addable[]) */
	public Iterator<AddableInteger, U> iterator(String[] variables, AddableInteger[][] domains, AddableInteger[] assignment) {
		
		/// @todo Improve by making use of assignments

		return new VRPiterator (variables, domains, null);
	}
	
	/** @see UtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][], Addable[]) */
	public SparseIterator<AddableInteger, U> sparseIter(String[] variables, AddableInteger[][] domains, AddableInteger[] assignment) {
		
		/// @todo Improve by making use of assignments

		return new VRPiterator (variables, domains, this.infeasibleUtil);
	}
	
	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#prettyPrint(java.io.Serializable) */
	public String prettyPrint(U ignoredUtil) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setDefaultUtility(java.io.Serializable) */
	public void setDefaultUtility(U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see BasicUtilitySolutionSpace#setInfeasibleUtility(java.io.Serializable) */
	public void setInfeasibleUtility(U utility) {
		this.infeasibleUtil = utility;
	}

	/** @see BasicUtilitySolutionSpace#setUtility(Addable[], java.io.Serializable) */
	public boolean setUtility(AddableInteger[] variablesValues, U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#setUtility(long, java.io.Serializable) */
	public void setUtility(long index, U utility) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#augment(Addable[]) */
	public void augment(AddableInteger[] variablesValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#getDomain(java.lang.String) */
	public AddableInteger[] getDomain(String variable) {
		
		for (int i = 0; i < this.vars.length; i++) 
			if (variable.equals(this.vars[i])) 
				return this.doms[i];

		return null;
	}

	/** @see SolutionSpace#getDomain(int) */
	public AddableInteger[] getDomain(int index) {
		return this.doms[index];
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getDomain(java.lang.String, int) */
	public AddableInteger[] getDomain(String variable, int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getDomains() */
	public AddableInteger[][] getDomains() {
		return this.doms;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getIndex(java.lang.String) */
	public int getIndex(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}

	/** @see SolutionSpace#getName() */
	public String getName() {
		return this.name;
	}

	/** @see SolutionSpace#getNumberOfSolutions() */
	public long getNumberOfSolutions() {

		long out = 1;
		for (AddableInteger[] dom : this.doms) {
			assert Math.log(out) + Math.log(dom.length) < Math.log(Long.MAX_VALUE) : "Too many solutions in a VehicleRoutinSpace";
			out *= dom.length;
		}
		
		return out;
	}

	/** @see SolutionSpace#getNumberOfVariables() */
	public int getNumberOfVariables() {
		return this.vars.length;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#getRelationName() */
	public String getRelationName() {
		return "global:vehicle_routing:" + this.name;
	}

	/** @see SolutionSpace#getVariable(int) */
	public String getVariable(int index) {
		return this.vars[index];
	}

	/** @see SolutionSpace#getVariables() */
	public String[] getVariables() {
		return this.vars;
	}

	/** @see SolutionSpace#iterator(java.lang.String[]) */
	public VRPiterator iterator(String[] order) {
		return this.iterator(order, this.getDomains());
	}

	/** @see SolutionSpace#sparseIter(java.lang.String[]) */
	public VRPiterator sparseIter(String[] order) {
		return this.sparseIter(order, this.getDomains());
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace, java.lang.String[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space, String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#join(SolutionSpace[], java.lang.String[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger>[] spaces, String[] totalVariablesOrder) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#join(SolutionSpace[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#knows(java.lang.Class) */
	public boolean knows(Class<?> spaceClass) {
		return knownSpaces.contains(spaceClass);
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#renameAllVars(java.lang.String[]) */
	public SolutionSpace<AddableInteger> renameAllVars(String[] newVarNames) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#renameVariable(java.lang.String, java.lang.String) */
	public void renameVariable(String oldName, String newName) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#setDomain(java.lang.String, Addable[]) */
	public void setDomain(String var, AddableInteger[] dom) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see SolutionSpace#setName(java.lang.String) */
	public void setName(String name) {
		this.name = name;
	}

	/** @see frodo2.solutionSpaces.SolutionSpace#setRelationName(java.lang.String) */
	public void setRelationName(String name) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#projExpectMonotone(java.lang.String, java.util.Map, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> projExpectMonotone(String varOut, Map< String, UtilitySolutionSpace<AddableInteger, U> > distributions, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

	/** @see SolutionSpace#getOwner() */
	public String getOwner() {
		return this.owner;
	}

	/** @see SolutionSpace#setOwner(java.lang.String) */
	public void setOwner(String owner) {
		this.owner = owner;
	}
	
	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#rescale(frodo2.solutionSpaces.Addable, frodo2.solutionSpaces.Addable)
	 */
	@Override
	public UtilitySolutionSpace<AddableInteger, U> rescale(U add, U multiply) {
		// TODO Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}

}
