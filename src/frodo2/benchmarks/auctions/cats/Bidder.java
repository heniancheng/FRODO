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
 * This class represents a bidder in the auction
 * 
 * @author Andreas Schaedeli
 *
 */
public class Bidder {
	
	/**Variable used to create a unique ID for each bidder*/
	static int NEXT_ID = 0;

	
	/**ID assigned to this bidder*/
	private int bidderID;
	
	/**List of bids this bidder has placed*/
	private List<Bid> bidsList;
	
	/**True if this bidder is a fake bidder (owned by an auctioneer to set its reserve price)*/
	private boolean isFake;
	
	/**
	 * The constructor just assigns a unique ID to the bidder
	 */
	public Bidder() {
		bidderID = NEXT_ID++;
		bidsList = new ArrayList<Bid>();
		isFake=false;
	}
	
	/**
	 * This method adds a bid to the bidder's list of bids
	 * 
	 * @param bid Bid to add
	 */
	public void addBid(Bid bid) {
		bidsList.add(bid);
	}

	/**
	 * @return This bidder's ID
	 */
	public int getBidderID() {
		return bidderID;
	}

	/**
	 * @return List of bids this bidder has placed
	 */
	public List<Bid> getBidsList() {
		return bidsList;
	}
	
	/**
	 * @return true if the bidder is a fake bidder
	 */
	public boolean isFake() {
		return isFake;
	}

	/**
	 * @param isFake  true if the bidder is a fake one
	 * */
	public void setFake(boolean isFake) {
		this.isFake = isFake;
	}

}
