# The Recurring Patterns (the senior spine)

These modules look like 7 unrelated demos. They are not. **Three patterns recur in every single
library** in this repo. Learn to spot them, and any new low-latency library becomes readable on
sight — that recognition *is* seniority.

---

## Pattern 1 — Pre-allocate & reuse (never allocate on the hot path)

Allocation feeds the GC, and the GC is enemy #1. So the hot path allocates **nothing**: objects are
created once at startup and reused forever.

| Library | How it shows up |
|---|---|
| Disruptor | `RingBuffer` holds N reusable event objects created by an `EventFactory` at startup |
| JCTools | Bounded array-backed queues: the backing array is allocated once |
| Agrona | Open-addressed maps store primitives in a pre-sized flat array; buffers are reused |
| SBE / Chronicle Wire | **Flyweight**: one mutable decoder wraps many different buffers over time |
| Object pools (capstone) | Order objects checked out and returned, never `new`-ed on the hot path |

**Tell in code:** you see `new` only in `@Setup`/constructors, never inside the message loop.

## Pattern 2 — The CPU/latency trade-off dial

Waiting for the next event is a spectrum from "burn a core for lowest latency" to "sleep and save
power for highest throughput". Every library exposes the *same dial* under a different name.

![diagram](./recurring-patterns-1.svg)

| Library | The dial |
|---|---|
| Disruptor | `WaitStrategy`: `BusySpin` → `Yielding` → `Sleeping` → `Blocking` |
| Agrona / Aeron | `IdleStrategy`: `BusySpin` → `Yielding` → `Sleeping` → `Backoff` |
| JCTools | you write the spin/park loop yourself around `poll()` |

**Senior move:** pick the strategy from the deployment reality (dedicated isolated core → busy-spin;
shared cloud VM → backoff), and *say why* in the interview.

## Pattern 3 — Layout to beat the cache

Two sub-moves: (a) keep related data **contiguous** so a cache-line fetch brings useful neighbours,
and (b) keep contended data **apart** so cores don't fight over a shared line.

| Move | Library expression |
|---|---|
| Contiguous primitives | Agrona / Eclipse primitive collections; array-backed linked lists (index pointers, not `Node`s) |
| Off-heap contiguity | Chronicle Bytes/Queue/Map; direct `ByteBuffer` |
| Padding contended fields | Disruptor `Sequence` and JCTools counters are padded; `@Contended` annotation |

**Tell in code:** fields padded with dummy `long`s, or class hierarchies whose only job is to insert
padding between hot fields (JCTools does exactly this).

---

## The synthesis question

For any construct, a senior can answer in one breath:

> *"Which enemy (GC / cache / coordination / jitter) does this kill, via which of the three patterns,
> and what number would prove it?"*

If you can do that for `MpscArrayQueue`, `Int2ObjectHashMap`, an SBE decoder, and a Chronicle
appender, you have internalized this repo.
