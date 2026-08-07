# Chapter 3 — Zero-Allocation Data Structures & SBE Serialization

> **Proof module:** [`phase3-zero-alloc`](../../phase3-zero-alloc) ·
> **Enemy:** GC pauses + cache misses.

Two ideas carry this chapter: **primitive open-addressed collections** (no boxing, no node objects)
and the **flyweight wire format** (decode by offset, allocate nothing). Together they let a system
process millions of messages/sec with a flat heap.

---

## 1. Why `HashMap<Integer, Order>` is a hot-path disaster

Three separate costs, all invisible in the source:

1. **Autoboxing.** `map.get(42)` boxes `42` into an `Integer` (allocation, unless in the −128..127
   cache) and every stored key is a boxed `Integer` object on the heap.
2. **Node objects.** `HashMap` stores each entry in a `Node` object (`hash`, `key` ref, `value` ref,
   `next` ref) — 32+ bytes, separately allocated, scattered across the heap.
3. **Pointer chasing.** A lookup hashes the key, indexes the bucket array, then follows `Node.next`
   references — **each hop a likely cache miss** (Ch.0). And every allocation feeds the GC.

So one logical lookup can be several cache misses + boxing allocation. At millions/sec this is the
difference between a flat heap and constant GC.

## 2. Agrona primitive collections — open addressing done right

Agrona's `Int2ObjectHashMap`, `Long2LongHashMap`, `Int2IntHashMap`, etc. store **primitive keys in a
single flat array** using **open addressing with linear probing**:

- The backing store is one `Object[]` (or two parallel primitive arrays) — **no `Node` objects**.
- Key `k` hashes to an index; if occupied by a different key, probe the **next** slot linearly. Because
  slots are contiguous, a probe sequence walks *within a cache line or two* — cache-friendly, unlike
  chained buckets.
- **Load factor** (default ~0.55–0.65) trades space for probe length; exceeding it triggers a
  **resize** (rehash into a bigger array — the one allocation event, which you do at startup by
  sizing correctly).
- **`missingValue` sentinel.** Primitive maps can't return `null`, so you pick a value that means
  "absent" (e.g. `Long.MIN_VALUE`, `-1`). `get` returns it on miss. You must choose a sentinel that
  can't be a real value. (The capstone [`OrderBook`](../../capstone/src/main/java/com/learning/hft/capstone/orderbook/OrderBook.java)
  uses `-1` for its id→slot `Long2LongHashMap`.)

Result: a lookup is **one hash + a short contiguous probe**, zero allocation, minimal cache misses.
See [`BoxingBenchmark`](../../phase1-benchmarking/src/main/java/com/learning/hft/benchmarking/BoxingBenchmark.java)
for the measured delta. **Eclipse Collections** offers the same primitive-collection idea with a
richer, more `Stream`-like API — pick Agrona when you're already in the Aeron/SBE ecosystem, Eclipse
when you want breadth.

### Buffers: `DirectBuffer` / `MutableDirectBuffer` / `UnsafeBuffer`
Agrona's buffer abstraction is the on/off-heap byte canvas everything else writes to:
- `DirectBuffer` (read) / `MutableDirectBuffer` (read-write) — the interface.
- `UnsafeBuffer` — the workhorse impl; can wrap a `byte[]` (on-heap) **or** a `ByteBuffer`/native
  address (off-heap). Access is by **absolute offset** (`getLong(offset)`, `putInt(offset, v)`), with
  optional bounds checks (disable in prod via a flag). This is what SBE and Aeron encode into.
- `ExpandableArrayBuffer` — grows on demand, for encoding messages of unknown length.

---

## 3. SBE (Simple Binary Encoding) — the flyweight wire format

SBE is the FIX community's binary encoding for ultra-low-latency messaging. Its whole philosophy: the
message **is** the bytes, and encoders/decoders are **flyweights** that read/write fields at fixed
offsets — never materializing an intermediate object.

