package com.learning.hft.decisioning;

/**
 * A tiny mutable order carrier the rule engines evaluate. Reused (not allocated per evaluation) on
 * the hot path — prices/quantities are scaled integers (Ch.3), never doubles.
 */
public final class Order {
    public long orderId;
    public int symbolId;
    public long price;     // scaled integer
    public long quantity;
    public long notional;  // price * quantity, scaled
    public byte side;      // 0 = BUY, 1 = SELL

    public Order set(long orderId, int symbolId, long price, long quantity, byte side) {
        this.orderId = orderId;
        this.symbolId = symbolId;
        this.price = price;
        this.quantity = quantity;
        this.notional = price * quantity;
        this.side = side;
        return this;
    }
}
