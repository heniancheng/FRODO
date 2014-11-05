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

/** Package handling the interface with the JaCoP solver */
package frodo2.solutionSpaces.JaCoP;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom2.Document;
import org.jdom2.Element;

import JaCoP.constraints.Alldifferent;
import JaCoP.constraints.ExtensionalConflictVA;
import JaCoP.constraints.ExtensionalSupportSTR;
import JaCoP.constraints.SumWeight;
import JaCoP.constraints.XgtY;
import JaCoP.constraints.XgteqY;
import JaCoP.constraints.XltY;
import JaCoP.constraints.XlteqY;
import JaCoP.constraints.XneqY;
import JaCoP.core.IntDomain;
import JaCoP.core.IntVar;
import JaCoP.core.IntervalDomain;
import JaCoP.core.Store;

import frodo2.algorithms.XCSPparser;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.crypto.AddableBigInteger;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** An XCSP parser that generates spaces based on JaCoP
 * @author Thomas Leaute, Arnaud Jutzeler
 * @param <U> the type used for utility values
 */
public class JaCoPxcspParser < U extends Addable<U> > extends XCSPparser<AddableInteger, U> {

	/** Constraint type */
	protected enum Constraint {
		/** Extensional soft constraint described by a soft relation */
		EXTENSIONAL, 
		/** Intensional constraint described by a predicate or a function */
		INTENSIONAL, 
		/** Unsupported */
		PROBABILITY, 
		/** A global constraint */
		GLOBAL 
	}

	/** Used for serialization */
	private static final long serialVersionUID = -7421948182797306824L;

	/** Constructor
	 * @param probDoc 	the problem Document in XCSP format
	 * @param params 	the parser's parameters
	 */
	public JaCoPxcspParser(Document probDoc, Element params) {
		super(probDoc, params);

		assert this.countNCCCs == false : "NCCCs not implemented"; /// @todo Implement NCCCs?

	}

	/** Constructor from a JDOM root Element in XCSP format
	 * @param agentName 					the name of the agent owning the input subproblem
	 * @param root 							the JDOM root Element in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param spacesToIgnoreNcccs			list of spaces for which NCCCs should NOT be counted
	 * @param mpc 							Whether to behave in MPC mode
	 */
	protected JaCoPxcspParser (String agentName, Element root, boolean countNCCCs, HashSet<String> spacesToIgnoreNcccs, boolean mpc) {
		super (agentName, root, countNCCCs, false, spacesToIgnoreNcccs, mpc);

		assert this.countNCCCs == false : "NCCCs not implemented"; /// @todo Implement NCCCs?

	}

	/** @see XCSPparser#newInstance(java.lang.String, org.jdom2.Element) */
	protected JaCoPxcspParser<U> newInstance (String agent, Element instance) {
		return new JaCoPxcspParser<U> (agent, instance, this.countNCCCs, this.spacesToIgnoreNcccs, super.mpc);
	}

	/** @see XCSPparser#setUtilClass(java.lang.Class) */
	@Override
	public void setUtilClass (Class<U> utilClass) {
		assert utilClass.equals(AddableInteger.class) || utilClass.equals(AddableBigInteger.class) : this.getClass().getSimpleName() + " does not support utilities of class " + utilClass;
		super.setUtilClass(utilClass);
	}

	/** @see XCSPparser#getSpaces(Set, boolean, boolean, Set) */
	@Override
	protected List< JaCoPutilSpace<U> > getSpaces (Set<String> vars, final boolean withAnonymVars, final boolean getProbs, Set<String> forbiddenVars) {

		U infeasibleUtil = (super.maximize() ? super.getMinInfUtility() : super.getPlusInfUtility());

		/// @todo Reuse code. 

		ArrayList< JaCoPutilSpace<U> > result = new ArrayList< JaCoPutilSpace<U> >();

		final boolean debugLoad = false;

		// First important element of XCSP format is the specification of the domains.		
		org.jdom2.Element domains = root.getChild("domains");

		// domain is represented as a list of integers. Potentially a problem 
		// if a domain is large. However, the hypercubes will have problems too
		// so it is unlikely for variables to have large domains.
		HashMap<String, AddableInteger[]> domainsHashMap = new HashMap<String, AddableInteger[]>();

		// Reads information about variables domains.
		for (org.jdom2.Element domain : (List<org.jdom2.Element>) domains.getChildren()) {

			String name = domain.getAttributeValue("name");

			// Hashmap to associate domain names with the list of elements in that domain.
			domainsHashMap.put(name, (AddableInteger[]) this.getDomain(domain, debugLoad));
		}

		if (debugLoad)
			System.out.println(domainsHashMap);

		// Second important element in XCSP format is describing variables.
		org.jdom2.Element variables = root.getChild("variables");

		// Each variable has its list of values in their domain. 
		HashMap<String, AddableInteger[]> variablesHashMap = new HashMap<String, AddableInteger[]>();

		for (org.jdom2.Element variable : (List<org.jdom2.Element>) variables.getChildren()) {

			String name = variable.getAttributeValue("name");
			String domName = variable.getAttributeValue("domain");

			if (!getProbs && domName == null) // we don't know the domain of this variable
				return null;

			// Variables domain is specified by the name so the actual domain is obtained
			// from the hashmap describing the domains.
			variablesHashMap.put(name, domainsHashMap.get(domName));
		}

		if (debugLoad)
			System.out.println(variablesHashMap);

		// All the relations
		org.jdom2.Element relations;
		org.jdom2.Element predicates;
		org.jdom2.Element functions;
		relations = root.getChild("relations");
		predicates = root.getChild("predicates");
		functions = root.getChild("functions");
		
		ArrayList<Element> predAndFunc = new ArrayList<Element> ();
		if (predicates != null) 
			predAndFunc.addAll(predicates.getChildren());
		if (functions != null) 
			predAndFunc.addAll(functions.getChildren());

		// This element actually describes all the constraints.
		org.jdom2.Element constraints = root.getChild("constraints");

		for (Element constraint : (List<Element>) constraints.getChildren()){

			String refName = constraint.getAttributeValue("reference");
			Element relation = null;

			// If it is either a relation, a predicate or a function, we need to get the corresponding Element whereas there no such Element with global constraints
			if(!refName.startsWith("global:")){

				Element parameters = constraint.getChild("parameters");

				// Constraint in intension
				if(parameters != null){

					for(Element pred: predAndFunc){
						if(pred.getAttributeValue("name").equals(refName)){
							relation = pred;
							break;
						}
					}

					assert relation != null: "The predicate or function " + refName + " referenced by the constraint cannot be found!";

					// Constraint in extension
				}else{

					for(Element rel: (List<Element>) relations.getChildren()){
						if(rel.getAttributeValue("name").equals(refName)){
							relation = rel;
							break;
						}
					}

					assert relation != null: "The relation " + refName + " referenced by the constraint cannot be found!";

				}

			}

			this.parseConstraint(result, constraint, variablesHashMap, relation, vars, getProbs, withAnonymVars, infeasibleUtil, forbiddenVars);

		}

		return result;

	}


