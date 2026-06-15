/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 * Prohibida la copia, distribución, ingeniería inversa o cualquier uso no autorizado.
 * Quien infrinja será perseguido penal y civilmente.
 */

package com.ivannafusion

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log

private const val TAG = "IVANNA-Audio"

object AudioEngine {
    private var audioTrack: AudioTrack? = null
    private var nativeHandle: Long = 0
    var initialized = false
    private var appContext: Context? = null   // 🔹 Guardamos el contexto para reiniciar

    var audio_fs_hz: Int = 48_000
    var audio_bit_depth: Int = 32
    var audio_latencia_us: Int = 0

    fun initialize(context: Context) {
        appContext = context.applicationContext   // 🔹 Almacenamos contexto
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nativeRate = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48_000
        audio_fs_hz = nativeRate.coerceIn(8_000, 192_000)

        nativeHandle = nativeCreateEngine(audio_fs_hz, audio_bit_depth)
        if (nativeHandle == 0L) {
            Log.e(TAG, "nativeCreateEngine retornó 0; stream AAudio no disponible")
        }

        // Conectar hiperplano SHM al engine nativo
        val shmBuf = ShmManager.getBuffer()
        if (shmBuf != null && nativeHandle != 0L) {
            try {
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(shmBuf)
                nativeSetHyperplane(address)
                Log.i(TAG, "Hyperplane SHM conectado al engine nativo")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo conectar hyperplane: ${e.message}")
            }
        }

        startAudioTrack()
        if (nativeHandle != 0L) nativeStartProcessing(nativeHandle)
        initialized = true
    }

    private fun startAudioTrack() {
        var bufferSize = AudioTrack.getMinBufferSize(
            audio_fs_hz,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (bufferSize <= 0) {
            audio_fs_hz = 48_000
            bufferSize = AudioTrack.getMinBufferSize(
                audio_fs_hz,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
        }

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(audio_fs_hz)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando AudioTrack: ${e.message}")
        }
    }

    fun getLatencyMicros(): Int {
        audio_latencia_us = if (nativeHandle != 0L) nativeGetLatency(nativeHandle) else 0
        return audio_latencia_us
    }

    fun setFusionLevel(level: Float) {
        if (nativeHandle != 0L) nativeSetFusionLevel(nativeHandle, level.coerceIn(0f, 1f))
    }

    fun getPhaseErrorRms(): Float =
        if (nativeHandle != 0L) nativeGetPhaseError(nativeHandle) else 0f

    // ── Evolutionary kernel ──────────────────────────────────────────────────
    fun initializeEvolution() {
        if (nativeHandle != 0L) nativeInitializeEvolution()
    }

    fun getBestFitness(): Float =
        if (nativeHandle != 0L) nativeGetBestFitness() else 0f

    fun getGeneration(): Int =
        if (nativeHandle != 0L) nativeGetGeneration() else 0

    fun evolveStep() {
        if (nativeHandle != 0L) nativeEvolveStep()
    }

    // ── Phase oracle ─────────────────────────────────────────────────────────
    fun predictSamples(input: FloatArray, output: FloatArray) {
        if (nativeHandle != 0L && input.size == output.size)
            nativePredictSamples(nativeHandle, input, output, input.size)
    }

    fun shutdown() {
        if (nativeHandle != 0L) nativeDestroyEngine(nativeHandle)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        initialized = false
    }

    // 🔹 NUEVO: Reinicio completo del motor de audio
    fun restart() {
        Log.w(TAG, "Restarting audio engine...")
        shutdown()
        // Pequeña pausa para liberación completa de recursos
        Thread.sleep(50)
        appContext?.let { initialize(it) }
            ?: Log.e(TAG, "Cannot restart audio: context is null")
        Log.i(TAG, "Audio engine restarted successfully")
    }

    // ── JNI declarations ─────────────────────────────────────────────────────
    private external fun nativeCreateEngine(sampleRate: Int, bitDepth: Int): Long
    private external fun nativeStartProcessing(handle: Long)
    private external fun nativeGetLatency(handle: Long): Int
    private external fun nativeSetFusionLevel(handle: Long, level: Float)
    private external fun nativeGetPhaseError(handle: Long): Float
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeSetHyperplane(address: Long)

    private external fun nativeInitializeEvolution()
    private external fun nativeGetBestFitness(): Float
    private external fun nativeGetGeneration(): Int
    private external fun nativeEvolveStep()
    private external fun nativePredictSamples(handle: Long, input: FloatArray, output: FloatArray, n: Int)

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            Log.i(TAG, "Librería nativa cargada correctamente")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "ERROR: No se pudo cargar librería nativa: ${e.message}")
        }
    }
}

    fun restart() {
        release()
        initialize(applicationContext)
    }
