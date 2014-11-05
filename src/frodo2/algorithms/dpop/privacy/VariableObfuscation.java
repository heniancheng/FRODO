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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.dpop.UTILmsg;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.algorithms.dpop.VALUEmsg;
import frodo2.algorithms.dpop.VALUEpropagation;
import frodo2.algorithms.dpop.UTILpropagation.OptUtilMessage;
import frodo2.algorithms.dpop.VALUEpropagation.AssignmentsMessage;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.crypto.AddableBigInteger;
import frodo2.solutionSpaces.hypercube.BasicHypercube;
import frodo2.solutionSpaces.hypercube.Hypercube;

/**
 * 
 * Module that intercepts messages during UTIL and VALUE propagation and obfuscates the variable names, variable domains, and utility values
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values, in stats gatherer mode only (in normal mode, AddableBigInteger is used)
 * @author Eric Zbinden, Thomas Leaute
 *
 */
public class VariableObfuscation < V extends Addable<V>, U extends Addable<U> > implements OutgoingMsgPolicyInterface<String>, StatsReporter {
	
	/** A random Stream */
	private Random rand = new SecureRandom();
	
	/** The type of the messages containing code names */
	public static final String CODE_NAME_TYPE = "Code_Name";
	
	/** The type of obfuscated VALUE messages */
	public static final String OBFUSCATED_VALUE_TYPE = "OBFUSCATED_VALUE";
	
	/** The type of obfuscated UTIL messages */
	public static final String OBFUSCATED_UTIL_TYPE = "OBFUSCATED_UTIL";
	
	/** A Map containing assignments variable-codeName with mergeBack
	 * @note must be used only when codeName are generated or to send codeNameMsg when mergeBack=true*/
	private Map<String, String> codeNamesMerged = new HashMap<String, String>();
	
	/** A Map containing assignment variable-codeName without mergeBack: 
	 * (Pseudo-)Parent - Child - CodeName
	 * @note must be used only when codeName are generated or to send codeNameMsg when mergeBack=false*/
	private Map<String, Map<String, String>> codeNamesUnique = new HashMap<String, Map<String, String>>();
	
	/** A Map containing assignments variable-domain with mergeBack
	 * @note must be used only when domains are generated or to send codeNameMsg when mergeBack=true*/
	private Map<String, V[]> obfuscatedMergedDomains = new HashMap<String, V[]>();
	
	/** A Map containing assignments variable-domain without mergeBack:
	 * (Pseudo-)Parent - Child - Domain
 	 * @note must be used only when domains are generated or to send codeNameMsg when mergeBack=false*/
	private Map<String, Map<String, V[]>> obfuscatedUniqueDomains = new HashMap<String, Map<String, V[]>>();
	
	/** A Map containing assignments of obfuscation keys to all variables of this agent 
	 * @note must be used only when domains are generated or to send utilKey when mergeBack=true*/
	private Map<String, AddableBigInteger[]> utilKeyMerged = new HashMap<String, AddableBigInteger[]>();
	
	/** A Map containing assignments of obfuscation keys without mergeBack:
	 * (Pseudo-)Parent - Child - UtilKey
	 *  @note must be used only when domains are generated or to send utilKey when mergeBack=false*/
	private Map<String, Map<String, AddableBigInteger[]>> utilKeyUnique = new HashMap<String, Map<String, AddableBigInteger[]>>();
	
	/** Map for all variables of this agent of reverse map assignment codeName-variable of their (pseudo-)parents:
	 * Child - Parents' CodeName - (Pseudo-)Parent */
	private Map<String, Map<String, String>> decodeCodeName = new HashMap<String, Map<String, String>>();
	
	/** Map for all variables of this agent of map assignment variable-codeName of their (pseudo-)parents:
	 * Child - (Pseudo-)Parent - Parents' CodeName */
	private Map<String, Map<String, String>> encodeParent = new HashMap<String, Map<String, String>>();
	
	/** Map for all variables of this agent of map assignment variable-domain of their (pseudo-)parents:
	 * Child - (Pseudo-)Parent - Domain. Cleartext domain in [0], obfuscated domain in [1]*/
	private Map<String, Map< String, List<V[]> >> decodeDomains = new HashMap<String, Map< String, List<V[]> >>();
	
	/** Map for all variables of this agent of maps of variable-utility obfuscation key assignments
	 * Child - (Pseudo-)Parent - Utility key */
	private Map<String, Map<String, AddableBigInteger[]>> receivedUtilKey = new HashMap<String, Map<String, AddableBigInteger[]>>();
	
	/** own - (myChild OR myCodeName sent to myChild) - myUTIL */
	private Map<String, Map<String, AddableBigInteger[]>> ownUtilKey = new HashMap<String, Map<String, AddableBigInteger[]>>();
	
	/** Map with : Parent - Number of time utilities in UTILmsg must be decrypted */
	private Map<String, Integer> numDecrypt = new HashMap<String, Integer>();
	
