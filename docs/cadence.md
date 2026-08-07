# 10-Week Cadence & Progress Checklist

Track your journey. Check a box only when you can **explain it** *and* **prove it with a number**.

## Weeks 1–2 · Foundations + Benchmarking
- [ ] Explain the cache hierarchy and the 64-byte cache line from memory
- [ ] Reproduce false sharing: measure padded vs unpadded counters (`phase0-foundations`)
- [ ] Write a correct JMH benchmark with `@State` + `Blackhole` (`phase1-benchmarking`)
- [ ] Read a `-prof gc` output; state the allocation rate of a boxed vs primitive map
- [ ] Generate one async-profiler flame graph (CPU) and one allocation flame graph

## Weeks 3–4 · Concurrency (Disruptor + JCTools)
- [ ] Build a single-producer/single-consumer Disruptor pipeline
- [ ] Explain `Sequence`, `SequenceBarrier`, and why they're padded
- [ ] Choose a `WaitStrategy` and justify it for a given deployment
- [ ] Replace `ArrayBlockingQueue` with `MpscArrayQueue`; measure the latency drop (`phase2-concurrency`)
- [ ] Read the JCTools source and point to the false-sharing padding

## Weeks 5–6 · Zero-Allocation (Agrona + SBE)
- [ ] Replace a `HashMap<Integer,…>` with an Agrona `Int2ObjectHashMap`; measure GC delta
- [ ] Read/write fields through a `MutableDirectBuffer` / `UnsafeBuffer`
- [ ] Encode + decode an order via a flyweight; prove zero allocation with `-prof gc` (`phase3-zero-alloc`)
- [ ] (Stretch) wire up the real SBE Maven plugin and generate codecs from an XML schema

## Weeks 7–8 · Transport & Persistence (Aeron + Chronicle)
- [ ] Send a message over an Aeron IPC channel (Publication → Subscription) (`phase4-transport-persistence`)
- [ ] Explain the back-pressure return codes of `offer()`
- [ ] Append and replay events with a Chronicle Queue (`ExcerptAppender`/`ExcerptTailer`)
- [ ] Store and look up a value in a Chronicle Map; explain where the bytes live
- [ ] Pin a thread with an `AffinityLock` (or `taskset`) and observe reduced jitter

## Weeks 9–10 · JVM/Hardware + Capstone
- [ ] Run a module under Epsilon GC and interpret survival vs OOM (`phase5-jvm-hardware`)
- [ ] Benchmark G1 vs ZGC pause times on an allocating workload
- [ ] Add `@Contended` to a contended field; measure the throughput change
- [ ] **Capstone 1:** matching engine hitting >1M orders/s, p99 measured (`capstone`)
- [ ] **Capstone 2:** blended VWAP over MPSC; quote the contention-latency drop
- [ ] **Capstone 3:** risk gateway with Chronicle Map check + Queue journal, durable + off hot path

## Weeks 11–12 · Systems Internals + Native Interop (the differentiators)
Deep dives: [`06-systems-internals`](chapters/06-systems-internals.md) ·
[`07-native-interop`](chapters/07-native-interop.md)
- [ ] Explain CPU pipeline/out-of-order/branch-prediction and read IPC from `perf stat`
- [ ] State when a GPU helps in finance (throughput math) and when it doesn't (the matching path)
- [ ] Map a file and share it between two processes (`MemoryMappedFileDemo`, `SharedMemoryIpcDemo`)
- [ ] Measure Nagle vs `TCP_NODELAY` RTT (`TcpNoDelayDemo`); join a multicast group (`UdpMulticastDemo`)
- [ ] Explain kernel bypass (DPDK/ef_vi/AF_XDP) and PTP timestamping in one paragraph each
- [ ] Tune a Linux box for latency: `isolcpus`+`nohz_full`, pinning, IRQ affinity, `performance` governor
- [ ] Publish/consume lock-free with `VarHandle` acquire/release (`VarHandleDemo`)
- [ ] Read off-heap memory + call C with FFM (`ForeignMemoryDemo`); call C with JNR (`JnrExample`)
- [ ] Explain the JNI boundary cost and why FFM/JNR beat it (`JniReference`)

## Definition of "done" for the whole curriculum
You can walk into an interview and, for each POC, state a **claim**, back it with a **JMH number**,
and show an **async-profiler flame graph** + an **Epsilon-GC clean run**. Claim → number → proof.
