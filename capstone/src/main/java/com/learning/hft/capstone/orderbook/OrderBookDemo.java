package com.learning.hft.capstone.orderbook;

/**
 * POC 1 — runnable walkthrough of the order book emitting fills. Zero allocation on the hot path
 * (the listener here prints, which allocates — that's demo output, not the engine).
 *
 * <pre>
 *   mvn -q -pl capstone exec:java -Dexec.mainClass=com.learning.hft.capstone.orderbook.OrderBookDemo
 * </pre>
 */
public final class OrderBookDemo {

    public static void main(String[] args) {
        OrderBook book = new OrderBook(1, 1000, 1, 1024);
        book.setListener((maker, taker, price, qty, side) ->
                System.out.printf("  FILL maker=%d taker=%d price=%d qty=%d takerSide=%s%n",
                        maker, taker, price, qty, side == OrderBook.BUY ? "BUY" : "SELL"));

        System.out.println("resting two bids at 100 (id1 first, then id2) and one at 99 (id3)");
        book.newOrder(1, 100, 5, OrderBook.BUY);
        book.newOrder(2, 100, 5, OrderBook.BUY);
        book.newOrder(3, 99, 5, OrderBook.BUY);
        System.out.println("bestBid=" + book.bestBid() + " bestAsk=" + book.bestAsk());

        System.out.println("incoming SELL 8 @ 100 -> hits id1 (5) then id2 (3), price-time priority");
        book.newOrder(4, 100, 8, OrderBook.SELL);
        System.out.println("bestBid=" + book.bestBid() + " (id2 has 2 left, id3 at 99 behind it)");

        System.out.println("incoming SELL 10 @ 99 -> sweeps id2 (2 @100) then id3 (5 @99), rests 3 @99");
        book.newOrder(5, 99, 10, OrderBook.SELL);
        System.out.println("bestBid=" + book.bestBid() + " bestAsk=" + book.bestAsk());
    }

    private OrderBookDemo() {
    }
}