	/** Parses a constraint
	 * @param spaces 				the list of spaces to which the constraint should be added
	 * @param constraint 			the XCSP description of the constraint
	 * @param variablesHashMap 		the domain of each variable
	 * @param relation				the relation, predicate or function referenced by the constraint
	 * @param vars 					if \c null, returns all constraints; otherwise, returns only the constraints involving at least one variable in \a vars
	 * @param getProbs 				if \c true, returns the probability spaces (ignoring \a withAnonymVars); else, returns the solution spaces
	 * @param withAnonymVars 		whether constraints involving variables with unknown owners should be taken into account
	 * @param infeasibleUtil 		the infeasible utility
	 * @param forbiddenVars 		any space involving any of these variables will be ignored
	 * @todo Reuse code. 
	 */
	protected void parseConstraint(ArrayList< JaCoPutilSpace<U> > spaces, Element constraint, 
			HashMap<String, AddableInteger[]> variablesHashMap, Element relation, 
			Set<String> vars, final boolean getProbs, final boolean withAnonymVars, U infeasibleUtil, Set<String> forbiddenVars) {

		String name = constraint.getAttributeValue("name");
		String owner = constraint.getAttributeValue("agent");

		//int arity = Integer.valueOf(constraint.getAttributeValue("arity"));
		String scope = constraint.getAttributeValue("scope");

		Pattern pattern = Pattern.compile("\\s+");

		String[] varNames = pattern.split(scope);

		// Skip this constraint if it does not involve any variables of interest (or if we want all constraints)
		if (vars != null && Collections.disjoint(vars, Arrays.asList(varNames)))
			return;

		// Skip this constraint if if involves any of the forbidden variables
		if (forbiddenVars != null) 
			for (String varName : varNames) 
				if (forbiddenVars.contains(varName)) {
					return;
				}

		AddableInteger[][] variables_domain = (AddableInteger[][]) Array.newInstance(variablesHashMap.values().iterator().next().getClass(), varNames.length);

		int no = -1;
		int size = 1;
		boolean hasAnonymVar = false; // whether one variable in the scope has no specified owner
		for (String n : varNames) {
			hasAnonymVar = hasAnonymVar || (this.getOwner(n) == null);
			no++;
			variables_domain[no] = variablesHashMap.get(n);
			assert Math.log((double) size) + Math.log((double) variables_domain[no].length) < Math.log(Integer.MAX_VALUE) : 
				"Size of utility array too big for an int";
			size *= variables_domain[no].length;
		}

		// If required, ignore the constraint if its scope contains variables with unknown owners
		if (!getProbs && !withAnonymVars && hasAnonymVar){
			return;
		}



		JaCoPutilSpace<U> current = new JaCoPutilSpace<U> (name, owner, constraint, relation, varNames, variables_domain, super.maximize(),infeasibleUtil);

		spaces.add(current);
	}

	/** @see XCSPparser#groundVars(String[], Addable[]) */
	@Override
	public Document groundVars (String[] vars, AddableInteger[] values) {

		/// @todo TBC
		assert false : "Not yet implemented!";
	return null;
	}

	/** @see XCSPparser#switchMaxMin(int) */
	@Override
	public Document switchMaxMin(int shiftInt) {

		/// @todo TBC
		assert false : "Not yet implemented!";
	return null;
	}

	/** @see XCSPparser#reset(ProblemInterface) */
	@Override
	public void reset(ProblemInterface<AddableInteger, U> newProblem) {

		/// @todo TBC
		assert false : "Not yet implemented!";
	}

