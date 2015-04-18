package com.germancoding.packetapi.defaultpackets;

import java.io.DataInputStream;
import java.io.IOException;

import com.germancoding.packetapi.Packet;
import com.germancoding.packetapi.PacketWriter;

public class ClosePacket extends Packet implements DefaultPacket{

	private String closeMessage = "Connection closed by remote partner";
	
	@Override
	public short getId() {
		return IDRegistry.CLOSE_PACKET;
	}

	@Override
	public void handle(DataInputStream in) throws IOException {
		closeMessage = in.readUTF();
	}

	@Override
	public PacketWriter prepare() throws IOException {
		PacketWriter writer = new PacketWriter(getId());
		writer.writeUTF(getCloseMessage());
		return writer;
	}

	@Override
	public boolean isCritical() {
		return false;
	}

	public String getCloseMessage() {
		return closeMessage;
	}

	public void setCloseMessage(String closeMessage) {
		this.closeMessage = closeMessage;
	}

}
