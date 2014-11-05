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

package frodo2.benchmarks.auctions.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom2.Document;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.XCSPparser;
import frodo2.benchmarks.auctions.cats.Auction;
import frodo2.benchmarks.auctions.cats.Bid;
import frodo2.benchmarks.auctions.cats.Bidder;
import frodo2.benchmarks.auctions.cats.Good;
import frodo2.benchmarks.auctions.xcsp.Instance;
import frodo2.solutionSpaces.AddableInteger;

/**
 * This is the main class for converting a CATS (Combinatorial Auctions Test Suite) into a DCOP (Distributed Constraint Optimization Problem). Three different methods are
 * implemented to do this, corresponding to three different DCOP models for the auction. 
 * 
 * @author Andreas Schaedeli, Thomas Leaute
 * @todo Add support for Python scripting
 *
 */
public class CATSToXCSP {

	/**Path and name of the source file; parsed from the args array*/
	private static String sourceFileName = null;

	/**Path where the output file should be saved; parsed from the args array*/
	private static String outputDir = null;

	/**ID of the method to be used; parsed from the args array*/
	private static int methodID = 0;

	/**Boolean indicating whether the auction should be transformed into a minimization problem or not*/
	private static boolean minimize = false;

	/** If \c true, bid prices should be ignored, and the output should be a pure DisCSP intance */
	private static boolean discsp = false;

	/**Boolean indicating whether sum constraints should be intensional (\c true) or extensional (\c false)*/
	private static boolean sumDecomposition = false;

	/** Whether all constraints must be intensional */
	private static boolean intensional = false;


	/**Number of goods in the auction*/
	private static int nbGoods = -1;

	/**List of all goods which already have a reserve price*/
	private static HashSet<Good> goodsWithRP = new HashSet<Good>();

	/**Auction instance created from the source file*/
	private static Auction auction = null;

	/**Mapping from dummy good number to bidder; assures no dummy good is assigned to two different bidders*/
	private static Map<Integer, Bidder> dummyGoodsToBiddersMap = new HashMap<Integer, Bidder>();

	
	/** Pattern to identify the desired number of bids, which may be lower than the actual number of bids */
	private static final Pattern nbrBidsPattern = Pattern.compile("%.*;\\sBids:\\s(\\d+).*");
	
	/**Pattern used to identify the \'goods\' line in the source file*/
	private static final Pattern goodsPattern = Pattern.compile(".*goods\\s*(\\d*).*");

	/**Pattern used to identify the bids in the source file*/
	private static final Pattern bidPattern = Pattern.compile("(\\d+)\\s+(-?\\d+(\\.\\d+)?)(\\s+\\d+)+\\s*#");


	/**
	 * The main method first checks the arguments, then parses the source file, converts the problem by the indicated method and finally
	 * writes the output file
	 * 
	 * @param args Arguments passed at program start
	 */
	public static void main(String[] args) {

		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. Use the option -license to display the license.\n");

		//Check for valid arguments
		if(!checkArgs(args)) {
			usage();
			System.exit(0);
		}
		else {
			//Try to parse the source file
			System.out.println("Parsing source file " + sourceFileName);
			try {
				auction = parseAuction (sourceFileName);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return;
			}

			// Create and output the XCSP file
			Document doc = toXCSP(auction, methodID, discsp, sumDecomposition, intensional, minimize);
			String outputFileName = (outputDir != null ? outputDir + "/" : "") + new File(sourceFileName).getName() + "_method" + methodID;
			if(sumDecomposition) 
				outputFileName += "_sum";
			if (intensional) 
				outputFileName += "_intensional";
			if(minimize) 
				outputFileName += "_min";
			if (discsp) 
				outputFileName += "_discsp";
			outputFileName += ".xml";
			System.out.println("Generating output file " + outputFileName);
			try{
				new XMLOutputter(Format.getPrettyFormat()).output(doc, new FileWriter (new File(outputFileName)));
			}
			catch(IOException e) {
				System.err.println("Output file " + outputFileName + "could not be written correctly");
				e.printStackTrace();
			}

			System.out.println("Done.");
		}
	}

