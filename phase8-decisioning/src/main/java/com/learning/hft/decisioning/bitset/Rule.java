package com.learning.hft.decisioning.bitset;

import com.learning.hft.decisioning.Order;

/**
 * A single pre-trade rule: returns true if the order PASSES this rule. Kept as a functional interface
 * for clarity; note that calling 1000s of distinct lambda implementations through one interface site
 * is <b>megamorphic</b> (Ch.5) — production systems compile rules to a monomorphic/branchless form.
 * The bitset engine's value is the O(1) applicability + instant enable/disable, not the dispatch.
 */
@FunctionalInterface
public interface Rule {
    boolean test(Order order);
}
