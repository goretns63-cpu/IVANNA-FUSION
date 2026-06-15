#include <jni.h>
#include <android/log.h>
#include <sys/mman.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_memfdCreate(JNIEnv*, jobject, jstring, jint) {
    return -1; // Ya no se usa
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeFtruncate(JNIEnv*, jobject, jint, jlong) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeMlock(JNIEnv*, jobject, jlong addr, jlong len) {
    return mlock(reinterpret_cast<void*>(addr), len);
}
