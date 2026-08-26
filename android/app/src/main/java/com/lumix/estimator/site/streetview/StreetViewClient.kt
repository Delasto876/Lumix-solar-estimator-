package com.lumix.estimator.site.streetview

import com.lumix.estimator.site.GeoPoint
import com.lumix.estimator.site.solarapi.GoogleSolarApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Site Survey / Solar Mapping round (spec "Integrate Street View (where available) for site
 * verification"): fetches a real ground-level photo of the site from Google's Street View Static
 * API — lets an installer sanity-check the map trace against what the property actually looks like
 * (access, obstructions, pole/meter location) without a site visit being the only way to see it.
 * See [StreetViewResult]'s own doc for why this is a three-way result, not a nullable. A plain
 * interface so [com.lumix.estimator.site.SolarSiteViewModel] (and any test) can substitute a fake
 * without a real network dependency, mirroring [com.lumix.estimator.site.solarapi.SolarApiClient]'s
 * own testability split.
 *
 * Two real requests, not one: the `metadata` endpoint is checked FIRST, per the spec's own "Never
 * fabricate ... data" extending to never rendering a fake/placeholder image as if it were real —
 * the Static API's own image endpoint returns a generic "Sorry, we have no imagery here" placeholder
 * with an HTTP 200 for a genuine coverage gap, not a clean error status, so skipping the metadata
 * check would silently present that placeholder as a real site photo.
 */
interface StreetViewClient {
    suspend fun fetchPanoramaImage(
        point: GeoPoint,
        headingDegrees: Double? = null,
        widthPx: Int = 640,
        heightPx: Int = 400
    ): StreetViewResult
}

/** The real implementation — plain [HttpURLConnection] GETs, same "no extra HTTP client library for one more endpoint" reasoning [com.lumix.estimator.site.solarapi.GoogleSolarApiClient] already documents. */
class GoogleStreetViewClient : StreetViewClient {

    override suspend fun fetchPanoramaImage(point: GeoPoint, headingDegrees: Double?, widthPx: Int, heightPx: Int): StreetViewResult {
        if (!GoogleSolarApiConfig.isConfigured) {
            return StreetViewResult.Unavailable("No Google API key configured — add SOLAR_API_KEY to local.properties to enable Street View.")
        }
        return withContext(Dispatchers.IO) {
            when (val status = fetchMetadataStatus(point)) {
                null -> StreetViewResult.Unavailable("Could not reach Street View — check your internet connection.")
                "OK" -> fetchImage(point, headingDegrees, widthPx, heightPx)
                "ZERO_RESULTS" -> StreetViewResult.NoCoverage("No Street View imagery is available at this location.")
                else -> StreetViewResult.Unavailable("Street View metadata returned status $status.")
            }
        }
    }

    private fun fetchMetadataStatus(point: GeoPoint): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (buildMetadataUrl(point).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            StreetViewMetadataParser.parseStatus(body)
        } catch (e: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchImage(point: GeoPoint, headingDegrees: Double?, widthPx: Int, heightPx: Int): StreetViewResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (buildImageUrl(point, headingDegrees, widthPx, heightPx).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return StreetViewResult.Unavailable("Street View image request failed (HTTP $code).")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            StreetViewResult.Available(bytes)
        } catch (e: IOException) {
            StreetViewResult.Unavailable("Could not reach Street View — check your internet connection. (${e.message ?: e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildMetadataUrl(point: GeoPoint): URL {
        val key = URLEncoder.encode(GoogleSolarApiConfig.apiKey, "UTF-8")
        return URI(
            "https://maps.googleapis.com/maps/api/streetview/metadata" +
                "?location=${point.latitude},${point.longitude}&key=$key"
        ).toURL()
    }

    private fun buildImageUrl(point: GeoPoint, headingDegrees: Double?, widthPx: Int, heightPx: Int): URL {
        val key = URLEncoder.encode(GoogleSolarApiConfig.apiKey, "UTF-8")
        val headingParam = headingDegrees?.let { "&heading=$it" } ?: ""
        return URI(
            "https://maps.googleapis.com/maps/api/streetview" +
                "?size=${widthPx}x${heightPx}&location=${point.latitude},${point.longitude}$headingParam&key=$key"
        ).toURL()
    }
}
