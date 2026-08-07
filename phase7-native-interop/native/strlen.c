/*
 * JNI implementation of com.learning.hft.nativeinterop.JniReference.strlen.
 *
 * The function name is the mangled JNI form: Java_<fully_qualified_class>_<method>, with '.' -> '_'.
 * Generate the matching header with:  javac -h native src/main/java/.../JniReference.java
 */
#include <jni.h>
#include <string.h>

JNIEXPORT jlong JNICALL
Java_com_learning_hft_nativeinterop_JniReference_strlen(JNIEnv *env, jobject self, jstring s) {
    /* GetStringUTFChars pins/copies the Java string into a C string. In a GetPrimitiveArrayCritical
       region (not used here) the GC can be blocked — one reason JNI hurts on the hot path. */
    const char *cstr = (*env)->GetStringUTFChars(env, s, NULL);
    if (cstr == NULL) {
        return -1; /* OutOfMemoryError already thrown */
    }
    jlong len = (jlong) strlen(cstr);
    (*env)->ReleaseStringUTFChars(env, s, cstr);
    return len;
}
