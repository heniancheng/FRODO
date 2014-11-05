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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.StringTokenizer;

import JaCoP.core.Store;
import JaCoP.constraints.*;
import JaCoP.core.IntVar;
import JaCoP.core.IntervalDomain;
import JaCoP.core.Var;

/**
 * Predicate wrapper that translates a predicate expression from XCSP into constraints
 * available in JaCoP. It accepts only functional representation. Possibly, 
 * some auxiliary variables will be created.
 * 
 * @author Radoslaw Szymanek and Krzysztof Kuchcinski for the initial version developed for JaCoP 2.4; 
 * @author Arnaud Jutzeler and Thomas Leaute for the modified version ported to FRODO and JaCoP 3. 
 */

public class Predicate extends DecomposedConstraint{

	/** The XCSP parameters of the constraint */
	String constraintParameters;

	/** Whether to print out debug information */
	final boolean debug = false;

	/** The functional representation of the predicate */
	String description;

	/** The constraint store */
	Store store;

	/** The XCSP parameters of the predicate */
	String predicateParameters;

	/** The auxiliary constraints created */
	ArrayList<Constraint> decompositionConstraints;
	
	/** The auxiliary variables created */
	ArrayList<Var> auxilaryVariables;
	
	/** The utility variable (if this is a soft constraint) */
	IntVar utilVar;
	
	/**
	 * It creates/imposes constraints into the store as soon as the Predicate
	 * constraint is being imposed.
	 * @param constraintParameters parameters to the constraint.
	 * @param predicateParameters parameters specified within a predicate definition.
	 * @param description description of the constraint specified as the predicate.
	 * @param store the constraint store in which context the constraints are being created/imposed.
	 */
	public Predicate(String constraintParameters, String predicateParameters,
					 String description, Store store) {

		this.store = store;

		this.constraintParameters = constraintParameters;
		this.predicateParameters = predicateParameters;
		this.description = description;

	}

	/** @see JaCoP.constraints.DecomposedConstraint#decompose(JaCoP.core.Store) */
	@Override
	public ArrayList<Constraint> decompose(Store store) {

		if (decompositionConstraints != null)
			return decompositionConstraints;
		
		decompositionConstraints = new ArrayList<Constraint>();
		auxilaryVariables = new ArrayList<Var>();
		
		StringTokenizer tokenizer = new StringTokenizer(predicateParameters, " ");
		StringTokenizer tokenizerConstraint = new StringTokenizer(
				constraintParameters, " ");

		HashMap<String, Object> variableMaping = new HashMap<String, Object>();

		while (tokenizer.hasMoreTokens()) {

			String nextToken = tokenizer.nextToken();

			if (nextToken.equals("int"))
				continue;
			
			String name = tokenizerConstraint.nextToken();
			
			Var temp = store.findVariable(name);
			
			if (temp == null)
				variableMaping.put(nextToken, Integer.valueOf(name));
			else
				variableMaping.put(nextToken, temp);

		}

		if (debug)
			System.out.println(variableMaping);

		/// TODO, test if trimming spaces does not cause problems.
		description = description.replace(" ", "");
		
		tokenizer = new StringTokenizer(description, "(,)");

		String nextToken = tokenizer.nextToken();

		Object token = parse(nextToken, tokenizer, store, variableMaping);
		assert token != null;

		if (debug)
			System.out.println(token);

		if (token instanceof Constraint) // hard constraint
			decompositionConstraints.add( (Constraint) token );
		
		else if (token instanceof IntVar) // soft constraint
			this.utilVar = (IntVar) token;
		
		else if (token instanceof Integer) // constant soft constraint
			this.utilVar = new IntVar (this.store, (Integer) token, (Integer) token);
		
		else if ("true".equals(token)) { }
		
		else if ("false".equals(token)) 
			this.store.impose(new XeqC (new IntVar (this.store, 0, 0), 1));
		
		else 
			System.err.println("Unrecognized token: " + token);

		return decompositionConstraints;
	}

	
    /**
     * It allows to obtain the constraint specified by the predicate
     * without imposing it.
     *
     * @param store the constraint store in which context the constraint is being created.
     * @return the constraint represented by this predicate constraint (expression).
     */

    public PrimitiveConstraint getConstraint(Store store) {

            StringTokenizer tokenizer = new StringTokenizer(predicateParameters,
                            " ");

            StringTokenizer tokenizerConstraint = new StringTokenizer(
                            constraintParameters, " ");

            HashMap<String, Object> variableMaping = new HashMap<String, Object>();

            while (tokenizer.hasMoreTokens()) {

                    String nextToken = tokenizer.nextToken();

                    if (nextToken.equals("int"))
                            continue;

                    String name = tokenizerConstraint.nextToken();

                    Var temp = store.findVariable(name);
                    if (temp == null)
                            variableMaping.put(nextToken, Integer.valueOf(name));
                    else
                            variableMaping.put(nextToken, temp);

            }

            if (debug)
                    System.out.println(variableMaping);

            description = description.replace(" ", "");
            
            tokenizer = new StringTokenizer(description, "(,)");

            String nextToken = tokenizer.nextToken();

            Object token = parse(nextToken, tokenizer, store, variableMaping);

            if (debug)
                    System.out.println(token);

            return (PrimitiveConstraint) token; /// @bug Can't the token also be an IntVar or an Integer?... 

    }

	
	
