# Capstone — The Three POCs

> The interview-grade deliverables. Each one combines *every* chapter. These are **stubs** — the
> structure and design are laid out; you implement the hot paths in weeks 9–10.

Each POC is judged the same way: **a claim, backed by a JMH number, backed by an async-profiler
flame graph and a clean Epsilon-GC run.**

![diagram](./README-1.svg)

## POC 1 — Zero-Allocation Limit Order Book
- **File:** [`orderbook/OrderBook.java`](src/main/java/com/learning/hft/capstone/orderbook/OrderBook.java)
- **Claim:** *"Lock-free matching engine: 3–5M orders/s single-thread, p99 < 2µs."*
- **Key trick:** time priority via parallel `int[] next / int[] prev` index arrays — an array-backed
  doubly-linked list with **no `Node` allocation** and cache-friendly traversal.

## POC 2 — Extreme-Scale Blended VWAP
- **File:** [`vwap/BlendedVwapEngine.java`](src/main/java/com/learning/hft/capstone/vwap/BlendedVwapEngine.java)
- **Claim:** *"Replacing locking with an MPSC queue cut cross-thread contention latency from ~15µs to
  ~300ns."*
- **Key trick:** many pinned producers → one `MpscArrayQueue` → single consumer updating flat
  primitive VWAP accumulators indexed by pair id.

## POC 3 — Nanosecond Risk Gateway & Journal
- **File:** [`riskgateway/RiskGateway.java`](src/main/java/com/learning/hft/capstone/riskgateway/RiskGateway.java)
- **Claim:** *"Every order is credit-checked against an off-heap Chronicle Map and journaled to a
  memory-mapped Chronicle Queue with sub-µs durable writes, without pausing the execution thread."*
- **Key trick:** mmap makes a durable write look like a memory store; the OS flushes to NVMe lazily.

## Build order & definition of done
Implement in order (1 → 2 → 3). A POC is "done" when you can produce, for it:
1. a JMH throughput + `Mode.SampleTime` p99 number,
2. an async-profiler **allocation** flame graph showing no hot-path allocation,
3. an Epsilon-GC run that survives your target message count.

## Wiring in the heavier deps
POC 1 (Aeron out) and POC 3 (Chronicle) need the phase-4 dependencies. Add them to
[`pom.xml`](pom.xml) when you flesh out those hot paths — the stubs deliberately depend only on
pure-JVM libraries so the module always compiles from day one.
