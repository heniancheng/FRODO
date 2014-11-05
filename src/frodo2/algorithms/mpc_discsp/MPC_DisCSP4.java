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

/** The MPC-Dis(W)CSP algorithms by Marius-Calin Silaghi */
package frodo2.algorithms.mpc_discsp;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.Queue;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.UtilitySolutionSpace;
import frodo2.solutionSpaces.UtilitySolutionSpace.SparseIterator;
import frodo2.solutionSpaces.UtilitySolutionSpace.ProjOutput;
import frodo2.solutionSpaces.hypercube.ScalarHypercube;

/** The MPC-DisCSP4 algorithm for DisCSP with privacy guarantees
 * 
 * MPC-DisCSP4 is described in the following paper:
 * 
 * Marius-Calin Silaghi. Hiding absence of solution for a distributed constraint satisfaction problem (poster). 
 * In Proceedings of the Eighteenth International Florida Artificial Intelligence Research Society Conference (FLAIRS'05), 
 * pages 854-855, Clearwater Beach, FL, USA, May 15-17 2005. AAAI Press.
 * 
 * @author Thomas Leaute
 * 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values, in stats gatherer mode only (in normal mode, AddableInteger is used)
 */
public class MPC_DisCSP4 < V extends Addable<V>, U extends Addable<U> > implements StatsReporter {
	
	/** The type of solution messages */
	protected static final String SOLUTION_MSG_TYPE = "Solution";
	
	/** A message containing the optimal solution for a variable
	 * @param <V> the type used for variable values
	 */
	public static class SolutionMsg < V extends Addable<V> > extends MessageWith2Payloads<String, V> {
		
		/** Empty constructor used for externalization */
		public SolutionMsg () { }

		/** Constructor
		 * @param var 	the variable
		 * @param val 	the optimal value for the variable
		 */
		public SolutionMsg (String var, V val) {
			super (SOLUTION_MSG_TYPE, var, val);
		}
		
		/** @return the variable */
		public String getVar () {
			return super.getPayload1();
		}
		
		/** @return the optimal value for the variable */
		public V getVal () {
			return super.getPayload2();
		}
	}
	
	/** The queue used to exchange messages */
	protected Queue queue;
	
	/** The problem */
	protected DCOPProblemInterface<V, U> problem;
	
	/** Whether to print statistics */
	private boolean silent;
	
	/** Whether the algorithm has been started */
	protected boolean started = false;
	
	/** For each entry in the S vector, for each variable, the index of its value in its domain */
	private ArrayList< int[] > solIndex;

	/** The agents in lexicographic order */
	protected ArrayList<String> agents;
	
	/** The number of agents */
	protected int nbrAgents;
	
	/** The degree of the polynomials in Shamir's secret sharing scheme */
	private int deg = -1;
	
	/** This agent's index in the ordered list of agents */
	protected int myAgentID;
	
	/** For each agent, its X value used to evaluate polynomials */
	private BigInteger[] agentXvalues;

	/** The number of publicly feasible solutions */
	protected int nbrSols;
	
	/** A source of randomness */
	private SecureRandom rand = new SecureRandom ();

	/** Shamir secret shares are modulo this number */
	protected final BigInteger modulo;
	
	/** For each agent, its S'' vector. These vectors must be multiplied element-wise to obtain S' */
	private TreeMap< Integer, BigInteger[] > SprimePrime = new TreeMap < Integer, BigInteger[] > ();
	
	/** The vector S' of Shamir shares of the feasibility of each publicly feasible solution */
	protected BigInteger[] Sprime;
	
	/** A random permutation used to shuffle the vectors */
	private int[] permutation;
	
	/** The inverse permutation */
	private int[] inversePerm;
	
	/** The Paillier crypto scheme */
	protected final PaillierCryptoScheme cryptoScheme;
	
	/** A vector of shares */
	private BigInteger[][] vectOfShares;
	
	/** Shares */
	private BigInteger[] shares;
	
	/** The coefficients used for Lagrange interpolation */
	private BigInteger[] lagrange;
	
	/** The vector S of Shamir shares of whether each solution is the optimal one */
	protected BigInteger[] S;
	
	/** When computing the vector S, the previous value of h */
	protected BigInteger hi;
	
	/** When computing the vector S, the previous value of Si*/
	protected BigInteger Si;
	
	/** A phase of the algorithm */
	protected enum Phase {
		/** Sharing of the S'' vectors */
		SprimePrime, 
		/** Element-wise multiplying vectors of shares */
		vectorMultiplication, 
		/** Shuffling vectors of shares */
		shuffling, 
		/** Computation of the h vector */
		h, 
		/** Computation of the S vector */
		S, 
		/** Computation of the solution */
		sol
	}

	/** The current phase */
	protected Phase phase = Phase.SprimePrime;

	/** A step of computation */
	protected int step = 0;
	
	/** The phases in a multiplication operation */
	protected enum MultiPhase {
		/** Exchanging shares of 0 to randomize the shares of the product */
		randomize, 
		/** Sharing the shares */
		share, 
		/** Revealing the reduced shares */
		reveal
	}

	/** The current phase of the current multiplication operation */
	protected MultiPhase multiPhase = MultiPhase.randomize;
	
	/** Boolean used to identify received messages that belong to the next phase and should be postponed */
	protected boolean currStep = false;

	/** Counter used to keep track of the number of shares exchanged */
	protected int sharesCount = 0;
	
	/** Counter used to keep track of the number of encrypted shares exchanged */
	private int encrSharesCount = 0;
	
	/** Messages received too early to be processed */
	protected ArrayList<Message> pendingMsgs = new ArrayList<Message> ();

	/** The ordered list of variables participating in public constraints */
	private String[] vars;
	
	/** Shares of the feasible values for my variables */
	private HashMap< String, BigInteger[] > solShares;
	
	/** The feasible solution found, if any */
	private HashMap<String, V> solution;

	/** The cost of the solution found */
	private U optCost;

	/** The matrix used to reduce the degree of a product polynomial. 
	 * 
	 * It is V^-1 * P * V, where V is the Vandermonde matrix, and P is a projection. 
	 */
	private Matrix reductor;
	
