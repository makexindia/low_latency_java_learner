package com.learning.hft.capstone.orderbook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    /** Records fills as {maker, taker, price, qty, side}. Allocation here is fine — it's a test. */
    private static final class Capture implements OrderBook.ExecutionListener {
        final List<long[]> fills = new ArrayList<>();

        @Override
        public void onFill(long maker, long taker, long price, long qty, byte side) {
            fills.add(new long[] {maker, taker, price, qty, side});
        }
    }

    private static OrderBook book(Capture c) {
        OrderBook b = new OrderBook(1, 1000, 1, 1024);
        b.setListener(c);
        return b;
    }

    @Test
    void restsThenPartiallyAndFullyFills() {
        Capture c = new Capture();
        OrderBook b = book(c);

        b.newOrder(1, 100, 10, OrderBook.BUY);   // rests
        assertEquals(100, b.bestBid());

        b.newOrder(2, 100, 4, OrderBook.SELL);   // hits id1 for 4
        assertEquals(1, c.fills.size());
        assertArray(c.fills.get(0), 1, 2, 100, 4, OrderBook.SELL);
        assertEquals(100, b.bestBid());          // 6 still resting

        b.newOrder(3, 100, 6, OrderBook.SELL);   // finishes id1
        assertEquals(2, c.fills.size());
        assertArray(c.fills.get(1), 1, 3, 100, 6, OrderBook.SELL);
        assertEquals(Long.MIN_VALUE, b.bestBid()); // book empty on bid side
    }

    @Test
    void honoursTimePriorityWithinAPriceLevel() {
        Capture c = new Capture();
        OrderBook b = book(c);

        b.newOrder(10, 100, 5, OrderBook.BUY);   // first in
        b.newOrder(11, 100, 5, OrderBook.BUY);   // second in
        b.newOrder(12, 100, 5, OrderBook.SELL);  // must hit id10 first

        assertEquals(1, c.fills.size());
        assertEquals(10, c.fills.get(0)[0]);     // maker is the earliest order

        b.newOrder(13, 100, 5, OrderBook.SELL);  // now hits id11
        assertEquals(11, c.fills.get(1)[0]);
    }

    @Test
    void sweepsMultiplePriceLevelsCheapestFirst() {
        Capture c = new Capture();
        OrderBook b = book(c);

        b.newOrder(30, 101, 5, OrderBook.SELL);
        b.newOrder(31, 102, 5, OrderBook.SELL);
        assertEquals(101, b.bestAsk());

        b.newOrder(32, 102, 8, OrderBook.BUY);   // takes 5@101 then 3@102
        assertEquals(2, c.fills.size());
        assertArray(c.fills.get(0), 30, 32, 101, 5, OrderBook.BUY);
        assertArray(c.fills.get(1), 31, 32, 102, 3, OrderBook.BUY);
        assertEquals(102, b.bestAsk());          // id31 has 2 left resting
    }

    @Test
    void cancelRemovesRestingOrder() {
        Capture c = new Capture();
        OrderBook b = book(c);

        b.newOrder(20, 100, 5, OrderBook.BUY);
        assertTrue(b.cancel(20));
        assertFalse(b.cancel(20));               // already gone
        assertEquals(Long.MIN_VALUE, b.bestBid());

        b.newOrder(21, 100, 5, OrderBook.SELL);  // nothing to hit -> rests as ask
        assertEquals(0, c.fills.size());
        assertEquals(100, b.bestAsk());
    }

    private static void assertArray(long[] fill, long maker, long taker, long price, long qty,
                                    byte side) {
        assertEquals(maker, fill[0], "maker");
        assertEquals(taker, fill[1], "taker");
        assertEquals(price, fill[2], "price");
        assertEquals(qty, fill[3], "qty");
        assertEquals(side, (byte) fill[4], "side");
    }
}
