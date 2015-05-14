# PacketAPI
A little Java API to send and receive packets over any network. Has TCP &amp; UDP support inbuild (but is also usable with Reliable UDP or any other stream-orientated network)

This library is designed to easily receive and send data over any network protocol. Data is send in packets which
are defined by this library (default packets) or by the user (user packets).
The PacketAPI works stream-orientated. All data is send to an OutputStream and read from an InputStream. There is also some basic UDP support.

Packets
--
A packet is only a pack of some bytes. Packets are designed to have less overhead. A packet looks like this:

	<LENGTH><PACKET_ID><DATA>
	
"LENGTH" are 4 bytes (parsed as a signed integer)

"PACKET_ID" is the ID of the packet, a two-byte short (also signed, though all ID's should be positive).

"DATA" are as many bytes as you want containing your packet data.


Note:
When using raw UDP: Avoid sending large packets (Bigger than UnreliableSocket.MAX_PACKET_SIZE) because otherwise the
packet will be splitted and if one of the splitted packets is lost or not received in order the whole connection
could fail (or at least the parsing). Sending such big packets is possible though.

Download
--
You can download a up to date pre-compiled version of this API here (Compiled with Java 8):
http://aknm-craft.de:8080/job/PacketAPI/lastSuccessfulBuild/artifact/PacketAPI/dist/lib/PacketAPI.jar

Of course you can also download/clone the source and compile it for yourself.

Javadoc
--
The javadoc of this project is available here: http://aknm-craft.de:8080/job/PacketAPI/javadoc

Usage
--
To create a new connection using the PacketAPI, simply create a new PacketHandler object.
```
PacketHandler myNewHandler = new PacketHandler(in, out, "Hello!", myListener);
```
'In' must be some InputStream which is already connected with your remote partner. 'Out' is the corresponding OutputStream. 'myListener' is a PacketListener which is notified when something happens. You can create a new one or set it to null.

Using the PacketAPI with UDP
--
Since the PacketAPI uses streams to send/receive data you might think that you can not use UDP. Wrong! The PacketAPI has support for UDP:
```
UnreliableSocket myUDPSocket = new UnreliableSocket(someUDPSocket);
PacketHandler myNewHandler = new PacketHandler(myUDPSocket.getInputStream(), myUDPSocket.getOutputStream(), "Hello!", myListener);
```

For more informations about the constructors and the methods, please read the javadocs. Not all methods are documentated yet but the most important things are.
