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

/** Tests for P-DPOP and P2-DPOP */
package frodo2.algorithms.dpop.privacy.test;

import frodo2.solutionSpaces.crypto.ElGamalSchemeTest;
import junit.framework.Test;
import junit.framework.TestSuite;

/** JUnit test suite for all the tests in frodo2.algorithms.dpop.privacy.test
 * @author Thomas Leaute
 */
public class AllTestsP_DPOP {

	/** @return The suite of unit tests */
	public static Test suite() {
		TestSuite suite = new TestSuite("All tests in frodo2.algorithms.dpop.privacy.test");
		//$JUnit-BEGIN$
		
		// P-DPOP
		suite.addTest(VariableObfuscationTest.suite());
		suite.addTest(P_DPOPagentTest.suite());
		
		// P2-DPOP
		suite.addTest(SecureCircularRoutingTest.suite());
		suite.addTest(SecureRerootingTest.suite());
		suite.addTest(FakeCryptoSchemeTest.suite());
		suite.addTest(ElGamalSchemeTest.suite());
		suite.addTest(P2_DPOPagentTest.suite());
		
		//$JUnit-END$
		return suite;
	}
}
