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

package frodo2.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.PipeFactory;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.communication.tcp.TCPPipeFactory;
import frodo2.controller.userIO.Console;
import frodo2.controller.userIO.DistributedSolver;
import frodo2.controller.userIO.UserIO;
import frodo2.daemon.Constructor;
import frodo2.daemon.LocalWhitePages;

/**
 * The controller class, a container for
 * the listeners that do the actual work
 * 
 * @author Brammert Ottens
 * @author Thomas Leaute
 */
public class Controller {
	
	/**
	 * userIO takes care of all the user input and output
	 */
	private UserIO userIO;
	
	/**
	 * the configuration manager sets up the experiments
	 */
	private ConfigurationManager confManager;
	
	/**
	 * The instance of the PipeFactory Visitor
	 */
	public static PipeFactory PipeFactoryInstance = new TCPPipeFactory();
	
	/**
	 * The queue that is used to send and receive messages
	 */
	protected Queue controlQueue;
	
	/** The global white pages*/
	private WhitePages wp;
	
	/** The local white pages, used when the experiments are run locally*/
	private LocalWhitePages lwp;
	
	/** The agent constructor, used when the experiments are run locally*/
	private Constructor constructor;
	
	/** Agent repository, used when experiments are run locally*/
	private HashMap< String, AgentInterface<?> > agents;
	
	/**
	 * is true when the experiment is suspended and false otherwise
	 */
	public boolean isSuspended = false;
	
	/**
	 * Is true when the experiment has stopped and false when it is running
	 */
	public boolean isStopped = true;
	
	
	/**
	 * Is true when the experiments are finished
	 */
	public boolean isFinished = false;
	
	/** The port the controller is listening to
	 * @todo Should be customizable. 
	 */
	public static final int PORT = 3000;
	
	/** The input pipe for external messages*/
	private QueueInputPipeInterface input;
	
	/** Used to synchronized access to isFinished */
	public Object isFinishedSync;
	
	/**
	 * Constructor for the controller. Experiments can either be run on the same
	 * machine the controller is running on, or on (a) separate machine(s).
	 * @param local true if the experiments are run locally
	 * @param ui true if the user interface should be used
	 * @param workDir folder containing the files mentioned in the configuration document. Must end with a slash.
	 */
	public Controller(boolean local, boolean ui, String workDir) {
		if(local) {
			initLocal(ui, workDir);
		} else {
			init(workDir, null);
		}
	}
	
	/** Constructor to use a solver in distributed mode
	 * @param solver 	the distributed solver
	 */
	public Controller (DistributedSolver<?, ?, ?> solver) {
		init ("", solver);
	}
	
	/**
	 * Constructor for the controller. Experiments can either be run on the same
	 * machine the controller is running on, or on (a) separate machine(s).
	 * @param local true if the experiments are run locally
	 * @param ui true if the user interface should be used
	 */
	public Controller(boolean local, boolean ui) {
		this (local, ui, "");
	}
	
	/**
	 * counts the number of instantiated daemon in order to launch the controller when everybody is ready.
	 */
	public static Integer daemonNb = null;
	
	/**
	 * @param local true if the experiments are run locally
	 * @param ui true if the user interface should be used
	 * @param testDir the working directory
	 * @param size the number of daemon to be instantiated
	 */
	public Controller(boolean local, boolean ui, String testDir, int size) {
		this (local,ui,testDir);
		daemonNb = size;
	}

