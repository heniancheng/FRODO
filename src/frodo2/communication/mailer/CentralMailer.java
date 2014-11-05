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

/** Special type of communication method that enforces that only one agent is awake at a time */
package frodo2.communication.mailer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jdom2.Element;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.AgentInterface;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWrapper;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.daemon.Daemon;

/** Centralized mail man that enforces that only one agent is awake at a time, which is useful to measure distributed runtime. 
 * @author Thomas Leaute
 */
public class CentralMailer extends Thread {

	/** The queue associated with a specific agent */
	protected class FakeQueue extends Queue {

		/** The agent name */
		protected String agent;

		/** Counts the total number of messages having been sent by this queue */
		protected int messageCounter;

		/** The last time we started measuring time again after interrupting the measurement */ 
		private long startTime = 0;

		/** Functions as a timer for the agent that owns this queue */
		protected long time = Long.MIN_VALUE;

		/** If \c false, this queue's time will always remain frozen at its initial value */
		private final boolean updateTime;

		/** Constructor 
		 * @param agent 		the agent name 
		 */
		protected FakeQueue (String agent) {
			this(agent, false);
		}

		/** Constructor 
		 * @param agent 		the agent name 
		 * @param updateTime 	If \c false, this queue's time will always remain frozen at its initial value
		 */
		protected FakeQueue (String agent, boolean updateTime) {
			super (measuringMsgs, false);
			this.updateTime = updateTime;

			this.agent = agent;

			inPolicies = new HashMap <String, ArrayList< IncomingMsgPolicyInterface<String> > > ();
			ArrayList< IncomingMsgPolicyInterface<String> > policies = new ArrayList< IncomingMsgPolicyInterface<String> >();
			inPolicies.put(ALLMESSAGES, policies);

			outPolicies = new HashMap <String, ArrayList< OutgoingMsgPolicyInterface<String> > > ();
			ArrayList< OutgoingMsgPolicyInterface<String> > policiesOut = new ArrayList< OutgoingMsgPolicyInterface<String> >();
			outPolicies.put(ALLMESSAGES, policiesOut);

			inputs = new HashSet <QueueInputPipeInterface> ();
			outputs = new HashMap <Object, QueueOutputPipeInterface> ();
		}

		/** Does exactly the same as the superclass, except that synchronizing over a lock is not necessary
		 * @see Queue#addIncomingMessagePolicy(String, IncomingMsgPolicyInterface) 
		 */
		@Override
		public void addIncomingMessagePolicy (String type, IncomingMsgPolicyInterface <String> policy) {

			ArrayList< IncomingMsgPolicyInterface<String> > policies = inPolicies.get(type);

			if (policies == null) { // We don't know this message type yet
				policies = new ArrayList< IncomingMsgPolicyInterface<String> >();
				inPolicies.put(type, policies);
				policies.add(policy);

			} else if (! policies.contains(policy)) 
				policies.add(policy); 

			policy.setQueue(this);
		}

		/** Does exactly the same as the superclass, except that synchronizing over a lock is not necessary
		 * @see Queue#addOutgoingMessagePolicy(String, OutgoingMsgPolicyInterface) 
		 */
		@Override
		public void addOutgoingMessagePolicy (String type, OutgoingMsgPolicyInterface <String> policy) {

			ArrayList< OutgoingMsgPolicyInterface<String> > policies = outPolicies.get(type);

			if (policies == null) { // We don't know this message type yet
				policies = new ArrayList< OutgoingMsgPolicyInterface<String> >();
				outPolicies.put(type, policies);
				policies.add(policy);

			} else if (! policies.contains(policy)) 
				policies.add(policy); 

			policy.setQueue(this);
		}

		/** 
		 * @see Queue#addOutputPipe(Object, QueueOutputPipeInterface) 
		 */
		@Override
		public void addOutputPipe (Object recipient, QueueOutputPipeInterface output) { }

		/** Adds the input message wrapper to the buffer of messages waiting to be put into the central priority queue
		 * @see Queue#addToInbox(MessageWrapper) 
		 */
		@Override
		public void addToInbox (MessageWrapper msg) {
			outbox.add(msg);
		}

