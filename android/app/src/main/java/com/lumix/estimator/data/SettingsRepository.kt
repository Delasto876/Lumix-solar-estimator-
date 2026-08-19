package com.lumix.estimator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumix.estimator.domain.SavingsCalculator
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
    private val billEscalationRateKey = doublePreferencesKey("bill_escalation_rate")
    private val panelDegradationRateKey = doublePreferencesKey("panel_degradation_rate")

    // A79 (spec Phase 16 — "improve settings/materials", §40's own "Company information / Address
    // / Phone / Email / Default warranty / Payment terms"): none of this existed anywhere before
    // this round — every key defaults to blank, since the app has no real Lumix business content
    // to pre-fill (inventing plausible-sounding business/legal text would be fabricating content a
    // real customer could see on a quote — see A78's identical reasoning for why the PDF/HTML/CSV
    // exports left these sections out entirely rather than guessing). The installer fills these in
    // with their own real details; once non-blank, the quote exports pick them up automatically.
    private val companyNameKey = stringPreferencesKey("company_name")
    private val companyAddressKey = stringPreferencesKey("company_address")
    private val companyPhoneKey = stringPreferencesKey("company_phone")
    private val companyEmailKey = stringPreferencesKey("company_email")
    private val defaultWarrantyKey = stringPreferencesKey("default_warranty")
    private val paymentTermsKey = stringPreferencesKey("payment_terms")

    // 2026-08-19 ("do this google sign in/OAuth" — confirmed scope: identity-capture only, no
    // gating, no backend): the signed-in Google account's own display name/email/photo, persisted
    // so it survives an app restart without re-prompting the account picker every launch. This is
    // NOT an authenticated session — see GoogleSignInManager's own doc — just a cached identity
    // SettingsScreen shows and can prefill Business Information fields from.
    private val googleSignedInNameKey = stringPreferencesKey("google_signed_in_name")
    private val googleSignedInEmailKey = stringPreferencesKey("google_signed_in_email")
    private val googleSignedInPhotoUrlKey = stringPreferencesKey("google_signed_in_photo_url")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val defaultTechnicalMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[defaultTechnicalModeKey] ?: false
    }

    val defaultGridServiceAmps: Flow<Double> = context.settingsDataStore.data.map { prefs ->
        prefs[defaultGridServiceAmpsKey] ?: SimulationEngine.DEFAULT_GRID_SERVICE_AMPS
    }

    /** Assumed annual JPS-style bill increase used in the 20-year savings projection — an estimate, not a measured figure. */
    val billEscalationRate: Flow<Double> = context.settingsDataStore.data.map { prefs ->
        prefs[billEscalationRateKey] ?: SavingsCalculator.BILL_ESCALATION_RATE
    }

    /** Assumed annual panel output decline used in the 20-year savings projection — an estimate, not a measured figure. */
    val panelDegradationRate: Flow<Double> = context.settingsDataStore.data.map { prefs ->
        prefs[panelDegradationRateKey] ?: SavingsCalculator.PANEL_DEGRADATION_RATE
    }

    val companyName: Flow<String> = context.settingsDataStore.data.map { it[companyNameKey] ?: "" }
    val companyAddress: Flow<String> = context.settingsDataStore.data.map { it[companyAddressKey] ?: "" }
    val companyPhone: Flow<String> = context.settingsDataStore.data.map { it[companyPhoneKey] ?: "" }
    val companyEmail: Flow<String> = context.settingsDataStore.data.map { it[companyEmailKey] ?: "" }
    val defaultWarranty: Flow<String> = context.settingsDataStore.data.map { it[defaultWarrantyKey] ?: "" }
    val paymentTerms: Flow<String> = context.settingsDataStore.data.map { it[paymentTermsKey] ?: "" }

    val googleSignedInName: Flow<String> = context.settingsDataStore.data.map { it[googleSignedInNameKey] ?: "" }
    val googleSignedInEmail: Flow<String> = context.settingsDataStore.data.map { it[googleSignedInEmailKey] ?: "" }
    val googleSignedInPhotoUrl: Flow<String> = context.settingsDataStore.data.map { it[googleSignedInPhotoUrlKey] ?: "" }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDefaultTechnicalMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[defaultTechnicalModeKey] = enabled }
    }

    suspend fun setDefaultGridServiceAmps(amps: Double) {
        context.settingsDataStore.edit { it[defaultGridServiceAmpsKey] = amps }
    }

    suspend fun setBillEscalationRate(rate: Double) {
        context.settingsDataStore.edit { it[billEscalationRateKey] = rate }
    }

    suspend fun setPanelDegradationRate(rate: Double) {
        context.settingsDataStore.edit { it[panelDegradationRateKey] = rate }
    }

    suspend fun setCompanyName(value: String) { context.settingsDataStore.edit { it[companyNameKey] = value } }
    suspend fun setCompanyAddress(value: String) { context.settingsDataStore.edit { it[companyAddressKey] = value } }
    suspend fun setCompanyPhone(value: String) { context.settingsDataStore.edit { it[companyPhoneKey] = value } }
    suspend fun setCompanyEmail(value: String) { context.settingsDataStore.edit { it[companyEmailKey] = value } }
    suspend fun setDefaultWarranty(value: String) { context.settingsDataStore.edit { it[defaultWarrantyKey] = value } }
    suspend fun setPaymentTerms(value: String) { context.settingsDataStore.edit { it[paymentTermsKey] = value } }

    suspend fun setGoogleSignedInIdentity(name: String, email: String, photoUrl: String) {
        context.settingsDataStore.edit {
            it[googleSignedInNameKey] = name
            it[googleSignedInEmailKey] = email
            it[googleSignedInPhotoUrlKey] = photoUrl
        }
    }

    /** Signs out — clears the cached identity only. Does not revoke Google account access (this app was never granted more than basic profile read). */
    suspend fun clearGoogleSignedInIdentity() {
        context.settingsDataStore.edit {
            it.remove(googleSignedInNameKey)
            it.remove(googleSignedInEmailKey)
            it.remove(googleSignedInPhotoUrlKey)
        }
    }
}