	/** Parses the description of an auction, in CATS format
	 * @param sourceFileName 	the path to the CATS file
	 * @return an Auction instance
	 * @throws FileNotFoundException 	if the input file is not found
	 */
	public static Auction parseAuction (String sourceFileName) throws FileNotFoundException {

		//Try to parse the source file
		Scanner fileScanner = null;
		int lineNumber = 1;
		int nbrBids = -1;
		try{
			fileScanner = new Scanner(new File(sourceFileName));
			String line = "";
			Matcher nbrBidsMatcher;
			Matcher goodsMatcher;
			Matcher bidMatcher;
			
			// Parse the desired number of bids
			while(fileScanner.hasNextLine() && nbrBids == - 1) {
				nbrBidsMatcher = nbrBidsPattern.matcher(fileScanner.nextLine());
				if (nbrBidsMatcher.find()) 
					nbrBids = Integer.parseInt(nbrBidsMatcher.group(1));
			}
			
			// Parse the number of goods
			nbGoods = -1;
			while(fileScanner.hasNextLine() && nbGoods == - 1) {
				line = fileScanner.nextLine();

				// Look for the number of goods
				if(nbGoods == -1) {
					goodsMatcher = goodsPattern.matcher(line);
					if(goodsMatcher.find()) {
						nbGoods = Integer.parseInt(goodsMatcher.group(1));
					}
				}
				lineNumber++;
			}

			// Initialize the auction and create all the goods sold in this auction
			auction = new Auction(nbGoods);
			dummyGoodsToBiddersMap.clear();

			// Parse the bids
			while(fileScanner.hasNextLine() && nbrBids > 0) {
				line = fileScanner.nextLine();
				bidMatcher = bidPattern.matcher(line);
				if(bidMatcher.find()) {	//Adds the bid with the found ID and price
					addBid(Integer.parseInt(bidMatcher.group(1)), Double.parseDouble(bidMatcher.group(2)), line);
					nbrBids--;
				}
				lineNumber++;
			}
			fileScanner.close();
		}
		catch(NumberFormatException e) {
			if(nbGoods == -1) {
				System.err.println("Expected to find integer value for number of goods at line " + lineNumber);
			}
			else {
				System.err.println("Bid ID at line " + lineNumber + " is no valid integer");
			}
			fileScanner.close();
			throw e;
		}

		return auction;
	}

	/** Formulates the input auction as a DCOP or DisCSP in XCSP format
	 * @param auction 			the auction
	 * @param methodID 			the ID of the XCSP modeling method
	 * @return the XCSP document that is a maximization DCOP without intensional SUM constraints
	 */
	public static Document toXCSP (Auction auction, int methodID) {
		return toXCSP (auction, methodID, false, false, false, false);
	}

	/** Formulates the input auction as a DCOP or DisCSP in XCSP format
	 * @param auction 			the auction
	 * @param methodID 			the ID of the XCSP modeling method
	 * @param discsp 			whether to only allow hard constraints
	 * @param sumDecomposition 	whether to use intensional SUM constraints
	 * @param intensional 		whether all constraints should be intensional
	 * @param minimize 			whether to output a minimization or a maximization problem
	 * @return the XCSP document
	 */
	public static Document toXCSP (Auction auction, int methodID, boolean discsp, boolean sumDecomposition, boolean intensional, boolean minimize) {

		CATSToXCSP.auction = auction;
		CATSToXCSP.methodID = methodID;
		CATSToXCSP.discsp = discsp;
		CATSToXCSP.sumDecomposition = sumDecomposition;
		CATSToXCSP.intensional = intensional;
		CATSToXCSP.minimize = minimize;

		Document doc = new Instance().create(auction, methodID, discsp, sumDecomposition, intensional);
		if(minimize && ! discsp) 
			doc = new XCSPparser<AddableInteger, AddableInteger>(doc).switchMaxMin((int) findMaxUtility() + 1);

		return doc;
	}

