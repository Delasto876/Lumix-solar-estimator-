package com.lumix.estimator.site

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory store for [SolarSite]s traced or entered this session. Not yet backed by Room —
 * a follow-up can persist it the same way `QuoteRepository` stores quotes (one JSON blob per
 * row via kotlinx.serialization) without changing this class's public surface.
 */
class SiteRepository {
    private val _sites = MutableStateFlow<List<SolarSite>>(emptyList())
    val sites: StateFlow<List<SolarSite>> = _sites

    fun save(site: SolarSite) {
        _sites.update { current -> current.filterNot { it.id == site.id } + site }
    }

    fun get(id: String): SolarSite? = _sites.value.firstOrNull { it.id == id }

    fun delete(id: String) {
        _sites.update { current -> current.filterNot { it.id == id } }
    }
}
