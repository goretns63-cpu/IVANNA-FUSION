package com.ivannafusion

import android.content.Context
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()
    private var hyperplaneBuffer: ByteBuffer? = null
    private var fd: Int = -1

    private const val SHM_SIZE = 2 * 1024 * 1024
    private const val SHM_NAME = "ivanna_hyperplane"

    private const val OFFSET_BIQUAD = 0
    private const val OFFSET_KALMAN = OFFSET_BIQUAD + (64 * 5 * 4)
    private const val OFFSET_POBLACION = OFFSET_KALMAN + (3 * 4)
    private const val OFFSET_TEMP = OFFSET_POBLACION + (128 * 256)
    private const val OFFSET_SCHED = OFFSET_TEMP + (10 * 2)
    private const val OFFSET_SEQ = OFFSET_SCHED + (8 * 8 * 4 * 4 * 3)
    private const val OFFSET_ACTIVE = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        _shmStatus.value = "Creando SHM nativa..."
        try {
            fd = memfdCreate(SHM_NAME, 1)
            if (fd >= 0) {
                val pfd = ParcelFileDescriptor.adoptFd(fd)
                val raf = RandomAccessFile(pfd.fileDescriptor, "rw")
                hyperplaneBuffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
                raf.close()
                pfd.close()
                try {
                    val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                    addressField.isAccessible = true
                    val address = addressField.getLong(hyperplaneBuffer)
                    nativeMlock(address, SHM_SIZE.toLong())
                } catch (e: Exception) { }
                _shmStatus.value = "SHM real activa ✅"
            } else {
                _shmStatus.value = "Fallback buffer directo 🟠"
                hyperplaneBuffer = ByteBuffer.allocateDirect(SHM_SIZE)
            }
        } catch (e: Exception) {
            _shmStatus.value = "Error SHM: ${e.message} ⚠️"
            hyperplaneBuffer = ByteBuffer.allocateDirect(SHM_SIZE)
        }
    }

    private external fun memfdCreate(name: String, flags: Int): Int
    private external fun nativeMlock(address: Long, length: Long): Int
    private external fun nativeFtruncate(fd: Int, length: Long): Int

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer
    fun readSeqCounter(): Long = hyperplaneBuffer?.getLong(OFFSET_SEQ) ?: 0L
    fun readActiveBuffer(): Int = hyperplaneBuffer?.get(OFFSET_ACTIVE)?.toInt()?.and(0xFF) ?: 0
    fun readKalmanState(): FloatArray {
        val buf = hyperplaneBuffer ?: return floatArrayOf(0f,0f,0f)
        return floatArrayOf(buf.getFloat(OFFSET_KALMAN), buf.getFloat(OFFSET_KALMAN+4), buf.getFloat(OFFSET_KALMAN+8))
    }
    fun readBiquadCoefs(): Array<IntArray> {
        val buf = hyperplaneBuffer ?: return Array(64){ IntArray(5) }
        val result = Array(64){ IntArray(5) }
        for (i in 0 until 64) for (j in 0 until 5)
            result[i][j] = buf.getInt(OFFSET_BIQUAD + ((i*5+j)*4))
        return result
    }
    fun readTemperatures(): ShortArray {
        val buf = hyperplaneBuffer ?: return ShortArray(10)
        return ShortArray(10) { buf.getShort(OFFSET_TEMP + it*2) }
    }
    fun writeFusionLevel(level: Float) { hyperplaneBuffer?.putFloat(OFFSET_KALMAN+12, level) }
    fun close() { _shmStatus.value = "SHM cerrada"; hyperplaneBuffer = null; if(fd>=0) android.system.Os.close(fd) }

    init {
        try {
            System.loadLibrary("ivanna_trascendental")
            _shmStatus.value = "Librería nativa cargada"
        } catch (e: UnsatisfiedLinkError) {
            _shmStatus.value = "Error librería nativa: ${e.message}"
        }
    }
}
