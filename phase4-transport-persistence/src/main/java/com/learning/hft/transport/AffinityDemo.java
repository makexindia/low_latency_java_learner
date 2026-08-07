package com.learning.hft.transport;

import net.openhft.affinity.AffinityLock;

/**
 * Chapter 4 — pin a hot thread to a dedicated CPU core. Enemy killed: <b>jitter</b>.
 *
 * <p>By default the OS scheduler is free to migrate a thread between cores (cold caches on arrival)
 * and to preempt it (latency spikes). {@link AffinityLock} pins the current thread to an isolated
 * core so it keeps its warm L1/L2 caches and runs uninterrupted. Pair with kernel isolation
 * (Linux {@code isolcpus=}) for the full effect.
 *
 * <p>On Linux the equivalent blunt instrument is {@code taskset -c 3 java ...}.
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase4-transport-persistence exec:java \
 *       -Dexec.mainClass=com.learning.hft.transport.AffinityDemo
 * </pre>
 */
public final class AffinityDemo {

    public static void main(String[] args) {
        try (AffinityLock lock = AffinityLock.acquireLock()) {
            System.out.println("Pinned hot thread to CPU " + lock.cpuId());
            // A real event loop would busy-spin here polling a queue / subscription.
            long sum = 0;
            for (int i = 0; i < 1_000_000; i++) {
                sum += i;
            }
            System.out.println("work done, sum=" + sum);
        }
    }

    private AffinityDemo() {
    }
}
