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

package frodo2.algorithms.maxsum;

import java.util.Arrays;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;

import frodo2.algorithms.AbstractDCOPsolver;
import frodo2.algorithms.AbstractSolver;
import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.varOrdering.factorgraph.FactorGraphGen;
import frodo2.gui.DOTrenderer;
import frodo2.solutionSpaces.Addable;

/** A solver for Max-Sum
 * @author Thomas Leaute
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class MaxSumSolver < V extends Addable<V>, U extends Addable<U> > extends AbstractDCOPsolver< V, U, Solution<V, U> > {
	
	/** The MaxSum module that collects statistics about the solution */
	private MaxSum<V, U> module;

	/** Default constructor */
	public MaxSumSolver() {
		super ("/frodo2/algorithms/maxsum/MaxSumAgent.xml");
	}
	
	/** Constructor
	 * @param agentConfig 	the agent configuration file
	 */
	public MaxSumSolver (Document agentConfig) {
		super (agentConfig);
	}

	/** Constructor
	 * @param agentConfig 	the agent configuration file
	 * @param useTCP 		whether to use TCP pipes
	 */
	public MaxSumSolver (Document agentConfig, boolean useTCP) {
		super (agentConfig, useTCP);
	}

	/** Constructor from an agent configuration file
	 * @param agentDescFile 	the agent configuration file
	 */
	public MaxSumSolver (String agentDescFile) {
		super (agentDescFile);
	}
	
	/** @see AbstractSolver#getSolGatherers() */
	@Override
	public List<? extends StatsReporter> getSolGatherers() {
		
		Element params = new Element ("module");
		params.setAttribute("reportStats", "false");
		params.setAttribute("DOTrenderer", DOTrenderer.class.getName());
		FactorGraphGen<V, U> gen = new FactorGraphGen<V, U> (params, super.problem);
		
		this.module = new MaxSum<V, U> (null, super.problem);
		this.module.setSilent(true);
		
		return Arrays.asList(gen, this.module);
	}

	/** @see AbstractSolver#buildSolution() */
	@Override
	public Solution<V, U> buildSolution() {
		return new Solution<V, U> (super.problem.getNbrVars(), null, this.module.getOptCost(), this.module.getOptAssignments(), super.factory.getNbrMsgs(), super.factory.getMsgNbrs(), 
				super.factory.getTotalMsgSize(), super.factory.getMsgSizes(), super.factory.getOverallMaxMsgSize(), super.factory.getMaxMsgSizes(), super.factory.getNcccs(), super.factory.getTime(), null, 0);
	}

}
