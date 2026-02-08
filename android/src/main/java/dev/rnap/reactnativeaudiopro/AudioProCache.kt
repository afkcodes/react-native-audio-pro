package dev.rnap.reactnativeaudiopro

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object AudioProCache {
    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    
    // Default cache size: 500MB
    private var maxCacheSize = 500 * 1024 * 1024L

    fun setMaxCacheSize(size: Long) {
        maxCacheSize = size
    }

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "audio_pro_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            databaseProvider = StandaloneDatabaseProvider(context)
            
            // We use a simple LRU evictor
            val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
            
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider!!)
        }
        return simpleCache!!
    }

    @Synchronized
    fun release() {
        simpleCache?.release()
        simpleCache = null
        databaseProvider = null
    }

    fun createDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        val cache = getInstance(context)
        // Default behavior of CacheDataSource is:
        // 1. Check cache. 
        // 2. If data exists, read from cache.
        // 3. If data missing (partial or full), read from upstream.
        // 4. If offline and reading from upstream -> Error.
        
        // This is the correct behavior for "Offline Mode". 
        // If the user says it throws "source error", it means the cache was MISSING the data.
        // This implies the previous playback didn't actually cache it? 
        // Or the cache key is different?
        
        // We will stick to the defaults which are correct for this requirement.
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            // No custom flags, use defaults.
    }

    @Synchronized
    fun getCacheSize(context: Context): Long {
        return getInstance(context).cacheSpace
    }

    @Synchronized
    fun clearCache(context: Context) {
        try {
            // Releasing the cache closes the database and releases file locks
            release()
            
            val cacheDir = File(context.cacheDir, "audio_pro_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
