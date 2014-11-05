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

import java.lang.reflect.Array;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.UTILpropagation.OptUtilMessage;
import frodo2.algorithms.dpop.UTILpropagation.SolutionMessage;
import frodo2.algorithms.dpop.UTILpropagation.StatsMessage;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.UtilitySolutionSpaceLimited;
import frodo2.solutionSpaces.UtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.crypto.CryptoScheme;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.HypercubeLimited;
import frodo2.solutionSpaces.hypercube.ScalarBasicHypercube;

/** P2-DPOP's UTIL propagation module, using partially homomorphic encryption on utilities and a linear order on variables
 * 
 * This is an optimization version of the ConsistencyProp() and FeasibleValue() procedures from the following paper: 
 * Thomas Leaute and Boi Faltings. Privacy-preserving multi-agent constraint satisfaction. In Proceedings of 
 * the 2009 IEEE International Conference on PrivAcy, Security, riSk and Trust (PASSAT'09), Vancouver, 
 * British Columbia, August 29-31 2009. IEEE Computer Society Press.
 * 
 * @author Eric Zbinden, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for cleartext utility values
 * @param <E> the type used for encrypted utility values
 */
public class EncryptedUTIL < V extends Addable<V>, U extends Addable<U>, E extends AddableLimited<U, E> > 
implements StatsReporter, OutgoingMsgPolicyInterface<String> {
	
	/** The type of the token sent by the root to the last variable in the linear ordering to tell it to start UTIL propagation */
	static final String LEAF_MSG_TYPE = "StartUTIL";
	
	/** The type of the encrypted UTIL messages */
	static final String ENCRYPTED_UTIL_TYPE = "EncryptedUTIL";
	
	/** The type of the message used to share codeName with children */
	public static final String CODENAME_TYPE = VariableObfuscation.CODE_NAME_TYPE;
	
	/** A source of randomness */
	private SecureRandom rand = new SecureRandom();
	
	/** List of all codeNames used by this agent */
	private Set<String> codeNames;
	
	/** This problem */
	private DCOPProblemInterface<V, U> problem;
	
	/** The time when the last stat message has been received */
	private long finalTime;
	
	/** In stats gatherer mode, the maximum number of variables in a UTIL message */
	private int maxMsgDim = 0;
	
	/** Queue to receive messages */
	private Queue queue;
	
	/** For each variable this agent owns, its relevant information */
	private Map<String, VariableInfo> infos;
	
	/** An encrypted version of +INF */
	private E infinity;
	
	/** Whether to minimize the NCCC count, at the expense of an increase in runtime */
	private final boolean minNCCCs;
	
	/** If merging back edges is allowed (i.e. one variable is always identified by the same codename, regardless of the pseudo-child) */
	private boolean mergeBack;
	
	/** If this module has started or not */
	private boolean start = false;
	
	/** Class used for encrypted utilities */
	@SuppressWarnings("unused")
	private Class<E> encryptedUtilClass;
	
	/** A set of UTIL messages sent, used to detect when one leaves the agent, so as only to re-encrypt it then */
	private HashMap< EncrUTIL<V, U, E>, VariableInfo > utilSent = new HashMap< EncrUTIL<V, U, E>, VariableInfo > ();
	
	/** A convenience class used to store information about a variable */
	private class VariableInfo {
		
		/** The variable this info corresponds to */
		public String self = null;
		
		/** The optimal value for this variable or null if this variable doesn't know its value yet */
		public V value = null;
		
		/** The optimal utility for the corresponding component */
		private U optUtil;
		
		/** Children of this variable in the DFS */
		public List<String> children = null; 
		
		/** The list of spaces this variable is responsible for enforcing */
		public LinkedList< UtilitySolutionSpace<V, U> > localSpaces = new LinkedList< UtilitySolutionSpace<V, U> > ();
		
		/** The received solution space by a root */
		public UtilitySolutionSpaceLimited<V,U,E> finalSpace;
		
		/** The number of iterations of the algo */ 
		public int iteration = 1;
		
		/** CryptoScheme for this variable */
		public CryptoScheme<U,E,?> cs;
		
		/** Under bound value used to remember the dichotomy search of the ideal value */
		public int lowerB = 0;
		
		/** Upper bound value used to remember the dichotomy search of the ideal value */
		public int upperB;
		
		/** Map of the codeNames of all neighbors */
		public Map<String,String> cns = new HashMap<String,String> ();
		
		/** Reverse map with the codeNames of all neighbors*/
		public Map<String,String> reversetable = new HashMap<String,String> ();
		
		/** Whether this variable is the current root */
		private boolean root = false;
		
		/** Map for all domains of neighbor variables received in codeNameMsg 
		 * Clear text domain in position 0. Obfuscated domain in position 1*/
		public Map<String,V[][]> domain = new HashMap<String,V[][]> ();

		/** Constructor 
		 * @param self the variable name
		 */
		public VariableInfo (String self) {
			this.self = self;
		}
		
		/** @see java.lang.Object#toString() */
		public String toString () {
			return "Encrypted VariableInfo: "+self+"\n" +
				   "\tvalue: "+value+"\n\titeration: "+ this.iteration +"\n" +
				   "\tlocalSpaces: " + this.localSpaces;
		}
		
		/** Decodes the input variable codename
		 * @param codeName 	the codename for a neighboring variable
		 * @return 	the true name of the corresponding variable
		 */
		public String decode(String codeName){
			return reversetable.get(codeName);
		}
		
		/** Looks up the codename for a variable
		 * @param variable 	the variable
		 * @return 	the corresponding codename
		 */
		public String encode(String variable){
			return cns.get(variable);
		}
		
		/** Looks up the true domain for the input variable
		 * @param var 	the variable
		 * @return 	the corresponding true domain
		 */
		public V[] getDomain(String var){
			return domain.get(var)[0];
		}
		
		/** Returns the obfuscated domain for the input variable
		 * @param var 	the variable
		 * @return 	the corresponding obfuscated domain
		 */
		public V[] getEncryptDomain(String var){
			return domain.get(var)[1];
		}
		
		/**
		 * Set the finalSpace received by a root
		 * @param finalSpace the space received by a root in a UTIL msg
		 */
		public void setFinalSpace(UtilitySolutionSpaceLimited<V,U,E> finalSpace){
			this.finalSpace = finalSpace;
			this.upperB = this.finalSpace.getDomain(this.self).length-1;
		}
		
		/**
		 * Call this method at the end of an iteration to prepare this VariableInfo for the next iteration
		 */
		public void reset(){
			this.localSpaces = new LinkedList< UtilitySolutionSpace<V, U> > ();
			this.finalSpace = null;
			this.lowerB = 0;
			iteration++;
		}
		
		/**
		 * Set the optimal value from its index in the finalSpace
		 * @param index the index of the optimal value in the finalSpace
		 */
		public void setOptimalValue(int index){
			this.value = this.finalSpace.getDomain(this.self)[index];
		}
			 
		/** @return if this variable has already been root (and therefore found its optimal value) or not */
		public boolean hasBeenRoot(){
			return (this.value != null);
		}
		
		/**
		 * Encrypt a value with the cryptoScheme
		 * @param value the value to encrypt
		 * @return return the encrypted value
		 */
		public E encrypt(U value){		
			return cs.encrypt(value);
		}
		
		/**
		 * Encrypt compactly a value with the cryptoScheme
		 * @param value 	the value to encrypt
		 * @return return the encrypted value
		 */
		public E encryptCompact (U value){		
			return cs.encrypt(value, this.optUtil);
		}
		
		/**
		 * Reencrypt a value with the cryptoScheme
		 * @param value the value to encrypt
		 * @return return the encrypted value
		 */
		public E reencrypt(E value){
			return cs.reencrypt(value);
		}
		
		/**
		 * @param from the lower bound of the wanted interval
		 * @param to   the upper bound of the wanted interval
		 * @return the min of the values contained in the finalSpace in the interval from-to.
		 */
		public E minFromTo(int from, int to){
			
			assert this.finalSpace != null : "Final UtilitySoltutionSpace not yet received";
			assert this.finalSpace.getVariables().length == 1 : "More than one variable in the final solution space: " + this.finalSpace;
			assert from >= 0 : "ArgumentError! from = " + from;
			assert to < this.finalSpace.getDomain(this.self).length : "ArgumentError: to = " + to + " >= " + this.finalSpace.getDomain(this.self).length;
			assert from <= to : "ArgumentError! from = "+from+" / to = "+to;
			
			E min = this.finalSpace.getUtility(from);			
			for(int i = from + 1; i<=to; i++)
				min = min.min(this.finalSpace.getUtility(i));
			
			return min;
		}
	}
	
	/** Constructor from XML descriptions
	 * @param problem 		description of the problem
	 * @param parameters 	description of the parameters of EncryptedUTIL
	 * @throws ClassNotFoundException if specified utility classes are not found
	 */
	@SuppressWarnings("unchecked")
	public EncryptedUTIL (DCOPProblemInterface<V, U> problem, Element parameters) throws ClassNotFoundException {
		
		this.problem = problem;
		
		// Parse whether to optimize runtime or NCCC count
		String minNCCCs = parameters.getAttributeValue("minNCCCs");
		if (minNCCCs != null) 
			this.minNCCCs = Boolean.parseBoolean(minNCCCs);
		else 
			this.minNCCCs = false;
		
		// Parse which class should be used for utilities values
		this.problem.setUtilClass((Class<U>) AddableInteger.class);
		Class<E> eUtilClass = (Class<E>) Class.forName(parameters.getAttributeValue("encryptUtilClass"));
		this.encryptedUtilClass = eUtilClass;

		String mergeArg = parameters.getAttributeValue("mergeBack");
		if (mergeArg == null) mergeBack = false;
		else mergeBack = Boolean.parseBoolean(mergeArg);

		/// @todo Use the ProblemRescaler to support arbitrary problems. 
		assert ! problem.maximize() : "P2-DPOP currently only supports minimization problems with non-negative costs";
		
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @param problem 		the overall problem
	 * @param parameters 	the description of what statistics should be reported
	 */
	public EncryptedUTIL (Element parameters, DCOPProblemInterface<V, U> problem) {
		this.minNCCCs = false;
	}
	
	/** @see StatsReporter#reset() */
	public void reset() {
		this.infos = null;
		this.codeNames = null;
		
		this.start = false;
	}
	
	/** Initiate the module */
	private void init(){
		this.infos = new HashMap<String, VariableInfo> ();
		this.codeNames = new HashSet<String> ();
		
		this.start = true;
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) { }

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		ArrayList <String> msgTypes = new ArrayList <String> (1);
		msgTypes.add(UTILpropagation.UTIL_STATS_MSG_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}
	
	/** @return the maximum number of variables in a UTIL message (in stats gatherer mode only) */
	public Integer getMaxMsgDim () {
		return this.maxMsgDim;
	}
	
	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (11);
		
		// Incoming messages
		types.add(AgentInterface.START_AGENT);
		types.add(CollaborativeDecryption.CRYPTO_SCHEME_TYPE);
		types.add(CollaborativeDecryption.OUTPUT_TYPE);
		types.add(DFSgeneration.OUTPUT_MSG_TYPE);
		types.add(DFSgenerationWithOrder.OUTPUT_MSG_TYPE);
		types.add(SecureCircularRouting.DELIVERY_MSG_TYPE);
		types.add(OptUtilMsg.COMP_OPT_UTIL_MSG_TYPE);
		types.add(CODENAME_TYPE);
		types.add(AgentInterface.AGENT_FINISHED);
		
		// Outgoing messages
		types.add(SecureCircularRouting.PREVIOUS_MSG_TYPE);
		types.add(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE);
		
		return types;
	}

	/** @see StatsReporter#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {

		String msgType = msg.getType();

		if (msgType.equals(AgentInterface.AGENT_FINISHED)) {
			
			this.reset();
			return;
			
		} else if (msgType.equals(UTILpropagation.UTIL_STATS_MSG_TYPE)){  //stat gather mode
			
			this.maxMsgDim = Math.max(this.maxMsgDim, ((StatsMessage) msg).getMsgDim());

			return;

		}

		if(!start)	init();
			
		if (msgType.equals(DFSgenerationWithOrder.OUTPUT_MSG_TYPE)){ // temporary DFS
			
			//Retrieve information from received msg
			DFSgeneration.MessageDFSoutput<V, U> msgCast = (DFSgeneration.MessageDFSoutput<V, U>) msg;
			String var = msgCast.getVar();
			DFSview<V, U> myRelation = msgCast.getNeighbors();
			
			VariableInfo info = infos.get(var);
			if (info == null) {
				info = new VariableInfo(var);
				infos.put(var, info);
			}

			//Info about children and utilitySpace
			List<String> children = myRelation.getChildren();
			info.children = children;
			
			// Check if this variable is isolated
			if (this.problem.getNbrNeighbors(var) == 0) {
				
				// Record the spaces
				for (UtilitySolutionSpace<V, U> space : myRelation.getSpaces()) 
					this.record(space, info);
				
				// Find the optimal value to the variable
				U utility = this.findOptimalValueForIsolatedVar(info);
				this.sendOutput(utility, info.value, info.self);
			}
			
		} else if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)){
			
			//Retrieve information from received msg
			DFSgeneration.MessageDFSoutput<V, U> msgCast = (DFSgeneration.MessageDFSoutput<V, U>) msg;
			String var = msgCast.getVar();
			DFSview<V, U> myRelation = msgCast.getNeighbors();
			
			VariableInfo info = infos.get(var);
			if (info == null) {
				info = new VariableInfo(var);
				infos.put(var, info);
			}

			if (myRelation == null) { // DFS output reset message
				info.root = false;
				info.cns = null;
				return;
			}
			
			//Info about children and utilitySpace
			List<String> children = myRelation.getChildren();
			info.children = children;
			HashSet<String> varsBelow = new HashSet<String> (children);
			varsBelow.addAll(myRelation.getAllPseudoChildren());
			for (UtilitySolutionSpace<V, U> space : myRelation.getSpaces()) 
				this.record(space, info);
			
			// If we are minimizing NCCCs, we should already compute the join of all local spaces while waiting for the UTIL message from the next variable in the linear ordering
			if (this.minNCCCs && !info.localSpaces.isEmpty()) {
				UtilitySolutionSpace<V, U> firstSpace = info.localSpaces.removeFirst();
				UtilitySolutionSpace<V, U>[] otherSpaces = info.localSpaces.toArray(new UtilitySolutionSpace [info.localSpaces.size()]);
				UtilitySolutionSpace<V, U> join = firstSpace.joinMinNCCCs(otherSpaces);
				info.localSpaces.clear();
				firstSpace = null;
				info.localSpaces.add(join);
			}

			//Generate codeNames and send them to all (pseudo-)children
			int neighbors = this.problem.getNbrNeighbors(var);
			if (neighbors > 0){
				
				//Initiate internal map for this variable
				String codeName;
				if(info.cns == null){
					info.cns = new HashMap<String,String>(neighbors+1);
					info.reversetable = new HashMap<String,String>(neighbors+1);
					info.domain = new HashMap<String,V[][]>(neighbors+1);
				}		
				
				if(mergeBack){
					// Generate CodeName and store it
					codeName = generateCodeName();
					info.reversetable.put(codeName, var);
					info.cns.put(var, codeName);		
					
					// Generate Domain and store it
					V[] trueDom = this.problem.getDomain(var);
					V[][] domPair = (V[][]) Array.newInstance(trueDom.getClass(), 2);
					domPair[0] = trueDom;
					domPair[1] = this.randomDomain(var);
					info.domain.put(var, domPair);
					
					//Send to all (pseudo-)children
					for(String neighbour : varsBelow){
						this.queue.sendMessage(this.problem.getOwner(neighbour),
											   new VarCodenameMsg<V> (var, neighbour, codeName, domPair));
					}
				} else {
					
					// Choose unique codenames for each (pseudo-)child
					for(String neighbour : varsBelow){
						
						codeName = generateCodeName();
						info.reversetable.put(codeName, var);
						//Useless to store its own codeName in CNS
						
						// Generate Domain and store it
						V[] trueDom = this.problem.getDomain(var);
						V[][] domPair = (V[][]) Array.newInstance(trueDom.getClass(), 2);
						domPair[0] = trueDom;
						domPair[1] = this.randomDomain(var);
						if (info.domain.get(var) == null) info.domain.put(var, domPair); //Used once to store clearText domain
						
						//Send
						this.queue.sendMessage(this.problem.getOwner(neighbour), 
											   new VarCodenameMsg<V> (var,neighbour,codeName,domPair));
					}
				}
			}
			
			// Check if this variable is the root
			info.root = (myRelation.getParent() == null);
			
			if(info.root) { // tell the last variable in the linear ordering to start UTIL propagation
				if (children.isEmpty()) // I am also the last (isolated) variable
					this.queue.sendMessageToSelf(new LeafMsg ());
				else 
					this.queue.sendMessageToSelf(new RoutingMsg<LeafMsg> (SecureCircularRouting.PREVIOUS_MSG_TYPE, info.self, new LeafMsg ()));
			}
			
		} else if (msgType.equals(SecureCircularRouting.DELIVERY_MSG_TYPE)){
			
			DeliveryMsg<Message> msgCast = (DeliveryMsg<Message>) msg;
						
			//Received msgPaylod
			Message inner = msgCast.getMessage();
			String innerType = inner.getType();
			if (innerType.equals(LEAF_MSG_TYPE)) { // a message telling the recipient variable that it is the last variable in the linear ordering
				
				// Start UTIL propagation
				this.projectAndSend(null, infos.get(msgCast.getDest()));
			}
			
			else if(innerType.equals(ENCRYPTED_UTIL_TYPE)){
				
				//Received UTIL
				EncrUTIL<V,U,E> util = (EncrUTIL<V,U,E>) inner;
				this.utilSent.remove(util);
				String myVar = msgCast.getDest();
				UtilitySolutionSpaceLimited<V,U,E> space = util.getSpace();

				//Decrypt all known codeNames
				VariableInfo info = infos.get(myVar);
				ArrayList<String> myCodenames = new ArrayList<String>();
				for(String codeName : space.getVariables()){
					
					String clearText = info.decode(codeName);
					if(clearText != null){
						//IF we are in mergeBack mode, or this clearText is not myVar's own codeName
						if(mergeBack || ! clearText.equals(myVar)){
							space.renameVariable(codeName, clearText);
							space.setDomain(clearText, info.getDomain(clearText));
						} else {
							myCodenames.add(codeName);
						}
					}
				}
				
				//Special decryption of own variable without mergeBack
				if(!mergeBack){
					
					int size = myCodenames.size();
					switch (size) {
					case 0: // my variable does not appear in the space; no decoding is needed
						break;
						
					case 1: // my variable appears only once; decoding is easy
						space.renameVariable(myCodenames.get(0), myVar);
						space.setDomain(myVar, info.getDomain(myVar));
						break;
						
					default: // my variable appears multiple times; decoding requires a space composition
						
						// Construct an single-entry array containing my variable's domain
						V[] domain = info.getDomain(myVar);
						int domSize = domain.length;
						V[][] domains = (V[][]) Array.newInstance(domain.getClass(), 1);
						domains[0] = domain;
						
						// Construct the space that contains the values of the codenamed variables as function of my variable
						ArrayList<V>[] arrays = new ArrayList [domSize];
						for(int i=0;i<domSize;i++){
							ArrayList<V> array = new ArrayList<V>();
						
							for(String cx : myCodenames){
								array.add(space.getDomain(cx)[i]);
							}			
							arrays[i] = array;
						}	
						
						BasicUtilitySolutionSpace<V, ArrayList<V>> sub =
							new BasicHypercube<V, ArrayList<V>>(new String[]{myVar}, domains, arrays, null);	
						
						// Perform the substitution of variables
						space = (UtilitySolutionSpaceLimited<V,U,E>) space.compose(myCodenames.toArray(new String [size]), sub);
					}
				}			
				
				//Compute and propagate UTIL
				projectAndSend(space, info);
				
			}
			
		} else if (msgType.equals(CollaborativeDecryption.CRYPTO_SCHEME_TYPE)){
			
			CollaborativeDecryption.CryptoSchemeMsg<U, E, ?> msgCast = (CollaborativeDecryption.CryptoSchemeMsg<U, E, ?>) msg;
			
			CryptoScheme<U,E,?> cs = msgCast.getCryptoScheme();
			String myVar = msgCast.getDest();
			
			VariableInfo info = infos.get(myVar);
			if(info == null){
				info = new VariableInfo(myVar);
				infos.put(myVar,info);
			}
			info.cs = cs;
			
			//Create encrypted PlusInfinity
			this.infinity = cs.encrypt(this.problem.getPlusInfUtility());
			
		} else if (msgType.equals(CollaborativeDecryption.OUTPUT_TYPE)){
			
			CollaborativeDecryption.DecryptionOutput<U> msgCast = (CollaborativeDecryption.DecryptionOutput<U>) msg;
			
			U min1 = msgCast.getPayload1();
			U min2 = msgCast.getPayload2();
			String myVar = msgCast.dest();
			VariableInfo info = infos.get(myVar);
	
			//Compare both minima and cut range of possible value by half. Inner is the ancient middle point
			int inner = (info.lowerB + info.upperB) / 2;			
			boolean min1IsMin; //boolean that says if min1 is smaller than min2
			if (min1.equals(min2)) 
				min1IsMin = this.rand.nextBoolean(); // when the two mins are equal, pick one at random
			else 
				min1IsMin = (min1.compareTo(min2) < 0);
			int rangeSize;
				
			if(min1IsMin) rangeSize = inner - info.lowerB + 1;
			else rangeSize = info.upperB - inner;
			
			if(rangeSize == 1){ // range = 1 mean, we found the real min !
				if(min1IsMin){
					info.setOptimalValue(info.lowerB);
					this.sendOutput(min1, info.value, info.self);
				} else {
					info.setOptimalValue(info.upperB);
					this.sendOutput(min2, info.value, info.self);
				}
				
			} else { // we still need to perform a round of decryption in order to find the real min
				//update the bounder
				if(min1IsMin) info.upperB = inner;
				else info.lowerB = inner+1;
				
				//new round of decryption. newInner is the new middle point
				int newInner = (info.lowerB + info.upperB) / 2;
				
				E newMin1 = info.minFromTo(info.lowerB, newInner);
				E newMin2 = info.minFromTo(newInner+1, info.upperB);
				this.queue.sendMessageToSelf(new DecryptRequest<U, E>(null, newMin1, null, newMin2, myVar));				
			}	
			
		} else if (msgType.equals(OptUtilMsg.COMP_OPT_UTIL_MSG_TYPE)){
			
			OptUtilMsg<U> msgCast = (OptUtilMsg<U>) msg;
			
			VariableInfo info = infos.get(msgCast.getDest());
			info.optUtil = msgCast.getUtil();
			for(String child : info.children)
				queue.sendMessage(this.problem.getOwner(child), new OptUtilMsg<U> (child, info.optUtil));
			
		} else if (msgType.equals(CODENAME_TYPE)){
			
			VarCodenameMsg<V> msgCast = (VarCodenameMsg<V>) msg;
			
			String sender = msgCast.getSender();
			String myVar = msgCast.getReceiver();
			String codeName = msgCast.getCodeName();
			V[][] domain = (V[][]) msgCast.getDomains();
			//Store information
			VariableInfo info = infos.get(myVar);
			if (info == null) {
				info = new VariableInfo(myVar);
				infos.put(myVar, info);
			}
			info.domain.put(sender, domain);
			info.cns.put(sender, codeName);
			info.reversetable.put(codeName, sender);
			
		}
	}
	
	/** Projects out a variable and sends the result to its parent (if any) or sends a reroot request
	 * @param info information about the variable to be projected out
	 * @param space the space received in EncryptedUTIL (and null if the variable is a leaf)
	 */
	@SuppressWarnings("unchecked")
	private void projectAndSend(UtilitySolutionSpaceLimited<V,U,E> space, VariableInfo info) {
				
		if(! info.localSpaces.isEmpty()){ // this variable is responsible for some spaces
			
			if(space != null) {
				if (this.minNCCCs) {
					// All local spaces have already been minNCCC-joined, so we can use the (more efficient) normal join
					assert info.localSpaces.size() == 1 : "Local spaces have not been minNCCC-joined";
					space = space.join(info.localSpaces.removeFirst());
					
				} else {
					// Join all local spaces with the normal join
					UtilitySolutionSpace<V, U> join = info.localSpaces.removeFirst();
					if (!info.localSpaces.isEmpty()) {
						join = join.join(info.localSpaces.toArray(new UtilitySolutionSpace [info.localSpaces.size()]));
						info.localSpaces.clear();
					}
					
					space = space.join(join);
				}
				
			} else {
				// Join all local spaces
				UtilitySolutionSpace<V, U> join = info.localSpaces.removeFirst();
				if (!info.localSpaces.isEmpty()) {
					assert !this.minNCCCs : "The local spaces have not been minNCCC-joined";
					join = join.join(info.localSpaces.toArray(new UtilitySolutionSpace [info.localSpaces.size()]));
					info.localSpaces.clear();
				}
				
				assert join.getNumberOfSolutions() < Integer.MAX_VALUE : "A HypercubeLimited can only contain up to 2^31-1 solutions";
				E[] encryptUtil = (E[]) Array.newInstance(this.infinity.getClass(), (int) join.getNumberOfSolutions());
				E compInf = this.infinity;

				if (info.iteration == 1) { // we don't know the optimal utility yet

					int i = 0;
					for(Iterator<V,U>  iter = join.iterator(); iter.hasNext(); i++)
						encryptUtil[i] = info.encrypt(iter.nextUtility());

				} else { // we know the optimal utility

					int i = 0;
					for(Iterator<V,U>  iter = join.iterator(); iter.hasNext(); i++)
						encryptUtil[i] = info.encryptCompact(iter.nextUtility());

					compInf = info.encryptCompact(this.problem.getPlusInfUtility());
				}

				//create encrypted UtilitySolutionSpace
				space = new HypercubeLimited<V, U, E> (join.getVariables(), join.getDomains(), encryptUtil, compInf);
			}
		}
			
		//Is root
		if (info.root){

			//begin decryption
			this.askDecrypt(space, info);

		} else {
			//Project the variable
			if(info.hasBeenRoot()) space = space.slice(info.self, info.value);
			else space = space.min(info.self);

			//propagate UTIL
			this.sendToParent(info.self, space);
		}		
	}
	
	/** 
	 * @param varInfo 	the info for an isolated variable
	 * @return Compute the minimal utility for an isolated variable
	 */
	@SuppressWarnings("unchecked")
	private U findOptimalValueForIsolatedVar(VariableInfo varInfo){
		
		if (varInfo.localSpaces.isEmpty()) { // unconstrained variable
			varInfo.value = this.problem.getDomain(varInfo.self)[0]; // return the first value in the variable's domain
			return this.problem.getZeroUtility();
		}
		
		// Join all local spaces
		UtilitySolutionSpace<V, U> join = varInfo.localSpaces.removeFirst();
		if (!varInfo.localSpaces.isEmpty()) {
			if (this.minNCCCs) 
				join = join.joinMinNCCCs(varInfo.localSpaces.toArray(new UtilitySolutionSpace [varInfo.localSpaces.size()]));
			else 
				join = join.join(varInfo.localSpaces.toArray(new UtilitySolutionSpace [varInfo.localSpaces.size()]));
			varInfo.localSpaces.clear();
		}
		
		// Project out this variable
		assert join.getNumberOfVariables() == 1 : 
			"Isolated variable " + varInfo.self + " has space expressed over several variables: " + join;
		ProjOutput<V, U> projOutput = join.projectAll(false);
		varInfo.value = projOutput.assignments.getUtility(0).get(0);
		return projOutput.space.getUtility(0);
	}
	
	/**
	 * Find out the optimal value of a variable
	 * @param space 	the utilitySolutionSpaceLimited
	 * @param info 		the variable info
	 */
	private void askDecrypt(UtilitySolutionSpaceLimited<V,U,E> space, VariableInfo info){
		
		assert space.getNumberOfVariables() == 1 : "The root variable's space depends on more than one variables\n"+space;
		
		info.setFinalSpace(space); //store final space		
		
		int inner = (info.lowerB + info.upperB) / 2;
		E min1 = info.minFromTo(info.lowerB, inner);
		E min2 = info.minFromTo(inner+1, info.upperB);
		
		//Send decryptionRequest
		this.queue.sendMessageToSelf(new DecryptRequest<U,E>(null,min1,null,min2,info.self));
	}
	
	/** Sends a UTIL message 
	 * @param var 		the sender of the message
	 * @param space 	the content of the message
	 */
	private void sendToParent (String var, UtilitySolutionSpaceLimited<V, U, E> space) {
		
		VariableInfo info = infos.get(var);
		
		assert space != null : "SPACE NULL!!!";
		
		for(String vari : space.getVariables()){
			
			String codeName = info.encode(vari);
			if(codeName != null){
				//Encode only known variable codename 
				space.renameVariable(vari, codeName);
				space.setDomain(codeName, info.getEncryptDomain(vari));
			}
		}
		
		//Send UTIL msg to SecureCircularRouting			
		EncrUTIL<V,U,E> msg = new EncrUTIL<V, U, E> (space);
		this.utilSent.put(msg, info);
		queue.sendMessageToSelf(new RoutingMsg<EncrUTIL<V,U,E>>(SecureCircularRouting.PREVIOUS_MSG_TYPE, var, msg));
		
		// Send a fake message to RerootRequester so that it can keep track of the countdowns
		queue.sendMessageToSelf(new SolutionMessage<V> (var, new String[] {var}, 
				new BasicHypercube< V, ArrayList<V> > () {
			public int getNumberOfVariables () {
				return 1;
			}
		}
		));

		info.reset();
	}
	
	/** Sends the output utility to itself and to the statistics monitor
	 * @param utility 	The utility corresponding to optimal value
	 * @param optValue  the optimal value
	 * @param var 		the variable who send 
	 */
	private void sendOutput(U utility, V optValue, String var) {
		
		VariableInfo info = infos.get(var);			
		if (info.iteration == 1) // propagate the optimal utility down the DFS
			this.queue.sendMessageToSelf(new OptUtilMsg<U> (var, utility));
	
		// Send the message with the optimal cost
		OptUtilMessage<U> output = new OptUtilMessage<U> (utility, var);
		queue.sendMessageToSelf(output);
		
		// Send the message with the optimal assignment to the root
		ArrayList<V> optVal = new ArrayList<V>(1);
		optVal.add(optValue);
		queue.sendMessageToSelf(new SolutionMessage<V> (var, new String[] {var}, new ScalarBasicHypercube< V, ArrayList<V> > (optVal, null)));
		
		info.reset();
	}
	
	/**
	 * Genereate a codeName and add it to the list of the used codeNames for this agent
	 * @return a codeName
	 */
	private String generateCodeName(){
		
		String codeName;
		
		do {
			codeName = Integer.toHexString(rand.nextInt());
		} while (!codeNames.add(codeName));
		
		return codeName;
	}
	
	/** Generates a domain of random numbers
	 * @param var 	the variable for which the domain must be created
	 * @return a random domain of values in form of a array of size specified
	 * @todo Make all obfuscated domains with the same size (given as a parameter in the XML file). 
	 */
	private V[] randomDomain(String var){

		V[] values = this.problem.getDomain(var).clone();
		int size = values.length;
		HashSet<String> tempVal = new HashSet<String>();
		
		for( int i =0; i<size;i++){
			
			String obfuscatedVal;
			do{
				obfuscatedVal = Integer.toString(rand.nextInt());
			} while (! tempVal.add(obfuscatedVal)); // loop as long as adding obfuscatedVal to tempVal does not change tempVal
			
			values[i] = values[i].fromString(obfuscatedVal);
		}
		return values;
	}
	
	/** Records the input space until spaces from all children have been received
	 * @param space 	the space to be recorded
	 * @param info 		the information on the variable responsible for the space
	 */
	private void record(UtilitySolutionSpace<V, U> space, VariableInfo info) {
		
		if (info.localSpaces.isEmpty()) 
			info.localSpaces.add(space);
		
		else if (this.minNCCCs) 
			info.localSpaces.add(space); // delay the join to save memory
		
		else 
			info.localSpaces.add(info.localSpaces.removeFirst().join(space));
	}
	
	/**
	 * Returns the time at which this module has finished, 
	 * determined by looking at the timestamp of the stat messages
	 * 
	 * @return the time at which this module has finished
	 */
	public long getFinalTime() {
		return finalTime;
	}

	/** Re-encrypts UTIL messages the first time they leave the agent
	 * @see OutgoingMsgPolicyInterface#notifyOut(Message) 
	 */
	@SuppressWarnings("unchecked")
	public Decision notifyOut(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(SecureCircularRouting.PREVIOUS_MSG_TYPE) || msgType.equals(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE)) {
			
			// Ignore this message if it does not carry a UTIL message or if it is sent to a variable I own
			EncrUTIL<V, U, E> innerMsg = null;
			if (msgType.equals(SecureCircularRouting.PREVIOUS_MSG_TYPE)) {
				
				RoutingMsg<Message> msgCast = (RoutingMsg<Message>) msg;
				if (msgCast.getPayload().getType().equals(ENCRYPTED_UTIL_TYPE)) { // UTIL message
					if (this.infos.containsKey(msgCast.getDest())) // virtual message
						return Decision.DONTCARE;
					else // physical message
						innerMsg = (EncrUTIL<V, U, E>) msgCast.getPayload();
				} else // not a UTIL message
					return Decision.DONTCARE;
				
			} else if (msgType.equals(SecureCircularRouting.TO_LAST_LEAF_MSG_TYPE)) {
				
				ToLastLeafMsg msgCast = (ToLastLeafMsg) msg;
				if (msgCast.getPayload().getType().equals(ENCRYPTED_UTIL_TYPE)) { // UTIL message
					if (this.infos.containsKey(msgCast.getVar())) // virtual message
						return Decision.DONTCARE;
					else // physical message
						innerMsg = (EncrUTIL<V, U, E>) msgCast.getPayload();
				} else // not a UTIL message
					return Decision.DONTCARE;
			}

			// Check whether this is a UTIL message I just created and that it has only traveled in virtual messages so far
			VariableInfo info = this.utilSent.remove(innerMsg);
			if (info != null) {

				// Re-encrypt the space
				UtilitySolutionSpaceLimited<V,U,E> space = innerMsg.getSpace();
				for(BasicUtilitySolutionSpace.Iterator<V,E> iterator = space.iterator(); iterator.hasNext(); ) 
					iterator.setCurrentUtility(info.reencrypt(iterator.nextUtility()));

				queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsMessage (space.getNumberOfVariables()));

				return Decision.DONTCARE;
			}
		}
		
		return Decision.DONTCARE;
	}
}
