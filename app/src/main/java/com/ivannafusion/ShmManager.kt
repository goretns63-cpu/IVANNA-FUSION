/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 * Prohibida la copia, distribución, ingeniería inversa o cualquier uso no autorizado.
 * Quien infrinja será perseguido penal y civilmente.
 */

package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

private const val TAG = "IVANNA-SHM"

object ShmManager {
    // Estado observable para la UI
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()

    var nativeLibLoaded = false
    var shmInitialized = false
    var lastError: String? = null

    private const val SHM_SIZE = 2 * 1024 * 1024  // 2 MiB
    private const val SHM_NAME = "ivanna_hyperplane"

    private var hyperplaneBuffer: ByteBuffer? = null

    // Offsets de las estructuras (igual que en la versión original)
    private const val OFFSET_BIQUAD = 0
    private const val OFFSET_KALMAN = OFFSET_BIQUAD + (64 * 5 * 4)
    private const val OFFSET_POBLACION = OFFSET_KALMAN + (3 * 4)
    private const val OFFSET_TEMP = OFFSET_POBLACION + (128 * 256)
    private const val OFFSET_SCHED = OFFSET_TEMP + (10 * 2)
    private const val OFFSET_SEQ = OFFSET_SCHED + (8 * 8 * 4 * 4 * 3)
    private const val OFFSET_ACTIVE = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        Log.i(TAG, "Inicializando ShmManager con SharedMemory...")
        _shmStatus.value = "Creando SharedMemory..."

        try {
            // Usar SharedMemory.create (API 27+)
            val shm = SharedMemory.create(SHM_NAME, SHM_SIZE)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            hyperplaneBuffer = shm.mapReadWrite()
            _shmStatus.value = "SHM real activa ✅"
            Log.i(TAG, "SharedMemory creada y mapeada correctamente")
            shmInitialized = true

            // Si se necesita mlock, se puede hacer sobre el buffer mapeado
            try {
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(hyperplaneBuffer)
                nativeMlock(address, SHM_SIZE.toLong())
                _shmStatus.value = "SHM mlock OK"
            } catch (e: Exception) {
                Log.w(TAG, "mlock no disponible: ${e.message}")
                _shmStatus.value = "mlock falló, pero SHM funciona"
            }
        } catch (e: Exception) {
            lastError = "SharedMemory: ${e.message}"
            _shmStatus.value = "Error SharedMemory: ${e.message} ⚠️"
            Log.e(TAG, "Error creando SharedMemory, usando buffer directo: ${e.message}", e)
            hyperplaneBuffer = ByteBuffer.allocateDirect(SHM_SIZE)
            shmInitialized = true
            _shmStatus.value = "Usando buffer directo (fallback) 🟠"
        }
    }

    // Las siguientes funciones nativas ya no son necesarias para crear SHM,
    // pero se mantienen como stubs para evitar UnsatisfiedLinkError
    private external fun nativeMlock(address: Long, length: Long): Int
    private external fun memfdCreate(name: String, flags: Int): Int
    private external fun nativeFtruncate(fd: Int, length: Long): Int

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer

    fun readSeqCounter(): Long {
        val buf = hyperplaneBuffer ?: return 0L
        return buf.getLong(OFFSET_SEQ)
    }

    fun readActiveBuffer(): Int {
        val buf = hyperplaneBuffer ?: return 0
        return buf.get(OFFSET_ACTIVE).toInt() and 0xFF
    }

    fun readKalmanState(): FloatArray {
        val buf = hyperplaneBuffer ?: return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            buf.getFloat(OFFSET_KALMAN),
            buf.getFloat(OFFSET_KALMAN + 4),
            buf.getFloat(OFFSET_KALMAN + 8)
        )
    }

    fun readBiquadCoefs(): Array<IntArray> {
        val buf = hyperplaneBuffer ?: return Array(64) { IntArray(5) }
        val result = Array(64) { IntArray(5) }
        for (i in 0 until 64) {
            for (j in 0 until 5) {
                result[i][j] = buf.getInt(OFFSET_BIQUAD + ((i * 5 + j) * 4))
            }
        }
        return result
    }

    fun readTemperatures(): ShortArray {
        val buf = hyperplaneBuffer ?: return ShortArray(10)
        val temps = ShortArray(10)
        for (i in 0 until 10) {
            temps[i] = buf.getShort(OFFSET_TEMP + (i * 2))
        }
        return temps
    }

    fun writeFusionLevel(level: Float) {
        hyperplaneBuffer?.putFloat(OFFSET_KALMAN + 12, level)
    }

    fun close() {
        _shmStatus.value = "SHM cerrada"
        hyperplaneBuffer = null
        shmInitialized = false
    }

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            nativeLibLoaded = true
            Log.i(TAG, "Librería nativa cargada correctamente")
            _shmStatus.value = "Librería nativa cargada"
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            lastError = "Native lib: ${e.message}"
            _shmStatus.value = "Error librería nativa: ${e.message}"
            Log.e(TAG, "ERROR: No se pudo cargar librería nativa: ${e.message}")
        }
    }
}
