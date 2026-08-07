# Topics Map — every concept → where it's taught & proven

The complete index for the time-poor senior. Each row: a concept, the **deep-dive doc** that explains
it, and the **runnable class/snippet** that proves it (— = conceptual, no code artifact). If a topic a
high-performance Java engineer is expected to know isn't here, it's a gap — open an issue.

## Hardware & memory (the substrate)
| Concept | Learn | Prove |
|---|---|---|
| Cache hierarchy, 64-byte line, locality | [00](chapters/00-foundations.md#1-the-memory-hierarchy-and-why-it-dominates-latency) | — |
| MESI coherence & false sharing | [00](chapters/00-foundations.md#2-cache-coherence-mesi-and-false-sharing) | `FalseSharingBenchmark` |
| Cache-line padding | [00](chapters/00-foundations.md#2-cache-coherence-mesi-and-false-sharing) · [05](chapters/05-jvm-hardware.md#4-contended--declarative-cache-line-padding) | `FalseSharingBenchmark` (manual), `@Contended` (Ch.5) |
| Struct-of-arrays / data-oriented design | [00](chapters/00-foundations.md#5-data-oriented-design-struct-of-arrays) | `OrderBook` (order pool) |
| Pipeline, superscalar, out-of-order exec | [06](chapters/06-systems-internals.md#1-cpu-architecture-beyond-the-cache) | — |
| Branch prediction / branchless code | [06](chapters/06-systems-internals.md#1-cpu-architecture-beyond-the-cache) | — |
| SIMD / Vector API | [06](chapters/06-systems-internals.md#1-cpu-architecture-beyond-the-cache) | — |
| GPU / SIMT (and when NOT to use it) | [06](chapters/06-systems-internals.md#2-gpu-architecture--and-honest-guidance-on-when-it-matters) | — |
| Virtual memory, pages, TLB, page faults | [06](chapters/06-systems-internals.md#3-memory-management-virtual-memory-pages-and-huge-pages) | — |
| Huge pages / THP | [06](chapters/06-systems-internals.md#3-memory-management-virtual-memory-pages-and-huge-pages) · [05](chapters/05-jvm-hardware.md) | — |
| Memory-mapped files (mmap) | [04](chapters/04-transport-persistence.md#1-memory-mapped-files-mmap--the-foundation-of-both) · [06](chapters/06-systems-internals.md) | `MemoryMappedFileDemo` |
| Shared-memory IPC (zero-copy) | [06](chapters/06-systems-internals.md#4-shared-memory--zero-copy-ipc) | `SharedMemoryIpcDemo` |
| NUMA, C-states, CPU governor | [05](chapters/05-jvm-hardware.md#5-mechanical-sympathy-at-the-oscpu-edge-bridge-to-ch6) · [06](chapters/06-systems-internals.md#6-linux-internals-for-low-latency) | — |

## Java Memory Model & concurrency
| Concept | Learn | Prove |
|---|---|---|
| happens-before, `volatile`, release/acquire | [00](chapters/00-foundations.md#4-the-java-memory-model--the-rules-you-actually-use) | `VarHandleDemo` |
| `VarHandle` access modes | [00](chapters/00-foundations.md#the-modern-tool-varhandle-and-why-unsafe-is-dying) · [07](chapters/07-native-interop.md#2-varhandle--the-sanctioned-on-heap-successor) | `VarHandleDemo` |
| Single-writer principle | [02](chapters/02-concurrency.md#2-the-single-writer-principle) | `OrderBook`, Disruptor |
| Lock-free vs locks / why parking is slow | [02](chapters/02-concurrency.md#1-why-locks-are-expensive-at-this-scale) | `QueueHandoffBenchmark` |
| Disruptor: ring buffer, sequences, gating | [02](chapters/02-concurrency.md#3-lmax-disruptor-internals) | `DisruptorDemo` |
| Disruptor: batching, wait strategies, topologies | [02](chapters/02-concurrency.md#3-lmax-disruptor-internals) | `DisruptorDemo` |
| JCTools SPSC/MPSC/MPMC, padding layout | [02](chapters/02-concurrency.md#4-jctools-internals) | `QueueHandoffBenchmark` |
| Back-pressure (bounded queues) | [02](chapters/02-concurrency.md#4-jctools-internals) · [04](chapters/04-transport-persistence.md#flow-control-and-back-pressure--the-offer-return-codes) | `AeronIpcDemo` (offer codes) |
| `setRelease`/lazySet publish idiom | [02](chapters/02-concurrency.md) · [07](chapters/07-native-interop.md#2-varhandle--the-sanctioned-on-heap-successor) | `VarHandleDemo` |

## Zero-allocation & serialization
| Concept | Learn | Prove |
|---|---|---|
| Autoboxing cost; open-addressed primitive maps | [03](chapters/03-zero-alloc.md#2-agrona-primitive-collections--open-addressing-done-right) | `BoxingBenchmark` |
| `missingValue` sentinel, load factor, probing | [03](chapters/03-zero-alloc.md#2-agrona-primitive-collections--open-addressing-done-right) | `OrderBook` (id→slot map) |
| DirectBuffer / MutableDirectBuffer / UnsafeBuffer | [03](chapters/03-zero-alloc.md#buffers-directbuffer--mutabledirectbuffer--unsafebuffer) | `OrderFlyweight` |
| Flyweight pattern | [03](chapters/03-zero-alloc.md#3-sbe-simple-binary-encoding--the-flyweight-wire-format) | `OrderFlyweight`, `FlyweightBenchmark` |
| SBE wire layout, header, groups, versioning | [03](chapters/03-zero-alloc.md#the-wire-layout) | `OrderFlyweight` (+ sbe-tool stretch) |
| Scaled-integer prices (never double) | [03](chapters/03-zero-alloc.md#why-prices-are-scaled-integers-never-double) | `OrderBook`, `OrderFlyweight` |
| Object pooling | [recurring-patterns](recurring-patterns.md#pattern-1--pre-allocate--reuse-never-allocate-on-the-hot-path) | `BlendedVwapEngine` (pool borrow/return) |

## Transport & persistence
| Concept | Learn | Prove |
|---|---|---|
| Aeron media driver, publication/subscription/image | [04](chapters/04-transport-persistence.md#2-aeron-internals) | `AeronIpcDemo` |
| Aeron log buffer, terms, positions, fragments | [04](chapters/04-transport-persistence.md#the-log-buffer-terms-and-positions) | `AeronIpcDemo` |
| offer() back-pressure return codes | [04](chapters/04-transport-persistence.md#flow-control-and-back-pressure--the-offer-return-codes) | `AeronIpcDemo` |
| Reliability over UDP (NAK) | [04](chapters/04-transport-persistence.md#reliability-over-udp) | — |
| Aeron Archive / Cluster (Raft) | [04](chapters/04-transport-persistence.md#archive-and-cluster-senior-topics) | — |
| Chronicle Bytes / Wire | [04](chapters/04-transport-persistence.md#chronicle-bytes-and-wire) | — |
| Chronicle Queue (appender/tailer, roll cycles) | [04](chapters/04-transport-persistence.md#chronicle-queue) | `ChronicleQueueDemo` |
| mmap journal (append + replay, durable) | [04](chapters/04-transport-persistence.md#chronicle-queue) · [06](chapters/06-systems-internals.md#4-shared-memory--zero-copy-ipc) | `MmapJournal` + `RiskGatewayTest` |
| Chronicle Map (off-heap KV) → Agrona placeholder | [04](chapters/04-transport-persistence.md#chronicle-map) | `RiskGateway` (credit store) |
| Thread affinity / CPU pinning | [04](chapters/04-transport-persistence.md#4-thread-affinity-pinning--jitter-control) · [06](chapters/06-systems-internals.md#6-linux-internals-for-low-latency) | `AffinityDemo` |

## Networking
| Concept | Learn | Prove |
|---|---|---|
| TCP handshake, Nagle, delayed-ACK, congestion | [06](chapters/06-systems-internals.md#tcp--reliable-ordered-and-full-of-latency-traps) | `TcpNoDelayDemo` |
| `TCP_NODELAY` | [06](chapters/06-systems-internals.md#tcp--reliable-ordered-and-full-of-latency-traps) | `TcpNoDelayDemo` |
| UDP, multicast, IGMP | [06](chapters/06-systems-internals.md#udp--connectionless-and-the-basis-of-market-data) | `UdpMulticastDemo` |
| Kernel bypass (DPDK, ef_vi, AF_XDP, io_uring) | [06](chapters/06-systems-internals.md#kernel-bypass--the-real-hft-frontier) | — |
| NIC timestamping, PTP clock sync | [06](chapters/06-systems-internals.md#kernel-bypass--the-real-hft-frontier) | — |

## JVM internals (GC, JIT, safepoints)
| Concept | Learn | Prove |
|---|---|---|
| TLAB, allocation rate, generational GC | [05](chapters/05-jvm-hardware.md#1-allocation-and-the-gc--the-enemy-you-design-around) | `EpsilonProof` |
| Escape analysis / scalar replacement | [05](chapters/05-jvm-hardware.md#escape-analysis--scalar-replacement-the-jits-gift) | — |
| Collectors: Epsilon, G1, ZGC, Shenandoah | [05](chapters/05-jvm-hardware.md#the-collectors--internals-and-when-to-use-each) | `run-epsilon.sh`, `run-gc-compare.sh` |
| ZGC colored pointers + load barrier | [05](chapters/05-jvm-hardware.md#the-collectors--internals-and-when-to-use-each) | — |
| Epsilon zero-alloc proof | [05](chapters/05-jvm-hardware.md#the-collectors--internals-and-when-to-use-each) | `EpsilonProof` |
| JIT tiers, inlining, monomorphic dispatch, deopt, OSR | [05](chapters/05-jvm-hardware.md#2-the-jit--warm-up-tiers-inlining-deopt) | — |
| Safepoints & time-to-safepoint | [05](chapters/05-jvm-hardware.md#3-safepoints--the-hidden-global-pauses) | — |
| `@Contended` | [05](chapters/05-jvm-hardware.md#4-contended--declarative-cache-line-padding) | — |

## Measurement
| Concept | Learn | Prove |
|---|---|---|
| JMH: fork, warm-up, Blackhole, @State, DCE | [01](chapters/01-benchmarking.md#2-jmh--how-it-defends-against-each-trap) | all `*Benchmark` classes |
| `-prof gc` allocation rate | [01](chapters/01-benchmarking.md#2-jmh--how-it-defends-against-each-trap) | `BoxingBenchmark`, `FlyweightBenchmark` |
| Coordinated omission + HdrHistogram | [01](chapters/01-benchmarking.md#3-coordinated-omission--the-tail-latency-lie-and-hdrhistogram) | `LatencyHistogramDemo` |
| async-profiler, safepoint bias | [01](chapters/01-benchmarking.md#4-async-profiler--flame-graphs-without-safepoint-bias) | `run-async-profiler.sh` |
| JFR / JMC / Eclipse MAT | [01](chapters/01-benchmarking.md#5-jfr-jmc-and-heap-analysis-mat) | — |

## Native interop
| Concept | Learn | Prove |
|---|---|---|
| `sun.misc.Unsafe` (deprecated) | [07](chapters/07-native-interop.md#1-sunmiscunsafe--the-old-foundation-and-why-its-dying) | `UnsafeDemo` |
| `VarHandle` | [07](chapters/07-native-interop.md#2-varhandle--the-sanctioned-on-heap-successor) | `VarHandleDemo` |
| FFM / Panama (`Arena`, `MemorySegment`, `Linker`) | [07](chapters/07-native-interop.md#3-ffm--project-panama--the-modern-off-heap--native-api) | `ForeignMemoryDemo` |
| JNI (boundary cost, GC interaction) | [07](chapters/07-native-interop.md#4-jni--the-classic-native-bridge-and-its-costs) | `JniReference` + `native/` |
| JNR-FFI | [07](chapters/07-native-interop.md#5-jnr-ffi--call-c-without-writing-c) | `JnrExample` |

## Capstone (integration)
| POC | Learn | Prove |
|---|---|---|
| Zero-alloc limit order book | [capstone README](../capstone/README.md) | `OrderBook` ✅ + `OrderBookTest` + `OrderBookBenchmark` |
| Blended VWAP (MPSC + pool) | [capstone README](../capstone/README.md) | `BlendedVwapEngine` ✅ + `BlendedVwapEngineTest` + `BlendedVwapDemo` |
| Risk gateway + mmap journal | [capstone README](../capstone/README.md) | `RiskGateway` + `MmapJournal` ✅ + `RiskGatewayTest` |

> **Legend:** ✅ implemented & tested. All three POCs are now implemented and covered by unit tests.
