package com.learning.hft.nativeinterop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Chapter 7 — the Foreign Function &amp; Memory (FFM) API, aka Project Panama. The sanctioned
 * replacement for BOTH off-heap {@code Unsafe} AND {@code JNI}. PREVIEW in JDK 21, final in JDK 22.
 *
 * <p>Two halves shown:
 * <ol>
 *   <li><b>Foreign Memory</b> — {@link Arena} owns off-heap memory and frees it deterministically;
 *       {@link MemorySegment} is a bounds-checked view. A bad offset is an exception, not a SIGSEGV.</li>
 *   <li><b>Foreign Function</b> — call C's {@code strlen} via a {@link Linker} downcall handle, with
 *       no JNI, no C glue code.</li>
 * </ol>
 *
 * <p>Run (note the required preview flag):
 * <pre>
 *   mvn -q -pl phase7-native-interop package
 *   java --enable-preview -cp phase7-native-interop/target/classes \
 *        com.learning.hft.nativeinterop.ForeignMemoryDemo
 * </pre>
 */
public final class ForeignMemoryDemo {

    public static void main(String[] args) throws Throwable {
        // (1) Off-heap memory, bounds- and lifetime-checked by the Arena.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(8L * Long.BYTES);
            for (int i = 0; i < 8; i++) {
                segment.setAtIndex(ValueLayout.JAVA_LONG, i, (long) i * i);
            }
            long sum = 0;
            for (int i = 0; i < 8; i++) {
                sum += segment.getAtIndex(ValueLayout.JAVA_LONG, i);
            }
            System.out.println("FFM off-heap sum of squares = " + sum);
        } // memory freed here, deterministically

        // (2) Call the C function `size_t strlen(const char*)` with no JNI.
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();
        MethodHandle strlen = linker.downcallHandle(
                stdlib.find("strlen").orElseThrow(() ->
                        new IllegalStateException("strlen not found in default C library")),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cString = arena.allocateUtf8String("hello, panama");
            long len = (long) strlen.invoke(cString);
            System.out.println("C strlen(\"hello, panama\") = " + len);
        }
    }

    private ForeignMemoryDemo() {
    }
}
