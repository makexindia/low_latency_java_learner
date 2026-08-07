# Chapter 1 — Benchmarking & Profiling

> 📖 **Deep dive (learn):** [`docs/chapters/01-benchmarking.md`](../docs/chapters/01-benchmarking.md).
> This page is **usage** (how to run the proof).

## What this module proves
That you can *measure*, not guess. It quantifies the allocation + cache cost of autoboxing by
comparing `HashMap<Integer,Integer>` against Agrona's primitive `Int2IntHashMap` — using a
methodologically correct JMH harness.

## The claim → the proof
> *"Using JMH with a GC profiler, I measured `HashMap<Integer,…>` allocating N bytes/op on lookups
> via autoboxing, while the primitive map reported ~0 B/op — and was M% faster."*

## Run it
```bash
mvn -Pbench -pl phase1-benchmarking package
java -jar phase1-benchmarking/target/benchmarks.jar Boxing -prof gc
```
Read `gc.alloc.rate.norm` (bytes allocated per op) and the average time. That's your number.

## JMH constructs to internalize (they appear in every later module)
| Construct | Why it matters |
|---|---|
| `@State` | Holds benchmark fixtures across invocations without re-allocating |
| `@Setup` / `@TearDown` | Keep setup cost *out* of the timed region |
| `Blackhole` | Consume results so the JIT can't dead-code-eliminate your work |
| `@Fork` | Fresh JVM → no cross-benchmark JIT profile pollution |
| `@Warmup` | Let the JIT compile hot code before measuring |
| `Mode.SampleTime` | Get p50/p99/p999 latency, not just the mean |
| `-prof gc` | The allocation-rate profiler — your zero-alloc lie detector |

## Coordinated omission (run this)
```bash
mvn -q -pl phase1-benchmarking exec:java -Dexec.mainClass=com.learning.hft.benchmarking.LatencyHistogramDemo
```
[`LatencyHistogramDemo`](src/main/java/com/learning/hft/benchmarking/LatencyHistogramDemo.java) prints
naive vs expected-interval-corrected percentiles so you can see the tail a naive loop hides. Read the
deep dive for *why*.

## Profiling (beyond JMH)
- **async-profiler** — see [`../scripts/run-async-profiler.sh`](../scripts/run-async-profiler.sh).
  CPU flame graph: `-e cpu`; allocation flame graph: `-e alloc`. Samples via `perf_events`, so no
  safepoint bias.
- **JFR** — `java -XX:+FlightRecorder -XX:StartFlightRecording=filename=rec.jfr ...`, then open in JMC.
- **Eclipse MAT** — load a heap dump, use the dominator tree to find leak suspects.

## Key file
- [`BoxingBenchmark.java`](src/main/java/com/learning/hft/benchmarking/BoxingBenchmark.java)

## TODO / extend
- [ ] Add a `Mode.SampleTime` latency-percentile variant
- [ ] Add a benchmark that (wrongly) omits `Blackhole` and watch the result get optimized to zero
