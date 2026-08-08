package com.learning.hft.decisioning.dataflow;

import org.agrona.DeadlineTimerWheel;

import java.util.concurrent.TimeUnit;

/**
 * Chapter 8 — the "prices skewed starting at a certain time" use case: a quote-skew that <b>activates
 * at a scheduled start time</b> and then recomputes incrementally as prices stream.
 *
 * <p>An Agrona {@link DeadlineTimerWheel} (O(1) schedule/expiry, the standard low-latency scheduler)
 * fires at time T and flips the {@code skewActive} input of a {@link DataflowGraph}; only the skew and
 * quote nodes recompute (dirty propagation). Price ticks before/after show the same incrementality.
 *
 * <pre>
 *   mvn -q -pl phase8-decisioning compile exec:java \
 *       -Dexec.mainClass=com.learning.hft.decisioning.dataflow.ScheduledSkewDemo
 * </pre>
 */
public final class ScheduledSkewDemo {

    public static void main(String[] args) {
        DataflowGraph g = new DataflowGraph();
        int bid = g.addInput(100.0);
        int ask = g.addInput(102.0);
        int active = g.addInput(0.0);
        int mid = g.addNode(new int[] {bid, ask}, (v, d) -> (v[d[0]] + v[d[1]]) / 2.0);
        int skew = g.addNode(new int[] {mid, active}, (v, d) -> v[d[1]] > 0 ? v[d[0]] * 0.001 : 0.0);
        int bidQuote = g.addNode(new int[] {mid, skew}, (v, d) -> v[d[0]] - v[d[1]]);
        int askQuote = g.addNode(new int[] {mid, skew}, (v, d) -> v[d[0]] + v[d[1]]);
        g.build();

        final long startTime = 0;
        final long skewStart = 100; // activate skew at t=100 ms
        DeadlineTimerWheel wheel =
                new DeadlineTimerWheel(TimeUnit.MILLISECONDS, startTime, 1, 256);
        wheel.scheduleTimer(skewStart);

        System.out.printf("t=%3d  bid/ask=%.1f/%.1f  quotes=%.4f/%.4f  (skew off)%n",
                startTime, g.value(bid), g.value(ask), g.value(bidQuote), g.value(askQuote));

        for (long now = 1; now <= 150; now++) {
            // a price tick at t=50 — incremental recompute, skew still off
            if (now == 50) {
                g.setInput(bid, 104.0);
                int n = g.recompute();
                System.out.printf("t=%3d  price tick: recomputed %d nodes  quotes=%.4f/%.4f%n",
                        now, n, g.value(bidQuote), g.value(askQuote));
            }

            wheel.poll(now, (timeUnit, deadline, timerId) -> {
                g.setInput(active, 1.0);
                int n = g.recompute();
                System.out.printf("t=%3d  SKEW ACTIVATED by timer: recomputed %d nodes  quotes=%.4f/%.4f%n",
                        deadline, n, g.value(bidQuote), g.value(askQuote));
                return true;
            }, 4);
        }
    }

    private ScheduledSkewDemo() {
    }
}
