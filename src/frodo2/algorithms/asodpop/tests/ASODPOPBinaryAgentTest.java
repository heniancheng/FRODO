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

package frodo2.algorithms.asodpop.tests;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.reformulation.ProblemRescaler;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.asodpop.ASODPOPBinaryDomains;
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
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * JUnit test of the ASODPOP agent
 * @author Brammert Ottens, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 *
 */
public class ASODPOPBinaryAgentTest < V extends Addable<V>, U extends Addable<U> > extends TestCase implements IncomingMsgPolicyInterface<String> {


	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 10;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 15;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;
	
	/** The queue used to listen to the agents */
	private Queue queue;
	
	/** For each agent, the output pipe to it */
	private Map<Object, QueueOutputPipeInterface> pipes;
	
	/** The testers pipe */
	QueueIOPipe pipe;
	
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

	/** The ADOPT stats gatherer listening for the solution */
	private ASODPOPBinaryDomains<V, U> statsGatherer;
	
	/** The description of the agent */
	private Document agentDesc;
	
	/** \c true if the algorithm must be tested with the central mailer */
	private boolean useCentralMailer;
	
	/** \c true when the central mailer is to be tested with delays */
	private boolean useDelay;

	/** The CentralMailer */
	private CentralMailer mailman;
	
	/** The type used for variable values */
	private Class<V> domClass;
	
	/** The type used for utility values */
	private Class<U> utilClass;
	
	/** Maximize of minimize */
	private final boolean maximize;
	
	/** The sign of costs/utilities */
	private final int sign;

	/** Whether to use XCSP */
	private boolean useXCSP;
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param string 			name of the method
	 * @param useXCSP 			whether to use XCSP
	 * @param useCentralMailer 	\c true when the central mailer should be used and tested
	 * @param useDelay 			\c true when the algorithm should be tested with the use of delay
	 * @param domClass 			The type used for variable values
	 * @param utilClass 		the type used for utility values
	 * @param maximize 			Maximize of minimize
	 * @param sign 				The sign of costs/utilities
	 */
	public ASODPOPBinaryAgentTest(String string, boolean useXCSP, boolean useCentralMailer, boolean useDelay, Class<V> domClass, Class<U> utilClass, boolean maximize, int sign) {
		super (string);
		this.useXCSP = useXCSP;
		this.useCentralMailer = useCentralMailer;
		this.useDelay = useDelay;
		this.domClass = domClass;
		this.utilClass = utilClass;
		this.maximize = maximize;
		this.sign = sign;
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for ASODPOPBinaryAgent");
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", true, false, false, AddableInteger.class, AddableInteger.class, true, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities and without XCSP");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", false, false, false, AddableInteger.class, AddableInteger.class, true, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities on maximization problems with unrestricted utility signs");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", true, false, false, AddableInteger.class, AddableInteger.class, true, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities on maximization problems with unrestricted utility signs and without XCSP");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", false, false, false, AddableInteger.class, AddableInteger.class, true, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities on minimization problems with unrestricted cost signs");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", true, false, false, AddableInteger.class, AddableInteger.class, false, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities on minimization problems with unrestricted cost signs and without XCSP");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", false, false, false, AddableInteger.class, AddableInteger.class, false, 0), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer utilities and real-valued variables");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableReal, AddableInteger> ("testRandomSharedMemory", true, false, false, AddableReal.class, AddableInteger.class, true, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with real utilities");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableReal> ("testRandomSharedMemory", true, false, false, AddableInteger.class, AddableReal.class, true, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with real utilities and without XCSP");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableReal> ("testRandomSharedMemory", false, false, false, AddableInteger.class, AddableReal.class, true, +1), 500));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer with integer utilities");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", true, true, false, AddableInteger.class, AddableInteger.class, true, +1), 500));
		suite.addTest(tmp);
		
		/// @bug Rarely gets stuck
