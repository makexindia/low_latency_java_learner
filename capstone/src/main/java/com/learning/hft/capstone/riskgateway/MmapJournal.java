package com.learning.hft.capstone.riskgateway;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * POC 3 — a minimal durable journal over a memory-mapped file (Ch.4/Ch.6 mmap). <b>Implemented.</b>
 *
 * <p>Appending a record is a memory store into the mapped region; the OS flushes dirty pages to disk
 * lazily, so the calling (execution) thread never blocks on I/O. A header long at offset 0 records the
 * write position, so the journal survives a restart: reopen the same file and {@link #replay} rebuilds
 * state. This is exactly Chronicle Queue's mechanism (Ch.4) with nothing hidden; Chronicle is the
 * production upgrade (roll cycles, indexing, tailer resumption).
 *
 * <p>Record layout (fixed 24 bytes): {@code [ clientId:long ][ notional:long ][ seq:long ]}.
 */
public final class MmapJournal implements AutoCloseable {

    /** Callback for {@link #replay}. Primitives only — zero allocation. */
    @FunctionalInterface
    public interface RecordConsumer {
        void accept(long clientId, long notional, long seq);
    }

    private static final int HEADER_BYTES = Long.BYTES; // stores write position
    private static final int RECORD_BYTES = Long.BYTES * 3;

    private final FileChannel channel;
    private final MappedByteBuffer buffer;
    private int writePos; // bytes written after the header

    public MmapJournal(Path file, long sizeBytes) throws IOException {
        this.channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        this.buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, sizeBytes);
        this.writePos = (int) buffer.getLong(0); // resume from a prior run (0 for a fresh file)
    }

    /** Append one durable record. Hot path: memory stores only, no syscall, no blocking. */
    public void append(long clientId, long notional, long seq) {
        final int pos = HEADER_BYTES + writePos;
        buffer.putLong(pos, clientId);
        buffer.putLong(pos + 8, notional);
        buffer.putLong(pos + 16, seq);
        writePos += RECORD_BYTES;
        buffer.putLong(0, writePos); // publish the new durable position
    }

    /** Replay every record in order (crash recovery / event sourcing). */
    public void replay(RecordConsumer consumer) {
        final int count = recordCount();
        for (int i = 0; i < count; i++) {
            final int pos = HEADER_BYTES + i * RECORD_BYTES;
            consumer.accept(buffer.getLong(pos), buffer.getLong(pos + 8), buffer.getLong(pos + 16));
        }
    }

    public int recordCount() {
        return writePos / RECORD_BYTES;
    }

    /** msync: force dirty pages to disk now (durability barrier). */
    public void force() {
        buffer.force();
    }

    @Override
    public void close() throws IOException {
        force();
        channel.close();
    }
}
