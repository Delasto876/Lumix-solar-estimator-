package com.lumix.estimator.ui.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

private val stages = listOf(
    "Analyzing your energy usage…",
    "Calculating solar production…",
    "Optimizing panel configuration…",
    "Sizing your battery requirements…",
    "Finding your best system…"
)

/** A short staged sequence shown while a quote is being calculated and saved. */
@Composable
fun CalculationSequenceOverlay(onComplete: () -> Unit) {
    val palette = LocalLumixPalette.current
    val reduceMotion = rememberReduceMotion()
    var stageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            onComplete()
            return@LaunchedEffect
        }
        for (i in stages.indices) {
            stageIndex = i
            delay(320)
        }
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(color = palette.solarYellow, strokeWidth = 3.dp)
            AnimatedContent(
                targetState = stageIndex,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "calcStage"
            ) { index ->
                Text(
                    stages[index],
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}
