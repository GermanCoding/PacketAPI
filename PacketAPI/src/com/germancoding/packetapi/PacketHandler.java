package com.germancoding.packetapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.germancoding.packetapi.Process.ActionType;
import com.germancoding.packetapi.defaultpackets.ClosePacket;
import com.germancoding.packetapi.defaultpackets.DefaultPacket;
import com.germancoding.packetapi.defaultpackets.HandshakePacket;

public class PacketHandler {

	/** Applications can change this value if they want **/
	public static int PROTOCOL_VERSION = 1;

	/** Handshake ID used in the sendHandshake() method. The other side will respond to that packet. **/
	public static int HANDSHAKE_ID_REQUEST = 0;

	/** Handshake ID used when replying to a handshake packet. The other side will not respond to that packet. **/
	public static int HANDSHAKE_ID_RESPONSE = 1;

	private HashMap<Short, Class<? extends Packet>> packetMap = new HashMap<Short, Class<? extends Packet>>();

	private String connectionName;
	public Logger logger = Logger.getLogger("PacketHandler");

	public InputStream in; // Public for direct access (Yes, that's not encapsulated...)
	public OutputStream out;
	private DataSender sender;
	private DataReader reader;
	private PacketListener listener;
	private DefaultPacketListener defaultPacketListener = new DefaultPacketListener(this);

	// State attributes
	private boolean closed;
	private boolean versionApproved;
	private boolean handshakeSend;
	private boolean closeListenerNotified;
	private int remoteProtocolVersion = -1;

	private static boolean hasExternalThread;
	private static int numberOfHandlers;
	private static LinkedList<Process> processingQueue = new LinkedList<Process>();

	/**
	 * Creates a new PacketHandler instance. The instance will use the given I/O streams to send and receive data.
	 * 
	 * @param in
	 *            The InputStream to read data from.
	 * @param out
	 *            The OutputStream to send data to.
	 * @param connectionName
	 *            Optional: Give the connection a name to identify it. Can be <code>null</code>.
	 * @param listener
	 *            A listener which is notified when something happens (A packet arrived, the connection failed...). Can be <code>null</code> if the application does not want to listen to
	 *            incoming data.
	 */
	public PacketHandler(InputStream in, OutputStream out, String connectionName, PacketListener listener) {
		if (in == null || out == null) {
			throw new IllegalArgumentException("InputStream or OutputStream can not be null");
		}
		if (connectionName == null)
			connectionName = "";
		this.in = in;
		this.out = out;
		this.setConnectionName(connectionName);
		if (listener != null) // Could be null, if the application doesn't want to listen to incoming packets
		{
			this.setListener(listener);
		} else {
			this.setListener(new PacketListener() { // Setup an empty listener for the application

				@Override
				public void onPacketReceived(PacketHandler handler, Packet packet) {
					; // Do nothing
				}

				@Override
				public void onConnectionFailed(PacketHandler handler) {
					;
				}

				@Override
				public void onUnknownPacketReceived(PacketHandler handler, short id) {
					;
				}

				@Override
				public void onConnectionClosed(PacketHandler handler, String message, boolean expected) {
					;
				}
			});
		}
		registerPacketDefaults();
		sender = new DataSender(this);
		reader = new DataReader(this);
		numberOfHandlers++;
	}

	private void registerPacketDefaults() {
		try {
			registerPacket(HandshakePacket.class);
			registerPacket(ClosePacket.class);
		} catch (Exception e) {
			logger.severe("Failed to register default packets! " + e);
		}
	}

	/**
	 * Registers a new packet. After registering the packet type can be send and received.
	 * 
	 * @param packetClass
	 *            The class of the new packet.
	 * @throws Exception
	 *             If reflection fails, like when there is no nullary constructor.
	 */
	public void registerPacket(Class<? extends Packet> packetClass) throws Exception {
		if (packetClass == null)
			throw new IllegalArgumentException("packetClass can not be null");
		// If this call fails (e.g when there is no nullary constructor), an exception will be thrown.
		int id = packetClass.newInstance().getId();
		packetMap.put((short) id, packetClass);
	}

