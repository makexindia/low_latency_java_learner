package com.learning.hft.benchmarking;

import org.HdrHistogram.Histogram;

/**
 * Chapter 1 — <b>coordinated omission</b>, made visible with HdrHistogram.
 *
 * <p>We simulate a service that normally responds in 1µs but occasionally stalls for 100ms. A naive
 * measurement loop, blocked inside the slow request, records ONE 100ms sample and misses the ~100k
 * requests that should have been sent during the stall — so its tail percentiles look great while
 * users suffered. {@link Histogram#recordValueWithExpectedInterval} synthesizes the omitted samples
 * (100ms, 100ms−1µs, …) and reveals the true tail.
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase1-benchmarking exec:java \
 *       -Dexec.mainClass=com.learning.hft.benchmarking.LatencyHistogramDemo
 * </pre>
 * Compare the two p99.9/max rows: the naive histogram hides the stall; the corrected one exposes it.
 */
public final class LatencyHistogramDemo {

    private static final long EXPECTED_INTERVAL_NANOS = 1_000;      // intended 1µs spacing
    private static final long NORMAL_LATENCY_NANOS = 1_000;         // 1µs
    private static final long STALL_LATENCY_NANOS = 100_000_000L;   // 100ms
    private static final int SAMPLES = 1_000_000;
    private static final int STALL_EVERY = 200_000;

    public static void main(String[] args) {
        Histogram naive = new Histogram(3);      // 3 significant digits, auto-resizing
        Histogram corrected = new Histogram(3);

        for (int i = 0; i < SAMPLES; i++) {
            long latency = (i % STALL_EVERY == 0) ? STALL_LATENCY_NANOS : NORMAL_LATENCY_NANOS;
            naive.recordValue(latency);
            corrected.recordValueWithExpectedInterval(latency, EXPECTED_INTERVAL_NANOS);
        }

        System.out.printf("%-32s %10s %10s %10s %12s%n", "histogram", "p50(us)", "p99(us)",
                "p99.9(us)", "max(us)");
        print("naive (coordinated omission)", naive);
        print("corrected (expected-interval)", corrected);
        System.out.println();
        System.out.println("The naive p99.9 hides the 100ms stalls; the corrected one shows the truth.");
    }

    private static void print(String label, Histogram h) {
        System.out.printf("%-32s %10.1f %10.1f %10.1f %12.1f%n",
                label,
                h.getValueAtPercentile(50.0) / 1_000.0,
                h.getValueAtPercentile(99.0) / 1_000.0,
                h.getValueAtPercentile(99.9) / 1_000.0,
                h.getMaxValue() / 1_000.0);
    }

    private LatencyHistogramDemo() {
    }
}