	/** The infinite cost value */
	private final AddableInteger plusinf;

	/** Constructor
	 * @param problem 	the overall problem
	 * @param params 	the parameters
	 */
	public MPC_DisCSP4 (DCOPProblemInterface<V, U> problem, Element params) {
		this(problem, params, new AddableInteger (1));
	}

	/** Constructor
	 * @param problem 	the overall problem
	 * @param params 	the parameters
	 * @param plusinf 	The infinite cost value
	 */
	@SuppressWarnings("unchecked")
	protected MPC_DisCSP4 (DCOPProblemInterface<V, U> problem, Element params, AddableInteger plusinf) {
		this.problem = problem;
		this.problem.setUtilClass((Class<U>) AddableInteger.class);
		this.modulo = new BigInteger (params.getAttributeValue("costModulo"));
		assert this.modulo.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 0 : "The modulo is too big";
		this.cryptoScheme = new PaillierCryptoScheme (Integer.parseInt(params.getAttributeValue("nbrBits")));
		this.plusinf = plusinf;
	}

	/** Constructor in stats gatherer mode
	 * @param params 	the parameters
	 * @param problem 	the overall problem
	 */
	public MPC_DisCSP4 (Element params, DCOPProblemInterface<V, U> problem) {
		this.problem = problem;
		this.modulo = null;
		this.cryptoScheme = null;
		this.solution = new HashMap<String, V> (problem.getNbrVars());
		this.plusinf = new AddableInteger (1);
	}
	
	/** @return the solution found */
	public HashMap<String, V> getSolution () {
		return this.solution;
	}
	
	/** @return the true cost of the solution found */
	public U getOptCost () {
		return this.optCost;
	}

	/** @see StatsReporter#reset() */
	public void reset() {
		this.solution = new HashMap<String, V> ();
	}

	/** @see StatsReporter#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	/** @see StatsReporter#setSilent(boolean) */
	public void setSilent(boolean silent) {
		this.silent = silent;
	}

	/** @see StatsReporter#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return Arrays.asList(
				AgentInterface.START_AGENT, 
				SharesMsg.SHARES_MSG_TYPE, 
				EncrSharesMsg.ENCR_SHARES_MSG_TYPE, 
				OneShareMsg.ONE_SHARE_MSG, 
				SolShareMsg.SOL_SHARE_MSG_TYPE
				);
	}

	/** @see StatsReporter#getStatsFromQueue(Queue) */
	public void getStatsFromQueue(Queue queue) {
		queue.addIncomingMessagePolicy(SOLUTION_MSG_TYPE, this);
	}

