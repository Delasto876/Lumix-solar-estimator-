package com.lumix.estimator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumix.estimator.domain.CodeRequirementReference
import com.lumix.estimator.domain.CodeStandard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.codeStandardsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumix_code_standards")

/**
 * A82 (spec Phase 19 — "electrical-code lookup architecture", "allow the administrator to
 * upload/update applicable standards later"): every [CodeStandard]/[CodeRequirementReference] on
 * file is exactly what an administrator has typed in through Settings — this repository never
 * fetches, generates, or pre-populates any standard or citation. Starts completely empty, same
 * "don't invent business/regulatory content" discipline this codebase already applies to
 * [BusinessInfo] and equipment pricing.
 *
 * Follows [SettingsRepository]'s own DataStore pattern, but for two growing lists rather than
 * scalar preferences — each list is stored as one JSON-encoded string, the same encode/decode
 * approach [QuoteRepository] already uses for its own JSON blobs.
 */
class CodeStandardRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val standardsKey = stringPreferencesKey("code_standards_json")
    private val referencesKey = stringPreferencesKey("code_requirement_references_json")

    val standards: Flow<List<CodeStandard>> = context.codeStandardsDataStore.data.map { prefs ->
        prefs[standardsKey]?.let { runCatching { json.decodeFromString<List<CodeStandard>>(it) }.getOrNull() } ?: emptyList()
    }

    val references: Flow<List<CodeRequirementReference>> = context.codeStandardsDataStore.data.map { prefs ->
        prefs[referencesKey]?.let { runCatching { json.decodeFromString<List<CodeRequirementReference>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun addStandard(name: String, edition: String, sourceNote: String) {
        val current = standards.first()
        val updated = current + CodeStandard(
            id = "std_${System.currentTimeMillis()}_${current.size}",
            name = name,
            edition = edition,
            sourceNote = sourceNote,
            addedAtMillis = System.currentTimeMillis()
        )
        context.codeStandardsDataStore.edit { it[standardsKey] = json.encodeToString(updated) }
    }

    suspend fun deleteStandard(id: String) {
        val updatedStandards = standards.first().filterNot { it.id == id }
        // A standard's own citations stop resolving to anything the moment the standard itself is
        // gone — remove them too, rather than leaving orphaned references CodeReferenceLookup would
        // silently treat as SourceRequired anyway (explicit deletion is clearer than silent orphaning).
        val updatedReferences = references.first().filterNot { it.standardId == id }
        context.codeStandardsDataStore.edit {
            it[standardsKey] = json.encodeToString(updatedStandards)
            it[referencesKey] = json.encodeToString(updatedReferences)
        }
    }

    suspend fun addReference(standardId: String, checkLabel: String, sectionArticle: String, relevanceNote: String) {
        val current = references.first()
        val updated = current + CodeRequirementReference(
            id = "ref_${System.currentTimeMillis()}_${current.size}",
            standardId = standardId,
            checkLabel = checkLabel,
            sectionArticle = sectionArticle,
            relevanceNote = relevanceNote
        )
        context.codeStandardsDataStore.edit { it[referencesKey] = json.encodeToString(updated) }
    }

    suspend fun deleteReference(id: String) {
        val updated = references.first().filterNot { it.id == id }
        context.codeStandardsDataStore.edit { it[referencesKey] = json.encodeToString(updated) }
    }
}
