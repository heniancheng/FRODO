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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.AbstractSolver;
import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.Solution;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.communication.AgentAddress;
import frodo2.controller.ConfigurationManager;
import frodo2.controller.Controller;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** An interface to the Controller (and a wrapper thereof) that is based on a solver
 * @author Thomas Leaute
 * @param <V> type used for variable values
 * @param <U> type used for utility values
 * @param <S> type used for the solution
 */
public class DistributedSolver < V extends Addable<V>, U extends Addable<U>, S extends Solution<V,U> > extends UserIO {
	
	/** A fake agent factory */
	private class FakeFactory extends AgentFactory<V> { }

	/** A lock used to block until the experiment finishes */
	private final Object lock = new Object ();
	
	/** The underlying solver */
	private AbstractSolver<DCOPProblemInterface<V, U>, V, U, S> solver;
	
	/** The temporary agent file */
	private File agentDesc;
	
	/** The temporary configuration file */
	private File config;
	
	/** Constructor
	 * @param solver  	the underlying solver
	 */
	public DistributedSolver(AbstractSolver<DCOPProblemInterface<V, U>, V, U, S> solver) {
		super.control = new Controller (this);
		this.solver = solver;
		this.solver.setFactory(new FakeFactory ());
	}
	
	/** Solves the input problem 
	 * @param pathToXCSP 	the problem file in XCSP format
	 * @return the solution to the input problem
	 */
	public S solve (String pathToXCSP) {
		
		// Generate a temp agent file and a temp configuration file
		this.createTempFiles(pathToXCSP);
		super.load(this.config.getName());
		
		// Parse the problem and pass it to the solver
		try {
			this.solver.setProblem(new XCSPparser<V, U> (XCSPparser.parse(pathToXCSP)));
		} catch (Exception e) {
			System.err.println("Failed to parse the problem file");
			e.printStackTrace();
		}
		
		// Register the solver's solution gatherers to the Controller's queue
		for (StatsReporter module : this.solver.getSolGatherers()) 
			module.getStatsFromQueue(super.controlQueue);
		
		// Start the experiment
		synchronized (this.lock) {
			super.startExperiment();

			// Wait for the experiment to end
			try {
				this.lock.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// Delete the two temp files
		this.agentDesc.delete();
		this.config.delete();
		
		return this.solver.buildSolution();
	}

	/** Creates the temporary agent description and configuration files
	 * @param pathToXCSP 	the path to the problem file
	 */
	private void createTempFiles(String pathToXCSP) {
		
		// First create the agent configuration file
		Document agentDoc = this.solver.getAgentDesc();
		this.agentDesc = new File (".tmpAgentDesc.xml");
		try {
			new XMLOutputter().output(agentDoc, new FileWriter (this.agentDesc));
		} catch (IOException e) {
			System.err.println("Failed to write the temporary agent description file");
			e.printStackTrace();
		}
		
		// Now, create the experiment file
		Element expElmt = new Element ("experiment");
		
		Element tmpElmt = new Element ("configuration");
		expElmt.addContent(tmpElmt);
		
		Element tmp2Elmt = new Element ("resultFile");
		tmpElmt.addContent(tmp2Elmt);
		tmp2Elmt.setAttribute("fileName", "solution.xml");
		
		tmp2Elmt = new Element ("agentDescription");
		tmpElmt.addContent(tmp2Elmt);
		tmp2Elmt.setAttribute("fileName", this.agentDesc.getName());
		
		tmpElmt = new Element ("problemList");
		expElmt.addContent(tmpElmt);
		tmpElmt.setAttribute("nbProblem", "1");
		
		tmp2Elmt = new Element ("file");
		tmpElmt.addContent(tmp2Elmt);
		tmp2Elmt.setAttribute("fileName", pathToXCSP);
		
		this.config = new File (".tmpConfig.xml");
		try {
			new XMLOutputter(Format.getPrettyFormat()).output(new Document (expElmt), new FileWriter (this.config));
		} catch (IOException e) {
			System.err.println("Failed to write the temporary configuration file");
			e.printStackTrace();
		}
	}

	/** @see UserIO#tellUser(java.lang.String) */
	@Override
	public void tellUser(String message) {
		
		if (message.equals(ConfigurationManager.END_TEXT)) { // we can return the solution
			synchronized (this.lock) {
				this.lock.notify();
			}
		}
		
		System.out.println(message);
	}

	/** @see UserIO#askUserYesNo(java.lang.String) */
	@Override
	public boolean askUserYesNo(String message) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";
		return false;
	}

	/** @see UserIO#stopRunning() */
	@Override
	public void stopRunning() {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see UserIO#showUserDaemonList(java.util.HashMap) */
	@Override
	public void showUserDaemonList(HashMap<String, AgentAddress> daemons) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

	/** @see UserIO#showUserAgentList(java.util.HashMap) */
	@Override
	public void showUserAgentList(HashMap<String, AgentAddress> agents) {
		/// @todo Auto-generated method stub
		assert false : "Not yet implemented";

	}

}
