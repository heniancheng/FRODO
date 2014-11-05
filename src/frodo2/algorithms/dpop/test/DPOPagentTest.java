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

package frodo2.algorithms.dpop.test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.AgentInterface.AgentFinishedMessage;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationParallel;
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
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test for DPOPagent
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * @todo Many tests should inherit this class to favor code reuse. 
 */
public class DPOPagentTest< V extends Addable<V>, U extends Addable<U> > extends TestCase implements IncomingMsgPolicyInterface<String> {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 10;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 50;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;
	
	/** The path to DPOPagent.xml */
	private String dpopPath = "src/frodo2/algorithms/dpop/DPOPagent.xml";
	
	/** The agent configuration file */
	protected Document agentConfig;

	/** The queue used to listen to the agents */
	protected Queue queue;
	
	/** For each agent, the output pipe to it */
	protected Map<Object, QueueOutputPipeInterface> pipes;
	
	/** The testers pipe */
	private QueueIOPipe pipe;
	
	/** All agents, indexed by their IDs */
	private Map< String, AgentInterface<V> > agents;
	
	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;

	/** Total number of agents */
	private int nbrAgents;
	
	/** Used to track the number of various types of messages received from the agents */
	protected int nbrMsgsReceived;
	
	/** Number of agents finished */
	protected int nbrAgentsFinished;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** \c true if the algorithm should be tested with the central mailer*/
	protected boolean useCentralMailer = false;
	
	/** \c true when the central mailer is to be tested with delays */
	private boolean useDelay;
	
	/** Whether to measure message numbers and sizes */
	private boolean measureMsgs;
	
	/** The XCSP random problem file */
	protected Document problemDoc;
	
	/** The class of the parser/subsolver to use */
	private Class< ? extends XCSPparser<V, U> > parserClass;
	
	/** The problem */
	protected DCOPProblemInterface<V, U> problem;
	
	/** The class used for variable values */
	protected Class<V> domClass;
	
	/** The class used for utility values */
	protected Class<U> utilClass;
	
	/** The module listening for the optimal utility to the problem */
	protected UTILpropagation<V, U> utilModule;
	
	/** The module listening for the optimal assignment to the problem */
	protected VALUEpropagation<V> valueModule;

	/** Whether to use XCSP */
	protected boolean useXCSP;

	/** Whether TCP pipes should be used for communication between agents */
	private boolean useTCP;

	/** The type of the start message */
	protected String startMsgType;

	/** Whether to count NCCCs */
	protected boolean countNCCCs;

	/** Whether we should maximize or minimize */
	protected boolean maximize = true;

	/** The CentralMailer */
	protected CentralMailer mailman;
	
	/** \c true when the weighted sum hypercube should be tested*/
	protected boolean aggregate = false;

	/** Whether to optimize runtime or NCCC count */
	private boolean minNCCCs;
	
	/** Whether to ignore Hypercube NCCCs or not*/
	private boolean ignoreHypercubeNCCCs;

	/** Whether we should swap */
	private boolean swap;

	/** Creates a JUnit test case corresponding to the input method
	 * @param useXCSP 			whether to use XCSP
	 * @param useTCP 			whether TCP pipes should be used for communication between agents
	 * @param useCentralMailer	\c true when the central mailer should be tested
	 * @param useDelay 			\c true when the central mailer should be tested with a delay
	 * @param domClass 			the class to be used for variable values
	 * @param utilClass 		the class to be used for utility values
	 */
	@SuppressWarnings("unchecked")
	public DPOPagentTest(boolean useXCSP, boolean useTCP, boolean useCentralMailer, boolean useDelay, Class<V> domClass, Class<U> utilClass) {
		this(useXCSP, useTCP, useCentralMailer, useDelay, domClass, utilClass, null, (Class<? extends XCSPparser<V, U>>) XCSPparser.class, false, false, false, false, false);
	}
	
	/** Constructor
	 * @param useXCSP 				whether to use XCSP
	 * @param swap 					whether we should swap
	 * @param minNCCCs 				whether to optimize runtime or NCCC count
	 * @param countNCCCs 			whether to count NCCCs
	 * @param ignoreHypercubeNCCCs 	Whether to ignore Hypercube NCCCs or not
	 */
	@SuppressWarnings("unchecked")
	public DPOPagentTest(boolean useXCSP, boolean swap, boolean minNCCCs, boolean countNCCCs, boolean ignoreHypercubeNCCCs) {
		this(useXCSP, false, false, false, (Class<V>) AddableInteger.class, (Class<U>) AddableInteger.class, null, (Class<? extends XCSPparser<V, U>>) XCSPparser.class, swap, minNCCCs, countNCCCs, false, ignoreHypercubeNCCCs);
	}
	
