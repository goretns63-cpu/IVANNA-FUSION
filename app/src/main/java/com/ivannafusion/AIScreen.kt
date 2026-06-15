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
    var generation    by remember { mutableIntStateOf(AudioEngine.getGeneration()) }
    var bestFitness   by remember { mutableFloatStateOf(AudioEngine.getBestFitness()) }
    var mutationRate  by remember { mutableFloatStateOf(0.05f) }
    var isAutoEvolve  by remember { mutableStateOf(false) }
    var elapsedSteps  by remember { mutableIntStateOf(0) }
    val fitnessHistory = remember { mutableStateListOf<Float>() }
    val scope = rememberCoroutineScope()

    // Auto-evolve loop
    LaunchedEffect(isAutoEvolve) {
        if (isAutoEvolve) {
            while (isAutoEvolve) {
                AudioEngine.evolveStep()
                generation = AudioEngine.getGeneration()
                bestFitness = AudioEngine.getBestFitness()
                elapsedSteps++
                fitnessHistory.add(bestFitness)
                if (fitnessHistory.size > 80) fitnessHistory.removeAt(0)
                delay(120)
            }
        }
    }

    // Animación de partículas
    val infiniteTransition = rememberInfiniteTransition(label = "aiAnim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
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
        Text(
            "🧠 INTELIGENCIA TRASCENDENTAL",
            color = Color(0xFFAA44FF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "© 2025 Luis Uriel Pimentel Pérez",
            color = Color.Gray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Waveform animada (neural activity)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFF0A0A1A))
        ) {
            val w = size.width
            val h = size.height
            val path = Path()
            for (i in 0..w.toInt()) {
                val x = i.toFloat()
                val y = h / 2f + h * 0.35f * sin(wavePhase + x * 0.04f).toFloat() *
                        sin(wavePhase * 0.3f + x * 0.01f).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Color(0xFFAA44FF).copy(alpha = 0.85f), style = Stroke(2.5f))
            // Segunda onda armónica
            val path2 = Path()
            for (i in 0..w.toInt()) {
                val x = i.toFloat()
                val y = h / 2f + h * 0.20f * sin(wavePhase * 1.7f + x * 0.03f).toFloat()
                if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
            }
            drawPath(path2, color = Color(0xFF00FFFF).copy(alpha = 0.40f), style = Stroke(1.5f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Métricas evolutivas
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AIMetric("GENERACIÓN", "$generation", Color(0xFFAA44FF))
                    AIMetric("FITNESS", "%.4f".format(bestFitness), Color(0xFF44FF88))
                    AIMetric("PASOS", "$elapsedSteps", Color(0xFF44AAFF))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Tasa de mutación: %.3f".format(mutationRate), color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Slider(
                    value = mutationRate,
                    onValueChange = { mutationRate = it },
                    valueRange = 0.005f..0.30f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFAA44FF), activeTrackColor = Color(0xFFAA44FF))
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Gráfica fitness history
        if (fitnessHistory.size > 1) {
            Text("Convergencia fitness", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color(0xFF0A0A1A))
            ) {
                val w = size.width
                val h = size.height
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
                // Línea máximo
                val maxY = h - h * ((max - min) / range).coerceIn(0f, 1f)
                drawLine(Color(0xFF44FF88).copy(alpha = 0.3f), Offset(0f, maxY), Offset(w, maxY), 1f)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Controles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        AudioEngine.evolveStep()
                        generation = AudioEngine.getGeneration()
                        bestFitness = AudioEngine.getBestFitness()
                        elapsedSteps++
                        fitnessHistory.add(bestFitness)
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

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                elapsedSteps = 0
                fitnessHistory.clear()
                scope.launch {
                    AudioEngine.initializeEvolution()
                    generation = AudioEngine.getGeneration()
                    bestFitness = AudioEngine.getBestFitness()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331A00))
        ) { Text("REINICIAR EVOLUCIÓN", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }

        Spacer(modifier = Modifier.height(12.dp))

        // Parámetros del engine
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F22)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("PARÁMETROS DEL ENGINE", color = Color(0xFFAA44FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                AIParamRow("Sample rate",  "${AudioEngine.audio_fs_hz} Hz",  Color.Cyan)
                AIParamRow("Bit depth",    "${AudioEngine.audio_bit_depth} bit", Color.Cyan)
                AIParamRow("Latencia",     "${AudioEngine.audio_latencia_us} µs", Color.Yellow)
                AIParamRow("SHM buffer",   if (ShmManager.shmInitialized) "activo" else "fallback",
                    if (ShmManager.shmInitialized) Color(0xFF44FF88) else Color(0xFFFF8800))
                AIParamRow("Fase RMS",     "%.4f rad".format(AudioEngine.getPhaseErrorRms()),
                    if (AudioEngine.getPhaseErrorRms() < 0.1f) Color(0xFF44FF88) else Color.Red)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
        ) { Text("VOLVER", fontFamily = FontFamily.Monospace) }
    }
}

@Composable
fun AIMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text(label, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AIParamRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
