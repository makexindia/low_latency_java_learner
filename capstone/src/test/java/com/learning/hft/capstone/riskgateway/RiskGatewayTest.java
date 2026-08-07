package com.learning.hft.capstone.riskgateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskGatewayTest {

    private static final long SIZE = 1 << 20;

    @Test
    void enforcesCreditLimit(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("risk.journal");
        try (MmapJournal journal = new MmapJournal(file, SIZE)) {
            RiskGateway gw = new RiskGateway(journal);
            gw.setLimit(1L, 1_000);

            assertTrue(gw.check(1L, 400));    // ok, 600 left
            assertEquals(600, gw.remaining(1L));
            assertFalse(gw.check(1L, 700));   // over limit -> reject
            assertEquals(600, gw.remaining(1L)); // unchanged on reject
            assertFalse(gw.check(99L, 1));    // unknown client -> reject

            assertEquals(1, journal.recordCount()); // only the accepted order was journaled
        }
    }

    @Test
    void journalIsDurableAcrossReopen(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("durable.journal");

        // First run: accept two orders, then close (flush).
        try (MmapJournal journal = new MmapJournal(file, SIZE)) {
            RiskGateway gw = new RiskGateway(journal);
            gw.setLimit(7L, 10_000);
            assertTrue(gw.check(7L, 1_000));
            assertTrue(gw.check(7L, 2_500));
        }

        // Second run: reopen the SAME file and replay — state survived the "crash".
        try (MmapJournal reopened = new MmapJournal(file, SIZE)) {
            assertEquals(2, reopened.recordCount());
            AtomicLong notionalSum = new AtomicLong();
            AtomicInteger seen = new AtomicInteger();
            reopened.replay((clientId, notional, seq) -> {
                assertEquals(7L, clientId);
                notionalSum.addAndGet(notional);
                seen.incrementAndGet();
            });
            assertEquals(2, seen.get());
            assertEquals(3_500, notionalSum.get());
        }
    }
}
