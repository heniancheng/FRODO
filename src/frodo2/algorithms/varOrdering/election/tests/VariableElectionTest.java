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

package frodo2.algorithms.varOrdering.election.tests;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.Problem;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.heuristics.LeastConnectedHeuristic;
import frodo2.algorithms.heuristics.MostConnectedHeuristic;
import frodo2.algorithms.heuristics.ScorePair;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.ScoringHeuristicWithTiebreaker;
import frodo2.algorithms.heuristics.SmallestDomainHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.VariableElection;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID.MessageLEoutput;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** JUnit test for the class VariableElection
 * @author Thomas Leaute
 * @param <S> type used for scores
 * @bug Even if all unit tests pass, sometimes a SocketException is thrown. This is because, as soon as 
 * a variable receives a MaxIDmsg with an ID higher than its own, it already knows that it is not the leader, and
 * already sends an output message saying it is not the leader. But then it must keep processing MessageMaxIDs until 
 * the protocol finishes. Since VariableElectionTest only listens to output messages, it sometimes starts closing queues
 * while MessageMaxIDs are still in the process of being delivered. Solution to fix this: make VariableElectionTest also listen
 * to MessageMaxIDs, and properly compute how many of these messages it must wait for before it can close the queues. 
 */
public class VariableElectionTest < S extends Comparable<S> & Serializable > extends TestCase implements IncomingMsgPolicyInterface<String> {
	
	/** Maximum number of variables in the random graph 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 12;
	
	/** Maximum number of edges in the random graph */
	private final int maxNbrEdges = 30;

	/** Maximum number of agents */
	private final int maxNbrAgents = 4;

	/** For each variable, the output of VariableElection */
	protected Map< String, MessageLEoutput<S> > outputs;

	/** Current number of variables that have not yet sent their output of the leader election protocol */
	protected int remainingOutputs;

	/** Used to make the test thread wait */
	protected final ReentrantLock finished_lock = new ReentrantLock ();
	
	/** Used to wake up the test thread when all agents have finished */
	protected final Condition finished = finished_lock.newCondition();

	/** List of queues corresponding to the different agents */
	protected Queue[] queues;
	
	/** Random graph used to generate a constraint graph */
	protected RandGraphFactory.Graph graph;

	/** One output pipe used to send messages to each queue */
	protected QueueOutputPipeInterface[] pipes;

	/** \c true whether TCP pipes should be used instead of QueueIOPipes */
	protected boolean useTCP;

	/** Whether we should use the VariableElection constructor that takes in XML elements */
	private boolean useXML;

	/** The ScoringHeuristic */
	private Class< ? extends ScoringHeuristic<?> > heuristic;

	/** The tie-breaking heuristic */
	private Class< ? extends ScoringHeuristic<?> > tiebreaking;

	/** Constructor that instantiates a test only for the input method
	 * @param useTCP 		\c true whether TCP pipes should be used instead of QueueIOPipes
	 * @param useXML 		Whether we should use the VariableElection constructor that takes in XML elements
	 * @param heuristic 	The ScoringHeuristic
	 * @param tiebreaking 	The tie-breaking heuristic
	 */
	public VariableElectionTest(boolean useTCP, boolean useXML, Class< ? extends ScoringHeuristic<?> > heuristic, 
			Class< ? extends ScoringHeuristic<?> > tiebreaking) {
		super ("testRandom");
		this.useTCP = useTCP;
		this.useXML = useXML;
		this.heuristic = heuristic;
		this.tiebreaking = tiebreaking;
	}

	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for VariableElection");
		
