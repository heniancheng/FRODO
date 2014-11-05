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

import java.io.IOException;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import junit.extensions.RepeatedTest;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.Solution;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.dpop.DPOPsolver;
import frodo2.algorithms.dpop.privacy.CollaborativeDecryption;
import frodo2.algorithms.dpop.privacy.EncryptedUTIL;
import frodo2.algorithms.dpop.privacy.P2_DPOPsolver;
import frodo2.algorithms.dpop.privacy.test.FakeCryptoScheme.FakeEncryptedInteger;
import frodo2.algorithms.test.AllTests;
import frodo2.algorithms.varOrdering.dfs.DFSgenerationWithOrder;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.AddableLimited;
import frodo2.solutionSpaces.AddableReal;
import frodo2.solutionSpaces.crypto.CryptoScheme;
import frodo2.solutionSpaces.crypto.ElGamalBigInteger;
import frodo2.solutionSpaces.crypto.ElGamalScheme;

/**
 * JUnit test for P_DPOP agent
 * @author Eric Zbinden, Thomas Leaute
 * @param <V> the type used for variable values
 * @param <E> The class used for encrypted values
 */
public class P2_DPOPagentTest < V extends Addable<V>, E extends AddableLimited<AddableInteger, E> > extends TestCase {
	
	
	/**
	 * The maximum number of variables in this problem
	 */
	private int maxVar = 5;
	
	/**
	 * The maximum number of agents in this problem
	 */
	private int maxAgent = 5;
	
	/**
	 * The maximum number of constraints in this problem
	 */
	private int maxEdge = 10;
	
	/** The class of the CryptoScheme */
	private Class< ? extends CryptoScheme<AddableInteger, E, ?> > schemeClass;
	
	/** The class used for variable values */
	private Class<V> domClass;
	
	/** The class used for encrypted values */
	private Class<E> classOfE;
	
	/** Whether to enable the merging of back-edges */
	private final boolean mergeBack;

	/** Whether to minimize the NCCC */
	private final boolean minNCCCs;
	
	/** Whether to use TCP pipes */
	private final boolean useTCP;
	
	/**
	 * Constructor
	 * @param schemeClass 	The class of the CryptoScheme
	 * @param domClass 		The class used for variable values
	 * @param classOfE 		The class used for encrypted values
	 * @param mergeBack 	Whether to enable the merging of back-edges
	 * @param minNCCCs 		Whether to minimize the NCCC
	 * @param useTCP 		Whether to use TCP pipes
	 */
	public P2_DPOPagentTest(Class<V> domClass, Class< ? extends CryptoScheme<AddableInteger, E, ?> > schemeClass, Class<E> classOfE, 
			boolean mergeBack, boolean minNCCCs, boolean useTCP) {
		super("testP2DPOPvsDPOP");
		this.domClass = domClass;
		this.schemeClass = schemeClass;
		this.classOfE = classOfE;
		this.mergeBack = mergeBack;
		this.minNCCCs = minNCCCs;
		this.useTCP = useTCP;
	}

