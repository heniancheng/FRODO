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

import frodo2.benchmarks.auctions.cats.Auction;
import frodo2.benchmarks.auctions.cats.Bid;
import frodo2.benchmarks.auctions.cats.Bidder;
import frodo2.benchmarks.auctions.cats.Good;

import org.jdom2.Element;

/**
 * This class is used to generate the 'constraints' tag in the output XML file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Constraints extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = 3312765494020333074L;

	/**List containing the constraints of this DCOP*/
	private List<Constraint> constraints;

	/**List containing the variables of this DCOP*/
	private List<Variable> variables;


	/**
	 * The constructor first calls the super class constructor to define the name of the tag this class represents. Then the constraints List 
	 * is initialized and the variables list is assigned to the instance variable
	 * 
	 * @param variables List of this DCOP's variables
	 */
	public Constraints(List<Variable> variables) {
		super("constraints");
		this.constraints = new ArrayList<Constraint>();
		this.variables = variables;
	}
	
	/** @return the constraints */
	public List<Constraint> getConstraints () {
		return this.constraints;
	}

	/**
	 * This method creates the constraints for this DCOP. This can be done in three different manners, depending on the method that was chosen at program start.
	 * 
	 * @param auction 			Auction to be transformed to a DCOP
	 * @param methodID 			Method to be used for transformation to a DCOP
	 * @param discsp 			whether bid prices should be ignored, and the output should be a pure DisCSP instance
	 * @param sumDecomposition 	<b>true</b> if sum constraints should be intensional instead of extensional
	 * @param intensional 		whether all constraints should be intensional
	 */
	public void create(Auction auction, int methodID, final boolean discsp, final boolean sumDecomposition, boolean intensional) {

		switch(methodID) {

		//In this case, there is one variable per bid, and constraints have to be added for the bid's utilities, for bids containing the same goods,
		//and for bids placed by the same bidder
		case 1: {

			//Creation of constraints for the bids' utilities: When a bid wins, it is worth a reward specified in the Bid object, else 0
			if (!discsp) {
				int bidID;
				for(Bid bid : auction.getBids()) {
					bidID = bid.getBidID();
					constraints.add(new Constraint("Bid_" + bidID + "_Utility", "1", variables.get(bidID).getVarName(), "Bid_" + bidID + "_Utility_Rel"));
				}
			}

			//Creation of constraints between bids for the same good; a good can only be won by one bid, so the sum of the variables of the scope has to be at most 1.
			//We use either extensional or intensional sum constraints.
			int arity;
			String sumReference;
			for(Good good : auction.getGoods()) {
				arity = good.getBidsList().size();
				if(arity > 1) {
					sumReference = (sumDecomposition || intensional ? "global:weightedSum" : "SUM" + arity);
					constraints.add(new Constraint("g" + good.getGoodID() + "capacity", "" + arity, 
							getScope(getVarNamesFromBids(good.getBidsList())), sumReference));
				}
			}

			//Creation of constraints between bids placed by the same bidder; the bidder wants to win only one of them, so the sum of the variables of the scope has to be at most 1.
			//We use either extensional or intensional sum constraints.
			String suffix = (discsp ? "bid" : "budget");
			for(Bidder bidder : auction.getBidders()) {
				arity = bidder.getBidsList().size();
				if(arity > 1) {
					sumReference = (discsp ? "XOR" + arity : (sumDecomposition || intensional ? "global:weightedSum" : "SUM" + arity)); /// @todo Replace XOR with SUM=1
					constraints.add(new Constraint("B" + bidder.getBidderID() + suffix, "" + arity, 
							getScope(getVarNamesFromBids(bidder.getBidsList())), sumReference));
				}
			}
			break;
		}

		//In this case, sum constraints are created between all variables held by a given bidder and between all variables held by a given auctioneer.
		//Furthermore, binary equality constraints are created for each bid, expressing that each auctioneer's copy of the variable for this bid equals the bidder's copy. 
		//Finally, one constraint per bid is needed to indicate the utility it yields when the bid wins; this constraint is expressed over the bidder's copy of the variable. 
		case 2: {

			int bidID;
			int arity;
			String sumReference;

			//Iteration over all bids to add unary constraints and n EQUALITY_BINARY constraints
			for(Bid bid : auction.getBids()) {
				bidID = bid.getBidID();

				//Add unary constraint for bid
				if (!discsp) 
					constraints.add(new Constraint("Bid_" + bidID + "_Utility", "1", "B" + bid.getBidder().getBidderID() + "b" + bidID, "Bid_" + bidID + "_Utility_Rel"));

				//Add n binary equality constraints between the copy of the variable held by the bidder placing the given bid and the copies of the variable
				//held by each auctioneer whose good is contained in the bid
				addEqualityConstraintsOnBids(bid, intensional);
			}

			//Iteration over all goods to add extensional or intensional sum constraints between bids containing the same good
			for(Good good : auction.getGoods()) {
				arity = good.getBidsList().size();

				//SUM1 is useless, as it's always OK (0|1)
				if(arity > 1) {
					if(good.getReservePrice() == 0){
						sumReference = (sumDecomposition || intensional ? "global:weightedSum" : "SUM" + arity);
					}else{//if it has a reserve price, need a XOR relation (the good is sold or not)
						sumReference = (sumDecomposition || intensional ? "global:weightedSum" : "XOR" + arity);
					}
					constraints.add(new Constraint("g" + good.getGoodID() + "capacity", "" + arity, 
							getScope(getVarNamesFromBids(good.getBidsList(), "A" + good.getGoodID())), sumReference));
				}
			}

			//Iteration over all bidders to add extensional or intensional sum constraints between bids placed by the same bidder
			String suffix = (discsp ? "bid" : "budget");
			for(Bidder bidder : auction.getBidders()) {
				arity = bidder.getBidsList().size();
				if(arity > 1) {
					if(bidder.isFake()){//if it is a fake bidder
						sumReference = (discsp ? "XOR" + arity : (sumDecomposition || intensional ? "global:weightedSum" : "XOR" + arity)); 
					}else{
						sumReference = (discsp ? "XOR" + arity : (sumDecomposition || intensional ? "global:weightedSum" : "SUM" + arity)); /// @todo Replace XOR with SUM=1
					}
					constraints.add(new Constraint("B" + bidder.getBidderID() + suffix, "" + arity, 
							getScope(getVarNamesFromBids(bidder.getBidsList(), "B" + bidder.getBidderID())), sumReference));
				}
			}
			break;
		}

		/* In this case, extensional or intensional sum constraints are needed for all the variables held by a given auctioneer (his good can only be won by one bidder). Furthermore,
		 * equality constraints are needed between each variable (representing a good) held by a bidder and its corresponding copy held by the auctioneer.
		 * Finally, utility constraints (with implicit sum<=1 over all the bids placed by a given bidder) are created, which represent the following:
		 * "Utility x is rewarded if bidder y wins bid z". This constraint is represented by assigning the value x to an assignment of the variables where
		 * all variables representing goods contained in the bid are 1 and all others are 0.*/
		case 3: 
		case 6: {
			List<Integer> biddersScope;
			List<Integer> goodsScope;
			int arity;
			String sumReference;

			//Iteration over all the goods to add extensional or intensional sum constraints over all the variables held by each auctioneer
			for(Good good : auction.getGoods()) {
				biddersScope = Instance.getBiddersScopeByGood(good);
				arity = biddersScope.size();
				if (arity == 0) 
					continue;

				if(good.getReservePrice() == 0){
					sumReference = (sumDecomposition || intensional ? "global:weightedSum" : "SUM" + arity);
				}else{ //if there is a reserve price
					sumReference = (sumDecomposition || intensional ? "global:weightedSum" : "XOR" + arity);	
				}
				constraints.add(new Constraint("g" + good.getGoodID() + "capacity", "" + arity, 
						getScope(getVarNamesFromGood(good, biddersScope, methodID)), sumReference, "Auctioneer_" + good.getGoodID()));
			}

			//Iteration over all the bidders to add equality and utility constraints
			for(Bidder bidder : auction.getBidders()) {

				if (methodID == 3) //Adding equality constraints between variables held by each bidder and the corresponding copy held by the auctioneer
					addEqualityConstraintsOnGoods(bidder, intensional);

				//Adding utility constraints for each bidder
				goodsScope = Instance.getGoodsScopeByBidder(bidder);
				constraints.add(new Constraint("Bidder_" + bidder.getBidderID() + "_Utility", "" + goodsScope.size(),
						getScope(getVarNamesFromBidder(bidder, goodsScope, methodID)), "Bidder_" + bidder.getBidderID() + "_Utility_Rel", "Bidder_" + bidder.getBidderID()));
			}
			break;
		}

		// Same as case 3, except that sum constraints are over ALL bidders
		case 4: 
		case 5: {
			// Generate the list of all bidder IDs
			int nbrBidders = auction.getBidders().size();
			List<Integer> biddersScope = new ArrayList<Integer> (nbrBidders);
			for (Bidder bidder : auction.getBidders()) 
				biddersScope.add(bidder.getBidderID());

			//Iteration over all bidders to add utility constraints
			List<Integer> goodsScope;
			for(Bidder bidder : auction.getBidders()) {
				goodsScope = Instance.getGoodsScopeByBidder(bidder);
				constraints.add(new Constraint("Bidder_" + bidder.getBidderID() + "_Utility", "" + goodsScope.size(),
						getScope(getVarNamesFromBidder(bidder, goodsScope, methodID)), "Bidder_" + bidder.getBidderID() + "_Utility_Rel", 
						(methodID == 5 ? "Bidder_" + bidder.getBidderID() : null)));
			}

			// For each good, there is a sum constraint over all corresponding variables hold by each bidder (method 4) or auctioneer (method 5)
			String sumReference = (sumDecomposition || intensional ? "global:weightedSum" : "SUM" + nbrBidders);
			String arity = Integer.toString(nbrBidders);
			for(Good good : auction.getGoods()) {
				if (methodID == 5 ? good.getBidsList().size() > 0 : good.getBidsList().size() > 1) {
					Constraint constraint = new Constraint("g" + good.getGoodID() + "capacity", arity, 
							getScope(getVarNamesFromGood(good, biddersScope, methodID)), sumReference, 
							(methodID == 5 ? "PUBLIC" : null));
					constraints.add(constraint);
				}
			}
			break;
		}
		}

		//Call to constraint.create() creates the content of the <constraint> tags; all constraints are added as sub-elements to the <constraints> element
		for(Constraint constraint : constraints) {
			constraint.create(sumDecomposition || intensional);
			addContent(constraint);
		}

		setAttribute("nbConstraints", "" + constraints.size());
	}

	/**
	 * This method returns the scope of a constraint in String representation. The variable names in the input list are separated by space characters and then
	 * the whole string is returned
	 * 
	 * @param varNamesList List of variable names
	 * @return Scope of the constraint in String representation
	 */
	private String getScope(List<String> varNamesList) {

		StringBuilder builder = new StringBuilder ();
		for(String varName : varNamesList) {
			builder.append(varName);
			builder.append(" ");
		}
		return builder.toString().trim();
	}

	/**
	 * This method generates a list of variable names from a list of bids. This is used for method 1, as we do in this case only have bids as variables, so
	 * generating the variable names is trivial (Bid_bidID)
	 * 
	 * @param bidsList List of bids
	 * @return List of variable names
	 */
	private List<String> getVarNamesFromBids(List<Bid> bidsList) {
		List<String> varNamesList = new ArrayList<String>();
		for(Bid bid : bidsList) {
			varNamesList.add("b" + bid.getBidID());
		}
		return varNamesList;
	}

	/**
	 * This method generates a list of variable names from a list of bids and an owner name. This is used for method 2, as there are copies of variables held
	 * by different owners, so the owner has to be specified in the variable name. The naming is as follows: Bid_bidID_ownerID. 
	 * 
	 * @param bidsList List of bids
	 * @param ownerID Owner of the variable
	 * @return List of variable names
	 */
	private List<String> getVarNamesFromBids(List<Bid> bidsList, String ownerID) {
		List<String> varNamesList = new ArrayList<String>();
		for(Bid bid : bidsList) {
			varNamesList.add(ownerID + "b" + bid.getBidID());
		}
		return varNamesList;
	}

	/**
	 * This method generates a list of variable names from a given good and a list of IDs of the bidders desiring this good. The variable names represent
	 * the variables held by the auctioneer, and there is one for every bidder who has placed at least one bid containing the auctioneer's good.
	 * This is used for conversion methods 3 and 4. 
	 * 
	 * @param good 			Good instance
	 * @param biddersScope 	List of IDs of bidders having placed at least one bid containing the given good
	 * @param methodID 		3, 4 or 5
	 * @return List of variable names
	 */
	private List<String> getVarNamesFromGood(Good good, List<Integer> biddersScope, int methodID) {
		List<String> varNamesList = new ArrayList<String>();
		if (methodID == 3 || methodID == 5) 
			for(Integer bidderID : biddersScope) 
				varNamesList.add("A" + good.getGoodID() + "B" + bidderID);
		else if (methodID == 4 || methodID == 6) 
			for(Integer bidderID : biddersScope) 
				varNamesList.add("B" + bidderID + "g" + good.getGoodID());
		return varNamesList;
	}

	/**
	 * This method generates a list of variable names from a given bidder and a list of IDs of the goods contained in at least one of his bids. The variable
	 * names represent the variables held by the bidder, and there is one for every good he desires. This is used for conversion methods 3 and 4. 
	 * 
	 * @param bidder 		Bidder instance
	 * @param goodsScope 	List of IDs of goods contained in at least one bid place by the given bidder
	 * @param methodID 		The method ID
	 * @return List of variable names
	 */
	private List<String> getVarNamesFromBidder(Bidder bidder, List<Integer> goodsScope, final int methodID) {
		List<String> varNamesList = new ArrayList<String>();
		String bidderString = "B" + bidder.getBidderID();
		
		if (methodID == 5) // over the auctioneers' variables
			for(Integer goodID : goodsScope) 
				varNamesList.add("A" + goodID + bidderString);
			
		else // over the bidder's variables
			for(Integer goodID : goodsScope) 
				varNamesList.add(bidderString + "g" + goodID);
		
		return varNamesList;
	}

	/**
	 * This method creates binary equality constraints between the copy of the variable corresponding to the bid held by the bidder, and all of the copies
	 * held by each auctioneer whose good the bid contains. This is used for conversion method 2
	 * 
	 * @param bid 			Bid for which equality constraints are created
	 * @param intensional 	whether the constraint should be intensional
	 */
	private void addEqualityConstraintsOnBids(Bid bid, final boolean intensional) {

		//Creating variable names
		String bidString = "b" + bid.getBidID();
		String varName1 = "B" + bid.getBidder().getBidderID() + bidString;
		String varName2;

		//For each auctioneer (here: auctioneerID = goodID), a constraint is created between the bidder's copy of the variable and the 
		//auctioneer's copy of the variable
		for(Good good : bid.getGoodsList()) {
			varName2 = "A" + good.getGoodID() + bidString;
			
			Constraint elmt = new Constraint(varName1 + "_EQUALS_" + varName2, "2", varName1 + " " + varName2, "EQUALITY_BINARY");
			if (intensional) {
				Element params = new Element ("parameters");
				elmt.addContent(params);
				params.setText(varName1 + " " + varName2);
			}
			
			constraints.add(elmt);
		}
	}

	/**
	 * This method is used for conversion method 3 and creates binary equality constraints between each variable held by the given bidder and its copy held
	 * by the auctioneer whose good the variable is representing.
	 * 
	 * @param bidder 		Bidder instance
	 * @param intensional 	whether the constraint should be intensional
	 */
	private void addEqualityConstraintsOnGoods(Bidder bidder, final boolean intensional) {
		String bidderString;
		String varName1;
		String varName2;
		List<Integer> goodsScope;

		bidderString = "B" + bidder.getBidderID();
		goodsScope = Instance.getGoodsScopeByBidder(bidder);
		for(Integer goodID : goodsScope) {
			varName1 = bidderString + "g" + goodID;
			varName2 = "A" + goodID + bidderString;
			
			Constraint elmt = new Constraint(varName1 + "_EQUALS_" + varName2, "2", varName1 + " " + varName2, "EQUALITY_BINARY");
			if (intensional) {
				Element params = new Element ("parameters");
				elmt.addContent(params);
				params.setText(varName1 + " " + varName2);
			}
			
			constraints.add(elmt);
		}
	}
}
