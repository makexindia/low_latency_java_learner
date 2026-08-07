package com.learning.hft.capstone.vwap;

import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;

/**
 * POC 2 — Extreme-Scale Blended VWAP (front-office side). <b>Fully implemented.</b>
 *
 * <p>Many producer threads (mock Tier-1 bank RFS feeds) publish price updates; a single pricing-engine
 * thread blends them into a real-time volume-weighted average price per currency pair. Combines:
 * <ul>
 *   <li><b>MPSC hand-off</b> — producers fan in to one consumer via a JCTools {@link MpscArrayQueue}
 *       (Ch.2 single-writer consumer, lock-free).</li>
 *   <li><b>Object pool</b> — {@link PriceUpdate} carriers are borrowed from a pre-allocated
 *       {@link MpmcArrayQueue} and recycled after processing, so the steady state allocates <b>zero</b>
 *       objects (Ch.3 / recurring-pattern "pre-allocate &amp; reuse").</li>
 *   <li><b>Primitive accumulators</b> — per-pair sums in flat {@code double[]} indexed by pair id, no
 *       maps, no boxing (Ch.0 data-oriented layout).</li>
 * </ul>
 * In production the producers would be pinned to cores (Ch.4/6 affinity) and decode via thread-local
 * SBE decoders (Ch.3); the MPSC-vs-lock latency claim is measured in phase2 {@code QueueHandoffBenchmark}.
 *
 * <p>Not thread-safe for the accumulators by design — only the single consumer thread calls
 * {@link #processOne()} / {@link #vwap(int)}. Producers only touch {@link #borrow()} / {@link #publish}.
 */
public final class BlendedVwapEngine {

    public static final int MAX_PAIRS = 100;

    /** Reusable, pooled carrier — never allocated on the hot path after warm-up. */
    public static final class PriceUpdate {
        public int pairId;
        public long price;   // scaled integer
        public long volume;
    }

    private final double[] sumPriceVolume = new double[MAX_PAIRS];
    private final double[] sumVolume = new double[MAX_PAIRS];

    private final MpmcArrayQueue<PriceUpdate> pool;   // free list (borrow/return, multi-threaded)
    private final MpscArrayQueue<PriceUpdate> inbound; // producers -> single consumer

    public BlendedVwapEngine(int capacity) {
        this.pool = new MpmcArrayQueue<>(capacity);
        this.inbound = new MpscArrayQueue<>(capacity);
        for (int i = 0; i < capacity; i++) {
            pool.offer(new PriceUpdate()); // one-time allocation of the whole pool
        }
    }

    /** Producer: borrow a carrier from the pool (spins if momentarily empty). */
    public PriceUpdate borrow() {
        PriceUpdate u;
        while ((u = pool.poll()) == null) {
            Thread.onSpinWait();
        }
        return u;
    }

    /** Producer: publish a filled carrier to the engine (spins on back-pressure). */
    public void publish(PriceUpdate update) {
        while (!inbound.offer(update)) {
            Thread.onSpinWait();
        }
    }

    /** Consumer: apply one update to the accumulators and recycle the carrier. @return false if idle. */
    public boolean processOne() {
        PriceUpdate u = inbound.poll();
        if (u == null) {
            return false;
        }
        sumPriceVolume[u.pairId] += (double) u.price * u.volume;
        sumVolume[u.pairId] += u.volume;
        pool.offer(u); // return to pool — zero allocation
        return true;
    }

    /** @return the blended VWAP for a pair, or NaN if no volume yet. */
    public double vwap(int pairId) {
        double v = sumVolume[pairId];
        return v == 0.0 ? Double.NaN : sumPriceVolume[pairId] / v;
    }

    /** @return total volume accumulated for a pair (used by tests to prove no message loss). */
    public long totalVolume(int pairId) {
        return (long) sumVolume[pairId];
    }
}
