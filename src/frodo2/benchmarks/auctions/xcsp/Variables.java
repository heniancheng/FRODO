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
 * This class is used to generate the 'variables' tag in the output XML file
 * 
 * @author Andreas Schaedeli
 *
 */
public class Variables extends Element {

	/**Classes extending Element should declare a serial Version UID*/
	private static final long serialVersionUID = -4886368853280176832L;


	/**List of variables of this DCOP*/
	private List<Variable> variables;


	/**
	 * The constructor first calls the super class constructor to define the name of the tag this class represents, then initializes an empty list of variables
	 */
	public Variables() {
		super("variables");
		variables = new ArrayList<Variable>();
	}

	/**
	 * This method generates all the variables used to solve this DCOP. As the number and types of the variables depend on the method, there is a switch on the
	 * method ID
	 * 
	 * @param auction Auction instance
	 * @param methodID ID of conversion method to be applied
	 */
	public void create(Auction auction, int methodID) {
		switch(methodID) {

		//In this case, there is only one variable per bid
		case 1: {
			//Iteration over all the bids placed in the auction; one variable of type "binary" (domain [0..1]) is added for each bid
			for(Bid bid : auction.getBids()) {
				variables.add(new Variable("b" + bid.getBidID(), "binary", "Bidder_" + bid.getBidder().getBidderID()));
			}
			break;
		}

		//In this case, there is basically one variable per bid. The bidder who has placed the bid as well as all the auctioneers concerned with the bid
		//hold a copy of the variable.
		case 2: {
			String bidSuffix;

			//Iteration over all the bids
			for(Bid bid : auction.getBids()) {
				bidSuffix = "b" + bid.getBidID();

				//Creating the variable copy for the bidder
				if(bid.getPrice() < 0){//if it is a fake bidder
					variables.add(new Variable("B" + bid.getBidder().getBidderID() + bidSuffix, "binary", "Auctioneer_" + bid.getGoodsList().get(0).getGoodID()));
				}else{
					variables.add(new Variable("B" + bid.getBidder().getBidderID() + bidSuffix, "binary", "Bidder_" + bid.getBidder().getBidderID()));
				}

				//Creating the variable copies for the auctioneers
				for(Good good : bid.getGoodsList()) {
					variables.add(new Variable("A" + good.getGoodID() + bidSuffix, "binary", "Auctioneer_" + good.getGoodID()));
				}
			}
			break;
		}

		/* For method 3, each auctioneer holds a variable for each bidder having placed at least one bid for the auctioneer's good. This variable is binary
		 * and indicates whether the bidder associated with it wins the good. Furthermore, each bidder holds a variable for each good he bids for. The bidders
		 * and auctioneers variable concerning the same good will have to be equal. */
		case 3: {
			List<Integer> goodsScope;
			List<Integer> biddersScope;

			//Creation of all the variables held by the bidders
			for(Bidder bidder : auction.getBidders()) {
				goodsScope = Instance.getGoodsScopeByBidder(bidder);
				for(Integer goodID : goodsScope) {
					if(bidder.isFake()){//if it is a fake bidder
						variables.add(new Variable("B" + bidder.getBidderID() + "g" + goodID, "binary", "Auctioneer_" + goodID));
					}else{
						variables.add(new Variable("B" + bidder.getBidderID() + "g" + goodID, "binary", "Bidder_" + bidder.getBidderID()));
					}
				}
			}

			//Creation of all the variables held by the auctioneers
			for(Good good : auction.getGoods()) {
				biddersScope = Instance.getBiddersScopeByGood(good);
				for(Integer bidderID : biddersScope) {
					variables.add(new Variable("A" + good.getGoodID() + "B" + bidderID, "binary", "Auctioneer_" + good.getGoodID()));
				}
			}
			break;
		}

		// For method 4, each bidder holds one variable per good, even if she isn't interested in all goods
		case 4: {
			for (Bidder bidder : auction.getBidders()) 
				for (Good good : auction.getGoods()) 
					this.variables.add(new Variable ("B" + bidder.getBidderID() + "g" + good.getGoodID(), "binary", "Bidder_" + bidder.getBidderID()));
			break;
		}
		
		// For method 5, each auctioneer holds on variable per bidder, even if the bidder isn't interested in the good
		case 5: {
			for (Good good : auction.getGoods()) 
				if (! good.getBidsList().isEmpty()) 
					for (Bidder bidder : auction.getBidders()) 
						this.variables.add(new Variable ("A" + good.getGoodID() + "B" + bidder.getBidderID(), "binary", "Auctioneer_" + good.getGoodID()));
			break;
		}
		
		/* For method 6, there is one common variable for each auctioneer and each bidder having placed at least one bid for the auctioneer's good. This variable is binary
		 * and indicates whether the bidder associated with it wins the good. */
		case 6: {
			List<Integer> goodsScope;

			//Creation of all the common variables
			for(Bidder bidder : auction.getBidders()) {
				goodsScope = Instance.getGoodsScopeByBidder(bidder);
				for(Integer goodID : goodsScope) 
					variables.add(new Variable("B" + bidder.getBidderID() + "g" + goodID, "binary", null));
			}

			break;
		}
		}

		//Attribute of the 'variables' tag
		setAttribute("nbVariables", "" + variables.size());

		//The variables are added to this Element, so they will be written inside the 'variables' tag in the output XML file
		for(Variable variable : variables) {
			variable.create();
			addContent(variable);
		}
	}

	/**
	 * @return List of variables
	 */
	public List<Variable> getVariables() {
		return variables;
	}
}
