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

package frodo2.algorithms.varOrdering.dfs.tests;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import frodo2.algorithms.heuristics.MostConnectedHeuristic;
import frodo2.algorithms.heuristics.ScoringHeuristic;
import frodo2.algorithms.heuristics.VarNameHeuristic;
import frodo2.algorithms.varOrdering.dfs.CHILDorderMsg;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.VarNbrMsg;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder.DFSorderOutputMessage;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;


/**
 * JUnit for DFSgeneration with Order
 * @author Eric Zbinden, Thomas Leaute
 */
public class DFSgenerationWithOrderTest extends DFSgenerationTest {
	
	/** For each variable, the total number of variables in its constraint graph component */
	private Map<String, Integer> totals;
	
	/** List of all roots */
	private ArrayList<String> are_root;
	
	/** Stores order of the Variables in the DFS*/
	private HashMap<String,Integer> node_order;
	
	/** Each variable overstates by rand(minIncr, 2*minIncr) its visiting order in the constraint graph traversal, in order to keep its number of neighbors secret */
	private final int minIncr;
	
	/** Constructor
	 * @param useTCP 		whether to use TCP pipes
	 * @param heuristic 	the class of the ScoringHeuristic used
	 * @param minIncr 		each variable overstates by rand(minIncr, 2*minIncr) its visiting order in the constraint graph traversal, in order to keep its number of neighbors secret
	 */
	public DFSgenerationWithOrderTest(boolean useTCP, Class< ? extends ScoringHeuristic<?> > heuristic, int minIncr) {
		super(useTCP, true, heuristic);
		this.minIncr = minIncr;
	}
	
	/** @see DFSgenerationTest#createDFSparams(org.jdom2.Element) */
	@Override
	protected Element createDFSparams(Element heuristicParams) {

		Element out = super.createDFSparams(heuristicParams);
		out.setAttribute("minIncr", Integer.toString(this.minIncr));
		
		return out;
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for DFSgenerationWithOrder");
		
		TestSuite testTmp = new TestSuite ("Tests for DFSgenerationWithOrder using shared memory pipes");
		testTmp.addTest(new RepeatedTest (new DFSgenerationWithOrderTest (false, VarNameHeuristic.class, 1), 3000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFSgenerationWithOrder using shared memory pipes and a minIncr greater than 1");
		testTmp.addTest(new RepeatedTest (new DFSgenerationWithOrderTest (false, VarNameHeuristic.class, 10), 3000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFSgenerationWithOrder using shared memory pipes and Most Connected heuristic");
		testTmp.addTest(new RepeatedTest (new DFSgenerationWithOrderTest (false, MostConnectedHeuristic.class, 1), 3000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for DFSgenerationWithOrder using TCP pipes");
		testTmp.addTest(new RepeatedTest (new DFSgenerationWithOrderTest (true, VarNameHeuristic.class, 1), 3000));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** Extends parent's method to include tests on correct order assignment
	 * @see DFSgenerationTest#testRandom()
	 */
	@Override
	public void testRandom() 
	throws IllegalArgumentException, IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException{
			
		totals = new HashMap<String, Integer>();
		node_order = new HashMap<String, Integer>();
		are_root = new ArrayList<String>();
		
		nbrOutputMessagesPerVar = 4; // 1 DFSoutput, 1 DFSstat, 1 VarNbrMsg, 1 DFSoutputWithOrder

		for (List<String> comp: graph.components){
			if (comp.size() == 1){
				node_order.put(comp.get(0), 0);
			}
		}
		
		super.testRandom();	
		
		HashSet<Integer> temp = new HashSet<Integer>();
		
		for (List<String> comp : graph.components){
			
			int size = comp.size();
			for (String ord : comp) {
				int order = node_order.get(ord);
				assertTrue("2 var have the same order",temp.add(order));
				assertTrue(order >= 0);
				if (this.minIncr == 1) 
					assertTrue (order < size);
			}
			temp.clear();
			
			for(String tot : comp) {
				if (this.minIncr == 1) 
					assertEquals("total number of var not correct. ", comp.size(), totals.get(tot).intValue());
				else 
					assertTrue (comp.size() <= totals.get(tot).intValue());
			}
		}
	}

	/** Listens to the output of the DFS generation protocol. 
	 * @see IncomingMsgPolicyInterface#getMsgTypes()
	 */
	@Override
	public Collection<String> getMsgTypes() {
		Collection<String> types = super.getMsgTypes();
		types.add(DFSgenerationWithOrder.CHILD_ORDER_MSG_TYPE);
		types.add(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE);
		types.add(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE);
		types.add(DFSgeneration.ROOT_VAR_MSG_TYPE);
		return types;
	}
	
	/** @see DFSgenerationTest#getOutputMsgType() */
	@Override
	protected String getOutputMsgType () {
		return DFSgenerationWithOrder.OUTPUT_MSG_TYPE;
	}
	
	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@Override
	public synchronized void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if(msgType.equals(DFSgeneration.ROOT_VAR_MSG_TYPE)){
			
			LeaderElectionMaxID.MessageLEoutput<?> msgCast = (LeaderElectionMaxID.MessageLEoutput<?>) msg;
			
			if(msgCast.getFlag())
				are_root.add(msgCast.getSender());
			
			return;
			
		} else if(msgType.equals(DFSgenerationWithOrder.CHILD_ORDER_MSG_TYPE)){
			
			CHILDorderMsg msgCast = (CHILDorderMsg) msg;
			int local_order = msgCast.getOrder();		
			String receiver = msgCast.getDest();
			
			if(node_order.get(receiver) == null ){
				if (are_root.contains(receiver)) 
					node_order.put(receiver, 0); //is root => 0
				else 
					node_order.put(receiver, local_order);				
			}
			
			return;
			
		} else if (msgType.equals(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE)){
			
			VarNbrMsg msgCast = (VarNbrMsg) msg;
			String var = msgCast.getDest();
			int total = msgCast.getTotal();
			
			totals.put(var, total);
			
			// Check the total number of variables
			int size = graph.components.get(graph.componentOf.get(var)).size();
			if (this.minIncr == 1) 
				assertEquals("total of var is wrong", size, total);
			else 
				assertTrue(size + " > " + total, size <= total);

		} else if (msgType.equals(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE)){
			
			DFSorderOutputMessage msgCast = (DFSorderOutputMessage) msg ;
			
			String var = msgCast.getVar();
			int order = msgCast.getTrueOrder();
				
			if (node_order.get(var) != null) {
				if (order != node_order.get(var)) fail("Var visited in "+node_order.get(var)+" but fixed in "+order);
				
			} else { 
				//node_order.put(var, 0);	//root
				if(order != 0) fail("Variable is not cross during DFS");				
			}
			
			/**
			 * @todo The module is tested against itself: we check that the order in the output message
			 * is the same as the order in the internal messages sent by the module. 
			 * This is not enough: they could be equal, but both wrong. 
			 * It is necessary to compute the orders independently outside of the module under test, 
			 * and compare with the orders in the output messages. 
			 */
		}
		
		super.notifyIn(msg);
		
	} 
	
	/** @return The DFSgeneration class under test */
	@Override
	protected Class<?> getDFSclass(){
		return DFSgenerationWithOrder.class;
	}
	
	
}
