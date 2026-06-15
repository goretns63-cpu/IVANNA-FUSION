package com.ivannafusion

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun IntroScreen(navController: NavController) {
    val context = LocalContext.current
    var showIntro by remember { mutableStateOf(true) }
    val videoUrls = listOf(
        "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    )
    var currentVideo by remember { mutableStateOf(0) }

    if (showIntro) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.parse(videoUrls[currentVideo]))
                        setOnCompletionListener {
                            currentVideo = (currentVideo + 1) % videoUrls.size
                            setVideoURI(Uri.parse(videoUrls[currentVideo]))
                            start()
                        }
                        start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            androidx.compose.material3.Button(
                onClick = { showIntro = false },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                androidx.compose.material3.Text("SALTAR")
            }
        }
        LaunchedEffect(Unit) { delay(20000); showIntro = false }
    } else {
        navController.navigate("simbiosis")
    }
}
