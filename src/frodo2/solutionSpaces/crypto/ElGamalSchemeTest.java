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

package frodo2.solutionSpaces.crypto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jdom2.Element;

import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.crypto.ElGamalScheme.ElGamalPublicKeyShare;
import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Junit Class to test the ElGamalScheme
 * @author Eric Zbinden, Thomas Leaute
 */
public class ElGamalSchemeTest extends TestCase {

	/** Number of cryptoSchemes
	 * @note must be >= 2 */
	private int agent = 10;
	
	/** Large big prime number */
	private final String p = "57475322849086478933";
	// better 512p : "29943207855436367407612837276286965993351959448989623660314348101880565473518597533295375157211097010902816881470671724126036289553986947199494369410918547";
	
	/** Large number that is a generator for p */
	private final String g = "5526868997990728076";
	// better associated g: "18261908958389154944274774667360451019974769453596444320512779869257372024106020602167056988140345164076916290105290743415373998274884678869342638514256724";
	
	/** Size of the elgamal vector */
	private String size = "1000";
	
	/** List of ElGamalSchemes */
	private ArrayList<ElGamalScheme> schemes;
	
	/** List of public key shares (possibly multiple per agent) */
	private ArrayList< List<ElGamalPublicKeyShare> > shares;
	
	/** Random generator */
	private Random rand;
	
	/** Maximum number of encryptions */
	private final int maxNbrEncryptions = 10;
	
	/** The number of public key shares each agent must produce */
	private final int nbrSharesPerAgent;
	
