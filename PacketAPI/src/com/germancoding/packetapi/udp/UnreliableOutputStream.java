package com.germancoding.packetapi.udp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.LinkedList;

public class UnreliableOutputStream extends OutputStream {

	private UnreliableSocket uSocket;
	private DatagramSocket socket;
	private LinkedList<Byte> buffer = new LinkedList<Byte>();

	public UnreliableOutputStream(UnreliableSocket uSocket) throws SocketException {
		super();
		this.uSocket = uSocket;
		this.socket = uSocket.getSocket();
	}

	@Override
	public void flush() throws IOException {
		while (buffer.size() > 0)
			// Send more than one packet if the buffer is really big
			sendPacket();
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

	private void sendPacket() throws IOException {
		int length = buffer.size();
		byte[] data = null;
		if (length > UnreliableSocket.MAX_PACKET_SIZE) {
			length = UnreliableSocket.MAX_PACKET_SIZE;
		}
		data = new byte[length];
		for (int i = 0; i < length; i++) {
			data[i] = buffer.removeFirst();
		}
		DatagramPacket packet = new DatagramPacket(data, length, uSocket.getRemoteAddress(), uSocket.getRemotePort());
		socket.send(packet);
	}

	@Override
	public void write(int b) throws IOException {
		buffer.add(Integer.valueOf(b).byteValue());
	}

}
