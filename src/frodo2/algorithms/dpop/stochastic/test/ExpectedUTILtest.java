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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.dpop.UTILmsg;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.UTILpropagation.SolutionMessage;
import frodo2.algorithms.dpop.stochastic.ExpectedUTIL;
import frodo2.algorithms.dpop.stochastic.SamplingPhase;
import frodo2.algorithms.dpop.stochastic.ExpectedUTIL.Method;
import frodo2.algorithms.dpop.stochastic.SamplingPhase.RandVarsProjMsg;
import frodo2.algorithms.dpop.test.UTILpropagationTest;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** JUnit tests for ExpectedUTIL
 * @author Thomas Leaute
 */
public class ExpectedUTILtest extends UTILpropagationTest<AddableReal> {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	protected final int maxNbrVars = 5;
	
	/** Maximum number of edges in the random graph */
	protected final int maxNbrEdges = 10;

	/** Maximum number of agents */
	protected final int maxNbrAgents = 5;
	
	/** The number of samples */
	private int nbrSamples;

	/** The method to use */
	private Method method;

	/** @return the test suite for this test */
	static public TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for ExpectedUTIL");
		
		/// @todo Add tests for Method.ROBUST
				
		TestSuite testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the consensus method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.CONSENSUS, 0), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the consensus method with low sampling");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.CONSENSUS, 2), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using TCP pipes and real utilities using the consensus method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (true, true, Method.CONSENSUS, 0), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the advanced consensus method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.CONSENSUS_ALL_SOLS, 0), 2000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the advanced consensus method with low sampling");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.CONSENSUS_ALL_SOLS, 2), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using TCP pipes and real utilities using the advanced consensus method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (true, true, Method.CONSENSUS_ALL_SOLS, 0), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the expectation method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.EXPECTATION, 0), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the expectation method with low sampling");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.EXPECTATION, 2), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using TCP pipes and real utilities using the expectation method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (true, true, Method.EXPECTATION, 0), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using shared memory pipes and real utilities using the expectationMonotone method with low sampling");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (false, true, Method.EXPECTATION_MONOTONE, 2), 200));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for ExpectedUTIL with XML support using TCP pipes and real utilities using the expectationMonotone method");
		testTmp.addTest(new RepeatedTest (new ExpectedUTILtest (true, true, Method.EXPECTATION_MONOTONE, 0), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}

	/** Constructor 
	 * @param useTCP 		whether to use TCP pipes or shared memory pipes
	 * @param useXML 		whether to use the XML-based constructor
	 * @param method 		the method to use 
	 * @param nbrSamples 	the number of samples
	 */
	public ExpectedUTILtest(boolean useTCP, boolean useXML, Method method, int nbrSamples) {
		super(useTCP, useXML, AddableReal.class, false);
		this.nbrSamples = nbrSamples;
		this.method = method;
		if (method == Method.EXPECTATION_MONOTONE) 
			super.sign = (this.maximize ? -1 : +1);
	}

	/** @see UTILpropagationTest#setUp() */
	protected void setUp () {
		super.setUp();
		
		graph = RandGraphFactory.getNiceRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		
		if (method == Method.CONSENSUS) 
			super.parameters.setAttribute("method", "consensus");
		else if (method == Method.CONSENSUS_ALL_SOLS) 
			super.parameters.setAttribute("method", "consensusAllSols");
		else if (method == Method.EXPECTATION) 
			super.parameters.setAttribute("method", "expectation");
		else if (method == Method.EXPECTATION_MONOTONE) 
			super.parameters.setAttribute("method", "expectationMonotone");
		/// @todo Add support for Method.ROBUST
	}
	
	/** Creates a new Listener
	 * @param useTCP 				\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param useXML 				whether we should use the constructor that takes in XML elements or the manual constructor
	 * @param parameters 			the parameters for the module under test
	 * @return 						the new listener
	 * @throws Exception 			if an error occurs
	 */
	@Override 
	protected Listener newListenerInstance(boolean useTCP, boolean useXML, Element parameters) throws Exception {
		return new Listener (useTCP, parameters);
	}
	
	/** The listener that checks the messages sent by the ExpectedUTIL listeners */
	protected class Listener extends UTILpropagationTest<AddableReal>.Listener {
		
		/** For each variable, the utility space it sent to its parent */
		private HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > sentUtils = new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
		
		/** For each variable, the join of all received utility spaces */
		private HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > joins = new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
		
		/** For arrays of variables, their chosen conditional optimal assignments */
		private HashMap< String[], BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > > condOptAssignments = 
			new HashMap< String[], BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > > ();
		
		/** For each decision variable, the random variables it is responsible for projecting out */
		private HashMap< String, HashSet<String> > whereToProject = new HashMap< String, HashSet<String> > ();
		
		/** Constructor that tests ExpectedUTIL on a random DFS
		 * @param useTCP 						\c true whether TCP pipes should be used instead of QueueIOPipes
		 * @param parameters 					the parameters for the module under test
		 * @throws Exception 	if an error occurs
		 */
		public Listener (boolean useTCP, Element parameters) throws Exception {
			super (useTCP, true, parameters, ExpectedUTIL.class, true);
			
			// Wait for one additional SolutionMessage per variable
			nbrMsgsRemaining += graph.nodes.size();
		}
		
		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#setUpPrelimModules(Queue, DCOPProblemInterface) */
		@Override 
		protected void setUpPrelimModules(Queue queue, DCOPProblemInterface<AddableInteger, AddableReal> subProblem) {
			
			// Set up the SamplingPhase module
			Element params = new Element ("module");
			params.setAttribute("nbrSamples", Integer.toString(nbrSamples));
			queue.addIncomingMessagePolicy(new SamplingPhase.AtLCAs<AddableInteger, AddableReal> (subProblem, params));
		}

		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#checkOutput() */
		@Override
		protected void checkOutput() {
			
			/// @todo Check output also when sampling
			if (nbrSamples != 0) 
				return;
			
			// For each variable, look up the constraints it is responsible for enforcing, and join them with its received UTIL messages
			for (Map.Entry< String, UtilitySolutionSpace<AddableInteger, AddableReal> > entry : super.hypercubes.entrySet()) {
				String var = entry.getKey();
				UtilitySolutionSpace<AddableInteger, AddableReal> hypercube = entry.getValue();
				
				if (hypercube == null) 
					continue;
				
				UtilitySolutionSpace<AddableInteger, AddableReal> join = this.joins.get(var);
				if (join == null) {
					this.joins.put(var, hypercube);
				} else 
					this.joins.put(var, join.join(hypercube));
			}
			
			// Look up the probability space of each random variable
			HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > probSpaces = new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
			for (UtilitySolutionSpace<AddableInteger, AddableReal> probSpace : super.parser.getProbabilitySpaces()) 
				probSpaces.put(probSpace.getVariable(0), probSpace);
						

			// Check that, for each variable, the UTIL message output by the module is consistent with the output optimal assignments
			for (Map.Entry< String, UtilitySolutionSpace<AddableInteger, AddableReal> > entry : this.joins.entrySet()) {
				String var = entry.getKey();
				UtilitySolutionSpace<AddableInteger, AddableReal> space = entry.getValue();
				
				// Compose the space with the optimal assignments chosen for the variable
				for (Map.Entry< String[], BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > > entry2 : this.condOptAssignments.entrySet()) {
					String[] vars = entry2.getKey();
					if (vars[0].equals(var)) {
						space = space.compose(vars, entry2.getValue());
						break;
					}
				}
				
				// Project out the random variables that this variable is responsible for projecting out
				assert this.whereToProject.containsKey(var) : 
					var + " not in " + this.whereToProject; /// @bug Sometimes fails
				for (String randVar : this.whereToProject.get(var)) {
					Map< String, UtilitySolutionSpace<AddableInteger, AddableReal> > distributions = 
						new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
					distributions.put(randVar, (UtilitySolutionSpace<AddableInteger, AddableReal>) probSpaces.get(randVar));
					space = space.expectation(distributions).resolve();
				}
				
				UtilitySolutionSpace<AddableInteger, AddableReal> sentUtil = this.sentUtils.get(var);
				
				// Check equality, with some allowed error margin
				if (sentUtil != null) {
					
					// Create iterators
					Iterator<AddableInteger, AddableReal> iter1 = sentUtil.iterator();
					Iterator<AddableInteger, AddableReal> iter2 = space.iterator(sentUtil.getVariables(), sentUtil.getDomains());
					
					// Iterate through all utilities
					while (iter1.hasNext()) 
						assertTrue (iter1.getCurrentUtility() + " != " + iter2.getCurrentUtility(), ((AddableReal) iter1.nextUtility()).equals((AddableReal) iter2.nextUtility(), 1e-6));
				}
			}
			
			String method = parameters.getAttributeValue("method");
			
			if (method.equals("consensusAllSols")) {
				
				// Check that the chosen optimal assignments are indeed the assignments that are most often optimal
				for (Map.Entry< String[], BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > > entry : this.condOptAssignments.entrySet()) {
					String var = entry.getKey()[0];
					BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > condOptSol = entry.getValue();
					
					// Look up the space out of which the current variable is projected out
					UtilitySolutionSpace<AddableInteger, AddableReal> join = this.joins.get(var);
					
					// Skip unconstrained variables
					if (join == null) 
						continue;
					
					// Compute the true ProjOutput for all scenarios
					ProjOutput<AddableInteger, AddableReal> projOutput = join.project(var, maximize);
					
					// Compute the joint probability of each case
					UtilitySolutionSpace<AddableInteger, AddableReal> probs = new ScalarHypercube<AddableInteger, AddableReal> ((AddableReal) new AddableReal(1), null, new AddableInteger [0].getClass());
					for (String randVar : projOutput.assignments.getVariables()) 
						if (super.parser.isRandom(randVar)) 
							probs = probs.multiply(probSpaces.get(randVar));
					
					// Reorder the ProjOutput: 1) separator variables in same order as in condOptSol, 2) random variables in same order as in probs
					String[] newOrder = new String [projOutput.assignments.getNumberOfVariables()];
					int i = 0;
					for (String myVar : condOptSol.getVariables())  
						newOrder[i++] = myVar;
					for (String randVar : probs.getVariables())  
						newOrder[i++] = randVar;
					projOutput.assignments = projOutput.assignments.changeVariablesOrder(newOrder);
					projOutput.space = projOutput.space.changeVariablesOrder(newOrder);
					
					// Reorder the join: 1) variables in the ProjOutput, 2) variable projected out
					String[] newOrder2 = new String [newOrder.length + 1];
					System.arraycopy(newOrder, 0, newOrder2, 0, newOrder.length);
					newOrder2[newOrder.length] = var;
					join = join.changeVariablesOrder(newOrder2);
					
					// Look up the domain of the projected out variable
					AddableInteger[] dom = join.getDomain(var);
					HashMap<AddableInteger, Integer> valIndex = new HashMap<AddableInteger, Integer> ();
					for (i = 0; i < dom.length; i++) 
						valIndex.put(dom[i], i);
					
					// Go through all possible assignments to the separator variables
					long trueCondOptUtilSize = projOutput.assignments.getNumberOfSolutions();
					long nbrScenarios = probs.getNumberOfSolutions();
					for (long i1 = 0, i2 = 0; i1 < trueCondOptUtilSize; i2++) { // i1 is the index in ProjOutput, i2 the index in condOptSol
						
						// Look up the chosen assignment 
						AddableInteger chosen = condOptSol.getUtility(i2).get(0);
						
						// Go through all possible scenarios, and count the number of (weighted) times each assignment is optimal
						HashMap<AddableInteger, AddableReal> counts = new HashMap<AddableInteger, AddableReal > ();
						counts.put(chosen, new AddableReal(0.0));
						for (long j = 0; j < nbrScenarios; j++, i1++) {
							
							// Get the probability for that scenario
							AddableReal prob = probs.getUtility(j);
							
							// Get the optimal assignment, and increment its count by prob
							AddableInteger assignment = projOutput.assignments.getUtility(i1).get(0);
							AddableReal count = counts.get(assignment);
							if (count == null) {
								counts.put(assignment, prob);
							} else 
								counts.put(assignment, count.add(prob));
							
							// If the chosen assignment is not the optimal one, but it is still optimal for that scenario, also increment its count by prob
							if (! assignment.equals(chosen) && projOutput.space.getUtility(i1).equals(join.getUtility(i1 * dom.length + valIndex.get(chosen)))) 
								counts.put(chosen, counts.get(chosen).add(prob));
						}
						
						// Check that no solution has a count higher that the count of the chosen solution
						AddableReal optCount = counts.get(chosen);
						assertTrue (optCount != null);
						for (AddableReal count : counts.values()) 
							assertTrue (optCount + " < " + count, optCount.compareTo(count) >= 0); /// @bug Rarely fails
					}
				}
				
			} else if (method.equals("expectation") || method.equals("expectationMonotone")) {
				
				// Check that the chosen conditional optimal assignments are indeed the ones that optimize the expected utility
				for (Map.Entry< String[], BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > > entry : this.condOptAssignments.entrySet()) {
					String var = entry.getKey()[0];
					BasicUtilitySolutionSpace < AddableInteger, ArrayList <AddableInteger> > condOptSol = entry.getValue();
					
					// Look up the space out of which the current variable is projected out
					UtilitySolutionSpace<AddableInteger, AddableReal> join = this.joins.get(var);
					
					// Skip unconstrained variables
					if (join == null) 
						continue;
					
					// Compute the expectation over each random variable in the join
					for (String randVar : join.getVariables()) {
						if (parser.isRandom(randVar)) {
							Map< String, UtilitySolutionSpace<AddableInteger, AddableReal> > distributions = 
								new HashMap< String, UtilitySolutionSpace<AddableInteger, AddableReal> > ();
							distributions.put(randVar, (UtilitySolutionSpace<AddableInteger, AddableReal>) parser.getProbabilitySpaces(randVar).get(0));
							join = join.expectation(distributions).resolve();
						}
					}
					
					// Project out the variable
					UtilitySolutionSpace<AddableInteger, AddableReal> bestExpect = join.blindProject(var, maximize);
					
					// Check that the chosen conditional optimal assignments are correct
					assertTrue (bestExpect + " != " + join.compose(new String[] { var }, condOptSol), 
							bestExpect.equivalent(join.compose(new String[] { var }, condOptSol)));
				}
				
			} else if (! parameters.getAttributeValue("method").equals("consensus")) 
				fail ("Unknown method");
			
		}
		
		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#getMsgTypes() */
		@Override
		public Collection<String> getMsgTypes() {
			Collection<String> types = super.getMsgTypes();
			types.add(UTILpropagation.UTIL_MSG_TYPE);
			types.add(UTILpropagation.OUTPUT_MSG_TYPE);
			types.add(SamplingPhase.RAND_VARS_PROJ_MSG_TYPE);
			return types;
		}
		
		/** @see frodo2.algorithms.dpop.test.UTILpropagationTest.Listener#notifyIn(Message) */
		@SuppressWarnings("unchecked")
		@Override
		public void notifyIn(Message msg) {
			
			String msgType = msg.getType();
			
			if (msgType.equals(UTILpropagation.UTIL_MSG_TYPE)) {
				
				UTILmsg<AddableInteger, AddableReal> msgCast = (UTILmsg<AddableInteger, AddableReal>) msg;
				String sender = msgCast.getSender();
				String dest = msgCast.getDestination();
				UtilitySolutionSpace<AddableInteger, AddableReal> space = msgCast.getSpace();
				
				if (this.sentUtils.containsKey(sender)) // this UTIL message has already been received (this is an artifact of the busy wait)
					return;
				
				// Record the space
				this.sentUtils.put(sender, space);
				
				// Compute the join of all spaces received by the destination variable
				UtilitySolutionSpace<AddableInteger, AddableReal> join = this.joins.get(dest);
				if (join == null) {
					this.joins.put(dest, space);
				} else {
					this.joins.put(dest, join.join(space));
				}
				
				// If sampling is enabled, check that all random variables in the space have sampled domains
				if (nbrSamples != 0) 
					for (String var : space.getVariables()) 
						if (parser.isRandom(var)) 
							assertEquals ("UTIL message contains a random variable with a non-sampled domain: " + msg + "\ndomain size", 2, space.getDomain(var).length);
				
				return;
			}
			
			if (msgType.equals(SamplingPhase.RAND_VARS_PROJ_MSG_TYPE)) {
				
				RandVarsProjMsg msgCast = (RandVarsProjMsg) msg;
				this.whereToProject.put(msgCast.getVariable(), msgCast.getRandVars());
				
				return;
			}
			
			if (msgType.equals(UTILpropagation.OUTPUT_MSG_TYPE)) { // optimal assignment to a variable
				
				SolutionMessage<AddableInteger> msgCast = (SolutionMessage<AddableInteger>) msg;
				this.condOptAssignments.put(msgCast.getVariables(), msgCast.getCondOptAssignments());
			}
			
			this.finished_lock.lock();
			if (--nbrMsgsRemaining <= 0) 
				this.finished.signal();
			this.finished_lock.unlock();
		}
	}
}