	public Packet getNewPacketInstance(short id) {
		Class<? extends Packet> packetClass = packetMap.get(id);
		if (packetClass == null) {
			logger.warning("Packet with id=" + id + " not found");
			return null;
		}
		try {
			return packetClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			logger.severe("Failed to instantiate a new packet class instance. " + e);
		}
		return null;
	}

	/**
	 * Sends the given packet by adding it to the sending queue.
	 * 
	 * @param p
	 *            The packet to send.
	 */
	public void sendPacket(Packet p) {
		getSender().sendPacket(p);
	}

	/**
	 * Notifies this instance that the connection has failed. This method notifies the listeners and closes the connection
	 * 
	 * @param e
	 *            A exception describing why the connection has failed.
	 */
	public void onConnectionFail(Exception e) {
		if (closed) // Abort if the connection was already closed (A closed connection can not fail)
			return;
		logger.severe("Connection '" + getConnectionName() + "' failed! " + e);
		getDefaultPacketListener().onConnectionFailed(this);
		getListener().onConnectionFailed(this);
		close();
	}

	/**
	 * Notifies this instance that the connection is closed.
	 * 
	 * @param message
	 *            Message why the connection was closed.
	 * @param expected
	 *            Whether this was expected (like there was a close packet) or not (like when the underlying socket is closed without notification)
	 */
	public void onConnectionClosed(String message, boolean expected) {
		getDefaultPacketListener().onConnectionClosed(this, message, expected);
		getListener().onConnectionClosed(this, message, expected);
		closeListenerNotified = true;
		close();
	}

	/**
	 * Closes the connection, the I/O streams and notfies the other side that we are closing this connection.
	 */
	public void close() {
		if (closed)
			return;
		if (!closeListenerNotified) {
			// Someone is calling close() directly so we assume that the connection was closed expectly
			getDefaultPacketListener().onConnectionClosed(this, "Connection closed locally", true);
			getListener().onConnectionClosed(this, "Connection closed locally", true);
		}
		closed = true;
		ClosePacket close = new ClosePacket();
		sendPacket(close);
		long timeout = System.currentTimeMillis() + 5000;
		while (!sender.queueEmpty()) {
			synchronized (this) {
				try {
					this.wait(1);
					if (System.currentTimeMillis() >= timeout)
						break;
				} catch (InterruptedException e) {
					break;
				}
			}
		}
		sender.interrupt();
		reader.interrupt();
		try {
			in.close();
			out.close();
		} catch (IOException e) {
			;
		}
		numberOfHandlers--;
		synchronized (processingQueue) { // <--- Request ownership of the object's monitor to call notify()
			processingQueue.notify(); // Wake up an waiting external thread (if there is one) to update itself.
		}
		dispose();
	}

	private void dispose() {
		sender = null;
		reader = null;
		packetMap = null;
		in = null;
		out = null;
		connectionName = null;
		logger = null;
		listener = null;
		defaultPacketListener = null;
	}

	/**
	 * @return The name of this connection.
	 */
	public String getConnectionName() {
		return connectionName;
	}

	public void setConnectionName(String connectionName) {
		this.connectionName = connectionName;
	}

	/**
	 * @return The reader which is reading data from the InputStream.
	 */
	public DataReader getReader() {
		return reader;
	}

	/**
	 * @return The sender which is sending data to the OutputStream.
	 */
	public DataSender getSender() {
		return sender;
	}

	/**
	 * @return The application/user listener.
	 */
	public PacketListener getListener() {
		return listener;
	}

	public void setListener(PacketListener listener) {
		this.listener = listener;
	}

	/**
	 * @return The listener used by the PacketAPI for default packets.
	 */
	public DefaultPacketListener getDefaultPacketListener() {
		return defaultPacketListener;
	}

	public void setDefaultPacketListener(DefaultPacketListener defaultPacketListener) {
		this.defaultPacketListener = defaultPacketListener;
	}

	/**
	 * Called when a new packet was received.
	 * @param packet The packet just received.
	 */
	public void onPacketReceived(Packet packet) {
		if (hasExternalThread) {
			// Processing is done by the external thread
			Process process = new Process(ActionType.RECEIVED, packet.getId(), packet, this);
			addToQueue(process);
		} else {
			// Directly pass the packet to the listener
			processPacket(packet);
		}
	}

