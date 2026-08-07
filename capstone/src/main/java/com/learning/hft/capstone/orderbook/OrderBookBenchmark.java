package com.learning.hft.capstone.orderbook;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * POC 1 — throughput of the matching hot path. Each op rests a bid then crosses it with a sell, so the
 * book stays bounded and we measure rest + match + free with zero steady-state allocation.
 *
 * <pre>
 *   mvn -Pbench -pl capstone package
 *   java -jar capstone/target/benchmarks.jar OrderBook -prof gc
 * </pre>
 * Read ops/s (each op = 2 orders) and confirm {@code gc.alloc.rate.norm ≈ 0 B/op}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OrderBookBenchmark {

    private OrderBook book;
    private long id;
    private long sink;

    @Setup(Level.Iteration)
    public void setup() {
        book = new OrderBook(1, 100_000, 1, 1 << 20);
        book.setListener((maker, taker, price, qty, side) -> sink += price);
        id = 0;
    }

    @Benchmark
    public long matchPair() {
        long buyId = ++id;
        long sellId = ++id;
        book.newOrder(buyId, 50_000, 10, OrderBook.BUY);  // rests (no ask)
        book.newOrder(sellId, 50_000, 10, OrderBook.SELL); // fully matches, book empties
        return sink;
    }
}
