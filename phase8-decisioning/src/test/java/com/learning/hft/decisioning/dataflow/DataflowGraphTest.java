package com.learning.hft.decisioning.dataflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataflowGraphTest {

    /**
     * Graph:  mid = (bid+ask)/2 ; skew = active>0 ? mid*0.001 : 0 ; bidQuote = mid-skew ;
     *         askQuote = mid+skew ;   plus an INDEPENDENT branch  otherDerived = other*2.
     */
    private DataflowGraph graph;
    private int bid, ask, active, other, mid, skew, bidQuote, askQuote, otherDerived;

    private void buildGraph() {
        graph = new DataflowGraph();
        bid = graph.addInput(100.0);
        ask = graph.addInput(102.0);
        active = graph.addInput(0.0);
        other = graph.addInput(5.0);

        mid = graph.addNode(new int[] {bid, ask}, (v, d) -> (v[d[0]] + v[d[1]]) / 2.0);
        skew = graph.addNode(new int[] {mid, active}, (v, d) -> v[d[1]] > 0 ? v[d[0]] * 0.001 : 0.0);
        bidQuote = graph.addNode(new int[] {mid, skew}, (v, d) -> v[d[0]] - v[d[1]]);
        askQuote = graph.addNode(new int[] {mid, skew}, (v, d) -> v[d[0]] + v[d[1]]);
        otherDerived = graph.addNode(new int[] {other}, (v, d) -> v[d[0]] * 2.0);
        graph.build();
    }

    @Test
    void initialValuesAreConsistent() {
        buildGraph();
        assertEquals(101.0, graph.value(mid), 1e-9);       // (100+102)/2
        assertEquals(0.0, graph.value(skew), 1e-9);        // inactive
        assertEquals(101.0, graph.value(bidQuote), 1e-9);
        assertEquals(10.0, graph.value(otherDerived), 1e-9);
    }

    @Test
    void priceTickRecomputesOnlyDownstreamOfThatPrice() {
        buildGraph();
        graph.setInput(bid, 104.0);                 // mid, skew, bidQuote, askQuote depend on bid
        int recomputed = graph.recompute();
        assertEquals(4, recomputed);                // NOT otherDerived (independent branch)
        assertEquals(103.0, graph.value(mid), 1e-9); // (104+102)/2
        assertEquals(10.0, graph.value(otherDerived), 1e-9); // untouched
    }

    @Test
    void skewActivationRecomputesOnlySkewAndQuotes() {
        buildGraph();
        graph.setInput(active, 1.0);                // flip skew on (as a timer would at start time T)
        int recomputed = graph.recompute();
        assertEquals(3, recomputed);                // skew, bidQuote, askQuote — NOT mid
        assertEquals(0.101, graph.value(skew), 1e-9); // 101 * 0.001
        assertEquals(101.0 - 0.101, graph.value(bidQuote), 1e-9);
    }

    @Test
    void unchangedInputDoesNoWork() {
        buildGraph();
        graph.setInput(bid, 100.0);                 // same value -> not dirty
        assertEquals(0, graph.recompute());
    }
}
