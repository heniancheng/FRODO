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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.adopt.BoundsMsg;
import frodo2.algorithms.adopt.Preprocessing;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;

/** JUnit tests for the Preprocessing module
 * @author Brammert Ottens
 */
public class testPreprocessing extends TestCase {

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 5;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 10;

	/** Maximum number of agents */
	private final int maxNbrAgents = 5;
	
	/** The queue we are listening to*/
	private Queue[] queues;
	
	/** Used to make the test thread wait */
	private final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	private final Condition finished = finished_lock.newCondition();

	/** Counts the number of messages received*/
	private int counter;
	
	/** A list variables*/
	ArrayList<String> variables;
	
	/** The domain of all the variables*/
	HashMap<String, AddableInteger[]> domains;
	
	/** The random problem */
	private XCSPparser<AddableInteger, AddableInteger> parser;
	
	/** The DFS belonging to the problem */
	private Map< String, DFSview<AddableInteger, AddableInteger> > dfs;
	
	/** The constraint graph of the problem */
	private Graph graph;
	
	/** The proper h values, calculated centrally */
	private HashMap<String, UtilitySolutionSpace<AddableInteger, AddableInteger>> precalculated_h;
	
	/** Constructor that instantiates a test only for the input method
	 * @param method test method
	 */
	public testPreprocessing(String method) {
		super (method);
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for Preprocessing");
		
		TestSuite testTmp = new TestSuite ("Tests for Simple Heuristic");
		testTmp.addTest(new RepeatedTest (new testPreprocessing ("testSimpleHeuristic"), 20));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DP0 heuristic");
		testTmp.addTest(new RepeatedTest (new testPreprocessing ("testDP0Heuristic"), 1000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DP1 heuristic");
		testTmp.addTest(new RepeatedTest (new testPreprocessing ("testDP1Heuristic"), 1000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DP2 heuristic");
		testTmp.addTest(new RepeatedTest (new testPreprocessing ("testDP2Heuristic"), 1000));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		parser = new XCSPparser<AddableInteger, AddableInteger>(AllTests.generateProblem(graph, false));
		dfs = frodo2.algorithms.dpop.test.UTILpropagationTest.computeDFS(graph, parser);
		
//		System.out.println(parser.toDOT());
		variables = new ArrayList<String>(parser.getVariables());
		domains = new HashMap<String, AddableInteger[]>();
		for(String var : variables)
			domains.put(var, parser.getDomain(var));
		counter = 0;
		
		precalculated_h = new HashMap<String, UtilitySolutionSpace<AddableInteger, AddableInteger>>(variables.size());
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	protected void tearDown () throws Exception {
		super.tearDown();
		for(Queue q : queues) {
			q.end();
		}
		this.queues = null;
		precalculated_h = null;
		this.dfs = null;
		this.graph = null;
		this.parser = null;
		this.variables = null;
		this.domains = null;
	}
	
	/**
	 * Test for the simple heuristic that only returns zeros
	 * @author Brammert Ottens, 19 mei 2009
	 * @throws Exception 	if an error occurs
	 */
	public void testSimpleHeuristic() throws Exception {
		testRandom(Preprocessing.SimpleHeuristic.class.getName());
	}
	
	/**
	 * Test for the DP0 heuristic
	 * @author Brammert Ottens, 19 mei 2009
	 * @throws Exception 	if an error occurs
	 */
	public void testDP0Heuristic() throws Exception {
		testRandom(Preprocessing.DP0.class.getName());
	}
	
	/**
	 * Test for the DP1 heuristic
	 * @author Brammert Ottens, 20 mei 2009
	 * @throws Exception 	if an error occurs
	 */
	public void testDP1Heuristic() throws Exception {
		
		// precalculate the heuristics centrally
		for(String variable : dfs.keySet()) {
			if(dfs.get(variable).getParent() == null) {
				calculateDP1(variable, parser.getSolutionSpaces());
			}
		}
		testRandom(Preprocessing.DP1.class.getName());
	}
	
	/**
	 * Test the heuristics for DP2
	 * @author Brammert Ottens, 18 jun 2009
	 * @throws Exception 	if an error occurs
	 */
	public void testDP2Heuristic() throws Exception {
		
		// precalculate the heuristics centrally
		for(String variable : dfs.keySet()) {
			if(dfs.get(variable).getParent() == null) {
				calculateDP2(variable, parser.getSolutionSpaces());
			}
		}
		testRandom(Preprocessing.DP2.class.getName());
	}
	
	/**
	 * Tests the Preprocessing listener with \c heuristic
	 * @param heuristic 	the heuristic to use in the test
	 * @throws Exception 	if an error occurs
	 */
	private void testRandom(String heuristic) throws Exception {
		
		int nbrAgents = graph.clusters.size();
		
		// Create the queue network
		queues = new Queue [nbrAgents];
		AllTests.createQueueNetwork(queues, graph, false);

		// Listen for statistics messages
		Queue myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(new receiveLowerBounds(heuristic, parser, dfs));
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);
		
		// Create the XML parameters
		Element params = new Element ("module");
		params.setAttribute("heuristic", heuristic);
		
		for(String a: parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(a)];

			XCSPparser<AddableInteger, AddableInteger> subProb = parser.getSubProblem(a);
			queue.setProblem(subProb);
			Preprocessing<AddableInteger, AddableInteger> pre = new Preprocessing<AddableInteger, AddableInteger>(subProb, params);
			forwardHeuristicsMessage forwarder = new forwardHeuristicsMessage();
			queue.addIncomingMessagePolicy(pre);
			queue.addIncomingMessagePolicy(forwarder);
		}
		
		// for each variable, create a MessageDFSoutput
		for(String a: parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(a)];
			
			Set<String> variables = parser.getVariables(a);
			for(String var : variables) {
				MessageDFSoutput<AddableInteger, AddableInteger> msg = new MessageDFSoutput<AddableInteger, AddableInteger> (var, dfs.get(var));
				queue.sendMessageToSelf(msg);
			}
		}
		
		// Wait until all messages have been send
		while (true) {
			this.finished_lock.lock();
			try {
				if (counter == variables.size()) { 
					break;
				} else if (! this.finished.await(20, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		myQueue.end();
	}
	
	/**
	 * Centrally calculate the DP1 heuristics
	 * 
	 * @author Brammert Ottens, 20 mei 2009
	 * @param variable The variable for which the heuristics must be calculated
	 * @param spaces A list with all the constraints of the problem
	 * @return A UtilitySolutionSpace containing the heuristic values
	 */
	private UtilitySolutionSpace<AddableInteger, AddableInteger> calculateDP1(String variable, List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces) {
		// find the children of the variable
		List<String> children = dfs.get(variable).getChildren();
		UtilitySolutionSpace<AddableInteger, AddableInteger> h = null;
		
		if(children.size() == 0) {
			String[] vars = new String[1];
			vars[0] = variable;
			AddableInteger[][] domains = new AddableInteger[1][];
			domains[0] = parser.getDomain(variable);
			AddableInteger[] utilities = new AddableInteger[domains[0].length];
			
			for(int i = 0; i < utilities.length; i++) {
				utilities[i] = new AddableInteger(0);
			}
			
			h = new Hypercube<AddableInteger, AddableInteger>(vars, domains, utilities, AddableInteger.PlusInfinity.PLUS_INF);
		} else {
			for(String child : children) {
				UtilitySolutionSpace<AddableInteger, AddableInteger> childH = calculateDP1(child, spaces);
				List<String> childChildren = dfs.get(child).getChildren();
				List<String> pseudo = dfs.get(child).getAllPseudoChildren();
				
				UtilitySolutionSpace<AddableInteger, AddableInteger> localSpace = null;
				
				for (ListIterator< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > iterator = spaces.listIterator(); iterator.hasNext(); ) {
					UtilitySolutionSpace<AddableInteger, AddableInteger> space = iterator.next();
					
					// Disregard the space if it does not involve variable or its children
					List<String> constraint_variables = Arrays.asList(space.getVariables());
					if (! constraint_variables.contains(variable) || ! constraint_variables.contains(child)) 
						continue;

					// Disregard the space if it involves any another variable lower than var in the DFS
					boolean isLowestVar = true;
					for (String otherVar : space.getVariables()) {
						if (childChildren.contains(otherVar) || pseudo.contains(otherVar)) {
							isLowestVar = false;
							break;
						}
					}
					
					if(isLowestVar) {
						if(localSpace == null)
							localSpace = space;
						else
							localSpace = localSpace.join(space);
					}
				}

				if(localSpace == null) {
					String[] vars = new String[2];
					vars[0] = variable;
					vars[1] = child;
					AddableInteger[][] domains = new AddableInteger[2][];
					domains[0] = parser.getDomain(variable);
					domains[1] = parser.getDomain(child);
					AddableInteger[] utilities = new AddableInteger[domains[0].length * domains[1].length];

					for(int i = 0; i < utilities.length; i++) {
						utilities[i] = new AddableInteger(0);
					}

					localSpace = new Hypercube<AddableInteger, AddableInteger>(vars, domains, utilities, AddableInteger.PlusInfinity.PLUS_INF);
//				} else {
//					ArrayList<String> constraint_variables = new ArrayList<String>(Arrays.asList(localSpace.getVariables()));
//					constraint_variables.remove(variable);
//					constraint_variables.remove(child);
//					if(constraint_variables.size() > 0)
//						localSpace = localSpace.project(constraint_variables.toArray(new String[0]), false).getSpace();
				}
				
				ArrayList<String> constraint_variables = new ArrayList<String>(Arrays.asList(localSpace.getVariables()));
				constraint_variables.remove(variable);
				if(h == null) {
					h = childH.join(localSpace).blindProject(constraint_variables.toArray(new String[0]), false);
				}
				else
					h = h.join(childH.join(localSpace).blindProject(constraint_variables.toArray(new String[0]), false));
			}
		}
		
		h = h.resolve();
		precalculated_h.put(variable, h);
		
		return h;
	}
	
	/**
	 * Pre calculates the h-values belonging to DP2
	 * 
	 * @author Brammert Ottens, 18 jun 2009
	 * @param variable	The variable for whom to calculate the value
	 * @param spaces	The problem
	 * @return	the h-values for \c variable
	 */
	private UtilitySolutionSpace<AddableInteger, AddableInteger> calculateDP2(String variable, List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces) {
		// find the children of the variable
		List<String> children = dfs.get(variable).getChildren();
		UtilitySolutionSpace<AddableInteger, AddableInteger> h = null;
		
		if(children.size() == 0) {
			String[] vars = new String[1];
			vars[0] = variable;
			AddableInteger[][] domains = new AddableInteger[1][];
			domains[0] = parser.getDomain(variable);
			AddableInteger[] utilities = new AddableInteger[domains[0].length];
			
			for(int i = 0; i < utilities.length; i++) {
				utilities[i] = new AddableInteger(0);
			}
			
			h = new Hypercube<AddableInteger, AddableInteger>(vars, domains, utilities, AddableInteger.PlusInfinity.PLUS_INF);
		} else {
			for(String child : children) {
				UtilitySolutionSpace<AddableInteger, AddableInteger> childH = calculateDP2(child, spaces);
				assert(childH.getVariables().length == 1);
				List<String> childChildren = dfs.get(child).getChildren();
				List<String> pseudoChildren = dfs.get(child).getAllPseudoChildren();
				
				UtilitySolutionSpace<AddableInteger, AddableInteger> localSpace = null;
				UtilitySolutionSpace<AddableInteger, AddableInteger> c = null;
				
				for (ListIterator< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > iterator = spaces.listIterator(); iterator.hasNext(); ) {
					UtilitySolutionSpace<AddableInteger, AddableInteger> space = iterator.next();
					
					// Disregard the space if it does not involve the child or on of its ancestors
					ArrayList<String> constraint_variables = new ArrayList<String> (Arrays.asList(space.getVariables()));
					if( ! constraint_variables.contains(child) ) 
						continue;

					// Disregard the space if it involves any another variable lower than var in the DFS
					boolean isLowestVar = true;
					for (String otherVar : space.getVariables()) {
						if (childChildren.contains(otherVar) || pseudoChildren.contains(otherVar)) {
							isLowestVar = false;
							break;
						}
					}
					
					if(isLowestVar) {
						if(constraint_variables.contains(variable)) {
							constraint_variables.remove(variable);
							constraint_variables.remove(child);
							if(constraint_variables.size() == 0)
								c = space;
							else 
								c = space.blindProject(constraint_variables.toArray(new String[0]), false);
							assert c.getVariables().length == 2;
						} else {
							constraint_variables.remove(child);
							if(localSpace == null)
								localSpace = space.blindProject(constraint_variables.toArray(new String[0]), false);
							else
								localSpace = localSpace.join(space.blindProject(constraint_variables.toArray(new String[0]), false));
						}
					}
				}

				if(localSpace == null) {
					String[] vars = new String[1];
					vars[0] = child;
					AddableInteger[][] domains = new AddableInteger[1][];
					domains[0] = parser.getDomain(child);
					AddableInteger[] utilities = new AddableInteger[domains[0].length];

					for(int i = 0; i < utilities.length; i++) {
						utilities[i] = new AddableInteger(0);
					}

					localSpace = new Hypercube<AddableInteger, AddableInteger>(vars, domains, utilities, AddableInteger.PlusInfinity.PLUS_INF);
				}
				
				if(c == null)  {
					String[] vars = new String[1];
					vars[0] = variable;
					AddableInteger[][] domains = new AddableInteger[1][];
					domains[0] = parser.getDomain(variable);
					AddableInteger[] utilities = new AddableInteger[domains[0].length];

					for(int i = 0; i < utilities.length; i++) {
						utilities[i] = new AddableInteger(0);
					}

					c = new Hypercube<AddableInteger, AddableInteger>(vars, domains, utilities, AddableInteger.PlusInfinity.PLUS_INF);
				}
				
				if(h == null) {
					h = (childH.join(localSpace).join(c)).blindProject(child, false);
					assert(h.getVariables().length == 1);
				} else {
					h = h.join(childH.join(localSpace).join(c).blindProject(child, false));
					assert(h.getVariables().length == 1);
				}
			}
		}
		
		assert(h.getVariables().length == 1);
		
		h = h.resolve();
		precalculated_h.put(variable, h);
		
		return h;
	}
	
	/**
	 * 
	 * @author brammert
	 *
	 */
	private class receiveLowerBounds implements IncomingMsgPolicyInterface<String> {

		/** The heuristic who's output should be tested */
		String heuristic;
		
		/** The random problem */
		private XCSPparser<AddableInteger, AddableInteger> parser;
		
		/** The DFS belonging to the problem */
		private Map< String, DFSview<AddableInteger, AddableInteger> > dfs;

		/**
		 * Constructor
		 * @param heuristic 	The heuristic to be tested
		 * @param parser 		A representation of the global problem
		 * @param dfs 			The DFS structure used for this problem
		 */
		public receiveLowerBounds(String heuristic, XCSPparser<AddableInteger, AddableInteger> parser, 
				Map< String, DFSview<AddableInteger, AddableInteger> > dfs) {
			this.heuristic = heuristic;
			this.parser = parser;
			this.dfs = dfs;
		}
		
		/** 
		 * 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
		 */
		public Collection<String> getMsgTypes() {
			ArrayList <String> msgTypes = new ArrayList <String> (3);
			msgTypes.add(Preprocessing.HEURISTICS_MSG_TYPE);
			return msgTypes;
		}

		/**
		 * 
		 * @see IncomingMsgPolicyInterface#notifyIn(Message)
		 */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			if(msg.getType().equals(Preprocessing.HEURISTICS_MSG_TYPE)) {				
				BoundsMsg<AddableInteger, AddableInteger> castMsg = (BoundsMsg<AddableInteger, AddableInteger>)msg;
				
				String var = castMsg.getSender();
				UtilitySolutionSpace<AddableInteger, AddableInteger> lb = castMsg.getBounds();
				
				if(heuristic.equals(Preprocessing.SimpleHeuristic.class.getName())) {
					for(AddableInteger value : domains.get(var)) {
						AddableInteger[] vals = {value};
						assertEquals(new AddableInteger(0), lb.getUtility(vals));						
					}
				} else if(heuristic.equals(Preprocessing.DP0.class.getName())) {
					
					List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = parser.getSolutionSpaces();
					
					// for each child, find the local problem and determine
					// the sum of the minimal utilities
					List<String> children = dfs.get(var).getChildren();
					AddableInteger sum = new AddableInteger(0);

					for(String child : children) {
						DFSview<AddableInteger, AddableInteger> view = dfs.get(child);
						
						// set the lower neighbours
						List<String> childsChildren = view.getChildren();
						List<String> pseudo = view.getAllPseudoChildren();
						
						// set the constraints this variable is responsible for
						for (ListIterator< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > iterator = spaces.listIterator(); iterator.hasNext(); ) {
							UtilitySolutionSpace<AddableInteger, AddableInteger> space = iterator.next();
							
							// Disregard the space if it does not involve var
							if (! Arrays.asList(space.getVariables()).contains(child)) 
								continue;
							
							// Disregard the space if it involves any another variable lower than var in the DFS
							boolean isLowestVar = true;
							for (String otherVar : space.getVariables()) {
								if (childsChildren.contains(otherVar) || pseudo.contains(otherVar)) {
									isLowestVar = false;
									break;
								}
							}

							if (isLowestVar) { // record the space
								sum = sum.add(space.blindProjectAll(false));
							}
						}
					}
					
					for(AddableInteger value : domains.get(var)) {
						AddableInteger[] vals = {value};
						assertEquals(sum, lb.getUtility(vals));						
					}
				} else if(heuristic.equals(Preprocessing.DP1.class.getName())) {
					assertEquals(precalculated_h.get(var), lb);
				} else if(heuristic.equals(Preprocessing.DP2.class.getName())) {
					assertEquals(precalculated_h.get(var), lb);
				}
				
				finished_lock.lock();
				if (++counter >= variables.size()) 
					finished.signal();
				finished_lock.unlock();
			}
			
		}
		
		/**
		 * 
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) { }
		
	}
	
	/**
	 * This listener simply forwards all Heuristics messages to the central stats reporter
	 * @author Brammert Ottens, 19 mei 2009
	 *
	 */
	private class forwardHeuristicsMessage implements IncomingMsgPolicyInterface<String> {
	
		/** the queue */
		Queue queue;

		/**
		 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
		 */
		public Collection <String> getMsgTypes() {
			ArrayList<String> msgTypes = new ArrayList<String>(1);
			msgTypes.add(Preprocessing.HEURISTICS_MSG_TYPE);
			return msgTypes;
		}

		/** 
		 * @see IncomingMsgPolicyInterface#notifyIn(Message)
		 */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			if(msg.getType().equals(Preprocessing.HEURISTICS_MSG_TYPE)) {
				BoundsMsg<AddableInteger, AddableInteger> msgCast = (BoundsMsg<AddableInteger, AddableInteger>)msg;
				if(msgCast.getReceiver() == null)
					queue.sendMessage(AgentInterface.STATS_MONITOR, msg);
			}
		}
		
		/**
		 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) {
			this.queue = queue;
		}
	}

}
