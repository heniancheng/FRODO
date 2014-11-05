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
 * This class is used to generate 'constraint' tags in the output XML file
 * 
 * @author Andreas Schaedeli, Thomas Leaute
 *
 */
public class Constraint extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = -486029672066351846L;
	
	
	/**Name of the constraint*/
	private String name;
	
	/**Arity of the constraint, i.e. number of variables over which the constraint spans*/
	private String arity;
	
	/**Scope of the constraint, i.e. names of the variables over which the constraint spans*/
	private String scope;
	
	/**Name of the relation applied to this constraint*/
	private String reference;
	
	/** The owner agent, if any */
	private String owner;

	
	/**
	 * The constructor calls the super class constructor to define the name of the tag this class represents, and assigns the instance variables.
	 * 
	 * @param name Name of the constraint
	 * @param arity Arity of the constraint
	 * @param scope Scope of the constraint
	 * @param reference Name of the relation applied to this constraint
	 */
	public Constraint(String name, String arity, String scope, String reference) {
		this (name, arity, scope, reference, null);
	}

	/**
	 * The constructor calls the super class constructor to define the name of the tag this class represents, and assigns the instance variables.
	 * 
	 * @param name 			Name of the constraint
	 * @param arity 		Arity of the constraint
	 * @param scope 		Scope of the constraint
	 * @param reference 	Name of the relation applied to this constraint
	 * @param owner 		The owner agent, if any
	 */
	public Constraint(String name, String arity, String scope, String reference, String owner) {
		super("constraint");
		this.name = name;
		this.arity = arity;
		this.scope = scope;
		this.reference = reference;
		this.owner = owner;
	}
	
	/** @return the owner agent, if any */
	public String getOwner () {
		return this.owner;
	}

	/**
	 * This method adds all the instance variables as attributes, so they will be included in the 'constraint' XML element
	 * 
	 * @param sumDecomposition <b>true</b> if sum constraints should be intensional instead of extensional
	 */
	public void create(boolean sumDecomposition) {
		setAttribute("name", name);
		if (this.owner != null) 
			this.setAttribute("agent", this.owner);
		setAttribute("arity", arity);
		setAttribute("scope", scope);
		setAttribute("reference", reference);
		
		if(sumDecomposition && this.reference.equalsIgnoreCase("global:weightedSum")) {
			SumParameters sumParams = new SumParameters(scope.trim().split(" "));
			sumParams.create(reference.contains("XOR"));
			addContent(sumParams);
		}
	}

}
