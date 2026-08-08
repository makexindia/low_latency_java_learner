package com.learning.hft.decisioning.bitset;

import com.learning.hft.decisioning.Order;

/**
 * Chapter 8 — evaluate many pre-trade rules per order with <b>bitwise</b> control. Enemy killed:
 * <b>coordination/latency</b> of a general rules engine.
 *
 * <p>Rules are indexed 0..N-1. Two {@code long[]} bitsets drive evaluation:
 * <ul>
 *   <li><b>enabled</b> — bit i set ⇒ rule i is active. Flip one bit to enable/disable a rule
 *       <b>live, with no restart</b> ({@link #enable}/{@link #disable}).</li>
 *   <li><b>applicable</b> (per call) — bit i set ⇒ rule i applies to this order/instrument
 *       (precompute one mask per instrument).</li>
 * </ul>
 * Evaluation iterates only the {@code enabled & applicable} bits using
 * {@link Long#numberOfTrailingZeros} + clear-lowest-set-bit ({@code bits &= bits - 1}), so disabled
 * or inapplicable rules cost nothing and there is <b>zero allocation</b>. Returns the id of the first
 * failing rule, or {@code -1} if the order passes.
 *
 * <p>This is the hot-path pattern behind "thousands of rules per order under ~100µs" with a
 * single-AND kill switch — see {@code docs/chapters/08-decisioning.md}.
 */
public final class BitsetRuleEngine {

    private final Rule[] rules;
    private final long[] enabled;
    private final int words;

    public BitsetRuleEngine(Rule[] rules) {
        this.rules = rules;
        this.words = (rules.length + 63) >>> 6;
        this.enabled = new long[words];
        enableAll();
    }

    public int ruleCount() {
        return rules.length;
    }

    public int words() {
        return words;
    }

    public void enableAll() {
        for (int w = 0; w < words; w++) {
            enabled[w] = 0L;
        }
        for (int i = 0; i < rules.length; i++) {
            enable(i); // set only bits that correspond to real rules (not the padding of the last word)
        }
    }

    public void enable(int ruleId) {
        enabled[ruleId >>> 6] |= (1L << (ruleId & 63));
    }

    public void disable(int ruleId) {
        enabled[ruleId >>> 6] &= ~(1L << (ruleId & 63));
    }

    public boolean isEnabled(int ruleId) {
        return (enabled[ruleId >>> 6] & (1L << (ruleId & 63))) != 0;
    }

    /** Evaluate all enabled rules (every rule considered applicable). */
    public int evaluate(Order order) {
        for (int w = 0; w < words; w++) {
            long bits = enabled[w];
            final int baseId = w << 6;
            while (bits != 0) {
                final int id = baseId + Long.numberOfTrailingZeros(bits);
                if (!rules[id].test(order)) {
                    return id;
                }
                bits &= bits - 1; // clear lowest set bit
            }
        }
        return -1;
    }

    /**
     * Evaluate only rules that are BOTH enabled and applicable to this order. {@code applicable} is a
     * bitset the same width as {@link #words()} (precompute one per instrument).
     */
    public int evaluate(Order order, long[] applicable) {
        for (int w = 0; w < words; w++) {
            long bits = enabled[w] & applicable[w];
            final int baseId = w << 6;
            while (bits != 0) {
                final int id = baseId + Long.numberOfTrailingZeros(bits);
                if (!rules[id].test(order)) {
                    return id;
                }
                bits &= bits - 1;
            }
        }
        return -1;
    }
}
