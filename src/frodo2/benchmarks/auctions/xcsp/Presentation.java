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

import frodo2.benchmarks.auctions.cats.Auction;

import org.jdom2.Element;

/**
 * This class is used to generate the 'presentation' tag of the output XML file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Presentation extends Element {
	
	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 8686394181981201398L;

	
	/**
	 * The constructor calls the super class constructor to define the name of the tag this class represents.
	 */
	public Presentation() {
		super("presentation");
	}

	/**
	 * This method adds the attributes name, maxConstraintArity, maximize and format to the element, so they will be written into the 'presentation' XML tag
	 * 
	 * @param auction 	Auction instance
	 * @param methodID 	ID of conversion method to be used
	 * @param discsp 	whether this is a pure satisfaction problem
	 */
	public void create(Auction auction, int methodID, boolean discsp) {
		String title = "Combinatorial Auction Problem";
		String maxArity = "" + Instance.findMaxConstraintArity(auction, methodID);
		String maximize = (discsp ? "false" : "true");
		String format = "XCSP 2.1_FRODO";
		
		setAttribute("name", title);
		setAttribute("maxConstraintArity", maxArity);
		setAttribute("maximize", maximize);
		setAttribute("format", format);
	}
}