	/** 
	 * @param args Array of arguments
	 * @return <b>true</b> if all mandatory arguments are correctly specified, else <b>false</b>
	 */
	private static boolean checkArgs(String[] args) {

		boolean argsOK = true;
		boolean srcOK = false;
		boolean methodOK = false;

		//Checks if there are any arguments
		if(args.length == 0) {
			argsOK = false;
		}

		else {
			//Iterates over all arguments
			for(int i = 0; i < args.length; i++) {

				//If the argument "-src" is found, the next argument must be the source file name
				if(args[i].equals("-src")) {
					if(i == args.length - 1) {
						System.err.println("No source file specified");
					}
					else {
						sourceFileName = args[i+1];
						i++;
						srcOK = true;
					}
				}

				//If the argument "-method" is found, the next argument must specify the ID of the conversion method to be used
				else if(args[i].equals("-method")) {
					if(i == args.length - 1) {
						System.err.println("No method specified");
					}
					else {
						try {
							methodID = Integer.parseInt(args[i+1]);
							if(methodID < 1 || methodID > 6) {
								System.err.println("No correct method ID was specified");
							}
							else {
								methodOK = true;
							}
						}
						catch(NumberFormatException e) {
							System.err.println("Specified method ID is no integer");
						}
						i++;
					}
				}

				//Displays the license
				else if(args[i].equals("-license")) {
					displayLicense();
				}

				//Displays a brief description of the conversion methods
				else if(args[i].equals("-methods")) {
					displayMethods();
				}

				else if(args[i].equals("-min")) {
					minimize = true;
				}

				else if (args[i].equals("-discsp")) 
					discsp = true;

				else if(args[i].equals("-sum")) {
					sumDecomposition = true;
				}

				else if(args[i].equals("-i")) {
					intensional = true;
				}

				//Specifies the output directory and creates it if it does not exist
				else if(args[i].equals("-out") && i < args.length - 1) {
					outputDir = args[i+1];
					if(!new File(outputDir).exists()) {
						System.out.println("Specified output directory does not exist... Creating it");
						new File(outputDir).mkdir();
					}
				}
			}
		}

		//Checks if all mandatory were found and OK
		argsOK = argsOK && srcOK && methodOK;
		return argsOK;
	}

	/**
	 * This method reads the license from the file LICENSE.txt and displays it if the file exists and can be read
	 */
	private static void displayLicense() {

		Scanner licenseScanner;
		try{
			licenseScanner = new Scanner(new File("LICENSE.txt"));
			while(licenseScanner.hasNextLine()) {
				System.out.println(licenseScanner.nextLine());
			}
			licenseScanner.close();
		}
		catch(FileNotFoundException e) {
			System.err.println("License file \'LICENSE.txt\' could not be read");
		}
	}

	/**
	 * This method displays a brief description of the three implemented conversion methods
	 */
	private static void displayMethods() {
		System.out.println(	"Implemented conversion methods:\n" + 
				"1: One variable per bid\n" + 
				"This method creates a single Boolean variable per bid and n-ary sum<=1 constraints between bids \n" + 
				"containing the same good and between all bids placed by the same bidder\n" + 
				"-------------------------------\n" + 
				"2: One variable per bid and bidder/auctioneer\n" + 
				"This method creates one Boolean variable per bid, owned by the corresponding bidder. \n" + 
				"Each auctioneer selling a good involved in the bid also gets a copy of this variable. \n" + 
				"There are n-ary sum<=1 constraints between all variables a bidder holds, \n" + 
				"and between all variables an auctioneer holds. Furthermore, \n" + 
				"there are binary equality constraints for each bid, expressing that each auctioneer's copy \n" + 
				"of the variable for this bid must be equal to the bidder's copy. \n" + 
				"-------------------------------\n" + 
				"3: One variable per good and bidder/auctioneer\n" +
				"This method creates one Boolean variable per good and bidder, which is equal to 1 if the bidder \n" + 
				"gets the good, and 0 otherwise. Both the bidder and the auctioneer of the good get a copy of the \n" +
				"variable. There are n-ary sum<=1 constraints between all the variables an auctioneer holds. \n" +
				"Furthermore, there are equality constraints between the two copies of a variable. Finally, \n" + 
				"there are utility constraints for each bidder. The utility is equal to a bid's price if and \n" + 
				"only if exactly the variables representing the goods contained in that bid are equal to 1. \n" + 
				"-------------------------------\n" + 
				"4: One variable per good and bidder\n" +
				"This is the same method as Method 3, with two differences: \n" +
				"- There are no auctioneers with copies of the bidders' variables; \n" +
				"- Each bidder owns one variable per good, even for goods she is not interested in. \n" + 
				"-------------------------------\n" + 
				"5: One variable per auctioneer and bidder\n" +
				"This is the same method as Method 3, with two differences: \n" +
				"- The bidders express their constraints over the variables hold by the auctioneers; \n" +
				"- Each auctioneer owns one variable per bidder, even if she is not interested in the good. \n" +
				"-------------------------------\n" + 
				"6: One common variable per auctioneer and bidder\n" +
				"This is the same method as Method 3, but with common variables instead of owned, copy variables. ");
	}

