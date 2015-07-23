/*******************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Nummer378
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PacketWriter {

	// Parts of this class are stolen from the PluginMessageAPI by iKeirNez | https://github.com/iKeirNez/PluginMessageAPI-Plus-LEGACY/blob/master/src/main/java/com/ikeirnez/pluginmessageframework/PacketWriter.java

	private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	private DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

	public PacketWriter(short id) {
		try {
			writeShort(id);
		} catch (IOException e) {
			e.printStackTrace(); // Should never happen since we only write into the memory
		}
	}

	public byte[] toByteArray() {
		return byteArrayOutputStream.toByteArray();
	}

	// DataOutputStream methods

	public void write(int v) throws IOException {
		dataOutputStream.write(v);
	}

	public void write(byte b[], int off, int len) throws IOException {
		dataOutputStream.write(b, off, len);
	}

	public void write(byte[] b) throws IOException {
		dataOutputStream.write(b);
	}

	public void flush() throws IOException {
		dataOutputStream.flush();
	}

	public void writeBoolean(boolean v) throws IOException {
		dataOutputStream.writeBoolean(v);
	}

	public void writeByte(int v) throws IOException {
		dataOutputStream.writeByte(v);
	}

	public void writeShort(int v) throws IOException {
		dataOutputStream.writeShort(v);
	}

	public void writeChar(int v) throws IOException {
		dataOutputStream.writeChar(v);
	}

	public void writeInt(int v) throws IOException {
		dataOutputStream.writeInt(v);
	}

	public void writeLong(long v) throws IOException {
		dataOutputStream.writeLong(v);
	}

	public void writeFloat(float v) throws IOException {
		dataOutputStream.writeFloat(v);
	}

	public void writeDouble(double v) throws IOException {
		dataOutputStream.writeDouble(v);
	}

	public void writeBytes(String s) throws IOException {
		dataOutputStream.writeBytes(s);
	}

	public void writeChars(String s) throws IOException {
		dataOutputStream.writeChars(s);
	}

	public void writeUTF(String str) throws IOException {
		dataOutputStream.writeUTF(str);
	}

	public int size() {
		return dataOutputStream.size();
	}

}
