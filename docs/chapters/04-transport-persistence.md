# Chapter 4 — Transport & Persistence: Aeron and Chronicle Internals

> **Proof module:** [`phase4-transport-persistence`](../../phase4-transport-persistence) ·
> **Enemy:** coordination (brokers, copies) + GC + I/O jitter.

Two questions this chapter answers definitively: *how do you move bytes between processes/machines in
microseconds?* (Aeron) and *how do you make them durable without blocking?* (Chronicle). The shared
secret is **memory-mapped files**.

---

## 1. Memory-mapped files (mmap) — the foundation of both

`mmap` maps a file's bytes directly into your process's virtual address space. After mapping, reading
or writing the file is just reading/writing memory — **no `read()`/`write()` syscalls per operation**.
The OS **page cache** holds the pages; the kernel flushes dirty pages to disk lazily (or on `msync`).

Why this is transformative for low latency:
- **A durable write looks like a memory store.** `buffer.putLong(offset, x)` on a mapped region marks
  a page dirty; your thread returns immediately. The kernel writes it back asynchronously. You get
  persistence **without the application thread ever blocking on I/O**.
- **Off-heap = GC-invisible.** Mapped memory is outside the Java heap, so the GC never scans it. You
  can hold gigabytes of state with zero GC pressure.
- **Zero-copy IPC.** Two processes mapping the **same** file share physical pages via the page cache;
  one writes, the other reads, with no kernel copy between them. (Deep dive: [Ch.6](06-systems-internals.md).)

Both Aeron IPC and Chronicle Queue are, underneath, disciplined protocols over mmap'd files.

---

## 2. Aeron internals

Aeron is a **brokerless** reliable message transport over UDP (unicast/multicast) or IPC. "Brokerless"
means there is no Kafka-like server in the middle: publishers write into a shared **log**, subscribers
read from it. Latency is microseconds because there's no broker hop, no per-message heap object, and
no serialization to a broker's format.

### The Media Driver
A separate component (embedded in-process for dev, standalone for prod) that owns the transport: it
manages the log buffers, does the actual UDP send/receive, handles flow control and retransmission,
and runs on its own threads (with configurable `IdleStrategy` — the same CPU/latency dial as the
Disruptor `WaitStrategy`, Ch.2). Clients talk to it over a shared-memory command-and-control buffer.

![diagram](./04-transport-persistence-1.svg)

### The log buffer: terms and positions
Each publication's log is **three "term" buffers** (rotated) plus metadata. A monotonic **position**
(64-bit) identifies every byte ever published on the stream. Messages are written as **frames** with
headers; large messages are **fragmented** across frames and reassembled by the subscriber. Rotating
three terms lets writing continue in term N+1 while term N is still being drained/cleaned — no stall
at buffer wrap.

### Publication, Subscription, Image
- **`Publication`** — the append side. `ExclusivePublication` (single writer) is faster than the
  shared `Publication` (multi-writer, needs coordination) — the single-writer principle again (Ch.2).
