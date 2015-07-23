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

import com.germancoding.packetapi.defaultpackets.ClosePacket;
import com.germancoding.packetapi.defaultpackets.HandshakePacket;
import com.germancoding.packetapi.defaultpackets.IDRegistry;
import com.germancoding.packetapi.defaultpackets.KeepAlivePacket;

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
		case IDRegistry.KEEPALIVE_PACKET:
			handleKeepAlivePacket((KeepAlivePacket) packet);
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
		if (!handler.isClosed())
			handler.onConnectionClosed(packet.getCloseMessage(), true);
	}

	private void handleKeepAlivePacket(KeepAlivePacket packet) {
		if (!packet.isResponse()) {
			KeepAlivePacket keepAliveResponse = new KeepAlivePacket();
			keepAliveResponse.setResponse(true);
			handler.sendPacket(keepAliveResponse);
		}
	}

}
