package com.lumix.estimator.ui.simulation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SystemStatus
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.LumixPalette
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlin.math.max
import kotlin.math.min

@Composable
fun HouseSimulationVisual(
    frame: SimFrame,
    config: SimSystemConfig,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val reduceMotion = rememberReduceMotion()

    val infinite = rememberInfiniteTransition(label = "houseFlow")
    val flowPhase = if (reduceMotion) 0f else infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "flowPhase"
    ).value
    val glowPulse = if (reduceMotion) 0f else infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPulse"
    ).value

    val maxPv = max(config.pvCapacityKw, 0.1)
    val maxBatteryFlow = max(config.batteryCapacityKwh * 0.5, 0.5)
    val maxLoad = max(config.peakLoadKw, 0.5)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        drawScene(
            frame = frame,
            config = config,
            palette = palette,
            flowPhase = flowPhase,
            glowPulse = glowPulse,
            reduceMotion = reduceMotion,
            maxPv = maxPv,
            maxBatteryFlow = maxBatteryFlow,
            maxLoad = maxLoad
        )
    }
}

private fun DrawScope.drawScene(
    frame: SimFrame,
    config: SimSystemConfig,
    palette: LumixPalette,
    flowPhase: Float,
    glowPulse: Float,
    reduceMotion: Boolean,
    maxPv: Double,
    maxBatteryFlow: Double,
    maxLoad: Double
) {
    val w = size.width
    val h = size.height

    // ---- sky ambience: sun by day, stars by night ----
    val dayFraction = ((frame.hour - 5.5) / (18.5 - 5.5)).coerceIn(0.0, 1.0)
    val isDaytime = frame.hour in 5.5..18.5
    val sunX = w * (0.15f + 0.7f * dayFraction.toFloat())
    val sunY = h * (0.26f - 0.14f * kotlin.math.sin(Math.PI * dayFraction).toFloat())

    if (isDaytime) {
        val intensity = (frame.pvKw / maxPv).coerceIn(0.15, 1.0).toFloat()
        val glow = 1f + glowPulse * 0.08f
        drawCircle(LumixColors.SolarYellow.copy(alpha = 0.10f * intensity), radius = 46.dp.toPx() * glow, center = Offset(sunX, sunY))
        drawCircle(LumixColors.SolarYellow.copy(alpha = 0.22f * intensity), radius = 26.dp.toPx() * glow, center = Offset(sunX, sunY))
        drawCircle(LumixColors.SolarYellow.copy(alpha = 0.55f + 0.45f * intensity), radius = 13.dp.toPx(), center = Offset(sunX, sunY))
    } else {
        drawCircle(palette.textSecondary.copy(alpha = 0.5f), radius = 10.dp.toPx(), center = Offset(w * 0.78f, h * 0.16f))
        val starSeeds = listOf(0.1f to 0.12f, 0.2f to 0.22f, 0.35f to 0.1f, 0.5f to 0.2f, 0.65f to 0.09f, 0.85f to 0.18f)
        starSeeds.forEach { (fx, fy) ->
            drawCircle(palette.textSecondary.copy(alpha = 0.4f), radius = 1.6.dp.toPx(), center = Offset(w * fx, h * fy))
        }
    }

    // ---- ground shadow ----
    drawOval(
        color = Color.Black.copy(alpha = if (palette.isDark) 0.35f else 0.12f),
        topLeft = Offset(w * 0.14f, h * 0.90f),
        size = Size(w * 0.62f, h * 0.055f)
    )

    // ---- house geometry ----
    val frontTopLeft = Offset(w * 0.20f, h * 0.58f)
    val frontTopRight = Offset(w * 0.62f, h * 0.58f)
    val frontBottomRight = Offset(w * 0.62f, h * 0.87f)
    val frontBottomLeft = Offset(w * 0.20f, h * 0.87f)
    val sideTopRight = Offset(w * 0.78f, h * 0.50f)
    val sideBottomRight = Offset(w * 0.78f, h * 0.80f)

    val wallLit = if (palette.isDark) Color(0xFF1B222D) else Color(0xFFE7EAF0)
    val wallShadow = if (palette.isDark) Color(0xFF11161F) else Color(0xFFC9CFD9)

    // front wall
    drawPath(
        Path().apply {
            moveTo(frontTopLeft.x, frontTopLeft.y)
            lineTo(frontTopRight.x, frontTopRight.y)
            lineTo(frontBottomRight.x, frontBottomRight.y)
            lineTo(frontBottomLeft.x, frontBottomLeft.y)
            close()
        },
        color = wallLit
    )
    // side wall (shadowed)
    drawPath(
        Path().apply {
            moveTo(frontTopRight.x, frontTopRight.y)
            lineTo(sideTopRight.x, sideTopRight.y)
            lineTo(sideBottomRight.x, sideBottomRight.y)
            lineTo(frontBottomRight.x, frontBottomRight.y)
            close()
        },
        color = wallShadow
    )

    // roof
    val roofApexLeft = Offset(w * 0.24f, h * 0.30f)
    val roofApexRight = Offset(w * 0.66f, h * 0.30f)
    val roofFrontLeft = frontTopLeft + Offset(-w * 0.03f, 0f)
    val roofFrontRight = frontTopRight + Offset(w * 0.03f, -h * 0.01f)
    val roofSideTop = sideTopRight + Offset(w * 0.02f, -h * 0.02f)

    val roofLit = if (palette.isDark) Color(0xFF232B38) else Color(0xFFB9C2D0)
    val roofShadow = if (palette.isDark) Color(0xFF161B24) else Color(0xFF9AA4B4)

    val mainRoofPath = Path().apply {
        moveTo(roofApexLeft.x, roofApexLeft.y)
        lineTo(roofApexRight.x, roofApexRight.y)
        lineTo(roofFrontRight.x, roofFrontRight.y)
        lineTo(roofFrontLeft.x, roofFrontLeft.y)
        close()
    }
    drawPath(mainRoofPath, color = roofLit)

    drawPath(
        Path().apply {
            moveTo(roofApexRight.x, roofApexRight.y)
            lineTo(roofSideTop.x, roofSideTop.y)
            lineTo(sideTopRight.x, sideTopRight.y)
            lineTo(roofFrontRight.x, roofFrontRight.y)
            close()
        },
        color = roofShadow
    )

    // ---- solar panels on the main roof plane (bilinear placement) ----
    if (config.panelCount > 0) {
        val cols = 4
        val rows = ((min(config.panelCount, 12) + cols - 1) / cols).coerceAtLeast(1)
        val totalToShow = min(config.panelCount, cols * rows)
        val lit = (frame.pvKw / maxPv).coerceIn(0.0, 1.0).toFloat()
        var placed = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (placed >= totalToShow) break
                val u0 = 0.08f + c * (0.84f / cols)
                val u1 = u0 + (0.84f / cols) * 0.86f
                val v0 = 0.18f + r * (0.62f / rows)
                val v1 = v0 + (0.62f / rows) * 0.78f
                val p00 = bilerp(roofApexLeft, roofApexRight, roofFrontLeft, roofFrontRight, u0, v0)
                val p10 = bilerp(roofApexLeft, roofApexRight, roofFrontLeft, roofFrontRight, u1, v0)
                val p01 = bilerp(roofApexLeft, roofApexRight, roofFrontLeft, roofFrontRight, u0, v1)
                val p11 = bilerp(roofApexLeft, roofApexRight, roofFrontLeft, roofFrontRight, u1, v1)
                val panelPath = Path().apply {
                    moveTo(p00.x, p00.y)
                    lineTo(p10.x, p10.y)
                    lineTo(p11.x, p11.y)
                    lineTo(p01.x, p01.y)
                    close()
                }
                drawPath(panelPath, color = Color(0xFF0B1220).copy(alpha = 0.92f))
                drawPath(panelPath, color = LumixColors.SolarYellow.copy(alpha = 0.08f + 0.22f * lit))
                drawPath(panelPath, color = palette.outline, style = Stroke(width = 1f))
                placed++
            }
        }
    }

    // ---- garage ----
    val garageLeft = Offset(w * 0.62f, h * 0.68f)
    drawRoundRect(
        color = wallShadow,
        topLeft = garageLeft,
        size = Size(w * 0.16f, h * 0.19f),
        cornerRadius = CornerRadius(3.dp.toPx())
    )
    drawRoundRect(
        color = wallLit.copy(alpha = 0.6f),
        topLeft = garageLeft + Offset(w * 0.015f, h * 0.05f),
        size = Size(w * 0.13f, h * 0.12f),
        cornerRadius = CornerRadius(2.dp.toPx())
    )

    // ---- windows (glow with house load) ----
    val loadGlow = (frame.houseLoadKw / maxLoad).coerceIn(0.1, 1.0).toFloat()
    val windowColor = Color(0xFFFFE1A6).copy(alpha = 0.25f + 0.55f * loadGlow)
    listOf(0.27f to 0.65f, 0.42f to 0.65f).forEach { (fx, fy) ->
        drawRoundRect(
            color = windowColor,
            topLeft = Offset(w * fx, h * fy),
            size = Size(w * 0.07f, h * 0.08f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }

    // ---- inverter + battery boxes on the front wall ----
    val inverterTopLeft = Offset(w * 0.36f, h * 0.74f)
    val inverterSize = Size(w * 0.055f, h * 0.08f)
    drawRoundRect(Color(0xFF0F141C), inverterTopLeft, inverterSize, CornerRadius(2.dp.toPx()))
    val statusColor = statusColor(frame.status)
    drawCircle(statusColor, radius = 2.5.dp.toPx(), center = Offset(inverterTopLeft.x + inverterSize.width / 2, inverterTopLeft.y + inverterSize.height * 0.25f))

    if (config.hasBattery) {
        val batteryTopLeft = Offset(w * 0.435f, h * 0.71f)
        val batterySize = Size(w * 0.05f, h * 0.11f)
        drawRoundRect(Color(0xFF0F141C), batteryTopLeft, batterySize, CornerRadius(2.dp.toPx()))
        val fillH = batterySize.height * (frame.batterySocPercent / 100f).coerceIn(0f, 1f)
        val batteryColor = if (frame.batteryPowerKw >= 0) LumixColors.EnergyGreen else LumixColors.TechnicalCyan
        drawRoundRect(
            color = batteryColor.copy(alpha = 0.85f),
            topLeft = Offset(batteryTopLeft.x + 2f, batteryTopLeft.y + batterySize.height - fillH + 2f),
            size = Size(batterySize.width - 4f, (fillH - 4f).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        drawRoundRect(batteryColor.copy(alpha = 0.5f), batteryTopLeft, batterySize, style = Stroke(width = 1.5f), cornerRadius = CornerRadius(2.dp.toPx()))
    }

    // ---- utility pole + wire to the house (only if grid-capable) ----
    val meterPoint = Offset(w * 0.20f, h * 0.78f)
    if (config.gridConnectable) {
        val poleX = w * 0.06f
        val poleTop = Offset(poleX, h * 0.45f)
        val poleBottom = Offset(poleX, h * 0.87f)
        val poleColor = palette.textSecondary.copy(alpha = 0.7f)
        drawLine(poleColor, poleTop, poleBottom, strokeWidth = 2.5.dp.toPx())
        drawLine(poleColor, poleTop + Offset(-w * 0.035f, h * 0.02f), poleTop + Offset(w * 0.035f, h * 0.02f), strokeWidth = 2.dp.toPx())

        val wireColor = when {
            frame.gridToHouseKw > 0.01 || frame.gridToBatteryKw > 0.01 -> LumixColors.SolarAmber
            else -> palette.textSecondary.copy(alpha = 0.35f)
        }
        drawLine(wireColor, poleTop + Offset(w * 0.035f, h * 0.02f), meterPoint, strokeWidth = 2.dp.toPx())
        drawRoundRect(Color(0xFF0F141C), meterPoint - Offset(w * 0.015f, 0f), Size(w * 0.03f, h * 0.04f), CornerRadius(1.dp.toPx()))
    }

    // ---- energy flow particles ----
    val panelAnchor = bilerp(roofApexLeft, roofApexRight, roofFrontLeft, roofFrontRight, 0.5f, 0.45f)
    val inverterAnchor = Offset(inverterTopLeft.x + inverterSize.width / 2, inverterTopLeft.y + inverterSize.height / 2)
    val batteryAnchor = Offset(w * 0.46f, h * 0.765f)
    val homeAnchor = Offset(w * 0.30f, h * 0.80f)

    if (!reduceMotion) {
        drawParticles(Offset(sunX, sunY), panelAnchor, (frame.pvKw / maxPv).toFloat(), LumixColors.SolarYellow, flowPhase)
        drawParticles(panelAnchor, inverterAnchor, (frame.pvKw / maxPv).toFloat(), LumixColors.SolarYellow, flowPhase)
        if (frame.solarToBatteryKw > 0.01 || frame.batteryToHouseKw > 0.01) {
            val toBattery = frame.solarToBatteryKw > frame.batteryToHouseKw
            val flowMag = (max(frame.solarToBatteryKw, frame.batteryToHouseKw) / maxBatteryFlow).toFloat()
            val color = if (toBattery) LumixColors.EnergyGreen else LumixColors.TechnicalCyan
            if (toBattery) drawParticles(inverterAnchor, batteryAnchor, flowMag, color, flowPhase)
            else drawParticles(batteryAnchor, inverterAnchor, flowMag, color, flowPhase)
        }
        val homeFlow = (frame.houseLoadKw / maxLoad).toFloat()
        drawParticles(inverterAnchor, homeAnchor, homeFlow, Color(0xFFF5F0E6), flowPhase)
        if (config.gridConnectable) {
            if (frame.gridToHouseKw > 0.01) {
                drawParticles(meterPoint, homeAnchor, (frame.gridToHouseKw / maxLoad).toFloat(), LumixColors.SolarAmber, flowPhase)
            }
            if (frame.gridToBatteryKw > 0.01) {
                drawParticles(meterPoint, batteryAnchor, (frame.gridToBatteryKw / maxBatteryFlow).toFloat(), LumixColors.SolarAmber, flowPhase)
            }
        }
    }
}

private fun bilerp(topLeft: Offset, topRight: Offset, bottomLeft: Offset, bottomRight: Offset, u: Float, v: Float): Offset {
    val top = Offset(topLeft.x + (topRight.x - topLeft.x) * u, topLeft.y + (topRight.y - topLeft.y) * u)
    val bottom = Offset(bottomLeft.x + (bottomRight.x - bottomLeft.x) * u, bottomLeft.y + (bottomRight.y - bottomLeft.y) * u)
    return Offset(top.x + (bottom.x - top.x) * v, top.y + (bottom.y - top.y) * v)
}

private fun DrawScope.drawParticles(from: Offset, to: Offset, intensity: Float, color: Color, phase: Float) {
    val clamped = intensity.coerceIn(0f, 1f)
    if (clamped < 0.02f) return
    val count = 1 + (clamped * 3).toInt()
    for (i in 0 until count) {
        val t = ((phase + i.toFloat() / count) % 1f)
        val pos = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
        val alpha = (kotlin.math.sin(Math.PI * t).toFloat().coerceAtLeast(0f)) * (0.5f + 0.5f * clamped)
        drawCircle(color.copy(alpha = alpha), radius = (2f + 2f * clamped).dp.toPx(), center = pos)
    }
}

internal fun statusColor(status: SystemStatus): Color = when (status) {
    SystemStatus.POWER_LIMITED -> LumixColors.WarningRed
    SystemStatus.GRID_POWERING_HOME, SystemStatus.BATTERY_PLUS_GRID, SystemStatus.GRID_CHARGING_BATTERY -> LumixColors.SolarAmber
    SystemStatus.BATTERY_POWERING_HOME -> LumixColors.TechnicalCyan
    SystemStatus.SOLAR_PLUS_BATTERY, SystemStatus.SOLAR_POWERING_HOME -> LumixColors.SolarYellow
    SystemStatus.IDLE -> Color.Gray
}
