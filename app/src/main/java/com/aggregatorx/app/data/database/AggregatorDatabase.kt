package com.aggregatorx.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aggregatorx.app.data.model.Provider
import com.aggregatorx.app.data.model.ScrapingConfig
import com.aggregatorx.app.data.model.SearchHistoryEntry
import com.aggregatorx.app.data.model.SiteAnalysis

@Database(
    entities = [
        Provider::class,
        SiteAnalysis::class,
        ScrapingConfig::class,
        SearchHistoryEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AggregatorDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun siteAnalysisDao(): SiteAnalysisDao
    abstract fun scrapingConfigDao(): ScrapingConfigDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
