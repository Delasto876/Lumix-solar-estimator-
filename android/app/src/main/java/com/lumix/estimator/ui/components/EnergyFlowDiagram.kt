package com.lumix.estimator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import com.lumix.estimator.ui.theme.rememberReduceMotion

data class FlowNode(
    val id: String,
    val title: String,
    val subtitle: String,
    val glyph: String,
    val accentColor: Color,
    val detail: String
)

/** A vertical Sun → Panels → Inverter → Battery → Home flow with a subtle animated pulse on each connector. */
@Composable
fun EnergyFlowDiagram(
    nodes: List<FlowNode>,
    modifier: Modifier = Modifier,
    onNodeClick: (FlowNode) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    Column(modifier = modifier.fillMaxWidth()) {
        nodes.forEachIndexed { index, node ->
            FlowNodeRow(node = node, onClick = { onNodeClick(node) })
            if (index != nodes.lastIndex) {
                FlowConnector(color = node.accentColor, reduceMotion = reduceMotion)
            }
        }
    }
}

@Composable
private fun FlowNodeRow(node: FlowNode, onClick: () -> Unit) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LumixRadius.md))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(node.accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(node.glyph, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(node.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text(node.subtitle, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
    }
}

@Composable
private fun FlowConnector(color: Color, reduceMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "flowConnector")
    val t = if (reduceMotion) 0f else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "flowDot"
    ).value

    Canvas(
        modifier = Modifier
            .padding(start = 32.dp)
            .width(3.dp)
            .height(26.dp)
    ) {
        drawLine(
            color = color.copy(alpha = 0.22f),
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = size.width
        )
        if (!reduceMotion) {
            drawCircle(
                color = color,
                radius = size.width * 1.6f,
                center = Offset(size.width / 2, size.height * t)
            )
        }
    }
}
