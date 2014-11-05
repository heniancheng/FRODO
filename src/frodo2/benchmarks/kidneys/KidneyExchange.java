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

package frodo2.benchmarks.kidneys;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/** Creates a sample population of kidney donor-patient pairs and generates a graph which is then written into a DCOP problem statement.
 * 
 * The method used to generate pairs is roughly the same as the one described in:
 * Susan L. Saidman, Alvin E. Roth, Tayfun Sonmez, M. Utku Unver, and Francis L. Delmonico. 
 * Increasing the opportunity of live kidney donation by matching for two- and three-way exchanges. 
 * Transplantation, 81(5):773-782, March 15 2006.
 * 
 * @author Jonas Helfer, Thomas Leaute
 */
public class KidneyExchange {
	
	/** Directory to which the files are output */
	public static final String outputDir = "./";
	
	/* Kidney donation stats for generating populations --------------------- */
	
	/** Blood types */
	private enum ABO {
		/** type O */ O,
		/** type A */ A,
		/** type B */ B,
		/** type AB */ AB;
	}
	/** Frequency of blood type O */
	public final static double bloodO = 48.14/100;
	/** Frequency of blood type A */
	public final static double bloodA = 33.73/100;
	/** Frequency of blood type B */
	public final static double bloodB = 14.28/100;
	/* Frequency of blood type AB */
	//public final static double bloodAB = 3.85/100;
	
	/** Frequency of women among the patients */
	public final static double sexF = 40.9/100;
	
	/** Frequency of spouse donors */
	public final static double relationSpouse = 48.97/100;
	
	/** Panel Reactive Antibody levels */
	private enum PRA {
		/** low PRA*/ LOW, 
		/** med PRA*/ MED, 
		/** high PRA*/ HIGH;
	}
	/** Frequency of low PRA */
	public final static double lowPRA = 70.19/100;
	/** Frequency of medium PRA */
	public final static double mediumPRA = 20.00/100;
	/* Frequency of high PRA */
	//public final static double highPRA = 9.81/100;
	
	/* Positive cross-match probabilities for random donors */
	/** Probability of positive cross-matches for low PRA */
	public final static double posXmatchLowPRA = 0.05;
	/** Probability of positive cross-matches for medium PRA */
	public final static double posXmatchMediumPRA = 0.45;
	/** Probability of positive cross-matches for high PRA */
	public final static double posXmatchHighPRA = 0.9;
	
	/** Negative cross-match modifier for husband donors (i.e. p of neg xmatch is 0.75 times that of a random donor) */
	public final static double negXmatchHusbandDonorFactor = 0.75;

	/** Creates a random problem instance and writes it to a file
	 * @param args 	nbrPairs
	 */
	public static void main(String[] args) {
		
		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. \n");

		// Parse the input arguments
		if (args.length < 1 || args.length > 3) {
			System.err.println("Usage: KidneyExchange [-i] [-s] [-a] nbrPairs, where:\n" +
					"\t -i (optional) imposes the use of intensional constraints\n" +
					"\t -s (optional) generates a StochDCOP with one random variable modeling each patient's probability to die before the transplant\n" + 
					"\t -a (optional) applies arc consistency before outputing the (Stoch)DCOP\n" + 
					"\t nbrPairs is the desired number of incompatible patient-donor pairs\n");
			System.exit(1);
		}
		
		int nbrPairs = 0;
		boolean intensional = false;
		boolean stoch = false;
		boolean consistency = false;
		for (String arg : args) {
			
			if (arg.equals("-i")) 
				intensional = true;
			
			else if (arg.equals("-s")) 
				stoch = true;
			
			else if (arg.equals("-a")) 
				consistency = true;
			
			else { // nbrPairs
				try {
					nbrPairs = Integer.parseInt(arg);
				} catch (NumberFormatException e) {
					System.err.println("The input number of pairs is not an interger: " + arg);
					return;
				}
			}
		}
		
		// Generate a problem instance
		KidneyExchange kd = new KidneyExchange (nbrPairs);
		Document problem = kd.generateProblem(intensional, stoch, consistency);
		
		// Write the problem to a file
		String fileName = "kidneyProb.xml";
		try {
			new XMLOutputter(Format.getPrettyFormat()).output(problem, new FileWriter (fileName));
		} catch (IOException e) {
			System.err.println("Could not save the problem to a file");
			e.printStackTrace();
			return;
		}
		System.out.println("Wrote " + fileName);
	}
	
	/** The patient-donor pairs, indexed by their IDs */
	private HashMap<Integer, PatientDonorPair> pairs;
	