		/** Removes this agent from the lists of destinations of all messages in the central priority queue */
		@Override
		public void cleanQueue() {
			for (Iterator<MessageWrapper> iter = orderedQueue.iterator(); iter.hasNext(); ) {
				MessageWrapper wrap = iter.next();

				// Check if the agent is in the list of destinations
				Collection<Object> destinations = wrap.getDestinations();
				if (destinations.contains(this.agent)) {

					destinations.remove(agent);

					// If the list of destinations becomes empty, discard the message altogether
					if (destinations.isEmpty()) 
						iter.remove();
				}
			}
		}

		/** @see Queue#end() */
		@Override
		public void end () {
			for (QueueInputPipeInterface input : inputs) 
				input.close();
			if (measureMsgs) 
				monitor.close();
		}

		/** @see Queue#getCurrentTime() */
		@Override
		public long getCurrentTime() {
			return time + (System.nanoTime() - startTime);
		}

		/** @see Queue#getInboxSize() */
		@Override
		public int getInboxSize() {

			// Check the time at which this method was called
			final long callTime = this.getCurrentTime();

			// Loop through the message queue, counting the messages that have a timestamp lower than callTime, and that include this agent as destination
			// NOTE: we have to iterate through the whole queue, because the iterator does not guarantee any order
			int out = 0;
			for (MessageWrapper msgWrapper : orderedQueue) 
				if (msgWrapper.getTime() <= callTime && msgWrapper.getDestinations().contains(this.agent)) 
					out++;

			return out;
		}

		/** Does the same as the superclass, but without the need to synchronize on a lock
		 * @see Queue#notifyInListeners(Message) 
		 */
		@Override
		protected void notifyInListeners (Message msg) {

			// First notify the policies listening for ALL messages
			ArrayList< IncomingMsgPolicyInterface<String> > policies = new ArrayList< IncomingMsgPolicyInterface<String> > (inPolicies.get(ALLMESSAGES));
			for (IncomingMsgPolicyInterface<String> module : policies) // iterate over a copy in case a listener wants to add more listeners
				module.notifyIn(msg);

			// Notify the listeners for this message type, if any
			ArrayList< IncomingMsgPolicyInterface<String> > modules = inPolicies.get(msg.getType());
			if (modules != null) {
				policies = new ArrayList< IncomingMsgPolicyInterface<String> > (modules);
				for (IncomingMsgPolicyInterface<String> module : policies) // iterate over a copy in case a listener wants to add more listeners
					module.notifyIn(msg);
			}
		}

		/** Does the same as the superclass, but without the need to synchronize on a lock
		 * @see Queue#notifyOutListeners(Message) 
		 */
		@Override
		protected boolean notifyOutListeners (Message msg) {

			boolean discard = false;

			// Notify the outgoing message listeners registered for all messages
			for (OutgoingMsgPolicyInterface<String> module : this.outPolicies.get(ALLMESSAGES)) 
				if (module.notifyOut(msg) == OutgoingMsgPolicyInterface.Decision.DISCARD) 
					discard = true;

			// Notify the listeners registered for this message's type
			ArrayList< OutgoingMsgPolicyInterface<String> > modules = this.outPolicies.get(msg.getType());
			if (modules != null) 
				for (OutgoingMsgPolicyInterface<String> module : modules) 
					if (module.notifyOut(msg) == OutgoingMsgPolicyInterface.Decision.DISCARD) 
						discard = true;

			return discard;
		}

		/** @see Queue#recordStats(Object, Message) */
		@Override
		protected void recordStats (Object to, Message msg) {

			// Skip this message if the queue is the controller's
			if (this.agent == AgentInterface.STATS_MONITOR) 
				return;

			super.recordStats(to, msg);
		}

		/** @see Queue#resetStats() */
		@Override
		public void resetStats () {
			super.resetStats();
			this.time = Long.MIN_VALUE;
		}

		/** @see Queue#sendMessage(Object, Message) */
		@Override
		public void sendMessage(Object to, Message msg) {

			// Discard the message if one of the outgoing listeners requires it
			if (this.notifyOutListeners(msg)) 
				return;

			MessageWrapper msgWrap = new MessageWrapper(msg);
			if(this.problem != null) 
				msgWrap.setNCCCs(this.problem.getNCCCs());
			msgWrap.setTime(this.getCurrentTime());
			msgWrap.setDestination(to);
			msgWrap.setMessageCounter(++messageCounter);

			sendMessage(to, msgWrap);
		}

