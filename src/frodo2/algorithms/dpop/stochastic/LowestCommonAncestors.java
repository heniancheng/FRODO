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

package frodo2.algorithms.dpop.stochastic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import frodo2.algorithms.varOrdering.dfs.DFSgeneration;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.DFSview;
import frodo2.algorithms.varOrdering.dfs.DFSgeneration.MessageDFSoutput;
import frodo2.communication.IncomingMsgPolicyInterface;
import frodo2.communication.Message;
import frodo2.communication.Queue;

/** An algorithm to compute multiple Lowest Common Ancestors
 * 
 * Nodes in the DFS can have flags of various types. The algorithm computes, for each flag type, the lca of all nodes with that flag. 
 * 
 * @author Thomas Leaute
 */
public class LowestCommonAncestors implements IncomingMsgPolicyInterface<String> {
	
	/** The type of the message containing information about the DFS */
	public static String DFS_MSG_TYPE = DFSgeneration.OUTPUT_MSG_TYPE;
	
	/** The type of a phase 1 message */
	public static final String PHASE1_MSG_TYPE = "LCAmsg1";
	
	/** The type of a phase 2 message */
	public static final String PHASE2_MSG_TYPE = "LCAmsg2";
	
	/** The type of the output messages */
	public static final String OUTPUT_MSG_TYPE = "LCAoutputMsg";
	
	/** The queue */
	protected Queue queue;
	
	/** Information about a node in the DFS */
	protected class NodeInfo {
		
		/** The set of flags for this node */
		public HashSet<String> myFlags = new HashSet<String> ();
		
		/** The set of all flags for which this node might be the lca */
		public HashSet<String> lcas = new HashSet<String> ();
		
		/** The parent node */
		public String parent;
		
		/** For each child node, the set of flags received from that node during phase 1 */
		public HashMap< String, HashSet<String> > childFlags;
		
		/** The set of all flags received from all children */
		public HashSet<String> allFlags;
		
		/** A counter for the number of messages to be received from children */
		public int nbrMsgsRemaining = 0;
		
		/** Used to store phase 1 messages received for this node until we receive the DFSoutput message for this node */
		public ArrayList<LCAmsg1> phase1msgs = new ArrayList<LCAmsg1> ();
		
		/** Constructor
		 * @param flags 	a set of flags
		 */
		public NodeInfo (Collection<String> flags) {
			this.myFlags.addAll(flags); 
			this.lcas.addAll(flags);
		}
		
		/** Adds flags to myFlags and lcas
		 * @param flags 	the flags
		 */
		public void addFlags (Collection<String> flags) {
			this.myFlags.addAll(flags); 
			this.lcas.addAll(flags);
		}
	}
	
	/** The known information about each node */
	protected HashMap<String, NodeInfo> infos;
	
	/** For each known node, the name of the agent that owns it */
	protected Map<String, String> owners = new HashMap<String, String> ();
	
	/** Constructor 
	 * @param flags 	for each node, a set of flags
	 * @param owners 	for each node, the ID of the owner agent
	 */
	public LowestCommonAncestors (Map< String, Set<String> > flags, Map<String, String> owners) {
		this.infos = new HashMap<String, NodeInfo> ();
		for (Map.Entry< String, Set<String> > entry : flags.entrySet()) 
			this.infos.put(entry.getKey(), new NodeInfo (entry.getValue()));
		this.owners = owners;
	}
	
	/** Empty constructor with no specified flags */
	protected LowestCommonAncestors () {
		this.infos = new HashMap<String, NodeInfo> ();
	}
		
	/** Adds flags
	 * @param flags 	for each node, a set of flags
	 */
	protected void setFlags (Map< String, HashSet<String> > flags) {
		for (Map.Entry< String, HashSet<String> > entry : flags.entrySet()) 
			this.infos.put(entry.getKey(), new NodeInfo (entry.getValue()));
	}
	
	/** @see IncomingMsgPolicyInterface#getMsgTypes() */
	public Collection<String> getMsgTypes() {
		ArrayList<String> types = new ArrayList<String> (3);
		types.add(DFS_MSG_TYPE);
		types.add(PHASE1_MSG_TYPE);
		types.add(PHASE2_MSG_TYPE);
		return types;
	}

