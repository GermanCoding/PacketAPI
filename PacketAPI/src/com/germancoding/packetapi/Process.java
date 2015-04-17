package com.germancoding.packetapi;

public class Process {

	private ActionType type;
	private short packetID;
	private Packet packet;
	private PacketHandler handler;

	public Process(ActionType type, short packetID, Packet packet, PacketHandler handler) {
		super();
		this.setType(type);
		this.setPacketID(packetID);
		this.setPacket(packet);
		this.setHandler(handler);
	}

	public ActionType getType() {
		return type;
	}

	public void setType(ActionType type) {
		this.type = type;
	}

	public short getPacketID() {
		return packetID;
	}

	public void setPacketID(short packetID) {
		this.packetID = packetID;
	}

	public Packet getPacket() {
		return packet;
	}

	public void setPacket(Packet packet) {
		this.packet = packet;
	}

	public PacketHandler getHandler() {
		return handler;
	}

	public void setHandler(PacketHandler handler) {
		this.handler = handler;
	}

	public enum ActionType {
		RECEIVED, UNKN_RECEIVED,
	}
}
