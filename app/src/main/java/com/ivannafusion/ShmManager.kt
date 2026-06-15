package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()
    private var hyperplaneBuffer: ByteBuffer? = null
    private var sharedMemory: SharedMemory? = null

    var nativeLibLoaded: Boolean = false
        private set
    var shmInitialized: Boolean = false
        private set
    var lastError: String? = null
        private set

    private const val SHM_SIZE = 2 * 1024 * 1024

    // Offsets (deben coincidir con audio_orchestrator.cpp Hyperplane struct)
    // biquad_coefs[64][5] = 64*5*4 = 1280 bytes
    private const val OFFSET_BIQUAD    = 0
    // kalman_state[3] floats = 12 bytes
    private const val OFFSET_KALMAN    = OFFSET_BIQUAD + 1280
    // poblacion_evolutiva[128][256] = 32768 bytes
    private const val OFFSET_POBLACION = OFFSET_KALMAN + 12
    // temp_soc[10] shorts = 20 bytes
    private const val OFFSET_TEMP      = OFFSET_POBLACION + 32768
    // sched_table[8][8][4][4][3] = 3072 bytes
    private const val OFFSET_SCHED     = OFFSET_TEMP + 20
    // seq_counter uint64 = 8 bytes (aligned to 8)
    private const val OFFSET_SEQ       = OFFSET_SCHED + 3072
    // active_buffer uint8 = 1 byte
    private const val OFFSET_ACTIVE    = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        _shmStatus.value = "Creando SharedMemory..."
        try {
            val shm = SharedMemory.create("ivanna_hyperplane", SHM_SIZE)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            val buf = shm.mapReadWrite()
            buf.order(ByteOrder.nativeOrder())
            // Inicializar a cero para que los valores iniciales sean válidos
            buf.position(0)
            repeat(SHM_SIZE / 4) { buf.putInt(0) }
            buf.position(0)
            sharedMemory = shm
            hyperplaneBuffer = buf
            try {
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(buf)
                nativeMlock(address, SHM_SIZE.toLong())
            } catch (_: Exception) {}
            shmInitialized = true
            _shmStatus.value = "SHM mlock OK ✅"
            android.util.Log.i("IVANNA-SHM", "SharedMemory.create OK size=$SHM_SIZE offsets: seq=$OFFSET_SEQ kalman=$OFFSET_KALMAN")
        } catch (e: Exception) {
            lastError = e.message
            _shmStatus.value = "Fallback buffer directo 🟠"
            val buf = ByteBuffer.allocateDirect(SHM_SIZE)
            buf.order(ByteOrder.nativeOrder())
            hyperplaneBuffer = buf
            shmInitialized = true
            android.util.Log.w("IVANNA-SHM", "SharedMemory falló: ${e.message}")
        }
    }

    private external fun nativeMlock(address: Long, length: Long): Int

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer

    // Crear una vista duplicada para lecturas seguras sin modificar position global
    private fun buf(): ByteBuffer? = hyperplaneBuffer?.duplicate()?.order(ByteOrder.nativeOrder())

    fun readSeqCounter(): Long = buf()?.getLong(OFFSET_SEQ) ?: 0L
    fun readActiveBuffer(): Int = buf()?.get(OFFSET_ACTIVE)?.toInt()?.and(0xFF) ?: 0

    fun readKalmanState(): FloatArray {
        val b = buf() ?: return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            b.getFloat(OFFSET_KALMAN),
            b.getFloat(OFFSET_KALMAN + 4),
            b.getFloat(OFFSET_KALMAN + 8)
        )
    }

    fun readBiquadCoefs(): Array<IntArray> {
        val b = buf() ?: return Array(64) { IntArray(5) }
        val result = Array(64) { IntArray(5) }
        for (i in 0 until 64) for (j in 0 until 5)
            result[i][j] = b.getInt(OFFSET_BIQUAD + (i * 5 + j) * 4)
        return result
    }

    fun readTemperatures(): ShortArray {
        val b = buf() ?: return ShortArray(10)
        return ShortArray(10) { b.getShort(OFFSET_TEMP + it * 2) }
    }

    fun writeFusionLevel(level: Float) {
        hyperplaneBuffer?.putFloat(OFFSET_KALMAN + 12, level)
    }

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
