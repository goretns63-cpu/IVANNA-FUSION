package com.ivannafusion

import android.content.Context
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

object ShmManager {
    private val _shmStatus = MutableStateFlow("Inicializando...")
    val shmStatus: StateFlow<String> = _shmStatus.asStateFlow()
    private var hyperplaneBuffer: ByteBuffer? = null

    fun initialize(context: Context) {
        _shmStatus.value = "Creando SharedMemory..."
        try {
            val shm = SharedMemory.create("ivanna_hyperplane", 2 * 1024 * 1024)
            shm.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            hyperplaneBuffer = shm.mapReadWrite()
            _shmStatus.value = "SHM real activa"
            Log.i("IVANNA-SHM", "SharedMemory creada correctamente")
        } catch (e: Exception) {
            _shmStatus.value = "Error: ${e.message}"
            hyperplaneBuffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            _shmStatus.value = "Fallback buffer directo"
        }
    }

    fun getBuffer(): ByteBuffer? = hyperplaneBuffer
    fun close() { _shmStatus.value = "SHM cerrada" }
}
