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

package frodo2.communication.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import frodo2.communication.AgentAddress;
import frodo2.communication.PipeFactory;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.communication.QueueOutputPipeInterface;

/**
 * @author Xavier Olive
 * This factory implements tcp pipes
 */
public class TCPPipeFactory implements PipeFactory {
	
	/** @see frodo2.communication.PipeFactory#inputPipe(frodo2.communication.Queue, frodo2.communication.AgentAddress, java.lang.Integer) */
	public QueueInputPipeInterface inputPipe(Queue queue, AgentAddress address, Integer maxNbrConnections) throws IOException {
		TCPAddress newAddress = (TCPAddress) address;
		return new QueueInputPipeTCP(queue, newAddress.getPort(), maxNbrConnections);
	}
	
	/** @see frodo2.communication.PipeFactory#inputPipe(frodo2.communication.Queue, frodo2.communication.AgentAddress) */
	public QueueInputPipeInterface inputPipe(Queue queue, AgentAddress address) throws IOException {
		TCPAddress newAddress = (TCPAddress) address;
		return new QueueInputPipeTCP(queue, newAddress.getPort());
	}

	/** @see frodo2.communication.PipeFactory#outputPipe(frodo2.communication.AgentAddress) */
	public QueueOutputPipeInterface outputPipe(AgentAddress address) throws UnknownHostException, IOException {
		TCPAddress newAddress = (TCPAddress) address;
		return new QueueOutputPipeTCP(newAddress.getAddress(),newAddress.getPort());
	}

	/** @see frodo2.communication.PipeFactory#outputPipe(frodo2.communication.AgentAddress, frodo2.communication.AgentAddress) */
	public QueueOutputPipeInterface outputPipe(AgentAddress address, AgentAddress rawDataAddress) throws UnknownHostException, IOException {
		TCPAddress newAddress = (TCPAddress) address;
		TCPAddress newRawAddress = (TCPAddress) rawDataAddress;
		return new QueueOutputPipeTCP(newAddress.getAddress(),newAddress.getPort(),newRawAddress.getAddress(),newRawAddress.getPort());
	}

	/** @see frodo2.communication.PipeFactory#getSelfAddress(int) */
	public AgentAddress getSelfAddress(int idx) {
		try {
			return new TCPAddress(InetAddress.getLocalHost().getCanonicalHostName(),idx);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
}
