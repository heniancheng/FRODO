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

package frodo2.communication.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import frodo2.communication.MessageWithRawData.RawDataHandler;

/** A raw data handler that works with TCP
 * @author Thomas Leaute
 */
public class RawDataHandlerTCP implements RawDataHandler {

	/** Used for serialization */
	private static final long serialVersionUID = 9055544029313537503L;

	/** ID of the raw data */
	private Integer rawDataID = null;
	
	/** IP from which the raw data can be retrieved */
	private String rawDataIP;
	
	/** Port number from which the raw data can be retrieved */
	private int rawDataPort;
	
	/** Socket used to communicate with the sender of the raw data  */
	private transient Socket socket;
	
	/** Output stream used to send requests to the sender of the message */
	private transient ObjectOutputStream toSender;
	
	/** Constructor
	 * @param rawDataID ID of the raw data
	 * @param rawDataIP IP from which the raw data can be retrieved
	 * @param rawDataPort Port number from which the raw data can be retrieved
	 */
	public RawDataHandlerTCP (Integer rawDataID, String rawDataIP, int rawDataPort) {
		this.rawDataID = rawDataID;
		this.rawDataIP = rawDataIP;
		this.rawDataPort = rawDataPort;
	}

	/** Gets the raw data
	 * 
	 * The first time this method is called, it sends a request for the raw data and returns a stream from which it can be read.
	 * It returns \c null if any exception occurs in the process, or this is not the first time the method is called.  
	 * @see frodo2.communication.MessageWithRawData.RawDataHandler#requestRawData()
	 */
	public synchronized ObjectInputStream requestRawData() {
		
		if (rawDataID != null) { // first time this method is called
			try {
				// Get the raw data stream
				InputStream stream = socket.getInputStream();

				// Tell the recipient to send the raw data
				toSender.writeBoolean(true);
				toSender.flush();

				// Reset rawDataID to indicate that there are no more pending raw data
				rawDataID = null;

				return new ObjectInputStream (stream);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		// the raw data stream has already been requested or an exception occurred
		return null;
	}

	/** Tells the sender to discard the raw data
	 * @see frodo2.communication.MessageWithRawData.RawDataHandler#discardRawData()
	 */
	public synchronized void discardRawData() {
		
		if (rawDataID != null) { // the raw data has not already been requested or discarded
			try {
				// Reset rawDataID to indicate that there are no more pending raw data
				rawDataID = null;
				
				// Tell the sender I'm not interested in the raw data any more
				if (toSender != null) {
					toSender.writeBoolean(false);
					toSender.flush();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/** This is the method called when deserializing a RawDataHandlerTCP
	 * 
	 * Establishes the connection with the sender of the raw data.  
	 * @param in the stream from which the message is read
	 * @throws ClassNotFoundException thrown if the class read from the stream is unknown
	 * @throws IOException thrown if an I/O error occurs
	 */
	private void readObject (java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		// Establish the connection with the sender of the raw data
		socket = new Socket (rawDataIP, rawDataPort);
		toSender = new ObjectOutputStream (socket.getOutputStream());
		
		// Send to the sender the ID of the raw data we are interested in
		toSender.writeInt(rawDataID);
		toSender.flush();
	}

	/** Closes the socket
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize () throws Throwable {
		socket.close();
		super.finalize();
	}
}
