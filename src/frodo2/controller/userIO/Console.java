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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import frodo2.communication.AgentAddress;
import frodo2.controller.Controller;

/**
 * A simple console based user interface
 * 
 * It responds to the following commands:
 * - to load a configuration file, type
 * 		open [filename]
 * - to start the experiments, type
 * 		start
 * - to exit, type
 * 		exit
 * @author brammertottens
 *
 */
public class Console extends UserIO {
	
	/**
	 * This variable is used to determine whether the program should exit or not
	 */
	boolean done = false;
	
	/**
	 * The constructor
	 * @param control 	the controller
	 */
	public Console(Controller control) {
		super(control);
	}

	/**
	 * 
	 * @see frodo2.controller.userIO.UserIO#askUserYesNo(java.lang.String)
	 */
	@Override
	public boolean askUserYesNo(String message) {
		/// @todo Auto-generated method stub
		assert false : "not implemented!";
		return false;
	}

	/**
	 * 
	 * @see frodo2.controller.userIO.UserIO#tellUser(java.lang.String)
	 */
	@Override
	public void tellUser(String message) {
		System.out.println("\n>>" + message);
		System.out.print(">");
	}
	
	/**
	 * 
	 * @see frodo2.controller.userIO.UserIO#showUserDaemonList(java.util.HashMap)
	 */
	public void showUserDaemonList(HashMap<String, AgentAddress> daemons) {
		System.out.println("\n     Registered Daemons");
		System.out.println("-------------------------------");
		for(Map.Entry<String, AgentAddress> entry : daemons.entrySet()) 
			System.out.println(entry.getKey() + " -> " + entry.getValue());
		System.out.println("-------------------------------");
		System.out.print(">");
	}
	
	/**
	 * 
	 * @see frodo2.controller.userIO.UserIO#showUserAgentList(java.util.HashMap)
	 */
	public void showUserAgentList(HashMap<String, AgentAddress> agents) {
		System.out.println("\n     Registered Agents");
		System.out.println("-------------------------------");
		for(Map.Entry<String, AgentAddress> entry : agents.entrySet()) 
			System.out.println(entry.getKey() + " -> " + entry.getValue());
		System.out.println("-------------------------------");
		System.out.print(">");
	}
	
	/**
	 * 
	 * @see frodo2.controller.userIO.UserIO#stopRunning()
	 */
	@Override
	public void stopRunning() {
		done = true;
	}
	
	/**
	 * The main loop that waits for user input is situated here
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		try{
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in), 1);
			
			while(!done) {
				// while the user does not want to stop
				// parse the input
				System.out.print(">");
				parseInput(br.readLine());
			}
		} catch(IOException ex) {
			 System.err.println(ex);
 		}
	}
	
	/**
	 * A simple function that looks at the input and determines what to
	 * @param input 	input String
	 */
	public void parseInput(String input) {
		input = input.trim();
		String[] parts = input.split(" ");
		
		if(parts[0].equals("open")) {
			try {
				this.load(parts[1]);
			} catch(ArrayIndexOutOfBoundsException ex) {
				tellUser("When opening a file you must give a filename!");
			}
		} else if (input.equals("get daemons")) {
			getDaemonList();
		} else if (input.equals("get agents")) {
			getAgentList();
		} else if(parts[0].equals("start")) {
			startExperiment();
//		} else if(parts[0].equals("suspend")) {
//			this.suspendExperiment();
//		} else if(parts[0].equals("continue")) {
//			this.continueExperiment();
//		} else if(parts[0].equals("stop")) {
//			this.stopExperiment();
//		} else if(parts[0].equals("clean")) {
//			this.clean();
//		} else if(parts[0].equals("debug")) {
//			if(parts[0].equals("on")) {
//				this.setDebug(true);
//			} else if(parts[0].equals("off")) {
//				this.setDebug(false);
//			} else {
//				tellUser("only on or off is allowed!");
//			}
//		} else if(parts[0].equals("logging")) {
//			if(parts[0].equals("on")) {
//				this.setLogging(true);
//			} else if(parts[0].equals("off")) {
//				this.setLogging(false);
//			} else {
//				tellUser("only on or off is allowed!");
//			}
		} else if(parts[0].equals("exit")) {
			this.exit();
			done = true;
		}
		else {
			System.err.println("The command \"" + input + "\" is not recognized!");
		}
	}

}
