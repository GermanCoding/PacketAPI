package com.germancoding.packetapi;

public interface PacketListener {

	public void onPacketReceived(PacketHandler handler, Packet packet);

	public void onConnectionFailed(PacketHandler handler);

	public void onConnectionClosed(PacketHandler handler);

	public void onUnknownPacketReceived(PacketHandler handler, short id);

}
