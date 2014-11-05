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

package frodo2.benchmarks.auctions.xcsp;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jdom2.Element;

/**
 * This class is used to generate the 'relation' tags in the XML output file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Relation extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 9169972812415905559L;
	
	
	/**Name of the relation*/
	private String name;
	
	/**Arity of the relation, i.e. number of variables over which the relation spans*/
	private String arity;
	
	/**Number of tuples that do not take the default utility*/
	private String nbTuples;
	
	/**Semantics of the relation; only 'soft' allowed at the moment*/
	private String semantics;
	
	/**Default utility of the relation*/
	private String defaultUtility;
	
	/**Mapping of utilities to a list of assignments rewarding that utility (for all assignments not yielding the default utility)*/
	private Map<Double, List<String>> alternativeAssignments;

	
	/** Constructor
	 * @param name Name of the relation
	 * @param arity Arity of the relation
	 * @param defaultUtility Default utility for all assignments that are not explicitly mentioned
	 * @param alternativeAssignments Mapping from utilities to assignments that do not yield default utility
	 */
	public Relation(String name, String arity, String defaultUtility, Map<Double, List<String>> alternativeAssignments) {
		this (name, arity, defaultUtility, alternativeAssignments, "soft");
	}
	
	/** Constructor
	 * @param name 						Name of the relation
	 * @param arity 					Arity of the relation
	 * @param defaultUtility 			Default utility for all assignments that are not explicitly mentioned
	 * @param alternativeAssignments 	Mapping from utilities to assignments that do not yield default utility
	 * @param semantics 				"soft", "supports" or "conflicts"
	 */
	public Relation(String name, String arity, String defaultUtility, Map<Double, List<String>> alternativeAssignments, String semantics) {
		super("relation");
		this.name = name;
		this.arity = arity;
		this.nbTuples = getNbTuples(alternativeAssignments);
		this.semantics = semantics;
		this.defaultUtility = defaultUtility;
		this.alternativeAssignments = alternativeAssignments;
	}

	/**
	 * This method adds the instance variables as attributes, so they are written to the 'relation' element in the XML output file. To write the alternative
	 * assignments, another method is used
	 */
	public void create() {
		setAttribute("name", name);
		setAttribute("arity", arity);
		setAttribute("nbTuples", nbTuples);
		setAttribute("semantics", semantics);
		if (this.semantics.equals("soft")) 
			setAttribute("defaultCost", defaultUtility);
		addContent(altAssignmentsToString());
	}

	/**
	 * This method transforms the mapping from utilities to the variable assignments yielding the corresponding utility into a string representation. The syntax
	 * is as follows:<br>
	 * utility1: ass_1|ass_2|...|ass_n|utility2: ass_1|ass_2 etc.
	 * 
	 * @return String representation of alternative assignments
	 */
	private String altAssignmentsToString() {
		
		StringBuilder builder = new StringBuilder ();
		
		//Iteration over all utilities different from the default utility
		for(Entry<Double, List<String>> utilityToAssignments : alternativeAssignments.entrySet()) {
			
			if (this.semantics.equals("soft")) {
				
				//Appending utility_x: 
				double util = utilityToAssignments.getKey();
				if (util == (double)(int)util) 
					builder.append((int) util);
				else 
					builder.append(util);
				builder.append(": ");
			}
			
			//Iteration over all assignments yielding this utility
			for(String assignment : utilityToAssignments.getValue()) {
				
				//Appending assignment_x|
				builder.append(assignment);
				builder.append("|");
			}
		}
		
		//Removing final |
		builder.setLength(builder.length() - 1);
		
		return builder.toString();
	}
	
	/**
	 * @param tuples Mapping from values to assignments
	 * @return Number of assignments found in the mapping
	 */
	private String getNbTuples(Map<Double, List<String>> tuples) {
		int nbTuples = 0;
		
		//Adds the size of each list of assignments to nbTuples
		for(List<String> assignments : tuples.values()) {
			nbTuples += assignments.size();
		}
		
		return "" + nbTuples;
	}
}
