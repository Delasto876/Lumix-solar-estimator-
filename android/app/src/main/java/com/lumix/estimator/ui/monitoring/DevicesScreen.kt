package com.lumix.estimator.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumix.estimator.domain.monitoring.DeviceTelemetry
import com.lumix.estimator.network.NetworkConnectivityObserver
import com.lumix.estimator.ui.components.CollapsibleSectionCard
import com.lumix.estimator.ui.components.LargeTitleTopBar
import com.lumix.estimator.ui.components.SurfaceCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A149 (Deye integration round): the dedicated "devices I manage" screen the user asked for —
 * "watch live data (current output, performance, status) for each inverter/plant." Reached from
 * Settings' "Device Monitoring" section (once Deye is connected), not a permanent bottom-nav tab —
 * see the README's own A149 section for why (this app's 5-tab [com.lumix.estimator.ui.components
 * .FloatingBottomNav] divides its width evenly with no scroll, and a 6th icon already caused a
 * cramped-label regression once before, per A16).
 */
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val palette = LocalLumixPalette.current
    val context = LocalContext.current
    val connectivityObserver = remember(context) { NetworkConnectivityObserver(context) }
    val viewModel: DevicesViewModel = viewModel(factory = DevicesViewModel.factory(connectivityObserver::isOnline))
    val state by viewModel.state.collectAsState()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleTopBar(
            title = "Devices",
            subtitle = "Deye inverters/plants you manage",
            onBack = onBack,
            actions = { IconButton(onClick = { viewModel.refreshNow() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh now") } }
        )

        when {
            state.notConfigured -> EmptyState(
                "No Deye account connected",
                "Connect your DeyeCloud account from Settings → Device Monitoring to see live devices here. Until then, everything else in the app keeps working with simulated data."
            )
            state.directoryError != null -> EmptyState("Couldn't load devices", state.directoryError.orEmpty())
            state.loadingDirectory -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { CircularProgressIndicator(color = palette.solarYellow) }
            state.devices.isEmpty() -> EmptyState("No devices found", "Your DeyeCloud account is connected, but returned no devices.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navBarBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.devices, key = { it.summary.id }) { device -> DeviceCard(device) }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    val palette = LocalLumixPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DeviceCard(device: DeviceLiveState) {
    val palette = LocalLumixPalette.current
    CollapsibleSectionCard(
        title = device.summary.label,
        subtitle = device.summary.plantName,
        initiallyExpanded = true
    ) {
        when (val fetch = device.fetchState) {
            is DeviceFetchState.Loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            is DeviceFetchState.Failed -> Text(fetch.reason, style = MaterialTheme.typography.bodySmall, color = palette.warningRedText)
            is DeviceFetchState.Connected -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (fetch.isLive) "● Live" else "● Simulated",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fetch.isLive) palette.solarYellowText else palette.textSecondary
                    )
                    Text(
                        deviceStatusLabel(fetch.telemetry),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(Icons.Default.WbSunny, "PV", "${oneDecimal(fetch.telemetry.pvPower)} kW", Modifier.weight(1f))
                    MetricTile(Icons.Default.BatteryChargingFull, "Battery", "${fetch.telemetry.batterySoc.roundToInt()}%", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(Icons.Default.Home, "Load", "${oneDecimal(fetch.telemetry.loadPower)} kW", Modifier.weight(1f))
                    MetricTile(Icons.Default.Bolt, "Grid", "${oneDecimal(fetch.telemetry.gridPower)} kW", Modifier.weight(1f))
                }
            }
        }
    }
}

private fun deviceStatusLabel(telemetry: DeviceTelemetry): String = when {
    telemetry.pvPower > 0.05 -> "Producing"
    telemetry.gridPower > 0.05 -> "Importing from grid"
    else -> "Idle"
}

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun MetricTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    SurfaceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = palette.solarYellow, modifier = Modifier.padding(end = 6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
