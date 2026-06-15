package com.ivannafusion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShmManager.initialize(this)
        AudioEngine.initialize(this)
        ThermalMonitor.initialize(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "intro") {
                        composable("intro") { IntroScreen(navController) }
                        composable("simbiosis") { SimbiosisScreen(navController) }
                        composable("monitor") { MonitorScreen(navController, AudioEngine, ShmManager) }
                        composable("settings") { SettingsAuditScreen(navController) }
                        composable("ai") { AIScreen(navController, AudioEngine) }
                    }
                }
            }
        }
    }
}
