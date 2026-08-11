package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import com.lumix.estimator.ui.theme.numberDisplayStyle
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(
    quoteId: Long,
    viewModel: SimulationViewModel,
    onBack: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(quoteId) {
        viewModel.load(quoteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.systemLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.currentFrame?.let {
                            Text(it.status.label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            if (!state.loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    TransportBar(
                        isPlaying = state.isPlaying,
                        speed = state.speed,
                        onTogglePlay = viewModel::togglePlay,
                        onSpeedChange = viewModel::setSpeed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading || state.config == null || state.currentFrame == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val config = state.config!!
        val frame = state.currentFrame!!

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusPill(frame)
            }

            item {
                SectionCard(title = "") {
                    HouseSimulationVisual(frame = frame, config = config)
                }
            }

            item {
                LivePowerRow(frame)
            }

            item {
                SectionCard(title = "") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        TimeDial(
                            hour = state.currentHour,
                            onScrub = viewModel::scrubTo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(frame: SimFrame) {
    val palette = LocalLumixPalette.current
    val color = statusColor(frame.status)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            frame.status.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
    }
}

@Composable
private fun LivePowerRow(frame: SimFrame) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LiveStat(label = "SOLAR", valueKw = frame.pvKw, color = palette.solarYellowText, modifier = Modifier.weight(1f))
            LiveStat(label = "GRID", valueKw = frame.gridPowerKw, color = if (frame.gridPowerKw >= 0) palette.solarAmberText else palette.energyGreenText, modifier = Modifier.weight(1f), showSign = true)
            LiveStat(label = "HOME", valueKw = frame.houseLoadKw, color = palette.textPrimary, modifier = Modifier.weight(1f))
            if (frame.batterySocPercent > 0f) {
                Column(modifier = Modifier.weight(1f)) {
                    LiveStat(label = "BATTERY", valueKw = frame.batteryPowerKw, color = if (frame.batteryPowerKw >= 0) palette.energyGreenText else palette.technicalCyanText, showSign = true)
                    Text(
                        "${frame.batterySocPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStat(label: String, valueKw: Double, color: Color, modifier: Modifier = Modifier, showSign: Boolean = false) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        AnimatedCounterText(
            targetValue = valueKw,
            format = { v -> (if (showSign && v > 0.001) "+" else "") + "%.2f kW".format(v) },
            style = numberDisplayStyle(size = 16.sp, weight = FontWeight.Bold),
            color = color
        )
    }
}
