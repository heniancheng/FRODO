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
package frodo2.controller.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.AgentAddress;
import frodo2.communication.Message;

/**
 * @author brammertottens
 *
 * This message contains three strings
 *  - the name of the agent reporting
 *  - the IP adres
 *  - the port it is listening to
 *  @todo Refactor this to MessageWith3Payloads
 */
public class MessageAgentReporting extends Message {

	/** The type of the message used by an agent to report to the white pages*/
	public static final String AGENT_REPORTING = "Agent-Reporting";
	
	/** The type of the message used by an daemon to report to the white pages*/
	public static final String DEAMON_REPORTING = "Daemon-Reporting";
	
	/** The ID of the agent*/
	private String ID;

	/**
	 * The address of the agent
	 */
	private AgentAddress address;

	
	/**
	 * A constructor
	 * @param type the message type
	 * @param ID the ID of the agent
	 * @param address the address of the agent
	 */
	public MessageAgentReporting(String type, String ID, AgentAddress address) {
		super(type);
		this.ID = ID;
		this.address = address;
	}
	
	/** Empty constructor used for externalization */
	public MessageAgentReporting () { }
	
	/** @see Message#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(this.address);
		out.writeObject(this.ID);
	}

	/** @see Message#readExternal(java.io.ObjectInput) */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.address = (AgentAddress) in.readObject();
		this.ID = (String) in.readObject();
	}

	/**
	 * get the ID of the agent
	 * @return ID
	 */
	public String getID() {
		return ID;
	}
	
	/**
	 * get the IP of the agent
	 * @return IP
	 */
	public AgentAddress getAddress() {
		return address;
	}
}
