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

package frodo2.solutionSpaces.JaCoP.tests;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.jdom2.Element;

import JaCoP.core.IntVar;
import JaCoP.core.Store;
import JaCoP.search.DepthFirstSearch;
import JaCoP.search.IndomainMin;
import JaCoP.search.SelectChoicePoint;
import JaCoP.search.SimpleSelect;
import JaCoP.search.SmallestDomain;

import frodo2.solutionSpaces.JaCoP.JaCoPxcspParser;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Test suite for JaCopxcspParser
 * @author Arnaud Jutzeler, Thomas Leaute
 * @todo add tests for the parseRelation() method
 * @todo add tests for functions
 */
public class JaCopxcspParserTest extends TestCase {
	
	/** The store */
	private Store store;
	
	/** All the variables created in the store */
	private IntVar[] allVars;

	/** @return the test suite for this test */
	public static TestSuite suite () {

		TestSuite suite = new TestSuite ("JUnit tests for JaCoPxcspParser");

		TestSuite predicateSuite = new TestSuite ("Tests predicates parsing");
		suite.addTest(predicateSuite);

		// Logic

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateNotParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateIffParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateAndParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateOrParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateXorParser"));

		// Relational

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateEqParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateNeParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateGeParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateGtParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateLeParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateLtParser"));

		// Arithmetic

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateNegParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateAbsParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateAddParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateSubParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateMulParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateDivParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateModParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicatePowParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateMinParser"));

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateMaxParser"));

		// Control

		predicateSuite.addTest(new JaCopxcspParserTest ("testPredicateIfParser"));

		// Composition

		predicateSuite.addTest(new JaCopxcspParserTest ("testComposedPredicateParser"));


		// Global constraints parsing

		TestSuite globalConsSuite = new TestSuite ("Tests global constraints parsing");
		suite.addTest(globalConsSuite);

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintWeightedSumEqParser"));

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintWeightedSumNeParser"));

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintWeightedSumGeParser"));

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintWeightedSumGtParser"));

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintWeightedSumLeParser"));

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintWeightedSumLtParser"));

		globalConsSuite.addTest(new JaCopxcspParserTest ("testGlobalConstraintAllDifferentParser"));

		return suite;
	}

	/** Generates a test using the specified method
	 * @param name 	name of the method
	 */
	public JaCopxcspParserTest(String name) {
		super(name);
		this.store = null;
		this.allVars = null;
	}