	/** 
	 * @see DCOPProblemInterface#getUtility(Map, boolean) 
	 * @todo Test this method 
	 * @todo Reuse code. 
	 */
	@Override
	public UtilitySolutionSpace<AddableInteger, U> getUtility (Map<String, AddableInteger> assignments, final boolean withAnonymVars) {

		UtilitySolutionSpace<AddableInteger, U> output = new  ScalarHypercube<AddableInteger, U>(this.getZeroUtility(), this.getInfeasibleUtil(), new AddableInteger [0].getClass());

		// Extract all spaces
		List< ? extends UtilitySolutionSpace< AddableInteger,  U> > spaces = this.getSolutionSpaces(withAnonymVars);

		// Go through the list of spaces
		for (UtilitySolutionSpace< AddableInteger,  U> space : spaces) {

			// Slice the space over the input assignments
			ArrayList<String> vars = new ArrayList<String> (space.getNumberOfVariables());
			for (String var : space.getVariables()) 
				if (assignments.containsKey(var)) 
					vars.add(var);
			int nbrVars = vars.size();
			AddableInteger[] values = (AddableInteger[]) Array.newInstance(this.valInstance.getClass(), nbrVars);
			for (int i = 0; i < nbrVars; i++) 
				values[i] = assignments.get(vars.get(i));
			UtilitySolutionSpace< AddableInteger, U> slice = space.slice(vars.toArray(new String[nbrVars]), values);

			// Join the slice with the output
			output = output.join(slice);
		}
		return output;
	}


