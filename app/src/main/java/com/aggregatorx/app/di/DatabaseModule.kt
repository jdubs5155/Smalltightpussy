package com.aggregatorx.app.di

import android.content.Context
import androidx.room.Room
import com.aggregatorx.app.data.database.*
import com.aggregatorx.app.engine.scraper.SmartNavigationEngine
import com.aggregatorx.app.engine.media.VideoExtractorEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AggregatorDatabase {
        return Room.databaseBuilder(
            context,
            AggregatorDatabase::class.java,
            "aggregator_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun provideProviderDao(database: AggregatorDatabase): ProviderDao {
        return database.providerDao()
    }
    
    @Provides
    fun provideSiteAnalysisDao(database: AggregatorDatabase): SiteAnalysisDao {
        return database.siteAnalysisDao()
    }
    
    @Provides
    fun provideScrapingConfigDao(database: AggregatorDatabase): ScrapingConfigDao {
        return database.scrapingConfigDao()
    }
    
    @Provides
    fun provideSearchHistoryDao(database: AggregatorDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }
    
    @Provides
    @Singleton
    fun provideSmartNavigationEngine(): SmartNavigationEngine {
        return SmartNavigationEngine()
    }
    
    @Provides
    @Singleton
    fun provideVideoExtractorEngine(): VideoExtractorEngine {
        return VideoExtractorEngine()
    }
}
