/*
 * IVANNA-FUSION TRASCENDENTAL
 * SHM Hyperplane - Android Shared Memory (ASharedMemory)
 */

#include <jni.h>
#include <android/log.h>
#include <android/sharedmem.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <cstring>

#define LOG_TAG "IVANNA-SHM-NATIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Crear memoria compartida con ASharedMemory (API 26+)
static int create_android_shm(const char* name, size_t size) {
    int fd = ASharedMemory_create(name, size);
    if (fd >= 0) {
        int prot = PROT_READ | PROT_WRITE;
        if (ASharedMemory_setProt(fd, prot) == 0) {
            LOGI("ASharedMemory creado: %s, size=%zu, fd=%d", name, size, fd);
            return fd;
        } else {
            LOGE("ASharedMemory_setProt falló para %s", name);
            close(fd);
            return -1;
        }
    } else {
        LOGE("ASharedMemory_create falló para %s: %s", name, strerror(errno));
        return -1;
    }
}

// JNI: inicializar el hiperplano (llamado desde ShmManager.initialize)
extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_memfdCreate(JNIEnv *env, jobject thiz,
                                             jstring name, jint flags) {
    const char *cname = env->GetStringUTFChars(name, nullptr);
    int fd = create_android_shm(cname, 2 * 1024 * 1024); // 2 MiB
    env->ReleaseStringUTFChars(name, cname);
    return (jint)fd;
}

// JNI: truncar (ftruncate) el descriptor
extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeFtruncate(JNIEnv *env, jobject thiz,
                                                 jint fd, jlong length) {
    int result = ftruncate(fd, (off_t)length);
    if (result != 0) {
        LOGE("ftruncate(%d, %ld) falló: %s", fd, (long)length, strerror(errno));
    }
    return (jint)result;
}

// JNI: mlock (bloquear en RAM)
extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeMlock(JNIEnv *env, jobject thiz,
                                             jlong address, jlong length) {
    void *ptr = reinterpret_cast<void*>(address);
    int result = mlock(ptr, (size_t)length);
    if (result != 0) {
        LOGE("mlock(%p, %ld) falló: %s", ptr, (long)length, strerror(errno));
    } else {
        LOGI("mlock OK en %p, %ld bytes", ptr, (long)length);
    }
    return (jint)result;
}
