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

package frodo2.controller;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jdom2.input.SAXBuilder;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import frodo2.algorithms.AgentInterface;
import frodo2.algorithms.StatsReporter;
import frodo2.algorithms.XCSPparser;
import frodo2.algorithms.AgentInterface.AgentFinishedMessage;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.MessageWith2Payloads;
import frodo2.communication.MessageWithPayload;
import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.controller.userIO.UserIO;
import frodo2.controller.WhitePages;
import frodo2.solutionSpaces.DCOPProblemInterface;
import frodo2.solutionSpaces.ProblemInterface;

/**
 * The ConfigurationManager takes care of setting up the experiments
 * @author Brammert Ottens
 * @author Thomas Leaute
 * @todo Listen to the stats sent by the agents.
 */
public class ConfigurationManager implements IncomingMsgPolicyInterface <String> {
	
	/** The message send to the Daemon that contain the configuration of an agent*/
	public static final String AGENT_CONFIGURATION_MESSAGE = "Agent-Configuration";
	
	/** The message type used to ask the white pages for a list of available daemons*/
	public static final String REQUEST_DAEMONS_CONFIG_MSG = "Request-Daemons-Config";
	
	/** Message used to tell the white pages that the algorithm can start */
	public static final String START = "Start";
	
	/** Message used to tell the white pages that the agent can connect to their neighbours */
	public static final String CONNECT = "Connect";
	
	/** Message to signal to the white pages to kill the agents*/
	public static final String KILL_ALL_AGENTS = "Kill-All-Agents";

	/** The text displayed when the experiment is over */
	public static final String END_TEXT = "All experiments are finished";
	
	/** The queue on which it should call sendMessage() */
	private Queue queue;
	
	/** contains the parsed XML tree with the configuration information*/
	private Document configDoc;
	
	/**when debug is true, debugging is enabled*/
	private boolean debug;
	
	/** logClass contains the name of the class that the agents should use when logging their actions*/
	private String logClass;
	
	/** agentDescription contains the filename of the XML file that describes the default agent that should be used
	 * @todo One should be able to define the agent type for each agent, in case they are not all the same. 
	 */
	private String agentDescriptionFile;
	
	/** timeOut contains the maximum time any agent should run the algorithm. If timeOut = -1, not timeOut is specified*/
	private int timeOut;
	
	/** resultFile contains the file name of the file where the results of the experiments should be stored
	 * @bug The solution is never written to the file
	 */
	private String resultFile;
	
	/** The list of messages types this listener wants to be notified of */
	private ArrayList <String> msgTypes = new ArrayList <String> ();
	
	/** The list of daemons that are available*/
	private HashMap<String, String[]> daemonList;
	
	/** The main controller class */
	private Controller control;
	
	/** After the configuration file has been loaded, this contains a list of problems that will be solved when the experiment starts*/
	private Element problemList;
	
	/** The JDOM Element describing the agent configuration */
	private Element agentDescription;
	
	/** The name of the agent, used to find the agent description when the configuration file is 
	 * to be found in a jar file*/
	private String agentName;
	
	/** the experiment that is currently run*/
	private int problem;
	
	/** Number of agents in the problem that is currently run*/
	private int numberOfAgents;
	
	/** The number of agents that reported to be finished*/
	private int numberOfAgentsFinished;
	
	/** The number of agents that reported to be white pages*/
	private int numberOfAgentsReported;
	
	/** The number of agents that are connected to their children*/
	private int numberOfAgentsConnected;
	
	/** Used to determine whether experiments should be run local or not*/
	private boolean local;

	/** Folder containing the files mentioned in the configuration document. Must end with a slash. */
	private String workDir;

	/** The nccc stamp of the AGENT_FINISHED message with the highest nccc stamp*/
	private long finalNCCCcount = -1;

	/** Whether to measure the number of messages and the total amount of information sent */
	private boolean measureMsgs = false;
	
	/** For each message type, the number of messages sent of that type */
	private TreeMap<String, Integer> msgNbrs;
	
	/** For each message type, the total amount of information sent in messages of that type, in bytes */
	private TreeMap<String, Long> msgSizes;
	
	/** For each message type, the size (in bytes) of the largest message */
	private TreeMap<String, Long> maxMsgSizes;

	/** The statistics listeners */
	private Collection<StatsReporter> statsReporters;

	/** The start time of the algorithm, in milliseconds */
	private long startTime;
		
