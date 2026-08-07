package com.learning.hft.capstone.riskgateway;

import org.agrona.collections.Long2LongHashMap;

/**
 * POC 3 — Nanosecond Risk Gateway & Journal. <b>STUB.</b>
 *
 * <p>Goal: pre-trade credit check on every order, and persist every message to disk BEFORE it goes
 * to market. <b>Target to quote:</b> sub-µs durable writes, off the hot path.
 *
 * <p>Design:
 * <ul>
 *   <li>Client credit limits in a Chronicle Map (off-heap KV, Chapter 4). This stub uses an Agrona
 *       {@link Long2LongHashMap} as an in-memory placeholder — swap it for {@code ChronicleMap} when
 *       you wire in the phase4 dependencies.</li>
 *   <li>On order arrival: check available credit; if approved, append the raw bytes to a Chronicle
 *       Queue (mmap → NVMe) so the write is durable without blocking the execution thread.</li>
 * </ul>
 *
 * <p>Prove it: journal is durable (replay after kill) and the append never stalls the hot path
 * (async-profiler wall-clock shows no I/O wait on the order thread).
 */
public final class RiskGateway {

    // Placeholder for a Chronicle Map: clientId -> remaining credit (scaled integer).
    private final Long2LongHashMap remainingCredit = new Long2LongHashMap(Long.MIN_VALUE);

    public void setLimit(long clientId, long limit) {
        remainingCredit.put(clientId, limit);
    }

    /**
     * @return true if the order passes the credit check (and, in the full version, has been journaled).
     */
    public boolean check(long clientId, long orderNotional) {
        final long available = remainingCredit.get(clientId);
        if (available == Long.MIN_VALUE || available < orderNotional) {
            return false; // unknown client or insufficient credit -> reject
        }
        remainingCredit.put(clientId, available - orderNotional);
        // TODO: append raw order bytes to a Chronicle Queue here (durable, off-heap) before returning.
        return true;
    }
}
