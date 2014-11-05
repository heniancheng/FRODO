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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;

import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.MASProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;

/** A MASproblem parser that is able to handle multiple types of agents
 * @author Brammert Ottens, 8 jun 2010
 * @param <U> the type used for decision values
 * @param <V> the type used for utility values
 */
@SuppressWarnings("unchecked")
public class MASparser <V extends Addable<V>, U extends Addable<U>> implements MASProblemInterface<V, U> {

	/** Used for serialization */
	private static final long serialVersionUID = 5407324017599034300L;

	/** the JDOM root element*/
	protected Element root;
	
	/** the name of the agent owning the problem, if applicable */
	private String agentName;
	
	/** the type of the agent owning this problem */
	protected String type;
	
	/** The class to be used for variable values */
	protected Class<V> domClass = (Class<V>) AddableInteger.class;
	
	/** The class to be used for utility values */
	protected Class<U> utilClass = (Class<U>) AddableInteger.class;
	
	/** Whether to count constraint checks */
	protected final boolean countNCCCs;
	
	/** a set of spaces for which ncccs should be ignored */
	protected HashSet<String> spacesToIgnoreNcccs;
	
	/** The NCCC count */
	private long ncccCount;
	
	/** Creates a JDOM Document out of the input XML file
	 * @param file 					the XML file
	 * @return 						a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 		if a parsing error occurs while reading the file
	 * @throws IOException 			if an I/O error occurs while accessing the file
	 * @author Thomas Leaute
	 */
	public static Document parse (File file) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
		builder.setFeature("http://apache.org/xml/features/xinclude", true);
		return builder.build(file);
	}
	
	/** Creates a JDOM Document out of the input XML file path in XCSP format
	 * @param path 					the XML file path
	 * @return 						a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 		if a parsing error occurs while reading the file
	 * @throws IOException 			if an I/O error occurs while accessing the file
	 */
	public static Document parse (String path) throws JDOMException, IOException {
		return parse(new File (path));
	}
	
	/** Creates a JDOM Document out of the input XML stream in XCSP format
	 * @param stream 				the XML stream
	 * @return 						a JDOM Document resulting from the parsing of the input file
	 * @throws JDOMException 		if a parsing error occurs while reading the file
	 * @throws IOException 			if an I/O error occurs while accessing the file
	 * @author Thomas Leaute
	 */
	public static Document parse (InputStream stream) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
		builder.setFeature("http://apache.org/xml/features/xinclude", true);
		return builder.build(stream);
	}
	
	/** Constructor from a JDOM root Element
	 * @param root 	the JDOM root Element
	 */
	public MASparser(Element root) {
		this.root = root;
		this.countNCCCs = false;
		this.spacesToIgnoreNcccs = new HashSet<String>();
	}
	
	/** Constructor from a JDOM root Element
	 * @param root 	the JDOM root Element
	 * @param countNCCCs \c true when NCCCs should be counted
	 */
	public MASparser(Element root, boolean countNCCCs) {
		this.root = root;
		this.countNCCCs = countNCCCs;
		this.spacesToIgnoreNcccs = new HashSet<String>();
	}

	/** Constructor from a JDOM root Element
	 * @param agentName 					the name of the agent owning the input subproblem
	 * @param type							the type of the agent
	 * @param root 							the JDOM root Element
	 * @param countNCCCs					\c true when NCCCs should be counted
	 * @param spacesToIgnoreNcccs 			the list of spaces who's NCCCs should not be counted
	 */
	public MASparser(String agentName, String type, Element root, boolean countNCCCs, HashSet<String> spacesToIgnoreNcccs) {
		this.root = root;
		this.type = type;
		this.countNCCCs = countNCCCs;
		this.agentName = agentName;
		this.spacesToIgnoreNcccs = spacesToIgnoreNcccs;
	}
	
	/** Constructor from a JDOM root Element
	 * @param agentName 					the name of the agent owning the input subproblem
	 * @param type							the type of the agent
	 * @param root 							the JDOM root Element
	 * @param countNCCCs					\c true when NCCCs should be counted
	 */
	public MASparser(String agentName, String type, Element root, boolean countNCCCs) {
		this.root = root;
		this.type = type;
		this.countNCCCs = countNCCCs;
		this.agentName = agentName;
		this.spacesToIgnoreNcccs = new HashSet<String>();
	}
	
	/** Constructor from a JDOM Document
	 * @param doc 	the JDOM Document
	 */
	public MASparser(Document doc) {
		this(doc.getRootElement());
	}
	
	/** Constructor 
	 * @param doc 	the JDOM Document
	 * @param params 	the parameters of the parser
	 */
	public MASparser(Document doc, Element params) {
		this.root = doc.getRootElement();
		
		// Parse the class of U
		String utilClassName = params.getAttributeValue("utilClass");
		if (utilClassName == null) 
			this.setUtilClass((Class<U>) AddableInteger.class);
		else {
			try {
				this.setUtilClass((Class<U>) Class.forName(utilClassName));
			} catch (ClassNotFoundException ex) {
				ex.printStackTrace();
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
	
	/** 
	 * @see frodo2.solutionSpaces.MASProblemInterface#getType()
	 */
	public String getType() {
		return type;
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#getAgent()
	 */
	public String getAgent() {
		return this.agentName;
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#getAgents()
	 */
	public Set<String> getAgents() {
		
		List<Element> agentElmts = (List<Element>)root.getChildren("agent");
		Set<String> agents = new HashSet<String>(agentElmts.size());
		for(Element e : agentElmts)
			agents.add(e.getAttributeValue("name"));
		return agents;
	}

	/** @see frodo2.solutionSpaces.ProblemInterface#getZeroUtility() */
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
	
	/** @see frodo2.solutionSpaces.ProblemInterface#getPlusInfUtility() */
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
	
	/** @see frodo2.solutionSpaces.ProblemInterface#getMinInfUtility() */
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
	
	
	/**
	 * @author Brammert Ottens, 10 jun 2010
	 * @return the local problem of the agent
	 * @warning should only be called by agents!
	 */
	public Element getLocalProblem() {
		assert root.getChildren().size() == 1;
		return root.getChild("agent").getChild("problem");
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#getSubProblem(java.lang.String)
	 */
	public MASparser<V, U> getSubProblem(String agent) {
		
		// extract the problem owned by the agent
		Element subProb = null;
		for(Element e : (List<Element>)root.getChildren())
			if(e.getAttributeValue("name").equals(agent)) {
				subProb = e;
				break;
			}
		
		String agentType = subProb.getAttributeValue("type");
		
		Element masProblem = new Element("MASproblem");
		masProblem.setAttribute("numberOfAgents", "1");
		subProb.detach();
		masProblem.addContent(subProb);
		
		return new MASparser<V, U>(agent, agentType, masProblem, this.countNCCCs, this.spacesToIgnoreNcccs);
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#multipleTypes()
	 */
	public boolean multipleTypes() {
		return true;
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#reset(frodo2.solutionSpaces.ProblemInterface)
	 */
	public void reset(ProblemInterface<V, U> newProblem) {
		// @todo Auto-generated method stub
		
	}

	/** @see ProblemInterface#setDomClass(java.lang.Class) */
	public void setDomClass(Class<V> domClass) {
		this.domClass = domClass;
	}

	/** @see ProblemInterface#getDomClass() */
	@Override
	public Class<V> getDomClass() {
		return this.domClass;
	}

	/** 
	 * @see frodo2.solutionSpaces.ProblemInterface#setUtilClass(java.lang.Class)
	 */
	public void setUtilClass(Class<U> utilClass) {
		this.utilClass = utilClass;		
	}
	
	/** @see MASProblemInterface#utilInstance() */
	public U utilInstance() {
		try {
			return utilClass.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	/** @see ProblemInterface#incrNCCCs(long) */
	public void incrNCCCs (long incr) {
		if (this.countNCCCs)
			this.ncccCount += incr;
	}
	
	/** @see ProblemInterface#setNCCCs(long) */
	public void setNCCCs (long ncccs) {
		if (this.countNCCCs)
			this.ncccCount = ncccs;
	}
	
	/** @see ProblemInterface#getNCCCs() */
	public long getNCCCs () {
		return this.ncccCount;
	}
}
