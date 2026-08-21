package com.lumix.estimator.ui.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.commercial.LoadInstance
import com.lumix.estimator.domain.commercial.commercialLoadKwAt
import com.lumix.estimator.domain.simulation.ApplianceState
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.domain.simulation.InverterMode
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.domain.simulation.WeatherCurve
import com.lumix.estimator.domain.simulation.WeatherEngine
import com.lumix.estimator.domain.simulation.WeatherScenario
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
    // Default view: pre-dawn, before solar production starts (SUNRISE_HOUR = 5.75) — the
    // installer opens the simulation and immediately sees the day begin, rather than starting
    // mid-afternoon with half the story already behind them.
    val currentHour: Double = 4.5,
    val currentFrame: SimFrame? = null,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val appliances: Map<SimApplianceType, ApplianceState> = emptyMap(),
    /** Phase 32: the Commercial/Industrial equivalent of [appliances] — empty for a RESIDENTIAL quote (seeded from [QuoteInputs.commercialIndustrialDesign] otherwise). */
    val commercialLoads: List<LoadInstance> = emptyList(),
    val gridConnected: Boolean = true,
    val inverterMode: InverterMode = InverterMode.SBU,
    val gridChargeEnabled: Boolean = true,
    val gridServiceAmps: Double = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS,
    /**
     * A80 (spec Phase 17 §"WEATHER SCENARIO SELECTION"): replaces the removed `WeatherState`
     * flat-percentage buttons — a climatological framing fed into [WeatherEngine.generate]
     * alongside [solarConditionsDeviation] and the quote's own install month to produce
     * [weatherCurve], the real per-timestep curve [rebuildTimeline] actually simulates against.
     */
    val weatherScenario: WeatherScenario = WeatherScenario.TYPICAL,
    /** A80: the "SOLAR CONDITIONS" slider — see [WeatherEngine.generate]'s own `solarConditionsDeviation` doc. */
    val solarConditionsDeviation: Double = 0.0,
    /** A80: the generated curve currently driving [timeline] — exposed so the UI's cloud/sun overlays can read the real per-instant availability instead of a flat multiplier. */
    val weatherCurve: WeatherCurve = WeatherCurve.CLEAR,
    val startSocFraction: Double = 0.6,
    val cloudEventActive: Boolean = false,
    val batteryFullHour: Double? = null,
    val technicalMode: Boolean = false,
    val dayType: DayType = DayType.WEEKDAY
) {
    /** Total appliance load right now, at [currentHour] on [dayType] — reflects each appliance's own schedule. */
    val applianceLoadKw: Double get() = totalApplianceLoadKwAt(appliances, currentHour, dayType)

    /** Phase 32: the Commercial/Industrial equivalent of [applianceLoadKw]. */
    val commercialLoadKw: Double get() = commercialLoadKwAt(commercialLoads, currentHour)
}

