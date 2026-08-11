package com.lumix.estimator.domain.simulation

import kotlinx.serialization.Serializable

@Serializable
enum class WeatherState(val label: String, val multiplier: Double, val glyph: String) {
    CLEAR("Clear", 1.00, "☀️"),
    PARTLY_CLOUDY("Partly Cloudy", 0.70, "⛅"),
    CLOUDY("Cloudy", 0.40, "☁️"),
    HEAVY_CLOUD("Heavy Cloud", 0.15, "🌥️"),
    STORM("Storm", 0.05, "⛈️")
}
