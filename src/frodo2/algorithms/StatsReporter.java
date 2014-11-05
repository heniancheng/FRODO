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

package frodo2.algorithms;

import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Queue;

/** An interface that any listener sending statistics message should implement
 * @author Thomas Leaute
 * @warning All modules implementing this interface must have a constructor that takes in two parameters: 
 * an instance of DCOPProblemInterface describing the agent's subproblem, and a JDOM Element specifying the module's parameters. 
 * @warning All modules implementing this class should have a constructor that takes in two arguments: 
 * a JDOM Element describing the StatsReporter and its parameters, and a DCOPProblemInterface for the overall problem. 
 * @todo Using two different constructors with reversed parameter orders is confusing; use a single constructor. 
 * @todo Create StatsReporters that gather usual stats (solution, ...) to improve code reuse across algorithms. 
 */
public interface StatsReporter extends IncomingMsgPolicyInterface<String> {
	
	/** Registers the StatsReporter to be notified of the stats messages received by the given queue
	 * @param queue the queue
	 */
	public void getStatsFromQueue (Queue queue);

	/** Tells the StatsReporter whether it should print its stats or remain silent
	 * @param silent 	if \c false, prints its stats
	 */
	public void setSilent (boolean silent);
	
	/** Re-initializes all problem-dependent fields (except the problem itself) 
	 * 
	 * In stats gatherer mode, also re-parses the relevant problem parameters. 
	 */
	public void reset ();
	
}
