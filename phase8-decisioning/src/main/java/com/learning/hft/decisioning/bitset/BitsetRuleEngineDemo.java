package com.learning.hft.decisioning.bitset;

import com.learning.hft.decisioning.Order;

/**
 * Chapter 8 — runnable demo of bitset rule evaluation + live enable/disable.
 *
 * <pre>
 *   mvn -q -pl phase8-decisioning compile exec:java \
 *       -Dexec.mainClass=com.learning.hft.decisioning.bitset.BitsetRuleEngineDemo
 * </pre>
 */
public final class BitsetRuleEngineDemo {

    // Rule ids (index into the engine's rule array).
    private static final int FAT_FINGER_PRICE = 0;
    private static final int MAX_QUANTITY = 1;
    private static final int MAX_NOTIONAL = 2;
    private static final int KILL_SWITCH = 3;
    private static final String[] NAMES = {"FAT_FINGER_PRICE", "MAX_QUANTITY", "MAX_NOTIONAL", "KILL_SWITCH"};

    public static void main(String[] args) {
        Rule[] rules = new Rule[4];
        rules[FAT_FINGER_PRICE] = o -> o.price >= 90_000 && o.price <= 110_000; // price band
        rules[MAX_QUANTITY] = o -> o.quantity <= 10_000;
        rules[MAX_NOTIONAL] = o -> o.notional <= 1_000_000_000L;
        rules[KILL_SWITCH] = o -> true; // passes normally; disable-the-market by making it fail

        BitsetRuleEngine engine = new BitsetRuleEngine(rules);
        Order order = new Order().set(1, 42, 95_000, 10_400, (byte) 0); // qty over the 10k cap, notional < cap

        int failed = engine.evaluate(order);
        System.out.println("order qty=10400 -> " + verdict(failed)); // fails MAX_QUANTITY

        System.out.println("operator disables MAX_QUANTITY live (one bit flip)...");
        engine.disable(MAX_QUANTITY);
        System.out.println("same order          -> " + verdict(engine.evaluate(order))); // now passes

        System.out.println("re-enable MAX_QUANTITY...");
        engine.enable(MAX_QUANTITY);
        System.out.println("same order          -> " + verdict(engine.evaluate(order))); // fails again
    }

    private static String verdict(int failedRuleId) {
        return failedRuleId < 0 ? "ACCEPTED" : "REJECTED by " + NAMES[failedRuleId];
    }

    private BitsetRuleEngineDemo() {
    }
}
