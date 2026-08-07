package com.learning.hft.benchmarking;

import org.agrona.collections.Int2IntHashMap;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Chapter 1 — quantifies the cost of AUTOBOXING. Enemies exposed: <b>GC pauses + cache misses</b>.
 *
 * <p>{@code HashMap<Integer,Integer>} boxes every {@code int} into an {@code Integer} object and
 * chases pointers through scattered {@code Node} objects. Agrona's {@link Int2IntHashMap} stores
 * primitive keys/values in a flat open-addressed array — no boxing, no node objects, cache-friendly.
 *
 * <p>The point of this module is the <b>method</b>, not just the result: note {@link Blackhole}
 * (defeats dead-code elimination), {@link Setup} (keeps allocation out of the timed loop), the
 * forked JVM, and explicit warm-up. Always run with the GC profiler to see the allocation delta:
 * <pre>
 *   mvn -Pbench -pl phase1-benchmarking package
 *   java -jar phase1-benchmarking/target/benchmarks.jar Boxing -prof gc
 * </pre>
 * Read the {@code gc.alloc.rate.norm} row: the primitive map should report ~0 B/op on lookups.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BoxingBenchmark {

    private static final int N = 1_000;

    private Map<Integer, Integer> boxed;
    private Int2IntHashMap primitive;

    @Setup
    public void setup() {
        boxed = new HashMap<>();
        primitive = new Int2IntHashMap(Integer.MIN_VALUE); // missingValue sentinel
        for (int i = 0; i < N; i++) {
            boxed.put(i, i);
            primitive.put(i, i);
        }
    }

    @Benchmark
    public void boxedLookups(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(boxed.get(i)); // unboxes -> may allocate, definitely pointer-chases
        }
    }

    @Benchmark
    public void primitiveLookups(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(primitive.get(i)); // returns primitive int, no boxing
        }
    }
}