	/**
	 * @param constraint		the jdom element describing the constraint
	 * @param relation			the jdom element describing the relation
	 * @param store				the store where the variables exist and in which we want to impose the extensional soft constraints
	 * @param utilVars			the list of utility variables to which we need to add the utility variable of this constraint
	 */
	public static void parseRelation(Element constraint, Element relation, Store store, ArrayList<IntVar> utilVars){

		String semantics = relation.getAttributeValue("semantics");

		String constraintName = constraint.getAttributeValue("name");

		// Extract the variables in the constraint
		String scope = constraint.getAttributeValue("scope");

		Pattern pattern = Pattern.compile("\\s+");
		String[] varNames = pattern.split(scope);
		int nbTuples = Integer.valueOf(relation.getAttributeValue("nbTuples"));
		String name = relation.getAttributeValue("name");
		int arity = Integer.valueOf(relation.getAttributeValue("arity"));

		String tuplesString = relation.getText();

		pattern = Pattern.compile("\\|");
		String[] tuples = pattern.split(tuplesString);
		assert nbTuples == 0 || tuples.length == nbTuples : "Relation `" + name + "' has nbTuples == " + nbTuples + 
		" but its description actually contains " + tuples.length + " tuples";

		pattern = Pattern.compile("\\s+");

		// The relation defines a soft extensional constraint
		if(semantics.equals("soft")){

			// JaCoP Variables = variables of the hypercube + the utility variable
			IntVar[] vars = new IntVar[varNames.length + 1];

			IntVar v;
			for(int i = 0; i < varNames.length; i++){
				v = (IntVar)store.findVariable(varNames[i]);

				// All these variables must be in the store
				assert v != null : "problem: variable " + varNames[i] + " not found in the store!";

				vars[i] = v;
			}

			String defaultCost = relation.getAttributeValue("defaultCost");
			assert nbTuples > 0 || defaultCost != null : "Relation `" + name + "' has nbTuples == " + nbTuples + " and no default cost";
			
			if(nbTuples == 0){
				tuples = new String[0];
			}
			
			Pattern patternColon = Pattern.compile(":");
			pattern = Pattern.compile("\\s+");

			// The domain of the utility variable
			IntervalDomain utilDom = new IntervalDomain (nbTuples);

			// The list that will contain all the supported tuples of the extensional contraint
			ArrayList<int[]> solutions = new ArrayList<int[]>();
			int currentUtil = 0;
			boolean isInfinity = false;

			// In this case, a default utility does not exist or is infeasible.
			// We just have to put all the tuples given explicitly that are feasible in the extensional constraint
			if(defaultCost == null || defaultCost.equals("-infinity") || defaultCost.equals("infinity")){

				ext: for (int i = 0; i < nbTuples; i++){
					if (tuples[i].contains(":")) {
						String[] pair = patternColon.split(tuples[i]);
						tuples[i] = pair[1];
						if(pair[0].trim().equals("-infinity") || pair[0].trim().equals("infinity")){
							isInfinity = true;
						}else{
							currentUtil = Integer.valueOf(pair[0].trim());
							isInfinity = false;
						}
					}

					// If the utility represents a feasible value, we need to put the tuple in the extensional constraint
					if(!isInfinity){

						int[] tuple = new int[arity + 1];

						String[] vals = pattern.split(tuples[i].trim());

						assert vals.length == varNames.length : "The tuple " + tuples[i].trim() + " does not specify a value for every variable!";
						
						for (int j = 0; j < varNames.length; j++) {
							tuple[j] = Integer.valueOf(vals[j]);

							// the value of the variable is no more contained in its domain (due to a previous slice operation)
							if(!vars[j].dom().contains(tuple[j]))
								continue ext;
						}

						tuple[arity] = currentUtil;

						utilDom.addDom(new IntervalDomain (currentUtil, currentUtil));

						solutions.add(tuple);
					}	
				}

				// In that case, a default utility does exist and it represents a feasible value.
				// We have to put all the tuples given explicitly that are feasible as well as those not specified that have the default utility in the extensional constraint
			}else{
				// As the ExtensionalSupport constraint in JaCoP are not PrimitiveConstraint we cannot use control constraints such as IfThenElse, Or, ...
				// I do not see any tricks to avoid building the complete list of tuples when the default utility is specified and is not equal to the infeasible utility.

				int defaultUtil = Integer.valueOf(defaultCost);
				// Add the default value to the domain of the utility variable
				utilDom.addDom(new IntervalDomain (defaultUtil, defaultUtil));

				// Compute the number of solutions
				int nbrSol = 1;
				for(int i = 0; i < varNames.length; i++){
					nbrSol *= vars[i].dom().getSize();
				}

				// We construct the HashMaps that will speed up the index computation of tuples
				@SuppressWarnings("unchecked")
				HashMap<Integer, Integer>[] steps_hashmaps = new HashMap[varNames.length];
				HashMap<Integer, Integer > steps;
				int step = nbrSol;
				IntDomain domain;
				int domain_size;

				// for every variable in the list of variables.
				for(int i = 0; i < varNames.length; i++) {
					//the domain of the ith variable in the list of variables
					domain = vars[i].dom();
					//size of the domain of the ith variable in the array of variables
					domain_size = domain.getSize();

					//the smallest step of the current variable is equivalent to the smallest step of the previous variable 
					//divided by the size of the domain of the current variable
					step = step / domain_size;

					//hashmap that maps a value of a variable to a step in the utility values "values" array
					steps = new HashMap<Integer, Integer>(domain_size);
					for( int j = 0, step_tmp = 0;  j < domain_size;  j++, step_tmp += step )
						steps.put(domain.getElementAt(j), step_tmp);

					steps_hashmaps[ i ] = steps;
				}

				// The array of utilities
				int[] utils = new int[nbrSol];
				Arrays.fill(utils, defaultUtil);
				int[] tuple = new int[arity + 1];

				// We iterate over every explicitly stated tuple
				ext: for(int i = 0; i < nbTuples; i++){

					if (tuples[i].contains(":")) {
						String[] pair = patternColon.split(tuples[i]);
						tuples[i] = pair[1];
						if(pair[0].trim().equals("-infinity") || pair[0].trim().equals("infinity")){
							isInfinity = true;
						}else{
							currentUtil = Integer.valueOf(pair[0].trim());
							isInfinity = false;
						}
					}
					
					String[] vals = pattern.split(tuples[i].trim());
					
					assert vals.length == varNames.length : "The tuple " + tuples[i].trim() + " does not specify a value for every variable!";
					
					for (int j = 0; j < varNames.length; j++) {
						tuple[j] = Integer.valueOf(vals[j]);

						// the value of the variable is no more contained in its domain (due to a previous slice operation)
						if(!vars[j].dom().contains(tuple[j]))
							continue ext;
					}

					// We calculate the index of this tuple
					int index = 0;
					for(int j = 0; j < varNames.length; j++){
						HashMap<Integer, Integer> steps_hashmap = steps_hashmaps[j];
						assert steps_hashmap != null: "The steps HashMap corresponding to the variable " +  vars[j].id + " has not been initialized!";
						Integer incr = steps_hashmap.get(tuple[j]);
						assert incr != null: "The steps HashMap corresponding to the variable " +  vars[j].id + " does not contain an entry for the value " + tuple[j];
						
						index += incr;
					}

					// We save its utility in the array
					if(isInfinity){
						utils[index] = Integer.MAX_VALUE;
					}else{
						utilDom.addDom(new IntervalDomain (currentUtil, currentUtil));
						utils[index] = currentUtil;
					}
				}


				int[] indexes = new int[varNames.length];
				Arrays.fill(indexes, 0);
				boolean hasIncr;

				// We iterate over our list of solutions and put in the extensional support constraint only the feasible ones
				for(int i = 0; i < nbrSol; i++){
					hasIncr = false;
					if(utils[i] != Integer.MAX_VALUE){
						tuple = new int[arity + 1];
						for (int j = 0; j < varNames.length; j++){
							domain = vars[j].dom();
							tuple[j] = domain.getElementAt(indexes[j]);
						}
						tuple[arity] = utils[i];
						solutions.add(tuple);
					}

					// Get the correct values of the variables for the next tuple
					for (int j = varNames.length-1; j >= 0; j--){
						domain = vars[j].dom();
						if(!hasIncr){
							if(indexes[j] == domain.getSize()-1){
								indexes[j] = 0;
							}else{
								indexes[j]++;
								hasIncr = true;
							}
						}
					}
				}
			}

			IntVar currentUtilVar = new IntVar (store, "util_" + constraintName, utilDom);
			// If the relation can only have the infeasible utility, the utility variable has an empty domain
			if(!utilDom.isEmpty()){
				utilVars.add(currentUtilVar);
			}
			vars[arity] = currentUtilVar;

			ExtensionalSupportSTR ext = new ExtensionalSupportSTR(vars, solutions.toArray(new int[solutions.size()][arity+1]));
			store.impose(ext);

		}else{

			// JaCoP Variables = variables of the hypercube + the utility variable
			IntVar[] vars = new IntVar[varNames.length];

			IntVar v;
			for(int i = 0; i < varNames.length; i++){
				v = (IntVar)store.findVariable(varNames[i]);

				// All these variables must be in the store
				assert v != null : "problem: variable " + varNames[i] + " not found in the store!";

				vars[i] = v;
			}

			// The list that will contain all the supported tuples of the extensional contraint
			ArrayList<int[]> solutions = new ArrayList<int[]>();

			ext: for (int i = 0; i < nbTuples; i++){

				int[] tuple = new int[arity];

				String[] vals = pattern.split(tuples[i].trim());
				
				assert vals.length == varNames.length : "The tuple " + tuples[i].trim() + " does not specify a value for every variable!";

				for (int j = 0; j < varNames.length; j++) {
					tuple[j] = Integer.valueOf(vals[j]);

					// if the value of the variable in the current tuple is not contained in the domain this variable,
					// we do not need to add the tuple in the constraint. Besides ExtensionalConflict would crash.
					// This can happen after a slice operation on a space for example.
					if(!vars[j].dom().contains(tuple[j])){
						continue ext;
					}

				}

				solutions.add(tuple);
			}

			// The relation indicates the supported tuples
			if(semantics.equals("supports")){

				ExtensionalSupportSTR ext = new ExtensionalSupportSTR(vars, solutions.toArray(new int[solutions.size()][arity]));
				store.impose(ext);

			// The relation indicates the conflicting tuples
			}else if(semantics.equals("conflicts")){

				if(solutions.size() > 0){
					ExtensionalConflictVA ext = new ExtensionalConflictVA(vars, solutions.toArray(new int[solutions.size()][arity]));
					store.impose(ext);
				}

			// Unknown semantic
			}else{
				System.out.println("The semantics of the relation " + name + " are not valid");
			}
		}
	}

