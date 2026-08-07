# Chapter 0 — Foundations

> 📖 **Deep dive (learn):** [`docs/chapters/00-foundations.md`](../docs/chapters/00-foundations.md).
> This page is **usage** (how to run the proof).

## What this module proves
False sharing is real and measurable. Two threads writing to two adjacent `volatile long`s run
several times slower than the same two threads writing to longs on separate cache lines — the only
difference is 56 bytes of padding.

## The claim → the proof
> *"Placing two hot counters on the same 64-byte cache line caused cross-core MESI invalidation; I
> measured a Nx throughput loss vs the padded layout — pure false sharing, no logic change."*

## Run it
```bash
mvn -Pbench -pl phase0-foundations package
java -jar phase0-foundations/target/benchmarks.jar FalseSharing
```
Compare the `padded` vs `unpadded` group throughput. The gap is your number.

## Key file
- [`FalseSharingBenchmark.java`](src/main/java/com/learning/hft/foundations/FalseSharingBenchmark.java)

## Study checklist
- [ ] Explain why `volatile` writes trigger cache-coherence traffic
- [ ] Explain why 56 bytes (not 64) is enough padding here
- [ ] Predict what `@Contended` (Chapter 5) would do to the `Unpadded` class
- [ ] Relate this to Disruptor `Sequence` and JCTools counters (Chapter 2)

## TODO / extend
- [ ] Add a struct-of-arrays vs array-of-structs traversal benchmark (spatial locality)
- [ ] Add a `VarHandle` example to contrast with `volatile`
