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

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a good sold in the auction
 * 
 * @author Andreas Schaedeli
 *
 */
public class Good {

	/**Unique ID of the good*/
	private int goodID;
	
	/**List of bids placed on this good*/
	private List<Bid> bidsList;
	
	/**Minimal price for this good*/
	private double reservePrice;
	
	

	/**
	 * The constructor assigns the ID to the good and initializes an empty list for the bids
	 * 
	 * @param goodID ID of the good
	 */
	public Good(int goodID) {
		this.goodID = goodID;
		bidsList = new ArrayList<Bid>();
		reservePrice = 0;
	}
	
	/**
	 * This method adds a bid placed on this good
	 * 
	 * @param bid Bid in which this good appears
	 */
	public void addBid(Bid bid) {
		bidsList.add(bid);
	}

	/**
	 * @return This good's ID
	 */
	public int getGoodID() {
		return goodID;
	}

	/**
	 * @return List of bids containing this good
	 */
	public List<Bid> getBidsList() {
		return bidsList;
	}
	

	/**
	 * @return The reserve price of this good
	 */
	public double getReservePrice() {
		return reservePrice;
	}

	/**
	 * @param reservePrice the new reserve price for the good
	 */
	public void setReservePrice(double reservePrice) {
		this.reservePrice = reservePrice;
	}
}
