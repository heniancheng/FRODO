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

package frodo2.algorithms;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageListener;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWrapper;
import frodo2.communication.OutgoingMsgPolicyInterface;
import frodo2.communication.Queue;
import frodo2.communication.QueueOutputPipeInterface;
import frodo2.communication.mailer.CentralMailer;
import frodo2.communication.sharedMemory.QueueIOPipe;
import frodo2.controller.Controller;
import frodo2.controller.WhitePages;
import frodo2.daemon.Daemon;
import frodo2.daemon.LocalAgentReport;
import frodo2.daemon.LocalWhitePages;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.MASProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;

/** An agent that uses a single queue. 
 * @author Thomas Leaute
 * @author Brammert Ottens
 * @param <Val> the type used for domain values
 */
public class SingleQueueAgent < Val extends Addable<Val> > implements AgentInterface<Val>, IncomingMsgPolicyInterface<String> {
	
	/** The agent's ID */
	protected String agentID;

	/** The agent's queue */
	final protected Queue queue;
	
	/** This agent's neighbor IDs */
	protected Set<String> neighbours;

	/** The message types this agent listens to */
	private Collection<String> msgTypes = new ArrayList<String> ();
	
	/** The number of neighbours connected to the agent */
	private int neighboursConnected;
	
	/** Pipe used to send messages to this agent */
	protected QueueIOPipe localInputPipe;
	
	/** The incoming port number */
	private int port;

	/** Whether to measure the number of messages and the total amount of information sent */
	private final boolean measureMsgs;

	/** The problem to solve */
	private ProblemInterface<Val, ?> problem;
	
	/** The module that monitors on the values of the variables this agent owns */
	private StatsReporterWithConvergence<Val> solutionMonitor;
	
	/** Output pipe that ignores anything sent to it */
	private static QueueOutputPipeInterface blackHole = new QueueOutputPipeInterface () {
		public void close() { }
		public void pushMessage(MessageWrapper msg) { }
		public String toDOT() { return "black_hole"; }
	};
	
