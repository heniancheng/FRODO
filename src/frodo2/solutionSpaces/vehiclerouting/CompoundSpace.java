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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.JoinOutputHypercube;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/**
 * @author Brammert Ottens, 28 apr 2010
 *
 * @param <U> type of utility values
 */
public class CompoundSpace < U extends Addable<U> > implements UtilitySolutionSpace<AddableInteger, U> {

	/** Used for serialization */
	private static final long serialVersionUID = -2483502481064316880L;


	/**
	 * Best first iterator for the combination of a VehicleRoutingSpace with
	 * a collection of sum constraints
	 * 
	 * @author Brammert Ottens, 28 apr 2010
	 *
	 */
	private class BestFirstIterator implements IteratorBestFirst<AddableInteger, U> {

		/** The current solution */
		private AddableInteger[] currentSolution;
		
		/** The utility of the current solution */
		private U currentUtility;
		
		/** For each variable its position in the assignment array */
		protected HashMap<String, Integer> variablePointer;
		
		/** The variables occuring in the VRP problem*/
		private String[] vehicleRoutingVariables;
		
		/** The variables occuring in the sum constraints */
		private String[] sumVariables;
		
		/** \c true when this space contains a VRP space, and \c false otherwise */
		private boolean hasVehicleRoutingProblem;
		
		/** \c true when this space contrains sum variables, and \c false otherwise */
		private boolean hasSumVariables;
		
		/** An iterator over the VRP space */
		private Iterator<AddableInteger, U> vehicleIterBestFirst;
		
		/** The number of solutions left */
		private int nbrSolsLeft;
		
		/** The number of possible assignments for the sum variables left */
		private int nbrSumSolsLeft;
		
		/** the total number of possible assignments to the sum variables */
		private int nbrSumSols;
		
		/** The total number of sum variables */
		private int nbrSumVariables;
		
		/** The total number of VRP variables */
		private int nbrVRPvariables;
		
		/** Domain value 1*/
		private AddableInteger one = new AddableInteger(1);
		
		/** Domain value 0*/
		private AddableInteger zero = new AddableInteger(0);
		
		/** The current assignment to the sum variables */
		private AddableInteger[] sumAssignment;
		
		/** The current assignment to the VRP variables */
		private AddableInteger[] vrpAssignment;
		
		/** The index of the own variable in the list of variables for the VRP problem */
		private int ownVariableAssignmentVRPIndex;
		
		/** The zero utility */
		private U zeroUtil;
		