	/**
	 * @param token 			the current token
	 * @param tokenizer 		the tokenizer
	 * @param store 			the constraint store
	 * @param variableMaping 	a mapping from name to variable or constant
	 * @return a constraint
	 */
	private Object parse(String token, StringTokenizer tokenizer, Store store,
			HashMap<String, Object> variableMaping) {
		
		try {
			return Integer.valueOf(token);
		} catch (Exception ex) {
			// if not an integer then just go on.
		}

        if (variableMaping.get(token) != null)
			return variableMaping.get(token);
		else {

			if (token.equals("abs")) {
				
				String nextToken = tokenizer.nextToken();
				
				if (nextToken.equals("sub")) { // |o1 - o2|

					Object o1 = parse(tokenizer.nextToken(), tokenizer, store, variableMaping);
					Object o2 = parse(tokenizer.nextToken(), tokenizer, store, variableMaping);
					
					// Convert o1 and o2 to IntVars
					IntVar v1 = null, v2 = null;
					if (o1 instanceof Integer) {
						if (o2 instanceof Integer) 
							return Math.abs((Integer)o1 - (Integer)o2);
						Integer i1 = (Integer)o1;
						v2 = (IntVar) o2;
						if (i1 == 0) { // actually |v2|
							if (v2.min() >= 0) 
								return v2;
							else {
								IntVar auxilary = (v2.max() <= 0 ? 
										new IntVar(store, -v2.max(), -v2.min()) :
										new IntVar(store, 0, Math.max(Math.abs(v2.min()), Math.abs(v2.max()))));
								auxilaryVariables.add(auxilary);
								decompositionConstraints.add(new AbsXeqY(v2, auxilary));
								return auxilary;
							}
						}
						v1 = new IntVar (store, i1, i1);
					} else if (o2 instanceof Integer) {
						v1 = (IntVar) o1;
						Integer i2 = (Integer)o2;
						if (i2 == 0) { // actually |v1|
							if (v1.min() >= 0) 
								return v1;
							else {
								IntVar auxilary = (v1.max() <= 0 ?
										new IntVar(store, -v1.max(), -v1.min()) : 
										new IntVar(store, 0, Math.max(Math.abs(v1.min()), Math.abs(v1.max()))));
								auxilaryVariables.add(auxilary);
								decompositionConstraints.add(new AbsXeqY(v1, auxilary));
								return auxilary;
							}
						}
						v2 = new IntVar (store, i2, i2);
					} else {
						v1 = (IntVar) o1;
						v2 = (IntVar) o2;
					}
					
					int auxMin = Math.max(0, Math.max(v1.min() - v2.max(), v2.min() - v1.max()));
					IntVar aux = new IntVar(store, auxMin, Math.max(Math.abs(v1.min() - v2.max()), Math.abs(v1.max() - v2.min())));
					auxilaryVariables.add(aux);
					decompositionConstraints.add(new Distance (v1, v2, aux));
					return aux;

				} else {

					Object o1 = parse(nextToken, tokenizer, store,
							variableMaping);

					if (o1 instanceof Integer) 
						return new Integer (Math.abs((Integer) o1));

					else if (o1 instanceof IntVar) {
						IntVar v1 = (IntVar) o1;
						if (v1.min() >= 0) 
							return v1;
						else {
							IntVar auxilary = (v1.max() <= 0 ?
									new IntVar(store, -v1.max(), -v1.min()) :
									new IntVar(store, 0, Math.max(Math.abs(v1.min()), Math.abs(v1.max()))));
							auxilaryVariables.add(auxilary);
							decompositionConstraints.add(new AbsXeqY(v1, auxilary));
							return auxilary;
						}
					}

					System.err.println("Failed to parse abs(" + o1 + ")");
					return null;
				}
			}
			
			if (token.equals("neg")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof Integer) 
					return new Integer (- (Integer) o1);

				else if (o1 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar auxilary = new IntVar(store, - v1.max(), - v1.min());
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XmulCeqZ(v1, -1, auxilary));
					return auxilary;
				}
				
				System.err.println("Failed to parse neg(" + o1 + ")");
				return null;
			}