	/**
	 * @param constraint	the jdom element describing the constraint
	 * @param predicate		the jdom element describing the predicate or function
	 * @param store			the store where the variables exist and in which we want to impose the intentional hard constraints
	 * @param utilVars		the list of utility variables to which we need to add the utility variable of this constraint (if it is a soft constraint)
	 * @todo test   
	 */
	public static void parsePredicate(Element constraint, Element predicate, Store store, ArrayList<IntVar> utilVars){
		assert constraint.getChild("parameters") != null;
		assert predicate.getChild("parameters") != null;
		assert predicate.getChild("expression") != null;
		assert predicate.getChild("expression").getChild("functional") != null;
		Predicate pred = new Predicate(constraint.getChildText("parameters"), predicate.getChildText("parameters"), predicate.getChild("expression").getChildText("functional"), store);
		pred.imposeDecomposition(store);
		if (pred.utilVar != null) 
			utilVars.add(pred.utilVar);
	}

	/**
	 * @param constraint			the jdom element describing the global constraint
	 * @param store					the store where the variables exist and in which we want to impose the intensional hard constraints
	 * @todo test   
	 */
	public static void parseGlobalConstraint(Element constraint, Store store){

		// Extract the variables in the constraint
		String scope = constraint.getAttributeValue("scope");
		Pattern pattern = Pattern.compile("\\s+");
		String[] varNames = pattern.split(scope);

		int arity = Integer.valueOf(constraint.getAttributeValue("arity"));

		if(constraint.getAttributeValue("reference").equals("global:weightedSum")){

			String parameters = constraint.getChild("parameters").getText();
			pattern = Pattern.compile("\\[(.*)\\]\\s*(\\d+)");
			Pattern pattern2 = Pattern.compile("\\{ ?(-?\\d+) (\\S+) ?\\}");

			ArrayList<IntVar> vars = new ArrayList<IntVar>();
			ArrayList<Integer> weights = new ArrayList<Integer>();

			Matcher m = pattern.matcher(parameters);
			m.find();
			int rightHandVal = Integer.parseInt(m.group(2));

			m = pattern2.matcher(m.group(1));
			
			Pattern constantPat = Pattern.compile("\\d+");
			
			int i = 0;
			IntVar v;
			for(; m.find(); i++){
				
				// constant parameter
				if(constantPat.matcher(m.group(2)).matches()){
					int val = Integer.parseInt(m.group(2));
					v = new IntVar(store, val, val);
				// variable name
				}else{
					assert i < arity : "There are not enough tuples in the parameters of the global constraint";
					assert Arrays.asList(varNames).contains(m.group(2)): "mismatch between the constraint scope and the variables in parameters!";

					v = (IntVar) store.findVariable(m.group(2));
					// All these variables must be in the store
					assert v != null: "The variable " + m.group(2) + " cannot be found in the store!";
				}
				vars.add(v);
				weights.add(Integer.parseInt(m.group(1)));
			}

			IntVar rightHandVar = new IntVar(store, "rhs_" + new Object().hashCode(), rightHandVal, rightHandVal);

			String atom = ((Element)constraint.getChild("parameters").getChildren().get(0)).getName();

			IntVar sumVar = null;

			if(atom.equals("eq")){

				sumVar = rightHandVar;

			}else if(atom.equals("ne")){

				sumVar = new IntVar(store, "sum_" + new Object().hashCode(), IntervalDomain.MinInt, IntervalDomain.MaxInt);

				store.impose(new XneqY(sumVar, rightHandVar)); 

			}else if(atom.equals("ge")){

				sumVar = new IntVar(store, "sum_" + new Object().hashCode(), IntervalDomain.MinInt, IntervalDomain.MaxInt);

				store.impose(new XgteqY(sumVar, rightHandVar)); 

			}else if(atom.equals("gt")){

				sumVar = new IntVar(store, "sum_" + new Object().hashCode(), IntervalDomain.MinInt, IntervalDomain.MaxInt);

				store.impose(new XgtY(sumVar, rightHandVar));

			}else if(atom.equals("le")){

				sumVar = new IntVar(store, "sum_" + new Object().hashCode(), IntervalDomain.MinInt, IntervalDomain.MaxInt);

				store.impose(new XlteqY(sumVar ,rightHandVar));

			}else if(atom.equals("lt")){

				sumVar = new IntVar(store, "sum_" + new Object().hashCode(), IntervalDomain.MinInt, IntervalDomain.MaxInt);

				store.impose(new XltY(sumVar, rightHandVar));

			}else{
				assert false: "atom " + atom + " not recognized!";
			}

			store.impose(new SumWeight(vars, weights, sumVar));

		}else if(constraint.getAttributeValue("reference").equals("global:allDifferent")){
			
			// Extract the parameters of the constraint
			assert constraint.getChild("parameters") != null : "No parameters passed to the constraint " + constraint.getAttributeValue("name");
			String params = constraint.getChildText("parameters").trim();
			params = params.substring(1, params.length() - 1).trim(); // removing the brackets
			pattern = Pattern.compile("\\s+");
			String[] paramVarNames = pattern.split(params);
			
			Pattern constantPat = Pattern.compile("\\d+");
			
			// find JaCoP variables
			IntVar[] vars = new IntVar[paramVarNames.length];

			IntVar v;
			for(int i = 0; i < paramVarNames.length; i++){
				// constant parameter
				if(constantPat.matcher(paramVarNames[i]).matches()){
					int val = Integer.parseInt(paramVarNames[i]);
					v = new IntVar(store, val, val);
				// variable name
				}else{	
					assert Arrays.asList(varNames).contains(paramVarNames[i]): "mismatch between the constraint scope and the variables in parameters!";
					v = (IntVar)store.findVariable(paramVarNames[i]);
				
					// All these variables must be in the store
					assert v != null : "problem: variable " + paramVarNames[i] + " not found in the store! Note: constant parameters not supported in allDifferent";
				}
				vars[i] = v;
			}

			store.impose(new Alldifferent(vars));
		}else{
			System.err.println("The global constraint " + constraint.getAttributeValue("reference") + " is not known");
			System.exit(2);
		}
	}