	/** Constructor
	 * @param probDesc 			the description of the problem
	 * @param agentDesc 		JDOM document containing the description of the problem
	 * @param mailman 			the CentralMailer; ignored if not measuring Simulated Time
	 * @throws JDOMException thrown if reading a description document failed
	 * @throws ClassNotFoundException if a module class mentioned in the description is unknown
	 * @throws NoSuchMethodException if a module class used does not contain a constructor that takes in a ProblemInterface and a JDOM Element
	 * @throws InvocationTargetException if a module constructor throws an exception
	 * @throws IllegalAccessException if the constructor for an IncomingMsgPolicyInterface is not accessible
	 * @throws InstantiationException if a module class provided in the description is an abstract class
	 * @throws IllegalArgumentException if the constructor of an IncomingMsgPolicyInterface does not take the proper arguments
	 */
	@SuppressWarnings("unchecked")
	public SingleQueueAgent(ProblemInterface<Val, ?> probDesc, Document agentDesc, CentralMailer mailman) 
	throws JDOMException, ClassNotFoundException, NoSuchMethodException, 
	IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {

		this.problem = probDesc;
		this.agentID = problem.getAgent();

		String measureTimeString = agentDesc.getRootElement().getAttributeValue("measureTime");
		boolean useCentralMailer = true;
		if(measureTimeString != null)
			useCentralMailer = Boolean.parseBoolean(measureTimeString);
		
		// Check whether we should count messages
		String measureMsgs = agentDesc.getRootElement().getAttributeValue("measureMsgs");
		if (measureMsgs != null) 
			this.measureMsgs = Boolean.parseBoolean(measureMsgs);
		else 
			this.measureMsgs = false;
		
		// Read the problem description class name from the agent description, the standard value is DCOPProblemInterface
		Element parserDesc = agentDesc.getRootElement().getChild("parser");
		Class<? extends ProblemInterface<Val, ?>> probDescClass = (Class<? extends ProblemInterface<Val, ?>>)DCOPProblemInterface.class;
		if(parserDesc != null) {
			String probDescClassName = parserDesc.getAttributeValue("probDescClass");
			if(probDescClassName != null)
				probDescClass = (Class<? extends ProblemInterface<Val, ?>>) Class.forName(probDescClassName);
 		}
		
		List<Element> types = agentDesc.getRootElement().getChildren("modules");
		Element modsElmt = null;
		if(types.size() == 1) {
			modsElmt = agentDesc.getRootElement().getChild("modules");
		} else {
			String type = ((MASProblemInterface<Val, ?>)probDesc).getType();
			for(Element t : types) {
				if(t.getAttributeValue("agentType").equals(type)) {
					modsElmt = t;
					break;
				}
			}
		}
		
		String solutionMonitorName = (modsElmt != null ? modsElmt.getAttributeValue("solutionMonitor") : null);
		
		// Create the queue
		if (useCentralMailer) 
			this.queue = mailman.newQueue(agentID);
		else 
			this.queue = new Queue(this.measureMsgs);
		
		// Count NCCCs
		this.queue.setProblem(probDesc);
		
		localInputPipe = new QueueIOPipe(queue);
		this.queue.addOutputPipe(this.agentID, new QueueIOPipe (this.queue, true));
		this.queue.addOutputPipe(STATS_MONITOR, blackHole);
		
		msgTypes.add(WhitePages.CONNECT_AGENT);
		msgTypes.add(AgentInterface.START_AGENT);
		msgTypes.add(LocalWhitePages.DIE);
		msgTypes.add(AGENT_FINISHED);
		this.queue.addIncomingMessagePolicy(this);
		
		// Set up the listeners/modules
		if (modsElmt != null) {
			for (Element moduleElmt : (List<Element>) modsElmt.getChildren()) {

				// Change the module's message types if required
				String className = moduleElmt.getAttributeValue("className");
				Class< MessageListener<String> > moduleClass = (Class< MessageListener<String> >) Class.forName(className);
				Element allMsgsElmt = moduleElmt.getChild("messages");
				if (allMsgsElmt != null) {
					for (Element msgElmt : (List<Element>) allMsgsElmt.getChildren()) {

						// Look up the new value for the message type
						String newType = msgElmt.getAttributeValue("value");
						String ownerClassName = msgElmt.getAttributeValue("ownerClass");
						if (ownerClassName != null) { // the attribute "value" actually refers to a field in a class
							Class<?> ownerClass = Class.forName(ownerClassName);
							try {
								Field field = ownerClass.getDeclaredField(newType);
								newType = (String) field.get(newType);
							} catch (NoSuchFieldException e) {
								System.err.println("Unable to read the value of the field " + ownerClass.getName() + "." + newType);
								e.printStackTrace();
							}
						}

						// Set the message type to its new value
						try {
							setMsgType(moduleClass, msgElmt.getAttributeValue("name"), newType);
						} catch (NoSuchFieldException e) {
							System.err.println("Unable to find the field " + moduleClass.getName() + "." + msgElmt.getAttributeValue("name"));
							e.printStackTrace();
						}
					}
				}
				
				// Instantiate the listener using reflection
				Class<?> parTypes[] = new Class[2];
				parTypes[0] = probDescClass;
				parTypes[1] = Element.class;
				Constructor< MessageListener<String> > constructor = moduleClass.getConstructor(parTypes);
				Object[] args = new Object[2];
				args[0] = probDesc;
				args[1] = moduleElmt;
				MessageListener<String> module = constructor.newInstance(args);
				if(className.equals(solutionMonitorName)) // every solutionMonitor must be of the type StatsReporterWithConvergence!
					solutionMonitor = (StatsReporterWithConvergence<Val>)module;
				
				// Register the module with the queue
				if (module instanceof IncomingMsgPolicyInterface) 
					queue.addIncomingMessagePolicy((IncomingMsgPolicyInterface<String>) module);
				if (module instanceof OutgoingMsgPolicyInterface) 
					queue.addOutgoingMessagePolicy((OutgoingMsgPolicyInterface<String>) module);
			}
		}
		
	}

	/** Changes the type of a message in a module
	 * @param moduleClass 					the class of the module
	 * @param msgType 						the message type whose value is to be changed
	 * @param newType 						 new value for the message type
	 * @throws NoSuchFieldException 		if \a msgType is not a field of \a module
	 */
	public static void setMsgType(Class< ? extends MessageListener<String> > moduleClass, String msgType, String newType) 
	throws NoSuchFieldException {
		
		Field field = moduleClass.getField(msgType);
		field.setAccessible(true);
		try {
			field.set(null, newType);
		} catch (IllegalAccessException e) { // should never happen
			System.err.println("Failed to set the field " + moduleClass.getName() + "." + msgType);
			e.printStackTrace();
		} catch (IllegalArgumentException e) { // should never happen
			e.printStackTrace();
		}
	}

