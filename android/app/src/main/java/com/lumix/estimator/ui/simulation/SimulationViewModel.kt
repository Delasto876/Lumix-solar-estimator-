package com.lumix.estimator.ui.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.simulation.ApplianceState
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.domain.simulation.InverterMode
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.domain.simulation.WeatherState
import com.lumix.estimator.domain.simulation.defaultApplianceStates
import com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SimulationUiState(
    val loading: Boolean = true,
    val systemLabel: String = "Your Solar System",
    val inputs: QuoteInputs? = null,
    val config: SimSystemConfig? = null,
    val timeline: List<SimFrame> = emptyList(),
    val currentHour: Double = 12.0,
    val currentFrame: SimFrame? = null,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val appliances: Map<SimApplianceType, ApplianceState> = emptyMap(),
    val gridConnected: Boolean = true,
    val inverterMode: InverterMode = InverterMode.SBU,
    val gridChargeEnabled: Boolean = true,
    val gridServiceAmps: Double = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS,
    val weather: WeatherState = WeatherState.CLEAR,
    val startSocFraction: Double = 0.6,
    val cloudEventActive: Boolean = false,
    val batteryFullHour: Double? = null,
    val technicalMode: Boolean = false,
    val dayType: DayType = DayType.WEEKDAY
) {
    /** Total appliance load right now, at [currentHour] on [dayType] — reflects each appliance's own schedule. */
    val applianceLoadKw: Double get() = totalApplianceLoadKwAt(appliances, currentHour, dayType)
}

