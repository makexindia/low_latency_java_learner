# Capstone — The Four POCs

> The interview-grade deliverables. Each one combines *every* chapter. **All four are implemented
> and covered by unit tests.**

Each POC is judged the same way: **a claim, backed by a JMH number, backed by an async-profiler
flame graph and a clean Epsilon-GC run.**

![diagram](./diagrams/capstone-architecture.svg)

## POC 1 — Zero-Allocation Limit Order Book ✅
- **Files:** [`OrderBook`](src/main/java/com/learning/hft/capstone/orderbook/OrderBook.java) ·
  [`OrderBookDemo`](src/main/java/com/learning/hft/capstone/orderbook/OrderBookDemo.java) ·
  [`OrderBookBenchmark`](src/main/java/com/learning/hft/capstone/orderbook/OrderBookBenchmark.java) ·
  tests: [`OrderBookTest`](src/test/java/com/learning/hft/capstone/orderbook/OrderBookTest.java)
- **Claim:** *"Lock-free matching engine, multi-million orders/s single-thread, p99 in low µs."*
- **Key trick:** an **array price ladder** (price→index in O(1)) plus time priority via parallel
  `int[] next / int[] prev` index arrays — an array-backed doubly-linked list with **no `Node`
  allocation** and cache-friendly traversal.
- **Run:**
  ```bash
  mvn -q -pl capstone compile exec:java -Dexec.mainClass=com.learning.hft.capstone.orderbook.OrderBookDemo
  mvn -Pbench -pl capstone package && java -jar capstone/target/benchmarks.jar OrderBook -prof gc
  ```

## POC 2 — Extreme-Scale Blended VWAP ✅
- **Files:** [`BlendedVwapEngine`](src/main/java/com/learning/hft/capstone/vwap/BlendedVwapEngine.java) ·
  [`BlendedVwapDemo`](src/main/java/com/learning/hft/capstone/vwap/BlendedVwapDemo.java) ·
  tests: [`BlendedVwapEngineTest`](src/test/java/com/learning/hft/capstone/vwap/BlendedVwapEngineTest.java)
- **Claim:** *"Replacing locking with an MPSC queue removes cross-thread contention"* (measure the
  per-op gap in phase2 `QueueHandoffBenchmark`). The demo sustains **~5M blended updates/s** through a
  single consumer.
- **Key trick:** many producers → one JCTools `MpscArrayQueue` → single consumer updating flat
  primitive VWAP accumulators indexed by pair id; `PriceUpdate` carriers are **borrowed from and
  returned to an object pool** (`MpmcArrayQueue`), so the steady state allocates zero objects.
- **Run:**
  ```bash
  mvn -q -pl capstone compile exec:java -Dexec.mainClass=com.learning.hft.capstone.vwap.BlendedVwapDemo
  ```

## POC 3 — Nanosecond Risk Gateway & Journal ✅
- **Files:** [`RiskGateway`](src/main/java/com/learning/hft/capstone/riskgateway/RiskGateway.java) ·
  [`MmapJournal`](src/main/java/com/learning/hft/capstone/riskgateway/MmapJournal.java) ·
  [`RiskGatewayDemo`](src/main/java/com/learning/hft/capstone/riskgateway/RiskGatewayDemo.java) ·
  tests: [`RiskGatewayTest`](src/test/java/com/learning/hft/capstone/riskgateway/RiskGatewayTest.java)
- **Claim:** *"Every order is credit-checked and journaled to a memory-mapped file with durable,
  non-blocking writes; state survives a restart via replay."*
- **Key trick:** credit store is an Agrona `Long2LongHashMap` (zero-alloc); the journal is a raw
  `java.nio` mmap file — append = memory store, replay = read back. mmap makes a durable write look
  like a memory store (the OS flushes lazily). The `RiskGatewayTest` proves durability by reopening
  the file and replaying.
- **Run:**
  ```bash
  mvn -q -pl capstone compile exec:java -Dexec.mainClass=com.learning.hft.capstone.riskgateway.RiskGatewayDemo
  ```

## POC 4 — Pre-Trade Rule Gateway + Scheduled Skew ✅
- **Files:** [`PreTradeRuleGateway`](src/main/java/com/learning/hft/capstone/rulegateway/PreTradeRuleGateway.java) ·
  [`QuoteSkewEngine`](src/main/java/com/learning/hft/capstone/rulegateway/QuoteSkewEngine.java) ·
  [`PreTradeRuleGatewayDemo`](src/main/java/com/learning/hft/capstone/rulegateway/PreTradeRuleGatewayDemo.java) ·
  tests: [`PreTradeRuleGatewayTest`](src/test/java/com/learning/hft/capstone/rulegateway/PreTradeRuleGatewayTest.java),
  [`QuoteSkewEngineTest`](src/test/java/com/learning/hft/capstone/rulegateway/QuoteSkewEngineTest.java)
- **Claim:** *"Thousands of pre-trade rules per order with a one-AND kill switch and live
  enable/disable; plus a quote skew that activates at a scheduled time — recomputed incrementally."*
- **Key trick:** reuses Chapter 8 — a bitset rule engine (fat-finger, max qty/notional,
  restricted-symbol via RoaringBitmap, kill switch), and a dataflow DAG + `DeadlineTimerWheel` for the
  scheduled skew. Accepted orders journal via the POC 3 `MmapJournal`.
- **Run** (needs `-am` to also build `phase8-decisioning`, or run `mvn -q install -DskipTests` once):
  ```bash
  mvn -q -pl capstone -am compile
  mvn -q -pl capstone exec:java -Dexec.mainClass=com.learning.hft.capstone.rulegateway.PreTradeRuleGatewayDemo
  ```

## Production upgrades (kept out to stay dependency-light)
- POC 1: SBE-encode fills onto an **Aeron** channel (Ch.4) instead of a callback.
- POC 3: swap the credit store for a **Chronicle Map** and the journal for a **Chronicle Queue**
  (Ch.4) — same shape, adds roll cycles/indexing (and the `--add-opens` flags).

## Definition of done (per POC)
1. a JMH throughput + `Mode.SampleTime` p99 number,
2. an async-profiler **allocation** flame graph showing no hot-path allocation,
3. an Epsilon-GC run that survives your target message count.
