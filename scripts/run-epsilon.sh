#!/usr/bin/env bash
# Run a main class under Epsilon GC (the no-op collector) to PROVE zero allocation.
# If the code allocates on the hot path, the heap fills and the JVM dies with OutOfMemoryError.
# Survival == proof of zero allocation.
#
# Usage: scripts/run-epsilon.sh <classpath> <mainClass> [args...]
#   e.g. scripts/run-epsilon.sh phase5-jvm-hardware/target/classes com.learning.hft.jvm.EpsilonProof --mode=reuse
set -euo pipefail

CP="${1:?classpath required}"
MAIN="${2:?main class required}"
shift 2

exec java \
  -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC \
  -Xms512m -Xmx512m \
  -Xlog:gc \
  -cp "$CP" "$MAIN" "$@"
