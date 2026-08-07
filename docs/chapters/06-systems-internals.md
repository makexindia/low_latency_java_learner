# Chapter 6 — Systems Internals: CPU, GPU, Memory, Networking, Linux

> **Proof module:** [`phase6-systems-internals`](../../phase6-systems-internals) ·
> **Why:** these are the JD lines ("CPU/GPU architecture, shared memory, TCP/UDP, Linux internals")
> that a normal business-app developer never touches — and the interview differentiators.

This is the layer beneath the JVM. You don't write most of it in Java, but you must understand it to
reason about latency, and to know which JVM/OS knobs to turn.

---

## 1. CPU architecture (beyond the cache)

Chapter 0 covered caches and coherence. The rest of the modern core:

- **Pipelining & superscalar**: an instruction is split into stages (fetch, decode, execute, …) and
  the core has *multiple* execution units, retiring ~4 instructions/cycle when fed well. The metric is
  **IPC (instructions per cycle)** — `perf stat` reports it; low IPC on hot code usually means you're
  **stalled on memory** (Ch.0) or mispredicting branches.
- **Out-of-order (OoO) execution**: the core executes independent instructions while a slow one (e.g.
  a cache miss) is pending, then retires them in program order. This is what hides some memory
  latency — and why *independent* work (ILP) is faster than a dependency chain.
- **Branch prediction & speculation**: the core guesses branch outcomes and speculatively executes
  ahead; a **misprediction** flushes the pipeline (~15–20 cycles). Hence **branchless code** and
  **predictable branches** matter on the hot path. (Spectre/Meltdown were abuses of speculation.)
- **SIMD (SSE/AVX/AVX-512)**: one instruction operates on a vector (e.g. 8 doubles). The JIT
  auto-vectorizes simple loops; Java's **Vector API** (incubator) exposes it explicitly. Great for
  bulk numeric work (risk, analytics); rarely the bottleneck on a matching hot path.
- **Prefetchers**: hardware detects sequential access and pre-loads lines — another reason
  struct-of-arrays sequential scans are fast (Ch.0).
- **TSC (Time Stamp Counter)**: a per-core cycle counter; `System.nanoTime()` reads it (via the OS).
  On modern CPUs it's invariant across frequency scaling, but reading it and cross-core comparisons
  have caveats — for latency histograms, sample deltas on one thread.

## 2. GPU architecture — and honest guidance on when it matters

A GPU is **SIMT (Single Instruction, Multiple Thread)**: thousands of lightweight threads grouped into
**warps** (32 threads) that execute the same instruction in lockstep over a huge register file, with
high-bandwidth memory. It's a **throughput** device, not a **latency** device.

