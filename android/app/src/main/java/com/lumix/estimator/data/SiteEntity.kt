package com.lumix.estimator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Save location. Reload project. Confirm location and polygon remain" (2026-08-18): the whole
 * [com.lumix.estimator.site.SolarSite] (including every traced [com.lumix.estimator.site.RoofPlane])
 * as one JSON blob, same pattern as [QuoteEntity.inputsJson]/[QuoteEntity.resultJson] — a handful
 * of top-level columns for listing/sorting without decoding, the full object only decoded when a
 * specific site is actually opened.
 */
@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val address: String?,
    val parish: String?,
    val town: String?,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val roofPlaneCount: Int,
    val totalCapacityKw: Double,
    val siteJson: String
)
