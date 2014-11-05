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

/** All solution spaces tests */
package frodo2.solutionSpaces.tests;

import frodo2.solutionSpaces.JaCoP.tests.AllTestsJaCoP;
import frodo2.solutionSpaces.crypto.ElGamalSchemeTest;
import frodo2.solutionSpaces.hypercube.tests.AllTestsHypercube;
import frodo2.solutionSpaces.vehiclerouting.test.VRPtests;
import junit.framework.Test;
import junit.framework.TestSuite;

/** All solution spaces tests
 * @author Thomas Leaute
 */
public class AllTestsSpaces {
	
	/** @return the test suite */
	public static Test suite() {
		TestSuite suite = new TestSuite("All tests for the solution spaces");
		//$JUnit-BEGIN$
		suite.addTest(ElGamalSchemeTest.suite());
		suite.addTest(AllTestsHypercube.suite());
		suite.addTest(AllTestsJaCoP.suite());
		suite.addTest(VRPtests.suite());
		//$JUnit-END$
		return suite;
	}
}