	/** @see StatsReporter#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(SOLUTION_MSG_TYPE)) { // in stats gatherer mode, the optimal assignment to a variable
			
			@SuppressWarnings("unchecked")
			SolutionMsg<V> msgCast = (SolutionMsg<V>) msg;
			String var = msgCast.getVar();
			
			if (var == null) { // infeasible
				if (! this.silent) 
					System.out.println("Optimal total cost: infinity");
				this.optCost = this.problem.getPlusInfUtility();
				
			} else { // feasible
				V val = msgCast.getVal();
				this.solution.put(var, val);
				if (! this.silent) 
					System.out.println("var `" + var + "' = " + val);
				
				// If all optimal assignments have been received, compute the corresponding cost
				if (this.solution.size() == this.problem.getNbrVars()) {
					this.optCost = this.problem.getUtility(this.solution).getUtility(0);
					if (! this.silent) 
						System.out.println("Optimal total cost: " + this.optCost);
				}
			}
			
			return;
		}
		
//		System.out.println(this.problem.getAgent() + " got " + msg);
		
		if (! this.started) 
			this.init();
		
		if (type.equals(SharesMsg.SHARES_MSG_TYPE)) {
			
			// Postpone this message if it belongs to a different (i.e. necessarily later) step of computation
			SharesMsg msgCast = (SharesMsg) msg;
			if (msgCast.step() != this.currStep) {
				this.pendingMsgs.add(msg);
				return;
			}
			
//			System.out.println(this.problem.getAgent() + " got " + msg);
			
			BigInteger[] shares = msgCast.getShares();
			
			if (this.phase.equals(Phase.SprimePrime)) { // currently sharing the S'' vectors
				
				assert msgCast.getAgent() >= 0 : "Receiving a share of 0 instead of a share of S'': " + Arrays.toString(shares);
				
//				System.out.println(this.problem.getAgent() + " got " + msgCast.getAgent() + "'s S'' share: " + Arrays.toString(shares));

				this.SprimePrime.put(msgCast.getAgent(), shares);
				
				if (this.SprimePrime.size() == this.nbrAgents) { // I have received all S'' shares
					
					// Pairwise multiply the first two S'' vectors
					this.Sprime = this.SprimePrime.remove(this.SprimePrime.firstKey());
					this.S = this.Sprime;
					BigInteger[] sPrimePrime = this.SprimePrime.remove(this.SprimePrime.firstKey());
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						this.Sprime[i] = this.Sprime[i].multiply(sPrimePrime[i]).mod(this.modulo);
					
//					System.out.println(this.problem.getAgent() + "'s raw S' (multiplication nbr " + (this.nbrAgents - this.SprimePrime.size() - 1) + "): " + Arrays.toString(this.Sprime));
					
					// Exchange shares of 0 to randomize the shares
					this.phase = Phase.vectorMultiplication;
					this.multiPhase = MultiPhase.randomize;
					this.shareVectorOfZeros();
				}
			}

			else if (this.phase.equals(Phase.vectorMultiplication)) { // pairwise multiplying vectors of shares
				
				if (this.multiPhase.equals(MultiPhase.randomize)) { // shares of 0 used to randomize a product
					
					assert msgCast.getAgent() < 0 : "Not shares of 0";
					
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						this.S[i] = this.S[i].add(shares[i]).mod(this.modulo);
					
					// If my vector has been fully randomized, share it
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						this.multiPhase = MultiPhase.share;
						this.shareVector(Arrays.asList(this.S));
					}
					
				} else if (this.multiPhase.equals(MultiPhase.share)) { // sharing a randomized product
					
					// Store the shares
					final int agentID = msgCast.getAgent();
					assert agentID >= 0 : "Receiving a share of 0 instead of a share of a product output: " + Arrays.toString(shares);
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						this.vectOfShares[i][agentID] = shares[i];
					
					// If have received all the shares, reduce the degree
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
//						System.out.println(this.problem.getAgent() + "'s shares of shares: " + Arrays.deepToString(this.vectOfShares));
						
						// Apply degree reduction
						for (int i = this.nbrSols - 1; i >= 0; i--) 
							this.vectOfShares[i] = new Matrix(this.vectOfShares[i], this.modulo).times(this.reductor).getArray()[0];
						
						// Reveal the reduced shares
						this.currStep = ! this.currStep;
						for (int a = this.nbrAgents - 1; a >= 0; a--) {
							
							BigInteger[] share = new BigInteger [this.nbrSols];
							for (int i = this.nbrSols - 1; i >= 0; i--) 
								share[i] = this.vectOfShares[i][a];
							this.queue.sendMessage(this.agents.get(a), new SharesMsg(this.myAgentID, share, this.currStep));
						}

						this.multiPhase = MultiPhase.reveal;
						this.processPending();
					}
					
				} else { // reduced shares of the product vector
					assert this.multiPhase.equals(MultiPhase.reveal);
					
					// Store the shares
					final int agentID = msgCast.getAgent();
					assert agentID >= 0 : "Receiving a share of 0 instead of a share of a product output: " + Arrays.toString(shares);
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						this.vectOfShares[i][agentID] = shares[i];

					// If have received all the shares, resolve them to reconstruct my reduced product vector
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
						// Resolve the shares
						for (int i = this.nbrSols - 1; i >= 0; i--) {
							BigInteger[] shares_i = this.vectOfShares[i];
							
							BigInteger si = BigInteger.ZERO;
							for (int j = this.nbrAgents - 1; j >= 0; j--) 
								si = si.add(shares_i[j].multiply(this.lagrange[j])).mod(this.modulo);
							
							this.S[i] = si;
						}
						
						// Move to the next multiplication (if any remains)
						this.nextMultiplication();
					}
				}
			}
			
		} else if (type.equals(EncrSharesMsg.ENCR_SHARES_MSG_TYPE)) {
			
			// Postpone this message if it belongs to a different (i.e. necessarily later) step of computation
			EncrSharesMsg msgCast = (EncrSharesMsg) msg;
			if (msgCast.step() != this.currStep) {
				this.pendingMsgs.add(msg);
				return;
			}
			
			// Retrieve the information from the message
			PaillierInteger[] encrVector = msgCast.getShares();
			PaillierPublicKey key = msgCast.getPublicKey();
			int id = msgCast.getAgentID();
			
			if (this.phase.equals(Phase.shuffling)) { // shuffling

				if (id >= 0) { // this is an agent's vector, which I must shuffle
					
					// If I am the last agent, I must wait until I have received all vectors before I start shuffling them
					if (this.myAgentID == this.nbrAgents - 1 && ++this.encrSharesCount < this.nbrAgents) 
						this.pendingMsgs.add(msg);
						
					else {
				
//						System.out.println(this.problem.getAgent() + " is shuffling " + this.agents.get(id) + "'s vector");
				
						// Shuffle and re-encrypt by adding encrypted shares of 0
						PaillierInteger[] encrVector2 = new PaillierInteger [this.nbrSols];
						for (int i = this.nbrSols - 1; i >= 0; i--) 
							encrVector2[this.permutation[i]] = encrVector[i].add(key.encrypt(this.vectOfShares[i][id]));
				
						// Pass the vector on to the next agent, or to its owner
						if (this.myAgentID == this.nbrAgents - 1) { // I'm the last agent
							this.queue.sendMessage(this.agents.get(id), new EncrSharesMsg (encrVector2, key, -1, ! this.currStep));
							if (this.encrSharesCount < 2 * this.nbrAgents - 1) { // not done shuffling
								this.processPending();
								return;
							}
						
						} else {
							this.queue.sendMessage(this.agents.get(this.myAgentID + 1), new EncrSharesMsg (encrVector2, key, id, this.currStep));
							if (++this.encrSharesCount < this.nbrAgents) // not done shuffling
								return;
						}
						
						// I'm done shuffling
						this.encrSharesCount = 0;
						this.currStep = ! this.currStep;
						this.processPending();
					}
				
				} else { // id < 0; this is my fully shuffled vector
				
//					System.out.println(this.problem.getAgent() + "'s shuffled encrypted S': " + Arrays.toString(encrVector));
				
					// Decrypt the vector
					this.Sprime = new BigInteger [this.nbrSols];
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						this.Sprime[i] = this.cryptoScheme.decrypt(encrVector[i]).mod(this.modulo);
					
//					System.out.println(this.problem.getAgent() + "'s shuffled vector S': " + Arrays.toString(this.Sprime));
					
					this.findFirstSolution();
				}

			} else { // un-shuffling
//				assert this.phase.equals(Phase.sol);

				if (id >= 0) { // this is an agent's vector, which I must un-shuffle

					// If I am the first agent, I must wait until I have received all vectors before I start un-shuffling them
					if (this.myAgentID == 0 && ++this.encrSharesCount < this.nbrAgents) 
						this.pendingMsgs.add(msg);
						
					else {
						
//						System.out.println(this.problem.getAgent() + " is un-shuffling " + this.agents.get(id) + "'s vector");
						
						// Un-shuffle and re-encrypt by adding encrypted shares of 0
						PaillierInteger[] encrVector2 = new PaillierInteger [this.nbrSols];
						for (int i = this.nbrSols - 1; i >= 0; i--) 
							encrVector2[this.inversePerm[i]] = encrVector[i].add(key.encrypt(this.vectOfShares[i][id]));
						
						// Pass the vector on to the previous agent, or to its owner
						if (this.myAgentID == 0) { // I'm the first agent
							this.queue.sendMessage(this.agents.get(id), new EncrSharesMsg (encrVector2, key, -1, ! this.currStep));
							if (this.encrSharesCount < 2 * this.nbrAgents - 1) { // not done un-shuffling
								this.processPending();
								return;
							}
							
						} else {
							this.queue.sendMessage(this.agents.get(this.myAgentID - 1), new EncrSharesMsg (encrVector2, key, id, this.currStep));
							if (++this.encrSharesCount < this.nbrAgents) // not done un-shuffling
								return;
						}
						
						// I'm done un-shuffling
						this.encrSharesCount = 0;
						this.currStep = ! this.currStep;
						this.processPending();
					}

				} else { // id < 0; this is my fully un-shuffled vector

//					System.out.println(this.problem.getAgent() + "'s un-shuffled encrypted S: " + Arrays.toString(encrVector));

					// Decrypt the vector
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						this.S[i] = this.cryptoScheme.decrypt(encrVector[i]).mod(this.modulo);

//					System.out.println(this.problem.getAgent() + "'s un-shuffled vector S: " + Arrays.toString(this.S));
					
					// Reveal each variable's value share to its owner
					this.revealSol();
				}
			}
			
		} else if (type.equals(OneShareMsg.ONE_SHARE_MSG)) { // message containing a single share
			
			// Postpone this message if it belongs to a different (i.e. necessarily later) step of computation
			OneShareMsg msgCast = (OneShareMsg) msg;
			if (msgCast.step() != this.currStep) {
				this.pendingMsgs.add(msg);
				return;
			}
			
			if (this.phase.equals(Phase.h)) { // computing h

				if (this.multiPhase.equals(MultiPhase.randomize)) { // a share of 0 used for randomization
					
					this.hi = this.hi.add(msgCast.getShare()).mod(this.modulo);
					
					// If my hi has been fully randomized, share it
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
//						System.out.println(this.problem.getAgent() + "'s randomized h[" + this.step + "] = " + this.hi);
						
						this.multiPhase = MultiPhase.share;
						this.share(this.hi);
					}
					
				} else if (this.multiPhase.equals(MultiPhase.share)) { // a share of a share

					// Store of the share
					this.shares[msgCast.getAgent()] = msgCast.getShare();
					
					// If have received all the shares, reduce the degree
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
//						System.out.println(this.problem.getAgent() + "'s shares of shares: " + Arrays.deepToString(this.shares[0]));
						
						// Apply degree reduction
						this.shares = new Matrix (this.shares, this.modulo).times(this.reductor).getArray()[0];
						
						// Reveal the reduced shares
						this.currStep = ! this.currStep;
						for (int a = this.nbrAgents - 1; a >= 0; a--) 
							this.queue.sendMessage(this.agents.get(a), new OneShareMsg(this.myAgentID, this.shares[a], this.currStep));

						this.multiPhase = MultiPhase.reveal;
						this.processPending();
					}
					
				} else {
					assert this.multiPhase.equals(MultiPhase.reveal);

					// Store the shares
					this.shares[msgCast.getAgent()] = msgCast.getShare();

					// If have received all the shares, resolve them to reconstruct my reduced hi
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
//						System.out.println(this.problem.getAgent() + "'s raw h[" + this.step + "] = " + this.hi);

						// Resolve the shares
						this.hi = BigInteger.ZERO;
						for (int a = this.nbrAgents - 1; a >= 0; a--) 
							this.hi = this.hi.add(this.shares[a].multiply(this.lagrange[a])).mod(this.modulo);
						
//						System.out.println(this.problem.getAgent() + "'s reduced h[" + this.step + "] = " + this.hi);
						
						// Compute Si
						this.S[this.step] = this.S[this.step].multiply(this.hi).mod(this.modulo);
						
//						System.out.println(this.problem.getAgent() + "'s S[" + this.step + "] = " + this.S[this.step]);

						// First exchange shares of 0 to randomize the shares
						this.phase = Phase.S;
						this.multiPhase = MultiPhase.randomize;
						this.share0();
					}
				}
			}
			
			else if (this.phase.equals(Phase.S)) { // computing S

				if (this.multiPhase.equals(MultiPhase.randomize)) { // a share of 0 used for randomization
					
					this.S[this.step] = this.S[this.step].add(msgCast.getShare()).mod(this.modulo);
					
					// If my Si has been fully randomized, share it
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
//						System.out.println(this.problem.getAgent() + "'s randomized S[" + this.step + "] = " + this.S[this.step]);
						
						this.multiPhase = MultiPhase.share;
						this.share(this.S[this.step]);
					}
					
				} else if (this.multiPhase.equals(MultiPhase.share)) { // a share of a share

					// Store of the share
					this.shares[msgCast.getAgent()] = msgCast.getShare();
					
					// If have received all the shares, reduce the degree
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;
						
//						System.out.println(this.problem.getAgent() + "'s shares of shares: " + Arrays.deepToString(this.shares[0]));
						
						// Apply degree reduction
						this.shares = new Matrix (this.shares, this.modulo).times(this.reductor).getArray()[0];
						
						// Reveal the reduced shares
						this.currStep = ! this.currStep;
						for (int a = this.nbrAgents - 1; a >= 0; a--) 
							this.queue.sendMessage(this.agents.get(a), new OneShareMsg(this.myAgentID, this.shares[a], this.currStep));

						this.multiPhase = MultiPhase.reveal;
						this.processPending();
					}
					
				} else if (this.multiPhase.equals(MultiPhase.reveal)) { // a share of the output of the multiplication
					
					// Store the shares
					this.shares[msgCast.getAgent()] = msgCast.getShare();

					// If have received all the shares, resolve them to reconstruct my reduced Si
					if (++this.sharesCount == this.nbrAgents) {
						this.sharesCount = 0;

//						System.out.println(this.problem.getAgent() + "'s raw S[" + this.step + "] = " + this.S[this.step]);

						// Resolve the shares
						this.Si = BigInteger.ZERO;
						for (int a = this.nbrAgents - 1; a >= 0; a--) 
							this.Si = this.Si.add(this.shares[a].multiply(this.lagrange[a])).mod(this.modulo);
						
						this.S[this.step] = this.Si;

//						System.out.println(this.problem.getAgent() + "'s reduced S[" + this.step + "] = " + this.S[this.step]);
						
						// Compute the next entry in S, if one remains
						if (++this.step < this.nbrSols) {
							
							// Multiply the shares
							this.hi = this.hi.multiply(BigInteger.ONE.subtract(this.Si)).mod(this.modulo);

//							System.out.println(this.problem.getAgent() + "'s h[" + this.step + "] = " + this.hi);

							// First exchange shares of 0 to randomize the shares
							this.phase = Phase.h;
							this.multiPhase = MultiPhase.randomize;
							this.share0();
							
						} else { // done computing S; encrypt it and send it to the last agent for un-shuffling
							this.step = 0;
							this.phase = Phase.sol;

//							System.out.println(this.problem.getAgent() + "'s S vector: " + Arrays.toString(this.S));

							// Encrypt my vector and send it to the last agent
							PaillierInteger[] encrVector = new PaillierInteger [this.nbrSols];
							if (this.myAgentID == this.nbrAgents - 1) // I am the last agent; no need to perform full encryption
								for (int i = this.nbrSols - 1; i >= 0; i--) 
									encrVector[i] = this.cryptoScheme.publicKey.fakeEncrypt(this.S[i]);
							else 
								for (int i = this.nbrSols - 1; i >= 0; i--) 
									encrVector[i] = this.cryptoScheme.publicKey.encrypt(this.S[i]);

//							System.out.println(this.problem.getAgent() + "'s encrypted S: " + Arrays.toString(encrVector));

							this.vectOfShares = this.newVectorOfSharesOf0();
							this.currStep = ! this.currStep;
							this.queue.sendMessage(this.agents.get(this.nbrAgents - 1), new EncrSharesMsg (encrVector, this.cryptoScheme.publicKey, this.myAgentID, this.currStep));
							this.processPending();
						}
					}
					
				}
			}
			
		} else if (type.equals(SolShareMsg.SOL_SHARE_MSG_TYPE)) { // a message containing shares of the optimal values for my variables
			
			// Postpone this message if it belongs to a different (i.e. necessarily later) step of computation
			SolShareMsg msgCast = (SolShareMsg) msg;
			if (msgCast.step() != this.currStep) {
				this.pendingMsgs.add(msg);
				return;
			}
			
			// If the message contains no shares, process it differently
			if (msgCast.getShares().isEmpty()) {
				
				if (msgCast.getSender() < 0) 
					this.terminate();
				else 
					this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));				
				
				return;
			}
			
			// Store the shares
			final int agentID = msgCast.getSender();
			for (Map.Entry<String, BigInteger> entry : msgCast.getShares().entrySet()) 
				this.solShares.get(entry.getKey())[agentID] = entry.getValue();
			
			// If have received all the shares, resolve them to reconstruct the optimal values to my variables
			if (++this.sharesCount == this.nbrAgents) {
				this.sharesCount = 0;
				
				for (Map.Entry< String, BigInteger[] > entry : this.solShares.entrySet()) {
					String var = entry.getKey();
					BigInteger[] shares = entry.getValue();
					
					// Resolve the shares
					BigInteger index = BigInteger.ZERO;
					for (int a = this.nbrAgents - 1; a >= 0; a--) 
						index = index.add(shares[a].multiply(this.lagrange[a])).mod(this.modulo);
					
					if (index.compareTo(BigInteger.valueOf(this.problem.getDomainSize(var))) >= 0) { // no solution found
						this.terminate();
						return;
					}
					
					// Store and report the solution
					V val = this.problem.getDomain(var)[index.intValue()];
					this.solution.put(var, val);
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (var, val));
					
//					System.out.println(var + " = " + val);
				}
				
				// Also notify the empty agents
				if (this.myAgentID == 0) 
					for (String agent : this.agents) 
						if (this.problem.getVariables(agent).isEmpty()) 
							this.queue.sendMessage(agent, new SolShareMsg (1, new HashMap<String, BigInteger> (), this.currStep));
				
				this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));				
			}			
		}
	}
	
	/** Terminates */
	protected void terminate() {

		// The first agent notifies the stats gatherer of the infeasibility
		if (this.myAgentID == 0) {
			this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (null, null));

			// Also notify the empty agents
			for (String agent : this.agents) 
				if (this.problem.getVariables(agent).isEmpty()) 
					this.queue.sendMessage(agent, new SolShareMsg (-1, new HashMap<String, BigInteger> (), this.currStep));
		}
		
