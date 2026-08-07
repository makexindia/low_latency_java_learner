package com.learning.hft.jvm;

/**
 * Chapter 5 — the Epsilon GC zero-allocation lie detector.
 *
 * <p>Epsilon allocates but NEVER reclaims. So under {@code -XX:+UseEpsilonGC}:
 * <ul>
 *   <li>{@code --mode=alloc} allocates a fresh array every iteration → heap fills → {@code OutOfMemoryError}.
 *       That crash is the <b>evidence of hidden allocation</b>.</li>
 *   <li>{@code --mode=reuse} reuses one pre-allocated array → heap stays flat → runs forever.
 *       Survival is the <b>proof of zero allocation</b>.</li>
 * </ul>
 *
 * <p>Run both under Epsilon and watch the difference:
 * <pre>
 *   mvn -q -pl phase5-jvm-hardware package
 *   ../scripts/run-epsilon.sh phase5-jvm-hardware/target/classes com.learning.hft.jvm.EpsilonProof --mode=alloc
 *   ../scripts/run-epsilon.sh phase5-jvm-hardware/target/classes com.learning.hft.jvm.EpsilonProof --mode=reuse
 * </pre>
 */
public final class EpsilonProof {

    private static final int ITERATIONS = 50_000_000;
    private static final int SIZE = 1_024;

    public static void main(String[] args) {
        final boolean reuse = args.length > 0 && args[0].contains("reuse");
        System.out.println("mode=" + (reuse ? "reuse (should survive)" : "alloc (should OOM under Epsilon)"));

        long checksum = 0;
        byte[] reused = new byte[SIZE]; // allocated ONCE

        for (int i = 0; i < ITERATIONS; i++) {
            final byte[] buffer = reuse ? reused : new byte[SIZE]; // <-- the only difference
            buffer[i & (SIZE - 1)] = (byte) i;
            checksum += buffer[i & (SIZE - 1)];

            if ((i & 0xFFFFF) == 0) {
                System.out.println("iteration " + i + " (heap holding steady if mode=reuse)");
            }
        }
        System.out.println("completed, checksum=" + checksum);
    }

    private EpsilonProof() {
    }
}
