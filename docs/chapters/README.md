# Deep-Dive Concept Docs

These are the **core learning resource** — long-form technical explanations of how each construct
works *internally*, with diagrams and worked examples. They are written to be self-contained: a
senior engineer new to the domain should not need to read anything else to understand the mechanics.

Each chapter here pairs with a runnable module (the *proof*). Read the concept doc, then run the
module.

| # | Concept doc (learn) | Module (run) |
|---|---|---|
| 0 | [Foundations: caches, MESI, the JMM](00-foundations.md) | [`phase0-foundations`](../../phase0-foundations) |
| 1 | [Benchmarking: JMH internals, coordinated omission](01-benchmarking.md) | [`phase1-benchmarking`](../../phase1-benchmarking) |
| 2 | [Concurrency: Disruptor & JCTools internals](02-concurrency.md) | [`phase2-concurrency`](../../phase2-concurrency) |
| 3 | [Zero-Allocation: Agrona maps & SBE wire format](03-zero-alloc.md) | [`phase3-zero-alloc`](../../phase3-zero-alloc) |
| 4 | [Transport & Persistence: Aeron & Chronicle internals](04-transport-persistence.md) | [`phase4-transport-persistence`](../../phase4-transport-persistence) |
| 5 | [JVM & Hardware: GC, JIT, safepoints internals](05-jvm-hardware.md) | [`phase5-jvm-hardware`](../../phase5-jvm-hardware) |
| 6 | [Systems Internals: CPU/GPU, memory, TCP/UDP, Linux](06-systems-internals.md) | [`phase6-systems-internals`](../../phase6-systems-internals) |
| 7 | [Native Interop: JNI, Unsafe, VarHandle, FFM, JNR-FFI](07-native-interop.md) | [`phase7-native-interop`](../../phase7-native-interop) |
| 8 | [Fast Decisioning: rules, bitsets, DAG/dataflow, CEP](08-decisioning.md) | [`phase8-decisioning`](../../phase8-decisioning) |

> Reading order matches the numbers. Each doc ends with a **"Senior interview answers"** section:
> the crisp, correct one-paragraph answer to the questions this topic generates.

See also [`../topics-map.md`](../topics-map.md) — every concept in the curriculum mapped to the exact
class/snippet that demonstrates it.
