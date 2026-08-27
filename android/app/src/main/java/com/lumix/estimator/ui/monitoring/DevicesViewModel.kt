package com.lumix.estimator.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.domain.monitoring.DeviceListResult
import com.lumix.estimator.domain.monitoring.DeviceSummary
import com.lumix.estimator.domain.monitoring.DeviceTelemetry
import com.lumix.estimator.domain.monitoring.MonitoringConfig
import com.lumix.estimator.domain.monitoring.MonitoringManufacturer
import com.lumix.estimator.domain.monitoring.MonitoringProvider
import com.lumix.estimator.domain.monitoring.MonitoringProviderRegistry
import com.lumix.estimator.domain.monitoring.MonitoringResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One device's current polled state, in one place so the screen never has to merge a device list with a separate telemetry map. */
data class DeviceLiveState(
    val summary: DeviceSummary,
    val fetchState: DeviceFetchState
)

sealed class DeviceFetchState {
    data object Loading : DeviceFetchState()
    /** [isLive] distinguishes real DeyeCloud data from the mock fallback — see [DevicesViewModel]'s own doc for how it's derived, since [MonitoringResult] itself doesn't carry this. */
    data class Connected(val telemetry: DeviceTelemetry, val isLive: Boolean) : DeviceFetchState()
    data class Failed(val reason: String) : DeviceFetchState()
}

data class DevicesUiState(
    val loadingDirectory: Boolean = true,
    val devices: List<DeviceLiveState> = emptyList(),
    val directoryError: String? = null,
    val notConfigured: Boolean = false
)

/**
 * A149 (Deye integration round, satisfying "add a dedicated system tab/screen showing connected
 * Deye devices... watch live data"): owns the device directory + per-device telemetry polling for
 * [DevicesScreen] — a real self-driving poll loop (started once in [init], not re-triggered on
 * every recomposition), so leaving and returning to this screen doesn't restart the interval.
 *
 * [isOnline] is supplied by the caller (backed by
 * [com.lumix.estimator.network.NetworkConnectivityObserver] in practice) rather than this
 * ViewModel reaching for a `Context` itself — keeps this class trivially constructible/testable,
 * matching every other ViewModel in this app.
 */
class DevicesViewModel(private val isOnline: () -> Boolean) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state

    private val provider: MonitoringProvider by lazy {
        MonitoringProviderRegistry.providerFor(MonitoringManufacturer.DEYE, isOnline)
    }

    init {
        viewModelScope.launch {
            while (true) {
                pollOnce()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    /** Manual "refresh now" — the automatic loop already covers steady-state polling, this is for a pull-to-refresh/explicit retry affordance. */
    fun refreshNow() {
        viewModelScope.launch { pollOnce() }
    }

    private suspend fun pollOnce() {
        when (val directory = provider.listDevices()) {
            is DeviceListResult.NotConfigured -> {
                _state.update { it.copy(loadingDirectory = false, devices = emptyList(), directoryError = null, notConfigured = true) }
                return
            }
            is DeviceListResult.Error -> {
                _state.update { it.copy(loadingDirectory = false, directoryError = directory.message, notConfigured = false) }
                return
            }
            is DeviceListResult.Available -> {
                // "Live" iff the same conditions FallbackMonitoringProvider itself uses to prefer
                // the real provider over mock — see this class's own doc for why a Connected
                // result under these conditions really did come from DeyeCloud, not a guess.
                val isLive = MonitoringConfig.credentialsFor(MonitoringManufacturer.DEYE).isConfigured && isOnline()
                val updated = directory.devices.map { summary ->
                    when (val result = provider.fetchLatest(summary.id)) {
                        is MonitoringResult.Connected -> DeviceLiveState(summary, DeviceFetchState.Connected(result.telemetry, isLive))
                        is MonitoringResult.Error -> DeviceLiveState(summary, DeviceFetchState.Failed(result.message))
                        is MonitoringResult.NotConfigured -> DeviceLiveState(summary, DeviceFetchState.Failed("Not configured."))
                    }
                }
                _state.update { it.copy(loadingDirectory = false, devices = updated, directoryError = null, notConfigured = false) }
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 45_000L

        fun factory(isOnline: () -> Boolean) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DevicesViewModel(isOnline) as T
        }
    }
}
