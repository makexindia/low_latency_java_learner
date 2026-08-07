package com.learning.hft.capstone.vwap;

import com.learning.hft.capstone.vwap.BlendedVwapEngine.PriceUpdate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlendedVwapEngineTest {

    /** Push one update through the REAL borrow→publish→process path (single-threaded). */
    private static void feed(BlendedVwapEngine e, int pair, long price, long volume) {
        PriceUpdate u = e.borrow();
        u.pairId = pair;
        u.price = price;
        u.volume = volume;
        e.publish(u);
        assertTrue(e.processOne());
    }

    @Test
    void blendsVolumeWeightedAverage() {
        BlendedVwapEngine e = new BlendedVwapEngine(16);
        feed(e, 0, 100, 10);   // 100 * 10 = 1000
        feed(e, 0, 200, 30);   // 200 * 30 = 6000
        // VWAP = (1000 + 6000) / (10 + 30) = 7000 / 40 = 175
        assertEquals(175.0, e.vwap(0), 1e-9);
        assertEquals(40, e.totalVolume(0));
    }

    @Test
    void isolatesPairs() {
        BlendedVwapEngine e = new BlendedVwapEngine(16);
        feed(e, 1, 500, 5);
        feed(e, 2, 900, 1);
        assertEquals(500.0, e.vwap(1), 1e-9);
        assertEquals(900.0, e.vwap(2), 1e-9);
        assertTrue(Double.isNaN(e.vwap(3))); // untouched pair
    }

    @Test
    void losesNoMessagesUnderConcurrentProducers() throws InterruptedException {
        final BlendedVwapEngine e = new BlendedVwapEngine(1 << 12);
        final int producers = 4;
        final int perProducer = 50_000;
        final long expectedVolume = (long) producers * perProducer; // every msg has volume 1, pair 0

        Thread[] ts = new Thread[producers];
        for (int p = 0; p < producers; p++) {
            ts[p] = new Thread(() -> {
                for (int i = 0; i < perProducer; i++) {
                    PriceUpdate u = e.borrow();
                    u.pairId = 0;
                    u.price = 100;
                    u.volume = 1;
                    e.publish(u);
                }
            });
        }
        for (Thread t : ts) {
            t.start();
        }

        long consumed = 0;
        long target = (long) producers * perProducer;
        while (consumed < target) {
            if (e.processOne()) {
                consumed++;
            } else {
                Thread.onSpinWait();
            }
        }
        for (Thread t : ts) {
            t.join();
        }

        assertEquals(expectedVolume, e.totalVolume(0)); // no lost or duplicated messages
        assertEquals(100.0, e.vwap(0), 1e-9);
    }
}