	/** The true parent of each of this agent's variables in the DFS */
	private Map<String, String> parents = new HashMap<String, String>();
	
	/** If mergeBackEdges are allowed */
	private final boolean mergeBack;
	
	/** Long.MIN_VALUE if the problem is a maximization problem, Long.MAX_VALUE otherwise */
	private final AddableBigInteger infeasibleUtil;
	
	/** The size of the BigInteger for the utility obfuscation */
	private final int numBits;
	
	/** The size by default of the BigInteger used for utility obfuscation*/
	private final int default_numBits = 128;
	
	/** For each internal variable, the number of codeName messages it is still expecting */
	private Map<String, Integer> incomingExpected = new HashMap<String, Integer>();
	
	/** Set of all waiting msg */
	private Map<String, ArrayList<UTILmsg<V, AddableBigInteger>>> waitingMsg = new HashMap<String, ArrayList<UTILmsg<V, AddableBigInteger>>>();
	
	/** Map that says if a variable has decoded a UTIL */
	private Map<String, Boolean> hasDecoded = new HashMap<String, Boolean>();
	
	/** Map with the UTIL codeNames received (used without mergeBack). 
	 * Variable X - Children of X - CodeName(s) of X
	 * @note used only without mergeBack
	 */
	private Map<String, Map<String, ArrayList<String>>> utilContent = new HashMap<String, Map<String, ArrayList<String>>>();
	
	/** The agent's queue */
	private Queue queue;
	
	/** Whether the algorithm has already started */
	private boolean started = false;
	
	/** Problem description */
	private DCOPProblemInterface<V, U> problem;

	/** The number of variables for which the stats gatherer is still waiting for an assignment */
	private int remainingVars;

	/** The solution found to the problem */
	private HashMap<String, V> solution;

	/** Whether the stats reporter should print its stats */
	private boolean silent = false;

	/**
	 * Constructor
	 * @param problem problem description
	 * @param parameters if mergeBackEdges feature is used or not
	 */
	@SuppressWarnings("unchecked")
	public VariableObfuscation(DCOPProblemInterface<V, U> problem, Element parameters){
		this.problem = problem;
		if (problem.maximize()) infeasibleUtil = new AddableBigInteger ("-infinity");
		else infeasibleUtil = new AddableBigInteger ("infinity");
		
		String mergeArg = parameters.getAttributeValue("mergeBack");
		if (mergeArg == null) mergeBack = false;
		else mergeBack = Boolean.parseBoolean(mergeArg);
		
		String numBitArg = parameters.getAttributeValue("numBits");
		if (numBitArg == null) numBits = default_numBits;
		else numBits = Integer.parseInt(numBitArg);
		
		this.problem.setUtilClass((Class<U>) AddableBigInteger.class);
	}
	
	/** Constructor in stats gatherer mode
	 * @param params 	the parameters of this module
	 * @param problem 	the overall problem
	 */
	public VariableObfuscation (Element params, DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.remainingVars = problem.getVariables().size();
		this.solution = new HashMap<String, V> ();
		this.infeasibleUtil = null;
		
		// Not used:
		this.numBits = 0;
		this.mergeBack = false;
	}
	
	/**
	 * In mergeBack mode: Generates one codeName for the input variable \a
	 * Without mergeBack mode: Initiate general codeName map only.
	 * @param var 	the variable
	 */
	private void generateCodeName(String var){
			
		if (mergeBack){
			//create codeName in with mergeBack
			String codeName;

			do{
				codeName = randomCodeName();
			} while(codeNamesMerged.values().contains(codeName)); //avoid collision
			codeNamesMerged.put(var, codeName);
			
		} else {
			//without mergeBack, codeName are generated when a dfs msg is received
			codeNamesUnique.put(var, new HashMap<String, String>()); //initiate the map to avoid nullPointerException
		}
	}
	
	/**
	 * In mergeBack mode: Generates a range of utilityKey for the input variable \a
	 * Without mergeBack mode: Initiate general utilityKey map only
	 * @param var 	the variable
	 */
	private void generateUtilObfuscKeys(String var){
		
		if(mergeBack){
			int sizeOfArray = problem.getDomainSize(var);
			AddableBigInteger[] list = new AddableBigInteger[sizeOfArray];

			for(int i=0; i<sizeOfArray; i++){

				list[i] = new AddableBigInteger(numBits, rand);
			}
			utilKeyMerged.put(var, list);

		} else {
			//without mergeBack, codeName are generated when a dfs msg is received
			utilKeyUnique.put(var, new HashMap<String, AddableBigInteger[]>()); //initiate the map to avoid nullPointerException
		}
	}
	
