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

package frodo2.algorithms.dpop.privacy;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import frodo2.communication.Message;

/** Message token used to tell to the root that the dfs can be rerooted */
public class DoneMsg extends Message implements Externalizable {

	/**
	 * Constructor
	 */
	public DoneMsg(){
		this.type = SecureRerooting.DONE;
	}

	/** @see java.io.Externalizable#readExternal(java.io.ObjectInput) */
	public void readExternal(ObjectInput arg0) throws IOException, ClassNotFoundException { }

	/** @see java.io.Externalizable#writeExternal(java.io.ObjectOutput) */
	public void writeExternal(ObjectOutput arg0) throws IOException {
	}
}
