package com.nazatric.thegadget.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nazatric.thegadget.logic.neutralUsername
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("gadget_prefs")

data class GadgetSettings(
    val username: String = "listener",
    val bio: String = "",
    val firstLaunch: Long = 0L,
    val glow: Float = 1f,
    val reduceMotion: Boolean = false,
    val grain: Boolean = true,
    val resume: Boolean = true,
    val autoplayResume: Boolean = false,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    val replayGainMode: Int = 1,
    val replayGainPreamp: Float = 0f,
    val skipOnError: Boolean = true,
    val gapless: Boolean = true,
    val offload: Boolean = false,
    val floatOutput: Boolean = false,
    val showTech: Boolean = true,
    val showLyrics: Boolean = true,
    val notificationArtwork: Boolean = true,
    val minDurationSec: Int = 30,
    val autoScan: Boolean = true,
    val preferEmbeddedArt: Boolean = true,
    val artworkPx: Int = 512,
    val keepScreenOn: Boolean = false,
    val includePodcasts: Boolean = false,
    val queueUris: String = "",
    val queueIndex: Int = 0,
    val queuePosition: Long = 0L,
    val safTrees: Set<String> = emptySet(),
    val sort: String = "title",
)

class GadgetPrefs(private val context: Context) {
    private object K {
        val username = stringPreferencesKey("username")
        val bio = stringPreferencesKey("bio")
        val firstLaunch = longPreferencesKey("first_launch")
        val glow = floatPreferencesKey("glow")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val grain = booleanPreferencesKey("grain")
        val resume = booleanPreferencesKey("resume")
        val autoplayResume = booleanPreferencesKey("autoplay_resume")
        val repeatMode = intPreferencesKey("repeat_mode")
        val shuffle = booleanPreferencesKey("shuffle")
        val replayGainMode = intPreferencesKey("rg_mode")
        val replayGainPreamp = floatPreferencesKey("rg_preamp")
        val skipOnError = booleanPreferencesKey("skip_on_error")
        val gapless = booleanPreferencesKey("gapless")
        val offload = booleanPreferencesKey("offload")
        val floatOutput = booleanPreferencesKey("float_output")
        val showTech = booleanPreferencesKey("show_tech")
        val showLyrics = booleanPreferencesKey("show_lyrics")
        val notificationArtwork = booleanPreferencesKey("notif_art")
        val minDurationSec = intPreferencesKey("min_duration")
        val autoScan = booleanPreferencesKey("auto_scan")
        val preferEmbeddedArt = booleanPreferencesKey("embedded_art")
        val artworkPx = intPreferencesKey("artwork_px")
        val keepScreenOn = booleanPreferencesKey("keep_screen")
        val includePodcasts = booleanPreferencesKey("podcasts")
        val queueUris = stringPreferencesKey("queue_uris")
        val queueIndex = intPreferencesKey("queue_index")
        val queuePosition = longPreferencesKey("queue_position")
        val safTrees = stringSetPreferencesKey("saf_trees")
        val sort = stringPreferencesKey("sort")
    }

    val settings: Flow<GadgetSettings> = context.store.data.map { p ->
        val first = p[K.firstLaunch] ?: 0L
        GadgetSettings(
            username = neutralUsername(p[K.username]),
            bio = p[K.bio].orEmpty().take(160),
            firstLaunch = first,
            glow = p[K.glow] ?: 1f,
            reduceMotion = p[K.reduceMotion] ?: false,
            grain = p[K.grain] ?: true,
            resume = p[K.resume] ?: true,
            autoplayResume = p[K.autoplayResume] ?: false,
            repeatMode = p[K.repeatMode] ?: 0,
            shuffle = p[K.shuffle] ?: false,
            replayGainMode = p[K.replayGainMode] ?: 1,
            replayGainPreamp = p[K.replayGainPreamp] ?: 0f,
            skipOnError = p[K.skipOnError] ?: true,
            gapless = p[K.gapless] ?: true,
            offload = p[K.offload] ?: false,
            floatOutput = p[K.floatOutput] ?: false,
            showTech = p[K.showTech] ?: true,
            showLyrics = p[K.showLyrics] ?: true,
            notificationArtwork = p[K.notificationArtwork] ?: true,
            minDurationSec = p[K.minDurationSec] ?: 30,
            autoScan = p[K.autoScan] ?: true,
            preferEmbeddedArt = p[K.preferEmbeddedArt] ?: true,
            artworkPx = p[K.artworkPx] ?: 512,
            keepScreenOn = p[K.keepScreenOn] ?: false,
            includePodcasts = p[K.includePodcasts] ?: false,
            queueUris = p[K.queueUris].orEmpty(),
            queueIndex = p[K.queueIndex] ?: 0,
            queuePosition = p[K.queuePosition] ?: 0L,
            safTrees = p[K.safTrees] ?: emptySet(),
            sort = p[K.sort] ?: "title",
        )
    }