### The schema drives codegen
You write an XML schema; the `sbe-tool` generates `XxxEncoder`/`XxxDecoder` classes at build time:
```xml
<sbe:messageSchema package="..." id="1" version="0" byteOrder="littleEndian">
  <types>
    <composite name="messageHeader">
      <type name="blockLength" primitiveType="uint16"/>
      <type name="templateId"  primitiveType="uint16"/>
      <type name="schemaId"    primitiveType="uint16"/>
      <type name="version"     primitiveType="uint16"/>
    </composite>
  </types>
  <sbe:message name="NewOrderSingle" id="1">
    <field name="orderId"  id="1" type="int64"/>
    <field name="price"    id="2" type="int64"/>   <!-- scaled integer, never a double -->
    <field name="quantity" id="3" type="int32"/>
    <field name="side"     id="4" type="uint8"/>
  </sbe:message>
</sbe:messageSchema>
```

### The wire layout
```
| message header (8B) | root block (fixed-length fields) | repeating groups | var-length data |
```
- **Message header**: `blockLength` (size of the fixed root block), `templateId` (which message),
  `schemaId`, `version`. The decoder reads this first to dispatch and to skip correctly.
- **Root block**: all fixed-length fields, in schema order, at **compile-time-known offsets**. Reading
  `price` is `buffer.getLong(offset + 8)` — one instruction, no parsing.
- **Repeating groups**: variable-count nested structures, length-prefixed.
- **Variable-length data**: strings/blobs, length-prefixed, at the end.

### Why it's zero-allocation and forward/backward compatible
- One decoder instance **wraps** the buffer and is reused across millions of messages (flyweight). No
  object per message. See the hand-rolled
  [`OrderFlyweight`](../../phase3-zero-alloc/src/main/java/com/learning/hft/zeroalloc/OrderFlyweight.java)
  — it *is* what SBE generates, with nothing hidden: fixed offset constants, `wrap()`, and
  offset-arithmetic getters/setters.
- **Versioning**: because the header carries `blockLength` and `version`, a new schema can **append**
  fields; an old decoder reads the fields it knows and uses `blockLength` to skip the rest. A newer
  decoder reading an older message returns defaults for fields added later. This lets you evolve
  message formats without a coordinated big-bang deploy — essential in a trading network.

### Why prices are scaled integers, never `double`
`double` can't represent `0.1` exactly, so money math accumulates rounding error and comparisons are
unsafe. The domain uses **scaled integers**: store `price × 10^n` as an `int64` (e.g. 1.0125 →
`101250` at scale 5). Exact, fast, and comparable. Every field in the flyweight is a `long`/`int` for
this reason.

---

## 4. Putting it together: the order codec, proven zero-alloc

[`FlyweightBenchmark`](../../phase3-zero-alloc/src/main/java/com/learning/hft/zeroalloc/FlyweightBenchmark.java)
allocates the buffer and flyweight once, then encodes+decodes in the timed loop:
```bash
mvn -Pbench -pl phase3-zero-alloc package
java -jar phase3-zero-alloc/target/benchmarks.jar Flyweight -prof gc   # gc.alloc.rate.norm ≈ 0 B/op
```
That `0 B/op` is the deliverable. Swap in real SBE codecs via the (commented) `sbe-tool` plugin in the
module `pom.xml` and re-run — the number stays zero because the mechanism is identical.

---

## Senior interview answers

- **"Why not `HashMap<Integer,…>` on the hot path?"** Autoboxing allocations + `Node` objects +
  pointer-chasing cache misses. Use an open-addressed primitive map (Agrona/Eclipse): flat array,
  linear probing, `missingValue` sentinel, zero allocation.
- **"What makes SBE fast?"** It's a flyweight: fields at compile-time-known offsets in the buffer,
  read/written directly, decoder reused across messages — zero intermediate objects, no parsing.
- **"How does SBE handle schema evolution?"** The header's `blockLength`/`version` let decoders skip
  unknown trailing fields and default missing ones, so you can append fields without breaking peers.
- **"Why scaled integers for price?"** `double` is inexact for decimals; scaled `int64` is exact,
  fast, and safely comparable.
