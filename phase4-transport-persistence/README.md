# Chapter 4 — Transport & Persistence

> 📖 **Deep dive (learn):** [`docs/chapters/04-transport-persistence.md`](../docs/chapters/04-transport-persistence.md).
> This page is **usage** (how to run the proof).

## What this module proves
Messages can cross process/machine boundaries and land durably on disk at microsecond latency,
without a broker and without touching the JVM heap: **Aeron** for transport, **Chronicle Queue/Map**
for persistence, **Java-Thread-Affinity** for pinning.

## The claim → the proof
> *"I moved messages over Aeron IPC (shared-memory log, no broker) and journaled every event to a
> memory-mapped Chronicle Queue — the writing thread never blocked on I/O, and replay reproduced the
> exact byte stream. A pinned core removed scheduler-induced latency spikes."*

## ⚠️ Environment note
These libraries use `Unsafe`/`mmap`/native code and are the most environment-sensitive in the repo.
This module is **independently buildable** so it never blocks the pure-JVM chapters. On modern JDKs
you may need to open some JDK internals to Chronicle/Aeron:
```bash
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
```
Affinity pinning is fully effective on Linux; on Windows/macOS it degrades gracefully to a no-op.

## Run it
```bash
mvn -q -pl phase4-transport-persistence exec:java -Dexec.mainClass=com.learning.hft.transport.AeronIpcDemo
mvn -q -pl phase4-transport-persistence exec:java -Dexec.mainClass=com.learning.hft.transport.ChronicleQueueDemo
mvn -q -pl phase4-transport-persistence exec:java -Dexec.mainClass=com.learning.hft.transport.AffinityDemo
```

## Aeron constructs to internalize
| Construct | Role |
|---|---|
| `MediaDriver` | The transport engine (embedded here; standalone in prod) |
| `Publication` / `ExclusivePublication` | Append side; exclusive is faster (single writer) |
| `Subscription` / `Image` / `FragmentHandler` | Poll side; one `Image` per publisher |
| `aeron:ipc` vs `aeron:udp` | Shared-memory vs unicast/multicast network |
| `offer()` return codes | Back-pressure: `BACK_PRESSURED`, `NOT_CONNECTED`, `ADMIN_ACTION`, … |
| Aeron Archive / Cluster | Record+replay / Raft-replicated state machines (senior topics) |

## Chronicle constructs to internalize
| Construct | Role |
|---|---|
| `Bytes` | Off-heap byte manipulation |
| `Wire` | Self-describing serialization, flyweight zero-alloc read |
| `ChronicleQueue` + `ExcerptAppender`/`ExcerptTailer` | mmap journal; roll cycles; replay |
| `ChronicleMap` | Off-heap, optionally persisted concurrent key-value store |
| `AffinityLock` | Pin a thread to a core (mechanical-sympathy jitter control) |

## Key files
- [`AeronIpcDemo.java`](src/main/java/com/learning/hft/transport/AeronIpcDemo.java)
- [`ChronicleQueueDemo.java`](src/main/java/com/learning/hft/transport/ChronicleQueueDemo.java)
- [`AffinityDemo.java`](src/main/java/com/learning/hft/transport/AffinityDemo.java)

## Study checklist
- [ ] Explain why IPC beats a broker for co-located processes
- [ ] Explain what happens on `BACK_PRESSURED` and how you'd handle it
- [ ] Explain how mmap makes a disk write look like a memory store
- [ ] Build a `ChronicleMap` credit-limit store (leads directly into POC 3)

## TODO / extend
- [ ] Split publisher and subscriber into two JVMs sharing the driver dir
- [ ] Journal with `writeBytes` + an SBE flyweight instead of `writeText`
- [ ] Add a `ChronicleMap` demo class
