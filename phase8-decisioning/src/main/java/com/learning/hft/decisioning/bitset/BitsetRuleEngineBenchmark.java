package com.learning.hft.decisioning.bitset;

import com.learning.hft.decisioning.Order;
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
 * Chapter 8 — cost of evaluating N pre-trade rules per order, and the (near-zero) effect of disabling
 * rules via the bitset. Run with the GC profiler to confirm zero allocation:
 * <pre>
 *   mvn -Pbench -pl phase8-decisioning package
 *   java -jar phase8-decisioning/target/benchmarks.jar Bitset -prof gc
 * </pre>
 * Compare {@code allEnabled} vs {@code halfDisabled}: disabled rules are skipped by the bit iteration,
 * so throughput scales with the number of ENABLED rules, not the total.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BitsetRuleEngineBenchmark {

    private static final int RULES = 1_000;

    private BitsetRuleEngine allEnabled;
    private BitsetRuleEngine halfDisabled;
    private Order order;

    @Setup
    public void setup() {
        allEnabled = new BitsetRuleEngine(buildRules());
        halfDisabled = new BitsetRuleEngine(buildRules());
        for (int i = 0; i < RULES; i += 2) {
            halfDisabled.disable(i); // turn off every other rule
        }
        order = new Order().set(1, 7, 100_000, 100, (byte) 0);
    }

    private static Rule[] buildRules() {
        Rule[] rules = new Rule[RULES];
        for (int i = 0; i < RULES; i++) {
            final long threshold = 1_000_000L + i; // each rule a distinct sanity bound
            rules[i] = o -> o.notional <= threshold;
        }
        return rules;
    }

    @Benchmark
    public int allEnabled() {
        return allEnabled.evaluate(order);
    }

    @Benchmark
    public int halfDisabled() {
        return halfDisabled.evaluate(order);
    }
}
