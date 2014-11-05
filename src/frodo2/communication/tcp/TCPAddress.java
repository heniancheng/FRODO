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

/**
 * 
 */
package frodo2.communication.tcp;

import frodo2.communication.AgentAddress;

/** A TCP/IP-port address
 * @author Xavier Olive, Thomas Leaute
 */
public class TCPAddress implements AgentAddress {
	
	/** Used for serialization */
	private static final long serialVersionUID = 7567208652764791606L;
	
	/** The IP address */
	private String _address;
	
	/** The port number */
	private int _port;
	
	/** Constructor
	 * @param iAddress 	IP address
	 * @param iPort 	port number
	 */
	public TCPAddress(String iAddress, int iPort) {
		_address = iAddress;
		_port = iPort;
	}
	
	/** @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals (Object o) {
		
		if (! (o instanceof TCPAddress)) 
			return false;
		
		TCPAddress o2 = (TCPAddress) o;
		
		return this._address.equals(o2._address) && this._port == o2._port;
	}
	
	/** @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return _address + ":" + _port;
	}
	
	/** @see AgentAddress#newAddress(int) */
	public TCPAddress newAddress(int iPort) {
		return new TCPAddress(_address,iPort);
	}
	
	/** @return the IP address */
	public String getAddress() {
		return _address;
	}
	
	/** @return the port number */
	public int getPort() {
		return _port;
	}

}