	/** Constructor
	 * @param useXCSP 				whether to use XCSP
	 * @param swap 					whether we should swap
	 * @param minNCCCs 				whether to optimize runtime or NCCC count
	 * @param countNCCCs 			whether to count NCCCs
	 * @param ignoreHypercubeNCCCs 	Whether to ignore Hypercube NCCCs or not
	 * @param dpopPath 				The path the the DPOP agent configuration file
	 */
	public DPOPagentTest(boolean useXCSP, boolean swap, boolean minNCCCs, boolean countNCCCs, boolean ignoreHypercubeNCCCs, String dpopPath) {
		this(useXCSP, swap, minNCCCs, countNCCCs, ignoreHypercubeNCCCs);
		this.dpopPath = dpopPath;
	}
	
	/** Constructor for a test with a subsolver
	 * @param parserClass 	class of the parser/subsolver
	 */
	@SuppressWarnings("unchecked")
	public DPOPagentTest (Class<? extends XCSPparser<V, U>> parserClass) {
		this(true, false, false, false, (Class<V>) AddableInteger.class, (Class<U>) AddableInteger.class, null, (Class<? extends XCSPparser<V, U>>) parserClass, false, false, false, false, false);
	}
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param useXCSP 			whether to use XCSP
	 * @param useTCP 			whether TCP pipes should be used for communication between agents
	 * @param useCentralMailer	\c true when the central mailer should be tested
	 * @param useDelay 			\c true when the central mailer should be tested with a delay
	 * @param domClass 			the class to be used for variable values
	 * @param utilClass 		the class to be used for utility values
	 * @param startMsgType 		the type of the start message
	 */
	@SuppressWarnings("unchecked")
	public DPOPagentTest(boolean useXCSP, boolean useTCP, boolean useCentralMailer, boolean useDelay, Class<V> domClass, Class<U> utilClass, String startMsgType) {
		this(useXCSP, useTCP, useCentralMailer, useDelay, domClass, utilClass, startMsgType, (Class<? extends XCSPparser<V, U>>) XCSPparser.class, false, false, false, false, false);
	}
	
	/** Creates a JUnit test case corresponding to the input method
	 * @param useXCSP 				whether to use XCSP
	 * @param useTCP 				whether TCP pipes should be used for communication between agents
	 * @param useCentralMailer		\c true when the central mailer should be tested
	 * @param useDelay 				\c true when the central mailer should be tested with a delay
	 * @param domClass 				the class to be used for variable values
	 * @param utilClass 			the class to be used for utility values
	 * @param startMsgType 			the type of the start message
	 * @param parserClass 			the class of the parser/subsolver
	 * @param swap 					whether we should swap
	 * @param minNCCCs 				whether to optimize runtime or NCCC count
	 * @param countNCCCs 			whether to count NCCCs
	 * @param measureMsgs 			whether to measure message numbers and sizes
	 * @param ignoreHypercubeNCCCs 	Whether to ignore Hypercube NCCCs or not
	 */
	public DPOPagentTest(boolean useXCSP, boolean useTCP, boolean useCentralMailer, boolean useDelay, Class<V> domClass, Class<U> utilClass, String startMsgType, Class< ? extends XCSPparser<V, U> > parserClass, 
			boolean swap, boolean minNCCCs, boolean countNCCCs, boolean measureMsgs, boolean ignoreHypercubeNCCCs) {
		super ("testRandom");
		this.useXCSP = useXCSP;
		this.useTCP = useTCP;
		this.domClass = domClass;
		this.utilClass = utilClass;
		this.useCentralMailer = useCentralMailer;
		this.parserClass = parserClass;
		this.countNCCCs = countNCCCs;
		this.useDelay = useDelay;
		this.measureMsgs = measureMsgs;
		this.minNCCCs = minNCCCs;
		this.ignoreHypercubeNCCCs = ignoreHypercubeNCCCs;
		this.swap = swap;
	}
	
