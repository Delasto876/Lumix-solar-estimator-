package com.lumix.estimator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumix.estimator.domain.simulation.SimulationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumix_settings")

/** SYSTEM follows the device's own light/dark setting; LIGHT/DARK are an explicit in-app override. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide preferences that aren't tied to any one quote — theme, simulation defaults. */
class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val defaultTechnicalModeKey = booleanPreferencesKey("default_technical_mode")
    private val defaultGridServiceAmpsKey = doublePreferencesKey("default_grid_service_amps")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val defaultTechnicalMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[defaultTechnicalModeKey] ?: false
    }

    val defaultGridServiceAmps: Flow<Double> = context.settingsDataStore.data.map { prefs ->
        prefs[defaultGridServiceAmpsKey] ?: SimulationEngine.DEFAULT_GRID_SERVICE_AMPS
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDefaultTechnicalMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[defaultTechnicalModeKey] = enabled }
    }

    suspend fun setDefaultGridServiceAmps(amps: Double) {
        context.settingsDataStore.edit { it[defaultGridServiceAmpsKey] = amps }
    }
}