		this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
	}

	/** Moves to the next vector multiplication, if any remains */
	protected void nextMultiplication() {
		
		if (! this.SprimePrime.isEmpty()) { // at least one multiplication remains
			
			// Multiplication
			BigInteger[] sPrimePrime = this.SprimePrime.remove(this.SprimePrime.firstKey());
			for (int i = this.nbrSols - 1; i >= 0; i--) 
				this.S[i] = this.S[i].multiply(sPrimePrime[i]).mod(this.modulo);
			
			// Exchange shares of 0 to randomize the shares
			this.multiPhase = MultiPhase.randomize;
			this.shareVectorOfZeros();
			
		} else { // no more multiplications; move on to shuffling S'
			
//			System.out.println(this.problem.getAgent() + "'s vector S':  " + Arrays.toString(this.S));

			if (this.nbrSols == 1) // no need to shuffle
				this.findFirstSolution();
				
			else {
				// Create a vector of shares of 0 used for randomization during the shuffling of vectors
				this.vectOfShares = this.newVectorOfSharesOf0();

				// Encrypt my vector and send it to the first agent
				PaillierInteger[] encrVector = new PaillierInteger [this.nbrSols];
				if (this.myAgentID == 0) // I am the first agent; no need to perform full encryption
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						encrVector[i] = this.cryptoScheme.publicKey.fakeEncrypt(this.S[i]);
				else 
					for (int i = this.nbrSols - 1; i >= 0; i--) 
						encrVector[i] = this.cryptoScheme.publicKey.encrypt(this.S[i]);

//				System.out.println(this.problem.getAgent() + "'s encrypted S': " + Arrays.toString(encrVector));

				this.currStep = ! this.currStep;
				this.phase = Phase.shuffling;
				this.queue.sendMessage(this.agents.get(0), new EncrSharesMsg (encrVector, this.cryptoScheme.publicKey, this.myAgentID, this.currStep));
				this.processPending();
			}
		}
	}

	/** Reveals to each agent all shares of its variables' optimal values */
	protected void revealSol () {
		
		for (String agent : this.agents) {
			
			// Skip this agent if it owns no variable
			if (this.problem.getVariables(agent).isEmpty()) 
				continue;
			
			// Compute a share of each variable's optimal value
			HashMap<String, BigInteger> solShares = new HashMap<String, BigInteger> (this.vars.length);
			for (int i = this.vars.length - 1; i >= 0; i--) {
				String var = this.vars[i];
				
				// Skip this variable if it is not owned by the current agent
				if (! agent.equals(this.problem.getOwner(var))) 
					continue;
				
				// Compute the share
				BigInteger val = BigInteger.ONE.negate();
				for (int j = this.nbrSols - 1; j >= 0; j--) 
					val = val.add(this.S[j].multiply(BigInteger.valueOf(1 + this.solIndex.get(j)[i]))).mod(this.modulo);
				
				solShares.put(var, val);
			}
			
			this.queue.sendMessage(agent, new SolShareMsg (this.myAgentID, solShares, this.currStep));
		}
	}
	
	/** Processes pending messages */
	protected void processPending () {
		
		for (Message msg : this.pendingMsgs) 
			this.queue.sendMessageToSelf(msg);
		this.pendingMsgs.clear();
	}

	/** Initiates the arithmetic circuit on S that singles out its first 1 entry */
	protected void findFirstSolution() {
		
		this.S = this.Sprime;
		this.phase = Phase.S;
		this.step = 0;
		
		this.hi = BigInteger.ONE;
		
//		System.out.println(this.problem.getAgent() + "'s hi[" + this.step + "] = " + this.hi);
		
		this.Si = this.S[0];
		
//		System.out.println(this.problem.getAgent() + "'s S[" + this.step + "] = " + this.Si);
		
		if (this.nbrSols == 1) { // we're done already

			// Reveal each variable's value share to its owner
			this.revealSol();

		} else {
			this.step++;
			this.hi = BigInteger.ONE.subtract(this.Si);
			
//			System.out.println(this.problem.getAgent() + "'s hi[" + this.step + "] = " + this.hi);
			
			// Perform the multiplication of shares
			this.S[this.step] = this.hi.multiply(this.S[this.step]);
			
			// First exchange shares of 0 to randomize the shares
			this.multiPhase = MultiPhase.randomize;
			this.share0();
		}
	}

	/** Parses the problem */
	@SuppressWarnings("unchecked")
	protected void init() {
		this.started = true;
		
		assert ! this.problem.maximize() : "Maximization problems are not supported";
		
		// Check if this agent is alone
		this.agents = new ArrayList<String> (this.problem.getAgents());
		if (this.agents.size() == 1) {
			
			// Join all constraints (if there are any)
			this.solution = new HashMap<String, V> ();
			List< ? extends UtilitySolutionSpace<V, AddableInteger> > allSpaces = (List<? extends UtilitySolutionSpace<V, AddableInteger>>) this.problem.getSolutionSpaces();
			if (! allSpaces.isEmpty()) {

				UtilitySolutionSpace<V, AddableInteger> privateSpace = allSpaces.get(0);
				allSpaces.remove(0);
				if (! allSpaces.isEmpty()) 
					privateSpace = privateSpace.join(allSpaces.toArray(new UtilitySolutionSpace [allSpaces.size()]));

				// Find optimal values for constrained variables
				ProjOutput<V, AddableInteger> projOutput = privateSpace.projectAll(false);

				// Check if the problem is infeasible
				if (projOutput.space.getUtility(0).compareTo(plusinf) >= 0) { // infeasible

					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (null, null));
					this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
					return;
				}

				ArrayList<V> values = projOutput.assignments.getUtility(0);
				if (values != null) {
					for (int i = privateSpace.getNumberOfVariables() - 1; i >= 0; i--) {

						String var = privateSpace.getVariable(i);
						V val = values.get(i);
						this.solution.put(var, val);

						this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (var, val));
					}
				}
			}
			
			// Assign random values to unconstrained variables
			for (String var : this.problem.getMyVars()) 
				if (! this.solution.containsKey(var)) 
					this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (var, this.problem.getDomain(var)[0]));
			
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			return;
		}

		// Get the agents
		Collections.sort(this.agents);
		this.nbrAgents = this.agents.size();
		// Make sure that the first agent is not empty
		for (int i = 0; i < this.nbrAgents; i++) {
			String agent = this.agents.get(i);
			if (! this.problem.getVariables(agent).isEmpty()) {
				if (i > 0) { // permute this agent with the first
					this.agents.set(i, this.agents.get(0));
					this.agents.set(0, agent);
				}
				break;
			}
		}
		this.agentXvalues = new BigInteger [nbrAgents];
		final String myName = this.problem.getAgent();
		for (int i = 0; i < nbrAgents; i++) {
			this.agentXvalues[i] = BigInteger.valueOf(i+1);
			if (myName.equals(this.agents.get(i))) 
				this.myAgentID = i;
		}
		
		// Get all public constraints and all private constraints
		UtilitySolutionSpace<V, AddableInteger> publicSpace = null;
		UtilitySolutionSpace<V, AddableInteger> privateSpace = null;
		final Set<String> myVars = this.problem.getMyVars();
		spaceLoop: for (UtilitySolutionSpace<V, AddableInteger> space : (List< UtilitySolutionSpace<V, AddableInteger> >) this.problem.getSolutionSpaces()) {
			
			String owner = space.getOwner();
			
			// Check whether this is a public space
			if ("PUBLIC".equals(owner)) {
				if (publicSpace == null) 
					publicSpace = space;
				else 
					publicSpace = publicSpace.join(space);
				
			} else { // not a public space
				
				final boolean mine = this.problem.getAgent().equals(owner); // whether I'm the only one who knows this space
				
				// Check if this space contains an external variable
				for (String var : space.getVariables()) {
					if (! myVars.contains(var)) { // external variable

						// Skip this space if it's not mine and it involves a variable owned by another agent whose name is before mine in lexicographic order
						if (!mine) 
							for (String var2 : space.getVariables()) 
								if (this.problem.getOwner(var2).compareTo(myName) < 0) 
									continue spaceLoop;
					}
				}
				
				// If we get here, this is a private space I am responsible of
				if (privateSpace == null) 
					privateSpace = (UtilitySolutionSpace<V, AddableInteger>) space;
				else 
					privateSpace = privateSpace.join((UtilitySolutionSpace<V, AddableInteger>) space);
			}
		}
		
		Class<? extends V[]> classOfDom = (Class<? extends V[]>) this.problem.getDomain(this.problem.getVariables().iterator().next()).getClass();
		if (publicSpace == null) 
			publicSpace = new ScalarHypercube<V, AddableInteger> (new AddableInteger (0), AddableInteger.PlusInfinity.PLUS_INF, classOfDom);
		if (privateSpace == null) 
			privateSpace = new ScalarHypercube<V, AddableInteger> (new AddableInteger (0), AddableInteger.PlusInfinity.PLUS_INF, classOfDom);
		
		// Initialize the solution and solution shares
		final int nbrIntVars = this.problem.getNbrIntVars();
		this.solShares = new HashMap< String, BigInteger[] > (nbrIntVars);
		for (String var : this.problem.getMyVars()) 
			this.solShares.put(var, new BigInteger [this.nbrAgents]);
		this.solution = new HashMap<String, V> (nbrIntVars);
		
		// Order the variables lexicographically
		final int nbrVars = this.problem.getNbrVars();
		this.vars = new String [nbrVars];
		this.problem.getVariables().toArray(this.vars);
		Arrays.sort(this.vars);
		V[][] doms = (V[][]) Array.newInstance(classOfDom, nbrVars);
		for (int i = 0; i < nbrVars; i++) 
			doms[i] = this.problem.getDomain(this.vars[i]);
		
		// Construct a vector containing all the publicly feasible solutions
		List<BigInteger> sPrimePrime = new ArrayList<BigInteger> ();
		this.solIndex = new ArrayList< int[] > ();
		SparseIterator<V, AddableInteger> iter = publicSpace.sparseIter(this.vars, doms);
		AddableInteger publicCost = null;
		while ( (publicCost = iter.nextUtility(plusinf, true)) != null) {
		
			// Record the feasibility of this solution
			AddableInteger cost = privateSpace.getUtility(this.vars, iter.getCurrentSolution());
			this.recordCandidateSol(publicCost, cost, sPrimePrime);
			
			// Record the value index of each variable
			V[] values = iter.getCurrentSolution();
			int[] valIndex = new int [nbrVars];
			for (int i = 0; i < nbrVars; i++) {
				V[] dom = doms[i];
				V val = values[i];
				for (int j = dom.length - 1; j >= 0; j--) {
					if (dom[j].equals(val)) {
						valIndex[i] = j;
						break;
					}
				}
			}
			this.solIndex.add(valIndex);
		}
		this.nbrSols = sPrimePrime.size();
		
		if (this.nbrSols == 0) { // infeasible
			if (this.myAgentID == 0) 
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (null, null));
			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));
			return;
		}
		
