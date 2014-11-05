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

package frodo2.algorithms.localSearch.dsa.tests;

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
import frodo2.algorithms.localSearch.dsa.DSA;
import frodo2.algorithms.localSearch.dsa.DSA.AssignmentMessage;
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
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** JUnit tests for the DSA agent
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @author Brammert Ottens, Thomas Leaute
 */
public class DSAagentTest < V extends Addable<V>, U extends Addable<U> > extends TestCase  implements IncomingMsgPolicyInterface<String> {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	protected final int maxNbrVars = 10;
	
	/** Maximum number of binary constraints in the problem */
	protected final int maxNbrEdges = 15;

	/** Maximum number of agents */
	protected final int maxNbrAgents = 10;
	
	/** The queue used to listen to the agents */
	protected Queue queue;
	
	/** For each agent, the output pipe to it */
	protected Map<Object, QueueOutputPipeInterface> pipes;
	
	/** The tester's pipe */
	protected QueueIOPipe pipe;
	
	/** All agents, indexed by their IDs */
	protected Map< String, AgentInterface<V> > agents;
	
	/** Total number of agents */
	protected int nbrAgents;
	
	/** Used to track the number of various types of messages received from the agents */
	protected int nbrMsgsReceived;
	
	/** Number of agents finished */
	protected int nbrAgentsFinished;
	
	/** Used to make the test thread wait */
	protected final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	protected final Condition finished = finished_lock.newCondition();

	/** Whether to use XCSP */
	private boolean useXCSP;

	/** Whether TCP pipes should be used for communication between agents */
	protected boolean useTCP;
	
	/** Whether we should count Non-Concurrent Constraint Checks */
	protected boolean countNCCCs = false;

	/** The DSA stats gatherer listening for the solution */
	protected DSA<V, U> statsGatherer;
	
	/** The type of the start message */
	protected String startMsgType;

	/** The description of the agent */
	protected Document agentDesc;
	
	/** \c true if the algorithm must be tested with the central mailer */
	protected boolean useCentralMailer;

	/** The overall problem */
	private DCOPProblemInterface<V, U> problem;

	/** The CentralMailer */
	protected CentralMailer mailman;

	/** The class used for variable values */
	protected Class<V> domClass;

	/** The class used for utility values */
	protected Class<U> utilClass;

	/** The decision strategy used */
	protected String strategy;
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param useXCSP 			Whether to use XCSP
	 * @param useTCP 			whether TCP pipes should be used for communication between agents
	 * @param countNCCCs 		whether we should tell the agent to count NCCCs
	 * @param useCentralMailer 	\c true when the central mailer should be used and tested
	 * @param startMsgType 		the type of the start message
	 * @param strategy			the decision strategy used
	 * @param domClass 			The class used for variable values
	 * @param utilClass 		the class used for utility values
	 */
	public DSAagentTest(boolean useXCSP, boolean useTCP, boolean countNCCCs, boolean useCentralMailer, String startMsgType, String strategy, Class<V> domClass, Class<U> utilClass) {
		super ("testRandom");
		this.useXCSP = useXCSP;
		this.useTCP = useTCP;
		this.countNCCCs = countNCCCs;
		this.useCentralMailer = useCentralMailer;
		this.domClass = domClass;
		this.utilClass = utilClass;
		this.strategy = strategy;
	}

	/** Sets the type of the start message for all modules
	 * @param startMsgType 		the new type for the start message
	 * @throws JDOMException 	if parsing the agent configuration file failed
	 */
	protected void setStartMsgType (String startMsgType) throws JDOMException {
		if (startMsgType != null) {
			this.startMsgType = startMsgType;
			for (Element module2 : (List<Element>) this.agentDesc.getRootElement().getChild("modules").getChildren()) {
				for (Element message : (List<Element>) module2.getChild("messages").getChildren()) {
					if (message.getAttributeValue("name").equals("START_MSG_TYPE")) {
						message.setAttribute("value", startMsgType);
						message.removeAttribute("ownerClass");
					}
				}
			}
		} else 
			this.startMsgType = AgentInterface.START_AGENT;
	}

	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for DSAagent");
		