		/** @see Queue#sendMessage(Object, MessageWrapper) */
		@Override
		public void sendMessage (Object to, MessageWrapper msgWrap) {
			
			// Fake the serialization of the message if it is not virtual
			if (! this.agent.equals(to)) 
				msgWrap.getMessage().fakeSerialize();
			
			// Deliver the message
			assert queues.containsKey(to) : "Unknown destination: " + to;
			queues.get(to).addToInbox(msgWrap);
		}

		/** @see Queue#sendMessageToMulti(Collection, Message) */
		@Override
		public <T extends Object> void sendMessageToMulti (Collection <T> recipients, Message msg) {

			// Discard the message if its list of recipients is empty, or if one of the outgoing listeners requires it
			if (recipients.isEmpty() || this.notifyOutListeners(msg)) 
				return;

			MessageWrapper msgWrap = new MessageWrapper(msg);
			if(this.problem != null) 
				msgWrap.setNCCCs(this.problem.getNCCCs());
			if (msg.getType() != AgentInterface.START_AGENT) 
				msgWrap.setTime(this.getCurrentTime());
			else 
				msgWrap.setTime(0);
			msgWrap.setDestinations(new HashSet<Object> (recipients));
			msgWrap.setMessageCounter(++messageCounter);

			// Fake the serialization of the message if any of the recipients is not the agent itself
			if (recipients.size() > 1 || ! recipients.contains(this.agent)) 
				msg.fakeSerialize();
			
			// Add the message to the central outbox
			outbox.add(msgWrap);
		}

		/** @see Queue#sendMessageToSelf(Message) */
		@Override
		public void sendMessageToSelf(Message msg) {
			this.sendMessage(this.agent, msg);
		}

		/** Sets the current message wrapper
		 * @param wrap 	current message wrapper
		 */
		protected void setCurrentMsgWrapper (MessageWrapper wrap) {
			super.msgWrap = wrap;
		}

		/** Sets the queue's timestamp to the current time */
		protected void freezeTime () {
			if (this.updateTime) {
				this.time += System.nanoTime() - startTime;
			}
		}

		/** Updates this queue's timer to the input time if the input is greater, and resets the start time to the current system time
		 * @param msgTime 	input time
		 */
		protected void updateTime (long msgTime) {

			if(updateTime && msgTime > this.time) 
				this.time = msgTime;
			startTime = System.nanoTime();
		}

	}

	/** Each agent's queue */
	protected HashMap<String, FakeQueue> queues;

	/** A comparator used in the PriorityQueue to order messages according to their timestamps
	 * @author Thomas Leaute
	 */
	private static class TimestampComparator implements Comparator<MessageWrapper> {

		/** Compares the timestamp of two messages
		 * @param m1 	the first message
		 * @param m2 	the second message
		 * @return the difference between the timestamp of m1 and m2. If the difference is 0, it returns the difference between the messageCounters of m1 and m2
		 * @see Comparator#compare(Object, Object) */
		public int compare(MessageWrapper m1, MessageWrapper m2) {

			if (m1.getTime() > m2.getTime()) 
				return 1;
			else if (m1.getTime() < m2.getTime()) 
				return -1;
			else 
				return m1.getMessageCounter() - m2.getMessageCounter();
		}
	}

	/** Type of messages sent when an exception occurs */
	public static final String ERROR_MSG = "CentralMailerErrorMsg";

	/** Type of out of memory message */
	public static final String OutOfMemMsg = "OutOfMem";

	/** The ordered repository of messages */
	protected PriorityQueue<MessageWrapper> orderedQueue;

	/** The time stamp of the last message that has been released */
	protected long lastTimeStamp = Long.MIN_VALUE;

	/** Buffer of messages waiting to be put into the priority queue */
	protected LinkedList<MessageWrapper> outbox = new LinkedList<MessageWrapper> ();

	/** Used to generate random delays 
	 * @todo Add message delays toXML configuration files. 
	 */
	protected final DelayGenerator delayGenerator;

	/** Whether to measure the number of messages and the total amount of information sent */
	protected final boolean measuringMsgs;

