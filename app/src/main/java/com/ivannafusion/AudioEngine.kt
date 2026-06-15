/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * ARQUITECTURA:
 *   - AAudio OUTPUT: emite silencio (no interfiere con el reproductor del sistema)
 *   - AudioRecord: captura el micrófono/loopback → envía muestras a nativeProcessCapture
 *   - Kalman nativo: trackea fase/frecuencia de la señal real capturada
 *   - SHM: estado Kalman disponible para la UI en tiempo real
 */

package com.ivannafusion

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

private const val TAG = "IVANNA-Audio"

object AudioEngine {
    private var nativeHandle: Long = 0
    var initialized = false
    private var appContext: Context? = null

    var audio_fs_hz: Int = 48_000
    var audio_bit_depth: Int = 32
    var audio_latencia_us: Int = 0

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val captureScope = CoroutineScope(Dispatchers.Default)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nativeRate = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48_000
        audio_fs_hz = nativeRate.coerceIn(8_000, 192_000)

        nativeHandle = nativeCreateEngine(audio_fs_hz, audio_bit_depth)
        if (nativeHandle == 0L) {
            Log.e(TAG, "nativeCreateEngine retornó 0")
        }

        // Conectar SHM antes de start
        val shmBuf = ShmManager.getBuffer()
        if (shmBuf != null && nativeHandle != 0L) {
            try {
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(shmBuf)
                if (address != 0L) {
                    nativeSetHyperplane(address)
                    Log.i(TAG, "Hyperplane SHM conectado addr=0x${address.toString(16)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo conectar hyperplane: ${e.message}")
            }
        }

        if (nativeHandle != 0L) nativeStartProcessing(nativeHandle)

        // Iniciar captura de audio real
        startAudioRecord()

        initialized = true
        Log.i(TAG, "AudioEngine inicializado: ${audio_fs_hz} Hz, ${audio_bit_depth} bits")
    }

    private fun startAudioRecord() {
        val minBuf = AudioRecord.getMinBufferSize(
            audio_fs_hz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuf <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize falló: $minBuf")
            return
        }

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                audio_fs_hz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                minBuf * 4
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord no inicializado state=${record.state}")
                record.release()
                return
            }
            audioRecord = record
            record.startRecording()

            val frameSize = minBuf / 4  // float = 4 bytes
            val buffer = FloatArray(frameSize)

            captureJob = captureScope.launch {
                Log.i(TAG, "Capture loop iniciado: frameSize=$frameSize")
                while (isActive) {
                    val read = record.read(buffer, 0, frameSize, AudioRecord.READ_BLOCKING)
                    if (read > 0 && nativeHandle != 0L) {
                        nativeProcessCapture(buffer, read)
                    }
                }
                Log.i(TAG, "Capture loop terminado")
            }
            Log.i(TAG, "AudioRecord iniciado: ${audio_fs_hz} Hz, bufSize=${minBuf * 4}")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando AudioRecord: ${e.message}")
        }
    }

    private fun stopAudioRecord() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
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

    fun predictSamples(input: FloatArray, output: FloatArray) {
        if (nativeHandle != 0L && input.size == output.size)
            nativePredictSamples(nativeHandle, input, output, input.size)
    }

    fun shutdown() {
        stopAudioRecord()
        if (nativeHandle != 0L) nativeDestroyEngine(nativeHandle)
        nativeHandle = 0L
        initialized = false
    }

    fun restart() {
        Log.w(TAG, "Restarting audio engine...")
        shutdown()
        Thread.sleep(50)
        appContext?.let { initialize(it) }
            ?: Log.e(TAG, "Cannot restart: context is null")
        Log.i(TAG, "Audio engine restarted")
    }

    // ── JNI ──────────────────────────────────────────────────────────────────
    private external fun nativeCreateEngine(sampleRate: Int, bitDepth: Int): Long
    private external fun nativeStartProcessing(handle: Long)
    private external fun nativeGetLatency(handle: Long): Int
    private external fun nativeSetFusionLevel(handle: Long, level: Float)
    private external fun nativeGetPhaseError(handle: Long): Float
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeSetHyperplane(address: Long)
    private external fun nativeProcessCapture(samples: FloatArray, n: Int)

    private external fun nativeInitializeEvolution()
    private external fun nativeGetBestFitness(): Float
    private external fun nativeGetGeneration(): Int
    private external fun nativeEvolveStep()
    private external fun nativePredictSamples(handle: Long, input: FloatArray, output: FloatArray, n: Int)

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            Log.i(TAG, "Librería nativa cargada")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "ERROR cargando librería nativa: ${e.message}")
        }
    }
}