	/**
	 * Generates a new codeName and new utility keys for the couple myVar-child and add it in the corresponding generals tables
	 * @param myVar the var that generates the codeName
	 * @param child the child who will receive msg that contains myVar's codeName
	 * @warning to use only without mergeBack mode
	 */
	private void generateNewCodeName(String myVar, String child){
		assert !mergeBack : "This method can not be called with mergeBack mode";
		
		//generate unique codeName
		String codeName;
		ext: while (true) {
			codeName = this.randomCodeName();

			// Check if this codename already exists
			for(Map<String, String> assign : codeNamesUnique.values()) 
				if (assign.values().contains(codeName)) 
					continue ext;
			
			break;
		}	
		//add it to codeNames map
		codeNamesUnique.get(myVar).put(child, codeName);
		//generate and store obfuscated domains
		obfuscatedUniqueDomains.get(myVar).put(child, randomDomain(problem.getDomain(myVar)));
		
		//generate new utility key
		int sizeOfArray = problem.getDomainSize(myVar);		
		AddableBigInteger[] list = new AddableBigInteger[sizeOfArray];
		
		for(int i=0; i<sizeOfArray; i++){
			
			list[i] = new AddableBigInteger(numBits, rand);
		}
		//store generated utility keys
		utilKeyUnique.get(myVar).put(child, list);
	}
	
	/** Initiates all maps for the input variable
	 * @param var the variable
	 */
	private void initiateAllMaps(String var){

		decodeCodeName.put(var, new HashMap<String, String>());
		decodeDomains.put(var, new HashMap< String, List<V[]> >());
		receivedUtilKey.put(var, new HashMap<String, AddableBigInteger[]>());
		encodeParent.put(var, new HashMap<String, String>());
		waitingMsg.put(var, new ArrayList<UTILmsg<V, AddableBigInteger>>());
		incomingExpected.put(var, -1); //integer initialized at -1, but will be assigned to true value after received DFS output
		if(!mergeBack) {
			utilContent.put(var, new HashMap<String, ArrayList<String>>());
			ownUtilKey.put(var, new HashMap<String, AddableBigInteger[]>());
		}
	}
	
	/**
	 * Create a random integer of 32 bits and return it in hex-decimal string form.
	 * @return a randomCodeName
	 */
	private String randomCodeName(){
		
		Integer randInt = rand.nextInt();
		String randString = Integer.toHexString(randInt);
		
		return randString;
	}
	
	/** Generates a domain of random numbers
	 * @param initDom 	the initial domain
	 * @return a random domain of values in form of a array of size specified
	 * @todo Make all obfuscated domains with the same size (given as a parameter in the XML file). 
	 */
	@SuppressWarnings("unchecked")
	private V[] randomDomain(V[] initDom){

		final int size = initDom.length;
		V valInstance = initDom[0];
		V[] values = (V[]) Array.newInstance(valInstance.getClass(), size);
		HashSet<Integer> tempVal = new HashSet<Integer>();
		
		for( int i =0; i<size;i++){
			
			int obfucatedVal;
			do{
				obfucatedVal = rand.nextInt();
			} while (! tempVal.add(obfucatedVal)); // loop as long as adding obfuscatedVal to tempVal does not change tempVal
			
			values[i] = valInstance.fromString(Integer.toString(obfucatedVal));
		}
		return values;
	}
	
	/**
	 * Translates (decodes or encodes) a specified value of the domain of variable \a other
	 * @param myVar the variable who calls this method
	 * @param other the variable who owns the value to translate
	 * @param value the value to be translated
	 * @param decode \c true if myVar wants to decode the value, \c false if myVar wants to encode the value
	 * @return the specified value translated (decoded or encoded) if myVar knows the translation, else returns \c null
	 */
	private V getTranslatedValue(String myVar, String other, V value, boolean decode){
		if (decode){
			//decode the value
			V[] obfuscVals = this.encodeDomain(myVar, other);
			
			if(obfuscVals != null){

				int index = -1;
				for(int i=0;i<obfuscVals.length;i++){
					if (obfuscVals[i].equals(value)) {
						index = i;
						break;
					}
				}			
				return this.decodeDomain(myVar, other)[index];

			} else return null; //don't know the values for "other"

		} else {
			//encode the value
			V[] vals = this.decodeDomain(myVar, other);
			if(vals != null){
				int index = -1;
				for(int i=0;i<vals.length;i++){
					if (vals[i].equals(value)) {
						index = i;
						break;
					}
				}
				return this.encodeDomain(myVar, other)[index];	
			} else return null; //don't know the values for "other"

		}
	}
		
	/** Initiates the variableObfuscation module */
	private void init(){
		for (String var : this.problem.getMyVars())
			this.init(var);
		
		for(String var : problem.getAllVars()) 
			hasDecoded.put(var, false);
		
		this.started = true;
	}
	
	/** Initiates all datastructures related to the input variable
	 * @param var 	the variable
	 */
	private void init (String var) {

		//initiate the codeName and 
		this.generateCodeName(var);
		
		//initiate the obfuscated domains
		this.generateObfuscatedDomains(var);
		
		//initiate the obfuscation key
		this.generateUtilObfuscKeys(var);
		
		//initiate reverse map for decode and other maps used in variableObfuscation
		this.initiateAllMaps(var);
	}
	