	/** @see DCOPProblemInterface#rescale(Addable, Addable) */
	public void rescale(U multiply, U add) {

		Element relations = this.root.getChild("relations");

		if(relations != null){

			// Modify each relation
			for (Element relElmt : (List<Element>) relations.getChildren()) {

				// Take care of the default cost
				String defaultCost = relElmt.getAttributeValue("defaultCost");
				if (defaultCost != null) 
					relElmt.setAttribute("defaultCost", multiply.fromString(defaultCost.trim()).multiply(multiply).add(add).toString());

				// Take care of each utility/cost in the list of tuples
				StringBuilder builder = new StringBuilder ();
				for (Iterator<String> iter = Arrays.asList(relElmt.getText().split("\\|")).iterator(); iter.hasNext(); ) {

					String tuple = iter.next();
					String[] split = tuple.split(":");
					assert split.length > 0 && split.length <= 2 : "Incorrect tuple format: " + tuple;
					if (split.length > 1) // there is a utility specified
						builder.append(multiply.fromString(split[0].trim()).multiply(multiply).add(add) + ":" + split[1]);
					else 
						builder.append(split[0]);

					if (iter.hasNext()) 
						builder.append("|");
				}
				relElmt.setText(builder.toString());
			}
		}
		
		
		Element functions = this.root.getChild("functions");
		
		if (functions != null) {
			
			// Modify each function
			for (Element funcElmt : (List<Element>) functions.getChildren()) {

				// Get the functional expression expr and encapsulate it inside add(add, mul(multipy, expr))
				Element functionalElmt = funcElmt.getChild("expression").getChild("functional");
				functionalElmt.setText("add(" + add + ", mul(" + multiply + ", " + functionalElmt.getText() + "))");
			}
		}
	}


