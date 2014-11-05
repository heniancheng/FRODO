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

package frodo2.algorithms.varOrdering.election.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.List;

import org.jdom2.Element;

import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.varOrdering.election.SecureVarElection;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

/** JUnit test for the class SecureVarElection*/
public class SecureVarElectionTest extends VariableElectionTest<Integer> {

	/** Constructor that instantiates a test only for the input method
	 * @param useTCP 	\c true whether TCP pipes should be used instead of QueueIOPipes
	 */
	public SecureVarElectionTest(boolean useTCP) {
		super(useTCP, true, VarNameHeuristic.class, null);
	}

	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for SecureVarElectionTest");
		
		TestSuite testTmp = new TestSuite ("Tests for SecureVarElection using shared memory pipes with XML");
		testTmp.addTest(new RepeatedTest (new SecureVarElectionTest (false), 20000));
		testSuite.addTest(testTmp);

		testTmp = new TestSuite ("Tests for SecureVarElection using TCP pipes with XML");
		testTmp.addTest(new RepeatedTest (new SecureVarElectionTest (true), 1000));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** @see VariableElectionTest#initiateParamAndListener(XCSPparser, Class, Class, int) */
	@Override
	protected void initiateParamAndListener(XCSPparser<AddableInteger, AddableInteger> parser, 
			Class< ? extends ScoringHeuristic<?> > heuristic, Class< ? extends ScoringHeuristic<?> > tiebreaking, int diameter)
	throws NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		// Create a description of the parameters of VariableElection
		Element parameters = new Element ("module");
		parameters.setAttribute("minNbrLies", Integer.toString(diameter - 1));

		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];
			
			XCSPparser<AddableInteger, AddableInteger> subProb = parser.getSubProblem(agent);
			queue.setProblem(subProb);

			// Instantiate the listener using reflection
			Class<?> parTypes[] = new Class[2];
			parTypes[0] = DCOPProblemInterface.class;
			parTypes[1] = Element.class;
			Constructor<?> constructor = SecureVarElection.class.getConstructor(parTypes);
			Object[] args = new Object[2];
			args[0] = subProb;
			args[1] = parameters;
			queue.addIncomingMessagePolicy((SecureVarElection) constructor.newInstance(args));

			queue.addIncomingMessagePolicy(this);
		}
	}
	
	/** @see VariableElectionTest#checkOutputs(Class, Map) */
	@Override
	protected void checkOutputs (Class<?> heuristic, Map<String, Integer> allUniqueIDs) {
		
		for (List<String> component : this.graph.components){
			
			int rootNumber = 0;
			for (String node : component){				
				if (this.outputs.get(node).getFlag()) rootNumber++;					
			}			
			//Verify that there is one and only one root in every component
			assertEquals("Illegal number of roots in component", 1, rootNumber);
		}
	}
}