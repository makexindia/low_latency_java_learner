package com.learning.hft.systems;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Enumeration;

/**
 * Chapter 6 — UDP multicast, the transport under exchange market-data feeds. Enemy: <b>coordination</b>
 * (one send fans out to many receivers at line rate; the network replicates the packet, not the app).
 *
 * <p>Receivers <b>join a group</b> (IGMP) via a {@link MembershipKey}; a single datagram from the
 * sender reaches all of them. No connection, no ordering, no delivery guarantee — you layer exactly
 * the reliability you need on top (as Aeron does with position/NAK, Ch.4).
 *
 * <p>Run (two terminals):
 * <pre>
 *   java -cp target/classes com.learning.hft.systems.UdpMulticastDemo receiver
 *   java -cp target/classes com.learning.hft.systems.UdpMulticastDemo sender
 * </pre>
 * Multicast requires a network interface that supports it; on some VMs/containers it may be disabled.
 */
public final class UdpMulticastDemo {

    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 5555;
    private static final int MESSAGES = 5;

    public static void main(String[] args) throws IOException, InterruptedException {
        final String role = args.length == 0 ? "help" : args[0];
        final NetworkInterface nif = firstMulticastInterface();

        switch (role) {
            case "sender" -> runSender(nif);
            case "receiver" -> runReceiver(nif);
            default -> System.out.println("usage: UdpMulticastDemo <sender|receiver>");
        }
    }

    private static void runReceiver(NetworkInterface nif) throws IOException {
        try (DatagramChannel ch = DatagramChannel.open(StandardProtocolFamily.INET)) {
            ch.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            ch.bind(new InetSocketAddress(PORT));
            ch.setOption(StandardSocketOptions.IP_MULTICAST_IF, nif);
            MembershipKey key = ch.join(InetAddress.getByName(GROUP), nif);
            System.out.println("receiver: joined " + GROUP + " on " + nif.getName());

            ByteBuffer buf = ByteBuffer.allocate(256);
            for (int i = 0; i < MESSAGES; i++) {
                buf.clear();
                ch.receive(buf);
                buf.flip();
                byte[] b = new byte[buf.remaining()];
                buf.get(b);
                System.out.println("receiver: " + new String(b));
            }
            key.drop();
        }
    }

    private static void runSender(NetworkInterface nif) throws IOException, InterruptedException {
        try (DatagramChannel ch = DatagramChannel.open(StandardProtocolFamily.INET)) {
            ch.setOption(StandardSocketOptions.IP_MULTICAST_IF, nif);
            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(GROUP), PORT);
            for (int i = 0; i < MESSAGES; i++) {
                byte[] msg = ("tick-" + i + " EURUSD=1.0850").getBytes();
                ch.send(ByteBuffer.wrap(msg), group);
                System.out.println("sender: sent tick-" + i);
                Thread.sleep(200);
            }
        }
    }

    private static NetworkInterface firstMulticastInterface() throws IOException {
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface ni = ifaces.nextElement();
            if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
                return ni;
            }
        }
        // Fall back to loopback (works for local sender+receiver on many OSes).
        return NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
    }

    private UdpMulticastDemo() {
    }
}
