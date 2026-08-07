package com.learning.hft.zeroalloc;

import org.agrona.MutableDirectBuffer;

/**
 * Chapter 3 — a hand-rolled FLYWEIGHT that mirrors what SBE generates from an XML schema.
 * Enemies killed: <b>GC</b> (no object per message) and <b>cache misses</b> (fixed contiguous layout).
 *
 * <p>The flyweight owns no data. It {@link #wrap(MutableDirectBuffer, int) wraps} a raw buffer and
 * reads/writes each field at a fixed byte offset. One flyweight instance decodes millions of
 * messages over its lifetime — strictly zero allocation on the hot path.
 *
 * <p>Fixed layout (21 bytes):
 * <pre>
 *   offset 0  : orderId (long,  8B)
 *   offset 8  : price   (long,  8B)   // scaled integer price, never a double
 *   offset 16 : quantity(int,   4B)
 *   offset 20 : side    (byte,  1B)   // 0 = BUY, 1 = SELL
 * </pre>
 *
 * <p>In production you would not write this by hand — an SBE schema generates encoder/decoder pairs
 * with the same offset-arithmetic mechanics, plus versioning and repeating groups. This class exists
 * so you can see the mechanism with nothing hidden.
 */
public final class OrderFlyweight {

    public static final int ORDER_ID_OFFSET = 0;
    public static final int PRICE_OFFSET = 8;
    public static final int QUANTITY_OFFSET = 16;
    public static final int SIDE_OFFSET = 20;
    public static final int MESSAGE_LENGTH = 21;

    private MutableDirectBuffer buffer;
    private int offset;

    /** Point this flyweight at a region of a buffer. Allocation-free. */
    public OrderFlyweight wrap(final MutableDirectBuffer buffer, final int offset) {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    public OrderFlyweight orderId(final long value) {
        buffer.putLong(offset + ORDER_ID_OFFSET, value);
        return this;
    }

    public long orderId() {
        return buffer.getLong(offset + ORDER_ID_OFFSET);
    }

    public OrderFlyweight price(final long value) {
        buffer.putLong(offset + PRICE_OFFSET, value);
        return this;
    }

    public long price() {
        return buffer.getLong(offset + PRICE_OFFSET);
    }

    public OrderFlyweight quantity(final int value) {
        buffer.putInt(offset + QUANTITY_OFFSET, value);
        return this;
    }

    public int quantity() {
        return buffer.getInt(offset + QUANTITY_OFFSET);
    }

    public OrderFlyweight side(final byte value) {
        buffer.putByte(offset + SIDE_OFFSET, value);
        return this;
    }

    public byte side() {
        return buffer.getByte(offset + SIDE_OFFSET);
    }
}
