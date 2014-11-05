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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** A message that has a single object as raw data
 * 
 * This class is provided for convenience, and as an example of how to extend the Message class. 
 * @author Thomas Leaute
 * @param <T> the class of the object passed as raw data
 */
public class MessageSerializedSimple <T extends Serializable> extends MessageWithRawData {
	
	/** The data that is going to be (de)serialized */
	private transient T data = null;

	/** Object used to synchronize the access to \a data
	 * 
	 * We cannot synchronize directly over \a data because it can be \c null. 
	 */
	private transient Object data_lock = new Object ();
	
	/** Empty constructor used for externalization */
	public MessageSerializedSimple () {
		data_lock = new Object ();
	}
	
	/** Constructor
	 * @param type type of this message
	 * @param data the raw data
	 */
	public MessageSerializedSimple (String type, T data) {
		super(type);
		this.data = data;
	}
	
	/** @param data the new value of the data */
	private void setData(T data) {
		synchronized (data_lock) {
			this.data = data;
		}
	}
	
	/** Writes this message's raw data to the provided output stream
	 * @see MessageWithRawData#serializeRawData(ObjectOutputStream)
	 */
	public synchronized void serializeRawData (ObjectOutputStream output) {
		try {
			synchronized (data_lock) {
				output.writeObject(data);
			}
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Reads an object of class T from the stream of raw data and stores it in \a data
	 * @see MessageWithRawData#deserializeRawData()
	 */
	@SuppressWarnings("unchecked")
	public synchronized void deserializeRawData () {
		synchronized (data_lock) {
			if (data == null) {

				// First request the raw data from the sender
				ObjectInputStream input = getRawData();

				try {
					setData((T) input.readObject());
					input.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @return the data
	 */
	public T getData() {
		synchronized (data_lock) {
			return data;
		}
	}

}