//		System.out.println(this.problem.getAgent() + "'s vector S'': " + sPrimePrime);
		
		// Share my secret vector with all agents
		this.deg = (int) ((nbrAgents - 1) / 2); // the degree of the polynomials
		this.shareVector(sPrimePrime);
		
		// Choose a random permutation that will be used to shuffle the vectors
		this.permutation = new int [this.nbrSols];
		this.inversePerm = new int [this.nbrSols];
		for (int i = this.nbrSols - 1; i >= 0; i--) 
			this.permutation[i] = i;
		for (int i = this.nbrSols - 1; i >= 1; i--) {
			int j = this.rand.nextInt(i + 1);
			
			// Swap the entries at indexes i and j
			int tmp = this.permutation[j];
			this.permutation[j] = this.permutation[i];
			this.permutation[i] = tmp;
		}
		for (int i = this.nbrSols - 1; i >= 0; i--) 
			this.inversePerm[this.permutation[i]] = i;
		
		this.vectOfShares = this.newVectorOfSharesOf0();
		this.shares = new BigInteger [this.nbrAgents];

		// Compute the Lagrange coefficients
		this.lagrange = new BigInteger [this.nbrAgents];
		for (int i = this.nbrAgents - 1; i >= 0; i--) {
			BigInteger xi = this.agentXvalues[i];
			
			BigInteger coeff = BigInteger.ONE;
			for (int j = this.nbrAgents - 1; j >= 0; j--) {
				if (i != j) {
					BigInteger xj = this.agentXvalues[j];
					coeff = coeff.multiply(xj).multiply(xj.subtract(xi).modInverse(this.modulo)).mod(this.modulo);
				}
			}
			
			this.lagrange[i] = coeff;
		}
		
