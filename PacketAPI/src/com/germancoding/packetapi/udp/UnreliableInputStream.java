package com.germancoding.packetapi.udp;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.LinkedList;

public class UnreliableInputStream extends InputStream {

	private UnreliableSocket uSocket;
	private DatagramSocket socket;
	private LinkedList<Byte> buffer = new LinkedList<Byte>();

	public UnreliableInputStream(UnreliableSocket socket) throws SocketException {
		super();
		this.uSocket = socket;
		this.socket = uSocket.getSocket();
	}

	@Override
	public int available() throws IOException {
		return buffer.size();
	};

	@Override
	public void close() throws IOException {
		uSocket.close();
		buffer.clear();
		buffer = null;
		socket = null;
		// TODO: What about NPE's when methods are called after closing?
		// Null-checks everywhere are ugly
	};

	@Override
	public int read() throws IOException {
		if (buffer.size() > 0) {
			return buffer.removeFirst().intValue();
		} else {
			// No more data in the buffer, read some new!
			if (!readPacket())
				return -1;
			return read();
		}
	}

	private boolean readPacket() throws IOException {
		if (socket.isClosed())
			return false;
		DatagramPacket packet = new DatagramPacket(new byte[UnreliableSocket.MAX_PACKET_SIZE], UnreliableSocket.MAX_PACKET_SIZE);
		socket.receive(packet);
		if (uSocket.getRemoteAddress() != null) {
			if (!packet.getAddress().equals(uSocket.getRemoteAddress()) || packet.getPort() != uSocket.getRemotePort()) {
				// Silently ignore packets that we don't know
				return true;
			}
		}
		byte[] data = packet.getData();
		// TODO: Offsets?
		if (packet.getLength() != data.length) {
			byte[] fixedData = new byte[packet.getLength()];
			System.arraycopy(data, 0, fixedData, 0, fixedData.length);
			data = fixedData;
		}
		for (byte b : data) {
			buffer.add(b);
		}
		return true;
	}

}