	/**
	 * In mergeBack mode: Generates an obfuscated domain for the input variable. 
	 * Without mergeBack mode: Only initiates the table 
	 * @param var 	the variable
	 */
	private void generateObfuscatedDomains(String var){
		
		if(mergeBack) 
			obfuscatedMergedDomains.put(var, randomDomain(problem.getDomain(var)));
		
		else {
			//Domains are generated after, when a dfs msg is received
			obfuscatedUniqueDomains.put(var, new HashMap<String, V[]>()); //initiate the map to avoid nullPointerException
		}
		
	}
	
	/** Send the delayed UTILmsg 
	 * @param myVar the var who can send its UTILmsg */
	private void sendDelayedMsg(String myVar){
		for(Message msg : waitingMsg.get(myVar)){
				
			String msgType = msg.getType();
			
			if(msgType.equals(UTILpropagation.UTIL_MSG_TYPE)){
				notifyOut(msg);
			} else if (msgType.equals(VariableObfuscation.OBFUSCATED_UTIL_TYPE)){
				notifyIn(msg);
			}	
		}
		waitingMsg.put(myVar, new ArrayList<UTILmsg<V, AddableBigInteger>>());	
		
	}

	/** 
	 * @see frodo2.communication.OutgoingMsgPolicyInterface#notifyOut(frodo2.communication.Message)
	 */
	@SuppressWarnings("unchecked")
	public Decision notifyOut(Message msg) {
		
		String msgType = msg.getType();
				
		/************************* DFS MSG *************************/
		if (msgType.equals(DFSgeneration.OUTPUT_MSG_TYPE)){
			
			//Cast
			DFSgeneration.MessageDFSoutput<V, U> msgCast = (DFSgeneration.MessageDFSoutput<V, U>) msg;
		
			//content
			String myVar = msgCast.getVar();
			DFSview<V, U> relations = msgCast.getNeighbors();		
			
			if (relations == null) { // DFS reset message
				this.init(myVar);
				return Decision.DONTCARE;
			}
			
			boolean added = false;
			
			//For all child and pseudo child, send the code name
			Set<String> below = new HashSet<String> (relations.getChildren());
			below.addAll(relations.getAllPseudoChildren());
			for (String child : below){
				
				/***** GENERATE OR GET ALL CODENAMES FOR THIS RELATION *****/
				if(!mergeBack) generateNewCodeName(myVar, child); //in mergeBack mode, codeName and domain are already generated
				
				String codeName;
				V[] dom = problem.getDomain(myVar);
				V[] obfusDom;
				AddableBigInteger[] utilityKey;
				if(mergeBack) {
					codeName = codeNamesMerged.get(myVar);
					obfusDom = this.obfuscatedMergedDomains.get(myVar);
					utilityKey = this.utilKeyMerged.get(myVar);
					
				} else {
					codeName = codeNamesUnique.get(myVar).get(child);
					obfusDom = this.obfuscatedUniqueDomains.get(myVar).get(child);
					utilityKey = this.utilKeyUnique.get(myVar).get(child);
				}
				
				/***** SEND CODENAMES *****/
				CodeNameMsg<V> oMsg = new CodeNameMsg<V> (myVar, child, codeName, dom, obfusDom, utilityKey);
				queue.sendMessage(problem.getOwner(child), oMsg); //send to child
				
				/***** ADD OWN REFERENCES IN THE DIFFERENT MAPS *****/
				if (relations.getChildren().contains(child)) {
					parents.put(child, myVar);
					if(!mergeBack)utilContent.get(myVar).put(child, new ArrayList<String>());
				}
				
				if(!mergeBack){    //without mergeBack
					//add all own key (one key per children)
					this.decodeCodeName.get(myVar).put(codeName, myVar); 
					//Trick to store all own obfuscated domains referred to the codeName of its child
					this.decodeDomains.get(myVar).put(codeName, Arrays.asList(dom, obfusDom));
					if(!added){
						//Store once the domain for myVar
						this.decodeDomains.get(myVar).put(myVar, Arrays.asList(dom, obfusDom));
						added = true;
					}
					ownUtilKey.get(myVar).put(child, utilityKey);
					//trick to store the link between its own codeName and the utility keys 
					ownUtilKey.get(myVar).put(codeName, utilityKey); 
					
				} else if (!added){ //with mergeBack
					//add key only once ('cause key is unique for every variables)
					this.addReverseMap(myVar, myVar, codeName, dom, obfusDom, utilityKey); 
					added = true;
				}
			}
						
			//Note how many times utilities must be decrypted 
			if (mergeBack) {
				numDecrypt.put(myVar, relations.getAllPseudoChildren().size()
									+ relations.getChildren().size());
			}

			//Note how many (pseudo-)parents this var has (to know how many CodeName messages must be received before UTIL propagation) 
			int parentRelation = (relations.getParent() == null ? 0 : 1) + relations.getPseudoParents().size();	
			int temp = parentRelation+1+incomingExpected.get(myVar); //1+incomingExpected = nb CodeNameMsg already received
			incomingExpected.put(myVar, temp); 
			//Send delayed msg if the expected incoming codeName msg is reached
			if(temp == 0) this.sendDelayedMsg(myVar);				
			
		/************************* UTIL MSG *************************/
		} else if (msgType.equals(UTILpropagation.UTIL_MSG_TYPE)){
			
			//cast
			UTILmsg<V, AddableBigInteger> msgCast = (UTILmsg<V, AddableBigInteger>) msg;	
						
			//content
			String myParent = msgCast.getDestination();
			String myVar = msgCast.getSender();
			
			Integer expected = incomingExpected.get(myVar);
			
			if (expected == null){ 	//This message is a UTIL that has just been decoded
				  					//Sender and receiver are owned by two different agents
				hasDecoded.put(myParent, false);
				return Decision.DONTCARE;
				
			} else if (expected == 0){ // we have received all expected codeNames
				
				if(hasDecoded.get(myParent)){         //true if myParent = me && this msg has been decoded
													  //This message is a UTIL that has just been decoded
  													  //Sender and receiver are owned by a common agent
					hasDecoded.put(myParent, false);
					return Decision.DONTCARE;
				}
					
				Hypercube<V, AddableBigInteger> space = msgCast.getSpace().toHypercube();

				//encode content
				for (String parent : space.getVariables()){
					
					String parentCodeName = this.encodeCodeName(myVar, parent);						
					if (parentCodeName != null) {
						V[] dom = space.getDomain(parent);
						V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
						doms[0] = dom;
						Hypercube<V, AddableBigInteger> e_p = 
							new Hypercube<V,AddableBigInteger> (new String[]{parent},
																doms,
																this.encryptUtility(myVar, parent),
																infeasibleUtil );
						space = (Hypercube<V, AddableBigInteger>) space.join(e_p); //set utility
						space.setDomain(parent, this.encodeDomain(myVar, parent));	//set domain
						space.renameVariable(parent, parentCodeName); //set name	
					}
				}

				//send obfuscated msg
				hasDecoded.put(myVar, false);
				ObsfUTILmsg<V> oMsg = new ObsfUTILmsg<V> (myVar, this.problem.getAgent(), myParent, space);
				this.queue.sendMessage(problem.getOwner(myParent), oMsg);
					
			} else {
				//CodeName msg are not already all received
				waitingMsg.get(myVar).add((msgCast));	
			}
				
			//discard the not obfuscated message
			return Decision.DISCARD;					
			
		/************************* VALUE MSG *************************/
		} else if (msgType.equals(VALUEpropagation.VALUE_MSG_TYPE)){
			
			//Cast
			VALUEmsg<V> msgCast = (VALUEmsg<V>) msg;	
			
			//content
			String child = msgCast.getDest();
			String x = parents.get(child);  //x = Current Variable	
			
			if(x == null)  {	//This message is a VALUE that has just been decoded
								//Sender and receiver are owned by two different agents
				return Decision.DONTCARE; 		
				
			} else if (hasDecoded.get(child)){ //This message is a VALUE that has just been decoded
				                               //Sender and receiver are owned by the same agent
				hasDecoded.put(child, false);
				return Decision.DONTCARE;
			
			} else { //This message needs to be encoded
				
				String[] vars = msgCast.getVariables();
				V[] values = msgCast.getValues();
				
				ArrayList<String> codeNames = null;
				ArrayList<String> varsInCodeNames = new ArrayList<String>();
				ArrayList<V> obfuscVals = new ArrayList<V>();
				
				if(!mergeBack) {
					codeNames = utilContent.get(x).get(child); //codeNames to send to x's child
					assert codeNames.size()!=0 : "Values for itself was not recorded from UTIL message";
				}		
				
				for (int i=0; i < vars.length; i++){
								
					if (!mergeBack && vars[i].equals(x)){ //without mergeBack and variable x
						
						//Add all necessary codeNames
						for (String c_x : codeNames){
							varsInCodeNames.add(c_x);
							obfuscVals.add(this.getTranslatedValue(x, c_x, values[i], false));
						}
						
					} else { //with mergeBack or without mergBack but with !x
						String codeName = this.encodeCodeName(parents.get(child), vars[i]);	
						
						//CodeName for this variable is known
						if (codeName != null) {
							varsInCodeNames.add(codeName);	
							V value = this.getTranslatedValue(x, vars[i], values[i], false);
							obfuscVals.add(value);
						//Unknown variable => This is already an obfuscated CodeName
						} else {
							varsInCodeNames.add(vars[i]);
							obfuscVals.add(values[i]);
						}
					}	
				}
				
				String[] cns = new String[varsInCodeNames.size()];
				cns = varsInCodeNames.toArray(cns);
				V[] vs = (V[]) Array.newInstance(obfuscVals.get(0).getClass(), obfuscVals.size());
				vs = obfuscVals.toArray(vs);

				//send obfuscated message
				ObfsVALUEmsg<V> oMsg = new ObfsVALUEmsg<V> (child, cns, vs);
				queue.sendMessage(problem.getOwner(child), oMsg);

				//discard the not obfuscated message
				return Decision.DISCARD;
			}
			
		} else if (msgType.equals(UTILpropagation.OPT_UTIL_MSG_TYPE)) { // the optimal utility for a constraint graph component
			
			OptUtilMessage<AddableBigInteger> msgCast = (OptUtilMessage<AddableBigInteger>) msg;
			AddableBigInteger util = msgCast.getUtility();
			
			// Replace large numbers by true infinities if necessary
			if (this.problem.maximize()) {
				if (util.compareTo(util.fromString(Integer.toString(Integer.MIN_VALUE))) <= 0) 
					msgCast.setPayload1(AddableBigInteger.MinInfinity.MIN_INF);
			} else if (util.compareTo(util.fromString(Integer.toString(Integer.MAX_VALUE))) >= 0) 
				msgCast.setPayload1(AddableBigInteger.PlusInfinity.PLUS_INF);
			
			return Decision.DONTCARE;
		}

		//else, we don't care
		return Decision.DONTCARE;
	}

