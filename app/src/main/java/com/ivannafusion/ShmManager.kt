/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 * Prohibida la copia, distribución, ingeniería inversa o cualquier uso no autorizado.
 * Quien infrinja será perseguido penal y civilmente.
 */

package com.ivannafusion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "IVANNA-SHM"

object ShmManager {
    // NUEVO: Estado observable para UI
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()

    var nativeLibLoaded = false
    var shmInitialized = false
    var lastError: String? = null

    private const val SHM_SIZE = 2 * 1024 * 1024
    private const val SHM_NAME = "ivanna_hyperplane"

    private var hyperplaneBuffer: ByteBuffer? = null
    private var fd: Int = -1

    private const val OFFSET_BIQUAD = 0
    private const val OFFSET_KALMAN = OFFSET_BIQUAD + (64 * 5 * 4)
    private const val OFFSET_POBLACION = OFFSET_KALMAN + (3 * 4)
    private const val OFFSET_TEMP = OFFSET_POBLACION + (128 * 256)
    private const val OFFSET_SCHED = OFFSET_TEMP + (10 * 2)
    private const val OFFSET_SEQ = OFFSET_SCHED + (8 * 8 * 4 * 4 * 3)
    private const val OFFSET_ACTIVE = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        Log.i(TAG, "Inicializando ShmManager...")
        _shmStatus.value = "Inicializando SHM..."
        try {
            fd = memfdCreate(SHM_NAME, 1)
            if (fd >= 0) {
                Log.i(TAG, "memfd_create exitoso fd=$fd")
                _shmStatus.value = "memfd_create OK"
                nativeFtruncate(fd, SHM_SIZE.toLong())
                val raf = RandomAccessFile("/proc/self/fd/$fd", "rw")
                hyperplaneBuffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
                raf.close()
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
            } else {
                Log.w(TAG, "memfd_create falló, usando fallback a archivo temporal")
                _shmStatus.value = "Fallback a archivo temporal"
                val shmFile = File(context.cacheDir, "$SHM_NAME.tmp")
                val raf = RandomAccessFile(shmFile, "rw")
                raf.setLength(SHM_SIZE.toLong())
                hyperplaneBuffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
                raf.close()
            }
            shmInitialized = true
            _shmStatus.value = "SHM inicializada correctamente"
            Log.i(TAG, "ShmManager inicializado correctamente")
        } catch (e: Exception) {
            lastError = "SHM: ${e.message}"
            _shmStatus.value = "Error SHM: ${e.message}"
            Log.e(TAG, "Error ShmManager: ${e.message}", e)
            hyperplaneBuffer = ByteBuffer.allocateDirect(SHM_SIZE)
            shmInitialized = true
            _shmStatus.value = "Usando buffer directo (fallback)"
        }
    }

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
        // liberar recursos si necesario
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
