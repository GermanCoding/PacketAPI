package com.germancoding.packetapi;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Represents a data packet that can be send and received over a network.<br>
 * Note: Each subclass of this class has to define a nullary constructor (Constructor with no arguments)<br>
 * Also, every class extending <code>Packet</code> should be careful with <code>null</code>. When accessing local attributes,
 * it is recommended to check whether they are null, because the object may be created by this library just before.<br>
 * A packet can be registered at the <code>PacketHandler</code> class<br>
 * 
 * @author Max/Nummer378/GermanCoding
 * @see PacketHandler
 */
public abstract class Packet {

	/**
	 * A nullary constructor. Each subclass needs one (either by defining no constructor or by defining a nullary and a custom constructor)
	 * It is not required to call super() since this constructor does nothing.
	 */
	public Packet() {
		super(); // Calling the constructor of java.lang.Object is useless but empty method bodies look sad
	}

	/**
	 * @return The id of this packet. Should always return the same value. Note: Please use only positive numbers
	 */
	public abstract short getId();

	/**
	 * Called by the <code>DataReader</code> when a packet with this id is read. This method/function should read data and
	 * store the values in his own attributes. <br>
	 * <br>
	 * Example:<br>
	 * <code>this.message = in.readUTF();</code> <br>
	 * <br>
	 * This method is connected with the <code>prepare()</code> function. All data that is written in the <code>prepare()</code> function should be read in this method <b>in the same order</b>.
	 * 
	 * @param in
	 *            A DataInputStream, provided by the <code>DataReader</code> containing all the data (if nothing failed) that is
	 *            needed to handle this packet.<br>
	 *            The DataInputStream has a fixed length so you can't read more bytes than <code>in.available()</code> to avoid issues with
	 *            other packets. If <code>in</code> has not enough bytes to fill this packet with data, simply throw an IOException. The DataInputStream
	 *            will also throw a EOFException when you try to read more bytes than <code>in.available()</code>
	 * @throws IOException
	 *             If reading fails, e.g there are not enough bytes or the bytes are wrong encoded (e.g you want to read UTF but there is no UTF-String encoded in bytes)
	 * @see <code>prepare()</code>
	 */
	public abstract void handle(DataInputStream in) throws IOException;

	/**
	 * Called by the <code>DataSender</code> when this packet is send over the network. This method/function should write data that is stored
	 * in his own attributes into a <code>PacketWriter</code>. You have to create a new PacketWriter instance for that. <br>
	 * <br>
	 * Example:<br>
	 * <code>PacketWriter writer = new PacketWriter(getId());<br>
		writer.writeUTF(this.message);<br>
		return writer;<br>
		</code> <br>
	 * <br>
	 * This method is connected with the <code>handle()</code> function. All data that is read in the <code>handle()</code> function should be written in this method <b>in the same order</b>.
	 * 
	 * @return A new <code>PacketWriter</code> instance where all the data of this packet is stored
	 * @throws IOException
	 *             If a PacketWriter call fails. Should never happen (since the <code>PacketWriter</code> only stores data in the memory)
	 * @see handle()
	 */
	public abstract PacketWriter prepare() throws IOException;

	/**
	 * Defines whether this packet type is critical.<br>
	 * Critical means that the handle() function should never fail. If it fails though the connection will be terminated<br>
	 * Note: If a <code>prepare()</code> call fails the connection is also terminated (and marked as failed) no matter if the packet is critical or not
	 * 
	 * @return Whether this packet type is a critical packet that has to be received properly or not.
	 */
	public abstract boolean isCritical();

}
