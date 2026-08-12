package com.lumix.estimator

import android.app.Application
import com.lumix.estimator.data.AppDatabase
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository

class LumixApp : Application() {
    lateinit var quoteRepository: QuoteRepository
        private set
    lateinit var priceRepository: PriceRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        quoteRepository = QuoteRepository(db.quoteDao())
        priceRepository = PriceRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
