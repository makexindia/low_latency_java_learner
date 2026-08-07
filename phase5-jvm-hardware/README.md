# Chapter 5 — JVM & Hardware Sandbox

> 📖 **Deep dive (learn):** [`docs/chapters/05-jvm-hardware.md`](../docs/chapters/05-jvm-hardware.md).
> This page is **usage** (how to run the proof).

## What this module proves
The runtime is a tunable machine, and you can *prove* claims about it: Epsilon GC verifies
zero-allocation, ZGC/Shenandoah deliver sub-ms pauses, `@Contended` pads away false sharing
declaratively, and CPU pinning removes jitter.

## The claim → the proof
> *"I ran the hot path under Epsilon GC (a no-op collector); it processed 50M messages with a flat
> heap and zero GC cycles — if a single object had leaked onto the hot path, Epsilon would have
> OOM'd. That's an unforgeable zero-allocation proof."*

## Run it
```bash
mvn -q -pl phase5-jvm-hardware package

# alloc mode OOMs under Epsilon (proves hidden allocation is detectable)
scripts/run-epsilon.sh phase5-jvm-hardware/target/classes com.learning.hft.jvm.EpsilonProof --mode=alloc

# reuse mode survives forever (proves zero allocation)
scripts/run-epsilon.sh phase5-jvm-hardware/target/classes com.learning.hft.jvm.EpsilonProof --mode=reuse

# compare GC pause behaviour across collectors on an allocating workload
scripts/run-gc-compare.sh phase5-jvm-hardware/target/classes com.learning.hft.jvm.EpsilonProof --mode=alloc
```

## The GC flag cheat-sheet
| Collector | Flag | Use for |
|---|---|---|
| Epsilon | `-XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC` | Proving zero allocation (no-op collector) |
| G1 (default) | `-XX:+UseG1GC` | Baseline; throughput-oriented |
| ZGC | `-XX:+UseZGC` | Sub-millisecond pauses, large heaps |
| Shenandoah | `-XX:+UseShenandoahGC` | Sub-millisecond pauses (concurrent compaction) |

Add `-Xlog:gc*` to see pause times; `-Xms == -Xmx` to avoid heap resizing jitter.

## `@Contended` (declarative cache-line padding)
The JVM can pad a hot field onto its own 64-byte line for you — the Chapter 0 fix, without manual
dummy longs:
```java
import jdk.internal.vm.annotation.Contended; // requires the flags/exports below

class Counters {
    @Contended volatile long producerSequence;
    @Contended volatile long consumerSequence;
}
```
Requires at runtime: `-XX:-RestrictContended`, and at compile/run:
`--add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED`.
This is exactly what the Disruptor's `Sequence` and JCTools' counters rely on internally.

## CPU pinning
- Linux blunt tool: `taskset -c 3 java ... ` pins the whole JVM to core 3.
- Per-thread: `net.openhft:affinity` `AffinityLock` (see Chapter 4).
- Best: isolate cores from the scheduler at boot (`isolcpus=`, `nohz_full=`) and pin hot threads there.

## Key file
- [`EpsilonProof.java`](src/main/java/com/learning/hft/jvm/EpsilonProof.java)

## Study checklist
- [ ] Explain why Epsilon OOM = proof of allocation (and why that's *useful*)
- [ ] Read `-Xlog:gc*` output and quote a p99 pause for G1 vs ZGC
- [ ] Add `@Contended` to a two-thread counter and measure the throughput jump
- [ ] Pin with `taskset` and observe reduced latency variance

## TODO / extend
- [ ] JMH `@Contended` benchmark (needs `--add-exports` in `@Fork` jvmArgs)
- [ ] JFR recording script + a JMC walkthrough note
