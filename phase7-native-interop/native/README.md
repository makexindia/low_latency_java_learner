# Building the JNI example

JNI requires a C toolchain, so it is built manually (not by the Maven reactor). Steps:

## 1. Generate the JNI header
```bash
cd phase7-native-interop
javac -h native -d target/jni-classes \
  src/main/java/com/learning/hft/nativeinterop/JniReference.java
# produces native/com_learning_hft_nativeinterop_JniReference.h
```

## 2. Compile the shared library
Point the compiler at your JDK's JNI headers (`$JAVA_HOME/include` and the OS-specific subdir).

**Linux (gcc):**
```bash
gcc -shared -fPIC -o native/libjnistrlen.so \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  native/strlen.c
```

**macOS (clang):**
```bash
clang -shared -fPIC -o native/libjnistrlen.dylib \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
  native/strlen.c
```

**Windows (MSVC `cl`):**
```bat
cl /LD native\strlen.c ^
  /I "%JAVA_HOME%\include" /I "%JAVA_HOME%\include\win32" ^
  /Fe:native\jnistrlen.dll
```

## 3. Run
```bash
java --enable-preview \
  -Djava.library.path=phase7-native-interop/native \
  -cp phase7-native-interop/target/classes \
  com.learning.hft.nativeinterop.JniReference
```

If the library isn't on `java.library.path`, `JniReference.main` prints a friendly message instead of
crashing.
