/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

object ShmManager {
    private const val SHM_SIZE = 2 * 1024 * 1024 // 2 MB (huge page)
    private const val SHM_NAME = "ivanna_hyperplane"
    private const val MFD_ALLOW_SEALING = 0x0002  // <linux/memfd.h>
    private const val MFD_HUGETLB       = 0x0004  // <linux/memfd.h>

    private var hyperplaneBuffer: ByteBuffer? = null
    private var fd: Int = -1

    // Offsets dentro del hiperplano
    private const val OFFSET_BIQUAD = 0
    private const val OFFSET_KALMAN = OFFSET_BIQUAD + (64 * 5 * 4)
    private const val OFFSET_POBLACION = OFFSET_KALMAN + (3 * 4)
    private const val OFFSET_TEMP = OFFSET_POBLACION + (128 * 256)
    private const val OFFSET_SCHED = OFFSET_TEMP + (10 * 2)
    private const val OFFSET_SEQ = OFFSET_SCHED + (8 * 8 * 4 * 4 * 3)
    private const val OFFSET_ACTIVE = OFFSET_SEQ + 8

    fun initialize(context: Context) {
        try {
            // memfd_create via syscall directo (requiere root / kernel modificado)
            fd = memfdCreate(SHM_NAME, MFD_ALLOW_SEALING or MFD_HUGETLB)
            if (fd < 0) {
                // Fallback a memoria directa cuando memfd_create no está disponible
                hyperplaneBuffer = java.nio.ByteBuffer.allocateDirect(SHM_SIZE)
            } else {
                val pfd = ParcelFileDescriptor.fromFd(fd)
                try {
                    Os.ftruncate(pfd.fileDescriptor, SHM_SIZE.toLong())
                } finally {
                    pfd.close()
                }
                // mlockall equivalente: bloquear región
                val raf = RandomAccessFile("/proc/self/fd/$fd", "rw")
                hyperplaneBuffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE.toLong())
                raf.close()
            }

            // Lock en memoria
            hyperplaneBuffer?.let { buf ->
                val addressField = java.nio.Buffer::class.java.getDeclaredField("address")
                addressField.isAccessible = true
                val address = addressField.getLong(buf)
                nativeMlock(address, SHM_SIZE.toLong())
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback a memoria estándar si no hay root
            hyperplaneBuffer = java.nio.ByteBuffer.allocateDirect(SHM_SIZE)
        }
    }

    private external fun nativeMlock(address: Long, length: Long): Int
    private external fun memfdCreate(name: String, flags: Int): Int

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
        // Escribe en la región compartida para el DSP
        hyperplaneBuffer?.putFloat(OFFSET_KALMAN + 12, level)
    }

    init {
        System.loadLibrary("ivanna_trascendental")
    }
}
