package com.learning.hft.decisioning.bitset;

import com.learning.hft.decisioning.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitsetRuleEngineTest {

    private static final int PRICE_BAND = 0;
    private static final int MAX_QTY = 1;
    private static final int MAX_NOTIONAL = 2;

    private static BitsetRuleEngine newEngine() {
        Rule[] rules = new Rule[3];
        rules[PRICE_BAND] = o -> o.price >= 90_000 && o.price <= 110_000;
        rules[MAX_QTY] = o -> o.quantity <= 10_000;
        rules[MAX_NOTIONAL] = o -> o.notional <= 1_000_000_000L;
        return new BitsetRuleEngine(rules);
    }

    @Test
    void passingOrderReturnsMinusOne() {
        BitsetRuleEngine e = newEngine();
        Order ok = new Order().set(1, 1, 100_000, 100, (byte) 0);
        assertEquals(-1, e.evaluate(ok));
    }

    @Test
    void reportsFirstFailingRule() {
        BitsetRuleEngine e = newEngine();
        Order badQty = new Order().set(1, 1, 90_000, 10_001, (byte) 0); // over qty cap
        assertEquals(MAX_QTY, e.evaluate(badQty));
    }

    @Test
    void disableFlipsTheDecisionLive() {
        BitsetRuleEngine e = newEngine();
        Order badQty = new Order().set(1, 1, 90_000, 10_001, (byte) 0);
        assertEquals(MAX_QTY, e.evaluate(badQty));

        e.disable(MAX_QTY);                 // operator turns the rule off, no restart
        assertEquals(-1, e.evaluate(badQty)); // now passes

        e.enable(MAX_QTY);                  // turn it back on
        assertEquals(MAX_QTY, e.evaluate(badQty));
    }

    @Test
    void applicableMaskExcludesRules() {
        BitsetRuleEngine e = newEngine();
        Order badQty = new Order().set(1, 1, 90_000, 10_001, (byte) 0);
        // Applicable mask with only PRICE_BAND and MAX_NOTIONAL set (MAX_QTY not applicable here).
        long[] applicable = new long[e.words()];
        applicable[0] = (1L << PRICE_BAND) | (1L << MAX_NOTIONAL);
        assertEquals(-1, e.evaluate(badQty, applicable)); // qty rule skipped -> passes
    }
}
