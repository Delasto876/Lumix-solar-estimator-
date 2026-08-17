package com.lumix.estimator.site

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * The same summary shown after tracing a roof on the map or entering one manually — one
 * shared readout so both entry paths visibly produce the same kind of result.
 */
@Composable
fun RoofPlaneCard(plane: RoofPlane, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    SectionCard(title = plane.label, modifier = modifier) {
        StatRow("Area", "%.1f m²".format(plane.roofAreaM2), "Usable", "%.1f m²".format(plane.usableAreaM2))
        val azimuth = plane.azimuthDegrees ?: plane.suggestedAzimuthDegrees
        StatRow(
            "Orientation",
            azimuth?.let { "${RoofGeometryEngine.compassLabel(it)} ${it.toInt()}°" + (if (plane.azimuthDegrees == null) " (suggested)" else "") } ?: "Not set",
            "Pitch",
            plane.pitchDegrees?.let { "${it.toInt()}°" } ?: "Not provided"
        )
        val layout = plane.panelLayout
        StatRow(
            "Panels",
            layout?.let { "${it.panelCount} × ${it.panelWattage.toInt()} W" } ?: "—",
            "Capacity",
            layout?.let { "%.2f kW".format(it.totalCapacityKw) } ?: "—"
        )
        if (plane.pitchDegrees == null) {
            Text(
                "Roof pitch not provided — area shown is the horizontal satellite projection, a preliminary estimate.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
        if (onRemove != null) {
            Text(
                "Remove",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.warningRedText,
                modifier = Modifier.padding(top = 4.dp).clickable { onRemove() }
            )
        }
    }
}

@Composable
private fun StatRow(label1: String, value1: String, label2: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(label1, value1, Modifier.weight(1f))
        Stat(label2, value2, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}
