/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun MonitorScreen(
    navController: NavController,
    audioEngine: AudioEngine,
    shmManager: ShmManager
) {
    val context = LocalContext.current
    var audio_fs_hz by remember { mutableIntStateOf(AudioEngine.audio_fs_hz) }
    var audio_bit_depth by remember { mutableIntStateOf(AudioEngine.audio_bit_depth) }
    var audio_latencia_us by remember { mutableIntStateOf(0) }
    var audio_error_fase_rms by remember { mutableFloatStateOf(0f) }
    var evo_generacion by remember { mutableIntStateOf(0) }
    var evo_fitness_mejor by remember { mutableFloatStateOf(0f) }
    var temp_cpu_core0 by remember { mutableIntStateOf(0) }
    var temp_gpu by remember { mutableIntStateOf(0) }

    // Estado del SHM (observable)
    val shmStatus by shmManager.shmStatus.collectAsState()

    LaunchedEffect(Unit) {
        AudioEngine.initializeEvolution()
        while (true) {
            audio_latencia_us = AudioEngine.getLatencyMicros()
            audio_error_fase_rms = AudioEngine.getPhaseErrorRms()
            temp_cpu_core0 = ThermalMonitor.temp_cpu_core0.toInt()
            temp_gpu = ThermalMonitor.temp_gpu.toInt()
            AudioEngine.evolveStep()
            evo_generacion = AudioEngine.getGeneration()
            evo_fitness_mejor = AudioEngine.getBestFitness()
            delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            "IVANNA MONITOR",
            color = Color.Cyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mostrar estado de la memoria compartida
        Text(
            text = "SHM: $shmStatus",
            color = Color.Green,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Métricas canónicas
        MetricRow("audio_fs_hz", "$audio_fs_hz Hz")
        MetricRow("audio_bit_depth", "$audio_bit_depth bits")
        MetricRow("audio_latencia_us", "$audio_latencia_us µs")
        MetricRow("audio_error_fase_rms", "%.4f rad".format(audio_error_fase_rms))
        MetricRow("evo_generacion", "$evo_generacion")
        MetricRow("evo_fitness_mejor", "%.4f".format(evo_fitness_mejor))
        MetricRow("temp_cpu_core0", "$temp_cpu_core0 °C")
        MetricRow("temp_gpu", "$temp_gpu °C")

        Spacer(modifier = Modifier.height(16.dp))

        // Botón para reiniciar el motor de audio y SHM
        Button(
            onClick = { audioEngine.restart() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("REINICIAR AUDIO", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Espectrograma OpenGL
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(SpectrogramRenderer())
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Campo de error de fase (mapa termográfico simplificado)
        PhaseErrorField(audio_error_fase_rms)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { navController.navigate("simbiosis") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VOLVER A SIMBIOSIS")
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PhaseErrorField(errorRms: Float) {
    val color = when {
        errorRms < 0.01f -> Color.Blue
        errorRms < 0.05f -> Color.Cyan
        errorRms < 0.1f -> Color.Yellow
        else -> Color.Red
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(color.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Error de fase: %.4f rad".format(errorRms),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

class SpectrogramRenderer : GLSurfaceView.Renderer {
    private var time = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        time += 0.016f
        // Renderizado de espectrograma vía shader (simplificado)
        // En producción: implementar FBO con FFT texture
    }
}
