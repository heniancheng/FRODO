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
package frodo2.daemon.userIO;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;

import frodo2.communication.AgentAddress;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.controller.messages.MessageAgentReporting;
import frodo2.daemon.Daemon;
import frodo2.daemon.LocalWhitePages;
import frodo2.controller.Controller;
import frodo2.controller.WhitePages;
import frodo2.controller.ConfigurationManager;

/**
 * @author brammertottens
 *
 */
public abstract class UserIO extends Thread implements IncomingMsgPolicyInterface<String> {
	
	/** The daemon this UI belongs to*/
	Daemon daemon;
	
	/** the daemons queue*/
	Queue queue;
	
	/** The list of messages types this listener wants to be notified of */
	private ArrayList <String> msgTypes = new ArrayList <String> ();

	/**
	 * Constructor
	 * @param daemon 	the daemon
	 */
	public UserIO(Daemon daemon) {
		this.daemon = daemon;
		msgTypes.add(WhitePages.KILL_AGENTS);
		msgTypes.add(ConfigurationManager.AGENT_CONFIGURATION_MESSAGE);
	}

	/** @see java.lang.Thread#start() */
	@Override
	public void start () {
		super.start();
	}

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes()
	 */
	public Collection<String> getMsgTypes() {
		return msgTypes;
	}

	/** 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 */
	public void notifyIn(Message msg) {
		/// @todo Auto-generated method stub
		return;
	}

	/** 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
	
	/**
	 * Report the daemon to the controller
	 * @param address of the controller
	 * @author Brammert Ottens, Thomas Leaute
	 */
	public void registerController(AgentAddress address) {
		QueueOutputPipeInterface output = null;
		try {
			output = Controller.PipeFactoryInstance.outputPipe(address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		queue.addOutputPipe(LocalWhitePages.CONTROLLER_ID, output);
		MessageAgentReporting msg = new MessageAgentReporting(MessageAgentReporting.DEAMON_REPORTING, daemon.daemonId, daemon.daemonAddress);
		queue.sendMessage(LocalWhitePages.CONTROLLER_ID, msg);
	}
	
	/**
	 * Quit the daemon;
	 */
	public void exit() {
		daemon.exit(true);
	}
	
	/**
	 * Give a message to the user
	 * @param message 	the message to be presented to the user
	 */
	public abstract void tellUser(String message);
	
	/**
	 * Stop the user interface
	 */
	public abstract void stopRunning();

}
