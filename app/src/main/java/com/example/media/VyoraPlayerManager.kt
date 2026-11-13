package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
object VyoraMediaCache {
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "vyora_video_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(150L * 1024L * 1024L) // 150MB LRU cache
                val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, dbProvider).also { simpleCache = it }
            }
        }
    }
}

@OptIn(UnstableApi::class)
class VyoraPlayerPool(private val context: Context) {
    private val appContext = context.applicationContext
    private val cache = VyoraMediaCache.getCache(appContext)
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
    private val upstreamFactory = DefaultDataSource.Factory(appContext, httpFactory)
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val players = mutableMapOf<String, ExoPlayer>()

    private fun buildPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 12000, 800, 1500)
            .build()
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    fun getOrPreparePlayer(videoId: String, videoUrl: String): ExoPlayer {
        val existing = players[videoId]
        if (existing != null) return existing

        // Keep max 3 players in memory (current + previous + next preload)
        if (players.size >= 3) {
            val oldestKey = players.keys.firstOrNull()
            if (oldestKey != null) {
                players.remove(oldestKey)?.release()
            }
        }
        val player = buildPlayer()
        if (videoUrl.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            player.prepare()
        }
        players[videoId] = player
        return player
    }

    fun preloadNext(videoId: String, videoUrl: String) {
        if (videoUrl.isBlank() || players.containsKey(videoId)) return
        val player = getOrPreparePlayer(videoId, videoUrl)
        player.playWhenReady = false
    }

    fun pauseAllExcept(activeVideoId: String?) {
        players.forEach { (id, player) ->
            if (id != activeVideoId && player.isPlaying) {
                player.pause()
            }
        }
    }

    fun pauseAll() {
        players.values.forEach { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
    }

    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
    }
}

fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