	/** The lock used for synchronization */
	protected ReentrantLock lock = new ReentrantLock();

	/** The condition on which the timed thread awaits to deliver a message */
	protected Condition msgReady = this.lock.newCondition();

	/** The condition on which execute() awaits to proceed to the next message */
	protected Condition msgDone = this.lock.newCondition();

	/** The currently active queue */
	protected FakeQueue currentQueue;
	
	/** The message delivered to the currently active Queue */
	protected volatile MessageWrapper currentMsg;

	/** Flag used to tell the CentralMailer that it should terminate */
	protected boolean stop = true;

	/** Whether the agents have already been notified that they are all idle */
	protected boolean idleMsgsSent = false;

	/**
	 * Constructor
	 * @param measureMsgs 			whether to measure the number and sizes of messages
	 * @param useDelayGenerator 	\c true when the DelayGenerator is to be used
	 * @param parameters			parameters of the CentralMailer (not used in this implementation)
	 */
	public CentralMailer(boolean measureMsgs, boolean useDelayGenerator, Element parameters) {
		super ("CentralMailer");
		
		orderedQueue = new PriorityQueue<MessageWrapper>(11, new TimestampComparator());
		this.queues = new HashMap<String, FakeQueue> ();

		this.measuringMsgs = measureMsgs;

		if (useDelayGenerator)  
			delayGenerator = new NegativeExponentialDistribution(0.5);
		else this.delayGenerator = null;

	}

	/** @see java.lang.Thread#start() */
	@Override
	public void start () {
		this.setDaemon(true);
		super.start();
	}
	
	/** Executes the algorithm
	 * @return true if the algorithm correctly terminated; false if it timed out
	 */
	public boolean execute () {
		return this.execute(AgentFactory.DEFAULT_TIMEOUT);
	}

