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

/** The MPC-DisCSP algorithms by Marius-Calin Silaghi */
package frodo2.algorithms.mpc_discsp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporter;
import frodo2.solutionSpaces.Addable;

/** A solver for MPC-Dis(W)CSP4 
 * 
 * MPC-DisWCSP4 is the MPC-DisCSP4, with the weak extension to DisWCSPs initially proposed for MPC-DisCSP2 in the following paper: 
 * 
 * Marius-Calin Silaghi and Debasis Mitra. Distributed constraint satisfaction and optimization with privacy enforcement. 
 * In Proceedings of the 2004 IEEE/WIC/ACM International Conference on Intelligent Agent Technology (IAT'04), 
 * pages 531-535, Beijing, China, September 20-24 2004. IEEE Computer Society Press.
 * 
 * MPC-DisCSP4 is described in the following paper:
 * 
 * Marius-Calin Silaghi. Hiding absence of solution for a distributed constraint satisfaction problem (poster). 
 * In Proceedings of the Eighteenth International Florida Artificial Intelligence Research Society Conference (FLAIRS'05), 
 * pages 854-855, Clearwater Beach, FL, USA, May 15-17 2005. AAAI Press.
 * 
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 * 
 */
public class MPC_DisWCSP4solver < V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, Solution<V, U> > {
	
	/** The module that gathers solution statistics */
	private MPC_DisCSP4<V, U> module;
	
	/** Constructor for MPC-DisWCSP4 */
	public MPC_DisWCSP4solver () {
		this ("/frodo2/algorithms/mpc_discsp/MPC-DisWCSP4.xml");
	}

	/** Constructor from an agent configuration file
	 * @param agentDescFile 	the agent configuration file (either for MPC-DisCSP4 or MPC-DisWCSP4)
	 */
	public MPC_DisWCSP4solver (String agentDescFile) {
		super (agentDescFile);
	}
	
	/** Constructor
	 * @param agentDesc 	the agent configuration file
	 */
	public MPC_DisWCSP4solver (Document agentDesc) {
		super (agentDesc);
	}

	/** Constructor
	 * @param agentDesc 	the agent configuration file
	 * @param useTCP 		Whether to use TCP pipes or shared memory pipes
	 * @warning Using TCP pipes automatically disables simulated time. 
	 */
	public MPC_DisWCSP4solver (Document agentDesc, boolean useTCP) {
		super (agentDesc, useTCP);
	}

	/** @see AbstractDCOPsolver#getSolGatherers() */
	@Override
	public List<StatsReporter> getSolGatherers() {
		
		this.module = new MPC_DisCSP4<V, U> (null, super.problem);
		this.module.setSilent(true);
		
		return Arrays.asList((StatsReporter) this.module);
	}

	/** @see AbstractDCOPsolver#buildSolution() */
	@Override
	public Solution<V, U> buildSolution() {

		Map<String, V>  solution = this.module.getSolution();
		int nbrMsgs = factory.getNbrMsgs();
		TreeMap<String, Integer> msgNbrs = factory.getMsgNbrs();
		long totalMsgSize = factory.getTotalMsgSize();
		TreeMap<String, Long> msgSizes = factory.getMsgSizes();
		long maxMsgSize = factory.getOverallMaxMsgSize();
		TreeMap<String, Long> maxMsgSizes = factory.getMaxMsgSizes();
		long ncccs = factory.getNcccs();
		int numberOfCoordinationConstraints = problem.getNumberOfCoordinationConstraints();
		int nbrVariables = problem.getNbrVars();
		long totalTime = factory.getTime();
		
		return new Solution<V, U> (nbrVariables, this.module.getOptCost(), this.module.getOptCost(), solution, nbrMsgs, msgNbrs, 
				totalMsgSize, msgSizes, maxMsgSize, maxMsgSizes, ncccs, totalTime, null, numberOfCoordinationConstraints);
	}

	/** Solves the input problem
	 * @param problem 		the problem
	 * @param measureMsgs 	whether message sizes should be measured
	 * @param timeout 		timeout in ms, if \c null, no timeout is used
	 * @param infiniteCost 	private constraints are only allowed to be soft constraints; infeasibility is identified by a cost of infiniteCost
	 * @param maxTotalCost 	maximum total cost of any solution; should be greater than (infiniteCost * number of agents) to guarantee correctness
	 * @return 				an optimal solution
	 */
	public Solution<V, U> solve (Document problem, boolean measureMsgs, Long timeout, int infiniteCost, int maxTotalCost) {
		
		this.setMaxCost(infiniteCost, maxTotalCost);

		return super.solve(problem, 0, measureMsgs, timeout);
	}

	/** Sets the maximum total cost of any solution
	 * @param infiniteCost 	private constraints are only allowed to be soft constraints; infeasibility is identified by a cost of infiniteCost
	 * @param maxTotalCost 	maximum total cost of any solution; should be greater than (infiniteCost * number of agents) to guarantee correctness
	 */
	private void setMaxCost(int infiniteCost, int maxTotalCost) {
		
		for (Element elmt : (List<Element>) super.agentDesc.getRootElement().getChild("modules").getChildren()) {
			if (elmt.getAttributeValue("className").equals(MPC_DisWCSP4.class.getName())) {
				elmt.setAttribute("infiniteCost", Integer.toString(infiniteCost));
				elmt.setAttribute("maxTotalCost", Integer.toString(maxTotalCost));
				return;
			}
		}
	}
	
}
