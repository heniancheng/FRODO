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

import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.heuristics.LeastConnectedHeuristic;
import frodo2.algorithms.heuristics.MostConnectedHeuristic;
import frodo2.algorithms.heuristics.RandScoringHeuristic;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationParallel;

/** Tests the DFSgenerationParallel module
 * @author Thomas Leaute
 */
public class DFSgenerationParallelTest extends DFSgenerationTest {
	
	/** The name of the heuristic used for root election */
	private final String rootElectionHeuristic;
	
	/**
	 * @param useTCP 					\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param rootElectionHeuristic 	the class of the ScoringHeuristic used for root election
	 * @param dfsHeuristic 				the class of the ScoringHeuristic used for DFS generation
	 */
	public DFSgenerationParallelTest (boolean useTCP, Class< ? extends ScoringHeuristic<?> > rootElectionHeuristic, 
			Class< ? extends ScoringHeuristic<?> > dfsHeuristic) {
		super (useTCP, true, dfsHeuristic);
		super.nbrOutputMessagesPerVar = 2; // 1 DFS output message + 1 DFS stats message
		this.rootElectionHeuristic = rootElectionHeuristic.getName();
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for DFSgenerationParallel");
		
		TestSuite testTmp = new TestSuite ("Tests for DFS generation using shared memory pipes with XML constructor");
		testTmp.addTest(new RepeatedTest (new DFSgenerationParallelTest (false, VarNameHeuristic.class, VarNameHeuristic.class), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes with XML constructor");
		testTmp.addTest(new RepeatedTest (new DFSgenerationParallelTest (true, VarNameHeuristic.class, VarNameHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes with XML constructor and Most Connected DFS heuristic");
		testTmp.addTest(new RepeatedTest (new DFSgenerationParallelTest (true, VarNameHeuristic.class, MostConnectedHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using TCP pipes with XML constructor and Least Connected DFS heuristic");
		testTmp.addTest(new RepeatedTest (new DFSgenerationParallelTest (true, VarNameHeuristic.class, LeastConnectedHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFS generation using shared memory pipes with XML constructor and random root election heuristic");
		testTmp.addTest(new RepeatedTest (new DFSgenerationParallelTest (false, RandScoringHeuristic.class, VarNameHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** @see DFSgenerationTest#getDFSclass() */
	@Override
	protected Class<?> getDFSclass(){
		return DFSgenerationParallel.class;
	}	
	
	/** Not sending any LEoutput because they are computed by the module
	 * @see DFSgenerationTest#sendLEoutputs(int, java.util.Map) 
	 */
	@Override
	protected void sendLEoutputs(int i, Map<String, String> rootForVar) { }
	
	/** @see DFSgenerationTest#createDFSparams(org.jdom2.Element) */
	@Override
	protected Element createDFSparams(Element heuristicParams) {
		
		Element parameters = new Element ("module");
		
		Element rootElection = new Element ("rootElectionHeuristic");
		parameters.addContent(rootElection);
		rootElection.setAttribute("className", this.rootElectionHeuristic);
		
		Element dfsGeneration = new Element ("dfsGeneration");
		parameters.addContent(dfsGeneration);
		dfsGeneration.setAttribute("className", DFSgeneration.class.getName());
		dfsGeneration.addContent(heuristicParams);
		
		return parameters;
	}
	
}
