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

package frodo2.algorithms.dpop.param;

import java.util.ArrayList;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.dpop.UTILpropagation;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A UTIL propagation protocol that supports parameters (i.e. variables with no owner that are never projected out)
 * @author Thomas Leaute
 * @param <Val> type used for variable values
 * @param <U> type used for utility values
 */
public class ParamUTIL < Val extends Addable<Val>, U extends Addable<U> > 
extends UTILpropagation<Val, U> {
	
	/** The type of the messages containing parametric optimal utility values sent by roots */
	public static final String OPT_PARAM_UTIL_MSG_TYPE = "OptParamUtilMessage";
	
	/** Optimal utility computed by the algorithm, depending on the values of the parameters */
	private UtilitySolutionSpace<Val, U> optUtil;
	
	/** Constructor
	 * @see UTILpropagation#UTILpropagation (DCOPProblemInterface)
	 */
	public < S extends UtilitySolutionSpace<Val, U> > ParamUTIL (DCOPProblemInterface<Val, U> problem) {
		super (problem);
		super.withAnonymVars = true;
	}
	
	/** Constructor from XML descriptions
	 * @see UTILpropagation#UTILpropagation (DCOPProblemInterface, Element)
	 */
	public ParamUTIL (DCOPProblemInterface<Val, U> problem, Element parameters) {
		super(problem, parameters);
		super.withAnonymVars = true;
	}
	
	/** The constructor called in "statistics gatherer" mode
	 * @see UTILpropagation#UTILpropagation (Element, DCOPProblemInterface)
	 */
	public ParamUTIL (Element parameters, DCOPProblemInterface<Val, U> problem) { 
		super (parameters, problem);
	}
	
	/** @see UTILpropagation#reset() */
	@Override
	public void reset () {
		super.reset();
		this.optUtil = null;
	}

	/** Message sent by roots containing the optimal utility value of their DFS tree, conditioned on the values of the parameters (if any)
	 * @param <Val> 	the type used for variable values
	 * @param <U> 		the type used for utility values
	 */
	public static class OptUtilMessage < Val extends Addable<Val>, U extends Addable<U> > 
	extends MessageWith2Payloads < UtilitySolutionSpace<Val, U> , String> {

		/** Empty constructor used for externalization */
		public OptUtilMessage () { }

		/** Constructor
		 * @param value 	optimal utility value, conditioned on the values of the parameters (if any)
		 * @param rootVar 	the name of the root variable reporting its optimal utility value
		 */
		public OptUtilMessage(UtilitySolutionSpace<Val, U> value, String rootVar) {
			super(OPT_PARAM_UTIL_MSG_TYPE, value, rootVar);
		}
		
		/** @return the optimal utility value, conditioned on the values of the parameters (if any) */
		public UtilitySolutionSpace<Val, U> getUtility () {
			return this.getPayload1();
		}
		
		/** @return the name of the root variable reporting its optimal utility value */
		public String getRoot() {
			return this.getPayload2();
		}
		
		/** @see Message#fakeSerialize() */
		@Override
		public void fakeSerialize () {
			super.setPayload1(super.getPayload1().resolve());
		}
	}
	
	/** @see UTILpropagation#notifyIn(Message) */
	@SuppressWarnings("unchecked")
	@Override
	public void notifyIn(Message msg) {
		
		if (msg.getType().equals(OPT_PARAM_UTIL_MSG_TYPE)) { // we are in stats gatherer mode

			OptUtilMessage<Val, U> msgCast = (OptUtilMessage<Val, U>) msg;
			if (!silent) {
				if (this.maximize) {
					System.out.println("Optimal parameterized utility for component rooted at `" + msgCast.getRoot() + "\': " + msgCast.getUtility());
				} else 
					System.out.println("Optimal parameterized cost for component rooted at `" + msgCast.getRoot() + "\': " + msgCast.getUtility());
			}
			if (this.optUtil == null) {
				this.optUtil = msgCast.getUtility();
			} else 
				this.optUtil = this.optUtil.join(msgCast.getUtility());
			
			return;
			
		} else 
			super.notifyIn(msg);
	}
	
	/** @see UTILpropagation#getStatsFromQueue(Queue) */
	@Override
	public void getStatsFromQueue(Queue queue) {
		ArrayList <String> msgTypes = new ArrayList <String> (2);
		msgTypes.add(OPT_PARAM_UTIL_MSG_TYPE);
		msgTypes.add(UTIL_STATS_MSG_TYPE);
		queue.addIncomingMessagePolicy(msgTypes, this);
	}

	/** @see UTILpropagation#sendOutput(UtilitySolutionSpace, java.lang.String) */
	@Override
	protected void sendOutput(UtilitySolutionSpace<Val, U> space, String root) {
		OptUtilMessage<Val, U> output = new OptUtilMessage<Val, U> (space, root);
		queue.sendMessageToSelf(output);
		queue.sendMessage(AgentInterface.STATS_MONITOR, output);
	}

	/**
	 * @return 	the optimal utility found to the problem
	 * @warning Only works if the module is instantiated in stats gatherer mode. 
	 */
	public UtilitySolutionSpace<Val, U> getOptParamUtil () {
		return this.optUtil;
	}
}
