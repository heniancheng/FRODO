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
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import frodo2.communication.Message;
import frodo2.communication.MessageWrapper;
import frodo2.communication.Queue;
import frodo2.communication.QueueInputPipeInterface;

/** This is a queue input pipe that receives messages through TCP
 * @author Thomas Leaute
 */
public class QueueInputPipeTCP extends Thread implements QueueInputPipeInterface {
	
	/** The server socket used to listen to connection requests from message senders */
	private ServerSocket servSocket = null;
	
	/** Currently active sockets from which messages are being read */
	private ArrayList<Socket> sockets;
	
	/** Whenever a request for connection is received, one such a thread is spawn to establish the connection */
	private static class Receiver extends Thread {
		
		/** The QueueInputPipeTCP that spawned this thread */
		private QueueInputPipeTCP server;
		
		/** The socket from which messages should be read */
		private Socket socket;
		
		/** Constructor 
		 * @param socket the socket used to exchange messages
		 * @param server the QueueInputPipeTCP that spawned this thread
		 */
		public Receiver (Socket socket, QueueInputPipeTCP server) {
			super ("QueueInputPipeTCPReceiver");
			this.socket = socket;
			this.server = server;
			synchronized (server.sockets) {
				server.sockets.add(socket);
			}
			start();
		}
		
		/** @see java.lang.Thread#start() */
		@Override
		public void start () {
			this.setDaemon(true);
			super.start();
		}

		/** Receives messages and passes them to the queue */
		public void run () {
			
			ObjectInputStream input;
			try {
				input = new ObjectInputStream (socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
				try {
					socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				synchronized (server.sockets) {
					server.sockets.remove(socket);
				}
				return;
			}
			
			while (true) {

				// Read next Message object from the socket and pass it to the queue
				try {
					@SuppressWarnings("unchecked")
					Class<? extends Message> msgClass = (Class<? extends Message>) input.readObject();
					Message msg = msgClass.newInstance();
					msg.readExternal(input);
					server.queue.addToInbox(new MessageWrapper(msg));
					
				} catch (IOException e) { // End Of File
					try {
						socket.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					synchronized (server.sockets) {
						server.sockets.remove(socket);
					}
					return;
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					continue;
				} catch (InstantiationException e) {
					e.printStackTrace();
					continue;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					continue;
				}
			}
		}
		
	}
	
	/** The number of connection requests currently still allowed */
	private Integer nbrConnections;

	/** This pipe's queue */
	private Queue queue;

	/** Constructor. Notifies the queue. 
	 * @param queue the queue messages should be passed to
	 * @param port port number used to listen for incoming messages
	 * @param maxNbrConnections maximum number of connection requests allowed. If \c null, there is no limit.
	 * @throws IOException thrown if an I/O error occurred
	 */
	public QueueInputPipeTCP (Queue queue, int port, Integer maxNbrConnections) throws IOException  {
		super ("QueueInputPipeTCP");
		servSocket = new ServerSocket (port);
		if (maxNbrConnections != null) {
			nbrConnections = new Integer(maxNbrConnections);
			sockets = new ArrayList<Socket> (nbrConnections);
		} else
			sockets = new ArrayList<Socket> ();
		this.queue = queue;
		queue.toBeClosedUponKill(this);
		start();
	}

	/** Constructor 
	 * 
	 * Notifies the queue. Listens to an unlimited number of connection requests. 
	 * @param queue the queue messages should be passed to
	 * @param port port number used to listen for incoming messages
	 * @throws IOException thrown if an I/O error occurred
	 */
	QueueInputPipeTCP (Queue queue, int port) throws IOException  {
		this(queue, port, null);
	}

	/** @see java.lang.Thread#start() */
	@Override
	public void start () {
		this.setDaemon(true);
		super.start();
	}

	/** Waits for requests for connection from senders, and creates Receivers */
	public void run () {
		// Keep waiting for connection requests, and handle each by spawning a new Receiver thread
		while (true) {
			try {
				// First check whether we have reached the maximum number of connections allowed
				if (nbrConnections != null && --nbrConnections < 0) { // no more connections allowed; quit the thread
					servSocket.close();
					return;
				}
				
				new Receiver(servSocket.accept(), this);

			} catch (SocketException e) {
				// The socket has been closed; terminate the thread 
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/** @see frodo2.communication.QueueInputPipeInterface#close() */
	public void close() {
		try {
			servSocket.close();
			synchronized (sockets) {
				for (Socket socket : sockets) {
					socket.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** @see frodo2.communication.QueueInputPipeInterface#toDOT() */
	public String toDOT() {
		return "TCPpipe_port" + servSocket.getLocalPort();
	}

}