	/** @see XCSPparser#getSubProblem(String) */
	@Override
	public JaCoPxcspParser<U> getSubProblem (String agent) {

		/// @todo Could it be possible to reuse more code from the superclass?

		// Extract the set of variables owned by the agent
		HashSet<Element> varElmts = new HashSet<Element> ();
		for (Element var : (List<Element>) root.getChild("variables").getChildren()) 
			if (agent.equals(var.getAttributeValue("agent"))) 
				varElmts.add(var);

		// Create the XCSP instance element
		Element instance = new Element ("instance");

		// Create the "presentation" element
		Element presentation = new Element ("presentation");
		instance.addContent(presentation);
		presentation.setAttribute("name", agent);
		presentation.setAttribute("maximize", Boolean.toString(this.maximize()));
		presentation.setAttribute("format", "XCSP 2.1_FRODO");
		
		// Create the agents
		Element agents = new Element ("agents");
		instance.addContent(agents);
		HashSet<String> knownAgents = new HashSet<String> ();
		knownAgents.add(agent);
		if (this.mpc || this.publicAgents) // the agent is supposed to know all the agents
			knownAgents.addAll(this.getAgents());

		// Create the domains
		Element domains = new Element ("domains");
		instance.addContent(domains);

		// Create the variables
		Element variables = new Element ("variables");
		instance.addContent(variables);
		HashSet<String> varNames = new HashSet<String> (varElmts.size()); // internal variables and relevant external variables
		for (Element varElmt : varElmts) {
			varNames.add(varElmt.getAttributeValue("name"));
		}
		
		// In MPC mode, all variables are public
		if (this.mpc) 
			varElmts.addAll((List<Element>) root.getChild("variables").getChildren());

		// Create the constraints
		Element constraints = new Element ("constraints");
		HashSet<String> relationNames = new HashSet<String> ();
		HashSet<String> predicateNames = new HashSet<String> ();
		HashSet<String> probNames = new HashSet<String> ();
		HashSet<String> constNames = new HashSet<String> ();

		// Go through the list of constraints several times until we are sure we have identified all variables that should be known to this agent
		HashMap< String, HashSet<String> > varScopes = new HashMap< String, HashSet<String> > ();
		int nbrVars;
		do {
			nbrVars = varNames.size();

			// Go through the list of all constraints in the overall problem
			for (Element constraint : (List<Element>) root.getChild("constraints").getChildren()) {

				// Skip this constraint if it has already been added
				String constName = constraint.getAttributeValue("name");
				if (constNames.contains(constName)) 
					continue;

				// Get the list of variables in the scope of the constraint
				HashSet<String> scope = new HashSet<String> (Arrays.asList(constraint.getAttributeValue("scope").split("\\s+")));

				String refName = constraint.getAttributeValue("reference");

				Constraint cons = Constraint.EXTENSIONAL;

				// Check the nature of the constraint

				// Global constraint
				if(constraint.getAttributeValue("reference").startsWith("global:")){

					cons =  Constraint.GLOBAL;

					// Constraint in intension
				}else if(constraint.getChild("parameters") != null){

					cons = Constraint.INTENSIONAL;

				}

				// Check if the agent is not supposed to know the constraint
				String constOwner = constraint.getAttributeValue("agent");
				if (! "PUBLIC".equals(constOwner) && constOwner != null && ! constOwner.equals(agent)) {
					
					if (! this.mpc) { // record the variable scopes
						for (String var : scope) {
							HashSet<String> varScope = varScopes.get(var);
							if (varScope == null) {
								varScope = new HashSet<String> ();
								varScopes.put(var, varScope);
							}
							varScope.add(constOwner);
						}
					}
					
					continue;
				}

				// If any of the variables in the scope is owned by this agent or the constraint is a probability law that must be known to the agent, 
				// add the constraint to the list of constraints
				final boolean knownConst = "PUBLIC".equals(constOwner) || agent.equals(constOwner);
				for (String var : scope) {
					if (knownConst || varNames.contains(var)) {


						switch (cons) {
						// Skip this variable if it is apparently not necessary for the agent to know this constraint
						case PROBABILITY: // probability space
							if (! this.isRandom(var)) 
								continue;
							probNames.add(refName);
							break;

						case EXTENSIONAL:
							if (!this.extendedRandNeighborhoods && this.isRandom(var))
								continue;
							relationNames.add(refName);
							break;

						case INTENSIONAL: 
							if (!this.extendedRandNeighborhoods && this.isRandom(var))
								continue;
							predicateNames.add(refName);
							break;

						default:
							break;
						}

						constraints.addContent((Element) constraint.clone());
						constNames.add(constName);

						// Add all variables in the scope to the list of variables known to this agent
						for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
							String varName = varElmt.getAttributeValue("name");
							if (scope.contains(varName)) {
								varElmts.add(varElmt);
								if (varElmt.getAttributeValue("agent") == null) 
									varNames.add(varName);
							}
						}

						break;
					}
				}
			}
		} while (nbrVars != varNames.size()); // loop as long as another variable has been added to the list of known variables

		// Set the number of constraints
		constraints.setAttribute("nbConstraints", Integer.toString(constraints.getContentSize()));
		
		// Add the agents that own constraints over shared variables and my own variables
		for (Element constraint : (List<Element>) root.getChild("constraints").getChildren()) {
			
			// Get the list of variables in the scope of the constraint
			HashSet<String> scope = new HashSet<String> (Arrays.asList(constraint.getAttributeValue("scope").split("\\s+")));
			
			// Check whether the constraint owner should be known to the agent because the constraint scope involves a variable they share
			String constOwner = constraint.getAttributeValue("agent");
			if (! "PUBLIC".equals(constOwner) && constOwner != null && ! constOwner.equals(agent)) {
				for (String var : scope) {
					if (! this.isRandom(var) && varNames.contains(var)) { // skip random variables and unknown variables
						String varOwner = this.getOwner(var);
						if (varOwner == null || varOwner.equals(agent)) { // the variable is shared or owned by this agent
							knownAgents.add(constOwner);
							break;
						}
					}
				}
			}
		}

