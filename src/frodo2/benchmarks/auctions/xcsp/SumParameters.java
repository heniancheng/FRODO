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
 * This class is used to represent the parameters of an intensional sum constraint
 * 
 * @author Andreas Schaedeli
 */
public class SumParameters extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 4306488561297146973L;
	
	/**Array of variable names contained in the sum constraint*/
	private String[] variables;
	
	/**
	 * Constructor calling the superclass' constructor to add the correct tag name to the output XCSP file
	 * 
	 * @param variables Array of variable names
	 */
	public SumParameters(String[] variables) {
		super("parameters");
		this.variables = variables;
	}
	
	/**
	 * This method adds the content to the parameters XML tag
	 * @param isXOR true if the reference of the constraint contains XOR (and not SUM) i.e. there is a fake bidder
	 */
	public void create(final boolean isXOR) {
		addContent(createParameters());
		if(isXOR)
			addContent(new Equal());
		else
			addContent(new LessEqual());
		addContent("1");
	}
	
	/**
	 * @return String containing a list of weights and variable names
	 */
	private String createParameters() {
		StringBuilder paramBuilder = new StringBuilder();
		paramBuilder.append("[");
		for(String variable : variables) {
			paramBuilder.append(" { 1 " + variable + " }");
		}
		paramBuilder.append(" ]");
		return paramBuilder.toString();
	}
	
	/**
	 * This internal class is needed to create the <b>le</b> XML tag; else, characters > and < are transformed into &gt and &lt
	 * 
	 * @author Andreas Schaedeli
	 */
	private class LessEqual extends Element {
		
		/**Classes extending Element should declare a serial Version UID*/
		private static final long serialVersionUID = -4503351280001861157L;

		/**
		 * Constructor calling the superclass constructor in order to create the le XML tag in the XCSP output file
		 */
		public LessEqual() {
			super("le");
		}
	}

	

	/**
	 * This internal class is needed to create the <b>eq</b> XML tag
	 * 
	 * @author Cecile Grometto
	 */
	private class Equal extends Element {

		/**Classes extending Element should declare a serial Version UID*/
		private static final long serialVersionUID = -4503351280003861157L;

		/**
		 * Constructor calling the superclass constructor in order to create the eq XML tag in the XCSP output file
		 */
		public Equal() {
			super("eq");
		}
	}

}

