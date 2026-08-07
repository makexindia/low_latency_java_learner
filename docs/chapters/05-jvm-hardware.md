# Chapter 5 — JVM & Hardware: GC, JIT, Safepoints, and Proving It

> **Proof module:** [`phase5-jvm-hardware`](../../phase5-jvm-hardware) ·
> **Enemy:** jitter (GC pauses, JIT deopt, safepoints, scheduling).

This is the chapter that separates "I wrote fast code" from "I proved the runtime does what I claim".
You must understand the GC, the JIT, and safepoints well enough to *tune and verify* them.

---

## 1. Allocation and the GC — the enemy you design around

### How allocation actually works: the TLAB
Each thread has a **Thread-Local Allocation Buffer** — a chunk of Eden it owns. `new` is usually just
a **pointer bump** in the TLAB (a few instructions, no locking). Cheap *per allocation*. The cost is
**downstream**: every allocated object must eventually be traced (marked live or dead) and reclaimed.
So the metric that matters is **allocation rate** (bytes/sec), because it sets **GC frequency**. High
allocation → frequent collections → pauses → tail-latency spikes. Hence: zero allocation on the hot
path.

### Escape analysis & scalar replacement (the JIT's gift)
C2 performs **escape analysis**: if it can prove an object never escapes a method, it can **scalar
replace** it — allocate its fields in registers/stack, never on the heap. This is why *some* allocations
are effectively free. But it's fragile: it fails across virtual calls it can't inline, when the object
is stored in a field, or when the method is too big to analyze. **Never rely on it for correctness of
your zero-alloc claim** — verify with `-prof gc` / Epsilon instead.

### Generational hypothesis and regions
Most objects die young. Collectors exploit this with a **young generation** (Eden + survivor spaces)
collected frequently and cheaply, and an **old generation** for survivors. **Humongous** objects
(larger than a region, in G1) are allocated specially and are expensive — avoid giant arrays on the
hot path.

### The collectors — internals and when to use each
| Collector | Model | Pause profile | Use |
|---|---|---|---|
| **Epsilon** | allocates, **never reclaims** | none (until OOM) | **proving zero-alloc**; short-lived batch |
| **G1** (default) | region-based, incremental compaction | tens of ms target (`MaxGCPauseMillis`) | general server default |
| **ZGC** | concurrent, **colored pointers + load barriers** | **sub-millisecond**, heap-size-independent | large heaps, low pause |
| **Shenandoah** | concurrent, **Brooks/load-reference barriers** | sub-millisecond | low pause, smaller heaps too |

**How ZGC hits sub-ms pauses:** it does almost all work (marking, relocation) **concurrently** with
your application. It stores metadata in unused bits of the pointer ("**colored pointers**") and uses a
**load barrier** — a few instructions injected on every reference load — to lazily fix up pointers to
relocated objects. You pay a small, *constant* throughput tax on loads in exchange for pauses that
don't grow with heap size. Shenandoah achieves similar via a forwarding-pointer/load-reference barrier.

**Epsilon as a lie detector:** it *allocates but never collects*. Run your "zero-alloc" hot path under
`-XX:+UseEpsilonGC`; if you actually allocate, the heap fills and you get `OutOfMemoryError`. Survival
across millions of messages is unforgeable proof. See
[`EpsilonProof`](../../phase5-jvm-hardware/src/main/java/com/learning/hft/jvm/EpsilonProof.java) and
[`../../scripts/run-epsilon.sh`](../../scripts/run-epsilon.sh) — the `alloc` mode OOMs, the `reuse`
mode runs forever.

### GC tuning knobs that matter
- `-Xms == -Xmx` — fix the heap so it never resizes (resizing = jitter).
- `-XX:+AlwaysPreTouch` — fault in all heap pages at startup, not lazily during the first run.
- `-Xlog:gc*` — see every pause and its cause.
- Large heaps + ZGC/Shenandoah for low pause; small footprint + Epsilon for zero-alloc proofs.

---

## 2. The JIT — warm-up, tiers, inlining, deopt

The JVM starts **interpreting** bytecode, then compiles hot methods:

![diagram](./diagrams/05-jvm-hardware-1.svg)

- **Tiered compilation**: C1 compiles quickly and inserts profiling counters; once a method is proven
  hot with good profile data, C2 recompiles it with aggressive optimizations. This is why **warm-up**
  matters and why JMH exists (Ch.1).
- **Inlining** is the mother optimization: it removes call overhead *and* exposes the callee's code to
  escape analysis, constant folding, etc. Small, monomorphic methods inline; large or
  **megamorphic** (many implementations at a call site) ones don't.
