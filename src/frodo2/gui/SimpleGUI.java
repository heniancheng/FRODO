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

package frodo2.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultEditorKit;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import frodo2.algorithms.AgentFactory;
import frodo2.algorithms.XCSPparser;
import frodo2.solutionSpaces.AddableInteger;

/** A simple GUI to configure and start FRODO
 * 
 * @author Andreas Schaedeli, Thomas Leaute
 * @todo Add the possibility to interrupt an algorithm. 
 * @todo Replace GraphViz by JUNG? http://jung.sourceforge.net/
 * @todo Display the console messages in a dedicated window?
 */
public class SimpleGUI extends JFrame implements ActionListener, ItemListener {

	/**Serial version ID of JFrame*/
	private static final long serialVersionUID = 4194657988533551087L;

	/**Class name of DOT renderer used to display graphs*/
	private static final String dotRendererClass = DOTrenderer.class.getName();

	/**Mapping from agent configuration file names to their absolute path*/
	private Map<String, String> agentNamesToPath;	//We have no interest in displaying the whole path to the user, so we use this map

	/**Text field containing path of problem file*/
	private JTextField problemField;

	/**List of DCOP solving agents*/
	private JComboBox agentList;

	/**Specified timeout value*/
	private JTextField timeoutField;

	/**Label indicating an error in chosen problem file*/
	private JLabel problemError;

	/**Label indicating an error in chosen agent configuration file*/
	private JLabel agentError;

	/**Label indicating an erroneous timeout value*/
	private JLabel timeoutError;

	/**Dialog window containing a text editor to change problem and agent configuration files*/
	private JDialog editorDialog;

	/**Pane containing a text editor to change problem and agent configuration files*/
	private JTextPane editorPane;

	/**String indicating the path of a temporary agent configuration file, if used*/
	private String tempAgentFile = null;


