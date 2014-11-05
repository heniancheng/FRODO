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

package frodo2.controller.userIO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import frodo2.communication.AgentAddress;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.Queue;
import frodo2.controller.Controller;
import frodo2.controller.WhitePages;

/**
 * This class implements all the functions that a user interfcae should deliver\
 * @author brammertottens
 *
 */
public abstract class UserIO extends Thread implements IncomingMsgPolicyInterface<String>{
	
	/** The message type used to convey the configuration file*/
	public static final String CONFIGURATION_MSG = "Configure";
	
	/** The messag type used to send messages to the user*/
	public static final String USER_NOTIFICATION_MSG = "User-Notification";
	
	/** The message the user sends to start the experiment*/
	public static final String START_MSG = "StartE";
	
	/** Message send to request a list of daemons from the white pages*/
	public static final String DEAMON_LIST_REQUEST = "Daemon list requested";
	
	/** Message send to request a list of agents from the white pages*/
	public static final String AGENTS_LIST_REQUEST = "Agent list requested";
	
	/** The queue of the controller*/
	protected Queue controlQueue;
	
	/** The controller*/
	protected Controller control;
	
	/** Lock for \a queue used for synchronization
	 * 
	 * We cannot synchronize directly on \a queue because it can be \c null. 
	 */
	private Object queue_lock = new Object();
	
	/** The list of messages types this listener wants to be notified of */
	private ArrayList <String> msgTypes = new ArrayList <String> ();
	
	/**
	 * A basic constructor
	 * @param control a reference to the controller
	 */
	public UserIO(Controller control) {
		this.control = control;
		msgTypes.add(USER_NOTIFICATION_MSG);
		msgTypes.add(WhitePages.DEAMONS_LIST);
		msgTypes.add(WhitePages.AGENTS_LIST);
	}
	
	/** Default constructor */
	protected UserIO () {
		this(null);
	}
	
	/** @see java.lang.Thread#start() */
	@Override
	public void start () {
		super.start();
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return msgTypes;
	}
	
	/**
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		synchronized (queue_lock) {
			this.controlQueue = queue;
		}
	}
	
	/**
	 * The user interface responds to message of the type
	 * USER_NOTIFICATION_MSG, that contain message to be
	 * displayed to the user
	 * @param msg	 the message that was just received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		if(msg.getType().equals(USER_NOTIFICATION_MSG)) {
			tellUser(((MessageWithPayload<String>)msg).getPayload());
		} 
		
		if(msg.getType().equals(WhitePages.DEAMONS_LIST)) {
			showUserDaemonList(((MessageWithPayload<HashMap<String, AgentAddress>>)msg).getPayload());
		}
		
		if(msg.getType().equals(WhitePages.AGENTS_LIST)) {
			showUserAgentList(((MessageWithPayload<HashMap<String, AgentAddress>>)msg).getPayload());
		}
	}
	
	
	
	/**
	 * Used to load a new configuration file
	 * @param filename the file to be loaded
	 */
	public void load(String filename) {
		MessageWithPayload<String> msg = new MessageWithPayload<String>(CONFIGURATION_MSG, filename);
		controlQueue.sendMessageToSelf(msg);
	}
	
	/**
	 * Used to start the experiments
	 */
	public void startExperiment() {
		Message msg = new Message(START_MSG);
		controlQueue.sendMessageToSelf(msg);
	}
	/**
	 * Used to stop the experiments
	 */
//	public void stopExperiment() {
//		control.stopExperiment();
//	}
	
	/**
	 * Used to suspend the current experiment
	 */
//	public void suspendExperiment() {
//		if(control.isSuspended) {
//			tellUser("The experiment is already suspended!");
//		} else {
//			control.suspendExperiment();
//		}
//	}
	
	/**
	 * Used to continue a suspended experiment
	 */
//	public void continueExperiment() {
//		if(!control.isSuspended) {
//			tellUser("The experiment is already running!");
//		} else {
//			control.continueExperiment();
//		}
//	}
	
	/**
	 * A message is send to all daemons to clean everything up.
	 * If project is still running or is suspended, ask the user if he really wants
	 * to clean everything up!
	 */
//	public void clean() {
//		if(!control.isStopped) {
//			if(askUserYesNo("The project you want to clean is still running. Do you want to stop the experiment and clean up? yes/no")) {
//				control.stopExperiment();
//				control.clean();
//			} 
//		} else {
//			control.clean();
//		}
//	}
	
	/**
	 * Tell the controller whether we want to do step by step debugging
	 * @param b 	whether debug mode should be enabled
	 */
	public void setDebug(boolean b) {
		tellUser("Not yet implemented");
	}
	
	/**
	 * Tell the controller whether we want to log everything or not
	 * @param b 	whether logging should be enabled
	 */
	public void setLogging(boolean b) {
		tellUser("Not yet implemented");
	}
	
	/**
	 * Ask the white pages for a list of all daemons that are registered
	 */
	public void getDaemonList() {
		Message msg = new Message(DEAMON_LIST_REQUEST);
		controlQueue.sendMessageToSelf(msg);
	}
	
	/**
	 * Ask the white pages for a list of all agents that are registered
	 */
	public void getAgentList() {
		Message msg = new Message(AGENTS_LIST_REQUEST);
		controlQueue.sendMessageToSelf(msg);
	}
	
	/**
	 * Stop the experiment, clean everything up and exit the program
	 */
	public void exit() {
//		if(!control.isStopped) {
//			if(askUserYesNo("The experiment is still running. Do you really want to stop? yes\no")) {
//				control.stopExperiment();
//				control.clean();
//				control.exit();
//			}
//		}
		
		control.exit(true);
		
	}
	
	
	/*
	 * Abstract functions
	 */
	
	/**
	 * Tell the user something
	 * @param message the message to be given
	 */
	public abstract void tellUser(String message);
	
	/**
	 * Ask the user a yes or no question
	 * @param message 	the text of the question
	 * @return return the answer of the user: yes is true, no is false
	 */
	public abstract boolean askUserYesNo(String message);
	
	/**
	 * stop the console
	 */
	public abstract void stopRunning();
	
	/**
	 * Display the list of daemons
	 * @param daemons a list of daemons together with their addresses
	 */
	public abstract void showUserDaemonList(HashMap<String, AgentAddress> daemons);
	
	/**
	 * Display the list of agents
	 * @param agents 	a list of agents together with their addresses
	 */
	public abstract void showUserAgentList(HashMap<String, AgentAddress> agents);
	

}
