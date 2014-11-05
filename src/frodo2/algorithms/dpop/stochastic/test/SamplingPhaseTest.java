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

package frodo2.algorithms.dpop.stochastic.test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.stochastic.SamplesMsg2;
import frodo2.algorithms.dpop.stochastic.SamplingPhase;
import frodo2.algorithms.dpop.stochastic.SamplingPhase.RandVarsProjMsg;
import frodo2.algorithms.dpop.test.UTILpropagationTest;
import frodo2.algorithms.test.AllTests;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;

/** JUnit tests for SamplingPhase
 * @author Thomas Leaute
 */
public class SamplingPhaseTest extends LowestCommonAncestorsTest {

	/** Maximum number of random variables in the random graph */
	private final int maxNbrRandVars = 10;
	
	/** The random problem */
	private Document problem;
	
	/** The parser for the overall problem */
	private XCSPparser<AddableInteger, AddableReal> parser;
	
	/** Each agent's subproblem */
	private HashMap< String, XCSPparser<AddableInteger, AddableReal> > subProblems;

	/** For each variable, the random variables 1) that one of its ancestors must project out and 2) that one of its descendants is a neighbor of */
	private HashMap< String, Collection<String> > allHigherRelevantRandVars;

	/** The inner class under test */
	private Class< ? extends IncomingMsgPolicyInterface<String> > versionClass;
	
	/** Where random variables should be projected out (leaves, lcas, or roots) */
	private String whereToProj;

	/** Constructor 
	 * @param versionClass 	the inner class under test 
	 * @param whereToProj 	where random variables should be projected out (leaves, lcas, or roots)
	 */
	public SamplingPhaseTest (Class< ? extends IncomingMsgPolicyInterface<String> > versionClass, String whereToProj) {
		super ("test");
		this.versionClass = versionClass;
		this.whereToProj = whereToProj;
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Random tests for SamplingPhase");
		
		TestSuite tmp = new TestSuite ("Random tests for SamplingPhase.AtLCAs with projection at lcas");
		tmp.addTest(new RepeatedTest (new SamplingPhaseTest (SamplingPhase.AtLCAs.class, "lcas"), 500));
		testSuite.addTest(tmp);
		
		tmp = new TestSuite ("Random tests for SamplingPhase.AtLCAs with projection at leaves");
		tmp.addTest(new RepeatedTest (new SamplingPhaseTest (SamplingPhase.AtLCAs.class, "leaves"), 100));
		testSuite.addTest(tmp);
		
		tmp = new TestSuite ("Random tests for SamplingPhase.AtRoots");
		tmp.addTest(new RepeatedTest (new SamplingPhaseTest (SamplingPhase.AtRoots.class, "roots"), 500));
		testSuite.addTest(tmp);
		
		tmp = new TestSuite ("Random tests for SamplingPhase.AtLeaves");
		tmp.addTest(new RepeatedTest (new SamplingPhaseTest (SamplingPhase.AtLeaves.class, "leaves"), 100));
		testSuite.addTest(tmp);
		
		return testSuite;
	}
	
	/** @see LowestCommonAncestorsTest#setUp() */
	@Override 
	protected void setUp () {
		super.setUp();
		
		subProblems = new HashMap< String, XCSPparser<AddableInteger, AddableReal> > ();
		allHigherRelevantRandVars = new HashMap< String, Collection<String> > ();
		this.problem = AllTests.generateProblem(this.graph, maxNbrRandVars, false);
		
		// Recompute allFlags. 
		super.allFlags.clear();
		XCSPparser<AddableInteger, AddableReal> parser2 = new XCSPparser<AddableInteger, AddableReal> (this.problem);
		this.dfs = UTILpropagationTest.computeDFS(graph, parser2, true);
		for (String var : parser2.getVariables()) 
			allFlags.put(var, new HashSet<String> ());
		for (String randVar : parser2.getVariables(null)) 
			for (String var : parser2.getNeighborVars(randVar)) 
				super.allFlags.get(var).add(randVar);
		
		// Initialize allHigherRelevantRandVars
		allHigherRelevantRandVars = new HashMap< String, Collection<String> > ();
		for (String var : this.graph.nodes) 
			this.allHigherRelevantRandVars.put(var, new HashSet<String> ());
	}
	
	/** @see LowestCommonAncestorsTest#tearDown() */
	@Override 
	protected void tearDown () throws Exception {
		super.tearDown();

		this.allHigherRelevantRandVars = null;
		this.problem = null;
		this.parser = null;
		this.subProblems = null;
	}
	
