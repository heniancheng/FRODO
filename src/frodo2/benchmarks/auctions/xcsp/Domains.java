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

import java.util.ArrayList;
import java.util.List;

import org.jdom2.Element;


/**
 * This class is used to generate the 'domains' tag in the output XML file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Domains extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 1205259034174446052L;
	
	
	/**List of domains*/
	private List<Domain> domains;
	
	/**
	 * The constructor calls the super class constructor to define the name of the tag this class represents, and initializes an empty list for the domains
	 */
	public Domains() {
		super("domains");
		domains = new ArrayList<Domain>();
	}

	/**
	 * This method creates the domains of this DCOP's variables. For the moment, there is only the binary domain [0, 1], so no parameters are needed.
	 */
	public void create() {
		domains.add(new Domain("binary", 0, 1));
		
		setAttribute("nbDomains", "" + domains.size());
		
		for(Domain domain : domains) {
			domain.create();
			addContent(domain);
		}
	}

}