//		tmp = new TestSuite ("Tests using QueueIOPipes and the central mailer with delays with integer utilities");
//		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomSharedMemory", true, true, true, AddableInteger.class, AddableInteger.class, true, +1), 5000));
//		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes with integer utilities");
		tmp.addTest(new RepeatedTest (new ASODPOPBinaryAgentTest<AddableInteger, AddableInteger> ("testRandomTCP", true, false, false, AddableInteger.class, AddableInteger.class, true, +1), 500));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	public void setUp () throws Exception {
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
		
		agentDesc = XCSPparser.parse("src/frodo2/algorithms/asodpop/ASODPOPBinaryagent.xml", false);
		
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
		queue.end();
		for (QueueOutputPipeInterface pipe : pipes.values())
			pipe.close();
		pipes.clear();
		for (AgentInterface<V> agent : agents.values()) 
			agent.kill();
		agents.clear();
		queue = null;
		pipes = null;
		agents = null;
		statsGatherer = null;
		this.agentDesc = null;
		this.pipe = null;
	}
	
	/** Tests the ASODPOPagent on a random problem using QueueIOPipes for inter-agent communication
	 * @throws Exception 	if an error occurs
	 */
	@SuppressWarnings("unchecked")
	public void testRandomSharedMemory () throws Exception {
		testRandom(false, (Class<? extends XCSPparser<V, U>>) XCSPparser.class, false);
	}

	/** Tests the ASODPOPagent on a random problem using TCP pipes for inter-agent communication
	 * @throws Exception 	if an error occurs
	 */
	@SuppressWarnings("unchecked")
	public void testRandomTCP () throws Exception {
		testRandom(true, (Class<? extends XCSPparser<V, U>>) XCSPparser.class, false);
	}
	
	/** Tests the DPOPagent on a random problem 
	 * @param useTCP 						whether TCP pipes should be used for communication between agents
	 * @param parserClass					the parser to be used
	 * @param aggregate						\c true when we use a model with aggregate constraints
	 * @throws IOException 					if unable to access DPOPagent.xml
	 * @throws JDOMException 				if unable to parse DPOPagent.xml
	 * @throws NoSuchMethodException 		if the parser does not have a constructor that takes in a Document
	 * @throws InvocationTargetException 	if the parser constructor throws an exception
	 * @throws IllegalAccessException 		if the parser constructor is not accessible
	 * @throws InstantiationException 		if the parser is abstract
	 * @throws IllegalArgumentException 	should never happen
	 */
	public void testRandom (boolean useTCP, Class< ? extends XCSPparser<V, U> > parserClass, boolean aggregate) 
	throws JDOMException, IOException, NoSuchMethodException, IllegalArgumentException, 
	InstantiationException, IllegalAccessException, InvocationTargetException {
		// Set up the input pipe for the queue
		pipe = new QueueIOPipe (queue);
		
		// Create the problem and agent descriptions
		Document problemDoc = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, this.maximize, this.sign, true);
		
		// Instantiate the parser/subsolver
		Constructor< ? extends XCSPparser<V, U> > constructor = null;
		DCOPProblemInterface<V, U> problem = null;
		constructor = parserClass.getConstructor(Document.class);
		problem = constructor.newInstance(problemDoc);
		problem.setDomClass(domClass);
		problem.setUtilClass(utilClass);
		if (!this.useXCSP) {
			Problem<V, U> prob = new Problem<V, U> (this.maximize);
			prob.reset(problem);
			prob.setDomClass(domClass);
			prob.setUtilClass(utilClass);
			problem = prob;
		}
		
		statsGatherer = new ASODPOPBinaryDomains<V, U> (null, problem);
		statsGatherer.setSilent(true);
		statsGatherer.getStatsFromQueue(queue);

		String useCentralMailerString = Boolean.toString(useCentralMailer);
		agentDesc.getRootElement().setAttribute("measureTime", useCentralMailerString);
		if(agentDesc.getRootElement().getChild("parser") != null)
			agentDesc.getRootElement().getChild("parser").setAttribute("parserClass", parserClass.getCanonicalName());
		else {
			Element elmt = new Element("parser");
			elmt.setAttribute("parserClass", parserClass.getCanonicalName());
			agentDesc.getRootElement().addContent(elmt);
		}
		
		// Go through the list of agents and instantiate them
		Set<String> agentNames = problem.getAgents();
		nbrAgents = agentNames.size();
		agents = new HashMap< String, AgentInterface<V> > (nbrAgents);
		synchronized (agents) {
			if (useTCP) { // use TCP pipes
				int port = 5500;
				for (String agent : agentNames) {
					DCOPProblemInterface<V, U> agentElmt = problem.getSubProblem(agent);
					agents.put(agentElmt.getAgent(), AgentFactory.createAgent(pipe, pipe, agentElmt, agentDesc, port++));
				}
			} else { // use QueueIOPipes
				for (String agent : agentNames) {
					DCOPProblemInterface<V, U> agentElmt = problem.getSubProblem(agent);
					agents.put(agentElmt.getAgent(), AgentFactory.createAgent(pipe, agentElmt, agentDesc, mailman));
				}
			}
		}
		
		long timeout = 10000; // in ms
		
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
		
		// Check that ASODPOP and DPOP agree on the total optimal utility
		U totalOptUtil = statsGatherer.getTotalOptUtil();
		Document dpopAgent = XCSPparser.parse(AgentFactory.class.getResourceAsStream("/frodo2/algorithms/dpop/DPOPagent.xml"), false);
		dpopAgent.getRootElement().getChild("parser").setAttribute("utilClass", this.utilClass.getName());
		U dpopOptUtil = (new DPOPsolver<V, U> (dpopAgent, this.domClass, this.utilClass)).solve(problemDoc).getUtility();
		assertEquals (dpopOptUtil, totalOptUtil);
		
		// Check that the optimal assignments have indeed the reported utility
		Map<String, V> optAssignments = statsGatherer.getOptAssignments();
		assertEquals (totalOptUtil, problem.getUtility(optAssignments).getUtility(0));
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
					pipes.put(agentID, Controller.PipeFactoryInstance.outputPipe(Controller.PipeFactoryInstance.getSelfAddress(port)));
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
				queue.sendMessageToMulti(pipes.keySet(), new Message (AgentInterface.START_AGENT));
			}
		}
		
		else if (msg.getType().equals(AgentInterface.AGENT_FINISHED)) {
			this.finished_lock.lock();
			MessageWrapper msgWrap = queue.getCurrentMessageWrapper();
			if (++nbrAgentsFinished >= this.nbrAgents) 
				this.finished.signal();
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
