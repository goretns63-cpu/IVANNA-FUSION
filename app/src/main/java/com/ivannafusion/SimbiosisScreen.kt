/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlin.math.*

private const val TAG = "IVANNA-UI"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimbiosisScreen(navController: NavController) {
    val context = LocalContext.current

    // Estado UI
    var fusionLevel     by remember { mutableFloatStateOf(0.5f) }
    var audio_latencia_us by remember { mutableIntStateOf(0) }
    var audio_error_fase_rms by remember { mutableFloatStateOf(0f) }
    var kalman_fase_rad by remember { mutableFloatStateOf(0f) }
    var kalman_frec_hz  by remember { mutableFloatStateOf(0f) }
    var shm_seq_counter by remember { mutableLongStateOf(0L) }
    var shm_buffer_activo by remember { mutableIntStateOf(0) }
    var isPredictionActive by remember { mutableStateOf(true) }
    var showGrandpaMode  by remember { mutableStateOf(false) }
    var showDiagnostics  by remember { mutableStateOf(false) }

    // Historial para línea de tiempo dual
    val pastSamples    = remember { mutableStateListOf<Float>() }
    val futureSamples  = remember { mutableStateListOf<Float>() }

    val gestureDetector = remember { IVANNAGestureDetector(context) }

    LaunchedEffect(Unit) {
        Log.d(TAG, "SimbiosisScreen iniciada")
        gestureDetector.setCallbacks(
            wristTwist    = { delta ->
                fusionLevel = (fusionLevel + delta * 0.1f).coerceIn(0f, 1f)
                AudioEngine.setFusionLevel(fusionLevel)
            },
            pinchRotate   = { /* ajuste de ancho de banda reservado */ },
            threeFingerSwipe = { navController.navigate("settings") },
            doubleTapLatency = { },
            twoFingerCircle  = { active -> isPredictionActive = active }
        )
        gestureDetector.register()

        while (true) {
            try {
                audio_latencia_us      = AudioEngine.getLatencyMicros()
                audio_error_fase_rms   = AudioEngine.getPhaseErrorRms()
                // Refrescar variables canónicas desde SHM
                ShmManager.refreshCanonicalVars()
                kalman_fase_rad  = ShmManager.kalman_fase_rad
                kalman_frec_hz   = ShmManager.kalman_frec_hz
                shm_seq_counter  = ShmManager.shm_seq_counter
                shm_buffer_activo = ShmManager.shm_buffer_activo

                // Acumular muestras pasadas (valor = error de fase como proxy de señal)
                pastSamples.add(audio_error_fase_rms)
                if (pastSamples.size > 256) pastSamples.removeAt(0)

                // Predecir muestras futuras con AudioEngine
                if (isPredictionActive && pastSamples.size >= 32) {
                    val inp = FloatArray(32) { pastSamples.getOrElse(pastSamples.size - 32 + it) { 0f } }
                    val out = FloatArray(32)
                    AudioEngine.predictSamples(inp, out)
                    futureSamples.clear()
                    futureSamples.addAll(out.toList())
                }
            } catch (_: Exception) {}
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose { gestureDetector.unregister() }
    }

    if (showGrandpaMode) {
        GrandpaModeOverlay(
            onDismiss = { showGrandpaMode = false },
            onMagic   = { fusionLevel = 1.0f; AudioEngine.setFusionLevel(1.0f) },
            onSilence  = { fusionLevel = 0f;   AudioEngine.setFusionLevel(0f) },
            onReset    = { fusionLevel = 0.5f; AudioEngine.setFusionLevel(0.5f) }
        )
        return
    }
    if (showDiagnostics) {
        DiagnosticsPanel(onDismiss = { showDiagnostics = false })
        return
    }

    val maxTemp = ThermalMonitor.getMaxTemperature()
    val thermalColor = when {
        maxTemp < 50 -> Color.Blue
        maxTemp < 70 -> Color.Cyan
        maxTemp < 85 -> Color.Magenta
        else         -> Color.Red
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
            .pointerInteropFilter { event ->
                gestureDetector.onTouchEvent(event)
                false
            }
    ) {
        // Aura térmica en bordes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, thermalColor.copy(alpha = 0.12f)),
                        radius = 1800f
                    )
                )
        )

        // Modo simbiótico extremo: solo líneas de fase y energía
        if (fusionLevel >= 0.98f) {
            SimbiosisMinorityReportUI(
                kalman_fase_rad = kalman_fase_rad,
                audio_latencia_us = audio_latencia_us
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "IVANNA FUSION",
                    color = Color.Cyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "© 2025 Luis Uriel Pimentel Pérez",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { showDiagnostics = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("DIAGNÓSTICO", fontSize = 10.sp, color = Color.Yellow,
                        fontFamily = FontFamily.Monospace)
                }

                // Dial de fusión
                FusionDial(
                    level  = fusionLevel,
                    onLevelChange = { newLevel ->
                        fusionLevel = newLevel
                        AudioEngine.setFusionLevel(newLevel)
                    },
                    modifier = Modifier.size(200.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Línea de tiempo dual (pasado + futuro predicho)
                DualTimeline(
                    pastSamples    = pastSamples,
                    futureSamples  = futureSamples.takeIf { isPredictionActive } ?: emptyList(),
                    isPredictionActive = isPredictionActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Anillo cromático de latencia
                LatencyRing(
                    latencyUs = audio_latencia_us,
                    modifier  = Modifier.size(140.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "${audio_latencia_us} µs",
                    color = latencyColor(audio_latencia_us),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Variables canónicas visibles
                CanonicalRow("audio_error_fase_rms",
                    "%.4f rad".format(audio_error_fase_rms),
                    if (audio_error_fase_rms < 0.1f) Color(0xFF44FF88) else Color.Red)
                CanonicalRow("kalman_fase_rad",
                    "%.4f".format(kalman_fase_rad), Color.Cyan)
                CanonicalRow("kalman_frec_hz",
                    "%.2f Hz".format(kalman_frec_hz),
                    if (kalman_frec_hz > 20f) Color(0xFF44FF88) else Color.Yellow)
                CanonicalRow("shm_seq_counter",
                    "$shm_seq_counter", Color.Gray)
                CanonicalRow("shm_buffer_activo",
                    "$shm_buffer_activo", Color.Gray)

                Spacer(modifier = Modifier.weight(1f))

                // Barra de navegación
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NavBtn("MONITOR")  { navController.navigate("monitor") }
                    NavBtn("ABUELO")   { showGrandpaMode = true }
                    NavBtn("IA")       { navController.navigate("ai") }
                    NavBtn("AJUSTES")  { navController.navigate("settings") }
                }
            }
        }
    }
}

@Composable
private fun CanonicalRow(name: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A3A)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Modo Minority Report: solo líneas de fase y energía */
@Composable
private fun SimbiosisMinorityReportUI(
    kalman_fase_rad: Float,
    audio_latencia_us: Int
) {
    val anim = rememberInfiniteTransition(label = "mr")
    val phase by anim.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "phase"
    )
    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Línea de fase
        for (i in 0..200) {
            val t = i / 200f
            val x = size.width * t
            val y = cy + cy * 0.4f * sin(phase + t * 6f + kalman_fase_rad)
            drawCircle(Color.Cyan.copy(alpha = 0.6f), 1.5f, Offset(x, y))
        }
        // Línea de energía
        for (i in 0..200) {
            val t = i / 200f
            val x = size.width * t
            val y = cy + cy * 0.2f * sin(phase * 1.5f + t * 4f)
            drawCircle(Color.Magenta.copy(alpha = 0.4f), 1f, Offset(x, y))
        }
        // Latencia en centro
        drawCircle(
            color = latencyColor(audio_latencia_us).copy(alpha = 0.7f),
            radius = 6f + audio_latencia_us / 300f,
            center = Offset(cx, cy),
            style = Stroke(2f)
        )
    }
}