	/**
	 * This method displays a manual indicating how to run the program
	 */
	private static void usage() {
		System.out.println("-------------------------------");
		System.out.println("To run this program, the following options may be used:");
		System.out.println("-src \'sourceFileName\' (mandatory) Specifies name and path of source file");
		System.out.println("-out \'outputDirectory\' (optional) Specifies the directory where the output file should be saved");
		System.out.println("-method [1 2 3 4 5 6] (mandatory) Specifies the conversion method");
		System.out.println("-min (optional) Specifies whether the problem should be converted into a minimization problem (maximization by default)");
		System.out.println("-discsp (optional) Specifies whether bid prices should be ignored, and the output should be a pure DisCSP instance");
		System.out.println("-sum (optional) Specifies that sum constraints should be intensional");
		System.out.println("-i (optional) Specifies that all constraints should be intensional");
		System.out.println("-methods (optional) Displays a brief description of the conversion methods");
		System.out.println("-license (optional) Displays the license");
		System.out.println("-------------------------------");
	}

	/**
	 * This method is called whenever a line in the source file matches the bid pattern. The bid is then parsed and a Bid object
	 * is created and added to the list of bids
	 * In case the bid contains any good ID that is greater than or equal to the number of goods, this represents a dummy good. All bids containing the same
	 * dummy good were placed by the same bidder. So, for each bid without dummy goods, a new Bidder is created. However, if there are dummy goods, we want to
	 * make sure all the bids with the same dummy good are assigned the same bidder, so there is a mapping from dummy good IDs to Bidders. All bidders have
	 * to be added to the auction object.
	 * Moreover, if the bid value is less than 0, we consider it as reserve price for the good it bids on.  
	 * 
	 * @param bidID Unique ID of the bid
	 * @param price Utility of the bid
	 * @param line Line of the source file containing the bid
	 */
	private static void addBid(int bidID, double price, String line) {

		List<Good> goodsList = new ArrayList<Good>();	//List to collect all the goods the bid contains

		//This line replaces the bid ID, the price and the final # by empty strings and removes trailing spaces (price can be integer or double value)
		line = line.replaceFirst("(\\d+)\\s+(-?\\d+(\\.\\d+)?\\s+)", "").replace("#", "").trim();
		//The bid line is then split by using space characters as delimiters
		String[] goods = line.split("\\s+");

		//Creation of the list of goods and the Bidder object
		Integer goodID;
		Bidder bidder = null;

		assert price >= 0 || goods.length==1 : "Reserve prices are only allowed for one good and not for bundles of goods.";		
		assert price >= 0 || !goodsWithRP.contains(auction.getGood(Integer.parseInt(goods[0]))) : "A good can have only one reserve price.";

		if((price < 0 && goods.length==1 && !goodsWithRP.contains(auction.getGood(Integer.parseInt(goods[0])))) || price >= 0){ //avoids strange fake bidders which bids negative price for more than one good or redundant reserve price
			//Iteration over parsed good IDs
			for(String good : goods) {
				goodID = Integer.parseInt(good);

				//"Normal" good
				if(goodID < nbGoods) {
					goodsList.add(auction.getGood(goodID));
				}

				//Dummy good; Good ID is higher than number of goods. Check whether there already exists a bidder for this dummy good ID
				else {
					bidder = dummyGoodsToBiddersMap.get(goodID);

					//No bidder had been created for this dummy good ID yet
					if(bidder == null) {
						bidder = new Bidder();
						dummyGoodsToBiddersMap.put(goodID, bidder);
						auction.addBidder(bidder);
					}
				}
			}


			//This case happens when a bid contains no dummy good
			if(bidder == null) {
				bidder = new Bidder();
				auction.addBidder(bidder);
			}
			if(price < 0){  //if it is a fake bidder
				assert methodID == 2 || methodID == 3 : "Reserve prices are not supported when using method " + methodID;
				auction.getGood(Integer.parseInt(goods[0])).setReservePrice(Math.abs(price));
				bidder.setFake(true);
				goodsWithRP.add(auction.getGood(Integer.parseInt(goods[0])));
			}

			auction.addBid(new Bid(bidID, bidder, price, goodsList));
		}
	}

	/**
	 * @return The maximum utility among all the bids
	 */
	private static double findMaxUtility() {
		double maxUtility = 0;
		if (! discsp) 
			for(Bid bid : auction.getBids()) 
				maxUtility = Math.max(maxUtility, bid.getPrice());
		return maxUtility;
	}
}