		TestSuite testTmp = new TestSuite ("Tests for VariableElection using shared memory pipes");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (false, false, VarNameHeuristic.class, null), 1000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, VarNameHeuristic.class, null), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes and the Most Connected heuristic");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, MostConnectedHeuristic.class, null), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes and the Most Connected heuristic, breaking ties on domain sizes");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, MostConnectedHeuristic.class, SmallestDomainHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes and the Least Connected heuristic");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, LeastConnectedHeuristic.class, null), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes and the Least Connected heuristic, breaking ties on domain sizes");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, LeastConnectedHeuristic.class, SmallestDomainHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes and the Smallest Domain heuristic");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, SmallestDomainHeuristic.class, null), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes and the Smallest Domain heuristic, breaking ties using MostConnectedHeuristic");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, false, SmallestDomainHeuristic.class, MostConnectedHeuristic.class), 50));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using shared memory pipes with XML");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (false, true, VarNameHeuristic.class, null), 500));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VariableElection using TCP pipes with XML");
		testTmp.addTest(new RepeatedTest (new VariableElectionTest< ScorePair<?, ?> > (true, true, VarNameHeuristic.class, null), 50));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
	}
	
	/** Ends all queues 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		super.tearDown();
		graph = null;
		outputs = null;
		for (Queue queue : queues) {
			queue.end();
		}
		queues = null;
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
		pipes = null;
	}
	
	/** Tests the variable election protocol on a random graph 
	 * @throws IOException thrown if the method fails to create pipes
	 * @throws NoSuchMethodException thrown if the VariableElection class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InvocationTargetException thrown if the VariableElection constructor throws an exception
	 * @throws IllegalAccessException thrown if the VariableElection class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InstantiationException would be thrown if VariableElection were abstract
	 * @throws IllegalArgumentException if the VariableElection constructor does not take the proper arguments
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testRandom () 
	throws IOException, NoSuchMethodException, IllegalArgumentException, 
	InstantiationException, IllegalAccessException, InvocationTargetException {
		
		int nbrVars = graph.nodes.size();
		outputs = new HashMap< String, MessageLEoutput<S> > (nbrVars);
		int nbrAgents = graph.clusters.size();
		
		// Create the queue network
		queues = new Queue [nbrAgents];
		pipes = AllTests.createQueueNetwork(queues, graph, useTCP);
		
		// Compute the size of the biggest component
		int diameter = 0;
		for (List<String> component : graph.components) {
			diameter = Math.max(diameter, component.size());
		}
		
		Map<String, S> allScores = new HashMap<String, S> (nbrVars);

		// Create the listeners
		if (useXML) { // use the constructor that takes in an XML description of the problem
			
			
			// Create a random problem and go through its list of agents
			Document problem = AllTests.generateProblem(graph, graph.nodes.size(), true);
			XCSPparser<AddableInteger, AddableInteger> parser = new XCSPparser<AddableInteger, AddableInteger> (problem);
			
			//Initiate description of parameters and listener
			this.initiateParamAndListener(parser, heuristic, this.tiebreaking, diameter);
			
			// Create the unique IDs
			for (String var : graph.nodes) {
				allScores.put(var, (S) var);
			}
			
		} else { // use the constructor that does not take in an XML description of the problem
			
			// Create a random problem and go through its list of agents
			XCSPparser<AddableInteger, AddableInteger> parser = new XCSPparser<AddableInteger, AddableInteger> (AllTests.generateProblem(graph, graph.nodes.size(), true));

			for (int i = 0; i < graph.clusters.size(); i++) {
				Queue queue = queues[i];
				String agent = Integer.toString(i);
				
				// Create the map that associates to each variable the ID of its owner agent
				HashMap<String, String> owners = new HashMap<String, String> (graph.nodes.size());
				for (Map.Entry<String, Integer> entry : graph.clusterOf.entrySet()) 
					owners.put(entry.getKey(), entry.getValue().toString());
				
				// Create the map containing the domains
				HashMap<String, AddableInteger[]> domains = new HashMap<String, AddableInteger[]> ();
				for (String var : parser.getVariables()) 
					domains.put(var, parser.getDomain(var));
				
				// Extract the list of spaces from the problem
				XCSPparser<AddableInteger, AddableInteger> subproblem = parser.getSubProblem(agent);
				queue.setProblem(subproblem);
				List< ? extends UtilitySolutionSpace<AddableInteger, AddableInteger> > spaces = subproblem.getSolutionSpaces(false);
				
				// Create the subproblem
				Problem<AddableInteger, AddableInteger> subProb = new Problem<AddableInteger, AddableInteger> (agent, owners, domains, spaces);
				
				// Set up the tie-breaking heuristic
				ScoringHeuristic<?> tiebreaker;
				if (this.tiebreaking != null) 
					tiebreaker = this.tiebreaking.getConstructor(DCOPProblemInterface.class, Element.class).newInstance(subProb, null);
				else 
					tiebreaker = new VarNameHeuristic (subProb, null);
				Map< String, ? extends Comparable<?> > tiebreakingScores = tiebreaker.getScores();

				// Create the list of neighborhoods and variable unique IDs for this agent
				if (heuristic == VarNameHeuristic.class) { // default heuristic 
					for (String var : graph.clusters.get(i)) {
						allScores.put(var, (S) var);
					}
					queue.addIncomingMessagePolicy(new VariableElection <String> (subProb, 
							new VarNameHeuristic(subProb, (Element) null), diameter - 1));
				
				} else if (heuristic == MostConnectedHeuristic.class) { // Most Connected heuristic 
					for (String var : graph.clusters.get(i)) {
						allScores.put(var, (S) new ScorePair (new Short ((short) graph.neighborhoods.get(var).size()), tiebreakingScores.get(var)));
					}
					queue.addIncomingMessagePolicy(new VariableElection < ScorePair<Short, ?> > (subProb, 
							new ScoringHeuristicWithTiebreaker (new MostConnectedHeuristic (subProb, null), tiebreaker), 
							diameter - 1));
				
				} else if (heuristic == LeastConnectedHeuristic.class) { // Least Connected heuristic 
					for (String var : graph.clusters.get(i)) {
						allScores.put(var, (S) new ScorePair (new Short ((short) - graph.neighborhoods.get(var).size()), tiebreakingScores.get(var)));
					}
					queue.addIncomingMessagePolicy(new VariableElection < ScorePair<Short, ?> > (subProb, 
							new ScoringHeuristicWithTiebreaker (new LeastConnectedHeuristic (subProb, null), tiebreaker), 
							diameter - 1));
				
				} else if (heuristic == SmallestDomainHeuristic.class) { // Smallest Domain heuristic 
					for (String var : graph.clusters.get(i)) {
						allScores.put(var, (S) new ScorePair (new Short ((short) - parser.getDomainSize(var)), tiebreakingScores.get(var)));
					}
					queue.addIncomingMessagePolicy(new VariableElection < ScorePair<Short, ?> > (subProb, 
							new ScoringHeuristicWithTiebreaker (new SmallestDomainHeuristic (subProb, null), tiebreaker), 
							diameter - 1));

				} else 
					fail ("Unknown heuristic");

				queue.addIncomingMessagePolicy(this);			
			}
		}
		
		// Tell all listeners to start the protocol
		this.remainingOutputs = nbrVars; // output messages
		Message startMsg = new Message (AgentInterface.START_AGENT);
		for (Queue queue : queues) {
			queue.sendMessageToSelf(startMsg);
		}
		
		// Wait until all agents have sent their outputs
		while (true) {
			this.finished_lock.lock();
			try {
				if (this.remainingOutputs == 0) {
					break;
				} else if (this.remainingOutputs < 0) {
					fail("At least one agent sent more than one output");
				} else if (! this.finished.await(10, TimeUnit.SECONDS)) {
					fail("Timeout");
				}
			} catch (InterruptedException e) {
				break;
			}
			this.finished_lock.unlock();
		}
		
		this.checkOutputs(heuristic, allScores);
		
		// Properly close the pipes
		for (QueueOutputPipeInterface pipe : pipes) {
			pipe.close();
		}
	}
	
	/** Checks that the outputs of the module are consistent with the correct outputs for the given heuristic
	 * @param heuristic 	the heuristic used
	 * @param allUniqueIDs 	the unique IDs 
	 */
	protected void checkOutputs (Class<?> heuristic, Map<String, S> allUniqueIDs) {
		
		// Compute the correct leaders (one per connected component)
		Map<String, S> correctOutputs = null;
		if (heuristic == VarNameHeuristic.class
				|| heuristic == MostConnectedHeuristic.class
				|| heuristic == LeastConnectedHeuristic.class
				|| heuristic == SmallestDomainHeuristic.class) 
			correctOutputs = LeaderElectionMaxIDTest.computeLeaders (graph.nodes.size(), graph.components, allUniqueIDs);
		
		// Compare the outputs with the correct outputs
		assertEquals (correctOutputs.size(), outputs.size());
		for (Map.Entry< String, MessageLEoutput<S> > entry : outputs.entrySet()) {
			String var = entry.getKey();
			MessageLEoutput<S> msg = entry.getValue();
			S id = allUniqueIDs.get(var);
			S leader = correctOutputs.get(var);
			assertEquals (leader.equals(id), (boolean) msg.getFlag());
			assertEquals (leader, msg.getLeader());
		}
	}
	
	/**
	 * Initiate the parameter and the listener by reflection 
	 * @param parser 		the problem
	 * @param heuristic 	a ScoringHeuristic
	 * @param tiebreaking 	the tie-breaking ScoringHeuristic
	 * @param diameter 		the diameter of the problem
	 * @throws NoSuchMethodException thrown if the VariableElection class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InvocationTargetException thrown if the VariableElection constructor throws an exception
	 * @throws IllegalAccessException thrown if the VariableElection class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InstantiationException would be thrown if VariableElection were abstract
	 * @throws IllegalArgumentException if the VariableElection constructor does not take the proper arguments
	 */
	protected void initiateParamAndListener(XCSPparser<AddableInteger, AddableInteger> parser, 
			Class< ? extends ScoringHeuristic<?> > heuristic, Class< ? extends ScoringHeuristic<?> > tiebreaking, int diameter)
	throws NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		
		// Create a description of the parameters of VariableElection
		Element parameters = new Element ("module");
		parameters.setAttribute("nbrSteps", Integer.toString(diameter - 1));
		Element varElectionHeuristicElmt = new Element ("varElectionHeuristic");
		parameters.addContent(varElectionHeuristicElmt);
		if (heuristic == VarNameHeuristic.class) 
			varElectionHeuristicElmt.setAttribute("className", heuristic.getName());
		else {
			varElectionHeuristicElmt.setAttribute("className", ScoringHeuristicWithTiebreaker.class.getName());
			Element heuristicElmt = new Element ("heuristic1");
			varElectionHeuristicElmt.addContent(heuristicElmt);
			heuristicElmt.setAttribute("className", heuristic.getName());
			if (tiebreaking != null) {
				heuristicElmt = new Element ("heuristic2");
				varElectionHeuristicElmt.addContent(heuristicElmt);
				heuristicElmt.setAttribute("className", tiebreaking.getName());
			}
		}

		for (String agent : parser.getAgents()) {
			Queue queue = queues[Integer.parseInt(agent)];
			
			XCSPparser<AddableInteger, AddableInteger> subProb = parser.getSubProblem(agent);
			queue.setProblem(subProb);

			// Instantiate the listener using reflection
			Class<?> parTypes[] = new Class[2];
			parTypes[0] = DCOPProblemInterface.class;
			parTypes[1] = Element.class;
			Constructor<?> constructor = VariableElection.class.getConstructor(parTypes);
			Object[] args = new Object[2];
			args[0] = subProb;
			args[1] = parameters;
			queue.addIncomingMessagePolicy((VariableElection<?>) constructor.newInstance(args));

			queue.addIncomingMessagePolicy(this);
		}
	}
	
	/**
	 * @param constr a constructor of VariableElection type
	 * @param args the argument that will fit in public constructor of VariableElection: a ProblemInterface and a JDOM Element
	 * @return a new Instance of VariableElection
	 * @throws InvocationTargetException thrown if the VariableElection constructor throws an exception
	 * @throws IllegalAccessException thrown if the VariableElection class does not have a public constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InstantiationException would be thrown if VariableElection were abstract
	 * @throws IllegalArgumentException if the VariableElection constructor does not take the proper arguments
	 */
	protected VariableElection<?> getNewInstance(Constructor<?> constr, Object[] args)
	throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException{
		return (VariableElection<?>) constr.newInstance(args);
	}
	

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 * 
	 * It listens to the output of the leader election protocol. 
	 */
	public Collection <String> getMsgTypes() {
		ArrayList <String> types = new ArrayList <String> ();
		types.add(LeaderElectionMaxID.OUTPUT_MSG_TYPE);
		return types;
	}

	/** Keeps track of the output of the leader election protocol sent by each variable 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();

		if (msgType.equals(LeaderElectionMaxID.OUTPUT_MSG_TYPE)) {

			LeaderElectionMaxID.MessageLEoutput<S> msg2 = (LeaderElectionMaxID.MessageLEoutput<S>) msg;

			// Record whether the sender of this message decided it was the leader
			synchronized (outputs) {
				outputs.put(msg2.getSender(), msg2);
			}
		}
		
		// Increment the counter of the number of messages received
		this.finished_lock.lock();
		if (--this.remainingOutputs <= 0) 
			this.finished.signal();
		this.finished_lock.unlock();
	}

	/** Does nothing in this case 
	 * @param queue the queue */
	public void setQueue(Queue queue) { }

}
