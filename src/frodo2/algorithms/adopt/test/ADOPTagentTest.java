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

package frodo2.algorithms.adopt.test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.adopt.ADOPT;
import frodo2.algorithms.adopt.Preprocessing;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.reformulation.ProblemRescaler;
import frodo2.algorithms.test.AllTests;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.mailer.CentralMailer;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.controller.Controller;
import frodo2.controller.WhitePages;
import frodo2.daemon.LocalAgentReport;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit test for ADOPTagent
 * 
 * @author Thomas Leaute, Brammert Ottens
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class ADOPTagentTest < V extends Addable<V>, U extends Addable<U> > extends TestCase implements IncomingMsgPolicyInterface<String> {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 15;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;
	
	/** The queue used to listen to the agents */
	private Queue queue;
	
	/** For each agent, the output pipe to it */
	private Map<Object, QueueOutputPipeInterface> pipes;
	
	/** The testers pipe */
	private QueueIOPipe pipe;
	
	/** All agents, indexed by their IDs */
	private Map< String, AgentInterface<V> > agents;
	
	/** Total number of agents */
	private int nbrAgents;
	
	/** Used to track the number of various types of messages received from the agents */
	private int nbrMsgsReceived;
	
	/** Number of agents finished */
	private int nbrAgentsFinished;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** Whether we should count Non-Concurrent Constraint Checks */
	private boolean countNCCCs = false;

	/** The ADOPT stats gatherer listening for the solution */
	private ADOPT<V, U> statsGatherer;
	
	/** Used to check the output of the preprocessing listener*/
	private Preprocessing<V, U> preProcessingTester;
	
	/** The type of the start message */
	private String startMsgType;

	/** The description of the agent */
	private Document agentDesc;
	
	/** \c true if the algorithm must be tested with the central mailer */
	private boolean useCentralMailer;

	/** The random problem */
	private DCOPProblemInterface<V, U> problem;
	
	/** \c true when the central mailer must be tested with delays*/
	private boolean useDelay;

	/** The CentralMailer */
	private CentralMailer mailman;
	
	/** The class of variable values */
	private Class<V> domClass;
	
	/** The class of utility values */
	private Class<U> utilClass;

	/** Whether to use XCSP */
	private boolean useXCSP;
	
	/** Whether to use TCP pipes */
	private boolean useTCP;
	
	/** Whether to test on maximization problem */
	private final boolean maximize;
	
	/** The required sign for the costs/utilities */
	private final int sign;
	
	/** Creates a JUnit test case
	 * @param useCentralMailer 	\c true when the central mailer should be tested
	 * @param useDelay 			\c true when the central mailer must be tested with delays
	 * @param startMsgType 		the type of the start message
	 * @param domClass 			the class of variable values
	 * @param utilClass 		the class of utility values
	 * @param useXCSP 			whether to use XCSP
	 * @param useTCP 			whether TCP pipes should be used for communication between agents
	 * @param countNCCCs 		whether we should tell the agent to count NCCCs
	 * @param maximize 			Whether to test on maximization problem
	 * @param sign 				The required sign for the costs/utilities
	 */
	public ADOPTagentTest(boolean useCentralMailer, boolean useDelay, String startMsgType, Class<V> domClass, Class<U> utilClass, boolean useXCSP, boolean useTCP, boolean countNCCCs, boolean maximize, int sign) {
		super ("testRandom");
		this.useCentralMailer = useCentralMailer;
		this.useDelay = useDelay;
		this.startMsgType = startMsgType;
		this.domClass = domClass;
		this.utilClass = utilClass;
		this.useXCSP = useXCSP;
		this.useTCP = useTCP;
		this.countNCCCs = countNCCCs;
		this.maximize = maximize;
		this.sign = sign;
	}

	/** Sets the type of the start message for all modules
	 * @param startMsgType 		the new type for the start message
	 * @throws JDOMException 	if parsing the agent configuration file failed
	 */
	private void setStartMsgType (String startMsgType) throws JDOMException {
		if (startMsgType != null) {
			this.startMsgType = startMsgType;
			for (Element module2 : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
				Element messages = module2.getChild("messages");
				if (messages != null) {
					for (Element message : (List<Element>) messages.getChildren()) {
						if (message.getAttributeValue("name").equals("START_MSG_TYPE")) {
							message.setAttribute("value", startMsgType);
							message.removeAttribute("ownerClass");
						}
					}
				}
			}
		} else 
			this.startMsgType = AgentInterface.START_AGENT;
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for ADOPTagent");
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, true, false, false, false, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes without XCSP");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, false, false, false, false, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on minimization problems with unconstrained cost signs");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, true, false, false, false, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on minimization problems with unconstrained cost signs without XCSP");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, false, false, false, false, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on maximization problems with unconstrained utility signs");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, true, false, false, true, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes on maximization problems with unconstrained utility signs without XCSP");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, false, false, false, true, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real-valued variables");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableReal, AddableInteger> (false, false, null, AddableReal.class, AddableInteger.class, true, false, false, false, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and real utilities");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableReal> (false, false, null, AddableInteger.class, AddableReal.class, true, false, false, false, +1), 500));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests using QueueIOPipes and real utilities without XCSP");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableReal> (false, false, null, AddableInteger.class, AddableReal.class, false, false, false, false, +1), 500));
		suite.addTest(tmp);
				
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (true, false, null, AddableInteger.class, AddableInteger.class, true, false, false, false, +1), 500));
		suite.addTest(tmp);
		
		/// @bug ADOPT does not seem to handle message delays. 