	/**
	 * Constructor defining and building the GUI
	 * 
	 * @param problemFilePath Path to problem file to use
	 * @param agentFilePath Path to agent configuration file to use
	 * @param timeout Timeout value
	 */
	public SimpleGUI(String problemFilePath, String agentFilePath, String timeout) {
		super("FRODO");

		//Configure menu bar
		JMenuBar menu = new JMenuBar();
		JMenu about = new JMenu("About");
		JMenuItem license = new JMenuItem("Display License");
		license.addActionListener(this);
		about.add(license);
		menu.add(about);
		this.setJMenuBar(menu);

		//Configure basic layout
		this.setLayout(new BorderLayout());
		JPanel center = new JPanel();
		center.setLayout(new GridLayout(3, 1));
		JPanel south = new JPanel();
		south.setLayout(new BorderLayout());

		//Configure sub-panel containing components related to choosing problem file
		JPanel problemPanel = new JPanel();
		problemPanel.setLayout(new GridLayout(4, 1));

		// Label
		JLabel problemLabel = new JLabel("Choose a problem file:");
		problemPanel.add(problemLabel);

		// Overall panel
		JPanel problemChooser = new JPanel();
		problemChooser.setLayout(new BorderLayout());
		problemPanel.add(problemChooser);
		
		// The field to choose the problem
		problemField = new JTextField(30);
		if(problemFilePath != null) {	//Set the problem file to the file given as parameter
			problemField.setText(problemFilePath);
		}
		problemChooser.add(problemField, BorderLayout.CENTER);
		
		// The problem buttons
		JPanel problemButtons = new JPanel();
		problemButtons.setLayout(new GridLayout(1, 3));
		problemChooser.add(problemButtons, BorderLayout.EAST);
		
		// The Browse button
		JButton problemBrowse = new JButton("Browse");
		problemBrowse.addActionListener(this);
		problemButtons.add(problemBrowse);
		
		// The Edit button
		JButton problemEdit = new JButton("Edit");
		problemEdit.addActionListener(this);
		problemButtons.add(problemEdit);
		
		// The Render button
		JButton problemRender = new JButton ("Render");
		problemRender.addActionListener(this);
		problemButtons.add(problemRender);

		// The error message field
		problemError = new JLabel("No correct problem file was specified");
		problemError.setForeground(Color.RED);
		problemError.setVisible(false);
		problemPanel.add(problemError);


		//Configure sub panel containing components relating to choosing agent configuration file
		JPanel agentPanel = new JPanel();
		agentPanel.setLayout(new GridLayout(3, 1));

		JLabel agentLabel = new JLabel("Choose an agent configuration file:");

		JPanel agentChooser = new JPanel();
		agentChooser.setLayout(new BorderLayout());
		agentList = new JComboBox (createAgentsList());
		agentList.addItem("Choose custom agent...");
		
		if(agentFilePath != null) {		//Sets the selected agent configuration file in the drop down menu to the file given as parameter
			if(agentNamesToPath.values().contains(agentFilePath)) {
				agentList.setSelectedItem(new File(agentFilePath).getName());
			}
			else {
				String agentName = new File(agentFilePath).getName();
				agentNamesToPath.put(agentName, agentFilePath);
				agentList.addItem(agentName);
				agentList.setSelectedItem(agentName);
			}
		}
		else {	//Default agent
			//agentList.setSelectedItem("DPOPagent.xml");
			agentList.setSelectedItem("ADOPTagent.xml");
		}
		
		agentList.addItemListener(this);
		JButton agentEdit = new JButton("Edit Agent File");
		agentEdit.addActionListener(this);

		agentChooser.add(agentList, BorderLayout.CENTER);
		agentChooser.add(agentEdit, BorderLayout.EAST);

		agentError = new JLabel("Agent description file was not found or is not valid");
		agentError.setForeground(Color.RED);
		agentError.setVisible(false);

		agentPanel.add(agentLabel);
		agentPanel.add(agentChooser);
		agentPanel.add(agentError);


		//Configure sub panel containing components related to choosing timeout value
		JPanel timeoutPanel = new JPanel();
		timeoutPanel.setLayout(new GridLayout(3, 1));

		JLabel timeoutLabel = new JLabel("Choose a timeout (in ms):");

		timeoutField = new JTextField((timeout == null ? "600000" : timeout));	//Set the timeout to the value given as parameter

		timeoutError = new JLabel("Specified timeout is no value of type Long");
		timeoutError.setForeground(Color.RED);
		timeoutError.setVisible(false);

		timeoutPanel.add(timeoutLabel);
		timeoutPanel.add(timeoutField);
		timeoutPanel.add(timeoutError);


		//Add all sub panels to the center panel
		center.add(problemPanel);
		center.add(agentPanel);
		center.add(timeoutPanel);


		//Configure lower panel containing solve button
		JButton solve = new JButton("Solve");
		solve.addActionListener(this);

		south.add(solve, BorderLayout.EAST);

		
		//Add center and south panel
		this.add(center, BorderLayout.CENTER);
		this.add(south, BorderLayout.SOUTH);
		
		this.setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		
		//Configure window size as a function of total size all components need
		Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension dim = this.getPreferredSize();
		this.setSize(dim);
		this.setLocation(screenDim.width / 2 - dim.width / 2, screenDim.height / 2 - dim.height / 2);	//Center the window
		this.setVisible(true);
	}

