/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun AIScreen(navController: NavController, audioEngine: AudioEngine) {
    // Variables canónicas del spec
    var evo_generacion       by remember { mutableIntStateOf(AudioEngine.getGeneration()) }
    var evo_fitness_mejor    by remember { mutableFloatStateOf(AudioEngine.getBestFitness()) }
    var evo_poblacion_tam    by remember { mutableIntStateOf(128) }
    var sched_nucleo_activo  by remember { mutableIntStateOf(0) }
    var sched_budget_mW      by remember { mutableIntStateOf(0) }
    var sched_throttle_predicho by remember { mutableStateOf(false) }
    var audio_error_fase_rms by remember { mutableFloatStateOf(0f) }
    var kalman_frec_hz       by remember { mutableFloatStateOf(0f) }
    var kalman_fase_rad      by remember { mutableFloatStateOf(0f) }
    var shm_seq_counter      by remember { mutableLongStateOf(0L) }

    var mutationRate  by remember { mutableFloatStateOf(0.05f) }
    var isAutoEvolve  by remember { mutableStateOf(false) }
    var elapsedSteps  by remember { mutableIntStateOf(0) }
    val fitnessHistory = remember { mutableStateListOf<Float>() }
    val scope = rememberCoroutineScope()

    // Refresco de todas las variables canónicas desde SHM
    LaunchedEffect(Unit) {
        AudioEngine.initializeEvolution()
        while (true) {
            ShmManager.refreshCanonicalVars()
            evo_generacion        = AudioEngine.getGeneration()
            evo_fitness_mejor     = AudioEngine.getBestFitness()
            evo_poblacion_tam     = 128
            sched_nucleo_activo   = ThermalMonitor.sched_nucleo_activo.toInt()
            sched_budget_mW       = ThermalMonitor.sched_budget_mW.toInt()
            sched_throttle_predicho = ThermalMonitor.sched_throttle_predicho
            audio_error_fase_rms  = AudioEngine.getPhaseErrorRms()
            kalman_frec_hz        = ShmManager.kalman_frec_hz
            kalman_fase_rad       = ShmManager.kalman_fase_rad
            shm_seq_counter       = ShmManager.shm_seq_counter
            delay(200)
        }
    }

    // Auto‑evolución
    LaunchedEffect(isAutoEvolve) {
        if (isAutoEvolve) {
            while (isAutoEvolve) {
                AudioEngine.evolveStep()
                evo_generacion    = AudioEngine.getGeneration()
                evo_fitness_mejor = AudioEngine.getBestFitness()
                elapsedSteps++
                fitnessHistory.add(evo_fitness_mejor)
                if (fitnessHistory.size > 80) fitnessHistory.removeAt(0)
                delay(120)
            }
        }
    }

    // Animación de actividad neural
    val infiniteTransition = rememberInfiniteTransition(label = "aiAnim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "wavePhase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060610))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text("🧠 INTELIGENCIA TRASCENDENTAL",
            color = Color(0xFFAA44FF), fontSize = 18.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text("© 2025 Luis Uriel Pimentel Pérez",
            color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

        Spacer(modifier = Modifier.height(12.dp))

        // Waveform animada
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFF0A0A1A))
        ) {
            val w = size.width; val h = size.height
            val path = Path()
            for (i in 0..w.toInt()) {
                val x = i.toFloat()
                val y = h / 2f + h * 0.35f *
                        sin(wavePhase + x * 0.04f).toFloat() *
                        sin(wavePhase * 0.3f + x * 0.01f).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFFAA44FF).copy(alpha = 0.85f), style = Stroke(2.5f))
            val path2 = Path()
            for (i in 0..w.toInt()) {
                val x = i.toFloat()
                val y = h / 2f + h * 0.20f * sin(wavePhase * 1.7f + x * 0.03f).toFloat()
                if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
            }
            drawPath(path2, Color(0xFF00FFFF).copy(alpha = 0.40f), style = Stroke(1.5f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Motor evolutivo ──
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("MOTOR EVOLUTIVO", color = Color(0xFFAA44FF), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AIMetric("evo_generacion",    "$evo_generacion",          Color(0xFFAA44FF))
                    AIMetric("evo_fitness_mejor", "%.4f".format(evo_fitness_mejor), Color(0xFF44FF88))
                    AIMetric("evo_poblacion_tam", "$evo_poblacion_tam",        Color(0xFF44AAFF))
                }
                Spacer(modifier = Modifier.height(6.dp))
                AIParamRow("Pasos acumulados", "$elapsedSteps", Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Tasa de mutación: %.3f".format(mutationRate),
                    color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Slider(
                    value = mutationRate, onValueChange = { mutationRate = it },
                    valueRange = 0.005f..0.30f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFAA44FF), activeTrackColor = Color(0xFFAA44FF))
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Planificador térmico ──
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("PLANIFICADOR TÉRMICO", color = Color(0xFFFFAA44), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                AIParamRow("sched_nucleo_activo",   "$sched_nucleo_activo", Color.Cyan)
                AIParamRow("sched_budget_mW",       "${sched_budget_mW} mW",
                    if (sched_budget_mW > 2000) Color(0xFF44FF88) else Color.Yellow)
                AIParamRow("sched_throttle_predicho",
                    if (sched_throttle_predicho) "⚠ SÍ" else "OK",
                    if (sched_throttle_predicho) Color.Red else Color(0xFF44FF88))
                Spacer(modifier = Modifier.height(6.dp))
                Text("TEMPERATURAS", color = Color.Gray, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AIMetric("CPU₀", "${ThermalMonitor.temp_cpu_core0}°C",
                        tempColor(ThermalMonitor.temp_cpu_core0.toInt()))
                    AIMetric("GPU",  "${ThermalMonitor.temp_gpu}°C",
                        tempColor(ThermalMonitor.temp_gpu.toInt()))
                    AIMetric("NPU",  "${ThermalMonitor.temp_npu}°C",
                        tempColor(ThermalMonitor.temp_npu.toInt()))
                    AIMetric("PMIC", "${ThermalMonitor.temp_pmic}°C",
                        tempColor(ThermalMonitor.temp_pmic.toInt()))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Oráculo de fase ──
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ORÁCULO DE FASE", color = Color(0xFF00FFFF), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                AIParamRow("audio_fs_hz",          "${AudioEngine.audio_fs_hz} Hz",  Color.Cyan)
                AIParamRow("audio_bit_depth",      "${AudioEngine.audio_bit_depth} bit", Color.Cyan)
                AIParamRow("audio_latencia_us",    "${AudioEngine.audio_latencia_us} µs", Color.Yellow)
                AIParamRow("audio_error_fase_rms",
                    "%.4f rad".format(audio_error_fase_rms),
                    if (audio_error_fase_rms < 0.1f) Color(0xFF44FF88) else Color.Red)
                AIParamRow("kalman_frec_hz",
                    "%.2f Hz".format(kalman_frec_hz),
                    if (kalman_frec_hz > 20f) Color(0xFF44FF88) else Color.Yellow)
                AIParamRow("kalman_fase_rad",
                    "%.4f rad".format(kalman_fase_rad), Color.Cyan)
                AIParamRow("shm_seq_counter",  "$shm_seq_counter",   Color.Gray)
                AIParamRow("SHM buffer",
                    if (ShmManager.shmInitialized) "activo" else "fallback",
                    if (ShmManager.shmInitialized) Color(0xFF44FF88) else Color(0xFFFF8800))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Gráfica de convergencia
        if (fitnessHistory.size > 1) {
            Text("Convergencia evo_fitness_mejor",
                color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Canvas(
                modifier = Modifier.fillMaxWidth().height(90.dp)
                    .background(Color(0xFF0A0A1A))
            ) {
                val w = size.width; val h = size.height
                val max = fitnessHistory.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
                val min = fitnessHistory.minOrNull() ?: 0f
                val range = (max - min).coerceAtLeast(0.001f)
                val stepX = w / (fitnessHistory.size - 1).coerceAtLeast(1)
                val path = Path()
                fitnessHistory.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = h - h * ((v - min) / range).coerceIn(0f, 1f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color(0xFF44FF88), style = Stroke(2f))
                val maxY = h - h * ((max - min) / range).coerceIn(0f, 1f)
                drawLine(Color(0xFF44FF88).copy(alpha = 0.3f),
                    Offset(0f, maxY), Offset(w, maxY), 1f)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Controles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        AudioEngine.evolveStep()
                        evo_generacion    = AudioEngine.getGeneration()
                        evo_fitness_mejor = AudioEngine.getBestFitness()
                        elapsedSteps++
                        fitnessHistory.add(evo_fitness_mejor)
                        if (fitnessHistory.size > 80) fitnessHistory.removeAt(0)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2233AA))
            ) { Text("PASO", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }

            Button(
                onClick = { isAutoEvolve = !isAutoEvolve },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAutoEvolve) Color(0xFF880022) else Color(0xFF226611)
                )
            ) {
                Text(if (isAutoEvolve) "⏹ STOP" else "▶ AUTO", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// Funciones auxiliares (deben estar definidas en el mismo archivo o en otro lugar)
@Composable
fun AIMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AIParamRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun tempColor(tempC: Int): Color = when {
    tempC < 50 -> Color(0xFF44FF88)
    tempC < 70 -> Color.Yellow
    tempC < 85 -> Color(0xFFFF8800)
    else -> Color.Red
}

// Clases y objetos referenciados (deben existir en el proyecto)
object ThermalMonitor {
    var sched_nucleo_activo = 4
    var sched_budget_mW = 1500
    var sched_throttle_predicho = false
    var temp_cpu_core0 = 45
    var temp_gpu = 40
    var temp_npu = 38
    var temp_pmic = 42
}

object ShmManager {
    var shmInitialized = true
    var kalman_frec_hz = 0f
    var kalman_fase_rad = 0f
    var shm_seq_counter = 0L
    fun refreshCanonicalVars() { /* implementación real */ }
}

object AudioEngine {
    var audio_fs_hz = 44100
    var audio_bit_depth = 16
    var audio_latencia_us = 5000
    fun initializeEvolution() { /* ... */ }
    fun evolveStep() { /* ... */ }
    fun getGeneration(): Int = 0
    fun getBestFitness(): Float = 0f
    fun getPhaseErrorRms(): Float = 0f
}
