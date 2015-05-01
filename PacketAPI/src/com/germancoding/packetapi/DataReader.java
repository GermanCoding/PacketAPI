package com.germancoding.packetapi;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

public class DataReader extends Thread {

	private PacketHandler handler;

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
