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

package frodo2.algorithms;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.BasicUtilitySolutionSpace;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.hypercube.Hypercube;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** An XCSP parser that provides convenient methods to extract information from XCSP files
 * @author Thomas Leaute
 * @param <U> the type used for variable values
 * @param <V> the type used for utility values
 */
@SuppressWarnings("unchecked")
public class XCSPparser < V extends Addable<V>, U extends Addable<U> > implements DCOPProblemInterface<V, U> {

	/** Used for serialization */
	private static final long serialVersionUID = 6812231232965926953L;

	/** Creates a JDOM Document out of the input XML file in XCSP format
	 * @param file 				the XML file
	 * @return 					a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 	if a parsing error occurs while reading the file
	 * @throws IOException 		if an I/O error occurs while accessing the file
	 * @note The input file will be checked against the XCSP schema file
	 */
	public static Document parse (File file) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder(XMLReaders.XSDVALIDATING);
		builder.setFeature("http://apache.org/xml/features/validation/schema", true);
		return builder.build(file);
	}

	/** Creates a JDOM Document out of the input XML file (not necessarily in XCSP format)
	 * @param file 				the XML file
	 * @param checkXCSP 		if \c true, checks that the input XCSP file is properly formatted
	 * @return 					a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 	if a parsing error occurs while reading the file
	 * @throws IOException 		if an I/O error occurs while accessing the file
	 */
	public static Document parse (File file, boolean checkXCSP) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder(checkXCSP ? XMLReaders.XSDVALIDATING : XMLReaders.NONVALIDATING); 
		if (checkXCSP) 
			builder.setFeature("http://apache.org/xml/features/validation/schema", true);
		return builder.build(file);
	}

	/** Creates a JDOM Document out of the input XML file path in XCSP format
	 * @param path 				the XML file path
	 * @return 					a JDOM Document resulting from the parsing of the input file
	 * @throws Exception 	if an error occurs
	 * @note The input file will be checked against the XCSP schema file
	 */
	public static Document parse (String path) throws Exception {
		return parse(new File (path));
	}

	/** Creates a JDOM Document out of the input XML file path (not necessarily in XCSP format)
	 * @param path 				the XML file path
	 * @param checkXCSP 		if \c true, checks that the input XCSP file is properly formatted
	 * @return 					a JDOM Document resulting from the parsing of the input file
	 * @throws Exception 	if an error occurs
	 */
	public static Document parse (String path, boolean checkXCSP) throws Exception {
		return parse(new File (path), checkXCSP);
	}

	/** Creates a JDOM Document out of the input XML stream in XCSP format
	 * @param stream 			the XML stream
	 * @return 					a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 	if a parsing error occurs while reading the file
	 * @throws IOException 		if an I/O error occurs while accessing the file
	 * @note The input stream will be checked against the XCSP schema file
	 */
	public static Document parse (InputStream stream) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder(XMLReaders.XSDVALIDATING);
		builder.setFeature("http://apache.org/xml/features/validation/schema", true);
		return builder.build(stream);
	}

	/** Creates a JDOM Document out of the input XML stream (not necessarily in XCSP format)
	 * @param stream 			the XML stream
	 * @param checkXCSP 		if \c true, checks that the input XCSP file is properly formatted
	 * @return 					a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 	if a parsing error occurs while reading the file
	 * @throws IOException 		if an I/O error occurs while accessing the file
	 */
	public static Document parse (InputStream stream, boolean checkXCSP) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder(checkXCSP ? XMLReaders.XSDVALIDATING : XMLReaders.NONVALIDATING);
		if (checkXCSP) 
			builder.setFeature("http://apache.org/xml/features/validation/schema", true);
		return builder.build(stream);
	}

	/** Returns a human-friendly String representation of the input JDOM Document 
	 * @param doc 	the JDOM Document
	 * @return 		a String representation of the input Document
	 */
	public static String toString (Document doc) {
		return new XMLOutputter(Format.getPrettyFormat()).outputString(doc);
	}

	/** Returns a human-friendly String representation of the input JDOM Element 
	 * @param root 	the JDOM Element
	 * @return 		a String representation of the input Element
	 */
	public static String toString (Element root) {
		return new XMLOutputter(Format.getPrettyFormat()).outputString(root);
	}

	/** Prints the input problem in DOT format
	 * @param args 				the path to the XCSP file
	 * @throws Exception 	if an error occurs
	 */
	public static void main (String[] args) throws Exception {

		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions.\n");

		if (args.length != 1) {
			System.out.println("ERROR: Takes exactly one parameter: the path to the input XCSP file.");
			return;
		}

		System.out.println(XCSPparser.toDOT(XCSPparser.parse(args[0], false)));
	}

	/** Returns the constraint graph in DOT format
	 * @param doc 	the XCSP instance document 
	 * @return 		a String representation of the constraint graph in DOT format
	 */
	public static String toDOT (Document doc) {
		return toDOT (doc.getRootElement());
	}

	/** Returns the constraint graph in DOT format
	 * @param root 	the XCSP instance element 
	 * @return 		a String representation of the constraint graph in DOT format
	 */
	public static String toDOT (Element root) {
		StringBuilder out = new StringBuilder ("graph {\n\tnode [shape = \"circle\"];\n");

		// Print the agents, with their respective variables
		XCSPparser<AddableInteger, AddableInteger> parser = new XCSPparser<AddableInteger, AddableInteger> (null, root, false);
		for (String agent : parser.getAgents()) {
			out.append("\tsubgraph cluster_" + agent + " {\n");
			out.append("\t\tlabel = " + agent + ";\n");
			
			// Add an invisible variable if the agent owns no variable, so that it is still displayed
			if (parser.getNbrVars(agent) == 0) 
				out.append("\t\t" + new Object().hashCode() + " [shape=\"none\", label=\"\"];\n");
			
			for (String var : parser.getVariables(agent)) {
				out.append("\t\t" + var);

				// If var if a facade variable, fill it
				for (String neigh : parser.getNeighborVars(var)) {
					if (! agent.equals(parser.getOwner(neigh))) {
						out.append(" [style=\"filled\"]");
						break;
					}
				}

				out.append(";\n");
			}
			out.append("\t}\n");
		}
		out.append("\n");

		// Print the variables with no specified owner
		for (String anonymVar : parser.getVariables(null)) 
			out.append("\t" + anonymVar + ";\n");
		out.append("\n");

		// Print the neighborhoods
		for (String var : parser.getVariables()) // variables with an owner
			for (String neighbor : parser.getNeighborVars(var, true)) 
				if (var.compareTo(neighbor) >= 0) 
					out.append("\t" + var + " -- " + neighbor + ";\n");
		for (String var : parser.getVariables(null)) // variables with no specified owner
			for (String neighbor : parser.getNeighborVars(var, true)) 
				if (var.compareTo(neighbor) >= 0) 
					out.append("\t" + var + " -- " + neighbor + ";\n");

		out.append("}\n");
		return out.toString();
	}

	/** JDOM Element in XCSP format */
	protected Element root;

	/** The class to be used for variable values */
	private Class<V> domClass = (Class<V>) AddableInteger.class;

	/** The class to be used for utility values */
	protected Class<U> utilClass = (Class<U>) AddableInteger.class;

	/** An instance of V */
	protected V valInstance = (V) new AddableInteger ();

	/** The name of the agent owning the problem */
	protected String agentName;

	/** If \c true, neighborhood relationships between decision variables are extended through random variables. 
	 * 
	 * In other words, for a given decision variable x, its neighborhood consists of: <br>
	 * - the decision variables that share constraints with x (as usual), <br>
	 * - the decision variables that are direct neighbors of any random variable that can be reached from x by a path that involves only random variables. 
	 */
	protected final boolean extendedRandNeighborhoods;
	
	/** Whether each agent knows the identities of all agents */
	protected final boolean publicAgents;
	
	/** Whether to behave in MPC mode. 
	 * 
	 * In MPC mode: 
	 * - each agent knows the identities of all agents; 
	 * - all variables are known to all agents.
	 */
	protected final boolean mpc;

	/** Whether to count constraint checks */
	protected final boolean countNCCCs;
	
	/** a set of spaces for which ncccs should be ignored */
	protected HashSet<String> spacesToIgnoreNcccs;
	
	/** The NCCC count */
	private long ncccCount;

	/** Constructor from a JDOM root Element in XCSP format
	 * @param root 	the JDOM root Element in XCSP format
	 */
	public XCSPparser (Element root) {
		this.root = root;
		this.extendedRandNeighborhoods = false;
		this.publicAgents = false;
		this.mpc = false;
		this.countNCCCs = false;
		this.spacesToIgnoreNcccs = new HashSet<String>();
	}

	/** Constructor from a JDOM root Element in XCSP format
	 * @param root 			the JDOM root Element in XCSP format
	 * @param countNCCCs 	Whether to count constraint checks
	 */
	protected XCSPparser (Element root, boolean countNCCCs) {
		this.root = root;
		this.countNCCCs = countNCCCs;
		this.extendedRandNeighborhoods = false;
		this.publicAgents = false;
		this.mpc = false;
		this.spacesToIgnoreNcccs = new HashSet<String>();
	}

	/** Constructor from a JDOM root Element in XCSP format
	 * @param root 							the JDOM root Element in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 */
	protected XCSPparser (Element root, boolean countNCCCs, boolean extendedRandNeighborhoods) {
		this(root, countNCCCs, extendedRandNeighborhoods, false);
	}

	/** Constructor from a JDOM root Element in XCSP format
	 * @param root 							the JDOM root Element in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 * @param publicAgents 					Whether each agent knows the identities of all agents
	 */
	protected XCSPparser (Element root, boolean countNCCCs, boolean extendedRandNeighborhoods, boolean publicAgents) {
		this.root = root;
		this.countNCCCs = countNCCCs;
		this.extendedRandNeighborhoods = extendedRandNeighborhoods;
		this.publicAgents = publicAgents;
		this.mpc = false;
		this.spacesToIgnoreNcccs = new HashSet<String>();
	}

	/** Constructor from a JDOM root Element in XCSP format
	 * @param agentName 	the name of the agent owning the input subproblem
	 * @param root 			the JDOM root Element in XCSP format
	 * @param countNCCCs 	Whether to count constraint checks
	 */
	protected XCSPparser (String agentName, Element root, boolean countNCCCs) {
		this (agentName, root, countNCCCs, false, new HashSet<String>(), false);
	}
	
	/** Constructor from a JDOM root Element in XCSP format
	 * @param agentName 					the name of the agent owning the input subproblem
	 * @param root 							the JDOM root Element in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 * @param spacesToIgnoreNcccs			list of spaces for which NCCCs should NOT be counted
	 * @param mpc 							Whether to behave in MPC mode
	 */
	protected XCSPparser (String agentName, Element root, boolean countNCCCs, boolean extendedRandNeighborhoods, HashSet<String> spacesToIgnoreNcccs, boolean mpc) {
		this.agentName = agentName;
		this.root = root;
		this.countNCCCs = countNCCCs;
		this.extendedRandNeighborhoods = extendedRandNeighborhoods;
		this.publicAgents = false;
		this.mpc = mpc;
		this.spacesToIgnoreNcccs = spacesToIgnoreNcccs;
	}

	/** Calls the corresponding constructor
	 * @param agent 	name of the agent
	 * @param instance 	the agent's subproblem
	 * @return a new instance of this class
	 */
	protected XCSPparser<V, U> newInstance (String agent, Element instance) {
		return new XCSPparser<V, U> (agent, instance, this.countNCCCs, this.extendedRandNeighborhoods, this.spacesToIgnoreNcccs, this.mpc);
	}

	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 	the JDOM Document in XCSP format
	 */
	public XCSPparser (Document doc) {
		this(doc.getRootElement());
	}

	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 			the JDOM Document in XCSP format
	 * @param countNCCCs 	Whether to count constraint checks
	 */
	public XCSPparser (Document doc, Boolean countNCCCs) {
		this(doc.getRootElement(), countNCCCs);
	}

	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 							the JDOM Document in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param spacesToIgnore	list of spaces for which NCCCs should NOT be counted
	 */
	public XCSPparser (Document doc, Boolean countNCCCs, HashSet<String> spacesToIgnore) {
		this(doc.getRootElement(), countNCCCs);
		this.spacesToIgnoreNcccs = spacesToIgnore;
	}

	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 							the JDOM Document in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 */
	public XCSPparser (Document doc, boolean countNCCCs, boolean extendedRandNeighborhoods) {
		this(doc.getRootElement(), countNCCCs, extendedRandNeighborhoods);
	}

	/** Constructor from a JDOM Document in XCSP format
	 * @param doc 							the JDOM Document in XCSP format
	 * @param countNCCCs 					Whether to count constraint checks
	 * @param extendedRandNeighborhoods 	whether we want extended random neighborhoods
	 * @param publicAgents 					Whether each agent knows the identities of all agents
	 */
	public XCSPparser (Document doc, boolean countNCCCs, boolean extendedRandNeighborhoods, boolean publicAgents) {
		this(doc.getRootElement(), countNCCCs, extendedRandNeighborhoods, publicAgents);
	}

	/** Constructor
	 * @param doc 		the JDOM Document in XCSP format
	 * @param params 	the parameters of the solver
	 */
	public XCSPparser (Document doc, Element params) {
		this.root = doc.getRootElement();
		this.extendedRandNeighborhoods = Boolean.parseBoolean(params.getAttributeValue("extendedRandNeighborhoods"));
		this.publicAgents = Boolean.parseBoolean(params.getAttributeValue("publicAgents"));
		this.mpc = Boolean.parseBoolean(params.getAttributeValue("mpc"));

		// Display the problem in DOT format if required
		if (Boolean.parseBoolean(params.getAttributeValue("displayGraph"))) {
			String dotRendererClass = params.getAttributeValue("DOTrenderer");
			if(dotRendererClass == null || dotRendererClass.equals("")) {
				System.out.println("Constraint graph:\n" + this.toDOT());
			}
			else {
				try {
					Class.forName(dotRendererClass).getConstructor(String.class, String.class).newInstance("Constraint graph", this.toDOT());
				} 
				catch(Exception e) {
					System.out.println("Could not instantiate given DOT renderer class: " + dotRendererClass);
				}
			}
		}

		// Parse the class of V
		String valClassName = params.getAttributeValue("domClass");
		if (valClassName != null) {
			try {
				this.setDomClass((Class<V>) Class.forName(valClassName));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			try {
				this.valInstance = domClass.newInstance();
			} catch (InstantiationException e) {
				System.err.println("Failed calling the nullary constructor for the class " + domClass.getName() + " used for variable values");
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				System.err.println("Failed calling the nullary constructor for the class " + domClass.getName() + " used for variable values");
				e.printStackTrace();
			}
		}

		// Parse the class of U
		String utilClassName = params.getAttributeValue("utilClass");
		if (utilClassName == null) 
			this.setUtilClass((Class<U>) AddableInteger.class);
		else {
			try {
				this.setUtilClass((Class<U>) Class.forName(utilClassName));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		// Parse whether we should count NCCCs
		String ncccString = params.getAttributeValue("countNCCCs");
		if (ncccString == null) 
			this.countNCCCs = false;
		else 
			this.countNCCCs = Boolean.parseBoolean(ncccString);
		
		// Parse which spaces should be ignored
		spacesToIgnoreNcccs = new HashSet<String>();
		Element spacesToIgnore = params.getChild("ignoreNCCCs");
		if(spacesToIgnore != null) {
			List<Element> list = spacesToIgnore.getChildren();
			for(Element classElmt : list)
				this.spacesToIgnoreNcccs.add(classElmt.getText());
		}
	}

	/** @see DCOPProblemInterface#setDomClass(java.lang.Class) */
	public void setDomClass (Class<V> domClass) {
		this.domClass = domClass;
		try {
			this.valInstance = domClass.newInstance();
		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + domClass.getName() + " used for variable values");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + domClass.getName() + " used for variable values");
			e.printStackTrace();
		}
	}

	/** @see ProblemInterface#getDomClass() */
	@Override
	public Class<V> getDomClass() {
		return this.domClass;
	}

	/**
	 * Adds a space to be ignored
	 * 
	 * @author Brammert Ottens, 8 feb. 2011
	 * @param spaceToIgnore the name of the space to be ignored
	 */
	public void addSpaceToIgnore(String spaceToIgnore) {
		this.spacesToIgnoreNcccs.add(spaceToIgnore);
	}
	
	/** @see DCOPProblemInterface#setUtilClass(java.lang.Class) */
	public void setUtilClass (Class<U> utilClass) {
		this.utilClass = utilClass;
	}

	/** @see DCOPProblemInterface#getAgent() */
	public String getAgent () {
		return this.agentName;
	}

	/** @see DCOPProblemInterface#getZeroUtility() */
	public U getZeroUtility () {
		try {
			return (U) utilClass.newInstance().getZero();

		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
	}

	/** @see DCOPProblemInterface#getPlusInfUtility() */
	public U getPlusInfUtility () {
		try {
			return (U) utilClass.newInstance().getPlusInfinity();

		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
	}

	/** @see DCOPProblemInterface#getMinInfUtility() */
	public U getMinInfUtility () {
		try {
			return (U) utilClass.newInstance().getMinInfinity();

		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
	}

	/** Extracts probability spaces from the constraints in the problem
	 * @return 		a list of hypercubes, or \c null if some information is missing in the problem file
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getProbabilitySpaces () {
		return this.getSpaces(null, true, true, null);
	}

	/** Extracts probability spaces involving the input variable from the constraints in the problem
	 * @return 		a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param var 	the variable of interest
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getProbabilitySpaces (String var) {
		HashSet<String> vars = new HashSet<String>();
		vars.add(var);
		return this.getSpaces(vars, true, true, null);
	}

	/** @see DCOPProblemInterface#getSolutionSpaces() */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces () {
		return this.getSpaces(null, false, false, null);
	}

	/** Extracts solution spaces involving the input variable from the constraints in the problem
	 * @return 			a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param var 		the variable of interest
	 * @warning Ignores hypercubes involving variables whose owner is unknown. 
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (String var) {
		HashSet<String> vars = null;
		if (var != null) {
			vars = new HashSet<String>();
			vars.add(var);
		}
		return this.getSpaces(vars, false, false, null);
	}

	/** Extracts solution spaces from the constraints in the problem
	 * @return 					a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param withAnonymVars 	whether hypercubes involving variables with unknown owners should be taken into account
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (final boolean withAnonymVars) {
		return this.getSpaces(null, withAnonymVars, false, null);
	}

	/** Extracts solution spaces involving the input variable from the constraints in the problem
	 * @return 					a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param var 				the variable of interest
	 * @param withAnonymVars 	whether hypercubes involving variables with unknown owners should be taken into account
	 */
	public List< ? extends UtilitySolutionSpace<V, U> > getSolutionSpaces (String var, final boolean withAnonymVars) {
		HashSet<String> vars = null;
		if (var != null) {
			vars = new HashSet<String>();
			vars.add(var);
		}
		return this.getSpaces(vars, withAnonymVars, false, null);
	}

	/** @see DCOPProblemInterface#getSolutionSpaces(java.lang.String, java.util.Set) */
	public List<? extends UtilitySolutionSpace<V, U>> getSolutionSpaces(String var, Set<String> forbiddenVars) {
		HashSet<String> vars = null;
		if (var != null) {
			vars = new HashSet<String>();
			vars.add(var);
		}
		return this.getSpaces(vars, false, false, forbiddenVars);
	}

	/** @see DCOPProblemInterface#getSolutionSpaces(java.lang.String, boolean, java.util.Set) */
	public List<? extends UtilitySolutionSpace<V, U>> getSolutionSpaces(String var, boolean withAnonymVars, Set<String> forbiddenVars) {
		HashSet<String> vars = null;
		if (var != null) {
			vars = new HashSet<String>();
			vars.add(var);
		}
		return this.getSpaces(vars, withAnonymVars, false, forbiddenVars);
	}
	
	/** @see DCOPProblemInterface#getSolutionSpaces(java.util.Set, boolean, java.util.Set) */
	public List<? extends UtilitySolutionSpace<V, U>> getSolutionSpaces(Set<String> vars, boolean withAnonymVars, Set<String> forbiddenVars) {
		return this.getSpaces(vars, withAnonymVars, false, forbiddenVars);
	}
	
	/** @return whether this parser is counting NCCCs */
	public boolean isCountingNCCCs () {
		return this.countNCCCs;
	}

	/** @see DCOPProblemInterface#incrNCCCs(long) */
	public void incrNCCCs (long incr) {
		if (this.countNCCCs)
			this.ncccCount += incr;
	}
	
	/** @see DCOPProblemInterface#setNCCCs(long) */
	public void setNCCCs (long ncccs) {
		if (this.countNCCCs)
			this.ncccCount = ncccs;
	}
	
	/** @see DCOPProblemInterface#getNCCCs() */
	public long getNCCCs () {
		return this.ncccCount;
	}
	
	/** The representation of an XCSP relation
	 * @author Thomas Leaute
	 * @param <V> 	the type used for variable values
	 * @param <U> 	the type used for utility values
	 */
	protected static class Relation < V extends Addable<V>, U extends Addable<U> > {

		/** The tuples */
		public V[][] tuples;

		/** The utilities */
		public U[] utilities;

		/** The default utility */
		public U defaultUtil;
	}

	/** @return -INF if we are maximizing, +INF if we are minimizing */
	protected U getInfeasibleUtil () {

		// Check whether we are minimizing or maximizing
		String maximize = root.getChild("presentation").getAttributeValue("maximize");
		if (maximize == null) 
			return this.getPlusInfUtility();
		else if (Boolean.parseBoolean(maximize)) 
			return this.getMinInfUtility();
		else 
			return this.getPlusInfUtility();
	}

	/** Extracts hypercubes from the constraints in the problem
	 * @author Radoslaw Szymanek, Thomas Leaute & Brammert Ottens 
	 * @return 					a list of hypercubes, or \c null if some information is missing in the problem file
	 * @param vars				if \c null, returns all constraints; otherwise, returns only the constraints involving at least one of the variables in \a vars
	 * @param withAnonymVars 	whether hypercubes involving variables with unknown owners should be taken into account
	 * @param getProbs 			if \c true, returns the probability spaces (ignoring \a withAnonymVars); else, returns the solution spaces
	 * @param forbiddenVars 	any space involving any of these variables will be ignored
	 * @todo The implementation can be improved so as to only parse things that are needed (wrt \a var). 
	 */
	protected List< ? extends UtilitySolutionSpace<V, U> > getSpaces (Set<String> vars, final boolean withAnonymVars, final boolean getProbs, 
			Set<String> forbiddenVars) {
		
		assert vars == null || !vars.isEmpty(): "The set of variables is empty";

		// Create an instance of U used to parse a utility value from a String
		U utilInstance = this.getZeroUtility();

		// The hypercubes are stored in the result array.
		ArrayList< UtilitySolutionSpace<V, U> > result = new ArrayList< UtilitySolutionSpace<V, U> >();

		final boolean debugLoad = false;

		// First important element of XCSP format is the specification of the domains.		
		org.jdom2.Element domains = root.getChild("domains");

		// domain is represented as a list of integers. Potentially a problem 
		// if a domain is large. However, the hypercubes will have problems too
		// so it is unlikely for variables to have large domains.
		HashMap<String, V[]> domainsHashMap = new HashMap<String, V[]>();

		// Reads information about variables domains.
		for (org.jdom2.Element domain : (List<org.jdom2.Element>) domains.getChildren()) {

			String name = domain.getAttributeValue("name");

			// Hashmap to associate domain names with the list of elements in that domain.
			domainsHashMap.put(name, (V[]) this.getDomain(domain, debugLoad));
		}

		if (debugLoad)
			System.out.println(domainsHashMap);

		// Second important element in XCSP format is describing variables.
		org.jdom2.Element variables = root.getChild("variables");

		// Each variable has its list of values in their domain. 
		HashMap<String, V[]> variablesHashMap = new HashMap<String, V[]>();

		for (org.jdom2.Element variable : (List<org.jdom2.Element>) variables.getChildren()) {

			String name = variable.getAttributeValue("name");
			String domName = variable.getAttributeValue("domain");

			if (!getProbs && domName == null) // we don't know the domain of this variable
				return null;

			// Variables domain is specified by the name so the actual domain is obtained
			// from the hashmap describing the domains.
			variablesHashMap.put(name, domainsHashMap.get(domName));
		}

		if (debugLoad)
			System.out.println(variablesHashMap);

		// Part responsible for reading the specification of relations or probabilities (depending on the getProbs flag)

		org.jdom2.Element relations;
		if (!getProbs) {
			relations = root.getChild("relations");
		} else 
			relations = root.getChild("probabilities");
		HashMap< String, Relation<V, U> > relationInfos = new HashMap< String, Relation<V, U> > ();

		if (relations != null) {

			for (org.jdom2.Element relation : (List<org.jdom2.Element>) relations.getChildren()) {

				String name = relation.getAttributeValue("name");
				Relation<V, U> relationInfo = new Relation<V, U> ();
				relationInfos.put(name, relationInfo);

				int arity = Integer.valueOf(relation.getAttributeValue("arity"));
				String semantics = relation.getAttributeValue("semantics");
				int nbTuples = Integer.valueOf(relation
						.getAttributeValue("nbTuples"));
				String defaultCost;
				if (!getProbs) {
					defaultCost = relation.getAttributeValue("defaultCost");
				} else 
					defaultCost = relation.getAttributeValue("defaultProb");

				if (defaultCost != null) 
					relationInfo.defaultUtil = utilInstance.fromString(defaultCost);

				// XCSP can have support and conflicts semantics too, these are of no 
				// use in Hypercubes/DPOP context so they are ignored.
				if (!semantics.equals("soft"))
					continue;

				if (nbTuples == 0 && defaultCost == null) {
					System.err.println("Relation `" + name + "' has nbTuples == 0 and no default cost");
					continue;
				}

				String tuplesString = relation.getText();

				V[][] relationTuples = (V[][]) new Addable[nbTuples][arity];
				U[] utility = (U[]) Array.newInstance(utilClass, nbTuples);

				// Get current utility of 
				Pattern pattern = Pattern.compile("\\|");
				String[] tuples = pattern.split(tuplesString);
				if (tuplesString.length() == 0) 
					tuples = new String [0];

				if (tuples.length != nbTuples) 
					System.err.println("Relation `" + name + "' has nbTuples == " + nbTuples + 
							" but its description actually contains " + tuples.length + " tuples: " + Arrays.toString(tuples));

				Pattern patternColon = Pattern.compile(":");
				pattern = Pattern.compile("\\s+");

				U currentUtility = null;

				for (int i = 0; i < nbTuples; i++) {

					if (tuples[i].contains(":")) {
						String[] pair = patternColon.split(tuples[i]);
						tuples[i] = pair[1];
						currentUtility = utilInstance.fromString(pair[0].trim());
					}

					String[] vals = pattern.split(tuples[i].trim());

					int position = -1;

					for (String value : vals) {

						position++;

						relationTuples[i][position] = valInstance.fromString(value);

					}

					utility[i] = currentUtility;

				}

				// stores for each relation name its list of tuples and utility for
				// each tuple.
				relationInfo.tuples = relationTuples;
				relationInfo.utilities = utility;
			}

			if (debugLoad)
				System.out.println(relationInfos);

		}

		// This element actually describes all the constraints.
		org.jdom2.Element constraints = root.getChild("constraints");

		U infeasibleUtil = this.getInfeasibleUtil();

		for (org.jdom2.Element constraint : (List<org.jdom2.Element>) constraints.getChildren()) 
			this.parseConstraint(result, constraint, variablesHashMap, relationInfos, vars, getProbs, withAnonymVars, infeasibleUtil, forbiddenVars);

		return result;		
	}

	/** Parses a constraint
	 * @param spaces 				the list of spaces to which the constraint should be added
	 * @param constraint 			the XCSP description of the constraint
	 * @param variablesHashMap 		the domain of each variable
	 * @param relationInfos 		relations, indexed by their names
	 * @param vars					if \c null, returns all constraints; otherwise, returns only the constraints involving at least one of the variables in \a vars
	 * @param getProbs 				if \c true, returns the probability spaces (ignoring \a withAnonymVars); else, returns the solution spaces
	 * @param withAnonymVars 		whether constraints involving variables with unknown owners should be taken into account
	 * @param infeasibleUtil 		the infeasible utility
	 * @param forbiddenVars 		any space involving any of these variables will be ignored
	 */
	protected void parseConstraint(ArrayList< UtilitySolutionSpace<V, U> > spaces, Element constraint, 
			HashMap<String, V[]> variablesHashMap, HashMap< String, Relation<V, U> > relationInfos, 
			Set<String> vars, final boolean getProbs, final boolean withAnonymVars, U infeasibleUtil, Set<String> forbiddenVars) {

		String name = constraint.getAttributeValue("name");
		String owner = constraint.getAttributeValue("agent");

		//int arity = Integer.valueOf(constraint.getAttributeValue("arity"));
		String scope = constraint.getAttributeValue("scope");
		String reference = constraint.getAttributeValue("reference");

		Relation<V, U> relationInfo = relationInfos.get(reference);
		if (relationInfo != null) {

			Pattern pattern = Pattern.compile("\\s+");

			String[] varNames = pattern.split(scope);

			// Skip this constraint if it does not involve any variable of interest (or if we want all constraints)
			if (vars != null && Collections.disjoint(vars, Arrays.asList(varNames)))
				return;

			// Skip this constraint if if involves any of the forbidden variables
			if (forbiddenVars != null) 
				for (String varName : varNames) 
					if (forbiddenVars.contains(varName)) 
						return;

			V[][] variables_domain = (V[][]) Array.newInstance(variablesHashMap.values().iterator().next().getClass(), varNames.length);

			int no = -1;
			int size = 1;
			boolean hasAnonymVar = false; // whether one variable in the scope has no specified owner
			for (String n : varNames) {
				hasAnonymVar = hasAnonymVar || (this.getOwner(n) == null);
				no++;
				variables_domain[no] = variablesHashMap.get(n);
				assert Math.log((double) size) + Math.log((double) variables_domain[no].length) < Math.log(Integer.MAX_VALUE) : 
					"Size of utility array too big for an int";
				size *= variables_domain[no].length;
			}

			// If required, ignore the constraint if its scope contains variables with unknown owners
			if (!getProbs && !withAnonymVars && hasAnonymVar) 
				return;

			// All information to create a hypercube is available.
			U[] utilArray = (U[]) Array.newInstance(utilClass, size);
			Hypercube<V, U> current = new Hypercube<V, U> (varNames, variables_domain, utilArray, infeasibleUtil, (this.countNCCCs && !this.ignore(Hypercube.class.getName()) ? this : null));
			current.setName(name);
			current.setRelationName(reference);
			current.setOwner(owner);

			if (relationInfo.defaultUtil != null) { // pre-fill the utility array with the default value
				Arrays.fill(utilArray, relationInfo.defaultUtil);
			}

			for (int i = 0; i < relationInfo.tuples.length; i++) {

				// adding one tuple by one with appropriate utility.
				current.setUtility(relationInfo.tuples[i], relationInfo.utilities[i]);

			}

			spaces.add(current);
		}
	}

	/** 
	 * @see DCOPProblemInterface#removeSpace(java.lang.String) 
	 * @todo Test this method. 
	 */
	public boolean removeSpace(String name) {
		
		Element consElmt = this.root.getChild("constraints");
		
		for (Iterator<Element> iter = consElmt.getChildren().iterator(); iter.hasNext(); ) {
			if (iter.next().getAttributeValue("name").equals(name)) {
				iter.remove();
				consElmt.setAttribute("nbConstraints", Integer.toString(consElmt.getContentSize()));
				return true;
			}
		}
		
		return false;
	}

	/** 
	 * @see DCOPProblemInterface#addSolutionSpace(UtilitySolutionSpace) 
	 * @todo Test this method. 
	 */
	public boolean addSolutionSpace (UtilitySolutionSpace<V, U> space) {
		
		// First check if the space's name is invalid or already taken
		String name = space.getName();
		if (name == null) 
			return false;
		for (Element elmt : (List<Element>) this.root.getChild("constraints").getChildren()) 
			if (elmt.getAttributeValue("name").equals(name)) 
				return false;
		
		// Construct the relation
		String relName = space.getName() + "_" + Integer.toHexString(new Object ().hashCode());
		Element elmt = getRelation(space, relName, "relation");
		
		// Record the relation
		Element elmt2 = this.root.getChild("relations");
		if (elmt2 == null) {
			elmt2 = new Element ("relations");
			this.root.addContent(elmt2);
		}
		elmt2.addContent(elmt);
		elmt2.setAttribute("nbRelations", Integer.toString(elmt2.getContentSize()));
		
		// Update the maxConstraintArity
		elmt2 = this.root.getChild("presentation");
		elmt2.setAttribute("maxConstraintArity", Integer.toString(Math.max(space.getNumberOfVariables(), Integer.parseInt(elmt2.getAttributeValue("maxConstraintArity")))));
		
		// Construct and record the constaint
		elmt = getConstraint(space, name, relName);
		elmt2 = this.root.getChild("constraints");
		elmt2.addContent(elmt);
		elmt2.setAttribute("nbConstraints", Integer.toString(elmt2.getContentSize()));
		
		return true;
	}

	/** @see DCOPProblemInterface#getAgents() */
	public Set<String> getAgents () {

		HashSet<String> agents = new HashSet<String> ();
		
		for (Element var : (List<Element>) root.getChild("agents").getChildren()) 
			agents.add(var.getAttributeValue("name"));

		return agents;
	}

	/** @see DCOPProblemInterface#getOwner(java.lang.String) */
	public String getOwner (String var) {

		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) 
			if (varElmt.getAttributeValue("name").equals(var)) 
				return varElmt.getAttributeValue("agent");

		// The variable was not found
		assert false : "Unknown variable '" + var + "'";
		return null;
	}

	/** @see DCOPProblemInterface#setOwner(java.lang.String, java.lang.String) */
	public boolean setOwner(String var, String owner) {
		
		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			if (varElmt.getAttributeValue("name").equals(var)) {
				varElmt.setAttribute("agent", owner);
				assert this.getAgents().contains(owner) : "Unknown agent " + owner;
				return true;
			}
		}
		
		return false;
	}

	/** @see DCOPProblemInterface#getOwners() */
	public Map<String, String> getOwners () {

		Map<String, String> out = new HashMap<String, String> (this.getNbrVars());

		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			String owner = varElmt.getAttributeValue("agent");
			if (owner != null) 
				out.put(varElmt.getAttributeValue("name"), owner);
		}

		return out;
	}

	/** Extracts the number of variables in the problem
	 * @return 	the number of variables in the problem
	 * @warning Ignores variables with no specified owner. 
	 */
	public int getNbrVars () {
		return this.getVariables().size();
	}

	/** Computes the number of variables owned by the input agent
	 * @param owner 	name of the agent
	 * @return 			the number of variables owned by \a owner
	 */
	public int getNbrVars (String owner) {

		int nbrVars = 0;
		for (Element var : (List<Element>) root.getChild("variables").getChildren()) 
			if (owner.equals(var.getAttributeValue("agent"))) 
				nbrVars++;
		return nbrVars;
	}

	/** @see DCOPProblemInterface#getNbrIntVars() */
	public int getNbrIntVars () {
		if (agentName != null) 
			return this.getNbrVars(agentName);
		return -1;
	}

	/** @see DCOPProblemInterface#getAllVars() */
	public Set<String> getAllVars() {
		HashSet<String> out = new HashSet<String> (this.getVariables());
		out.addAll(this.getAnonymVars());
		return out;
	}

	/** @see DCOPProblemInterface#getVariables() */
	public Set<String> getVariables () {

		Set<String> out = new HashSet<String> ();

		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) 
			if (! "random".equals(varElmt.getAttributeValue("type"))) // ignore random variables
				out.add(varElmt.getAttributeValue("name"));

		return out;
	}

	/** @see DCOPProblemInterface#getVariables(java.lang.String) */
	public Set<String> getVariables (String owner) {

		Set<String> out = new HashSet<String> ();

		if (owner != null) {
			for (Element var : (List<Element>) root.getChild("variables").getChildren()) 
				if (owner.equals(var.getAttributeValue("agent"))) 
					out.add(var.getAttributeValue("name"));

		} else 
			for (Element var : (List<Element>) root.getChild("variables").getChildren()) 
				if (var.getAttributeValue("agent") == null) 
					out.add(var.getAttributeValue("name"));

		return out;
	}

	/** @see DCOPProblemInterface#getMyVars() */
	public Set<String> getMyVars () {
		if (this.agentName == null) 
			return new HashSet<String> ();
		else 
			return this.getVariables(agentName);
	}

	/** @see DCOPProblemInterface#getExtVars() */
	public Set<String> getExtVars () {

		HashSet<String> out = new HashSet<String> ();
		
		if (this.agentName == null) 
			return out;

		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			String owner = varElmt.getAttributeValue("agent");
			if (owner != null && !owner.equals(agentName)) 
				out.add(varElmt.getAttributeValue("name"));
		}

		return out;
	}

	/** @see DCOPProblemInterface#getAnonymVars() */
	public Set<String> getAnonymVars () {
		return this.getVariables(null);
	}
	
	/** 
	 * @see DCOPProblemInterface#addVariable(java.lang.String, java.lang.String, java.lang.String) 
	 * @todo Test this method. 
	 */
	public boolean addVariable(String name, String owner, String domain) {
		
		// Check if this variable already exists
		if (this.getDomain(name) != null) 
			return false;
		
		// Check if there exists a domain with the given name
		for (Element domElmt : (List<Element>) root.getChild("domains").getChildren()) {
			if (domElmt.getAttributeValue("name").equals(domain)) {
				
				// Create the Element
				Element varElmt = new Element ("variable");
				varElmt.setAttribute("name", name);
				if (owner != null) {
					varElmt.setAttribute("agent", owner);
					assert this.getAgents().contains(owner) : "Adding a variable owned by an unknown agent is currently unsupported";
				}
				varElmt.setAttribute("domain", domain);
				
				// Add the Element
				Element varsElmt = this.root.getChild("variables");
				varsElmt.addContent(varElmt);
				varsElmt.setAttribute("nbVariables", Integer.toString(varsElmt.getContentSize()));
				
				return true;
			}
		}
		
		// No domain exists with the given name
		return false;
	}

	/** 
	 * @see DCOPProblemInterface#addVariable(java.lang.String, java.lang.String, V[]) 
	 * @todo Test this method. 
	 */
	public boolean addVariable(String name, String owner, V[] domain) {
		
		// Check if this variable already exists
		if (this.getDomain(name) != null) 
			return false;
		
		// First create a new domain
		Element domElmt = new Element ("domain");
		String domName = name + "_" + Integer.toHexString(new Object ().hashCode());
		domElmt.setAttribute("name", domName);
		domElmt.setAttribute("nbValues", Integer.toString(domain.length));
		StringBuilder builder = new StringBuilder ();
		for (V val : domain) 
			builder.append(val.toString() + " ");
		domElmt.setText(builder.toString());
		
		// Record the domain
		Element domsElmt = this.root.getChild("domains");
		domsElmt.addContent(domElmt);
		domsElmt.setAttribute("nbDomains", Integer.toString(domsElmt.getContentSize()));
		
		// Add the variable
		return this.addVariable(name, owner, domName);
	}

	/** Builds the subproblem description for a given agent by extracting it from the overall problem description
	 * @param agent 		the name of the agent
	 * @return 				the subproblem corresponding to \a agent, or \c null if \a agent owns no variable
	 */
	public XCSPparser<V, U> getSubProblem (String agent) {

		// Extract the set of variables owned by the agent
		HashSet<Element> varElmts = new HashSet<Element> ();
		for (Element var : (List<Element>) root.getChild("variables").getChildren()) 
			if (agent.equals(var.getAttributeValue("agent"))) 
				varElmts.add(var);

		// Create the XCSP instance element
		Element instance = new Element ("instance");

		// Create the "presentation" element
		Element presentation = new Element ("presentation");
		instance.addContent(presentation);
		presentation.setAttribute("name", agent);
		presentation.setAttribute("maximize", Boolean.toString(this.maximize()));
		presentation.setAttribute("format", "XCSP 2.1_FRODO");
		
		// Create the agents
		Element agents = new Element ("agents");
		instance.addContent(agents);
		HashSet<String> knownAgents = new HashSet<String> ();
		knownAgents.add(agent);
		if (this.mpc || this.publicAgents) // the agent is supposed to know all the agents
			knownAgents.addAll(this.getAgents());

		// Create the domains
		Element domains = new Element ("domains");
		instance.addContent(domains);

		// Create the variables
		Element variables = new Element ("variables");
		instance.addContent(variables);
		HashSet<String> varNames = new HashSet<String> (varElmts.size()); // internal variables and relevant external variables
		for (Element varElmt : varElmts) {
			varNames.add(varElmt.getAttributeValue("name"));
		}
		
		// In MPC mode, all variables are public
		if (this.mpc) 
			varElmts.addAll((List<Element>) root.getChild("variables").getChildren());

		// Create the constraints
		Element constraints = new Element ("constraints");
		HashSet<String> relationNames = new HashSet<String> ();
		HashSet<String> probNames = new HashSet<String> ();
		HashSet<String> constNames = new HashSet<String> ();

		// Go through the list of constraints several times until we are sure we have identified all variables that should be known to this agent
		HashMap< String, HashSet<String> > varScopes = new HashMap< String, HashSet<String> > ();
		int nbrVars;
		do {
			nbrVars = varNames.size();

			// Go through the list of all constraints in the overall problem
			for (Element constraint : (List<Element>) root.getChild("constraints").getChildren()) {

				// Skip this constraint if it has already been added
				String constName = constraint.getAttributeValue("name");
				if (constNames.contains(constName)) 
					continue;

				// Get the list of variables in the scope of the constraint
				HashSet<String> scope = new HashSet<String> (Arrays.asList(constraint.getAttributeValue("scope").split("\\s+")));

				// Check if this constraint is a probability space
				String refName = constraint.getAttributeValue("reference");
				Element probElmt = null;
				Element probsElmt = root.getChild("probabilities");
				if (probsElmt != null) {
					for (Element prob : (List<Element>) probsElmt.getChildren()) {
						if (prob.getAttributeValue("name").equals(refName)) {
							probElmt = prob;
							break;
						}
					}
				}
				
				// Check if the agent is not supposed to know the constraint
				String constOwner = constraint.getAttributeValue("agent");
				if (! "PUBLIC".equals(constOwner) && constOwner != null && ! constOwner.equals(agent)) {
					
					if (! this.mpc) { // record the variable scopes
						for (String var : scope) {
							HashSet<String> varScope = varScopes.get(var);
							if (varScope == null) {
								varScope = new HashSet<String> ();
								varScopes.put(var, varScope);
							}
							varScope.add(constOwner);
						}
					}
					
					continue;
				}

				// If any of the variables in the scope is owned by this agent or the constraint is a probability law that must be known to the agent, 
				// add the constraint to the list of constraints
				final boolean knownConst = "PUBLIC".equals(constOwner) || agent.equals(constOwner);
				for (String var : scope) {
					if (knownConst || varNames.contains(var)) {

						// Skip this variable if it is apparently not necessary for the agent to know this constraint
						if (probElmt != null) { // probability space
							if (! this.isRandom(var)) 
								continue;
							probNames.add(refName);
						}
						else { // solution space
							if (!this.extendedRandNeighborhoods && this.isRandom(var))
								continue;
							relationNames.add(refName);
						}

						constraints.addContent((Element) constraint.clone());
						constNames.add(constName);

						// Add all variables in the scope to the list of variables known to this agent
						for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
							String varName = varElmt.getAttributeValue("name");
							if (scope.contains(varName)) {
								varElmts.add(varElmt);
								if (varElmt.getAttributeValue("agent") == null) 
									varNames.add(varName);
							}
						}

						break;
					}
				}
			}
		} while (nbrVars != varNames.size()); // loop as long as another variable has been added to the list of known variables

		// Set the number of constraints
		constraints.setAttribute("nbConstraints", Integer.toString(constraints.getContentSize()));
		
		// Add the agents that own constraints over shared variables and my own variables
		constLoop: for (Element constraint : (List<Element>) root.getChild("constraints").getChildren()) {
			
			// Get the list of variables in the scope of the constraint
			HashSet<String> scope = new HashSet<String> (Arrays.asList(constraint.getAttributeValue("scope").split("\\s+")));
			
			// Skip this constraint if it is a probability space
			String refName = constraint.getAttributeValue("reference");
			Element probsElmt = root.getChild("probabilities");
			if (probsElmt != null) 
				for (Element prob : (List<Element>) probsElmt.getChildren()) 
					if (prob.getAttributeValue("name").equals(refName)) 
						continue constLoop;
			
			// Check whether the constraint owner should be known to the agent because the constraint scope involves a variable they share
			String constOwner = constraint.getAttributeValue("agent");
			if (! "PUBLIC".equals(constOwner) && constOwner != null && ! constOwner.equals(agent)) {
				for (String var : scope) {
					if (! this.isRandom(var) && varNames.contains(var)) { // skip random variables and unknown variables
						String varOwner = this.getOwner(var);
						if (varOwner == null || varOwner.equals(agent)) { // the variable is shared or owned by this agent
							knownAgents.add(constOwner);
							break;
						}
					}
				}
			}
		}

		// Add the domains of the variables
		HashSet<String> domNames = new HashSet<String> ();
		for (Element varElmt : varElmts) {

			String domName = varElmt.getAttributeValue("domain");
			if (! domNames.add(domName)) // domain already added to the list of domains
				continue;
			for (Element domain : (List<Element>) root.getChild("domains").getChildren()) {
				if (domName.equals(domain.getAttributeValue("name"))) {
					domains.addContent((Element) domain.clone());
					break;
				}
			}
		}

		// Set the number of domains
		domains.setAttribute("nbDomains", Integer.toString(domNames.size()));

		// Add all variables known to this agent
		variables.setAttribute("nbVariables", Integer.toString(varElmts.size()));
		for (Element varElmt : varElmts) {
			Element newVarElmt = (Element) varElmt.clone();
			variables.addContent(newVarElmt);
			
			// Check the owner of this variable
			String owner = varElmt.getAttributeValue("agent");
			if (owner != null) 
				knownAgents.add(owner);
			else if (! "random".equals(varElmt.getAttributeValue("type"))) { // shared variable; set its agent scope
				HashSet<String> varScope = varScopes.get(varElmt.getAttributeValue("name"));
				if (varScope != null) {
					String scope = "";
					for (String neigh : varScope) 
						scope += neigh + " ";
					newVarElmt.setAttribute("scope", scope);
				}
			}
		}
		
		// Fill in the list of agents
		agents.setAttribute("nbAgents", Integer.toString(knownAgents.size()));
		for (Element agentElmt : (List<Element>) this.root.getChild("agents").getChildren()) 
			if (knownAgents.contains(agentElmt.getAttributeValue("name"))) 
				agents.addContent((Element) agentElmt.clone());

		// Create the relations (if the original problem contained any)
		int maxConstraintArity = 0;
		if (this.root.getChild("relations") != null) {
			Element elmt = new Element ("relations");
			instance.addContent(elmt);
			
			// Go through the list of all relations in the overall problem
			for (Element relation : (List<Element>) root.getChild("relations").getChildren()) {
				
				// Add the relation to the list of relations if it is referred to by any of this agent's constraints
				if (relationNames.remove(relation.getAttributeValue("name"))) {
					elmt.addContent((Element) relation.clone());
					maxConstraintArity = Math.max(maxConstraintArity, Integer.parseInt(relation.getAttributeValue("arity")));
				}
			}
			elmt.setAttribute("nbRelations", Integer.toString(elmt.getContentSize()));
		}

		if (! relationNames.isEmpty()) 
			this.foundUndefinedRelations(relationNames);

		// Create the probabilities (if the original problem contained any)
		if (root.getChild("probabilities") != null) {
			Element elmt = new Element ("probabilities");
			instance.addContent(elmt);
			elmt.setAttribute("nbProbabilities", Integer.toString(probNames.size()));

			// Go through the list of all probabilities in the overall problem
			for (Element probability : (List<Element>) root.getChild("probabilities").getChildren()) {

				// Add the probability to the list of probabilities if it is referred to by any of this agent's constraints
				if (probNames.remove(probability.getAttributeValue("name"))) {
					elmt.addContent((Element) probability.clone());
					maxConstraintArity = Math.max(maxConstraintArity, Integer.parseInt(probability.getAttributeValue("arity")));
				}
			}
		}

		if (! probNames.isEmpty()) 
			System.err.println("Undefined probabilities: " + probNames);

		// Set the maxConstraintArity
		presentation.setAttribute("maxConstraintArity", Integer.toString(maxConstraintArity));

		// Add the "constraints" element after the "relations" and "probabilities" element
		instance.addContent(constraints);

		XCSPparser<V, U> out = newInstance (agent, instance);
		out.setUtilClass(utilClass);
		out.setDomClass(domClass);
		return out;
	}

	/** Prints out an error message
	 * @param relationNames 	names of the undefined relations
	 */
	protected void foundUndefinedRelations(HashSet<String> relationNames) {
		System.err.println("Undefined relations: " + relationNames);
	}

	/** Extracts the collection of neighbors of a given variable
	 * @param var 	the name of the variable
	 * @return 		a collection of neighbor variables of \a var
	 * @warning Ignores variables with no specified owner. 
	 */
	public HashSet<String> getNeighborVars (String var) {
		return this.getNeighborVars(var, false);
	}

	/** Extracts the collection of neighbors of a given variable
	 * @param var 				the name of the variable
	 * @param withAnonymVars 	if \c false, ignores variables with no specified owner
	 * @return 					a collection of neighbor variables of \a var
	 */
	public HashSet<String> getNeighborVars (String var, final boolean withAnonymVars) {

		HashSet<String> out = new HashSet<String> ();

		LinkedList<String> pending = new LinkedList<String> (); // variable(s) whose direct neighbors will be returned
		pending.add(var);
		HashSet<String> done = new HashSet<String> ();
		do {
			// Retrieve the next pending variable
			String var2 = pending.poll();
			if (! done.add(var2)) // we have already processed this variable
				continue;

			// Go through the list of constraint scopes
			for (Element constraint : (List<Element>) root.getChild("constraints").getChildren()) {

				// Check if var2 is in the scope
				String[] scope = constraint.getAttributeValue("scope").trim().split("\\s+");
				Arrays.sort(scope);
				if (Arrays.binarySearch(scope, var2) >= 0) {

					// Go through the list of variables in the scope
					for (String neighbor : scope) {

						// Check if the neighbor is random
						if (! this.isRandom(neighbor)) // not random
							out.add(neighbor);

						else { // the neighbor is random

							// Add it to the list of neighbors if we are interested in random neighbors
							if (withAnonymVars) 
								out.add(neighbor);

							// Later look for its own neighbors if we want extended neighborhoods
							if (this.extendedRandNeighborhoods) 
								pending.add(neighbor);
						}
					}
				}
			}
		} while (! pending.isEmpty());

		// Remove the variable itself from its list of neighbors
		out.remove(var);

		return out;
	}

	/** @see DCOPProblemInterface#getNbrNeighbors(java.lang.String) */
	public int getNbrNeighbors (String var) {
		return this.getNbrNeighbors(var, false);
	}

	/** Extracts the number of neighbors of an input variable
	 * @param var 				the variable
	 * @param withAnonymVars 	if \c false, ignores variables with no specified owner
	 * @return 					the number of neighbor variables of \a var
	 */
	public int getNbrNeighbors (String var, final boolean withAnonymVars) {
		return this.getNeighborVars(var, withAnonymVars).size();
	}

	/** Parses the problem description to construct, for each variable owned by the input agent, its list of neighbors
	 * @param agent 	the name of the agent
	 * @return 			for each of the agent's variables, its collection of neighbors
	 * @warning Ignores variables with no specified owner. 
	 */
	public Map< String, HashSet<String> > getNeighborhoods (String agent) {
		return this.getNeighborhoods(agent, false, false);
	}

	/** @see DCOPProblemInterface#getNeighborhoods() */
	public Map< String, HashSet<String> > getNeighborhoods () {
		return this.getNeighborhoods(this.agentName);
	}

	/** Parses the problem description to construct, for each variable owned by the input agent, its list of neighbors with no specified owner
	 * @param agent 	the name of the agent
	 * @return 			for each of the agent's variables, its collection of neighbors with no specified owner
	 */
	public Map< String, HashSet<String> > getAnonymNeighborhoods (String agent) {
		return this.getNeighborhoods(agent, true, true);
	}

	/** @see DCOPProblemInterface#getAnonymNeighborhoods() */
	public Map< String, HashSet<String> > getAnonymNeighborhoods () {
		return this.getAnonymNeighborhoods(agentName);
	}

	/** Parses the problem description to construct, for each variable owned by the input agent, its list of neighbors
	 * @param agent 			the name of the agent
	 * @param withAnonymVars 	if \c false, ignores variables with no specified owner
	 * @param onlyAnonymVars 	if \c true, only considers variables with no specified owner (in which case this superseeds \a withAnonymVars)
	 * @return 					for each of the agent's variables, its collection of neighbors
	 * 
	 * @todo Improve the performance by avoiding to call getNeighborVars on each variable, which requires parsing the constraints multiple times. 
	 */
	public Map< String, HashSet<String> > getNeighborhoods (String agent, final boolean withAnonymVars, final boolean onlyAnonymVars) {

		// For each variable that this agent owns, a collection of neighbor variables
		Map< String, HashSet<String> > neighborhoods = new HashMap< String, HashSet<String> > ();

		// Go through the list of variables owned by the input agent (or through all variables if the input agent is null)
		for (String var : (agent == null ? this.getVariables() : this.getVariables(agent))) {

			// Get the neighbors of this variable
			HashSet<String> neighbors = this.getNeighborVars(var, onlyAnonymVars || withAnonymVars);
			neighborhoods.put(var, neighbors);

			// Remove the non-anonymous variables if required
			if (onlyAnonymVars) 
				for (Iterator<String> iter = neighbors.iterator(); iter.hasNext(); ) 
					if (! this.isRandom(iter.next())) 
						iter.remove();
		}

		return neighborhoods;
	}

	/** Computes the number of neighboring variables of all variables owned by a given agent
	 * @param agent 	name of the agent
	 * @return 			for each variable owned by \a agent, its number of neighboring variables
	 * @warning Ignores variables with no specified owner. 
	 */
	public Map<String, Integer> getNeighborhoodSizes (String agent) {

		Map<String, Integer> out = new HashMap<String, Integer> ();

		// Go through the list of neighbors of each of the agent's variables
		for (Map.Entry< String, HashSet<String> > neighborhood : getNeighborhoods(agent).entrySet()) 
			out.put(neighborhood.getKey(), neighborhood.getValue().size());

		return out;
	}

	/** @see DCOPProblemInterface#getNeighborhoodSizes() */
	public Map<String, Integer> getNeighborhoodSizes () {
		return this.getNeighborhoodSizes(this.agentName);
	}

	/** Returns the neighboring agents of the input variable
	 * @param var 	the variable
	 * @return 		the variable's neighboring agents
	 */
	private HashSet<String> getAgentNeighbors (String var) {
		
		HashSet<String> out = new HashSet<String> ();

		LinkedList<String> pending = new LinkedList<String> (); // variable(s) whose direct agent neighbors will be returned
		pending.add(var);
		HashSet<String> done = new HashSet<String> ();
		do {
			// Retrieve the next pending variable
			String var2 = pending.poll();
			if (! done.add(var2)) // we have already processed this variable
				continue;
			
			// Go through the list of constraint scopes
			for (Element constraint : (List<Element>) root.getChild("constraints").getChildren()) {

				// Check if var2 is in the scope
				String[] scope = constraint.getAttributeValue("scope").trim().split("\\s+");
				Arrays.sort(scope);
				if (Arrays.binarySearch(scope, var2) >= 0) {

					// If the constraint has a specific owner, add it to the set of agents
					String consOwner = constraint.getAttributeValue("agent");
					if ("PUBLIC".equals(consOwner)) 
						consOwner = null;
					if (consOwner != null) 
						out.add(consOwner);
					
					// Go through the list of variables in the scope
					for (String neighbor : scope) {

						// Check if the neighbor is random
						if (! this.isRandom(neighbor)) { // not random
							String varOwner = this.getOwner(neighbor);
							if (varOwner != null) 
								out.add(varOwner);
						} else if (this.extendedRandNeighborhoods)
							pending.add(neighbor); // later look for this random neighbor's own neighbors
					}
				}
			}
		} while (! pending.isEmpty());
		
		// Add the variable's scope if present
		HashSet<String> scope = this.getScope(var);
		if (scope != null) 
			out.addAll(scope);

		// Remove the owner agent from the list of neighbors
		out.remove(this.getOwner(var));

		return out;
	}

	/** Returns the agent scope of the variable
	 * @param var 	the variable
	 * @return the agent scope of the variable
	 */
	private HashSet<String> getScope(String var) {
		
		for (Element varElmt : (List<Element>) this.root.getChild("variables").getChildren()) {
			if (varElmt.getAttributeValue("name").equals(var)) {
				String scope = varElmt.getAttributeValue("scope");
				if (scope == null) 
					return null;
				return new HashSet<String> (Arrays.asList(scope.split("\\s+")));
			}
		}
		
		return null;
	}

	/** @see DCOPProblemInterface#getAgentNeighborhoods(java.lang.String) */
	public Map< String, Collection<String> > getAgentNeighborhoods (String agent) {
		
		Set<String> vars = this.getVariables();
		Map< String, Collection<String> > out = new HashMap< String, Collection<String> > (vars.size());
		for (String var : vars) {
			String owner = this.getOwner(var);
			if (agent == null || agent.equals(owner) || owner == null && ! this.isRandom(var)) 
				out.put(var, this.getAgentNeighbors(var));
		}

		return out;
	}

	/** @see DCOPProblemInterface#getAgentNeighborhoods() */
	public Map< String, Collection<String> > getAgentNeighborhoods () {
		return this.getAgentNeighborhoods(agentName);
	}

	/** This method only makes sense in subclasses of XCSPparser that handle backyard variables
	 * @param var 	variable
	 * @return an empty map
	 */
	public Map< String, Collection<String> > getBackyardNeighborhood (String var) {
		return new HashMap< String, Collection<String> > ();
	}

	/** Extracts the size of the domain of the input variable
	 * @param var 	the variable
	 * @return 		the size of the domain of \a var
	 */
	public int getDomainSize (String var) {

		// Parse the name of the domain
		String domName = null;
		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			if (varElmt.getAttributeValue("name").equals(var)) {
				domName = varElmt.getAttributeValue("domain");
				break;
			}
		}
		if (domName == null) 
			return -1;

		// Parse the domain size
		for (Element domElmt : (List<Element>) root.getChild("domains").getChildren()) 
			if (domElmt.getAttributeValue("name").equals(domName)) 
				return Integer.parseInt(domElmt.getAttributeValue("nbValues"));

		// The domain is not defined
		System.err.println("The domain " + domName + " for variable " + var + " is not defined");
		return -1;
	}

	/** Extracts the domain of a given variable
	 * @param var 	the variable
	 * @return 		an array of domain values
	 */
	public V[] getDomain (String var) {

		// Parse the name of the domain
		String domName = null;
		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			if (varElmt.getAttributeValue("name").equals(var)) {
				domName = varElmt.getAttributeValue("domain");
				break;
			}
		}
		if (domName == null) 
			return null;

		// Parse the domain
		for (Element domElmt : (List<Element>) root.getChild("domains").getChildren()) 
			if (domElmt.getAttributeValue("name").equals(domName)) 
				return this.getDomain(domElmt, false);

		// The domain is not defined
		System.err.println("The domain " + domName + " for variable " + var + " is not defined");
		return null;
	}

	/** Parses and instantiates a domain from a domain Element
	 * @author Radoslaw Szymanek
	 * @param domainElmt 	the JDOM Element representing the domain
	 * @param debugLoad 	if \c true, prints out some debug information
	 * @return 				an array of domain values
	 */
	protected V[] getDomain (Element domainElmt, final boolean debugLoad) {

		String values = domainElmt.getText().trim();

		if (debugLoad)
			System.out.println("values " + values);

		Pattern pattern = Pattern.compile("\\s+");
		String[] intervals = pattern.split(values);

		ArrayList<V> dom = new ArrayList<V>();

		pattern = Pattern.compile("\\.\\.");

		for (String interval : intervals) {

			if (debugLoad)
				System.out.println("interval " + interval);

			if (interval.equals(""))
				continue;

			String[] parts = pattern.split(interval);

			// Domain element specified as a number
			if (parts.length == 1) 
				dom.add(this.valInstance.fromString(parts[0].trim()));


			// Domain elements specified as an interval
			if (parts.length == 2) {
				int min = Integer.valueOf(parts[0].trim());
				int max = Integer.valueOf(parts[1].trim());
				for (int i = min; i <= max; i++)
					dom.add(this.valInstance.fromString(Integer.toString(i)));
			}

		}
		
		assert dom.size() == Integer.parseInt(domainElmt.getAttributeValue("nbValues")) : 
			"Incorrect number of values in the domain " + domainElmt.getAttributeValue("name") + ": " + 
				dom.size() + " != " + Integer.parseInt(domainElmt.getAttributeValue("nbValues"));

		return dom.toArray((V[]) Array.newInstance(this.domClass, dom.size()));
	}

	/** Sets the domain of a variable in the problem
	 * 
	 * If the input domain has values that appear more than once, it is reduced so as to only contain each value once. 
	 * If the input variable is a random variable, the input domain is treated as a set of samples, and the probability law
	 * for the random variable is re-computed so as to reflect the frequency of each value in the sample set. 
	 * If the variable is unknown, it adds it to the problem, assuming it is a random variable. 
	 * @param var 	the name of the variable
	 * @param dom 	the domain; if empty, does nothing 
	 * @todo Test the case when the variable is unknown. 
	 */
	public void setDomain (String var, V[] dom) {

		// Return immediately if no samples are provided
		if (dom.length == 0) 
			return;

		// If dom contains several times the same value, it must be reduced.
		ArrayList<V> domReduced = new ArrayList<V> (dom.length);
		HashMap<V, Double> weights = new HashMap<V, Double> ();
		double weightIncr = 1.0 / dom.length;
		for (V val : dom) {
			Double w = weights.get(val);
			if (w != null) { // redundant value
				weights.put(val, w + weightIncr);
			} else { // first time we see this value
				weights.put(val, weightIncr);
				domReduced.add(val);
			}
		}

		this.setDomain(var, domReduced, weights);
	}

	/** @see DCOPProblemInterface#setProbSpace(java.lang.String, java.util.Map) 
	 * @warning This method assumes that the input variable is a random variable
	 * @todo Test this method. */
	public void setProbSpace(String var, Map<V, Double> prob) {

		// Extract the variable's domain while computing the sum of the weights
		ArrayList<V> domain = new ArrayList<V> (prob.size());
		Double norm = 0.0;
		for (Map.Entry<V, Double> entry : prob.entrySet()) {
			domain.add(entry.getKey());
			norm += entry.getValue();
		}

		// Renormalize the weights
		for (Map.Entry<V, Double> entry : prob.entrySet()) {
			entry.setValue(entry.getValue() / norm);
		}

		this.setDomain(var, domain, prob);
	}

	/** Sets the domain of a variable
	 * @param var 		the variable
	 * @param domain 	its new domain
	 * @param weights 	normalized weights for each value in the new domain (used only if the variable is random)
	 */
	private void setDomain (String var, ArrayList<V> domain, Map<V, Double> weights) {

		int nbrVals = domain.size();
		V[] dom = domain.toArray((V[]) Array.newInstance(domain.get(0).getClass(), nbrVals));
		Arrays.sort(dom);

		// Check if var is a random variable
		Boolean isRandom = null;
		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			if (var.equals(varElmt.getAttributeValue("name"))) {
				isRandom = new String ("random").equals(varElmt.getAttributeValue("type"));
				break;
			}
		}

		// If the variable is unknown, add it to the problem, treating it as a random variable
		if (isRandom == null) {
			Element varElmt = new Element ("variable");
			varElmt.setAttribute("name", var);
			varElmt.setAttribute("type", "random");

			Element varsElmt = this.root.getChild("variables");
			varsElmt.setAttribute("nbVariables", String.valueOf(Integer.parseInt(varsElmt.getAttributeValue("nbVariables")) + 1));
			varsElmt.addContent(varElmt);

			isRandom = true;
		}

		// If var is a random variable, its probability law must be updated accordingly. 
		if (isRandom) {

			// Find the constraint corresponding to the probability law for this random variable 
			Element element = null;
			find: for (Element constElmt : (List<Element>) this.root.getChild("constraints").getChildren()) {
				if (Arrays.asList(constElmt.getAttributeValue("scope").split("\\s+")).contains(var)) {

					// Check if this constraint corresponds to a probability law
					for (Element probElmt : (List<Element>) root.getChild("probabilities").getChildren()) {
						if (probElmt.getAttributeValue("name").equals(constElmt.getAttributeValue("reference"))) {

							element = constElmt;
							break find;
						}
					}
				}
			}

			// If the constraint was not found, create it
			if (element == null) {
				element = new Element ("constraint");
				element.setAttribute("name", var);
				element.setAttribute("arity", "1");
				element.setAttribute("scope", var);

				Element consElmt = this.root.getChild("constraints");
				consElmt.addContent(element);
				consElmt.setAttribute("nbConstraints", String.valueOf(Integer.parseInt(consElmt.getAttributeValue("nbConstraints")) + 1));
			}

			// Create a new XCSP probability description
			String[] vars = new String[] { var };

			V[][] doms = (V[][]) Array.newInstance(dom.getClass(), 1);
			doms[0] = dom;

			AddableReal[] utils = new AddableReal [nbrVals];
			for (int i = 0; i < nbrVals; i++) 
				utils[i] = new AddableReal (weights.get(dom[i]));

			String probName = "dynamic_prob_for_" + element.getAttributeValue("name");
			this.root.getChild("probabilities").addContent(getRelation(new Hypercube<V, AddableReal> (vars, doms, utils, null), probName, "probability"));

			// Set the constraint to refer to this new description
			element.setAttribute("reference", probName);
		}

		// Generate the domain description
		Element elmt = new Element ("domain");
		String domName = "dynamic_domain_of_" + var;
		elmt.setAttribute("name", domName);
		elmt.setAttribute("nbValues", String.valueOf(nbrVals));

		StringBuilder builder = new StringBuilder (dom[0].toString());
		for (int i = 1; i < dom.length; i++) 
			builder.append(" " + dom[i]);
		elmt.setText(builder.toString());

		Element domsElmt = this.root.getChild("domains");
		domsElmt.addContent(elmt);
		domsElmt.setAttribute("nbDomains", String.valueOf( Integer.parseInt(domsElmt.getAttributeValue("nbDomains")) + 1) );

		// Add the reference to this domain in the variable's description
		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) {
			if (var.equals(varElmt.getAttributeValue("name"))) {
				varElmt.setAttribute("domain", domName);
				break;
			}
		}

	}

	/** @see java.lang.Object#toString() */
	public String toString () {
		return "Problem for agent " + this.agentName + ":\n" + XCSPparser.toString(this.root);
	}

	/**@return a DOT-formatted reprensentation of the problem */
	public String toDOT () {
		return XCSPparser.toDOT(this.root);
	}

	/** Returns whether the input variable is defined as a random variable
	 * @param var 	the name of the variable
	 * @return 		\c true if the input variable is a random variable, \c false if not or if the variable is unknown
	 */
	public boolean isRandom (String var) {

		for (Element varElmt : (List<Element>) root.getChild("variables").getChildren()) 
			if (var.equals(varElmt.getAttributeValue("name"))) 
				return new String ("random").equals(varElmt.getAttributeValue("type"));

		// Variable not found
		return false;
	}

	/** Transforms the problem into one where some variables' values have been fixed
	 * @param vars 		the variables whoses values should be fixed
	 * @param values 	the values for the variables
	 * @return the transformed problem
	 * @warning Does not work if one of the contraints sees all its variables grounded. 
	 * @todo Test this method. 
	 */
	public Document groundVars (String[] vars, V[] values) {

		assert vars.length <= values.length : "Not enough values provided";

		HashMap<String, V> grounding = new HashMap<String, V> ();
		V[][] doms = (V[][]) Array.newInstance(this.valInstance.getClass(), new int[] {values.length, 1});
		for (int i = 0; i < vars.length; i++) {
			V val = values[i];
			grounding.put(vars[i], val);
			doms[i][0] = val;
		}

		// Create the XCSP instance element
		Element instance = new Element ("instance");

		// Create the "presentation" element
		Element presentation = new Element ("presentation");
		instance.addContent(presentation);
		presentation.setAttribute("name", "reduced_problem");
		presentation.setAttribute("maximize", Boolean.toString(this.maximize()));
		presentation.setAttribute("format", "XCSP 2.1_FRODO");

		// Create the "agents" element
		instance.addContent((Element) root.getChild("agents").clone());
		
		// Create the domains
		instance.addContent((Element) root.getChild("domains").clone());

		// Create the variables, ignoring the ones that are being grounded
		Element variables = new Element ("variables");
		instance.addContent(variables);
		int nbrVariables = 0;
		for (Element var : (List<Element>) root.getChild("variables").getChildren()) {
			if (!grounding.containsKey(var.getAttributeValue("name"))) { // this variable is not being grounded
				variables.addContent((Element) var.clone());
				nbrVariables++;
			}
		}
		variables.setAttribute("nbVariables", String.valueOf(nbrVariables));

		// Parse all the constraints in the original problem into hypercubes
		List< ? extends UtilitySolutionSpace<V, U> > hypercubes = this.getSolutionSpaces(true);

		// Create the relations
		Element relations = new Element ("relations");
		instance.addContent(relations);
		relations.setAttribute("nbRelations", Integer.toString(hypercubes.size()));

		// Create the constraints
		Element constraints = new Element ("constraints");
		instance.addContent(constraints);
		constraints.setAttribute("nbConstraints", Integer.toString(hypercubes.size()));

		// Slice all hypercubes, grounding variables as required
		int constraintID = -1;
		for (UtilitySolutionSpace<V, U> hypercube : hypercubes) {
			constraintID++;
			String id = String.valueOf(constraintID);

			UtilitySolutionSpace<V, U> slice = hypercube.slice(vars, doms);
			relations.addContent(getRelation(slice, id, "relation"));
			constraints.addContent(getConstraint(slice, id, id));

		}

		return new Document (instance);
	}

	/** Generates the XCSP "relation" or "probability" fragment corresponding to the input hypercube
	 * @param <V> 			the type used for variable values
	 * @param hypercube 	the hypercube
	 * @param id 			id of the relation/probability
	 * @param nature 		either "relation" or "probability"
	 * @return the XCSP "relation" or "probability" fragment corresponding to the input hypercube
	 */
	public static < V extends Addable<V> > Element getRelation (UtilitySolutionSpace< V, ? extends Addable<?> > hypercube, String id, String nature) {

		int nbrVars = hypercube.getNumberOfVariables();
		assert hypercube.getNumberOfSolutions() < Integer.MAX_VALUE : "A relation can only contain up to 2^31-1 solutions";
		int nbrUtils = (int) hypercube.getNumberOfSolutions();
		assert nbrUtils > 0;

		Element out = new Element (nature);
		out.setAttribute("name", id);
		out.setAttribute("arity", Integer.toString(nbrVars));
		out.setAttribute("nbTuples", Integer.toString(nbrUtils));
		out.setAttribute("semantics", "soft");

		// Iterate through all utility values
		StringBuilder builder = new StringBuilder ("\n");
		for (UtilitySolutionSpace.Iterator< V, ? extends Addable<?> > iter = hypercube.iterator(); ; ) {
			
			// Write the utility
			builder.append(iter.nextUtility().toString());
			builder.append(":");
			
			// Write the assignment
			for (V val : iter.getCurrentSolution()) 
				builder.append(val.toString() + " ");
			
			if (iter.hasNext()) 
				builder.append("|");
			else 
				break;
		}
		out.addContent(builder.toString());

		return out;
	}

	/** Creates an XCSP-formated description of this contraint
	 * @param hypercube 	the constraint
	 * @param id 			the name of the constraint
	 * @param ref 			the name of the relation
	 * @return the corresponding constraint description
	 */
	public static Element getConstraint (UtilitySolutionSpace<?, ?> hypercube, String id, String ref) {

		Element elmt = new Element ("constraint");
		elmt.setAttribute("name", id);
		elmt.setAttribute("arity", String.valueOf(hypercube.getNumberOfVariables()));

		// Generate the scope
		String scope = "";
		for (String var : hypercube.getVariables()) 
			scope += var + " ";
		elmt.setAttribute("scope", scope.trim());

		elmt.setAttribute("reference", ref);

		return elmt;
	}

	/** 
	 * @see frodo2.solutionSpaces.DCOPProblemInterface#getUtility(java.util.Map) 
	 * @todo Test this method with incomplete assignments. 
	 */
	public UtilitySolutionSpace<V, U> getUtility (Map<String, V> assignments) {
		return this.getUtility(assignments, false);
	}

	/** 
	 * @see DCOPProblemInterface#getUtility(Map, boolean) 
	 * @todo Test this method 
	 */
	public UtilitySolutionSpace<V, U> getUtility (Map<String, V> assignments, final boolean withAnonymVars) {

		UtilitySolutionSpace<V, U> output = null;

		// Extract all hypercubes
		List< ? extends UtilitySolutionSpace<V, U> > hypercubes = this.getSolutionSpaces(withAnonymVars);

		// Go through the list of hypercubes
		for (UtilitySolutionSpace<V, U> hypercube : hypercubes) {

			// Slice the hypercube over the input assignments
			ArrayList<String> vars = new ArrayList<String> (hypercube.getNumberOfVariables());
			for (String var : hypercube.getVariables()) 
				if (assignments.containsKey(var)) 
					vars.add(var);
			int nbrVars = vars.size();
			V[] values = (V[]) Array.newInstance(this.valInstance.getClass(), nbrVars);
			for (int i = 0; i < nbrVars; i++) 
				values[i] = assignments.get(vars.get(i));
			UtilitySolutionSpace<V, U> slice = hypercube.slice(vars.toArray(new String[nbrVars]), values);

			// Join the slice with the output
			output = (output == null ? slice : output.join(slice));
		}

		return (output != null ? output : 
			new ScalarHypercube<V, U> (this.getZeroUtility(), this.getInfeasibleUtil(), (Class<V[]>) Array.newInstance(this.domClass, 0).getClass()));
	}

	/** 
	 * @see DCOPProblemInterface#getExpectedUtility(Map) 
	 * @todo Test this method.
	 */
	public UtilitySolutionSpace<V, U> getExpectedUtility (Map<String, V> assignments) {

		// Compute the utility, as a function of the random variables
		UtilitySolutionSpace<V, U> util = (UtilitySolutionSpace<V, U>) this.getUtility(assignments, true);

		// Compute the expectation over the random variables 
		HashMap< String, UtilitySolutionSpace<V, U> > distributions = 
			new HashMap< String, UtilitySolutionSpace<V, U> > ();
		for (UtilitySolutionSpace<V, U> probSpace : this.getProbabilitySpaces()) 
			distributions.put(probSpace.getVariable(0), probSpace);
		if (! distributions.isEmpty()) 
			util = util.expectation(distributions);
		
		return util;
	}

	/** 
	 * @see DCOPProblemInterface#getParamUtility(java.util.Map) 
	 * @todo Test this method.
	 */
	public UtilitySolutionSpace<V, U> getParamUtility (Map< String[], BasicUtilitySolutionSpace< V, ArrayList<V> > > assignments) {

		Class<? extends V[]> classOfDom = (Class<? extends V[]>) Array.newInstance(assignments.values().iterator().next().getUtility(0).get(0).getClass(), 0).getClass();
		U zero = this.getZeroUtility();
		UtilitySolutionSpace<V, U> output = new ScalarHypercube<V, U> (zero, this.getInfeasibleUtil(), classOfDom);

		// Extract all hypercubes
		List< ? extends UtilitySolutionSpace<V, U> > hypercubes = this.getSolutionSpaces(true);

		// Go through the list of hypercubes
		for (UtilitySolutionSpace<V, U> hypercube : hypercubes) {

			// Compose the hypercube with each input assignment
			UtilitySolutionSpace<V, U> composition = hypercube;
			for (Map.Entry< String[], BasicUtilitySolutionSpace< V, ArrayList<V> > > entry : assignments.entrySet()) 
				composition = composition.compose(entry.getKey(), entry.getValue());

			// Join the composition with the output
			output = output.join(composition);
		}

		return output;
	}

	/** @see DCOPProblemInterface#maximize() */
	public boolean maximize() {
		String maximize = this.root.getChild("presentation").getAttributeValue("maximize");
		if (maximize == null) // by default, minimize
			return false;
		return Boolean.parseBoolean(maximize);
	}

	/** @see DCOPProblemInterface#setMaximize(boolean) */
	public void setMaximize(boolean maximize) {
		this.root.getChild("presentation").setAttribute("maximize", Boolean.toString(maximize));
	}

	/** @see DCOPProblemInterface#rescale(Addable, Addable) */
	public void rescale(U multiply, U add) {
		
		// Modify each relation
		for (Element relElmt : (List<Element>) this.root.getChild("relations").getChildren()) {
			
			// Take care of the default cost
			String defaultCost = relElmt.getAttributeValue("defaultCost");
			if (defaultCost != null) 
				relElmt.setAttribute("defaultCost", multiply.fromString(defaultCost.trim()).multiply(multiply).add(add).toString());
			
			// Take care of each utility/cost in the list of tuples
			StringBuilder builder = new StringBuilder ();
			for (Iterator<String> iter = Arrays.asList(relElmt.getText().split("\\|")).iterator(); iter.hasNext(); ) {
				
				String tuple = iter.next();
				String[] split = tuple.split(":");
				assert split.length > 0 && split.length <= 2 : "Incorrect tuple format: " + tuple;
				if (split.length > 1) // there is a utility specified
					builder.append(multiply.fromString(split[0].trim()).multiply(multiply).add(add) + ":" + split[1]);
				else 
					builder.append(split[0]);
				
				if (iter.hasNext()) 
					builder.append("|");
			}
			relElmt.setText(builder.toString());
		}
	}

	/**
	 * Change the problem from a maximisation problem to a minimisation problem
	 *  
	 * @author Brammert Ottens, 9 sep 2009
	 * @param shiftInt the amount with which to shift all numbers to ensure all numbers having the same sign
	 * @return A Document with the same problem, but now represented as a minimisation problem
	 * @todo Compute the shift instead of taking it as an argument. 
	 */
	public Document switchMaxMin(int shiftInt) {

		String maximize = root.getChild("presentation").getAttributeValue("maximize");
		assert maximize.equals("true");
		root.getChild("presentation").setAttribute("maximize", "false");

		// Create the method to parse a utility from a String
		Method utilFromString;
		try {
			utilFromString = utilClass.getMethod("fromString", String.class);
		} catch (NoSuchMethodException e) {
			System.err.println("Failed accessing the fromString() method for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}
		U utilInstance;
		try {
			utilInstance = utilClass.newInstance();
		} catch (InstantiationException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the nullary constructor for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}

		U shift;
		try {
			shift = (U) utilFromString.invoke(utilInstance, Integer.toString(shiftInt));
			if(maximize.equals("false"))
				shift = shift.flipSign();
		} catch (IllegalArgumentException e) {
			System.err.println("Failed calling the fromString() method for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			System.err.println("Failed calling the fromString() method for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		} catch (InvocationTargetException e) {
			System.err.println("Failed calling the fromString() method for the class " + utilClass.getName() + " used for utility values");
			e.printStackTrace();
			return null;
		}

		org.jdom2.Element relations = root.getChild("relations");

		if (relations != null) {

			for (org.jdom2.Element relation : (List<org.jdom2.Element>) relations.getChildren()) {

				String semantics = relation.getAttributeValue("semantics");
				int nbTuples = Integer.valueOf(relation.getAttributeValue("nbTuples"));
				String defaultCost = relation.getAttributeValue("defaultCost");

				try {
					if (defaultCost != null) { 
						U defaultUtil = (U) utilFromString.invoke(utilInstance, defaultCost);
						defaultUtil = defaultUtil.flipSign().add(shift);
						relation.setAttribute("defaultCost", defaultUtil.toString());
					}

					// XCSP can have support and conflicts semantics too, these are of no 
					// use in Hypercubes/DPOP context so they are ignored.
					if (!semantics.equals("soft"))
						continue;

					if (nbTuples == 0 && defaultCost == null)
						continue;

					String tuplesString = relation.getText();

					String[] relationTuples = new String[nbTuples];
					Arrays.fill(relationTuples, "");
					U[] utility = (U[]) Array.newInstance(utilClass, nbTuples);

					// Get current utility of 
					Pattern pattern = Pattern.compile("\\|");
					String[] tuples = pattern.split(tuplesString);

					Pattern patternColon = Pattern.compile(":");
					pattern = Pattern.compile("\\s+");

					U currentUtility = null;
					int counter = -1;
					for (int i = 0; i < nbTuples; i++) {

						if (tuples[i].contains(":")) {
							counter++;
							String[] pair = patternColon.split(tuples[i]);
							tuples[i] = pair[1];
							currentUtility = (U) utilFromString.invoke(utilInstance, pair[0].trim());
							utility[counter] = currentUtility;
						}

						String[] vals = pattern.split(tuples[i].trim());

						for (String value : vals) 
							relationTuples[counter] += value + " ";

						if (i < nbTuples - 1) 
							relationTuples[counter] += "|";
					}

					// stores for each relation name its list of tuples and utility for
					// each tuple.
					String newTuples = "";
					for(int i = 0; i <= counter; i++) {
						newTuples += utility[i].flipSign().add(shift).toString() + ":" + relationTuples[i];
					}
					relation.setText(newTuples);
				} catch (IllegalArgumentException e) {
					System.err.println("Failed calling the fromString() method for the class " + utilClass.getName() + " used for utility values");
					e.printStackTrace();
					return null;
				} catch (IllegalAccessException e) {
					System.err.println("Failed calling the fromString() method for the class " + utilClass.getName() + " used for utility values");
					e.printStackTrace();
					return null;
				} catch (InvocationTargetException e) {
					System.err.println("Failed calling the fromString() method for the class " + utilClass.getName() + " used for utility values");
					e.printStackTrace();
					return null;
				}
			}
		}

		root.detach();
		return new Document(root);
	}

	/** @see DCOPProblemInterface#reset(ProblemInterface) */
	public void reset(ProblemInterface<V, U> newProblem) {

		assert newProblem instanceof XCSPparser : "Cannot reset an XCSPparser based on a problem of class: " + newProblem.getClass();

	XCSPparser<V, U> prob = (XCSPparser<V, U>) newProblem;
	this.agentName = prob.agentName;
	this.root = prob.root;
	this.utilClass = prob.utilClass;
	this.valInstance = prob.valInstance;
	}

	/** 
	 * @see DCOPProblemInterface#getNumberOfCoordinationConstraints()
	 * @todo This implementation is very inefficient; it should not need to parse the spaces
	 */
	public int getNumberOfCoordinationConstraints() {
		List<? extends UtilitySolutionSpace<V, U>> spaces = this.getSolutionSpaces();
		Map<String, String> owners = this.getOwners();
		int counter = 0;

		for(UtilitySolutionSpace<V, U> space : spaces) {
			String[] variables = space.getVariables();
			if(variables.length > 0) {
				String owner = owners.get(variables[0]);
				if(owner != null) {
					int i = 1;
					while(i < variables.length && owner.equals(owners.get(variables[i]))){i++;}

					if(i != variables.length)
						counter++;
				}
			}
		}

		return counter;
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#multipleTypes()
	 */
	public boolean multipleTypes() {
		return false;
	}

	/**
	 * @author Brammert Ottens, 7 feb. 2011
	 * @param spaceClass the space class for which we want to know whether ncccs should be ignored or not
	 * @return \c true when ncccs should be ignored, and \c false otherwise
	 */
	protected boolean ignore(String spaceClass) {
		return this.spacesToIgnoreNcccs.contains(spaceClass);
	}

}
