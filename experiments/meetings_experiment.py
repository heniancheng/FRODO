"""
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
"""

""" This script reproduces some of the experimental results in Section 6.2 of the following journal article: 

Thomas Leaute and Boi Faltings. Protecting privacy through distributed computation in multi-agent decision making. 
Journal of Artificial Intelligence Research (JAIR), 47:649-695, August 2013.
"""

# Add the FRODO benchmarks folder to the Python path and import the frodo2 module
import sys
sys.path.append("../frodo2.jar/frodo2/benchmarks")
import frodo2

# The command to call java and the JVM parameters
java = "java"
javaParams = [
			"-Xmx2G", # sets the Java heap space to 2 GB
			"-classpath", "../frodo2.jar", # sets the Java classpath to include FRODO
			]

# Define the random problems to be generated
generator = "frodo2.benchmarks.meetings.MeetingScheduling"
genParams = [
			"-i", 				# use intensional constraints
			"-EAV", 			# use the "Events As Variables" formulation
			"-PEAV", 			# also use the "Private Events As Variables" formulation
# 			"-infinity", 1, 	# the cost incurred by violation one constraint (default = infinity)
#			"-tightness", .3, 	# the probability for an agent to be unavailable at any given time slot
# 			"-maxCost", 10, 	# each agent has a random cost (<= maxCost) for attending a meeting at any given time slot
			3, 					# the number of agents
			list(range(1, 7)), 	# the varying number of meetings
			2, 					# the number of agents per meeting
			8, 					# the number of timeslots
			]
problemFileEAV = "meetingScheduling_EAV.xml"
problemFilePEAV = "meetingScheduling_PEAV.xml"
nbrProblems = 7 # for each combination of generator options, the number of problems to run the algorithms on

# Define the algorithms to run
# Each algorithm is a list [algoName, solverClassName, agentConfigFilePath, inputProblemFilePath]
algos = [
# 		["ADOPT", "frodo2.algorithms.adopt.ADOPTsolver", "../agents/ADOPT/ADOPTagentJaCoP.xml", problemFilePEAV], 
# 
# 		["AFB", "frodo2.algorithms.afb.AFBsolver", "../agents/AFB/AFBagentJaCoP.xml", problemFilePEAV], 
# 
		["DPOP", "frodo2.algorithms.dpop.DPOPsolver", "../agents/DPOP/DPOPagentJaCoP.xml", problemFilePEAV], 
# 		["ASO-DPOP", "frodo2.algorithms.asodpop.ASODPOPsolver", "../agents/DPOP/ASO-DPOP/ASO-DPOPagentJaCoP.xml", problemFilePEAV], 
# 		["MB-DPOP", "frodo2.algorithms.dpop.DPOPsolver", "../agents/DPOP/MB-DPOP/MB-DPOPagentJaCoP.xml", problemFilePEAV], 
#  		["O-DPOP", "frodo2.algorithms.odpop.ODPOPsolver", "../agents/DPOP/O-DPOP/O-DPOPagentJaCoP.xml", problemFilePEAV], 
		["P-DPOP", "frodo2.algorithms.dpop.privacy.P_DPOPsolver", "../agents/DPOP/P-DPOP/P-DPOPagentJaCoP.xml", problemFilePEAV], 
#  		["P3/2-DPOP", "frodo2.algorithms.dpop.privacy.P3halves_DPOPsolver", "../agents/DPOP/P-DPOP/P1.5-DPOPagentJaCoP.xml", problemFilePEAV], 
		["P2-DPOP", "frodo2.algorithms.dpop.privacy.P2_DPOPsolver", "../agents/DPOP/P-DPOP/P2-DPOPagentJaCoP_DisCSP.xml", problemFilePEAV], 
# 		["P2-DPOP", "frodo2.algorithms.dpop.privacy.P2_DPOPsolver", "../agents/DPOP/P-DPOP/P2-DPOPagentJaCoP.xml", problemFilePEAV], # to be used with -maxCost
# 
# 		["DSA", "frodo2.algorithms.localSearch.dsa.DSAsolver", "../agents/DSA/DSAagentJaCoP.xml", problemFilePEAV], 
# 
# 		["MaxSum", "frodo2.algorithms.maxsum.MaxSumSolver", "../agents/MaxSum/MaxSumAgentJaCoP.xml", problemFilePEAV], 
# 
# 		["MGM", "frodo2.algorithms.localSearch.mgm.MGMsolver", "../agents/MGM/MGMagentJaCoP.xml", problemFilePEAV], 
# 		["MGM2", "frodo2.algorithms.localSearch.mgm.mgm2.MGM2solver", "../agents/MGM/MGM2agentJaCoP.xml", problemFilePEAV], 
# 
		["MPC-DisCSP4", "frodo2.algorithms.mpc_discsp.MPC_DisWCSP4solver", "../agents/MPC/MPC-DisCSP4_JaCoP.xml", problemFileEAV], 
# 		["MPC-DisWCSP4", "frodo2.algorithms.mpc_discsp.MPC_DisWCSP4solver", "../agents/MPC/MPC-DisWCSP4_JaCoP.xml", problemFileEAV], # to be used with -maxCost
# 
# 		["SynchBB", "frodo2.algorithms.synchbb.SynchBBsolver", "../agents/SynchBB/SynchBBagentJaCoP.xml", problemFilePEAV], 
		]
timeout = 600 # in seconds

# The CSV file to which the statistics should be written
output = "outputMeetings.csv"

# Run the experiment
frodo2.run(java, javaParams, generator, genParams, nbrProblems, algos, timeout, output)

# Tip: if some of the algorithms tend to time out most of the time on some problem files, 
# you can run 2 experiments: one for all algorithms on the smaller problem sizes, 
# and one with only the faster algorithms on the larger problem sizes

# Plot the graphs
frodo2.plot(output, xCol = 9, yCol = 13) # the first column has index 0
