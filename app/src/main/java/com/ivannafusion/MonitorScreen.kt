package com.ivannafusion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MonitorScreen(
    navController: NavController,
    audioEngine: AudioEngine,
    shmManager: ShmManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shmStatus by shmManager.shmStatus.collectAsState()
    val isRealShm = !shmStatus.contains("fallback") && !shmStatus.contains("Error")

    var audioFs by remember { mutableIntStateOf(AudioEngine.audio_fs_hz) }
    var bitDepth by remember { mutableIntStateOf(AudioEngine.audio_bit_depth) }
    var latencyUs by remember { mutableIntStateOf(0) }
    var phaseError by remember { mutableFloatStateOf(0f) }
    var generation by remember { mutableIntStateOf(0) }
    var bestFitness by remember { mutableFloatStateOf(0f) }
    var cpuTemp by remember { mutableIntStateOf(0) }
    var gpuTemp by remember { mutableIntStateOf(0) }

    val phaseHistory = remember { mutableStateListOf<Float>() }
    var fusionLevel by remember { mutableFloatStateOf(0.5f) }
    val sampleRates = listOf(44100, 48000, 96000, 192000)
    var selectedRate by remember { mutableIntStateOf(audioFs) }

    fun exportDiagnostics() {
        scope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val json = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("shm_status", shmStatus)
                    put("audio_fs_hz", audioFs)
                    put("latency_us", latencyUs)
                    put("phase_error_rms", phaseError)
                    put("fusion_level", fusionLevel)
                    put("generation", generation)
                    put("best_fitness", bestFitness)
                    put("cpu_temp", cpuTemp)
                    put("gpu_temp", gpuTemp)
                }
                val file = File(context.getExternalFilesDir(null), "IVANNA_Diagnostic_$timestamp.json")
                file.writeText(json.toString(2))
                android.widget.Toast.makeText(context, "Exportado a ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error exportando: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun changeSampleRate(rate: Int) {
        selectedRate = rate
        scope.launch {
            audioEngine.restart()
            delay(200)
            audioFs = AudioEngine.audio_fs_hz
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            latencyUs = AudioEngine.getLatencyMicros()
            phaseError = AudioEngine.getPhaseErrorRms()
            AudioEngine.evolveStep()
            generation = AudioEngine.getGeneration()
            bestFitness = AudioEngine.getBestFitness()
            cpuTemp = ThermalMonitor.temp_cpu_core0.toInt()
            gpuTemp = ThermalMonitor.temp_gpu.toInt()
            phaseHistory.add(phaseError)
            if (phaseHistory.size > 60) phaseHistory.removeAt(0)
            delay(100)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("IVANNA MONITOR", color = Color.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Surface(shape = RoundedCornerShape(8.dp), color = if (isRealShm) Color(0xFF00AA44) else Color(0xFFAA6600)) {
                Text(if (isRealShm) "SHM REAL" else "SHM FALLBACK", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Text("© 2025 Luis Uriel Pimentel Pérez — Todos los derechos reservados", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))

        Spacer(modifier = Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricCard("Fs", "$audioFs Hz", Color.Cyan)
            MetricCard("Bit depth", "$bitDepth bits", Color.Magenta)
            MetricCard("Latencia", "$latencyUs µs", Color.Yellow)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricCard("Error fase", "%.4f rad".format(phaseError), if (phaseError < 0.05f) Color.Green else Color.Red)
            MetricCard("Evo gen", "$generation", Color(0xFFAA88FF))
            MetricCard("Fitness", "%.4f".format(bestFitness), Color(0xFFFFAA44))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricCard("CPU temp", "$cpuTemp °C", if (cpuTemp < 60) Color.Green else Color.Red)
            MetricCard("GPU temp", "$gpuTemp °C", if (gpuTemp < 60) Color.Green else Color.Red)
            MetricCard("Fusión", "%.2f".format(fusionLevel), Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Error de fase (rad) últimos 60 ciclos", color = Color.LightGray, fontSize = 12.sp)
        PhaseErrorGraph(phaseHistory)

        Spacer(modifier = Modifier.height(16.dp))
        Text("Nivel de fusión simbiótica", color = Color.White, fontSize = 14.sp)
        Slider(
            value = fusionLevel,
            onValueChange = {
                fusionLevel = it
                audioEngine.setFusionLevel(it)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AudioEngine.evolveStep() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2266AA))) {
                Text("Evolucionar 1 paso")
            }
            Button(onClick = { audioEngine.restart() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA4422))) {
                Text("Reset evolución")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Frecuencia:", color = Color.White, modifier = Modifier.weight(1f))
            sampleRates.forEach { rate ->
                FilterChip(
                    selected = selectedRate == rate,
                    onClick = { changeSampleRate(rate) },
                    label = { Text("$rate Hz") },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { exportDiagnostics() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006644))) {
            Text("EXPORTAR DIAGNÓSTICO JSON")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("simbiosis") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
            Text("VOLVER A SIMBIOSIS")
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.width(110.dp).height(60.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PhaseErrorGraph(data: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).background(Color.Black)) {
        if (data.isEmpty()) return@Canvas
        val width = size.width
        val height = size.height
        val maxError = data.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
        val stepX = width / (data.size - 1).coerceAtLeast(1)
        val path = Path()
        for (i in data.indices) {
            val x = i * stepX
            val y = height * (1 - (data[i] / maxError).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }
        drawPath(path = path, color = Color.Cyan, style = Stroke(width = 2f))
        val thresholdY = height * (1 - (0.05f / maxError).coerceIn(0f, 1f))
        drawLine(
            color = Color.Red,
            start = Offset(0f, thresholdY),
            end = Offset(width, thresholdY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}
