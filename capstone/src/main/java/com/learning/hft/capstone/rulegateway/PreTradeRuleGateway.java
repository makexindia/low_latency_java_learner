package com.learning.hft.capstone.rulegateway;

import com.learning.hft.capstone.riskgateway.MmapJournal;
import com.learning.hft.decisioning.Order;
import com.learning.hft.decisioning.bitset.BitsetRuleEngine;
import com.learning.hft.decisioning.bitset.Rule;
import org.roaringbitmap.RoaringBitmap;

/**
 * Capstone POC 4 — Pre-Trade Rule Gateway. <b>Implemented.</b>
 *
 * <p>Answers "evaluate many rules per trade request, with quick enable/disable" (Chapter 8): a
 * {@link BitsetRuleEngine} runs a set of pre-trade rules per order; a {@link RoaringBitmap} holds the
 * (potentially large, sparse) restricted-symbol list; a global <b>kill switch</b> and per-rule
 * <b>enable/disable</b> take effect live with no restart. Accepted orders are journaled durably via
 * the existing {@link MmapJournal} (POC 3 / Ch.4 mmap). Single-writer by design (Ch.2).
 *
 * <p>Companion {@link QuoteSkewEngine} covers the streaming "skew from a start time" half of the use
 * case with a scheduled dataflow recompute.
 */
public final class PreTradeRuleGateway {

    public static final int KILL_SWITCH = 0;
    public static final int FAT_FINGER_PRICE = 1;
    public static final int MAX_QUANTITY = 2;
    public static final int MAX_NOTIONAL = 3;
    public static final int RESTRICTED_SYMBOL = 4;

    private final BitsetRuleEngine engine;
    private final RoaringBitmap restricted = new RoaringBitmap();
    private final MmapJournal journal;
    private final Order scratch = new Order(); // reused — zero allocation on the hot path
    private boolean killEngaged;
    private long seq;

    public PreTradeRuleGateway(long minPrice, long maxPrice, long maxQuantity, long maxNotional,
                               MmapJournal journal) {
        this.journal = journal;
        Rule[] rules = new Rule[5];
        rules[KILL_SWITCH] = o -> !killEngaged;                                  // fails when engaged
        rules[FAT_FINGER_PRICE] = o -> o.price >= minPrice && o.price <= maxPrice;
        rules[MAX_QUANTITY] = o -> o.quantity <= maxQuantity;
        rules[MAX_NOTIONAL] = o -> o.notional <= maxNotional;
        rules[RESTRICTED_SYMBOL] = o -> !restricted.contains(o.symbolId);
        this.engine = new BitsetRuleEngine(rules);
    }

    /** Mark a symbol as restricted (no trading). */
    public void restrict(int symbolId) {
        restricted.add(symbolId);
    }

    /** Engage/disengage the global kill switch (live). */
    public void setKillSwitch(boolean engaged) {
        this.killEngaged = engaged;
    }

    /** Turn an individual rule on/off live (no restart). */
    public void enableRule(int ruleId) {
        engine.enable(ruleId);
    }

    public void disableRule(int ruleId) {
        engine.disable(ruleId);
    }

    /**
     * Evaluate all enabled pre-trade rules for an order. If it passes, journal it durably.
     *
     * @return -1 if accepted (and journaled), else the id of the first failing rule.
     */
    public int check(long orderId, int symbolId, long price, long quantity, byte side) {
        scratch.set(orderId, symbolId, price, quantity, side);
        int failed = engine.evaluate(scratch);
        if (failed < 0) {
            journal.append(orderId, scratch.notional, ++seq); // persist before it proceeds
        }
        return failed;
    }
}