	/**
	 * The constructor of the Configuration manager
	 * @param control a pointer to the control to stop the UI when something goes wrong
	 * @param local true if the experiments run on the same machine
	 * @param workDir folder containing the files mentioned in the configuration document. Must end with a slash.
	 */
	public ConfigurationManager(Controller control, boolean local, String workDir) {
		this.control = control;
		this.local = local;
		this.workDir = workDir;
		msgTypes.add(UserIO.CONFIGURATION_MSG);
		msgTypes.add(WhitePages.DEAMONS_CONFIG_MSG);
		msgTypes.add(AgentInterface.AGENT_FINISHED);
		msgTypes.add(AgentInterface.AGENT_CONNECTED);
		msgTypes.add(WhitePages.AGENT_REPORTED);
		msgTypes.add(UserIO.START_MSG);
		msgTypes.add(WhitePages.ALL_AGENTS_KILLED);
	}

	/**
	 * The constructor of the Configuration manager
	 * @param control a pointer to the control to stop the UI when something goes wrong
	 * @param local true if the experiments run on the same machine
	 */
	public ConfigurationManager(Controller control, boolean local) {
		this(control, local, "");
	}

	/** @see frodo2.communication.IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		return msgTypes;
	}
	
	
	
	
	/**
	 * The Configuration Manager is triggered by the reception of a CONFIGURATION
	 * message, containing a filename that points to the configuration file. The
	 * configuration file is a XML file containing the following information
	 * 
	 * - a list of problem descriptions: to be send to the different daemons
	 * - a result file : to store the results of a particular experiment
	 * - a class describing the default agent to be used
	 * - the following optional elements
	 * 		-> debugging yes/no
	 * 		-> which logging class to use
	 * 		-> the timeout that should be in place
	 * @param msg the message that has been received
	 */
	@SuppressWarnings("unchecked")
	public void notifyIn(Message msg) {
		
		String msgType = msg.getType();
		String configFile = null;
		
		if(msgType.equals(UserIO.CONFIGURATION_MSG)) { // a configuration message has been received
			configFile = ((MessageWithPayload<String>) msg).getPayload();
			
			// parse the configuration document
			try{
				parseConfigurationFile(workDir + configFile);
			} catch(JDOMException e) {
				e.printStackTrace();
				control.exit(true);
			} catch(NullPointerException e) {
				e.printStackTrace();
				control.exit(true);
			} catch(IOException e) {
				e.printStackTrace();
				control.exit(true);
			}
		}
		
		/** After the START_MSG has been received, adding a new daemon has no effect.
		 *  @todo Perhaps in the future it would be interesting to be able to add daemons while
		 *  the program is running. 
		 */
		if(msgType.equals(UserIO.START_MSG)) { // the user wants to start running the experiment
			if(configDoc == null) {
				// ask the white pages for the set of daemons available at the moment
				tellUser("No configuration file has been specified!");
			} else {
				if(local) {
					// run the first experiment. The next experiment is activated when all the agents finished the first
					runExperiment(((Element)problemList.getChildren().get(problem)).getAttributeValue("fileName"));
					problem++;
				} else {
					// before the experiment can start, first get an up to date list of the available daemons
					Message newMsg = new Message(REQUEST_DAEMONS_CONFIG_MSG);
					queue.sendMessageToSelf(newMsg);
				}
			}
		}
		
		/** When the white pages return a set of daemons, the experiments can start*/
		if(msgType.equals(WhitePages.DEAMONS_CONFIG_MSG)) { // the list of available daemons is received
		
			daemonList = ((MessageWithPayload<HashMap<String, String[]>>)msg).getPayload();
			problem = 0;
			
			if(daemonList.size() == 0) {
				// tell the user that no daemons have registered
				tellUser("There are no daemons available. Please specify a set of Daemons");
			} else {
				// run the first experiment. The next experiment is activated when all the agents finished the first
				runExperiment(((Element)problemList.getChildren().get(problem)).getAttributeValue("fileName"));
				problem++;
			}
		}
		
		/** An agent has reported itself*/
		if(msgType.equals(WhitePages.AGENT_REPORTED)) { 
			numberOfAgentsReported++;
			
			if(numberOfAgentsConnected == numberOfAgents && numberOfAgentsReported == numberOfAgents) {
				// All the agents are initialized and connected,
				// Start the experiment via the white pages
				Message msgNew = new Message(START);
				queue.sendMessageToSelf(msgNew);
			} else if(numberOfAgentsReported == numberOfAgents){
				// All the agents are initialized,
				// Tell all agents to connect to their neighbors via the white pages
				Message msgNew = new Message(CONNECT);
				queue.sendMessageToSelf(msgNew);
			}
		}
		
		/** An agent is connected to all its neighbours*/
		if(msgType.equals(AgentInterface.AGENT_CONNECTED)) { 
			numberOfAgentsConnected++;
			
			if(numberOfAgentsConnected == numberOfAgents && numberOfAgentsReported == numberOfAgents){
				// All the agents are initialized,
				// Start the experiment via the white pages
				Message newMsg = new Message(START);
				
				this.startTime = System.currentTimeMillis();
				queue.sendMessageToSelf(newMsg);
			}
		}
		
		/** An agent has finished*/
		if(msgType.equals(AgentInterface.AGENT_FINISHED)) { 
			numberOfAgentsFinished++;
			MessageWrapper msgWrap = queue.getCurrentMessageWrapper();
			long ncccs = msgWrap.getNCCCs();
			if (ncccs != -1 && ncccs > this.finalNCCCcount) 
				this.finalNCCCcount = ncccs;
			
			// Record the stats
			if (this.measureMsgs) {
				
				AgentFinishedMessage msgCast = (AgentFinishedMessage) msg;
				
				// Increment nbrMsgs
				for (Map.Entry<String, Integer> entry : msgCast.getMsgNbrs().entrySet()) {
					String type = entry.getKey();

					Integer nbr = this.msgNbrs.get(type);
					if (nbr == null) 
						this.msgNbrs.put(type, entry.getValue());
					else 
						this.msgNbrs.put(type, nbr + entry.getValue());
				}
				
				// Increment msgSizes
				for (Map.Entry<String, Long> entry : msgCast.getMsgSizes().entrySet()) {
					String type = entry.getKey();
					
					Long size = this.msgSizes.get(type);
					if (size == null) 
						this.msgSizes.put(type, entry.getValue());
					else 
						this.msgSizes.put(type, size + entry.getValue());
				}
				
				// Update maxMsgSizes
				for (Map.Entry<String, Long> entry : msgCast.getMaxMsgSizes().entrySet()) {
					String type = entry.getKey();
					
					Long maxSize = this.maxMsgSizes.get(type);
					if (maxSize == null || entry.getValue() > maxSize) 
						this.maxMsgSizes.put(type, entry.getValue());
				}
			}
			
			if(numberOfAgentsFinished == numberOfAgents){
				// the problem has been solved, proceed to the next experiment the daemons 
				// take care of deleting the agents and reporting to the white pages that they
				// are done
				
				System.out.println("Algorithm finished in " + (System.currentTimeMillis() - this.startTime) + " ms");
				
				NumberFormat formatter = NumberFormat.getInstance();

				// Print NCCCs
				if (this.finalNCCCcount > 0) 
					System.out.println("Number of NCCCs = " + formatter.format(this.finalNCCCcount));
				
				if (this.measureMsgs) {
					
					// Print the number of messages sent
					int totalNbr = 0;
					System.out.println("Number of messages sent (by type): ");
					for (Map.Entry<String, Integer> entry : this.msgNbrs.entrySet()) {
						int nbr = entry.getValue();
						System.out.println("\t" + entry.getKey() + ":\t" + formatter.format(nbr));
						totalNbr += nbr;
					}
					System.out.println("\t- Total:\t" + formatter.format(totalNbr));

					// Print the amount of information sent
					long totalSize = 0;
					System.out.println("Amount of information sent (by type, in bytes): ");
					for (Map.Entry<String, Long> entry : this.msgSizes.entrySet()) {
						long size = entry.getValue();
						System.out.println("\t" + entry.getKey() + ":\t" + formatter.format(size));
						totalSize += size;
					}
					System.out.println("\t- Total:\t" + formatter.format(totalSize));
					
					// Print the maximum message size
					long maxSize = 0;
					System.out.println("Size of the largest message sent (by type, in bytes): ");
					for (Map.Entry<String, Long> entry : this.maxMsgSizes.entrySet()) {
						long size = entry.getValue();
						System.out.println("\t" + entry.getKey() + ":\t" + formatter.format(size));
						maxSize = Math.max(size, maxSize);
					}
					System.out.println("\t- Overall maximum:\t" + formatter.format(maxSize));
				}
				
				cleanProblem();
				Message newMsg = new Message(KILL_ALL_AGENTS);
				queue.sendMessageToSelf(newMsg);
			}
		}
		
		/** All agents are killed, we are ready for the next problem to be solved*/
		if(msgType.equals(WhitePages.ALL_AGENTS_KILLED)) {
			if(problem == problemList.getChildren().size()) {
				cleanProblem();
				problem = 0;
				tellUser(END_TEXT);
				control.setFinished();
			} else {
				runExperiment(((Element)problemList.getChildren().get(problem)).getAttributeValue("fileName"));
				problem++;
			}
		}
		
	}

