package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()
    private var hyperplaneBuffer: ByteBuffer? = null
    private var sharedMemory: SharedMemory? = null

    // ── Propiedades públicas requeridas por DiagnosticsPanel ──────────────────
    var nativeLibLoaded: Boolean = false
        private set
    var shmInitialized: Boolean = false
        private set
    var lastError: String? = null
        private set
    // ─────────────────────────────────────────────────────────────────────────

    private const val SHM_SIZE = 2 * 1024 * 1024
    private const val SHM_NAME = "ivanna_hyperplane"

    private const val OFFSET_BIQUAD    = 0
    private const val OFFSET_KALMAN    = OFFSET_BIQUAD + (64 * 5 * 4)
    private const val OFFSET_POBLACION = OFFSET_KALMAN + (3 * 4)
    private const val OFFSET_TEMP      = OFFSET_POBLACION + (128 * 256)
    private const val OFFSET_SCHED     = OFFSET_TEMP + (10 * 2)
    private const val OFFSET_SEQ       = OFFSET_SCHED + (8 * 8 * 4 * 4 * 3)
    private const val OFFSET_ACTIVE    = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        _shmStatus.value = "Creando SharedMemory..."
        try {
            val shm = SharedMemory.create(SHM_NAME, SHM_SIZE)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            val buf = shm.mapReadWrite()
            sharedMemory = shm
            hyperplaneBuffer = buf
            // mlock vía reflection
            try {
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(buf)
                nativeMlock(address, SHM_SIZE.toLong())
            } catch (_: Exception) {}
            shmInitialized = true
            _shmStatus.value = "SHM mlock OK ✅"
            android.util.Log.i("IVANNA-SHM", "SharedMemory.create OK, size=$SHM_SIZE")
        } catch (e: Exception) {
            lastError = e.message
            _shmStatus.value = "Fallback buffer directo 🟠"
            hyperplaneBuffer = ByteBuffer.allocateDirect(SHM_SIZE)
            shmInitialized = true  // fallback funcional para el engine
            android.util.Log.w("IVANNA-SHM", "SharedMemory falló: ${e.message}; usando DirectByteBuffer")
        }
    }

    private external fun nativeMlock(address: Long, length: Long): Int

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer
    fun readSeqCounter(): Long = hyperplaneBuffer?.getLong(OFFSET_SEQ) ?: 0L
    fun readActiveBuffer(): Int = hyperplaneBuffer?.get(OFFSET_ACTIVE)?.toInt()?.and(0xFF) ?: 0
    fun readKalmanState(): FloatArray {
        val buf = hyperplaneBuffer ?: return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(buf.getFloat(OFFSET_KALMAN), buf.getFloat(OFFSET_KALMAN + 4), buf.getFloat(OFFSET_KALMAN + 8))
    }
    fun readBiquadCoefs(): Array<IntArray> {
        val buf = hyperplaneBuffer ?: return Array(64) { IntArray(5) }
        val result = Array(64) { IntArray(5) }
        for (i in 0 until 64) for (j in 0 until 5)
            result[i][j] = buf.getInt(OFFSET_BIQUAD + ((i * 5 + j) * 4))
        return result
    }
    fun readTemperatures(): ShortArray {
        val buf = hyperplaneBuffer ?: return ShortArray(10)
        return ShortArray(10) { buf.getShort(OFFSET_TEMP + it * 2) }
    }
    fun writeFusionLevel(level: Float) { hyperplaneBuffer?.putFloat(OFFSET_KALMAN + 12, level) }
    fun close() {
        _shmStatus.value = "SHM cerrada"
        hyperplaneBuffer = null
        sharedMemory?.close()
        sharedMemory = null
        shmInitialized = false
    }

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            nativeLibLoaded = true
            _shmStatus.value = "Librería nativa cargada"
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            lastError = e.message
            _shmStatus.value = "Error librería nativa: ${e.message}"
        }
    }
}
