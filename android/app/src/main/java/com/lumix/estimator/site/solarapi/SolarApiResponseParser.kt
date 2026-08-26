package com.lumix.estimator.site.solarapi

import com.lumix.estimator.site.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Site Survey / Solar Mapping round: pure JSON -> [SolarApiBuildingInsights] mapping, kept
 * separate from [GoogleSolarApiClient]'s own HTTP call so this logic is testable/diagnosable with a
 * plain JSON string, no network involved (the same reason this codebase already keeps every other
 * kotlinx.serialization `Json { ignoreUnknownKeys = true }` instance narrowly scoped rather than
 * decoding straight into a public domain type — see e.g. `data/QuoteRepository.kt`'s own pattern).
 * `ignoreUnknownKeys = true` is load-bearing here specifically: Google's real
 * `buildingInsights:findClosest` response has many more fields (financial analyses, panel configs,
 * whole-roof stats, imagery-processed dates, etc.) than this app actually needs — only
 * [WireRoofSegmentStats]/[WireSolarPotential]'s own subset below is modeled, everything else must
 * decode without failing rather than throwing on a field this app doesn't care about.
 */
internal object SolarApiResponseParser {

    @Serializable
    private data class WireLatLng(val latitude: Double = 0.0, val longitude: Double = 0.0)

    @Serializable
    private data class WireBoundingBox(val sw: WireLatLng = WireLatLng(), val ne: WireLatLng = WireLatLng())

    @Serializable
    private data class WireSizeAndSunshineStats(
        val areaMeters2: Double = 0.0,
        val sunshineQuantiles: List<Double> = emptyList()
    )

    @Serializable
    private data class WireRoofSegmentStats(
        val pitchDegrees: Double? = null,
        val azimuthDegrees: Double? = null,
        val stats: WireSizeAndSunshineStats = WireSizeAndSunshineStats(),
        val center: WireLatLng = WireLatLng(),
        val boundingBox: WireBoundingBox = WireBoundingBox()
    )

    @Serializable
    private data class WireSolarPotential(
        val maxArrayAreaMeters2: Double? = null,
        val maxSunshineHoursPerYear: Double? = null,
        val roofSegmentStats: List<WireRoofSegmentStats> = emptyList()
    )

    @Serializable
    private data class WireDate(val year: Int = 0, val month: Int = 0, val day: Int = 0)

    @Serializable
    private data class WireBuildingInsightsResponse(
        val center: WireLatLng = WireLatLng(),
        val imageryDate: WireDate? = null,
        val imageryQuality: String? = null,
        val solarPotential: WireSolarPotential? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** @return null if [rawJson] doesn't parse as a Building Insights response at all (malformed/unexpected shape) — the caller maps that to [SolarApiResult.Unavailable], never a fabricated result. */
    fun parse(rawJson: String): SolarApiBuildingInsights? {
        val wire = runCatching { json.decodeFromString<WireBuildingInsightsResponse>(rawJson) }.getOrNull() ?: return null
        val potential = wire.solarPotential
        return SolarApiBuildingInsights(
            center = GeoPoint(wire.center.latitude, wire.center.longitude),
            imageryDateLabel = wire.imageryDate?.takeIf { it.year > 0 }?.let { "%04d-%02d".format(it.year, it.month) },
            imageryQuality = wire.imageryQuality,
            maxArrayAreaMeters2 = potential?.maxArrayAreaMeters2,
            maxSunshineHoursPerYear = potential?.maxSunshineHoursPerYear,
            roofSegments = potential?.roofSegmentStats.orEmpty().map { segment ->
                SolarApiRoofSegment(
                    pitchDegrees = segment.pitchDegrees,
                    azimuthDegrees = segment.azimuthDegrees,
                    areaMeters2 = segment.stats.areaMeters2,
                    sunshineQuantiles = segment.stats.sunshineQuantiles,
                    boundingBoxSw = GeoPoint(segment.boundingBox.sw.latitude, segment.boundingBox.sw.longitude),
                    boundingBoxNe = GeoPoint(segment.boundingBox.ne.latitude, segment.boundingBox.ne.longitude),
                    center = GeoPoint(segment.center.latitude, segment.center.longitude)
                )
            }
        )
    }
}
