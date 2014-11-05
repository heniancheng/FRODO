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

package frodo2.algorithms.dpop.privacy.test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jdom2.Element;


import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.dpop.privacy.CodeNameMsg;
import frodo2.algorithms.dpop.privacy.ObfsVALUEmsg;
import frodo2.algorithms.dpop.privacy.ObsfUTILmsg;
import frodo2.algorithms.dpop.privacy.VariableObfuscation;
import frodo2.algorithms.heuristics.MostConnectedHeuristic;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.election.SecureVarElection;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.crypto.AddableBigInteger;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Unit tests for VariableObfuscation
 * @author Eric Zbinden, Thomas Leaute
 * @param <V> the type used for variable values
 * @todo Add test to verify that all variables are using the transmitted codeName and domain 
 * @todo Add test that testifies that all domains used correspond to the correct variable/codeName in UTILmsg
 */
public class VariableObfuscationTest < V extends Addable<V> > extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private int maxNbrVars = 7;
	
	/** Maximum number of edges in the random graph */
	private int maxNbrEdges = 35;

	/** Maximum number of agents */
	private int maxNbrAgents = 7;
	
	/** If mergeBack is used or not */
	private boolean mergeBack;
	
	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;

	/** List of queues corresponding to the different agents */
	private Queue[] queues;
	
	/** One output pipe used to send messages to each queue */
	protected QueueOutputPipeInterface[] pipes;
	
	/** Whether to test using TCP pipes */
	private final boolean useTCP;
	
	/** Lock used to synchronize access to this module's members 
	 * 
	 * This is required because this module is registered to multiple queues, 
	 * therefore multiple threads can call notifyIn simultaneously. 
	 */
	private final Object lock = new Object ();
	
	/** A set with all codeNames */
	private Set<String> cn;
	
	/** A map with codeNames used to test if all codeNames are identical in mergeBack mode. 
	 * 
	 * Variable - CodeName */
	private Map<String, String> cns;
	
	/** A set with all obfuscated domain */ 
	private Set< List<V> > vs;
	
	/** A map with obfuscated domains used to test if all domains are identical in mergeBack mode.
	 * 
	 * CodeName - Obfuscated Domain */
	private Map< String, List<V> > vss;

	/** Parser for the random XCSP problem */
	private XCSPparser<V, AddableBigInteger> parser;
	
	/** List of received codeNames in Obfuscated UTIL messages */ 
	private ArrayList<String> receivedCN;
	
	/** List of received domains in Obfuscated UTIL messages */
	private ArrayList< List<V> > receivedDom;

	/** The number of agents that have not yet sent their AGENT_FINISHED message */
	private int nbrAgents;

	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** The class used for variable values */
	private Class<V> domClass;

	/** Constructor that instantiates a test only for the input method
	 * @param method 	test method
	 * @param useTCP 	whether to use TCP pipes
	 * @param domClass 	The class used for variable values
	 */
	public VariableObfuscationTest(String method, boolean useTCP, Class<V> domClass) {
		super(method);
		this.useTCP = useTCP;
		this.domClass = domClass;
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for VariableObfuscationTest");
		
		TestSuite testTmp = new TestSuite ("Tests whether all names and values are correctly obfuscated in mergeBack mode");
		testTmp.addTest(new RepeatedTest (new VariableObfuscationTest<AddableInteger> ("testMerged", false, AddableInteger.class), 5000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests whether all names and values are correctly obfuscated in mergeBack mode using real-valued variables");
		testTmp.addTest(new RepeatedTest (new VariableObfuscationTest<AddableReal> ("testMerged", false, AddableReal.class), 5000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests whether all names and values are correctly obfuscated in mergeBack mode with TCP pipes");
		testTmp.addTest(new RepeatedTest (new VariableObfuscationTest<AddableInteger> ("testMerged", true, AddableInteger.class), 500));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests whether all names and values are correctly obfuscated without mergeBack mode");
		testTmp.addTest(new RepeatedTest (new VariableObfuscationTest<AddableInteger> ("testUnique", false, AddableInteger.class), 5000));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/**
	 * Tests whenever all name and values are correctly obfuscated in mergeBack mode
	 * Also tests that parents send always the same codeName and domain to all of their children 
	 * @throws Exception 	if an error occurs
	 */
	public void testMerged() throws Exception {
		randomTest(true);
	}
	
	/**
	 * Tests whenever all name and values are correctly obfuscated without mergeBack mode
	 * Also test that all codeNames and domains are different
	 * @throws Exception 	if an error occurs
	 */
	public void testUnique() throws Exception {
		
		// Set the problem sizes lower, otherwise it the tests take up too much memory
		this.maxNbrVars = 5;
		this.maxNbrEdges = 10;
		this.maxNbrAgents = 5;
		
		randomTest(false);
	}
	
	/**
	 * 
	 * @param mergeBack if we are in mergeBack mode or not
	 * @throws IOException 					if an IO exception occur
	 * @throws NoSuchMethodException 		if VariableObfuscation does not have a constructor with the required signature
	 * @throws InvocationTargetException 	if the constructor for VariableObfuscation throws an exception
	 * @throws IllegalAccessException 		if the constructor for VariableObfuscation is inaccessible
	 * @throws InstantiationException 		if VariableObfuscation is an abstract class
	 * @throws IllegalArgumentException 	if the constructor of VariableObfuscation is passed incorrect parameters
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void randomTest(boolean mergeBack) throws IOException, NoSuchMethodException, IllegalArgumentException, 
	InstantiationException, IllegalAccessException, InvocationTargetException {
		
		this.mergeBack = mergeBack;
		Element params = new Element ("module");
		params.setAttribute("mergeBack", Boolean.toString(this.mergeBack));
		
		// Create the queue network
		nbrAgents = graph.clusters.size();
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, useTCP);
		
		// Listen for statistics messages
		Queue myQueue = new Queue (false);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : this.queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		DFSgeneration dfsModule = new DFSgeneration (null, this.parser);
		dfsModule.setSilent(true); // set to false to see the DFS
		dfsModule.getStatsFromQueue(myQueue);

		// Instantiate the modules
		for (int i = 0; i < graph.clusters.size(); i++){
			Queue queue = queues[i];
			String agent = Integer.toString(i);
			
			queue.addIncomingMessagePolicy(this);
			
			// Extract the subproblem for that agent
			XCSPparser<V, AddableBigInteger> subProb = this.parser.getSubProblem(agent);
			queue.setProblem(subProb);
			
			// Instantiate SecureVarElection
			queue.addIncomingMessagePolicy(new SecureVarElection (subProb, this.graph.nodes.size()));
			
			// Instantiate DFSgeneration
			queue.addIncomingMessagePolicy(new DFSgeneration (subProb, 
					new DFSgeneration.ScoreBroadcastingHeuristic<Short>(new MostConnectedHeuristic (subProb, null), subProb.getAgentNeighborhoods())));
			
			// Instantiate VariableObfuscation
			Constructor<VariableObfuscation> constructor = VariableObfuscation.class.getConstructor(DCOPProblemInterface.class, Element.class);
			VariableObfuscation<V, AddableBigInteger> module = constructor.newInstance(subProb, params);
			queue.addIncomingMessagePolicy(module);
			queue.addOutgoingMessagePolicy(module);
			
			// Instantiate UTILpropagation
			queue.addIncomingMessagePolicy(new UTILpropagation<V, AddableBigInteger> (subProb));
			
			// Instantiate VALUEpropagation
			queue.addIncomingMessagePolicy(new VALUEpropagation<V> (subProb, false));
		}		
		
		// Tell all agents to start the protocol
		for (Queue queue : this.queues) 
			queue.sendMessageToSelf(new Message (AgentInterface.START_AGENT));

		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.nbrAgents == 0) {
					break;
				} else if (this.nbrAgents < 0) {
					fail("At least one variable sent more than two outputs");
				} else if (! this.finished.await(60, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		//Test all Obfuscated UTIL messages
		for (String var : receivedCN){		
			assertTrue("Variable "+var+" not obfuscated", cn.contains(var));
		}
		for (List<V> dom : receivedDom){
			assertTrue("Domain is not obfuscated: "+dom.toString(), vs.contains(dom));
		}
		
		myQueue.end();
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		parser = new XCSPparser<V, AddableBigInteger> (AllTests.generateProblem(graph, true));
		parser.setDomClass(this.domClass);
		parser.setUtilClass(AddableBigInteger.class);
		
		cns = new HashMap<String, String>();
		cn = new HashSet<String>();
		vss = new HashMap< String, List<V> >();
		vs = new HashSet< List<V> >();		
		receivedCN = new ArrayList<String>();
		receivedDom = new ArrayList< List<V> >();
	}
	
	/** Ends all queues 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		this.parser = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		pipes = null;
		
		cns = null;
		cn = null;
		vss = null;
		vs = null;
		receivedCN = null;
		receivedDom = null;
	}

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#notifyIn(frodo2.communication.Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {

		String msgType = msg.getType();
		
		if (msgType.equals(VariableObfuscation.CODE_NAME_TYPE)){
			
			CodeNameMsg<V> msgCast = (CodeNameMsg<V>) msg;
			
			String sender = msgCast.getSender();
			String codeName = msgCast.getCodeName();
			List<V> obfuscValues = Arrays.asList(msgCast.getOfuscatedDomain().clone());
			
			synchronized (lock) {
				if(mergeBack){
					//CodeName
					String code = cns.get(sender); //return null if this is the first msg received from sender
					if (code != null) assertTrue("CodeNames are different", code.equals(codeName));
					else {
						cns.put(sender, codeName);
						cn.add(codeName);
					}
					//Domain
					List<V> vals = vss.get(codeName); //return null if this is the first msg received from sender
					if(vals != null) assertTrue("Space values are different", vals.equals(obfuscValues));
					else {
						vss.put(codeName, obfuscValues);
						vs.add(obfuscValues);
					}
				} else {

					assertTrue("CodeName are identical", cn.add(codeName)); //if already contained in cn, return false
					assertTrue("Space values are identical", vs.add(obfuscValues)); //if already contained in vs, return false

					vss.put(codeName, obfuscValues);
				}
			}
			
		} else if (msgType.equals(VariableObfuscation.OBFUSCATED_VALUE_TYPE)){
			
			ObfsVALUEmsg<V> msgCast = (ObfsVALUEmsg<V>) msg;
			
			String[] vars = msgCast.getVariables();
			V[] vals = msgCast.getValues();
			
			synchronized (lock) {
				for (String var : vars){
					assertTrue("Var "+var+" not obfuscated", cn.contains(var));
				}

				ext: for (int i=0; i<vals.length; i++){
					for (V val : vss.get(vars[i])){

						if (val.equals(vals[i])) 
							break ext;
						//if val is found in vars, jump out of ext loop. Else, exit of inner loop and assertion fail.
					}

					fail("Value " + vals[i] + " not obfuscated");						
				}
			}
			
		} else if (msgType.equals(VariableObfuscation.OBFUSCATED_UTIL_TYPE)){

			synchronized (lock) {
				ObsfUTILmsg<V> msgCast = (ObsfUTILmsg<V>) msg;	
				for(String codeName : msgCast.getSpace().getVariables()){
					receivedCN.add(codeName);
				}
				for(V[] dom : msgCast.getSpace().getDomains()){
					receivedDom.add(Arrays.asList(dom.clone()));
				}
			}

			
		} else if (msgType.equals(AgentInterface.AGENT_FINISHED)) { 
			
			this.finished_lock.lock();
			if (--this.nbrAgents <= 0) 
				this.finished.signal();
			this.finished_lock.unlock();
		}
	}

	/**
	 * @see frodo2.communication.MessageListener#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList <String> (4);
		msgTypes.add(VariableObfuscation.CODE_NAME_TYPE);
		msgTypes.add(VariableObfuscation.OBFUSCATED_VALUE_TYPE);
		msgTypes.add(VariableObfuscation.OBFUSCATED_UTIL_TYPE);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		return msgTypes;
	}

	/**
	 * @see frodo2.communication.MessageListener#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		// no use	
	}
	

}
