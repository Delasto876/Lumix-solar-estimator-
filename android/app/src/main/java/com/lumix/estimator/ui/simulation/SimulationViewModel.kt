package com.lumix.estimator.ui.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SimulationUiState(
    val loading: Boolean = true,
    val systemLabel: String = "Your Solar System",
    val config: SimSystemConfig? = null,
    val timeline: List<SimFrame> = emptyList(),
    val currentHour: Double = 12.0,
    val currentFrame: SimFrame? = null,
    val isPlaying: Boolean = false,
    val speed: Float = 1f
)

class SimulationViewModel(
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SimulationUiState())
    val state: StateFlow<SimulationUiState> = _state.asStateFlow()

    private var playJob: Job? = null

    fun load(quoteId: Long) {
        viewModelScope.launch {
            val saved = quoteRepository.getSavedQuote(quoteId) ?: return@launch
            val config = SimSystemConfig.from(saved.result)
            val timeline = SimulationEngine.buildDayTimeline(config)
            val label = saved.inputs.customerName.takeIf { it.isNotBlank() } ?: "Your Solar System"
            _state.update {
                it.copy(
                    loading = false,
                    systemLabel = label,
                    config = config,
                    timeline = timeline,
                    currentFrame = SimulationEngine.frameAt(timeline, it.currentHour)
                )
            }
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
            s.copy(currentHour = hour, currentFrame = frame)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playJob?.cancel()
    }

    companion object {
        fun factory(quoteRepository: QuoteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SimulationViewModel(quoteRepository) as T
            }
        }
    }
}
