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

package frodo2.algorithms.mpc_discsp;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.jdom2.Element;

import frodo2.algorithms.AgentInterface;
import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.AddableInteger;
import frodo2.solutionSpaces.DCOPProblemInterface;

/** The MPC-DisWCSP4 algorithm for DisWCSP with privacy guarantees
 * 
 * MPC-DisWCSP4 is MPC-DisCSP4, with the weak extension to DisWCSPs initially proposed for MPC-DisCSP2 in the following paper: 
 * 
 * Marius-Calin Silaghi and Debasis Mitra. Distributed constraint satisfaction and optimization with privacy enforcement. 
 * In Proceedings of the 2004 IEEE/WIC/ACM International Conference on Intelligent Agent Technology (IAT'04), 
 * pages 531-535, Beijing, China, September 20-24 2004. IEEE Computer Society Press.
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
 * 
 * @todo Important performance improvement: before un-shuffling, check whether it is necessary by checking whether the S vector sums up to 1. 
 * @todo Add support for maximization problems and costs/utilities with unrestricted signs. 
 */
public class MPC_DisWCSP4 < V extends Addable<V>, U extends Addable<U> > extends MPC_DisCSP4<V, U> {
	
	/** In private constraints, infinite costs are replaced with this value */
	private final AddableInteger infiniteCost;
	
	/** The maximum total cost of any solution */
	private final int maxCost;
	
	/** The cost for which we are looking for all solutions */
	private int targetCost = 0;

	/** Constructor
	 * @param problem 	the overall problem
	 * @param params 	the parameters
	 */
	public MPC_DisWCSP4 (DCOPProblemInterface<V, U> problem, Element params) {
		super (problem, params, AddableInteger.PlusInfinity.PLUS_INF);
		this.infiniteCost = new AddableInteger (Integer.parseInt(params.getAttributeValue("infiniteCost")));
		this.maxCost = Integer.parseInt(params.getAttributeValue("maxTotalCost"));
	}

	/** Constructor in stats gatherer mode
	 * @param params 	the parameters
	 * @param problem 	the overall problem
	 */
	public MPC_DisWCSP4 (Element params, DCOPProblemInterface<V, U> problem) {
		super(params, problem);
		this.infiniteCost = null;
		this.maxCost = 0;
	}
	
