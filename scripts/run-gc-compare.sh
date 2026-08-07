#!/usr/bin/env bash
# Run the same workload under G1, ZGC, and Shenandoah, logging pause times so you can compare.
#
# Usage: scripts/run-gc-compare.sh <classpath> <mainClass> [args...]
set -euo pipefail

CP="${1:?classpath required}"
MAIN="${2:?main class required}"
shift 2

for GC in UseG1GC UseZGC UseShenandoahGC; do
  echo "==================== -XX:+$GC ===================="
  # Some collectors may be unavailable on a given JDK build; don't abort the whole loop.
  java -XX:+UnlockExperimentalVMOptions "-XX:+$GC" \
    -Xms1g -Xmx1g \
    -Xlog:gc \
    -cp "$CP" "$MAIN" "$@" || echo "(skipped: $GC unavailable on this JDK)"
  echo
done