- **Monomorphic vs megamorphic dispatch:** a call site that only ever sees one concrete type is
  **monomorphic** — C2 inlines it and even devirtualizes. See two types → **bimorphic** (still
  cheap). See many → **megamorphic** — an expensive vtable lookup that blocks inlining. Practical
  consequence: prefer `final` classes and avoid passing many different lambda/impl types through one
  hot interface call site.
- **Deoptimization**: C2 compiles under *speculative assumptions* (e.g. "this call site is
  monomorphic", "this branch is never taken", "this class isn't loaded yet"). If an assumption breaks
  at runtime, the JVM **deoptimizes** — throws away the compiled code and falls back to the
  interpreter, then recompiles. A deopt storm is a latency cliff. Diagnose with
  `-XX:+PrintCompilation` and `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining`.
- **On-Stack Replacement (OSR)**: compiling a long-running loop mid-flight; the OSR-compiled shape can
  differ from a normal call, another reason to trust JMH over hand-rolled loops.

---

## 3. Safepoints — the hidden global pauses

A **safepoint** is a point where the JVM can stop **all** application threads to do work that needs a
consistent view of the heap: GC, biased-lock revocation (pre-JDK15), deoptimization, thread dumps,
`Object.hashCode` bias, class redefinition, etc. Each thread polls a safepoint flag at method returns
and loop back-edges; when the JVM requests a safepoint, it waits for **every** thread to reach one —
this is **"time to safepoint" (TTSP)**.

Why you care:
- A single thread slow to reach a safepoint (e.g. a huge counted loop with elided polls) stalls
  **all** threads → a global pause that isn't a GC pause and won't show in GC logs.
- This is also the mechanism behind **safepoint bias** in naive profilers (Ch.1).
- Diagnose with `-Xlog:safepoint` and JFR's safepoint events. Keep hot loops from monopolizing a
  thread; avoid pathological `System.gc()`; be aware `-XX:+UseCountedLoopSafepoints` trades a poll in
  long loops for lower TTSP.

Related: **biased locking** (an old optimization for uncontended locks) was **removed in JDK 15+**
because its revocation caused safepoints and it hurt modern workloads — don't design around it.

---

## 4. `@Contended` — declarative cache-line padding

Ch.0's false-sharing fix, but done by the JVM. Annotate a hot, independently-written field:
```java
import jdk.internal.vm.annotation.Contended;   // internal — needs --add-exports
class Sequences {
    @Contended volatile long producer;
    @Contended volatile long consumer;
}
```
The JVM pads the field onto its own cache line (default 128 bytes to also dodge adjacent-line
prefetchers). Requires `-XX:-RestrictContended` at runtime and, since it's a JDK-internal annotation,
`--add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED` to compile against it. This is exactly
what the Disruptor's `Sequence` and JCTools' counters use internally (Ch.2). The module README shows a
runnable variant.

---

## 5. Mechanical sympathy at the OS/CPU edge (bridge to Ch.6)

Even a perfectly tuned JVM jitters if the OS or CPU interferes:
- **CPU pinning** (`taskset`, `isolcpus`, `AffinityLock`) keeps a hot thread on one isolated core.
- **C-states / frequency scaling**: a core that idles drops into a deep C-state and takes microseconds
  to wake; set the governor to `performance` and disable deep C-states for latency-critical cores.
- **Transparent Huge Pages (THP)**: 2 MB pages reduce TLB misses but THP's background defrag can cause
  jitter — many shops disable THP and use explicit huge pages instead.
- **NUMA**: memory attached to a remote socket is slower; pin threads and memory to the same node.

All of this is Chapter 6.

---

## Senior interview answers

- **"Why does allocation rate matter more than allocation cost?"** `new` is a cheap TLAB bump, but
  every byte allocated must later be traced/reclaimed; allocation rate sets GC frequency, and GC =
  pauses = tail latency.
- **"How does ZGC keep pauses sub-millisecond?"** Concurrent marking/relocation with colored pointers
  and a load barrier that lazily fixes references, so pause time is independent of heap size.
- **"What is a safepoint and why can it hurt latency?"** A global stop-the-world checkpoint for
  GC/deopt/etc.; the JVM waits for *every* thread to reach one, so a slow-to-safepoint thread pauses
  the whole app — invisible in GC logs.
- **"How do you prove zero allocation?"** Epsilon GC survival + JMH `-prof gc` 0 B/op + an
  async-profiler alloc flame graph with no hot-path frames.
- **"What's a deopt and how do you avoid a storm?"** C2 discards code when a speculative assumption
  breaks; avoid megamorphic hot call sites and keep hot methods stable/monomorphic.