		/**
		 * Constructor
		 * 
		 * @param vrpSpace 			the vehicle routing space
		 * @param spaces			the sum constraint spaces
		 * @param variablesOrder	the order in which the variables should be reported to the outside world
		 * @param ownVariable		The ID of the variable that owns this constraint
		 */
		public BestFirstIterator(VehicleRoutingSpace<U> vrpSpace, List<UtilitySolutionSpace<AddableInteger, U>> spaces, String[] variablesOrder, String ownVariable) {
			// check whether the space has a VRP space
			hasVehicleRoutingProblem = vrpSpace != null;
			int nbrVRPsols = 0;
			
			// If there is a space, then get the best first
			// iterator over it. If there is no space, set
			// the value of the variable owning the space to 1.
			if(hasVehicleRoutingProblem) {
				vehicleIterBestFirst = vrpSpace.iteratorBestFirst(false);
				vehicleRoutingVariables = vrpSpace.getVariables();
				nbrVRPvariables = vehicleRoutingVariables.length;
				vrpAssignment = new AddableInteger[nbrVRPvariables];
				zeroUtil = vrpSpace.getUtility(new String[0], new AddableInteger[0]).getZero();
			} else {
				vrpAssignment = new AddableInteger[1];
				vrpAssignment[0] = one;
				vehicleRoutingVariables = new String[1];
				vehicleRoutingVariables[0] = ownVariable;
				ownVariableAssignmentVRPIndex = 0;
				nbrVRPvariables = 1;
				zeroUtil = spaces.get(0).getUtility(0).getZero();
				this.currentUtility = zeroUtil;
			}
			
			// process the sum constraints
			HashSet<String> sumVariables = new HashSet<String>();
			for(UtilitySolutionSpace<AddableInteger, U> space : spaces) {
				String[] variables = space.getVariables();
				for(String var : variables) {
					sumVariables.add(var);
				}
			}
			sumVariables.remove(ownVariable);
			nbrSumVariables = sumVariables.size();
			hasSumVariables = nbrSumVariables > 0;
			
			if(hasSumVariables) {
				this.sumVariables = new String[nbrSumVariables];
				sumAssignment = new AddableInteger[nbrSumVariables];
				int i = 0;
				for(String var : sumVariables) {
					this.sumVariables[i] = var;
					i++;
				}
				
				if(hasVehicleRoutingProblem) {
					for(i = 0; i < nbrVRPvariables; i++) {
						if(vehicleRoutingVariables[i].equals(ownVariable))
							ownVariableAssignmentVRPIndex = i;
					}
					nbrVRPsols = (int)(Math.pow(2, nbrVRPvariables));
				}
			} else if (hasVehicleRoutingProblem && spaces.size() == 1){
					AddableInteger[] val = new AddableInteger[1];
					val[0] = one;
					String[] var = new String[1];
					var[0] = ownVariable;
					vehicleIterBestFirst = vrpSpace.iteratorBestFirst(false, var, val);
					nbrVRPsols = (int)(Math.pow(2, nbrVRPvariables - 1));
			} else {
				nbrVRPsols = (int)(Math.pow(2, nbrVRPvariables));
			}
			int totalNbrVariables = nbrVRPvariables + nbrSumVariables;
			currentSolution = new AddableInteger[totalNbrVariables];
			
			// combine the variables to one array, used for outputting
			// the solutions
			variables = variablesOrder;
			variablePointer = new HashMap<String, Integer>(totalNbrVariables);
			int i = 0;
			for(; i < variablesOrder.length; i++) {
				assert variablesOrder[i] != null;
				variablePointer.put(variablesOrder[i], i);
			}
			assert totalNbrVariables == variablePointer.size();
			
			nbrSumSolsLeft = 0;
			
			if(hasVehicleRoutingProblem) 
				if(this.hasSumVariables)
					nbrSolsLeft = nbrVRPsols* (int)(Math.pow(2, nbrSumVariables));
				else
					nbrSolsLeft = nbrVRPsols;
			else if(hasSumVariables)
				nbrSolsLeft = (int)(Math.pow(2, nbrSumVariables));
			else
				nbrSolsLeft = 1;
		}
		
		/**
		 * Method used to iterate over the assignments in a best first order
		 * @author Brammert Ottens, 28 apr 2010
		 */
		private void iter() {
			--nbrSolsLeft;
			
			if(nbrSumSolsLeft > 0) {
				iterateSumVars();
			} else {
				if(hasVehicleRoutingProblem) {
					if(vehicleIterBestFirst.hasNext()) {
						vrpAssignment = vehicleIterBestFirst.nextSolution();
						currentUtility = vehicleIterBestFirst.getCurrentUtility();
					}
				} else {
					vrpAssignment[0] = vrpAssignment[0] == one ? zero : one;
				}

				if(hasSumVariables) {
					Arrays.fill(sumAssignment, zero);
					if(vrpAssignment[ownVariableAssignmentVRPIndex].equals(zero)) {
						sumAssignment[0] = one;
						nbrSumSolsLeft = nbrSumSols - 1;
					} else {
						nbrSumSolsLeft = 0;
					}
				}
			}
			combineVRPAndSum();
		}
		
		/** Iterate of the possible combinations of NAND variables */
		private void iterateSumVars() {
			nbrSumSolsLeft--;
			if(nbrSumSolsLeft == 0)
				return;
			
			int i = 0;
			sumAssignment[i] = (sumAssignment[i] == zero) ? one : zero;
			boolean carry = sumAssignment[i] == zero;

			while(i + 1 < nbrSumVariables) {
				i++;
				if(carry) {
					sumAssignment[i] = (sumAssignment[i] == zero) ? one : zero;
					carry = sumAssignment[i] == zero;
				} else break;
			}
		}
		