	/** @see junit.framework.TestCase#setUp() */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with not predicate */
	public void testPredicateNotParser() {
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2", "not(eq(X0, X1))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 1", "not(eq(X0, X1))");
		
		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with and predicate */
	public void testPredicateAndParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 2", "and(eq(X0, X1), eq(X2, X3))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 2 v2 1", "and(eq(X0, X1), eq(X2, X3))");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 3", "and(eq(X0, X1), eq(X2, X3))");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 3 v2 2", "and(eq(X0, X1), eq(X2, X3))");
		
		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with or predicate */
	public void testPredicateOrParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 2", "or(eq(X0, X1), eq(X2, X3))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 2 v2 1", "or(eq(X0, X1), eq(X2, X3))");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 3", "or(eq(X0, X1), eq(X2, X3))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 3 v2 2", "or(eq(X0, X1), eq(X2, X3))");
		
		assertTrue(getSolution());	
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with xor predicate */
	public void testPredicateXorParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 2", "xor(eq(X0, X1), eq(X2, X3))");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 2 v2 1", "xor(eq(X0, X1), eq(X2, X3))");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 3", "xor(eq(X0, X1), eq(X2, X3))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 3 v2 2", "xor(eq(X0, X1), eq(X2, X3))");
		
		assertTrue(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with iff predicates */
	public void testPredicateIffParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 2", "iff(eq(X0, X1), eq(X2, X3))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 2 v2 1", "iff(eq(X0, X1), eq(X2, X3))");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 1 v2 3", "iff(eq(X0, X1), eq(X2, X3))");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 3 v2 2", "iff(eq(X0, X1), eq(X2, X3))");
		
		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with eq predicate */
	public void testPredicateEqParser() {
	resetStore();
		
		createConstraint("v1 v2", "v1 v2", "eq(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v2 v1", "eq(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 -1", "eq(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 1", "eq(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "-1 v1", "eq(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "1 v1", "eq(X0, X1)");

		assertTrue(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with ne predicate */
	public void testPredicateNeParser() {
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2", "ne(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v2 v1", "ne(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 2", "ne(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 1", "ne(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "2 v1", "ne(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "1 v1", "ne(X0, X1)");

		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with ge predicate */
	public void testPredicateGeParser() {
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2", "ge(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v2 v1", "ge(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 2", "ge(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "1 v1", "ge(X0, X1)");

		assertTrue(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with gt predicate */
	public void testPredicateGtParser() {
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2", "gt(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v2 v1", "gt(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 2", "gt(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "1 v1", "gt(X0, X1)");

		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with le predicate */
	public void testPredicateLeParser() {
	resetStore();
		
		createConstraint("v1 v2", "v1 v2", "le(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v2 v1", "le(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 2", "le(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "1 v1", "le(X0, X1)");

		assertTrue(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with lt predicate */
	public void testPredicateLtParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 v2", "lt(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v2 v1", "lt(X0, X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 2", "lt(X0, X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "1 v1", "lt(X0, X1)");

		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with neg predicate */
	public void testPredicateNegParser() {

		resetStore();
		
		createConstraint("v1", "v1 -1", "eq(neg(X0), X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 1", "eq(neg(X0), X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 -1", "eq(neg(X0), X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 -1", "eq(neg(X0), X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v3", "-3 v3", "ne(neg(X0), X1)");

		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with abs predicate */
	public void testPredicateAbsParser() {
		
		resetStore();
		
		createConstraint("v1", "v1 1", "eq(abs(X0), X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 1", "eq(abs(X0), X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 -1", "eq(abs(X0), X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 -1", "eq(abs(X0), X1)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 -1", "ne(abs(X0), X1)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2", "-2 v2", "ne(abs(X0), X1)");

		assertFalse(getSolution());
	}

	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with add predicate */
	public void testPredicateAddParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 v2 3", "eq(add(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 2", "eq(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm1 vm2", "vm1 vm2 -3", "eq(add(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1 vm2", "vm1 vm2 3", "eq(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 4", "ne(add(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 3", "ne(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v3", "v1 2 v3", "ne(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v2 v3", "1 v2 v3", "ne(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 2 3", "ne(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v2", "1 v2 3", "ne(add(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v3", "1 2 v3", "ne(add(X0, X1), X2)");

		assertFalse(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with sub predicate */
	public void testPredicateSubParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 v2 -1", "eq(sub(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 1", "eq(sub(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm1 vm2", "vm1 vm2 1", "eq(sub(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1 vm2", "vm1 vm2 -3", "eq(sub(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v2", "v2 1 4", "ne(sub(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2", "v2 1 1", "ne(sub(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "2 v1 4", "ne(sub(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v4", "2 1 v4", "ne(sub(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2 v4", "v2 1 v4", "ne(sub(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v4", "2 v1 v4", "ne(sub(X0, X1), X2)");

		assertTrue(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with mul predicate */
	public void testPredicateMulParser() {

		resetStore();
		
		createConstraint("v1 v2", "v1 v2 2", "eq(mul(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 3", "eq(mul(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm1 vm2", "vm1 vm2 2", "eq(mul(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1 v2", "vm1 v2 -2", "eq(mul(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2", "v2 3 4", "ne(mul(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2", "v2 3 6", "ne(mul(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v3", "2 v3 6", "ne(mul(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v6", "2 3 v6", "ne(mul(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v2 v6", "v2 3 v6", "ne(mul(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v3 v6", "2 v3 v6", "ne(mul(X0, X1), X2)");

		assertFalse(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with div predicate */
	public void testPredicateDivParser() {

		resetStore();
		
		createConstraint("v1 v2", "v2 v1 2", "eq(div(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v3 v2", "v3 v2 1", "eq(div(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 0", "eq(div(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 v2 1", "eq(div(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm6 vm2", "vm6 vm2 3", "eq(div(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm6 v2", "vm6 v2 -3", "eq(div(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v10", "v10 5 3", "ne(div(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v11", "v11 5 2", "ne(div(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v5", "11 v5 2", "ne(div(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v2", "11 5 v2", "ne(div(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v11 v2", "v11 5 v2", "ne(div(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v5 v2", "11 v5 v2", "ne(div(X0, X1), X2)");

		assertFalse(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with mod predicate */
	public void testPredicateModParser() {

		resetStore();
		
		createConstraint("v4 v2", "v4 v2 0", "eq(mod(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v5 v2", "v5 v2 0", "eq(mod(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm2 vm1", "vm2 vm1 0", "eq(mod(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v24 v8", "v24 v8 1", "ne(mod(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v24", "v24 8 0", "ne(mod(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v8", "24 v8 1", "ne(mod(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "24 8 v1", "ne(mod(X0, X1), X2)");

		assertTrue(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with pow predicate */
	public void testPredicatePowParser() {
		
		resetStore();
		
		createConstraint("vm8", "-2 3 vm8", "eq(pow(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 10 1", "eq(pow(X0, X1), X2)"); ///@bug

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v5", "0 v5 0", "eq(pow(X0, X1), X2)"); ///@bug

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2 v3", "v2 v3 8", "eq(pow(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm2 v3", "vm2 v3 -8", "eq(pow(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm2 v3", "vm2 v3 8", "eq(pow(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm2 4", "vm2 4 16", "eq(pow(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("-2 v4", "-2 v4 -16", "eq(pow(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 vm2", "v1 vm2 1", "eq(pow(X0, X1), X2)"); ///@bug

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1 vm2", "vm1 vm2 1", "eq(pow(X0, X1), X2)"); ///@bug

		 assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2 vm2", "v2 vm2 0", "eq(pow(X0, X1), X2)");

		assertFalse(getSolution());
		
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with min predicate */
	public void testPredicateMinParser() {

		resetStore();
		
		createConstraint("v2 v4", "v2 v4 2", "eq(min(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v5 v2", "v5 v2 5", "eq(min(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm2 vm1", "vm2 vm1 -2", "eq(min(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 0 -1", "eq(min(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v24 v5", "v24 v5 24", "ne(min(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v24", "v24 17 17", "ne(min(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v17", "24 v17 17", "ne(min(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v17", "24 17 v17", "ne(min(X0, X1), X2)");

		assertFalse(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with max predicate */
	public void testPredicateMaxParser() {

		resetStore();
		
		createConstraint("v2 v4", "v2 v4 2", "eq(max(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v5 v2", "v5 v2 5", "eq(max(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("vm2 vm1", "vm2 vm1 -2", "eq(max(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("vm1", "vm1 0 1", "eq(max(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v24 v5", "v24 v5 24", "ne(max(X0, X1), X2)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v24", "v24 17 17", "ne(max(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v17", "24 v17 17", "ne(max(X0, X1), X2)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v17", "24 17 v17", "ne(max(X0, X1), X2)");

		assertTrue(getSolution());
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) with if predicate */
	public void testPredicateIfParser() {

		resetStore();
		
		createConstraint("v1 v2 v3", "v1 1 v2 v3 2", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2 v3", "v1 0 v2 v3 3", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2 v3", "v1 1 v2 v3 3", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2 v3", "v1 0 v2 v3 2", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v3", "v1 0 2 v3 2", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1 v2", "v1 0 v2 3 2", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertFalse(getSolution());
		
		resetStore();
		
		createConstraint("v1", "v1 0 2 3 2", "eq(if(eq(X0, X1), X2, X3), X4)");

		assertFalse(getSolution());
		
	}
	
	/** Test method for parsePredicate(Element constraint, Element predicate, Store store) on more complex predicates */
	public void testComposedPredicateParser() {

		resetStore();
		
		createConstraint("v4 v6", "v4 2 v9 3 1", "eq(abs(sub(div(X0,X1),div(X2,X3))),X4)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v4 v6", "v4 5 v9 3 3", "eq(abs(sub(div(X0,X1),div(X2,X3))),X4)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v4 v9", "v4 3 v9 7 1", "eq(abs(sub(mod(X0,X1),mod(X2,X3))),X4)");

		assertTrue(getSolution());
		
		resetStore();
		
		createConstraint("v2 v3 v4 v6", "v2 2 v3 3 0 v4 3 v6 5 0", "and(eq(abs(sub(div(X0,X1),div(X2,X3))),X4),eq(abs(sub(mod(X5,X6),mod(X7,X8))),X9))");

		assertTrue(getSolution());
		
	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on allDifferent constraint */
	public void testGlobalConstraintAllDifferentParser() {
		Store store = new Store();
		ArrayList<IntVar> vars = new ArrayList<IntVar>();
		IntVar v1 = new IntVar(store, "v1", 0, 0);
		vars.add(v1);
		IntVar v2 = new IntVar(store, "v2", 0, 1);
		vars.add(v2);
		IntVar v3 = new IntVar(store, "v3", 1, 2);
		vars.add(v3);

		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:allDifferent");
		Element params = new Element ("parameters");
		cons.addContent(params);
		params.setText(" [ v1 v2 v3 ] ");

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[3]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);	

		assertTrue(result);

		for(int i = 0; i < 3; i++){
			assertTrue(search.getSolution()[i].singleton());

			assertEquals(i, search.getSolution()[i].valueEnumeration().nextElement());
		}

		store = new Store();
		vars = new ArrayList<IntVar>();
		v1 = new IntVar(store, "v1", 1, 1);
		vars.add(v1);
		v2 = new IntVar(store, "v2", 1, 2);
		vars.add(v2);
		v3 = new IntVar(store, "v3", 2, 2);
		vars.add(v3);

		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:allDifferent"); 	
		params = new Element ("parameters");
		cons.addContent(params);
		params.setText(" [ v1 v2 v3 ] ");

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[3]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		result = search.labeling(store, select);	

		assertFalse(result);
		
		resetStore();
		
		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:allDifferent"); 	
		params = new Element ("parameters");
		cons.addContent(params);
		params.setText(" [ v1 v2 v3 0 ] ");

		JaCoPxcspParser.parseGlobalConstraint(cons, this.store);

		assertTrue(getSolution());	
		
		resetStore();
		
		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:allDifferent"); 	
		params = new Element ("parameters");
		cons.addContent(params);
		params.setText(" [ 1 v1 v2 v3 ] ");

		JaCoPxcspParser.parseGlobalConstraint(cons, this.store);
		
		assertFalse(getSolution());

	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on WeightedSum constraint with eq atom*/
	public void testGlobalConstraintWeightedSumEqParser() {
		
		resetStore();
		
		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:weightedSum"); 	
		Element param = new Element("parameters");
		param.setText("[ { 1 v2 } { 1 v1 } { 1 v3 } ] \n 6");
		param.addContent(new Element("eq"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		assertTrue(getSolution());
		
		resetStore();
		
		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:weightedSum"); 	
		param = new Element("parameters");
		param.setText("[ { 1 v1 } { 1 v2 } { 1 v3 } ]  \n \n 6");
		param.addContent(new Element("eq"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		assertTrue(getSolution());
		
		resetStore();
		
		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "3");
		cons.setAttribute("scope", "v1 v2 v3");
		cons.setAttribute("reference", "global:weightedSum"); 	
		param = new Element("parameters");
		param.setText("[ { 1 v2 } { 1 v1 } { 1 v3 } ] \n 5");
		param.addContent(new Element("eq"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		assertFalse(getSolution());
		
		resetStore();
		
		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "2");
		cons.setAttribute("scope", "v1 v2");
		cons.setAttribute("reference", "global:weightedSum"); 	
		param = new Element("parameters");
		param.setText("[ { 1 v2 } { 1 v1 } { 1 3 } ] \n 6");
		param.addContent(new Element("eq"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		assertTrue(getSolution());
		
		resetStore();
		
		cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "2");
		cons.setAttribute("scope", "v1 v2");
		cons.setAttribute("reference", "global:weightedSum"); 	
		param = new Element("parameters");
		param.setText("[ { 1 v2 } { 1 v1 } { 1 3 } ] \n 5");
		param.addContent(new Element("eq"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		assertFalse(getSolution());
	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on WeightedSum constraint with ne atom*/
	public void testGlobalConstraintWeightedSumNeParser() {
		Store store = new Store();
		ArrayList<IntVar> vars = new ArrayList<IntVar>();
		IntVar v1 = new IntVar(store, "v1", 1, 1);
		vars.add(v1);
		IntVar v2 = new IntVar(store, "v2", 1, 1);
		vars.add(v2);
		IntVar v3 = new IntVar(store, "v3", 1, 1);
		vars.add(v3);
		IntVar v4 = new IntVar(store, "v4", 1, 2);
		vars.add(v4);

		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v1 v2 v3 v4");
		cons.setAttribute("reference", "global:weightedSum"); 	
		Element param = new Element("parameters");
		param.setText("[ { 1 v1 } { 5 v2 } { 1 v3 } { 1 v4 } ]  8");
		param.addContent(new Element("ne"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);	

		assertTrue(result);

		for(int i = 0; i < 3; i++){
			assertTrue(search.getSolution()[i].singleton());

			assertEquals(1, search.getSolution()[i].valueEnumeration().nextElement());
		}

		assertTrue(search.getSolution()[3].singleton());

		assertEquals(2, search.getSolution()[3].valueEnumeration().nextElement());
	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on WeightedSum constraint with ge atom*/
	public void testGlobalConstraintWeightedSumGeParser() {
		Store store = new Store();
		ArrayList<IntVar> vars = new ArrayList<IntVar>();
		IntVar v1 = new IntVar(store, "v1", 3, 3);
		vars.add(v1);
		IntVar v2 = new IntVar(store, "v2", 3, 3);
		vars.add(v2);
		IntVar v3 = new IntVar(store, "v3", 3, 3);
		vars.add(v3);
		IntVar v4 = new IntVar(store, "v4", 1, 2);
		vars.add(v4);

		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v4 v3 v2 v1");
		cons.setAttribute("reference", "global:weightedSum"); 	
		Element param = new Element("parameters");
		param.setText("[ { 1 v4 } { 2 v2 } { 2 v3 } { 1 v1 } ]  17");
		param.addContent(new Element("ge"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);	

		assertTrue(result);

		for(int i = 0; i < 3; i++){
			assertTrue(search.getSolution()[i].singleton());

			assertEquals(3, search.getSolution()[i].valueEnumeration().nextElement());
		}

		assertTrue(search.getSolution()[3].singleton());

		assertEquals(2, search.getSolution()[3].valueEnumeration().nextElement());
	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on WeightedSum constraint with gt atom*/
	public void testGlobalConstraintWeightedSumGtParser() {
		Store store = new Store();
		ArrayList<IntVar> vars = new ArrayList<IntVar>();
		IntVar v1 = new IntVar(store, "v1", 3, 3);
		vars.add(v1);
		IntVar v2 = new IntVar(store, "v2", 3, 3);
		vars.add(v2);
		IntVar v3 = new IntVar(store, "v3", 3, 3);
		vars.add(v3);
		IntVar v4 = new IntVar(store, "v4", 1, 2);
		vars.add(v4);

		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v4 v3 v2 v1");
		cons.setAttribute("reference", "global:weightedSum"); 	
		Element param = new Element("parameters");
		param.setText("[ { 1 v4 } { 2 v2 } { 2 v3 } { 1 v1 } ]  17");
		param.addContent(new Element("gt"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);

		assertFalse(result);

		store = new Store();
		vars = new ArrayList<IntVar>();
		IntVar v5 = new IntVar(store, "v5", 3, 3);
		vars.add(v5);
		IntVar v6 = new IntVar(store, "v6", 3, 3);
		vars.add(v6);
		IntVar v7 = new IntVar(store, "v7", 3, 3);
		vars.add(v7);
		IntVar v8 = new IntVar(store, "v8", 1, 2);
		vars.add(v8);

		cons = new Element("constraint");
		cons.setAttribute("name", "c_2");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v6 v7 v5 v8");
		cons.setAttribute("reference", "global:weightedSum"); 	
		param = new Element("parameters");
		param.setText("[ { 2 v6 } { 2 v7 } { 1 v5 } { 1 v8 } ]  16");
		param.addContent(new Element("gt"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		result = search.labeling(store, select);

		assertTrue(result);

		for(int i = 0; i < 3; i++){
			assertTrue(search.getSolution()[i].singleton());

			assertEquals(3, search.getSolution()[i].valueEnumeration().nextElement());
		}

		assertTrue(search.getSolution()[3].singleton());

		assertEquals(2, search.getSolution()[3].valueEnumeration().nextElement());
	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on WeightedSum constraint with le atom*/
	public void testGlobalConstraintWeightedSumLeParser() {
		Store store = new Store();
		ArrayList<IntVar> vars = new ArrayList<IntVar>();
		IntVar v1 = new IntVar(store, "v1", 2, 3);
		vars.add(v1);
		IntVar v2 = new IntVar(store, "v2", 2, 3);
		vars.add(v2);
		IntVar v3 = new IntVar(store, "v3", 2, 3);
		vars.add(v3);
		IntVar v4 = new IntVar(store, "v4", 1, 1);
		vars.add(v4);

		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v4 v3 v2 v1");
		cons.setAttribute("reference", "global:weightedSum"); 	
		Element param = new Element("parameters");
		param.setText("[ { -1 v4 } { 1 v2 } { 1 v3 } { 1 v1 } ]  5");
		param.addContent(new Element("le"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);	

		assertTrue(result);

		for(int i = 0; i < 3; i++){
			assertTrue(search.getSolution()[i].singleton());

			assertEquals(2, search.getSolution()[i].valueEnumeration().nextElement());
		}

		assertTrue(search.getSolution()[3].singleton());

		assertEquals(1, search.getSolution()[3].valueEnumeration().nextElement());
	}

	/** Test method for parseGlobalConstraint(Element constraint, Store store) on WeightedSum constraint with lt atom*/
	public void testGlobalConstraintWeightedSumLtParser() {
		Store store = new Store();
		ArrayList<IntVar> vars = new ArrayList<IntVar>();
		IntVar v1 = new IntVar(store, "v1", 2, 3);
		vars.add(v1);
		IntVar v2 = new IntVar(store, "v2", 2, 3);
		vars.add(v2);
		IntVar v3 = new IntVar(store, "v3", 2, 3);
		vars.add(v3);
		IntVar v4 = new IntVar(store, "v4", 1, 1);
		vars.add(v4);

		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v4 v3 v2 v1");
		cons.setAttribute("reference", "global:weightedSum"); 	
		Element param = new Element("parameters");
		param.setText("[ { -1 v4 } { 1 v2 } { 1 v3 } { 1 v1 } ]  5");
		param.addContent(new Element("lt"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);

		assertFalse(result);

		store = new Store();
		vars = new ArrayList<IntVar>();
		IntVar v5 = new IntVar(store, "v5", 2, 3);
		vars.add(v5);
		IntVar v6 = new IntVar(store, "v6", 2, 3);
		vars.add(v6);
		IntVar v7 = new IntVar(store, "v7", 2, 3);
		vars.add(v7);
		IntVar v8 = new IntVar(store, "v8", 1, 1);
		vars.add(v8);

		cons = new Element("constraint");
		cons.setAttribute("name", "c_2");
		cons.setAttribute("arity", "4");
		cons.setAttribute("scope", "v6 v7 v5 v8");
		cons.setAttribute("reference", "global:weightedSum"); 	
		param = new Element("parameters");
		param.setText("[ { 1 v6 } { 1 v7 } { 1 v5 } { -1 v8 } ]  6");
		param.addContent(new Element("lt"));
		cons.addContent(param);

		JaCoPxcspParser.parseGlobalConstraint(cons, store);

		select = new SimpleSelect<IntVar>(vars.toArray(new IntVar[4]),
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		result = search.labeling(store, select);


		assertTrue(result);

		for(int i = 0; i < 3; i++){
			assertTrue(search.getSolution()[i].singleton());

			assertEquals(2, search.getSolution()[i].valueEnumeration().nextElement());
		}

		assertTrue(search.getSolution()[3].singleton());

		assertEquals(1, search.getSolution()[3].valueEnumeration().nextElement());
	}

	/** This method creates a contraint and a predicate in XCSP standard and pass them to the parser
	 * that will create the corresponding constraints in the store
	 * @param scope			the scope of the constraint
	 * @param parameters	the parameters of the constraint
	 * @param predicate		the functional description of the predicate
	 */
	private void createConstraint(String scope, String parameters, String predicate){
		
		Pattern pattern = Pattern.compile("\\s+");
		String[] varNames = pattern.split(scope);
		
		Element cons = new Element("constraint");
		cons.setAttribute("name", "c_1");
		cons.setAttribute("arity", String.valueOf(varNames.length));
		cons.setAttribute("scope", scope);
		cons.setAttribute("reference", "r_1"); 	
		Element param = new Element("parameters");
		param.setText(parameters);
		cons.addContent(param);
		
		pattern = Pattern.compile("\\s+");
		String[] params = pattern.split(parameters);
		StringBuilder builder = new StringBuilder("int X0");
		for(int i = 1; i < params.length; i++){
			builder.append(" int X" + i);
		}

		Element pred = new Element("predicate");
		pred.setAttribute("name", "r_1");
		param = new Element("parameters");
		param.setText(builder.toString());
		pred.addContent(param);
		Element expression = new Element("expression");
		pred.addContent(expression);
		Element functional = new Element("functional");
		functional.setText(predicate);
		expression.addContent(functional);
		
		JaCoPxcspParser.parsePredicate(cons, pred, store, new ArrayList<IntVar> ());
	}
	
	
	/** Perform a depth first search on every variables contained in allVars
	 * @return true if the problem contained in the store is sat, false otherwise
	 */
	private boolean getSolution(){
		
		if(!store.consistency())
			return false;
		
		SelectChoicePoint<IntVar> select = new SimpleSelect<IntVar>(allVars,
				new SmallestDomain<IntVar>(),
				new IndomainMin<IntVar>());

		DepthFirstSearch<IntVar> search = new DepthFirstSearch<IntVar>();
		search.getSolutionListener().recordSolutions(true);
		search.setPrintInfo(false);

		boolean result = search.labeling(store, select);
		
		return result;
	}
	
	/**
	 * Small tool that creates a new store with 50 variables each one having a singleton domain
	 * 25 variables with positive values: v0::0, v1::1, v2::2, ..., v24::24
	 * 25 variables with negative values: vm0::0, vm1::-1, vm2::-2, ..., vm24::-24
	 */
	private void resetStore(){
		this.store = new Store();
		this.allVars= new IntVar[50];
		
		for(int i = 0; i < 25; i++){
			allVars[i] = new IntVar(store, "vm"+i, i*-1, i*-1);
		}
		
		for(int i = 0; i < 25; i++){
			allVars[i+25] = new IntVar(store, "v"+i, i, i);
		}
	}
}
