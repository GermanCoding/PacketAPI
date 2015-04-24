package com.germancoding.packetapi;

import com.germancoding.packetapi.defaultpackets.ClosePacket;
import com.germancoding.packetapi.defaultpackets.HandshakePacket;
import com.germancoding.packetapi.defaultpackets.IDRegistry;

public class DefaultPacketListener implements PacketListener {

	private PacketHandler handler;

	public DefaultPacketListener(PacketHandler handler) {
		this.handler = handler;
	}

	@Override
	public void onPacketReceived(PacketHandler handler, Packet packet) {
		switch (packet.getId()) {
		case IDRegistry.HANDSHAKE_PACKET:
			handleHandshakePacket((HandshakePacket) packet);
			break;
		case IDRegistry.CLOSE_PACKET:
			handleClosePacket((ClosePacket) packet);
			break;
		default:
			break;
		}
		;
	}

	@Override
	public void onConnectionFailed(PacketHandler handler) {
		;
	}

	@Override
	public void onConnectionClosed(PacketHandler handler, String message, boolean expected) {
		;
	}

	@Override
	public void onUnknownPacketReceived(PacketHandler handler, short id) {
		;
	}

	// Handlers

	private void handleHandshakePacket(HandshakePacket packet) {
		if (packet.getHandshakeID() == PacketHandler.HANDSHAKE_ID_REQUEST) {
			handler.sendHandshake(PacketHandler.HANDSHAKE_ID_RESPONSE);
		}
		handler.setRemoteProtocolVersion(packet.getProtocolVersion());
		if (packet.getProtocolVersion() == PacketHandler.PROTOCOL_VERSION) {
			handler.setVersionApproved(true);
		}
	}

	private void handleClosePacket(ClosePacket packet) {
		handler.onConnectionClosed(packet.getCloseMessage(), true);
	}

}
