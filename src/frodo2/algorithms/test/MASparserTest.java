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

package frodo2.algorithms.test;

import java.io.IOException;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;

import frodo2.algorithms.MASparser;
import frodo2.solutionSpaces.AddableInteger;

/**
 * @author Brammert Ottens, 9 jun 2010
 */
public class MASparserTest extends TestCase {

	/** Constructor
	 * @param name 	the name of the test method
	 */
	public MASparserTest(String name) {
		super(name);
	}

	/** @return the test suite for this test */
	public static TestSuite suite () {
	
		TestSuite suite = new TestSuite ("JUnit tests for MASparser");
		
		suite.addTest(new MASparserTest ("testParseString"));
		suite.addTest(new MASparserTest ("testGetSubProblemString"));
		
		return suite;
	}
	
	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/** 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for MASparser#parse(java.io.File).
	 * 
	 * It simply reads in an xml file that links to two other xml files
	 */
	public void testParseString() {
		try {
			Document parsedDescription = MASparser.parse("src/frodo2/algorithms/test/testXInclude.xml");
			Element root = parsedDescription.getRootElement();
			List<Element> children = root.getChildren();
			int i = 1;
			for(Element child : children) {
				assertTrue(child.getName().equals("include"));
				assertTrue(child.getAttributeValue("name").equals(Integer.toString(i)));
				i++;
			}
		} catch (JDOMException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Test method for MASparser#parse(java.io.InputStream).
	 */
	public void testParseInputStream() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for MASparser#getSubProblem(java.lang.String).
	 * @author Brammert Ottens, Thomas Leaute
	 */
	public void testGetSubProblemString() {
		try {
			SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
			Document agentDescription = builder.build("src/frodo2/algorithms/test/agentDescription.xml");
			MASparser<AddableInteger, AddableInteger> parser = new MASparser<AddableInteger, AddableInteger> (agentDescription);
			MASparser<AddableInteger, AddableInteger> subProblem1 = parser.getSubProblem("agent1");
			MASparser<AddableInteger, AddableInteger> subProblem2 = parser.getSubProblem("agent2");
			MASparser<AddableInteger, AddableInteger> subProblem3 = parser.getSubProblem("agent3");
			
			assertTrue(subProblem1.getAgent().equals("agent1"));
			assertTrue(subProblem1.getType().equals("1"));
			assertTrue(subProblem2.getAgent().equals("agent2"));
			assertTrue(subProblem2.getType().equals("2"));
			assertTrue(subProblem3.getAgent().equals("agent3"));
			assertTrue(subProblem3.getType().equals("3"));
		} catch (JDOMException e) {
			// @todo Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// @todo Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
