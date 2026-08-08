# Chapter 8 — Fast Decisioning

> 📖 **Deep dive (learn):** [`docs/chapters/08-decisioning.md`](../docs/chapters/08-decisioning.md)
> — rules vs RETE vs CEP, bitset evaluation + live enable/disable, RoaringBitmap membership,
> DAG/dataflow dirty propagation, timer-wheel scheduling. This README is just **how to run**.

## What this module proves
Deciding fast: evaluate many rules per request with a one-AND kill switch (bitset), recompute derived
values incrementally as inputs stream (DAG dataflow), and where the heavyweight rule/CEP engines
(Drools, Esper) actually belong.

## Run the hot-path core (default build)
```bash
mvn -q -pl phase8-decisioning compile exec:java -Dexec.mainClass=com.learning.hft.decisioning.bitset.BitsetRuleEngineDemo
mvn -q -pl phase8-decisioning compile exec:java -Dexec.mainClass=com.learning.hft.decisioning.dataflow.ScheduledSkewDemo
mvn -q -pl phase8-decisioning compile exec:java -Dexec.mainClass=com.learning.hft.decisioning.membership.SymbolSetDemo

# Benchmark: N rules/order, and the ~zero cost of disabled rules
mvn -Pbench -pl phase8-decisioning package
java -jar phase8-decisioning/target/benchmarks.jar Bitset -prof gc
```

## Run the framework demos (opt-in profile)
Esper (CEP) and Drools (RETE) are **off-hot-path** and heavy, so they build only with `-Pframeworks`
(keeps the default reactor light and always-green — same isolation idea as phase4/phase7):
```bash
mvn -q -Pframeworks -pl phase8-decisioning compile exec:java -Dexec.mainClass=com.learning.hft.decisioning.frameworks.esper.EsperCepDemo
mvn -q -Pframeworks -pl phase8-decisioning compile exec:java -Dexec.mainClass=com.learning.hft.decisioning.frameworks.drools.DroolsRulesDemo
```

## Key files → concept
| File | Demonstrates |
|---|---|
| [`BitsetRuleEngine`](src/main/java/com/learning/hft/decisioning/bitset/BitsetRuleEngine.java) | bitset applicability + enabled mask, branchless bit iteration, live enable/disable |
| [`DataflowGraph`](src/main/java/com/learning/hft/decisioning/dataflow/DataflowGraph.java) | DAG topo-sort + dirty propagation (incremental recompute) |
| [`ScheduledSkewDemo`](src/main/java/com/learning/hft/decisioning/dataflow/ScheduledSkewDemo.java) | timer-wheel scheduled activation (skew from time T) |
| [`SymbolSetDemo`](src/main/java/com/learning/hft/decisioning/membership/SymbolSetDemo.java) | RoaringBitmap vs BitSet for restricted-symbol membership |
| [`EsperCepDemo`](src/frameworks/java/com/learning/hft/decisioning/frameworks/esper/EsperCepDemo.java) | CEP: EPL window + spike detection (off hot path) |
| [`DroolsRulesDemo`](src/frameworks/java/com/learning/hft/decisioning/frameworks/drools/DroolsRulesDemo.java) | RETE ruleset in-memory (off hot path) |

## Tests
`mvn -q -pl phase8-decisioning test` — bitset engine (incl. live toggle + applicable mask) and
dataflow (dirty propagation / incrementality) are unit-tested.

## Capstone
POC 4 ([`PreTradeRuleGateway`](../capstone/src/main/java/com/learning/hft/capstone/rulegateway/PreTradeRuleGateway.java) +
[`QuoteSkewEngine`](../capstone/src/main/java/com/learning/hft/capstone/rulegateway/QuoteSkewEngine.java))
reuses this module end-to-end.
