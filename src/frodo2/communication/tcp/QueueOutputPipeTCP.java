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

/** Contains classes used for communication between agents via TCP */
package frodo2.communication.tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import frodo2.communication.Message;
import frodo2.communication.MessageWithRawData;
import frodo2.communication.MessageWrapper;
import frodo2.communication.QueueOutputPipeInterface;

/** This is a queue output pipe that sends messages through TCP
 * @author Thomas Leaute
 * @todo Use ZIP streams to reduce information exchange?... 
 */
public class QueueOutputPipeTCP implements Runnable, QueueOutputPipeInterface {

	/** List into which pushed messages should be added, until they are sent */
	private LinkedList <Message> messages = new LinkedList <Message> ();
	
	/** IP address to which recipients should connect to request raw data */
	private String rawDataIP;
	
	/** Port number on which the pipe should wait for requests for raw data */
	private int rawDataPort;
	
	/** Used to tell the thread to stop */
	private boolean keepGoing = true;

	/** Output stream to which outgoing messages should be written */
	private ObjectOutputStream output;

	/** The name of this pipe, used only by QueueOutputPipeTCP#toDOT() */
	private String name;
	
	/** The ID incremented each time a message with raw data is sent */
	private Integer rawDataID = 0;
	
	/** Lock for the rawDataID field */
	private final ReentrantLock rawDataID_lock = new ReentrantLock();
	
	/** Lock for the rawDataInfos field */
	private final ReentrantLock rawDataInfos_lock = new ReentrantLock();
	
	/** Lock for the messages field */
	private final ReentrantLock messages_lock = new ReentrantLock();
	
	/** Condition used to signal that the messages list is not empty */
	private final Condition messageReceived = messages_lock.newCondition(); /// @todo Use a BlockingQueue instead?
	
	/** A convenience class used to store information about raw data */
	private static class RawDataInfo {
		
		/** A message with associated raw data */
		public MessageWithRawData msg;
		
		/** The number of recipients potentially interested in the raw data */
		public Integer counter;

		/** Constructor
		 * @param msg the message with raw data
		 * @param counter the number of recipients potentially interested in the raw data
		 */
		public RawDataInfo(MessageWithRawData msg, Integer counter) {
			this.msg = msg;
			this.counter = counter;
		}
	}
	
	/** For each raw data ID, the information about the corresponding raw data */
	private HashMap<Integer, RawDataInfo> rawDataInfos = new HashMap<Integer, RawDataInfo> ();
	
	/** A thread that listens for requests to serialize or discard raw data */
	private class RawDataSender extends Thread {
		
		/** The socket used to communicate with the potential raw data recipient */
		private Socket socket;
		
		/** Constructor 
		 * @param socket the socket used to communicate with the potential raw data recipient
		 */
		public RawDataSender(Socket socket) {
			super("RawDataSender");
			this.socket = socket;
			start();
		}

		/** @see java.lang.Thread#start() */
		@Override
		public void start () {
			this.setDaemon(true);
			super.start();
		}