			if (token.equals("sub")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					IntVar auxilary = new IntVar(store, v1.min() - v2.max(), v1.max() - v2.min());
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XplusYeqZ(v2, auxilary, v1));
					return auxilary;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (c2 == 0) 
						return v1;
					IntVar auxilary = new IntVar(store, v1.min() - c2, v1.max() - c2);
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XplusCeqZ(auxilary, c2, v1));
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					IntVar auxilary = new IntVar(store, c1 - v2.max(), c1 - v2.min());
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XplusYeqC(v2, auxilary, c1));
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer ((Integer)o1 - (Integer)o2);

				System.err.println("Failed to parse sub(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("add")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					IntVar auxilary = new IntVar(store, v1.min() + v2.min(), v1.max() + v2.max());
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XplusYeqZ(v1, v2, auxilary));
					return auxilary;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (c2 == 0) 
						return v1;
					IntVar auxilary = new IntVar(store, v1.min() + c2, v1.max() + c2);
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XplusCeqZ(v1, c2, auxilary));
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					if (c1 == 0) 
						return v2;
					IntVar auxilary = new IntVar(store, c1 + v2.min(), c1 + v2.max());
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XplusCeqZ(v2, c1, auxilary));
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer ((Integer)o1 + (Integer)o2);

				System.err.println("Failed to parse add(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("mul")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					
					// Compute the bounds
					int min = v1.min() * v2.min();
					int max = min;
					for (int tmp : Arrays.asList(v1.min() * v2.max(), v1.max() * v2.min(), v1.max() * v2.max())) {
						min = Math.min(min, tmp);
						max = Math.max(max, tmp);
					}
					
					IntVar auxilary = new IntVar(store, min, max);
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XmulYeqZ(v1, v2, auxilary));
					return auxilary;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					
					if (c2 == 1) 
						return v1;
					
					// Compute the bounds
					int min = v1.min() * c2;
					int max = min;
					int tmp = v1.max() * c2;
					min = Math.min(min, tmp);
					max = Math.max(max, tmp);
					
					IntVar auxilary = new IntVar(store, min, max);
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XmulCeqZ(v1, c2, auxilary));
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					
					if (c1 == 1) 
						return v2;
					
					// Compute the bounds
					int min = v2.min() * c1;
					int max = min;
					int tmp = v2.max() * c1;
					min = Math.min(min, tmp);
					max = Math.max(max, tmp);
					
					IntVar auxilary = new IntVar(store, min, max);
					auxilaryVariables.add(auxilary);
					decompositionConstraints.add(new XmulCeqZ(v2, c1, auxilary));
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer ((Integer)o1 * (Integer)o2);

				System.err.println("Failed to parse mul(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("div")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					v2.domain.subtract(0); // v2 != 0
					int min2 = (v2.min() == 0 ? 1 : v2.min());
					int max2 = (v2.max() == 0 ? -1 : v2.max());
					
					// Compute the bounds for the auxiliary variable aux = v1 / v2
					int min = v1.min() / min2;
					int max = min;
					for (int tmp : Arrays.asList(v1.min() / max2, v1.max() / min2, v1.max() / max2)) {
						min = Math.min(min, tmp);
						max = Math.max(max, tmp);
					}
					IntVar aux = new IntVar(store, min, max);

					auxilaryVariables.add(aux);
					decompositionConstraints.add(new XdivYeqZ (v1, v2, aux));
					
					return aux;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					
					if (c2 == 1) 
						return v1;
					
					// Compute the bounds for the auxiliary variable aux = v1 / c2
					int min = v1.min() / c2;
					int max = min;
					int tmp = v1.max() / c2;
					min = Math.min(min, tmp);
					max = Math.max(max, tmp);
					IntVar aux = new IntVar(store, min, max);

					auxilaryVariables.add(aux);
					decompositionConstraints.add(new XdivYeqZ (v1, new IntVar (store, c2, c2), aux));
					
					return aux;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					v2.domain.subtract(0); // v2 != 0
					int min2 = (v2.min() == 0 ? 1 : v2.min());
					int max2 = (v2.max() == 0 ? -1 : v2.max());
					
					// Compute the bounds for the auxiliary variable aux = c1 / v2
					int min = c1 / min2;
					int max = min;
					int tmp = c1 / max2;
					min = Math.min(min, tmp);
					max = Math.max(max, tmp);
					IntVar aux = new IntVar(store, min, max);

					auxilaryVariables.add(aux);
					decompositionConstraints.add(new XdivYeqZ (new IntVar(store, c1, c1), v2, aux));
					
					return aux;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer ((Integer)o1 / (Integer)o2);

				System.err.println("Failed to parse div(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("mod")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					v2.domain.subtract(0); // v2 != 0
					int min2 = (v2.min() == 0 ? 1 : v2.min());
					int max2 = (v2.max() == 0 ? -1 : v2.max());
					
					// Check if we can guarantee that v1 mod v2 = v1
					if (v1.min() >= 0 && v1.max() < min2) 
						return v1;
					
					IntVar T3; // t3 = |v2|
					if (min2 > 0) 
						T3 = v2;
					else {
						T3 = (v2.max() <= 0 ?
								new IntVar(store, -v2.max(), -v2.min()) :
								new IntVar(store, 0, Math.max(Math.abs(min2), Math.abs(max2))));
						auxilaryVariables.add(T3);
						decompositionConstraints.add(new AbsXeqY(v2, T3));
					}
					
					IntVar auxilary = new IntVar(store, 0, T3.max() - 1); // a < t3
					IntVar T2 = new IntVar(store, v1.min() - auxilary.max(), v1.max()); // t2 = v1 - a
					
					// Compute the bounds for t1 = t2 / v2
					int min = T2.min() / min2;
					int max = min;
					for (int tmp : Arrays.asList(T2.min() / max2, T2.max() / min2, T2.max() / max2)) {
						min = Math.min(min, tmp);
						max = Math.max(max, tmp);
					}
					
					IntVar T1 = new IntVar(store, min, max);
					
					auxilaryVariables.add(T1);
					auxilaryVariables.add(T2);
					auxilaryVariables.add(auxilary);
					
					decompositionConstraints.add(new XmulYeqZ(T1, v2, T2));
					decompositionConstraints.add(new XplusYeqZ(T2, auxilary, v1));
					decompositionConstraints.add(new XltY(auxilary, T3));

					return auxilary;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					
					// Check if we can guarantee that v1 mod c2 = v1
					if (v1.min() >= 0 && v1.max() < c2) 
						return v1;

					IntVar auxilary = new IntVar(store, 0, Math.abs(c2) - 1); // a < |c2|
					IntVar T2 = new IntVar(store, v1.min() - auxilary.max(), v1.max()); // t2 = v1 - a
					
					// Compute the bounds for t1 = t2 / c2
					int min = T2.min() / c2;
					int max = min;
					int tmp = T2.max() / c2;
					min = Math.min(min, tmp);
					max = Math.max(max, tmp);
					
					IntVar T1 = new IntVar(store, min, max);
					
					auxilaryVariables.add(T1);
					auxilaryVariables.add(T2);
					auxilaryVariables.add(auxilary);

					decompositionConstraints.add(new XmulCeqZ(T1, c2, T2));
					decompositionConstraints.add(new XplusYeqZ(T2, auxilary, v1));
					decompositionConstraints.add(new XltC(auxilary, Math.abs(c2)));

					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					v2.domain.subtract(0); // v2 != 0
					int min2 = (v2.min() == 0 ? 1 : v2.min());
					int max2 = (v2.max() == 0 ? -1 : v2.max());
					
					// Check if we can guarantee that c1 mod v2 = c1
					if (c1 >= 0 && c1 < min2) 
						return c1;

					IntVar T3; // t3 = |v2|
					if (min2 > 0) 
						T3 = v2;
					else {
						T3 = (v2.max() <= 0 ?
								new IntVar(store, -v2.max(), -v2.min()) :
								new IntVar(store, 0, Math.max(Math.abs(min2), Math.abs(max2))));
						auxilaryVariables.add(T3);
						decompositionConstraints.add(new AbsXeqY(v2, T3));
					}
					
					IntVar auxilary = new IntVar(store, 0, T3.max() - 1); // a < t3
					IntVar T2 = new IntVar(store, c1 - auxilary.max(), c1); // t2 = c1 - a
					
					// Compute the bounds for t1 = t2 / v2
					int min = T2.min() / min2;
					int max = min;
					for (int tmp : Arrays.asList(T2.min() / max2, T2.max() / min2, T2.max() / max2)) {
						min = Math.min(min, tmp);
						max = Math.max(max, tmp);
					}
					
					IntVar T1 = new IntVar(store, min, max);
					
					auxilaryVariables.add(T1);
					auxilaryVariables.add(T2);
					auxilaryVariables.add(auxilary);
					
					decompositionConstraints.add(new XmulYeqZ(T1, v2, T2));
					decompositionConstraints.add(new XplusYeqC(T2, auxilary, c1));
					decompositionConstraints.add(new XltY(auxilary, T3));


					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer ((Integer)o1 % (Integer)o2);

				System.err.println("Failed to parse mod(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("pow")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof Integer) {
					String val = String.valueOf(o1);
					if (variableMaping.get(val) == null)
						variableMaping.put(val,	new IntVar(store, (Integer) o1, (Integer) o1));
					o1 = variableMaping.get(val);
				}

				if (o2 instanceof Integer) {
					String val = String.valueOf(o2);
					if (variableMaping.get(val) == null)
						variableMaping.put(val,
								new IntVar(store, (Integer) o2, (Integer) o2));
					o2 = variableMaping.get(val);
				}
				

				IntVar v1 = (IntVar) o1;
				IntVar v2 = (IntVar) o2;
				IntVar auxilary = null;
				
				// If the variables can take negative values, we need to add specific variables and constraints as JaCoP's native XexpYeqZ constraint
				// does not handle negative values.
				if(v1.min() < 0){
					
					IntVar v3 = new IntVar(store, 2, 2); // v3 = 2
					auxilaryVariables.add(v3);
					
					IntVar reminder = new IntVar(store, 0, 1); // reminder = v2 mod 2
					auxilaryVariables.add(reminder);
					
					IntVar abs1 = new IntVar(store, 0, Math.max(Math.abs(v1.min()), Math.abs(v1.max()))); // abs1 = |v1|
					auxilaryVariables.add(abs1);
					
					IntVar posPow = new IntVar(store, 0, 
							(int) Math.pow(abs1.max(), Math.max(Math.abs(v2.min()), Math.abs(v2.max())))); // posPow = abs1 ^ |v2|
					auxilaryVariables.add(posPow);
					
					auxilary = new IntVar(store, - posPow.max(), posPow.max()); // aux = +/- posPow
					auxilaryVariables.add(auxilary);
					
					IntVar negPow = new IntVar(store, - posPow.max(), 0); // negPow = - posPow
					auxilaryVariables.add(negPow);
					
					decompositionConstraints.add(new AbsXeqY(v1, abs1));
					
					decompositionConstraints.add(new XmodYeqZ(v2, v3, reminder));
					
					decompositionConstraints.add(new XmulCeqZ(posPow, -1, negPow));
					
					if(v2.min() < 0){
						
						IntVar abs2 = new IntVar(store, 0, Math.max(Math.abs(v2.min()), Math.abs(v2.max()))); // abs2 = |v2|
						auxilaryVariables.add(abs2);
						
						IntVar result2 = new IntVar(store, - posPow.max(), posPow.max()); // result2 = posPow or result2 = negPow = - posPow
						auxilaryVariables.add(result2);
						
						decompositionConstraints.add(new AbsXeqY(v2, abs2));
						
						decompositionConstraints.add(new XexpYeqZ(abs1, abs2, posPow));
						
						decompositionConstraints.add(new IfThenElse(
								new XltC(v1, 0), 
								new IfThenElse(
										new XeqC(reminder, 0), 
										new XeqY(result2, posPow), 
										new XeqY(result2, negPow)),  
								new XeqY(result2, posPow)));
						
						decompositionConstraints.add(new IfThenElse(
								new XltC(v2, 0), 
								new IfThenElse(
										new XeqC(abs1, 1), 
										new XeqY(auxilary, result2) , 
										new XeqC(auxilary, -1)), 
								new XeqY(auxilary, result2)));
						
					}else{
						
						decompositionConstraints.add(new XexpYeqZ(abs1, v2, posPow));
						
						decompositionConstraints.add(new IfThenElse(
								new XltC(v1, 0), 
								new IfThenElse(
										new XeqC(reminder, 0), 
										new XeqY(auxilary, posPow), 
										new XeqY(auxilary, negPow)), 
								new XeqY(auxilary, posPow)));
					}
				}else{
					
					int maxAbs2 = Math.max(Math.abs(v2.min()), Math.abs(v2.max())); // abs2 = |v2|
					auxilary = new IntVar(store, 0, (int) Math.pow(v1.max(), maxAbs2));
					auxilaryVariables.add(auxilary);
					
					if(v2.min() < 0){
						
						IntVar abs2 = new IntVar(store, 0, maxAbs2);
						auxilaryVariables.add(abs2);
						
						IntVar result = new IntVar(store, 0, (int) Math.pow(v1.max(), abs2.max())); // result = v1 ^ abs2
						auxilaryVariables.add(result);
						
						decompositionConstraints.add(new AbsXeqY(v2, abs2));
						
						decompositionConstraints.add(new XexpYeqZ(v1, abs2, result));
						
						decompositionConstraints.add(new IfThenElse(
								new XltC(v2, 0), 
								new IfThenElse(
										new XeqC(v1, 1), 
										new XeqC(auxilary, 1), 
										new XeqC(auxilary, -1)), 
								new XeqY(auxilary, result)));
						
					// the domains only contain positive values
					}else{

						decompositionConstraints.add(new XexpYeqZ(v1, v2, auxilary));
					}
				}
				
				///@bug when the value of o2 is 0,1 the JaCoP constraint XexpYeqZ shows invalid behaviour
				if(v1.dom().isIntersecting(-1, 1) ||
						v2.dom().isIntersecting(-1, 1)){
							System.err.println("The power operation is performed on variables that have -1, 0, 1 in their domains." +
									"\nThis can result in erroneous solutions due to some bugs in JaCoP.");
				}

				return auxilary;

			}
			
			if (token.equals("min")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					if (v1.max() <= v2.min()) 
						return v1;
					else if (v2.min() <= v1.min()) 
						return v2;
					IntVar auxilary = new IntVar(store, Math.min(v1.min(), v2.min()), Math.min(v1.max(), v2.max()));
					auxilaryVariables.add(auxilary);
					
					IntVar[] listVars = {v1, v2};
					
					decompositionConstraints.add(new Min(listVars, auxilary));
					
					return auxilary;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (v1.max() <= c2) 
						return v1;
					else if (c2 <= v1.min()) 
						return c2;
					IntVar auxilary = new IntVar(store, Math.min(v1.min(), c2), Math.min(v1.max(), c2));
					auxilaryVariables.add(auxilary);
					
					IntVar[] listVars = {v1, new IntVar(store, c2, c2)};
					
					decompositionConstraints.add(new Min(listVars, auxilary));
							
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					if (c1 <= v2.min()) 
						return c1;
					else if (v2.min() <= c1) 
						return v2;
					IntVar auxilary = new IntVar(store, Math.min(c1, v2.min()), Math.min(c1, v2.max()));
					auxilaryVariables.add(auxilary);
					
					IntVar[] listVars = {v2, new IntVar(store, c1, c1)};
					
					decompositionConstraints.add(new Min(listVars, auxilary));
					
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer (Math.min((Integer)o1, (Integer)o2));

				System.err.println("Failed to parse min(" + o1 + ", " + o2 + ")");
				return null;
			}
			
			if (token.equals("max")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1;
					IntVar v2 = (IntVar) o2;
					if (v1.min() >= v2.max()) 
						return v1;
					else if (v2.min() >= v1.max()) 
						return v2;
					IntVar auxilary = new IntVar(store, Math.max(v1.min(), v2.min()), Math.max(v1.max(), v2.max()));
					auxilaryVariables.add(auxilary);
					
					IntVar[] listVars = {v1, v2};
					
					decompositionConstraints.add(new Max(listVars, auxilary));
					
					return auxilary;
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (v1.min() >= c2) 
						return v1;
					else if (c2 >= v1.max()) 
						return c2;
					IntVar auxilary = new IntVar(store, Math.max(v1.min(), c2), Math.max(v1.max(), c2));
					auxilaryVariables.add(auxilary);
					
					IntVar[] listVars = {v1, new IntVar(store, c2, c2)};
					
					decompositionConstraints.add(new Max(listVars, auxilary));
							
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					if (c1 >= v2.max()) 
						return c1;
					else if (v2.min() >= c1) 
						return v2;
					IntVar auxilary = new IntVar(store, Math.max(c1, v2.min()), Math.max(c1, v2.max()));
					auxilaryVariables.add(auxilary);
					
					IntVar[] listVars = {v2, new IntVar(store, c1, c1)};
					
					decompositionConstraints.add(new Max(listVars, auxilary));
					
					return auxilary;
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return new Integer (Math.max((Integer)o1, (Integer)o2));

				System.err.println("Failed to parse max(" + o1 + ", " + o2 + ")");
				return null;
			}
			
			if (token.equals("if")) {
				
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o3 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				
				if ("true".equals(o1)) 
					return o2;
				else if ("false".equals(o1)) 
					return o3;

				if (o1 instanceof PrimitiveConstraint && o2 instanceof IntVar && o3 instanceof IntVar) {
					IntVar v2 = (IntVar) o2;
					IntVar v3 = (IntVar) o3;
					IntervalDomain auxDom = new IntervalDomain (v2.min(), v2.max());
					auxDom.addDom(v3.domain);
					IntVar auxilary = new IntVar(store, auxDom);
					auxilaryVariables.add(auxilary);
					PrimitiveConstraint thenCons = new XeqY(auxilary, v2);
					PrimitiveConstraint elseCons = new XeqY(auxilary, v3);
					
					decompositionConstraints.add(new IfThenElse((PrimitiveConstraint) o1, 
							  thenCons,
							  elseCons));
					
					return auxilary;
				} else if (o1 instanceof PrimitiveConstraint && o2 instanceof IntVar && o3 instanceof Integer) {
					IntVar v2 = (IntVar) o2;
					Integer c3 = (Integer) o3;
					IntervalDomain auxDom = new IntervalDomain (c3, c3);
					auxDom.addDom(v2.domain);
					IntVar auxilary = new IntVar(store, auxDom);
					auxilaryVariables.add(auxilary);
					
					PrimitiveConstraint thenCons = new XeqY(auxilary, v2);
					PrimitiveConstraint elseCons = new XeqC(auxilary, c3);
					
					decompositionConstraints.add(new IfThenElse((PrimitiveConstraint) o1, 
							  thenCons,
							  elseCons));
					
					return auxilary;
				} else if (o1 instanceof PrimitiveConstraint && o2 instanceof Integer && o3 instanceof IntVar) {
					Integer c2 = (Integer) o2;
					IntVar v3 = (IntVar) o3;
					IntervalDomain auxDom = new IntervalDomain (c2, c2);
					auxDom.addDom(v3.domain);
					IntVar auxilary = new IntVar(store, auxDom);
					auxilaryVariables.add(auxilary);
					
					PrimitiveConstraint thenCons = new XeqC(auxilary, c2);
					PrimitiveConstraint elseCons = new XeqY(auxilary, v3);
					
					decompositionConstraints.add(new IfThenElse((PrimitiveConstraint) o1, 
							  thenCons,
							  elseCons));
					
					return auxilary;
				} else if (o1 instanceof PrimitiveConstraint && o2 instanceof Integer && o3 instanceof Integer) {
					Integer c2 = (Integer) o2;
					Integer c3 = (Integer) o3;
					IntervalDomain auxDom = new IntervalDomain (c2, c2);
					auxDom.addDom(new IntervalDomain (c3, c3));
					IntVar auxilary = new IntVar(store, auxDom);
					auxilaryVariables.add(auxilary);
					
					PrimitiveConstraint thenCons = new XeqC(auxilary, c2);
					PrimitiveConstraint elseCons = new XeqC(auxilary, c3);
					
					decompositionConstraints.add(new IfThenElse((PrimitiveConstraint) o1, 
							  thenCons,
							  elseCons));
					
					return auxilary;
				}
				
				System.err.println("Failed to parse:\n\t if \n\t " + o1 + "\n\t then \n\t " + o2 + " \n\t else \n\t " + o3);
				return null;
			}

			if (token.equals("eq")) {
				
				String nextToken = tokenizer.nextToken();

				if (nextToken.equals("abs")) { // | ... | = ...
					
					String nextNextToken = tokenizer.nextToken();

					if (nextNextToken.equals("sub")) { // |o1 - o2| = o3

						Object o1 = parse(tokenizer.nextToken(), tokenizer,
								store, variableMaping);
						Object o2 = parse(tokenizer.nextToken(), tokenizer,
								store, variableMaping);
						Object o3 = parse(tokenizer.nextToken(), tokenizer,
								store, variableMaping);

						if (o1 instanceof Integer) {
							String val = String.valueOf(o1);
							if (variableMaping.get(val) == null)
								variableMaping.put(val, new IntVar(store, (Integer) o1,
										(Integer) o1));
							o1 = variableMaping.get(val);
						}

						if (o2 instanceof Integer) {
							String val = String.valueOf(o2);
							if (variableMaping.get(val) == null)
								variableMaping.put(val, new IntVar(store, (Integer) o2,
										(Integer) o2));
							o2 = variableMaping.get(val);
						}

						if (o3 instanceof Integer) {
							String val = String.valueOf(o3);
							if (variableMaping.get(val) == null)
								variableMaping.put(val, new IntVar(store, (Integer) o3,
										(Integer) o3));
							o3 = variableMaping.get(val);
						}

						return new Distance((IntVar) o1, (IntVar) o2,
								(IntVar) o3);

					} else { // |o1| = o2

						Object o1 = parse(nextNextToken, tokenizer, store,
								variableMaping);
						
						Object o2 = parse(tokenizer.nextToken(), tokenizer,
								store, variableMaping);

						if(o1 instanceof IntVar && o2 instanceof IntVar){
						
							return new AbsXeqY((IntVar) o1, (IntVar) o2);
						
						}else if(o1 instanceof IntVar && o2 instanceof Integer){
							
							IntVar auxilary = new IntVar(store, (Integer) o2,
									(Integer) o2);
							auxilaryVariables.add(auxilary);
							
							return new AbsXeqY((IntVar) o1, auxilary);
							
						}else if(o1 instanceof Integer && o2 instanceof IntVar){
							
							return new XeqC((IntVar) o2, Math.abs((Integer) o1));
							
						}
						
						System.err.println("Failed to parse eq(abs(" + o1 + "), " + o2 + ")");
						return null;
					}

				}

				if (nextToken.equals("add")) { // o1 + o2 = o3
					
					Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);
					Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);
					Object o3 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);

					if (o1 instanceof IntVar && o2 instanceof IntVar
							&& o3 instanceof IntVar) { // X + Y = Z
						return (new XplusYeqZ((IntVar) o1, (IntVar) o2,
								(IntVar) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof Integer
							&& o3 instanceof IntVar) { // X + C = Z
						return (new XplusCeqZ((IntVar) o1, (Integer) o2,
								(IntVar) o3));
					}

					if (o1 instanceof Integer && o2 instanceof IntVar
							&& o3 instanceof IntVar) { // C + X = Z
						return (new XplusCeqZ((IntVar) o2, (Integer) o1,
								(IntVar) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof IntVar
							&& o3 instanceof Integer) { // X + Y = C
						return (new XplusYeqC((IntVar) o1, (IntVar) o2,
								(Integer) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof Integer
							&& o3 instanceof Integer) { // X + C = C
						return (new XeqC((IntVar) o1, new Integer ((Integer) o3 - (Integer) o2)));
					}

					if (o1 instanceof Integer && o2 instanceof IntVar
							&& o3 instanceof Integer) { // C + X = C
						return (new XeqC((IntVar) o2, new Integer ((Integer) o3 - (Integer) o1)));
					}

					if (o1 instanceof Integer && o2 instanceof Integer
							&& o3 instanceof IntVar) { // C + C = X
						return (new XeqC((IntVar) o3, new Integer ((Integer) o1 + (Integer) o2)));
					}

					System.err.println("Failed to parse eq(add(" + o1 + ", " + o2 + "), " + o3 + ")");
					return null;
				}

				if (nextToken.equals("mul")) {

					Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);
					Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);
					Object o3 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);

					if (o1 instanceof IntVar && o2 instanceof IntVar
							&& o3 instanceof IntVar) { // X * Y = Z
						return (new XmulYeqZ((IntVar) o1, (IntVar) o2,
								(IntVar) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof Integer
							&& o3 instanceof IntVar) { // X * C = Z
						return (new XmulCeqZ((IntVar) o1, (Integer) o2,
								(IntVar) o3));
					}

					if (o1 instanceof Integer && o2 instanceof IntVar
							&& o3 instanceof IntVar) { // C * X = Z
						return (new XmulCeqZ((IntVar) o2, (Integer) o1,
								(IntVar) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof IntVar
							&& o3 instanceof Integer) { // X * Y = C
						return (new XmulYeqC((IntVar) o1, (IntVar) o2,
								(Integer) o3));
					}

					if (o1 instanceof Integer && o2 instanceof Integer
							&& o3 instanceof IntVar) { // C * C = X
						return (new XeqC((IntVar) o3, new Integer ((Integer) o1 * (Integer) o2)));
					}

					System.err.println("Failed to parse eq(mul(" + o1 + ", " + o2 + "), " + o3 + ")");
					return null;
				}
				
				// o1 = o2

				// System.out.println("nextToken " + nextToken);

				Object o1 = parse(nextToken, tokenizer, store, variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				// System.out.println(o1);
				// System.out.println(o2);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1, v2 = (IntVar) o2;
					if (! v1.domain.isIntersecting(v2.domain)) 
						return "false";
					return new XeqY(v1, v2);
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (! v1.domain.contains(c2)) 
						return "false";
					return new XeqC(v1, c2);
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					IntVar v2 = (IntVar) o2;
					Integer c1 = (Integer) o1;
					if (! v2.domain.contains(c1)) 
						return "false";
					return new XeqC(v2, c1);
				} else if (o1 instanceof Integer && o2 instanceof Integer) 
					return Boolean.toString(o1.equals(o2));
				
				System.err.println("Failed to parse eq(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("ne")) {

				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar)
					return new XneqY((IntVar) o1, (IntVar) o2);
				else if (o1 instanceof IntVar && o2 instanceof Integer)
					return new XneqC((IntVar) o1, (Integer) o2);
				else if (o1 instanceof Integer && o2 instanceof IntVar)
					return new XneqC((IntVar) o2, (Integer) o1);
				else if (o1 instanceof Integer && o2 instanceof Integer) 
					return Boolean.toString(! o1.equals(o2));

				System.err.println("Failed to parse ne(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("ge")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1, v2 = (IntVar) o2;
					if (v1.min() >= v2.max()) 
						return "true";
					else if (v1.max() < v2.min()) 
						return "false";
					return new XgteqY(v1, v2);
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (v1.min() >= c2) 
						return "true";
					else if (v1.max() < c2) 
						return "false";
					return new XgteqC(v1, c2);
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					Integer c1 = (Integer) o1;
					IntVar v2 = (IntVar) o2;
					if (c1 >= v2.max()) 
						return "true";
					else if (c1 < v2.min()) 
						return "false";
					return new XlteqC(v2, c1);
				}

				System.err.println("Failed to parse ge(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("gt")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1, v2 = (IntVar) o2;
					if (v1.min() > v2.max()) 
						return "true";
					else if (v1.max() <= v2.min()) 
						return "false";
					return new XgtY(v1, v2);
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (v1.min() > c2) 
						return "true";
					else if (v1.max() <= c2) 
						return "false";
					return new XgtC(v1, c2);
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					IntVar v2 = (IntVar) o2;
					Integer c1 = (Integer) o1;
					if (c1 > v2.max()) 
						return "true";
					else if (c1 <= v2.min()) 
						return "false";
					return new XltC(v2, c1);
				}

				System.err.println("Failed to parse gt(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("le")) {

				String nextToken = tokenizer.nextToken();

				if (nextToken.equals("add")) { // o1 + o2 <= o3

					Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);
					Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);
					Object o3 = parse(tokenizer.nextToken(), tokenizer, store,
							variableMaping);

					if (o1 instanceof IntVar && o2 instanceof IntVar
							&& o3 instanceof IntVar) {
						return (new XplusYlteqZ((IntVar) o1, (IntVar) o2,
								(IntVar) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof Integer
							&& o3 instanceof IntVar) {
						return (new XplusClteqZ((IntVar) o1, (Integer) o2,
								(IntVar) o3));
					}

					if (o1 instanceof Integer && o2 instanceof IntVar
							&& o3 instanceof IntVar) {
						return (new XplusClteqZ((IntVar) o2, (Integer) o1,
								(IntVar) o3));
					}

					if (o1 instanceof IntVar && o2 instanceof IntVar
							&& o3 instanceof Integer) {

						String val = String.valueOf(o3);
						if (variableMaping.get(val) == null)
							variableMaping.put(val, new IntVar(store, (Integer) o3,
									(Integer) o3));
						o3 = variableMaping.get(val);

						return (new XplusYlteqZ((IntVar) o1, (IntVar) o2,
								(IntVar) o3));
					}

					System.err.println("Failed to parse le(add(" + o1 + ", " + o2 + "), " + o3 + ")");
					return null;
				}
				
				// o1 <= o2

				Object o1 = parse(nextToken, tokenizer, store, variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1, v2 = (IntVar) o2;
					if (v1.max() <= v2.min()) 
						return "true";
					else if (v1.min() > v2.max()) 
						return "false";
					return new XlteqY(v1, v2);
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (v1.max() <= c2) 
						return "true";
					else if (v1.min() > c2) 
						return "false";
					return new XlteqC(v1, c2);
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					IntVar v2 = (IntVar) o2;
					Integer c1= (Integer) o1;
					if (c1 <= v2.min()) 
						return "true";
					else if (c1 > v2.max()) 
						return "false";
					return new XgteqC(v2, c1);
				}

				System.err.println("Failed to parse le(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("lt")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);

				if (o1 instanceof IntVar && o2 instanceof IntVar) {
					IntVar v1 = (IntVar) o1, v2 = (IntVar) o2;
					if (v1.max() < v2.min()) 
						return "true";
					else if (v1.min() >= v2.max()) 
						return "false";
					return new XltY(v1, v2);
				} else if (o1 instanceof IntVar && o2 instanceof Integer) {
					IntVar v1 = (IntVar) o1;
					Integer c2 = (Integer) o2;
					if (v1.max() < c2) 
						return "true";
					else if (v1.min() >= c2) 
						return "false";
					return new XltC(v1, c2);
				} else if (o1 instanceof Integer && o2 instanceof IntVar) {
					IntVar v2 = (IntVar) o2;
					Integer c1= (Integer) o1;
					if (c1 < v2.min()) 
						return "true";
					else if (c1 >= v2.max()) 
						return "false";
					return new XgtC(v2, c1);
				}

				System.err.println("Failed to parse lt(" + o1 + ", " + o2 + ")");
				return null;
			}

			if (token.equals("not")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				
				if ("true".equals(o1)) 
					return "false";
				else if ("false".equals(o1)) 
					return "true";

				return new Not((PrimitiveConstraint) o1);
			}

			if (token.equals("and")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				
				if ("true".equals(o1)) {
					if ("true".equals(o2)) 
						return "true";
					else if ("false".equals(o2)) 
						return "false";
					return o2;
				} else if ("false".equals(o1)) 
					return "false";
				else if ("true".equals(o2)) 
					return o1;
				else if ("false".equals(o2)) 
					return "false";

				return new And((PrimitiveConstraint) o1,
						(PrimitiveConstraint) o2);
			}

			if (token.equals("or")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				
				if ("true".equals(o1)) 
					return "true";
				else if ("true".equals(o2)) 
					return "true";
				else if ("false".equals(o1)) {
					if ("false".equals(o2)) 
						return "false";
					return o2;
				} else if ("false".equals(o2)) 
					return o1;

				return new Or((PrimitiveConstraint) o1,
						(PrimitiveConstraint) o2);
			}

			if (token.equals("xor")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				
				if ("true".equals(o1)) {
					if ("false".equals(o2)) 
						return "true";
					else if ("true".equals(o2)) 
						return "false";
					return new Not((PrimitiveConstraint) o2);
				} else if ("false".equals(o1)) {
					if ("true".equals(o2)) 
						return "true";
					else if ("false".equals(o2)) 
						return "false";
					return o2;
				} else if ("true".equals(o2)) 
					return new Not((PrimitiveConstraint) o1);
				else if ("false".equals(o2)) 
					return o1;

				return new Eq(new Not((PrimitiveConstraint) (o1)),
						(PrimitiveConstraint) (o2));
			}
			
			if (token.equals("iff")) {
				Object o1 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				Object o2 = parse(tokenizer.nextToken(), tokenizer, store,
						variableMaping);
				
				if ("true".equals(o1)) {
					if ("true".equals(o2)) 
						return "true";
					else if ("false".equals(o2)) 
						return "false";
					return o2;
				} else if ("false".equals(o1)) {
					if ("false".equals(o2)) 
						return "true";
					else if ("true".equals(o2)) 
						return "false";
					return new Not((PrimitiveConstraint) o2);
				} else if ("true".equals(o2)) 
					return o1;
				else if ("false".equals(o2)) 
					return new Not((PrimitiveConstraint) o1);
				
				return new Eq((PrimitiveConstraint) (o1),
						(PrimitiveConstraint) (o2));
			}

			System.err.println("Unknown token: `" + token + "'");
			return null;

		}

	}


	/** @see JaCoP.constraints.DecomposedConstraint#imposeDecomposition(JaCoP.core.Store) */
	@Override
	public void imposeDecomposition(Store store) {
		
		if (decompositionConstraints == null)
			decompose(store);
		
		for (Constraint c : decompositionConstraints)
			store.impose(c);
		
		store.auxilaryVariables.addAll(auxilaryVariables);
	}

	/** @see JaCoP.constraints.DecomposedConstraint#auxiliaryVariables() */
	@Override
	public ArrayList<Var> auxiliaryVariables() { 
		return auxilaryVariables;
	}
	
}
