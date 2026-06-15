package com.ivannafusion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@Composable
fun AIScreen(navController: NavController, audioEngine: AudioEngine) {
    var generation by remember { mutableIntStateOf(AudioEngine.getGeneration()) }
    var bestFitness by remember { mutableFloatStateOf(AudioEngine.getBestFitness()) }
    var mutationRate by remember { mutableFloatStateOf(0.05f) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🧠 INTELIGENCIA TRASCENDENTAL", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Generación: $generation")
                Text("Mejor fitness: %.4f".format(bestFitness))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tasa mutación: %.3f".format(mutationRate))
                Slider(value = mutationRate, onValueChange = { mutationRate = it }, valueRange = 0.01f..0.2f)
                Button(onClick = {
                    scope.launch {
                        AudioEngine.evolveStep()
                        generation = AudioEngine.getGeneration()
                        bestFitness = AudioEngine.getBestFitness()
                    }
                }) { Text("Evolucionar manual") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) { Text("Volver") }
    }
}