class SimulationViewModel(
    private val quoteRepository: QuoteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SimulationUiState())
    val state: StateFlow<SimulationUiState> = _state.asStateFlow()

    private var playJob: Job? = null
    private var cloudEventJob: Job? = null

    /**
     * A45: quote isolation, structurally rather than by nav-graph convention alone. This VM
     * currently only ever gets a fresh instance per navigation to Simulation (verified — no
     * `launchSingleTop`/backstack reuse across quote IDs exists in `LumixNavHost`), so this was
     * never actually reachable as a live bug. But `load()` used to read `inverterMode`/
     * `gridChargeEnabled`/`dayType` off whatever `_state.value` already held rather than a clean
     * default, and never reset `weather`/`startSocFraction`/`currentHour`/`isPlaying` at all —
     * silently correct only because nothing currently reuses the instance. Resetting to a clean
     * [SimulationUiState] before loading removes that reliance entirely, so a future nav-graph
     * change (e.g. `launchSingleTop`) can't leak one quote's simulation settings into another's.
     */
    fun load(quoteId: Long) {
        playJob?.cancel()
        playJob = null
        cloudEventJob?.cancel()
        cloudEventJob = null
        _state.value = SimulationUiState(loading = true)
        viewModelScope.launch {
            val saved = quoteRepository.getSavedQuote(quoteId) ?: return@launch
            val config = SimSystemConfig.from(saved.result, saved.inputs)
            val appliances = defaultApplianceStates(saved.inputs)
            // Phase 32: the Commercial/Industrial equivalent of [appliances] — empty for a
            // RESIDENTIAL quote, since commercialIndustrialDesign is only ever set otherwise.
            val commercialLoads = saved.inputs.commercialIndustrialDesign?.loads ?: emptyList()
            val gridConnected = config.gridConnectable
            val technicalMode = settingsRepository.defaultTechnicalMode.first()
            val gridServiceAmps = settingsRepository.defaultGridServiceAmps.first()
            // No inverterMode/gridChargeEnabled/dayType/startSocFraction passed here — the clean
            // SimulationUiState() set above and buildDayTimeline's own defaults already agree
            // (SBU / true / WEEKDAY / 0.6), so this quote starts from the same known-clean state
            // every time, never a leftover from whatever quote was viewed previously. Same for
            // weatherScenario/solarConditionsDeviation — TYPICAL / 0.0, the clean default.
            val weatherCurve = WeatherEngine.generate(WeatherScenario.TYPICAL, config.installMonth)
            val timeline = SimulationEngine.buildDayTimeline(
                config,
                gridConnected = gridConnected,
                applianceStates = appliances,
                commercialLoads = commercialLoads,
                gridServiceAmps = gridServiceAmps,
                installMonth = config.installMonth,
                weatherCurve = weatherCurve
            )
            val label = saved.inputs.customerName.takeIf { it.isNotBlank() } ?: "Your Solar System"
            _state.update {
                it.copy(
                    loading = false,
                    systemLabel = label,
                    inputs = saved.inputs,
                    config = config,
                    appliances = appliances,
                    commercialLoads = commercialLoads,
                    gridConnected = gridConnected,
                    gridServiceAmps = gridServiceAmps,
                    weatherCurve = weatherCurve,
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

    /** Phase 32: replaces the full Commercial/Industrial loads list — the "when they run" edits (quantity/watts/hours/typicalStartHour) apply here exactly like [setApplianceState] does for residential. Session-only, same as every other simulation edit — never written back to the saved quote's own [QuoteInputs.commercialIndustrialDesign]. */
    fun setCommercialLoads(loads: List<LoadInstance>) {
        rebuildTimeline(commercialLoads = loads)
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

    /** A80: picks which climatological scenario [WeatherEngine.generate] builds the day's curve from — see [WeatherScenario]'s own doc. */
    fun setWeatherScenario(scenario: WeatherScenario) {
        rebuildTimeline(weatherScenario = scenario)
    }

    /** A80: the "SOLAR CONDITIONS" slider, [-0.2, 0.2] — see [WeatherEngine.generate]'s own `solarConditionsDeviation` doc. */
    fun setSolarConditionsDeviation(deviation: Double) {
        rebuildTimeline(solarConditionsDeviation = deviation.coerceIn(-0.2, 0.2))
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

    /**
     * A80: a brief, self-reverting demonstration dip — temporarily switches to the RAINY
     * scenario (a real, cloudier generated curve, not an instant flat drop) then reverts to
     * whatever scenario/deviation was active before. Replaces the old `WeatherState.STORM` flip.
     */
    fun triggerCloudEvent() {
        if (cloudEventJob?.isActive == true) return
        val previousScenario = _state.value.weatherScenario
        val previousDeviation = _state.value.solarConditionsDeviation
        cloudEventJob = viewModelScope.launch {
            _state.update { it.copy(cloudEventActive = true) }
            setWeatherScenario(WeatherScenario.RAINY)
            delay(3500)
            rebuildTimeline(weatherScenario = previousScenario, solarConditionsDeviation = previousDeviation)
            _state.update { it.copy(cloudEventActive = false) }
        }
    }

    private fun rebuildTimeline(
        appliances: Map<SimApplianceType, ApplianceState> = _state.value.appliances,
        commercialLoads: List<LoadInstance> = _state.value.commercialLoads,
        gridConnected: Boolean = _state.value.gridConnected,
        inverterMode: InverterMode = _state.value.inverterMode,
        gridChargeEnabled: Boolean = _state.value.gridChargeEnabled,
        gridServiceAmps: Double = _state.value.gridServiceAmps,
        weatherScenario: WeatherScenario = _state.value.weatherScenario,
        solarConditionsDeviation: Double = _state.value.solarConditionsDeviation,
        startSocFraction: Double = _state.value.startSocFraction,
        dayType: DayType = _state.value.dayType
    ) {
        val config = _state.value.config ?: return
        val effectiveGridConnected = gridConnected && config.gridConnectable
        val weatherCurve = WeatherEngine.generate(weatherScenario, config.installMonth, solarConditionsDeviation)
        val timeline = SimulationEngine.buildDayTimeline(
            config,
            gridConnected = effectiveGridConnected,
            startSocFraction = startSocFraction,
            applianceStates = appliances,
            commercialLoads = commercialLoads,
            inverterMode = inverterMode,
            gridChargeEnabled = gridChargeEnabled,
            gridServiceAmps = gridServiceAmps,
            dayType = dayType,
            installMonth = config.installMonth,
            weatherCurve = weatherCurve
        )
        _state.update {
            it.copy(
                appliances = appliances,
                commercialLoads = commercialLoads,
                gridConnected = effectiveGridConnected,
                inverterMode = inverterMode,
                gridChargeEnabled = gridChargeEnabled,
                gridServiceAmps = gridServiceAmps,
                weatherScenario = weatherScenario,
                solarConditionsDeviation = solarConditionsDeviation,
                weatherCurve = weatherCurve,
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
                advanceHour(simHours)
            }
        }
    }

    /**
     * A45 midnight-SOC-jump fix: [SimulationEngine.buildDayTimeline] builds one 24h array
     * starting from [SimulationUiState.startSocFraction], and [SimulationEngine.frameAt] wraps
     * any hour >= 24 back to the START of that same array via `hour.mod(24.0)`. Advancing time
     * straight through midnight with a plain `.mod(24.0)` (the old behavior) therefore jumped
     * from wherever the day's battery integration actually ended (e.g. 20%) back to the array's
     * fresh starting point (e.g. 60%) — a real discontinuity, not a display artifact. Midnight
     * itself is not a battery event: crossing it here rebuilds tomorrow's timeline anchored at
     * *today's actual ending state of charge*, so the physical battery energy is continuous
     * across the boundary; only the day/array being looked up changes. Loops so a large jump
     * (e.g. a lag spike at high playback speed) that crosses more than one midnight still chains
     * each day's ending SOC into the next correctly, rather than only handling one crossing.
     */
    private fun advanceHour(deltaHours: Double) {
        var rawHour = _state.value.currentHour + deltaHours
        while (rawHour >= 24.0) {
            rawHour -= 24.0
            val current = _state.value
            val config = current.config
            val endingSoc = current.timeline.lastOrNull()
            val nextStartSoc = if (endingSoc != null && config != null && config.batteryCapacityKwh > 0) {
                (endingSoc.batterySocKwh / config.batteryCapacityKwh).coerceIn(0.0, 1.0)
            } else {
                current.startSocFraction
            }
            rebuildTimeline(startSocFraction = nextStartSoc)
        }
        setHourInternal(rawHour)
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
