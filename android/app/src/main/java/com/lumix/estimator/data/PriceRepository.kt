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

class PriceRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val regularKey = stringPreferencesKey("regular_prices_json")
    private val discountKey = stringPreferencesKey("discount_prices_json")
    private val useDiscountDefaultKey = stringPreferencesKey("use_discount_default")

    val regularPrices: Flow<PriceList> = context.priceDataStore.data.map { prefs ->
        decodeOrDefault(prefs[regularKey])
    }

    val discountPrices: Flow<PriceList> = context.priceDataStore.data.map { prefs ->
        decodeOrDefault(prefs[discountKey])
    }

    private fun decodeOrDefault(raw: String?): PriceList {
        if (raw == null) return PriceList.DEFAULT
        return try {
            json.decodeFromString(PriceList.serializer(), raw)
        } catch (e: Exception) {
            PriceList.DEFAULT
        }
    }

    suspend fun updateRegular(prices: PriceList) {
        context.priceDataStore.edit { it[regularKey] = json.encodeToString(prices) }
    }

    suspend fun updateDiscount(prices: PriceList) {
        context.priceDataStore.edit { it[discountKey] = json.encodeToString(prices) }
    }

    suspend fun resetRegularToDefault() = updateRegular(PriceList.DEFAULT)

    suspend fun resetDiscountToDefault() = updateDiscount(PriceList.DEFAULT)
}
