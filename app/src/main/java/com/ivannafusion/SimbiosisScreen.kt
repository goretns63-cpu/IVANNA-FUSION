/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "IVANNA-UI"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimbiosisScreen(navController: NavController) {
    val context = LocalContext.current
    var fusionLevel by remember { mutableFloatStateOf(0.5f) }
    var latencyUs by remember { mutableIntStateOf(0) }
    var phaseError by remember { mutableFloatStateOf(0f) }
    var isPredictionActive by remember { mutableStateOf(true) }
    var showGrandpaMode by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val gestureDetector = remember { IVANNAGestureDetector(context) }

    LaunchedEffect(Unit) {
        Log.d(TAG, "SimbiosisScreen iniciada")
        gestureDetector.setCallbacks(
            wristTwist = { delta ->
                fusionLevel = (fusionLevel + delta * 0.1f).coerceIn(0f, 1f)
                AudioEngine.setFusionLevel(fusionLevel)
            },
            pinchRotate = { },
            threeFingerSwipe = { navController.navigate("settings") },
            doubleTapLatency = { },
            twoFingerCircle = { active -> isPredictionActive = active }
        )
        gestureDetector.register()

        while (true) {
            try {
                latencyUs = AudioEngine.getLatencyMicros()
                phaseError = AudioEngine.getPhaseErrorRms()
            } catch (e: Exception) { }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose { gestureDetector.unregister() }
    }

    if (showGrandpaMode) {
        GrandpaModeOverlay(
            onDismiss = { showGrandpaMode = false },
            onMagic = { fusionLevel = 1.0f; AudioEngine.setFusionLevel(1.0f) },
            onSilence = { fusionLevel = 0f; AudioEngine.setFusionLevel(0f) },
            onReset = { fusionLevel = 0.5f; AudioEngine.setFusionLevel(0.5f) }
        )
    } else if (showDiagnostics) {
        DiagnosticsPanel(onDismiss = { showDiagnostics = false })
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A1A))
                .pointerInteropFilter { event ->
                    gestureDetector.onTouchEvent(event)
                    // false: Compose sigue propagando el evento a los hijos (Buttons, etc.)
                    // true bloquearía todos los clicks de la pantalla
                    false
                }
        ) {
            val maxTemp = ThermalMonitor.getMaxTemperature()
            val thermalColor = when {
                maxTemp < 50 -> Color.Blue
                maxTemp < 70 -> Color.Cyan
                maxTemp < 85 -> Color.Magenta
                else -> Color.Red
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, thermalColor.copy(alpha = 0.1f)),
                            center = Offset.Infinite
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "IVANNA FUSION",
                    color = Color.Cyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
                )

                Button(
                    onClick = { showDiagnostics = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("DIAGNÓSTICO", fontSize = 10.sp, color = Color.Yellow)
                }

                FusionDial(
                    level = fusionLevel,
                    onLevelChange = { newLevel ->
                        fusionLevel = newLevel
                        AudioEngine.setFusionLevel(newLevel)
                    },
                    modifier = Modifier.size(200.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                DualTimeline(
                    isPredictionActive = isPredictionActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LatencyRing(
                    latencyUs = latencyUs,
                    modifier = Modifier.size(140.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${latencyUs} µs",
                    color = when {
                        latencyUs in 1..1000 -> Color.Green
                        latencyUs in 1001..2000 -> Color.Yellow
                        latencyUs in 2001..3000 -> Color(0xFFFFA500)
                        else -> if (latencyUs <= 0) Color.Gray else Color.Red
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Error fase: %.4f rad".format(phaseError),
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { navController.navigate("monitor") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A3A))
                    ) {
                        Text("MONITOR", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { showGrandpaMode = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1A1A))
                    ) {
                        Text("ABUELO", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { navController.navigate("ai") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A103A))
                    ) {
                        Text("IA", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { navController.navigate("settings") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A1A))
                    ) {
                        Text("AJUSTES", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val nativeLibStatus = if (ShmManager.nativeLibLoaded) "✓ CARGADA" else "✗ FALLÓ"
    val nativeLibColor = if (ShmManager.nativeLibLoaded) Color.Green else Color.Red

    val shmStatus = if (ShmManager.shmInitialized) "✓ OK" else "✗ FALLÓ"
    val shmColor = if (ShmManager.shmInitialized) Color.Green else Color.Red

    val audioInitStatus = if (AudioEngine.initialized) "✓ ${AudioEngine.audio_fs_hz}Hz" else "✗ NO INICIADO"
    val audioColor = if (AudioEngine.initialized) Color.Green else Color.Red

    val latencyStatus = AudioEngine.getLatencyMicros()
    val latencyText = if (latencyStatus > 0) "$latencyStatus µs" else "NO DISPONIBLE"

    val recordPerm = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val modifyPerm = context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED
    val writePerm = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "DIAGNÓSTICO DEL SISTEMA",
            color = Color.Yellow,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        DiagRow("Librería nativa (.so)", nativeLibStatus, nativeLibColor)
        DiagRow("Memoria compartida", shmStatus, shmColor)
        DiagRow("Motor de audio", audioInitStatus, audioColor)
        DiagRow("Latencia actual", latencyText, if (latencyStatus > 0) Color.Green else Color.Red)

        Spacer(modifier = Modifier.height(16.dp))

        Text("PERMISOS:", color = Color.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        DiagRow("RECORD_AUDIO", if (recordPerm) "✓ CONCEDIDO" else "✗ DENEGADO", if (recordPerm) Color.Green else Color.Red)
        DiagRow("MODIFY_AUDIO_SETTINGS", if (modifyPerm) "✓ CONCEDIDO" else "✗ DENEGADO", if (modifyPerm) Color.Green else Color.Red)
        DiagRow("WRITE_EXTERNAL_STORAGE", if (writePerm) "✓ CONCEDIDO" else "✗ DENEGADO", if (writePerm) Color.Green else Color.Red)

        if (ShmManager.lastError != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("ÚLTIMO ERROR:", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(ShmManager.lastError!!, color = Color.Red, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("VOLVER", fontSize = 16.sp)
        }
    }
}

@Composable
fun DiagRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp)
        Text(text = value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FusionDial(level: Float, onLevelChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2 - 16

            drawCircle(
                color = Color(0xFF333333),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 16f)
            )

            val sweepAngle = level * 270f
            drawArc(
                color = when {
                    level < 0.33f -> Color(0xFF0088FF)
                    level < 0.66f -> Color.Cyan
                    else -> Color.Magenta
                },
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 16f)
            )

            val indicatorAngle = Math.toRadians((135f + sweepAngle).toDouble())
            val indicatorX = centerX + (radius - 8) * cos(indicatorAngle).toFloat()
            val indicatorY = centerY + (radius - 8) * sin(indicatorAngle).toFloat()

            drawCircle(
                color = Color.White,
                radius = 12f,
                center = Offset(indicatorX, indicatorY)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(level * 100).toInt()}%",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "FUSIÓN",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun DualTimeline(isPredictionActive: Boolean, modifier: Modifier = Modifier) {
    val realSamples = remember { FloatArray(128) { kotlin.math.sin(it * 0.15f) * 40 } }
    val predictedSamples = remember { FloatArray(128) { kotlin.math.sin((it + 128) * 0.15f) * 40 } }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val pastWidth = width * 0.5f

        for (i in 0 until 127) {
            val x1 = i * pastWidth / 128
            val x2 = (i + 1) * pastWidth / 128
            val y1 = centerY - realSamples[i]
            val y2 = centerY - realSamples[i + 1]

            drawLine(
                color = Color(0xFF00FF88),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.5f
            )
        }

        if (isPredictionActive) {
            for (i in 0 until 127) {
                val x1 = pastWidth + i * (width - pastWidth) / 128
                val x2 = pastWidth + (i + 1) * (width - pastWidth) / 128
                val y1 = centerY - predictedSamples[i]
                val y2 = centerY - predictedSamples[i + 1]

                drawLine(
                    color = Color(0xFF00FF88).copy(alpha = 0.35f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.5f
                )
            }
        }

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(pastWidth, 0f),
            end = Offset(pastWidth, height),
            strokeWidth = 1f
        )
    }
}

@Composable
fun LatencyRing(latencyUs: Int, modifier: Modifier = Modifier) {
    val color = when {
        latencyUs <= 0 -> Color(0xFF444444)
        latencyUs < 1000 -> Color(0xFF00FF00)
        latencyUs < 2000 -> Color.Yellow
        latencyUs < 3000 -> Color(0xFFFF8800)
        else -> Color.Red
    }

    val progress = if (latencyUs > 0) (latencyUs / 3000f).coerceIn(0f, 1f) else 0f

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 8

        drawCircle(
            color = Color(0xFF222222),
            radius = radius,
            style = Stroke(width = 12f)
        )

        if (progress > 0) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 12f)
            )
        }
    }
}

@Composable
fun GrandpaModeOverlay(
    onDismiss: () -> Unit,
    onMagic: () -> Unit,
    onSilence: () -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MODO ABUELO",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 32.dp)
            )

            GrandpaButton("ENCENDER", Color(0xFF00AA00), onMagic)
            GrandpaButton("SILENCIO", Color(0xFFAAAA00), onSilence)
            GrandpaButton("MAGIA", Color(0xFFAA00AA), onMagic)
            GrandpaButton("RESET", Color(0xFFAA0000), onReset)

            Button(
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text("VOLVER", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun GrandpaButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(90.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text = text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
    }
}
