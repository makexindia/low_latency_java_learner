# Chapter 2 — Concurrency & Lock-Free

> 📖 **Deep dive (learn):** [`docs/chapters/02-concurrency.md`](../docs/chapters/02-concurrency.md).
> This page is **usage** (how to run the proof).

## What this module proves
Data can move between threads without locks or allocation. It shows (1) a minimal LMAX Disruptor
pipeline whose events are pre-allocated and reused, and (2) the per-op overhead gap between a
lock-based `ArrayBlockingQueue` and a lock-free JCTools `MpscArrayQueue`.

## The claim → the proof
> *"I replaced a lock-based hand-off with a JCTools MPSC queue. Per-op cost dropped from X ns to Y ns,
> and the Disruptor path allocated zero bytes per message (events reused from the ring)."*

## Run it
```bash
# Disruptor demo (prints consumed events)
mvn -q -pl phase2-concurrency exec:java -Dexec.mainClass=com.learning.hft.concurrency.DisruptorDemo

# Queue hand-off benchmark
mvn -Pbench -pl phase2-concurrency package
java -jar phase2-concurrency/target/benchmarks.jar QueueHandoff -prof gc
```

## Disruptor constructs to internalize
| Construct | Role |
|---|---|
| `RingBuffer` | Pre-allocated ring of reusable events (zero-alloc publish) |
| `EventFactory` | Fills every slot once at startup (`LongEvent::new`) |
| `Sequence` / `SequenceBarrier` | Padded, lock-free progress counters; gate consumers |
| `EventHandler` / `WorkHandler` | Broadcast consumer vs work-stealing consumer |
| `WaitStrategy` | The CPU/latency dial: `BusySpin` → `Yielding` → `Sleeping` → `Blocking` |

## JCTools queue selection (cardinality = speed)
| Queue | Producers / Consumers |
|---|---|
| `SpscArrayQueue` | 1 / 1 — fastest, least coordination |
| `MpscArrayQueue` | many / 1 — the classic "fan-in to one engine" (VWAP POC) |
| `SpmcArrayQueue` | 1 / many |
| `MpmcArrayQueue` | many / many — most general, most coordination |

## Key files
- [`DisruptorDemo.java`](src/main/java/com/learning/hft/concurrency/DisruptorDemo.java)
- [`QueueHandoffBenchmark.java`](src/main/java/com/learning/hft/concurrency/QueueHandoffBenchmark.java)

## Study checklist
- [ ] Explain the single-writer principle and where it appears here
- [ ] Swap in a `BusySpinWaitStrategy` and justify when it's the right call
- [ ] Read `MpscArrayQueue` source; point at the padding that stops false sharing
- [ ] Build a diamond (dependency-graph) topology with two parallel handlers → a joiner

## TODO / extend
- [ ] Multi-producer cross-thread latency harness (`Mode.SampleTime`, real threads)
- [ ] Compare `WorkHandler` pool vs broadcast `EventHandler`s
