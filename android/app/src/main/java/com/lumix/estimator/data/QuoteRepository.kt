package com.lumix.estimator.data

import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SavedQuote(
    val id: Long,
    val timestamp: Long,
    val inputs: QuoteInputs,
    val result: QuoteResult
)

class QuoteRepository(private val dao: QuoteDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<QuoteEntity>> = dao.observeAll()

    private fun entityFor(id: Long, timestamp: Long, inputs: QuoteInputs, result: QuoteResult) = QuoteEntity(
        id = id,
        timestamp = timestamp,
        customerName = inputs.customerName,
        customerContact = inputs.customerContact,
        parish = inputs.parish,
        nearestTown = inputs.nearestTown,
        propertyType = inputs.propertyType.label,
        quoteMode = inputs.quoteMode.name,
        systemMode = result.effectiveSystemMode.name,
        pvKw = result.pvKw,
        panelCount = result.panelCount,
        inverterName = result.inverterName,
        batteryKwh = result.totalBatteryKwh,
        grandTotal = result.grandTotal,
        inputsJson = json.encodeToString(inputs),
        resultJson = json.encodeToString(result)
    )

    suspend fun save(inputs: QuoteInputs, result: QuoteResult): Long =
        dao.insert(entityFor(id = 0, timestamp = System.currentTimeMillis(), inputs = inputs, result = result))

    /**
     * A56: overwrites an already-saved row in place — used when CREATE QUOTE finishes a system
     * that was already calculated (and saved) earlier in the estimate flow, so the project stays
     * one row (and one quote ID, so an in-progress SIMULATE link keeps working) rather than
     * duplicating.
     */
    suspend fun update(id: Long, inputs: QuoteInputs, result: QuoteResult) {
        dao.update(entityFor(id = id, timestamp = System.currentTimeMillis(), inputs = inputs, result = result))
    }

    suspend fun getSavedQuote(id: Long): SavedQuote? {
        val entity = dao.getById(id) ?: return null
        return SavedQuote(
            id = entity.id,
            timestamp = entity.timestamp,
            inputs = json.decodeFromString(QuoteInputs.serializer(), entity.inputsJson),
            result = json.decodeFromString(QuoteResult.serializer(), entity.resultJson)
        )
    }

    suspend fun delete(entity: QuoteEntity) = dao.delete(entity)

    suspend fun clearAll() = dao.deleteAll()
}
