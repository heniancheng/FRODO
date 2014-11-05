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
 * This class is used to generate the 'domain' tags in the output XML file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Domain extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 8501853959023776972L;
	
	
	/**Name of the domain*/
	private String name;
	
	/**Lower bound of the domain*/
	private int lowerBound;
	
	/**Upper bound of the domain*/
	private int upperBound;

	
	/**
	 * The constructor first calls the super class constructor to define the name of the tag this class represents. Furthermore, it assigns values to
	 * all the instance variables
	 * 
	 * @param name Name of the domain
	 * @param lowerBound Lower bound of the domain
	 * @param upperBound Upper bound of the domain
	 */
	public Domain(String name, int lowerBound, int upperBound) {
		super("domain");
		this.name = name;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	/**
	 * This method adds the instance variables as attributes, so they will appear in the 'domain' XML tag. 
	 * The domain is represented by lowerBound..upperBound, e.g. 2..5
	 */
	public void create() {
		setAttribute("name", name);
		setAttribute("nbValues", "" + (upperBound - lowerBound + 1));
		addContent("" + lowerBound + ".." + upperBound);
	}

}