	/** @see MPC_DisCSP4#notifyIn(Message) */
	@Override
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(SOLUTION_MSG_TYPE)) { // in stats gatherer mode, the optimal assignment to a variable
			
			super.notifyIn(msg);
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
			
			BigInteger[] shares = msgCast.getShares();
			
			if (this.phase.equals(Phase.SprimePrime)) { // currently computing the vector S'
				
				assert msgCast.getAgent() >= 0 : "Receiving a share of 0 instead of a share of S'': " + Arrays.toString(shares);

				// Add all shares to compute the total cost of each publicly feasible solution
				if (this.Sprime == null) 
					this.Sprime = shares;
				else 
					for (int i = shares.length - 1; i >= 0; i--) 
						this.Sprime[i] = this.Sprime[i].add(shares[i]).mod(this.modulo);

				if (++this.sharesCount == this.nbrAgents) {
					this.sharesCount = 0;

//					System.out.println(this.problem.getAgent() + "'s vector S': " + Arrays.toString(this.Sprime));

					if (this.nbrSols == 1) { // no need to shuffle
						this.S = new BigInteger [this.nbrSols];
						this.findAllSolutions (0);
						
					} else {
						// Encrypt my vector and send it to the first agent
						PaillierInteger[] encrVector = new PaillierInteger [this.nbrSols];
						if (this.myAgentID == 0) // I am the first agent; no need to perform full encryption
							for (int i = this.nbrSols - 1; i >= 0; i--) 
								encrVector[i] = this.cryptoScheme.publicKey.fakeEncrypt(this.Sprime[i]);
						else 
							for (int i = this.nbrSols - 1; i >= 0; i--) 
								encrVector[i] = this.cryptoScheme.publicKey.encrypt(this.Sprime[i]);

//						System.out.println(this.problem.getAgent() + "'s encrypted S': " + Arrays.toString(encrVector));

						this.currStep = ! this.currStep;
						this.phase = Phase.shuffling;
						this.queue.sendMessage(this.agents.get(0), new EncrSharesMsg (encrVector, this.cryptoScheme.publicKey, this.myAgentID, this.currStep));
						this.processPending();
					}
				}
			}

			else 
				super.notifyIn(msg);
			
		} else 
			super.notifyIn(msg);
	}
	
	/** Moves to the next target cost, or terminates if the maximum cost has been reached */
	@Override
	protected void terminate() {
			
//		System.out.println(this.problem.getAgent() + " found no solution of cost " + this.targetCost);

		// Check whether we have exhausted all possible cost values
		if (++this.targetCost == this.maxCost) {

//			System.out.println(this.problem.getAgent() + " has found the problem infeasible");

			// The first agent notifies the stats gatherer of the infeasibility
			if (this.myAgentID == 0) {
				this.queue.sendMessage(AgentInterface.STATS_MONITOR, new SolutionMsg<V> (null, null));
				
				// Also notify the empty agents
				for (String agent : this.agents) 
					if (this.problem.getVariables(agent).isEmpty()) 
						this.queue.sendMessage(agent, new SolShareMsg (1, new HashMap<String, BigInteger> (), this.currStep));
			}

			this.queue.sendMessageToSelf(new Message (AgentInterface.AGENT_FINISHED));

		} else { // find a solution with incremented cost
			
			// Also notify the empty agents
			if (this.myAgentID == 0) 
				for (String agent : this.agents) 
					if (this.problem.getVariables(agent).isEmpty()) 
						this.queue.sendMessage(agent, new SolShareMsg (-1, new HashMap<String, BigInteger> (), this.currStep));

			this.findAllSolutions(this.targetCost);
		}
	}

	/** @see MPC_DisCSP4#nextMultiplication() */
	@Override
	protected void nextMultiplication () {
		
		this.step++;
		if (this.step == this.targetCost) // skip this step
			this.step++;
		if (this.step <= this.maxCost) { // at least one multiplication remains
			
//			System.out.println(this.problem.getAgent() + "'s step = " + this.step + " / " + this.maxCost);

			// Perform the multiplication of shares
			if (this.step < this.targetCost) 
				for (int i = this.nbrSols - 1; i >= 0; i--) 
					this.S[i] = this.S[i].multiply(this.Sprime[i].subtract(BigInteger.valueOf(this.step))).mod(this.modulo);
			else 
				for (int i = this.nbrSols - 1; i >= 0; i--) 
					this.S[i] = this.S[i].multiply(BigInteger.valueOf(this.step).subtract(this.Sprime[i])).mod(this.modulo);
			
			// First exchange shares of 0 to randomize the shares
			this.multiPhase = MultiPhase.randomize;
			this.shareVectorOfZeros();
			
		} else { // no more multiplications; move on to computing S
			this.phase = Phase.S;
			this.step = 0;
			
//			System.out.println(this.problem.getAgent() + "'s final p: " + Arrays.toString(this.S));
			
			this.hi = BigInteger.ONE;
			
//			System.out.println(this.problem.getAgent() + "'s hi[" + this.step + "] = " + this.hi);
			
			this.Si = this.S[0];
			
//			System.out.println(this.problem.getAgent() + "'s S[" + this.step + "] = " + this.Si);
			
			if (this.nbrSols == 1) { // we're done already

				// Reveal each variable's value share to its owner
				this.revealSol();

			} else {
				this.step++;
				this.hi = BigInteger.ONE.subtract(this.Si);
				
//				System.out.println(this.problem.getAgent() + "'s hi[" + this.step + "] = " + this.hi);
				
				// Perform the multiplication of shares
				this.S[this.step] = this.hi.multiply(this.S[this.step]);
				
				// First exchange shares of 0 to randomize the shares
				this.multiPhase = MultiPhase.randomize;
				this.share0();
			}
		}
	}
	
	
	/** @see MPC_DisCSP4#findFirstSolution() */
	@Override
	protected void findFirstSolution() {
		this.findAllSolutions(0);
	}

	/** Starts the computation of the p vector, which will have a share of 1 for each solution of the input cost
	 * @param cost 	the cost of interest
	 */
	private void findAllSolutions(final int cost) {
		
//		System.out.println(this.problem.getAgent() + " is looking for a solution of cost " + cost);
		
		this.phase = Phase.vectorMultiplication;
		
		// Initialize the vector p by filling it with 1 / ( cost! (maxCost - cost)! )
		BigInteger fill = BigInteger.ONE;
		for (BigInteger i = BigInteger.ONE; i.intValue() <= cost; i = i.add(BigInteger.ONE)) // multiplies by 1/1 * 1/2 * ... * 1/cost
			fill = fill.multiply(i.modInverse(this.modulo)).mod(this.modulo);
		for (BigInteger i = BigInteger.valueOf(this.maxCost - cost); i.intValue() >= 1; i = i.subtract(BigInteger.ONE)) // multiplies by 1/(maxCost-cost) * ... * 1/2 * 1/1
			fill = fill.multiply(i.modInverse(this.modulo)).mod(this.modulo);
		Arrays.fill(this.S, fill);
		
		// Step 0: multiply with (S' - 0)
		this.step = 0;
		if (cost == this.step) // skip this step
			this.step++;
		if (this.step < cost) 
			for (int i = this.nbrSols - 1; i >= 0; i--) 
				this.S[i] = this.S[i].multiply(this.Sprime[i].subtract(BigInteger.valueOf(this.step))).mod(this.modulo);
		else 
			for (int i = this.nbrSols - 1; i >= 0; i--) 
				this.S[i] = this.S[i].multiply(BigInteger.valueOf(this.step).subtract(this.Sprime[i])).mod(this.modulo);
		
		// Step 1: multiply with (S' - 1)
		this.step++;
		if (cost == this.step) // skip this step
			this.step++;
		if (this.step < cost) 
			for (int i = this.nbrSols - 1; i >= 0; i--) 
				this.S[i] = this.S[i].multiply(this.Sprime[i].subtract(BigInteger.valueOf(this.step))).mod(this.modulo);
		else
			for (int i = this.nbrSols - 1; i >= 0; i--) 
				this.S[i] = this.S[i].multiply(BigInteger.valueOf(this.step).subtract(this.Sprime[i])).mod(this.modulo);

		// First exchange shares of 0 to randomize the shares
		this.multiPhase = MultiPhase.randomize;
		this.shareVectorOfZeros();
	}

	/** @see MPC_DisCSP4#init() */
	@Override
	protected void init() {
		super.init();
		this.S = new BigInteger [this.nbrSols];
	}

	/** @see MPC_DisCSP4#recordCandidateSol(AddableInteger, AddableInteger, java.util.List) */
	@Override
	protected void recordCandidateSol(AddableInteger publicCost, AddableInteger privateCost, List<BigInteger> sPrimePrime) {
		
		assert privateCost.intValue() >= 0 : "Negative costs unsupported";
		if (this.myAgentID == 0) { // by convention, only the first agent counts the additional cost imposed by public constraints
			assert publicCost.intValue() >= 0 : "Negative costs unsupported";
			privateCost = privateCost.add(publicCost);
		}
		sPrimePrime.add(BigInteger.valueOf(privateCost.min(this.infiniteCost).intValue()));
	}

}
