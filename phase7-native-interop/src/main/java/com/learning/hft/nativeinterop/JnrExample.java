package com.learning.hft.nativeinterop;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Platform;

/**
 * Chapter 7 — JNR-FFI: call a native C library by declaring a Java interface — no C glue code, no
 * header generation. JNR builds the trampolines at runtime (libffi). The pragmatic pre-Panama choice
 * for "I just need to call a few C functions"; still widely used (e.g. jRuby).
 *
 * <p>Here we bind the platform C library and call {@code size_t strlen(const char*)}, which exists on
 * both libc (Linux/macOS) and msvcrt (Windows), so the demo is cross-platform.
 *
 * <p>Run: {@code java --enable-preview -cp <cp> com.learning.hft.nativeinterop.JnrExample}
 * (the {@code --enable-preview} is only because this module also holds the FFM demo; JNR itself is
 * not preview).
 */
public final class JnrExample {

    /** Declare the native functions you want; JNR maps names + marshals arguments. */
    public interface LibC {
        long strlen(String s);
        // NOTE: getpid is `getpid` on libc but `_getpid` on msvcrt — a good example of why you keep
        // platform-specific symbol names out of a portable interface.
    }

    public static void main(String[] args) {
        final String cLib = Platform.getNativePlatform().getStandardCLibraryName();
        final LibC libc = LibraryLoader.create(LibC.class).load(cLib);

        final String s = "hello, jnr";
        final long len = libc.strlen(s);
        System.out.println("loaded C library: " + cLib);
        System.out.println("C strlen(\"" + s + "\") = " + len + " (java length = " + s.length() + ")");
    }

    private JnrExample() {
    }
}
