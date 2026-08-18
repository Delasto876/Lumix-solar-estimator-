package com.lumix.estimator.site

import com.lumix.estimator.data.SiteDao
import com.lumix.estimator.data.SiteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * "Save location. Reload project. Confirm location and polygon remain" (2026-08-18): now backed
 * by Room — the whole [SolarSite] as one JSON blob per row, same pattern
 * `QuoteRepository`/[com.lumix.estimator.data.QuoteEntity] already uses. Was purely in-memory
 * before this round (this class's own prior doc said so, framed as a deliberate near-term gap);
 * that gap is what would have failed the project owner's own explicit save/reload test steps.
 */
class SiteRepository(private val dao: SiteDao) {

    private val json = Json { ignoreUnknownKeys = true }

    val sites: Flow<List<SolarSite>> = dao.observeAll().map { entities ->
        entities.mapNotNull { decodeOrNull(it.siteJson) }
    }

    private fun decodeOrNull(raw: String): SolarSite? = try {
        json.decodeFromString(SolarSite.serializer(), raw)
    } catch (e: Exception) {
        null
    }

    private fun entityFor(site: SolarSite) = SiteEntity(
        id = site.id,
        name = site.name,
        address = site.address,
        parish = site.parish,
        town = site.town,
        latitude = site.latitude,
        longitude = site.longitude,
        timestampMillis = site.timestampMillis,
        roofPlaneCount = site.roofPlanes.size,
        totalCapacityKw = site.totalCapacityKw,
        siteJson = json.encodeToString(site)
    )

    suspend fun save(site: SolarSite) {
        dao.upsert(entityFor(site))
    }

    suspend fun get(id: String): SolarSite? = dao.getById(id)?.let { decodeOrNull(it.siteJson) }

    suspend fun delete(id: String) = dao.deleteById(id)
}
