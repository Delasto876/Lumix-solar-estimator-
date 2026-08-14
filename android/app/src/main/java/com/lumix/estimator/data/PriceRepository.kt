package com.lumix.estimator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumix.estimator.domain.PriceList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.priceDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumix_prices")

/**
 * A57 (spec §11): ONE price list — the separate "discount price list" this class used to also
 * store/expose (`discountPrices`/`updateDiscount`/`resetDiscountToDefault`, all removed) is gone;
 * a discount is applied per-quote via [com.lumix.estimator.domain.QuoteInputs.discountType]/
 * [com.lumix.estimator.domain.QuoteInputs.discountValue] on top of this one list's subtotal, not
 * by swapping the whole quote onto a second set of prices. The DataStore key stays named
 * "regular_prices_json" (an old installation's existing data, not worth a migration); the
 * now-orphaned "discount_prices_json"/"use_discount_default" keys are simply never read again.
 */
class PriceRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val regularKey = stringPreferencesKey("regular_prices_json")

    val prices: Flow<PriceList> = context.priceDataStore.data.map { prefs ->
        decodeOrDefault(prefs[regularKey])
    }

    private fun decodeOrDefault(raw: String?): PriceList {
        if (raw == null) return PriceList.DEFAULT
        return try {
            json.decodeFromString(PriceList.serializer(), raw)
        } catch (e: Exception) {
            PriceList.DEFAULT
        }
    }

    suspend fun update(prices: PriceList) {
        context.priceDataStore.edit { it[regularKey] = json.encodeToString(prices) }
    }

    suspend fun resetToDefault() = update(PriceList.DEFAULT)
}
