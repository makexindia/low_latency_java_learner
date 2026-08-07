package com.learning.hft.systems;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Chapter 6 — zero-copy IPC through a shared memory-mapped file. Enemy killed: <b>coordination</b>
 * (no sockets, no kernel copy — two processes share physical pages via the page cache).
 *
 * <p>Layout of the mapped region: {@code [ sequence:long ][ value:long ]}. The writer stores a
 * {@code value} then publishes by bumping {@code sequence}; the reader spins on {@code sequence} and,
 * when it advances, reads the new {@code value}. This is a 1-slot shared-memory mailbox — the same
 * idea Aeron IPC / Chronicle scale up to full ring logs.
 *
 * <p>Two processes (real IPC — run in two terminals):
 * <pre>
 *   java -cp target/classes com.learning.hft.systems.SharedMemoryIpcDemo reader
 *   java -cp target/classes com.learning.hft.systems.SharedMemoryIpcDemo writer
 * </pre>
 * With no argument it runs an in-process two-thread demo (same mechanism, one JVM) so it works under
 * {@code exec:java}.
 *
 * <p><b>Caveat:</b> {@link MappedByteBuffer} accesses are not JMM-volatile, so this spin protocol is
 * illustrative. Production shared-memory rings add explicit fences / {@code VarHandle} acquire-release
 * (Ch.7) over the mapped region.
 */
public final class SharedMemoryIpcDemo {

    private static final int SEQ_OFFSET = 0;
    private static final int VALUE_OFFSET = 8;
    private static final long SIZE = 16;
    private static final int MESSAGES = 10;

    public static void main(String[] args) throws IOException, InterruptedException {
        final Path file = Path.of(System.getProperty("java.io.tmpdir"), "learning-shm.dat");
        final MappedByteBuffer buf = map(file);

        if (args.length == 0) {
            System.out.println("no role given -> in-process demo (see javadoc for two-terminal IPC)");
            Thread reader = new Thread(() -> runReader(buf), "reader");
            reader.start();
            runWriter(buf);
            reader.join();
        } else if (args[0].equals("writer")) {
            runWriter(buf);
        } else {
            runReader(buf);
        }
    }

    private static MappedByteBuffer map(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_WRITE, 0, SIZE);
            buf.putLong(SEQ_OFFSET, 0);
            buf.force();
            return buf;
        }
    }

    private static void runWriter(MappedByteBuffer buf) {
        for (int i = 1; i <= MESSAGES; i++) {
            buf.putLong(VALUE_OFFSET, (long) i * 100);
            buf.putLong(SEQ_OFFSET, i); // publish
            System.out.println("writer: published seq=" + i + " value=" + (i * 100));
            spinMillis(50);
        }
    }

    private static void runReader(MappedByteBuffer buf) {
        long lastSeq = 0;
        while (lastSeq < MESSAGES) {
            long seq = buf.getLong(SEQ_OFFSET);
            if (seq != lastSeq) {
                long value = buf.getLong(VALUE_OFFSET);
                System.out.println("reader: observed seq=" + seq + " value=" + value);
                lastSeq = seq;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    private static void spinMillis(long millis) {
        long end = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    private SharedMemoryIpcDemo() {
    }
}