	/**
	 * Constructor
	 * @param toTest 				the method to test
	 * @param nbrSharesPerAgent 	The number of public key shares each agent must produce
	 */
	public ElGamalSchemeTest(String toTest, int nbrSharesPerAgent) {
		super(toTest);
		this.nbrSharesPerAgent = nbrSharesPerAgent;
	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for ElGamalScheme");
		
		TestSuite testTmp = new TestSuite ("Testing the decryptOnce method of ElGamalScheme");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("decryptOnce", 1), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing the decryptOnce method of ElGamalScheme with multiple shares per agent");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("decryptOnce", 2), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing the partialEncrypt method of ElGamalScheme");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("partialEncrypt", 1), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing the partialEncrypt method of ElGamalScheme with multiple shares per agent");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("partialEncrypt", 2), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing the decrypt method of ElGamalScheme");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("decrypt", 1), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing the decrypt method of ElGamalScheme with multiple shares per agent");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("decrypt", 2), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing ElGamalBigInteger.add");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("add", 1), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing ElGamalBigInteger.add with multiple shares per agent");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("add", 2), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Testing ElGamalBigInteger.min");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("min", 1), 100));
		testSuite.addTest(testTmp);
			
		testTmp = new TestSuite ("Testing ElGamalBigInteger.min with multiple shares per agent");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("min", 2), 100));
		testSuite.addTest(testTmp);
			
		testTmp = new TestSuite ("Testing the decryption of infinity method of ElGamalScheme");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("infinity", 1), 100));
		testSuite.addTest(testTmp);
				
		testTmp = new TestSuite ("Testing the decryption of infinity method of ElGamalScheme with multiple shares per agent");
		testTmp.addTest(new RepeatedTest (new ElGamalSchemeTest ("infinity", 2), 100));
		testSuite.addTest(testTmp);
				
		return testSuite;
	}
	
	/**
	 * Test the add() method of ElGamalBigInteger
	 */
	public void add(){
		
		//initiate
		agent = 2;
		int siz = Integer.valueOf(size)/10;
		AddableInteger SIZE = new AddableInteger(siz); //value of infinity
		
		init();
		
		//Generate r and s
		AddableInteger r,s;
		do{
			r = new AddableInteger(rand.nextInt(siz));
			s = new AddableInteger(rand.nextInt(siz));
		} while (r.add(s).compareTo(SIZE) >= 0);	//must not be greater than infinity	
		AddableInteger val = r.add(s); //result

		//Test add()
		ElGamalBigInteger eVal = schemes.get(0).reencrypt(schemes.get(0).encrypt(r)); 	//0 encrypts r	
		eVal = eVal.add(s);																//1 adds s to r	
		eVal = schemes.get(1).reencrypt(eVal);						  					//1 reencrypts (r+s)
		ElGamalBigInteger pVal = schemes.get(0).partialDecrypt(eVal, null);				//0 partially decrypts (r+s)	
		AddableInteger deVal = schemes.get(1).decrypt(eVal, pVal);						//1 decrypts (r+s)
		
		assertTrue("Expected: "+val+" found: "+deVal,deVal.equals(val));
	}
	
	/**
	 * Test the method min(ElGamalBigInteger other) of ElGamalBigInteger
	 * @param infinity whether we test with infinity or not
	 */
	public void min(boolean infinity){
		size = "100";
		agent = 2;		
		init();
		
		//initialize the value to test
		AddableInteger r = new AddableInteger(rand.nextInt(Integer.valueOf(size)));
		AddableInteger s;
		if(!infinity) s = new AddableInteger(rand.nextInt(Integer.valueOf(size)));
		else s = AddableInteger.PlusInfinity.PLUS_INF;
		
		//The 2 agents used for this test
		ElGamalScheme agent0 = schemes.get(0);
		ElGamalScheme agent1 = schemes.get(1);
		
		//Encrypt both value by different agent
		ElGamalBigInteger rVal = agent0.reencrypt(agent0.encrypt(r));	
		ElGamalBigInteger sVal = agent1.reencrypt(agent1.encrypt(s));
		
		//Get the encrypted min : r.min(s) and s.min(r)
		ElGamalBigInteger min1 = rVal.min(sVal);
		ElGamalBigInteger min2 = sVal.min(rVal);
		
		//Decrypt both min
		ElGamalBigInteger minPartial1 = agent1.partialDecrypt(min1, null);
		AddableInteger decryptMin1 = agent0.decrypt(min1, minPartial1);
		ElGamalBigInteger minPartial2 = agent0.partialDecrypt(min2, null);
		AddableInteger decryptMin2 = agent1.decrypt(min2, minPartial2);
				
		//Insure correctness
		if(infinity) {
			assertTrue("Expected "+r+" to be smaller than infinity, but was not in (r.min(s))", decryptMin1.equals(r));
			assertTrue("Expected "+r+" to be smaller than infinity, but was not in (s.min(r))", decryptMin2.equals(r));
			
		} else {
			if(r.compareTo(s) < 0) {
				assertTrue("Expected "+r+" to be smaller than "+s+", but was not in (r.min(s))", decryptMin1.equals(r));
				assertTrue("Expected "+r+" to be smaller than "+s+", but was not in (s.min(r)", decryptMin2.equals(r));
				
			} else {
				assertTrue("Expected "+s+" to be smaller than "+r+", but was not in (r.min(s))", decryptMin1.equals(s));
				assertTrue("Expected "+s+" to be smaller than "+r+", but was not in (s.min(r))", decryptMin2.equals(s));
			}
		}	
	}
	
	/**
	 * Test the method min(ElGamalBigInteger other) of ElGamalBigInteger
	 */
	public void min(){
		min(false);
	}
	
	/**
	 * Used to test the decryption of PlusInfinity
	 * Test decryptOnce(PLUS_INF)
	 * 		decrypt(PLUS_INF)
	 */
	public void infinity(){
		randomDecryptOnce(AddableInteger.PlusInfinity.PLUS_INF);
		randomDecrypt(AddableInteger.PlusInfinity.PLUS_INF);
		min(true);
	}
	
	/** Testing decryption by one agent */
	public void decryptOnce(){
		randomDecryptOnce(new AddableInteger(rand.nextInt(Integer.valueOf(size))));
	}
	
	/** Testing decryption by all the agents */
	public void decrypt(){
		randomDecrypt(new AddableInteger(rand.nextInt(Integer.valueOf(size))));
	}

	/**
	 * Used to test the decryption
	 * Encrypts val a random number of times by the same ElGamalScheme then decrypts it
	 * @param val the value to encrypt
	 */
	private void randomDecryptOnce(AddableInteger val){
		
		agent = 1;
		init();
				
		ElGamalBigInteger eVal = null;
		AddableInteger deVal = null;
		
		for(int i=0;i<agent;i++){
			ElGamalScheme crypter = schemes.get(i);
			int nbrEncryptions = rand.nextInt(maxNbrEncryptions);
			nbrEncryptions ++; //avoid 0
				
			eVal = crypter.encrypt(val);
															
			for(int j=1; j <= nbrEncryptions; j++)
				eVal = crypter.reencrypt(eVal);
			
			deVal = crypter.decrypt(eVal, null);
			
			//System.out.println("Value: "+val+"\nEncrypted by :"+eVal+"\nDecrypted: "+deVal);	
			assertTrue("Expected: "+val+"\tFound: "+deVal, deVal.equals(val));	
		}
	}
	
	/**
	 * Used to test the decryption
	 * Encrypts a random number of times by all the ElGamalSchemes, then decrypts it
	 * @param val the value to encrypt
	 */
	private void randomDecrypt(AddableInteger val){
		
		this.agent = 10;
		init();
		
		ElGamalBigInteger eVal = schemes.get(0).encrypt(val);
		ElGamalBigInteger pVal = null;
		AddableInteger deVal = null;
		
		//Encrypt
		for(int i=0;i<agent;i++){
			ElGamalScheme crypter = schemes.get(i);
			int nbrEncryptions = rand.nextInt(maxNbrEncryptions);
			nbrEncryptions ++; //avoid 0
						
			for(int j = 1; j <= nbrEncryptions; j++){
				eVal = crypter.reencrypt(eVal);
				//System.out.println("crypter "+i+" reencrypted for the "+j+" times: "+eVal);
			}
		}
		
		//Decrypt once by agent
		for(int i= agent-1;i>0;i--){
			ElGamalScheme crypter = schemes.get(i);
			
			pVal = crypter.partialDecrypt(eVal, pVal); //Partial decryption
			//System.out.println("crypter "+i+" partially decrypt once: "+pVal);		
		}
		
		deVal = schemes.get(0).decrypt(eVal, pVal);
		//System.out.println("crypter 0 decrypt the value: "+deVal);
		
		//System.out.println("Value: "+val+"\nEncrypted by :"+eVal+"\nBi^a:         "+pVal+"\nDecrypted: "+deVal);	
		assertTrue("Expected: "+val.toString()+"\tFound: "+deVal, deVal.equals(val));		
	}
	
	/**
	 * simplified decryption of 2 agents 
	 */
	public void partialEncrypt(){
		
		this.size= "10";
		agent = 2;
		init();
		
		AddableInteger val = new AddableInteger(rand.nextInt(Integer.valueOf(size))); //no infinity
		ElGamalBigInteger eVal = schemes.get(0).reencrypt(schemes.get(0).encrypt(val)); 	//System.out.println("First encryption by scheme 0: "+eVal);
		eVal = schemes.get(1).reencrypt(eVal);												//System.out.println("Reencryption by scheme 1: "+eVal);
		ElGamalBigInteger pVal = schemes.get(1).partialDecrypt(eVal, null);					//System.out.println("Partial decryption by scheme 1: "+pVal);
		AddableInteger deVal = schemes.get(0).decrypt(eVal, pVal);							//System.out.println("Decryption by scheme 0. Expected: "+val+" Found: "+deVal);
		//System.out.println("***---***---***");
		
		assertTrue("Expected: "+val+" found: "+deVal,deVal.equals(val));		
	}
	
	/**
	 * Initialize
	 */
	private void init(){
		
		Element params = new Element("ElGamalScheme");
		params.setAttribute("modulus", p);
		params.setAttribute("generator", g);
		params.setAttribute("infinity", size);
		
		//create all schemes and public key shares
		this.schemes.clear();
		this.shares.clear();
		for(int i=0;i<agent;i++){
			ElGamalScheme scheme = new ElGamalScheme(params);
			schemes.add(scheme);
			
			ArrayList<ElGamalPublicKeyShare> list = new ArrayList<ElGamalPublicKeyShare> (this.nbrSharesPerAgent);
			for (int j = 0; j < this.nbrSharesPerAgent; j++) 
				list.add(scheme.newPublicKeyShare());
			
			this.shares.add(list);
		}
		
		//exchange keys
		for (int i = 0; i < this.schemes.size(); i++) 
			for (int j = 0; j < this.shares.size(); j++) 
				if (i != j) 
					for (ElGamalPublicKeyShare key : this.shares.get(j)) 
						this.schemes.get(i).addPublicKeyShare(key);
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		rand = new Random();
		schemes = new ArrayList<ElGamalScheme>();
		this.shares = new ArrayList< List<ElGamalPublicKeyShare> > ();
	}
	
	/** Ends all queues 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		rand = null;
		schemes = null;
		this.shares = null;
	}
}