	/**
	 * 
	 * @see frodo2.communication.IncomingMsgPolicyInterface#setQueue(frodo2.communication.Queue)
	 */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}
	
	/**
	 * This function is used to send a message to the user via the UI
	 * @param message the message to be send to the user
	 */
	private void tellUser(String message) {
		Message msg = new MessageWithPayload <String> (UserIO.USER_NOTIFICATION_MSG, message);
		queue.sendMessageToSelf(msg);
	}
	
	/**
	 * This function parses the "configuration" element in the experiment file
	 * @param configFile 		A string containing the filename of the experimental file
	 * @return 					0 when parsing succeeded and -1 if parsing failed
	 * @throws JDOMException 	if an error occurred when parsing the configuration file
	 * @throws IOException 		if an error occurred when accessing the configuration file
	 */
	public int parseConfigurationFile(String configFile) throws JDOMException, IOException {

		SAXBuilder builder = new SAXBuilder();
		configDoc = builder.build(new File(configFile));

		
		Element root = configDoc.getRootElement();
		// check if the document has the proper root
		if(!root.getName().equals("experiment")) {
			tellUser("Error parsing the configuration file: <" + configFile + "> does not contain the proper root element!");
			configDoc = null;
			return -1;
		}
		
		Element config = root.getChild("configuration");
		
		// which agent to use
		agentDescription = config.getChild("agentDescription");
		if(agentDescription == null) {
			// notify the user that the configuration file is incomplete
			tellUser("The configuration file <" + configFile + "> is missing the <agentDecsription> tag!");
			configDoc = null;
			return -1;
		} else {
			String description = agentDescription.getAttributeValue("fileName");
			if(description == null) {
				agentName = agentDescription.getAttributeValue("agentName");
				if(agentName == null) {
					tellUser("Error parsing configuration file: The agentDescription element should contain either a \"fileName\" or an \"agentName\" attribute!");
					agentDescriptionFile = null;
					return -1;
				} 
			} else {
				agentDescriptionFile = workDir + description;
			}
		}
		
		// where to store the results
		if(config.getChild("resultFile") == null) {
			// notify the user that the configuration file is incomplete
			tellUser("The configuration file <" + configFile + "> is missing the <resultFile> tag!");
			configDoc = null;
			return -1;
		} else {
			resultFile = workDir + config.getChild("resultFile").getAttributeValue("fileName");
		}
		
		// enable/disable debugging
		if(config.getChild("debug") == null) {
			debug = false;
		} else {
			debug = Boolean.parseBoolean(config.getChild("debug").getAttributeValue("enabled"));
		}
		
		// enable/disable logging
		if(config.getChild("logging") == null) {
			logClass = "";
		} else {
			logClass = config.getChild("logging").getAttributeValue("className");
		}
		
		// if specified set a timeout, in seconds!
		if(config.getChild("timeOut") == null) {
			timeOut = -1;
		} else {
			timeOut = Integer.parseInt(config.getChild("timeOut").getAttributeValue("duration"));
		}
		
		// finally, get the list of problems
		problemList = root.getChild("problemList");
		if(problemList == null) {
			tellUser("Error parsing the configuration file: <" + configFile + "> does not contain a list of problems!");
			configDoc = null;
			return -1;
		} 
		
		return 0;
	}

	
	/**
	 * This file sets up a specific problem and tells the agents to run it
	 * @param filename 		the name of the XCSP problem file (without the path)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void runExperiment(String filename) {
		SAXBuilder builder = new SAXBuilder();
		Document problemDoc;
		String[] dList = null;
		
		try {
			// Get the problem definition
			problemDoc = builder.build(workDir + filename);
			Document agentDescr = null;
			// Get the agent description
			if(agentDescriptionFile == null) {
				agentDescr = builder.build(ConfigurationManager.class.getResourceAsStream("/frodo2/" + agentName));
			} else {
				agentDescr = builder.build(new File(agentDescriptionFile));
			}
			
			// Override the "measureTime" attribute since simulated time is not supported in advanced mode
			agentDescr.getRootElement().setAttribute("measureTime", "false");
			
			// Check whether we should be counting messages
			String measureMsgs = agentDescr.getRootElement().getAttributeValue("measureMsgs");
			if (measureMsgs != null) {
				this.msgNbrs = new TreeMap<String, Integer> ();
				this.msgSizes = new TreeMap<String, Long> ();
				this.maxMsgSizes = new TreeMap<String, Long> ();
				this.measureMsgs = Boolean.parseBoolean(measureMsgs);
			} else 
				this.measureMsgs = false;
			
			// Instantiate the parser
			XCSPparser<?, ?> parser;
			Element parserElmt = agentDescr.getRootElement().getChild("parser");
			if (parserElmt != null) {
				String parserClassName = parserElmt.getAttributeValue("parserClass");
				Class< ? extends XCSPparser<?, ?> > parserClass = (Class< ? extends XCSPparser<?, ?> >) Class.forName(parserClassName);
				Constructor< ? extends XCSPparser<?, ?> > constructor = parserClass.getConstructor(Document.class, Element.class);
				parser = constructor.newInstance(problemDoc, parserElmt);
			} else 
				parser = new XCSPparser (problemDoc);
			
			// see if the agents will send stats. If yes, add the appropriate listeners to the queue
			Element modsElmt = agentDescr.getRootElement().getChild("modules");
			if (modsElmt != null) {
				List<Element> modules = modsElmt.getChildren();
				statsReporters = new ArrayList<StatsReporter> (modules.size());
				for (Element statsListener : modules) {

					String reportStats = statsListener.getAttributeValue("reportStats");
					if (reportStats == null || ! Boolean.parseBoolean(reportStats)) 
						continue;

					// Instantiate the listener by calling its constructor in "statistics gatherer" mode
					Class<?> listenerClass = Class.forName(statsListener.getAttributeValue("className"));
					Constructor<?> constructor = listenerClass.getConstructor(Element.class, DCOPProblemInterface.class);
					StatsReporter listener = (StatsReporter) constructor.newInstance(statsListener, parser);

					this.statsReporters.add(listener);
					listener.getStatsFromQueue(this.queue);
				}
			}
			
			// Get the list of agents in the problem
			ArrayList<String> agents = new ArrayList<String> (parser.getAgents());			
			numberOfAgents = agents.size();
			numberOfAgentsFinished = 0;
			numberOfAgentsReported = 0;
			numberOfAgentsConnected = 0;
			
			// distribute the agents over the daemons
			// For now, the agents are equally distributed among the
			// available daemons
			if(!local) {
				dList = daemonList.keySet().toArray(new String[0]);
			}
			for(int agent = 0; agent < numberOfAgents; agent++) {
				
				// Get the problem description
				ProblemInterface<?, ?> problem = parser.getSubProblem(agents.get(agent));
				
				MessageWith2Payloads <ProblemInterface<?, ?>, Document> msg = 
					new MessageWith2Payloads <ProblemInterface<?, ?>, Document> (AGENT_CONFIGURATION_MESSAGE, problem, agentDescr);
				if(local) {
					queue.sendMessageToSelf(msg);
				} else {
					queue.sendMessage(dList[agent%dList.length], msg);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Reset all the fields used in monitoring the
	 * problem
	 */
	private void cleanProblem() {
		finalNCCCcount = 0;
		numberOfAgents = 0;
		numberOfAgentsFinished = 0;
		numberOfAgentsReported = 0;
		numberOfAgentsConnected = 0;
		if(statsReporters != null) {
			for(StatsReporter s : statsReporters) {
				queue.deleteIncomingMessagePolicy(s);
			}
			statsReporters.clear();
		}
	}
	

	/**
	 * A getter function for testing purposes
	 * @return debug
	 */
	public boolean getDebug() {
		return debug;
	}
	
	/**
	 * A getter function for testing purposes
	 * @return agentDescription
	 */
	public String getAgentDescription(){
		return agentDescriptionFile;
	}
	
	/**
	 * A getter function for testing purposes
	 * @return timeOut
	 */
	public int getTimeOut(){
		return timeOut;
	}
	
	/**
	 * A getter function for testing purposes
	 * @return logClass
	 */
	public String getLogClass() {
		return logClass;
	}

	/**
	 * A getter function for testing purposes
	 * @return resultFile
	 */
	public String getResultFile() {
		return resultFile;
	}
	
	/**
	 * A getter function for testing purposes
	 * @return daemonList
	 */
	public HashMap<String, String[]> getDaemonList() {
		return daemonList;
	}
	
	/** @return the maximum number of Non-Concurrent Constraint Checks */
	public long getNCCCs () {
		return this.finalNCCCcount;
	}
}
