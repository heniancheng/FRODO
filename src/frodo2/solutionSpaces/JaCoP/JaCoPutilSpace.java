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

package frodo2.solutionSpaces.JaCoP;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jdom2.Element;

import JaCoP.constraints.Sum;
import JaCoP.constraints.XplusYeqC;
import JaCoP.core.IntDomain;
import JaCoP.core.IntVar;
import JaCoP.core.IntervalDomain;
import JaCoP.core.Store;
import JaCoP.search.DepthFirstSearch;
import JaCoP.search.IndomainMin;
import JaCoP.search.InputOrderSelect;
import JaCoP.search.Search;
import frodo2.algorithms.XCSPparser;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.SolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** A UtilitySolutionSpace based on JaCoP as a local solver
 * @author Thomas Leaute, Radoslaw Szymanek
 * @param <U> the type used for utility values
 * @todo Performance improvement: don't go through XCSP but rather work directly on JaCoP's data structures. 
 */
public class JaCoPutilSpace < U extends Addable<U> > implements UtilitySolutionSpace<AddableInteger, U>, Externalizable {

	/** For now (and maybe forever) we will assume that all variables are projected in the same way (all minimized or all maximized) */
	private boolean maximize;

	/** The XCSP constraints in this space */
	private HashMap<String, Element> constraints;

	/** The JaCoP Store */
	private Store store;

	/** The consistency of the JaCoP Store */
	private boolean isConsistent;

	/** The XCSP relations in this space */
	private HashMap<String, Element> relations;

	/** The ordered names of the variables of the space*/
	private String[] vars;
	
	/** The ordered names of the variables whose projection has been requested */
	private String[] projectedVars;
	
	/** The names of the variables that have been grounded by a slice operation */
	private String[] slicedVars;

	/** The variable names, including the projected out and sliced out variables, but excluding the utility variable */
	HashMap<String, AddableInteger[]> allVars;

	/** The types of spaces that we know how to handle */
	private static HashSet< Class<?> > knownSpaces;

	static {
		knownSpaces = new HashSet< Class<?> > ();
		knownSpaces.add(JaCoPutilSpace.class);
		knownSpaces.add(ScalarHypercube.class);
	}

	/** The name of the UtilSpace **/
	private String name;

	/** The default Utility **/
	public U defaultUtil;

	/** The infeasible utility */
	public U infeasibleUtil;

	/** The owner of this space */
	private String owner;

	/** Constructor				construct an explicit JaCoPutilSpace that owns only one constraint
	 * @param name				the name of the JaCoPutilSpace corresponds to the name of its XCSP constraint
	 * @param owner 			the owner
	 * @param constraint 		a jdom element that contains the specification of the constraint
	 * @param relation 			a jdom element that contains the specification of the relation or predicate, can be null in presence of a global constraint
	 * @param varNames 			the names of the variables
	 * @param domains 			the domains of the variables
	 * @param maximize	 		whether we should maximize the utility or minimize the cost
	 * @param infeasibleUtil 	the infeasible utility
	 */
	JaCoPutilSpace (String name, String owner, Element constraint, Element relation, String[] varNames, AddableInteger[][] domains, boolean maximize, U infeasibleUtil) {
		this.name = name;
		this.owner = owner;

		this.infeasibleUtil = infeasibleUtil;
		this.defaultUtil = this.infeasibleUtil.getZero();

		this.constraints = new HashMap<String, Element> (1);
		this.constraints.put(constraint.getAttributeValue("name"), constraint);

		this.relations = new HashMap<String, Element> (0);
		if(relation != null){
			this.relations.put(relation.getAttributeValue("name"), relation);
		}

		this.maximize = maximize;

		this.allVars = new HashMap<String, AddableInteger[]>(varNames.length);

		// Construct the array of variables
		assert varNames.length == domains.length;
		for(int i = varNames.length - 1; i >= 0; i--) 
			allVars.put(varNames[i], domains[i]);

		this.vars = allVars.keySet().toArray(new String[this.allVars.size()]);
		this.projectedVars = new String[0];
		this.slicedVars = new String[0];
		this.store = null;

	}

	/** Empty constructor that does nothing */
	public JaCoPutilSpace() { }

	/** Constructor				construct an explicit scalar JaCoPutilSpace that owns only one constraint
	 * @param name				the name of the JaCoPutilSpace corresponds to the name of its XCSP constraint
	 * @param maximize	 		whether we should maximize the utility or minimize the cost
	 * @param defaultUtil 		the utility of the scalar space
	 * @param infeasibleUtil 	the infeasible utility
	 */
	JaCoPutilSpace (String name, boolean maximize, U defaultUtil, U infeasibleUtil) {
		this.name = name;

		this.infeasibleUtil = infeasibleUtil;
		this.defaultUtil = defaultUtil;

		this.constraints = new HashMap<String, Element> (0);
		this.relations = new HashMap<String, Element> (0);

		this.allVars = new HashMap<String, AddableInteger[]>(0);

		this.maximize = maximize;

		this.vars = allVars.keySet().toArray(new String[this.allVars.size()]);
		this.projectedVars = new String[0];
		this.slicedVars = new String[0];
		this.store = null;
	}


	/** Constructor				construct an explicit JaCoPutilSpace
	 * @param name				the name of the JaCoPutilSpace
	 * @param vars				the variables
	 * @param constraint 		a jdom element that contains the specification of the constraint
	 * @param relation 			a jdom element that contains the specification of the relation or predicate, can be null in presence of a global constraint 
	 * @param maximize	 		whether we should maximize the utility or minimize the cost
	 * @param infeasibleUtil 	The infeasible utility
	 */
	private JaCoPutilSpace (String name, HashMap<String, AddableInteger[]> vars, Element constraint, Element relation, boolean maximize, U infeasibleUtil) {

		this.name = name;
		this.infeasibleUtil = infeasibleUtil;
		this.defaultUtil = infeasibleUtil.getZero();

		this.constraints = new HashMap<String, Element> (1);
		this.constraints.put(constraint.getAttributeValue("name"), constraint);

		this.relations =new HashMap<String, Element> (0);
		if(relation != null){
			this.relations.put(relation.getAttributeValue("name"), relation);
		}

		this.maximize = maximize;
		this.allVars = new HashMap<String, AddableInteger[]>(vars);
		this.vars = allVars.keySet().toArray(new String[this.allVars.size()]);
		this.projectedVars = new String[0];
		this.slicedVars = new String[0];
		this.store = null;
	}

	/** Constructor				construct an implicit JaCoPutilSpace
	 * @param name				the name of the JaCoPutilSpace
	 * @param constraints 		a collection of XCSP elements that describe a constraint
	 * @param relations 		a collection of XCSP elements that describe a relation
	 * @param allVars			all the variables of the space including the projected out and sliced out variables
	 * @param vars				the variables
	 * @param projectedVars		the variables whose projection has been requested
	 * @param slicedVars 		the variables that have been sliced out
	 * @param maximize	 		whether we should maximize the utility or minimize the cost
	 * @param defaultUtil		The default utility
	 * @param infeasibleUtil 	The infeasible utility
	 */
	public JaCoPutilSpace (String name, Map<String, Element> constraints, Map<String, Element> relations, HashMap<String, AddableInteger[]> allVars, 
			String[] vars, String[] projectedVars, String[] slicedVars, boolean maximize, U defaultUtil, U infeasibleUtil) {

		this.name = name;
		this.constraints = new HashMap<String, Element>(constraints);
		this.relations = new HashMap<String, Element>(relations);
		this.maximize = maximize;
		this.infeasibleUtil = infeasibleUtil;
		this.defaultUtil = defaultUtil;
		this.allVars = new HashMap<String, AddableInteger[]>(allVars);
		this.vars = vars;
		this.projectedVars = projectedVars;
		this.slicedVars = slicedVars;
		this.store = null;
	}