	/** @see AgentInterface#addOutputPipe(String, QueueOutputPipeInterface) */
	public void addOutputPipe(String agent, QueueOutputPipeInterface outputPipe) {
		queue.addOutputPipe(agent, outputPipe);
		oneMoreNeighbor();
	}
	
	/** Increments the number of neighbors connected, and notifies the controller when they are all connected */
	protected void oneMoreNeighbor () {
		neighboursConnected++;
		
		if(neighboursConnected == neighbours.size())  // we are ready to start
			queue.sendMessage(AgentInterface.STATS_MONITOR, new Message(AgentInterface.AGENT_CONNECTED));
	}
	
	/** @see AgentInterface#connect() */
	public void connect() {

		// Parse the list of neighbors
		this.neighbours = this.problem.getAgents();
		this.neighbours.remove(this.agentID);
		this.neighboursConnected = 0;
		
		// if there are no neighbours, you are by default connected to all your neighbours
		if(neighbours.isEmpty()) 
			queue.sendMessage(STATS_MONITOR, new Message(AgentInterface.AGENT_CONNECTED));
		
		for(String neighbour : neighbours) {
			
			// Skip this neighbor if we have already established connection
			if (this.queue.getOutputPipe(neighbour) != null) {
				this.oneMoreNeighbor();
				continue;
			}
			
			MessageWith2Payloads<String, String> newMsg = 
				new MessageWith2Payloads<String, String>(AgentInterface.LOCAL_AGENT_ADDRESS_REQUEST, agentID, neighbour);
			queue.sendMessage(Daemon.DAEMON, newMsg);
		}
	}

	/** @see AgentInterface#getID() */
	public String getID() {
		return agentID;
	}

	/** @see AgentInterface#kill() */
	public void kill() {
		queue.end();
	}

	/** Does nothing
	 * @see AgentInterface#start() 
	 */
	public void start() { }

	/** @see AgentInterface#setup(QueueOutputPipeInterface, QueueOutputPipeInterface, int) */
	public void setup(QueueOutputPipeInterface toDaemonPipe, QueueOutputPipeInterface toControllerPipe, int port) {
		this.port = port;
		this.queue.addOutputPipe(STATS_MONITOR, toControllerPipe);
		this.queue.addOutputPipe(Daemon.DAEMON, toDaemonPipe);

		// Create input pipes
		if (port >= 0) {
			try {
				Controller.PipeFactoryInstance.inputPipe(queue, Controller.PipeFactoryInstance.getSelfAddress(port));
			} catch (IOException e) {
				System.err.println("Unable to create the agent's TCP input pipe");
				e.printStackTrace();
			}
		}

		// Report to the local white pages
		this.report();
	}
	
	/** @see AgentInterface#report() */
	public void report () {
		this.queue.sendMessage(Daemon.DAEMON, new LocalAgentReport (agentID, port, localInputPipe));
	}
	
	/** Sends a message to the controller saying that the agent has finished */
	protected void finished () {
		if (this.measureMsgs) // send a message with statistics
			queue.sendMessage(STATS_MONITOR, new AgentFinishedMessage (queue.getMsgNbrs(), queue.getMsgSizes(), queue.getMaxMsgSizes()));
		else 
			queue.sendMessage(STATS_MONITOR, new Message(AGENT_FINISHED));
		queue.resetStats();
	}

	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return msgTypes;
	}

	/** 
	 * @see IncomingMsgPolicyInterface#notifyIn(Message)
	 * @warning Any method overriding this one should start with a call to super.notify(msg).
	 */
	public void notifyIn(Message msg) {
		
		if(msg.getType().equals(WhitePages.CONNECT_AGENT)) { // connect to all its neighbours
			connect();
		}
		
		else if(msg.getType().equals(AgentInterface.START_AGENT)) { // start the agent
			start();
		}
		
		else if(msg.getType().equals(LocalWhitePages.DIE)) { // agent should die
			kill();
		}
		
		else if (msg.getType().equals(AGENT_FINISHED)) // the algorithm terminated; report this to the controller
			finished();
	}

	/** Does nothing
	 * @see IncomingMsgPolicyInterface#setQueue(Queue)
	 */
	public void setQueue(Queue queue) { }
	
	/**
	 * @see AgentInterface#getCurrentSolution()
	 */
	public Map<String, Val> getCurrentSolution() {
		return solutionMonitor.getCurrentSolution();
	}

}
