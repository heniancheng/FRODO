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

import java.util.Arrays;
import java.util.List;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.RandGraphFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.localSearch.dsa.DSA;
import frodo2.algorithms.localSearch.dsa.VALUEmsg;
import frodo2.algorithms.test.AllTests;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * JUnit tests for the DSA algorithm. Since DSA it is not complete, the only
 * things that can be tested are the different decision strategies.
 * 
 * @param <U> the type used for utility values
 * 
 * @author Brammert Ottens, Thomas Leaute
 */
public class TestDSA < U extends Addable<U> > extends TestCase {

	/** Maximum number of variables in the problem 
	 * @note Must be at least 2. 
	 */
	private final int maxNbrVars = 40;
	
	/** Maximum number of binary constraints in the problem */
	private final int maxNbrEdges = 800;

	/** Maximum number of agents */
	private final int maxNbrAgents = 10;
	
	/** A random problem used to test*/
	private XCSPparser<AddableInteger, U> problem;
	
	/** The agent for which the decision strategy is to be tested */
	private String agent;
	
	/** The variable for which the decision strategy is to be tested */
	private String variable;
	
	/** The domain of \c variable*/
	private AddableInteger[] ownDomain;
	
	/** The size of \c ownDomain */
	private int domainSize;
	
	/** The neighboring variables of \a variable*/
	private String[] neighbours;
	
	/** The number of neighbours */
	private int numberOfNeighbours;
	
	/** The initial value of \a variable*/
	private AddableInteger initialValue;
	
	/** Array for storing the context values */
	private AddableInteger[] context;
	
	/** Array for storing the context variables */
	private String[] contextVariables;
	
	/** A list of spaces known by \a agent*/
	private List< ? extends UtilitySolutionSpace<AddableInteger, U> > spaces;
	
	/** The subproblem belonging to the agent */
	private XCSPparser<AddableInteger, U> subProblem;
	
	/** \c true when the problem is a maximization problem, and \c false otherwise */
	private boolean maximize;
	
	/** The utility of the optimal assignment */
	private U optimal;
	
	/** The utility of the original assignment */
	private U originalUtil;
	
	/** The optimal value assignment given the context */
	private AddableInteger optimalValue;
	
	/** The final value of the variable */
	private AddableInteger value;
	
	/** The queue used to send messages */
	private Queue q;
	
	/** Dummy queue to be used as a sink */
	private Queue q2;
	
	/** The modules parameters*/
	private Element parameters;
	
	/** Instance of the module to be tested */
	private DSA<AddableInteger, U> module;
	
	/** Decision strategy to be tested */
	private String strategy;
	
	/** The difference between the optimal utility and the current utility */
	private int comparison;

	/** The class used for utility values */
	private Class<U> utilClass;
	
	/**
	 * @param name 		the name of the test to be performed 
	 * @param strategy 	the strategy to be tested
	 * @param maximize 	whether to maximize or minimize
	 * @param utilClass the type used for utility values
	 */
	public TestDSA(String name, String strategy, boolean maximize, Class<U> utilClass) {
		super(name);
		this.strategy = strategy;
		this.maximize = maximize;
		this.utilClass = utilClass;
	}

