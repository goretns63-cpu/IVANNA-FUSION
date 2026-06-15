/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <ctime>
#include <arm_neon.h>

#define LOG_TAG "IVANNA-Audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Hyperplane layout (debe coincidir con ShmManager.kt) ────────────────────
struct Hyperplane {
    int32_t  biquad_coefs[64][5];
    float    kalman_state[3];
    uint8_t  poblacion_evolutiva[128][256];
    int16_t  temp_soc[10];
    uint8_t  sched_table[8][8][4][4][3];
    uint64_t seq_counter;
    uint8_t  active_buffer;
};

// ── Kalman cúbico interno ────────────────────────────────────────────────────
struct KalmanState {
    float phase = 0.0f;
    float freq  = 440.0f;   // Hz – valor sensato, converge rápido
    float chirp = 0.0f;
    float P[3][3];
    float R = 0.001f;       // ruido medición más bajo → sigue mejor la señal
    bool  initialized = false;
};

struct AudioEngine {
    AAudioStream *stream    = nullptr;
    Hyperplane   *hyperplane = nullptr;
    float  fusion_level     = 0.5f;
    int    sampleRate       = 48000;
    int    bitDepth         = 32;
    int64_t frameCounter    = 0;

    float bufferA[512];
    float bufferB[512];
    float *activeBuffer  = bufferA;
    float *processBuffer = bufferB;

    KalmanState kalman;
    float phase_error_rms = 0.0f;
};

static AudioEngine g_engine;

// ── Kalman ──────────────────────────────────────────────────────────────────
static void kalmanInit(KalmanState &k, int sampleRate) {
    k.phase = 0.0f;
    // freq interna en rad/sample: 440 Hz * 2π / fs
    k.freq  = 440.0f * 2.0f * (float)M_PI / (float)sampleRate;
    k.chirp = 0.0f;
    memset(k.P, 0, sizeof(k.P));
    k.P[0][0] = 0.01f;
    k.P[1][1] = 1.0f;
    k.P[2][2] = 0.001f;
    k.R       = 1e-4f;  // buffer sintético deterministico → medición muy confiable
    k.initialized = true;
    LOGI("Kalman init: fs=%d, freq_rad=%.6f (440 Hz)", sampleRate, k.freq);
}

static float kalmanStep(KalmanState &k, float measurement, float dt) {
    // Ruido de proceso pequeño: el engine es deterministico
    const float qPhase = 1e-6f;
    const float qFreq  = 1e-4f;
    const float qChirp = 1e-8f;

    // Predict
    float new_phase = k.phase + k.freq * dt + 0.5f * k.chirp * dt * dt;
    float new_freq  = k.freq  + k.chirp * dt;
    float new_chirp = k.chirp;

    k.P[0][0] += dt * dt * k.P[1][1] + qPhase;
    k.P[1][1] += qFreq;
    k.P[2][2] += qChirp;

    // Update: H = [1,0,0]
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

// ── DSP ─────────────────────────────────────────────────────────────────────
static void processAudioBlock(float *input, float *output, int numFrames,
                               float fusion, KalmanState &k, int sampleRate,
                               float *phaseErrorAcc, int *phaseErrorCount) {
    float dt = 1.0f / static_cast<float>(sampleRate);

    for (int i = 0; i < numFrames; i++) {
        float in_sample = input[i];
        float innov = kalmanStep(k, in_sample, dt);

        *phaseErrorAcc   += innov * innov;
        (*phaseErrorCount)++;

        // fusion=0 → señal pura (sin artefactos Kalman)
        // fusion>0 → mezcla con predicción; la predicción converge rápido
        float predicted = k.phase;
        // Limitar la contribución predicha para evitar distorsión
        predicted = std::fmaxf(-1.0f, std::fminf(1.0f, predicted));
        output[i] = in_sample * (1.0f - fusion) + predicted * fusion * 0.05f;
        // 0.05: el phase Kalman está en radianes acumulados, no amplitud normalizada;
        // escalar para que no sature la salida de audio
    }

    g_engine.frameCounter += numFrames;
}

// ── Callback AAudio ─────────────────────────────────────────────────────────
static float g_phaseErrorAcc   = 0.0f;
static int   g_phaseErrorCount = 0;

aaudio_data_callback_result_t audioCallback(
    AAudioStream * /*stream*/,
    void         * /*userData*/,
    void          *audioData,
    int32_t        numFrames
) {
    float *out     = static_cast<float*>(audioData);
    float *current = g_engine.activeBuffer;
    float *next    = (current == g_engine.bufferA) ? g_engine.bufferB : g_engine.bufferA;

    processAudioBlock(current, out, numFrames,
                      g_engine.fusion_level,
                      g_engine.kalman,
                      g_engine.sampleRate,
                      &g_phaseErrorAcc, &g_phaseErrorCount);

    if (g_phaseErrorCount > 0) {
        g_engine.phase_error_rms = std::sqrt(g_phaseErrorAcc / g_phaseErrorCount);
        g_phaseErrorAcc   = 0.0f;
        g_phaseErrorCount = 0;
    }

    g_engine.activeBuffer = next;

    if (g_engine.hyperplane) {
        g_engine.hyperplane->seq_counter++;
        g_engine.hyperplane->active_buffer =
            (g_engine.activeBuffer == g_engine.bufferA) ? 0 : 1;
        // kalman_state[0] = fase acumulada (rad)
        // kalman_state[1] = frecuencia en Hz (no rad/sample)
        // kalman_state[2] = chirp (rad/sample²)
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
    g_engine.phase_error_rms = 0.0f;
    g_engine.frameCounter    = 0;
    g_phaseErrorAcc          = 0.0f;
    g_phaseErrorCount        = 0;

    // Buffers con tono 440 Hz a amplitud baja
    float dt = 1.0f / static_cast<float>(sampleRate);
    for (int i = 0; i < 512; i++) {
        float t = i * dt;
        g_engine.bufferA[i] = std::sin(2.0f * (float)M_PI * 440.0f * t) * 0.05f;
        g_engine.bufferB[i] = std::sin(2.0f * (float)M_PI * 440.0f * (t + 512 * dt)) * 0.05f;
    }

    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setDataCallback(builder, audioCallback, nullptr);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &g_engine.stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK)
        LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));

    int32_t bufSz = g_engine.stream
        ? AAudioStream_getBufferSizeInFrames(g_engine.stream) : 0;
    LOGI("AudioEngine creado: %d Hz, %d bits, buffer: %d frames", sampleRate, bitDepth, bufSz);

    return reinterpret_cast<jlong>(&g_engine);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeStartProcessing(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/) {
    if (!g_engine.stream) return;
    aaudio_result_t r = AAudioStream_requestStart(g_engine.stream);
    if (r != AAUDIO_OK) LOGE("requestStart: %s", AAudio_convertResultToText(r));
    else LOGI("Audio processing started");
}

JNIEXPORT jint JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetLatency(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong /*handle*/) {
    if (!g_engine.stream) return 0;
    int64_t framePosition = 0, presentationNs = 0;
    aaudio_result_t r = AAudioStream_getTimestamp(
        g_engine.stream, CLOCK_MONOTONIC, &framePosition, &presentationNs);
    if (r != AAUDIO_OK) return 0;
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
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
    return g_engine.phase_error_rms;
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
