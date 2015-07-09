package com.germancoding.packetapi.defaultpackets;

import java.io.DataInputStream;
import java.io.IOException;

import com.germancoding.packetapi.Packet;
import com.germancoding.packetapi.PacketWriter;

public class KeepAlivePacket extends Packet implements DefaultPacket {
	
	private boolean response;

	@Override
	public short getId() {
		return IDRegistry.KEEPALIVE_PACKET;
	}

	@Override
	public void handle(DataInputStream in) throws IOException {
		setResponse(in.readBoolean());
	}

	@Override
	public PacketWriter prepare() throws IOException {
		PacketWriter writer = new PacketWriter(getId());
		writer.writeBoolean(isResponse());
		return writer;
	}

	@Override
	public boolean isCritical() {
		return false;
	}

	public boolean isResponse() {
		return response;
	}

	public void setResponse(boolean response) {
		this.response = response;
	}

}
