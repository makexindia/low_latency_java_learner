package com.learning.hft.capstone.vwap;

import org.jctools.queues.MpscArrayQueue;

/**
 * POC 2 — Extreme-Scale Blended VWAP (front-office side). <b>STUB.</b>
 *
 * <p>Goal: ingest 5 mock Tier-1 bank RFS feeds, compute a real-time volume-weighted average price
 * across 100 currency pairs, and publish the blended price.
 * <b>Target to quote:</b> cross-thread contention 15µs → ~300ns by replacing locks with MPSC.
 *
 * <p>Design:
 * <ul>
 *   <li>5 producer threads, each pinned to a core (Chapter 4 affinity), decoding market data with a
 *       thread-local SBE decoder (Chapter 3).</li>
 *   <li>All producers publish updates into one {@link MpscArrayQueue} (Chapter 2).</li>
 *   <li>A single pricing-engine thread drains the queue and updates per-pair VWAP accumulators held
 *       in primitive arrays indexed by pair id (Chapter 0 — no maps, no boxing).</li>
 * </ul>
 *
 * <p>Prove it: measure cross-thread hand-off latency (`Mode.SampleTime`) for a locked queue vs this
 * MPSC path and quote the drop.
 */
public final class BlendedVwapEngine {

    private static final int MAX_PAIRS = 100;

    // Per-pair VWAP accumulators — flat primitive arrays, indexed by pair id. Zero allocation.
    private final double[] sumPriceVolume = new double[MAX_PAIRS];
    private final double[] sumVolume = new double[MAX_PAIRS];

    // Producers offer PriceUpdate flyweights/records; single consumer drains. Capacity power-of-two.
    private final MpscArrayQueue<Object> inbound = new MpscArrayQueue<>(1 << 16);

    /** Called by the single pricing-engine thread. */
    public double vwap(int pairId) {
        final double vol = sumVolume[pairId];
        return vol == 0.0 ? Double.NaN : sumPriceVolume[pairId] / vol;
    }

    // TODO: producer side — onQuote(pairId, price, volume) offers onto `inbound`.
    // TODO: consumer side — drain loop applying updates to the accumulators + publishing blended price.
    // TODO: replace Object payload with a reusable PriceUpdate flyweight (no per-message allocation).

    public MpscArrayQueue<Object> inbound() {
        return inbound;
    }
}
