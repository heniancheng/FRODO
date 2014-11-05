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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.election.LeaderElectionMaxID;
import frodo2.algorithms.varOrdering.election.SecureVarElection;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.DCOPProblemInterface;

/**
 * 
 * A DCOP solver using P2-DPOP
 * @author Eric Zbinden, Thomas Leaute
 * 
 * @param <V> class used for value
 * @param <U> class used for utility values
 * 
 * @todo Create a subclass of Solution that includes the stats from CollaborativeDecryption
 */
public class P2_DPOPsolver< V extends Addable<V>, U extends Addable<U> > extends DPOPsolver<V,U>{
	
	/** The EncryptedUTIL module */
	private EncryptedUTIL<V,U,?> encryptModule;
	
	/** The Collaborative Decryption module */
	@SuppressWarnings("rawtypes")
	private CollaborativeDecryption collabDecrypt;

	/** The RerootRequester module */
	private RerootRequester<V, U> rerootRequester;
	
	/** Default constructor 
	 * @param <UL> class used for encrypted utility values
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver () {
		this(false);
	}
	
	/** Constructor 
	 * @param <UL> class used for encrypted utility values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	@SuppressWarnings("unchecked")
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (boolean useTCP) {
		this((Class<V>) AddableInteger.class, (Class<U>) AddableInteger.class, (Class<UL>) AddableInteger.class, useTCP);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param eUtilClass    the class to use for encrypted utilities
	 * @param <UL> class used for encrypted utility values
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (Class<V> domClass, Class<U> utilClass, Class<UL> eUtilClass) {
		this ("/frodo2/algorithms/dpop/privacy/P2-DPOPagent.xml", domClass, utilClass, eUtilClass);
	}
	
	/** Constructor 
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to use for utilities
	 * @param eUtilClass    the class to use for encrypted utilities
	 * @param <UL> class used for encrypted utility values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (Class<V> domClass, Class<U> utilClass, Class<UL> eUtilClass, boolean useTCP) {
		this ("/frodo2/algorithms/dpop/privacy/P2-DPOPagent.xml", domClass, utilClass, eUtilClass, useTCP);
	}
	
	/**
	 * Constructor
	 * @param agentDesc 	the agent description file
	 */
	public P2_DPOPsolver(String agentDesc) {
		super(agentDesc);
	}
	
	/**
	 * Constructor
	 * @param agentDesc the agent description
	 */
	public P2_DPOPsolver(Document agentDesc) {
		super(agentDesc);
	}
	