	/** @see LowestCommonAncestorsTest#checkOutput() */
	@SuppressWarnings("unchecked")
	@Override 
	protected void checkOutput() {

		// Check that the RandVarsProjMsg messages are correct
		if (this.whereToProj.equals("lcas")) {
			super.checkOutput();
			
		} else if (this.whereToProj.equals("roots")) {
			
			// Check that all random variables must be projected out at a root
			HashSet<String> allLCAs = new HashSet<String> ();
			for (Map.Entry< String, HashSet<String> > entry : this.lcas.entrySet()) {
				
				HashSet<String> myLCAs = entry.getValue();
				allLCAs.addAll(myLCAs);
				
				if (! myLCAs.isEmpty()) 
					assertTrue (this.dfs.get(entry.getKey()).getParent() == null);
			}
			
			// Check that we know where to project out all random variables
			for (HashSet<String> flags : this.allFlags.values()) 
				assertTrue (allLCAs.containsAll(flags));
			
		} else { // this.whereToProj.equals("leaves")
			for (Map.Entry< String, HashSet<String> > entry : this.lcas.entrySet()) 
				assertTrue (entry.getValue().containsAll(super.allFlags.get(entry.getKey())));
		}
		
		if (this.versionClass == SamplingPhase.AtLeaves.class) {
			
			// Check that each agent has sampled all its neighboring random variables
			for (DCOPProblemInterface<AddableInteger, AddableReal> subProblem : subProblems.values()) 
				for (String randVar : subProblem.getAnonymVars()) 
					assertTrue (! subProblem.getProbabilitySpaces(randVar).get(0).equivalent(parser.getProbabilitySpaces(randVar).get(0)));
			
		} else { // we are not sampling at the leaves
			
			// Check the correctness of the probability spaces, knowing that disconnected components may disagree on the samples for a given random variable
			int nbrComponents = graph.components.size();
			HashMap< String, Hypercube<AddableInteger, AddableReal> > [] probSpaces = new HashMap [nbrComponents];
			for (int i = 0; i < nbrComponents; i++) {
				probSpaces[i] = new HashMap< String, Hypercube<AddableInteger, AddableReal> > ();
			}
			
			// Go through the list of subproblems
			for (XCSPparser<AddableInteger, AddableReal> subProblem : subProblems.values()) {
				
				// Go through the list of internal variables
				for (String var : subProblem.getMyVars()) {
					
					// Compile the set of relevant random variables for var
					HashSet<String> randVars = new HashSet<String> (this.lcas.get(var));
					randVars.addAll(this.allHigherRelevantRandVars.get(var));
					
					// Check that each relevant random variable has been properly sampled
					for (String randVar : randVars) {
						
						// Check that randVar has been sampled
						List< ? extends UtilitySolutionSpace<AddableInteger, AddableReal> > myProbSpaces = subProblem.getProbabilitySpaces(randVar);
						
						assertTrue (! myProbSpaces.isEmpty());
						UtilitySolutionSpace<AddableInteger, AddableReal> probSpace = myProbSpaces.get(0);
						assertTrue (! probSpace.equivalent(parser.getProbabilitySpaces(randVar).get(0)));
						
						// Check that the sampled probability space is consistent with the other agents' in the same graph component
						HashMap< String, Hypercube<AddableInteger, AddableReal> > compProbSpaces = probSpaces[graph.componentOf.get(var)];
						Hypercube<AddableInteger, AddableReal> prevProbSpace = compProbSpaces.get(randVar);
						if (prevProbSpace == null) { // first time we get a sample probability space for this random variable
							compProbSpaces.put(randVar, prevProbSpace);
							continue;
						}
						
						assertTrue (probSpace.equivalent(prevProbSpace));						
					}
				}
			}
		}
		
	}
	
	/** @see LowestCommonAncestorsTest#setModules() */
	@Override
	protected void setModules() throws Exception {

		parser = new XCSPparser<AddableInteger, AddableReal> (this.problem);
		parser.setUtilClass(AddableReal.class);

		// Create a description of the parameters of SamplingPhase
		Element parameters = new Element ("module");
		parameters.setAttribute("nbrSamples", "5");
		parameters.setAttribute("whereToProject", this.whereToProj);

		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];
			queue.addIncomingMessagePolicy(this);

			// Create the SamplingPhase module
			Constructor< ? extends IncomingMsgPolicyInterface<String> > constructor = this.versionClass.getConstructor(DCOPProblemInterface.class, Element.class);
			XCSPparser<AddableInteger, AddableReal> subProblem = parser.getSubProblem(agent);
			queue.setProblem(subProblem);
			this.subProblems.put(agent, subProblem);
			queue.addIncomingMessagePolicy(constructor.newInstance(subProblem, parameters));
		}
		
	}
	
	/** @see LowestCommonAncestorsTest#getMsgTypes() */
	@Override 
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (2);
		types.add(SamplingPhase.PHASE2_MSG_TYPE);
		types.add(SamplingPhase.RAND_VARS_PROJ_MSG_TYPE);
		return types;
	}

	/** @see LowestCommonAncestorsTest#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	@Override 
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(SamplingPhase.PHASE2_MSG_TYPE)) {
			
			SamplesMsg2<AddableInteger> msgCast = (SamplesMsg2<AddableInteger>) msg;
			
			// Record the flags
			synchronized (this.allHigherRelevantRandVars) {
				this.allHigherRelevantRandVars.get(msgCast.getNode()).addAll(msgCast.getSamples().keySet());
			}
		}
		
		else if (type.equals(SamplingPhase.RAND_VARS_PROJ_MSG_TYPE)) { // where to project out random variables
			
			RandVarsProjMsg msgCast = (RandVarsProjMsg) msg;
			synchronized (lcas) {
				lcas.put(msgCast.getVariable(), msgCast.getRandVars());
			}
			
			// Increment the counter of the number of messages received
			this.finished_lock.lock();
			if (--this.remainingOutputs <= 0) 
				this.finished.signal();
			this.finished_lock.unlock();
		}
	}

}
