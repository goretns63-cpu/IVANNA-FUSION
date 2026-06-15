package com.ivannafusion

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun IntroScreen(navController: NavController) {
    var navigated by remember { mutableStateOf(false) }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseAnim.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotAnim = rememberInfiniteTransition(label = "rot")
    val rotation by rotAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val glowAnim = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glowAnim.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    if (!navigated) {
        LaunchedEffect(Unit) {
            delay(4500)
            navigated = true
            navController.navigate("simbiosis") { popUpTo("intro") { inclusive = true } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D0D2B), Color(0xFF000000)),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Canvas con anillos orbitales
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.minDimension * 0.30f

            // Anillo exterior giratorio
            for (i in 0 until 8) {
                val angle = Math.toRadians((rotation + i * 45.0))
                val x = cx + (baseR * 1.35f) * cos(angle).toFloat()
                val y = cy + (baseR * 1.35f) * sin(angle).toFloat()
                drawCircle(
                    color = Color(0xFF00FFFF).copy(alpha = glowAlpha * 0.8f),
                    radius = 5f * pulse,
                    center = Offset(x, y)
                )
            }

            // Anillo medio (contra-rotación)
            for (i in 0 until 6) {
                val angle = Math.toRadians((-rotation * 0.7 + i * 60.0))
                val x = cx + (baseR * 1.05f) * cos(angle).toFloat()
                val y = cy + (baseR * 1.05f) * sin(angle).toFloat()
                drawCircle(
                    color = Color(0xFFFF00FF).copy(alpha = glowAlpha * 0.6f),
                    radius = 4f * pulse,
                    center = Offset(x, y)
                )
            }

            // Núcleo pulsante
            drawCircle(
                color = Color(0xFF00FFFF).copy(alpha = glowAlpha * 0.25f),
                radius = baseR * pulse * 1.05f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xFF00FFFF).copy(alpha = 0.60f),
                radius = baseR * pulse * 0.82f,
                center = Offset(cx, cy),
                style = Stroke(width = 3.5f)
            )
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = 0.85f),
                radius = baseR * pulse * 0.48f,
                center = Offset(cx, cy)
            )
        }

        // Texto
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "IVANNA",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 10.sp
            )
            Text(
                text = "FUSION",
                color = Color(0xFF00FFFF),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "TRASCENDENTAL DSP ENGINE",
                color = Color(0xFF00FFFF).copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
        }

        // Botón SALTAR
        Button(
            onClick = {
                navigated = true
                navController.navigate("simbiosis") { popUpTo("intro") { inclusive = true } }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A1A3A),
                contentColor = Color(0xFF00FFFF)
            )
        ) {
            Text("SALTAR", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
