# Goal: From Junior to Senior Low-Latency Java Engineer

> The north-star document. Everything in this repo exists to move you along this path.
> Read this first, then follow the chapters in [`README.md`](README.md).

## The one mental model that unifies everything

Every library and technique in this curriculum exists to defeat **one of four enemies**:

| Enemy | What it costs you | Killed by |
|---|---|---|
| **GC pauses** | Unpredictable multi-ms stalls from allocation | Zero-allocation, object pools, off-heap, flyweights |
| **Cache misses** | ~100+ cycles per main-memory fetch | Data-oriented layout, primitive collections, padding |
| **Coordination** | Lock contention, OS context switches | Lock-free ring buffers & queues, single-writer principle |
| **Jitter** | Safepoints, JIT deopt, OS scheduling noise | CPU pinning, GC tuning, warm-up, huge pages |

For **every construct you learn, ask:** *which enemy does this kill, and by what mechanism?*
That question is what separates a senior from someone who memorized API names.

---

## The path (6 phases → capstone)

![diagram](./GOALS-1.svg)

> The **deep-dive concept docs** in [`docs/chapters/`](docs/chapters/README.md) are the primary
> learning material for every phase; the modules are the runnable proof. See
> [`docs/topics-map.md`](docs/topics-map.md) for the concept→class index.

Each phase is a Maven module in this repo. You **read the chapter (theory) → run the module (proof)**.

---

## Phase 0 — Foundations (before any library)

You cannot use these tools well without the CS underneath them.

| Concept | What to actually understand |
|---|---|
| CPU cache hierarchy | L1/L2/L3, the **64-byte cache line**, spatial vs temporal locality |
| False sharing | Two hot variables on one cache line → cache-line ping-pong across cores |
| Java Memory Model | `volatile`, happens-before, `final` semantics, `VarHandle` (modern `Unsafe` replacement) |
| Mechanical sympathy | Data-oriented design; array-of-structs vs struct-of-arrays |
| GC basics | Allocation rate → GC frequency; why "zero allocation on the hot path" matters |
| Off-heap & mmap | `ByteBuffer.allocateDirect`, memory-mapped files, the OS page cache |

**Do:** Two threads incrementing adjacent `long` fields, then pad them apart, and measure the 5–10× swing yourself. → `phase0-foundations`

## Phase 1 — Benchmarking & profiling (measure before you optimize)

Build this reflex *first*, or every later claim is unverifiable.

- **JMH** — `@Benchmark`, `@State`, `@Setup`/`@TearDown`, `Blackhole` (defeats dead-code elimination), `@Param`, `Mode.SampleTime` for latency percentiles, `-prof gc` for allocation rate.
- **Async-Profiler** — CPU flame graphs, **allocation** flame graphs (`-e alloc`), wall-clock mode; *why* it avoids safepoint bias (perf_events sampling, not safepoint stack walks).
- **JFR + JMC** — GC pauses, safepoint pauses, allocation recording.
- **Eclipse MAT** — heap-dump dominator tree, leak suspects.

**Milestone:** quantify the autoboxing cost of `HashMap<Integer,…>` vs a primitive map with a GC-profiled JMH run. → `phase1-benchmarking`

## Phase 2 — Concurrency & lock-free structures

### LMAX Disruptor
`RingBuffer` (pre-allocated reusable events) · `EventFactory` · `Sequence`/`Sequencer`/`SequenceBarrier` (padded, `@Contended`) · `EventHandler`/`WorkHandler` · `WaitStrategy` (BusySpin → Yielding → Sleeping → Blocking) · topologies (multicast, **diamond/pipeline** dependency graphs).

### JCTools
`SpscArrayQueue` · `MpscArrayQueue` (the POC-2 workhorse) · `MpmcArrayQueue` · `relaxed*` offer/poll. Read the source to see **field-layout padding via inheritance** that kills active + passive false sharing.

**Milestone:** swap `ArrayBlockingQueue` for `MpscArrayQueue` and measure the cross-thread latency drop. → `phase2-concurrency`

## Phase 3 — Zero-allocation data structures & serialization

- **Agrona** — `Int2ObjectHashMap`, `Long2LongHashMap`, `IntArrayList`; `DirectBuffer`/`MutableDirectBuffer`/`UnsafeBuffer`; `IdleStrategy`; `Agent`/`AgentRunner`.
- **Eclipse Collections** — primitive containers, memory-efficient immutables.
- **SBE** — XML message schema → generated **flyweight** encoders/decoders; encode/decode by direct offset manipulation, no intermediate objects; versioning & repeating groups.

**Milestone:** encode a `NewOrderSingle` into an `UnsafeBuffer`, decode it back, JMH `-prof gc` shows **zero allocation**. → `phase3-zero-alloc`

