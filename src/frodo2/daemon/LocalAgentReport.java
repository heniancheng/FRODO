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

package frodo2.daemon;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.MessageWith3Payloads;
import frodo2.communication.sharedMemory.QueueIOPipe;

/** A message that an agent sends to its local white pages to report
 * @author Thomas Leaute
 */
public class LocalAgentReport extends MessageWith3Payloads<String, Integer, QueueIOPipe> {
	
	/** Empty constructor used for externalization */
	public LocalAgentReport () { }

	/** Constructor
	 * @param agentID 		the agent's ID
	 * @param port 			the port the agent listens on 
	 * @param localPipe 	the agent's local I/O pipe
	 */
	public LocalAgentReport (String agentID, Integer port, QueueIOPipe localPipe) {
		super (AgentInterface.LOCAL_AGENT_REPORTING, agentID, port, localPipe);
	}
	
	/** @return the agent's ID */
	public String getAgentID () {
		return super.getPayload1();
	}
	
	/** @return the port the agent listens on */
	public Integer getPort () {
		return super.getPayload2();
	}

	/** @return the agent's local I/O pipe */
	public QueueIOPipe getLocalPipe () {
		return super.getPayload3();
	}
	
}
