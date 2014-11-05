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

/** Tests for the classes in the algorithms package */
package frodo2.algorithms.test;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.RandGraphFactory.Edge;
import frodo2.algorithms.RandGraphFactory.Graph;
import frodo2.algorithms.adopt.test.AllTestsADOPT;
import frodo2.algorithms.afb.test.AllTestsAFB;
import frodo2.algorithms.asodpop.tests.AllTestsASODPOP;
import frodo2.algorithms.dpop.count.test.TestCountSolutions;
import frodo2.algorithms.dpop.memory.tests.MB_DPOPagentTest;
import frodo2.algorithms.dpop.param.test.AllTestsParamDPOP;
import frodo2.algorithms.dpop.privacy.test.AllTestsP_DPOP;
import frodo2.algorithms.dpop.restart.test.AllTestsS_DPOP;
import frodo2.algorithms.dpop.stochastic.test.AllTestsStochDPOP;
import frodo2.algorithms.dpop.test.AllTestsDPOP;
import frodo2.algorithms.localSearch.dsa.tests.AllTestsDSA;
import frodo2.algorithms.localSearch.mgm.mgm2.tests.MGM2agentTest;
import frodo2.algorithms.localSearch.mgm.tests.MGMagentTest;
import frodo2.algorithms.maxsum.tests.MaxSumTests;
import frodo2.algorithms.mpc_discsp.tests.MPC_DisWCSP4tests;
import frodo2.algorithms.odpop.tests.AllTestsODPOP;
import frodo2.algorithms.synchbb.test.AllTestsSynchBB;
import frodo2.algorithms.varOrdering.dfs.tests.AllTestsDFS;
import frodo2.algorithms.varOrdering.election.tests.AllTestsElection;
import frodo2.algorithms.varOrdering.linear.tests.CentralLinearOrderingTest;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.controller.Controller;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.hypercube.Hypercube;
import junit.framework.Test;
import junit.framework.TestSuite;

/** JUnit test suite for all the tests in frodo2.algorithms.dpop.test
 * @author Thomas Leaute
 */
public class AllTests {
	
	/** The default constraint tightness */
	public static final double DEFAULT_P2 = 0.3;

	/** @return The suite of unit tests */
	public static Test suite() {
		TestSuite suite = new TestSuite("All tests for all algorithms");

		suite.addTest(XCSPparserTest.suite());
		suite.addTest(ProblemTest.suite());
		suite.addTest(MASparserTest.suite());
		
		suite.addTest(AllTestsElection.suite());
		suite.addTest(AllTestsDFS.suite());
		suite.addTest(CentralLinearOrderingTest.suite());
		
		suite.addTest(AllTestsDPOP.suite());
		suite.addTest(AllTestsParamDPOP.suite());
		suite.addTest(AllTestsStochDPOP.suite());
		suite.addTest(AllTestsP_DPOP.suite());
		suite.addTest(AllTestsS_DPOP.suite());
		suite.addTest(MB_DPOPagentTest.suite());
		
		suite.addTest(AllTestsADOPT.suite());
		
		suite.addTest(AllTestsODPOP.suite());
		suite.addTest(AllTestsASODPOP.suite());
		
		suite.addTest(TestCountSolutions.suite());
		
		suite.addTest(AllTestsDSA.suite());
		suite.addTest(MGMagentTest.suite());
		suite.addTest(MGM2agentTest.suite());
		suite.addTest(MaxSumTests.suite());
		
		suite.addTest(AllTestsSynchBB.suite());
		
		suite.addTest(AllTestsAFB.suite());
		
		suite.addTest(MPC_DisWCSP4tests.suite());
		//$JUnit-END$
		return suite;
	}
	
