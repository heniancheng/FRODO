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

package frodo2.communication;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** A message containing data that must be custom-serialized
 * @author Thomas Leaute
 */
public abstract class MessageWithRawData extends Message {
	
	/** The object used to handle the raw data */
	protected RawDataHandler handler;
	
	/** Object used to synchronize access to the raw data handler
	 * 
	 * We cannot directly synchronize over \a handler because it can be \c null. 
	 */
	private transient Object rawDataHandler_lock = new Object ();
	
	/** This class is responsible for handling the raw data */
	public static interface RawDataHandler extends Serializable {

		/** @return the stream from which the raw data can be read */
		public ObjectInputStream requestRawData ();
		
		/** Discards the raw data */
		public void discardRawData ();
	}
	
	/** Empty constructor used for externalization */
	public MessageWithRawData () {
		this.rawDataHandler_lock = new Object ();
	}

	/** @see Message#writeExternal(java.io.ObjectOutput) */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(this.handler);
	}

	/** @see Message#readExternal(java.io.ObjectInput) */
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		this.handler = (RawDataHandler) in.readObject();
	}

	/** Constructor
	 * @param type the type of the message
	 */
	public MessageWithRawData(String type) {
		super(type);
	}

	/** @return the raw data handler */
	public RawDataHandler getHandler() {
		synchronized (rawDataHandler_lock) {
			return handler;
		}
	}

	/** Sets the raw data handler for this message
	 * @param handler the handler to set
	 */
	public void setHandler(RawDataHandler handler) {
		synchronized (rawDataHandler_lock) {
			this.handler = handler;
		}
	}

	/** Serializes this message's raw data into the given stream
	 * @param stream output stream to which the raw data should be written
	 */
	public abstract void serializeRawData (ObjectOutputStream stream);
	
	/** @return the stream from which the raw data can be read */
	public ObjectInputStream getRawData () {
		return handler.requestRawData();
	}
	
	/** Deserializes the raw data by reading it from the output of MessageWithRawData#getRawData() */
	public abstract void deserializeRawData ();
	
	/** Discards the raw data
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize () throws Throwable {
		handler.discardRawData();
		super.finalize();
	}

}
