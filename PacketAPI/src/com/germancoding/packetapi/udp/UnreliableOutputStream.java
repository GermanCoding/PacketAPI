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