	/** 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		RandGraphFactory.Graph graph = RandGraphFactory.getRandGraph(maxNbrVars, maxNbrEdges, maxNbrAgents);
		problem = new XCSPparser<AddableInteger, U> (AllTests.generateProblem(graph, 0, this.maximize));
		problem.setUtilClass(utilClass);
		String[] agents = problem.getAgents().toArray(new String[0]);
		q = new Queue(false);
		q2 = new Queue(false); // Dummy queue to make sure that the messages sent by the module are taken care of
		QueueIOPipe pipe = new QueueIOPipe(q2);
		
		for(String agent : problem.getAgents())
			q.addOutputPipe(agent, pipe);
		q.addOutputPipe(AgentInterface.STATS_MONITOR, pipe);
		
		// randomly select a non-empty agent
		do {
			agent = agents[(int)(Math.random()*agents.length)];
		} while (this.problem.getVariables(this.agent).isEmpty());
		
		// get the spaces
		subProblem = problem.getSubProblem(agent);
		spaces = subProblem.getSolutionSpaces();
		
		//select one of the variables of the agent
		String[] variables = problem.getVariables(agent).toArray(new String[0]);
		variable = variables[(int)(Math.random()*variables.length)];
		ownDomain = problem.getDomain(variable);
		domainSize = problem.getDomainSize(variable);
		
		// find the neighbours of this variable
		neighbours = problem.getNeighborVars(variable).toArray(new String[0]);
		
		// first create a context
		numberOfNeighbours = neighbours.length;
		context = new AddableInteger[numberOfNeighbours + 1];
		contextVariables = new String[numberOfNeighbours + 1];
		int i = 0;
		for(String neighbour : neighbours) {
			AddableInteger[] domain = problem.getDomain(neighbour);
			AddableInteger value = domain[(int)(Math.random()*domain.length)];
			contextVariables[i] = neighbour;
			context[i] = value;
			i++;
		}
		contextVariables[i] = variable;
		
		//determine the utility of the original assignment
		context[i] = initialValue;
		originalUtil = this.problem.getZeroUtility();
		for(int j = 0; j < spaces.size(); j++) {
			UtilitySolutionSpace<AddableInteger, U> space = spaces.get(j);
			if (!Arrays.asList(space.getVariables()).contains(variable))
				continue;
			originalUtil = originalUtil.add(space.getUtility(contextVariables, context));
		}
		// calculate the optimal assignment, given the context
		if(maximize)
			optimal = this.problem.getMinInfUtility();
		else
			optimal =  this.problem.getPlusInfUtility();

		optimalValue = ownDomain[0];

		for(i = 0; i < domainSize; i++) {
			AddableInteger v = ownDomain[i];
			context[numberOfNeighbours]= v;
			U util = this.problem.getZeroUtility();
			for(int j = 0; j < spaces.size(); j++) {
				UtilitySolutionSpace<AddableInteger, U> space = spaces.get(j);
				if (!Arrays.asList(space.getVariables()).contains(variable))
					continue;
				util = util.add(space.getUtility(contextVariables, context));
			}

			if(maximize) {
				if(optimal.compareTo(util) < 0) {
					optimal = util;
					optimalValue = v;
				}
			} else {
				if(optimal.compareTo(util) > 0) {
					optimal = util;
					optimalValue = v;
				}
			}

		}
		
		// set up the module
		parameters = new Element("module");
		parameters.setAttribute("p", Double.toString(Math.random()));
		parameters.setAttribute("convergence", Boolean.toString(true));
		parameters.setAttribute("strategy", strategy);
		module = new DSA<AddableInteger, U> (subProblem, parameters);
		
		module.setQueue(q);
		
		module.notifyIn(new Message("Start"));

		// obtain the initial assignment
		initialValue = module.getCurrentValue(variable);

		// set the context
		i = 0;
		for(; i < numberOfNeighbours; i++) {
			VALUEmsg<AddableInteger> message = new VALUEmsg<AddableInteger>(contextVariables[i], variable, context[i]);
			module.notifyIn(message);
		}
		
		// obtain the current assignment
		value = module.getCurrentValue(variable);

		comparison = optimal.compareTo(originalUtil);
		if(!maximize) {
			comparison = -comparison;
		}
	}

	/** 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		q.end();
		q2.end();
		this.agent = null;
		this.context = null;
		this.contextVariables = null;
		this.initialValue = null;
		this.module = null;
		this.neighbours = null;
		this.optimal = null;
		this.optimalValue = null;
		this.originalUtil = null;
		this.ownDomain = null;
		this.parameters = null;
		this.problem = null;
		this.q = null;
		this.q2 = null;
		this.spaces = null;
		this.subProblem = null;
		this.value = null;
		this.variable = null;
	}
	
	/** @return the test suite */
	public static TestSuite suite () {
		TestSuite suite = new TestSuite ("Random tests for DSA decision strategies");
		
		TestSuite tmp = new TestSuite ("Tests strategy A on minimization problems with AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableInteger> ("testStrategyA", DSA.A.class.getName(), false, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy A on maximization problems with AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableInteger> ("testStrategyA", DSA.A.class.getName(), true, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy C on minimization problems with AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableInteger> ("testStrategyC", DSA.C.class.getName(), false, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy C on maximization problems with AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableInteger> ("testStrategyC", DSA.C.class.getName(), true, AddableInteger.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy E on minimization problems with AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableInteger> ("testStrategyE", DSA.E.class.getName(), false, AddableInteger.class), 50));
		suite.addTest(tmp);
	
		tmp = new TestSuite ("Tests strategy E on maximization problems with AddableInteger utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableInteger> ("testStrategyE", DSA.E.class.getName(), true, AddableInteger.class), 50));
		suite.addTest(tmp);
	
		tmp = new TestSuite ("Tests strategy A on minimization problems with AddableReal utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableReal> ("testStrategyA", DSA.A.class.getName(), false, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy A on maximization problems with AddableReal utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableReal> ("testStrategyA", DSA.A.class.getName(), true, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy C on minimization problems with AddableReal utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableReal> ("testStrategyC", DSA.C.class.getName(), false, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy C on maximization problems with AddableReal utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableReal> ("testStrategyC", DSA.C.class.getName(), true, AddableReal.class), 50));
		suite.addTest(tmp);
		
		tmp = new TestSuite ("Tests strategy E on minimization problems with AddableReal utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableReal> ("testStrategyE", DSA.E.class.getName(), false, AddableReal.class), 50));
		suite.addTest(tmp);
	
		tmp = new TestSuite ("Tests strategy E on maximization problems with AddableReal utilities");
		tmp.addTest(new RepeatedTest (new TestDSA<AddableReal> ("testStrategyE", DSA.E.class.getName(), true, AddableReal.class), 50));
		suite.addTest(tmp);
	
		return suite;
	}
	
	/**
	 * Test the strategy A
	 * 
	 * @author Brammert Ottens, 11 aug 2009
	 */
	public void testStrategyA() {
		
		if(comparison <= 0) {
			assertEquals(initialValue, value);
		} else {
			// either the value has not changed, or the optimal value has been chosen
			assertTrue(initialValue.equals(value) || optimalValue.equals(value));
		}
	}
	
	/**
	 * Test the strategy C
	 * 
	 * @author Brammert Ottens, 11 aug 2009
	 */
	public void testStrategyC() {
		
		if(comparison < 0) {
			assertEquals(initialValue, value);
		} else {
			// either the value has not changed, or the optimal value has been chosen
			assertTrue(initialValue.equals(value) || optimalValue.equals(value));
		}
	}
	
	/**
	 * Test the strategy E
	 * 
	 * @author Brammert Ottens, 11 aug 2009
	 */
	public void testStrategyE() {
		
		U utility = module.getCurrentUtility(variable);
		
		if( comparison < 0) {
			assertEquals(initialValue, value);
		} else if(comparison > 0) {
			assertEquals(optimal, utility);
		} else {
			// either the value has not changed, or the optimal value has been chosen
			assertTrue(initialValue.equals(value) || optimal.equals(utility));
		}
	}

}