		/** Waits for requests concerning raw data */
		public void run () {
			
			// Get the info about the requested raw data
			ObjectInputStream inStream;
			Integer rawDataID;
			try {
				inStream = new ObjectInputStream (socket.getInputStream());
				rawDataID = inStream.readInt();
			} catch (IOException e2) {
				System.err.println("Failed to read the raw data ID");
				e2.printStackTrace();
				return;
			}
			RawDataInfo rawDataInfo;
			try {
				rawDataInfos_lock.lock();
				assert rawDataInfos.containsKey(rawDataID) : "Received a request for an unknown raw data ID " + rawDataID;
				rawDataInfo = rawDataInfos.get(rawDataID);
			} finally {
				rawDataInfos_lock.unlock();
			}
			
			// Increment the number of potential recipients for the raw data
			synchronized (rawDataInfo) {
				rawDataInfo.counter++;
			}
			
			// Check whether we must send the raw data
			try {
				if (inStream.readBoolean()) {
					try {
						ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
						rawDataInfo.msg.serializeRawData(outStream);
						try {
							outStream.close();
						} catch (IOException e) { }
					} catch (IOException e1) {
						System.err.println("Unable to set up the stream to send raw data in the following message:\n" + rawDataInfo.msg);
						e1.printStackTrace();
					}
				}
			} catch (IOException e1) {
				System.err.println("Failed to determine whether the recipients wants the raw data of the following message:\n"
						+ rawDataInfo.msg);
				e1.printStackTrace();
			}
			
			try {
				inStream.close();
			} catch (IOException e) { }
			
			// Check whether there are no more potential recipients for the raw data
			synchronized (rawDataInfo) {
				if (--rawDataInfo.counter <= 0) { // no more potential recipients; discard the data
					synchronized (rawDataInfos) {
						rawDataInfos.remove(rawDataID);
					}
				}
			}
		}
	}
	
	/** The thread responsible for waiting for requests for raw data */
	private class RawDataServer extends Thread {

		/** Server socket used to wait for requests for raw data */
		private ServerSocket servSocket;

		/** Constructor
		 * @throws IOException thrown if an I/O errors occurs when creating the server socket
		 */
		public RawDataServer() throws IOException {
			super("RawDataServer");
			servSocket = new ServerSocket (rawDataPort);
			start();
		}
		
		/** @see java.lang.Thread#start() */
		@Override
		public void start () {
			this.setDaemon(true);
			super.start();
		}

		/** Waits for requests for raw data
		 * @see java.lang.Thread#run()
		 */
		public void run () {
			while (true) {
				// Wait for a request and spawn a new RawDataSender
				try {
					new RawDataSender (servSocket.accept());
				} catch (IOException e) { // the socket server was closed
					return;
				}
			}
		}
	}
	
	/** The thread responsible for waiting for requests for raw data */
	private RawDataServer rawDataServer;
	
	/** The QueueOutputPipeTCP's thread */
	private Thread myThread;
	
	/** The name of this QueueOutputPipeTCP's thread */
	private static String myThreadName = "QueueOutputPipeTCP";
	
	/** Initializes the output pipe
	 * @param output output stream to which outgoing messages should be written
	 * @param address IP address of the recipient (only used by QueueOutputPipeTCP#toDOT())
	 * @param port port number of the recipient (only used by QueueOutputPipeTCP#toDOT())
	 * @param rawDataIP IP address to which recipients should connect to request raw data
	 * @param rawDataPort port number on which the pipe should wait for requests for raw data
	 */
	private void init (ObjectOutputStream output, String address, int port, String rawDataIP, int rawDataPort) {
		this.output = output;
		if (address.equals("localhost")) {
			this.name = "TCPpipe_port" + port;
		} else {
			this.name = "TCTpipe_IP" + address + "_port" + port;
		}
		this.rawDataPort = rawDataPort;
		this.rawDataIP = rawDataIP;
		myThread = new Thread(this, myThreadName);
		myThread.setDaemon(true);
		myThread.start();
	}

	/** Constructor
	 * @param address IP address of the recipient
	 * @param port port number of the recipient
	 * @param rawDataIP IP address to which recipients should connect to request raw data
	 * @param rawDataPort port number on which the pipe should wait for requests for raw data
	 * @throws IOException thrown if an I/O error occurs while setting up the connection
	 * @throws UnknownHostException thrown if the provided address does not work
	 */
	QueueOutputPipeTCP(String address, int port, String rawDataIP, int rawDataPort) throws UnknownHostException, IOException {
		ObjectOutputStream out = null;
		for (int i = 0; ; i++) {
			try {
				out = new ObjectOutputStream (new Socket (address, port).getOutputStream());
			} catch (UnknownHostException e) {
				throw e;
			} catch (IOException e) {
				if (i >= 1000) {
					throw e;
				} else 
					continue;
			}
			break;
		}
		init(out, address, port, rawDataIP, rawDataPort);
	}

