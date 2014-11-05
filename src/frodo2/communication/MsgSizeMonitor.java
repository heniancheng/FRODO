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
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.concurrent.SynchronousQueue;

/** Measures message sizes using serialization
 * @author Thomas Leaute
 */
public class MsgSizeMonitor implements Runnable {
	
	/** Destination of the current message */
	private Object currentDest;
	
	/** The output streams to which messages are written, classified by destination */
	private HashMap<Object, ObjectOutputStream> out = new HashMap<Object, ObjectOutputStream> ();
	
	/** The streams underlying \a out, classified by destination */
	private HashMap<Object, PipedOutputStream> outRaw = new HashMap<Object, PipedOutputStream> ();
	
	/** The input streams from which bytes are read, classified by destination */
	private HashMap<Object, PipedInputStream> in = new HashMap<Object, PipedInputStream> ();
	
	/** The writer thread */
	private Thread writer;
	
	/** Used by the reader to pass a message to the writer */
	private SynchronousQueue<Message> queue = new SynchronousQueue<Message> ();
	
	/** Used by the writer to tell the reader that it is done writing the message */
	private boolean doneWriting = false;
	
	/** Used by the writer to tell the reader that it has written the additional closing byte */
	private boolean wroteLastByte = false;
	
	/** Constructor
	 * @throws IOException 	if the constructor failed to create the streams
	 */
	public MsgSizeMonitor () throws IOException {
		
		// Start the writer thread
		this.writer = new Thread (this, "MsgSizeMonitor");
		this.writer.setDaemon(true);
		this.writer.start();
	}
	
	/** Creates a new message destination
	 * @param dest 	message destination
	 * @return the new input stream
	 * @throws IOException if an I/O error occurs
	 */
	private PipedInputStream createNewDest (Object dest) throws IOException {
		
		// Create the streams
		PipedInputStream inStream = new PipedInputStream ();
		this.in.put(dest, inStream);
		PipedOutputStream outRawStream = new PipedOutputStream (inStream);
		this.outRaw.put(dest, outRawStream);
		ObjectOutputStream outStream = new ObjectOutputStream (outRawStream);
		this.out.put(dest, outStream);
		
		// Flush the serialization stream header
		outStream.flush();
		inStream.skip(4); // skip the magic number and the version 
		
		return inStream;
	}
	
	/** Closes the stream */
	public void close () {
		
		// Attempt to close all input streams, which will kill the writer if it is currently writing
		try {
			for (PipedInputStream inStream : this.in.values()) 
				inStream.close();
		} catch (IOException e) { }
		
		// Keep interrupting the writer until it's dead
		while (this.writer.getState() != Thread.State.TERMINATED) {
			this.writer.interrupt();
		}
	}

	/** @see java.lang.Runnable#run() */
	public void run() {
		
		while (true) {
			try {
				// Retrieve the message
				Message msg = this.queue.take();
				
				// Get the output streams for the current destination
				ObjectOutputStream outStream = this.out.get(this.currentDest);
				PipedOutputStream outRawStream = this.outRaw.get(this.currentDest);
				
				// Write the message
				outStream.writeObject(msg.getClass());
				msg.writeExternal(outStream);
				this.currentDest = null;
				this.doneWriting = true;
				
				// Write one last byte
				outRawStream.write(0);
				outStream.flush();
				this.wroteLastByte = true;

			} catch (InterruptedException e) {
				return;
			} catch (Throwable e) {
				e.printStackTrace();
				return;
			}
		}
	}
	
	/** Computes the size of the input message
	 * @param dest 	the message destination
	 * @param msg 	the message
	 * @return 		the number of bytes necessary to serialize this message
	 * @throws IOException 	if an error occurs
	 */
	public long getMsgSize (Object dest, Message msg) throws IOException {
		
		// Create new streams if we haven't seen this destination before
		PipedInputStream inStream = this.in.get(dest);
		if (inStream == null) 
			inStream = this.createNewDest(dest);
		
		// Give the message to the writer
		try {
			this.currentDest = dest;
			this.doneWriting = false;
			this.wroteLastByte = false;
			this.queue.put(msg);
			
		} catch (InterruptedException e) {
			return -1;
		}
		
		// Read the message byte by byte until the writer is done writing
		long size = 0;
		while (! this.doneWriting) {
			inStream.read();
			size++;
		}
		
		// Finish reading the message
		int bytes;
		while (true) {
			
			// Read as many bytes as I can
			bytes = inStream.available();
			if (bytes > 0) 
				size += inStream.skip(bytes);
			else if (this.wroteLastByte && inStream.available() <= 0)
				return size - 1;
		}
	}
	
}
