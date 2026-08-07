# Chapter 6 — Systems Internals

> 📖 **Deep dive (learn):** [`docs/chapters/06-systems-internals.md`](../docs/chapters/06-systems-internals.md)
> — CPU/GPU architecture, virtual & shared memory, TCP/UDP/multicast, kernel bypass, Linux internals.
> This README is just **how to run** the proofs.

## What this module proves
The layer beneath the JVM, with runnable Java: memory-mapped files, zero-copy shared-memory IPC, UDP
multicast (the market-data transport), and the `TCP_NODELAY`/Nagle latency effect.

## Run it
```bash
# mmap: write/read a file as memory
mvn -q -pl phase6-systems-internals exec:java -Dexec.mainClass=com.learning.hft.systems.MemoryMappedFileDemo

# Nagle vs TCP_NODELAY loopback RTT
mvn -q -pl phase6-systems-internals exec:java -Dexec.mainClass=com.learning.hft.systems.TcpNoDelayDemo

# shared-memory IPC (in-process demo; see javadoc for two-terminal cross-process)
mvn -q -pl phase6-systems-internals exec:java -Dexec.mainClass=com.learning.hft.systems.SharedMemoryIpcDemo

# UDP multicast (two terminals, after `mvn -q -pl phase6-systems-internals package`)
java -cp phase6-systems-internals/target/classes com.learning.hft.systems.UdpMulticastDemo receiver
java -cp phase6-systems-internals/target/classes com.learning.hft.systems.UdpMulticastDemo sender
```

## Key files → concept
| File | Demonstrates |
|---|---|
| [`MemoryMappedFileDemo`](src/main/java/com/learning/hft/systems/MemoryMappedFileDemo.java) | mmap: file as memory, page cache, `force`/msync |
| [`SharedMemoryIpcDemo`](src/main/java/com/learning/hft/systems/SharedMemoryIpcDemo.java) | zero-copy IPC via a shared mapped region |
| [`UdpMulticastDemo`](src/main/java/com/learning/hft/systems/UdpMulticastDemo.java) | IGMP group join, one-send-to-many |
| [`TcpNoDelayDemo`](src/main/java/com/learning/hft/systems/TcpNoDelayDemo.java) | Nagle vs `TCP_NODELAY` RTT |

## Not code (read the deep dive)
CPU pipeline/OoO/branch-prediction/SIMD, GPU SIMT (and when *not* to use it), TLB/huge pages, kernel
bypass (DPDK/ef_vi/AF_XDP/io_uring), Linux scheduler/`isolcpus`/IRQ affinity/C-states/NUMA/`perf`.
