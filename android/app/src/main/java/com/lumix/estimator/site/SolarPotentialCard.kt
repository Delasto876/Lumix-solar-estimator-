package com.lumix.estimator.site

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.site.geometry.RoofScoreCalculator
import com.lumix.estimator.site.geometry.SolarSuitabilityCalculator
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * The polished "here's what your roof can do" summary — a diagram of the actual panel layout,
 * the headline numbers, a 100-point Roof Score breakdown, and (once wired in a later phase)
 * "Use This Roof" to carry these numbers into the estimator. Everything here is read from
 * [plane], never recomputed or approximated further — this is a presentation layer over
 * [RoofGeometryEngine] and [com.lumix.estimator.site.geometry.PanelLayoutOptimizer]'s own output.
 */
@Composable
fun SolarPotentialCard(
    plane: RoofPlane,
    latitude: Double,
    longitude: Double,
    onUseThisRoof: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val score = remember(plane, latitude) {
        RoofScoreCalculator.score(
            usableAreaM2 = plane.usableAreaM2,
            roofAreaM2 = plane.roofAreaM2,
            azimuthDegrees = plane.azimuthDegrees ?: plane.suggestedAzimuthDegrees,
            latitude = latitude,
            pitchDegrees = plane.pitchDegrees,
            shadingFactor = plane.shadingFactor
        )
    }
    // Site Survey / Solar Mapping round (spec "Show roof areas using a clear suitability gradient
    // such as: Excellent/Good/Moderate/Poor/Unsuitable"): a narrower, exposure-only companion to
    // the Roof Score above — see SolarSuitabilityCalculator's own doc for the two computation paths.
    val suitability = remember(plane, latitude) { SolarSuitabilityCalculator.evaluate(plane, latitude) }

    SectionCard(title = "Solar Potential — ${plane.label}", modifier = modifier) {
        RoofPlaneDiagram(plane = plane, modifier = Modifier.padding(vertical = 4.dp))

        StatRow("Roof Area", "%.1f m²".format(plane.roofAreaM2), "Usable", "%.1f m²".format(plane.usableAreaM2))
        val layout = plane.panelLayout
        StatRow(
            "Panels",
            layout?.let { "${it.panelCount} × ${it.panelWattage.toInt()} W" } ?: "—",
            "Potential PV",
            layout?.let { "%.2f kW".format(it.totalCapacityKw) } ?: "—"
        )
        val azimuth = plane.azimuthDegrees ?: plane.suggestedAzimuthDegrees
        StatRow(
            "Orientation",
            azimuth?.let { "${RoofGeometryEngine.compassLabel(it)} ${it.toInt()}°" } ?: "Not set",
            "Pitch",
            plane.pitchDegrees?.let { "${it.toInt()}°" } ?: "Not provided"
        )
        StatRow("Solar Exposure", suitability.tier.label, "Exposure Score", "${(suitability.score0to1 * 100).toInt()}%")
        Text(suitability.disclaimer, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SUN PATH", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textSecondary)
            SunPathDiagram(latitude = latitude, longitude = longitude, roofAzimuthDegrees = azimuth)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ROOF SCORE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Text(
                    "${score.total} / 100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.solarYellowText
                )
            }
            ScoreBar("Area", score.areaScore)
            ScoreBar("Orientation", score.orientationScore)
            ScoreBar("Pitch", score.pitchScore)
            ScoreBar("Usable space", score.usableSpaceScore)
            ScoreBar("Shading", score.shadingScore)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MONTHLY PEAK SUN HOURS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textSecondary)
            MonthlyPshChart(latitude = latitude, longitude = longitude)
        }

        Text(
            "Estimates only, from a hand-traced satellite polygon or typed dimensions — verify on-site before installation. Not a certified engineering assessment.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        if (onUseThisRoof != null) {
            LumixPrimaryButton(text = "Use This Roof", onClick = onUseThisRoof, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ScoreBar(label: String, scoreOutOf20: Int) {
    val palette = LocalLumixPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.weight(1f))
        Text("$scoreOutOf20/20", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}

@Composable
private fun StatRow(label1: String, value1: String, label2: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(label1, value1, Modifier.weight(1f))
        if (label2.isNotEmpty()) Stat(label2, value2, Modifier.weight(1f))
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
