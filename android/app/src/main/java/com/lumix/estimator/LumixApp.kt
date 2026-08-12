package com.lumix.estimator

import android.app.Application
import com.lumix.estimator.data.AppDatabase
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.site.SiteRepository
import org.osmdroid.config.Configuration

class LumixApp : Application() {
    lateinit var quoteRepository: QuoteRepository
        private set
    lateinit var priceRepository: PriceRepository
        private set
    lateinit var siteRepository: SiteRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        quoteRepository = QuoteRepository(db.quoteDao())
        priceRepository = PriceRepository(this)
        siteRepository = SiteRepository()

        // osmdroid tile usage policy requires a real, distinguishing user agent (an unset/default
        // one gets bulk anonymous traffic rate-limited or blocked). Cache path is app-specific
        // external storage, which needs no runtime storage permission on this app's minSdk 26+.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = getExternalFilesDir("osmdroid") ?: filesDir
            osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")
        }
    }
}
