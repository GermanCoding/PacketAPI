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

	private HashMap<Short, Class<? extends Packet>> packetMap = new HashMap<Short, Class<? extends Packet>>();

	private String connectionName;
	public Logger logger = Logger.getLogger("PacketHandler");

	public InputStream in;
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

	private static boolean hasExternalThread;
	private static int numberOfHandlers;
	private static LinkedList<Process> processingQueue = new LinkedList<Process>();

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

	public void sendPacket(Packet p) {
		getSender().sendPacket(p);
	}

	public void onConnectionFail(Exception e) {
		if (closed) // Abort if the connection was already closed (A closed connection can not fail)
			return;
		logger.severe("Connection '" + getConnectionName() + "' failed! " + e);
		getDefaultPacketListener().onConnectionFailed(this);
		getListener().onConnectionFailed(this);
		close();
	}

	public void onConnectionClosed(String message, boolean expected) {
		getDefaultPacketListener().onConnectionClosed(this, message, expected);
		getListener().onConnectionClosed(this, message, expected);
		closeListenerNotified = true;
		close();
	}

	public void close() {
		if (closed)
			return;
		if(!closeListenerNotified)
		{
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

	public String getConnectionName() {
		return connectionName;
	}

	public void setConnectionName(String connectionName) {
		this.connectionName = connectionName;
	}

	public DataReader getReader() {
		return reader;
	}

	public DataSender getSender() {
		return sender;
	}

	public PacketListener getListener() {
		return listener;
	}

	public void setListener(PacketListener listener) {
		this.listener = listener;
	}

	public DefaultPacketListener getDefaultPacketListener() {
		return defaultPacketListener;
	}

	public void setDefaultPacketListener(DefaultPacketListener defaultPacketListener) {
		this.defaultPacketListener = defaultPacketListener;
	}

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

	public boolean isClosed() {
		return closed;
	}

	private void addToQueue(Process process) {
		synchronized (processingQueue) {
			processingQueue.add(process);
			processingQueue.notify();
		}
	}

	public void sendHandshake() {
		HandshakePacket handshake = new HandshakePacket();
		handshake.setHandshakeID((int) System.currentTimeMillis()); // Bits will be cut off if the long value is to big for an integer
		handshake.setProtocolVersion(PacketHandler.PROTOCOL_VERSION);
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

	public boolean isVersionApproved() {
		return versionApproved;
	}

	public void setVersionApproved(boolean versionApproved) {
		this.versionApproved = versionApproved;
	}

	public boolean isHandshakeSend() {
		return handshakeSend;
	}

	public void setHandshakeSend(boolean handshakeSend) {
		this.handshakeSend = handshakeSend;
	}

}
