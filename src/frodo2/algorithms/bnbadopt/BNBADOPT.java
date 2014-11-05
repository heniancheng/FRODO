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

/** Classes implementing the ADOPT algorithm */
package frodo2.algorithms.bnbadopt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jdom2.Element;
import org.util.FileUtil;
import org.util.StringUtil;
import org.util.TempDataUtil;


import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.StatsReporterWithConvergence;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableDelayed;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace.Iterator;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/**
 * This class implements the BNBADOPT algorithm as described in:
 * 
 * "An Asynchronous Complete Method for Distributed Constraint Optimization" P.J. Modi et al AAMAS05
 * 
 * @author Brammert Ottens, Thomas Leaute
 * @param <Val> 	the type used for variable values
 * @param <U>	 	the type used for utility values
 *
 * @warning ADOPT only works for minimization problems, in which all costs are non-negative.
 * 
 */
public class BNBADOPT<Val extends Addable<Val>, U extends Addable<U>>
implements StatsReporterWithConvergence<Val> {
	
	/** To log or not to log */
	private static final boolean LOG = false;
	
	/** The message queue */
	private Queue queue;

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/**
	 * The type of the output messages containing the optimal assignment to a
	 * variable
	 */
	public static final String OUTPUT_MSG_TYPE = "OutputMessageBNBADOPT";

	/** The type of the message containing the assignment history */
	public static final String CONV_STATS_MSG_TYPE = "BNBADOPTConvStatsMsg";

	/** Infinite utility */
	private U infinity;

	/** For each variable all the info needed to run the algorithm */
	private HashMap<String, VariableInfo<Val, U>> infos;

	/** For each known variable, the name of the agent that owns it */
	private Map<String, String> owners = new HashMap<String, String>();

	/** Object with version specific methods */
	private Version<Val, U> adoptVersion;

	/**
	 * Variable used in order to determine whether the algorithm can be
	 * initiated or not for a particular variable
	 */
	private HashMap<String, Boolean> variableReady = new HashMap<String, Boolean>();

	/** The global assignment */
	private Map<String, Val> assignment;

	/** The utility of the solution reported to the stats gatherer */
	private U optTotalUtil;

	/** Variable that counts the number of variables that have terminated */
	private int variableReadyCounter = 0;

	/** Whether the stats reporter should print its stats */
	private boolean silent = false;
	
	/** The agent's problem */
	private DCOPProblemInterface<Val, U> problem;

	/** Whether the algorithm has been started */
	private boolean started = false;
	
	/** \c true when the convergence history is to be stored */
	private final boolean convergence;
	
	/** For each variable its assignment history */
	private HashMap<String, ArrayList<CurrentAssignment<Val>>> assignmentHistoriesMap;
	
	/** The time at which the ADOPT module is finished*/
	private long finalTime = Long.MIN_VALUE;

	/** Constructor for the stats gatherer mode
	 * @param problem 		the overall problem
	 * @param parameters 	the parameters of the module
	 */
	public BNBADOPT(Element parameters, DCOPProblemInterface<Val, U> problem) {
		this.problem = problem;
		assignment = new HashMap<String, Val>();
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<Val>>>();
		optTotalUtil = problem.getZeroUtility();
		this.convergence = false;
	}

	/**
	 * A constructor that takes in the description of the problem and the
	 * parameters of adopt
	 * 
	 * @param problem Problem description
	 * @param parameters adopt's parameters
	 * @throws Exception 	if an error occurs
	 */
	public BNBADOPT(DCOPProblemInterface<Val, U> problem, Element parameters) throws Exception {
		this.problem = problem;
		infinity = problem.getPlusInfUtility();
		
		// Extract the parameters
		String versionName = parameters.getAttributeValue("version");
		if (versionName == null)
			versionName = Original.class.getName();
		setVersion(versionName);
		
		String convergence = parameters.getAttributeValue("convergence");
		if(convergence != null)
			this.convergence = Boolean.parseBoolean(convergence);
		else
			this.convergence = false;
	}

	/**
	 * Constructor
	 * 
	 * @param problem the problem
	 * @param versionName the name of the version of ADOPT
	 * @param convergence \c true when the assignment history should be stored
	 * @throws Exception 	if an error occurs
	 */
	public BNBADOPT(DCOPProblemInterface<Val, U> problem, String versionName, boolean convergence) throws Exception {
		this.problem = problem;
		setVersion(versionName);
		infinity = problem.getPlusInfUtility();
		
		this.convergence = convergence;
	}

	/** Parses the problem */
	private void init() {

		// Extract the owners of known variables
		this.owners = problem.getOwners();

		assert this.checkAllCostsNonNeg() : "All costs in the minimization problem must be non-negative for ADOPT to work; problem: " + this.problem;

		// initialize all version independent variables
		Collection<String> variables = problem.getMyVars();
		infos = new HashMap<String, VariableInfo<Val, U>>((int) Math.ceil(variables.size() / 0.75));
		assignmentHistoriesMap = new HashMap<String, ArrayList<CurrentAssignment<Val>>>(variables.size());
		
		for (String var : variables) {
			infos.put(var, new VariableInfo<Val, U>(var,problem.getDomain(var), infinity.getZero()));
			assignmentHistoriesMap.put(var, new ArrayList<CurrentAssignment<Val>>());
			variableReady.put(var, false);
		}
		
		this.started = true;
		
		assert ! problem.maximize() : "ADOPT only works for minimization problems";
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		/// @todo Auto-generated method stub
	}

	/**
	 * @return \c true if all utilities in all spaces are non-negative, \c false
	 *         otherwise
	 * @author Thomas Leaute
	 */
	private boolean checkAllCostsNonNeg() {
		U zero = this.infinity.getZero();

		for (UtilitySolutionSpace<Val, U> space : this.problem.getSolutionSpaces()) 
			for (Iterator<Val, U> iter = space.iterator(); iter.hasNext(); ) 
				if (iter.nextUtility().compareTo(zero) < 0) 
					return false;

		return true;
	}

	/**
	 * A message holding an assignment to a variable
	 * 
	 * @param <Val> type used for variable values
	 */
	public static class AssignmentMessage< Val extends Addable<Val> >
			extends MessageWith2Payloads<String, Val> {

		/** Empty constructor used for externalization */
		public AssignmentMessage () { }

		/**
		 * Constructor
		 * 
		 * @param var the variable
		 * @param val the value assigned to the variable \a var
		 */
		public AssignmentMessage(String var, Val val) {
			super(OUTPUT_MSG_TYPE, var, val);
		}

		/** @return the variable */
		public String getVariable() {
			return this.getPayload1();
		}

		/** @return the value */
		public Val getValue() {
			return this.getPayload2();
		}
	}

	/**
	 * Helper function to set the heuristic used for preprocessing
	 * 
	 * @param versionName the name of the version class
	 * 
	 * @throws ClassNotFoundException if the version is not found
	 * @throws NoSuchMethodException if the version class does not have a constructor taking a single argument of class ADOPT
	 * @throws InstantiationException  if calling the constructor for the version failed
	 * @throws IllegalAccessException if calling the constructor for the version failed
	 * @throws InvocationTargetException if calling the constructor for the version failed
	 */
	@SuppressWarnings("unchecked")
	private void setVersion(String versionName) throws ClassNotFoundException,
			NoSuchMethodException, InstantiationException,
			IllegalAccessException, InvocationTargetException {

		Class<Version<Val, U>> heuristicClass = (Class<Version<Val, U>>) Class.forName(versionName);
		Class<?> parTypes[] = new Class[1];
		parTypes[0] = BNBADOPT.class;
		Constructor<Version<Val, U>> constructor = heuristicClass.getConstructor(parTypes);
		Object[] args = new Object[1];
		args[0] = this;
		this.adoptVersion = constructor.newInstance(args);
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> 	msgTypes = (ArrayList<String>) adoptVersion.getMsgTypes();
		msgTypes.add(DFSgeneration.OUTPUT_MSG_TYPE);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(Preprocessing.HEURISTICS_MSG_TYPE);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		return msgTypes;
	}

	/** @see StatsReporterWithConvergence#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		String type = msg.getType();


		if (type.equals(BNBADOPT.OUTPUT_MSG_TYPE)) { // in stats gatherer mode, the message containing information about an agent's assignments
			BNBADOPT.AssignmentMessage<Val> msgCast = (BNBADOPT.AssignmentMessage<Val>) msg;
			String variable = msgCast.getVariable();
			Val value = msgCast.getValue();
			assignment.put(variable, value);
			
			// If all solution message have been received, compute the total optimal cost
			if (this.assignment.size() == this.problem.getVariables().size()) {
				this.optTotalUtil = this.problem.getUtility(assignment).getUtility(0);
			}
			
			if (!silent) {
				System.out.println("var `" + variable + "' = " + value);
				
				// If all solution message have been received, display the total optimal cost
				if (this.assignment.size() == this.problem.getVariables().size()) 
					System.out.println("Total " + (this.problem.maximize() ? "maximal utility: " : "minimal cost: ") + optTotalUtil);
			}
			
			long time = queue.getCurrentMessageWrapper().getTime();
			if(finalTime < time)
				finalTime = time;

			return;
		}

		else if (type.equals(BNBADOPT.CONV_STATS_MSG_TYPE)) { // in stats gatherer mode, the message sent by a variable containing the assignment history
			StatsReporterWithConvergence.ConvStatMessage<Val> msgCast = (StatsReporterWithConvergence.ConvStatMessage<Val>)msg;
			assignmentHistoriesMap.put(msgCast.getVar(), msgCast.getAssignmentHistory());

			return;
		}

		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this has not been done yet
		if (!this.started)
			init();

		if (type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // receiving DFS tree information for ONE variable
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>) msg;

			String var = msgCast.getVar();
			VariableInfo<Val, U> variable = infos.get(var);
			DFSview<Val, U> neighbours = msgCast.getNeighbors();

			variable.setSeparator(neighbours.getParent(), neighbours.getPseudoParents());

			// set the lower neighbours
			List<String> children = neighbours.getChildren();
			List<String> pseudo = neighbours.getAllPseudoChildren();
			variable.setLowerNeighbours(children, pseudo, infinity.getZero());

			// set the constraints this variable is responsible for
			for (UtilitySolutionSpace<Val, U> space : neighbours.getSpaces()) 
				variable.storeConstraint(space);

			if (variable.isReady()) {
				adoptVersion.init(variable);
				if (variable.isSingleton()) {
					decideSingleton(variable);
				}
			} else {
				variableReady.put(variable.variableID, true);
			}

		} else if (type.equals(Preprocessing.HEURISTICS_MSG_TYPE)) { // receiving lower bounds
			BoundsMsg<Val, U> msgCast = (BoundsMsg<Val, U>) msg;

			String var = msgCast.getReceiver();
			UtilitySolutionSpace<Val, U> bounds = msgCast.getBounds();
			VariableInfo<Val, U> variable = null;
			if (var == null) {
//				System.out.println(msg);
				variable = infos.get(msgCast.getSender());
				variable.setOwnLowerBound(bounds);
			} else {
				String child = msgCast.getSender();
				variable = infos.get(var);

				variable.setLowerBoundChild(child, bounds);
			}

			if (variable.isReady()) {
				adoptVersion.init(variable);
				if (variable.isSingleton()) {
					decideSingleton(variable);
				}
			} else {
				variableReady.put(variable.variableID, true);
			}
		} else {
			adoptVersion.notify(msg);
		}

	}

	/**
	 * When this variable is isolated, immediately calculate its decision and
	 * terminate
	 * 
	 * @param variable the information about the variable
	 */
	private void decideSingleton(VariableInfo<Val, U> variable) {
		if(LOG)
			log(variable, "This is a singleton variable");
		variable.setDelta();
		variable.setCurrentAssignmentSingleton();
		if(convergence)
			assignmentHistoriesMap.get(variable.variableID).add(new CurrentAssignment<Val>(queue.getCurrentTime(), variable.currentAssignment));
		AssignmentMessage<Val> output = new AssignmentMessage<Val> (variable.variableID, variable.currentAssignment);
		queue.sendMessage(AgentInterface.STATS_MONITOR, output);
		if(convergence)
			queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(BNBADOPT.CONV_STATS_MSG_TYPE, variable.variableID, assignmentHistoriesMap.get(variable.variableID)));
		variableReadyCounter++;
		if (variableReadyCounter == infos.size()) 
			queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(OUTPUT_MSG_TYPE, this);
		queue.addIncomingMessagePolicy(CONV_STATS_MSG_TYPE, this);
	}

	/**
	 * @see frodo2.algorithms.StatsReporterWithConvergence#getAssignmentHistories()
	 */
	public HashMap<String, ArrayList<CurrentAssignment<Val>>> getAssignmentHistories() {
		return assignmentHistoriesMap;
	}
	
	/**
	 * Two contexts are compatible if they agree on the shared variables
	 * 
	 * @param context1 the first context
	 * @param context2 the second context
	 * @return \c true if the contexts are compatible and \c false otherwise
	 */
	public boolean compatible(HashMap<String, assignval<Val>> context1,
			HashMap<String, assignval<Val>> context2) {
		for (Map.Entry<String, assignval<Val>> entry : context1.entrySet()) {
			assignval<Val> val2 = context2.get(entry.getKey());
			if (!(val2 == null) && (!(entry.getValue().ID==(val2.ID))||!(entry.getValue().val).equals(val2.val))) {
				return false;
			}
		}
		return true;
	}
	
	public static  class assignval<Val>{
		public Val val;
		public long ID;
	    public assignval(Val v,long id){
			val=v;
			ID=id;
		}
	}
	
	/**
	 * update the context when receive a value
	 */
	public void PriorityMerge(String var,Val val,long id,HashMap<String,assignval<Val>> context){
		if(context.containsKey(var)&&id<=context.get(var).ID) return ;
		context.put(var, new assignval<Val>(val,id));	
	}
	
	/**
	 * update the context when receive a cost
	 */
	public void PriorityMerges(HashMap<String,assignval<Val>> getContext,VariableInfo<Val,U>variable){
		for (Entry<String, assignval<Val>> entry : getContext.entrySet()) {
			String var = entry.getKey();
			if (!variable.neighbours.containsKey(var))
			PriorityMerge(var,getContext.get(var).val,getContext.get(var).ID,variable.currentContext);
		}
	}

	/**
	 * Sends a message to the owner of the variable
	 * 
	 * @param variable the name of the destination variable
	 * @param msg the message
	 * @param messageSize the size of the message (in bytes)
	 */
	public void sendMessageToVariable(String variable, Message msg, long messageSize) {
		queue.sendMessage(owners.get(variable), msg);
	}

	/**
	 * Used to log the state of a variable
	 * 
	 * @param variable the variable info
	 * @param message the message to be logged
	 * @param <Val> 	the type used for variable values
	 * @param <U>	 	the type used for utility values
	 */
	public static <Val extends Addable<Val>, U extends Addable<U>> void log(VariableInfo<Val, U> variable, String message) {
		assert LOG;
		try {
			variable.logFile.write(message + "\n");
			variable.logFile.flush();
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}

	/**
	 * The different versions of adopt handle some things differently. This
	 * interface defines what functions the different versions should implement.
	 * 
	 * @param <Val> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	private static interface Version<Val extends Addable<Val>, U extends Serializable & Addable<U>> {

		/**
		 * This function is called when we received both the HEURISTICS_MSG_TYPE
		 * and the DFSgeneration.OUTPUT_MSG_TYPE message for the variable
		 * 
		 * @param variable the variable info
		 */
		public void init(VariableInfo<Val, U> variable);

		/**
		 * Tells ADOPT how to respond to certain messages specific to this
		 * version
		 * 
		 * @param msg the message
		 */
		public void notify( Message msg);

		/**
		 * Returns a collection with messages this version is interested in
		 * 
		 * @return a list of message id's
		 */
		public Collection<String> getMsgTypes();
	}

	/**
	 * The original adopt version
	 * 
	 * @param <Val> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	public static class Original<Val extends Addable<Val>, U extends Addable<U>>
			implements Version<Val, U> {

		/** The message type for the value message */
		public static final String VALUE_MSG_TYPE = "VALUE";


		/** The message type for the cost message */
		public static final String COST_MSG_TYPE = "COST";

		/** The message type for the terminate message */
		public static final String TERMINATE_MSG_TYPE = "TERMINATE";

		/** A link to the outer class */
		private BNBADOPT<Val, U> adopt;

		/**
		 * Constructor
		 * 
		 * @param adopt the ADOPT module
		 */
		public Original(BNBADOPT<Val, U> adopt) {
			this.adopt = adopt;
		}

		/** @see BNBADOPT.Version#init(VariableInfo) */
		public void init(VariableInfo<Val, U> variable) {
		
			
			//for every ancestors to init a value to domain[0]
			for(String var:variable.separator){
				if(var==null)break;    //root node needn't init the ancestors
				Val[] val=adopt.problem.getDomain(var);
				assignval<Val> ass=new assignval<Val>(val[0],1);
					variable.currentContext.put(var,ass);
				
			}
			// to init the assignVarID=0
			variable.assignVarID=0;
			
			Arrays.fill(variable.lbSUM, variable.zero);    //init variable.lbsum=0
			Arrays.fill(variable.ubSUM, variable.zero);    //init variable.ubsum=0

			// initialize the bounds
			for(Map.Entry<String, U> entry : variable.hChild.entrySet()) {
				for(Val d : variable.domain) {
					variable.InitChild(entry.getKey(),d);       //init the lb(c,d),ub(c,d) including the lb(c,d)=0/hchild(c,d)
					}                                           //ub(c,d)=infinity
				}
			
			if (!variable.isSingleton()) {
				variable.setDelta();
			}

			variable.InitSelf(variable);       //compute the LB UB including the initial lbsum,ubsum,delta
			if(adopt.convergence)              //and decide a value for self
				adopt.assignmentHistoriesMap.get(variable.variableID).add(new CurrentAssignment<Val>(adopt.queue.getCurrentTime(), variable.currentAssignment));

		
						
			variable.initialized = true;
			/*
			 * this is used for myself
			 * System.out.println("initiailized:");
			 */
			backTrack(variable);
		}

		/**
		 * Handle the reception of a terminate message
		 * 
		 * @param variable the recipient variable's info
		 * @param msg the message
		 * @todo check what happens when the terminate message does not contain
		 *       a new context
		 */
		public void handleTERMINATEmessage(VariableInfo<Val, U> variable, ENDmsg<Val> msg) {
			// adopt.log(variable, "Received a TERMINATE message : " +
			// msg.getSender() + " -> " + msg.getReceiver() + " {" +
			// msg.getContext() + "}");
			variable.terminate = true;
			variable.currentContext = msg.getContext();

			if(variable.lowerNeighbours.length==0){   
					variable.setDelta();
					variable.InitSelf(variable);	
			
			}else{          //the nonleaf node need to resetbounds and resetcontext
				boolean flag=reset(variable);
				// recalculate the delta for this value
				variable.setDelta();
				if(flag)variable.InitSelf(variable);
			}
			
			/*
			 * this is used for myself
			 * System.out.println(msg.getType()+"\t"+msg.getSender());
			 */
			backTrack(variable);

		}

		/**
		 * Handle the reception of a value message
		 * 
		 * @param variable the recipient variable's info
		 * @param msg the message
		 */
		public void handleVALUEmessage(VariableInfo<Val, U> variable,
				VALUEmsg<Val, U> msg) {
			String sender = msg.getSender();

			//HashMap<String,assignval<Val>> tempContext=new HashMap<String,assignval<Val>>(variable.currentContext.size());
			//tempContext.putAll(variable.currentContext);
			
			
			

				if (variable.initialized) {
					if (!variable.terminate) {
					 	// adopt.log(variable, "Received a VALUE message : " +
					    // msg.getSender() + " -> " + msg.getReceiver() + " {" +
					    // msg.getValue() + ", " + msg.getThreshold() + "}");
					    // only if you have received a value message from all your
				     	// higher priority neighbours can the UB(d) be something
					    // other than infinite
		
						HashMap<String,assignval<Val>> Tempcontext=(HashMap<String,assignval<Val>>)variable.currentContext.clone();
						adopt.PriorityMerge(sender,msg.getValue(),msg.getID(),variable.currentContext);
						//the leaf node needn't to resetbounds and resetcontext
						if(variable.lowerNeighbours.length==0){   
							if(!adopt.compatible(Tempcontext,variable.currentContext)){
								variable.setDelta();
								variable.InitSelf(variable);
							}
						
						}else{          //the nonleaf node need to resetbounds and resetcontext
							boolean flag=reset(variable);
							// recalculate the delta for this value
							variable.setDelta();
							if(flag)variable.InitSelf(variable);
						}
						
						if (sender.equals(variable.separator[0])) {
							variable.threshold = msg.getThreshold();
							}

					

					assert  variable.checkUpperBounds();


					/*
					 * this is used for myself
					 * System.out.println(msg.getType()+"\t"+msg.getSender()+"\t"+msg.getValue()+"\t"+msg.getID());
					 */
					backTrack(variable);

					}
				} else {
				variable.currentContext.put(sender, new assignval<Val>(msg.getValue(),msg.getID()));
				}
			}

		/**
		 * Handle the reception of a cost message
		 * 
		 * @param variable the recipient variable's info
		 * @param msg the message
		 */
		public void handleCOSTmessage(VariableInfo<Val, U> variable, COSTmsg<Val, U> msg) {

			String sender = msg.getSender();
			HashMap<String, assignval<Val>> context = msg.getContext();
			Val d = context.get(variable.variableID).val;
			
			if(d!=null)context.remove(variable.variableID);

				

			if (!variable.execution_terminated) {

				if (!variable.terminate) {
					adopt.PriorityMerges(context, variable);	
				    if(reset(variable))variable.InitSelf(variable);
						
					}
					
				}
			

			if (adopt.compatible(context, variable.currentContext)) {
				Integer senderPos = variable.neighbours.get(sender);

				 U newLB;
				 U newUB;
				if (d != null) {
					Integer valuePos = variable.valuePointer.get(d);
					 {
						
						 if (variable.lb.get(valuePos).get(senderPos).compareTo(msg.getLB()) < 0){
							 newLB=msg.getLB();
							 }else{newLB=variable.lb.get(valuePos).get(senderPos);}
						if(msg.getUB().compareTo(variable.ub.get(valuePos).get(senderPos)) < 0) {
							 newUB = msg.getUB();
							 }else{newUB=variable.ub.get(valuePos).get(senderPos);}
						variable.updateBounds(d, senderPos, newLB, newUB);
						variable.context.get(valuePos).set(senderPos, context);
					}
				} else {// my value is not in the context, update bounds for all
						// my values
					for (Map.Entry<Val, Integer> entry : variable.valuePointer.entrySet()) {
						int index = entry.getValue();
						if (variable.lb.get(index).get(senderPos).compareTo(msg.getLB()) < 0){
							 newLB=msg.getLB();
						}else{newLB=variable.lb.get(index).get(senderPos);}
						if(msg.getUB().compareTo(variable.ub.get(index).get(senderPos)) < 0) {
							newUB=msg.getUB();
						}else{newUB=variable.ub.get(index).get(senderPos);}						
						variable.updateBounds(entry.getKey(), senderPos, newLB, newUB);							
						variable.context.get(index).set(senderPos, context);
						}
					}
			}

			/*
			 * this is used for myself
			 * System.out.println(msg.getType()+"\t"+msg.receiver+"\t"+msg.getLB()+"\t"+msg.getUB());
			 */
			backTrack(variable);
		}


		/**
		 * Backtrack method
		 * 
		 * @param variable the variable info
		 */
		@SuppressWarnings("unchecked")
		private void backTrack(VariableInfo<Val, U> variable) {

			
			/*
			 * this is used for myself
			 */
			 if(adopt.LOG) {
				 HashMap<String, Val> TempContext=new HashMap<String, Val>(variable.currentContext.size()+1);
				  for(Map.Entry<String, assignval<Val>> entry:variable.currentContext.entrySet())
				  TempContext.put(entry.getKey(), entry.getValue().val);
				 adopt.log(variable, variable.toString());
				 adopt.log(variable,"currentContext= "+TempContext+"\n");
			 }
			  
			 
			if(variable.execution_terminated)
				return;
			
			U UBold = variable.UB;
			
			boolean valueChanged = false;
			int index=variable.valuePointer.get(variable.currentAssignment);
			if(variable.LBperD.get(index).compareTo(variable.threshold)>=0||
					variable.LBperD.get(index).compareTo(variable.UB)>=0)
			{
				if(variable.lbD.compareTo(variable.currentAssignment)!=0){
					variable.assignVarID++;
					valueChanged=true;
					
				}
				variable.currentAssignment = variable.lbD;
			//if(valueChanged)System.out.println(variable.variableID+"\t"+variable.assignVarID+"\t"+variable.currentAssignment);

			}
			
			
			//------------debug-------------
			//----------------------------------------
			String file_name="debug_log.txt";
			if(TempDataUtil.temp_bool==false)
			{
				File f=new File(file_name);
				if(f.exists()==true)
				{
					f.delete();
				}
				TempDataUtil.temp_bool=true;
			}
			
			 
			FileUtil.writeStringAppend(			
					StringUtil.formatWidths(
							new String[]{
							    "ID="+variable.variableID,
							    "Value="+variable.currentAssignment.intValue(),
							    "Threshold="+variable.threshold,
							    "LB="+variable.LB,
							    "UB="+variable.UB,
							    "context:"+variable.context.toString()}, 
							new int[]{
							    "ID=".length()+3,
							    "Value=".length()+3,
							    "Threshold=".length()+3,
							    "LB=".length()+5,
							    "UB=infinity".length(),0},
							    " ")+"\n", file_name);
			//-----------------------------------------
			//----------------------
			if (!variable.execution_terminated) {
				
				
				if (variable.terminate || ( variable.separator[0] == null&&variable.UB.compareTo(variable.LB)<=0)) {
					if(LOG)
						BNBADOPT.log(variable, "If a terminate message has been received, terminate!: " + variable.terminate);
					variable.execution_terminated = true;
					for (int i = 0; i < variable.numberOfChildren; i++) {
						HashMap<String, assignval<Val>> currentContextPlus = (HashMap<String, assignval<Val>>) variable.currentContext.clone();
						currentContextPlus.put(variable.variableID, new assignval<Val>(variable.currentAssignment,variable.assignVarID));

						ENDmsg<Val> msg = new ENDmsg<Val>(variable.variableID, variable.lowerNeighbours[i], currentContextPlus);
						long messageSize = variable.variableID.length();
						for (String var : currentContextPlus.keySet()) {
							messageSize += var.length() + 8;
						}
						adopt.sendMessageToVariable(variable.lowerNeighbours[i], msg, messageSize);
						
						if(LOG)
							BNBADOPT.log(variable, "Sending TERMINATE message to " + variable.lowerNeighbours[i]+"\t");
					}

					// If the variable is root
					if (variable.separator[0] == null) {
						AssignmentMessage<Val> output = new AssignmentMessage<Val> (variable.variableID, variable.currentAssignment);
						adopt.queue.sendMessage(AgentInterface.STATS_MONITOR, output);
					} else {
						AssignmentMessage<Val> msg = new AssignmentMessage<Val>(variable.variableID, variable.currentAssignment);
						adopt.queue.sendMessage(AgentInterface.STATS_MONITOR, msg);
					}
					
					if(adopt.convergence)
						adopt.queue.sendMessage(AgentInterface.STATS_MONITOR, new StatsReporterWithConvergence.ConvStatMessage<Val>(BNBADOPT.CONV_STATS_MSG_TYPE, variable.variableID, adopt.assignmentHistoriesMap.get(variable.variableID)));
					adopt.variableReadyCounter++;
					if (adopt.variableReadyCounter == adopt.infos.size()) 
						adopt.queue.sendMessageToSelf(new Message(AgentInterface.AGENT_FINISHED));
				}
			}

			if (!variable.execution_terminated) {
				
				if(valueChanged && adopt.convergence)
					adopt.assignmentHistoriesMap.get(variable.variableID).add(new CurrentAssignment<Val> (adopt.queue.getCurrentTime(), variable.currentAssignment));

				//maintainAllocationInvariant(variable);
				for (int i = 0; i < variable.lowerNeighbours.length; i++) {
					
					String lowerNeighbor = variable.lowerNeighbours[i];
					VALUEmsg<Val, U> msg;
					long messageSize = 0;
					if (i < variable.numberOfChildren) {
						if(LOG)
							BNBADOPT.log(variable, "Sending VALUE message (" + variable.currentAssignment + ") to " + lowerNeighbor);
						variable.childth=variable.AllocationThreshold(variable,i);
						msg = new VALUEmsg<Val, U>(variable.variableID, lowerNeighbor, variable.currentAssignment, variable.childth,variable.assignVarID);
						messageSize = variable.variableID.length() + lowerNeighbor.length() + 12;
					} else {
						if(LOG)
							BNBADOPT.log(variable, "Sending VALUE message (" + variable.currentAssignment + ") to " + lowerNeighbor);
						msg = new VALUEmsg<Val, U>(variable.variableID, lowerNeighbor, variable.currentAssignment, variable.zero.getPlusInfinity(),variable.assignVarID);
						messageSize = variable.variableID.length() + lowerNeighbor.length() + 8;
					}

					// Only sent a newly created VALUE message if it is different
					// from the previously created VALUE message. Otherwise, just
					// sent the same object
					Message lastValSent = variable.lastValueSent[i];
					if (lastValSent != null && lastValSent.equals(msg)) {
						adopt.sendMessageToVariable(lowerNeighbor, msg, messageSize);
					} else {
						adopt.sendMessageToVariable(lowerNeighbor, msg, messageSize);
						variable.lastValueSent[i] = msg;
					}
					// adopt.log(variable, "Sending a VALUE message to variable " +
					// lowerNeighbor + " : {" + msg.getValue() + ", " +
					// msg.getThreshold() + "}");
				}

				// the root node does not need to send any COST messages
				String parent = variable.separator[0];
				if (parent != null) {
					COSTmsg<Val, U> msg = new COSTmsg<Val, U>( variable.variableID, parent, variable.currentContext, variable.LB, variable.UB);
					long messageSize = variable.variableID.length() + parent.length() + 8;
					for (String var : variable.currentContext.keySet()) {
						messageSize += var.length() + 8;
					}

					adopt.sendMessageToVariable(parent, msg, messageSize);
					// adopt.log(variable, "Sending a COST message to variable "
					// + parent + " : {" + msg.getLB() + ", " + msg.getUB() +
					// ", " + msg.getContext() + "}");
				}
			}

		}
		
		

		/**
		 * Resets the bounds, child thresholds and context of any child whose
		 * context does not match the current context
		 * 
		 * @param variable the variable info
		 */
		public boolean reset(VariableInfo<Val, U> variable) {
			boolean reset=false;
			for (Val d : variable.domain) {
				int index = variable.valuePointer.get(d);
				ArrayList<HashMap<String, assignval<Val>>> context = variable.context.get(index);
				for (int i = 0; i < variable.numberOfChildren; i++) {
					String child = variable.lowerNeighbours[i];
					if (!adopt.compatible(context.get(i), variable.currentContext)) {
						variable.resetBounds(d, child, adopt.infinity);
						assert variable.lb.get(index).get(i).equals(variable.zero);
						variable.resetChildContext(d, i);
						reset=true;
					}
				}
			}
			return reset;
		}
		
		/**
		 * reset the leaf node
		 * 
		 */
		public boolean resetleaf(VariableInfo<Val, U> variable) {
			boolean reset=false;
			for (Val d : variable.domain) {
				int index = variable.valuePointer.get(d);
				for (int i = 0; i < variable.numberOfChildren; i++) {
					String child = variable.lowerNeighbours[i];
					variable.resetBounds(d, child, adopt.infinity);
					assert variable.lb.get(index).get(i).equals(variable.zero);
					reset=true;
					}
				}
			return reset;
			}
			

		/**
		 * This function checks whether the info from all children is consistent
		 * with the current context
		 * 
		 * @param variable the variable info
		 * @return \c true when consistent and \c false otherwise
		 */
		public boolean checkChildInfo(VariableInfo<Val, U> variable) {

			for (int index : variable.valuePointer.values()) {
				ArrayList<HashMap<String, assignval<Val>>> context = variable.context.get(index);
				ArrayList<U> lbs = variable.lb.get(index);
				ArrayList<U> ubs = variable.ub.get(index);
				for (int i = 0; i < variable.numberOfChildren; i++) {
					if (!adopt.compatible(variable.currentContext, context.get(i))) {
						if (!lbs.get(i).equals(variable.zero) || !ubs.get(i).equals(variable.zero.getPlusInfinity()))
							return false;
					}
				}
			}

			return true;
		}

		/** @see BNBADOPT.Version#notify(Message) */
		@SuppressWarnings("unchecked")
		public void notify(Message msg) {
			String type = msg.getType();

			if (type.equals(COST_MSG_TYPE)) {
				COSTmsg<Val, U> msgCast = (COSTmsg<Val, U>) msg;
				String var = msgCast.receiver;
				VariableInfo<Val, U> variable = adopt.infos.get(var);
				if(LOG)
					log(variable, "Received a COST message("  + msgCast.getLB() + ", " + msgCast.getUB() + ")\n" + variable.toString());
				
				// make sure that you are initialized until you start dealing
				// with this message
				if (variable.initialized && (variable.lastMessageReceived == null || !variable.lastMessageReceived.getType().equals(COST_MSG_TYPE) || !variable.lastMessageReceived.equals(msgCast))) {
					variable.lastMessageReceived = msg;
					handleCOSTmessage(variable, msgCast);
				}
			}
			if (type.equals(TERMINATE_MSG_TYPE)) {
				ENDmsg<Val> msgCast = (ENDmsg<Val>) msg;
				String var = msgCast.receiver;
				VariableInfo<Val, U> variable = adopt.infos.get(var);
				if(LOG)
					log(variable, "Received a TERMINATE message\n" + variable.toString() + "\n" + variable.lastMessageReceived);
				
				// make sure that you are initialized until you start dealing
				// with this message
				if (variable.initialized && (variable.lastMessageReceived == null || !variable.lastMessageReceived.getType().equals(TERMINATE_MSG_TYPE) || !variable.lastMessageReceived.equals(msgCast))) {
					variable.lastMessageReceived = msg;
					handleTERMINATEmessage(variable, msgCast);
				} else {
					adopt.queue.sendMessageToSelf(msgCast);
				}
			}
			if (type.equals(VALUE_MSG_TYPE)) {
				VALUEmsg<Val, U> msgCast = (VALUEmsg<Val, U>) msg;
				String var = msgCast.receiver;
				VariableInfo<Val, U> variable = adopt.infos.get(var);
				
				if(LOG)
					log(variable, "Received a VALUE message (" + msgCast.getSender() + ", " + msgCast.getValue() + ")\n" + variable.toString());
				
				if (variable.lastMessageReceived == null || !variable.lastMessageReceived.getType().equals(VALUE_MSG_TYPE) || !variable.lastMessageReceived.equals(msgCast)) {
					variable.lastMessageReceived = msg;
					handleVALUEmessage(variable, msgCast);
				}
			}

		}

		/** @see BNBADOPT.Version#getMsgTypes() */
		public Collection<String> getMsgTypes() {
			ArrayList<String> msgTypes = new ArrayList<String>();
			msgTypes.add(COST_MSG_TYPE);
			msgTypes.add(TERMINATE_MSG_TYPE);
			msgTypes.add(VALUE_MSG_TYPE);
			return msgTypes;
		}

	}

	/**
	 * Helper class that contains all the info belonging to a specific variable
	 * 
	 * @param <Val> the type used for variable values
	 * @param <U> the type used for utility values
	 */
	private static class VariableInfo<Val extends Addable<Val>, U extends Serializable & Addable<U>> {

		/** false when the variable is not initialized yet and true otherwise */
		public boolean initialized = false;		

		/** true when the variable received a terminate message from its parent */
		public boolean terminate = false;

		/** true when execution has been terminated */
		public boolean execution_terminated = false;

		/**
		 * True when the variable has received a VALUE message from all its
		 * higher priority neighbours
		 */
		//public boolean full_info = false;
		
		/** to define a mark for every value of variable */
		public long assignVarID;

		/** Counts from how many agents it has received a VALUE message */
		//public int full_info_counter = 0;

		/** The number of higher priority neighbours */
		public int nbrOfSeparators = 0;

		/**
		 * For each lower priority neighbour the last VALUE message that has
		 * been sent
		 */
		public Message[] lastValueSent;

		/**
		 * Remembers the last message that has been received. When the same
		 * message is received multiple times in a row, only the first message
		 * is processed. The other messages do not contain any new information
		 * and only generate more messages
		 */
		public Message lastMessageReceived = null;

		/** This variable's ID */
		public String variableID;

		/**
		 * A zero utility
		 */
		private U zero;

		/** The join of all constraints this variable is responsible for */
		private UtilitySolutionSpace<Val, U> space;
		
		/**
		 * This variable's a list of lower neighbours. The first m entries are
		 * its children, the rest are its pseudo children
		 */
		public String[] lowerNeighbours;

		/**
		 * The lower bounds for the children determined by the Preprocessing
		 * module
		 */
		public HashMap<String, U> hChild;
		
		public U childth;

		/**
		 * The variables own lower bounds determined by the Preprocessing module
		 */
		public UtilitySolutionSpace<Val, U> h;

		/** This variable's number of children */
		public int numberOfChildren;

		/**
		 * The variable's parent and pseudo parents, separator[0] = parent, rest
		 * is pseudo parent
		 */
		public String[] separator;

		/**
		 * A list of my neighbours and their pointers in the separator and
		 * lowerNeighbours lists
		 */
		public HashMap<String, Integer> neighbours;

		/**
		 * For each variable/value v/d combination it contains a list of lower
		 * bounds for v's children
		 */
		public ArrayList<ArrayList<U>> lb;

		/**
		 * Stores the sum of all the values in lb. Uses some more memory in
		 * order to prevent having to check the delta each time
		 */
		public U[] lbSUM;

		/**
		 * For each variable/value v/d combination it contains a list of upper
		 * bounds for v's children
		 */
		public ArrayList<ArrayList<U>> ub;

		/**
		 * Stores the sum of all the values in ub. Uses some more memory in
		 * order to prevent having to check the delta each time
		 */
		public U[] ubSUM;

		/** The current minimal upper bound */
		public U UB;

		/** For each value the upper bound */
		public ArrayList<U> UBperD;

		/** The current minimal lower bound */
		public U LB;

		/** For each value the lower bound */
		public ArrayList<U> LBperD;

		/** The value that minimizes the lower bound */
		public Val lbD;

		/** The value that minimizes the upper bound */
		public Val ubD;

		/** For each variable the current assignment */
		public Val currentAssignment;

		/**
		 * Given the current context, this map contains the utilities for each
		 * value of this variable
		 */
		public ArrayList<U> delta;

		/** For each value the position in the lb and ub arrays */
		public HashMap<Val, Integer> valuePointer;

		/** The domain of this variable */
		public ArrayList<Val> domain;

		/** The current threshold */
		public U threshold;

		/** For each value and child, its context */
		public ArrayList<ArrayList<HashMap<String, assignval<Val>>>> context;

		/** For each variable, its current context */
		public HashMap<String, assignval<Val>> currentContext;

		/** This variables private log file */
		public BufferedWriter logFile;

		/**
		 * Constructor
		 * 
		 * @param variableID the name of the variable
		 * @param domain the variable's domain
		 * @param zero the zero utility
		 */
		@SuppressWarnings("unchecked")
		public VariableInfo(String variableID, Val[] domain, U zero) {
			this.variableID = variableID;
			LBperD = new ArrayList<U>();
			UBperD = new ArrayList<U>();
			this.domain = new ArrayList<Val>();
			this.zero = zero;
			this.numberOfChildren = -1;

			lb = new ArrayList<ArrayList<U>>(domain.length);
			lbSUM = (U[]) new Addable[domain.length];
			ub = new ArrayList<ArrayList<U>>(domain.length);
			ubSUM = (U[]) new Addable[domain.length];
			context = new ArrayList<ArrayList<HashMap<String, assignval<Val>>>>();
			currentContext = new HashMap<String, assignval<Val>>();
			delta = new ArrayList<U>();
			valuePointer = new HashMap<Val, Integer>();
			neighbours = new HashMap<String, Integer>();
			hChild = new HashMap<String, U>();

			int i = 0;
			for (Val value : domain) {
				valuePointer.put(value, i);
				delta.add(zero);
				this.domain.add(value);
				context.add(new ArrayList<HashMap<String, assignval<Val>>>());
				lb.add(new ArrayList<U>());
				LBperD.add(zero);
				lbSUM[i] = zero;
				ub.add(new ArrayList<U>());
				UBperD.add(zero);
				ubSUM[i] = zero;
				i++;
			}

			currentAssignment = domain[0];
			LB = zero.getPlusInfinity();
			UB = zero.getPlusInfinity();
			if (LOG) {
				try {
					logFile = new BufferedWriter(new FileWriter("D://workspace//FRODO2//log//variable-"+variableID+".log"));
						
				} catch (IOException ex) {
					System.err.println(ex);
				}
			}
		}
		

		/**
		 * Makes this variable responsible for this constraint
		 * 
		 * @param space the constraint
		 */
		public void storeConstraint(UtilitySolutionSpace<Val, U> space) {
			
			if (this.space == null) 
				this.space = space;
			else 
				this.space = this.space.join(space);
		}

		/**
		 * Given the current context, set the utilities for each value of this
		 * variable
		 * 
		 * @todo when the deltas are set for the first time, the old LB(d) is
		 *       always smaller. Find some way to fix this!
		 */
		public void setDelta() {

			U minLB = zero.getPlusInfinity();
			U minUB = zero.getPlusInfinity();
			
			HashMap<String, Val> TempContext=new HashMap<String, Val>(this.currentContext.size()+1);
			for(Map.Entry<String, assignval<Val>> entry:this.currentContext.entrySet())
				TempContext.put(entry.getKey(), entry.getValue().val);

			/// @todo Use a sliced iterator instead? This might not be possible as the context can be incomplete. 
			for (Val d : domain) {
				TempContext.put(variableID, d);
				U cost = (this.space == null ? this.zero : this.space.getUtility(TempContext));
				int index = valuePointer.get(d);

				delta.set(index, cost);
				LBperD.set(index, lbSUM[index].add(cost));
				if (LBperD.get(index).compareTo(minLB) < 0) {
					minLB = LBperD.get(index);
					lbD = d;
				}

		
				UBperD.set(index, ubSUM[index].add(cost));
				
				if (UBperD.get(index).compareTo(minUB) < 0) {
					minUB = UBperD.get(index);
					ubD = d;
				}
			}

			LB = minLB;
			UB = minUB;
		}

		/**
		 * This function is used to find the optimal decision of a singleton
		 * agent
		 */
		public void setCurrentAssignmentSingleton() {
			U min = zero.getPlusInfinity();
			Val minD = null;
			for (Map.Entry<Val, Integer> entry : valuePointer.entrySet()) {
				U myDelta = delta.get(entry.getValue());
				if (min.compareTo(myDelta) > 0) {
					min = myDelta;
					minD = entry.getKey();
				}
			}

			if (minD == null) {
				currentAssignment = valuePointer.keySet().iterator().next();
			} else {
				currentAssignment = minD;
			}
		}

		/**
		 * Set the lower bound found by the Preprocessing module for \c child
		 * 
		 * @author Brammert Ottens, 19 mei 2009
		 * @param child		The child to whom the bounds belong
		 * @param bounds	The bounds themselves
		 */
		public void setLowerBoundChild(String child, UtilitySolutionSpace<Val, U> bounds) {
			hChild.put(child, bounds.blindProjectAll(false));
		}

		/**
		 * Set the lower bound on ones own values, as determined by the
		 * Preprocessing module
		 * 
		 * @author Brammert Ottens, 19 mei 2009
		 * @param bounds	The bounds
		 */
		public void setOwnLowerBound(UtilitySolutionSpace<Val, U> bounds) {
			h = bounds;
		}

			
		/**
		 * InitChild
		 */
		public void InitChild(String child,Val var){
		
				int childIndex = neighbours.get(child);
				int index=valuePointer.get(var);
				
				//U minChildH = hChild.get(child);
				U minChildH = zero; 
				lb.get(index).set(childIndex, minChildH);
				ub.get(index).set(childIndex,zero.getPlusInfinity() );
				lbSUM[index] = lbSUM[index].add(minChildH);
				ubSUM[index] = ubSUM[index].add(zero.getPlusInfinity());
		}
										
		/**
		 *  * to InitSelf
	     */@SuppressWarnings("unchecked")
		public void InitSelf(VariableInfo<Val,U> variable){
			
		for(Val d : domain) {
			int index = valuePointer.get(d);
			
			Val[] variable_values = (Val[]) Array.newInstance(d.getClass(), 1);
			variable_values[0] = d;
			
			U currentDelta = delta.get(index);
			U currentLB;
			if(variable.lowerNeighbours.length<=0){
				lbSUM[index]=zero;
				ubSUM[index]=zero;
			}
			currentLB=currentDelta.add(lbSUM[index]).max(h.getUtility(variable_values));
			
			LBperD.set(index, currentLB);
			
			UBperD.set(index, currentDelta.add(ubSUM[index]));
		
			
			if(LB.compareTo(currentLB) > 0) {
				LB = currentLB;
				lbD = d;
			}
			
			// If we use >, then ubD remains null when upperBound = infinity!
			if(UB.compareTo(UBperD.get(index)) >= 0) {
				UB = UBperD.get(index);
				ubD = d;
			}
		}
		currentAssignment = lbD;
		assignVarID=assignVarID+1;
		//System.out.println(variableID+"\t"+assignVarID+"\t"+currentAssignment);
		threshold=zero.getPlusInfinity();
	}
		/**
		 * to compute a threshldDist to input a value message
		 * @param variable
		 * @return
		 */
		public U AllocationThreshold(VariableInfo<Val, U> variable,int child){
			U thresholdDist=zero;
			U sum=variable.zero;
			ArrayList<U> lbTemp=variable.lb.get(variable.valuePointer.get(variable.currentAssignment));
			int index=0;
			U lbb;
			java.util.Iterator<U> iter=lbTemp.iterator();
			while(iter.hasNext()){
				lbb=iter.next();
				if(index!=child)sum=sum.add(lbb);
				index++;
			}
			
			if(variable.threshold.compareTo(variable.UB) < 0)
			{
				thresholdDist=variable.threshold.subtract(variable.getCurrentDelta()).subtract(sum);
			}else{
				thresholdDist=variable.UB.subtract(variable.getCurrentDelta()).subtract(sum);
			}
			
			return thresholdDist;
		}
				
		/**
		 * Resets the bounds for a particular value / child combination
		 * 
		 * @param d   			the value
		 * @param child 		the child
		 * @param upperBound	the upper bound
		 */
		public void resetBounds(Val d, String child, U upperBound) {
			int index = valuePointer.get(d);
			
			// reset the child bounds
//			lb.get(index).set(neighbours.get(child), hChild.get(child));
			lb.get(index).set(neighbours.get(child), zero);
			ub.get(index).set(neighbours.get(child), upperBound);

			// recalculate the global upper and lower bounds
			computeLB(d);
			computeUB(d);
		}

		/**
		 * Updates the bounds
		 * 
		 * @param value	the value
		 * @param child	the child
		 * @param newUb	the new upper bound
		 * @param newLb	the new lower bound
		 */
		public void updateBounds(Val value, int child, U newLb, U newUb) {
			Integer index = valuePointer.get(value);

			// set the new bounds
			lb.get(index).set(child, newLb);
			ub.get(index).set(child, newUb);

			// recalculate the global upper and lower bounds
			computeLB(value);
			computeUB(value);
		}

		/**
		 * Computes the sum of all child lower bounds for domain value "value"
		 * 
		 * @param value	the domain value
		 */
		@SuppressWarnings("unchecked")
		public void computeLB(Val value) {
			int index = valuePointer.get(value);
			U lbSUMlocal = zero;

			AddableDelayed<U> sum = lbSUMlocal.addDelayed();
			for (U bound : lb.get(index)) {
				sum.addDelayed(bound);
			}
			lbSUMlocal = sum.resolve();

			lbSUM[index] = lbSUMlocal;
			Val[] val = (Val[]) Array.newInstance(value.getClass(), 1);
			val[0] = value;
			
			LBperD.set(index, lbSUMlocal.add(delta.get(index)).max(h.getUtility(val)));
//			LBperD.set(index, lbSUMlocal.add(delta.get(index)));

			// update the overall lower bound for this variable
			if (lbD.equals(value) && LB.compareTo(LBperD.get(index)) != 0) {
				LB = zero.getPlusInfinity();
				for (Map.Entry<Val, Integer> entry : valuePointer.entrySet()) {
			
					U LB2 = LBperD.get(entry.getValue());
					if (LB.compareTo(LB2) > 0) {
						LB = LB2;
						lbD = entry.getKey();
					}
				}
			} else if (LB.compareTo(LBperD.get(index)) > 0) {
				LB = LBperD.get(index);
				lbD = value;
			}
		}

		/**
		 * Computes the sum of all child upper bounds for domain value "value"
		 * 
		 * @param value	the domain value
		 */
		public void computeUB(Val value) {
			int index = valuePointer.get(value);

			U ubSUMlocal = zero;

			AddableDelayed<U> sum = ubSUMlocal.addDelayed();
			for (U bound : ub.get(index)) {
				sum.addDelayed(bound);
			}
			ubSUMlocal = sum.resolve();

			ubSUM[index] = ubSUMlocal;

			UBperD.set(index, ubSUMlocal.add(delta.get(index)));


			// update the overall upper bound for this variable
			if (ubD.equals(value) && UB.compareTo(UBperD.get(index)) != 0) {
				UB = zero.getPlusInfinity();
				for (Map.Entry<Val, Integer> entry : valuePointer.entrySet()) {
					U UB2 = UBperD.get(entry.getValue());
					if (UB.compareTo(UB2) > 0) {
						UB = UB2;
						ubD = entry.getKey();
					}
				}
			} else if (UB.compareTo(UBperD.get(index)) > 0) {
				UB = UBperD.get(index);
				ubD = value;
			}
		}

		/**
		 * Resets the context for a particular value / child combination
		 * 
		 * @param d   the value
		 * @param i   the index of the child
		 */
		public void resetChildContext(Val d, int i) {
			context.get(valuePointer.get(d)).set(i, new HashMap<String, assignval<Val>>());
		}

		/**
		 * Sets the separator of this variable
		 * 
		 * @param parent		the parent (or null if any)
		 * @param pseudoParents	the list of pseudo-parents
		 */
		public void setSeparator(String parent, List<String> pseudoParents) {
			separator = new String[pseudoParents.size() + 1];
			int i = 0;
			if (parent != null) {
				separator[0] = parent;
				neighbours.put(parent, 0);
				i++;
			}

			for (int j = 0; j < pseudoParents.size(); j++) {
				separator[i] = pseudoParents.get(j);
				neighbours.put(pseudoParents.get(j), i);
				i++;
			}

			nbrOfSeparators = neighbours.size();
		}

		/**
		 * Sets the lower neighbours of this variable. The first k are the
		 * children, the rest the pseudochildren
		 * 
		 * @param children 			the list of children
		 * @param pseudoChildren	the list of pseudo-children
		 * @param zero				the zero utility
		 */
		public void setLowerNeighbours(List<String> children, List<String> pseudoChildren, U zero) {
			lowerNeighbours = new String[children.size() + pseudoChildren.size()];
			numberOfChildren = children.size();

			// add the children
			for (int index : valuePointer.values()) {
				ArrayList<U> lbs = lb.get(index);
				ArrayList<U> ubs = ub.get(index);
				ArrayList< HashMap<String, assignval<Val>> > contexts = context.get(index);

				for (int i = 0; i < numberOfChildren; i++) {
					lbs.add(zero);
					ubs.add(zero.getPlusInfinity());
					lowerNeighbours[i] = children.get(i);
					neighbours.put(children.get(i), i);
					contexts.add(i, new HashMap<String, assignval<Val>>());
				
				}
			}

			// add the pseudo children
			int i = numberOfChildren;
			for (String pseudo : pseudoChildren) {
				lowerNeighbours[i] = pseudo;
				neighbours.put(pseudo, i);
				i++;
			}

			lastValueSent = new Message[lowerNeighbours.length];

			for (int k = 0; k < lowerNeighbours.length; k++) {
				String neighbour = lowerNeighbours[k];
				assert k == neighbours.get(neighbour);
			}

		}

		/**
		 * Getter function
		 * 
		 * @return the utility of this variable for the current value it set
		 *         itself to
		 */
		public U getCurrentDelta() {
			return delta.get(valuePointer.get(currentAssignment));
		}

		/**
		 * Check to see if this variable has any neighbours
		 * 
		 * @return true when a singleton and false otherwise
		 */
		public boolean isSingleton() {
			return neighbours.size() == 0;
		}

		/**
		 * A Variable is ready to start adopt when it has received all
		 * preprocessing messages. That is, 1 message from its own preprocessing
		 * phase, and 1 for each one of its children.
		 * 
		 * @author Brammert Ottens, 29 mei 2009
		 * @return \c true when ready to start, and false otherwise
		 */
		public boolean isReady() {
			return h != null && hChild.size() == numberOfChildren;
		}

		/**
		 * This functions checks, for each value, whether the bounds add up and
		 * are equal to the overall bound
		 * 
		 * @return true when consistent and false otherwise
		 */
		public boolean checkUpperBounds() {

			for (int index : valuePointer.values()) {
				U sum = zero;
				boolean infinity = false;

				// calculate the sum of the children-reported upper bounds
				for (U bound : ub.get(index)) {
					if (bound.equals(zero.getPlusInfinity())) {
						infinity = true;
					}
					sum = sum.add(bound);
				}

				// add the value of the agent
				sum = sum.add(delta.get(index));

				if (ub.get(index).size() == 0 || sum.equals(UBperD.get(index)))
					return true;
				if (ub.get(index).size() == 0 || infinity
						|| !UBperD.get(index).equals(zero.getPlusInfinity()))
					return true;
			}
			return false;
		}

		/** @see java.lang.Object#toString() */
		public String toString() {
			String state = "";

			state +=  variableID + "\t";
			state += assignVarID+"\t";
			state += "Current assignment: " + currentAssignment + "\t";
			if(separator != null)
				state += "Parent = " + separator[0] + "\t";
			state += "number of children = " + numberOfChildren + "\n";
			state += "delta ="+delta.toString()+"\n";
	
			state += "Current LB:" + LB + "\t";
			state += "LBd is " + lbD + "\t";
			state += "Current UB:" + UB + "\t";
			state += "UBd is " + ubD + "\t";
			
			state += "\nCurrent threshold: " + threshold + "\n";
			
			state += "lb(x,d) = " + lb.toString() + "\t";
			state += "LB(d) = " + LBperD.toString() + "\n";
			
			state += "ub(x,d) = " + ub.toString() + "\t";
			state += "UB(d) = " + UBperD.toString() + "\n";
		
			return state;

		}
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @return the optimal assignments to all variables */
	public Map<String, Val> getOptAssignments() {
		return this.assignment;
	}

	/**
	 * @return the total optimal utility across all components of the constraint
	 *         graph
	 */
	public U getTotalOptUtil() {
		return this.optTotalUtil;
	}

	/**
	 * @see StatsReporterWithConvergence#getCurrentSolution()
	 */
	public Map<String, Val> getCurrentSolution() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return null;
	}
	
	/**
	 * Returns the time at which this module has finished, 
	 * determined by looking at the timestamp of the stat messages
	 * 
	 * @author Brammert Ottens, 22 feb 2010
	 * @return the time at which this module has finished
	 */
	public long getFinalTime() {
		return finalTime;
	}
}