	public void onUnknownPacketReceived(short id) {
		if (hasExternalThread) {
			Process process = new Process(ActionType.UNKN_RECEIVED, id, null, this);
			addToQueue(process);
		} else {
			processUnknownPacket(id);
		}
	}

	private void processUnknownPacket(short id) {
		getDefaultPacketListener().onUnknownPacketReceived(this, id);
		getListener().onUnknownPacketReceived(this, id);
	}

	private void processPacket(Packet packet) {
		if (packet instanceof DefaultPacket) {
			getDefaultPacketListener().onPacketReceived(this, packet);
		} else {
			getListener().onPacketReceived(this, packet);
		}
	}

	/**
	 * Whether the connection was closed (using the close() function of this class) or not.
	 * @return
	 */
	public boolean isClosed() {
		return closed;
	}

	private void addToQueue(Process process) {
		synchronized (processingQueue) {
			processingQueue.add(process);
			processingQueue.notify();
		}
	}

	/**
	 * Sends a handshake to exchange version numbers. You have to call this method if you want to use state attributes like isVersionApproved().
	 */
	public void sendHandshake() {
		sendHandshake(HANDSHAKE_ID_REQUEST);
	}

	/**
	 * Sends a handshake using the given id as the handshake id.
	 * @param id The handshake id.
	 */
	public void sendHandshake(int id) {
		HandshakePacket handshake = new HandshakePacket();
		handshake.setHandshakeID(HANDSHAKE_ID_REQUEST);
		handshake.setProtocolVersion(id);
		sendPacket(handshake);
		setHandshakeSend(true);
	}

	public static boolean hasExternalThread() {
		return hasExternalThread;
	}

	/**
	 * External threads can call this method to become a processor for incoming packets for ALL handlers<br>
	 * Threads won't leave this method until the connections of all handlers are closed (or have failed)
	 * The thread can not be interrupted, all attempts will be ignored
	 * 
	 * @throws IllegalAccessError
	 *             If there is already a processor thread (@see hasExternalThread() )
	 */
	public static void threadJoin() {
		if (hasExternalThread) {
			throw new IllegalAccessError("There is already an external thread");
		}
		hasExternalThread = true;
		while (numberOfHandlers > 0) {
			try {
				Process proc = null;
				synchronized (processingQueue) {
					if (!processingQueue.isEmpty()) {
						proc = processingQueue.removeFirst();
					}
				}
				if (proc != null) {
					switch (proc.getType()) {
					case RECEIVED:
						proc.getHandler().processPacket(proc.getPacket());
						break;
					case UNKN_RECEIVED:
						proc.getHandler().processUnknownPacket(proc.getPacketID());
						break;
					default:
						break;
					}
				} else // No more elements in the queue, wait for a notify
				{
					synchronized (processingQueue) {
						try {
							processingQueue.wait();
						} catch (InterruptedException e) {
							;
						}
					}
				}
			} catch (Throwable e) {
				System.err.println("Exception in external processing thread: " + e);
			}
		}
		hasExternalThread = false;
	}

	/**
	 * @return Whether the protocol version is approved. The protocol version is approved when both peers have the same protocol version.<br>
	 *         To request a version check, simply send a handshake packet by calling <code>sendHandshake()</code>. After receiving the response from the other side and comparing the version numbers, the version will
	 *         be approved.
	 */
	public boolean isVersionApproved() {
		return versionApproved;
	}

	public void setVersionApproved(boolean versionApproved) {
		this.versionApproved = versionApproved;
	}

	/**
	 * @return Whether this side has send a handshake packet.<br>
	 *         Note: Handshake packets are not send automatically, you have to call <code>sendHandshake()</code>.
	 */
	public boolean isHandshakeSend() {
		return handshakeSend;
	}

	public void setHandshakeSend(boolean handshakeSend) {
		this.handshakeSend = handshakeSend;
	}

	/**
	 * @return The protocol version of the other peer or -1 if the version used by the other peer is unknown (e.g no handshake was send)
	 */
	public int getRemoteProtocolVersion() {
		return remoteProtocolVersion;
	}

	public void setRemoteProtocolVersion(int remoteProtocolVersion) {
		this.remoteProtocolVersion = remoteProtocolVersion;
	}

}
