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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import frodo2.benchmarks.auctions.cats.Auction;
import frodo2.benchmarks.auctions.cats.Bid;
import frodo2.benchmarks.auctions.cats.Bidder;
import frodo2.benchmarks.auctions.cats.Good;

import org.jdom2.Element;

/**
 * This class is used to generate the 'relations' tag in the XML output file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Relations extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 7080885614966779031L;


	/**List of relations*/
	List<Relation> relations;


	/**
	 * The constructor first calls the super class constructor to define the name of the tag this class represents, then initializes an empty list of relations
	 */
	public Relations() {
		super("relations");
		relations = new ArrayList<Relation>();
	}

	/**
	 * This method creates the relations of this DCOP. The number and type of the relations depend on the method, therefore a switch is needed. The generated
	 * relations are then added as content to this element, so they will be written inside the 'relations' tag. The number of relations is added as
	 * a simple attribute
	 * 
	 * @param auction 			Auction instance
	 * @param methodID 			ID of conversion method to be applied
	 * @param discsp 			whether bid prices should be ignored, and the output should be a pure DisCSP instance
	 * @param sumDecomposition 	<b>true</b> if sum constraints should be intensional instead of extensional
	 * @param intensional 		whether all constraints should be intensional
	 */
	public void create(Auction auction, int methodID, boolean discsp, boolean sumDecomposition, boolean intensional) {
		
		String infinity = (discsp ? "infinity" : "-infinity");
		
		switch(methodID) {

		//There are first of all binary equality relations between each copy of the variables held by the bidders and their corresponding copies held by
		//the auctioneers. Notice that there is no break at the end of case 2 and 3, as the remaining constraints are the same as for method 1
		case 2:
		case 3:

			if (!intensional) 
				relations.add(new Relation("EQUALITY_BINARY", "2", infinity, createConstantUtilityMapping(0, Arrays.asList("0 0", "1 1"))));

		case 1:
		case 6:

			/*For all methods, we need to have SUMn relations, where n is the maximum constraint arity. So a SUM relation is created for all numbers of values
			 *from 2 to n. Constraint name is SUMn, and specific assignments are 00...0, 10...0, 01...0 to 00...1, with the corresponding utility of 0. 
			 *In the pure DisCSP case, we also need XORn relations, which are SUMn relations except that the 00...0 tuple is forbidden. 
			 *Furthermore, there is a relation for each bid, with default utility zero, and utility = price if the variable is equal to 1 for methods 1 and 2.
			 *For method 3, the utility relation is different; for each "real" bidder, there is a non-negative utility for all combinations of variables = 1 that
			 *correspond to one of his bids. The fake bidder (with a utility < 0) represents the reserve price of a good. If there is such a bidder the corresponding good
			 *can only have XORn relations (it sells the good or not).*/

			//Generate SUM relations
			int maxArity = Instance.findMaxConstraintArity(auction, methodID);
			if(!sumDecomposition && !intensional) {
				for(int i = 1; i <= maxArity; i++) {
					relations.add(new Relation("SUM" + i, "" + i, infinity, createConstantUtilityMapping(0, createSUMAssignments(i, false))));
				}
				if(discsp && (methodID == 2 || methodID == 3 || methodID == 6)){
					for(int i = 1; i <= maxArity; i++) {
						relations.add(new Relation("XOR" + i, "" + i, "infinity", createConstantUtilityMapping(0, createSUMAssignments(i, true))));
					}
				}
			}

			if(methodID == 1 || methodID == 2) {
				if (discsp) // generate XOR relations
					for(int i = 1; i <= maxArity; i++) 
						relations.add(new Relation("XOR" + i, "" + i, "infinity", createConstantUtilityMapping(0, createSUMAssignments(i, true)))); /// @todo Replace XOR with SUM=1

				else // generate unary relations for utility if variable = 1
					for(Bid bid : auction.getBids()) {
						if(methodID == 2 && bid.getPrice() < 0){// if it is a fake bidder, should pay when it doesn't win the good
							relations.add(new Relation("Bid_" + bid.getBidID() + "_Utility_Rel", "1", "0", createConstantUtilityMapping(bid.getPrice(), Arrays.asList("0"))));
						}else{
							relations.add(new Relation("Bid_" + bid.getBidID() + "_Utility_Rel", "1", "0", createConstantUtilityMapping(bid.getPrice(), Arrays.asList("1"))));
						}
					}
			}
			else { // methods 3 and 6
				List<Integer> goodsScope;
				for(Bidder bidder : auction.getBidders()) {
					goodsScope = Instance.getGoodsScopeByBidder(bidder);

					relations.add(new Relation("Bidder_" + bidder.getBidderID() + "_Utility_Rel", "" + goodsScope.size(), 
							infinity, createUtilityMappingByBidder(bidder, goodsScope, discsp), discsp && intensional ? "supports" : "soft"));
				}
			}

			break;

		case 4:
		case 5:
			if(!sumDecomposition && !intensional) {
				
				// For each good, one SUM constraint over all bidders
				int nbrBidders = auction.getBidders().size();
				this.relations.add(new Relation ("SUM" + nbrBidders, Integer.toString(nbrBidders), 
						"-infinity", createConstantUtilityMapping(0, createSUMAssignments(nbrBidders, false))));
			}

			// For each bidder, one n-ary utility constraint over the goods she wants
			List<Integer> goodsScope;
			for(Bidder bidder : auction.getBidders()) {
				goodsScope = Instance.getGoodsScopeByBidder(bidder);
				relations.add(new Relation("Bidder_" + bidder.getBidderID() + "_Utility_Rel", "" + goodsScope.size(), 
						"-infinity", createUtilityMappingByBidder(bidder, goodsScope, discsp), discsp ? "supports" : "soft"));
			}
			break;
		}

		//Create relations (i.e. generate the content of their XML tags) and add them as content to this Element
		for(Relation relation : relations) {
			relation.create();
			addContent(relation);
		}

		setAttribute("nbRelations", "" + relations.size());
	}

	/**
	 * @return List of generated relations
	 */
	public List<Relation> getRelations() {
		return relations;
	}

	/**
	 * This method constructs a list of strings that represent all assignments of the specified number of variables where the SUM relation has 
	 * utility 0, i.e. all assignments where the sum of all variable values is <= 1
	 * 
	 * @param nbVariables 	Number of variables between which the SUM relation should be created
	 * @param xor 			if \c true, the tuple full of zero is forbidden
	 * @return List of Strings containing all accepted assignments
	 */
	private List<String> createSUMAssignments(int nbVariables, boolean xor) {

		List<String> assignments = new ArrayList<String>(nbVariables + 1);
		StringBuilder builder;

		//Iteration over all accepted assignments, which are nbVariables (+ 1 if !xor)
		for(int i = (xor ? 1 : 0); i <= nbVariables; i++) {
			builder = new StringBuilder();
			//A 1 is added in the i-th position, all other variables should be 0. For i = 0, the 00...0 String is generated
			for(int j = 1; j <= nbVariables; j++) {
				builder.append(i == j ? "1 " : "0 ");
			}
			//Trim is called as there should be no trailing white-spaces
			assignments.add(builder.toString().trim());
		}
		return assignments;
	}

	/**
	 * @param utility Constant utility for all assignments
	 * @param assignments List of variable Assignments yielding the same utility
	 * @return Mapping from constant utility to assignments
	 */
	private Map<Double, List<String>> createConstantUtilityMapping(double utility, List<String> assignments) {
		Map<Double, List<String>> utilitiesToAssignments = new HashMap<Double, List<String>>();
		utilitiesToAssignments.put(utility, assignments);
		return utilitiesToAssignments;
	}

	/**
	 * This method creates a mapping from utilities to the corresponding variable assignments. For each bid, the utility is rewarded if and only if each variable
	 * standing for a good contained in the bid is 1, and all the others are 0 (a bidder wants to win only one of his bids). A first exception is the all 0 string;
	 * in this case, the reward is 0. The second exception is the fake bids; in this case, the utility is rewarded if and only if each variable is 0 (it does not win the bid = 
	 * the good is sold).
	 * 
	 * @param bidder 		Bidder instance
	 * @param goodsScope 	List of IDs of all the goods the bidder has placed a bid for
	 * @param discsp 		whether bid prices should be ignored, and the output should be a pure DisCSP instance
	 * @return Mapping from utilities to corresponding assignments; usually one utility to one assignment per bid
	 */
	private Map<Double, List<String>> createUtilityMappingByBidder(Bidder bidder, List<Integer> goodsScope, final boolean discsp) {
		Map<Double, List<String>> utilitiesToAssignments = new HashMap<Double, List<String>>();
		List<String> assignments = new ArrayList<String>();
		List<Integer> goodsPerBid;
		StringBuilder builder = new StringBuilder();

		if (!discsp) {
			//Create the mapping from utility 0 to the all zero string
			for(int i = 0; i < goodsScope.size(); i++) {
				builder.append("0 ");
			}
			assignments.add(builder.toString().trim());			
			if(bidder.getBidsList().get(0).getPrice() < 0){//if it is a fake bidder
				utilitiesToAssignments.put(bidder.getBidsList().get(0).getPrice(), new ArrayList<String>(assignments));
			}else{
				utilitiesToAssignments.put(0.0, new ArrayList<String>(assignments));
			}
		}

		//Create the mapping from each bid's utility to the corresponding assignment of variables
		for(Bid bid : bidder.getBidsList()) {

			// Retrieve the list of already existing assignments for this price (if any)
			Double price = (discsp ? 0.0 : bid.getPrice());
			if(price < 0)//for reserve price
				price = 0.0; 
			assignments = utilitiesToAssignments.get(price);
			if (assignments == null) { // first bid with this price
				assignments = new ArrayList<String> ();
				utilitiesToAssignments.put(price, assignments);
			}

			builder = new StringBuilder();
			goodsPerBid = getGoodIDsForBid(bid);

			//Adds a 1 to the string if the good with ID goodID is contained in the given bid, else 0
			for(Integer goodID : goodsScope) {
				builder.append(goodsPerBid.contains(goodID) ? "1 " : "0 ");
			}
			assignments.add(builder.toString().trim());
		}

		return utilitiesToAssignments;
	}

	/**
	 * @param bid Bid instance
	 * @return List of IDs of goods contained in the given bid
	 */
	private List<Integer> getGoodIDsForBid(Bid bid) {
		List<Integer> goodIDsForBid = new ArrayList<Integer>();

		for(Good good : bid.getGoodsList()) {
			goodIDsForBid.add(good.getGoodID());
		}

		return goodIDsForBid;
	}

}