	/** A convenience method that takes in a graph and creates all corresponding queues and pipes
	 * 
	 * For each cluster in the graph, a new queue is created. For each edge in the graph between two nodes \a node1 and \a node2, 
	 * a new pipe is created such that the queue corresponding to \a node1's cluster can send a message to the queue corresponding
	 * to \a node2's cluster using the cluster ID of \a node1 as recipient ID.
	 * 
	 * If the graph actually has no clusters, then one queue is created for each node. 
	 * 
	 * @param queues array of queues that is going to be populated with new Queue instances
	 * @param graph the constraint graph
	 * @param useTCP \c true if the queues should use TCP pipes to communicate with each other; \c false if they should use QueueIOPipes.
	 * @return An array of output pipes; the calling object is responsible for calling close() on each pipe to dispose of it.
	 * @throws IOException thrown when the method failed to create TCP pipes
	 */
	public static QueueOutputPipeInterface[] createQueueNetwork (Queue[] queues, RandGraphFactory.Graph graph, boolean useTCP) 
	throws IOException {
		
		int nbrQueues;
		if (graph.clusters != null) { // one queue per cluster 
			nbrQueues = graph.clusters.size();
		} else { // no clusters; one queue per node
			nbrQueues = graph.nodes.size();
		}

		// Create the queues
		for (int i = 0; i < nbrQueues; i++) {
			Queue queue = new Queue (false);
			queues[i] = queue;
		}
		
		// Associate an index to each node in the graph
		HashMap<String, Integer> nodeIndexes = new HashMap<String, Integer> (graph.nodes.size());
		for (int i = 0; i < graph.nodes.size(); i++) 
			nodeIndexes.put(graph.nodes.get(i), i);

		// Create the pipes to send messages to the queues
		QueueOutputPipeInterface[] pipes = new QueueOutputPipeInterface[nbrQueues];
		QueueIOPipe[] selfOutputs = new QueueIOPipe[nbrQueues];
		if (useTCP) {
			for (int i = 0; i < queues.length; i++) {
				int port = 5000 + i;
				Queue queue_i = queues[i];
				Controller.PipeFactoryInstance.inputPipe(queue_i, Controller.PipeFactoryInstance.getSelfAddress(port), 1);
				pipes[i] = Controller.PipeFactoryInstance.outputPipe(Controller.PipeFactoryInstance.getSelfAddress(port));
				selfOutputs[i] = new QueueIOPipe (queue_i);
			}
		} else { // use QueueIOPipes
			for (int i = 0; i < queues.length; i++) {
				QueueIOPipe pipe = new QueueIOPipe (queues[i]);
				pipes[i] = pipe;
				selfOutputs[i] = pipe;
			}
		}

		// Generate the pipes
		if (graph.clusters != null) { // one queue per cluster 
			for (RandGraphFactory.Edge edge : graph.edges) {
				
				int cluster1 = graph.clusterOf.get(edge.source);
				int cluster2 = graph.clusterOf.get(edge.dest);
				String cluster1Str = Integer.toString(cluster1);
				
				if (cluster1 == cluster2) { // same agent; don't use the TCP pipe
					Queue queue = queues[cluster1];
					if (queue.getOutputPipe(cluster1Str) == null) // the queue doesn't have a self output yet
						queue.addOutputPipe(cluster1Str, selfOutputs[cluster1]);
					
				} else { // different agents 
					Queue queue1 = queues[cluster1];
					String cluster2Str = Integer.toString(cluster2);
					
					if (queue1.getOutputPipe(cluster2Str) == null) { // no pipe between the two agents yet
						queue1.addOutputPipe(cluster2Str, pipes[cluster2]);
						queues[cluster2].addOutputPipe(cluster1Str, pipes[cluster1]);
					}
				}
			}
		} else { // no clusters; one queue per node
			for (RandGraphFactory.Edge edge : graph.edges) {
				queues[nodeIndexes.get(edge.source)].addOutputPipe(edge.dest, pipes[nodeIndexes.get(edge.dest)]);
				queues[nodeIndexes.get(edge.dest)].addOutputPipe(edge.source, pipes[nodeIndexes.get(edge.source)]);
			}
		}
		
		return pipes;
	}

	/** Creates a random problem
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * @param maxNbrVars	maximum number of variables
	 * @param maxNbrEdges	maximum number of edges
	 * @param maxNbrAgents 	maximum number of agents
	 * @param maximize  	when true generate a maximization problem, when false a minimization problem
	 * @return a JDOM Document describing the problem
	 */
	public static Document createRandProblem (int maxNbrVars, int maxNbrEdges, int maxNbrAgents, boolean maximize) {
		return createRandProblem (maxNbrVars, maxNbrEdges, maxNbrAgents, maximize, 0);
	}
	
	/** Creates a random problem
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * @param maxNbrVars	maximum number of variables
	 * @param maxNbrEdges	maximum number of edges
	 * @param maxNbrAgents 	maximum number of agents
	 * @param maximize  	when true generate a maximization problem, when false a minimization problem
	 * @param p2 			the constraint tightness
	 * @return a JDOM Document describing the problem
	 */
	public static Document createRandProblem (int maxNbrVars, int maxNbrEdges, int maxNbrAgents, boolean maximize, double p2) {
		return createRandProblem (maxNbrVars, maxNbrEdges, maxNbrAgents, maximize, 0, p2);
	}
	
