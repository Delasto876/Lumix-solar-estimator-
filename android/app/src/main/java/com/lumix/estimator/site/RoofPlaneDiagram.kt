package com.lumix.estimator.site

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * A top-down diagram of a roof plane's outline with every packed panel drawn as a rectangle —
 * a real visualization of [RoofPlane.panelLayout], not just a placement count. Distinct from
 * the live satellite map: this reads the same [GeoPoint] geometry but projects it into its own
 * self-contained local-meters coordinate frame, so it renders identically whether the roof was
 * traced on the map or built from typed manual dimensions.
 */
@Composable
fun RoofPlaneDiagram(plane: RoofPlane, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    if (plane.vertices.size < 3) return

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        val reference = plane.vertices.first()
        val localVertices = RoofGeometryEngine.toLocalMeters(plane.vertices, reference)

        val minX = localVertices.minOf { it.first }
        val maxX = localVertices.maxOf { it.first }
        val minY = localVertices.minOf { it.second }
        val maxY = localVertices.maxOf { it.second }
        val spanX = (maxX - minX).coerceAtLeast(0.5)
        val spanY = (maxY - minY).coerceAtLeast(0.5)

        val padding = 0.85f
        val scale = (minOf(size.width / spanX, size.height / spanY) * padding).toFloat()
        val drawnWidth = (spanX * scale).toFloat()
        val drawnHeight = (spanY * scale).toFloat()
        val marginX = (size.width - drawnWidth) / 2f
        val marginY = (size.height - drawnHeight) / 2f

        // Local-meters Y is "north"; flip to canvas Y (which increases downward) so north is up.
        fun toCanvas(x: Double, y: Double): Offset = Offset(
            marginX + ((x - minX) * scale).toFloat(),
            size.height - marginY - ((y - minY) * scale).toFloat()
        )

        val outline = Path().apply {
            localVertices.forEachIndexed { index, (x, y) ->
                val p = toCanvas(x, y)
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(outline, color = palette.solarYellow.copy(alpha = 0.10f))
        drawPath(outline, color = palette.solarYellow, style = Stroke(width = 3f))

        val layout = plane.panelLayout ?: return@Canvas
        val panelWidthPx = (layout.panelWidthM * scale).toFloat()
        val panelHeightPx = (layout.panelHeightM * scale).toFloat()

        layout.placements.forEach { placement ->
            val localCenter = RoofGeometryEngine.toLocalMeters(listOf(placement.center), reference).first()
            val center = toCanvas(localCenter.first, localCenter.second)
            rotate(degrees = placement.rotationDegrees.toFloat(), pivot = center) {
                val topLeft = Offset(center.x - panelWidthPx / 2f, center.y - panelHeightPx / 2f)
                val panelSize = Size(panelWidthPx, panelHeightPx)
                drawRect(color = palette.technicalCyan.copy(alpha = 0.28f), topLeft = topLeft, size = panelSize)
                drawRect(color = palette.technicalCyan, topLeft = topLeft, size = panelSize, style = Stroke(width = 1.5f))
            }
        }
    }
}
