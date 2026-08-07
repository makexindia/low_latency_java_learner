package com.learning.hft.capstone.vwap;

import com.learning.hft.capstone.vwap.BlendedVwapEngine.PriceUpdate;

/**
 * POC 2 — runnable demo: 5 producer threads feed one pricing engine, which blends VWAP over 100 pairs.
 *
 * <pre>
 *   mvn -q -pl capstone exec:java -Dexec.mainClass=com.learning.hft.capstone.vwap.BlendedVwapDemo
 * </pre>
 * Prints throughput and a few blended prices. Uses a per-producer xorshift PRNG (no allocation, no
 * shared RNG contention) so the hot path stays clean.
 */
public final class BlendedVwapDemo {

    private static final int PRODUCERS = 5;
    private static final int PER_PRODUCER = 2_000_000;

    public static void main(String[] args) throws InterruptedException {
        final BlendedVwapEngine engine = new BlendedVwapEngine(1 << 14);
        final long total = (long) PRODUCERS * PER_PRODUCER;

        Thread[] producers = new Thread[PRODUCERS];
        for (int p = 0; p < PRODUCERS; p++) {
            final int bank = p;
            producers[p] = new Thread(() -> produce(engine, bank), "feed-" + p);
        }

        long start = System.nanoTime();
        for (Thread t : producers) {
            t.start();
        }

        // This thread is the single pricing-engine consumer.
        long consumed = 0;
        while (consumed < total) {
            if (engine.processOne()) {
                consumed++;
            } else {
                Thread.onSpinWait();
            }
        }
        long elapsedNanos = System.nanoTime() - start;

        for (Thread t : producers) {
            t.join();
        }

        double perSecond = total / (elapsedNanos / 1_000_000_000.0);
        System.out.printf("processed %,d updates in %.1f ms = %.2f M updates/s%n",
                total, elapsedNanos / 1_000_000.0, perSecond / 1_000_000.0);
        for (int pair = 0; pair < 3; pair++) {
            System.out.printf("  pair %d: VWAP=%.5f  volume=%,d%n",
                    pair, engine.vwap(pair) / 100_000.0, engine.totalVolume(pair));
        }
    }

    private static void produce(BlendedVwapEngine engine, int bank) {
        long seed = 0x9E3779B97F4A7C15L * (bank + 1); // distinct stream per producer
        for (int i = 0; i < PER_PRODUCER; i++) {
            seed ^= seed << 13;
            seed ^= seed >>> 7;
            seed ^= seed << 17; // xorshift64
            long r = seed & Long.MAX_VALUE;

            PriceUpdate u = engine.borrow();
            u.pairId = (int) (r % BlendedVwapEngine.MAX_PAIRS);
            u.price = 100_000 + (r % 5_000);   // ~1.00000..1.05000 scaled by 1e5
            u.volume = 1 + (r % 10);
            engine.publish(u);
        }
    }

    private BlendedVwapDemo() {
    }
}
