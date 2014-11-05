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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.privacy.test.FakeCryptoScheme;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.VarNbrMsg;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder.DFSorderOutputMessage;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.crypto.CryptoScheme;
import frodo2.solutionSpaces.crypto.CryptoScheme.PublicKeyShare;

/** P2-DPOP's collaborative decryption module
 * 
 * This is the Decrypt() procedure from the following paper: 
 * Thomas Leaute and Boi Faltings. Privacy-preserving multi-agent constraint satisfaction. In Proceedings of 
 * the 2009 IEEE International Conference on PrivAcy, Security, riSk and Trust (PASSAT'09), Vancouver, 
 * British Columbia, August 29-31 2009. IEEE Computer Society Press.
 * 
 * @author Eric Zbinden, Thomas Leaute
 * @param <C> the type used for cleartext numbers
 * @param <E> the type used for encrypted numbers
 * @param <K> the class used for shares of the public key
 */
public class CollaborativeDecryption < C extends Addable<C>, E extends AddableLimited<C, E>, K extends PublicKeyShare > 
implements StatsReporter {
	
	/** This problem */
	private DCOPProblemInterface<?,C> problem ;
	
	/**Queue of this agent module */
	private Queue queue;
	
	/** If this module is in statistics gathering mode, print out info. By default = true */
	private boolean silent = true;
	
	/** Constructor for the CryptoScheme */
	private Constructor<? extends CryptoScheme<C,E,K>> cryptoConstr;
	
	/** Parameters to pass to the CryptoScheme constructor. If no argument need, cryptoParameter is null */
	private Element cryptoParameter;
	
	/** All variableInfos of this agent */
	private Map<String, VariableInfo> infos;
	
	/** Number of Decryption requests received for every variable. Used for statistics gathering mode*/
	private HashMap<String,Integer> requestCount;
	
	/** A source of randomness */
	private Random rand = new SecureRandom();

	/** Whether the algorithm has already started */
	private boolean started = false;
	
	/** The type of the messages containing the CryptoScheme */
	public static final String CRYPTO_SCHEME_TYPE = "CryptoScheme";
	
	/** The type of the messages corresponding to decryption requests */
	public static final String REQUEST_TYPE = "DecryptionRequest";
	
	/** The type of the messages containing the outputs of decryptions */
	public static final String OUTPUT_TYPE = "DecryptionOutput";
	
	/** The type of the messages corresponding to vector decryption requests */
	public static final String VECTOR_REQUEST_TYPE = "VectorDecryptionRequest";
	
	/** The type of the messages containing the outputs of decryptions */
	public static final String VECTOR_OUTPUT_TYPE = "VectorDecryptionOutput";
	
	/** The type of the message containing the decryption request stats */
	public static final String STAT_REQUEST_TYPE = "StatRequest";
	
	/** The type of the message used to exchange KeyPair */
	public static final String KEY_SHARE_TYPE = "KeyShareType";
	
	/** Convent class to store information about a variable */
	private class VariableInfo {
		
		/** Name of the variable corresponding to this variableInfo*/
		public String self;
		
		/** CodeNames used instead of this variable's name */
		public HashSet<String> codenames = new HashSet<String> ();
		
		/** CryptoScheme for this variable */
		public CryptoScheme<C,E,K> cs;
		
		/** The number of keys still needed to be received */
		public int keysToReceive = -1;
		
		/** The number of public key shares that need to be sent */
		public int keysToSend = -1;
		
		/**
		 * Constructor
		 * @param self the name of this variable
		 */
		public VariableInfo(String self){
			this.self = self;
		}
		
		/** @return a new codename */
		public String newCodename () {
			String codename = Integer.toHexString(rand.nextInt());
			this.codenames.add(codename);
			return codename;
		}
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString(){
			return "VariableInfo for: "+self+"\n\tCryptoScheme: "+cs+"\n\tKeyToReceive: "+this.keysToReceive;
		}
	}
	
	/** Message sent to stat gatherer with all decryption requests for an agent */
	public static class StatRequestOutput extends MessageWithPayload<HashMap<String,Integer>> implements Externalizable {
		
		/**
		 * Constructor
		 * @param requestCount the map that contains all decryption requests received by one agent 
		 */
		public StatRequestOutput(HashMap<String, Integer> requestCount){
			this.type = STAT_REQUEST_TYPE;
			this.setPayload(requestCount);
		}
		
		/**
		 * Empty Constructor
		 */
		public StatRequestOutput(){
			this.type = STAT_REQUEST_TYPE;
		}

		/** @see java.io.Externalizable#readExternal(java.io.ObjectInput)*/
		@SuppressWarnings("unchecked")
		public void readExternal(ObjectInput in) throws IOException,
				ClassNotFoundException {
			this.setPayload((HashMap<String,Integer>)in.readObject());
			
		}

		/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(this.getPayload());
		}
		
		/** @return the map in the payload message */
		public HashMap<String,Integer> getMap(){
			return this.getPayload();
		}		
	}
	
	/**
	 * Message used to transfer the cryptoScheme to others modules of this agent.
	 * @param <C> used for clear text value
	 * @param <E> used for encrypted value
	 * @param <K> the class used for shares of the public key
	 */
	public static class CryptoSchemeMsg<C extends Addable<C>, E extends AddableLimited<C,E>, K extends PublicKeyShare> 
	extends MessageWith2Payloads<CryptoScheme<C,E,K>,String> {
		
		/** Empty constructor used for externalization */
		public CryptoSchemeMsg () { }

		/**
		 * Constructor
		 * @param cs the cryptoScheme
		 * @param dest the destination of this message
		 */
		public CryptoSchemeMsg(CryptoScheme<C,E,K> cs, String dest){
			this.type = CRYPTO_SCHEME_TYPE;
			this.setPayload1(cs);
			this.setPayload2(dest);
		}
		
		/** @return the cryptoScheme */
		public CryptoScheme<C,E,K> getCryptoScheme(){
			return this.getPayload1();
		}
		
		/** @return the destination of this message */
		public String getDest(){
			return this.getPayload2();
		}
		
		/** @see MessageWith2Payloads#toString() */
		@Override
		public String toString() {
			return "Message (" + super.type + ")\n\t CryptoScheme = " + this.getCryptoScheme() + "\n\t variable = " + this.getDest();
		}
	}
	
	/**
	 * Vector decryption Output Message
	 * @param <C> class used for clear text vector element
	 */
	public static class VectorOutput<C extends Addable<C>> extends MessageWith2Payloads<C, String> {

		/** Empty constructor used for externalization */
		public VectorOutput () { }

		/**
		 * Constructor
		 * @param output the result of the decryption for the head element
		 * @param myVar the variable that sends and receives this message
		 */
		public VectorOutput(C output, String myVar){
			this.type = CollaborativeDecryption.VECTOR_OUTPUT_TYPE;
			this.setPayload1(output);
			this.setPayload2(myVar);
		}
		
		/** @return the output of the decryption */
		public C getOutput(){
			return this.getPayload1();
		}
		
		/** @return the destination of this message */
		public String getDest(){
			return this.getPayload2();
		}
	}
	
	/**
	 * Decryption Output Message
	 * @param <C> class used for clear text utility
	 */
	public static class DecryptionOutput<C extends Addable<C>> extends MessageWith3Payloads<C,C,String> {

		/** Empty constructor used for externalization */
		public DecryptionOutput () { }

		/**
		 * Constructor
		 * @param min1 a decrypted local minimum
		 * @param min2 a decrypted local minimum
		 * @param dest the destination of this message
		 */
		public DecryptionOutput(C min1, C min2, String dest){
			this.type = OUTPUT_TYPE;
			this.setPayload1(min1);
			this.setPayload2(min2);
			this.setPayload3(dest);
		}
		
		/** @return the variable destination of this message */
		public String dest(){
			return this.getPayload3();
		}
		
		/** @see frodo2.communication.MessageWith3Payloads#toString() */
		public String toString(){
			return "DECRYPTION OUTPUT:\n" +
					"\tdestination: "+dest()+"\tmin1: "+this.getPayload1()+"\tmin2: "+this.getPayload2();
		}
	}
	
	/** Constructor from XML descriptions
	 * @param problem 		description of the problem
	 * @param parameters 	description of the parameters of CollaborativeDecryption
	 * @throws ClassNotFoundException 		if the class for the CryptoScheme is not found
	 * @throws NoSuchMethodException 		if the CryptoScheme does not have a constructor that takes in an Element
	 */
	@SuppressWarnings("unchecked")
	public CollaborativeDecryption (DCOPProblemInterface<?, C> problem, Element parameters)
	throws ClassNotFoundException, NoSuchMethodException {
		
		this.problem = problem;
		
		Class<? extends CryptoScheme<C,E,K>> cryptoClass;
		
		// Create the CryptoScheme
		if (parameters == null) {
			System.err.println("Warning! No CryptoScheme specified; using the FakeCryptoScheme that does not actually protect privacy.");
			cryptoClass = (Class<? extends CryptoScheme<C, E, K>>) FakeCryptoScheme.class;
		}else {
			Element cryptoParams = parameters.getChild("cryptoScheme");
			if (cryptoParams == null) {
				System.err.println("Warning! No CryptoScheme specified; using the FakeCryptoScheme that does not actually protect privacy.");
				cryptoClass = (Class<? extends CryptoScheme<C, E, K>>) FakeCryptoScheme.class;
			} else {
				cryptoClass = (Class<? extends CryptoScheme<C, E, K>>) Class.forName(cryptoParams.getAttributeValue("className"));
				this.cryptoParameter = cryptoParams;
			}
		}
		
		this.cryptoConstr = cryptoClass.getConstructor(Element.class);
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported
	 */
	public CollaborativeDecryption(Element parameters, DCOPProblemInterface<?, C> problem) {
		this.problem = problem;

		if (parameters != null) {
			String silent = parameters.getAttributeValue("reportStats");
			if (silent != null) 
				this.silent = ! Boolean.parseBoolean(silent);
		}
		
		//initialize variables
		requestCount = new HashMap<String,Integer>(); 
		for(String var : this.problem.getVariables()){
			requestCount.put(var, 0);
		}
	}
	
	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (7);
		types.add(AgentInterface.START_AGENT);
		types.add(REQUEST_TYPE);
		types.add(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE);
		types.add(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE);
		types.add(SecureCircularRouting.DELIVERY_MSG_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		types.add(VECTOR_REQUEST_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {

		String msgType = msg.getType();
		
		//STAT GATHERING MODE
		if (msgType.equals(STAT_REQUEST_TYPE)){
			
			StatRequestOutput msgCast = (StatRequestOutput) msg;		
			HashMap<String,Integer> rc = msgCast.getMap();
			
			//Retrieve information and store it in a global map
			for(Entry<String, Integer> entry : rc.entrySet()) 
				if (this.requestCount != null) 
					requestCount.put(entry.getKey(), entry.getValue());
					
			if(!silent){
				System.out.println("Number of decryptions performed by each variable:");
				for (Entry<String,Integer> map : msgCast.getMap().entrySet()){
					System.out.println("\t"+map.getKey()+": "+map.getValue());
				}
			}
			
			return;			
		}
		
		if(! this.started )
			init();			
		
		if (msgType.equals(AgentInterface.AGENT_FINISHED)){
			
			//Send information about decryption request to stat agent
			this.queue.sendMessage(AgentInterface.STATS_MONITOR, new StatRequestOutput(this.requestCount));			
			this.reset();
			
		} else if (msgType.equals(REQUEST_TYPE)){
			
			DecryptRequest<C,E> msgCast = (DecryptRequest<C,E>) msg;
			String myVar = msgCast.decryptFor();
						
			//collect all request
			addDecryptRequest(myVar);
			
			//Send further the request
			queue.sendMessageToSelf(
					new RoutingMsg<DecryptRequest<C,E>>(
					SecureCircularRouting.PREVIOUS_MSG_TYPE,
					myVar,
					new DecryptRequest<C, E> (msgCast.getPayload1(), msgCast.initialMin1(),
									   msgCast.getPayload2(), msgCast.initialMin2(),
									   infos.get(myVar).newCodename()) ));
			
		} else if (msgType.equals(SecureCircularRouting.DELIVERY_MSG_TYPE)){
			
			DeliveryMsg<Message> msgCast = (DeliveryMsg<Message>) msg;
			String receiver = msgCast.getDest();
			
			//Received msgPaylod
			Message inner = msgCast.getMessage();
			if(inner.getType().equals(REQUEST_TYPE)){
				
				DecryptRequest<C,E> request = (DecryptRequest<C,E>) inner;
								
				String decryptFor = request.decryptFor();
				VariableInfo info = infos.get(receiver);
				E min1 = request.getPayload1();
				E min2 = request.getPayload2();
				E initialMin1 = request.initialMin1();
				E initialMin2 = request.initialMin2();
				
				if(info.codenames.remove(decryptFor)){					
					//Decrypt and send to self the answer
					queue.sendMessageToSelf(new DecryptionOutput<C>(info.cs.decrypt(initialMin1, min1),info.cs.decrypt(initialMin2, min2),receiver));
					
				} else {
										
					//Partial decrypt and send further the msg
					DecryptRequest<C,E> respond = new DecryptRequest<C,E>(
							info.cs.partialDecrypt(initialMin1, min1),
							initialMin1,
							info.cs.partialDecrypt(initialMin2, min2),
							initialMin2,
							decryptFor);
					
					queue.sendMessageToSelf(
							new RoutingMsg<DecryptRequest<C,E>>(SecureCircularRouting.PREVIOUS_MSG_TYPE, receiver, respond ));
				}	
				
			} else if (inner.getType().equals(VECTOR_REQUEST_TYPE)){
				
				DecryptVectorRequest<C,E> request = (DecryptVectorRequest<C,E>) inner;

				String decryptFor = request.getDecryptFor();
				VariableInfo info = infos.get(receiver);
				
				if(info.codenames.remove(decryptFor)){
					//Decrypt and send the answer to myself 
					queue.sendMessageToSelf(new VectorOutput<C> (info.cs.decrypt(request.getInitial(), request.getElem()), receiver));
					
				} else {
					//Partial decrypt and send the msg further
					DecryptVectorRequest<C,E> respond = new DecryptVectorRequest<C,E>(info.cs.partialDecrypt(request.getInitial(),request.getElem()), request.getInitial(), decryptFor);
					
					queue.sendMessageToSelf(
							new RoutingMsg<DecryptVectorRequest<C,E>>(SecureCircularRouting.PREVIOUS_MSG_TYPE, receiver, respond));
				}	
				
			} else if(inner.getType().equals(KEY_SHARE_TYPE)){
				
				KeyShareMsg<K> keyMsg = (KeyShareMsg<K>) inner;
				
				K key = keyMsg.getKey();
				String sender = keyMsg.getSender();
				VariableInfo info = infos.get(receiver);
				
				info.keysToReceive--;
				
				// Store the key and propagate it if needed
				if(! info.codenames.remove(sender)) {
					
					info.cs.addPublicKeyShare(key);
					
					this.queue.sendMessageToSelf(new RoutingMsg< KeyShareMsg<K> > (
							SecureCircularRouting.PREVIOUS_MSG_TYPE,
							receiver,
							new KeyShareMsg<K>(key, sender)));
				}
				
				//Send cryptoScheme to other modules of this agent if all keys have been received
				if(info.keysToReceive==0)
					this.queue.sendMessageToSelf(new CryptoSchemeMsg<C,E,K>(info.cs, info.self));			
			}
			
		} else if (msgType.equals(VECTOR_REQUEST_TYPE)){
			
			DecryptVectorRequest<C,E> msgCast = (DecryptVectorRequest<C, E>) msg;
			String myVar = msgCast.getDecryptFor();
			
			//collect all requests
			addDecryptRequest(myVar);
			
			//Send the request further
			this.queue.sendMessageToSelf(
					new RoutingMsg<DecryptVectorRequest<C,E>>(
							SecureCircularRouting.PREVIOUS_MSG_TYPE,
							myVar,
							new DecryptVectorRequest<C,E>(msgCast.getElem(), msgCast.getInitial(), infos.get(myVar).newCodename())));
			
		} else if (msgType.equals(DFSgenerationWithOrder.OUTPUT_ORDER_TYPE)) {
			
			DFSorderOutputMessage msgCast = (DFSorderOutputMessage) msg;
			String myVar = msgCast.getVar();
			VariableInfo info = this.infos.get(myVar);
			if (info != null) 
				info.keysToSend = msgCast.getDeclaredOrder() - msgCast.getTrueOrder() + 1;
			
		} else if (msgType.equals(DFSgenerationWithOrder.VARIABLE_COUNT_TYPE)){
			
			VarNbrMsg msgCast = (VarNbrMsg) msg;
			
			String myVar = msgCast.getDest();
			int total = msgCast.getTotal();
			VariableInfo info = infos.get(myVar);
			
			//Isolated variables don't need to exchange key shares, and they should only be exchanged once
			if(this.problem.getNbrNeighbors(myVar) > 0 && info.keysToReceive != 0){
				info.keysToReceive += total + 1; 
				
				//Send to previous variable my public key share(s)
				assert info.keysToSend > 0;
				for (int i = 0; i < info.keysToSend; i++) 
					this.queue.sendMessageToSelf(new RoutingMsg< KeyShareMsg<K> > (
							SecureCircularRouting.PREVIOUS_MSG_TYPE, 
							myVar, 
							new KeyShareMsg<K> (info.cs.newPublicKeyShare(), info.newCodename())));
			}
		}
	}
	
	/**
	 * Initiate the statistic gatherer
	 */
	private void init() {
		
		try{			
			infos = new HashMap<String, VariableInfo>();
			
			for(String var : this.problem.getMyVars()){
				
				if(this.problem.getNbrNeighbors(var) != 0){ // an isolated variable doesn't need a CryptoScheme and therefore no VariableInfo either
					
					VariableInfo info = new VariableInfo(var);
					infos.put(var, info);
					
					info.cs = cryptoConstr.newInstance(this.cryptoParameter);		
				}
			}
			
			//initialize request counter for my own variables
			requestCount = new HashMap<String,Integer>(); 
			for(String var : this.problem.getMyVars()){
				requestCount.put(var, 0);
			}
			
			this.started = true;	
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	/**
	 * Add one to the request counter of a variable passed in argument
	 * @param var the variable who ask a decryption
	 */
	private void addDecryptRequest(String var){
		requestCount.put(var, requestCount.get(var)+1);
	}
	
	/** @return the total number of decryption requests. Used in statistic gathering mode*/
	public int requestCount(){
		int total = 0;
		for (Integer request : requestCount.values()){
			total += request;
		}
		
		return total;
	}

	/** @see frodo2.algorithms.StatsReporter#getStatsFromQueue(frodo2.communication.Queue) */
	public void getStatsFromQueue(Queue queue) {		
		ArrayList <String> msgTypes = new ArrayList <String> (1);
		msgTypes.add(STAT_REQUEST_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}

	/** @see frodo2.algorithms.StatsReporter#reset() */
	public void reset() {
		requestCount = null;
		this.started = false;
	}

	/** @see frodo2.algorithms.StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}
	
	/** @return the mapping of the number of decryption requests. Used in statistic gathering mode*/
	public Map<String,Integer> getRequestCountMapping(){
		return this.requestCount;
	}
}
