package com.learning.hft.concurrency;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Chapter 2 — the minimal LMAX Disruptor pipeline. Enemy killed: <b>coordination</b>
 * (no locks, no OS context switches) and <b>GC</b> (events are pre-allocated and reused).
 *
 * <p>The {@code RingBuffer} is filled once with reusable {@link LongEvent} instances by the
 * {@code EventFactory} ({@code LongEvent::new}). Publishing does NOT allocate — the producer claims
 * a slot, mutates the existing event in place, and publishes its sequence. Consumers advance their
 * own padded {@code Sequence} counters, so producer/consumer coordinate lock-free.
 *
 * <p>Run:
 * <pre>
 *   mvn -q -pl phase2-concurrency exec:java \
 *       -Dexec.mainClass=com.learning.hft.concurrency.DisruptorDemo
 * </pre>
 *
 * <p>Pattern: <i>pre-allocate &amp; reuse</i> + <i>the CPU/latency dial</i> (the WaitStrategy).
 */
public final class DisruptorDemo {

    /** A reusable, mutable event. Created once per ring slot, never per message. */
    public static final class LongEvent {
        private long value;

        void set(long value) {
            this.value = value;
        }

        long get() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int bufferSize = 1024; // must be a power of two

        // Default WaitStrategy is BlockingWaitStrategy. For lowest latency on a dedicated core,
        // construct with a BusySpinWaitStrategy instead (the CPU/latency dial).
        Disruptor<LongEvent> disruptor =
                new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWith(
                (event, sequence, endOfBatch) ->
                        System.out.printf("consumed value=%d seq=%d endOfBatch=%b%n",
                                event.get(), sequence, endOfBatch));

        RingBuffer<LongEvent> ringBuffer = disruptor.start();

        for (long i = 0; i < 10; i++) {
            long seq = ringBuffer.next();     // claim the next slot
            try {
                ringBuffer.get(seq).set(i);   // mutate the pre-allocated event in place
            } finally {
                ringBuffer.publish(seq);      // make it visible to consumers
            }
        }

        Thread.sleep(200); // let the consumer drain before shutdown
        disruptor.shutdown();
    }

    private DisruptorDemo() {
    }
}
