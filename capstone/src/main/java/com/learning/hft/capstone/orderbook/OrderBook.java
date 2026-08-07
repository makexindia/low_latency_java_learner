package com.learning.hft.capstone.orderbook;

import org.agrona.collections.Long2LongHashMap;

/**
 * POC 1 — Zero-Allocation Limit Order Book (exchange side). <b>Fully implemented.</b>
 *
 * <p>Matches crossing orders by <b>price-time priority</b> and emits fills to an
 * {@link ExecutionListener}, allocating <b>nothing</b> on the hot path after construction.
 * <b>Target to quote:</b> multi-million orders/s single-thread, p99 in the low microseconds
 * (measure with {@link OrderBookBenchmark}).
 *
 * <h2>Design — why it is zero-allocation and cache-friendly</h2>
 * <ul>
 *   <li><b>Price ladder</b> (not a {@code TreeMap}). Prices are scaled integers on a fixed tick
 *       grid, so a price maps to an array index in O(1): {@code idx = (price - minPrice)/tick}.
 *       {@link #bidHead}/{@link #askHead} are arrays indexed by that tick — no red-black-tree nodes,
 *       no allocation, cache-friendly. This is how real matching engines do it (Ch.0 layout).</li>
 *   <li><b>Order pool via parallel arrays</b> (struct-of-arrays). Each resting order is a slot index
 *       into {@link #orderId}/{@link #remaining}/{@link #nextOrder}/{@link #prevOrder}. Time priority
 *       within a level is an array-backed doubly-linked list ({@code next}/{@code prev} are integer
 *       indices, never {@code Node} objects) — the Ch.0 "index-array linked list" trick.</li>
 *   <li><b>Free list</b> ({@link #freeStack}) recycles slots — allocation happens once, at
 *       construction.</li>
 *   <li><b>Order-id → slot</b> map is an Agrona {@link Long2IntHashMap} (primitive, no boxing) for
 *       O(1) cancels (Ch.3).</li>
 * </ul>
 *
 * <p>Not thread-safe by design: a matching engine is single-writer (Ch.2 single-writer principle).
 * Feed it from one thread (e.g. a Disruptor consumer). Production would SBE-encode the fills onto an
 * Aeron channel (Ch.4); here we expose a callback so tests and benchmarks stay pure-JVM.
 */
public final class OrderBook {

    public static final byte BUY = 0;
    public static final byte SELL = 1;

    /** Zero-allocation fill callback. All parameters are primitives — no boxing. */
    @FunctionalInterface
    public interface ExecutionListener {
        void onFill(long makerOrderId, long takerOrderId, long price, long quantity, byte takerSide);
    }

    private static final int NIL = -1;

    private final long minPrice;
    private final long tickSize;
    private final int numTicks;

    // --- order pool (struct-of-arrays), all pre-allocated ---
    private final long[] orderId;
    private final long[] remaining;
    private final int[] priceIdx;
    private final int[] nextOrder;
    private final int[] prevOrder;
    private final byte[] side;

    // --- price ladder: head/tail slot per tick, per side ---
    private final int[] bidHead;
    private final int[] bidTail;
    private final int[] askHead;
    private final int[] askTail;

    // best-price cursors (bids descend from bestBidIdx; asks ascend from bestAskIdx)
    private int bestBidIdx = NIL;      // highest tick with a resting bid, or NIL
    private int bestAskIdx;            // lowest tick with a resting ask, or numTicks (== none)

    // --- free-slot stack ---
    private final int[] freeStack;
    private int freeTop;

    private final Long2LongHashMap idToSlot; // orderId -> slot (slot fits in a long)

    private ExecutionListener listener = (m, t, p, q, s) -> { };

    public OrderBook(long minPrice, long maxPrice, long tickSize, int maxOrders) {
        if (tickSize <= 0 || maxPrice < minPrice || maxOrders <= 0) {
            throw new IllegalArgumentException("bad ladder configuration");
        }
        this.minPrice = minPrice;
        this.tickSize = tickSize;
        this.numTicks = (int) ((maxPrice - minPrice) / tickSize) + 1;
        this.bestAskIdx = numTicks;

        this.orderId = new long[maxOrders];
        this.remaining = new long[maxOrders];
        this.priceIdx = new int[maxOrders];
        this.nextOrder = new int[maxOrders];
        this.prevOrder = new int[maxOrders];
        this.side = new byte[maxOrders];

        this.bidHead = newFilled(numTicks);
        this.bidTail = newFilled(numTicks);
        this.askHead = newFilled(numTicks);
        this.askTail = newFilled(numTicks);

        this.freeStack = new int[maxOrders];
        for (int i = 0; i < maxOrders; i++) {
            freeStack[i] = maxOrders - 1 - i; // pop returns 0,1,2,... first
        }
        this.freeTop = maxOrders;

        this.idToSlot = new Long2LongHashMap(NIL);
    }

    public void setListener(ExecutionListener listener) {
        this.listener = listener;
    }