**Where it fits in finance:** batch/parallel numeric workloads — Monte Carlo risk (VaR, XVA), options
pricing across large grids, backtesting, and ML model training/inference. **Where it does *not* fit:**
the order-matching / market-data hot path — PCIe transfer latency (µs–ms) and kernel-launch overhead
dwarf your nanosecond budget, and the work is branchy and serial, not SIMT-friendly. Java access is
via **JCuda**/**JOCL** (bindings) or **TornadoVM** (JIT Java to GPU). **Senior framing:** "GPUs win on
throughput-bound parallel math like risk and ML; they're the wrong tool for the latency-bound matching
path, where the PCIe/launch overhead alone exceeds the entire time budget." Saying *that* shows
judgment; claiming you'd GPU-accelerate a matching engine shows the opposite.

---

## 3. Memory management: virtual memory, pages, and huge pages

Every process sees a private **virtual address space**. The MMU translates virtual → physical addresses
in **page** units (default 4 KB) via **page tables**. Recent translations are cached in the **TLB
(Translation Lookaside Buffer)**; a **TLB miss** walks the page table (slow), and a **page fault**
(page not resident) traps into the kernel.

Consequences for low latency:
- **`-XX:+AlwaysPreTouch`** faults in all heap pages at startup so you don't pay page faults during the
  first real requests.
- **Huge pages** (2 MB / 1 GB) mean one TLB entry covers far more memory → fewer TLB misses on large
  working sets. Use explicit huge pages (`-XX:+UseLargePages`) for predictability; be wary of
  **Transparent Huge Pages (THP)**, whose background defrag ("khugepaged") causes jitter — many
  low-latency shops disable THP.
- **No swapping.** A latency-critical process must never be swapped to disk; lock memory / disable swap
  on the box.

## 4. Shared memory — zero-copy IPC

Two processes can map the **same** physical pages and communicate by reading/writing memory, with **no
kernel copy** and no syscall per message. Mechanisms:
- **`mmap` a file with `MAP_SHARED`** — both processes map the same file; writes by one are visible to
  the other via the page cache. (This is how **Aeron IPC** and **Chronicle Queue** do cross-process
  messaging, Ch.4.)
- **POSIX shared memory** (`shm_open` + `mmap`) / **`/dev/shm`** — a tmpfs-backed region that lives in
  RAM (no disk write-back), ideal for pure IPC ring buffers.
- From Java: `FileChannel.map(...)` returns a `MappedByteBuffer` over a file; point two JVMs at the
  same file and you have shared memory. See
  [`MemoryMappedFileDemo`](../../phase6-systems-internals/src/main/java/com/learning/hft/systems/MemoryMappedFileDemo.java)
  and the two-role
  [`SharedMemoryIpcDemo`](../../phase6-systems-internals/src/main/java/com/learning/hft/systems/SharedMemoryIpcDemo.java),
  which passes a counter between two processes through a mapped file with a spin-wait — no sockets.

![diagram](./diagrams/06-systems-internals-1.svg)

---

## 5. Networking: TCP vs UDP, multicast, and kernel bypass

### TCP — reliable, ordered, and full of latency traps
TCP gives you a reliable ordered byte stream, paid for with mechanisms that hurt latency if you don't
tune them:
- **Nagle's algorithm** buffers small writes to coalesce them → adds up to ~40 ms delay. **Disable it**
  with `TCP_NODELAY` for request/response and trading traffic. See
  [`TcpNoDelayDemo`](../../phase6-systems-internals/src/main/java/com/learning/hft/systems/TcpNoDelayDemo.java)
  (`Socket.setTcpNoDelay(true)`), which measures loopback RTT with Nagle on vs off.
- **Delayed ACK** (peer holds the ACK ~40 ms hoping to piggyback) interacts pathologically with Nagle
  — the classic "40 ms stall". `TCP_QUICKACK` mitigates on Linux.
- **Head-of-line blocking**: one lost segment stalls everything behind it (why market data prefers UDP).
- **Congestion control** (Reno/CUBIC/BBR), slow start, and the socket **send/receive buffers** all
  shape throughput and latency.
- **3-way handshake** (SYN/SYN-ACK/ACK) adds an RTT to connection setup — keep connections warm.

### UDP — connectionless, and the basis of market data
No handshake, no ordering, no delivery guarantee, no head-of-line blocking. You add exactly the
reliability you need on top (as Aeron does with position/NAK, Ch.4). **Multicast** is UDP's superpower:
one send reaches many receivers who **join a group** (IGMP); the network replicates the packet, so an
exchange feeds thousands of subscribers at line rate without N copies. See
[`UdpMulticastDemo`](../../phase6-systems-internals/src/main/java/com/learning/hft/systems/UdpMulticastDemo.java)
(a `DatagramChannel` joining a group via `MembershipKey`).

### Kernel bypass — the real HFT frontier
Even a perfectly tuned socket pays for the kernel network stack, interrupts, and a copy into user
space (µs-scale). The lowest-latency systems **bypass the kernel** entirely:
- **DPDK** — poll-mode drivers in user space; the NIC DMAs packets straight into user buffers.
- **Solarflare Onload / ef_vi**, **Exablaze**, **AF_XDP** — user-space or accelerated datapaths.
- **NIC hardware timestamping + PTP (Precision Time Protocol)** — sub-microsecond clock sync across
  machines so you can measure one-way latency and order events across hosts.
- **`io_uring`** — modern Linux async I/O with shared submission/completion ring buffers (mmap!) that
  slashes syscall overhead for disk/network.
- **`SO_BUSY_POLL`** — have the socket busy-poll the NIC instead of waiting for an interrupt.

These are mostly C/native, but Java systems reach them via JNI/FFM (Ch.7) or by running the datapath in
a sidecar. **Senior framing:** know that the kernel stack is the floor for socket latency, and that
bypass (DPDK/ef_vi) is how you get under it.

---

## 6. Linux internals for low latency

- **Scheduler**: the default **CFS** is fair, not latency-optimal. `SCHED_FIFO`/`SCHED_RR` (real-time
  classes, via `chrt`) let a hot thread run until it yields. Combine with pinning.
- **Core isolation**: `isolcpus=`, `nohz_full=` (stop the periodic scheduler tick on a core), and
  `rcu_nocbs=` (offload RCU callbacks) at boot give a core almost entirely to your pinned thread —
  minimal jitter. `taskset`/`numactl` place the thread and its memory.
- **IRQ affinity**: move device interrupts *off* your hot cores (`/proc/irq/*/smp_affinity`) so a NIC
  interrupt doesn't preempt the matcher.
- **Power management**: set the CPU governor to `performance` and disable deep **C-states** for hot
  cores — a core that sleeps takes microseconds to wake (`cpupower`).
- **NUMA**: on multi-socket boxes, remote-node memory is slower; keep a thread and its data on the same
  node (`numactl --cpunodebind --membind`).
- **Observability**: `/proc` (per-process/thread stats), **`perf`** (hardware counters, `perf stat`,
  `perf record`), **ftrace**, and **eBPF/bcc** (programmable in-kernel tracing) — how you find *where*
  the jitter comes from.

---

## Senior interview answers

- **"TCP or UDP for market data, and why?"** UDP (usually multicast): no head-of-line blocking, one
  send fans out to many subscribers via IGMP; add your own position/NAK reliability (Aeron-style).
- **"What's `TCP_NODELAY`?"** Disables Nagle's small-write coalescing; essential for request/response
  latency, especially given the Nagle + delayed-ACK 40 ms stall.
- **"How do you get under socket latency?"** Kernel bypass — DPDK / Solarflare ef_vi / AF_XDP — so the
  NIC DMAs into user space, skipping the kernel stack, interrupts, and the copy.
- **"How do you give a thread a whole core?"** `isolcpus`+`nohz_full` to isolate it, pin with
  `taskset`/affinity, move IRQs off it, `performance` governor, disable deep C-states, keep its memory
  NUMA-local.
- **"When would you use a GPU here?"** Throughput-bound parallel math (Monte Carlo risk, ML) — never
  the latency-bound matching path, where PCIe/launch overhead exceeds the whole budget.
