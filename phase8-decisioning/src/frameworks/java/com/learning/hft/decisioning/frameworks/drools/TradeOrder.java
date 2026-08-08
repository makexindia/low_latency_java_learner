package com.learning.hft.decisioning.frameworks.drools;

/** A fact the Drools rules match against. JavaBean getters so DRL patterns can read properties. */
public class TradeOrder {
    private final int symbolId;
    private final long quantity;
    private final long notional;
    private String rejectedBy;

    public TradeOrder(int symbolId, long quantity, long notional) {
        this.symbolId = symbolId;
        this.quantity = quantity;
        this.notional = notional;
    }

    public int getSymbolId() {
        return symbolId;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getNotional() {
        return notional;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    /** Called from a rule's consequence when the order violates it. */
    public void reject(String rule) {
        this.rejectedBy = rule;
    }
}
