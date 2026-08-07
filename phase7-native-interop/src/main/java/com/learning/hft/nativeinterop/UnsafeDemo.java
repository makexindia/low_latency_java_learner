package com.learning.hft.nativeinterop;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Chapter 7 — {@code sun.misc.Unsafe}: the (deprecated) old foundation of off-heap Java.
 *
 * <p>Shown so you can READ legacy library code (Netty, Agrona, Disruptor all used it). For NEW code,
 * use {@code VarHandle} (on-heap ordered/atomic — see {@link VarHandleDemo}) or the FFM API
 * (off-heap — see {@link ForeignMemoryDemo}). {@code Unsafe} is deprecated for removal (JEP 471/498):
 * a bad address is a JVM crash, not an exception.
 *
 * <p>Run: {@code java --enable-preview -cp target/classes com.learning.hft.nativeinterop.UnsafeDemo}
 */
public final class UnsafeDemo {

    public static void main(String[] args) throws Exception {
        final Unsafe unsafe = obtainUnsafe();

        // Off-heap allocation (malloc/free) — invisible to the GC.
        final int count = 8;
        final long bytes = (long) count * Long.BYTES;
        final long base = unsafe.allocateMemory(bytes);
        try {
            for (int i = 0; i < count; i++) {
                unsafe.putLong(base + (long) i * Long.BYTES, (long) i * i);
            }
            long sum = 0;
            for (int i = 0; i < count; i++) {
                sum += unsafe.getLong(base + (long) i * Long.BYTES);
            }
            System.out.println("Unsafe off-heap sum of squares = " + sum);
        } finally {
            unsafe.freeMemory(base); // manual free — forget this and you leak native memory
        }
    }

    /** The {@code theUnsafe} field is private; libraries grab it via reflection. */
    private static Unsafe obtainUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    private UnsafeDemo() {
    }
}
