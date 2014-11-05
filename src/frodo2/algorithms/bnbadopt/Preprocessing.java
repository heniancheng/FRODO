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

package frodo2.algorithms.bnbadopt;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;

/**
 * @author Brammert Ottens
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 * 
 * The preprocessing listener computes heuristics for the lower bounds used by bnbadopt. Currently, it implements
 * the following heuristics:
 * 
 * - trivial heuristic: set every lb to 0
 */
public class Preprocessing <Val extends Addable<Val>, U extends Addable<U> > 
implements StatsReporter {

	/** The type of the message telling the module to start */
	public static String START_MSG_TYPE = AgentInterface.START_AGENT;

	/** The type of message containing the heuristics */
	public final static String HEURISTICS_MSG_TYPE = "Heuristics";
	
	/** The type of the heuristics stats message*/
	public final static String HEURISTICS_STAT_MSG_TYPE = "Heuristics stat";

	/** A list of variables this agent owns */
	private ArrayList<String> variables = new ArrayList<String> ();
	
	/** A list of domains of each of the variables*/
	private HashMap<String, Val[]> domains;
	
	/** The heuristic that is used to generate the lower bounds*/
	private PreprocessingHeuristic<Val, U> heuristic;
	
	/** used to create the null value*/
	private U zeroObject;
	
	/** The agent's queue*/
	private Queue queue;

	/** The agent's problem */
	private DCOPProblemInterface<Val, U> problem;
	
	/** For each known variable, the name of the agent that owns it */
	private Map<String, String> owners = new HashMap<String, String> ();
	
	/** Whether the algorithm has been started */
	private boolean started = false;
	
	/** Container for the reported heuristics*/
	public HashMap<String, UtilitySolutionSpace<Val, U>> reportedHeuristics;

	/** Constructor in stats gatherer mode
	 * @param problem 	not used at this time
	 * @param e 		not used at this time 
	 */
	public Preprocessing (Element e, DCOPProblemInterface<Val, U> problem) {
		reportedHeuristics = new HashMap<String, UtilitySolutionSpace<Val, U>>();
		this.heuristic = new SimpleHeuristic<Val, U>(this);
	}
	
	/** Constructor
	 * @param variables 				the variables
	 * @param domains 					the domains
	 * @param heuristicsName 			name of the heuristics class used
	 * @param zeroObject 				the zero utility
	 * @throws Exception 	if an error occurs
	 */
	public Preprocessing (ArrayList<String> variables, HashMap<String, Val[]> domains, String heuristicsName, U zeroObject) throws Exception {
		this.variables = variables;
		this.domains = domains;
		this.zeroObject = zeroObject;
		setHeuristics(heuristicsName);
	}
	
	/** Constructor
	 * @param problem 			the agent's problem
	 * @param heuristicsName 	name of the heuristic class used
	 * @throws Exception 	if an error occurs
	 */
	public Preprocessing (DCOPProblemInterface<Val, U> problem, String heuristicsName) throws Exception {
		this.problem = problem;
		this.zeroObject = problem.getZeroUtility();
		setHeuristics(heuristicsName);
	}
	
	/** Constructor from XML descriptions
	 * @param problem 			description of the problem
	 * @param parameters 		description of the parameters of Preprocessing
	 * @throws Exception 	if an error occurs
	 */
	public Preprocessing (DCOPProblemInterface<Val, U> problem, Element parameters) throws Exception {
		this.problem = problem;
		zeroObject = problem.getZeroUtility();
		
		// Extract the parameters
		String heuristicName = parameters.getAttributeValue("heuristic");
		if (heuristicName == null) 
			heuristicName = SimpleHeuristic.class.getName();
		setHeuristics(heuristicName);
	}
	
	/** Parses the problem */
	private void init () {
		this.variables = new ArrayList<String> (problem.getMyVars());
		domains = new HashMap<String, Val[]>();
		for (String var : variables) 
			domains.put(var, problem.getDomain(var));
		
		owners = problem.getOwners();
		
		heuristic.init();
		
		this.started = true;

		assert ! problem.maximize() : "bnbadopt only works for minimization problems";
	}
	
	/** @see StatsReporter#reset() */
	public void reset() {
		/// @todo Auto-generated method stub
	}

	/** Helper function to set the heuristics used for preprocessing
	 * 
	 * @param heuristicName 	the name of the heuristic class
	 * @throws ClassNotFoundException 	if the heuristic class name is unknown
	 * @throws InstantiationException 	if the heuristic class does not have an accessible nullary constructor
	 * @throws IllegalAccessException 	if the heuristic class does not have an accessible nullary constructor
	 */
	@SuppressWarnings("unchecked")
	private void setHeuristics(String heuristicName) 
		throws ClassNotFoundException, InstantiationException, IllegalAccessException {
	
		try {
			Class< PreprocessingHeuristic<Val, U> > heuristicClass = (Class<PreprocessingHeuristic<Val, U>>) Class.forName(heuristicName);
			Class<?> parTypes[] = new Class[1];
			parTypes[0] = Preprocessing.class;
			Object[] args = new Object[1];
			args[0] = this;
			Constructor<PreprocessingHeuristic<Val, U>> constructor;
			constructor = heuristicClass.getConstructor(parTypes);
			this.heuristic = constructor.newInstance(args);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * Convenience method to used to create a UtilitySpace<Val, U> and fill it with
	 * the value \c utility
	 * 
	 * @author Brammert Ottens, 19 mei 2009
	 * @param variables		the variable for which the space must be created
	 * @param utility	the utility to be used
	 * @return	a UtilitySpace over variable \c var with all utilities set to \c utility
	 */
	@SuppressWarnings("unchecked")
	public UtilitySolutionSpace<Val, U> createUtilitySpace(String[] variables, U utility) {
		int	domainSize = 1;
		
		Val[][] domains = (Val[][])Array.newInstance(Array.newInstance(this.domains.get(this.variables.get(0))[0].getClass(), 0).getClass(), variables.length);
		for(int i = 0; i < variables.length; i++) {
			domains[i] = this.problem.getDomain(variables[i]);
			domainSize *= domains[i].length; 
		}
		
		
		U[] utilities = (U[]) Array.newInstance(utility.getClass(), domainSize*variables.length);
		Arrays.fill(utilities, utility);
		
		return new Hypercube<Val, U>(variables, domains, utilities, utility.getPlusInfinity());
	}
	
	/** 
	 * @see frodo2.algorithms.StatsReporter#getStatsFromQueue(frodo2.communication.Queue)
	 */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(HEURISTICS_STAT_MSG_TYPE, this);
	}

	/**
	 * Returns the reported heuristics
	 * @author Brammert Ottens, 22 jun 2009
	 * @return	the reported heuristics
	 */
	public HashMap<String, UtilitySolutionSpace<Val, U>> getReportedHeuristics() {
		return this.reportedHeuristics;
	}

	/** 
	 * @see frodo2.algorithms.StatsReporter#setSilent(boolean)
	 */
	public void setSilent(boolean silent) {
		// TODO Auto-generated method stub
		
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList <String> msgTypes = new ArrayList <String> (4);
		msgTypes.add(START_MSG_TYPE);
		msgTypes.add(DFSgeneration.OUTPUT_MSG_TYPE);
		msgTypes.add(heuristic.getMsgType());
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		return msgTypes;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	public void notifyIn( Message msg) {
		
		String type = msg.getType();
		
		if(type.equals(HEURISTICS_STAT_MSG_TYPE)) { // in stats gatherer mode
			BoundsMsg<Val, U> msgCast = (BoundsMsg<Val, U>) msg;
			reportedHeuristics.put(msgCast.getSender(), msgCast.getBounds());
			return;
		}
		
		else if (type.equals(AgentInterface.AGENT_FINISHED)) {
			this.reset();
			return;
		}
		
		// Parse the problem if this hasn't been done yet
		if (! this.started) 
			init();
		
		if(type.equals(DFSgeneration.OUTPUT_MSG_TYPE)) { // receiving DFS tree information for ONE variable
			DFSgeneration.MessageDFSoutput<Val, U> msgCast = (DFSgeneration.MessageDFSoutput<Val, U>)msg;
			
			String var = msgCast.getVar();
			DFSview<Val, U> relationships = msgCast.getNeighbors();
			frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> varInfo = heuristic.getVariableInfo(var);
			
			// set the lower neighbours
			String parent = relationships.getParent();
			varInfo.setNumberOfChildren(relationships.getChildren().size());
			if (parent != null) 
				varInfo.setParent(parent);
			
			if(parent != null) {
				
				// set the constraints this variable is responsible for
				for (UtilitySolutionSpace<Val, U> space : relationships.getSpaces()) 
					heuristic.processDFSOutput(space, new ArrayList<String> (Arrays.asList(space.getVariables())), var);
				
				
				if(varInfo.isInfoReady()) {
					Message newMsg = heuristic.createHeuristicInfoMessage(varInfo);
					queue.sendMessage(owners.get(parent), newMsg);
				}
			} 
			
			if(varInfo.isHeuristicReady()) {
				varInfo.heuristicSent = true;
				queue.sendMessage(AgentInterface.STATS_MONITOR, new BoundsMsg<Val, U>(HEURISTICS_STAT_MSG_TYPE, varInfo.variableID, null, varInfo.h));
				queue.sendMessageToSelf(new BoundsMsg<Val, U>(HEURISTICS_MSG_TYPE, varInfo.variableID, null, varInfo.h));
				if(varInfo.parent != null) {
					String destAgent = owners.get(varInfo.parent);
					Message newMsg = new BoundsMsg<Val, U>(HEURISTICS_MSG_TYPE, varInfo.variableID,  varInfo.parent, varInfo.h);
					queue.sendMessage(destAgent, newMsg);
				}
			}
		} else if (type.equals(heuristic.getMsgType())) {
			HeuristicMsg<Val, U> msgCast = (HeuristicMsg<Val, U>)msg;
			String var = msgCast.getReceiver();
			UtilitySolutionSpace<Val, U> space = msgCast.getSpace();
			frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> varInfo = heuristic.getVariableInfo(var);
			
			heuristic.processHeuristicsInfoMessage(space, var);
			
			if(varInfo.isHeuristicReady()) {
				varInfo.heuristicSent = true;
				queue.sendMessage(AgentInterface.STATS_MONITOR, new BoundsMsg<Val, U>(HEURISTICS_STAT_MSG_TYPE, varInfo.variableID, null, varInfo.h));
				queue.sendMessageToSelf(new BoundsMsg<Val, U>(HEURISTICS_MSG_TYPE, varInfo.variableID, null, varInfo.h));
				if(varInfo.parent != null) {
					String destAgent = owners.get(varInfo.parent);
					Message newMsg = new BoundsMsg<Val, U>(HEURISTICS_MSG_TYPE, varInfo.variableID,  varInfo.parent, varInfo.h);
					queue.sendMessage(destAgent, newMsg);
				}
			}
			
			if(varInfo.isInfoReady()) {
				Message newMsg = heuristic.createHeuristicInfoMessage(varInfo);
				queue.sendMessage(owners.get(varInfo.parent), newMsg);
			}
		}
		
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** This interface is used to implement the different heuristics for obtaining lower bounds.
	 * @param <Val> 	the type used for variable values
	 * @param <U> 		the type used for utility values
	 */
	public static interface PreprocessingHeuristic <Val extends Addable<Val>, U extends Addable<U> > {

		/**
		 * Initializes the variableInfo container
		 * 
		 * @author Brammert Ottens, 27 mei 2009
		 */
		public void init();
		
		/**
		 * Report the messages this heuristic shall be listening to
		 * @return a list of message types
		 */
		public String getMsgType();
		
		/**
		 * Returns the variable info for \c var
		 * @author Brammert Ottens, 27 mei 2009
		 * @param var	The ID of the variable
		 * @return		The info for this variable
		 */
		public VariableInfo<Val, U> getVariableInfo(String var);
		
		/**
		 * Method to process a space controlled by this variable
		 * 
		 * @author Brammert Ottens, 27 mei 2009
		 * @param space						The space to be processed
		 * @param constraint_variables		A list of variables that participate in the constraint
		 * @param var					The variable to whom the space belongs
		 */
		public void processDFSOutput(UtilitySolutionSpace<Val, U> space, List<String> constraint_variables, String var);
		
		/**
		 * Method to process a space received from a child
		 * 
		 * @author Brammert Ottens, 27 mei 2009
		 * @param space	The space to process
		 * @param var	The variable that received the space
		 */
		public void processHeuristicsInfoMessage(UtilitySolutionSpace<Val, U> space, String var);
		
		/**
		 * Creates the heuristics information message by taking the join of h and c, and projecting out 
		 * its own variable.
		 * 
		 * @author Brammert Ottens
		 * @param varInfo The variable for whom the message must be created
		 * @return The HeuristicsInformation message
		 */
		public HeuristicMsg<Val, U> createHeuristicInfoMessage(VariableInfo<Val, U> varInfo);
		
		/**
		 * Container for information belonging to a specific variable
		 * 
		 * @author Brammert Ottens, 20 mei 2009
		 * @param <Val> 	the type used for variable values
		 * @param <U> 		the type used for utility values
		 *
		 */
		public class VariableInfo<Val extends Addable<Val>, U extends Addable<U> > {
			
			/** The ID of this variable */
			public String variableID;
			
			/** The variables parent*/
			public String parent;
			
			/** Counts the number of children from whom information has been received */
			public int infoCounter;
			
			/** The number of children of this variable */
			public int numberOfChildren;
			
			/** \c true when info has sent to ones parent */
			public boolean infoSent;
			
			/** \c true when the heuristics has been calculated*/
			public boolean heuristicSent;
			
			/** Stores the minimal utilities received by the children */
			UtilitySolutionSpace<Val, U> h;
			
			/** the constraints owned by this variable */
			UtilitySolutionSpace<Val, U> c;
			
			/**
			 * Constructor
			 * 
			 * @param variableID		The ID of this variable
			 * @param p					A link to the Preprocessing class
			 */
			public VariableInfo(String variableID, Preprocessing<Val, U> p) {
				this.variableID = variableID;
				numberOfChildren = -1;
				String[] variables = new String[1];
				variables[0] = variableID;
				h = p.createUtilitySpace(variables, p.zeroObject);
			}
			
			/**
			 * Set the parent of this variable, in this very general version of the class, nothing should happen
			 * 
			 * @author Brammert Ottens, 27 mei 2009
			 * @param parent	The parent of this variable
			 */
			public void setParent(String parent) {
				this.parent = parent;
			}
			
			
			/**
			 * Returns true when this variable is ready to send info to its parent. By default the
			 * variable is never ready
			 * 
			 * @author Brammert Ottens, 27 mei 2009
			 * @return	\c true when ready
			 */
			public boolean isInfoReady() {
				return false;
			}
			
			/**
			 * Returns true when this variable is ready to send the heuristic. By default the
			 * variable is always ready
			 * 
			 * @author Brammert Ottens, 27 mei 2009
			 * @return	\c true when ready
			 */
			public boolean isHeuristicReady() {
				return true;
			}
			
			/**
			 * Set the infoCounter to the number of children. The
			 * value should be decreased everytime information
			 * from a child is received
			 * 
			 * @author Brammert Ottens, 27 mei 2009
			 * @param numberOfChildren	The number of children of this variable
			 */
			public void setNumberOfChildren(int numberOfChildren) {
				this.numberOfChildren = numberOfChildren;
			}
		}
		
		
	}
	
	/**
	 * This simple heuristic sets all lower bounds to zero, and should
	 * be used when running the original bnbadopt
	 * @param <Val> 	the type used for variable values
	 * @param <U> 		the type used for utility values
	 */
	public static class SimpleHeuristic <Val extends Addable<Val>, U extends Addable<U> > implements PreprocessingHeuristic<Val, U> {

		/** A link to the parent class */
		Preprocessing<Val, U> p;
		
		/** a map containing the information for all the variables */
		HashMap<String, VariableInfo<Val, U>> infos;
		
		/**
		 * Constructor
		 * @param p		A link to the parent class
		 */
		public SimpleHeuristic(Preprocessing<Val, U> p) {
			this.p = p;			
		}
		
		/**
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#init()
		 */
		public void init() {
			infos = new HashMap<String, VariableInfo<Val, U>>(p.variables.size());

			for(String variable : p.variables) {
				VariableInfo<Val, U> varInfo = new VariableInfo<Val, U>(variable, p);
				infos.put(variable, varInfo);
			}
		}
		
		/** @see Preprocessing.PreprocessingHeuristic#getMsgType() */
		public String getMsgType() {
			return null;
		}

		/** 
		 * Does nothing for the simple heuristics
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#processDFSOutput(frodo2.solutionSpaces.UtilitySolutionSpace, java.util.List, String)
		 */
		public void processDFSOutput(UtilitySolutionSpace<Val, U> space,
				List<String> constraint_variables, String var) {}

		/** 
		 * Does nothing for the simple heuristics
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#processHeuristicsInfoMessage(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String)
		 */
		public void processHeuristicsInfoMessage(
				UtilitySolutionSpace<Val, U> space, String var) {}

		/** 
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#getVariableInfo(java.lang.String)
		 */
		public frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> getVariableInfo(
				String var) {
			return infos.get(var);
		}

		/** 
		 * Does nothing for the simple heuristics
		 * @see Preprocessing.PreprocessingHeuristic#createHeuristicInfoMessage(VariableInfo)
		 */
		public HeuristicMsg<Val, U> createHeuristicInfoMessage(VariableInfo<Val, U> varInfo) {
			// does nothing for the simple heuristics
			return null;
		}
	}
	
	/**
	 * Implementation of the DP0 heuristic
	 * 
	 * @author Brammert Ottens, 18 mei 2009
	 * 
	 * @param <Val>	the type used to represent domain elements
	 * @param <U>	the type used to represent utility values
	 */
	public static class DP0 <Val extends Addable<Val>, U extends Addable<U> > implements PreprocessingHeuristic<Val, U> {
		
		/** A link to the parent class */
		Preprocessing<Val, U> p;
		
		/** a map containing the information for all the variables */
		HashMap<String, VariableInfo> infos;
		
		/** The type of the heuristic message send by this heuristic*/
		public static final String HEURISTIC_INFO_TYPE  = "DP0 heuristic info";
		
		/** Constructor 
		 * @param p A link to the super class*/
		public DP0(Preprocessing<Val, U> p) {
			this.p = p;
		}
		
		/**
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#init()
		 */
		public void init() {
			infos = new HashMap<String, VariableInfo>(p.variables.size());

			for(String variable : p.variables) {
				VariableInfo varInfo = new VariableInfo(variable);
				infos.put(variable, varInfo);
			}
		}

		/** 
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#getMsgType()
		 */
		public String getMsgType() {
			return DP0.HEURISTIC_INFO_TYPE;
		}

		/** 
		 * @see frodo2.algorithms.bnbbnbadopt.Preprocessing.PreprocessingHeuristic#processDFSOutput(frodo2.solutionSpaces.UtilitySolutionSpace, java.util.List, String)
		 */
		public void processDFSOutput(UtilitySolutionSpace<Val, U> space,
				List<String> constraint_variables, String var) {
			VariableInfo varInfo = infos.get(var);
			String[] parentVars = new String[1];
			parentVars[0] = varInfo.parent;
			varInfo.c  = varInfo.c.join(p.createUtilitySpace(parentVars, space.blindProjectAll(false)));
		}

		/** 
		 * @see frodo2.algorithms.bnbbnbadopt.Preprocessing.PreprocessingHeuristic#processHeuristicsInfoMessage(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String)
		 */
		public void processHeuristicsInfoMessage(
				UtilitySolutionSpace<Val, U> space, String var) {
			VariableInfo varInfo = infos.get(var);
			varInfo.infoCounter++;
			varInfo.h = varInfo.h.join(space);
		}

		/** 
		 * @see frodo2.algorithms.bnbbnbadopt.Preprocessing.PreprocessingHeuristic#getVariableInfo(java.lang.String)
		 */
		public frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> getVariableInfo(
				String var) {
			return infos.get(var);
		}

		/** 
		 * @see Preprocessing.PreprocessingHeuristic#createHeuristicInfoMessage(Preprocessing.PreprocessingHeuristic.VariableInfo)
		 */
		public HeuristicMsg<Val, U> createHeuristicInfoMessage(PreprocessingHeuristic.VariableInfo<Val, U> var) {
			VariableInfo varInfo = (VariableInfo)var;
			varInfo.infoSent = true;
			return new HeuristicMsg<Val, U>(HEURISTIC_INFO_TYPE, varInfo.parent, varInfo.variableID, varInfo.c);
		}

		/**
		 * A container for variable dependent information
		 * 
		 * @author Brammert Ottens, 18 mei 2009
		 */
		public class VariableInfo extends frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> {
			
			/**
			 * A constructor
			 * 
			 * @param variableID			The ID of the variable
			 */
			public VariableInfo(String variableID) {
				super(variableID, p);
			}
			
			/**
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#setParent(java.lang.String)
			 */
			@Override
			public void setParent(String parent) {
				this.parent = parent;
				String[] parentVars = new String[1];
				parentVars[0] = parent;
				c = p.createUtilitySpace(parentVars, p.zeroObject);
			}
			
			/**
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#isHeuristicReady()
			 */
			@Override
			public boolean isHeuristicReady() {
				return !heuristicSent && infoCounter == numberOfChildren;
			}
			
			/**
			 * As soon as the DFS information has been received, the info message is ready to be send 
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#isInfoReady()
			 */
			public boolean isInfoReady() {
				return !infoSent && numberOfChildren != -1 && parent != null;
			}
		
			
		}
	}

	/**
	 * Implementation of the DP1 heuristic
	 * @author Brammert Ottens, 20 mei 2009
	 * 
	 * @param <Val>	the type used to represent domain elements
	 * @param <U>	the type used to represent utility values
	 */
	public static class DP1 <Val extends Addable<Val>, U extends Addable<U> > implements PreprocessingHeuristic<Val, U> {

		/** The type of the heuristic message send by this heuristic*/
		public static final String HEURISTIC_INFO_TYPE  = "DP1 heuristic info";
		
		/** A link to the parent class */
		private Preprocessing<Val, U> p;
		
		/** Map to the information belonging to different variables */
		private HashMap<String, VariableInfo> infos;
		
		/**
		 * Constructor
		 * 
		 * @param p the link to the parent class
		 */
		public DP1(Preprocessing<Val, U> p) {
			this.p = p;
		}
		
		/** 
		 * Initializes variables
		 * 
		 * @author Brammert Ottens, 20 mei 2009
		 */
		public void init() {
			infos = new HashMap<String, VariableInfo>(p.variables.size());

			for(String variable : p.variables) {
				VariableInfo varInfo = new VariableInfo(variable);
				infos.put(variable, varInfo);
			}
		}
		
		/** 
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#getMsgType()
		 */
		public String getMsgType() {
			return HEURISTIC_INFO_TYPE;
		}
		
		/**
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#processDFSOutput(frodo2.solutionSpaces.UtilitySolutionSpace, java.util.List, java.lang.String)
		 */
		public void processDFSOutput(UtilitySolutionSpace<Val, U> space, List<String> constraint_variables, String var) {
			VariableInfo varInfo = infos.get(var);
			
			if(space.getDomain(varInfo.parent) != null) {
				varInfo.c = varInfo.c.join(space);
			}
		}
		
		/**
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#processHeuristicsInfoMessage(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String)
		 */
		public void processHeuristicsInfoMessage(UtilitySolutionSpace<Val, U> space, String var) {
			VariableInfo varInfo = infos.get(var);
			varInfo.infoCounter++;
			varInfo.h = varInfo.h.join(space);
		}
		
		/** 
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#getVariableInfo(java.lang.String)
		 */
		public frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> getVariableInfo(
				String var) {
			return infos.get(var);
		}

		/** 
		 * @see Preprocessing.PreprocessingHeuristic#createHeuristicInfoMessage(Preprocessing.PreprocessingHeuristic.VariableInfo)
		 */
		public HeuristicMsg<Val, U> createHeuristicInfoMessage(PreprocessingHeuristic.VariableInfo<Val, U> var) {
			VariableInfo varInfo = (VariableInfo)var;
			varInfo.infoSent = true;
			
			ArrayList<String> constraint_variables = new ArrayList<String> (Arrays.asList(varInfo.c.getVariables()));
			
			if(constraint_variables.size() > 2) {
				constraint_variables.remove(varInfo.variableID);
				constraint_variables.remove(varInfo.parent);
				varInfo.c = varInfo.c.blindProject(constraint_variables.toArray(new String[0]), false);
			}
			return new HeuristicMsg<Val, U>(HEURISTIC_INFO_TYPE, varInfo.parent, varInfo.variableID, 
					(varInfo.h.join(varInfo.c)).blindProject(varInfo.variableID, false));
		}

		/**
		 * Container for information belonging to a specific variable
		 * 
		 * @author Brammert Ottens, 20 mei 2009
		 *
		 */
		public class VariableInfo extends frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> {
			
			/**
			 * Constructor
			 * 
			 * @param variableID		The ID of this variable
			 */
			public VariableInfo(String variableID) {
				super(variableID, p);
			}
			
			/**
			 * Set the parent of this variable
			 * 
			 * @author Brammert Ottens, 27 mei 2009
			 * @param parent	The parent of this variable
			 */
			@Override
			public void setParent(String parent) {
				this.parent = parent;
				
				String[] variables = new String[2];
				variables[0] = parent;
				variables[1] = variableID;
				c = p.createUtilitySpace(variables, p.zeroObject);
			}
			
			/**
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#isHeuristicReady()
			 */
			@Override
			public boolean isHeuristicReady() {
				return !heuristicSent &&  infoCounter == numberOfChildren;
			}
			
			/**
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#isInfoReady()
			 */
			@Override
			public boolean isInfoReady() {
				return !infoSent && infoCounter == numberOfChildren && parent != null;
			}
		}
	}
	
	/**
	 * Implementation of the DP2 heuristic
	 * @author Brammert Ottens, 27 mei 2009
	 * 
	 * @param <Val> the type used to represent domain elements
	 * @param <U>	the type used to represent utility values
	 */
	public static class DP2 <Val extends Addable<Val>, U extends Addable<U> > implements PreprocessingHeuristic<Val, U> {

		/** The type of the heuristic message send by this heuristic*/
		public static final String HEURISTIC_INFO_TYPE  = "DP2 heuristic info";
		
		/** A link to the parent class */
		private Preprocessing<Val, U> p;
		
		/** Map to the information belonging to different variables */
		private HashMap<String, VariableInfo> infos;
		
		/**
		 * Constructor
		 * 
		 * @param p the link to the parent class
		 */
		public DP2(Preprocessing<Val, U> p) {
			this.p = p;
		}
				
		/** 
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#getMsgType()
		 */
		public String getMsgType() {
			return HEURISTIC_INFO_TYPE;
		}

		/** 
		 * Initializes variables
		 * 
		 * @author Brammert Ottens, 21 mei 2009
		 */
		public void init() {
			infos = new HashMap<String, VariableInfo>(p.variables.size());

			for(String variable : p.variables) {
				String[] vars = new String[1];
				vars[0] = variable;
				VariableInfo varInfo = new VariableInfo(variable);
				infos.put(variable, varInfo);
			}
		}
		
		/** 
		 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic#processDFSOutput(frodo2.solutionSpaces.UtilitySolutionSpace, java.util.List, java.lang.String)
		 */
		public void processDFSOutput(UtilitySolutionSpace<Val, U> space,
				List<String> constraint_variables, String var) {
			VariableInfo varInfo = infos.get(var);
			String parent = varInfo.parent;
			if(constraint_variables.contains(parent)) {
				varInfo.c = space;
			} else {
				constraint_variables.remove(var);
				if(varInfo.minC == null)
					varInfo.minC = space.blindProject(constraint_variables.toArray(new String[0]), false);
				else
					varInfo.minC = varInfo.minC.join(space.blindProject(constraint_variables.toArray(new String[0]), false));
			}
		}

		/** 
		 * @see frodo2.algorithms.bnbbnbadopt.Preprocessing.PreprocessingHeuristic#processHeuristicsInfoMessage(frodo2.solutionSpaces.UtilitySolutionSpace, java.lang.String)
		 */
		public void processHeuristicsInfoMessage(
				UtilitySolutionSpace<Val, U> space, String var) {
			VariableInfo varInfo = infos.get(var);
			varInfo.infoCounter++;
			varInfo.h = varInfo.h.join(space);
		}

		/** 
		 * @see frodo2.algorithms.bnbbnbbnbadopt.Preprocessing.PreprocessingHeuristic#getVariableInfo(java.lang.String)
		 */
		public frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> getVariableInfo(
				String var) {
			return infos.get(var);
		}

		/** 
		 * @see Preprocessing.PreprocessingHeuristic#createHeuristicInfoMessage(Preprocessing.PreprocessingHeuristic.VariableInfo)
		 */
		public HeuristicMsg<Val, U> createHeuristicInfoMessage(PreprocessingHeuristic.VariableInfo<Val, U> var) {
			VariableInfo varInfo = (VariableInfo)var;
			varInfo.infoSent = true;
			
			ArrayList<String> constraint_variables = new ArrayList<String> (Arrays.asList(varInfo.c.getVariables()));
			
			if(constraint_variables.size() > 2) {
				constraint_variables.remove(varInfo.variableID);
				constraint_variables.remove(varInfo.parent);
				varInfo.c = varInfo.c.blindProject(constraint_variables.toArray(new String[0]), false);
			}
			
			return new HeuristicMsg<Val, U>(HEURISTIC_INFO_TYPE, varInfo.parent, varInfo.variableID, 
					varInfo.h.join(varInfo.c.join(varInfo.minC)).blindProject(varInfo.variableID, false));
		}

		/**
		 * Convenience class that contains information on a specific variable
		 * @author brammertottens
		 *
		 */
		public class VariableInfo extends frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo<Val, U> {
			
			/** The joint of the constraints between this variable and its ancestors(without its parent), where all variables
			 * but itself are projected out */
			UtilitySolutionSpace<Val, U> minC;
			
			/**
			 * Constructor
			 * 
			 * @param variableID	The ID of this variable
			 */
			public VariableInfo(String variableID) {
				super(variableID, p);
			}
			/**
			 * Set the parent of this variable
			 * 
			 * @author Brammert Ottens, 27 mei 2009
			 * @param parent	The parent of this variable
			 */
			@Override
			public void setParent(String parent) {
				this.parent = parent;
				
				String[] variables = new String[1];
				variables[0] = parent;
				c = p.createUtilitySpace(variables, p.zeroObject);
				
				variables = new String[2];
				variables[0] = parent;
				variables[1] = variableID;
				minC = p.createUtilitySpace(variables, p.zeroObject);
			}
			
			/**
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#isInfoReady()
			 */
			@Override
			public boolean isInfoReady() {
				return !infoSent && infoCounter == numberOfChildren && parent != null;
			}
			
			/**
			 * @see frodo2.algorithms.bnbadopt.Preprocessing.PreprocessingHeuristic.VariableInfo#isHeuristicReady()
			 */
			@Override
			public boolean isHeuristicReady() {
				return !heuristicSent && infoCounter == numberOfChildren;
			}
		}
	}
}
