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

""" This script reproduces some of the experimental results in Section 6.1 of the following journal article: 

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
generator = "frodo2.benchmarks.graphcoloring.GraphColoring"
genParams = [
			"-i", 				# use intensional constraints
# 			"-soft", 			# outputs a Max-DisCSP instead of a pure DisCSP
# 			"-mpc", 			# also outputs a (Max-)DisCSP formulation in which all constraints are public
			list(range(3, 11)), # the varying number of nodes
			.4, 				# the density 
			0.0, 				# the tightness
			3, 					# the number of colors
# 			.3, 				# the fraction of nodes whose colors are uncontrollable (produces a StochDCOP)
			]
problemFile = "graphColoring.xml"
nbrProblems = 7 # for each combination of generator options, the number of problems to run the algorithms on

# Define the algorithms to run
# Each algorithm is a list [algoName, solverClassName, agentConfigFilePath, inputProblemFilePath]
algos = [
# 		["ADOPT", "frodo2.algorithms.adopt.ADOPTsolver", "../agents/ADOPT/ADOPTagentJaCoP.xml", problemFile], 
# 
# 		["AFB", "frodo2.algorithms.afb.AFBsolver", "../agents/AFB/AFBagentJaCoP.xml", problemFile], 
# 
		["DPOP", "frodo2.algorithms.dpop.DPOPsolver", "../agents/DPOP/DPOPagentJaCoP.xml", problemFile], 
# 		["ASO-DPOP", "frodo2.algorithms.asodpop.ASODPOPsolver", "../agents/DPOP/ASO-DPOP/ASO-DPOPagentJaCoP.xml", problemFile], 
# 		["MB-DPOP", "frodo2.algorithms.dpop.DPOPsolver", "../agents/DPOP/MB-DPOP/MB-DPOPagentJaCoP.xml", problemFile], 
#  		["O-DPOP", "frodo2.algorithms.odpop.ODPOPsolver", "../agents/DPOP/O-DPOP/O-DPOPagentJaCoP.xml", problemFile], 
		["P-DPOP", "frodo2.algorithms.dpop.privacy.P_DPOPsolver", "../agents/DPOP/P-DPOP/P-DPOPagentJaCoP.xml", problemFile], 
		["P3/2-DPOP", "frodo2.algorithms.dpop.privacy.P3halves_DPOPsolver", "../agents/DPOP/P-DPOP/P1.5-DPOPagentJaCoP.xml", problemFile], 
		["P2-DPOP", "frodo2.algorithms.dpop.privacy.P2_DPOPsolver", "../agents/DPOP/P-DPOP/P2-DPOPagentJaCoP_DisCSP.xml", problemFile], 
# 		["P2-DPOP", "frodo2.algorithms.dpop.privacy.P2_DPOPsolver", "../agents/DPOP/P-DPOP/P2-DPOPagentJaCoP.xml", problemFile], # to be used with -soft
# 
# 		["DSA", "frodo2.algorithms.localSearch.dsa.DSAsolver", "../agents/DSA/DSAagentJaCoP.xml", problemFile], 
# 
# 		["MaxSum", "frodo2.algorithms.maxsum.MaxSumSolver", "../agents/MaxSum/MaxSumAgentJaCoP.xml", problemFile], 
# 
# 		["MGM", "frodo2.algorithms.localSearch.mgm.MGMsolver", "../agents/MGM/MGMagentJaCoP.xml", problemFile], 
# 		["MGM2", "frodo2.algorithms.localSearch.mgm.mgm2.MGM2solver", "../agents/MGM/MGM2agentJaCoP.xml", problemFile], 
# 
		["MPC-DisCSP4", "frodo2.algorithms.mpc_discsp.MPC_DisWCSP4solver", "../agents/MPC/MPC-DisCSP4_JaCoP.xml", problemFile], 
# 		["MPC-DisWCSP4", "frodo2.algorithms.mpc_discsp.MPC_DisWCSP4solver", "../agents/MPC/MPC-DisWCSP4_JaCoP.xml", problemFile], # to be used with -soft
# 
# 		["SynchBB", "frodo2.algorithms.synchbb.SynchBBsolver", "../agents/SynchBB/SynchBBagentJaCoP.xml", problemFile], 
		]
timeout = 600 # in seconds

# The CSV file to which the statistics should be written
output = "outputGraphColoring.csv"

# Run the experiment
frodo2.run(java, javaParams, generator, genParams, nbrProblems, algos, timeout, output)

# Tip: if some of the algorithms tend to time out most of the time on some problem files, 
# you can run 2 experiments: one for all algorithms on the smaller problem sizes, 
# and one with only the faster algorithms on the larger problem sizes

# Plot the graphs
frodo2.plot(output, xCol = 6, yCol = 13) # the first column has index 0