    suspend fun ensureFirstLaunch() {
        context.store.edit { p ->
            if (p[K.firstLaunch] == null) p[K.firstLaunch] = System.currentTimeMillis()
            if (p[K.username].isNullOrBlank()) p[K.username] = "listener"
        }
    }

    suspend fun setUsername(value: String) = edit(K.username, neutralUsername(value))
    suspend fun setBio(value: String) = edit(K.bio, value.trim().take(160))
    suspend fun setGlow(value: Float) = edit(K.glow, value.coerceIn(0.35f, 1.4f))
    suspend fun setReduceMotion(value: Boolean) = edit(K.reduceMotion, value)
    suspend fun setGrain(value: Boolean) = edit(K.grain, value)
    suspend fun setResume(value: Boolean) = edit(K.resume, value)
    suspend fun setAutoplayResume(value: Boolean) = edit(K.autoplayResume, value)
    suspend fun setRepeatMode(value: Int) = edit(K.repeatMode, value)
    suspend fun setShuffle(value: Boolean) = edit(K.shuffle, value)
    suspend fun setReplayGainMode(value: Int) = edit(K.replayGainMode, value.coerceIn(0, 2))
    suspend fun setReplayGainPreamp(value: Float) = edit(K.replayGainPreamp, value.coerceIn(-12f, 6f))
    suspend fun setSkipOnError(value: Boolean) = edit(K.skipOnError, value)
    suspend fun setGapless(value: Boolean) = edit(K.gapless, value)
    suspend fun setOffload(value: Boolean) = edit(K.offload, value)
    suspend fun setFloatOutput(value: Boolean) = edit(K.floatOutput, value)
    suspend fun setShowTech(value: Boolean) = edit(K.showTech, value)
    suspend fun setShowLyrics(value: Boolean) = edit(K.showLyrics, value)
    suspend fun setNotificationArtwork(value: Boolean) = edit(K.notificationArtwork, value)
    suspend fun setMinDurationSec(value: Int) = edit(K.minDurationSec, value.coerceIn(0, 600))
    suspend fun setAutoScan(value: Boolean) = edit(K.autoScan, value)
    suspend fun setPreferEmbeddedArt(value: Boolean) = edit(K.preferEmbeddedArt, value)
    suspend fun setArtworkPx(value: Int) = edit(K.artworkPx, value)
    suspend fun setKeepScreenOn(value: Boolean) = edit(K.keepScreenOn, value)
    suspend fun setIncludePodcasts(value: Boolean) = edit(K.includePodcasts, value)
    suspend fun setSort(value: String) = edit(K.sort, value)
    suspend fun setQueue(uris: List<String>, index: Int, position: Long) {
        context.store.edit {
            it[K.queueUris] = uris.joinToString("\u001e")
            it[K.queueIndex] = index
            it[K.queuePosition] = position
        }
    }

    suspend fun addSafTree(uri: String) {
        context.store.edit {
            it[K.safTrees] = (it[K.safTrees] ?: emptySet()) + uri
        }
    }

    suspend fun removeSafTree(uri: String) {
        context.store.edit {
            it[K.safTrees] = (it[K.safTrees] ?: emptySet()) - uri
        }
    }

    private suspend fun <T> edit(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.store.edit { it[key] = value }
    }
}

fun GadgetSettings.queueList(): List<String> =
    queueUris.split('\u001e').filter { it.isNotBlank() }
