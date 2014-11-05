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

package frodo2.algorithms.varOrdering.linear;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import frodo2.communication.Message;
import frodo2.solutionSpaces.Addable;
import frodo2.solutionSpaces.UtilitySolutionSpace;

/** A message containing a linear order on all variables belonging to a given connected component of the constraint graph 
 * @param <V> the type used for variable values
 * @param <U> the type used for utility values
 */
public class OrderMsg < V extends Addable<V>, U extends Addable<U> > extends Message {
	
	/** The types of the messages containing the chosen linear order of variables */
	public static final String ORDER_MSG_TYPE = "VarOrder";
	
	/** The types of the messages containing the chosen linear order of variables sent to the stats gatherer */
	public static final String STATS_MSG_TYPE = "VarOrderStats";
	
	/** The ID of the connected component in the constraint graph */
	private Comparable<?> componentID;
	
	/** The order of the variables, grouped into clusters */
	private List<List<String>> order;
	
	/** The ID of each cluster */
	private List<String> ids;
	
	/** The agent corresponding to each cluster in the order */
	private List<String> agents;
	
	/** The (joint) constraint that each cluster is responsible for enforcing */
	private List< UtilitySolutionSpace<V, U> > spaces;
	
	/** Empty constructor used for externalization */
	public OrderMsg () {
		super ();
	}
	
	
	/** Constructor
	 * @param type 			the type of the message
	 * @param componentID 	The ID of the connected component in the constraint graph
	 * @param order 		The variable order
	 * @param agents 		The agent corresponding to each variable in the order
	 */
	public OrderMsg (String type, Comparable<?> componentID, List<String> order, List<String> agents) {
		super (type);
		this.componentID = componentID;
		this.order = new ArrayList<List<String>>();
		this.agents = new ArrayList<String>(agents);
		this.ids = order;
		ArrayList<String> list;
		for(int i = 0; i < order.size(); i++){
			list = new ArrayList<String>();
			list.add(order.get(i));
			this.order.add(list);
		}
	}
	
	/** Constructor
	 * @param type 			the type of the message
	 * @param componentID 	The ID of the connected component in the constraint graph
	 * @param order 		The cluster order
	 * @param agents 		The agent corresponding to each variable in the order
	 */
	public OrderMsg (String type, List<List<String>> order, List<String> agents, Comparable<?> componentID) {
		this(type, order, agents, componentID, null);
	}

	/** Constructor
	 * @param type 			the type of the message
	 * @param componentID 	The ID of the connected component in the constraint graph
	 * @param order 		The cluster order
	 * @param agents 		The agent corresponding to each variable in the order
	 * @param spaces 		The space each cluster is responsible for enforcing
	 */
	public OrderMsg (String type, List<List<String>> order, List<String> agents, Comparable<?> componentID, List< UtilitySolutionSpace<V, U> > spaces) {
		this(type, order, agents, agents, componentID, spaces);
	}

	/** Constructor
	 * @param type 			the type of the message
	 * @param componentID 	The ID of the connected component in the constraint graph
	 * @param order 		The cluster order
	 * @param ids 			Each cluster's ID
	 * @param agents 		The agent corresponding to each variable in the order
	 * @param spaces 		The space each cluster is responsible for enforcing
	 */
	public OrderMsg (String type, List<List<String>> order, List<String> ids, List<String> agents, Comparable<?> componentID, List< UtilitySolutionSpace<V, U> > spaces) {
		super (type);
		this.componentID = componentID;
		this.order = new ArrayList<List<String>>();
		this.agents = new ArrayList<String>(agents);
		this.ids = ids;
		ArrayList<String> list;
		for(int i = 0; i < order.size(); i++){
			list = new ArrayList<String>();
			list.addAll(order.get(i));
			this.order.add(list);
		}
		this.spaces = spaces;
	}

	/** 
	 * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) 
	 * @warning The spaces are NOT serialized. 
	 */
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(super.type);
		out.writeObject(this.componentID);
		
		// Serialize the order manually
		assert this.order.size() < Short.MAX_VALUE;
		out.writeShort(this.order.size());
		for (int i = 0; i < this.order.size(); i++){
			
			// Serialize the ID
			out.writeObject(this.ids.get(i));
			
			// Serialize the agents manually
			out.writeObject(agents.get(i));
			
			List<String> cluster = this.order.get(i);
			out.writeShort(cluster.size());
			for(int j = 0; j < cluster.size(); j++){
				
				// Serialize the variables manually
				out.writeObject(cluster.get(j));
				
			}
		}
	}
	
	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.type = (String) in.readObject();
		this.componentID = (Comparable<?>) in.readObject();
		
		// Read the order
		short nbrClusters = in.readShort();
		this.order = new ArrayList<List<String>> (nbrClusters);
		this.agents = new ArrayList<String> (nbrClusters);
		this.ids = new ArrayList<String> (nbrClusters);
		for(short i = 0; i < nbrClusters; i++){
			
			// Read the IDs
			this.ids.add((String) in.readObject());
			
			// Read the agents
			this.agents.add((String) in.readObject());
			
			short nbrVars = in.readShort();
			ArrayList<String> cluster = new ArrayList<String> (nbrVars);
			for (short j = 0; j < nbrVars; j++){
				
				// Read the variables
				cluster.add((String) in.readObject());
			
			}
			this.order.add(cluster);
		}
	}
	
	/** @return The ID of the connected component in the constraint graph */
	public Comparable<?> getComponentID () {
		return this.componentID;
	}
	
	/** @return The variable order */
	public List<List<String>> getOrder () {
		return this.order;
	}
	
	/** @return The variable order in a flattened list */
	public List<String> getFlatOrder () {
		ArrayList<String> flatOrder = new ArrayList<String>();
		for(List<String> cluster: order)
			flatOrder.addAll(cluster);
		return flatOrder;
	}
	
	/** @return each cluster's ID */
	public List<String> getIDs () {
		return this.ids;
	}
	
	/** @return The agent corresponding to each variable in the order */
	public List<String> getAgents () {
		return this.agents;
	}

	/** @return the (joint) constraint that each cluster is responsible for enforcing */
	public List< UtilitySolutionSpace<V, U> > getSpaces() {
		return spaces;
	}

	/** @see Message#toString() */
	@Override
	public String toString () {
		return super.toString() + "\n\tcomponentID = " + this.componentID + "\n\torder = " + this.order + "\n\tagents = " + this.agents;
	}
}