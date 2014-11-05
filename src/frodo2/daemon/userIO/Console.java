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

package frodo2.daemon.userIO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import frodo2.communication.tcp.TCPAddress;
import frodo2.controller.Controller;
import frodo2.daemon.Daemon;

/**
 * A simple console based user interface
 * 
 * The console responds to the following commands
 * - to register the daemon to the controller, type
 * 		controller [IP of the controller] 3000 (the latter is the port of the controller)
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
	 * @param daemon 	the daemon
	 */
	public Console(Daemon daemon) {
		super(daemon);
	}

	/**
	 * 
	 * @see frodo2.controller.userIO.UserIO#tellUser(java.lang.String)
	 */
	@Override
	public void tellUser(String message) {
		System.out.println(">>" + message);
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
	 * @param input 	the input to be parsed
	 */
	public void parseInput(String input) {
		input = input.trim();
		String[] parts = input.split(" ");
		if(parts[0].equals("controller")) {
			/// @todo The controller's port should be parameterizable
			registerController(new TCPAddress(parts[1], Controller.PORT));
		} else if(parts[0].equals("exit")) {
			this.exit();
			done = true;
		}
		else {
			tellUser("The command \"" + input + "\" is not recognized!");
		}
	}
	
}
