package com.lumix.estimator.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberInfiniteTransition
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

private val stages = listOf("PV", "LOAD", "BATTERY", "INVERTER", "SYSTEM")
private const val STAGE_DURATION_MS = 260L

/**
 * A short staged checklist shown while a quote is being calculated — deliberately not a
 * generic spinner, so the wait itself communicates that real sizing work is happening.
 */
@Composable
fun CalculationSequenceOverlay(onComplete: () -> Unit) {
    val palette = LocalLumixPalette.current
    val reduceMotion = rememberReduceMotion()
    var completedCount by remember { mutableIntStateOf(0) }
    var ready by remember { mutableIntStateOf(0) } // 0 = running, 1 = "SYSTEM READY" beat

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            onComplete()
            return@LaunchedEffect
        }
        for (i in stages.indices) {
            delay(STAGE_DURATION_MS)
            completedCount = i + 1
        }
        ready = 1
        delay(400)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (ready == 1) "SYSTEM READY" else "CALCULATING SYSTEM",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (ready == 1) palette.energyGreenText else palette.textSecondary
            )
            Column(
                modifier = Modifier.padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                stages.forEachIndexed { index, label ->
                    AnimatedVisibility(visible = ready == 0, enter = fadeIn(), exit = fadeOut()) {
                        StageRow(label = label, done = index < completedCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(label: String, done: Boolean) {
    val palette = LocalLumixPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "stagePulse")
    val pulse = infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    ).value

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .alpha(if (done) 1f else pulse),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "…",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (done) palette.energyGreenText else palette.textSecondary
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (done) palette.textPrimary else palette.textSecondary
        )
    }
}
