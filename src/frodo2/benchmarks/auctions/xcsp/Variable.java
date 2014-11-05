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

import org.jdom2.Element;

/**
 * This class is used to generate the 'variable' tags in the output XML file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Variable extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = -4102365588799441715L;
	
	
	/**Name of the variable*/
	private String name;
	
	/**Domain of the variable*/
	private String domain;
	
	/**Owner of the variable*/
	private String owner;

	
	/**
	 * This method adds the instance variables as attributes, so they are written to the 'variable' element in the XML output file.
	 * 
	 * @param name Name of the variable
	 * @param domain Domain of the variable
	 * @param owner Owner of the variable
	 */
	public Variable(String name, String domain, String owner) {
		super("variable");
		this.name = name;
		this.domain = domain;
		this.owner = owner;
	}

	/**
	 * This method adds the instance variables as attributes to the Element, so they will be written to the 'variable' tag in the XML output file
	 */
	public void create() {
		setAttribute("name", name);
		setAttribute("domain", domain);
		if (this.owner != null) 
			setAttribute("agent", owner);
	}
	
	/**
	 * @return Name of the variable
	 */
	public String getVarName() {
		return name;
	}
	
	/** @return the owner agent */
	public String getOwner () {
		return this.owner;
	}
}
