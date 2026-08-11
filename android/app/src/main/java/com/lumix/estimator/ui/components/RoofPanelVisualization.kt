package com.lumix.estimator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixMotion
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** A simple grid of panels that fill in one by one as [panelCount] changes. */
@Composable
fun RoofPanelVisualization(
    panelCount: Int,
    modifier: Modifier = Modifier,
    panelsPerRow: Int = 4
) {
    val reduceMotion = rememberReduceMotion()
    var visibleCount by remember(panelCount, reduceMotion) {
        mutableIntStateOf(if (reduceMotion) panelCount else 0)
    }

    LaunchedEffect(panelCount, reduceMotion) {
        if (reduceMotion) {
            visibleCount = panelCount
            return@LaunchedEffect
        }
        visibleCount = 0
        repeat(panelCount) {
            delay(60)
            visibleCount++
        }
    }

    if (panelCount <= 0) return

    val rows = (panelCount + panelsPerRow - 1) / panelsPerRow
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until panelsPerRow) {
                    val index = r * panelsPerRow + c
                    if (index < panelCount) {
                        PanelTile(lit = index < visibleCount, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelTile(lit: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    val alpha by animateFloatAsState(targetValue = if (lit) 1f else 0f, animationSpec = LumixMotion.gentle(), label = "panelAlpha")
    val scale by animateFloatAsState(targetValue = if (lit) 1f else 0.6f, animationSpec = LumixMotion.gentle(), label = "panelScale")
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .height(28.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .clip(shape)
            .background(palette.technicalCyan.copy(alpha = 0.22f))
            .border(1.dp, palette.technicalCyan.copy(alpha = 0.4f), shape)
    )
}
