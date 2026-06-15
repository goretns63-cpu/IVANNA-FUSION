/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 * Prohibida la copia, distribución, ingeniería inversa o cualquier uso no autorizado.
 * Quien infrinja será perseguido penal y civilmente.
 */

package com.ivannafusion

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    // Solo permisos dangerous que el sistema puede mostrar en runtime.
    // MODIFY_AUDIO_SETTINGS y VIBRATE son protection=normal → se otorgan en instalación.
    // WRITE_EXTERNAL_STORAGE está bloqueado en API 30+ con targetSdk >= 30.
    // READ_MEDIA_AUDIO reemplaza READ_EXTERNAL_STORAGE en API 33+.
    private val requiredPermissions: Array<String> = buildList {
        add(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.READ_MEDIA_AUDIO)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initializeTrascendental()
        } else {
            Toast.makeText(this, "Permisos denegados. IVANNA no puede operar.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificación de integridad del APK
        if (!verifyIntegrity()) {
            Toast.makeText(this, "INTEGRIDAD COMPROMETIDA. IVANNA SE BLOQUEA.", Toast.LENGTH_LONG).show()
            Process.killProcess(Process.myPid())
            return
        }

        // Aislamiento de prioridad
        Process.setThreadPriority(Process.myTid(), Process.THREAD_PRIORITY_URGENT_AUDIO)

        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            initializeTrascendental()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun initializeTrascendental() {
        // Inicializar memoria compartida
        ShmManager.initialize(this)

        // Inicializar motor de audio nativo
        AudioEngine.initialize()

        // Cargar eBPF si estamos en kernel modificado
        ThermalMonitor.initialize(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "simbiosis") {
                        composable("simbiosis") { SimbiosisScreen(navController) }
                        composable("monitor") { MonitorScreen(navController) }
                        composable("settings") { SettingsAuditScreen(navController) }
                    }
                }
            }
        }
    }

    private fun verifyIntegrity(): Boolean {
        return try {
            val apkPath = applicationInfo.sourceDir
            val apkFile = File(apkPath)
            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedHash = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.ivannafusion.INTEGRITY_HASH") ?: return true
            hash == expectedHash || expectedHash == "SHA256_PLACEHOLDER_BUILD_TIME"
        } catch (e: Exception) {
            false
        }
    }
}
