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

package frodo2.algorithms.dpop.privacy;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.jdom2.Element;

import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.VarNbrMsg;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.crypto.CryptoScheme;

/** P2-DPOP's variable re-ordering algorithm
 * 
 * This is the InitializeOrder() and ShiftOrder() procedures from the following paper, modified so as not to re-shuffle at every iteration: 
 * 
 * Thomas Leaute and Boi Faltings. Privacy-preserving multi-agent constraint satisfaction. In Proceedings of 
 * the 2009 IEEE International Conference on PrivAcy, Security, riSk and Trust (PASSAT'09), Vancouver, 
 * British Columbia, August 29-31 2009. IEEE Computer Society Press.
 * 
 * @author Eric Zbinden, Thomas Leaute
 * @param <C> class used for clear text vector element
 * @param <E> class used for encrypted vector element
 * 
 * @todo It is inefficient to use bit-array-based ElGamalBigIntegers, because we don't need (partial) additive homomorphism
 */
public class SecureRerooting<C extends Addable<C>,E extends AddableLimited<C,E>> 
implements IncomingMsgPolicyInterface<String>, OutgoingMsgPolicyInterface<String> {
	
	/** Type of output of the secureRerooting module */
	public static String OUTPUT = "NewRoot";
	
	/** Token type telling the reroot of the dfs can be executed now */
	public static final String DONE = "RerootingDone";
	
	/** Set to 0, to identify the root of the component in the vector */
	private final C rootElement;
	
	/** Set to 1, for non-root variables in the vector */
	private final C notRootElement;
	
	/** Set to 2, for fake variables in the vector */
	private final C fakeElement;
	
	/** Random generator */
	private Random rand = new SecureRandom();
	
	/**All information about the variables owned by this agent */
	private Map<String,VariableInfo> infos;
	
	/** A set of vector messages sent, used to detect when one leaves the agent, so as only to re-encrypt it then */
	private HashMap< VectorMsg<C, E>, VariableInfo > vectorSent = new HashMap< VectorMsg<C, E>, VariableInfo > ();
	
	/** Convenient class to store information about a variable of this agent*/
	private class VariableInfo{
		
		/** My name */
		public String self;
		
		/** My codeName */
		public String codeName;
		
		/** Order of this variable */
		public int order;
		
		/** The order for this variable that it declared to the next variable */
		public int declaredOrder;
		
		/** If this variable is a new root */
		public Boolean root = null;
		
		/** A Done message waiting to be forwarded until I know I am not the new root */
		private Message pendingDone;
		
		/** If a reroot request is received and this variable hasn't received its CryptoScheme */
		public boolean waitCrypto = false;
		
		/** Believed total number of variables in this component */
		public int total = -1;
		
		/** A random permutation used to shuffle the vectors */
		public int[] permutation;
		
		/** Has this variable already been root ? */
		public boolean hasBeenRoot = false;
		
		/** CryptoScheme used to encrypt vectors */
		public CryptoScheme<C,E,?> cs;
		
		/** Pending VectorMsgs waiting for the cryptoscheme to be received */
		public ArrayList<VectorMsg<C,E>> waitingVector;
		
		/** The vector of this variable*/
		public ArrayList<E> vector;
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			return "VariableInfo\n\t var: " + this.self + "\n\t total: " + this.total +"\n\t hasBeenRoot: "+this.hasBeenRoot
			+ "\n\t permutation: " + Arrays.toString(this.permutation) + "\n\t CryptoScheme: " + this.cs + "\n\t vector: " + this.vector;
		}
		
		/**
		 * Constructor
		 * @param self the variable
		 */
		public VariableInfo(String self){
			this.self = self;
		}
		
		/** Create the vector of this variable 
		 * @param order 			the order of this variable in the linear ordering
		 * @param declaredOrder 	the order for this variable that it declared to the following variable
		 * @param size				the size of the component  
		 */
		public void createVector(int order, int declaredOrder, int size){
			
			// Create the vector
			this.vector = new ArrayList<E> (size);
			for (int i = 0; i < size; i++) 
				this.vector.add(null);
			
			// Fill in the vector
			for (int i = 0; i < order; i++) 
				this.vector.set(i, this.cs.encrypt(notRootElement, fakeElement));
			
			this.vector.set(order, cs.encrypt(rootElement, fakeElement));
			
			for (int i = order + 1; i <= declaredOrder; i++) 
				this.vector.set(i, this.cs.encrypt(fakeElement, fakeElement));
			
			for (int i = declaredOrder + 1; i < size; i++) 
				this.vector.set(i, this.cs.encrypt(notRootElement, fakeElement));
		}
		
		/** @return remove and return the head element of this variable's vector */
		public E remove(){
			return vector.remove(0);
		}
		
		/** Shuffles the input vector
		 * @param vector the vector to shuffle 
		 */
		public void shuffle (ArrayList<E> vector){
			
			assert vector.size() == this.permutation.length : vector.size() + " != " + this.permutation.length;
			
			ArrayList<E> old = new ArrayList<E> (vector);
			
			for (int i = this.permutation.length - 1; i >= 0; i--) 
				vector.set(this.permutation[i], old.get(i));
		}
		
		/** Encrypts a vector already encrypted 
		 *  @param vector The vector to re-encrypt
		 */
		public void encryptVector(ArrayList<E> vector){
			
			for (int i = vector.size() - 1; i >= 0; i--)
				vector.set(i, cs.reencrypt(vector.get(i)));
		}
	}	
	
	/** The type of the message containing an ordering vector */
	public static final String VECTOR_TYPE = "RerootingVector";
	
	/** Message used to tell a variable that it is the new root of its component */
	public static class SecureRootingMsg extends LeaderElectionMaxID.MessageLEoutput<String>{
		
		/** Empty constructor used for externalization */
		public SecureRootingMsg () { }

		/** Constructor
		 * @param sender the sender (and root) of the new tree
		 */
		public SecureRootingMsg(String sender){
			super(OUTPUT, sender, true, sender);
		}
	}
	
	/** Queue where messages are received */
	private Queue queue;
	
	/** true if this module has started */
	private boolean started = false;
	
	/** The problem interface */
	private DCOPProblemInterface<?, C> problem;

	/** The type of the messages sent to request a rerooting of the variable order */
	public static final String REROOTING_REQUEST_TYPE = "RerootRequest";
	
	/** Constructor
	 * @param problem 	the agent's subproblem
	 * @param params 	the parameters of the module
	 */
	@SuppressWarnings("unchecked")
	public SecureRerooting (DCOPProblemInterface<?, C> problem, Element params) {
		
		// Parse the class to be used for C
		String classOfCname = AddableInteger.class.getName();
		if (params != null) {
			String tmp = params.getAttributeValue("cleartextClass");
			if (tmp != null) 
				classOfCname = tmp;
		}

		// Instantiate this.rootElement & this.notRootElement
		C instance = (C) new AddableInteger ();
		try {
			Class<C> classOfC = (Class<C>) Class.forName(classOfCname);
			instance = classOfC.newInstance();
		} catch (ClassNotFoundException e) {
			System.err.println("The class specified as the `cleartextClass' for the SecureRerooting module was not found");
			e.printStackTrace();
		} catch (InstantiationException e) {
			System.err.println("Failed to call the empty constructor for the class specified as the `cleartextClass' for the SecureRerooting module");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			System.err.println("The class specified as the `cleartextClass' for the SecureRerooting module does not have an public empty constructor");
			e.printStackTrace();
		}
		this.rootElement = instance.fromString("0");
		this.notRootElement = instance.fromString("1");
		this.fakeElement = instance.fromString("2");
		
		this.problem = problem;
	}
	
	/** Initiate the module */
	private void init(){
		int size = problem.getMyVars().size();		
		
		infos = new HashMap<String, VariableInfo>(size);
		started = true;
	}
	
	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (9);
		
		// Incoming messages
		types.add(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE);
		types.add(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE);
		types.add(SecureCircularRouting.DELIVERY_MSG_TYPE);
		types.add(CollaborativeDecryption.CRYPTO_SCHEME_TYPE);
		types.add(CollaborativeDecryption.OUTPUT_TYPE);
		types.add(CollaborativeDecryption.VECTOR_OUTPUT_TYPE);
		types.add(REROOTING_REQUEST_TYPE);
		
		// Outgoing messages
		types.add(SecureCircularRouting.PREVIOUS_MSG_TYPE);
		types.add(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE);
		
		return types;
	}
	
	/** Sends a vector to the previous variable in the linear ordering
	 * @param info   	the sender variable 
	 * @param vector 	the vector
	 * @param owner 	the codename of the initial sender of the vector
	 * @param round 	the round
	 */
	private void passOn (VariableInfo info, ArrayList<E> vector, String owner, final short round) {
		
		VectorMsg<C, E> msg = new VectorMsg<C,E>(vector, owner, round);
		
		if (round != 2 && round != 4) // no need to re-encrypt messages in round 2 and 4
			this.vectorSent.put(msg, info);
		
		this.queue.sendMessageToSelf(new RoutingMsg<VectorMsg<C,E>>(
				SecureCircularRouting.PREVIOUS_MSG_TYPE,
				info.self, 
				msg));
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
				
		if(!started) init();
		
		String msgType = msg.getType();
		
		if(msgType.equals(SecureCircularRouting.DELIVERY_MSG_TYPE)){ 
			
			DeliveryMsg<?> msgCast = (DeliveryMsg<?>) msg;
			String myVar = msgCast.getDest();
			String innerMsgType = msgCast.getMessage().getType();
			
			if (innerMsgType.equals(VECTOR_TYPE)){
				
				VectorMsg<C, E> inner = (VectorMsg<C, E>) msgCast.getMessage();
				this.vectorSent.remove(inner);
				VariableInfo info = infos.get(myVar);
				
				if (info.cs != null) 
					this.processVectorMsg (info, inner);
				
				else { // no crypto scheme yet
					
					// Put this vector in the waiting list
					if(info.waitingVector == null)
						info.waitingVector = new ArrayList<VectorMsg<C,E>>();
					info.waitingVector.add(inner);
				}
				
			} else if (innerMsgType.equals(DONE)){
				
				VariableInfo info = infos.get(myVar);
				
				if (info.root == null) {
					
					// Postpone this message until it is confirmed that I am not the new root
					info.pendingDone = msg;
				}
				
				else if (!info.root) {
					
					// Transmit the Done message
					info.root = null;
					this.queue.sendMessageToSelf(new RoutingMsg<DoneMsg> (
						SecureCircularRouting.PREVIOUS_MSG_TYPE,
						myVar,
						new DoneMsg()));
				}
								
				else {
					assert info.root && !info.hasBeenRoot;

					//The root must send to self a LeaderElectionMaxID to restart the DFS
					info.hasBeenRoot = true;
					info.root = null;
					this.queue.sendMessageToSelf(new SecureRootingMsg(myVar));
				}
				
			} else return;
			
		} else if (msgType.equals(REROOTING_REQUEST_TYPE)){
			
			RerootingMsg msgCast = (RerootingMsg) msg;			
			String myVar = msgCast.getDest();	
						
			VariableInfo info = infos.get(myVar);
			if(info == null){
				info = new VariableInfo(myVar);
				infos.put(myVar, info);
			}
			
			if (info.cs == null) // I haven't received my crypto scheme yet
				info.waitCrypto = true;

			else if (info.vector.size() == info.total) // very first iteration; send my vector around
				this.passOn(info, info.vector, info.codeName, (short) 1);

			else // ith iteration (i > 1); directly decrypt whether I am the next root
				this.queue.sendMessageToSelf(new DecryptVectorRequest<C,E>(null, info.remove(), info.self));
			
		}  else if (msgType.equals(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE)){
			
			DFSgenerationWithOrder.DFSorderOutputMessage msgCast = (DFSgenerationWithOrder.DFSorderOutputMessage) msg;
			
			String myVar = msgCast.getVar();

			VariableInfo info = infos.get(myVar);
			if(info == null){
				info = new VariableInfo(myVar);
				infos.put(myVar, info);
			}
			//Store information
			info.order = msgCast.getTrueOrder();
			info.declaredOrder = msgCast.getDeclaredOrder();
			
		} else if (msgType.equals(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE)){
			
			VarNbrMsg msgCast = (VarNbrMsg) msg;
			
			String myVar = msgCast.getDest();

			VariableInfo info = infos.get(myVar);
			if(info == null){
				info = new VariableInfo(myVar);
				infos.put(myVar, info);
			}
			
			//Store information
			info.total = msgCast.getTotal();
			
			// Create the random permutation used to shuffle the vectors
			info.permutation = new int [info.total];
			for (int i = info.total - 1; i >= 0; i--) 
				info.permutation[i] = i;
			for (int i = 0; i < info.total - 1; i++) {
				int j = i + this.rand.nextInt(info.total - i);
				
				// Swap the entries at indexes i and j
				int tmp = info.permutation[i];
				info.permutation[i] = info.permutation[j];
				info.permutation[j] = tmp;
			}
			
		} else if (msgType.equals(CollaborativeDecryption.CRYPTO_SCHEME_TYPE)){
			
			CollaborativeDecryption.CryptoSchemeMsg<C,E,?> msgCast = (CollaborativeDecryption.CryptoSchemeMsg<C,E,?>) msg;
						
			String myVar = msgCast.getDest();
			
			//Store information
			VariableInfo info = infos.get(myVar);
			
			info.cs = msgCast.getCryptoScheme();
			info.createVector(info.order, info.declaredOrder, info.total); 
			info.codeName = Integer.toHexString(rand.nextInt());
			
			//If this variable has already received a rerooting request
			if(info.waitCrypto){
				assert info.vector.size() == info.total : info.vector.size() + " != " + info.total;
				this.passOn(info, info.vector, info.codeName, (short) 1);
				info.waitCrypto = false;
			}		
			
			//If this variable has already received a vector to shuffle and send
			if(info.waitingVector != null){
				for(Iterator<VectorMsg<C,E>> iter = info.waitingVector.iterator();iter.hasNext();)
					this.processVectorMsg(info, iter.next());
				info.waitingVector = null;
			}
			
			
		} else if (msgType.equals(CollaborativeDecryption.VECTOR_OUTPUT_TYPE)){
			
			CollaborativeDecryption.VectorOutput<C> msgCast = (CollaborativeDecryption.VectorOutput<C>) msg;
			String myVar = msgCast.getDest();
			C head = msgCast.getOutput();
			VariableInfo info = infos.get(myVar);
			
			// If fakeElement, decrypt the next one
			if (head.equals(this.fakeElement)) {
				
				// Tell the RerootRequester that we are skipping this iteration
				this.queue.sendMessageToSelf(new UTILpropagation.SolutionMessage<AddableInteger> (myVar, new String[] { myVar }, null));

				if (! info.vector.isEmpty()) 
					this.queue.sendMessageToSelf(new DecryptVectorRequest<C,E>(null, info.remove(), info.self));
			}
			
			else if(head.equals(rootElement)) { // new root
				
				info.root = true;
				this.queue.sendMessageToSelf(new RoutingMsg<DoneMsg> (
						SecureCircularRouting.PREVIOUS_MSG_TYPE,
						myVar,
						new DoneMsg()));
				
			} else { // not root
				assert head.equals(this.notRootElement);
				info.root = false;
				
				// Process pending Done message if any
				if (info.pendingDone != null) {
					this.notifyIn(info.pendingDone);
					info.pendingDone = null;
				}
			}
		} 
	}

	/** Processes a received vector message
	 * @param info 	the information about the destination variable of the message
	 * @param msg 	the vector message
	 */
	private void processVectorMsg(VariableInfo info, VectorMsg<C, E> msg) {

		ArrayList<E> vector = msg.getVector();
		String owner = msg.getOwner();
		short round = msg.getRound();
		
		if (round == 1) {
			
			// If the vector is mine, move to the next round
			if (owner.equals(info.codeName)) 
				round++;
			
			else // Add my fakeElements
				for (int i = info.order + 1; i <= info.declaredOrder; i++) 
					vector.set(i, info.cs.encrypt(fakeElement, fakeElement));
		}
		
		if (round > 1 && info.order == 0) // I'm the root; move to the next round
			round++;
		
		if (round == 3) // shuffle
			info.shuffle(vector);
			
		else if (round == 4 && owner.equals(info.codeName)) { // end of round 4
			info.vector = vector;
			
			// Decrypt
			this.queue.sendMessageToSelf(new DecryptVectorRequest<C,E>(null, info.remove(), info.self));
			
			return;
		}
		
		// Pass on
		this.passOn(info, vector, owner, round);
	}

	/** Re-encrypts vector messages the first time they leave the agent
	 * @see OutgoingMsgPolicyInterface#notifyOut(Message) 
	 */
	@SuppressWarnings("unchecked")
	public Decision notifyOut(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(SecureCircularRouting.PREVIOUS_MSG_TYPE) || msgType.equals(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE)) {
			
			// Ignore this message if it does not carry a vector message or if it is sent to a variable I own
			VectorMsg<C, E> innerMsg = null;
			if (msgType.equals(SecureCircularRouting.PREVIOUS_MSG_TYPE)) {
				
				RoutingMsg<Message> msgCast = (RoutingMsg<Message>) msg;
				if (msgCast.getPayload().getType().equals(VECTOR_TYPE)) { // vector message
					if (this.infos.containsKey(msgCast.getDest())) // virtual message
						return Decision.DONTCARE;
					else // physical message
						innerMsg = (VectorMsg<C, E>) msgCast.getPayload();
				} else // not a vector message
					return Decision.DONTCARE;
				
			} else if (msgType.equals(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE)) {
				
				ToLastLeafMsg msgCast = (ToLastLeafMsg) msg;
				if (msgCast.getPayload().getType().equals(VECTOR_TYPE)) { // vector message
					if (this.infos.containsKey(msgCast.getVar())) // virtual message
						return Decision.DONTCARE;
					else // physical message
						innerMsg = (VectorMsg<C, E>) msgCast.getPayload();
				} else // not a vector message
					return Decision.DONTCARE;
			}

			// Check whether this is a vector message I just created and that it has only traveled in virtual messages so far
			VariableInfo info = this.vectorSent.remove(innerMsg);
			if (info != null) {

				// Re-encrypt the vector
				ArrayList<E> vector = innerMsg.getVector();
				info.encryptVector(vector);

				return Decision.DONTCARE;
			}
		}
		
		return Decision.DONTCARE;
	}
}