	/**
	 * Constructor
	 * @param agentDesc the agent description
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public P2_DPOPsolver(Document agentDesc, boolean useTCP) {
		super(agentDesc, useTCP);
	}
		
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param parserClass	the class used to parse problems	
	 */
	public P2_DPOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass) {
		super(agentDesc, parserClass);
	}
	
	/** Constructor 
	 * @param agentDesc 	description of the agent to be used
	 * @param parserClass	the class used to parse problems	
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public P2_DPOPsolver (Document agentDesc, Class< ? extends XCSPparser<V, U> > parserClass, boolean useTCP) {
		super(agentDesc, parserClass, useTCP);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param eUtilClass    the class to be used for encrypted utility values
	 * @param <UL> class used for encrypted utility values
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, Class<UL> eUtilClass) {
		this(agentDescFile, domClass, utilClass, eUtilClass, false);
	}
	
	/** Constructor 
	 * @param agentDescFile description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param eUtilClass    the class to be used for encrypted utility values
	 * @param <UL> class used for encrypted utility values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (String agentDescFile, Class<V> domClass, Class<U> utilClass, Class<UL> eUtilClass, boolean useTCP) {
		super (agentDescFile, domClass, utilClass, useTCP);
		
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").equals(EncryptedUTIL.class.getName())){
				module.setAttribute("encryptUtilClass", eUtilClass.getName());
			}
		}
	}
	
	/** Constructor 
	 * @param agentDesc		description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param eUtilClass    the class to be used for encrypted utility values
	 * @param <UL> class used for encrypted utility values
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (Document agentDesc, Class<V> domClass, Class<U> utilClass, Class<UL> eUtilClass) {
		this(agentDesc, domClass, utilClass, eUtilClass, false);
	}
	
	/** Constructor 
	 * @param agentDesc		description of the agent to be used
	 * @param domClass 		the class to use for variable values
	 * @param utilClass 	the class to be used for utility values
	 * @param eUtilClass    the class to be used for encrypted utility values
	 * @param <UL> class used for encrypted utility values
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public < UL extends AddableLimited<U, UL> > P2_DPOPsolver (Document agentDesc, Class<V> domClass, Class<U> utilClass, Class<UL> eUtilClass, boolean useTCP) {
		super (agentDesc, useTCP);
		super.setDomClass(domClass);
		super.setUtilClass(utilClass);
		
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").equals(EncryptedUTIL.class.getName())){
				module.setAttribute("encryptUtilClass", eUtilClass.getName());
			}
		}
	}
	
	/** @see DPOPsolver#getSolGatherers() */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ArrayList<StatsReporter> getSolGatherers() {

		ArrayList<StatsReporter> solGatherers = new ArrayList<StatsReporter> (5);
		
		rerootRequester = new RerootRequester<V, U> (null, problem);
		rerootRequester.setSilent(true);
		solGatherers.add(rerootRequester);
		
		encryptModule = new EncryptedUTIL (null, problem);
		encryptModule.setSilent(true);
		solGatherers.add(encryptModule);
		
		collabDecrypt = new CollaborativeDecryption(null, problem);
		collabDecrypt.setSilent(true);
		solGatherers.add(collabDecrypt);
		
		dfsModule = new DFSgenerationWithOrder(null, problem);
		dfsModule.setSilent(true);
		solGatherers.add(dfsModule);

		// Monitor the linear ordering (for debugging purposes)
		SecureCircularRouting module = new SecureCircularRouting (null, problem);
		module.setSilent(true);
		solGatherers.add(module);

		return solGatherers;
	}

	/** @see DPOPsolver#buildSolution() */
	@Override
	public Solution<V, U> buildSolution() {

		U optUtil = this.rerootRequester.getOptUtil();
		Map<String, V>  solution = this.rerootRequester.getSolution();
		int nbrMsgs = factory.getNbrMsgs();
		TreeMap<String, Integer> msgNbrs = factory.getMsgNbrs();
		long totalMsgSize = factory.getTotalMsgSize();
		TreeMap<String, Long> msgSizes = factory.getMsgSizes();
		long maxMsgSize = factory.getOverallMaxMsgSize();
		TreeMap<String, Long> maxMsgSizes = factory.getMaxMsgSizes();
		long ncccs = factory.getNcccs();
		int maxMsgDim = encryptModule.getMaxMsgDim();
		int numberOfCoordinationConstraints = problem.getNumberOfCoordinationConstraints();
		int nbrVariables = problem.getNbrVars();
		
		HashMap<String, Long> timesNeeded = new HashMap<String, Long> ();
		timesNeeded.put(dfsModule.getClass().getName(), dfsModule.getFinalTime());
		timesNeeded.put(encryptModule.getClass().getName(), encryptModule.getFinalTime());
		long totalTime = factory.getTime();
		
		return new Solution<V, U> (nbrVariables, optUtil, super.problem.getUtility(solution).getUtility(0), solution, nbrMsgs, msgNbrs, 
				totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccs, totalTime, timesNeeded, maxMsgDim, numberOfCoordinationConstraints);
	}
	
	/** Clear this class' member attributes */
	@Override
	public void clear () {
		this.collabDecrypt = null;
		this.encryptModule = null;
		this.rerootRequester = null;
		super.clear();
		
		DFSgeneration.ROOT_VAR_MSG_TYPE = LeaderElectionMaxID.OUTPUT_MSG_TYPE;
	}
	
	/** @return \c null because P2-DPOP does not really run on a DFS */
	@Override
	public HashMap< String, DFSview<V, U> > getDFS () {
		return null;
	}

	/** @see DPOPsolver#setNbrElectionRounds(int) */
	@Override
	protected void setNbrElectionRounds (int nbrElectionRounds) {
		
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) 
			if (module.getAttributeValue("className").equals(SecureVarElection.class.getName())) 
				module.setAttribute("minNbrLies", Integer.toString(nbrElectionRounds));
	}
	
	/** Sets the value of infinity used by the CryptoScheme
	 * @param infinity 	costs greater than or equal to this value will be considered infinite
	 */
	private void setInfinity (int infinity) {
		
		for (Element module : (List<Element>) this.agentDesc.getRootElement().getChild("modules").getChildren()) {
			if (module.getAttributeValue("className").endsWith("CollaborativeDecryption")) {
				Element scheme = module.getChild("cryptoScheme");
				assert scheme != null : "No cryptoScheme found in the module CollaborativeDecryption in the agent configuration file";
				scheme.setAttribute("infinity", Integer.toString(infinity));
				return;
			}
		}
		
		assert false : "No module CollaborativeDecryption found in the agent configuration file";
	}

	/** Solves the input problem
	 * @param problem 		the problem
	 * @param infinity 		costs greater than or equal to this value will be considered infinite
	 * @return 				an optimal solution
	 */
	public Solution<V, U> solve (Document problem, Integer infinity) {
		this.setInfinity(infinity);
		return super.solve(problem);
	}
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @param infinity 		costs greater than or equal to this value will be considered infinite
	 * @return 				an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, Integer infinity) {
		this.setInfinity(infinity);
		return super.solve(problem);
	}
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @param timeout 		timeout in ms, if \c null, no timeout is used
	 * @param infinity 		costs greater than or equal to this value will be considered infinite
	 * @return 				an optimal solution
	 */
	public Solution<V, U> solve (Document problem, Long timeout, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 		the problem
	 * @param timeout 		timeout in ms, if \c null, no timeout is used
	 * @param infinity 		costs greater than or equal to this value will be considered infinite
	 * @return 				an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, Long timeout, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (Document problem, int nbrElectionRounds, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, int nbrElectionRounds, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (Document problem, int nbrElectionRounds, boolean measureMsgs, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, measureMsgs);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, int nbrElectionRounds, boolean measureMsgs, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, measureMsgs);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param cleanAfterwards 		if \c true, cleans all the agents and the queue when they're done
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (Document problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, boolean cleanAfterwards, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, measureMsgs, timeout, cleanAfterwards);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param cleanAfterwards 		if \c true, cleans all the agents and the queue when they're done
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, boolean cleanAfterwards, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, measureMsgs, timeout, cleanAfterwards);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (Document problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, measureMsgs, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param measureMsgs 			whether message sizes should be measured
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, int nbrElectionRounds, boolean measureMsgs, Long timeout, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, measureMsgs, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (Document problem, int nbrElectionRounds, Long timeout, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, timeout);
	}
	
	/** Solves the input problem
	 * @param problem 				the problem
	 * @param nbrElectionRounds 	the number of rounds of VariableElection (must be greater than the diameter of the constraint graph)
	 * @param timeout 				timeout in ms, if \c null, no timeout is used
	 * @param infinity 				costs greater than or equal to this value will be considered infinite
	 * @return 						an optimal solution
	 */
	public Solution<V, U> solve (DCOPProblemInterface<V, U> problem, int nbrElectionRounds, Long timeout, int infinity) {
		this.setInfinity(infinity);
		return super.solve(problem, nbrElectionRounds, timeout);
	}
	
}
