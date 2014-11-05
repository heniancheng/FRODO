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
import java.util.TreeSet;

import frodo2.benchmarks.auctions.cats.Auction;
import frodo2.benchmarks.auctions.cats.Bid;
import frodo2.benchmarks.auctions.cats.Bidder;
import frodo2.benchmarks.auctions.cats.Good;

import org.jdom2.Document;
import org.jdom2.Element;

/**
 * This class is used to generate the 'instance' tag in the output XML file.
 * 
 * @author Andreas Schaedeli
 */
public class Instance extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = -3048277817601121098L;

	/**Constructor calls the super class constructor with the name for the XML tag*/
	public Instance() {
		super("instance");

	}

	/**
	 * This method builds the content of the "instance" tag. This tag contains an attribute and some child elements.
	 * 
	 * @param auction 			Auction to be transformed into a DCOP
	 * @param methodID 			ID of the conversion method to be used
	 * @param discsp 			whether bid prices should be ignored, and the output should be a pure DisCSP instance
	 * @param sumDecomposition 	<b>true</b> if sum constraints should be intensional instead of extensional
	 * @param intensional 		whether all constraints should be intensional
	 * @return Document object that can be written as XML formatted file
	 */
	public Document create(Auction auction, int methodID, boolean discsp, boolean sumDecomposition, boolean intensional) {

		//Adding the attribute for the name space
		if (sumDecomposition || intensional) 
			this.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschemaJaCoP.xsd", 
					org.jdom2.Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
		else 
			this.setAttribute("noNamespaceSchemaLocation", "src/frodo2/algorithms/XCSPschema.xsd", 
					org.jdom2.Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));

		//Initialize sub-elements
		Presentation presentation = new Presentation();
		Domains domains = new Domains();
		Variables variables = new Variables();
		Relations relations = new Relations();
		Constraints constraints = new Constraints(variables.getVariables());

		//Create sub-elements
		presentation.create(auction, methodID, discsp);
		domains.create();
		variables.create(auction, methodID);
		relations.create(auction, methodID, discsp, sumDecomposition, intensional);
		constraints.create(auction, methodID, discsp, sumDecomposition, intensional);
		
		// Declare the agents
		TreeSet<String> agentNames = new TreeSet<String> ();
		for (Variable var : variables.getVariables()) {
			String owner = var.getOwner();
			if (owner != null) 
				agentNames.add(owner);
		}
		for (Constraint constraint : constraints.getConstraints()) {
			String owner = constraint.getOwner();
			if (owner != null && ! owner.equals("PUBLIC")) 
				agentNames.add(owner);
		}
		Element agents = new Element ("agents");
		agents.setAttribute("nbAgents", Integer.toString(agentNames.size()));
		for (String agentName : agentNames) {
			Element agent = new Element ("agent");
			agents.addContent(agent);
			agent.setAttribute("name", agentName);
		}
		
		Element predicates = null;
		if (intensional && methodID == 3) { // Create the binary equality predicate
			
			predicates = new Element ("predicates");
			predicates.setAttribute("nbPredicates", "1");
			
			Element predicate = new Element ("predicate");
			predicates.addContent(predicate);
			predicate.setAttribute("name", "EQUALITY_BINARY");
			
			Element parameters = new Element ("parameters");
			predicate.addContent(parameters);
			parameters.setText("int X int Y");
			
			Element expression = new Element ("expression");
			predicate.addContent(expression);
			
			Element functional = new Element ("functional");
			expression.addContent(functional);
			functional.setText("eq(X, Y)");
		}
		
		//Add sub-elements
		addContent(presentation);
		addContent(agents);
		addContent(domains);
		addContent(variables);
		if (predicates != null) 
			this.addContent(predicates);
		addContent(relations);
		addContent(constraints);

		return new Document(this);
	}

	/**
	 * This method finds the maximum arity of any constraints in this DCOP. This number depends on the conversion method, as each method creates 
	 * different constraints.
	 * 
	 * @param auction Auction instance
	 * @param method ID of conversion method to be applied
	 * @return Maximum constraint arity
	 */
	public static int findMaxConstraintArity(Auction auction, int method) {
		int max = 0;
		
		switch(method) {
		
		//For the first and second methods, there are constraints over all the bids for a specific good, and over all the bids from a specific bidder. 
		//We therefore search for the maximum number of bids in one constraint
		case 1:
		case 2:
			List<Good> goods = auction.getGoods();
			List<Bidder> bidders = auction.getBidders();
			
			//Examining constraints over all bids containing the specified good; iteration over all goods
			for(Good good : goods) {
				max = Math.max(max, good.getBidsList().size());
			}
			
			//Examining constraints over all bids placed by the specified bidder; iteration over all bidders
			for(Bidder bidder : bidders) {
				max = Math.max(max, bidder.getBidsList().size());
			}
			break;

		//There are SUM constraints between all the variables held by an auctioneer, which are as many as there are different bidders competing for
		//the auctioneer's good. Therefore, we need to find the maximum number of bidders competing for one good. Furthermore, there is a utility constraint
		//for each bidder, that contains as many variables as there are different goods across all its bids
		case 3:
		case 6:
			for(Good good : auction.getGoods()) {
				max = Math.max(max, Instance.getBiddersScopeByGood(good).size());
			}
			for(Bidder bidder : auction.getBidders()) {
				max = Math.max(max, getGoodsScopeByBidder(bidder).size());
			}
			break;

		// Same as Method 3, except that each bidder pretends to be interested in each and every good
		case 4: 
		case 5:
			max = Math.max(max, auction.getBidders().size());
			for(Bidder bidder : auction.getBidders()) 
				max = Math.max(max, getGoodsScopeByBidder(bidder).size());
			break;
			
		}
		return max;
	}
	
	/**
	 * @param bidder Bidder instance
	 * @return List of IDs of all the goods that are contained in at least one bid placed by the given bidder
	 */
	public static List<Integer> getGoodsScopeByBidder(Bidder bidder) {
		List<Integer> goodsList = new ArrayList<Integer>();
		
		//Iteration over all the bids placed by the bidder
		for(Bid bid : bidder.getBidsList()) {
			
			//Iteration over all the goods contained in the given bid
			for(Good good : bid.getGoodsList()) {
				if(!goodsList.contains(good.getGoodID())) {
					goodsList.add(good.getGoodID());
				}
			}
		}
		return goodsList;
	}
	
	/**
	 * @param good Good instance
	 * @return List of IDs of all the bidders having placed at least one bid containing the given good
	 */
	public static List<Integer> getBiddersScopeByGood(Good good) {
		List<Integer> biddersList = new ArrayList<Integer>();
		
		//Iteration over all the bids containing the good
		for(Bid bid : good.getBidsList()) {
			if(!biddersList.contains(bid.getBidder().getBidderID())) {
				biddersList.add(bid.getBidder().getBidderID());
			}
		}
		return biddersList;
	}
}