	/**
	 * @return a ScalarHypercube if there is no variable left, or the current object
	 */
	public Object readResolve(){
		if(this.allVars.size() == 0){
			return new ScalarHypercube<AddableInteger, U>(this.defaultUtil, this.infeasibleUtil, new AddableInteger [0].getClass());
		}
		
		return this;
	}
	
	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	///@todo change the whole method for optimizing purpose: do a master-slave JaCoP search to get only the feasible tuples,
	// then pass only those with their location and a default utility that is the infeasible utility 
	public void writeExternal(ObjectOutput out) throws IOException {
		// This function writes almost the same amount of data as the writeExternal of the corresponding hypercube would do

		// Write the name of the space and relation
		String newName = "explicit" + new Object().hashCode();
		out.writeObject(newName);
		out.writeObject("r_" + newName); /// @bug Write the name of the underlying relation (if any), else null

		// Write the type of optimization
		out.writeBoolean(maximize);

		// Write the variables
		assert this.vars.length < Short.MAX_VALUE : "Too many variables to fit in a short";
		out.writeShort(this.vars.length);
		for (int i = 0; i < this.vars.length; i++) 
			out.writeObject(this.vars[i]);

		/// @todo Write less data by taking advantage of the fact that we know we are using AddableIntegers

		// Write the domains
		assert this.vars.length < Short.MAX_VALUE : "Too many domains to fit in a short";
		out.writeShort(this.vars.length); // number of domains
		AddableInteger[] dom = this.allVars.get(vars[0]);
		assert dom.length < Short.MAX_VALUE : "Too many values to fit in a short";
		out.writeShort(dom.length); // size of first domain
		out.writeObject(dom[0]); // first value of first domain
		final boolean externalize = dom[0].externalize();
		for (int i = 1; i < dom.length; i++) { // remaining values in first domain
			if (externalize) 
				dom[i].writeExternal(out);
			else 
				out.writeObject(dom[i]);
		}
		for (int i = 1; i < this.vars.length; i++) { // remaining domains
			dom = this.allVars.get(vars[i]);
			assert dom.length < Short.MAX_VALUE : "Too many values to fit in a short";
			out.writeShort(dom.length); // size of domain
			for (int j = 0; j < dom.length; j++) { // each value in the domain
				if (externalize) 
					dom[j].writeExternal(out);
				else 
					out.writeObject(dom[j]);
			}
		}

		out.writeObject(this.infeasibleUtil);

		long nbrSol = this.getNumberOfSolutions();
		assert nbrSol < Integer.MAX_VALUE : "Cannot extensionalize a space containing more than " + Integer.MAX_VALUE + " solutions";
		out.writeInt((int)nbrSol); // number of utilities
		for(Iterator<AddableInteger, U> iter = this.iterator(); iter.hasNext(); ) 
			out.writeObject(iter.nextUtility()); // each utility
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws IOException,
	ClassNotFoundException {
		// Construct the data structures that are special to JaCoPutilSpace

		this.allVars = new HashMap<String, AddableInteger[]>();
		this.constraints = new HashMap<String, Element>(1);
		this.relations = new HashMap<String, Element>(1);
		this.projectedVars = new String[0];
		this.slicedVars = new String[0];

		this.name = (String) in.readObject();
		String relationName = (String) in.readObject();

		// Read the type of optimization
		this.maximize = in.readBoolean();

		// Read the variables
		this.vars = new String[in.readShort()];
		for (int i = 0; i < this.vars.length; i++) 
			this.vars[i] = (String) in.readObject();

		// Read the domains
		final int nbrDoms = in.readShort(); // number of domains
		int domSize = in.readShort(); // size of first domain
		AddableInteger val = (AddableInteger) in.readObject(); // first value of first domain
		final boolean externalize = val.externalize();
		AddableInteger[] dom = (AddableInteger[]) Array.newInstance(val.getClass(), domSize);
		dom[0] = val;
		for (int i = 1; i < domSize; i++) { // read the remaining values in the first domain
			if (externalize) {
				val = val.getZero();
				val.readExternal(in);
				dom[i] = (AddableInteger) val.readResolve();
			} else 
				dom[i] = (AddableInteger) in.readObject();
		}
		this.allVars.put(vars[0], dom);
		for (int i = 1; i < nbrDoms; i++) { // read the remaining domains
			domSize = in.readShort(); // domain size
			dom = (AddableInteger[]) Array.newInstance(val.getClass(), domSize);
			for (int j = 0; j < domSize; j++) { // each value in the domain
				if (externalize) {
					val = val.getZero();
					val.readExternal(in);
					dom[j] = (AddableInteger) val.readResolve();
				} else 
					dom[j] = (AddableInteger) in.readObject();
			}
			this.allVars.put(vars[i], dom);
		}
		this.infeasibleUtil = (U) in.readObject();
		this.defaultUtil = this.infeasibleUtil.getZero();

		//@todo The following code is highly redundant with the code in resolve()

		// Add the constraint
		this.constraints.put("c_" + name, XCSPparser.getConstraint(this, "c_" + name, relationName));

		// Create the relation
		Element rel = new Element("relation");
		rel.setAttribute("name", relationName);
		rel.setAttribute("arity", Integer.toString(vars.length));
		rel.setAttribute("semantics", "soft");
		// Read the utilities
		final int nbrSol = in.readInt();

		rel.setAttribute("nbTuples", Integer.toString(nbrSol));

		StringBuilder builder = new StringBuilder ("\n");

		int[] indexes = new int[vars.length];
		Arrays.fill(indexes, 0);

		boolean hasIncr;
		for (int i = 0; i < nbrSol; i++){
			hasIncr = false;
			// Read the utility
			builder.append((U) in.readObject()); /// @todo externalize if possible
			builder.append(":");

			AddableInteger[] domain;
			// Add the tuples of the relation
			for (int j = 0; j < vars.length; j++){
				domain = allVars.get(vars[j]);
				builder.append(domain[indexes[j]] + " ");
			}

			// Get the correct values of the variables for the next tuple
			for (int j = vars.length-1; j >= 0; j--){
				domain = allVars.get(vars[j]);
				if(!hasIncr){
					if(indexes[j] == domain.length-1){
						indexes[j] = 0;
					}else{
						indexes[j]++;
						hasIncr = true;
					}
				}
			}	

			if(i == nbrSol-1) break;

			builder.append("|");
		}
		rel.addContent(builder.toString());

		// Add the relation
		this.relations.put(relationName, rel);
	}

	/** @see java.lang.Object#toString() */
	public String toString () {
		StringBuilder builder = new StringBuilder ("JaCoPutilSpace \n");
		builder.append("\t variables:         " + Arrays.toString(this.vars) + "\n");
		builder.append("\t projectedVars:     " + Arrays.toString(this.projectedVars) + "\n");
		builder.append("\t slicedVars:     " + Arrays.toString(this.slicedVars) + "\n");
		
		builder.append("\t constraints:       ");
		for(Element cons: this.constraints.values()){
			builder.append(cons.getAttributeValue("name") + " ");
		}
		builder.append("\n");
		
//		builder.append("\t relations:\n");
//		for(Element rel: this.relations.values()){
//			builder.append("\t\t" + XCSPparser.toString(rel) + "\n");
//		}

		builder.append("\t maximize:          " + this.maximize + "\n");
		builder.append("\t baseUtil:    " + this.defaultUtil + "\n");
		builder.append("\t infeasibleUtil:    " + this.infeasibleUtil);
		return builder.toString();
	}

	/** @see UtilitySolutionSpace#changeVariablesOrder(java.lang.String[]) */
	public UtilitySolutionSpace<AddableInteger, U> changeVariablesOrder(
			String[] variablesOrder) {

		JaCoPutilSpace<U> out = new JaCoPutilSpace<U> (this.name + "_reordered", this.constraints, this.relations, this.allVars, this.vars, this.projectedVars, this.slicedVars, this.maximize, this.defaultUtil, this.infeasibleUtil);
		out.vars = variablesOrder;

		return out;

	}

	/** @see Object#clone() */
	public UtilitySolutionSpace<AddableInteger, U> clone() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#compose(java.lang.String[], BasicUtilitySolutionSpace) */
	public UtilitySolutionSpace<AddableInteger, U> compose(
			String[] vars,
			BasicUtilitySolutionSpace<AddableInteger, ArrayList<AddableInteger>> substitution) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#consensus(java.lang.String, java.util.Map, boolean) */
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensus(
			String varOut,
			Map<String, UtilitySolutionSpace<AddableInteger, U>> distributions,
			boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#consensusAllSols(java.lang.String, java.util.Map, boolean) */
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> consensusAllSols(
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

	/** @see frodo2.solutionSpaces.UtilitySolutionSpace#expectation(java.util.Map) */
	public UtilitySolutionSpace<AddableInteger, U> expectation(Map< String, UtilitySolutionSpace<AddableInteger, U> > distributions) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#isIncludedIn(UtilitySolutionSpace) */
	public boolean isIncludedIn(
			UtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return false;
	}

	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean) */
	public UtilitySolutionSpace.IteratorBestFirst<AddableInteger, U> iteratorBestFirst(
			boolean maximize) {

		return new JaCoPutilSpaceIterBestFirst<U> (this, maximize);
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<AddableInteger, U> join(
			UtilitySolutionSpace<AddableInteger, U> space,
			String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace) */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace<AddableInteger, U> join(
			UtilitySolutionSpace<AddableInteger, U> space) {
		return this.join(new UtilitySolutionSpace[] { space });
	}

	/** @see UtilitySolutionSpace#join(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<AddableInteger, U> join(
			UtilitySolutionSpace<AddableInteger, U>[] spaces) {


		// Build the lists of constraints, utility variables, and projected variables
		HashMap<String, Element> newConst = new HashMap<String, Element> ();
		HashMap<String, Element> newRel = new HashMap<String, Element> ();
		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]> (this.allVars);
		HashSet<String> newVars = new HashSet<String>();
		HashSet<String> newProjectedVars = new HashSet<String>();
		HashSet<String> newSlicedVars = new HashSet<String>();

		// Go through all spaces
		ArrayList< UtilitySolutionSpace<AddableInteger, U> > allSpaces = new ArrayList< UtilitySolutionSpace<AddableInteger, U> > (spaces.length + 1);
		allSpaces.add(this);
		allSpaces.addAll(Arrays.asList(spaces));
		U newDefaultUtil = this.defaultUtil;
		for (UtilitySolutionSpace<AddableInteger, U> space : allSpaces) {

			if(space instanceof ScalarHypercube<?,?>) 
				newDefaultUtil.add(((ScalarHypercube<AddableInteger,U>)space).getUtility(0));
			else if (space instanceof Hypercube<?, ?>) 
				return this.toHypercube().join(spaces);
			else{

				// First cast the space to a JaCoPutilSpace
				assert space instanceof JaCoPutilSpace<?> : "All spaces must be JaCoPutilSpaces";
				JaCoPutilSpace<U> spaceCast = null;
				try {
					spaceCast = (JaCoPutilSpace<U>) space;
				} catch (ClassCastException e) { }
				
				/// @bug If the current space is supposed to project out any variable contained in another space, 
				/// it must do so before the join is performed, by calling resolve(). This should normally never happen, though. 
				assert Collections.disjoint(Arrays.asList(this.projectedVars), Arrays.asList(spaceCast.vars));
				
				newDefaultUtil.add(spaceCast.defaultUtil);
				newConst.putAll(spaceCast.constraints);
				newRel.putAll(spaceCast.relations);
				newAllVars.putAll(spaceCast.allVars);
				newVars.addAll(Arrays.asList(spaceCast.vars));
				newProjectedVars.addAll(Arrays.asList(spaceCast.projectedVars));
				newSlicedVars.addAll(Arrays.asList(spaceCast.slicedVars));
			}
		}
		
		assert Collections.disjoint(newProjectedVars, newVars);

		UtilitySolutionSpace<AddableInteger, U> out;
		
		// Joining only ScalarHypercube results in a ScalarHypercube
		if(newAllVars.size() == 0){
			out = new ScalarHypercube<AddableInteger, U>(newDefaultUtil, this.infeasibleUtil, new AddableInteger [0].getClass());
		}else{
			out = new JaCoPutilSpace<U> ("joined" + new Object().hashCode(), newConst, newRel, newAllVars,
					newVars.toArray(new String[newVars.size()]), newProjectedVars.toArray(new String[newProjectedVars.size()]),
					newSlicedVars.toArray(new String[newSlicedVars.size()]), this.maximize, newDefaultUtil, this.infeasibleUtil);
		}
		return out;
	}

	/** @see UtilitySolutionSpace#joinMinNCCCs(UtilitySolutionSpace) */
	public UtilitySolutionSpace<AddableInteger, U> joinMinNCCCs(
			UtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#joinMinNCCCs(UtilitySolutionSpace[]) */
	public UtilitySolutionSpace<AddableInteger, U> joinMinNCCCs(
			UtilitySolutionSpace<AddableInteger, U>[] spaces) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#multiply(UtilitySolutionSpace, java.lang.String[]) */
	public UtilitySolutionSpace<AddableInteger, U> multiply(
			UtilitySolutionSpace<AddableInteger, U> space,
			String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#multiply(UtilitySolutionSpace) */
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
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(String[] vars, boolean maximum) {

		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]> (this.allVars);
		String newVars[] = new String[this.vars.length - vars.length];
		String newProjectedVars[] = Arrays.copyOf(this.projectedVars, this.projectedVars.length + vars.length);
		String newSlicedVars[] = this.slicedVars;

		// move the projected vars from vars to projectedVars
		int i = 0;
		HashSet<String> inVars = new HashSet<String> (Arrays.asList(vars));
		for(String v: this.vars){
			if(inVars.contains(v))
				continue;
			newVars[i] = v;
			i++;
		}
		System.arraycopy(vars, 0, newProjectedVars, this.projectedVars.length, vars.length);
		
		// For now (and maybe forever) we will assume that all variables are projected in the same way (all minimized or all maximized)
		assert (this.maximize == maximum) : "All variables must be projected the same way!";

		JaCoPutilSpace<U> outSpace = new JaCoPutilSpace<U> (this.getName() + "projected", this.constraints, this.relations, newAllVars,
				newVars, newProjectedVars, newSlicedVars, this.maximize, this.defaultUtil, this.infeasibleUtil);
		JaCoPoptAssignments assignments = new JaCoPoptAssignments (this, outSpace.vars, vars);

		ProjOutput<AddableInteger, U> out = new ProjOutput<AddableInteger, U> (outSpace, vars, assignments);
		
		return out;
	}

	/** @see UtilitySolutionSpace#project(int, boolean) */
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> project(
			int numberToProject, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#project(java.lang.String, boolean) */
	public ProjOutput<AddableInteger, U> project(String variableName, boolean maximum) {

		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]> (this.allVars);
		String newVars[] = new String[this.vars.length-1];
		String newProjectedVars[] = Arrays.copyOf(this.projectedVars, this.projectedVars.length + 1);
		String newSlicedVars[] = this.slicedVars;

		// move the projected var from vars to projectedVars
		int i = 0;
		for(String v: this.vars){
			if(v.equals(variableName))
				continue;
			newVars[i] = v;
			i++;
		}
		newProjectedVars[this.projectedVars.length] = variableName;
		
		// For now (and maybe forever) we will assume that all variables are projected in the same way (all minimized or all maximized)
		assert (this.maximize == maximum) : "All variables must be projected the same way!";

		JaCoPutilSpace<U> outSpace = new JaCoPutilSpace<U> (this.getName() + "projected", this.constraints, this.relations, newAllVars,
				newVars, newProjectedVars, newSlicedVars, this.maximize, this.defaultUtil, this.infeasibleUtil);
		JaCoPoptAssignments assignments = new JaCoPoptAssignments (this, outSpace.vars, new String[] {variableName});

		ProjOutput<AddableInteger, U> out = new ProjOutput<AddableInteger, U> (outSpace, 
				new String[] {variableName}, 
				assignments);
		
		return out;
	}

	/** @see UtilitySolutionSpace#projectAll(boolean) */
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> projectAll(boolean maximum) {

		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]> (this.allVars);
		String newVars[] = new String[0];
		String newProjectedVars[] = Arrays.copyOf(this.projectedVars, this.projectedVars.length + this.vars.length);
		String newSlicedVars[] = this.slicedVars;

		// move all variables from vars to projectedVars
		int i = this.projectedVars.length;
		for (String var : this.getVariables()) 
			newProjectedVars[i++] = var;

		// For now (and maybe forever) we will assume that all variables are projected in the same way (all minimized or all maximized)
		assert (this.maximize == maximum) : "All variables must be projected the same way!";

		JaCoPutilSpace<U> outSpace = new JaCoPutilSpace<U> (this.getName() + "projected", this.constraints, this.relations, newAllVars,
				newVars, newProjectedVars, newSlicedVars, this.maximize, this.defaultUtil, this.infeasibleUtil);
		JaCoPoptAssignments assignments = new JaCoPoptAssignments (this, outSpace.vars, this.getVariables());

		ProjOutput<AddableInteger, U> out = new ProjOutput<AddableInteger, U> (outSpace, 
				this.getVariables(), 
				assignments);

		return out;
	}

	/** @see UtilitySolutionSpace#projectAll(boolean, java.lang.String[]) */
	public UtilitySolutionSpace.ProjOutput<AddableInteger, U> projectAll(
			boolean maximum, String[] order) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#resolve() */
	public UtilitySolutionSpace<AddableInteger, U> resolve() {	
	
		if(vars.length == 0){ // No variable, we construct a scalar hypercube
			U cost = this.getUtility(0);
			return new ScalarHypercube<AddableInteger, U>(cost, this.infeasibleUtil, new AddableInteger [0].getClass());
		}

		// The constraint and relation resulting from the projections
		String newName = "explicit" + new Object().hashCode();
		Element newCons = new Element ("constraint");
		newCons.setAttribute("name", "c_" + newName);
		newCons.setAttribute("arity", String.valueOf(vars.length));

		// Generate the scope
		String scope = "";
		for (String var : vars) 
			scope += var + " ";
		newCons.setAttribute("scope", scope.trim());

		newCons.setAttribute("reference", "r_" + newName);

		// Create the relation
		Element newRel = new Element("relation");
		newRel.setAttribute("name", "r_" + newName);
		newRel.setAttribute("arity", Integer.toString(vars.length));
		newRel.setAttribute("semantics", "soft");
		newRel.setAttribute("defaultCost", this.infeasibleUtil.toString());
		
		// The variables
		HashMap<String, AddableInteger[]> newVars = new HashMap<String, AddableInteger[]>();
		for(String v: this.vars) 
			newVars.put(v, this.allVars.get(v));

		// If the store does not exist yet, we create it
		if(this.store == null){
			this.store = createStore();
			this.isConsistent = store.consistency();
		}
		
		if(!isConsistent){
			newRel.setAttribute("nbTuples", "0");
			return new JaCoPutilSpace<U> (newName, newVars,  newCons, newRel, this.maximize, this.infeasibleUtil);
		}

		// Find the JaCoP Variables in the store
		int n = 0;
		IntVar[] vars = new IntVar[this.vars.length];
		for(String var: this.vars){
			vars[n] = (IntVar)store.findVariable(var);
			assert vars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}

		// Find the projected variables in the store
		IntVar[] projectedVars = new IntVar[this.projectedVars.length];
		n = 0;
		for(String var: this.projectedVars){
			projectedVars[n] = (IntVar) store.findVariable(var);
			assert projectedVars[n] != null: "Variable " + var + " not found in the store!";
			n++;
		}

		IntVar utilVar = (IntVar)store.findVariable("util_total");

		// Method 1: We first record all solutions of the master search, and then assign one solution by one to optimize.

		///@todo Method 2: use JaCoP Listeners to be able to avoid saving all the solutions of the first search!

		// Create the master search
		Search<IntVar> masterSearch = new DepthFirstSearch<IntVar> ();
		masterSearch.getSolutionListener().recordSolutions(true);
		masterSearch.getSolutionListener().searchAll(true);
		masterSearch.setAssignSolution(false);

		// Debug information
		masterSearch.setPrintInfo(false);
		boolean result = masterSearch.labeling(store, new InputOrderSelect<IntVar> (store, vars, new IndomainMin<IntVar>()));

		if(!result){
			// No solution
			newRel.setAttribute("nbTuples", "0");
			return new JaCoPutilSpace<U> (newName, newVars,  newCons, newRel, this.maximize, this.infeasibleUtil);
		}

		StringBuilder builder = new StringBuilder ("\n");
		
		// Create the heuristic for the slave search
		InputOrderSelect<IntVar> slaveHeuristic;
		if(projectedVars.length != 0) // we need to project some variables
			slaveHeuristic = new InputOrderSelect<IntVar> (store, projectedVars, new IndomainMin<IntVar>());
		else 
			slaveHeuristic = new InputOrderSelect<IntVar> (store, new IntVar[] { utilVar }, new IndomainMin<IntVar>());

		int nbSol = 0;
		int lvlReminder = store.level;
		for (int i=1; i<=masterSearch.getSolutionListener().solutionsNo(); i++){
			// Unfortunately, one cannot say that each feasible solution of the masterSearch will really give a feasible solution in the second search
			// Indeed, this case can happen with the intensional constraints. 

			store.setLevel(lvlReminder+1);
			masterSearch.assignSolution(i-1);

			// Optimization search
			Search<IntVar> slaveSearch = new DepthFirstSearch<IntVar> ();
			slaveSearch.getSolutionListener().recordSolutions(true);
			slaveSearch.getSolutionListener().searchAll(false);
			slaveSearch.setAssignSolution(false);


			// Debug information
			slaveSearch.setPrintInfo(false);

			result = slaveSearch.labeling(store, slaveHeuristic, utilVar);
			
			if(!result){
				// No solution for this specific assignment
			}else{

				nbSol++;
				//slaveSearch.getSolution(0);
				int cost = slaveSearch.getCostValue();
				// If it is a maximization problem
				if(this.maximize == true){
					cost *= -1;
				}

				// Write the utility
				builder.append(this.defaultUtil.intValue() + cost);
				builder.append(":");

				for (int j=0; j< vars.length; j++){
					assert vars[j].singleton(): "problem with JaCoP's solution";
					builder.append(vars[j].value() + " ");
				}

				if(i<masterSearch.getSolutionListener().solutionsNo())
					builder.append("|");

			}

			for(int k = store.level; k > lvlReminder; k--){
				store.removeLevel(k);
			}
		}
		
		store.setLevel(lvlReminder);
		
		newRel.setAttribute("nbTuples", String.valueOf(nbSol));
		newRel.addContent(builder.toString());
		
		return new JaCoPutilSpace<U> (newName, newVars, newCons, newRel, this.maximize, this.infeasibleUtil);
	}

	/**
	 * This method constructs the store and creates all the JaCoP variables needed in it as well as all the JaCoP constraints.
	 * This method is call only once, when we need the store and it has not been created yet. It's not located in the constructor because
	 * constructing the whole store is costly, and we do not always need to perform operations on it.
	 * 
	 * @return the store in which we have created all the variables and imposed all the constraints.
	 */
	public Store createStore() {

		// Create the JaCoP Store
		Store store = new Store();
		store.setID("store" + new Object().hashCode());

		AddableInteger[] dom;
		IntervalDomain jacopDom;

		// Create all the JaCoP variables in the store
		IntVar[] vars = new IntVar[this.allVars.size()];
		int n = 0;
		for(String var: this.allVars.keySet()){

			// Construct the domain
			dom = this.allVars.get(var);
			jacopDom = new IntervalDomain (dom.length);
			for (AddableInteger val : dom){
				jacopDom.addDom(new IntervalDomain (val.intValue(), val.intValue()));
			}
			// Construct the JaCoP variable
			vars[n] = new IntVar (store, var, jacopDom);

			n++;
		}

		ArrayList<IntVar> utilVars = new ArrayList<IntVar>();

		// Parse and impose the constrains
		for (Element constraint : this.constraints.values()){
			int arity = Integer.valueOf(constraint.getAttributeValue("arity"));

			String reference = constraint.getAttributeValue("reference");

			// Global constraint
			if(constraint.getAttributeValue("reference").startsWith("global:")){

				JaCoPxcspParser.parseGlobalConstraint(constraint, store);

			}else{

				Element currentRelation = relations.get(reference);

				assert (currentRelation != null): "relation/predicate referenced in the constraint not found!";

				if(currentRelation.getName().equals("relation")){

					assert (arity == Integer.valueOf(currentRelation.getAttributeValue("arity"))) : "The relation referenced by the constraint is not of the same arity!";
					JaCoPxcspParser.parseRelation(constraint, currentRelation, store, utilVars);


				}else if(currentRelation.getName().equals("predicate") || currentRelation.getName().equals("function")){


					JaCoPxcspParser.parsePredicate(constraint, currentRelation, store, utilVars);


				}else if(currentRelation.getName().equals("global constraint")){
					assert false: "to implement";
				}else{
					assert false: "unknown XCSP contraint reference " + currentRelation.getName();
				}

			}


		}

		// First construct the domain for the sum variable
		/// @todo Properly compute the exact domain, not just a super-interval?
		int min = 0, max = 0;
		for (IntVar var : utilVars) {
			// It is possible that a relation cannot be satisfied. Hence the variable corresponding to its utility will have an empty domain
			if(!var.dom().isEmpty()){
				min += ((IntDomain)var.dom()).min();
				max += ((IntDomain)var.dom()).max();
			}
		}

		IntervalDomain sumDom = new IntervalDomain (min, max);


		// If it is a minimization problem, the total cost is simply the sum of the utility variables.
		if(this.maximize == false){
			// Construct the sum variable
			IntVar utilVar = new IntVar (store, "util_total", sumDom);
			store.impose(new Sum (utilVars, utilVar));
			// If it is a maximization problem, we need to create the total utility variable (that is the negation of the total cost variable)
			// to be able to convert the problem into a minimization one as JaCoP can only handle those.
		}else{
			// Construct the sum variable
			IntVar utilVar = new IntVar (store, "cost_total", sumDom);
			store.impose(new Sum (utilVars, utilVar));

			// @todo compute the exact opposite domain of sumDom
			IntervalDomain oppDom = new IntervalDomain(IntDomain.MinInt,IntDomain.MaxInt);
			if(min == 0 && max == 0){
				oppDom = new IntervalDomain (0, 0);
			}
			IntVar utilNegation = new IntVar(store, "util_total", oppDom);
			store.impose(new XplusYeqC(utilVar, utilNegation, 0));
		}

		return store;
	}

	/** @see UtilitySolutionSpace#sample(int) */
	public Map<AddableInteger, Double> sample(int nbrSamples) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String[], Addable[][]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			String[] variablesNames, AddableInteger[][] subDomains) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String[], Addable[]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			String[] variablesNames, AddableInteger[] values) {
		
		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]>(this.allVars);
		HashSet<String> newVars = new HashSet<String>(Arrays.asList(this.vars));
		ArrayList<String> newSlicedVars = new ArrayList<String>(Arrays.asList(this.slicedVars));
		
		AddableInteger[] domain;
		boolean contains = false;
		for(int i = 0 ; i < variablesNames.length; i++){

			contains = newVars.remove(variablesNames[i]);

			if (contains) {
				newSlicedVars.add(variablesNames[i]);
				domain = new AddableInteger[1];
				domain[0] = values[i];
				newAllVars.put(variablesNames[i], domain);
			}
		}
		
		JaCoPutilSpace<U> out = new JaCoPutilSpace<U> ("sliced" + new Object().hashCode(), this.constraints, this.relations, newAllVars,
				newVars.toArray(new String[newVars.size()]),Arrays.copyOf(this.projectedVars, this.projectedVars.length),
				newSlicedVars.toArray(new String[newSlicedVars.size()]), this.maximize, this.defaultUtil, this.infeasibleUtil);
		
		return out;
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String, Addable[]) */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			String var, AddableInteger[] subDomain) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#slice(java.lang.String, Addable) */
	public UtilitySolutionSpace<AddableInteger, U> slice(
			String var, AddableInteger val) {
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

	/** @see UtilitySolutionSpace#split(Addable, boolean) */
	public UtilitySolutionSpace<AddableInteger, U> split(
			U threshold, boolean maximum) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see BasicUtilitySolutionSpace#augment(Addable[], java.io.Serializable) */
	public void augment(AddableInteger[] variablesValues,
			U utilityValue) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}
	
	/** @see java.lang.Object#hashCode() */
	@Override
	public int hashCode () {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return 0;
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals (Object o) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see BasicUtilitySolutionSpace#equivalent(BasicUtilitySolutionSpace) */
	public boolean equivalent(BasicUtilitySolutionSpace<AddableInteger, U> space) {
		
		if (space == null) 
			return false;
		
		Iterator<AddableInteger, U> myIter = this.iterator(this.getVariables(), this.getDomains());
		BasicUtilitySolutionSpace.Iterator<AddableInteger, U> otherIter = space.iterator(this.getVariables(), this.getDomains());
		
		if (myIter.getNbrSolutions() != otherIter.getNbrSolutions()) 
			return false;
		
		while (myIter.hasNext()) 
			if (! myIter.nextUtility().equals(otherIter.nextUtility())) 
				return false;
		
		return true;
	}

	/** @see BasicUtilitySolutionSpace#getClassOfU() */
	public Class<U> getClassOfU() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see BasicUtilitySolutionSpace#getDefaultUtility() */
	public U getDefaultUtility() {
		return this.infeasibleUtil.getZero();
	}

	/** @see BasicUtilitySolutionSpace#getUtility(Addable[]) */
	@SuppressWarnings("unchecked")
	public U getUtility(AddableInteger[] variablesValues) {
		// The input does not specify a value for each variable
		if(variablesValues.length < vars.length){
			return this.infeasibleUtil.getZero();
		}
		
		if(this.defaultUtil.equals(this.infeasibleUtil)){
			return this.infeasibleUtil;
		}

		// If this is a scalar space
		if(this.allVars.size() == 0 || constraints.isEmpty()){
			return this.defaultUtil;
		}

		// If the store does not exist yet, we create it
		if(this.store == null){
			this.store = createStore();
			isConsistent = store.consistency();
		}
		
		if(!isConsistent){
			return this.infeasibleUtil;
		}

		int lvlReminder = store.level;

		// Change the store level to be able to ground the variable in an reversible manner
		store.setLevel(lvlReminder+1);
		IntVar vars[] = new IntVar[this.vars.length];
		for(int i = 0; i < variablesValues.length; i++){
			// Find the variable in the store
			vars[i] = (IntVar)store.findVariable(this.vars[i]);
			assert vars[i] != null: "Variable " + this.vars[i] + " not found in the store!";
			assert variablesValues[i] != null : "null value passed for variable " + this.vars[i];

			// We ground the variables in the separator
			try{
				vars[i].domain.in(lvlReminder+1, vars[i], variablesValues[i].intValue(), variablesValues[i].intValue());
			}catch (JaCoP.core.FailException e){

				for(int k = store.level; k > lvlReminder; k--){
					store.removeLevel(k);
				}

				store.setLevel(lvlReminder);

				return this.infeasibleUtil;				
			}
		}

		U costValue = this.infeasibleUtil;

		if(store.consistency()){
			
			IntVar utilVar = (IntVar) store.findVariable("util_total");
			assert utilVar != null: "Variable " + "util_total" + " not found in the store!";

			if(this.projectedVars.length == 0){
				IntVar[] util = {utilVar};

				// Optimization search
				Search<IntVar> search = new DepthFirstSearch<IntVar> ();
				search.getSolutionListener().recordSolutions(true);
				search.setAssignSolution(false);

				// Debug information
				search.setPrintInfo(false);

				boolean result = search.labeling(store, new InputOrderSelect<IntVar> (store, util, new IndomainMin<IntVar>()));

				if(!result){
					// The solution given in argument is inconsistent!
					costValue = this.infeasibleUtil;
				}else{

					int cost = search.getSolution()[0].valueEnumeration().nextElement();

					// If it is a maximization problem
					if(this.maximize == true){
						cost *= -1;
					}

					try {
						costValue = (U) this.defaultUtil.getClass().getConstructor(int.class).newInstance(cost);
					} catch (Exception e) {
						e.printStackTrace();
					}

				}


			}else{
				// Find the variables that were already projected (the ones whose solution values we want) in the store
				IntVar projectedVars[] = new IntVar[this.projectedVars.length];
				int n = 0;
				for(String var: this.projectedVars){

					// Find the JaCoP variable
					projectedVars[n] = (IntVar) store.findVariable(var);
					assert projectedVars[n] != null: "Variable " + var + " not found in the store!";
					n++;

				}

				// We add the utilVar to the list of searched variables, this is not mandatory but this will prevent JaCoP from crashing if there is no projected variable!
				//searchedVars[this.projectedVars.size()] = utilVar;

				// Optimization search
				Search<IntVar> search = new DepthFirstSearch<IntVar> ();
				search.getSolutionListener().recordSolutions(false);
				search.setAssignSolution(false);

				// Debug information
				search.setPrintInfo(false);
				
				boolean result = search.labeling(store, new InputOrderSelect<IntVar> (store, projectedVars, new IndomainMin<IntVar>()), utilVar);

				if(!result){
					// The solution given in argument is inconsistent!
					costValue = this.infeasibleUtil;
				}else{

					int cost = search.getCostValue();

					// If it is a maximization problem
					if(this.maximize == true){
						cost *= -1;
					}

					try {
						costValue = (U) this.defaultUtil.getClass().getConstructor(int.class).newInstance(cost);
					} catch (Exception e) {
						e.printStackTrace();
					}

				}
			}
		}

		// Store backtrack
		for(int k = store.level; k > lvlReminder; k--){
			store.removeLevel(k);
		}

		store.setLevel(lvlReminder);
		
		return costValue.add(this.defaultUtil);

	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.lang.String[], Addable[]) */
	public U getUtility(String[] variablesNames,
			AddableInteger[] variablesValues) {

		assert variablesNames.length >= this.vars.length;
		assert variablesNames.length == variablesValues.length;

		//Note: "variables_names" and "variables_values" may contain variables that are not present in this hypercube but must 
		//provide a value for each variable of this space otherwise a null is returned.

		AddableInteger[] assignment = new AddableInteger[vars.length];
		final int variables_size = variablesNames.length;
		final int variables_size2 = vars.length;

		// loop over all the variables present in the array "variablesNames"
		String var;
		ext: for(int i = 0; i < variables_size2; i++){
			var = this.vars[i];
			for(int j = 0; j < variables_size; j++){
				if( var.equals(variablesNames[j])) {
					assignment[i] = variablesValues[j];
					continue ext;
				}
			}

			// No value found for variable i
			return this.infeasibleUtil.getZero();
		}

		return getUtility(assignment);
	}

	/** @see BasicUtilitySolutionSpace#getUtility(java.util.Map) */
	public U getUtility(Map<String, AddableInteger> assignments) {

		// obtain the correct values array that corresponds to the index
		AddableInteger[] values = new AddableInteger[vars.length];
		for(int i = 0; i < vars.length; i++){
			values[i] = assignments.get(vars[i]);
			if(values[i] == null) {
				return this.infeasibleUtil.getZero();
			}
		}

		return getUtility(values);
	}

	/** @see BasicUtilitySolutionSpace#getUtility(long) */
	public U getUtility(long index) {

		// obtain the correct values array that corresponds to the index
		AddableInteger[] values = new AddableInteger[vars.length];
		AddableInteger[] domain;
		long location = this.getNumberOfSolutions();
		int indice;
		for(int i = 0; i < vars.length; i++){

			domain = allVars.get(vars[i]);
			location = location/domain.length;

			indice = (int) (index/location);
			index = index % location;

			values[i] = domain[indice];
		}
		return getUtility(values);
	}

	/** @see BasicUtilitySolutionSpace#isIncludedIn(BasicUtilitySolutionSpace) */
	public boolean isIncludedIn(
			BasicUtilitySolutionSpace<AddableInteger, U> space) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return false;
	}

	/** @see UtilitySolutionSpace#iterator() */
	@Override
	public UtilitySolutionSpace.Iterator<AddableInteger, U> iterator() {
		return (UtilitySolutionSpace.Iterator<AddableInteger, U>) this.iterator(false);
	}

	/** @see UtilitySolutionSpace#sparseIter() */
	@Override
	public UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter() {
		return this.iterator(true);
	}

	/** Returns an iterator
	 * @param sparse 	whether the iterator should skip infeasible solutions
	 * @return an iterator
	 */
	private UtilitySolutionSpace.SparseIterator<AddableInteger, U> iterator(final boolean sparse) {

		AddableInteger[][] variables_domain = new AddableInteger[vars.length][];

		for(int i = 0; i < vars.length; i++){
			variables_domain[i] = allVars.get(vars[i]);
		}

		if (sparse) 
			return new JaCoPutilSpaceIter2<U> (this, this.vars, variables_domain);
		else 
			return new JaCoPutilSpaceIter<U> (this, this.vars, variables_domain);
	}

	/** @see UtilitySolutionSpace#iterator(java.lang.String[], Addable[][], Addable[]) */
	@Override
	public UtilitySolutionSpace.Iterator<AddableInteger, U> iterator(String[] variables, AddableInteger[][] domains, AddableInteger[] assignment) {
		return (UtilitySolutionSpace.Iterator<AddableInteger, U>) this.iterator(variables, domains, assignment, false);
	}
	
	/** @see UtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][], Addable[]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter(String[] variables, AddableInteger[][] domains, AddableInteger[] assignment) {
		return this.iterator(variables, domains, assignment, true);
	}
	
	/** Returns an iterator
	 * @param variables 	The variables to iterate over
	 * @param domains		The domains of the variables over which to iterate
	 * @param assignment 	An array that will be used as the output of nextSolution()
	 * @param sparse 		Whether to return an iterator that skips infeasible solutions
	 * @return an iterator which allows to iterate over the given variables and their utilities 
	 */
	private UtilitySolutionSpace.SparseIterator<AddableInteger, U> 
	iterator(String[] variables, AddableInteger[][] domains, AddableInteger[] assignment, final boolean sparse) {

		// We want to allow the input list of variables not to contain all this space's variables
		int nbrInputVars = variables.length;
		ArrayList<String> vars = new ArrayList<String> (nbrInputVars);
		ArrayList<AddableInteger[]> doms = new ArrayList<AddableInteger[]> (nbrInputVars);

		// Go through the list of input variables
		for (int i = 0; i < nbrInputVars; i++) {

			// Record the variable
			String var = variables[i];
			vars.add(var);

			// Record the domain, as the intersection of the input domain with the space's domain, if any
			AddableInteger[] myDom = this.getDomain(var);
			if (myDom == null) // unknown variable
				doms.add(domains[i]);
			else 
				doms.add(BasicHypercube.intersection(myDom, domains[i]));

		}

		// Add the variables that are in this space and not in the input list
		int myNbrVars = this.vars.length;
		for (int i = 0; i < myNbrVars; i++) {
			String var = this.vars[i];
			if (! vars.contains(var)) {
				vars.add(var);
				doms.add(this.allVars.get(var));
			}
		}

		int nbrVarsIter = vars.size();
		
		if (sparse) 
			return new JaCoPutilSpaceIter2<U> (this, vars.toArray(new String [nbrVarsIter]), 
					doms.toArray((AddableInteger[][]) Array.newInstance(domains.getClass().getComponentType(), nbrVarsIter)));
		else 
			return new JaCoPutilSpaceIter<U> (this, vars.toArray(new String [nbrVarsIter]), 
					doms.toArray((AddableInteger[][]) Array.newInstance(domains.getClass().getComponentType(), nbrVarsIter)));
	}

	/** @see BasicUtilitySolutionSpace#prettyPrint(java.io.Serializable) */
	public String prettyPrint(U ignoredUtil) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see BasicUtilitySolutionSpace#setDefaultUtility(java.io.Serializable) */
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
		
		// Create String and regex representations of the assignment
		StringBuilder regex = new StringBuilder ("([\\+\\-])?\\d+\\s*:\\s*");
		StringBuilder string = new StringBuilder (utility.toString() + ":");
		for (AddableInteger val : variablesValues) {
			regex.append(val + "\\s*");
			string.append(val + " ");
		}
		String tuple = string.toString().trim();
		String regexStr = regex.toString();
		
		// Look for this assignment in the list of tuples
		assert this.constraints.size() == 1 : "Setting the utility of a compound space is currently unsupported";
		Element relation = this.relations.get(this.constraints.values().iterator().next().getAttributeValue("reference"));
		String tuples = relation.getText();
		
		if (tuples.matches(regexStr) || tuples.split(regexStr).length > 1) // the assignment was found in the list of tuples
			relation.setText(tuples.replaceFirst(regexStr, tuple));
		
		else { // the assignment wasn't found; add it
			
			StringBuilder builder = new StringBuilder (tuples);
			if (tuples.length() > 0) 
				builder.append("|");
			builder.append(tuple);
			relation.setText(builder.toString());
			
			relation.setAttribute("nbTuples", Integer.toString(1 + Integer.parseInt(relation.getAttributeValue("nbTuples"))));
		}
		
		return true;
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
		for(String v: this.vars){
			if(v.equals(variable))
				return allVars.get(variable);
		}
		return null;
	}

	/** Returns an array of all possible values that the projected variable provided as a parameter 
	 * can take in this JaCoPutilSpace
	 * @param variable 		the name of the projected variable
	 * @return  			the projected variable's domain
	 */
	public AddableInteger[] getProjVarDomain(String variable) {
		for(String v: this.projectedVars){
			if(v.equals(variable))
				return allVars.get(variable);
		}
		return null;
	}

	/** @see SolutionSpace#getDomain(int) */
	public AddableInteger[] getDomain(int index) {
		return allVars.get(vars[index]);
	}

	/** @see SolutionSpace#getDomain(java.lang.String, int) */
	public AddableInteger[] getDomain(String variable, int index) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see SolutionSpace#getDomains() */
	public AddableInteger[][] getDomains() {
		AddableInteger[][] domains = new AddableInteger[vars.length][];
		for(int i = 0; i < vars.length; i++){
			domains[i] = allVars.get(vars[i]);
		}
		return domains;
	}

	/** @see SolutionSpace#getIndex(java.lang.String) */
	public int getIndex(String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return 0;
	}

	/** @see SolutionSpace#getName() */
	public String getName() {
		return this.name;
	}

	/** @see SolutionSpace#getOwner() */
	public String getOwner() {
		return owner;
	}

	/** @see SolutionSpace#setOwner(java.lang.String) */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/** @see SolutionSpace#getNumberOfSolutions() */
	public long getNumberOfSolutions() {
		long nbrUtils = 1;

		for(String var: this.vars){
			assert Math.log(nbrUtils) + Math.log(allVars.get(var).length) < Math.log(Long.MAX_VALUE) : "Long overflow: too many solutions in an explicit space";
			nbrUtils *= allVars.get(var).length;
		}
		return nbrUtils;
	}

	/** @see SolutionSpace#getNumberOfVariables() */
	public int getNumberOfVariables() {
		return this.vars.length;
	}

	/** @see SolutionSpace#getVariable(int) */
	public String getVariable(int index) {
		return vars[index];
	}

	/** @see SolutionSpace#getVariables() */
	public String[] getVariables() {
		return vars;
	}

	/** @see UtilitySolutionSpace#iterator(java.lang.String[]) */
	@Override
	public UtilitySolutionSpace.Iterator<AddableInteger, U> iterator(String[] order) {
		return (UtilitySolutionSpace.Iterator<AddableInteger, U>) this.iterator(order, false);
	}
	
	/** @see UtilitySolutionSpace#sparseIter(java.lang.String[]) */
	@Override
	public UtilitySolutionSpace.SparseIterator<AddableInteger, U> sparseIter(String[] order) {
		return this.iterator(order, true);
	}
	
	/** Returns an iterator with a specific variable order
	 * @param order 	the order of iteration of the variables
	 * @param sparse 	whether to return an iterator that skips infeasible solutions
	 * @return 			an iterator which can be used to iterate through solutions 
	 * @warning The input array of variables must contain exactly all of the space's variables. 
	 */
	private UtilitySolutionSpace.SparseIterator<AddableInteger, U> iterator(String[] order, final boolean sparse) {

		AddableInteger[][] variables_domain = new AddableInteger[vars.length][];
		for(int i = 0; i < vars.length; i++){
			variables_domain[i] = allVars.get(order[i]);
		}

		if (sparse) 
			return this.sparseIter(order, variables_domain);
		else 
			return this.iterator(order, variables_domain);
	}

	/** @see SolutionSpace#join(SolutionSpace, java.lang.String[]) */
	public SolutionSpace<AddableInteger> join(
			SolutionSpace<AddableInteger> space, String[] totalVariables) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see SolutionSpace#join(SolutionSpace) */
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

	/** @see SolutionSpace#renameVariable(java.lang.String, java.lang.String) */
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
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see UtilitySolutionSpace#blindProject(java.lang.String, boolean) */
	public UtilitySolutionSpace<AddableInteger, U> blindProject(String varOut, boolean maximize) {
		
		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]> (this.allVars);
		HashSet<String> newVars = new HashSet<String>(Arrays.asList(this.vars));
		ArrayList<String> newProjectedVars = new ArrayList<String>(Arrays.asList(this.projectedVars));
		String newSlicedVars[] = this.slicedVars;

		// move the projected vars from vars to projectedVars
		if (newVars.remove(varOut)) 
			newProjectedVars.add(varOut);

		// For now (and maybe forever) we will assume that all variables are projected in the same way (all minimized or all maximized)
		assert (this.maximize == maximize) : "All variables must be projected the same way!";

		return new JaCoPutilSpace<U> (this.getName() + "projected", this.constraints, this.relations, newAllVars,
				newVars.toArray(new String[newVars.size()]), newProjectedVars.toArray(new String[newProjectedVars.size()]),
				newSlicedVars, this.maximize, this.defaultUtil, this.infeasibleUtil);
	}

	/** @see UtilitySolutionSpace#blindProject(java.lang.String[], boolean) */
	public UtilitySolutionSpace<AddableInteger, U> blindProject(String[] varsOut, boolean maximize) {
		
		HashMap<String, AddableInteger[]> newAllVars = new HashMap<String, AddableInteger[]> (this.allVars);
		HashSet<String> newVars = new HashSet<String>(Arrays.asList(this.vars));
		ArrayList<String> newProjectedVars = new ArrayList<String>(Arrays.asList(this.projectedVars));
		String newSlicedVars[] = this.slicedVars;

		// move the projected vars from vars to projectedVars
		for(int i = varsOut.length - 1; i >= 0; i--) 
			if (newVars.remove(varsOut[i])) 
				newProjectedVars.add(varsOut[i]);

		// For now (and maybe forever) we will assume that all variables are projected in the same way (all minimized or all maximized)
		assert (this.maximize == maximize) : "All variables must be projected the same way!";

		return new JaCoPutilSpace<U> (this.getName() + "projected", this.constraints, this.relations, newAllVars,
				newVars.toArray(new String[newVars.size()]), newProjectedVars.toArray(new String[newProjectedVars.size()]),
				newSlicedVars, this.maximize, this.defaultUtil, this.infeasibleUtil);
	}

	/** @see UtilitySolutionSpace#blindProjectAll(boolean) */
	public U blindProjectAll(boolean maximize) {
		return this.blindProject(this.getVariables(), maximize).getUtility(0);
	}

	/** @see UtilitySolutionSpace#iteratorBestFirst(boolean, java.lang.String[], Addable[]) */
	public IteratorBestFirst<AddableInteger, U> iteratorBestFirst(
			boolean maximize, String[] fixedVariables,
			AddableInteger[] fixedValues) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#max(java.lang.String) */
	public UtilitySolutionSpace<AddableInteger, U> max(
			String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see UtilitySolutionSpace#min(java.lang.String) */
	public UtilitySolutionSpace<AddableInteger, U> min(
			String variable) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see SolutionSpace#getRelationName() */
	public String getRelationName() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see SolutionSpace#renameAllVars(java.lang.String[]) */
	public SolutionSpace<AddableInteger> renameAllVars(String[] newVarNames) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
	return null;
	}

	/** @see SolutionSpace#setRelationName(java.lang.String) */
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

	/** @see UtilitySolutionSpace#setProblem(ProblemInterface) */
	public void setProblem(ProblemInterface<AddableInteger, U> problem) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/**
	 * @return the store associated to this solution space
	 */
	public Store getStore() {
		return store;
	}

	/**
	 * @return all the constraints contained in this solution space
	 */
	public HashMap<String, Element> getConstraints() {
		return constraints;
	}

	/**
	 * @return all the relations/predicates contained in this solution space
	 */
	public HashMap<String, Element> getRelations() {
		return relations;
	}

	/**
	 * @return all the variables of this solution space whose projection has been requested
	 */
	public String[] getProjectedVars() {
		return projectedVars;
	}

	/** 
	 * @return true if it is a maximization problem, false if it is a minimization problem.
	 */
	public boolean maximize() {
		return maximize;
	}

	/**
	 * @return an hypercube corresponding to this utility space
	 * @todo test this method, there are bugs!
	 */
	public Hypercube<AddableInteger, U> toHypercube(){
		// ScalarHypercube
		if(this.vars.length == 0){
			return new ScalarHypercube<AddableInteger, U>(this.getUtility(0), this.infeasibleUtil, new AddableInteger [0].getClass());
		}else{

			final int nbrVars = vars.length;
			String[] outVars = new String [nbrVars];
			int nbrUtils = 1;
			AddableInteger[][] doms = new AddableInteger[nbrVars][];

			int n = 0;
			AddableInteger[] dom;
			for(String var: this.vars){
				outVars[n] = var;
				dom = allVars.get(var);
				assert Math.log(nbrUtils) + Math.log(dom.length) < Math.log(Integer.MAX_VALUE) : "Integer overflow: too many solutions in an explicit space";
				nbrUtils *= dom.length;
				doms[n] = dom;
				n++;
			}

			@SuppressWarnings("unchecked")
			U[] utils = (U[]) Array.newInstance(this.defaultUtil.getClass(), nbrUtils);
			Arrays.fill(utils, this.infeasibleUtil.getZero());

			Hypercube<AddableInteger, U> out = new Hypercube<AddableInteger, U> (outVars, doms, utils, this.infeasibleUtil);

			long nbrSol = this.getNumberOfSolutions();
			assert nbrSol < Integer.MAX_VALUE : "A hypercube can only contain up to " + Integer.MAX_VALUE + " solutions";

			Iterator<AddableInteger, U> iter = this.iterator(outVars, doms);
			for (int i = 0; i < (int)nbrSol; i++){
				out.setUtility(i, iter.nextUtility());
			}
			return out;
		}
	}

	/** @see UtilitySolutionSpace#iterator(java.lang.String[], Addable[][]) */
	@Override
	public Iterator<AddableInteger, U> iterator(String[] variables, AddableInteger[][] domains) {
		return this.iterator(variables, domains, (AddableInteger[]) Array.newInstance(AddableInteger.class, variables.length));
	}
	
	/** @see UtilitySolutionSpace#sparseIter(java.lang.String[], Addable[][]) */
	@Override
	public SparseIterator<AddableInteger, U> sparseIter(String[] variables, AddableInteger[][] domains) {
		return this.sparseIter(variables, domains, (AddableInteger[]) Array.newInstance(AddableInteger.class, variables.length));
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
