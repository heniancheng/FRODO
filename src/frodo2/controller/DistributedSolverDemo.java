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

import frodo2.algorithms.Solution;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.controller.userIO.DistributedSolver;
import frodo2.solutionSpaces.AddableInteger;

/** A demo for the distributed solver
 * @author Thomas Leaute
 */
public class DistributedSolverDemo {

	/** This should be launched instead of the Controller (which is created and launched internally by the DistributedSolver)
	 * @param args 	the XCSP problem file
	 * @throws InterruptedException 	thrown if the call to sleep() is interrupted
	 */
	public static void main(String[] args) throws InterruptedException {
		
		// Create the chosen underlying solver (for instance, for DPOP)
		DPOPsolver<AddableInteger, AddableInteger> solver = new DPOPsolver<AddableInteger, AddableInteger> ();
		
		// Create the distributed solver
		// Long live generics!
		DistributedSolver< AddableInteger, AddableInteger, Solution<AddableInteger, AddableInteger> > distSolver = 
				new DistributedSolver< AddableInteger, AddableInteger, Solution<AddableInteger, AddableInteger> > (solver);
		
		System.out.println("You have a few seconds to register the daemons...");
		Thread.sleep(15000);
		
		// Solve and display the solution
		System.out.println("Starting the experiment...");
		Solution<AddableInteger, AddableInteger> sol = distSolver.solve(args[0]);
		System.out.println(sol);
		
	}

}
