/*******************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Maximilian Froehling alias Nummer378/GermanCoding
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/
package com.germancoding.packetapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import com.germancoding.packetapi.Process.ActionType;
import com.germancoding.packetapi.defaultpackets.ClosePacket;
import com.germancoding.packetapi.defaultpackets.DefaultPacket;
import com.germancoding.packetapi.defaultpackets.HandshakePacket;
import com.germancoding.packetapi.defaultpackets.KeepAlivePacket;

public class PacketHandler {

	/** Applications can change this value if they want. Default is 1 **/
	public static int PROTOCOL_VERSION = 1;
	/** Handshake ID used in the sendHandshake() method. The other side will respond to that packet. Default is 0 **/
	public static final int HANDSHAKE_ID_REQUEST = 0;

	/** Handshake ID used when replying to a handshake packet. The other side will not respond to that packet. Default is 1 **/
	public static final int HANDSHAKE_ID_RESPONSE = 1;

	/** Timeout (in MS) after which KeepAlive packets should be send. Default is 20.000 ms **/
	public static int DATA_TIMEOUT = 20000;

	public Logger logger = Logger.getLogger("PacketHandler");

	protected InputStream in; // Protected for direct access
	protected OutputStream out;

	private DataSender sender;
	private DataReader reader;

	private PacketListener listener;
	private DefaultPacketListener defaultPacketListener = new DefaultPacketListener(this);

	// State attributes
	private String connectionName;
	private boolean closed;
	private boolean versionApproved;
	private boolean handshakeSend;
	private boolean closeListenerNotified;
	private int remoteProtocolVersion = -1;
	private long lastPacketReceived;
	private boolean autoSendKeepAlive;
	private boolean notifyDefaults;

	private HashMap<Short, Class<? extends Packet>> packetMap = new HashMap<Short, Class<? extends Packet>>(); // TODO: What about a static packet map? (The local packet map could be optional)

	private boolean autoProcessPackets = true;
	private LinkedList<Process> processingQueue = new LinkedList<Process>();

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
	}

	private void registerPacketDefaults() {
		try {
			registerPacket(HandshakePacket.class);
			registerPacket(ClosePacket.class);
			registerPacket(KeepAlivePacket.class);
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
		short id = packetClass.newInstance().getId();
		packetMap.put(id, packetClass);
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
	 * Sends the given packet by adding it to the sending queue. Sendings packets that are not registered is possible, but is not recommended.
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
	 *            An exception describing why the connection has failed.
	 */
	public void onConnectionFail(Exception e) {
		if (closed) // Abort if the connection was already closed (A closed connection can not fail)
			return;
		logger.warning("Connection '" + getConnectionName() + "' failed! " + e);
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
		if (closed)
			return;
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
		closed = true;
		if (!closeListenerNotified) {
			// Someone is calling close() directly so we assume that the connection was closed expectly
			getDefaultPacketListener().onConnectionClosed(this, "Connection closed locally", true);
			getListener().onConnectionClosed(this, "Connection closed locally", true);
		}
		ClosePacket close = new ClosePacket();
		sendPacket(close);
		long timeout = System.currentTimeMillis() + 5000;
		do {
			synchronized (this) {
				try {
					this.wait(500);
					if (System.currentTimeMillis() >= timeout) {
						break;
					}
				} catch (InterruptedException e) {
					break;
				}
			}
		} while (!sender.queueEmpty());
		sender.interrupt();
		reader.interrupt();
		try {
			in.close();
			out.close();
		} catch (IOException e) {
			;
		}
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

	protected void setDefaultPacketListener(DefaultPacketListener defaultPacketListener) {
		this.defaultPacketListener = defaultPacketListener;
	}

	/**
	 * Called when a new packet was received.
	 * 
	 * @param packet
	 *            The packet just received.
	 */
	public void onPacketReceived(Packet packet) {
		if (!autoProcessPackets) {
			// Processing is done by the external thread
			Process process = new Process(ActionType.RECEIVED, packet.getId(), packet, this);
			addToQueue(process);
		} else {
			// Directly pass the packet to the listener
			processPacket(packet);
		}
	}

	public void onUnknownPacketReceived(short id) {
		if (!autoProcessPackets) {
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
		setLastPacketReceived(System.currentTimeMillis());
		if (packet instanceof DefaultPacket) {
			getDefaultPacketListener().onPacketReceived(this, packet);
			if (notifyDefaults) {
				getListener().onPacketReceived(this, packet);
			}
		} else {
			getListener().onPacketReceived(this, packet);
		}
	}

	/**
	 * 
	 * @return Whether the connection was closed (using the close() function of this class) or not.
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
	 * 
	 * @param id
	 *            The handshake id.
	 */
	public void sendHandshake(int id) {
		HandshakePacket handshake = new HandshakePacket();
		handshake.setHandshakeID(id);
		handshake.setProtocolVersion(PROTOCOL_VERSION);
		sendPacket(handshake);
		setHandshakeSend(true);
	}

	/**
	 * Returns all packets that have not been processed yet. Returns <code>null</code> if {@link #automaticPacketProcessing()} is true. <br>
	 * Calling this method will first pass the cached packets to the listeners before it returns them. <br>
	 * If {@link #automaticPacketProcessing()} is false, it is recommended to call this method frequently otherwise no packets will get processed.
	 * 
	 * @return All packets in the queue or an empty list. Only <code>null</code> if packets are automatically processed.
	 * @see #automaticPacketProcessing()
	 */
	public List<Packet> getCachedPackets() {
		if (automaticPacketProcessing() && processingQueue.isEmpty()) // There could be packets left in the queue even if automatic processing is on
			return null;

		ArrayList<Packet> packets = new ArrayList<Packet>();

		for (Process ppacket : processingQueue) {

			switch (ppacket.getType()) {
			case RECEIVED:
				processPacket(ppacket.getPacket());
				packets.add(ppacket.getPacket());
				break;
			case UNKN_RECEIVED:
				processUnknownPacket(ppacket.getPacketID());
				break;
			default:
				logger.warning("Unknown packet processor type: " + ppacket.getType());
				break;
			}
		}

		processingQueue.clear();
		return packets;
	}

	/**
	 * @return Whether automatic packet processing is on. If true, packets will be passed directly to the listener after receiving.
	 *         This is done on an async thread (called DataReader). 
	 *         <br> In some cases you may want to handle to the incoming packets on your (main) thread.
	 *         To achieve this, set automatic packet processing to false and call {@link #getCachedPackets()} frequently to get your packets processed on your thread.
	 *         <br> Default is true.
	 */
	public boolean automaticPacketProcessing() {
		return autoProcessPackets;
	}

	public void setAutomaticPacketProcessing(boolean on) {
		autoProcessPackets = on;
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

	/**
	 * @return The timestamp when the last packet was (successfull) received. 0 if no packet was received yet.
	 */
	public long getLastPacketReceived() {
		return lastPacketReceived;
	}

	public void setLastPacketReceived(long lastPacketReceived) {
		this.lastPacketReceived = lastPacketReceived;
	}

	/**
	 * @return Whether the library automatically sends packets to keep the connection alive. Default is false.
	 */
	public boolean autoSendKeepAlive() {
		return autoSendKeepAlive;
	}

	public void setAutoSendKeepAlive(boolean autoSendKeepAlive) {
		this.autoSendKeepAlive = autoSendKeepAlive;
	}

	/**
	 * @return If no data is send by the application for some time, this value returns true.
	 * @see #DATA_TIMEOUT
	 */
	public boolean shouldSendKeepAlive() {
		return (System.currentTimeMillis() - getLastPacketReceived()) >= DATA_TIMEOUT;
	}

	/**
	 * Sets whether this library calls PacketListener.onPacketReceived when default packets are received.
	 * 
	 * @param notify
	 *            Whether to notify your PacketListener when we receive default packets. Normally, only the DefaultPacketListener gets notified.
	 */
	public void notifyOnDefaultPackets(boolean notify) {
		this.notifyDefaults = notify;
	}

}
