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

package frodo2.algorithms.varOrdering.dfs;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.communication.Message;
import frodo2.communication.MessageWith3Payloads;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** A DFSgeneration module that also computes the order in which is variable is visited and the total number of variables in the DFS
 * 
 * This is (a modified version of) the DFSorder() procedure from the following paper: 
 * Thomas Leaute and Boi Faltings. Privacy-preserving multi-agent constraint satisfaction. In Proceedings of 
 * the 2009 IEEE International Conference on PrivAcy, Security, riSk and Trust (PASSAT'09), Vancouver, 
 * British Columbia, August 29-31 2009. IEEE Computer Society Press.
 * 
 * @author Eric Zbinden, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class DFSgenerationWithOrder < V extends Addable<V>, U extends Addable<U> > extends DFSgeneration<V, U> {
	
	/** The type of the messages telling whether a given variable is a root */
	public static String ROOT_VAR_MSG_TYPE = LeaderElectionMaxID.OUTPUT_MSG_TYPE;
	
	/** @see DFSgeneration#getRootVarMsgType() */
	@Override
	protected String getRootVarMsgType () {
		return ROOT_VAR_MSG_TYPE;
	}

	/** The type of the message used to tell the recipient that it is a child of the sender */
	public static String CHILD_ORDER_MSG_TYPE = "CHILDwithOrder";
	
	/** @see DFSgeneration#getChildMsgType() */
	@Override
	protected String getChildMsgType () {
		return CHILD_ORDER_MSG_TYPE;
	}

	/** The type of the message used to tell the recipient that it is a pseudo-child of the sender */
	public static String PSEUDO_ORDER_MSG_TYPE = "PSEUDOwithOrder";
	
	/** @return The type of the message used to tell the recipient that it is a pseudo-child of the sender */
	@Override
	protected String getPseudoMsgType () {
		return PSEUDO_ORDER_MSG_TYPE;
	}

	/** The type of the output messages */
	public static String OUTPUT_MSG_TYPE = "DFSoutput_DFSgenerationWithOrder";
	
	/** @return The type of the output messages */
	@Override
	protected String getOutputMsgType () {
		return OUTPUT_MSG_TYPE;
	}

	/** The type of the output messages containing the orders of variables in the DFS */
	public static final String OUTPUT_ORDER_TYPE = "DFSorderOutput";
	
	/** The type of the top-down messages sent by the root containing the number of variables in the DFS */
	public static final String VARIABLE_COUNT_TYPE = "DFSorderVarNbr";
	
	/** Map of the true final order of this agent's variables */
	private Map<String, Integer> trueOrder;
	
	/** Map of the declared final order of this agent's variables */
	private Map<String, Integer> declaredOrder;
	
	/** Map of the last order received */
	private Map<String, Integer> order_process; 
	
	/** For each variable, whether it is a root */
	private Map<String, Boolean> are_root;
	
	/** For each variable, the total number of variables in its constraint graph component */
	private Map<String, Integer> nbrVars;
	
	/** A source of randomness */
	private final SecureRandom rnd = new SecureRandom ();
	
	/** Each variable overstates by rand(minIncr, 2*minIncr) its visiting order in the constraint graph traversal, in order to keep its number of neighbors secret */
	private final int minIncr;
	
	/** Carries DFS order information for one variable. Used to exchange info between modules */
	public static class DFSorderOutputMessage extends MessageWith3Payloads<String, Integer, Integer> {

		/** Used for serialization */
		private static final long serialVersionUID = -8862767293116462691L;
		
		/** Empty constructor used for externalization */
		public DFSorderOutputMessage () {
			super.type = OUTPUT_ORDER_TYPE;
		}

		/** Constructor
		 * @param var 				Name of variable
		 * @param trueOrder 		order of visit of the variable, as believed by the variable
		 * @param declaredOrder 	order of visit of the variable, as declared by the variable
		 */
		public DFSorderOutputMessage(String var, int trueOrder, int declaredOrder){
			super(OUTPUT_ORDER_TYPE, var, trueOrder, declaredOrder);
		}
		
		/** @return the true order */
		public int getTrueOrder(){
			return this.getPayload2();
		}
		
		/** @return the true order */
		public int getDeclaredOrder(){
			return this.getPayload3();
		}
		
		/** @return the variable */
		public String getVar(){
			return this.getPayload1();
		}
		
		/** @see MessageWith3Payloads#toString() */
		public String toString(){
			return "DFSwithOrderOutput:\n"+
					"\tSender: "+this.getVar()+"\n"+
					"\ttrueOrder: "+this.getTrueOrder() + "\n\tdeclaredOrder: " + this.getDeclaredOrder();
		}
	}
	
	/** Constructor
	 * @param problem 					this agent's problem
	 * @param parameters 				the parameters for DFSgenerationWithOrder
	 * @throws ClassNotFoundException 	if the heuristic class is unknown
	 */
	public DFSgenerationWithOrder (DCOPProblemInterface<V, U> problem, Element parameters) throws ClassNotFoundException {
		super(problem, parameters);
		
		trueOrder = new HashMap<String, Integer>();
		declaredOrder = new HashMap<String, Integer>();
		order_process = new HashMap<String, Integer>();
		are_root = new HashMap<String, Boolean>();
		this.nbrVars = new HashMap<String, Integer> ();
		
		String minIncrStr = parameters.getAttributeValue("minIncr");
		if (minIncrStr == null) 
			this.minIncr = 1;
		else 
			this.minIncr = Integer.parseInt(minIncrStr);
		assert this.minIncr > 0 : "Incorrect value for minIncr (= " + minIncrStr + "); must be be > 0";
	}

	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported
	 */
	public DFSgenerationWithOrder(Element parameters, DCOPProblemInterface<V, U> problem) {
		super(parameters, problem);
		this.minIncr = 0;
	}
	
	/** @see DFSgeneration#init() */
	@Override
	protected void init () {
		super.init();
		
		for (String var : problem.getMyVars()){
			init(var);
		}
	}

	/** @see DFSgeneration#init(java.lang.String) */
	@Override
	protected void init(String var){
		super.init(var);
		
		trueOrder.put(var, 0);
		declaredOrder.put(var, 0);
		order_process.put(var, 0);
		are_root.put(var, false);
	}

	/** @see DFSgeneration#getMsgTypes() */
	@Override
	public Collection<String> getMsgTypes() {
		Collection<String> msgTypes = super.getMsgTypes();
		msgTypes.add(VARIABLE_COUNT_TYPE);
		return msgTypes;
	}
	
	/** @see DFSgeneration#reset() */
	@Override
	public void reset () {
		super.reset();
		
		trueOrder = new HashMap<String, Integer>();
		declaredOrder = new HashMap<String, Integer>();
		order_process = new HashMap<String, Integer>();
		are_root = new HashMap<String, Boolean>();
		this.nbrVars = new HashMap<String, Integer> ();
	}
	
	/** @see DFSgeneration#makeChildToken(Serializable, String, String, Collection) */
	@Override
	protected CHILDmsg makeChildToken(Serializable rootID, String var, String parent, Collection<String> openNeighbors){
		return new CHILDorderMsg (var, parent, rootID, orderOfNextChild(var));
	}
	
	/** @see DFSgeneration#notifyIn(Message) */
	@Override
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		// Parse the problem if it has not been done yet
		if (! this.started && ! msgType.equals(STATS_MSG_TYPE) && msgType.equals(AgentInterface.AGENT_FINISHED)) 
			init();

		if(msgType.equals(this.getRootVarMsgType())){
			
			LeaderElectionMaxID.MessageLEoutput<?> msgCast = (LeaderElectionMaxID.MessageLEoutput<?>) msg;
			
			String myVar = msgCast.getSender();
			
			if(msgCast.getFlag()) {
				
				if(!openNeighbors.containsKey(myVar)) init(myVar);
				
				if (this.openNeighbors.get(myVar).isEmpty()) // isolated variable
					queue.sendMessageToSelf(new DFSorderOutputMessage(myVar, 0, 0));
				
				else if (! are_root.put(myVar, true)) {

					int declared = this.rnd.nextInt(this.minIncr) + this.minIncr - 1;
					this.declaredOrder.put(myVar, declared);
					
					// Send output message to the other modules of this agent
					queue.sendMessageToSelf(new DFSorderOutputMessage(myVar, 0, declared));
				}
			}		
			
		} else if (msgType.equals(VARIABLE_COUNT_TYPE)){
			
			VarNbrMsg msgCast = (VarNbrMsg) msg;
			
			String myVar = msgCast.getDest();
			int total = msgCast.getTotal();
			this.nbrVars.put(myVar, total);
			
			// Forward the total number of variables to each child
			for (String child : dfsViews.get(myVar).getChildren())
				queue.sendMessage(super.owners.get(child), new VarNbrMsg (total, child));
		}
		
		super.notifyIn(msg);
	}
	
	/** @see DFSgeneration#processAdditionalMsgInformation(Message, java.lang.String, DFSview) */
	protected void processAdditionalMsgInformation(Message msg, String myVar, DFSview<V, U> myRelationships){
		
		String msgType = msg.getType();
		
		 if (msgType.equals(this.getChildMsgType())){
			
			CHILDorderMsg msgCast = (CHILDorderMsg) msg;
			
			int order = msgCast.getOrder();
			order_process.put(myVar, order);
			
			//first token received
			if (trueOrder.get(myVar) == 0) {
				if(!isRoot(myVar)) { //root must stay with order 0
					trueOrder.put(myVar, order);
					int declared = order + this.rnd.nextInt(this.minIncr) + this.minIncr - 1;
					this.declaredOrder.put(myVar, declared);
					
					// Send output message to the other modules of this agent
					queue.sendMessageToSelf(new DFSorderOutputMessage(myVar, order, declared));
				}
			}
					
		} 
	}
	
	/** @see DFSgeneration#sendAdditionalDFSoutput(Serializable, java.lang.String) */
	@Override
	protected void sendAdditionalDFSoutput(Serializable rootID, String myVar){

		if (trueOrder.get(myVar) == 0){ // => root
			if(order_process.get(myVar) == 0){ //no token exchanged, 1 var in the component
				order_process.put(myVar, 1); 
			}
			if (this.nbrVars.get(myVar) == null) 
				queue.sendMessageToSelf(new VarNbrMsg(order_process.get(myVar), myVar));
			
		}
	}
	
	/**
	 * @param var the variable to test
	 * @return if the specified variable is a root
	 */
	private boolean isRoot(String var){
		return are_root.get(var);
	}

	
	/**
	 * @param var the variable who calls this method
	 * @return the order of the next var
	 */
	private int orderOfNextChild(String var){
		int o_p = order_process.get(var);
		int o_f = this.trueOrder.get(var);
		
		if (o_p == o_f) return this.declaredOrder.get(var) + 1; //next variable, increment of 1
		else return o_p; //msg cross this var who is already marked, no increment
	}

}
