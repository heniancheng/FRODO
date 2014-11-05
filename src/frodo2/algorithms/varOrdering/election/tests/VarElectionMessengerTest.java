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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import junit.extensions.RepeatedTest;
import junit.framework.TestSuite;

import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.MaxIDmsg;
import frodo2.algorithms.varOrdering.election.VarElectionMessenger;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;

/** JUnit test for the class VarElectionMEssengerTest */
public class VarElectionMessengerTest extends LeaderElectionMaxIDTest<Integer> {

	/**
	 * A IncomingMsgPolicyInterface used in JUNIT VarElectionMessengerTest to test whenever ID send are strictly increasing
	 * @author Eric Zbinden
	 */
	private class IdIncreasingVerifyer implements IncomingMsgPolicyInterface<String> {
		
		/**
		 * A map containing all last ID received from all neighbors
		 */
		private Map<String, Integer> receivedID;
		
		/**
		 * Constructor
		 * @param neighborhoods the neighborhoods of this agent
		 */
		private IdIncreasingVerifyer(Set<String> neighborhoods){
			receivedID = new HashMap<String, Integer>(neighborhoods.size());
			for (String neighbor : neighborhoods){
				receivedID.put(neighbor, Integer.MIN_VALUE);
			}
		}

		/**
		 * @see frodo2.communication.IncomingMsgPolicyInterface#notifyIn(frodo2.communication.Message)
		 */
		@SuppressWarnings("unchecked")
		public void notifyIn(Message msg) {
			
			if (msg.getType().equals(LeaderElectionMaxID.LE_MSG_TYPE)) {
				
				MaxIDmsg<Integer> msgCast = (MaxIDmsg<Integer>) msg;
				int receiveID = (Integer) msgCast.getMaxID();
				String receivedFrom = (String) msgCast.getSender();
				
				synchronized(receivedID){
					int maxID = receivedID.get(receivedFrom);

					assertTrue("ID msg are not strictly increasing", maxID <= receiveID);
					if (maxID < receiveID){
						receivedID.remove(receivedFrom);
						receivedID.put(receivedFrom, receiveID);
					}	
				}
			}
		}

		/**
		 * @see frodo2.communication.MessageListener#getMsgTypes()
		 */
		public Collection<String> getMsgTypes() {
			ArrayList <String> msgTypes = new ArrayList <String> (1);
			msgTypes.add(LeaderElectionMaxID.LE_MSG_TYPE);
			return msgTypes;
		}

		/**
		 * @see frodo2.communication.MessageListener#setQueue(frodo2.communication.Queue)
		 */
		public void setQueue(Queue queue) {
			//don't care			
		}		
	}	
	
	/** Constructor that instantiates a test only for the input method
	 * @param method test method
	 */
	public VarElectionMessengerTest(String method) {
		super(method);
		
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () { 

		TestSuite testSuite = new TestSuite ("Tests for VarElectionMessenger");
		
		TestSuite testTmp = new TestSuite ("Tests for VarElectionMessenger using shared memory pipes");
		testTmp.addTest(new RepeatedTest (new VarElectionMessengerTest ("testRandomSharedMemory"), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for VarElectionMessenger using TCP pipes");
		testTmp.addTest(new RepeatedTest (new VarElectionMessengerTest ("testRandomTCP"), 100));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/** @see LeaderElectionMaxIDTest#getNbrMsgsNeeded() */
	@Override
	protected int getNbrMsgsNeeded(){
		
		//We count 3x the normal number of messages (2N of lies and N without lie)
		//Then subtract 2*nbrVariables because there is only 1 output message per variable
		return super.getNbrMsgsNeeded()*3 - 2 * graph.nodes.size();
	}
	
	/** 
	 * @see frodo2.algorithms.varOrdering.election.tests.LeaderElectionMaxIDTest#initiatingIDandListener()
	 */
	protected Map<String, Integer> initiatingIDandListener(){
		
		Map<String, Integer> uniqueIDs = new HashMap<String, Integer>(queues.length);
		
		//Generate ID
		List<Integer> ids = new ArrayList<Integer>(queues.length);
		Random rand = new Random();
		boolean colision;
		do{
			colision = false;
			int max = Integer.MIN_VALUE;
			for(int i=0; i<queues.length;i++){
				int newID = rand.nextInt();
				if (newID == max){
					colision = true;
				} else if (max < newID){
					max = newID;
					colision = false;
				}
				ids.add(newID);
			}
			
		}while(colision);
		
		// Generate the listeners
		for (int i =0; i<queues.length; i++) {
			String name = graph.nodes.get(i);
			
			//link name and id
			uniqueIDs.put(name, ids.get(i));
			
			//listeners
			queues[i].addIncomingMessagePolicy(new VarElectionMessenger(
					name,
					ids.get(i),
					graph.neighborhoods.get(name),
					nbrAgents - 1)); //add to compute the algo
			queues[i].addIncomingMessagePolicy(this); //add to verify the result
			queues[i].addIncomingMessagePolicy(new IdIncreasingVerifyer(graph.neighborhoods.get(name))); //add to verify ID increasing or not			
		}
		
		return uniqueIDs;
		
	}
	

	/**
	 * @see frodo2.algorithms.varOrdering.election.tests.LeaderElectionMaxIDTest#checkOutputs(java.util.Map)
	 */
	@Override
	protected void checkOutputs(Map<String, Integer> allUniqueIDs){
		
		// Compute the correct leaders (one per connected component)
		Map<String, Integer> correctOutputs = computeLeaders (graph.nodes.size(), graph.components, allUniqueIDs);
		
		
		for (List<String> component : this.graph.components){
			
			int rootNumber = 0;
			for (String node : component){

				if (this.outputs.get(node).getFlag()) {
					rootNumber++;					
					//Verify if this root should really be a root
					assertEquals ("Root has not maxID", allUniqueIDs.get(node), correctOutputs.get(node));
				}
			}			
			//Verify that there is one and only one root in every component
			assertEquals("Illegal number of root in one component", 1, rootNumber);
		
		}
	}

}
