/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * ARQUITECTURA:
 *   - AAudio OUTPUT: emite silencio. No inyecta tonos sintéticos.
 *   - Kalman corre sobre muestras escritas desde AudioRecord (Kotlin→SHM→nativeProcessCapture).
 *   - phase_error_rms refleja el error real de la señal capturada.
 */

#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <ctime>
#include <atomic>

#define LOG_TAG "IVANNA-Audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Hyperplane layout ────────────────────────────────────────────────────────
struct Hyperplane {
    int32_t  biquad_coefs[64][5];          // 1280 B
    float    kalman_state[3];              //   12 B
    uint8_t  poblacion_evolutiva[128][256];// 32768 B
    int16_t  temp_soc[10];                //   20 B
    uint8_t  sched_table[8][8][4][4][3];  // 3072 B
    uint64_t seq_counter;                 //    8 B
    uint8_t  active_buffer;               //    1 B
};

// ── Kalman cúbico ────────────────────────────────────────────────────────────
struct KalmanState {
    float phase = 0.0f;
    float freq  = 0.0f;   // rad/sample
    float chirp = 0.0f;
    float P[3][3];
    float R = 1e-4f;
    bool  initialized = false;
};

static void kalmanInit(KalmanState &k, int sampleRate) {
    k.phase = 0.0f;
    k.freq  = 440.0f * 2.0f * (float)M_PI / (float)sampleRate;
    k.chirp = 0.0f;
    memset(k.P, 0, sizeof(k.P));
    k.P[0][0] = 0.1f;
    k.P[1][1] = 1.0f;
    k.P[2][2] = 0.001f;
    k.R = 1e-4f;
    k.initialized = true;
}

static float kalmanStep(KalmanState &k, float measurement, float dt) {
    const float qPhase = 1e-8f;
    const float qFreq  = 1e-6f;
    const float qChirp = 1e-10f;

    float new_phase = k.phase + k.freq * dt + 0.5f * k.chirp * dt * dt;
    float new_freq  = k.freq  + k.chirp * dt;
    float new_chirp = k.chirp;

    k.P[0][0] += dt * dt * k.P[1][1] + qPhase;
    k.P[1][1] += qFreq;
    k.P[2][2] += qChirp;

    float S  = k.P[0][0] + k.R;
    float K0 = k.P[0][0] / S;
    float K1 = k.P[1][0] / S;
    float K2 = k.P[2][0] / S;
    float innov = measurement - new_phase;

    k.phase = new_phase + K0 * innov;
    k.freq  = new_freq  + K1 * innov;
    k.chirp = new_chirp + K2 * innov;

    k.P[0][0] *= (1.0f - K0);
    k.P[1][1] *= (1.0f - K1);
    k.P[2][2] *= (1.0f - K2);

    return innov;
}

// ── Engine ───────────────────────────────────────────────────────────────────
struct AudioEngine {
    AAudioStream *stream    = nullptr;
    Hyperplane   *hyperplane = nullptr;
    float  fusion_level     = 0.5f;
    int    sampleRate       = 48000;
    int    bitDepth         = 32;
    int64_t frameCounter    = 0;
    KalmanState kalman;
    std::atomic<float> phase_error_rms{0.0f};
};

static AudioEngine g_engine;
static float g_phaseErrorAcc   = 0.0f;
static int   g_phaseErrorCount = 0;

