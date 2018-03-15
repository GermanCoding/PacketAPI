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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

public class DataReader extends Thread {

	protected PacketHandler handler;

	public DataReader(PacketHandler packetHandler) {
		this.handler = packetHandler;
		this.setName("DataReader - " + handler.getConnectionName());
		this.start();
	}

	@Override
	public void run() {
		DataInputStream in = new DataInputStream(handler.in);
		try {
			while (!Thread.interrupted()) {
				int length = in.readInt();
				if (length <= 0) {
					throw new IOException("Protocol violation: Illegal length received");
				}
				
				byte[] data = new byte[length];
				in.readFully(data); // Read the whole packet into the buffer
				// Create a sub-inputstream which can only read this packet
				DataInputStream packetIn = new DataInputStream(new ByteArrayInputStream(data));

				short id = packetIn.readShort();

				Packet packet = handler.getNewPacketInstance(id);
				if (packet == null) {
					handler.onUnknownPacketReceived(id);
					continue;
				}

				try {
					packet.handle(packetIn);
				} catch (IOException e) {
					if (packet.isCritical())
						throw new IOException("Parsing packet with id " + id + " failed: " + e);
					else
						continue;
				}

				if (packetIn.available() > 0) {
					System.out.println("[DEBUG] [" + this.getName() + "] Packet with id " + id + " was not fully read, " + packetIn.available() + " bytes left in the buffer.");
					// TODO: Remove this debug message in release version
				}
				handler.onPacketReceived(packet);
			}
		} catch (Exception e) {
			if (Thread.interrupted() || handler.isClosed() || e instanceof InterruptedException)
				return; // Close silently
			if (e instanceof EOFException) {
				handler.onConnectionClosed("EOFException in DataReader", false);
			} else {
				handler.onConnectionFail(e);
			}
		}
	}

}
