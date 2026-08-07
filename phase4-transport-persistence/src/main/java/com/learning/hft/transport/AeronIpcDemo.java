package com.learning.hft.transport;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.BufferUtil;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.charset.StandardCharsets;

/**
 * Chapter 4 — brokerless messaging over an Aeron IPC (shared-memory) channel.
 * Enemy killed: <b>coordination</b> (no broker, no heap copy) and <b>jitter</b>.
 *
 * <p>An embedded {@link MediaDriver} owns the shared log. A {@link Publication} appends messages to
 * it; a {@link Subscription} polls them out via a {@link FragmentHandler}. No Kafka, no sockets, no
 * serialization to a broker — just two processes (here, one) sharing an mmap'd log.
 *
 * <p>The {@link IdleStrategy} on the poll loop is the same CPU/latency dial as the Disruptor
 * {@code WaitStrategy} (see {@code docs/recurring-patterns.md}).
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase4-transport-persistence exec:java \
 *       -Dexec.mainClass=com.learning.hft.transport.AeronIpcDemo
 * </pre>
 */
public final class AeronIpcDemo {

    private static final String CHANNEL = "aeron:ipc";
    private static final int STREAM_ID = 10;
    private static final int MESSAGE_COUNT = 10;

    public static void main(String[] args) {
        try (MediaDriver driver = MediaDriver.launchEmbedded();
             Aeron aeron = Aeron.connect(
                     new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
             Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {

            final UnsafeBuffer buffer =
                    new UnsafeBuffer(BufferUtil.allocateDirectAligned(256, 64));
            final IdleStrategy idle = new BusySpinIdleStrategy();

            final FragmentHandler handler = (buf, offset, length, header) -> {
                final byte[] bytes = new byte[length];
                buf.getBytes(offset, bytes);
                System.out.println("received: " + new String(bytes, StandardCharsets.UTF_8));
            };

            int published = 0;
            int received = 0;
            while (received < MESSAGE_COUNT) {
                if (published < MESSAGE_COUNT) {
                    final byte[] payload =
                            ("order-" + published).getBytes(StandardCharsets.UTF_8);
                    buffer.putBytes(0, payload);
                    // offer() returns the new position, or a NEGATIVE back-pressure code:
                    //   BACK_PRESSURED, NOT_CONNECTED, ADMIN_ACTION, CLOSED, MAX_POSITION_EXCEEDED
                    if (publication.offer(buffer, 0, payload.length) > 0) {
                        published++;
                    }
                }
                received += subscription.poll(handler, 10);
                idle.idle();
            }
        }
    }

    private AeronIpcDemo() {
    }
}
