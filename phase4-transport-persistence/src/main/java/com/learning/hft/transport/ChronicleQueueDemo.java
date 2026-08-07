package com.learning.hft.transport;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;

import java.nio.file.Path;

/**
 * Chapter 4 — persist and replay events with a memory-mapped Chronicle Queue.
 * Enemy killed: <b>GC</b> (off-heap) and I/O <b>jitter</b> (the app thread never blocks on disk).
 *
 * <p>Appending is a store into an mmap'd file; the OS flushes dirty pages to NVMe lazily via the
 * page cache, so the writing thread pays no synchronous I/O cost. A {@link ExcerptTailer} replays
 * the exact byte stream later — the basis of event sourcing and the risk-gateway journal (POC 3).
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase4-transport-persistence exec:java \
 *       -Dexec.mainClass=com.learning.hft.transport.ChronicleQueueDemo
 * </pre>
 */
public final class ChronicleQueueDemo {

    public static void main(String[] args) {
        final Path dir = Path.of(System.getProperty("java.io.tmpdir"), "learning-cq");

        try (ChronicleQueue queue = ChronicleQueue.singleBuilder(dir).build()) {
            // --- write side ---
            final ExcerptAppender appender = queue.createAppender();
            for (int i = 0; i < 10; i++) {
                appender.writeText("order-" + i); // production: writeBytes with an SBE flyweight
            }
            System.out.println("appended 10 events to " + dir);

            // --- replay side ---
            final ExcerptTailer tailer = queue.createTailer();
            String text;
            while ((text = tailer.readText()) != null) {
                System.out.println("replayed: " + text);
            }
        }
    }

    private ChronicleQueueDemo() {
    }
}
