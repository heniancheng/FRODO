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

package frodo2.benchmarks.vehiclerouting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator;
import frodo2.solutionSpaces.vehiclerouting.VehicleRoutingSpace;

/** A file converter from Cordeau's MDVRP format into FRODO XCSP format
 * 
 * @see "http://neo.lcc.uma.es/radi-aeb/WebVRP/index.html?/Problem_Instances/CordeauFilesDesc.html"
 * @author Thomas Leaute
 * @todo Add support for Python scripting
 */
public class CordeauToXCSP {
	
	/** A depot */
	private static class Depot {
		
		/** The depot ID */
		public int id;
		
		/** The maximal distance allowed for each vehicle in this depot */
		public float maxDist;
		
		/** This depot's visibility radius */
		public float horizon;
		
		/** The maximal load allowed for each vehicle in this depot */
		public int maxLoad;
		
		/** The x coordinate */
		public float x;
		
		/** The y coordinate */
		public float y;
		
		/** The IDs of the customers this depot can serve */
		public ArrayList<Integer> customers = new ArrayList<Integer> ();
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			return "Depot:\n\tid:\t\t" + this.id + "\n\tmaxDist:\t" + this.maxDist + "\n\tmaxLoad:\t" + this.maxLoad 
				+ "\n\tx:\t\t" + this.x + "\n\ty:\t\t" + this.y + "\n\tcustomers:\t" + this.customers;
		}
	}
	
	/** A customer */
	private static class Customer {
		
		/** The customer ID */
		public int id;
		
		/** The x coordinate */
		public double x; 
		
		/** The y coordinate */
		public double y;
		
		/** The radius of its uncertainty circle, or 0.0 if the customer's position is certain */
		public double radius = 0.0;
		
		/** The demand */
		public int demand;
		
		/** The IDs of the depots that can serve the customer */
		public ArrayList<Integer> depots;
		
		/** @see java.lang.Object#toString() */
		@Override
		public String toString () {
			return "Customer:\n\tid:\t" + this.id + "\n\tx:\t" + this.x + "\n\ty:\t" + this.y + 
				(this.radius > 0.0 ? "\n\tradius:\t" + this.radius : "") + 
				"\n\tdemand:\t" + this.demand + "\n\tdepots:\t" + this.depots;
		}
	}
	
	/** Prints the format of the input parameters */
	private static void printUsage () {
		
		System.out.println("Usage: CordeauToXCSP [-license] [-e] [-Q maxLoad] [-s minSplit] [-u size] input_Cordeau_file [depot_radius]\n" +
				"\t -license [optional]: displays the GNU Affero GPL licence and quits \n" + 
				"\t -e [optional]: outputs an extensional DCOP instead of using intentional VRP constraints \n" + 
				"\t -Q maxLoad [optional]: overrides the maximum load for each vehicle specified in the input Cordeau file \n" + 
				"\t -s minSplit [optional]: uses split deliveries, in which case each customer's order can be split among multiple depots, with a minimum split size of minSplit \n" +
				"\t -u size [optional]: all customers that can be served by more than one depot have uncertain positions given by random variables of the given domain sizes \n" +
				"\t input_Cordeau_file: the path to the input MDVRP file in Cordeau's format\n" +
				"\t depot_radius [optional]: a depot cannot serve a customer that is farther away than depot_radius");
	}

	/**
	 * @param args 			input_Cordeau_file [depot_radius]
	 * @throws IOException 	if an I/O error occurs
	 */
	public static void main(String[] args) throws IOException {
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. Use the option -license to display the license.\n");
		
		if (args.length < 1 || args.length > 10) {
			printUsage();
			return;
		}
		
		// Check whether the -license option was passed
		if (args[0].equals("-license")) {
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
		
		// Check whether the -e option was passed
		boolean extensional = false;
		if (args[0].equals("-e")) {
			
			extensional = true;
			
			// Shift the arguments to get rid of -e
			String[] tmp = args;
			args = new String [args.length - 1];
			System.arraycopy(tmp, 1, args, 0, args.length);
		}

		// Check whether the -Q option was passed
		int maxLoad = 0;
		if (args[0].equals("-Q")) {
			
			try {
				maxLoad = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("The following input maximum vehicle load is not an integer: " + args[1]);
				printUsage();
				return;
			}
			
			if (maxLoad <= 0) {
				System.err.println("Ignoring the input maximum vehicle load because it is non-positive: " + maxLoad);
				maxLoad = 0;
			}
			
			// Shift the arguments to get rid of "-Q maxLoad"
			String[] tmp = args;
			args = new String [args.length - 2];
			System.arraycopy(tmp, 2, args, 0, args.length);
		}

		// Check whether the -s option was passed
		int minSplit = 0;
		if (args[0].equals("-s")) {
			
			try {
				minSplit = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("The following input min split size is not an integer: " + args[1]);
				printUsage();
				return;
			}
			
			// Shift the arguments to get rid of "-s minSize"
			String[] tmp = args;
			args = new String [args.length - 2];
			System.arraycopy(tmp, 2, args, 0, args.length);
		}

		// Check whether the -u option was passed
		int randDomSize = 0;
		if (args[0].equals("-u")) {
			
			try {
				randDomSize = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("The following input random variable domain size is not an integer: " + args[1]);
				printUsage();
				return;
			}
			
			// Shift the arguments to get rid of "-u size"
			String[] tmp = args;
			args = new String [args.length - 2];
			System.arraycopy(tmp, 2, args, 0, args.length);
		}

		String filepath = args[0];
		System.out.println("Parsing the input file: " + filepath);
		File file = new File (filepath);
		BufferedReader in = new BufferedReader (new FileReader (file));
		
		Float depotRadius = null;
		if (args.length == 2) 
			depotRadius = new Float (args[1]);
		
		String outputFile = file.getName() + (args.length == 2 ? "_radius" + args[1] : "") + 
			(extensional ? "_extensional" : "") + 
			(maxLoad > 0 ? "_maxLoad" + maxLoad : "") + 
			(minSplit > 0 ? "_split" + minSplit : "") + 
			(randDomSize > 0 ? "_uncertain" + randDomSize : "") + ".xml";
		System.out.println("Generating XCSP file: " + outputFile);
		CordeauToXCSP converter = new CordeauToXCSP (file.getName());
		converter.parse(in, depotRadius, maxLoad);
		Document xcspDoc = converter.createXCSP(extensional, minSplit, randDomSize);
		
		// Write output XCSP to a file
		new XMLOutputter(Format.getPrettyFormat()).output(xcspDoc, new FileWriter (outputFile));

		System.out.println("Done.");
	}

	/** The depots */
	private ArrayList<Depot> depots;
	
	/** The number of vehicles per depot */
	private int nbrVehiclesPerDepot;
	
	/** The customers */
	private Customer[] customers;

	/** The name of the input file */
	private String inputFileName;

	/** Constructor
	 * @param inputFileName 	the name of the input file
	 */
	public CordeauToXCSP(String inputFileName) {
		this.inputFileName = inputFileName;
	}

	/** Parses the input file
	 * @param in 			input file
	 * @param depotRadius 	the depot radius
	 * @param maxLoad 		the maximum vehicle load
	 * @throws IOException 	if an I/O exception occurs
	 */
	public void parse (BufferedReader in, Float depotRadius, int maxLoad) throws IOException {
		
		// Read the first line: type nbrVehiclesPerDepot nbrCustomers nbrDepots
		String[] split = in.readLine().split("\\s+");
		assert split.length == 4 : "First line must be of format: type nbrVehiclesPerDepot nbrCustomers nbrDepots";
		assert split[0].equals("2") : "The input file is not of type 2 (MDVRP)";
		nbrVehiclesPerDepot = Integer.parseInt(split[1]);
		int nbrCustomers = Integer.parseInt(split[2]);
		int nbrDepots = Integer.parseInt(split[3]);

		// For each depot, read maxDist and maxLoad
		float maxMaxDist = 0;
		depots = new ArrayList<Depot> (nbrDepots);
		for (int i = 0; i < nbrDepots; i++) {
			
			split = in.readLine().split("\\s+");
			assert split.length == 2 : "Depot definition must be of format: maxDist maxLoad";
			
			Depot depot = new Depot ();
			depots.add(depot);
			depot.id = i;
			depot.maxDist = Float.parseFloat(split[0]);
			if (depot.maxDist > maxMaxDist) 
				maxMaxDist = depot.maxDist;
			
			if (maxLoad > 0) 
				depot.maxLoad = maxLoad;
			else 
				depot.maxLoad = Integer.parseInt(split[1]);
		}
		
		if (depotRadius == null) 
			depotRadius = (maxMaxDist > 0 ? new Float (maxMaxDist / 2.) : new Float(Float.MAX_VALUE));
		
		// Parse the description of each customer. The format is: 
		// id x y 0 demand 1 nbrDepots [list of depots that can serve the customer, identified by 2^depotID]
		customers = new Customer [nbrCustomers];
		for (int i = 0; i < nbrCustomers; i++) {
			
			split = in.readLine().trim().split("\\s+");
			assert split.length >= 7 : "Customer definition must start with: id x y 0 demand 1 nbrDepots";
			
			Customer customer = new Customer ();
			customers[i] = customer;
			customer.id = Integer.parseInt(split[0]);
			customer.x = Double.parseDouble(split[1]);
			customer.y = Double.parseDouble(split[2]);
			assert split[3].equals("0") : "Service duration for customer " + customer.id + " is not 0";
			customer.demand = Integer.parseInt(split[4]);
			assert split[5].equals("1") : "Frequence of visit for customer " + customer.id + " is not 1";
			
			// Parse the list of depots that can serve this customer
			int nbr = Integer.parseInt(split[6]);
			assert split.length == 7 + nbr : "The list of depots for customer " + customer.id + " does not contain " + nbr + " elements";
			customer.depots = new ArrayList<Integer> (nbr);
			final double log2 = Math.log(2);
			for (int j = 0; j < nbr; j++) {
				
				int powerOf2 = Integer.parseInt(split[7 + j]);
				int depotID = (int) (Math.log(powerOf2) / log2);
				customer.depots.add(depotID);
				depots.get(depotID).customers.add(customer.id);
			}
		}
		
		// Parse the position of each depot
		double depotRadiusSquared = Math.pow(depotRadius, 2.0);
		for (int i = 0; i < nbrDepots; i++) {
			
			split = in.readLine().trim().split("\\s+");
			assert split.length >= 3 : "Depot definition must be of format: nbr x y";
			
			Depot depot = depots.get(i);
			depot.x = Float.parseFloat(split[1]);
			depot.y = Float.parseFloat(split[2]);
			depot.horizon = depotRadius;
			
			// This depot should not serve customers if they are farther away than depotRadius
			if (depotRadius != null) {
				
				// Go through the list of potential customers
				for (Iterator<Integer> iter = depot.customers.iterator(); iter.hasNext(); ) {
					Customer customer = customers[iter.next() - 1];
					
					// Remove this customer if it is too far away
					if (depotRadiusSquared < Math.pow(customer.x - depot.x, 2) + Math.pow(customer.y - depot.y, 2)) {
						iter.remove();
						customer.depots.remove(new Integer (depot.id));
					}
				}
			}
			
//			System.out.println(depot);
		}
		
		// Remove the isolated depots
		depotLoop: for (int i = 0; i < nbrDepots; i++) {
			
			// Look for a customer that can be served by more than one depot
			for (Integer custID : depots.get(i).customers) 
				if (customers[custID - 1].depots.size() > 1) 
					continue depotLoop;
			
			depots.set(i, null);
		}
	}

	/** 
	 * @param extensional 	whether the output should be purely extensional, or should involve intentional VRP constraints
	 * @param minSplit 		the minimum split size (no split if <= 0)
	 * @param randDomSize 	the size of random variables' domain
	 * @return 				an XCSP Document 
	 */
	public Document createXCSP(final boolean extensional, final int minSplit, final int randDomSize) {
		
		// Create the root element
		Element probElement = new Element ("instance");
		probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschemaVRP.xsd", 
				org.jdom2.Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));

		// Create the "presentation" element
		Element presElmt = new Element ("presentation");
		probElement.addContent(presElmt);
		presElmt.setAttribute("name", this.inputFileName);
		presElmt.setAttribute("maximize", "false");
		presElmt.setAttribute("format", "XCSP 2.1_FRODO");
		int maxArity = 0; // computed later
		
		// Create the "agents" element
		Element elmt = new Element ("agents");
		probElement.addContent(elmt);
		elmt.setAttribute("nbAgents", Integer.toString(this.depots.size()));
		for (Depot depot : this.depots) {
			if (depot != null) {
				Element depotElmt = new Element ("agent");
				elmt.addContent(depotElmt);
				depotElmt.setAttribute("name", "d" + depot.id);
			}
		}
		
		// Create the "domains" element
		Element domsElmt = new Element ("domains");
		probElement.addContent(domsElmt);
		final boolean splitDeliveries = (minSplit > 0);
		if (!splitDeliveries) {
			
			// All domains are binary
			Element subElmt = new Element ("domain");
			domsElmt.addContent(subElmt);
			subElmt.setAttribute("name", "Boolean");
			subElmt.setAttribute("nbValues", "2");
			subElmt.addContent("0 1");
		}
		if (randDomSize > 0) {
			
			// Create the domain common to all random variables, corresponding to angles 
			Element domElmt = new Element ("domain");
			domsElmt.addContent(domElmt);
			domElmt.setAttribute("name", "angles");
			domElmt.setAttribute("nbValues", Integer.toString(randDomSize));
			StringBuilder builder = new StringBuilder ();
			for (int i = 0; i < randDomSize; i++) 
				builder.append(Integer.toString(i) + " ");
			domElmt.addContent(builder.toString());
		}

		// Create the "variables" element
		Element varsElement = new Element ("variables");
		probElement.addContent(varsElement);
		
		// For each customer, create one variable per depot that can serve it (if there are at least two)
		// as well as one random variable if required
		HashSet<Integer> sharedCustomers = new HashSet<Integer> ();
		for (Customer cust : this.customers) {
			
			// Skip this customer if it cannot be served by at least 2 depots
			if (cust.depots.size() < 2) 
				continue;
			sharedCustomers.add(cust.id);
			
			if (splitDeliveries) {
				
				// Create the variable domain, which is {0, minSplit..(demand-minSplit), demand} (provided demand >= 2*minSplit)
				elmt = new Element ("domain");
				domsElmt.addContent(elmt);
				elmt.setAttribute("name", "c" + cust.id);
				if (cust.demand >= 2*minSplit) {
					elmt.setAttribute("nbValues", Integer.toString(cust.demand - 2*minSplit + 3));
					elmt.addContent("0 " + minSplit + ".." + (cust.demand-minSplit) + " " + cust.demand);
				} else {
					elmt.setAttribute("nbValues", "2");
					elmt.addContent("0 " + cust.demand);
				}
			}
			
			// Go through the list of depots that can serve this customer
			for (Integer depotID : cust.depots) {
				
				// Create the variable
				elmt = new Element ("variable");
				varsElement.addContent(elmt);
				elmt.setAttribute("name", "d" + depotID + "c" + cust.id);
				elmt.setAttribute("domain", (splitDeliveries ? "c" + cust.id : "Boolean"));
				elmt.setAttribute("agent", "d" + depotID);
			}
			
			if (randDomSize > 1) {
				
				// Create a random variable defining the exact position of the customer
				elmt = new Element ("variable");
				varsElement.addContent(elmt);
				elmt.setAttribute("name", "r" + cust.id);
				elmt.setAttribute("domain", "angles");
				elmt.setAttribute("type", "random");
			}
		}
		
		if (randDomSize > 1) {
			// Create the "probabilities" element
			Element probsElmt = new Element ("probabilities");
			probElement.addContent(probsElmt);
			probsElmt.setAttribute("nbProbabilities", "1");
			elmt = new Element ("probability");
			probsElmt.addContent(elmt);
			elmt.setAttribute("name", "angleProb");
			elmt.setAttribute("semantics", "soft");
			elmt.setAttribute("arity", "1");
			
//			// Uniform distribution
//			String prob = Double.toString(1.0 / randDomSize);
//			elmt.setAttribute("defaultProb", prob);
//			elmt.setAttribute("nbTuples", "1");
//			elmt.addContent(prob + ":0");
			
			// Non-uniform distribution
			elmt.setAttribute("nbTuples", Integer.toString(randDomSize));
			float atomicProb = (float) (1.0 / (randDomSize * (randDomSize + 1) / 2));
			StringBuilder builder = new StringBuilder ();
			float prob = atomicProb;
			for (int i = 0; i < randDomSize - 1; i++, prob+=atomicProb) 
				builder.append(prob + ":" + i + "|");
			builder.append(prob + ":" + (randDomSize - 1));
			elmt.addContent(builder.toString());
		}
		
		// Create the "relations" and "constraints" elements
		Element relElement = new Element ("relations");
		probElement.addContent(relElement);
		Element conElement = new Element ("constraints");
		probElement.addContent(conElement);

		// Create the SUM relations
		HashSet<String> sums = new HashSet<String> ();
		for (Customer customer : customers) {
			
			// Skip the constraint if it involves < 2 depots
			int arity = customer.depots.size();
			if (arity < 2) 
				continue;
			
			// Check the nature of the SUM constraint for this customer
			String sumName = "SUM_" + arity + (splitDeliveries ? "_" + customer.demand : "");
			if (! sums.add(sumName)) // the relation has already been created
				continue;
			
			elmt = new Element ("relation");
			relElement.addContent(elmt);
			elmt.setAttribute("name", sumName);
			elmt.setAttribute("arity", Integer.toString(arity));
			maxArity = Math.max(maxArity, arity);
			elmt.setAttribute("semantics", "soft");
			elmt.setAttribute("defaultCost", "infinity");
			
			// Create the tuples
			StringBuilder builder = new StringBuilder ("0:");
			int nbrTuples = this.writeSum(builder, "", (splitDeliveries ? customer.demand : 1), arity, true);
			elmt.setAttribute("nbTuples", Integer.toString(nbrTuples));
			elmt.addContent(builder.toString());
		}
		
		
		// For each customer, create the constraint(s)
		for (Customer customer : customers) {
			
			// Skip the constraint if it involves < 2 depots
			int arity = customer.depots.size();
			if (arity < 2) 
				continue;
			
			// Create the SUM constraint
			elmt = new Element ("constraint");
			conElement.addContent(elmt);
			elmt.setAttribute("name", "SUM_c" + customer.id);
			elmt.setAttribute("arity", Integer.toString(arity));
			elmt.setAttribute("reference", "SUM_" + arity + (splitDeliveries ? "_" + customer.demand : ""));
			
			// Build the scope
			StringBuilder builder = new StringBuilder ();
			for (Integer depotID : customer.depots) 
				builder.append("d" + depotID + "c" + customer.id + " ");
			elmt.setAttribute("scope", builder.toString());
			
			// Create the probability constraint if required
			if (randDomSize > 0) {
				
				elmt = new Element ("constraint");
				conElement.addContent(elmt);
				elmt.setAttribute("name", "position_c" + customer.id);
				elmt.setAttribute("arity", "1");
				elmt.setAttribute("reference", "angleProb");
				elmt.setAttribute("scope", "r" + customer.id);
				
				// Also compute the radius such that the customer always remains visible to its current depots
				customer.radius = Double.MAX_VALUE;
				for (Integer depotID : customer.depots) {
					Depot depot = this.depots.get(depotID);
					customer.radius = Math.min(customer.radius, depot.horizon - Math.sqrt(Math.pow(depot.x - customer.x, 2.0) + Math.pow(depot.y - customer.y, 2.0)));
				}
			}
		}
		
		// For each depot, create the VRP constraint
		for (Depot depot : depots) {
			
			if (depot == null) // isolated depot
				continue;
			
			elmt = new Element ("constraint");
			conElement.addContent(elmt);
			elmt.setAttribute("name", "d" + depot.id + "_VRP");
			
			// Create the scope
			StringBuilder builder = new StringBuilder ();
			int arity = 0;
			for (Integer customerID : depot.customers) {
				
				// Skip this variable if the corresponding customer can only be served by this depot
				if (! sharedCustomers.contains(customerID))
					continue;
				
				builder.append("d" + depot.id + "c" + customerID + " ");
				arity++;
				
				// Add the random variable if required
				if (randDomSize > 0) {
					builder.append("r" + customerID + " ");
					arity++;
				}
			}
			elmt.setAttribute("scope", builder.toString());
			elmt.setAttribute("arity", Integer.toString(arity));
			maxArity = Math.max(maxArity, arity);
			
			// Check whether we want an extensional constraint or an intensional VRP constraint
			if (! extensional) {

				elmt.setAttribute("reference", "global:vehicle_routing");
				if (splitDeliveries) 
					elmt.setAttribute("minSplit", Integer.toString(minSplit));

				// Create the parameters
				Element params = new Element ("parameters");
				elmt.addContent(params);

				// Create the description of the depot
				elmt = new Element ("depot");
				params.addContent(elmt);
				elmt.setAttribute("nbVehicles", Integer.toString(this.nbrVehiclesPerDepot));
				elmt.setAttribute("maxDist", Float.toString(depot.maxDist));
				elmt.setAttribute("maxLoad", Integer.toString(depot.maxLoad));
				elmt.setAttribute("xCoordinate", Float.toString(depot.x));
				elmt.setAttribute("yCoordinate", Float.toString(depot.y));

				// Create the description of the customers
				elmt = new Element ("customers");
				params.addContent(elmt);
				for (Integer customerID : depot.customers) {
					Customer customer = customers[customerID - 1];
					
					Element elmt2 = new Element ("customer");
					elmt.addContent(elmt2);
					elmt2.setAttribute("id", Integer.toString(customer.id));
					elmt2.setAttribute("demand", Integer.toString(customer.demand));
					elmt2.setAttribute("xCoordinate", Double.toString(customer.x));
					elmt2.setAttribute("yCoordinate", Double.toString(customer.y));

					// Check if this customer can be served by at least 2 depots
					if (sharedCustomers.contains(customerID)) {
						
						// Add the decision variable
						elmt2.setAttribute("varName", "d" + depot.id + "c" + customerID);
						
						// Add information about uncertainty if required
						if (randDomSize > 0 && customer.depots.size() > 1) {
							elmt2.setAttribute("uncertaintyAngleVar", "r" + customerID);
							elmt2.setAttribute("uncertaintyRadius", Double.toString(customer.radius));
						}
					}
				}

			} else { // extensional

				String relationName = "r_d" + depot.id + "_VRP";
				elmt.setAttribute("reference", relationName);
				
				/// @todo Handle uncertainty if required
				assert randDomSize == 0 : "Not supported yet";

				// Create a VRP space
				String[] vars = builder.toString().trim().split("\\s");
				HashMap<String, frodo2.solutionSpaces.vehiclerouting.Customer> openCustomers = 
					new HashMap<String, frodo2.solutionSpaces.vehiclerouting.Customer> ();
				HashSet<frodo2.solutionSpaces.vehiclerouting.Customer> selectedCustomers = 
					new HashSet<frodo2.solutionSpaces.vehiclerouting.Customer> (); 
				for (Integer customerID : depot.customers) {
					Customer customer = customers[customerID - 1];
					
					frodo2.solutionSpaces.vehiclerouting.Customer custom = 
						new frodo2.solutionSpaces.vehiclerouting.Customer (customer.id, customer.demand, customer.x, customer.y, 0.0, null);

					if (sharedCustomers.contains(customerID)) 
						openCustomers.put("d" + depot.id + "c" + customerID, custom);
					else 
						selectedCustomers.add(custom);
				}
				VehicleRoutingSpace<AddableReal> vrpSpace = new VehicleRoutingSpace<AddableReal> (nbrVehiclesPerDepot, depot.maxDist, depot.maxLoad, 
						depot.x, depot.y, vars, new HashMap<String, AddableInteger[]> (), 
						openCustomers, selectedCustomers, new HashMap<String, frodo2.solutionSpaces.vehiclerouting.Customer> (), 
						"d" + depot.id + "_VRP", null, AddableReal.PlusInfinity.PLUS_INF, minSplit, null);

				// Create an extensional relation corresponding to this space
				Element elmt2 = new Element ("relation");
				relElement.addContent(elmt2);
				elmt2.setAttribute("name", relationName);
				elmt2.setAttribute("arity", Integer.toString(arity));
				SparseIterator<AddableInteger, AddableReal> iter = vrpSpace.sparseIter();
				elmt2.setAttribute("semantics", "soft");
				elmt2.setAttribute("defaultCost", "infinity");
				builder = new StringBuilder ();
				AddableReal util = iter.nextUtility();
				int nbrTuples = 0;
				if (util != null) {
					nbrTuples++;
					builder.append(util.toString() + ":");
					for (AddableInteger val : iter.getCurrentSolution()) 
						builder.append(val + " ");
					while ( (util = iter.nextUtility()) != null) {
						assert nbrTuples < Integer.MAX_VALUE : "A relation can only contain up to 2^32 solutions";
						nbrTuples++;
						builder.append("|" + util + ":");
						for (AddableInteger val : iter.getCurrentSolution()) 
							builder.append(val + " ");
					}
				}
				elmt2.setText(builder.toString());
				elmt2.setAttribute("nbTuples", Integer.toString(nbrTuples));
			}
		}
		
		domsElmt.setAttribute("nbDomains", Integer.toString(domsElmt.getContentSize()));
		varsElement.setAttribute("nbVariables", Integer.toString(varsElement.getContentSize()));
		relElement.setAttribute("nbRelations", Integer.toString(relElement.getContentSize()));
		conElement.setAttribute("nbConstraints", Integer.toString(conElement.getContentSize()));
		presElmt.setAttribute("maxConstraintArity", Integer.toString(maxArity));

		return new Document (probElement);		
	}
	
	/** Writes the tuples for a sum relation
	 * @param builder 	the StringBuilder
	 * @param prefix 	assignments to the previous variables
	 * @param sum 		the sum to be decomposed over the remaining variables
	 * @param nbrVars 	the number of remaining variables
	 * @param top 	whether this is the top-level call to this recursive method
	 * @return the number of tuples
	 */
	private int writeSum (StringBuilder builder, String prefix, int sum, int nbrVars, final boolean top) {
		
		if (nbrVars == 1) {
			builder.append(prefix);
			builder.append(Integer.toString(sum));
			if (!top) 
				builder.append("|");
			return 1;
		}
		
		// Loop over the allowed values for the first remaining variable
		int nbrTuples = 0;
		for (int val = 0; val <= sum; val++) 
			nbrTuples += this.writeSum(builder, prefix + val + " ", sum - val, nbrVars - 1, false);
		
		if (top) 
			builder.deleteCharAt(builder.length() - 1);
		
		return nbrTuples;
	}

}