	/**
	 * @see frodo2.communication.MessageListener#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList <String> (8);
		msgTypes.add(AgentInterface.START_AGENT);
		msgTypes.add(UTILpropagation.UTIL_MSG_TYPE);
		msgTypes.add(UTILpropagation.OPT_UTIL_MSG_TYPE);
		msgTypes.add(DFSgeneration.OUTPUT_MSG_TYPE);
		msgTypes.add(VALUEpropagation.VALUE_MSG_TYPE);
		msgTypes.add(CODE_NAME_TYPE);
		msgTypes.add(OBFUSCATED_VALUE_TYPE);
		msgTypes.add(OBFUSCATED_UTIL_TYPE);
		return msgTypes;
	}

	/**
	 * @see frodo2.communication.MessageListener#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;		
	}

	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#notifyIn(frodo2.communication.Message)
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		
		if (msgType.equals(VALUEpropagation.OUTPUT_MSG_TYPE)) { // we are in stats gatherer mode
			
			AssignmentsMessage<V> msgCast = (AssignmentsMessage<V>) msg;
			String[] vars = msgCast.getVariables();
			ArrayList<V> vals = msgCast.getValues();
			for (int i = 0; i < vars.length; i++) {
				String var = vars[i];
				V val = vals.get(i);
				if (!silent) 
					System.out.println("var `" + var + "' = " + val);
				solution.put(var, val);
			}
			
			// When we have received all messages, print out the corresponding utility. 
			if (!silent && --this.remainingVars <= 0) {
				U util = this.problem.getUtility(this.solution, false).getUtility(0);
				
				// Fix the utility for infeasible problems
				if (this.problem.maximize()) {
					if (util.compareTo(util.fromString(Integer.toString(Integer.MIN_VALUE))) <= 0) 
						util = this.problem.getMinInfUtility();
				} else if (util.compareTo(util.fromString(Integer.toString(Integer.MAX_VALUE))) >= 0) 
					util = this.problem.getPlusInfUtility();

				if (this.problem.maximize()) 
					System.out.println("Total optimal utility: " + util);
				else 
					System.out.println("Total optimal cost: " + util);
			}

			return;
		}
		
		if (! this.started) 
			this.init();
		
		/************************* CODE NAME MSG *************************/
		if (msgType.equals(CODE_NAME_TYPE)){
			
			//cast
			CodeNameMsg<V> msgCast = (CodeNameMsg<V>) msg;
			
			String dest = msgCast.getReceiver();	
			//add content in the reverse maps
			addReverseMap(msgCast.getSender(),
						  dest,
						  msgCast.getCodeName(),
						  msgCast.getCleartextDomain(),
						  msgCast.getOfuscatedDomain(),
						  msgCast.getOfuscatedUtility());	
			
			//send delayed UTILmsg
			incomingExpected.put(dest, incomingExpected.get(dest)-1);
			if (incomingExpected.get(dest)==0) this.sendDelayedMsg(dest);
			
		/************************* OBFUSC UTIL MSG *************************/
		} else if (msgType.equals(OBFUSCATED_UTIL_TYPE)){
			
			//Cast
			ObsfUTILmsg<V> msgCast = (ObsfUTILmsg<V>) msg;	
						
			String myVar = msgCast.getDestination();

			if (incomingExpected.get(myVar) == 0){ // we have already received all codeNames
				//content
				String sender = msgCast.getSender();
				UtilitySolutionSpace<V, AddableBigInteger> space = msgCast.getSpace();
					
				// Decode the variables
				ArrayList<Integer> indexes = new ArrayList<Integer>(); // indexes of variables in the space corresponding to myself
				for( String codeName : space.getVariables()){					
					
					// Check whether I know this codeName
					String var = this.decodeCodeName(myVar, codeName);
					if (var != null){					
						
						// Check whether this codeName refers to myself or to another variable higher in the DFS
						if(var.equals(myVar)){
							indexes.add(space.getIndex(codeName));
							if(!mergeBack) {
								utilContent.get(myVar).get(sender).add(codeName);
							}
							
						} else {
							V[] decodedDomain = this.decodeDomain(myVar, var);
							space.renameVariable(codeName, var); //set name
							space.setDomain(var, decodedDomain); //set domain						
						}				
					}
				}

				assert ! indexes.isEmpty() : "Received obfuscated UTIL message does not contain the codename of the destination variable";				
				
				int indexSize = indexes.size();
				if (indexSize == 1) { //Current variable is only once in the space
					String codeName = space.getVariable(indexes.get(0));
					space.renameVariable(codeName, myVar); //set name
					V[] decodedDomain = this.decodeDomain(myVar, myVar);
					space.setDomain(myVar, decodedDomain); //set domain
					
					//decrypt utilities
					AddableBigInteger[] decryptedUtil;
					if(mergeBack) decryptedUtil = this.decryptUtility(myVar, myVar);
					else decryptedUtil = this.decryptMySelfUtility(myVar, codeName);
					V[][] doms = (V[][]) Array.newInstance(decodedDomain.getClass(), 1);
					doms[0] = decodedDomain;
					UtilitySolutionSpace<V, AddableBigInteger> e_p = 
						new Hypercube<V, AddableBigInteger> (new String[]{myVar},
																doms,
																decryptedUtil,
																null);
					if (mergeBack){
						 /* Decrypt utility X times where X is the number of (pseudo-)children
						  * Because the join operation is commutative, the decryption will be done on only one UTILmsg. 
						  * The join of all UTILmsg will be in this manner decrypted. 
						  */
						 for (int i=0; i<numDecrypt.get(myVar);i++){
							 space = space.join(e_p);
						 }
						 numDecrypt.put(myVar, 0); //set numDecrypt to 0. All other UTIL won't be decrypted (the join of all UTIL will)
						
					} else space = space.join(e_p); //set utility without mergeBack			
	
				} else { //Current variable is several times in the space
					String[] codeNames = new String[indexSize];	
					V[] domain = this.decodeDomain(myVar, myVar);
					
					for(int i=0; i<indexSize;i++){
						codeNames[i] = space.getVariable(indexes.get(i));
					}
										
					for(String mySelf : codeNames){
						V[] dom = space.getDomain(mySelf);
						V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
						doms[0] = dom;
						UtilitySolutionSpace<V, AddableBigInteger> e_x = new Hypercube<V, AddableBigInteger> (new String[] {mySelf},
								   doms,
								   this.decryptMySelfUtility(myVar, mySelf),
								   infeasibleUtil);
						space = space.join(e_x); //set utility	
					}
					
					int size = domain.length;
					ArrayList<V>[] arrays = new ArrayList [size];
					for(int i=0;i<size;i++){
						ArrayList<V> array = new ArrayList<V>();
					
						for(String cx : codeNames){
							array.add(space.getDomain(cx)[i]);
						}			
						arrays[i] = array;
					}	
					
					V[][] doms = (V[][]) Array.newInstance(domain.getClass(), 1);
					doms[0] = domain;
					BasicUtilitySolutionSpace<V, ArrayList<V>> sub =
						new BasicHypercube<V, ArrayList<V>>(new String[]{myVar}, doms, arrays, null);	
					space = space.compose(codeNames, sub); //set name & domain														
				}
				
				//send decoded msg
				hasDecoded.put(myVar, true);
				UTILmsg<V, AddableBigInteger> iMsg = 
					new UTILmsg<V, AddableBigInteger>(sender, this.problem.getAgent(), myVar, space);
				this.queue.sendMessageToSelf(iMsg);
				
			} else {
				//All codeName are not already received
				waitingMsg.get(myVar).add(msgCast);
			}		
	
		/************************* OBFUSC VALUE MSG *************************/
		} else if (msgType.equals(OBFUSCATED_VALUE_TYPE)){
			
			//cast
			ObfsVALUEmsg<V> msgCast = (ObfsVALUEmsg<V>) msg;
				
			//content
			String myVar = msgCast.getDest();
			String[] varsCoded = msgCast.getVariables(); 
			V[] obfuscVals = msgCast.getValues();
			
			//Decode
			int size = varsCoded.length;
			String[] vars = new String[size];
			V[] vals = (V[]) Array.newInstance(obfuscVals.getClass().getComponentType(), size);
			for( int i=0; i<size;i++){
				
				String var = this.decodeCodeName(myVar, varsCoded[i]);			
				if(var != null) {
					vars[i] = var;
					vals[i] = this.getTranslatedValue(myVar, vars[i], obfuscVals[i], true);				
				} else {
					vars[i] = varsCoded[i];
					vals[i] = obfuscVals[i];
				}
			}
			
			//send
			hasDecoded.put(myVar, true);
			VALUEmsg<V> iMsg = new VALUEmsg<V>(myVar, vars, vals); 
			this.queue.sendMessageToSelf(iMsg);
		}		
	}
	
	/** Puts in the reverse maps the assignment codeName-variable and parent-domain for a given child variable 
	 * @param parent 			the parent that sent this message
	 * @param myVar 			the given child variable that received the assignment codeName-variable from its parent
	 * @param codeName 			The code name of the parent
	 * @param domain 			the domain of the parent variable, in cleartext
	 * @param obfuscatedDomain 	the obfuscated domain of the parent
	 * @param utilityKeys       the utility obfuscation keys of the parent
	 */
	private void addReverseMap(String parent, String myVar, String codeName, V[] domain, V[] obfuscatedDomain, AddableBigInteger[] utilityKeys){
		decodeCodeName.get(myVar).put(codeName, parent);
		encodeParent.get(myVar).put(parent, codeName);
		decodeDomains.get(myVar).put(parent, Arrays.asList(domain, obfuscatedDomain));
		receivedUtilKey.get(myVar).put(parent, utilityKeys);
	}
	
	/**
	 * Returns the variable assigned with the given codeName if and only if the variable who want to decode is a child 
	 * or a pseudo child of the coded variable. Else, return null
	 * @param myVar the variable who want to decode the codeName
	 * @param codeName the codeName to decode
	 * @return the variable which correspond to the code name or null if this knowledge is unknown
	 */
	private String decodeCodeName(String myVar, String codeName){
		return decodeCodeName.get(myVar).get(codeName);
	}
	
	/**
	 * Returns the codeName assigned with the given parent if and only if the variable who wants to decode is a child 
	 * or a pseudo child of the coded variable. Else, returns null
	 * @param myVar the variable who wants to decode the codeName
	 * @param parent the parent to encode
	 * @return the codeName which corresponds to the parent or null if this codename is unknown
	 */
	private String encodeCodeName(String myVar, String parent){
		return encodeParent.get(myVar).get(parent);
	}
	
	/**
	 * Returns the parent's cleartext domain if and only if the variable who wants to decode is a child 
	 * or wants to decode its own domain. Else, returns null
	 * @param myVar 	the variable who want to decode the domain
	 * @param parent 	the parent whose domain has to be decoded
	 * @return The parent's cleartext domain. Or null if this parent's cleartext domain is unknown.
	 */
	private V[] decodeDomain(String myVar, String parent){
		List<V[]> temp = decodeDomains.get(myVar).get(parent);
		if (temp != null) return temp.get(0);
		return null;
	}
	
	/**
	 * Returns the parent's obfuscated domain if and only if 
	 * the variable who wants to obfuscate is a child or wants to obfuscate its own domain. Else, returns null
	 * @param myVar 	the variable who wants to obfuscate the domain
	 * @param parent 	the parent whose domain has to be obfuscated
	 * @return The parent's obfuscated domain. Or \c null if this parent's domain is unknown.
	 */
	private V[] encodeDomain(String myVar, String parent){
		List<V[]> temp = decodeDomains.get(myVar).get(parent);
		if (temp != null) return temp.get(1);
		return null;
	}
	
	/**
	 * 
	 * @param myVar 	the variable that called this method
	 * @param parent 	the variable whose utility keys we want
	 * @return the tab of the utility keys
	 */
	private AddableBigInteger[] encryptUtility(String myVar, String parent){
		return this.receivedUtilKey.get(myVar).get(parent);
	}
	
	/**
	 * 
	 * @param myVar 	the variable that called this method
	 * @param child 	the variable whose utility keys we want
	 * @return the tab of the utility keys of inverse sign
	 */
	private AddableBigInteger[] decryptUtility(String myVar, String child){
		AddableBigInteger[] received = this.receivedUtilKey.get(myVar).get(child);
		AddableBigInteger[] result = new AddableBigInteger[received.length];
		for(int i=0; i<result.length; i++){
			result[i] = received[i].flipSign();
		}
		return result;
	}
	
	/**
	 * 
	 * @param myVar 	the variable that wants to decrypt its own utility keys
	 * @param child 	the child to whom myVar has sent this utilityKey
	 * @return a tab with the utility keys of inverse sign
	 */
	private AddableBigInteger[] decryptMySelfUtility(String myVar, String child){
		AddableBigInteger[] received = this.ownUtilKey.get(myVar).get(child);
		AddableBigInteger[] result = new AddableBigInteger[received.length];
		for(int i=0; i<result.length; i++){
			result[i] = received[i].flipSign();
		}
		return result;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(VALUEpropagation.OUTPUT_MSG_TYPE, this);
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.remainingVars = problem.getVariables().size();
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent  = silent;
	}
}