		// Add the domains of the variables
		HashSet<String> domNames = new HashSet<String> ();
		for (Element varElmt : varElmts) {

			String domName = varElmt.getAttributeValue("domain");
			if (! domNames.add(domName)) // domain already added to the list of domains
				continue;
			for (Element domain : (List<Element>) root.getChild("domains").getChildren()) {
				if (domName.equals(domain.getAttributeValue("name"))) {
					domains.addContent((Element) domain.clone());
					break;
				}
			}
		}

		// Set the number of domains
		domains.setAttribute("nbDomains", Integer.toString(domNames.size()));

		// Add all variables known to this agent
		variables.setAttribute("nbVariables", Integer.toString(varElmts.size()));
		for (Element varElmt : varElmts) {
			Element newVarElmt = (Element) varElmt.clone();
			variables.addContent(newVarElmt);
			
			// Check the owner of this variable
			String owner = varElmt.getAttributeValue("agent");
			if (owner != null) 
				knownAgents.add(owner);
			else if (! "random".equals(varElmt.getAttributeValue("type"))) { // shared variable; set its agent scope
				HashSet<String> varScope = varScopes.get(varElmt.getAttributeValue("name"));
				if (varScope != null) {
					String scope = "";
					for (String neigh : varScope) 
						scope += neigh + " ";
					newVarElmt.setAttribute("scope", scope);
				}
			}
		}
		
		// Fill in the list of agents
		agents.setAttribute("nbAgents", Integer.toString(knownAgents.size()));
		for (Element agentElmt : (List<Element>) this.root.getChild("agents").getChildren()) 
			if (knownAgents.contains(agentElmt.getAttributeValue("name"))) 
				agents.addContent((Element) agentElmt.clone());

		Element elmt;
		int maxConstraintArity = 0;

		
		// Create the relations (if the original problem contained any)
		if (root.getChild("relations") != null) {

			// Create the relations
			elmt = new Element ("relations");
			instance.addContent(elmt);

			// Go through the list of all relations in the overall problem
			for (Element relation : (List<Element>) root.getChild("relations").getChildren()) {

				// Add the relation to the list of relations if it is referred to by any of this agent's constraints
				if (relationNames.remove(relation.getAttributeValue("name"))) {
					elmt.addContent((Element) relation.clone());
					maxConstraintArity = Math.max(maxConstraintArity, Integer.parseInt(relation.getAttributeValue("arity")));
				}
			}
			elmt.setAttribute("nbRelations", Integer.toString(elmt.getContentSize()));
		}

		if (! relationNames.isEmpty()) 
			this.foundUndefinedRelations(relationNames);

		
		// Create the probabilities (if the original problem contained any)
		if (root.getChild("probabilities") != null) {
			elmt = new Element ("probabilities");
			instance.addContent(elmt);
			elmt.setAttribute("nbProbabilities", Integer.toString(probNames.size()));

			// Go through the list of all probabilities in the overall problem
			for (Element probability : (List<Element>) root.getChild("probabilities").getChildren()) {

				// Add the probability to the list of probabilities if it is referred to by any of this agent's constraints
				if (probNames.remove(probability.getAttributeValue("name"))) {
					elmt.addContent((Element) probability.clone());
					maxConstraintArity = Math.max(maxConstraintArity, Integer.parseInt(probability.getAttributeValue("arity")));
				}
			}
		}

		if (! probNames.isEmpty()) 
			System.err.println("Undefined probabilities: " + probNames);

		
		// Create the predicates (if the original problem contained any)
		if (root.getChild("predicates") != null) {
			elmt = new Element ("predicates");
			instance.addContent(elmt);

			// Go through the list of all predicates in the overall problem
			for (Element predicate : (List<Element>) root.getChild("predicates").getChildren()) {

				// Add the predicate to the list of predicates if it is referred to by any of this agent's constraints
				if (predicateNames.remove(predicate.getAttributeValue("name"))) {
					elmt.addContent((Element) predicate.clone());
				}
			}
			elmt.setAttribute("nbPredicates", Integer.toString(elmt.getContentSize()));
		}

		
		// Create the functions (if the original problem contained any)
		if (root.getChild("functions") != null) {
			elmt = new Element ("functions");
			instance.addContent(elmt);

			// Go through the list of all functions in the overall problem
			for (Element function : (List<Element>) root.getChild("functions").getChildren()) {

				// Add the function to the list of functions if it is referred to by any of this agent's constraints
				if (predicateNames.remove(function.getAttributeValue("name"))) {
					elmt.addContent((Element) function.clone());
				}
			}
			elmt.setAttribute("nbFunctions", Integer.toString(elmt.getContentSize()));
		}

		if (! predicateNames.isEmpty()) 
			System.err.println("Undefined predicates or functions: " + predicateNames);

		
		// Set the maxConstraintArity
		presentation.setAttribute("maxConstraintArity", Integer.toString(maxConstraintArity));

		// Add the "constraints" element after the "relations" and "probabilities" element
		instance.addContent(constraints);
		
		JaCoPxcspParser<U> out = newInstance (agent, instance);
		return out;
	}
}


