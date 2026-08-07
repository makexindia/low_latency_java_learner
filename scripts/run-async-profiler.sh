#!/usr/bin/env bash
# Attach async-profiler to a running JVM and emit a flame graph. No safepoint bias (perf_events).
# Download async-profiler from https://github.com/async-profiler/async-profiler and set ASYNC_PROFILER_HOME.
#
# Usage: scripts/run-async-profiler.sh <pid> [cpu|alloc|wall] [seconds]
#   cpu   -> where CPU time goes
#   alloc -> where allocation happens (your GC-pressure hunt)
#   wall  -> wall-clock (includes blocked/parked time)
set -euo pipefail

: "${ASYNC_PROFILER_HOME:?set ASYNC_PROFILER_HOME to your async-profiler install dir}"

PID="${1:?target JVM pid required}"
EVENT="${2:-cpu}"
DURATION="${3:-30}"
OUT="flamegraph-${EVENT}-${PID}.html"

"$ASYNC_PROFILER_HOME/bin/asprof" -d "$DURATION" -e "$EVENT" -f "$OUT" "$PID"
echo "wrote $OUT"
