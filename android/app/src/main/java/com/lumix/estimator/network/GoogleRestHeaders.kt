package com.lumix.estimator.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Site Survey / Solar Mapping round, real-key follow-up: Google Cloud Console's own "Android apps"
 * application restriction (the type this app's own README already told the user to apply to their
 * Maps Platform key) is enforced only against the two headers below — a genuine Maps SDK view
 * (`GoogleMap`/`MapView`) sends them itself, but this app's own direct REST calls to the Solar API,
 * Elevation API and Street View Static API (see [com.lumix.estimator.site.solarapi.GoogleSolarApiClient],
 * [com.lumix.estimator.site.elevation.GoogleElevationApiClient],
 * [com.lumix.estimator.site.streetview.GoogleStreetViewClient] — all plain [java.net.HttpURLConnection]
 * GETs, not the SDK) do not, and Google's servers reject an Android-app-restricted key's REST calls
 * that arrive without them. Harmless to send unconditionally: an unrestricted (or non-Android-app-
 * restricted) key simply ignores both headers, so every call site can add them without first checking
 * which restriction type the configured key actually carries.
 *
 * Deliberately lives here (an `android.content.Context`-dependent utility) rather than inside any of
 * the three REST clients above, so those clients stay pure Kotlin with zero `android.*` imports —
 * see each client's own doc for why that matters (the standalone-kotlinc diagnostic route and the
 * real [com.lumix.estimator.domain.commercial.SiteSurveyEndToEndTest] suite both depend on it).
 */
object GoogleRestHeaders {

    /** Cached per process — the app's own package name and signing certificate never change while it's running. */
    @Volatile
    private var cached: Map<String, String>? = null

    /**
     * Returns `{"X-Android-Package": ..., "X-Android-Cert": ...}` for [context]'s own app, or an
     * empty map if the signing certificate can't be read (never a placeholder/fabricated value —
     * an empty map just means the two headers are omitted, exactly as if this function didn't exist).
     */
    fun forContext(context: Context): Map<String, String> {
        cached?.let { return it }
        val packageName = context.packageName
        val sha1 = signingCertificateSha1(context, packageName) ?: return emptyMap()
        val headers = mapOf("X-Android-Package" to packageName, "X-Android-Cert" to sha1)
        cached = headers
        return headers
    }

    private fun signingCertificateSha1(context: Context, packageName: String): String? {
        return try {
            val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return null
                val signatures = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
                signatures?.firstOrNull()?.toByteArray() ?: return null
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray() ?: return null
            }
            val digest = MessageDigest.getInstance("SHA-1").digest(signatureBytes)
            digest.joinToString(separator = "") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
