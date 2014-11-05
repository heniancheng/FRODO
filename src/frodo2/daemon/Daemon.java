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
package frodo2.daemon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.AgentAddress;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.communication.tcp.TCPAddress;
import frodo2.controller.Controller;
import frodo2.controller.messages.MessageAgentReporting;
import frodo2.daemon.userIO.Console;
import frodo2.daemon.userIO.UserIO;

/**
 * @author brammertottens
 * This is the client side daemon that spawns
 * new agents for the experiments
 */
public class Daemon {
	
	/** The default port the Daemon is listening to */
	private static final int DEFAULT_PORT = 25000;
	
	/** The id of the daemon */
	public static final String DAEMON = "deamon";
	
	/** The pipe for receiving internal messages*/
	private QueueIOPipe inputInternal;
	
	/** The daemons local Id*/
	public String daemonId;
	
	/** The daemon address */
	public AgentAddress daemonAddress;
	
	/** The daemons queue*/
	Queue daemonQueue;
	
	/** User interface*/
	private UserIO ui;
	
	/** Local white pages*/
	private LocalWhitePages lwp;
	
	/** the constructor*/
	private Constructor constructor;
	
	/** A list of agents*/
	private HashMap< String, AgentInterface<?> > agentList;

	/** The port number that the daemon listens on */
	private int port;

	/**
	 * The constructor
	 * @param useUI 	whether or not to use the User Interface
	 */
	public Daemon(boolean useUI) {
		this(useUI, DEFAULT_PORT);
	}
	
	/**
	 * The constructor
	 * @param useUI 	whether or not to use the User Interface
	 * @param port 		the port number the daemon listens on
	 */
	public Daemon(boolean useUI, int port) {
		this.port = port;
		init(useUI);
	}
	
	/**
	 * A constructor that tells the daemon the controller's address
	 * @param controllerAddress 	address of the controller
	 * @param useUI 				whether or not to use the User Interface
	 */
	public Daemon(AgentAddress controllerAddress, boolean useUI) {
		this(controllerAddress, useUI, Daemon.DEFAULT_PORT);
	}
	
	/**
	 * A constructor that tells the daemon the controller's address
	 * @param controllerAddress 	address of the controller
	 * @param useUI 				whether or not to use the User Interface
	 * @param port 					the port number the daemon listens on
	 */
	public Daemon(AgentAddress controllerAddress, boolean useUI, int port) {
		this.port = port;
		init(useUI);
		ui.registerController(controllerAddress);
	}
	
	/**
	 * Initialisation
	 * @param useUI 	whether or not to use the User Interface
	 */
	private void init(boolean useUI) {
		
		// get the local address
	
		daemonAddress = Controller.PipeFactoryInstance.getSelfAddress(port);
		daemonId = "daemon@" + daemonAddress;
		
		// create the listeners
		daemonQueue = new Queue(false);
		inputInternal = new QueueIOPipe(daemonQueue);
		try {
			Controller.PipeFactoryInstance.inputPipe(daemonQueue, daemonAddress);
		} catch (IOException e) {
			e.printStackTrace();
		}
		lwp = new LocalWhitePages(this);
		agentList = new HashMap< String, AgentInterface<?> >();
		constructor = new Constructor(false, inputInternal, this);
		ui = new Console(this);
		if(useUI) {
			ui.start();
		}
		
		// add the listeners and pipes to the queue
		daemonQueue.addIncomingMessagePolicy(ui);
		daemonQueue.addIncomingMessagePolicy(lwp);
		daemonQueue.addIncomingMessagePolicy(constructor);
	}
	
	/**
	 * @param args 	arguments passed to the main method
	 */
	public static void main(String[] args) {
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. Use the option -license to display the license.\n");
		
		// Loop through the list of input arguments 
		String controllerIP = null;
		int daemonPort = DEFAULT_PORT;
		for (int i = 0; i < args.length; i++) {
			
			// If passed "-license", display the license and quit
			if (args[i].equals("-license")) {
				try {
					BufferedReader reader = new BufferedReader (new FileReader (new File ("LICENSE.txt")));
					String line = reader.readLine();
					while (line != null) {
						System.out.println(line);
						line = reader.readLine();
					}
					reader.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					System.exit(1);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				return;
			}
			
			// If passed "-controller IP", set the controller's address to IP
			/// @todo The controller's port should be parameterizable
			if (args[i].equals("-controller") && ++i < args.length) {
				controllerIP = args[i];
				continue;
			} else if (args[i].equals("-daemonport") && ++i < args.length) {
				daemonPort = Integer.parseInt(args[i]);
				continue;
			}

			System.err.println("Invalid arguments given. Please use one of the following arguments");
			System.err.println("------------------------------------------------------------------");
			System.err.println("\n-license\t\t to print the license");
			System.err.println("-controller ip-address\t to give the IP address of the controller");
			System.err.println("-daemonport port\t to give the port number the daemon should listen on");
			System.exit(1);
		}
		
		if (controllerIP != null) 
			new Daemon (new TCPAddress(controllerIP, Controller.PORT), true, daemonPort); 
		else 
			new Daemon(true, daemonPort);
	}
	
	/** @return the port number that the daemon listens on */
	public int getPort () {
		return this.port;
	}
	
	/**
	 * get the list of agents running under this daemon
	 * @return a collection of agent names
	 */
	public Collection<String> getAgents() {
		return agentList.keySet();
	}
	
	/**
	 * Add an agent to the list of agents
	 * @param ID 		ID of the agent
	 * @param agent 	the agent itself
	 */
	public void addAgent(String ID, AgentInterface<?> agent) {
		agentList.put(ID, agent);
	}
	
	/**
	 * Add agent toID's input pipe as an output pipe to agent fromID's queue
	 * @param fromID 	ID of the sender agent
	 * @param toID 		ID of the recipient agent
	 * @param pipe 		the pipe the sender should use to send messages to the recipient
	 */
	public void addOutputPipe(String fromID, String toID, QueueOutputPipeInterface pipe) {
		assert(agentList.containsKey(fromID)) : "Either the problem does not completely run on the controller machine or agent " + fromID + " has not registered yet";
		agentList.get(fromID).addOutputPipe(toID, pipe);
	}
	
	/**
	 * Register the daemon to the controller. Only to be used for testing purposes!
	 */
	public void registerDaemon() {
		MessageAgentReporting msg = new MessageAgentReporting(MessageAgentReporting.DEAMON_REPORTING, daemonId, daemonAddress);
		daemonQueue.sendMessage(LocalWhitePages.CONTROLLER_ID, msg);
	}
	
	/**
	 * Getter for the Daemons internal input pipe
	 * @return QueueIOPipe
	 */
	public QueueIOPipe getInput() {
		return inputInternal;
	}
	
	/**
	 * Delete the pointers to all agents that are running.
	 * Used to clear up after an experiment.
	 */
	public void clearAgents() {
		synchronized (agentList) {
			agentList.clear();
		}
	}
	
	/**
	 * Exit the daemon
	 * @param quit 	whether the JVM should be terminated
	 * @bug Before exiting, the Daemon should warn the Controller. 
	 */
	public void exit(boolean quit) {
		daemonQueue.end();
		ui.stopRunning();
		if(quit) {
			System.exit(0);
		}
	}

}
