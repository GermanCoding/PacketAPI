package com.germancoding.packetapi;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.LinkedList;

import com.germancoding.packetapi.defaultpackets.KeepAlivePacket;

public class DataSender extends Thread {

	private PacketHandler handler;
	private LinkedList<Packet> sendQueue = new LinkedList<Packet>();

	public DataSender(PacketHandler handler) {
		this.handler = handler;
		this.setName("DataSender - " + handler.getConnectionName());
		this.start();
	}

	public void sendPacket(Packet packet) {
		synchronized (sendQueue) {
			sendQueue.add(packet);
		}
		synchronized (this) {
			this.notify();
		}
	}

	public boolean queueEmpty() {
		synchronized (sendQueue) {
			return sendQueue.isEmpty();
		}
	}

	@Override
	public void run() {
		try {
			while (!Thread.interrupted()) {
				Packet toSend = null;
				synchronized (sendQueue) {
					if (!sendQueue.isEmpty())
						toSend = sendQueue.removeFirst();
				}
				if (toSend != null) {
					// Create a new DOS every time to avoid buffer overflows (the written counter is an integer which will overflow at some point)
					DataOutputStream dos = new DataOutputStream(handler.out);
					PacketWriter writer = toSend.prepare();
					dos.writeInt(writer.toByteArray().length); // Write packet length
					dos.write(writer.toByteArray()); // Write packetID and content - The PacketWriter already prepared this data for us
					dos.flush(); // Flush it, (R)UDP implementations will send at least one UDP packet with the written data
					// TODO: flush() may be good for UDP, but in TCP it could cause lots of small packets which is not very efficient - Maybe some own algorithm to check whether flushs are necessary?
					dos = null; // TODO: Is this neccessary? Or does the GC delete this object anyway when we go into a new loop?
				} else if (handler.shouldSendKeepAlive() && handler.autoSendKeepAlive()) {
					KeepAlivePacket autoKeepAlive = new KeepAlivePacket();
					handler.sendPacket(autoKeepAlive);
					synchronized (this) {
						try {
							this.wait(PacketHandler.DATA_TIMEOUT / 3); // Wait a bit, give the other side time to receive the packet
						} catch (InterruptedException e) {
							return;
						}
					}
				} else {
					synchronized (this) {
						try {
							this.wait(PacketHandler.DATA_TIMEOUT); // Wakeup after some time to send KeepAlive's
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			}
		} catch (IOException e) {
			if (Thread.interrupted() || handler.isClosed())
				return; // Close silently
			if (e instanceof EOFException) { // EOF = End of File (Though, we don't have a "file" here :) )
				// Are EOF's possible in a SENDING stream??? But anyway, handling them is always good...
				handler.onConnectionClosed("EOFException in DataSender", false);
			} else {
				handler.onConnectionFail(e);
			}
		}
	}
}
