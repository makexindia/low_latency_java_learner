package com.learning.hft.zeroalloc;

import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Chapter 3 — encode + decode an order through the {@link OrderFlyweight} and prove ZERO allocation.
 *
 * <p>The buffer and the flyweight are allocated once in {@link #setup()}. The timed method only does
 * offset arithmetic against off-heap-style memory, so {@code -prof gc} should report
 * {@code gc.alloc.rate.norm = 0 B/op}. That "0 B/op" line is the whole point of the chapter.
 *
 * <pre>
 *   mvn -Pbench -pl phase3-zero-alloc package
 *   java -jar phase3-zero-alloc/target/benchmarks.jar Flyweight -prof gc
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FlyweightBenchmark {

    private UnsafeBuffer buffer;
    private OrderFlyweight flyweight;

    @Setup
    public void setup() {
        buffer = new UnsafeBuffer(new byte[64]); // allocated ONCE
        flyweight = new OrderFlyweight();         // allocated ONCE
    }

    @Benchmark
    public long encodeThenDecode() {
        flyweight.wrap(buffer, 0)
                .orderId(1_000_042L)
                .price(101_250L) // scaled integer (e.g. 1.0125 * 10^5)
                .quantity(500)
                .side((byte) 1);

        // Decode back — no new objects, just reads at fixed offsets.
        return flyweight.wrap(buffer, 0).orderId()
                + flyweight.price()
                + flyweight.quantity()
                + flyweight.side();
    }
}