	/** Executes the algorithm
	 * @param timeout 	the timeout in milliseconds
	 * @return true if the algorithm correctly terminated; false if it timed out
	 */
	public boolean execute (long timeout) {
		
		// Convert the timeout to nanoseconds
		if (timeout < Long.MAX_VALUE / 1000000L) 
			timeout *= 1000000L;
		else 
			timeout = Long.MAX_VALUE;
		
		if (! this.isAlive()) 
			this.start();

		try {
			this.lock.lock();
			
			if (this.stop) // run() isn't ready yet
				this.msgDone.await();

			int nbrAgentsLeft = this.queues.size() - 2; // -2 to not count the stats monitor nor the daemon
			
			// Initially, the outbox contains only messages sent by the controller
			Object agent = AgentInterface.STATS_MONITOR;
			FakeQueue queue = this.queues.get(agent);

			while (true) {

				// Process the messages in the outbox
				for (MessageWrapper outWrap : this.outbox) {
					
					//				System.out.println("Out: " + outWrap);
					assert outWrap.getTime() < 0 || this.lastTimeStamp <= outWrap.getTime() : 
						"Attempting to send the following message, \nwhich has a timestamp lower than " +
						"the timetamp of the last released message (" + this.lastTimeStamp + "):\n" + outWrap;

					// Record stats about this message if needed
					Message outMsg = outWrap.getMessage();
					if (this.measuringMsgs) 
						for (Object dest : outWrap.getDestinations()) // for each destination
							if (! dest.equals(agent)) // skip virtual messages
								queue.recordStats(dest, outMsg);

					// Add delays if required
					if (this.delayGenerator != null) {

						// Each message destination must get a different delay
						Collection<Object> destinations = outWrap.getDestinations();
						if (destinations.size() == 1) { // only one destination

							// No delay for virtual messages or messages sent to the stats monitor
							Object dest = destinations.iterator().next();
							if (! agent.equals(dest) && ! dest.equals(AgentInterface.STATS_MONITOR)) 
								outWrap.addDelay(this.delayGenerator.generateDelay());

							this.orderedQueue.add(outWrap);

						} else { // create a new message wrapper for each destination and add it to the priority queue
							assert destinations.size() > 0;
							long ncccs = outWrap.getNCCCs();
							long timeStamp = outWrap.getTime();
							int msgCounter = outWrap.getMessageCounter();

							for (Object dest : destinations) {
								ArrayList<Object> singleDest = new ArrayList<Object> (1);
								singleDest.add(dest);
								MessageWrapper wrap = new MessageWrapper (outMsg, ncccs, timeStamp, singleDest, msgCounter);

								// No delay for virtual messages or messages sent to the stats monitor
								if (! agent.equals(dest) && ! dest.equals(AgentInterface.STATS_MONITOR)) 
									wrap.addDelay(this.delayGenerator.generateDelay());

								this.orderedQueue.add(wrap);
							}
						}

					} else // no delays; just add the message to the priority queue as is
						this.orderedQueue.add(outWrap);
				}

				this.outbox.clear();

				//			System.out.println("In: " + this.orderedQueue.peek());

				// Retrieve the next message to be delivered
				MessageWrapper wrap = this.orderedQueue.peek();

				if (wrap == null) { // no more messages: all agents are idle
					
					// Notify all agents that they are all idle, unless they have all terminated already
					if (! this.idleMsgsSent && nbrAgentsLeft > 0) {
						this.orderedQueue.add(new MessageWrapper (new Message (
								AgentInterface.ALL_AGENTS_IDLE), 0, lastTimeStamp, new HashSet<Object> (this.queues.keySet()), 0));
						this.idleMsgsSent = true;
						continue; // let the agents process the ALL_AGENTS_IDLE message

					} else { // all agents have been notified of the idleness 
						
						// Reset the last timestamp seen and return
						this.lastTimeStamp = Long.MIN_VALUE;
						this.idleMsgsSent = false;
						this.lock.unlock();
						return true; // correct termination
					}
				}

				Collection<Object> destinations = wrap.getDestinations();
				Iterator<Object> iter = destinations.iterator();
				agent = iter.next();
				queue = this.queues.get(agent);
				queue.setCurrentMsgWrapper(wrap);

				// Check if the messages still has remaining destinations
				if (iter.hasNext()) { 
					iter.remove();
				} else // no more destinations
					this.orderedQueue.poll();

				assert this.orderedQueue.size() == 0 || this.orderedQueue.peek().getDestinations().size() > 0;
				
				// Deliver the message and wait until it has been processed or a timeout has occurred
				this.currentMsg = wrap;
				this.currentQueue = queue;
				this.msgReady.signal(); // tells run() to wake up as soon as I have released the lock by entering await()
				long timeLeft = timeout - Math.max(0, Math.max(wrap.getTime(), queue.getCurrentTime()));
				while (true) {
					
					// Wait until the agent is done processing the message
					if ((timeLeft = this.msgDone.awaitNanos(timeLeft)) <= 0) { // timeout
						this.stop = true;
						this.lock.unlock();
						return false;
					}
					
					if (this.currentMsg != null)  // spurious wakeup
						continue;
					else if (this.stop) { // exception in the other thread
						this.lock.unlock();
						return false;
					} else 
						break;
				}
				
				if (!agent.equals(AgentInterface.STATS_MONITOR) && wrap.getMessage().getType().equals(AgentInterface.AGENT_FINISHED)) 
					nbrAgentsLeft--;
			}
			
		} catch (OutOfMemoryError e) {
			MessageWrapper wrap = new MessageWrapper(new Message(OutOfMemMsg));
			FakeQueue queue = this.queues.get(Daemon.DAEMON);
			stop = true;
			// Notify the recipient's modules
			queue.notifyInListeners(wrap.getMessage());
			this.lock.unlock();
			return false;
			
		} catch (Throwable e) {
			System.err.println("The CentralMailer was interrupted due to the following exception:");
			e.printStackTrace();
			MessageWrapper wrap = new MessageWrapper(new Message(ERROR_MSG));
			FakeQueue queue = this.queues.get(Daemon.DAEMON);
			stop = true;
			queue.notifyInListeners(wrap.getMessage());
			this.lock.unlock();
			return false;
		}
	}