//		System.out.println(this.problem.getAgent() + "'s Lagrange coefficients: " + Arrays.toString(this.lagrange));
		
		// Compute the reduction matrix
		
		// Build the Vandermonde
		BigInteger[][] vandermonde = new BigInteger [nbrAgents][nbrAgents];
		for (int i = nbrAgents - 1; i >= 0; i--) 
			for (int j = nbrAgents - 1; j >= 0; j--) 
				vandermonde[i][j] = this.agentXvalues[j].modPow(BigInteger.valueOf(i), this.modulo);
//		System.out.println("Vandermonde: " + Arrays.deepToString(vandermonde));
		
		// Compute the inverse of the Vandermonde
		Matrix vandermondeInv = new Matrix(vandermonde, this.modulo).inverse();
//		System.out.println("Vandermonde inverse: " + vandermondeInv);
//		System.out.println("Product: " + vandermondeInv.times(new Matrix (vandermonde, this.modulo)));
		
		// Build the projection matrix
		Matrix proj = Matrix.identity(nbrAgents, nbrAgents, this.modulo);
		assert this.deg >= 0;
		for (int i = deg + 1; i < nbrAgents; i++)
			proj.set(i, i, BigInteger.ZERO);
//		System.out.println("Projection: " + proj);
		
		this.reductor = vandermondeInv.times(proj).times(new Matrix (vandermonde, this.modulo));
