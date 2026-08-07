package com.learning.hft.nativeinterop;

/**
 * Chapter 7 — JNI reference (the classic native bridge). The Java side compiles fine without the
 * native library; it only needs the {@code .so}/{@code .dll} at RUNTIME. Building that library needs
 * a C toolchain, so the full C source + build commands live in {@code native/} and the module README
 * — this repo does not build JNI in the Maven reactor (that's an environment concern, not a learning
 * gap).
 *
 * <p>Why JNI is avoided on the hot path (see the deep dive): each call is a managed↔native state
 * transition the JIT cannot inline across; critical regions can block the GC; a crash in C crashes the
 * JVM. Prefer FFM ({@link ForeignMemoryDemo}) or JNR-FFI ({@link JnrExample}) for new code.
 */
public final class JniReference {

    /** Implemented in C (see native/strlen.c). */
    public native long strlen(String s);

    public static void main(String[] args) {
        try {
            System.loadLibrary("jnistrlen"); // looks for libjnistrlen.so / jnistrlen.dll on java.library.path
            long len = new JniReference().strlen("hello, jni");
            System.out.println("JNI strlen = " + len);
        } catch (UnsatisfiedLinkError e) {
            System.out.println("JNI library not built. See native/README.md for the "
                    + "javac -h + cc build steps, then rerun with -Djava.library.path=native.");
        }
    }
}
