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
