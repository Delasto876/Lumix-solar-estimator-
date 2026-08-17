package com.lumix.estimator

import android.app.Application
import com.lumix.estimator.data.AppDatabase
import com.lumix.estimator.data.CodeStandardRepository
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.site.SiteRepository

class LumixApp : Application() {
    lateinit var quoteRepository: QuoteRepository
        private set
    lateinit var priceRepository: PriceRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    /** A81 (Phase 18): Solar Site's own saved sites/roof-planes — restored alongside the rest of that feature. */
    lateinit var siteRepository: SiteRepository
        private set
    /** A82 (Phase 19): administrator-entered electrical-code standards/citations — see the repository's own doc. */
    lateinit var codeStandardRepository: CodeStandardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        quoteRepository = QuoteRepository(db.quoteDao())
        priceRepository = PriceRepository(this)
        settingsRepository = SettingsRepository(this)
        siteRepository = SiteRepository()
        codeStandardRepository = CodeStandardRepository(this)
    }
}