// ── Componentes visuales ──────────────────────────────────────────────────────

@Composable
fun FusionDial(level: Float, onLevelChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "dial")
    val glow by anim.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "glow"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r  = size.minDimension * 0.45f
            // Arco de fondo
            drawArc(Color(0xFF333355), -220f, 260f, false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(14f))
            // Arco de nivel
            val sweepAngle = 260f * level
            val arcColor = when {
                level < 0.33f -> Color(0xFF4488FF)
                level < 0.66f -> Color(0xFF44FFAA)
                else          -> Color(0xFFFF44AA)
            }
            drawArc(arcColor.copy(alpha = glow), -220f, sweepAngle, false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(14f))
            // Punto indicador
            val angle = Math.toRadians((-220 + sweepAngle).toDouble())
            val px = cx + r * cos(angle).toFloat()
            val py = cy + r * sin(angle).toFloat()
            drawCircle(Color.White, 7f, Offset(px, py))
        }
        Text(
            "FUSIÓN: ${"%.2f".format(level)}",
            color = Color.White, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
        )
        Slider(
            value = level, onValueChange = onLevelChange,
            valueRange = 0f..1f,
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Cyan, activeTrackColor = Color.Cyan
            )
        )
        Text(
            if (level < 0.5f) "DISCRETO" else if (level < 0.9f) "MIXTO" else "SIMBIÓTICO",
            color = Color.Cyan.copy(alpha = 0.7f),
            fontSize = 10.sp, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DualTimeline(
    pastSamples: List<Float>,
    futureSamples: List<Float>,
    isPredictionActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(Color(0xFF050510))) {
        val w  = size.width
        val h  = size.height
        val cx = w / 2f

        // Eje X en microsegundos — etiqueta visual implícita por posición
        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(cx, 0f), Offset(cx, h), 1f)

        // Pasado (izquierda) — muestras reales
        if (pastSamples.size > 1) {
            val maxVal = pastSamples.maxOrNull()?.coerceAtLeast(0.001f) ?: 0.001f
            val step = cx / (pastSamples.size - 1).coerceAtLeast(1)
            val path = Path()
            pastSamples.forEachIndexed { i, v ->
                val x = cx - cx + i * step
                val y = h - h * (v / maxVal).coerceIn(0f, 1f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color.Cyan, style = Stroke(2f))
        }

        // Futuro (derecha) — predicho semitransparente
        if (isPredictionActive && futureSamples.size > 1) {
            val maxVal = futureSamples.maxOrNull()?.coerceAtLeast(0.001f) ?: 0.001f
            val step = cx / (futureSamples.size - 1).coerceAtLeast(1)
            val path = Path()
            futureSamples.forEachIndexed { i, v ->
                val x = cx + i * step
                val y = h - h * (v / maxVal).coerceIn(0f, 1f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color.Cyan.copy(alpha = 0.35f), style = Stroke(1.5f))
            // Destello verde cuando latencia "negativa" (predicción activa)
            drawRect(
                Color(0xFF00FF88).copy(alpha = 0.06f),
                topLeft = Offset(cx, 0f), size = Size(cx, h)
            )
        }

        // Etiquetas de tiempo
        drawText("PASADO", Color.Gray.copy(alpha = 0.5f), Offset(4f, h - 4f))
        if (isPredictionActive)
            drawText("FUTURO PREDICHO", Color.Cyan.copy(alpha = 0.4f), Offset(cx + 4f, h - 4f))
    }
}

// Helper de drawText sobre Canvas sin TextMeasurer (texto simplificado via círculos omitido)
private fun DrawScope.drawText(text: String, color: Color, at: Offset) {
    // Implementación mínima: un punto indicador de región
    drawCircle(color, 3f, at)
}

@Composable
fun LatencyRing(latencyUs: Int, modifier: Modifier = Modifier) {
    val ringColor = latencyColor(latencyUs)
    val anim = rememberInfiniteTransition(label = "ring")
    val pulse by anim.animateFloat(
        initialValue = 0.7f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulse"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension * 0.43f
        // Fondo
        drawCircle(Color(0xFF111133), r, Offset(cx, cy))
        // Anillo
        drawCircle(ringColor.copy(alpha = pulse), r, Offset(cx, cy), style = Stroke(8f))
        // Punto central
        drawCircle(ringColor.copy(alpha = 0.5f), r * 0.15f, Offset(cx, cy))
    }
}

private fun latencyColor(latencyUs: Int): Color = when {
    latencyUs in 1..1000   -> Color(0xFF00FF00)
    latencyUs in 1001..2000 -> Color(0xFFFFFF00)
    latencyUs in 2001..3000 -> Color(0xFFFFA500)
    latencyUs > 3000        -> Color.Red
    else                    -> Color.Gray
}

// ── Modo abuelo ───────────────────────────────────────────────────────────────

@Composable
fun GrandpaModeOverlay(
    onDismiss: () -> Unit,
    onMagic:   () -> Unit,
    onSilence: () -> Unit,
    onReset:   () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("IVANNA", color = Color.White, fontSize = 40.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            GrandpaBtn("ENCENDER", Color(0xFF006633)) { onMagic() }
            GrandpaBtn("SILENCIO", Color(0xFF660033)) { onSilence() }
            GrandpaBtn("MAGIA ✨", Color(0xFF334499)) { onMagic() }
            GrandpaBtn("RESET",   Color(0xFF444444)) { onReset() }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("VOLVER", fontSize = 20.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun GrandpaBtn(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        Text(label, fontSize = 28.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}

// ── Panel de diagnóstico ──────────────────────────────────────────────────────

@Composable
fun DiagnosticsPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shmStatus   = if (ShmManager.shmInitialized) "✓ OK" else "✗ FALLÓ"
    val shmColor    = if (ShmManager.shmInitialized) Color.Green else Color.Red
    val libStatus   = if (ShmManager.nativeLibLoaded) "✓ CARGADA" else "✗ FALLÓ"
    val libColor    = if (ShmManager.nativeLibLoaded) Color.Green else Color.Red
    val audioStatus = if (AudioEngine.initialized) "✓ ${AudioEngine.audio_fs_hz}Hz" else "✗ NO INICIADO"
    val audioColor  = if (AudioEngine.initialized) Color.Green else Color.Red
    val latUs       = AudioEngine.getLatencyMicros()
    val recPerm     = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("DIAGNÓSTICO DEL SISTEMA", color = Color.Yellow, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp))

        DiagRow("Librería nativa",    libStatus,    libColor)
        DiagRow("Memoria compartida", shmStatus,    shmColor)
        DiagRow("Motor de audio",     audioStatus,  audioColor)
        DiagRow("Latencia actual",    if (latUs > 0) "$latUs µs" else "NO DISPONIBLE",
            if (latUs > 0) Color.Green else Color.Red)
        DiagRow("RECORD_AUDIO",
            if (recPerm) "✓ CONCEDIDO" else "✗ DENEGADO",
            if (recPerm) Color.Green else Color.Red)

        Spacer(modifier = Modifier.height(12.dp))
        Text("ESTADO SHM", color = Color.Cyan, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

        DiagRow("shm_seq_counter",  "${ShmManager.shm_seq_counter}",    Color.White)
        DiagRow("shm_buffer_activo","${ShmManager.shm_buffer_activo}",   Color.White)
        DiagRow("kalman_fase_rad",   "%.4f".format(ShmManager.kalman_fase_rad),  Color.Cyan)
        DiagRow("kalman_frec_hz",    "%.2f Hz".format(ShmManager.kalman_frec_hz), Color.Cyan)

        if (ShmManager.lastError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("ÚLTIMO ERROR:", color = Color.Red, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace)
            Text(ShmManager.lastError!!, color = Color.Red, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) { Text("VOLVER", fontSize = 16.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
fun DiagRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}