	/** @see IncomingMsgPolicyInterface#notifyIn(Message) */
	public void notifyIn(Message msg) {
		
		String type = msg.getType();
		
		if (type.equals(DFS_MSG_TYPE)) { // DFS information
			
			MessageDFSoutput<?, ?> msgCast = (MessageDFSoutput<?, ?>) msg;
			String node = msgCast.getVar();
			DFSview<?, ?> neighbors = msgCast.getNeighbors();
			NodeInfo nodeInfo = this.infos.get(node);
			
			if (nodeInfo.childFlags != null) // this is the second time we receive this DFS message; discard it
				return;
			
			nodeInfo.childFlags = new HashMap< String, HashSet<String> > ();
			nodeInfo.allFlags = new HashSet<String> ();
			
			// Extract from the message the information about the corresponding node's parent
			nodeInfo.parent = neighbors.getParent();
			
			// Extract from the number of this node's children
			nodeInfo.nbrMsgsRemaining = neighbors.getChildren().size();
			
			// If this node is a leaf, send a message to its parent (if any) with all its flags 
			if (nodeInfo.nbrMsgsRemaining <= 0) {
				
				if (nodeInfo.parent != null) {
					nodeInfo.allFlags.addAll(nodeInfo.myFlags);
					queue.sendMessage(owners.get(nodeInfo.parent), this.newPhase1msg(node, nodeInfo));
				}
					
				if (nodeInfo.parent == null || nodeInfo.myFlags.isEmpty()) // terminate if this node is also a root, or if it has no flags
					this.sendOutput(node, nodeInfo);

			} else { // this node has children; process possible phase 1 messages already received from some of these children
				
				for (LCAmsg1 lcaMsg : nodeInfo.phase1msgs) 
					this.notifyIn(lcaMsg);
				nodeInfo.phase1msgs = null;
			}
			
		}
		
		else if (type.equals(PHASE1_MSG_TYPE)) { // phase 1 message received from a child
			
			LCAmsg1 msgCast = (LCAmsg1) msg;
			String node = msgCast.getDest();
			NodeInfo nodeInfo = this.infos.get(node);
			
			// Check if we have received the DFSoutput message for that node yet
			if (nodeInfo.allFlags == null) { // we must wait for the DFSoutput message
				nodeInfo.phase1msgs.add(msgCast);
				return;
			}
			
			String child = msgCast.getSender();
			HashSet<String> flags = msgCast.getFlags();
						
			// Update the set of potential lcas for that node
			for (String flag : flags) 
				if (nodeInfo.allFlags.contains(flag)) // this flag has already been received from another child
					nodeInfo.lcas.add(flag);
			
			// Store the flags received
			if (! flags.isEmpty()) 
				nodeInfo.childFlags.put(child, flags);
			nodeInfo.allFlags.addAll(flags);
			
			// Check if this node has received messages from all its children
			if (--nodeInfo.nbrMsgsRemaining <= 0) {
				
				// Check if this node has a parent
				if (nodeInfo.parent != null) {
					
					// Add my own flags to the set of all flags
					nodeInfo.allFlags.addAll(nodeInfo.myFlags);
					
					// Send all flags to the parent
					queue.sendMessage(owners.get(nodeInfo.parent), this.newPhase1msg(node, nodeInfo));
					
					// If this node knows no flags, we can already terminate
					if (nodeInfo.allFlags.isEmpty()) 
						this.sendOutput(node, nodeInfo);

				} else { // this node is a root; move to phase 2
					
					// Add my own flags to the list of my lcas
					nodeInfo.lcas.addAll(nodeInfo.myFlags);
					
					// Terminate phase 2
					terminatePhase2 (node, nodeInfo, nodeInfo.allFlags);
				}
			}
		}
		
		else if (type.equals(PHASE2_MSG_TYPE)) { // phase 2 message received from parent
			
			LCAmsg2 msgCast = (LCAmsg2) msg;
			String node = msgCast.getNode();
			NodeInfo nodeInfo = this.infos.get(node);
			Set<String> flags = msgCast.getFlags();
			
			// Add my own flags to the list of my lcas
			nodeInfo.lcas.addAll(nodeInfo.myFlags);
			
			// Remove from the list of my lcas the flags that are not in the set received from my parent
			for (Iterator<String> iter = nodeInfo.lcas.iterator(); iter.hasNext(); ) 
				if (! flags.contains(iter.next())) 
					iter.remove();
			
			// Terminate phase 2
			terminatePhase2 (node, nodeInfo, flags);
		}
		
	}
	
	/** Creates a new Phase 1 message
	 * @param node 		sender variable
	 * @param nodeInfo 	information concerning the sender variable
	 * @return a new Phase1 message
	 */
	protected LCAmsg1 newPhase1msg (String node, NodeInfo nodeInfo) {
		return new LCAmsg1 (node, nodeInfo.parent, nodeInfo.allFlags);
	}

	/** Sends messages to children and the output message
	 * @param node 			the current node
	 * @param nodeInfo 		information about the current node
	 * @param pendingFlags 	a set of flags whose lca has not yet been computed
	 */
	protected void terminatePhase2(String node, NodeInfo nodeInfo, Set<String> pendingFlags) {

		// Go through the list of children and the flags received from them
		for (Map.Entry< String, HashSet<String> > entry : nodeInfo.childFlags.entrySet()) {
			String child = entry.getKey();
			HashSet<String> flags = entry.getValue();
			
			// Only keep flags that are still pending
			for (Iterator<String> iter = flags.iterator(); iter.hasNext(); ) 
				if (! pendingFlags.contains(iter.next())) 
					iter.remove();			
			
			// Remove from the set of flags the ones for which I am the lca
			flags.removeAll(nodeInfo.lcas);
			
			// Send the set of flags to the child
			queue.sendMessage(owners.get(child), new LCAmsg2 (PHASE2_MSG_TYPE, child, flags));
		}
		
		// Send the output message
		this.sendOutput(node, nodeInfo);
	}
	
	/** Sends the output message
	 * @param node 		the corresponding node in the DFS
	 * @param nodeInfo 	the info about the node
	 */
	protected void sendOutput (String node, NodeInfo nodeInfo) {
		queue.sendMessageToSelf(new LCAmsg2 (OUTPUT_MSG_TYPE, node, nodeInfo.lcas));
	}

	/** @see IncomingMsgPolicyInterface#setQueue(Queue) */
	public void setQueue(Queue queue) {
		this.queue = queue;
	}

}
