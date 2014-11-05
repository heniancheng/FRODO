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

package frodo2.controller;

import junit.framework.Test;
import junit.framework.TestSuite;

/** JUnit test suite for all unit tests in this package
 * @author Thomas Leaute
 *
 */
public class AllTests {

	/**
	 * @return The suite of unit tests
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite(
				"Test for frodo2.controller");
		//$JUnit-BEGIN$
		suite.addTestSuite(TestWhitePages.class);
		suite.addTestSuite(TestConfigurationManager.class);
		suite.addTest(TestController.suite());
		//$JUnit-END$
		return suite;
	}

}