		/**
		 * Method to combine the nandAssignment and the
		 * packetAssignment in the currentAssignment variable
		 * 
		 * @author Brammert Ottens, 25 nov 2009
		 */
		protected void combineVRPAndSum() {
			for(int i = 0; i < this.nbrVRPvariables; i++) {
				currentSolution[variablePointer.get(vehicleRoutingVariables[i])] = vrpAssignment[i];
			}
			for(int i = 0; i < this.nbrSumVariables; i++) {
				currentSolution[variablePointer.get(sumVariables[i])] = sumAssignment[i];
			}
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
			// @todo Auto-generated method stub
			assert false : "Not Implemented";
			return null;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#getNbrSolutions()
		 */
		public long getNbrSolutions() {
			// @todo Auto-generated method stub
			assert false : "Not Implemented";
			return 0;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#getVariablesOrder()
		 */
		public String[] getVariablesOrder() {
			// @todo Auto-generated method stub
			assert false : "Not Implemented";
			return null;
		}

		/** 
		 * @see frodo2.solutionSpaces.SolutionSpace.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return this.nbrSolsLeft > 0;
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

		/** @see frodo2.solutionSpaces.UtilitySolutionSpace.IteratorBestFirst#maximalCut() */
		public U maximalCut() {
			return zeroUtil;
		}
	}
	
	/** The sum spaces */
	private ArrayList< UtilitySolutionSpace<AddableInteger, U> > inputs = new ArrayList< UtilitySolutionSpace<AddableInteger, U> > ();
	
	/** The vrp space */
	private VehicleRoutingSpace<U> vrpSpace;
	
	/** The variables occuring in this space */
	private String[] variables;
	
	/** The domains of the variables occuring in this space */
	private AddableInteger[][] domains;
	
	/** Pointer to the position of variables in the variable array */
	private HashMap<String, Integer> variablePointer;
	
	/** The types of spaces that we know how to handle */
	private static HashSet< Class<?> > knownSpaces;
	
	/** The variable that owns this constraint */
	private String ownVariable;

	/** The infeasible utility */
	private U infeasibleUtil;
	
	static {
		knownSpaces = new HashSet< Class<?> > ();
		knownSpaces.add(BasicHypercube.class);
		knownSpaces.add(ScalarBasicHypercube.class);
		knownSpaces.add(Hypercube.class);
		knownSpaces.add(Hypercube.NullHypercube.class);
		knownSpaces.add(ScalarHypercube.class);
		knownSpaces.add(JoinOutputHypercube.class);
	}
	
	/**
	 * Constructor
	 * 
	 * @param variable			the variable to which this space belongs
	 * @param domain			the domain of this variable
	 * @param infeasibleUtil 	the infeasible utility
	 */
	public CompoundSpace (String variable, AddableInteger[] domain, U infeasibleUtil) {
		variables = new String[1];
		variables[0] = variable;
		domains = new AddableInteger[1][];
		domains[0] = domain;
		variablePointer = new HashMap<String, Integer>(1);
		variablePointer.put(variable, 0);
		ownVariable = variable;
		this.infeasibleUtil = infeasibleUtil;
	}
	
	
	/**
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#iteratorBestFirst(boolean)
	 */
	public IteratorBestFirst<AddableInteger, U> iteratorBestFirst(boolean maximize) {
		return new BestFirstIterator(vrpSpace, inputs, variables, ownVariable);
	}
	
	/**
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#join(frodo2.solutionSpaces.UtilitySolutionSpace[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> join(UtilitySolutionSpace<AddableInteger, U>[] spaces) {
	
		inputs = new ArrayList<UtilitySolutionSpace<AddableInteger, U>>(spaces.length);
		
		HashMap<String, AddableInteger[]> variables = new HashMap<String, AddableInteger[]>();
		for(UtilitySolutionSpace<AddableInteger, U> space : spaces) {
			String[] spaceVariables = space.getVariables();
			AddableInteger[][] spaceDomains = space.getDomains();
			for(int i = 0; i < spaceVariables.length; i++)
				variables.put(spaceVariables[i], spaceDomains[i]);

			if(space instanceof VehicleRoutingSpace) {
				assert vrpSpace == null;
				vrpSpace = (VehicleRoutingSpace<U>)space;
			} else {
				inputs.add(space);
			}
		}
		
		int nbrVariables = variables.size();
		this.variables = new String[nbrVariables];
		this.domains = new AddableInteger[nbrVariables][];
		variablePointer = new HashMap<String, Integer>();
		
		int index = 0;
		for(Entry<String, AddableInteger[]> e : variables.entrySet()) {
			String var = e.getKey();
			this.variables[index] = var;
			this.domains[index] = e.getValue();
			variablePointer.put(var, index++);
		}
		                           
		
		if(this.variables.length == 0)
			return null;
		else
			return this;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#changeVariablesOrder(java.lang.String[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> changeVariablesOrder(
			String[] variablesOrder) {
		
		int nbrVariables = variablesOrder.length;
		
		AddableInteger[][] newDomains = new AddableInteger[domains.length][];
		HashMap<String, Integer> newVariablePointer = new HashMap<String, Integer>(domains.length);
		
		for(int i = 0; i < nbrVariables; i++) {
			String var = variablesOrder[i];
			newDomains[i] = domains[variablePointer.get(var)];
			newVariablePointer.put(var, i);
		}
		
		variables = variablesOrder;
		domains = newDomains;
		variablePointer = newVariablePointer;
		
		return this;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#compose(java.lang.String[], frodo2.solutionSpaces.BasicUtilitySolutionSpace)
	 */
	public UtilitySolutionSpace<AddableInteger, U> compose(
			String[] vars,
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> substitution) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#consensus(java.lang.String, java.util.Map, boolean)
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensus(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#consensusAllSols(java.lang.String, java.util.Map, boolean)
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensusAllSols(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
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

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#expectation(java.util.Map) */
	public UtilitySolutionSpace<AddableInteger, U> expectation(Map< String, UtilitySolutionSpace<AddableInteger, U> > distributions) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#isIncludedIn(frodo2.solutionSpaces.UtilitySolutionSpace)
	 */
	public boolean isIncludedIn(UtilitySolutionSpace<AddableInteger, U> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return false;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#join(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> join(
			UtilitySolutionSpace<AddableInteger, U> space,
			String[] totalVariables) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#join(frodo2.solutionSpaces.UtilitySolutionSpace)
	 */
	public UtilitySolutionSpace<AddableInteger, U> join(
			UtilitySolutionSpace<AddableInteger, U> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#joinMinNCCCs(frodo2.solutionSpaces.UtilitySolutionSpace)
	 */
	public UtilitySolutionSpace<AddableInteger, U> joinMinNCCCs(
			UtilitySolutionSpace<AddableInteger, U> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#joinMinNCCCs(frodo2.solutionSpaces.UtilitySolutionSpace[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> joinMinNCCCs(
			UtilitySolutionSpace<AddableInteger, U>[] spaces) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#max(java.lang.String)
	 */
	public UtilitySolutionSpace<AddableInteger, U> max(String variable) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U> space,
			String[] totalVariables) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace)
	 */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#multiply(frodo2.solutionSpaces.UtilitySolutionSpace[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U>[] spaces) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#project(java.lang.String[], boolean)
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(
			String[] variablesNames, boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#project(int, boolean)
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(
			int numberToProject, boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#project(java.lang.String, boolean)
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(
			String variableName, boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#projectAll(boolean)
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> projectAll(
			boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#projectAll(boolean, java.lang.String[])
	 */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> projectAll(
			boolean maximum, String[] order) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#resolve()
	 */
	public UtilitySolutionSpace<AddableInteger, U> resolve() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#sample(int)
	 */
	public Map<AddableInteger, Double> sample(int nbrSamples) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String[], Addable[][])
	 */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			String[] variablesNames, AddableInteger[][] subDomains) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String[], Addable[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			String[] variablesNames, AddableInteger[] values) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String, Addable[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> slice(String var,
			AddableInteger[] subDomain) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(java.lang.String, frodo2.solutionSpaces.Addable)
	 */
	public UtilitySolutionSpace<AddableInteger, U> slice(String var,
			AddableInteger val) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#slice(Addable[])
	 */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			AddableInteger[] variablesValues) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#split(frodo2.solutionSpaces.Addable, boolean)
	 */
	public UtilitySolutionSpace<AddableInteger, U> split(U threshold,
			boolean maximum) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#augment(Addable[], java.io.Serializable)
	 */
	public void augment(AddableInteger[] variablesValues, U utilityValue) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#equivalent(frodo2.solutionSpaces.BasicUtilitySolutionSpace)
	 */
	public boolean equivalent(BasicUtilitySolutionSpace<AddableInteger, U> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return false;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getClassOfU()
	 */
	public Class<U> getClassOfU() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getDefaultUtility()
	 */
	public U getDefaultUtility() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getUtility(Addable[])
	 */
	public U getUtility(AddableInteger[] variablesValues) {
		return getUtility(this.variables, variablesValues);
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#getUtility(java.lang.String[], Addable[])
	 */
	public U getUtility(String[] variablesNames,
			AddableInteger[] variablesValues) {
		
		U util = this.infeasibleUtil.getZero();
		
		// First look up the non-VRP spaces because they are cheaper
		for(UtilitySolutionSpace<AddableInteger, U> space : inputs) {
			U util2 = space.getUtility(variablesNames, variablesValues);
			if (util2 == this.infeasibleUtil) 
				return util2;
			util = util.add(util2);
		}
		
		if(this.vrpSpace != null) {
			util = util.add(vrpSpace.getUtility(variablesNames, variablesValues));
		}
		
		return util;
	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.util.Map) */
	public U getUtility(Map<String, AddableInteger> assignments) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}

	/** 
	 * @see BasicUtilitySolutionSpace#getUtility(long)
	 */
	public U getUtility(long index) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** @see UtilitySolutionSpace#setProblem(ProblemInterface) */
	public void setProblem(ProblemInterface<AddableInteger, U> problem) {
		for (UtilitySolutionSpace<AddableInteger, U> space : this.inputs) 
			space.setProblem(problem);
		if (this.vrpSpace != null) 
			this.vrpSpace.setProblem(problem);
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#isIncludedIn(frodo2.solutionSpaces.BasicUtilitySolutionSpace)
	 */
	public boolean isIncludedIn(
			BasicUtilitySolutionSpace<AddableInteger, U> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return false;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#iterator()
	 */
	public Iterator<AddableInteger, U> iterator() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#iterator(java.lang.String[], Addable[][])
	 */
	public Iterator<AddableInteger, U> iterator(String[] variables,
			AddableInteger[][] domains) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#prettyPrint(java.io.Serializable)
	 */
	public String prettyPrint(U ignoredUtil) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setDefaultUtility(java.io.Serializable)
	 */
	public void setDefaultUtility(U utility) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** @see BasicUtilitySolutionSpace#setInfeasibleUtility(java.io.Serializable) */
	public void setInfeasibleUtility(U utility) {
		this.infeasibleUtil = utility;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#setUtility(Addable[], java.io.Serializable)
	 */
	public boolean setUtility(AddableInteger[] variablesValues, U utility) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return false;
	}

	/** 
	 * @see BasicUtilitySolutionSpace#setUtility(long, java.io.Serializable)
	 */
	public void setUtility(long index, U utility) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#augment(Addable[])
	 */
	public void augment(AddableInteger[] variablesValues) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getDomain(java.lang.String)
	 */
	public AddableInteger[] getDomain(String variable) {
		if(variablePointer.containsKey(variable))
			return domains[variablePointer.get(variable)];
		
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getDomain(int)
	 */
	public AddableInteger[] getDomain(int index) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getDomain(java.lang.String, int)
	 */
	public AddableInteger[] getDomain(String variable, int index) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getDomains()
	 */
	public AddableInteger[][] getDomains() {
		return this.domains;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getIndex(java.lang.String)
	 */
	public int getIndex(String variable) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return 0;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getName()
	 */
	public String getName() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see SolutionSpace#getNumberOfSolutions()
	 */
	public long getNumberOfSolutions() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return 0;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getNumberOfVariables()
	 */
	public int getNumberOfVariables() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return 0;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getRelationName()
	 */
	public String getRelationName() {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getVariable(int)
	 */
	public String getVariable(int index) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#getVariables()
	 */
	public String[] getVariables() {
		return this.variables;
	}

	/** 
	 * @see frodo2.solutionSpaces.BasicUtilitySolutionSpace#iterator(java.lang.String[])
	 */
	public UtilitySolutionSpace.Iterator<AddableInteger, U>  iterator(
			String[] order) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace, java.lang.String[])
	 */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space, String[] totalVariables) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace)
	 */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace[], java.lang.String[])
	 */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger>[] spaces, String[] totalVariablesOrder) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** @see SolutionSpace#knows(java.lang.Class) */
	public boolean knows(Class<?> spaceClass) {
		return knownSpaces.contains(spaceClass);
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#renameAllVars(java.lang.String[])
	 */
	public SolutionSpace<AddableInteger> renameAllVars(String[] newVarNames) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#renameVariable(java.lang.String, java.lang.String)
	 */
	public void renameVariable(String oldName, String newName) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#setDomain(java.lang.String, Addable[])
	 */
	public void setDomain(String var, AddableInteger[] dom) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#setName(java.lang.String)
	 */
	public void setName(String name) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}

	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#setRelationName(java.lang.String)
	 */
	public void setRelationName(String name) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		
	}


	/** 
	 * @see frodo2.solutionSpaces.SolutionSpace#join(frodo2.solutionSpaces.SolutionSpace[])
	 */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger>[] spaces) {
		// @todo Auto-generated method stub
		assert false : "not implemented";
		return null;
	}
	
	/** @return a shallow clone of this hypercube */
	@Override
	public CompoundSpace< U > clone () {
		assert false : "Not Implemented";
		return null;
	}


	/** 
	 * @see frodo2.solutionSpaces.UtilitySolutionSpace#min(java.lang.String)
	 */
	public UtilitySolutionSpace<AddableInteger, U> min(String variable) {
		// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean, java.lang.String[], Addable[]) */
	public UtilitySolutionSpace.IteratorBestFirst<AddableInteger, U> iteratorBestFirst(
			boolean maximize, String[] fixedVariables,
			AddableInteger[] fixedValues) {
		// @todo Auto-generated method stub
		assert false : "NotImplemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#blindProject(java.lang.String, boolean) */
	public UtilitySolutionSpace<AddableInteger, U> blindProject(String varOut,
			boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#blindProject(java.lang.String[], boolean) */
	public UtilitySolutionSpace<AddableInteger, U> blindProject(
			String[] varsOut, boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#blindProjectAll(boolean) */
	public U blindProjectAll(boolean maximize) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#projExpectMonotone(java.lang.String, java.util.Map, boolean) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput<AddableInteger, U> projExpectMonotone(String varOut, Map< String, UtilitySolutionSpace<AddableInteger, U> > distributions, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see UtilitySolutionSpace#toHypercube() */
	public Hypercube<AddableInteger, U> toHypercube() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.SolutionSpace#getOwner() */
	public String getOwner() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.SolutionSpace#setOwner(java.lang.String) */
	public void setOwner(String owner) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#iterator(java.lang.String[], Addable[][], Addable[]) */
	public frodo2.solutionSpaces.UtilitySolutionSpace.Iterator<AddableInteger, U> iterator(
			String[] variables, AddableInteger[][] domains,
			AddableInteger[] assignment) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
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


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#sparseIter() */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter() {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#sparseIter(java.lang.String[]) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter(
			String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][]) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter(
			String[] variables, AddableInteger[][] domains) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}


	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][], Addable[]) */
	@Override
	public frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter(
			String[] variables, AddableInteger[][] domains,
			AddableInteger[] assignment) {
		/// @todo Auto-generated method stub
		assert false : "Not implemented";
		return null;
	}
}