	/** @return the test suite for this test */
	public static TestSuite suite () {
		TestSuite testSuite = new TestSuite ("Tests for P2DPOP agent");
		
		TestSuite testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with FakeCryptoScheme with mergeBack");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, FakeEncryptedInteger> (AddableInteger.class, FakeCryptoScheme.class, FakeEncryptedInteger.class, true, false, false), 12000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with FakeCryptoScheme with mergeBack and TCP pipes");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, FakeEncryptedInteger> (AddableInteger.class, FakeCryptoScheme.class, FakeEncryptedInteger.class, true, false, true), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with FakeCryptoScheme with mergeBack and with minNCCCs");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, FakeEncryptedInteger> (AddableInteger.class, FakeCryptoScheme.class, FakeEncryptedInteger.class, true, true, false), 12000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with FakeCryptoScheme with mergeBack with real-valued variables");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableReal, FakeEncryptedInteger> (AddableReal.class, FakeCryptoScheme.class, FakeEncryptedInteger.class, true, false, false), 25000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with FakeCryptoScheme without mergeBack");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, FakeEncryptedInteger> (AddableInteger.class, FakeCryptoScheme.class, FakeEncryptedInteger.class, false, false, false), 5000));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with ElGamalScheme with mergeBack");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, ElGamalBigInteger> (AddableInteger.class, ElGamalScheme.class, ElGamalBigInteger.class, true, false, false), 250));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with ElGamalScheme with mergeBack and TCP pipes");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, ElGamalBigInteger> (AddableInteger.class, ElGamalScheme.class, ElGamalBigInteger.class, true, false, true), 100));
		testSuite.addTest(testTmp);
		
		testTmp = new TestSuite ("Tests for P2DPOP vs DPOP with ElGamalScheme without mergeBack");
		testTmp.addTest(new RepeatedTest (new P2_DPOPagentTest<AddableInteger, ElGamalBigInteger> (AddableInteger.class, ElGamalScheme.class, ElGamalBigInteger.class, false, false, false), 250));
		testSuite.addTest(testTmp);
		
		return testSuite;
	}
	
	/**
	 * Test whenever P-DPOP's and DPOP's answers to a random problem are equivalent
	 * @throws IOException is thrown if an I/O exception occur when accessing to the description of P-DPOP or DPOP algorithm
	 * @throws JDOMException is thrown if a parsing error occurs
	 */
	public void testP2DPOPvsDPOP () throws JDOMException, IOException {
		
		//Create new random problem
		Document problem = AllTests.createRandProblem(maxVar, maxEdge, maxAgent, false, +1);
		XCSPparser<V, AddableInteger> parser = new XCSPparser<V, AddableInteger>(problem);
		
		// Set the CryptoScheme and the mergeBack and minNCCCs flags
		Document agentDesc = XCSPparser.parse(AgentFactory.class.getResourceAsStream("/frodo2/algorithms/dpop/privacy/P2-DPOPagent.xml"), false);
		for (Element module : (List<Element>) agentDesc.getRootElement().getChild("modules").getChildren()) {
			String className = module.getAttributeValue("className");
			if (className.equals(CollaborativeDecryption.class.getName())) {
				
				Element schemeElmt = module.getChild("cryptoScheme");
				schemeElmt.setAttribute("className", this.schemeClass.getName());
				
				// Use small numbers of bits for the modulus and the generator to speed up the tests
				schemeElmt.setAttribute("modulus", "57475322849086478933");
				schemeElmt.setAttribute("generator", "5526868997990728076");
				break;
				
			} else if (className.equals(EncryptedUTIL.class.getName())) {
				module.setAttribute("mergeBack", Boolean.toString(this.mergeBack));
				module.setAttribute("minNCCCs", Boolean.toString(this.minNCCCs));
				
			} else if (className.equals(DFSgenerationWithOrder.class.getName())) 
				module.setAttribute("minIncr", "2");
		}
		
		//Compute both solutions
		Solution<V, AddableInteger> p2dpopSolution = new P2_DPOPsolver<V, AddableInteger>(agentDesc, this.domClass, AddableInteger.class, this.classOfE, this.useTCP)
			.solve(problem, parser.getNbrVars(), 240000L);
		Solution<V, AddableInteger> dpopSolution = new DPOPsolver<V, AddableInteger>(this.domClass, AddableInteger.class).solve(problem, parser.getNbrVars());
		
		assertNotNull ("P2-DPOP timed out", p2dpopSolution);
				
		//Verify the utilities of the solutions found by P-DPOP and DPOP
		assertEquals("P2-DPOP's and DPOP's utilities are different", dpopSolution.getUtility(), p2dpopSolution.getUtility());
		
		// Verify that P2DPOP's chosen assignments indeed have the correct utility
		assertEquals("The chosen assignments' utility differs from the reported utility", p2dpopSolution.getUtility(), p2dpopSolution.getReportedUtil());
	}
}
