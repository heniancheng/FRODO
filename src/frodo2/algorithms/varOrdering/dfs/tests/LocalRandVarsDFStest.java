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

package frodo2.algorithms.varOrdering.dfs.tests;

import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationParallel;
import frodo2.algorithms.varOrdering.dfs.LocalRandVarsDFS;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Test case for LocalRandVarsDFS
 * @author Thomas Leaute
 */
public class LocalRandVarsDFStest extends TestCase {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 15;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param method 	name of the method
	 */
	public LocalRandVarsDFStest (String method) {
		super (method);
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for LocalRandVarsDFS");
		
		suite.addTest(new RepeatedTest (new LocalRandVarsDFStest ("test"), 100));
		
		return suite;
	}
	
	/** Checks that E[DPOP] with LocalRandVarsDFS always terminates 
	 * @throws Exception 	if an error occurs while creating the agent description file 
	 * @todo Test that the heuristic is properly implemented. 
	 */
	public void test () throws Exception {
		
		Document problem = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, maxNbrVars, false);
		
		Document agentDesc = XCSPparser.parse(AgentFactory.class.getResourceAsStream("/frodo2/algorithms/dpop/stochastic/E-DPOP.xml"), false);
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").equals(DFSgenerationParallel.class.getName())) {
				module.getChild("rootElectionHeuristic").getChild("heuristic1").setAttribute("className", LocalRandVarsDFS.VarElectionHeuristic.class.getName());
				module.getChild("dfsGeneration").setAttribute("className", LocalRandVarsDFS.class.getName());
			}
		}

		new DPOPsolver<AddableInteger, AddableReal> (agentDesc).solve(problem);

	}
	
}