class SimulationViewModel(
    private val quoteRepository: QuoteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SimulationUiState())
    val state: StateFlow<SimulationUiState> = _state.asStateFlow()

    private var playJob: Job? = null
    private var cloudEventJob: Job? = null

    fun load(quoteId: Long) {
        viewModelScope.launch {
            val saved = quoteRepository.getSavedQuote(quoteId) ?: return@launch
            val config = SimSystemConfig.from(saved.result)
            val appliances = defaultApplianceStates(saved.inputs)
            val gridConnected = config.gridConnectable
            val inverterMode = _state.value.inverterMode
            val gridChargeEnabled = _state.value.gridChargeEnabled
            val technicalMode = settingsRepository.defaultTechnicalMode.first()
            val gridServiceAmps = settingsRepository.defaultGridServiceAmps.first()
            val timeline = SimulationEngine.buildDayTimeline(
                config,
                gridConnected = gridConnected,
                applianceStates = appliances,
                inverterMode = inverterMode,
                gridChargeEnabled = gridChargeEnabled,
                gridServiceAmps = gridServiceAmps,
                dayType = _state.value.dayType
            )
            val label = saved.inputs.customerName.takeIf { it.isNotBlank() } ?: "Your Solar System"
            _state.update {
                it.copy(
                    loading = false,
                    systemLabel = label,
                    inputs = saved.inputs,
                    config = config,
                    appliances = appliances,
                    gridConnected = gridConnected,
                    gridServiceAmps = gridServiceAmps,
                    technicalMode = technicalMode,
                    timeline = timeline,
                    currentFrame = SimulationEngine.frameAt(timeline, it.currentHour),
                    batteryFullHour = SimulationEngine.nextBatteryFullHour(timeline, it.currentHour)
                )
            }
        }
    }

    /**
     * Replaces one appliance's full state (enabled flag + its real list of [ApplianceState.runs])
     * directly — the schedule editor edits actual start-time/duration/day-type runs, not a
     * lossy Morning/Noon/Night abstraction that used to get rebuilt from scratch on every edit.
     */
    fun setApplianceState(type: SimApplianceType, newState: ApplianceState) {
        val updated = _state.value.appliances.toMutableMap().apply { this[type] = newState }
        rebuildTimeline(appliances = updated)
    }

    fun setGridConnected(connected: Boolean) {
        rebuildTimeline(gridConnected = connected)
    }

    fun setInverterMode(mode: InverterMode) {
        rebuildTimeline(inverterMode = mode)
    }

    fun setGridChargeEnabled(enabled: Boolean) {
        rebuildTimeline(gridChargeEnabled = enabled)
    }

    fun setGridServiceAmps(amps: Double) {
        rebuildTimeline(gridServiceAmps = amps)
    }

    fun setWeather(weather: WeatherState) {
        rebuildTimeline(weather = weather)
    }

    fun setStartSocFraction(fraction: Double) {
        rebuildTimeline(startSocFraction = fraction.coerceIn(0.1, 1.0))
    }

    fun setTechnicalMode(enabled: Boolean) {
        _state.update { it.copy(technicalMode = enabled) }
    }

    fun setDayType(dayType: DayType) {
        rebuildTimeline(dayType = dayType)
    }

    /** A brief, self-reverting dip in weather — clouds roll in, production drops, then clears. */
    fun triggerCloudEvent() {
        if (cloudEventJob?.isActive == true) return
        val previousWeather = _state.value.weather
        cloudEventJob = viewModelScope.launch {
            _state.update { it.copy(cloudEventActive = true) }
            setWeather(WeatherState.STORM)
            delay(3500)
            setWeather(previousWeather)
            _state.update { it.copy(cloudEventActive = false) }
        }
    }

    private fun rebuildTimeline(
        appliances: Map<SimApplianceType, ApplianceState> = _state.value.appliances,
        gridConnected: Boolean = _state.value.gridConnected,
        inverterMode: InverterMode = _state.value.inverterMode,
        gridChargeEnabled: Boolean = _state.value.gridChargeEnabled,
        gridServiceAmps: Double = _state.value.gridServiceAmps,
        weather: WeatherState = _state.value.weather,
        startSocFraction: Double = _state.value.startSocFraction,
        dayType: DayType = _state.value.dayType
    ) {
        val config = _state.value.config ?: return
        val effectiveGridConnected = gridConnected && config.gridConnectable
        val timeline = SimulationEngine.buildDayTimeline(
            config,
            cloudMultiplier = weather.multiplier,
            gridConnected = effectiveGridConnected,
            startSocFraction = startSocFraction,
            applianceStates = appliances,
            inverterMode = inverterMode,
            gridChargeEnabled = gridChargeEnabled,
            gridServiceAmps = gridServiceAmps,
            dayType = dayType
        )
        _state.update {
            it.copy(
                appliances = appliances,
                gridConnected = effectiveGridConnected,
                inverterMode = inverterMode,
                gridChargeEnabled = gridChargeEnabled,
                gridServiceAmps = gridServiceAmps,
                weather = weather,
                startSocFraction = startSocFraction,
                dayType = dayType,
                timeline = timeline,
                currentFrame = SimulationEngine.frameAt(timeline, it.currentHour),
                batteryFullHour = SimulationEngine.nextBatteryFullHour(timeline, it.currentHour)
            )
        }
    }

    fun scrubTo(hour: Double) {
        pause()
        setHourInternal(hour.mod(24.0))
    }

    fun play() {
        if (playJob?.isActive == true) return
        _state.update { it.copy(isPlaying = true) }
        playJob = viewModelScope.launch {
            var lastNanos = System.nanoTime()
            while (isActive) {
                delay(16)
                val now = System.nanoTime()
                val elapsedSeconds = (now - lastNanos) / 1_000_000_000.0
                lastNanos = now
                // 1x = 1 simulated minute per real second.
                val simHours = (_state.value.speed * elapsedSeconds) / 60.0
                val newHour = (_state.value.currentHour + simHours).mod(24.0)
                setHourInternal(newHour)
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        _state.update { it.copy(isPlaying = false) }
    }

    fun togglePlay() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
    }

    private fun setHourInternal(hour: Double) {
        _state.update { s ->
            val frame = if (s.timeline.isNotEmpty()) SimulationEngine.frameAt(s.timeline, hour) else s.currentFrame
            val fullHour = if (s.timeline.isNotEmpty()) SimulationEngine.nextBatteryFullHour(s.timeline, hour) else null
            s.copy(currentHour = hour, currentFrame = frame, batteryFullHour = fullHour)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playJob?.cancel()
        cloudEventJob?.cancel()
    }

    companion object {
        fun factory(quoteRepository: QuoteRepository, settingsRepository: SettingsRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SimulationViewModel(quoteRepository, settingsRepository) as T
            }
        }
    }
}