    /**
     * Submit a limit order. Crosses against the opposite side first (price-time priority); any
     * unfilled remainder rests on the book. Hot path: zero allocation.
     */
    public void newOrder(long id, long price, long quantity, byte orderSide) {
        final int idx = priceToIndex(price);
        long qty = quantity;

        if (orderSide == BUY) {
            // Match against asks priced <= our bid, cheapest first.
            while (qty > 0 && bestAskIdx < numTicks && bestAskIdx <= idx) {
                qty = matchLevel(askHead, askTail, bestAskIdx, id, BUY, qty);
                while (bestAskIdx < numTicks && askHead[bestAskIdx] == NIL) {
                    bestAskIdx++;
                }
            }
            if (qty > 0) {
                rest(bidHead, bidTail, idx, id, qty, BUY);
                if (idx > bestBidIdx) {
                    bestBidIdx = idx;
                }
            }
        } else {
            // Match against bids priced >= our ask, highest first.
            while (qty > 0 && bestBidIdx >= 0 && bestBidIdx >= idx) {
                qty = matchLevel(bidHead, bidTail, bestBidIdx, id, SELL, qty);
                while (bestBidIdx >= 0 && bidHead[bestBidIdx] == NIL) {
                    bestBidIdx--;
                }
            }
            if (qty > 0) {
                rest(askHead, askTail, idx, id, qty, SELL);
                if (idx < bestAskIdx) {
                    bestAskIdx = idx;
                }
            }
        }
    }

    /** Cancel a resting order by id. O(1) unlink. @return true if it was resting. */
    public boolean cancel(long id) {
        final int slot = (int) idToSlot.get(id);
        if (slot == NIL) {
            return false;
        }
        final int idx = priceIdx[slot];
        final byte s = side[slot];
        final int[] head = (s == BUY) ? bidHead : askHead;
        final int[] tail = (s == BUY) ? bidTail : askTail;

        unlink(head, tail, idx, slot);
        idToSlot.remove(id);
        freeSlot(slot);

        if (s == BUY && idx == bestBidIdx) {
            while (bestBidIdx >= 0 && bidHead[bestBidIdx] == NIL) {
                bestBidIdx--;
            }
        } else if (s == SELL && idx == bestAskIdx) {
            while (bestAskIdx < numTicks && askHead[bestAskIdx] == NIL) {
                bestAskIdx++;
            }
        }
        return true;
    }

    /** @return best (highest) bid price, or {@link Long#MIN_VALUE} if no bids. */
    public long bestBid() {
        return bestBidIdx == NIL ? Long.MIN_VALUE : indexToPrice(bestBidIdx);
    }

    /** @return best (lowest) ask price, or {@link Long#MAX_VALUE} if no asks. */
    public long bestAsk() {
        return bestAskIdx == numTicks ? Long.MAX_VALUE : indexToPrice(bestAskIdx);
    }

    // ---------------------------------------------------------------- internals

    /** Fill the taker against the FIFO queue at one price level. @return leftover taker quantity. */
    private long matchLevel(int[] head, int[] tail, int levelIdx, long takerId, byte takerSide,
                            long takerQty) {
        final long price = indexToPrice(levelIdx);
        int maker = head[levelIdx];
        while (maker != NIL && takerQty > 0) {
            long avail = remaining[maker];
            final long fill = Math.min(takerQty, avail);

            listener.onFill(orderId[maker], takerId, price, fill, takerSide);

            takerQty -= fill;
            avail -= fill;
            remaining[maker] = avail;

            if (avail == 0) {
                final int next = nextOrder[maker];
                head[levelIdx] = next;
                if (next != NIL) {
                    prevOrder[next] = NIL;
                } else {
                    tail[levelIdx] = NIL;
                }
                idToSlot.remove(orderId[maker]);
                freeSlot(maker);
                maker = next;
            } else {
                break; // taker exhausted; maker stays at head, partially filled
            }
        }
        return takerQty;
    }

    private void rest(int[] head, int[] tail, int idx, long id, long qty, byte s) {
        final int slot = allocSlot();
        orderId[slot] = id;
        remaining[slot] = qty;
        priceIdx[slot] = idx;
        side[slot] = s;
        nextOrder[slot] = NIL;

        final int t = tail[idx];
        if (t == NIL) {
            head[idx] = slot;
            prevOrder[slot] = NIL;
        } else {
            nextOrder[t] = slot;
            prevOrder[slot] = t;
        }
        tail[idx] = slot;
        idToSlot.put(id, slot);
    }

    private void unlink(int[] head, int[] tail, int idx, int slot) {
        final int p = prevOrder[slot];
        final int n = nextOrder[slot];
        if (p != NIL) {
            nextOrder[p] = n;
        } else {
            head[idx] = n;
        }
        if (n != NIL) {
            prevOrder[n] = p;
        } else {
            tail[idx] = p;
        }
    }

    private int allocSlot() {
        if (freeTop == 0) {
            throw new IllegalStateException("order pool exhausted — size maxOrders higher");
        }
        return freeStack[--freeTop];
    }

    private void freeSlot(int slot) {
        freeStack[freeTop++] = slot;
    }

    private int priceToIndex(long price) {
        if (price < minPrice) {
            throw new IllegalArgumentException("price below ladder min");
        }
        final int idx = (int) ((price - minPrice) / tickSize);
        if (idx >= numTicks) {
            throw new IllegalArgumentException("price above ladder max");
        }
        return idx;
    }

    private long indexToPrice(int idx) {
        return minPrice + (long) idx * tickSize;
    }

    private static int[] newFilled(int n) {
        final int[] a = new int[n];
        java.util.Arrays.fill(a, NIL);
        return a;
    }
}
