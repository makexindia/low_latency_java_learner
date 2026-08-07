package com.learning.hft.concurrency;

import org.jctools.queues.MpscArrayQueue;
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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Chapter 2 — per-operation cost of a lock-based queue vs a lock-free JCTools queue.
 * Enemy killed: <b>coordination</b>.
 *
 * <p>{@link ArrayBlockingQueue} guards every offer/poll with a {@code ReentrantLock}; under
 * contention that means kernel-mediated parking. {@link MpscArrayQueue} is lock-free and its fields
 * are cache-line padded (via class-hierarchy layout tricks — read the JCTools source) to avoid
 * false sharing between the producer index and consumer index.
 *
 * <p>This single-threaded offer+poll micro-benchmark isolates the per-op overhead. For the full
 * cross-thread contention story (the "15µs → ~300ns" claim), see the capstone VWAP POC which runs
 * real producer threads against a single consumer.
 *
 * <pre>
 *   mvn -Pbench -pl phase2-concurrency package
 *   java -jar phase2-concurrency/target/benchmarks.jar QueueHandoff -prof gc
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QueueHandoffBenchmark {

    private static final Integer PAYLOAD = 42;

    private ArrayBlockingQueue<Integer> blocking;
    private MpscArrayQueue<Integer> mpsc;

    @Setup
    public void setup() {
        blocking = new ArrayBlockingQueue<>(1024);
        mpsc = new MpscArrayQueue<>(1024);
    }

    @Benchmark
    public void arrayBlockingQueue(Blackhole bh) {
        blocking.offer(PAYLOAD);
        bh.consume(blocking.poll());
    }

    @Benchmark
    public void mpscArrayQueue(Blackhole bh) {
        mpsc.offer(PAYLOAD);
        bh.consume(mpsc.poll());
    }
}