//		System.out.println("Reductor: " + this.reductor);
	}

	/** Records a candidate solution
	 * @param publicCost 	the public cost of the candidate solution
	 * @param privateCost 	the private cost of the candidate solution
	 * @param sPrimePrime 	the vector of private costs of candidate solutions
	 */
	protected void recordCandidateSol(AddableInteger publicCost, AddableInteger privateCost, List<BigInteger> sPrimePrime) {
		
		assert publicCost.equals(new AddableInteger (0)) : "Cost value " + publicCost + " should be either 0 or infinity";
		assert privateCost.equals(new AddableInteger (0)) || privateCost.equals(AddableInteger.PlusInfinity.PLUS_INF) : "Cost value " + privateCost + " should be either 0 or infinity";
		sPrimePrime.add(BigInteger.ONE.subtract(BigInteger.valueOf(privateCost.intValue()).min(BigInteger.ONE))); // 1 = feasibility; 0 = infeasibility
	}

	/** @return vector of shares of 0 */
	private BigInteger[][] newVectorOfSharesOf0() {
		
		BigInteger[][] shares = new BigInteger [this.nbrSols][this.nbrAgents];
		
		BigInteger[] poly;
		BigInteger[] share;
		final int nbrAgentsMin1 = this.nbrAgents - 1;
		final BigInteger[] agentXvalues = this.agentXvalues;
		int a;
		
		for (int i = this.nbrSols - 1; i >= 0; i--) {
			
			poly = this.randPoly (BigInteger.ZERO);
			
			// Evaluate the polynomial
			share = shares[i];
			for (a = nbrAgentsMin1; a >= 0; a--) 
				share[a] = this.evaluate(poly, agentXvalues[a]);
		}
		return shares;
	}
	
	/** Creates and sends share of 0 */
	protected void share0 () {

		this.currStep = ! this.currStep;

		BigInteger[] poly = this.randPoly (BigInteger.ZERO);

		// Send its share to each agent
		for (int a = 0; a < nbrAgents; a++) {
			
			// Evaluate the polynomial
			BigInteger share = this.evaluate(poly, this.agentXvalues[a]);

			this.queue.sendMessage(this.agents.get(a), new OneShareMsg (this.myAgentID, share, this.currStep));
		}
		
		this.processPending();
	}

	/** Shares a vector full of zeros */
	protected void shareVectorOfZeros () {
		
		this.currStep = ! this.currStep;

		BigInteger[][] shares = this.newVectorOfSharesOf0();
		
		// Send its share to each agent
		for (int a = 0; a < nbrAgents; a++) {
			
			BigInteger[] share = new BigInteger [this.nbrSols];
			for (int i = this.nbrSols - 1; i >= 0; i--) 
				share[i] = shares[i][a];
			
			this.queue.sendMessage(this.agents.get(a), new SharesMsg (-1, share, this.currStep));
		}
		
		this.processPending();
	}
	
	/** Shares a number with all agents
	 * @param nbr 	the secret number
	 */
	private void share (BigInteger nbr) {
		
		this.currStep = ! this.currStep;

		// Create a random secret polynomial
		BigInteger[] poly = this.randPoly(nbr);
		
		// Send its share to each agent
		for (int a = 0; a < nbrAgents; a++) {
			
			// Evaluate the polynomial
			BigInteger share = this.evaluate(poly, this.agentXvalues[a]);
			
			this.queue.sendMessage(this.agents.get(a), new OneShareMsg (this.myAgentID, share, this.currStep));
		}
		
		this.processPending();
	}

	/** Shares a secret vector with all agents
	 * @param vector 	the secret vector
	 */
	private void shareVector (List<BigInteger> vector) {
		
		this.currStep = ! this.currStep;

		// Create a vector of random secret polynomials
		final int nbrSols = this.nbrSols;
		assert this.deg >= 0;
		BigInteger[][] polyVect = new BigInteger [nbrSols][deg + 1];
		for (int i = this.nbrSols - 1; i >= 0; i--) 
			polyVect[i] = this.randPoly (vector.get(i));
		
		BigInteger[] share;
		final BigInteger[] agentXvalues = this.agentXvalues;
		int i;
		
		// Send its share to each agent
		for (int a = nbrAgents - 1; a >= 0; a--) {
			
			// Evaluate the polynomials
			share = new BigInteger [nbrSols];
			for (i = 0; i < nbrSols; i++) 
				share[i] = this.evaluate(polyVect[i], agentXvalues[a]);
			
			this.queue.sendMessage(this.agents.get(a), new SharesMsg (this.myAgentID, share, this.currStep));
		}
		
		this.processPending();
	}

	/** Creates a random polynomial
	 * @param at0 	value of the polynomial at 0
	 * @return a random polynomial
	 */
	private BigInteger[] randPoly(BigInteger at0) {
		
		assert this.deg >= 0;
		BigInteger[] poly = new BigInteger [deg + 1];
		poly[0] = at0;
		for (int j = this.deg; j >= 1; j--) 
			poly[j] = BigInteger.valueOf(this.rand.nextInt(this.modulo.intValue()));
		
		return poly;
	}

	/** Evaluates a polynomial P at x
	 * @param poly 	the polynomial P
	 * @param x 	the value
	 * @return P(x)
	 */
	private BigInteger evaluate (BigInteger[] poly, BigInteger x) {
		
		BigInteger val = poly[0];
		for (int exp = poly.length - 1; exp > 0; exp--) 
			val = val.add(poly[exp].multiply(x.modPow(BigInteger.valueOf(exp), this.modulo))).mod(this.modulo);
		
		return val;
	}
	
}
