package com.learning.hft.foundations;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Chapter 0 — proves FALSE SHARING. Enemy killed: <b>cache misses</b>.
 *
 * <p>Two threads each hammer their own {@code volatile long}. In {@link Unpadded} the two counters
 * sit on the SAME 64-byte cache line, so every write on one core invalidates the other core's copy
 * (MESI ping-pong). In {@link Padded} we insert 7 dummy longs (56 bytes) between them, pushing each
 * counter onto its own cache line — the contention vanishes.
 *
 * <p>Run:
 * <pre>
 *   mvn -Pbench -pl phase0-foundations package
 *   java -jar phase0-foundations/target/benchmarks.jar FalseSharing
 * </pre>
 * Expect the {@code padded} group to out-throughput {@code unpadded} by ~2–10x depending on CPU.
 *
 * <p>Pattern: <i>layout to beat the cache</i> (keep contended data apart). See
 * {@code docs/recurring-patterns.md}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FalseSharingBenchmark {

    /** Both counters land on one cache line → they fight. */
    @State(Scope.Group)
    public static class Unpadded {
        volatile long a;
        volatile long b;
    }

    /** 7 * 8 = 56 bytes of padding force {@code a} and {@code b} onto separate 64-byte lines. */
    @State(Scope.Group)
    public static class Padded {
        volatile long a;
        @SuppressWarnings("unused")
        long p1, p2, p3, p4, p5, p6, p7;
        volatile long b;
    }

    @Benchmark
    @Group("unpadded")
    @GroupThreads(1)
    public long unpaddedWriteA(Unpadded s) {
        return s.a++;
    }

    @Benchmark
    @Group("unpadded")
    @GroupThreads(1)
    public long unpaddedWriteB(Unpadded s) {
        return s.b++;
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(1)
    public long paddedWriteA(Padded s) {
        return s.a++;
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(1)
    public long paddedWriteB(Padded s) {
        return s.b++;
    }
}
