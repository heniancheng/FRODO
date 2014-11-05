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

/** Unit tests for SynchBB */
package frodo2.algorithms.varOrdering.linear.tests;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.linear.CentralLinearOrdering;
import frodo2.algorithms.varOrdering.linear.OrderMsg;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Unit tests for CentralLinearOrdering
 * @author Thomas Leaute
 */
public class CentralLinearOrderingTest extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** @return the suite of tests */
	public static TestSuite suite () {
		
		TestSuite suite = new TestSuite ("Unit tests for CentralLinearOrdering using the max width-min domain size heuristic");
		suite.addTest(new RepeatedTest (new CentralLinearOrderingTest (), 1000));
		
		return suite;
	}

	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 15;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 100;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;

	/** The constraint graph for the overall problem */
	private Graph graph;

	/** The parser for the overall problem */
	private XCSPparser<AddableInteger, AddableInteger> parser;

	/** List of queues corresponding to the different agents */
	private Queue[] queues;
	
	/** One output pipe used to send messages to each queue */
	private QueueOutputPipeInterface[] pipes;

	/** The number of output messages we are still waiting for */
	private int remainingOutputs;
	
	/** The chosen (flat) order on variables */
	private List<String> order;
	
	/** The chosen (clustered) order on variables */
	private List< List<String> > clusteredOrder;
	
	/** The reported list of owners */
	private List<String> owners;

	/** Used to make the test thread wait */
	protected final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	protected final Condition finished = finished_lock.newCondition();

	/** Constructor from a test method */
	public CentralLinearOrderingTest() {
		super("test");
	}
	
	/** @see junit.framework.TestCase#setUp() */
	@Override
	protected void setUp () {
		this.graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		this.parser = new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, true), false, false, true);
	}
	
	/** @see junit.framework.TestCase#tearDown() */
	@Override
	protected void tearDown () throws Exception {
		this.graph = null;
		this.parser = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		pipes = null;
		this.order = null;
		this.clusteredOrder = null;
		this.owners = null;
	}
	
	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (1);
		types.add(OrderMsg.ORDER_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) { }

	/** Test method
	 * @throws IOException 					if an I/O error occurred when creating the pipes
	 * @throws NoSuchMethodException		if the module does not have a constructor with the proper signature
	 * @throws IllegalArgumentException		should never happen
	 * @throws InstantiationException 		if the module is an abstract class
	 * @throws IllegalAccessException 		if the module's constructor is not accessible
	 * @throws InvocationTargetException 	if the module's constructor throws an exception
	 */
	@SuppressWarnings("unchecked")
	public void test () throws IOException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		// Create the queue network
		int nbrAgents = graph.clusters.size();
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, false);
		
		// Make each agent connected to all other agents
		for (int i = 0; i < nbrAgents; i++) {
			Queue queue = queues[i];
			for (int j = 0; j < nbrAgents; j++) 
				queue.addOutputPipe(Integer.toString(j), pipes[j]);
		}
		
		// Listen for statistics messages
		Queue myQueue = new Queue (false);
		myQueue.addIncomingMessagePolicy(OrderMsg.STATS_MSG_TYPE, this);
		QueueIOPipe myPipe = new QueueIOPipe (myQueue);
		for (Queue queue : this.queues) 
			queue.addOutputPipe(AgentInterface.STATS_MONITOR, myPipe);

		// Create the modules
		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];
			
			XCSPparser<AddableInteger, AddableInteger> subProb = parser.getSubProblem(agent);
			queue.setProblem(subProb);
			
			Constructor<?> constructor = CentralLinearOrdering.MaxWidthMinDom.class.getConstructor(DCOPProblemInterface.class, Element.class);
			queue.addIncomingMessagePolicy((CentralLinearOrdering<AddableInteger, AddableInteger>) constructor.newInstance(subProb, null));
			queue.addIncomingMessagePolicy(this);			
		}
		
		// We should expect one stats message + one output message per non-empty agent
		this.remainingOutputs = 1;
		for (List<String> agent : graph.clusters) 
			if (! agent.isEmpty()) 
				this.remainingOutputs++;
		
		// Tell all listeners to start the protocol
		Message startMsg = new Message (AgentInterface.START_AGENT);
		for (Queue queue : queues) 
			queue.sendMessageToSelf(startMsg);
		
		// Wait until we have received all expected outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.remainingOutputs == 0) {
					break;
				} else if (this.remainingOutputs < 0) {
					fail("Too many outputs");
				} else if (! this.finished.await(2, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		// Properly close the pipes
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		myQueue.end();
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		@SuppressWarnings("unchecked")
		OrderMsg<AddableInteger, AddableInteger> msgCast = (OrderMsg<AddableInteger, AddableInteger>) msg;
		
		this.finished_lock.lock();
		
		// Check whether this is the first output message received
		if (this.clusteredOrder != null) {
			assertEquals(this.clusteredOrder, msgCast.getOrder());
			assertEquals(this.owners, msgCast.getAgents());
			
		} else { // first output received
			
			// Check the order
			this.order = msgCast.getFlatOrder();
			this.clusteredOrder = msgCast.getOrder();
			for (int i = 0; i < this.order.size(); i++) { // for each variable in the order
				
				// Check that no later variable should have been chosen instead
				int maxWidth = 0;
				int minDomSize = this.parser.getDomainSize(this.order.get(i));
				for (int j = i; j < this.order.size(); j++) {
					String var = this.order.get(j);
					
					// Compute the local width
					Collection<String> neighbors = this.parser.getNeighborVars(var);
					int width = 0;
					for (int k = 0; k < i; k++) 
						if (neighbors.contains(this.order.get(k))) 
							width++;
					
					// Check the correctness
					if (j == i) 
						maxWidth = width;
					else 
						assertTrue (width + " < " + maxWidth + " || (" + width + " == " + maxWidth + " && " + this.parser.getDomainSize(var) + " >= " + minDomSize + ")", 
								width < maxWidth || (width == maxWidth && this.parser.getDomainSize(var) >= minDomSize));
				}
			}
			
			// Check the owners
			this.owners = msgCast.getAgents();
			for (int i = 0; i < this.owners.size(); i++) 
				assertEquals(this.owners.get(i), this.parser.getOwner(this.order.get(i)));
		}
		
		if (--this.remainingOutputs <= 0) 
			this.finished.signal();
		
		this.finished_lock.unlock();
	}

}
