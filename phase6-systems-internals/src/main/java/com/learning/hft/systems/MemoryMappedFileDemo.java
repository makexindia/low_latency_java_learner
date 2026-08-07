package com.learning.hft.systems;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Chapter 6 — memory-mapped files (mmap). Enemy killed: I/O <b>jitter</b> + <b>GC</b> (off-heap).
 *
 * <p>{@link FileChannel#map} maps a file into the process address space. After that, reading/writing
 * the file is reading/writing memory — no {@code read()}/{@code write()} syscall per operation. The
 * OS page cache holds the pages and flushes them lazily (or on {@link MappedByteBuffer#force}).
 * This is the mechanism under Chronicle Queue (Ch.4) and Aeron IPC.
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase6-systems-internals exec:java \
 *       -Dexec.mainClass=com.learning.hft.systems.MemoryMappedFileDemo
 * </pre>
 */
public final class MemoryMappedFileDemo {

    private static final int COUNT = 16;
    private static final long SIZE = COUNT * Long.BYTES;

    public static void main(String[] args) throws IOException {
        final Path file = Path.of(System.getProperty("java.io.tmpdir"), "learning-mmap.dat");

        try (FileChannel ch = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            final MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_WRITE, 0, SIZE);

            // Writing is a memory store; the kernel flushes dirty pages asynchronously.
            for (int i = 0; i < COUNT; i++) {
                buf.putLong(i * Long.BYTES, (long) i * i);
            }
            buf.force(); // msync: make the writes durable now (optional; kernel would do it anyway)

            // Reading is a memory load; the page is already resident in the page cache.
            long sum = 0;
            for (int i = 0; i < COUNT; i++) {
                sum += buf.getLong(i * Long.BYTES);
            }
            System.out.println("mapped " + SIZE + " bytes at " + file);
            System.out.println("sum of squares 0.." + (COUNT - 1) + " = " + sum);
        }
    }

    private MemoryMappedFileDemo() {
    }
}
