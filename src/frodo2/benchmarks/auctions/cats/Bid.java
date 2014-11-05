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

package frodo2.benchmarks.auctions.cats;

import java.util.List;

/**
 * This class represents a bid placed in an auction
 * 
 * @author Andreas Schaedeli
 *
 */
public class Bid {

	/**ID of the bid; corresponds to position in source file*/
	private int bidID;
	
	/**Bidder who has placed this bid*/
	private Bidder bidder;
	
	/**Price the bidder is willing to pay when this bid wins*/
	private double price;
	
	/**List of goods contained in this bid*/
	private List<Good> goodsList;
	
	
	/**
	 * The constructor assigns the instance variables of this object
	 * 
	 * @param bidID ID of the bid
	 * @param bidder Bidder who has placed this bid
	 * @param price Price the bidder would pay if this bid wins
	 * @param goodsList List of goods contained in this bid
	 */
	public Bid(int bidID, Bidder bidder, double price, List<Good> goodsList) {
		this.bidID = bidID;
		this.bidder = bidder;
		this.price = price;
		this.goodsList = goodsList;
	}

	/**
	 * @return This bid's ID
	 */
	public int getBidID() {
		return bidID;
	}

	/**
	 * @return Bidder who has placed this bid
	 */
	public Bidder getBidder() {
		return bidder;
	}

	/**
	 * @return Price of this bid
	 */
	public double getPrice() {
		return price;
	}

	/**
	 * @return Goods list of this bid
	 */
	public List<Good> getGoodsList() {
		return goodsList;
	}
}