	/** Waits for a message to be available and then delivers it
	 * @see java.lang.Thread#run() 
	 */
	@Override
	public void run () {
		
		this.lock.lock();
		this.stop = false;
		this.msgDone.signal(); // in case execute() got the lock first
		
		try {
			while (! this.stop) {
				
				// Wait for a message to be available for delivery
				while (this.currentMsg == null) { // handles spurious wakeups
					this.msgReady.await();
					if (this.stop) {
						this.msgDone.signal();
						this.lock.unlock();
						
						return;
					}
				}
				
				this.lock.unlock();
				
				// Update the recipient's NCCC counter if necessary
				assert this.currentMsg != null;
				this.currentQueue.updateNCCCs(this.currentMsg.getNCCCs());

				// Update the recipient's timestamp and start time if necessary
				assert this.checkTimestamp(this.currentMsg.getTime()) : 
					"Attempting to release the following message, which has a timestamp lower than " +
					"the timetamp of the last released message (" + this.lastTimeStamp + "):\n" + this.currentMsg;
				this.currentQueue.updateTime(this.currentMsg.getTime());

				// Notify the recipient's modules
				this.currentQueue.notifyInListeners(this.currentMsg.getMessage()); /// @bug Store the message if required

				// Stop measuring time
				this.currentQueue.freezeTime();
				
				// Return the control to the main thread
				this.lock.lock();
				this.currentMsg = null;
				this.msgDone.signal();

			}
			
			this.lock.unlock();
			
		} catch (OutOfMemoryError e) {
			MessageWrapper wrap = new MessageWrapper(new Message(OutOfMemMsg));
			FakeQueue queue = this.queues.get(Daemon.DAEMON);
			// Notify the recipient's modules
			queue.notifyInListeners(wrap.getMessage());
		} catch (Throwable e) {
			System.err.println("The CentralMailer was interrupted due to the following exception:");
			e.printStackTrace();
			MessageWrapper wrap = new MessageWrapper(new Message(ERROR_MSG));
			FakeQueue queue = this.queues.get(Daemon.DAEMON);
			queue.notifyInListeners(wrap.getMessage());
		} finally {
			lock.lock();
			this.stop = true;
			this.currentMsg = null;
			this.msgDone.signal();
			lock.unlock();
		}
	}

	/** Kills the CentralMailer */
	public void end () {
		try {
			lock.lock();
			if(!stop) {
				stop = true;
				this.msgReady.signal();
				this.msgDone.await();
			}
			lock.unlock();
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/** Checks that the input time is higher than the last timestamp
	 * @param time 	input time
	 * @return \c true iff the test passed
	 */
	protected boolean checkTimestamp(long time) {

		if (time >= 0 && time < this.lastTimeStamp) 
			return false;

		this.lastTimeStamp = time;
		return true;
	}

	/** Creates a new queue for the given agent
	 * @param agent 	agent name
	 * @return a queue
	 */
	public Queue newQueue (String agent) {
		return this.newQueue(agent, true);
	}

	/** Creates a new queue for the given agent
	 * @param agent 		agent name
	 * @param updateTime 	If \c false, this queue's time will always remain frozen at its initial value
	 * @return a queue
	 */
	public Queue newQueue (String agent, boolean updateTime) {

		FakeQueue queue = new FakeQueue(agent, updateTime);
		this.queues.put(agent, queue);

		if (agent.equals(AgentInterface.STATS_MONITOR)) 
			this.queues.put(Daemon.DAEMON, queue);

		return queue;
	}

	/**
	 * Abstract class that defines the functions needed in a delay generator
	 * 
	 * @author Brammert Ottens
	 *
	 */
	public static abstract class DelayGenerator {

		/**
		 * Generates a delay in nanoseconds based on a probability distribution
		 * @return the delay
		 */
		public abstract long generateDelay();
	}

	/**
	 * This class is used to generate message delay according to the 
	 * negative exponential distribution
	 * 
	 * @author Brammert Ottens
	 */
	private static class NegativeExponentialDistribution extends DelayGenerator {

		/** Parameter of the negative exponential distribution */
		private final double lambda;

		/**
		 * Constructor
		 * 
		 * @param lambda	The parameter of the distribution
		 */
		public NegativeExponentialDistribution(double lambda) {
			this.lambda = lambda;
		}

		/**
		 * Generates a delay based on the negative exponential distribution
		 * 
		 * @return the delay (in nanoseconds)
		 */
		public long generateDelay() {
			double p = Math.random();
			long delay = (long)((Math.log(1-p)/(-lambda))*100000);
			return  delay;
		}
	}
}
