package com.learning.hft.capstone.rulegateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteSkewEngineTest {

    @Test
    void skewIsOffBeforeTheScheduledTime() {
        QuoteSkewEngine e = new QuoteSkewEngine(0, 100, 0.001);
        e.onQuote(100.0, 102.0);        // mid = 101
        e.advanceTime(50);              // before skewStart
        assertFalse(e.isSkewActive());
        assertEquals(101.0, e.bidQuote(), 1e-9);
        assertEquals(101.0, e.askQuote(), 1e-9);
    }

    @Test
    void skewActivatesAtTheScheduledTimeAndSkewsQuotes() {
        QuoteSkewEngine e = new QuoteSkewEngine(0, 100, 0.001);
        e.onQuote(100.0, 102.0);        // mid = 101
        e.advanceTime(150);             // crosses skewStart = 100
        assertTrue(e.isSkewActive());
        assertEquals(101.0, e.mid(), 1e-9);
        assertEquals(101.0 - 0.101, e.bidQuote(), 1e-9); // mid - 10bps
        assertEquals(101.0 + 0.101, e.askQuote(), 1e-9);
    }
}