	/**
	 * 
	 * @param args Array of parameters containing 3 optional arguments:<br>
	 * - Path to problem file (must be the first or the only given file)<br>
	 * - Path to agent configuration file (must be the second given file)<br>
	 * - -timeout followed by TIMEOUT_VALUE (can be given in any position)
	 */
	public static void main(String[] args) {

		// The GNU GPL copyright notice
		System.out.println("FRODO  Copyright (C) 2008-2013  Thomas Leaute, Brammert Ottens & Radoslaw Szymanek");
		System.out.println("This program comes with ABSOLUTELY NO WARRANTY.");
		System.out.println("This is free software, and you are welcome to redistribute it");
		System.out.println("under certain conditions. \n");
		
		String problemFilePath = null;
		boolean problemFileGiven = false;	//Assures we only check for problem file once
		String agentFilePath = null;
		boolean agentFileGiven = false;	//Assures we only check for agent file once
		String timeout = null;
		
		for(int i = 0; i < args.length; i++) {

			//Parse timeout
			if(args[i].equals("-timeout")) {
				if(i + 1 < args.length) {
					try{
						int timeoutValue = Integer.parseInt(args[i + 1]);
						timeout = "" + timeoutValue;
					}
					catch(NumberFormatException e) {
						System.err.println("Given timeout value is not an integer");
					}
					i++;
				}
			}

			//Parse problem file (must be the first or the only file given)
			else if(!problemFileGiven) {
				problemFileGiven = true;
				File problemFile = new File(args[i]);
				if(problemFile.exists() && problemFile.isFile()) {
					problemFilePath = problemFile.getAbsolutePath();
				}
				else {
					System.err.println("Given problem file could not be found");
				}
			}

			//Parse agent file (must be the second file given)
			else if(!agentFileGiven){
				agentFileGiven = true;
				File agentFile = new File(args[i]);
				if(agentFile.exists() && agentFile.isFile()) {
					agentFilePath = agentFile.getAbsolutePath();
				}
				else {
					System.err.println("Given agent configuration file could not be found");
				}
			}
		}
		

		new SimpleGUI(problemFilePath, agentFilePath, timeout);
	}

	
	/**
	 * This method is called whenever a button associated with this ActionListener is pressed
	 * 
	 * @param e Action event
	 */
	public void actionPerformed(ActionEvent e) {
		String actionCommand = e.getActionCommand();

		if(actionCommand.equals("Display License")) {	//Menu Bar --> About --> Display License
			JTextArea licenseArea = new JTextArea(readLicense());
			licenseArea.setRows(50);
			JOptionPane.showMessageDialog(this, new JScrollPane(licenseArea), "FRODO License", JOptionPane.PLAIN_MESSAGE);
		}

		//Browses for an existing problem file
		else if(actionCommand.equals("Browse")) {	//Browse button in problem file part
			JFileChooser fileChooser = new JFileChooser(".");
			int option = fileChooser.showOpenDialog(this);
			if(option != JFileChooser.CANCEL_OPTION && option != JFileChooser.ERROR_OPTION) {	//User did not exit dialog without choosing
				File problemFile = fileChooser.getSelectedFile();
				problemField.setText(problemFile.getAbsolutePath());
				problemError.setVisible(false);
			}
			fileChooser = null;	//Needs to be done, as open JFileChooser instances may cause problems when exiting the program
		}

		//Shows a dialog containing a text editor and the chosen problem file's content
		else if(actionCommand.equals("Edit")) {	//Edit button in problem file part
			if(new File(problemField.getText()).exists() && new File(problemField.getText()).isFile()) {
				editProblemFile();
			}
			else {	//No valid problem file had been chosen
				problemError.setVisible(true);
			}
		}
		
		// Starts a DOTrenderer for the selected problem
		else if (actionCommand.equals("Render")) {
			try {
				XCSPparser<AddableInteger, AddableInteger> parser = 
					new XCSPparser<AddableInteger, AddableInteger> (XCSPparser.parse(this.problemField.getText(), false));
				new DOTrenderer ("Constraint graph", parser.toDOT());
			} catch (Exception e2) {
				this.problemError.setVisible(true);
			}
		}

		//Shows a dialog containing a text editor and the chosen agent configuration file's content
		else if(actionCommand.equals("Edit Agent File")) {	//Edit button in agent configuration file part
			String agentFile = agentNamesToPath.get(agentList.getSelectedItem());
			editAgentFile(agentList.getSelectedItem().toString().contains("CUSTOM"), agentFile);
		}

		//Saves the current editor content to the specified problem file
		else if(actionCommand.equals("Save Problem")) {	//Save button in problem file editor dialog
			saveFile(problemField.getText());
		}

		//Saves the current editor content to the specified agent configuration file
		else if(actionCommand.equals("Save Agent")) {	//Save button in agent configuration file editor dialog (custom configuration file) 
			saveFile(agentNamesToPath.get(agentList.getSelectedItem()));
		}

		//Saves the current editor content to a temporary agent configuration file
		else if(actionCommand.equals("Save Agent Temp")) {	//Save button in agent configuration file editor dialog (built-in configuration file)
			tempAgentFile = ".temp." + agentList.getSelectedItem().toString();
			saveFile(tempAgentFile);
			
			// Select this new custom file
			String fileName = "(CUSTOM) " + agentList.getSelectedItem().toString();
			agentNamesToPath.put(fileName, tempAgentFile);
			agentList.addItem(fileName);
			agentList.setSelectedItem(fileName);	//Select chosen agent
		}

		//Saves the current editor content to a new file specified by the file chooser dialog
		else if(actionCommand.equals("Save as")) {	//Save as button in agent configuration file editor dialog (built-in configuration file)
			JFileChooser fileChooser = new JFileChooser(".");
			int option = fileChooser.showSaveDialog(this);
			if(option != JFileChooser.CANCEL_OPTION && option != JFileChooser.ERROR_OPTION) {	//User did not exit dialog without choosing
				File file = fileChooser.getSelectedFile();
				String filePath = file.getAbsolutePath();
				saveFile(filePath);
				
				// Select this new custom file
				String fileName = "(CUSTOM)" + file.getName();
				agentNamesToPath.put(fileName, filePath);
				agentList.addItem(fileName);
				agentList.setSelectedItem(fileName);	//Select chosen agent
			}
			fileChooser = null;	//Needs to be done, as open JFileChooser instances may cause problems when exiting the program
		}

		//Exits the editor dialog without saving changes
		else if(actionCommand.equals("Cancel")) {	//Cancel button in any editor dialog
			editorDialog.dispose();
		}

		//Tries to solve the given DCOP problem
		else if(actionCommand.equals("Solve")) {
			solveProblem();
		}
	}

