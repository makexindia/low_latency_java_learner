# Chapter 3 — Zero-Allocation & Serialization

> 📖 **Deep dive (learn):** [`docs/chapters/03-zero-alloc.md`](../docs/chapters/03-zero-alloc.md).
> This page is **usage** (how to run the proof).

## What this module proves
You can represent and (de)serialize domain objects with **zero heap allocation** on the hot path,
using primitive collections (Agrona / Eclipse Collections) and the **flyweight** pattern (the idea
behind SBE and Chronicle Wire).

## The claim → the proof
> *"My order codec encodes and decodes with 0 bytes/op (verified via JMH `-prof gc`): a single
> reused flyweight reads fields by fixed byte offset over a pre-allocated buffer — the same
> mechanism SBE generates."*

## Run it
```bash
mvn -Pbench -pl phase3-zero-alloc package
java -jar phase3-zero-alloc/target/benchmarks.jar Flyweight -prof gc
```
Confirm `gc.alloc.rate.norm ≈ 0 B/op`. That zero is your headline.

## Key files
- [`OrderFlyweight.java`](src/main/java/com/learning/hft/zeroalloc/OrderFlyweight.java) — hand-rolled flyweight (SBE mechanics, nothing hidden)
- [`FlyweightBenchmark.java`](src/main/java/com/learning/hft/zeroalloc/FlyweightBenchmark.java) — the zero-alloc proof

## Agrona / Eclipse constructs to internalize
| Construct | Purpose |
|---|---|
| `Int2ObjectHashMap`, `Long2LongHashMap` | Primitive-keyed maps — no autoboxing, flat arrays |
| `IntArrayList` | Primitive list — no `Integer` boxing |
| `DirectBuffer` / `MutableDirectBuffer` / `UnsafeBuffer` | Byte-level read/write, on- or off-heap |
| `ExpandableArrayBuffer` | Growable buffer for encoding unknown-length messages |
| Eclipse `IntObjectHashMap`, `MutableIntList` | The alternative primitive-collections library |

## Stretch: real SBE codegen
1. Add `src/main/resources/order-schema.xml` (an SBE message schema — `<sbe:messageSchema>` with a
   `NewOrderSingle` message defining `orderId`, `price`, `quantity`, `side`).
2. Uncomment the `exec-maven-plugin` block in [`pom.xml`](pom.xml) and add the `sbe-tool` dependency.
3. `mvn generate-sources` emits `NewOrderSingleEncoder` / `NewOrderSingleDecoder` into
   `target/generated-sources/sbe`. Compare the generated offset arithmetic to `OrderFlyweight` —
   it's the same idea, industrialized (versioning, repeating groups, var-length strings).

## Study checklist
- [ ] Explain why prices are scaled integers (`long`), never `double`, in these systems
- [ ] Explain how a flyweight decodes a *stream* of messages with one instance
- [ ] Replace a `HashMap<Integer,Order>` order index with `Int2ObjectHashMap` and re-measure GC

## TODO / extend
- [ ] Add the real SBE schema + generated codecs and benchmark them against `OrderFlyweight`
- [ ] Add an off-heap `UnsafeBuffer` over `ByteBuffer.allocateDirect` variant