// ── Callback AAudio OUTPUT — emite SILENCIO ──────────────────────────────────
aaudio_data_callback_result_t audioCallback(
    AAudioStream * /*stream*/,
    void         * /*userData*/,
    void          *audioData,
    int32_t        numFrames
) {
    // Silencio puro: no se inyecta ningún tono sintético en la cadena de audio
    memset(audioData, 0, (size_t)numFrames * 2 * sizeof(float)); // stereo float

    g_engine.frameCounter += numFrames;

    if (g_engine.hyperplane) {
        g_engine.hyperplane->seq_counter++;
        g_engine.hyperplane->active_buffer = 0;
        g_engine.hyperplane->kalman_state[0] = g_engine.kalman.phase;
        g_engine.hyperplane->kalman_state[1] =
            g_engine.kalman.freq * g_engine.sampleRate / (2.0f * (float)M_PI);
        g_engine.hyperplane->kalman_state[2] = g_engine.kalman.chirp;
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// ── JNI ─────────────────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ivannafusion_AudioEngine_nativeCreateEngine(
        JNIEnv * /*env*/, jobject /*thiz*/, jint sampleRate, jint bitDepth) {
    g_engine.sampleRate = sampleRate;
    g_engine.bitDepth   = bitDepth;
    kalmanInit(g_engine.kalman, sampleRate);
    g_engine.phase_error_rms.store(0.0f);
    g_engine.frameCounter = 0;
    g_phaseErrorAcc       = 0.0f;
    g_phaseErrorCount     = 0;

    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED); // SHARED para coexistir
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setDataCallback(builder, audioCallback, nullptr);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &g_engine.stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK)
        LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));
    else
        LOGI("AudioEngine OK: %d Hz, %d bits (SHARED, silent output)", sampleRate, bitDepth);

    return reinterpret_cast<jlong>(&g_engine);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeStartProcessing(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/) {
    if (!g_engine.stream) return;
    aaudio_result_t r = AAudioStream_requestStart(g_engine.stream);
    if (r != AAUDIO_OK) LOGE("requestStart: %s", AAudio_convertResultToText(r));
    else LOGI("Audio processing started (silent output)");
}

JNIEXPORT jint JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetLatency(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/) {
    if (!g_engine.stream) return 0;
    int64_t framePosition = 0, presentationNs = 0;
    aaudio_result_t r = AAudioStream_getTimestamp(
        g_engine.stream, CLOCK_MONOTONIC, &framePosition, &presentationNs);
    if (r != AAUDIO_OK) return 0;
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t nowNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
    int64_t writtenFrames = AAudioStream_getFramesWritten(g_engine.stream);
    int64_t pendingFrames = writtenFrames - framePosition;
    if (pendingFrames < 0) pendingFrames = 0;
    int64_t latencyNs = presentationNs - nowNs +
                        pendingFrames * 1000000000LL / g_engine.sampleRate;
    if (latencyNs < 0) latencyNs = 0;
    return (jint)(latencyNs / 1000);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSetFusionLevel(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/, jfloat level) {
    g_engine.fusion_level = level;
    if (g_engine.hyperplane) {
        float *f = reinterpret_cast<float*>(g_engine.hyperplane->poblacion_evolutiva[0]);
        f[0] = level;
    }
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetPhaseError(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/) {
    return g_engine.phase_error_rms.load();
}

// Llamado desde Kotlin con muestras reales de AudioRecord (mono float)
JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeProcessCapture(
        JNIEnv *env, jobject /*thiz*/, jfloatArray samples, jint n) {
    jfloat *buf = env->GetFloatArrayElements(samples, nullptr);
    float dt = 1.0f / (float)g_engine.sampleRate;
    float acc = 0.0f;
    int   cnt = 0;
    for (int i = 0; i < n; i++) {
        float innov = kalmanStep(g_engine.kalman, buf[i], dt);
        acc += innov * innov;
        cnt++;
    }
    if (cnt > 0) {
        g_engine.phase_error_rms.store(std::sqrt(acc / cnt));
    }
    env->ReleaseFloatArrayElements(samples, buf, JNI_ABORT);

    // Actualizar SHM con estado Kalman actualizado
    if (g_engine.hyperplane) {
        g_engine.hyperplane->kalman_state[0] = g_engine.kalman.phase;
        g_engine.hyperplane->kalman_state[1] =
            g_engine.kalman.freq * g_engine.sampleRate / (2.0f * (float)M_PI);
        g_engine.hyperplane->kalman_state[2] = g_engine.kalman.chirp;
    }
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeDestroyEngine(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/) {
    if (g_engine.stream) {
        AAudioStream_requestStop(g_engine.stream);
        AAudioStream_close(g_engine.stream);
        g_engine.stream = nullptr;
    }
    LOGI("Audio engine destroyed");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSetHyperplane(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong address) {
    g_engine.hyperplane = reinterpret_cast<Hyperplane*>((uintptr_t)address);
    LOGI("Hyperplane connected at 0x%llx", (long long)address);
}

} // extern "C"
