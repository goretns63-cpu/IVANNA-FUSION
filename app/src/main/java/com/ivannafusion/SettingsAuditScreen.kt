/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsAuditScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedSampleRate by remember { mutableIntStateOf(AudioEngine.audio_fs_hz) }
    var selectedBitDepth by remember { mutableIntStateOf(AudioEngine.audio_bit_depth) }
    var developerMode by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "AJUSTES Y AUDITORÍA",
            color = Color.Cyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Selector de frecuencia de muestreo
        Text("Frecuencia de muestreo", color = Color.White, fontSize = 18.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(44100, 48000, 96000, 192000, 384000).forEach { rate ->
                FilterChip(
                    selected = selectedSampleRate == rate,
                    onClick = { selectedSampleRate = rate; AudioEngine.audio_fs_hz = rate },
                    label = { Text("${rate / 1000} kHz") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Cyan,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de profundidad de bits
        Text("Profundidad de bits", color = Color.White, fontSize = 18.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(16, 24, 32).forEach { bits ->
                FilterChip(
                    selected = selectedBitDepth == bits,
                    onClick = { selectedBitDepth = bits; AudioEngine.audio_bit_depth = bits },
                    label = { Text("$bits bits") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Cyan,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Modo desarrollador
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Modo desarrollador", color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked = developerMode,
                onCheckedChange = { developerMode = it }
            )
        }

        if (developerMode) {
            DeveloperPanel()
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exportación
        Button(
            onClick = {
                exportDiagnostics(context) { status -> exportStatus = status }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EXPORTAR DIAGNÓSTICO (JSON/CSV)")
        }

        if (exportStatus.isNotEmpty()) {
            Text(
                exportStatus,
                color = if (exportStatus.contains("ÉXITO")) Color.Green else Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Copyright
        Text(
            "© 2025 Luis Uriel Pimentel Pérez",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { navController.navigate("simbiosis") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VOLVER")
        }
    }
}

@Composable
fun DeveloperPanel() {
    var seqCounter by remember { mutableLongStateOf(0L) }
    var activeBuffer by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            seqCounter = ShmManager.readSeqCounter()
            activeBuffer = ShmManager.readActiveBuffer()
            kotlinx.coroutines.delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text("MÉTRICAS DEL HIPERPLANO", color = Color.Yellow, fontWeight = FontWeight.Bold)
        Text("shm_seq_counter: $seqCounter", color = Color.White, fontSize = 12.sp)
        Text("shm_buffer_activo: $activeBuffer", color = Color.White, fontSize = 12.sp)
        Text("kalman_fase_rad: %.4f".format(ShmManager.readKalmanState()[0]), color = Color.White, fontSize = 12.sp)
        Text("kalman_frec_hz: %.2f".format(ShmManager.readKalmanState()[1]), color = Color.White, fontSize = 12.sp)
    }
}

private fun exportDiagnostics(context: Context, onStatus: (String) -> Unit) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val baseDir = File(context.getExternalFilesDir(null), "IVANNA_Diagnostics")
        baseDir.mkdirs()

        // Exportar JSON
        val json = JSONObject().apply {
            put("timestamp", timestamp)
            put("audio_fs_hz", AudioEngine.audio_fs_hz)
            put("audio_bit_depth", AudioEngine.audio_bit_depth)
            put("audio_latencia_us", AudioEngine.audio_latencia_us)
            put("audio_error_fase_rms", AudioEngine.getPhaseErrorRms())
            put("evo_poblacion_tam", 128)
            put("evo_generacion", 0)
            put("evo_fitness_mejor", 0.0)
            put("temp_cpu_core0", ThermalMonitor.temp_cpu_core0)
            put("temp_gpu", ThermalMonitor.temp_gpu)
            put("temp_npu", ThermalMonitor.temp_npu)
            put("temp_pmic", ThermalMonitor.temp_pmic)
            put("shm_seq_counter", ShmManager.readSeqCounter())
            put("shm_buffer_activo", ShmManager.readActiveBuffer())
        }

        val jsonFile = File(baseDir, "ivanna_diagnostic_$timestamp.json")
        FileWriter(jsonFile).use { it.write(json.toString(2)) }

        // Exportar CSV
        val csvFile = File(baseDir, "ivanna_metrics_$timestamp.csv")
        FileWriter(csvFile).use { writer ->
            writer.write("metric,value,unit\n")
            writer.write("audio_fs_hz,${AudioEngine.audio_fs_hz},Hz\n")
            writer.write("audio_bit_depth,${AudioEngine.audio_bit_depth},bits\n")
            writer.write("audio_latencia_us,${AudioEngine.audio_latencia_us},us\n")
            writer.write("audio_error_fase_rms,${AudioEngine.getPhaseErrorRms()},rad\n")
            writer.write("temp_cpu_core0,${ThermalMonitor.temp_cpu_core0},C\n")
            writer.write("temp_gpu,${ThermalMonitor.temp_gpu},C\n")
        }

        onStatus("ÉXITO: Exportado a ${baseDir.absolutePath}")
    } catch (e: Exception) {
        onStatus("ERROR: ${e.message}")
    }
}