	/** Constructor
	 * 
	 * Sets the raw data port to \ port + 1 and the raw data IP to "localhost."
	 * @param address IP address of the recipient
	 * @param port port number of the recipient
	 * @throws IOException thrown if an I/O error occurs while setting up the connection
	 * @throws UnknownHostException thrown if the provided address does not work
	 */
	QueueOutputPipeTCP(String address, int port) throws UnknownHostException, IOException {
		ObjectOutputStream out = null;
		for (int i = 0; ; i++) {
			try {
				out = new ObjectOutputStream (new Socket (address, port).getOutputStream());
			} catch (UnknownHostException e) {
				throw e;
			} catch (IOException e) {
				if (i >= 1000) {
					throw e;
				} else 
					continue;
			}
			break;
		}
		init(out, address, port, "localhost", port + 1);
	}

	/** @see frodo2.communication.QueueOutputPipeInterface#pushMessage(frodo2.communication.MessageWrapper) */
	public void pushMessage(MessageWrapper msgWrap) {
		try {
			messages_lock.lock();
			messages.add(msgWrap.getMessage());
			messageReceived.signal();
		} finally {
			messages_lock.unlock();
		}
	}
	
	/** Close all pipes, and tells the thread to stop */
	public void close () {
		keepGoing = false;
		myThread.interrupt();
		if (rawDataServer != null) {
			try {
				rawDataServer.servSocket.close();
				try {
					rawDataServer.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/** Continuously checks if there are messages to be sent and sends them */
	public void run () {
		while (keepGoing) {

			Message msg = null;
			try {
				messages_lock.lock();
				if (messages.isEmpty()) { // wait for notification of new message
					
					// Flush the stream before going to sleep
					try {
						output.flush();
					} catch (IOException e1) {
						e1.printStackTrace();
						return;
					}
					
					// Go to sleep
					try {
						messageReceived.await();
					} catch (InterruptedException e) { // the method close() has been called
						return;
					}
					continue;
				} else 
					msg = messages.removeFirst();
			} finally {
				if(messages_lock.isHeldByCurrentThread())
					messages_lock.unlock();
			}
			
			// First check whether this message is of type MessageWithRawData
			if (msg instanceof MessageWithRawData) {
				MessageWithRawData msgCast = (MessageWithRawData) msg;

				// Check whether the raw data still remains to be serialized
				if (msgCast.getHandler() == null) {

					// Create and start the RawDataServer if it is not already running
					if (rawDataServer == null) {
						try {
							rawDataServer = new RawDataServer ();
						} catch (IOException e) {
							System.err.println("Unable to create RawDataServer");
							e.printStackTrace();
							continue;
						}
					}

					// Record the info about these new raw data
					try {
						rawDataID_lock.lock();
						rawDataInfos_lock.lock();
						rawDataInfos.put(++rawDataID, new RawDataInfo (msgCast, 0));
						
						// Set up the raw data handler for this message
						msgCast.setHandler(new RawDataHandlerTCP (rawDataID, rawDataIP, rawDataPort));
					} finally {
						rawDataID_lock.unlock();
						if(rawDataInfos_lock.isHeldByCurrentThread())
							rawDataInfos_lock.unlock();
					}
				}

				/// @todo There is a privacy issue when the raw data is already serialized: 
				/// the recipients learn the IP address of the sender of the raw data
			}

			// Now, send the message
			try {
				output.writeObject(msg.getClass());
				msg.writeExternal(output);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			/// @todo Reset the streams regularly to get rid of references to previously sent objects that prevent garbage collection?
		}

		// Close the stream
		try {
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** @see frodo2.communication.QueueOutputPipeInterface#toDOT() */
	public String toDOT() {
		return name;
	}

}
