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

static int create_android_shm(const char* name, size_t size) {
    int fd = ASharedMemory_create(name, size);
    if (fd >= 0) {
        if (ASharedMemory_setProt(fd, PROT_READ | PROT_WRITE) == 0) {
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

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_memfdCreate(JNIEnv *env, jobject, jstring name, jint) {
    const char *cname = env->GetStringUTFChars(name, nullptr);
    int fd = create_android_shm(cname, 2 * 1024 * 1024);
    env->ReleaseStringUTFChars(name, cname);
    return fd;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeFtruncate(JNIEnv *, jobject, jint fd, jlong length) {
    return ftruncate(fd, (off_t)length);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeMlock(JNIEnv *, jobject, jlong addr, jlong len) {
    return mlock(reinterpret_cast<void*>(addr), (size_t)len);
}
