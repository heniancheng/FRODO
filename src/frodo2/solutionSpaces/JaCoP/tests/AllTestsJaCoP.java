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

/** The hypercube tests */
package frodo2.solutionSpaces.JaCoP.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

/** The JaCoP tests
 * @author Thomas Leaute
 */
public class AllTestsJaCoP {
	
	/** @return the test suite */
	public static Test suite() {
		TestSuite suite = new TestSuite("All tests for JaCoP");
		//$JUnit-BEGIN$
		suite.addTest(JaCopxcspParserTest.suite());
		suite.addTest(JaCoPtests.suite());
		//$JUnit-END$
		return suite;
	}
}
