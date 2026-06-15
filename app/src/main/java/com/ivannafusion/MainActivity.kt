package com.ivannafusion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    private val requestRecordAudio =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                AudioEngine.initialize(this)
            } else {
                android.widget.Toast.makeText(
                    this,
                    "Se necesita el micrófono para operar",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ShmManager.initialize(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            AudioEngine.initialize(this)
        } else {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }

        ThermalMonitor.initialize(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "intro") {
                        composable("intro")     { IntroScreen(navController) }
                        composable("simbiosis") { SimbiosisScreen(navController) }
                        composable("monitor")   { MonitorScreen(navController, AudioEngine, ShmManager) }
                        composable("settings")  { SettingsAuditScreen(navController) }
                        composable("ai")        { AIScreen(navController, AudioEngine) }
                    }
                }
            }
        }
    }
}
