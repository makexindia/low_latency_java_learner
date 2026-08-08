package com.learning.hft.decisioning.membership;

import org.roaringbitmap.RoaringBitmap;

import java.util.BitSet;

/**
 * Chapter 8 — set membership for rules like "is this symbol on the restricted list?".
 * Compares a plain {@link BitSet} (dense, hot-path) with a {@link RoaringBitmap} (compressed, large
 * sparse sets — the library "used as the compressed bitset at the core of matching engines").
 *
 * <pre>
 *   mvn -q -pl phase8-decisioning compile exec:java \
 *       -Dexec.mainClass=com.learning.hft.decisioning.membership.SymbolSetDemo
 * </pre>
 */
public final class SymbolSetDemo {

    public static void main(String[] args) {
        // A sparse restricted-symbol set: a few thousand ids scattered across a large id space.
        int universe = 5_000_000;
        int[] restricted = new int[2_000];
        for (int i = 0; i < restricted.length; i++) {
            restricted[i] = (i * 2_711) % universe; // scattered
        }

        BitSet bitSet = new BitSet(universe);
        RoaringBitmap roaring = new RoaringBitmap();
        for (int id : restricted) {
            bitSet.set(id);
            roaring.add(id);
        }

        int probe = restricted[7];
        System.out.println("BitSet.get(" + probe + ")       = " + bitSet.get(probe));
        System.out.println("RoaringBitmap.contains(" + probe + ") = " + roaring.contains(probe));
        System.out.println("RoaringBitmap.contains(42)      = " + roaring.contains(42));

        // Memory: BitSet always allocates universe/8 bytes; Roaring compresses the sparse set.
        long bitSetBytes = (long) universe / 8;
        System.out.printf("%nsparse set of %,d ids over a %,d universe:%n", restricted.length, universe);
        System.out.printf("  BitSet footprint      ~ %,d bytes (fixed = universe/8)%n", bitSetBytes);
        System.out.printf("  RoaringBitmap footprint ~ %,d bytes (compressed)%n", roaring.serializedSizeInBytes());
        System.out.println();
        System.out.println("Rule of thumb: dense/small hot-path flags -> long[]/BitSet;");
        System.out.println("large sparse membership (restricted lists, entitlements) -> RoaringBitmap.");
    }

    private SymbolSetDemo() {
    }
}
