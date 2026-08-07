package com.learning.hft.capstone.riskgateway;

import org.agrona.collections.Long2LongHashMap;

/**
 * POC 3 — Nanosecond Risk Gateway &amp; Journal. <b>Fully implemented.</b>
 *
 * <p>Pre-trade credit check on every order, with every accepted order persisted before it would go to
 * market. Combines:
 * <ul>
 *   <li><b>Credit store</b> — an Agrona {@link Long2LongHashMap} (primitive, zero-alloc, cache-friendly
 *       open addressing, Ch.3). In production this is a Chronicle Map (off-heap, durable, huge) —
 *       Ch.4; the API here is deliberately the same shape.</li>
 *   <li><b>Durable journal</b> — every accepted order is appended to an {@link MmapJournal}
 *       (memory-mapped file) before returning, so it is crash-durable without the hot thread blocking
 *       on I/O (Ch.4/Ch.6 mmap). Chronicle Queue is the production upgrade.</li>
 * </ul>
 *
 * <p>Single-writer by design (Ch.2): drive it from one gateway thread.
 */
public final class RiskGateway {

    private final Long2LongHashMap remainingCredit = new Long2LongHashMap(Long.MIN_VALUE);
    private final MmapJournal journal;
    private long seq;

    public RiskGateway(MmapJournal journal) {
        this.journal = journal;
    }

    public void setLimit(long clientId, long limit) {
        remainingCredit.put(clientId, limit);
    }

    /** @return remaining credit for a client (0 if unknown). */
    public long remaining(long clientId) {
        long r = remainingCredit.get(clientId);
        return r == Long.MIN_VALUE ? 0 : r;
    }

    /**
     * Check an order against available credit; if approved, decrement credit and journal it durably.
     *
     * @return true if accepted (and persisted), false if the client is unknown or over limit.
     */
    public boolean check(long clientId, long orderNotional) {
        final long available = remainingCredit.get(clientId);
        if (available == Long.MIN_VALUE || available < orderNotional) {
            return false; // unknown client or insufficient credit -> reject, no journal entry
        }
        remainingCredit.put(clientId, available - orderNotional);
        journal.append(clientId, orderNotional, ++seq); // persist BEFORE the order proceeds
        return true;
    }
}