	/**
	 * Initializes the controller
	 * @param workDir 	folder containing the files mentioned in the configuration document. Must end with a slash.
	 * @param solver 	the distributed solver, if any
	 */
	private void init(String workDir, DistributedSolver<?, ?, ?> solver) {
		// create the sync object for the isFinished variable
		isFinishedSync = new Object();
		
		// create the queue and the listeners
		controlQueue = new Queue(false);
		try{
			this.input = Controller.PipeFactoryInstance.inputPipe(controlQueue, Controller.PipeFactoryInstance.getSelfAddress(PORT));
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		confManager = new ConfigurationManager(this, false, workDir);	
		wp = new WhitePages();
		
		if (solver != null) 
			this.userIO = solver;
		else 
			this.userIO = new Console(this);
		
		// add the listeners and pipes to the queue
		controlQueue.addIncomingMessagePolicy(userIO);
		controlQueue.addIncomingMessagePolicy(confManager);
		controlQueue.addIncomingMessagePolicy(wp);
	}
	
	/**
	 * Initializes the controller for running the experiments itself
	 * @param ui 	currently unused parameter
	 * @param workDir folder containing the files mentioned in the configuration document. Must end with a slash.
	 * @bug ui is not used. 
	 */
	private  void initLocal(boolean ui, String workDir) {
		// create the sync object for the isFinished variable
		isFinishedSync = new Object();
		
		// create the queue and the listeners		
		controlQueue = new Queue(false);
		input = new QueueIOPipe(controlQueue);
		confManager = new ConfigurationManager(this, true, workDir);	
		constructor = new Constructor(true, (QueueIOPipe)input, this);
		lwp = new LocalWhitePages(this);
		userIO = new Console(this);
		
		// add the listeners and pipes to the queue
		controlQueue.addIncomingMessagePolicy(userIO);
		controlQueue.addIncomingMessagePolicy(confManager);
		controlQueue.addIncomingMessagePolicy(lwp);
		controlQueue.addIncomingMessagePolicy(constructor);
		
		// initialize the agent repository
		agents = new HashMap< String, AgentInterface<?> >();
	}
	
	/**
	 * @param args	if -local then the experiments are run on the local machine (default is distributed)
	 * 				if -license is passed as an argument, displays the license and returns
	 */
	public static void main(String[] args) {
		
		String workDir = "";
		
		Arrays.sort(args);
		
		// Print out the license and return if the -license option was used
		if (args.length > 0 && args[0].equals("-license")) {
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
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. Use the option -license to display the license.\n");
		
		if(args.length == 2 && args[0].equals("-workdir")) {
			if(args[1].endsWith("/")) {
				workDir = args[1];
			} else {
				workDir = args[1] + "/";
			}
		} else if(args.length == 3) {
			if(args[2].endsWith("/")) {
				workDir = args[2];
			} else {
				workDir = args[2] + "/";
			}
		}
		
		if (workDir.length() > 0) 
			System.out.println("The working directory is set to: " + workDir);
		
		if (args.length > 0 && args[0].equals("-local")) {
			System.out.println("Running locally");
			new Controller(true, true, workDir).run();
		} else if(args.length == 0 || (args.length > 0 && args[0].equals("-workdir"))) {
			System.out.println("Running distributedly");
			new Controller(false, true, workDir).run();
		} else {
			System.err.println("The arguments given are not valid. Please either give -local or -license");
		}
	}

	/**
	 * Start the controller
	 */
	private void run() {
		userIO.start();
	}
	
	/**
	 * Function to add agents to the repository. Only to be used when the experiments are run locally
	 * @param agentId 	the agent's ID
	 * @param agent 	the agent itself
	 */
	public void addAgent(String agentId, AgentInterface<?> agent) {
		if(agents == null) {
			userIO.tellUser("The experiments are not run locally! Please initialize the controller with controller(true, true)");
		} else {
			agents.put(agentId, agent);
		}
	}
	
	/**
	 * Add agent toID's input pipe as an output pipe to agent fromID's queue
	 * @param fromID 	the ID of the sender agent
	 * @param toID 		the ID of the recipient agent
	 * @param pipe 		the pipe
	 */
	public void addOutputPipe(String fromID, String toID, QueueOutputPipeInterface pipe) {
		assert(agents.containsKey(fromID)) : "Either the problem does not completely run on the controller machine or agent " + fromID + " has not registered yet";
		agents.get(fromID).addOutputPipe(toID, pipe);
	}
	
	/**
	 * @return the set of agents known to the controller
	 */
	public Collection<String> getAgents() {
		return agents.keySet();
	}
	
	/**
	 * This function must be called to stop the controller
	 * @param quit 	whether the JVM should be terminated
	 */
	public void exit(boolean quit) {
		controlQueue.end();
		if(quit) {
			System.exit(0);
		}
		
	}

	/** This functions sets a boolean value to true when finished. Used for testing purposes*/
	public void setFinished() {
		System.out.println("Experiments finished");
		synchronized (isFinishedSync) {
			isFinished = true;
			this.isFinishedSync.notify();
		}
	}

	/**
	 * This function is there mainly for test purposes and can be removed in the end
	 */
	public void endQueue() {
		controlQueue.end();
	}

	/**
	 * Delete all the agents from the repository
	 */
	public void clearAgents() {
		agents.clear();
		
	}
}
