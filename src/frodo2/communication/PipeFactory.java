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

package frodo2.communication;

import java.io.IOException;
import java.net.UnknownHostException;


/**
 * @author Xavier Olive
 * for pipe instantiatons
 * is implemented for TCP and MPI for instance
 */
public interface PipeFactory {
	
	/** create an input pipe with the following arguments
	 * @param queue (self-explanatory)
	 * @param address (self-explanatory)
	 * @param maxNbrConnections (self-explanatory)
	 * @return a pipe
	 * @throws IOException (self-explanatory)
	 */
	QueueInputPipeInterface inputPipe(Queue queue, AgentAddress address, Integer maxNbrConnections) throws IOException;
	
	/** create an input pipe with the following arguments
	 * @param queue (self-explanatory)
	 * @param address (self-explanatory)
	 * @return a pipe
	 * @throws IOException (self-explanatory)
	 */
	QueueInputPipeInterface inputPipe(Queue queue, AgentAddress address) throws IOException;

	/**
	 * @param address (self-explanatory)
	 * @return a pipe
	 * @throws UnknownHostException (self-explanatory)
	 * @throws IOException (self-explanatory)
	 */
	QueueOutputPipeInterface outputPipe(AgentAddress address) throws UnknownHostException, IOException;
	
	/** create an output pipe with the following arguments
	 * @param address (self-explanatory)
	 * @param rawDataAddress (self-explanatory)
	 * @return a pipe
	 * @throws UnknownHostException (self-explanatory)
	 * @throws IOException (self-explanatory)
	 */
	QueueOutputPipeInterface outputPipe(AgentAddress address, AgentAddress rawDataAddress) throws UnknownHostException, IOException;

	/**
	 * @param idx can be port (tcp), rank (mpi), ...
	 * @return an address to yourself depending on an int parameter
	 */
	public AgentAddress getSelfAddress(int idx);

}