- **`Subscription`** — the receive side. It has one **`Image`** per connected publication (a view of
  that publisher's term buffers).
- **`FragmentHandler`** — your callback; `subscription.poll(handler, fragmentLimit)` drains up to
  `fragmentLimit` fragments per call (batching, like the Disruptor). You call it in a loop driven by
  an `IdleStrategy`.

### Flow control and back-pressure — the `offer()` return codes
`publication.offer(buffer, offset, length)` returns the new **position** (≥0) on success, or a
**negative code** you must handle:
| Code | Meaning | Typical response |
|---|---|---|
| `BACK_PRESSURED` | subscriber(s) too slow; log window full | retry / drop / signal upstream |
| `NOT_CONNECTED` | no subscriber yet | retry or buffer |
| `ADMIN_ACTION` | term rotating | retry immediately |
| `CLOSED` | publication closed | stop |
| `MAX_POSITION_EXCEEDED` | log exhausted | rotate/rebuild |

Handling back-pressure explicitly is the whole point — you never silently queue unbounded memory. See
[`AeronIpcDemo`](../../phase4-transport-persistence/src/main/java/com/learning/hft/transport/AeronIpcDemo.java).

### Reliability over UDP
UDP has no delivery guarantee, so Aeron adds its own: subscribers detect gaps by **position** and send
**NAKs**; the driver retransmits from the term buffer. Loss recovery is thus bounded and doesn't need
TCP's head-of-line blocking. For market data, UDP **multicast** lets one publish reach many
subscribers at line rate (see [Ch.6](06-systems-internals.md) on IGMP/multicast).

### Archive and Cluster (senior topics)
- **Aeron Archive** — records a stream to disk and replays it (event sourcing, late-joiner catch-up).
- **Aeron Cluster** — Raft consensus over Aeron to run a **replicated state machine** (e.g. the
  matching engine) across nodes for fault tolerance while staying deterministic. This is how you make
  an exchange both fast *and* highly available.

---

## 3. Chronicle / OpenHFT internals

The OpenHFT stack is the other dominant ecosystem, built entirely around mmap and flyweights.

### Chronicle Bytes and Wire
- **`Bytes`** — an off-heap byte buffer abstraction (OpenHFT's counterpart to Agrona's
  `DirectBuffer`), with cursor-style read/write and support for mmap'd regions.
- **`Wire`** — a serialization layer over `Bytes` that can be **binary** (compact, fast) or **text**
  (YAML/JSON, for debugging) with the *same* code. Wire uses the **flyweight** pattern: it reads
  fields directly out of the buffer into a reused, mutable object — millions of msgs/sec at zero heap
  allocation, same idea as SBE (Ch.3) but self-describing.

### Chronicle Queue
A **persisted, broker-less journal**: an appender writes excerpts into mmap'd files; a tailer replays
them. Key mechanics:
- **`ExcerptAppender`** / **`ExcerptTailer`** — write and read cursors. Appends are memory stores into
  the mapped region → **sub-microsecond, non-blocking** (the kernel flushes lazily).
- **Roll cycles** — files roll by time (e.g. daily) so old data can be archived/deleted; the tailer
  transparently follows rolls.
- **Replay & event sourcing** — because every excerpt is durable and ordered, you can rebuild state by
  replaying from the start, or resume a tailer from a stored index. This is the risk-gateway journal
  pattern (POC 3) and the basis of crash recovery.
- On modern JDKs Chronicle needs `--add-opens java.base/sun.nio.ch=ALL-UNNAMED` (and a couple more) to
  reach the mmap internals — see the module README. See
  [`ChronicleQueueDemo`](../../phase4-transport-persistence/src/main/java/com/learning/hft/transport/ChronicleQueueDemo.java).

### Chronicle Map
An **off-heap, optionally-persisted, concurrent key-value store**. Keys and values are serialized (via
Wire) into mmap'd segments; lookups don't touch the Java heap and survive restarts (if file-backed).
Ideal for the risk-gateway's client credit limits: large, concurrent, GC-free, durable.

> **Design note for the capstone:** POC 3 journals via a raw `java.nio` mmap file
> ([`MmapJournal`](../../capstone/src/main/java/com/learning/hft/capstone/riskgateway/MmapJournal.java))
> rather than pulling in Chronicle, so it builds with no `--add-opens` flags — but the *mechanism*
> (append = memory store to a mapped region, replay = read it back) is exactly Chronicle Queue's.
> Chronicle is the production upgrade.

---

## 4. Thread affinity (pinning) — jitter control

`net.openhft:affinity`'s `AffinityLock` pins the current thread to a specific core so the OS scheduler
can't (a) **migrate** it to another core, arriving with cold caches, or (b) **preempt** it with
another runnable thread. Combined with kernel core isolation (`isolcpus`/`nohz_full`, Ch.6), a hot
thread gets a core to itself and runs with minimal jitter. On Linux this is fully effective; on
Windows/macOS it degrades to a best-effort no-op. See
[`AffinityDemo`](../../phase4-transport-persistence/src/main/java/com/learning/hft/transport/AffinityDemo.java).

---

## Senior interview answers

- **"Why is Aeron faster than Kafka?"** Brokerless: publishers write into a shared mmap'd log that
  subscribers read directly — no broker hop, no per-message heap object, batched polling, and reliable
  UDP with position-based NAK recovery instead of a TCP/broker round trip.
- **"How does a Chronicle Queue append avoid blocking on disk?"** It's a memory store into an mmap'd
  page; the kernel flushes dirty pages asynchronously, so the app thread never waits on I/O.
- **"How do you get HA without losing determinism?"** Aeron Cluster: Raft-replicate the input stream
  and run the same deterministic state machine (e.g. matcher) on every node.
- **"What do you do on `BACK_PRESSURED`?"** Never block unbounded — retry with an idle strategy, shed
  load, or propagate back-pressure upstream; it means the subscriber can't keep up.