	/** Constructor
	 * @param nbrPairs 	the desired number of (incompatible) patient-donor pairs
	 */
	public KidneyExchange (int nbrPairs) {
		pairs = new HashMap<Integer, PatientDonorPair> (nbrPairs);

		PatientDonorPair p;
		for (int id = 1; id <= nbrPairs; ){
			p = generatePair(id, null);
			
			//only use incompatible pairs
			if(!p.compatible){
				pairs.put(id, p);
				id++;
			}
		}
	}
	
	/** Generates an XCSP-formatted document
	 * @param intensional 	whether to use intensional constraints
	 * @param stoch 		whether to produce a StochDCOP
	 * @param consistency 	whether to apply arc consistency
	 * @return Document 	the XCSP problem instance
	 */
	public Document generateProblem (final boolean intensional, final boolean stoch, final boolean consistency){
		
		assert !(intensional && stoch) : "Intensional StochDCOPs are currently unsupported";
		
		/* Stores all the values the variables can take 
		 * Integer: identifier of variable
		 * TreeSet<Integer>: identifiers of potential receivers/donors
		 */
		HashMap<Integer, TreeSet<Integer>> canGiveTo = new HashMap<Integer,TreeSet<Integer>>();
		HashMap<Integer, TreeSet<Integer>> canGetFrom = new HashMap<Integer,TreeSet<Integer>>();
		
		
		Element probElement = new Element ("instance");
		if (intensional) 
			probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschemaJaCoP.xsd", 
				Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
		else 
			probElement.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschema.xsd", 
				Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));

		
		// Create the "presentation" element
		Element elmt = new Element ("presentation");
		probElement.addContent(elmt);
		elmt.setAttribute("name", "randomKidneyExchangeProblem");
		elmt.setAttribute("maxConstraintArity", (stoch ? "6" : "3"));
		elmt.setAttribute("maximize", "true");
		elmt.setAttribute("format", "XCSP 2.1_FRODO");
		
		elmt = new Element ("agents");
		probElement.addContent(elmt);
		elmt.setAttribute("nbAgents", Integer.toString(this.pairs.size()));
		for (Integer pairID : this.pairs.keySet()) {
			Element agent = new Element ("agent");
			elmt.addContent(agent);
			agent.setAttribute("name", "a" + pairID);
		}
		
		// Each pair/agent owns one variable for the donor it receives from, and one for the patient it gives to
		// +1 binary random variable per patient in the StochDCOP case
		Element elmtDomains = new Element ("domains");
		probElement.addContent(elmtDomains);
		elmtDomains.setAttribute("nbDomains", Integer.toString(pairs.size()*2 + (stoch ? 1 : 0)));
		
		Element varsElement = new Element ("variables");
		probElement.addContent(varsElement);
		varsElement.setAttribute("nbVariables", Integer.toString(pairs.size() * (stoch ? 3 : 2)));
		
		Element relElement = new Element ("relations");
		if (! intensional) 
			probElement.addContent(relElement);
		
		Element predElement = new Element ("predicates");
		if (intensional) 
			probElement.addContent(predElement);
		
		Element funcElement = new Element ("functions");
		if (intensional) 
			probElement.addContent(funcElement);
		
		Element probaElement = new Element ("probabilities");
		if (stoch) 
			probElement.addContent(probaElement);
		
		Element conElement = new Element ("constraints");
		probElement.addContent(conElement);
		
		//calculate compatibilities
		for (Integer pairID : pairs.keySet()) {
			canGiveTo.put(pairID, new TreeSet<Integer> ());
			canGetFrom.put(pairID, new TreeSet<Integer> ());
		}
		for (Map.Entry<Integer, PatientDonorPair> entry : pairs.entrySet()) {
			Integer pairID = entry.getKey();
			PatientDonorPair pair = entry.getValue();
			TreeSet<Integer> compatiblePatients = canGiveTo.get(pairID);
			
			for (Map.Entry<Integer, PatientDonorPair> entry2 : pairs.entrySet()) {
				Integer pairID2 = entry2.getKey();
				if (pairID == pairID2) 
					continue;
				PatientDonorPair pair2 = entry2.getValue();
				
				if (pair.canGiveTo(pair2)) {
					compatiblePatients.add(pairID2);
					canGetFrom.get(pairID2).add(pairID);
				}
			}
		}
		
		System.out.println(toDOT(canGiveTo)); /// @todo Make it possible to use the DOTrenderer
		
		if (consistency) { // apply arc consistency
			System.out.println("Applying arc consistency");
			arcConsistency (canGiveTo, canGetFrom);
		}
		
		//start making the document from here...
		
		//generate domains... ------------------------------------
		for(Integer key: pairs.keySet()){
			
			// From whom does this pair get a kidney?
			elmt = new Element ("variable");
			varsElement.addContent(elmt);
			elmt.setAttribute("name", "f" + key);
			elmt.setAttribute("domain", "D_f" + key);
			elmt.setAttribute("agent", "a" + key);
			
			TreeSet<Integer> values = canGetFrom.get(key);
			Element subElmt = new Element ("domain");
			elmtDomains.addContent(subElmt);
			subElmt.setAttribute("name", "D_f" + key);
			subElmt.setAttribute("nbValues", Integer.toString(values.size()+1)); // +1 for not receiving from anyone
			subElmt.addContent("0 " + implode(values," ")); // 0 means not receiving from anyone
			
			// To whom does this pair give a kidney?
			elmt = new Element ("variable");
			varsElement.addContent(elmt);
			elmt.setAttribute("name", "t" + key);
			elmt.setAttribute("domain", "D_t" + key);
			elmt.setAttribute("agent", "a" + key);
			
			values = canGiveTo.get(key);
			subElmt = new Element ("domain");
			elmtDomains.addContent(subElmt);
			subElmt.setAttribute("name", "D_t" + key);
			subElmt.setAttribute("nbValues", Integer.toString(values.size()+1)); // +1 for not giving to anyone
			subElmt.addContent("0 " + implode(values," ")); // 0 means not giving to anyone
			
			if (stoch) { // is this patient going to survive the transplant?
				
				elmt = new Element ("variable");
				varsElement.addContent(elmt);
				elmt.setAttribute("name", "s" + key);
				elmt.setAttribute("domain", "Boolean");
				elmt.setAttribute("type", "random");
			}
		}
		
		if (stoch) { // add one binary domain for the random variables
			
			elmt = new Element ("domain");
			elmtDomains.addContent(elmt);
			elmt.setAttribute("name", "Boolean");
			elmt.setAttribute("nbValues", "2");
			elmt.setText("0 1"); // 1 = the patient survives
		}
		
		// Create the predicates and functions
		if (intensional) {
			
			// Create the predicate iff(eq(T, 0), eq(F, 0))
			elmt = new Element ("predicate");
			predElement.addContent(elmt);
			elmt.setAttribute("name", "I_give_iff_I_receive");
			
			Element subElmt = new Element ("parameters");
			elmt.addContent(subElmt);
			subElmt.setText("int T int F");
			
			subElmt = new Element ("expression");
			elmt.addContent(subElmt);
			elmt = new Element ("functional");
			subElmt.addContent(elmt);
			elmt.setText("iff(eq(T, 0), eq(F, 0))");
			
			
			// Create the utility function for 2-cycles
			elmt = new Element ("function");
			funcElement.addContent(elmt);
			elmt.setAttribute("name", "cycle2");
			elmt.setAttribute("return", "int");
			
			subElmt = new Element ("parameters");
			elmt.addContent(subElmt);
			subElmt.setText("int F int T");

			subElmt = new Element ("expression");
			elmt.addContent(subElmt);
			elmt = new Element ("functional");
			subElmt.addContent(elmt);
			elmt.setText("if(and(eq(F, T), ne(F, 0)), 10, 0)");
			
			
			// Create the predicate "If give to you IFF you get from me"
			elmt = new Element ("predicate");
			predElement.addContent(elmt);
			elmt.setAttribute("name", "I_give_to_you_iff_you_get_from_me");
			
			subElmt = new Element ("parameters");
			elmt.addContent(subElmt);
			subElmt.setText("int T1 int V1 int F2 int V2");
			
			subElmt = new Element ("expression");
			elmt.addContent(subElmt);
			elmt = new Element ("functional");
			subElmt.addContent(elmt);
			elmt.setText("iff(eq(T1, V1), eq(F2, V2))");
			
			
			// Create the utility function for 3-cycles: if(and(I give to you, I receive from the one you give to), 29, 0)
			elmt = new Element ("function");
			funcElement.addContent(elmt);
			elmt.setAttribute("name", "cycle3");
			elmt.setAttribute("return", "int");
			
			subElmt = new Element ("parameters");
			elmt.addContent(subElmt);
			subElmt.setText("int T1 int V int F1 int T2");

			subElmt = new Element ("expression");
			elmt.addContent(subElmt);
			elmt = new Element ("functional");
			subElmt.addContent(elmt);
			elmt.setText("if(and(eq(T1, V), eq(F1, T2), 29, 0)");			
		}
		
		
		// Loop through all pairs to create the 2-cycle constraints
		for (Map.Entry< Integer, TreeSet<Integer> > entry : canGiveTo.entrySet()) {
			Integer pairID = entry.getKey();
			TreeSet<Integer> compatiblePatients = entry.getValue();
			TreeSet<Integer> compatibleDonors = canGetFrom.get(pairID);
			
			if (! intensional) {
				if (! stoch) {
					
					// Create the relation
					elmt = new Element ("relation");
					elmt.setAttribute("name", "r_" + pairID + "_2cycles");
					elmt.setAttribute("arity", "2");
					elmt.setAttribute("semantics", "soft");

					// Create the String representation for the tuples
					// The utility is 10 if I give to and receive a kidney from the same pair, 
					// -INF if I give without receiving or receive without giving, and 0 otherwise
					elmt.setAttribute("defaultCost", "0");
					int nbrTuples = 0;
					StringBuilder builder = new StringBuilder ();

					TreeSet<Integer> possible2cycles = new TreeSet<Integer> (compatiblePatients);
					possible2cycles.retainAll(compatibleDonors);
					if (! possible2cycles.isEmpty()) {
						nbrTuples += possible2cycles.size();
						builder.append("10:");
						for (Integer otherPair : possible2cycles) 
							builder.append(otherPair + " " + otherPair + "|"); // receiving from and giving to the same pair
					}

					if (! compatiblePatients.isEmpty()) {
						nbrTuples += compatiblePatients.size();
						builder.append("-infinity:");
						for (Integer otherPatient : compatiblePatients) 
							builder.append("0 " + otherPatient + "|"); // not receiving but still giving
					}

					if (! compatibleDonors.isEmpty()) {
						nbrTuples += compatibleDonors.size();
						builder.append("-infinity:");
						for (Integer otherDonor : compatibleDonors) 
							builder.append(otherDonor + " 0|"); // receiving but not giving
					}

					if (nbrTuples > 0) {
						builder.deleteCharAt(builder.length() - 1);
						elmt.setText(builder.toString());
						elmt.setAttribute("nbTuples", Integer.toString(nbrTuples));
						relElement.addContent(elmt);

						// Create the constraint
						elmt = new Element ("constraint");
						conElement.addContent(elmt);
						elmt.setAttribute("name", "c_" + pairID);
						elmt.setAttribute("arity", "2");
						elmt.setAttribute("scope", "f" + pairID + " t" + pairID);
						elmt.setAttribute("reference", "r_" + pairID + "_2cycles");
					}

				} else { // StochDCOP
					
					// Create the probability distribution for my survival
					elmt = new Element ("probability");
					probaElement.addContent(elmt);
					elmt.setAttribute("name", "p_" + pairID + "_survives");
					elmt.setAttribute("arity", "1");
					elmt.setAttribute("semantics", "soft");
					elmt.setAttribute("nbTuples", "1");
					double proba = Math.random();
					elmt.setAttribute("defaultProb", Double.toString(1 - proba));
					elmt.setText(Double.toString(proba) + ": 1");
					
					elmt = new Element ("constraint");
					conElement.addContent(elmt);
					elmt.setAttribute("name", "c_" + pairID + "_survives");
					elmt.setAttribute("arity", "1");
					elmt.setAttribute("scope", "s" + pairID);
					elmt.setAttribute("reference", "p_" + pairID + "_survives");
					
					
					// Create the relation and constraint for "I_give_iff_I_receive"
					if (compatiblePatients.size() + compatibleDonors.size() > 0) { // non-isolated pair
						
						elmt = new Element ("relation");
						relElement.addContent(elmt);
						elmt.setAttribute("name", "r_" + pairID + "_I_give_iff_I_receive");
						elmt.setAttribute("arity", "2");
						elmt.setAttribute("semantics", "soft");
						elmt.setAttribute("nbTuples", Integer.toString(compatiblePatients.size() + compatibleDonors.size()));
						elmt.setAttribute("defaultCost", "0");

						StringBuilder builder = new StringBuilder ("-infinity: ");
						for (Integer to : compatiblePatients) 
							builder.append("0 " + to + "|");
						for (Integer from : compatibleDonors) 
							builder.append(from + " 0|");
						builder.deleteCharAt(builder.length() - 1);
						elmt.setText(builder.toString());
						
						elmt = new Element ("constraint");
						conElement.addContent(elmt);
						elmt.setAttribute("name", "c_" + pairID + "_I_give_iff_I_receive");
						elmt.setAttribute("arity", "2");
						elmt.setAttribute("scope", "f" + pairID + " t" + pairID);
						elmt.setAttribute("reference", "r_" + pairID + "_I_give_iff_I_receive");
					}
					
					
					// Now, loop through all other pairs I can give to AND receive from
					TreeSet<Integer> intersect = new TreeSet<Integer> (compatiblePatients);
					intersect.retainAll(compatibleDonors);
					for (Integer toAndFrom : intersect) {
						
						// Create the relation and constraint for the 2-cycle
						elmt = new Element ("relation");
						relElement.addContent(elmt);
						elmt.setAttribute("name", "r_" + pairID + "_" + toAndFrom + "_2cycle");
						elmt.setAttribute("arity", "4");
						elmt.setAttribute("semantics", "soft");
						elmt.setAttribute("nbTuples", "1");
						elmt.setAttribute("defaultCost", "0");
						elmt.setText("10: " + toAndFrom + " " + toAndFrom + " 1 1"); // I get from you + I give to you + I survive + you survive
						
						elmt = new Element ("constraint");
						conElement.addContent(elmt);
						elmt.setAttribute("name", "c_" + pairID + "_" + toAndFrom + "_2cycle");
						elmt.setAttribute("arity", "4");
						elmt.setAttribute("scope", "f" + pairID + " t" + pairID + " s" + pairID + " s" + toAndFrom);
						elmt.setAttribute("reference", "r_" + pairID + "_" + toAndFrom + "_2cycle");
					}
				}
				
			} else { // intensional
				
				// Create the constraint "I give iff I receive"
				elmt = new Element ("constraint");
				conElement.addContent(elmt);
				elmt.setAttribute("name", "c_" + pairID + "_hard");
				elmt.setAttribute("arity", "2");
				elmt.setAttribute("scope", "f" + pairID + " t" + pairID);
				elmt.setAttribute("reference", "I_give_iff_I_receive");
				
				Element subElmt = new Element ("parameters");
				elmt.addContent(subElmt);
				subElmt.setText("f" + pairID + " t" + pairID);
				
				
				// Create the soft constraint for 2-cycles
				elmt = new Element ("constraint");
				conElement.addContent(elmt);
				elmt.setAttribute("name", "c_" + pairID + "_soft");
				elmt.setAttribute("arity", "2");
				elmt.setAttribute("scope", "f" + pairID + " t" + pairID);
				elmt.setAttribute("reference", "cycle2");
				
				subElmt = new Element ("parameters");
				elmt.addContent(subElmt);
				subElmt.setText("f" + pairID + " t" + pairID);
			}
			
			
			// Loop through all compatible patients to create the consistency constraints and the 3-cycle constraints
			for (Integer pairID2 : compatiblePatients) {
				TreeSet<Integer> compatibleDonors2 = canGetFrom.get(pairID2);
				
				if (! intensional) {
					
					// Create the relation: "If give to you IFF you get from me"
					elmt = new Element ("relation");
					relElement.addContent(elmt);
					elmt.setAttribute("name", "r_" + pairID + "_" + pairID2 + "_consistency");
					elmt.setAttribute("arity", "2");
					elmt.setAttribute("semantics", "soft");

					// Build the String representation of the disallowed tuples
					elmt.setAttribute("defaultCost", "0");
					StringBuilder builder = new StringBuilder ("-infinity:");
					elmt.setAttribute("nbTuples", Integer.toString(compatiblePatients.size() + compatibleDonors2.size()));

					// If I give to you, you cannot receive from anyone else
					builder.append(pairID2 + " 0|"); // not receiving at all
					for (Integer otherDonor : compatibleDonors2) 
						if (otherDonor != pairID) 
							builder.append(pairID2 + " " + otherDonor + "|");

					// If you receive from me, I cannot give to anyone else
					builder.append("0 " + pairID + "|"); // not giving at all
					for (Integer otherPatient : compatiblePatients) 
						if (otherPatient != pairID2) 
							builder.append(otherPatient + " " + pairID + "|");

					builder.deleteCharAt(builder.length() - 1);
					elmt.setText(builder.toString());

					// Create the constraint
					elmt = new Element ("constraint");
					conElement.addContent(elmt);
					elmt.setAttribute("name", "c_" + pairID + "_" + pairID2);
					elmt.setAttribute("arity", "2");
					elmt.setAttribute("scope", "t" + pairID + " f" + pairID2);
					elmt.setAttribute("reference", "r_" + pairID + "_" + pairID2 + "_consistency");
					
				} else { // intensional
					
					// Create constraint "I give to you IFF you get from me"
					elmt = new Element ("constraint");
					conElement.addContent(elmt);
					elmt.setAttribute("name", "c_" + pairID + "_" + pairID2);
					elmt.setAttribute("arity", "2");
					elmt.setAttribute("scope", "t" + pairID + " f" + pairID2);
					elmt.setAttribute("reference", "I_give_to_you_iff_you_get_from_me");
					
					Element subElmt = new Element ("parameters");
					elmt.addContent(subElmt);
					subElmt.setText("t" + pairID + " " + pairID2 + " f" + pairID2 + " " + pairID);
				}
				
				
				// Create the 3-cycle relation
				// Look up all possible 3rd parties (lexicographically lower than the two first parties if !StochDCOP)
				TreeSet<Integer> thirdParties = new TreeSet<Integer> ();
				for (Integer otherDonor : compatibleDonors) {
					if (!stoch && otherDonor >= pairID2) 
						break;
					thirdParties.add(otherDonor);
				}
				if (thirdParties.isEmpty()) 
					continue;
				TreeSet<Integer> thirdParties2 = new TreeSet<Integer> ();
				for (Integer otherPatient : canGiveTo.get(pairID2)) {
					if (!stoch && otherPatient >= pairID) 
						break;
					thirdParties2.add(otherPatient);
				}
				if (thirdParties2.isEmpty()) 
					continue;
				thirdParties.retainAll(thirdParties2);
				if (thirdParties.isEmpty()) 
					continue;

				if (! intensional) {

					if (! stoch) {
						
						elmt = new Element ("relation");
						relElement.addContent(elmt);
						elmt.setAttribute("name", "r_" + pairID + "_" + pairID2 + "_3cycles");
						elmt.setAttribute("arity", "3");
						elmt.setAttribute("semantics", "soft");

						// Build the String representation for the tuples
						elmt.setAttribute("defaultCost", "0");
						StringBuilder builder = new StringBuilder ("29:");
						elmt.setAttribute("nbTuples", Integer.toString(thirdParties.size()));
						for (Integer thirdParty : thirdParties) 
							builder.append(thirdParty + " " + pairID2 + " " + thirdParty + "|");

						builder.deleteCharAt(builder.length() - 1);
						elmt.setText(builder.toString());


						// Create the constraint
						elmt = new Element ("constraint");
						conElement.addContent(elmt);
						elmt.setAttribute("name", "c_" + pairID + "_" + pairID2 + "_3cycles");
						elmt.setAttribute("arity", "3");
						elmt.setAttribute("scope", "f" + pairID + " t" + pairID + " t" + pairID2);
						elmt.setAttribute("reference", "r_" + pairID + "_" + pairID2 + "_3cycles");

					} else { // StochDCOP
						
						// Loop through the 3rd parties
						for (Integer thirdParty : thirdParties) {
							
							// Create the relation and constraint for the 3-cycle
							elmt = new Element ("relation");
							relElement.addContent(elmt);
							elmt.setAttribute("name", "r_" + pairID + "_" + pairID2 + "_" + thirdParty + "_3cycle");
							elmt.setAttribute("arity", "6");
							elmt.setAttribute("semantics", "soft");
							int nbrTuples = 1;
							elmt.setAttribute("defaultCost", "0");
							
							StringBuilder builder = new StringBuilder ("9: " + thirdParty + " " + pairID2 + " " + thirdParty + " 1 1 1"); // we all survive
							if (compatibleDonors.contains(pairID2)) { // if the third party dies, we can still do a 2-cycle
								builder.append("|" + thirdParty + " " + pairID2 + " " + thirdParty + " 1 1 0");
								nbrTuples++;
							}
							if (compatiblePatients.contains(thirdParty)) { // if patient 2 dies, we can still do a 2-cycle
								builder.append("|" + thirdParty + " " + pairID2 + " " + thirdParty + " 1 0 1");
								nbrTuples++;
							}
							elmt.setText(builder.toString());
							elmt.setAttribute("nbTuples", Integer.toString(nbrTuples));
							
							elmt = new Element ("constraint");
							conElement.addContent(elmt);
							elmt.setAttribute("name", "c_" + pairID + "_" + pairID2 + "_" + thirdParty + "_3cycle");
							elmt.setAttribute("arity", "6");
							elmt.setAttribute("scope", "f" + pairID + " t" + pairID + " t" + pairID2 + " s" + pairID + " s" + pairID2 + " s" + thirdParty);
							elmt.setAttribute("reference", "r_" + pairID + "_" + pairID2 + "_" + thirdParty + "_3cycle");
						}
						
					}

				} else { // intensional
					
					// Create the soft constraint for 3-cycles
					elmt = new Element ("constraint");
					conElement.addContent(elmt);
					elmt.setAttribute("name", "c_" + pairID + "_" + pairID2 + "_3cycles");
					elmt.setAttribute("arity", "3");
					elmt.setAttribute("scope", "f" + pairID + " t" + pairID + " t" + pairID2);
					elmt.setAttribute("reference", "cycle3");
					
					Element subElmt = new Element ("parameters");
					elmt.addContent(subElmt);
					subElmt.setText("t" + pairID + " " + pairID2 + " f" + pairID + " t" + pairID2);
				}
			}
		}
		
		conElement.setAttribute("nbConstraints", Integer.toString(conElement.getContentSize()));
		relElement.setAttribute("nbRelations", Integer.toString(relElement.getContentSize()));
		probaElement.setAttribute("nbProbabilities", Integer.toString(probaElement.getContentSize()));
		predElement.setAttribute("nbPredicates", Integer.toString(predElement.getContentSize()));
		funcElement.setAttribute("nbFunctions", Integer.toString(funcElement.getContentSize()));
		
		return new Document (probElement);
		
	}
	
	/** Enforces arc consistency
	 * 
	 * Arc consistency means that I should not get a kidney from anyone if I cannot give one back in return, and vice-versa. 
	 * 
	 * @param canGiveTo 	for each donor, its compatible patients
	 * @param canGetFrom 	for each patient, its compatible donors
	 */
	private static void arcConsistency(
			HashMap< Integer, TreeSet<Integer> > canGiveTo,
			HashMap< Integer, TreeSet<Integer> > canGetFrom) {
		
		// Loop as long as a change has occurred
		boolean change = true;
		while (change) {
			change = false;
			
			// Look for donors who cannot give to anyone
			for (Map.Entry< Integer, TreeSet<Integer> > entry : canGiveTo.entrySet()) {
				if (! entry.getValue().isEmpty()) 
					continue;
				Integer pairID = entry.getKey();
				
				// Clear its list of compatible donors
				TreeSet<Integer> compatibleDonors = canGetFrom.get(pairID);
				for (Integer pairID2 : compatibleDonors) 
					canGiveTo.get(pairID2).remove(pairID);
				change = change || ! compatibleDonors.isEmpty();
				compatibleDonors.clear();
			}
			
			// Look for patients who cannot receive from anyone
			for (Map.Entry< Integer, TreeSet<Integer> > entry : canGetFrom.entrySet()) {
				if (! entry.getValue().isEmpty()) 
					continue;
				Integer pairID = entry.getKey();
				
				// Clear its list of compatible patients
				TreeSet<Integer> compatiblePatients = canGiveTo.get(pairID);
				for (Integer pairID2 : compatiblePatients) 
					canGetFrom.get(pairID2).remove(pairID);
				change = change || ! compatiblePatients.isEmpty();
				compatiblePatients.clear();
			}
		}
	}

	/** A patient-donor pair */
	private static class PatientDonorPair{
		
		/** The pair's ID */
		public Integer id;

		/** The patient's blood type */
		public ABO patientABO;
		
		/** The donor's blood type */
		public ABO donorABO;
		
		/** The patient's PRA level */
		public PRA pra;
		
		/** Whether the patient is compatible with the donor */
		public boolean compatible;
		
		/** Stores compatibility with other PatientDonor pairs*/
		public HashMap<Integer,Boolean> relations = new HashMap<Integer,Boolean>();
		
		/** Generates a pair according to the description and calculates its compatibility 
		 * @param id 				the pair ID
		 * @param husbandDonor 		whether the donor is the patient's husband
		 * @param patientABO 		the patient's blood type
		 * @param donorABO 			the donor's blood type
		 * @param pra 				the patient's PRA level
		 */
		public PatientDonorPair(int id, boolean husbandDonor, ABO patientABO, ABO donorABO, PRA pra) {
			this.id = id;
			this.patientABO = patientABO;
			this.pra = pra;
			this.donorABO = donorABO;
			this.compatible = ! KidneyExchange.positiveXmatch(this.pra, husbandDonor) 
								&& KidneyExchange.ABOcompatible(this.donorABO, this.patientABO);
		}
		
		/** Whether this pair's donor can give to the input pair's patient
		 * @param p2 	the receiving PatientDonorPair
		 * @return \c true iff this can donate to p2
		 */
		public boolean canGiveTo (PatientDonorPair p2){
			
			// First check whether we already know the compatibility
			Boolean compatible = this.relations.get(p2.id);
			if(compatible != null)
				return compatible;
			
			// Compute the compatibility
			boolean b = ! KidneyExchange.positiveXmatch(p2.pra, false) 
						&& KidneyExchange.ABOcompatible(this.donorABO, p2.patientABO);
			
			this.relations.put(p2.id, b);
			
			return b;
		}
		
		
		/** @see java.lang.Object#toString() */
		public String toString(){
			String desc = "patient donor pair:\n";
			desc += "patient ABO: " + this.patientABO + "\n";
			desc += "donor ABO: " + this.donorABO + "\n";
			desc += "patient PRA: " + this.pra + "\n";
			desc += "compatible? " + (this.compatible ? "YES" : "NO");
			
			return "{"+desc+"}";
		}
	}
	
	
	/** Generates a PatientDonorPair according to the probabilities given 
	 * @param id 	the pair ID
	 * @param pra 	indicates the PRA of the patient
	 * @return the pair
	 */
	static private PatientDonorPair generatePair(int id, PRA pra) {
		
		Random rnd = new Random();
		
		ABO patientABO = KidneyExchange.convertABO(rnd.nextDouble());
		ABO donorABO = KidneyExchange.convertABO(rnd.nextDouble());
		boolean female = (rnd.nextDouble() < KidneyExchange.sexF);
		boolean spouse = (rnd.nextDouble() < KidneyExchange.relationSpouse);
		PRA patientPRA = (pra == null ? convertPRA(rnd.nextDouble()) : pra);
		
		return new PatientDonorPair(id, female && spouse, patientABO, donorABO, patientPRA);
	}
	
	/** Helper method which returns the blood type corresponding to the probability given 
	 * @param d 	in [0,1] probability Double 
	 * @return the blood type corresponding to given probability
	 */
	private static ABO convertABO(double d) {
		
		if (d <= KidneyExchange.bloodO)
			return ABO.O;
		else if (d <= KidneyExchange.bloodO + KidneyExchange.bloodA)
			return ABO.A;
		else if (d <= KidneyExchange.bloodO + KidneyExchange.bloodA + KidneyExchange.bloodB)
			return ABO.B;
		else
			return ABO.AB;
	}
	
	/** Returns the discretized PRA corresponding to the given double
	 * @param d 	in [0,1]
	 * @return the PRA
	 */
	private static PRA convertPRA (double d){
		
		if (d <= KidneyExchange.lowPRA)
			return PRA.LOW;
		else if (d <= KidneyExchange.lowPRA + KidneyExchange.mediumPRA)
			return PRA.MED;
		else
			return PRA.HIGH;
	}
	 
	/** Checks the blood-type compatibility of a patient-donor pair
	 * @param donorType 		the donor type
	 * @param patientType 		the patient type
	 * @return compatibility
	 */
	private static boolean ABOcompatible (ABO donorType, ABO patientType) {
		return (donorType == patientType || donorType == ABO.O || patientType == ABO.AB);
	}
	
	
	/** Returns true for positive X match with probability depending on several factors
	 * @param pra 			the patient PRA
	 * @param husbandDonor 	whether the donor is the patient's husband
	 * @return true if positive x match
	 */
	private static boolean positiveXmatch (PRA pra, boolean husbandDonor) {
		
		// calculate compatibility: 
		double xMatchPrb = 0;
		
		// low PRA
		if (pra == PRA.LOW)
			xMatchPrb = KidneyExchange.posXmatchLowPRA;
		//medium PRA
		else if(pra == PRA.MED)
			xMatchPrb = KidneyExchange.posXmatchMediumPRA;
		//high PRA
		else 
			xMatchPrb = KidneyExchange.posXmatchHighPRA;
		
		if(husbandDonor)
			xMatchPrb = 1 - KidneyExchange.negXmatchHusbandDonorFactor*(1-xMatchPrb);
		
		return (Math.random() < xMatchPrb);
	}
	
	/** 
	 * @param set 	the set
	 * @param sep separator 
	 * @return the elements (toString) of the array concatenated with sep
	 */
	private static String implode (TreeSet<?> set, String sep){
		
		if (set.isEmpty()) 
			return "";
		
		else {
			StringBuilder builder = new StringBuilder ();
			for (Iterator<?> iter = set.iterator(); ; ) {
				builder.append(iter.next());
				if (iter.hasNext()) 
					builder.append(sep);
				else 
					break;
			}

			return builder.toString();
		}
	}
	
	/** Generates a DOT representation of the compatibility graph
	 * @param canGiveTo 	for each donor, the list of patients it can give to
	 * @return a DOT representation of the compatibility graph
	 */
	private static String toDOT (HashMap< Integer, TreeSet<Integer> > canGiveTo) {
		
		StringBuilder builder = new StringBuilder ("digraph {\n");
		builder.append("\tnode [shape = \"circle\"];\n\n");
		
		for (Map.Entry< Integer, TreeSet<Integer> > entry : canGiveTo.entrySet()) {
			Integer donor = entry.getKey();
			
			builder.append("\t" + donor + ";\n");
			
			for (Integer patient : entry.getValue()) 
				builder.append("\t" + donor + " -> " + patient + ";\n");
			
			builder.append("\n");
		}
		
		builder.append("}");
		return builder.toString();
	}

}
