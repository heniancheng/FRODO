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

package frodo2.algorithms.reformulation;

import java.util.Arrays;
import java.util.Collection;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** A module that rescales the problem
 * @author Thomas Leaute
 * @param <U> the class used for utility values
 */
public class ProblemRescaler< U extends Addable<U> > implements IncomingMsgPolicyInterface<String> {
	
	/** The type of the message sent when this module is done rescaling the problem */
	public static final String DONE = "ProblemRescalerDONE";
	
	/** The agent's queue */
	private Queue queue;
	
	/** The agent's subproblem */
	final private DCOPProblemInterface<?, U> problem;
	
	/** Whether the problem should be made a maximization problem */
	private final boolean maximize;
	
	/** How much should be added to each cost/utility to make them all of the desired sign */
	private U shift;
	
	/** Constructor
	 * @param problem 	The agent's subproblem
	 * @param params 	The parameters of the module
	 */
	public ProblemRescaler (DCOPProblemInterface<?, U> problem, Element params) {
		this.problem = problem;
		
		assert params.getAttributeValue("maximize") != null : "No argument \"maximize\" passed to " + this.getClass();
		this.maximize = Boolean.parseBoolean(params.getAttributeValue("maximize"));
		
		String shiftStr = params.getAttributeValue("shift");
		if (shiftStr == null) 
			this.shift = problem.getZeroUtility();
		else 
			this.shift = problem.getZeroUtility().fromString(shiftStr);
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return Arrays.asList(AgentInterface.START_AGENT);
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public void notifyIn(Message msg) {

		this.problem.rescale((this.problem.maximize() != this.maximize ? this.shift.fromString("-1") : this.shift.fromString("1"))
				, this.shift);
		this.problem.setMaximize(maximize);

		this.queue.sendMessageToSelf(new Message (DONE));
	}

}