	/**
	 * This method is called when another item is selected from the list of available agent configuration files. This is mainly used to add custom files.
	 * 
	 * @param e Item event
	 */
	public void itemStateChanged(ItemEvent e) {
		if(e.getStateChange() == ItemEvent.SELECTED && e.getItem().equals("Choose custom agent...")) {	//User wants to choose a custom agent configuration file
			JFileChooser fileChooser = new JFileChooser(".");
			int option = fileChooser.showOpenDialog(this);
			if(option != JFileChooser.CANCEL_OPTION || option != JFileChooser.ERROR_OPTION) {	//User did not exit dialog without choosing
				File agentFile = fileChooser.getSelectedFile();
				String fileName = "(CUSTOM) " + agentFile.getName();
				
				//Add new agent to the list and map it to its path
				agentNamesToPath.put(fileName, agentFile.getAbsolutePath());
				agentList.addItem(fileName);
				agentList.setSelectedItem(fileName);	//Select chosen agent
			}
			fileChooser = null;	//Needs to be done, as open JFileChooser instances may cause problems when exiting the program
		}
		tempAgentFile = null;	//If user chooses another agent, the temporary agent configuration file is no longer used
		agentError.setVisible(false);	//If user chooses another agent, error must not be displayed any more
	}

	/**
	 * This method reads the license from the file LICENSE.txt and displays it if the file exists and can be read
	 * 
	 * @return String containing license text
	 */
	private String readLicense() {

		Scanner licenseScanner;
		StringBuilder license = new StringBuilder();
		try{
			licenseScanner = new Scanner(new File("LICENSE.txt"));
			while(licenseScanner.hasNextLine()) {
				license.append(licenseScanner.nextLine() + "\n");
			}
			licenseScanner.close();
			return license.toString();
		}
		catch(FileNotFoundException e) {
			return "License file \'LICENSE.txt\' could not be read";
		}
	}

