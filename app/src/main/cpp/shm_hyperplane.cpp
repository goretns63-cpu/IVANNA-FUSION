/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 * Prohibida la copia, distribución, ingeniería inversa o cualquier uso no autorizado.
 */

#include <jni.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <linux/memfd.h>
#include <unistd.h>
#include <cerrno>
#include <android/log.h>

#define LOG_TAG "IVANNA-SHM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeMlock(JNIEnv *env, jobject thiz, jlong address, jlong length) {
    int result = mlock(reinterpret_cast<void*>(static_cast<uintptr_t>(address)), static_cast<size_t>(length));
    if (result != 0) {
        LOGE("mlock failed: %d", errno);
    } else {
        LOGI("mlock success: %ld bytes at 0x%llx", static_cast<long>(length), static_cast<long long>(address));
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_memfdCreate(JNIEnv *env, jobject thiz, jstring name, jint flags) {
    const char *cname = env->GetStringUTFChars(name, nullptr);
    
    #ifdef __NR_memfd_create
        int fd = syscall(__NR_memfd_create, cname, static_cast<unsigned int>(flags));
    #else
        int fd = -1;
        LOGE("memfd_create not available on this kernel");
    #endif
    
    env->ReleaseStringUTFChars(name, cname);
    return fd;
}

} // extern "C"
