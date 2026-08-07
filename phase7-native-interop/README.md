# Chapter 7 — Native Interop

> 📖 **Deep dive (learn):** [`docs/chapters/07-native-interop.md`](../docs/chapters/07-native-interop.md)
> — JNI vs Unsafe vs VarHandle vs FFM vs JNR-FFI, boundary costs, the decision guide.
> This README is just **how to run** the proofs.

## What this module proves
The five ways Java touches off-heap memory and native code, on the historical arc from `Unsafe` → FFM.

## ⚠️ Preview flag
The FFM API is **preview in JDK 21**, so this module is compiled with `--enable-preview` and **every**
class here needs `--enable-preview` to run.

## Run it
```bash
mvn -q -pl phase7-native-interop package

CP=phase7-native-interop/target/classes
java --enable-preview -cp "$CP" com.learning.hft.nativeinterop.UnsafeDemo
java --enable-preview -cp "$CP" com.learning.hft.nativeinterop.VarHandleDemo
java --enable-preview -cp "$CP" com.learning.hft.nativeinterop.ForeignMemoryDemo
# JNR needs jnr-ffi on the classpath — easiest via the dependency plugin or your IDE:
mvn -q -pl phase7-native-interop dependency:build-classpath -Dmdep.outputFile=cp.txt
java --enable-preview -cp "$CP:$(cat phase7-native-interop/cp.txt)" com.learning.hft.nativeinterop.JnrExample
```
`UnsafeDemo` may print an illegal-access warning — expected; that's the deprecation in action.

## Key files → concept
| File | Demonstrates | Status |
|---|---|---|
| [`UnsafeDemo`](src/main/java/com/learning/hft/nativeinterop/UnsafeDemo.java) | off-heap malloc/free, raw access | deprecated — read, don't write |
| [`VarHandleDemo`](src/main/java/com/learning/hft/nativeinterop/VarHandleDemo.java) | acquire/release publish, CAS | **current** for on-heap |
| [`ForeignMemoryDemo`](src/main/java/com/learning/hft/nativeinterop/ForeignMemoryDemo.java) | `Arena`/`MemorySegment` + C downcall | FFM (preview in 21) |
| [`JnrExample`](src/main/java/com/learning/hft/nativeinterop/JnrExample.java) | call C via a Java interface | mature 3rd-party |
| [`JniReference`](src/main/java/com/learning/hft/nativeinterop/JniReference.java) + [`native/`](native) | classic JNI bridge | legacy — [build steps](native/README.md) |

## Decision guide
On-heap atomic/ordered field access → **VarHandle**. Off-heap memory / mmap → **FFM `MemorySegment`**.
Call C: **FFM** (JDK 22+) → **JNR-FFI** (quick, no glue) → **JNI** (legacy only).
