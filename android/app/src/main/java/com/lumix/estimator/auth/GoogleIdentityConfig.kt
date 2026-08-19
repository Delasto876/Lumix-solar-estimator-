package com.lumix.estimator.auth

/**
 * 2026-08-19 ("do this google sign in/OAuth" — scope confirmed as identity-capture only: a
 * "Sign in with Google" button in Settings that fills in the installer's name/email, nothing
 * gated, no backend): same blank-by-default, "read BuildConfig in exactly one place" pattern as
 * [com.lumix.estimator.domain.ai.AiConfig]/[com.lumix.estimator.map.GoogleMapsConfig].
 * [com.lumix.estimator.LumixApp.onCreate] calls [configure] once at startup with
 * `BuildConfig.GOOGLE_WEB_CLIENT_ID`, itself sourced from `android/local.properties` (see
 * `app/build.gradle.kts`) — never hardcoded, never committed.
 *
 * **This is a WEB application OAuth client ID, not the Android one.** Android's Credential
 * Manager "Sign in with Google" flow (see [GoogleSignInManager]) takes a `serverClientId` that
 * must be a "Web application" type OAuth client from Google Cloud Console — a *separate* client
 * from the "Android" type client (package name + SHA-1 fingerprint) that also has to exist in the
 * same Cloud project for Google to recognize this app as allowed to call in at all. Both are
 * required; only the Web client's ID goes in code/`local.properties` here.
 */
object GoogleIdentityConfig {
    @Volatile private var webClientId: String = ""

    fun configure(webClientId: String) {
        this.webClientId = webClientId
    }

    val isConfigured: Boolean get() = webClientId.isNotBlank()
    val serverClientIdOrNull: String? get() = webClientId.takeIf { it.isNotBlank() }
}
