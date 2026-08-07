package com.learning.hft.nativeinterop;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Chapter 7 — {@code VarHandle}: the sanctioned successor to {@code Unsafe} for on-heap ordered and
 * atomic field/array access. It exposes the full access-mode ladder (plain / opaque / acquire-release
 * / volatile / CAS) and the JIT intrinsifies each to the same instructions Unsafe used — but safely.
 *
 * <p>{@code setRelease} + {@code getAcquire} is THE lock-free publish idiom (Ch.2): publish the
 * payload, then release-store a ready flag; the reader acquire-loads the flag and is guaranteed to
 * see the payload — without a full volatile barrier.
 *
 * <p>Run: {@code java --enable-preview -cp target/classes com.learning.hft.nativeinterop.VarHandleDemo}
 */
public final class VarHandleDemo {

    private long payload;   // the data
    private volatile long ready; // the flag (accessed via VarHandle with weaker modes)

    private static final VarHandle PAYLOAD;
    private static final VarHandle READY;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PAYLOAD = l.findVarHandle(VarHandleDemo.class, "payload", long.class);
            READY = l.findVarHandle(VarHandleDemo.class, "ready", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) {
        VarHandleDemo d = new VarHandleDemo();

        // Publish idiom: plain write of payload, then RELEASE store of the flag.
        PAYLOAD.set(d, 42L);
        READY.setRelease(d, 1L);

        // Consume idiom: ACQUIRE load of the flag; if set, payload write is visible.
        if ((long) READY.getAcquire(d) == 1L) {
            System.out.println("published payload = " + (long) PAYLOAD.get(d));
        }

        // Atomic read-modify-write (replaces Unsafe CAS / AtomicLong).
        boolean won = READY.compareAndSet(d, 1L, 2L);
        long after = (long) READY.getAndAdd(d, 10L);
        System.out.println("CAS 1->2 succeeded=" + won + ", getAndAdd returned " + after
                + ", now = " + (long) READY.getVolatile(d));
    }
}