//		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer with delays");
//		tmp(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (true, true, null, AddableInteger.class, AddableInteger.class, true, false, false), 500, false, +1));
//		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and NCCCs");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, true, false, true, false, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and NCCCs without XCSP");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, false, false, true, false, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and NCCCs and the central mailer");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (true, false, null, AddableInteger.class, AddableInteger.class, true, false, true, false, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, null, AddableInteger.class, AddableInteger.class, true, true, false, false, +1), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and a different type of start message");
		tmp.addTest(new RepeatedTest (new ADOPTagentTest<AddableInteger, AddableInteger> (false, false, "START NOW!", AddableInteger.class, AddableInteger.class, true, false, false, false, +1), 50));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	public void setUp () throws Exception {
		
		// Create the problem
		Document problemDoc = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, this.maximize, this.sign);
		problem = new XCSPparser<V, U> (problemDoc, countNCCCs);
		problem.setDomClass(domClass);
		problem.setUtilClass(utilClass);
		if (! this.useXCSP) {
			Problem<V, U> newProb = new Problem<V, U> (this.maximize);
			newProb.setDomClass(domClass);
			newProb.setUtilClass(utilClass);
			newProb.reset(this.problem);
			this.problem = newProb;
		}
		
		nbrMsgsReceived = 0;
		nbrAgentsFinished = 0;
		
		// Create the queue
		if (this.useCentralMailer) {
			mailman = new CentralMailer (false, this.useDelay, null);
			this.queue = mailman.newQueue(AgentInterface.STATS_MONITOR);
		} else 
			queue = new Queue (false);
		
		queue.addIncomingMessagePolicy(this);
		pipes = new HashMap<Object, QueueOutputPipeInterface> ();
		statsGatherer = new ADOPT<V, U> (null, (DCOPProblemInterface<V, U>) problem);
		statsGatherer.setSilent(true);
		statsGatherer.getStatsFromQueue(queue);
		
		preProcessingTester = new Preprocessing<V, U>(null, (DCOPProblemInterface<V, U>) null);
		preProcessingTester.setSilent(true);
		preProcessingTester.getStatsFromQueue(queue);
		
		agentDesc = XCSPparser.parse("src/frodo2/algorithms/adopt/ADOPTagent.xml", false);
		this.setStartMsgType(startMsgType);
		
		// Fix the ProblemRescaler's shift if the problem is not a minimization problem with non-negative costs
		if (this.maximize || this.sign <= 0) {
			for (Element module : (List<Element>) this.agentDesc.getRootElement().getChild("modules").getChildren()) {
				if (module.getAttributeValue("className").equals(ProblemRescaler.class.getName())) {
					module.setAttribute("shift", "1000000");
					break;
				}
			}
		}
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown () throws Exception {
		super.tearDown();
		if (this.useCentralMailer) 
			mailman.end();
		problem = null;
		queue.end();
		for (QueueOutputPipeInterface pipe : pipes.values())
			pipe.close();
		pipes.clear();
		for (AgentInterface<V> agent : agents.values()) 
			agent.kill();
		agents.clear();
		queue = null;
		pipes = null;
		statsGatherer = null;
		preProcessingTester = null;
		this.agentDesc = null;
		this.pipe = null;
		this.preProcessingTester = null;
		this.startMsgType = null;
	}
	
	/** Tests the DPOPagent on a random problem */
	public void testRandom () {
		
		// Set up the input pipe for the queue
		pipe = new QueueIOPipe (queue);
				
		// Create the agent descriptions
		String useCentralMailerString = Boolean.toString(useCentralMailer);
		agentDesc.getRootElement().setAttribute("measureTime", useCentralMailerString);
		
		// Go through the list of agents and instantiate them
		Set<String> agentNames = problem.getAgents();
		nbrAgents = agentNames.size();
		agents = new HashMap< String, AgentInterface<V> > (nbrAgents);
		synchronized (agents) {
			if (useTCP) { // use TCP pipes
				int port = 5500;
				for (String agent : agentNames) {
					ProblemInterface<V, U> agentElmt = problem.getSubProblem(agent);
					agents.put(agentElmt.getAgent(), AgentFactory.createAgent(pipe, pipe, agentElmt, agentDesc, port++));
				}
			} else { // use QueueIOPipes
				for (String agent : agentNames) {
					ProblemInterface<V, U> agentElmt = problem.getSubProblem(agent);
					agents.put(agentElmt.getAgent(), AgentFactory.createAgent(pipe, agentElmt, agentDesc, mailman));
				}
			}
		}
		
		long timeout = 240000; // in ms
		
		if (this.useCentralMailer) 
			assertTrue("Timeout", this.mailman.execute(timeout));
			
		else {
			// Wait for all agents to finish
			while (true) {
				this.finished_lock.lock();
				try {
					if (nbrAgentsFinished >= nbrAgents) {
						break;
					} else if (! this.finished.await(timeout, TimeUnit.MILLISECONDS)) {
						fail("Timeout");
					}
				} catch (InterruptedException e) {
					break;
				}
				this.finished_lock.unlock();
			}
		}
		
		U totalOptUtil = statsGatherer.getTotalOptUtil();
		U dpopOptUtil = new DPOPsolver<V, U> (this.domClass, this.utilClass).solve(problem).getUtility();
		
		if (!this.maximize && this.sign > 0) {
			// Check that all the heuristic values that have been calculated are lower bounds
			HashMap<String, UtilitySolutionSpace<V, U>> reportedHeuristics = this.preProcessingTester.getReportedHeuristics();
			assertEquals (reportedHeuristics.size(), problem.getNbrVars());
			for(UtilitySolutionSpace<V, U> h : reportedHeuristics.values()) {
				U max = h.blindProjectAll(true);
				assertTrue (max + " > " + dpopOptUtil, max.compareTo(dpopOptUtil) <= 0);
			}
		}
		
		// Check that ADOPT and DPOP agree on the total optimal utility
		assertEquals (dpopOptUtil, totalOptUtil);
		
		// Check that the optimal assignments have indeed the reported utility
		Map<String, V> optAssignments = statsGatherer.getOptAssignments();
		assertEquals (problem.getUtility(optAssignments).getUtility(0), totalOptUtil);
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (4);
		types.add(AgentInterface.LOCAL_AGENT_REPORTING);
		types.add(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST);
		types.add(AgentInterface.AGENT_CONNECTED);
		types.add(AgentInterface.AGENT_FINISHED);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		if (msg.getType().equals(AgentInterface.LOCAL_AGENT_REPORTING)) {
			LocalAgentReport msgCast = (LocalAgentReport) msg;
			String agentID = msgCast.getAgentID();
			QueueIOPipe pipe = msgCast.getLocalPipe();
			queue.addOutputPipe(agentID, pipe);
			
			// Create a TCP pipe if required
			int port = msgCast.getPort();
			if (port >= 0) {
				try {
					pipes.put(agentID, Controller.PipeFactoryInstance.outputPipe( Controller.PipeFactoryInstance.getSelfAddress(port)));
				} catch (UnknownHostException e) { // should never happen
					e.printStackTrace();
					fail();
				} catch (IOException e) {
					e.printStackTrace();
					fail();
				}
			} else 
				pipes.put(agentID, pipe);
			
			// Check if all agents have reported, and if so, tell them to connect
			if (pipes.size() >= nbrAgents) {
				synchronized (agents) { // synchronize so we don't tell the agents to connect before the list of agents is ready
					queue.sendMessageToMulti(pipes.keySet(), new Message (WhitePages.CONNECT_AGENT));
				}
			}
		}
		
		else if (msg.getType().equals(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST)) {
			MessageWith2Payloads<String, String> msgCast = (MessageWith2Payloads<String, String>) msg;
			String recipient = msgCast.getPayload2();
			agents.get(msgCast.getPayload1()).addOutputPipe(recipient, pipes.get(recipient));
		}
		
		else if (msg.getType().equals(AgentInterface.AGENT_CONNECTED)) {
			if (++nbrMsgsReceived >= nbrAgents) { // all agents are now connected; tell them to start
				queue.sendMessageToMulti(pipes.keySet(), new Message (startMsgType));
			}
		}
		
		else if (msg.getType().equals(AgentInterface.AGENT_FINISHED)) {
			this.finished_lock.lock();
			MessageWrapper msgWrap = queue.getCurrentMessageWrapper();
			if (++nbrAgentsFinished >= this.nbrAgents) 
				this.finished.signal();
			
			if (this.countNCCCs) {
				
				// The NCCC counter is allowed to be 0 if one agent is unconstrained
				boolean forbid0 = true;
				for (String agent : this.problem.getAgents()) {
					if (this.problem.getSubProblem(agent).getSolutionSpaces().isEmpty()) {
						forbid0 = false;
						break;
					}
				}
				
				if (forbid0) 
					assertTrue ("NCCC count: " + msgWrap.getNCCCs(), msgWrap.getNCCCs() > 0);
			}
			
			if (this.useCentralMailer) 
				assertTrue (msgWrap.getTime() >= 0);
			this.finished_lock.unlock();
		}
		
	}

	/** Does nothing
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) { }
	
}