		TestSuite tmp = new TestSuite ("Tests DSA A using QueueIOPipes and AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, false, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and AddableInteger utilities without XCSP");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (false, false, false, false, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and AddableInteger utilities and real-valued variables");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableReal, AddableInteger> (true, false, false, false, null, DSA.A.class.getName(), AddableReal.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, false, null, DSA.C.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, false, null, DSA.E.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and the central mailer AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, true, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes and the central mailer AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, true, null, DSA.C.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes and the central mailer AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, true, null, DSA.E.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and NCCCs AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, true, false, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and NCCCs AddableInteger utilities without XCSP");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (false, false, true, false, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes and NCCCs AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, true, false, null, DSA.C.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes and NCCCs AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, true, false, null, DSA.E.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and NCCCs and the central mailer AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, true, true, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes and NCCCs and the central mailer AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, true, true, null, DSA.C.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes and NCCCs and the central mailer AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, true, true, null, DSA.E.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using TCP pipes AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, true, false, false, null, DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using TCP pipes AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, true, false, false, null, DSA.C.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using TCP pipes AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, true, false, false, null, DSA.E.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and a different type of start message AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableInteger> (true, false, false, false, "START NOW!", DSA.A.class.getName(), AddableInteger.class, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, false, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and AddableReal utilities without XCSP");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (false, false, false, false, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, false, null, DSA.C.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, false, null, DSA.E.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and the central mailer AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, true, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes and the central mailer AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, true, null, DSA.C.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes and the central mailer AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, true, null, DSA.E.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and NCCCs AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, true, false, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and NCCCs AddableReal utilities without XCSP");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (false, false, true, false, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes and NCCCs AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, true, false, null, DSA.C.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes and NCCCs AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, true, false, null, DSA.E.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and NCCCs and the central mailer AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, true, true, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using QueueIOPipes and NCCCs and the central mailer AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, true, true, null, DSA.C.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using QueueIOPipes and NCCCs and the central mailer AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, true, true, null, DSA.E.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using TCP pipes AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, true, false, false, null, DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA C using TCP pipes AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, true, false, false, null, DSA.C.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA E using TCP pipes AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, true, false, false, null, DSA.E.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests DSA A using QueueIOPipes and a different type of start message AddableReal utilities");
		tmp.addTest(new RepeatedTest (new DSAagentTest<AddableInteger, AddableReal> (true, false, false, false, "START NOW!", DSA.A.class.getName(), AddableInteger.class, AddableReal.class), 50));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	public void setUp () throws Exception {

		agentDesc = XCSPparser.parse("src/frodo2/algorithms/localSearch/dsa/DSAagent.xml", false);
		for (Element module2 : (List<Element>) this.agentDesc.getRootElement().getChild("modules").getChildren()) {
			if(module2.getAttributeValue("className").equals(DSA.class.getName())) {
				module2.setAttribute("strategy", strategy);
			}
		}
		this.setStartMsgType(startMsgType);
	
		nbrMsgsReceived = 0;
		nbrAgentsFinished = 0;
		
		// Create the queue
		if (this.useCentralMailer) {
			mailman = new CentralMailer (false, false, null);
			this.queue = mailman.newQueue(AgentInterface.STATS_MONITOR);
		} else 
			queue = new Queue (false);
		
		queue.addIncomingMessagePolicy(this);
		pipes = new HashMap<Object, QueueOutputPipeInterface> ();
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
		this.startMsgType = null;
		this.problem = null;
	}
	
	/** Tests the DSAagent on a random problem */
	public void testRandom () {
		
		// Set up the input pipe for the queue
		pipe = new QueueIOPipe (queue);
		
		// Create the problem and agent descriptions
		boolean maximize = (Math.random() < .5);
		Document problemDoc = AllTests.createRandProblem(maxNbrVars, maxNbrEdges, maxNbrAgents, maximize);
		problem = new XCSPparser<V, U> (problemDoc, countNCCCs);
		problem.setDomClass(domClass);
		problem.setUtilClass(this.utilClass);
		if (! this.useXCSP) {
			Problem<V, U> prob = new Problem<V, U> (maximize);
			prob.reset(this.problem);
			this.problem = prob;
			problem.setDomClass(domClass);
			problem.setUtilClass(this.utilClass);
		}
		
		statsGatherer = new DSA<V, U> (null, problem);
		statsGatherer.setSilent(true);
		statsGatherer.getStatsFromQueue(queue);
		
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
		
		long timeout = 60000; // in ms
		
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
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (5);
		types.add(AgentInterface.LOCAL_AGENT_REPORTING);
		types.add(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST);
		types.add(AgentInterface.AGENT_CONNECTED);
		types.add(AgentInterface.AGENT_FINISHED);
		types.add(DSA.OUTPUT_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(AgentInterface.LOCAL_AGENT_REPORTING)) {
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
		
		else if (msgType.equals(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST)) {
			MessageWith2Payloads<String, String> msgCast = (MessageWith2Payloads<String, String>) msg;
			String recipient = msgCast.getPayload2();
			agents.get(msgCast.getPayload1()).addOutputPipe(recipient, pipes.get(recipient));
		}
		
		else if (msgType.equals(AgentInterface.AGENT_CONNECTED)) {
			if (++nbrMsgsReceived >= nbrAgents) { // all agents are now connected; tell them to start
				queue.sendMessageToMulti(pipes.keySet(), new Message (startMsgType));
			}
		}
		
		else if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			this.finished_lock.lock();
			if (++nbrAgentsFinished >= this.nbrAgents) 
				this.finished.signal();
			this.finished_lock.unlock();
			if (this.useCentralMailer) 
				assertTrue (queue.getCurrentMessageWrapper().getTime() >= 0);
		}
		
		else if (msgType.equals(DSA.OUTPUT_MSG_TYPE)) {
			
			if (this.countNCCCs)  {
				
				// Don't check the NCCC counter if this variable is unconstrained
				AssignmentMessage<V, U> msgCast = (AssignmentMessage<V, U>) msg;
				if (this.problem.getSolutionSpaces(msgCast.getVariable(), null).isEmpty()) 
					return;
				
				MessageWrapper msgWrap = queue.getCurrentMessageWrapper();
				assertTrue (msgWrap.getNCCCs() > 0);
			}
		}
		
	}

	/** Does nothing
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) { }

}
