package com.learning.hft.systems;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Chapter 6 — the latency cost of Nagle's algorithm, and the {@code TCP_NODELAY} fix.
 * Enemy: <b>jitter/latency</b> from the kernel coalescing small writes.
 *
 * <p>Nagle buffers small outbound writes to coalesce them into fewer packets; combined with the
 * peer's delayed-ACK it can add tens of milliseconds to a request/response exchange. This demo runs a
 * loopback ping-pong of tiny messages with Nagle ON (default) vs OFF ({@code setTcpNoDelay(true)}) and
 * prints the average round-trip time so you can see the difference.
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase6-systems-internals exec:java \
 *       -Dexec.mainClass=com.learning.hft.systems.TcpNoDelayDemo
 * </pre>
 * (Effect size varies by OS/TCP-stack; the point is the mechanism and the API.)
 */
public final class TcpNoDelayDemo {

    private static final int ROUNDS = 200;

    public static void main(String[] args) throws Exception {
        System.out.printf("Nagle ON  (default)     : avg RTT = %6.1f us%n", runEcho(false));
        System.out.printf("Nagle OFF (TCP_NODELAY) : avg RTT = %6.1f us%n", runEcho(true));
    }

    private static double runEcho(boolean noDelay) throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            Thread serverThread = new Thread(() -> echoServer(server), "echo-server");
            serverThread.setDaemon(true);
            serverThread.start();

            try (Socket client = new Socket()) {
                client.setTcpNoDelay(noDelay);
                client.connect(server.getLocalSocketAddress());
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                DataInputStream in = new DataInputStream(client.getInputStream());

                // warm-up
                for (int i = 0; i < 50; i++) {
                    out.writeInt(i);
                    out.flush();
                    in.readInt();
                }

                long start = System.nanoTime();
                for (int i = 0; i < ROUNDS; i++) {
                    out.writeInt(i);
                    out.flush();
                    in.readInt();
                }
                long elapsed = System.nanoTime() - start;
                return (elapsed / 1_000.0) / ROUNDS; // microseconds per round trip
            }
        }
    }

    private static void echoServer(ServerSocket server) {
        try (Socket conn = server.accept()) {
            conn.setTcpNoDelay(true); // echo side responsive
            DataInputStream in = new DataInputStream(conn.getInputStream());
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            while (true) {
                int v = in.readInt();
                out.writeInt(v);
                out.flush();
            }
        } catch (IOException ignored) {
            // client closed -> stop
        }
    }

    private TcpNoDelayDemo() {
    }
}
