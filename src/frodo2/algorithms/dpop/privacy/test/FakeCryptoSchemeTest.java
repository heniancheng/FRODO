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

package frodo2.algorithms.dpop.privacy.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;


import frodo2.algorithms.dpop.privacy.test.FakeCryptoScheme.FakeEncryptedInteger;
import frodo2.solutionSpaces.AddableInteger;

/**
 * Junit test to test the FakeCryptoScheme
 * @author Eric Zbinden
 *
 */
public class FakeCryptoSchemeTest extends TestCase {

	/** Random seed */
	private Random rand;
	
	/** All cryptoSchemes */
	private ArrayList<FakeCryptoScheme> fcs;
	
	/** All keys */
	private ArrayList<FakeEncryptedInteger> keys;
	
	/** AddableInteger CONSTANT zero*/
	private final AddableInteger ZERO = new AddableInteger(0);
	
	/** AddableInteger CONSTANT 42*/
	private final AddableInteger V42 = new AddableInteger(42);
	
	/** AddableBigInteger CONSTANT 10*/
	private final FakeEncryptedInteger TEN = new FakeEncryptedInteger("10");

	/** Number of cryptoSchemes */
	private int agent = 10; 
	
	/**
	 * Constructor
	 * @param toTest the method to test
	 */
	public FakeCryptoSchemeTest(String toTest) {
		super(toTest);

	}
	
	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for FakeCryptoScheme");
		
		TestSuite testTmp = new TestSuite ("Test if all keys are unique in a series of FakeCrypoSchemes");
		testTmp.addTest(new RepeatedTest (new FakeCryptoSchemeTest ("unique"), 10));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Test the partialyDecrypt method of FakeCrypoScheme");
		testTmp.addTest(new RepeatedTest (new FakeCryptoSchemeTest ("partialyDecrypt"), 10));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Test the decrypt method of FakeCrypoScheme");
		testTmp.addTest(new RepeatedTest (new FakeCryptoSchemeTest ("decrypt"), 10));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Test the decryption of infinity method of FakeCrypoScheme");
		testTmp.addTest(new RepeatedTest (new FakeCryptoSchemeTest ("decryptInfinity"), 10));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/**
	 * Test the decryption of infinity
	 */
	public void decryptInfinity(){
		
		decrypt(AddableInteger.PlusInfinity.PLUS_INF);		
	}
	
	/**
	 * Test the unicity of all FakeCryptoScheme keys
	 */
	public void unique(){
		
		init();
		
		HashSet<FakeEncryptedInteger> k = new HashSet<FakeEncryptedInteger>();
		
		for(FakeEncryptedInteger key : keys){
			
			assertTrue("key "+key+" already exists", k.add(key));		
			//System.out.println(key.toString());
		}
	}
	
	/**
	 * Used to test the decryption
	 * @param val the value to encrypt
	 */
	private void decrypt(AddableInteger val){
		
		init();
		
		FakeEncryptedInteger eVal = null;
		AddableInteger deVal = null;
		
		for(int i=0;i<agent;i++){
			FakeCryptoScheme crypter = fcs.get(i);
			int nbrEncryptions = rand.nextInt(9);
			nbrEncryptions ++; //avoid 0
				
			eVal = crypter.encrypt(val);
				
			for(int j=2; j <= nbrEncryptions; j++)
				eVal = crypter.reencrypt(eVal);
			
			deVal = crypter.decrypt(eVal, null);
				
			//System.out.println("eVal: "+eVal+"\tdeVal: "+deVal+"\tkey: "+keys.get(i));
			assertTrue("Expected: "+val.toString()+"\tFound: "+deVal, deVal.equals(val));		
		}
	}
	
	/**
	 * Test the decrypt method
	 */
	public void decrypt(){
		decrypt(V42);
	}
	
	/**
	 * Test method partialyDecrypt
	 */
	public void partialyDecrypt(){
		init();

		FakeEncryptedInteger eVal = null;
		FakeEncryptedInteger deVal = null;
		int[] encrypted = new int[agent];
		for(int i=0;i<agent;i++)
			encrypted[i] = 0;
		
		//Encrypt 43 times by a group of cryptoSchemes (max 9 encryptions for one unique cryptoScheme) 
		int x = rand.nextInt(agent);
		encrypted[x]++;		
		eVal = fcs.get(x).encrypt(V42);		

		for(int i =0;i<42;i++){
			do{
				x = rand.nextInt(agent);
			} while (encrypted[x] >= 9);
			encrypted[x]++;
			eVal = fcs.get(x).reencrypt(eVal);
		}
		
		//PartialyDecrpyt
		for(int i=0;i<agent;i++){
			
			FakeEncryptedInteger key = keys.get(i);
			deVal = fcs.get(i).partialDecrypt(eVal);
			
			FakeEncryptedInteger mod = deVal.mod(key.multiply(TEN));
			FakeEncryptedInteger mod2 = deVal.mod(key);
			
			assertTrue("value "+deVal+" not decrypted for key "+key + "; mod = " + mod + " != mod2 = " + mod2, mod.equals(mod2));			
		}
	}
	
	/**
	 * Initialize
	 */
	private void init(){
		
		FakeCryptoScheme.resetCounter();
		
		//create fakeCryptoScheme
		for(int i = 0; i< agent; i++){
			fcs.add(new FakeCryptoScheme(null));
		}
		//store key
		for(int i = 0; i< agent; i++){
			keys.add(fcs.get(i).encrypt(ZERO));
		}
	}
	
	/** @see junit.framework.TestCase#setUp() */
	protected void setUp () {
		fcs = new ArrayList<FakeCryptoScheme>();
		keys = new ArrayList<FakeEncryptedInteger>();
		rand = new Random();
	}
	
	/** Ends all queues 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown () throws Exception {
		fcs = null;
		keys = null;
		rand = null;
	}

}