	/** Sets the type of the start message for all modules
	 * @param startMsgType 		the new type for the start message
	 * @throws JDOMException 	if parsing the agent configuration file failed
	 */
	protected void setStartMsgType (String startMsgType) throws JDOMException {
		this.startMsgType = AgentInterface.START_AGENT;
		if (startMsgType != null) {
			this.startMsgType = startMsgType;
			for (Element module2 : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
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
	@SuppressWarnings("unchecked")
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for DPOPagent");
		
		TestSuite tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, false, false, AddableInteger.class, AddableInteger.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities and without XCSP");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (false, false, false, false, AddableInteger.class, AddableInteger.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities and real-valued variables");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableReal, AddableInteger> (true, false, false, false, AddableReal.class, AddableInteger.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities and the central mailer");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, true, false, AddableInteger.class, AddableInteger.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities and the central mailer with delay");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, true, true, AddableInteger.class, AddableInteger.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities and the central mailer and measuring messages");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, true, false, AddableInteger.class, AddableInteger.class, null, 
				(Class<? extends XCSPparser<AddableInteger, AddableInteger>>) XCSPparser.class, false, false, false, true, false), 1000));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes with integer-valued utilities");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, true, false, false, AddableInteger.class, AddableInteger.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with real-valued utilities");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableReal> (true, false, false, false, AddableInteger.class, AddableReal.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with real-valued utilities and without XCSP");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableReal> (false, false, false, false, AddableInteger.class, AddableReal.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with real-valued utilities and the central mailer");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableReal> (true, false, true, false, AddableInteger.class, AddableReal.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using TCP pipes with real-valued utilities");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableReal> (true, true, false, false, AddableInteger.class, AddableReal.class), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes with integer-valued utilities and a different type for the start message");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, false, false, AddableInteger.class, AddableInteger.class, "START NOW!"), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes using swapping");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, true, false, false, false), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes counting NCCCs");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, true, false, true, false), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes counting NCCCs without XCSP");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (false, true, false, true, false), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes counting NCCCs, but ignoring Hypercube NCCCs");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, true, false, true, true), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes minimizing NCCCs");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, true, false, false), 25));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests using QueueIOPipes minimizing NCCCs and counting NCCCs");
		tmp.addTest(new RepeatedTest (new DPOPagentTest<AddableInteger, AddableInteger> (true, false, true, true, false), 25));
		suite.addTest(tmp);
		
		return suite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	public void setUp () throws Exception {
		
		agentConfig = XCSPparser.parse(this.dpopPath, false);
		
		// Set the class used for utilities and whether we should minimize the NCCC count
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").equals(UTILpropagation.class.getName())) {
				module.setAttribute("minNCCCs", Boolean.toString(minNCCCs));
			}
		}
		
		// Set the type of the start message
		this.setStartMsgType(startMsgType);
		
		// Set whether we should swap
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(VALUEpropagation.class.getName())) 
				module.setAttribute("swap", Boolean.toString(swap));
		
		// set the proper parser
		if(agentConfig.getRootElement().getChild("parser") != null)
			agentConfig.getRootElement().getChild("parser").setAttribute("parserClass", parserClass.getCanonicalName());
		else {
			Element elmt = new Element("parser");
			elmt.setAttribute("parserClass", parserClass.getCanonicalName());
			agentConfig.getRootElement().addContent(elmt);
		}
	
		nbrMsgsReceived = 0;
		nbrAgentsFinished = 0;
		
		// Create the queue
		if (this.useCentralMailer) {
			mailman = new CentralMailer (this.measureMsgs, this.useDelay, null);
			this.queue = mailman.newQueue(AgentInterface.STATS_MONITOR);
		} else 
			queue = new Queue (false);
		
		queue.addIncomingMessagePolicy(this);
		pipes = new HashMap<Object, QueueOutputPipeInterface> ();
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problemDoc = AllTests.generateProblem(graph, this.maximize);
		
		
		// Instantiate the parser/subsolver
		Constructor< ? extends XCSPparser<V, U> > constructor = this.parserClass.getConstructor(Document.class, Boolean.class);
		XCSPparser<V, U> parser = constructor.newInstance(problemDoc, this.countNCCCs);
		this.problem = parser;
		parser.setDomClass(domClass);
		parser.setUtilClass(utilClass);
		if (! this.useXCSP) {
			this.problem = new Problem<V, U> (this.maximize);
			this.problem.reset(parser);
			this.problem.setDomClass(domClass);
			this.problem.setUtilClass(utilClass);
		}
		
		if(this.ignoreHypercubeNCCCs)
			parser.addSpaceToIgnore("frodo2.solutionSpaces.hypercube.Hypercube");
		
		utilModule = new UTILpropagation<V, U> (null, parser);
		utilModule.setSilent(true);
		utilModule.getStatsFromQueue(queue);
		valueModule = new VALUEpropagation<V> (null, parser);
		valueModule.setSilent(true);
		valueModule.getStatsFromQueue(queue);
		
		DFSgeneration<V, U> module = new DFSgeneration<V, U> (null, parser);
		module.setSilent(true);
		module.getStatsFromQueue(queue);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown () throws Exception {
		super.tearDown();
		if (this.useCentralMailer) 
			mailman.end();
		queue.end();
		for (QueueOutputPipeInterface pipe : pipes.values())
			pipe.close();
		pipe.close();
		pipe = null;
		pipes.clear();
		for (AgentInterface<V> agent : agents.values()) 
			agent.kill();
		agents.clear();
		queue = null;
		pipes = null;
		agents = null;
		graph = null;
		problemDoc = null;
		problem = null;
		utilModule = null;
		valueModule = null;
		this.startMsgType = null;
	}
	
	/** Tests the DPOPagent on a random problem 
	 * @throws Exception if an error occurs
	 */
	public void testRandom () throws Exception {
		
		// Set up the input pipe for the queue
		pipe = new QueueIOPipe (queue);
		
		// Create the agent descriptions
		String useCentralMailerString = Boolean.toString(useCentralMailer);
		agentConfig.getRootElement().setAttribute("measureTime", useCentralMailerString);
		agentConfig.getRootElement().setAttribute("measureMsgs", Boolean.toString(this.measureMsgs));
		
		// Create the set of agents, including potentially empty agents
		Set<String> agentsSet  = new HashSet<String> ();
		for (int i = graph.clusters.size() - 1; i >= 0; i--) 
			agentsSet.add(Integer.toString(i));
		nbrAgents = agentsSet.size();
		
		// Go through the list of agents and instantiate them
		agents = new HashMap< String, AgentInterface<V> > (nbrAgents);
		synchronized (agents) {
			if (useTCP) { // use TCP pipes
				int port = 5500;
				for (String agent : agentsSet) {
					DCOPProblemInterface<V, U> subproblem = problem.getSubProblem(agent);
					agents.put(agent, AgentFactory.createAgent(pipe, pipe, subproblem, agentConfig, port++));
				}
			} else { // use QueueIOPipes
				for (String agent : agentsSet) {
					DCOPProblemInterface<V, U> subproblem = problem.getSubProblem(agent);
					agents.put(agent, AgentFactory.createAgent(pipe, subproblem, agentConfig, mailman));
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
		
		if (this.useCentralMailer) 
			mailman.end();
		
		checkOutput();
	}

	/** Checks that the output of the algorithm is correct 
	 * @throws Exception if an error occurs
	 */
	protected void checkOutput() throws Exception {
		
		U optUtil = this.utilModule.getOptUtil();
		Map<String, V> solution = this.valueModule.getSolution();
		
		// First check that the output assignments and the output utility are consistent
		problem.setDomClass(domClass);
		problem.setUtilClass(utilClass);
		UtilitySolutionSpace<V, U> realUtil = problem.getUtility(solution);
		assertEquals (realUtil.getUtility(0), optUtil);
		
		// Check that the output utility is the same as the optimal utility computed by DPOP, but using default heuristics for DFSgenerationParallel
	
		// Generate the agent description
		Document agentConfig = XCSPparser.parse("src/frodo2/algorithms/dpop/DPOPagent.xml", false);
		agentConfig.getRootElement().setAttribute("measureTime", "false");
		
		// Set the class used for utilities
		agentConfig.getRootElement().getChild("parser").setAttribute("utilClass", this.utilClass.getName());
		agentConfig.getRootElement().getChild("parser").setAttribute("domClass", this.domClass.getName());

		// Reset the DFS heuristics
		for (Element module : (List<Element>) agentConfig.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").equals(DFSgenerationParallel.class.getName())) {
				module.removeChild("rootElectionHeuristic");
				module.getChild("dfsGeneration").removeChild("dfsHeuristic");
			}
		}

		// Solve the problem and compare the two utilities
		DPOPsolver<V, U> solver = new DPOPsolver<V, U> (agentConfig, parserClass);
		assertEquals (solver.solve(problem).getUtility(), optUtil);		
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
				queue.sendMessageToMulti(pipes.keySet(), new Message (startMsgType));
			}
		}
		
		else if (msg.getType().equals(AgentInterface.AGENT_FINISHED)) {
			
			if (this.countNCCCs) { 
				MessageWrapper msgWrap = queue.getCurrentMessageWrapper();
				if(this.ignoreHypercubeNCCCs)
					assertTrue (msgWrap.getNCCCs() == 0);
				else
					assertTrue (msgWrap.getNCCCs() >= 0);
			}
			
			if (this.useCentralMailer) 
				assertTrue (queue.getCurrentMessageWrapper().getTime() >= 0);
			
			if (this.measureMsgs) {
				AgentFinishedMessage msgCast = (AgentFinishedMessage) msg;
				assertFalse (msgCast.getMsgNbrs() == null);
				assertFalse (msgCast.getMsgSizes() == null);
				assertFalse (msgCast.getMaxMsgSizes() == null);
			}

			this.finished_lock.lock();
			if (++nbrAgentsFinished >= this.nbrAgents) 
				this.finished.signal();
			this.finished_lock.unlock();
		}
		
	}

	/** Does nothing
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) { }
	
}