	/** Creates a random problem
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * @param maxNbrVars	maximum number of variables
	 * @param maxNbrEdges	maximum number of edges
	 * @param maxNbrAgents 	maximum number of agents
	 * @param maximize  	when true generate a maximization problem, when false a minimization problem
	 * @param sign 			the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param p2 			the constraint tightness
	 * @return a JDOM Document describing the problem
	 */
	public static Document createRandProblem (int maxNbrVars, int maxNbrEdges, int maxNbrAgents, boolean maximize, int sign, double p2) {
		return generateProblem (RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents), maximize, sign, false, p2);
	}
	
	/** Creates a random problem
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * @param maxNbrVars	maximum number of variables
	 * @param maxNbrEdges	maximum number of edges
	 * @param maxNbrAgents 	maximum number of agents
	 * @param maximize  	when true generate a maximization problem, when false a minimization problem
	 * @param sign 			the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param amplitude 	the amplitude of the utility values
	 * @return a JDOM Document describing the problem
	 */
	public static Document createRandProblem (int maxNbrVars, int maxNbrEdges, int maxNbrAgents, boolean maximize, int sign, int amplitude) {
		return generateProblem (RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents), maximize, sign, false, amplitude);
	}
	
	/** Creates a random problem
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * @param maxNbrVars	maximum number of variables
	 * @param maxNbrEdges	maximum number of edges
	 * @param maxNbrAgents 	maximum number of agents
	 * @param maximize  	when true generate a maximization problem, when false a minimization problem
	 * @param sign 			the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param binary		when \c true, the generated problem should have variables with binary domains
	 * @return a JDOM Document describing the problem
	 */
	public static Document createRandProblem (int maxNbrVars, int maxNbrEdges, int maxNbrAgents, boolean maximize, int sign, boolean binary) {
		return generateProblem (RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents), maximize, sign, binary);
	}
	
	
	/** Creates a random problem with random variables
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * Furthermore, variables can be of type "random" and the new concept of "probabilities" is introduced, 
	 * in which a probability is the same as a relation, except that any constraint based on it will not be 
	 * a utility solution space, but rather a probability space. 
	 * 
	 * @param maxNbrVars 		maximum number of variables
	 * @param maxNbrEdges 		maximum number of edges
	 * @param maxNbrAgents 		maximum number of agents
	 * @param maxNbrRandVars 	maximum number of random variables
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @return a JDOM Document describing the problem
	 */
	public static Document createRandProblem (int maxNbrVars, int maxNbrEdges, int maxNbrAgents, int maxNbrRandVars, boolean maximize) {
		return generateProblem (RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents), maxNbrRandVars, maximize);
	}
	
	/** Creates a random problem with random variables
	 * 
	 * The output document is in XCSP format, with an additional attribute \a owner 
	 * for each variable specifying the name of the agent that owns the variable.
	 * 
	 * Furthermore, variables can be of type "random" and the new concept of "probabilities" is introduced, 
	 * in which a probability is the same as a relation, except that any constraint based on it will not be 
	 * a utility solution space, but rather a probability space. 
	 * 
	 * @param nbrVars 		number of variables
	 * @param nbrEdges 		number of edges
	 * @param nbrAgents 	number of agents
	 * @param nbrRandVars 	number of random variables
	 * @param maximize 		when true generate a maximization problem, when false a minimization problem
	 * @return a JDOM Document describing the problem
	 */
	public static Document createSizedRandProblem (int nbrVars, int nbrEdges, int nbrAgents, int nbrRandVars, boolean maximize) {
		return generateSizedProblem (RandGraphFactory.getSizedRandGraph(nbrVars, nbrEdges, nbrAgents), nbrRandVars, maximize);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param maxNbrRandVars 	maximum number of random variables to be added to the graph
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @return 					a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, int maxNbrRandVars, boolean maximize) {
		return generateProblem(graph, maxNbrRandVars, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), new ArrayList< Hypercube<AddableInteger, AddableReal> > (), maximize);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param maxNbrRandVars 	maximum number of random variables to be added to the graph
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @param sign 				the desired sign for the utilities (if 0, utilities can be either sign)
	 * @return 					a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, int maxNbrRandVars, boolean maximize, int sign) {
		return generateProblem(graph, maxNbrRandVars, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), new ArrayList< Hypercube<AddableInteger, AddableReal> > (), maximize, sign);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 		a constraint graph
	 * @param nbrRandVars 	number of random variables to be added to the graph
	 * @param maximize 		when true generate a maximization problem, when false a minimization problem
	 * @return 				a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateSizedProblem (Graph graph, int nbrRandVars, boolean maximize) {
		return generateSizedProblem(graph, nbrRandVars, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), new ArrayList< Hypercube<AddableInteger, AddableReal> > (), maximize);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param maxNbrRandVars 	maximum number of random variables to be added to the graph
	 * @param solutionSpaces 	the list to which the randomly generated solution spaces will be added
	 * @param probSpaces 		the list to which the randomly generated probability spaces will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, int maxNbrRandVars, 
			List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, List< Hypercube<AddableInteger, AddableReal> > probSpaces, boolean maximize) {
		return generateProblem (graph, maxNbrRandVars, solutionSpaces, probSpaces, maximize, 0);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param maxNbrRandVars 	maximum number of random variables to be added to the graph
	 * @param solutionSpaces 	the list to which the randomly generated solution spaces will be added
	 * @param probSpaces 		the list to which the randomly generated probability spaces will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @param sign 				the desired sign for the utilities (if 0, utilities can be either sign)
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, int maxNbrRandVars, 
			List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, List< Hypercube<AddableInteger, AddableReal> > probSpaces, boolean maximize, int sign) {

		// Choose the number of random variables
		int nbrRandVars = (int) (Math.random() * (maxNbrRandVars+1));
		
		return generateSizedProblem (graph, nbrRandVars, solutionSpaces, probSpaces, maximize, sign);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param nbrRandVars	 	number of random variables to be added to the graph
	 * @param solutionSpaces 	the list to which the randomly generated solution spaces will be added
	 * @param probSpaces 		the list to which the randomly generated probability spaces will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateSizedProblem (Graph graph, int nbrRandVars, 
			List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, List< Hypercube<AddableInteger, AddableReal> > probSpaces, boolean maximize) {
		return generateSizedProblem (graph, nbrRandVars, solutionSpaces, probSpaces, maximize, 0);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param nbrRandVars	 	number of random variables to be added to the graph
	 * @param solutionSpaces 	the list to which the randomly generated solution spaces will be added
	 * @param probSpaces 		the list to which the randomly generated probability spaces will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @param sign 				the desired sign for the utilities (if 0, utilities can be either sign)
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, int, boolean)
	 */
	public static Document generateSizedProblem (Graph graph, int nbrRandVars, 
			List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, List< Hypercube<AddableInteger, AddableReal> > probSpaces, boolean maximize, int sign) {
		
		Document problem = generateProblem(graph, solutionSpaces, maximize, sign);
		
		if (nbrRandVars == 0) 
			return problem;

		// Get the "variables" element
		Element varsElmt = problem.getRootElement().getChild("variables");

		// Update the number of variables
		varsElmt.setAttribute("nbVariables", String.valueOf(Integer.parseInt(varsElmt.getAttributeValue("nbVariables")) + nbrRandVars));

		// Create the "probabilities" element
		Element probsElmt = new Element ("probabilities");
		problem.getRootElement().addContent(probsElmt);
		probsElmt.setAttribute("nbProbabilities", String.valueOf(nbrRandVars));

		// Get the "constraints" element
		Element constsElmt = problem.getRootElement().getChild("constraints");
		int nbrNewConst = 0;

		// Get the "relations" element
		Element relsElmt = problem.getRootElement().getChild("relations");
		int relID = relsElmt.getContentSize();

		// Create each new random variable
		for (int i = graph.nodes.size(); i < graph.nodes.size() + nbrRandVars; i++) {
			String varName = "r" + i;

			// Create the "variable" element
			Element varElmt = new Element ("variable");
			varsElmt.addContent(varElmt);
			varElmt.setAttribute("name", varName);
			varElmt.setAttribute("domain", "D");
			varElmt.setAttribute("type", "random");

			// Create the "probability" element
			Hypercube<AddableInteger, AddableReal> probSpace = randProbSpace(Arrays.asList(new String[] {varName}));
			probSpaces.add(probSpace);
			probsElmt.addContent(XCSPparser.getRelation(probSpace, "p_" + varName, "probability"));

			// Create the "constraint" element for the probability space
			constsElmt.addContent(XCSPparser.getConstraint(probSpace, "c_" + varName, "p_" + varName));

			// Add constraints between this random variable and some randomly chosen, non-random variables
			ArrayList<String> allVars = new ArrayList<String> ();
			allVars.addAll(graph.nodes);
			int nbrConst = (int) (Math.random() * graph.nodes.size() / 2) + 1;
			for (int j = 0; j < nbrConst && !allVars.isEmpty(); j++) {

				nbrNewConst++;

				// Choose a non-random variable to create the constraint with
				String var = allVars.remove((int) (Math.random() * allVars.size()));
				List<String> scope = new ArrayList<String> (Arrays.asList(varName, var));
				
				// With some probability, also involve the previous random variable in the same constraint
				if (i > graph.nodes.size() && Math.random() < 0.2) 
					scope.add("r" + (i-1));

				// Create a random constraint
				Hypercube<AddableInteger, AddableInteger> constraint = randHypercube(scope, maximize, sign);
				constraint.setName("c_" + varName + "_" + "r_" + relID);
				solutionSpaces.add(constraint);

				// Create the "relation" element
				relsElmt.addContent(XCSPparser.getRelation(constraint, "r_" + String.valueOf(relID), "relation"));

				// Create the "constraint" element
				constsElmt.addContent(XCSPparser.getConstraint(constraint, "c_" + varName + "_" + "r_" + relID, "r_" + String.valueOf(relID)));

				relID++;
			}
		}

		// Update the number of constraints (solution spaces and probability spaces)
		constsElmt.setAttribute("nbConstraints", String.valueOf(Integer.parseInt(constsElmt.getAttributeValue("nbConstraints")) + nbrNewConst + nbrRandVars));

		// Update the number of relations
		relsElmt.setAttribute("nbRelations", String.valueOf(Integer.parseInt(relsElmt.getAttributeValue("nbRelations")) + nbrNewConst));

		return problem;
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph a constraint graph
	 * @param maximize 	when true generate a maximization problem, when false a minimization problem
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, boolean maximize) {
		return generateProblem (graph, maximize, 0);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 	a constraint graph
	 * @param maximize 	when true generate a maximization problem, when false a minimization problem
	 * @param sign 		the desired sign for the utilities (if 0, utilities can be either sign)
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, boolean maximize, int sign) {
		return generateProblem (graph, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), maximize, sign, false, 100, DEFAULT_P2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 	a constraint graph
	 * @param maximize 	when true generate a maximization problem, when false a minimization problem
	 * @param sign 		the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param binary	when \c true, the generated problem should have variables with binary domains
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, boolean maximize, int sign, boolean binary) {
		return generateProblem (graph, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), maximize, sign, binary, 100, DEFAULT_P2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 	a constraint graph
	 * @param maximize 	when true generate a maximization problem, when false a minimization problem
	 * @param sign 		the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param binary	when \c true, the generated problem should have variables with binary domains
	 * @param p2 		the constraint tightness
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, boolean maximize, int sign, boolean binary, double p2) {
		return generateProblem (graph, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), maximize, sign, binary, 100, p2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 		a constraint graph
	 * @param maximize 		when true generate a maximization problem, when false a minimization problem
	 * @param sign 			the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param binary		when \c true, the generated problem should have variables with binary domains
	 * @param amplitude 	the amplitude of the utility values
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, boolean maximize, int sign, boolean binary, int amplitude) {
		return generateProblem (graph, new ArrayList< Hypercube<AddableInteger, AddableInteger> > (), maximize, sign, binary, amplitude, DEFAULT_P2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param solutionSpaces 	list to which the randomly generated hypercubes will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, boolean maximize) {
		return generateProblem (graph, solutionSpaces, maximize, 0, false, 100, DEFAULT_P2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param solutionSpaces 	list to which the randomly generated hypercubes will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @param sign 				the desired sign for the utilities (if 0, utilities can be either sign)
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, boolean maximize, int sign) {
		return generateProblem (graph, solutionSpaces, maximize, sign, false, 100, DEFAULT_P2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param solutionSpaces 	list to which the randomly generated hypercubes will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @param binary		when \c true, the generated problem should have variables with binary domains
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, boolean maximize, boolean binary) {
		return generateProblem (graph, solutionSpaces, maximize, 0, binary, 100, DEFAULT_P2);
	}
	
	/** Creates a problem description based on the input constraint graph
	 * @param graph 			a constraint graph
	 * @param solutionSpaces 	list to which the randomly generated hypercubes will be added
	 * @param maximize 			when true generate a maximization problem, when false a minimization problem
	 * @param sign 				the desired sign for the utilities (if 0, utilities can be either sign)
	 * @param binary			when \c true, the generated problem should have variables with binary domains
	 * @param amplitude 		the amplitude of the utility values
	 * @param p2 				the constraint tightness (i.e. probability of infeasibility)
	 * @return a problem description based on the input graph
	 * @see AllTests#createRandProblem(int, int, int, boolean)
	 */
	public static Document generateProblem (Graph graph, List< Hypercube<AddableInteger, AddableInteger> > solutionSpaces, boolean maximize, int sign, boolean binary, int amplitude, double p2) {
		
		// Create the root element
		Element probElement = new Element ("instance");
		probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschema.xsd", 
				Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));

		// Create the "presentation" element
		Element elmt = new Element ("presentation");
		probElement.addContent(elmt);
		elmt.setAttribute("name", "randomProblem");
		elmt.setAttribute("maxConstraintArity", "2");
		elmt.setAttribute("maximize", Boolean.toString(maximize));
		elmt.setAttribute("format", "XCSP 2.1_FRODO");

		// Create the "agents" element
		elmt = new Element ("agents");
		probElement.addContent(elmt);
		elmt.setAttribute("nbAgents", Integer.toString(graph.clusters.size()));
		for (int agent = 0; agent < graph.clusters.size(); agent++) {
			Element subElmt = new Element ("agent");
			elmt.addContent(subElmt);
			subElmt.setAttribute("name", Integer.toString(agent));
		}

		// Create the "domains" element
		// The random hypercubes use only use the domain {1, 2, 3}
		elmt = new Element ("domains");
		probElement.addContent(elmt);
		elmt.setAttribute("nbDomains", "1");
		Element subElmt = new Element ("domain");
		elmt.addContent(subElmt);
		subElmt.setAttribute("name", "D");
		if(binary) {
			subElmt.setAttribute("nbValues", "2");
			subElmt.addContent("1..2");
		} else {
			subElmt.setAttribute("nbValues", "3");
			subElmt.addContent("1..3");
		}
		

		// Create the "variables" element
		Element varsElement = new Element ("variables");
		probElement.addContent(varsElement);
		varsElement.setAttribute("nbVariables", Integer.toString(graph.nodes.size()));

		// Take care of the variables
		for (String varID : graph.nodes) {
			elmt = new Element ("variable");
			varsElement.addContent(elmt);
			elmt.setAttribute("name", varID);
			elmt.setAttribute("domain", "D");
			elmt.setAttribute("agent", Integer.toString(graph.clusterOf.get(varID)));
		}

		// Create the "relations" and "constraints" elements
		Element relElement = new Element ("relations");
		probElement.addContent(relElement);
		int relationID = 0;
		Element conElement = new Element ("constraints");
		probElement.addContent(conElement);
		int constraintID = 0;

		// Create unary constraints
		for (Map.Entry< String, Set<String> > neighborhood : graph.neighborhoods.entrySet()) {
			
			// With a small probability, leave this variable unconstrained
			if (Math.random() < 0.2) 
				continue;
			
			String varID = neighborhood.getKey();
			
			// Generate a random hypercube
			ArrayList<String> vars = new ArrayList<String> (1);
			vars.add(varID);
			Hypercube<AddableInteger, AddableInteger> hypercube = randHypercube(vars, maximize, sign, binary, amplitude, p2);
			hypercube.setName("c_" + Integer.toString(constraintID));
			solutionSpaces.add(hypercube);
			
			// Create the "relation" element
			relElement.addContent(XCSPparser.getRelation(hypercube, "r_" + String.valueOf(relationID), "relation"));
			
			// Create the "constraint" element
			conElement.addContent(XCSPparser.getConstraint(hypercube, "c_" + Integer.toString(constraintID++), "r_" + Integer.toString(relationID++)));
		}
		
		// Group edges together to form n-ary constraints
		LinkedList<Edge> allEdges = new LinkedList<Edge> (Arrays.asList(graph.edges));
		while (! allEdges.isEmpty()) {
			
			ArrayList<String> vars = new ArrayList<String> ();
			
			// Take the first remaining edge to form a temporary binary constraint
			Edge edge = allEdges.removeFirst();
			vars.add(edge.source);
			vars.add(edge.dest);
			
			// Augment the binary constraint to form an n-ary constraint with some probability and if possible
			// Look for another variable that is the neighbor of all selected variables
			for (Iterator<String> varIter = graph.nodes.iterator(); varIter.hasNext() && Math.random() < .2; ) {
				String var = varIter.next();

				// Skip this variable if it has already been selected
				if (vars.contains(var)) 
					continue;

				// Skip this variable if it is not a neighbor of all selected variables
				boolean skip = false;
				for (String var2 : vars) {
					if (! graph.neighborhoods.get(var2).contains(var)) {
						skip = true;
						break;
					}
				}
				if (skip) 
					continue;

				// Select this variable to augment the constraint
				vars.add(var);

				// Remove from allEdges the edges that are now covered by this extended constraint
				for (Iterator<Edge> edgeIter = allEdges.iterator(); edgeIter.hasNext(); ) {
					edge = edgeIter.next();
					
					// Remove this edge if both source and destination have been selected
					if (vars.contains(edge.source) && vars.contains(edge.dest)) 
						edgeIter.remove();
				}
			}

			// Generate random hypercube
			Hypercube< AddableInteger, AddableInteger > hypercube = randHypercube(vars, maximize, sign, binary, amplitude, p2);
			hypercube.setName("c_" + Integer.toString(constraintID));
			solutionSpaces.add(hypercube);

			// Create the "relation" element
			relElement.addContent(XCSPparser.getRelation(hypercube, "r_" + String.valueOf(relationID), "relation"));

			// Create the "constraint" element
			conElement.addContent(XCSPparser.getConstraint(hypercube, "c_" + Integer.toString(constraintID++), "r_" + Integer.toString(relationID++)));
		}

		// Set the number of relations and constraints
		relElement.setAttribute("nbRelations", Integer.toString(relationID));
		conElement.setAttribute("nbConstraints", Integer.toString(constraintID));

		return new Document (probElement);
	}
	
	/** Generates a random hypercube
	 * 
	 * All domains are {1, 2, 3} and utility values are random integers between -50 and +50, with some probability of being infinite. 
	 * @param variables 	list of variables
	 * @param maximize 		if \c true, return a maximization problem
	 * @return a random hypercube
	 */
	public static Hypercube<AddableInteger, AddableInteger> randHypercube (List<String> variables, boolean maximize) {
		return randHypercube (variables, maximize, 0, false, 100, DEFAULT_P2);
	}
	
	/** Generates a random hypercube
	 * 
	 * All domains are {1, 2, 3} and utility values are random integers between -50 and +50, with some probability of being infinite. 
	 * @param variables 	list of variables
	 * @param maximize 		if \c true, return a maximization problem
	 * @param binary		when \c true, the generated problem should have variables with binary domains
	 * @return a random hypercube
	 */
	public static Hypercube<AddableInteger, AddableInteger> randHypercube (List<String> variables, boolean maximize, boolean binary) {
		return randHypercube (variables, maximize, 0, binary, 100, DEFAULT_P2);
	}
	
	/** Generates a random hypercube
	 * 
	 * All domains are {1, 2, 3} and utility values are random integers 
	 * 	- between -50 and +50, if \a sign == 0
	 * 	- between -100 and 0 if \a sign < 0
	 * 	- between 0 and +100 if \a sign > 0
	 * with some probability of being infinite. 
	 * 
	 * @param variables 	list of variables
	 * @param maximize 		if \c true, return a maximization problem
	 * @param sign 			the desired sign for utilities (if 0, utilities can take both signs)
	 * @return a random hypercube
	 */
	public static Hypercube<AddableInteger, AddableInteger> randHypercube (List<String> variables, boolean maximize, final int sign) {
		return randHypercube (variables, maximize, sign, false, 100, DEFAULT_P2);
	}
	
	/** Generates a random hypercube
	 * 
	 * All domains are {1, 2, 3} and utility values are random integers 
	 * 	- between -amplitude/2 and +amplitude/2, if \a sign == 0
	 * 	- between -amplitude and 0 if \a sign < 0
	 * 	- between 0 and +amplitude if \a sign > 0
	 * with some probability of being infinite. 
	 * 
	 * @param variables 	list of variables
	 * @param maximize 		if \c true, return a maximization problem
	 * @param sign 			the desired sign for utilities (if 0, utilities can take both signs)
	 * @param amplitude 	the amplitude of the utility values
	 * @return a random hypercube
	 */
	public static Hypercube<AddableInteger, AddableInteger> randHypercube (List<String> variables, boolean maximize, final int sign, int amplitude) {
		return randHypercube (variables, maximize, sign, false, amplitude, DEFAULT_P2);
	}
	
	/** Generates a random hypercube
	 * 
	 * All domains are {1, 2, 3} and utility values are random integers 
	 * 	- between -amplitude/2 and +amplitude/2, if \a sign == 0
	 * 	- between -amplitude and 0 if \a sign < 0
	 * 	- between 0 and +amplitude if \a sign > 0
	 * with some probability of being infinite. 
	 * 
	 * @param variables 	list of variables
	 * @param maximize 		if \c true, return a maximization problem
	 * @param sign 			the desired sign for utilities (if 0, utilities can take both signs)
	 * @param binary		when \c true, the generated problem should have variables with binary domains
	 * @param amplitude 	the amplitude of the utility values
	 * @param p2 			tightness (i.e. probability of infeasibility)
	 * @return a random hypercube
	 */
	public static Hypercube<AddableInteger, AddableInteger> randHypercube (List<String> variables, boolean maximize, final int sign, boolean binary, int amplitude, double p2) {
		
		amplitude = Math.abs(amplitude);
		
		// Create the domains
		AddableInteger[][] domains = new AddableInteger[variables.size()][];
		AddableInteger[] domain = null;
		domain =  binary ? new AddableInteger[2] : new AddableInteger[3];
		domain[0] = new AddableInteger (1); 
		domain[1] = new AddableInteger (2);
		if(!binary)
			domain[2] = new AddableInteger (3);
		Arrays.fill(domains, domain);
		
		AddableInteger infeasibleUtil;
		if (maximize) 
			infeasibleUtil = AddableInteger.MinInfinity.MIN_INF;
		else 
			infeasibleUtil = AddableInteger.PlusInfinity.PLUS_INF;
		
		// Create the random utilities
		int nbrUtil =  binary ? (int) Math.pow(2.0, (double)variables.size()) : (int) Math.pow(3.0, (double)variables.size());
		AddableInteger[] utilities = new AddableInteger[nbrUtil];
		for (int i = 0; i < nbrUtil; i++) {
			if (Math.random() < p2) {
				utilities[i] = infeasibleUtil;
			} else {
				int rand = (int) (amplitude*Math.random()); 	// between 0 and amplitude
				if (sign == 0) 	rand -= amplitude/2;			// between -amplitude/2 and +amplitude/2
				if (sign < 0) 	rand -= amplitude;				// between -amplitude and 0
				utilities[i] = new AddableInteger(rand);
			}
		}
		
		return new Hypercube<AddableInteger, AddableInteger> (variables.toArray(new String[0]), domains, utilities, infeasibleUtil);
	}
	
	/** Generates a random probability space
	 * 
	 * All domains are {1, 2, 3} and utility values are random real values that all sum up to 1.  
	 * @param variables list of variables
	 * @return a random probability space
	 */
	public static Hypercube<AddableInteger, AddableReal> randProbSpace (List<String> variables) {
		
		// Create the domains
		AddableInteger[][] domains = new AddableInteger[variables.size()][];
		AddableInteger[] domain = {new AddableInteger (1), new AddableInteger (2), new AddableInteger (3)};
		Arrays.fill(domains, domain);
		
		// Create the random utilities
		int nbrUtil = (int) Math.pow(3.0, (double)variables.size());
		double[] utilitiesTmp = new double[nbrUtil];
		double sum = 0;
		for (int i = 0; i < nbrUtil; i++) {
			double rand = Math.random();
			utilitiesTmp[i] = rand;
			sum += rand;
		}
		
		// Re-scale the utilities
		AddableReal[] utilities = new AddableReal [nbrUtil];
		for (int i = 0; i < nbrUtil; i++) 
			utilities[i] = new AddableReal (utilitiesTmp[i] / sum);
		
		return new Hypercube<AddableInteger, AddableReal> (variables.toArray(new String[0]), domains, utilities, null);
	}
	
	/** Generates a random problem and stores it into randProb.xml
	 * @param args 			ignored
	 * @throws IOException 	if an error occurs when writing to the file
	 */
	public static void main (String[] args) throws IOException {
		Document prob = AllTests.createRandProblem(50, 300, 5, true, 1);
		new XMLOutputter(Format.getPrettyFormat()).output(prob, new FileWriter ("randProb.xml"));
	}

}