## Phase 4 — Transport & persistence

- **Aeron** — Media Driver (embedded vs standalone) · `Publication`/`ExclusivePublication` · `Subscription`/`Image`/`FragmentHandler` · IPC vs UDP unicast vs multicast · back-pressure return codes · Archive (record/replay) · Cluster (Raft).
- **Chronicle / OpenHFT** — `Bytes`, `Wire` (self-describing zero-alloc read), `Chronicle Queue` (mmap journal: `ExcerptAppender`/`ExcerptTailer`, roll cycles), `Chronicle Map` (off-heap KV), `Java-Thread-Affinity` (`AffinityLock`).

**Milestone:** credit-check in a Chronicle Map → journal raw bytes to a Chronicle Queue → replay to rebuild state. → `phase4-transport-persistence`

## Phase 5 — The JVM & hardware sandbox (the senior differentiator)

Where you *prove* the work:
- **Epsilon GC** (`-XX:+UseEpsilonGC`) — no-op collector; if the heap grows, you allocated.
- **ZGC / Shenandoah vs G1** — benchmark sub-ms pauses; know when each fits.
- **`@Contended`** (`-XX:-RestrictContended`) — cache-line padding.
- **CPU pinning** — `taskset`, `isolcpus`, thread affinity, NUMA.
- JIT warm-up, tiered compilation, `-XX:+PrintCompilation`; jitter reduction (huge pages, THP).

→ `phase5-jvm-hardware`

## Phase 6 — Systems internals (below the JVM)

The job-spec lines ordinary developers can't answer. **CPU**: pipeline, out-of-order, branch
prediction, SIMD. **GPU**: SIMT — and the judgment to know it's for throughput math (risk/ML), not
the latency path. **Memory**: virtual memory, TLB, huge pages, and **shared memory** via mmap.
**Networking**: TCP (Nagle/`TCP_NODELAY`/delayed-ACK), UDP **multicast** for market data, and
**kernel bypass** (DPDK/ef_vi/AF_XDP/io_uring, PTP timestamping). **Linux**: scheduler/`SCHED_FIFO`,
`isolcpus`/`nohz_full`, IRQ affinity, C-states, NUMA, `perf`/eBPF.

→ `phase6-systems-internals` · deep dive [`06-systems-internals`](docs/chapters/06-systems-internals.md)

## Phase 7 — Native interop (Java ↔ off-heap & C)

The ladder from `Unsafe` to Panama. **`sun.misc.Unsafe`** (deprecated) → **`VarHandle`** (on-heap
ordered/atomic access, the sanctioned successor) → **FFM / Project Panama** (`Arena`/`MemorySegment`
off-heap + `Linker` downcalls, replacing both Unsafe and JNI) → **JNI** (classic, costly boundary) →
**JNR-FFI** (call C via a Java interface, no glue). Know which to reach for and why.

→ `phase7-native-interop` · deep dive [`07-native-interop`](docs/chapters/07-native-interop.md)

## Capstone — the three POCs

Done in order, each with a JMH harness, an async-profiler allocation flame graph, and an Epsilon-GC run proving zero allocation:

1. **Zero-Allocation Limit Order Book** — price-time priority matching, no object churn. *Target: 3–5M orders/s single-thread, p99 < 2µs.*
2. **Extreme-Scale Blended VWAP** — 5 RFS feeds → MPSC → pricing engine over 100 pairs. *Target: cross-thread contention 15µs → ~300ns.*
3. **Nanosecond Risk Gateway & Journal** — Chronicle Map credit check + Chronicle Queue persistence. *Target: sub-µs durable writes off the hot path.*

→ `capstone`

---

## Suggested cadence

| Weeks | Focus |
|---|---|
| 1–2 | Phase 0 + Phase 1 |
| 3–4 | Phase 2 (Disruptor + JCTools) |
| 5–6 | Phase 3 (Agrona + SBE) |
| 7–8 | Phase 4 (Aeron + Chronicle) |
| 9–10 | Phase 5 + wire it all into the capstone POCs |

See [`docs/cadence.md`](docs/cadence.md) for the trackable checklist.

## The recurring senior insight

The same three patterns appear in **every** library — see [`docs/recurring-patterns.md`](docs/recurring-patterns.md):
1. **Pre-allocate & reuse** (ring buffers, flyweights, object pools)
2. **The CPU/latency trade-off dial** (Disruptor `WaitStrategy` ≈ Agrona `IdleStrategy`)
3. **Layout to beat the cache** (padding, primitive collections, off-heap)

When you can explain a *new* library in those terms on sight, you're senior.
