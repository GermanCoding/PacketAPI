package com.germancoding.packetapi.defaultpackets;

import java.io.DataInputStream;
import java.io.IOException;

import com.germancoding.packetapi.Packet;
import com.germancoding.packetapi.PacketWriter;

public class HandshakePacket extends Packet implements DefaultPacket {

	private int handshakeID;
	private int protocolVersion;

	// Nullary constructor
	public HandshakePacket() {
	}

	@Override
	public void handle(DataInputStream in) throws IOException {
		this.handshakeID = in.readInt();
		this.protocolVersion = in.readInt();
	}

	@Override
	public PacketWriter prepare() throws IOException {
		PacketWriter data = new PacketWriter(getId());
		data.writeInt(handshakeID);
		data.write(protocolVersion);
		return data;
	}

	public int getHandshakeID() {
		return handshakeID;
	}

	public void setHandshakeID(int handshakeID) {
		this.handshakeID = handshakeID;
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	public void setProtocolVersion(int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	@Override
	public short getId() {
		return IDRegistry.HANDSHAKE_PACKET;
	}

	@Override
	public boolean isCritical() {
		return true;
	}

}
