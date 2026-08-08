package com.learning.hft.capstone.rulegateway;

import com.learning.hft.decisioning.dataflow.DataflowGraph;
import org.agrona.DeadlineTimerWheel;

import java.util.concurrent.TimeUnit;

/**
 * Capstone POC 4 (companion) — streaming quote <b>skew that activates at a scheduled start time</b>.
 *
 * <p>Combines the Chapter 8 {@link DataflowGraph} (incremental recompute / dirty propagation) with an
 * Agrona {@link DeadlineTimerWheel} (O(1) scheduler): quotes track the streaming mid, and at the
 * scheduled time the timer flips the skew on — recomputing only the skew and quote nodes.
 */
public final class QuoteSkewEngine {

    private final DataflowGraph g = new DataflowGraph();
    private final int bid;
    private final int ask;
    private final int active;
    private final int mid;
    private final int skew;
    private final int bidQuote;
    private final int askQuote;
    private final DeadlineTimerWheel wheel;
    private boolean skewActive;

    /**
     * @param startTime wheel epoch (same time unit as {@link #advanceTime})
     * @param skewStart time at which the skew turns on
     * @param skewFraction e.g. 0.001 = 10 bps
     */
    public QuoteSkewEngine(long startTime, long skewStart, double skewFraction) {
        bid = g.addInput(0.0);
        ask = g.addInput(0.0);
        active = g.addInput(0.0);
        mid = g.addNode(new int[] {bid, ask}, (v, d) -> (v[d[0]] + v[d[1]]) / 2.0);
        skew = g.addNode(new int[] {mid, active}, (v, d) -> v[d[1]] > 0 ? v[d[0]] * skewFraction : 0.0);
        bidQuote = g.addNode(new int[] {mid, skew}, (v, d) -> v[d[0]] - v[d[1]]);
        askQuote = g.addNode(new int[] {mid, skew}, (v, d) -> v[d[0]] + v[d[1]]);
        g.build();

        wheel = new DeadlineTimerWheel(TimeUnit.MILLISECONDS, startTime, 1, 256);
        wheel.scheduleTimer(skewStart);
    }

    /** Feed a top-of-book update; recomputes incrementally. */
    public void onQuote(double bidPx, double askPx) {
        g.setInput(bid, bidPx);
        g.setInput(ask, askPx);
        g.recompute();
    }

    /** Advance the clock; when it crosses the scheduled time the skew turns on automatically. */
    public void advanceTime(long now) {
        // poll() advances ~one tick per call, so drain ticks up to `now`.
        while (wheel.currentTickTime() < now) {
            wheel.poll(now, (unit, deadline, timerId) -> {
                g.setInput(active, 1.0);
                g.recompute();
                skewActive = true;
                return true;
            }, 4);
        }
    }

    public boolean isSkewActive() {
        return skewActive;
    }

    public double bidQuote() {
        return g.value(bidQuote);
    }

    public double askQuote() {
        return g.value(askQuote);
    }

    public double mid() {
        return g.value(mid);
    }
}