	/**
	 * This method creates the mapping from all currently implemented DCOP solving agents to their paths, and returns an array containing only the file
	 * names of the configuration files, in order to display them in the drop down list.
	 * 
	 * @return Array containing the names of all currently implemented DCOP solving agents
	 */
	private String[] createAgentsList() {

		// Check if the OR-Objects library is available to use the VRP code
		boolean enableVRP = true;
		try {
			Class.forName("com.orllc.orobjects.lib.graph.vrp.Composite");
		} catch (ClassNotFoundException e) {
			enableVRP = false;
		}
		
		// Check if JaCoP is available
		boolean enableJaCoP = true;
		try {
			Class.forName("JaCoP.core.Store");
		} catch (ClassNotFoundException e) {
			enableJaCoP = false;
		}

		agentNamesToPath = new HashMap<String, String>();

		// ADOPT
		agentNamesToPath.put("ADOPTagent.xml", "/frodo2/algorithms/adopt/ADOPTagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("ADOPTagentVRP.xml", "/frodo2/algorithms/adopt/ADOPTagent.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("ADOPTagentJaCoP.xml", "/frodo2/algorithms/adopt/ADOPTagentJaCoP.xml");

		// ASO-DPOP
		agentNamesToPath.put("ASODPOPagent.xml", "/frodo2/algorithms/asodpop/ASODPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("ASODPOPagentVRP.xml", "/frodo2/algorithms/asodpop/ASODPOPagentVRP.xml");
		agentNamesToPath.put("ASODPOPBinaryagent.xml", "/frodo2/algorithms/asodpop/ASODPOPBinaryagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("ASODPOPBinaryagentVRP.xml", "/frodo2/algorithms/asodpop/ASODPOPBinaryagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("ASODPOPagentJaCoP.xml", "/frodo2/algorithms/asodpop/ASODPOPagentJaCoP.xml");
		agentNamesToPath.put("BNBADOPTagent.xml", "/frodo2/algorithms/bnbadopt/BNBADOPTagent.xml");
		// DPOP
		agentNamesToPath.put("DPOPagent.xml", "/frodo2/algorithms/dpop/DPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("DPOPagentVRP.xml", "/frodo2/algorithms/dpop/DPOPagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("DPOPagentJaCoP.xml", "/frodo2/algorithms/dpop/DPOPagentJaCoP.xml");

		// MB-DPOP
		agentNamesToPath.put("MB-DPOPagent.xml", "/frodo2/algorithms/dpop/memory/MB-DPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("MB-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/memory/MB-DPOPagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("MB-DPOPagentJaCoP.xml", "/frodo2/algorithms/dpop/memory/MB-DPOPagentJaCoP.xml");

		// DSA
		agentNamesToPath.put("DSAagent.xml", "/frodo2/algorithms/localSearch/dsa/DSAagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("DSAagentVRP.xml", "/frodo2/algorithms/localSearch/dsa/DSAagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("DSAagentJaCoP.xml", "/frodo2/algorithms/localSearch/dsa/DSAagentJaCoP.xml");
		
		// E[DPOP]
		agentNamesToPath.put("E-DPOPagent.xml", "/frodo2/algorithms/dpop/stochastic/E-DPOP.xml");
		agentNamesToPath.put("Complete-E-DPOPagent.xml", "/frodo2/algorithms/dpop/stochastic/Complete-E-DPOP.xml");
		agentNamesToPath.put("Robust-E-DPOPagent.xml", "/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOP.xml");
		if (enableVRP) {
			agentNamesToPath.put("Complete-E-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/stochastic/Complete-E-DPOPagentVRP.xml");
			agentNamesToPath.put("E-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/stochastic/E-DPOPagentVRP.xml");
			agentNamesToPath.put("Robust-E-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/stochastic/robust/Robust-E-DPOPagentVRP.xml");
		}

		// O-DPOP
		agentNamesToPath.put("ODPOPagent.xml", "/frodo2/algorithms/odpop/ODPOPagent.xml");
		agentNamesToPath.put("ODPOPagentBinaryDomains.xml", "/frodo2/algorithms/odpop/ODPOPagentBinaryDomains.xml");
		if (enableVRP) 
			agentNamesToPath.put("ODPOPagentBinaryDomainsVRP.xml", "/frodo2/algorithms/odpop/ODPOPagentBinaryDomainsVRP.xml");
		agentNamesToPath.put("ODPOPagentFullDomain.xml", "/frodo2/algorithms/odpop/ODPOPagentFullDomain.xml");
		if (enableVRP) 
			agentNamesToPath.put("ODPOPagentVRP.xml", "/frodo2/algorithms/odpop/ODPOPagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("ODPOPagentJaCoP.xml", "/frodo2/algorithms/odpop/ODPOPagentJaCoP.xml");
		
		// MGM
		agentNamesToPath.put("MGMagent.xml", "/frodo2/algorithms/localSearch/mgm/MGMagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("MGMagentVRP.xml", "/frodo2/algorithms/localSearch/mgm/MGMagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("MGMagentJaCoP.xml", "/frodo2/algorithms/localSearch/mgm/MGMagentJaCoP.xml");
		
		// MGM2
		agentNamesToPath.put("MGM2agent.xml", "/frodo2/algorithms/localSearch/mgm/mgm2/MGM2agent.xml");
		if (enableVRP) 
			agentNamesToPath.put("MGM2agentVRP.xml", "/frodo2/algorithms/localSearch/mgm/mgm2/MGM2agentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("MGM2agentJaCoP.xml", "/frodo2/algorithms/localSearch/mgm/mgm2/MGM2agentJaCoP.xml");
		
		// Param-DPOP
		agentNamesToPath.put("Param-DPOPagent.xml", "/frodo2/algorithms/dpop/param/Param-DPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("Param-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/param/Param-DPOPagentVRP.xml");

		// P(2)-DPOP
		agentNamesToPath.put("P-DPOPagent.xml", "/frodo2/algorithms/dpop/privacy/P-DPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("P-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/privacy/P-DPOPagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("P-DPOPagentJaCoP.xml", "/frodo2/algorithms/dpop/privacy/P-DPOPagentJaCoP.xml");
		
		agentNamesToPath.put("P1.5-DPOPagent.xml", "/frodo2/algorithms/dpop/privacy/P1.5-DPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("P1.5-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/privacy/P1.5-DPOPagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("P1.5-DPOPagentJaCoP.xml", "/frodo2/algorithms/dpop/privacy/P1.5-DPOPagentJaCoP.xml");
		
		agentNamesToPath.put("P2-DPOPagent.xml", "/frodo2/algorithms/dpop/privacy/P2-DPOPagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("P2-DPOPagentVRP.xml", "/frodo2/algorithms/dpop/privacy/P2-DPOPagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("P2-DPOPagentJaCoP.xml", "/frodo2/algorithms/dpop/privacy/P2-DPOPagentJaCoP.xml");


		// SynchBB
		agentNamesToPath.put("SynchBBagent.xml", "/frodo2/algorithms/synchbb/SynchBBagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("SynchBBagentVRP.xml", "/frodo2/algorithms/synchbb/SynchBBagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("SynchBBagentJaCoP.xml", "/frodo2/algorithms/synchbb/SynchBBagentJaCoP.xml");
		
		// AFB
		agentNamesToPath.put("AFBagent.xml", "/frodo2/algorithms/afb/AFBagent.xml");
		if (enableVRP) 
			agentNamesToPath.put("AFBagentVRP.xml", "/frodo2/algorithms/afb/AFBagentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("AFBagentJaCoP.xml", "/frodo2/algorithms/afb/AFBagentJaCoP.xml");

		// MPC-DisCSP4
		agentNamesToPath.put("MPC-DisCSP4.xml", "/frodo2/algorithms/mpc_discsp/MPC-DisCSP4.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("MPC-DisCSP4_JaCoP.xml", "/frodo2/algorithms/mpc_discsp/MPC-DisCSP4_JaCoP.xml");

		// MPC-DisWCSP4
		agentNamesToPath.put("MPC-DisWCSP4.xml", "/frodo2/algorithms/mpc_discsp/MPC-DisWCSP4.xml");
		if (enableVRP) 
			agentNamesToPath.put("MPC-DisWCSP4_VRP.xml", "/frodo2/algorithms/mpc_discsp/MPC-DisWCSP4_VRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("MPC-DisWCSP4_JaCoP.xml", "/frodo2/algorithms/mpc_discsp/MPC-DisWCSP4_JaCoP.xml");

		// Max-Sum
		agentNamesToPath.put("MaxSumAgent.xml", "/frodo2/algorithms/maxsum/MaxSumAgent.xml");
		if (enableVRP) 
			agentNamesToPath.put("MaxSumAgentVRP.xml", "/frodo2/algorithms/maxsum/MaxSumAgentVRP.xml");
		if (enableJaCoP) 
			agentNamesToPath.put("MaxSumAgentJaCoP.xml", "/frodo2/algorithms/maxsum/MaxSumAgentJaCoP.xml");
		
		List<String> keys = new ArrayList<String>(agentNamesToPath.keySet());
		Collections.sort(keys);
		return keys.toArray(new String [keys.size()]);
	}

	/**
	 * This method first checks if all the necessary parameters (problem file, agent description file, timeout value) are valid. If so, it slightly changes the agent configuration file
	 * in order to use a DOT renderer to display graphs, and then starts the solver.
	 */
	private void solveProblem() {
		
		//Initialize
		boolean paramsOK = true;
		problemError.setVisible(false);
		agentError.setVisible(false);
		timeoutError.setVisible(false);

		//Get problem file path and check if file is valid (exists and is file)
		final String problemFile = problemField.getText();
		if(!new File(problemFile).exists() || !new File(problemFile).isFile()) {
			problemError.setVisible(true);
			paramsOK = false;
		}
		
		// Attempt to parse the agent configuration file
		final String tempAgentFileName = ".temp.agent.xml";
		try {
			Document doc;
			
			// Check whether we are using an external file
			String agentName = (String) agentList.getSelectedItem();
			if (agentName.contains("(CUSTOM)"))
				doc = XCSPparser.parse(new File(tempAgentFile == null ? 
												agentNamesToPath.get(agentName) : 
												tempAgentFile), false);
			else // file is inside FRODO's JAR
				doc = XCSPparser.parse(SimpleGUI.class.getResourceAsStream(this.agentNamesToPath.get(agentName)), false);
			
			Element root = doc.getRootElement();
				
			//Set DOTrenderer for parser
			Element parser = root.getChild("parser");
			parser.setAttribute("DOTrenderer", dotRendererClass);

			//Set DOTrenderer for every module containing this attribute
			for(Object module : root.getChild("modules").getChildren()) {
				if(((Element)module).getAttribute("DOTrenderer") != null) {
					((Element)module).setAttribute("DOTrenderer", dotRendererClass);
				}
			}

			//Saves the modified Document in a temporary file, which will be given to the solver
			new XMLOutputter(Format.getPrettyFormat()).output(doc, new FileWriter (new File(tempAgentFileName)));
			new File(tempAgentFileName).deleteOnExit();
		}
		catch(Exception e) {	//Agent description file did not contain valid XML code or could not be read
			agentError.setVisible(true);
			paramsOK = false;
			e.printStackTrace();
		}

		//Get timeout value and test if it is an integer
		final String timeoutString = timeoutField.getText();
		if (timeoutString.length() > 0) {
			try{
				Long.parseLong(timeoutString);
			}
			catch(NumberFormatException e) {
				timeoutError.setVisible(true);
				paramsOK = false;
			}
		}

		//All parameters are valid
		if(paramsOK) {
			this.setEnabled(false);	//Lock the window
			
			//Show infinite progress bar while solver is working
			JProgressBar progressBar = new JProgressBar();
			progressBar.setIndeterminate(true);
			progressBar.setString("Solving DCOP...");
			progressBar.setStringPainted(true);
			final JOptionPane progressViewer = new JOptionPane(progressBar, JOptionPane.PLAIN_MESSAGE);
			progressViewer.setOptions(new String[0]);
			final JDialog dialog = progressViewer.createDialog(SimpleGUI.this, "Please wait...");
			new Thread(new Runnable() {

				public void run() {
					dialog.setEnabled(false);
					dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
					dialog.setVisible(true);
				}
			}).start();

			//Start solver in a new thread; else, progress bar can not be shown correctly
			new Thread(new Runnable() {

				@SuppressWarnings("rawtypes")
				public void run() {
					
					try {
						System.out.println("Parsing the chosen problem file...");
						Document problem = XCSPparser.parse(problemFile, false);

						System.out.println("Parsing the chosen agent configuration file...");
						Document agent = XCSPparser.parse(tempAgentFileName, false);

						System.out.println("Setting up the agents...");
						AgentFactory<?> factory;
						if (timeoutString.length() > 0) 
							factory = new AgentFactory (problem, agent, Long.parseLong(timeoutString));
						else
							factory = new AgentFactory (problem, agent);

						System.out.println("Waiting for the agents to terminate...");
						factory.end();
						System.out.println("Done.");
						
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					dialog.dispose();
					SimpleGUI.this.setEnabled(true);	//Unlocks the window when solver is done
				}
			}).start();
		}
	}

	/**
	 * This method creates a dialog containing a text editor to edit the chosen problem file. It allows saving the changes by overwriting the existing file.
	 */
	private void editProblemFile() {
		
		//Initialize components
		editorPane = new JTextPane();
		JPanel editorPanel = new JPanel();
		editorPanel.add(editorPane);

		JButton saveButton = new JButton("Save");
		saveButton.setActionCommand("Save Problem");
		saveButton.addActionListener(this);
		saveButton.setToolTipText("Overwrites existing file with editor content");
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		cancelButton.setToolTipText("Closes editor without saving");

		//Create JOptionPane with scroll bars and the specified buttons
		JOptionPane editorOptionPane = new JOptionPane(new JScrollPane(editorPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS), 
				JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[] {saveButton, cancelButton});

		try{
			//Reads the given problem file into the editor
			editorPane.read(new FileInputStream(new File(problemField.getText())), new DefaultEditorKit());
			editorDialog = editorOptionPane.createDialog(this, "Edit File");

			//Set limit and actual dimensions
			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			int maxWidth = dim.width / 5 * 4;
			int maxHeight = dim.height / 3 * 2;
			int width = Math.min(editorPanel.getSize().width + 50, maxWidth);
			int height = Math.min(editorPanel.getSize().height + 100, maxHeight);
			editorDialog.setSize(width, height);
			editorDialog.setLocation(dim.width / 2 - width / 2, dim.height / 2 - height / 2);	//Center dialog window

			//Enforces that user must click on "Save" or "Cancel"
			editorDialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
			editorDialog.setVisible(true);
		}
		catch(Exception ex) {	//Problem file could not be read correctly
			problemError.setVisible(true);
		}
	}

	/**
	 * This method creates a dialog containing a text editor to edit the chosen agent configuration file. Depending on whether the file is custom or built-in, it allows overwriting the
	 * existing file, saving the editor content in a specified location or in a temporary file.
	 * 
	 * @param isCustomFile <b>true</b> if chosen agent configuration file is a custom file, <b>false</b> if it is a built-in file
	 * @param filePath Path to the chosen agent configuration file
	 */
	private void editAgentFile(boolean isCustomFile, String filePath) {
		
		//Initialize components
		editorPane = new JTextPane();
		JPanel editorPanel = new JPanel();
		editorPanel.add(editorPane);

		JButton saveButton = new JButton("Save");
		saveButton.addActionListener(this);
		if(isCustomFile) {	//Custom files are overwritten with the editor's content
			saveButton.setActionCommand("Save Agent");
			saveButton.setToolTipText("Overwrites existing file with editor content");
		}
		else {	//Built-in files must not be overwritten; the editor's content is saved in a temporary file
			saveButton.setActionCommand("Save Agent Temp");
			saveButton.setToolTipText("Saves edited content in a temporary file");
		}

		//Save as button only used with built-in files (allows creating a new file)
		JButton saveAsButton = null;
		if(!isCustomFile) {
			saveAsButton = new JButton("Save as");
			saveAsButton.addActionListener(this);
			saveAsButton.setToolTipText("Lets you choose where to save the editor content");
		}

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		cancelButton.setToolTipText("Closes editor without saving");

		//Options depend on whether chosen file is custom or built-in
		Object[] buttons = (isCustomFile ? new Object[] {saveButton, cancelButton} : new Object[] {saveButton, saveAsButton, cancelButton});
		JOptionPane editorOptionPane = new JOptionPane(new JScrollPane(editorPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS), 
				JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, buttons);

		try{
			//Read chosen file's content into editor
			if(isCustomFile) {
				editorPane.read(new FileInputStream(new File(filePath)), new DefaultEditorKit());
			}
			else {
				editorPane.read(SimpleGUI.class.getResourceAsStream(filePath), new DefaultEditorKit());
			}
			editorDialog = editorOptionPane.createDialog(this, "Edit File");

			//Set limit and actual dimensions
			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			int maxWidth = dim.width / 5 * 4;
			int maxHeight = dim.height / 3 * 2;
			int width = Math.min(editorPanel.getSize().width + 50, maxWidth);
			int height = Math.min(editorPanel.getSize().height + 100, maxHeight);
			editorDialog.setSize(width, height);
			editorDialog.setLocation(dim.width / 2 - width / 2, dim.height / 2 - height / 2);	//Center dialog window

			editorDialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
			editorDialog.setVisible(true);
		}
		catch(Exception ex) {	//Agent configuration file could not be read correctly
			agentError.setVisible(true);
		}
	}

	/**
	 * This method saves the current editor content to a file with the given path. Existing files are overwritten.
	 * 
	 * @param filePath Path and name of file where editor content should be saved
	 */
	private void saveFile(String filePath) {
		try{
			PrintWriter writer = new PrintWriter(filePath);
			writer.println(editorPane.getText());
			writer.close();
		}
		catch(Exception e) {
			System.out.println("Could not save file: " + filePath);
		}
		if(filePath.contains(".temp.")) {	//Temporary files have to be deleted when JVM exits
			new File(filePath).deleteOnExit();
		}
		editorDialog.dispose();
	}
}
