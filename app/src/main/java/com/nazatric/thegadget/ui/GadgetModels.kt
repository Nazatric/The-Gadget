package com.nazatric.thegadget.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nazatric.thegadget.AppContainer
import com.nazatric.thegadget.GadgetApp
import com.nazatric.thegadget.data.db.PlaylistEntity
import com.nazatric.thegadget.data.db.TrackSummary
import com.nazatric.thegadget.data.library.ScanState
import com.nazatric.thegadget.data.prefs.GadgetPrefs
import com.nazatric.thegadget.data.prefs.GadgetSettings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(private val prefs: GadgetPrefs) : ViewModel() {
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GadgetSettings())
    fun username(value: String) = viewModelScope.launch { prefs.setUsername(value) }
    fun bio(value: String) = viewModelScope.launch { prefs.setBio(value) }
    fun glow(value: Float) = viewModelScope.launch { prefs.setGlow(value) }
    fun reduceMotion(value: Boolean) = viewModelScope.launch { prefs.setReduceMotion(value) }
    fun grain(value: Boolean) = viewModelScope.launch { prefs.setGrain(value) }
    fun resume(value: Boolean) = viewModelScope.launch { prefs.setResume(value) }
    fun autoplay(value: Boolean) = viewModelScope.launch { prefs.setAutoplayResume(value) }
    fun replayGain(value: Int) = viewModelScope.launch { prefs.setReplayGainMode(value) }
    fun preamp(value: Float) = viewModelScope.launch { prefs.setReplayGainPreamp(value) }
    fun skipOnError(value: Boolean) = viewModelScope.launch { prefs.setSkipOnError(value) }
    fun gapless(value: Boolean) = viewModelScope.launch { prefs.setGapless(value) }
    fun offload(value: Boolean) = viewModelScope.launch { prefs.setOffload(value) }
    fun floatOutput(value: Boolean) = viewModelScope.launch { prefs.setFloatOutput(value) }
    fun showTech(value: Boolean) = viewModelScope.launch { prefs.setShowTech(value) }
    fun showLyrics(value: Boolean) = viewModelScope.launch { prefs.setShowLyrics(value) }
    fun notificationArt(value: Boolean) = viewModelScope.launch { prefs.setNotificationArtwork(value) }
    fun minDuration(value: Int) = viewModelScope.launch { prefs.setMinDurationSec(value) }
    fun autoScan(value: Boolean) = viewModelScope.launch { prefs.setAutoScan(value) }
    fun embeddedArt(value: Boolean) = viewModelScope.launch { prefs.setPreferEmbeddedArt(value) }
    fun artworkPx(value: Int) = viewModelScope.launch { prefs.setArtworkPx(value) }
    fun keepScreen(value: Boolean) = viewModelScope.launch { prefs.setKeepScreenOn(value) }
    fun podcasts(value: Boolean) = viewModelScope.launch { prefs.setIncludePodcasts(value) }
    fun sort(value: String) = viewModelScope.launch { prefs.setSort(value) }
    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { /* filled by library vm */ }
}

data class AlbumGroup(val album: String, val artist: String, val count: Int, val durationMs: Long, val artUri: String, val year: Int)
data class ArtistGroup(val artist: String, val count: Int, val albums: Int, val artUri: String)
data class NameGroup(val name: String, val count: Int, val artUri: String)

data class LibraryIndex(
    val tracks: List<TrackSummary> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val genres: List<NameGroup> = emptyList(),
    val folders: List<NameGroup> = emptyList(),
)

fun indexLibrary(tracks: List<TrackSummary>, sort: String): LibraryIndex {
    val sorted = when (sort) {
        "artist" -> tracks.sortedBy { it.artist.lowercase() }
        "album" -> tracks.sortedBy { it.album.lowercase() }
        "duration" -> tracks.sortedByDescending { it.durationMs }
        "year" -> tracks.sortedByDescending { it.year }
        "added" -> tracks.sortedByDescending { it.dateAdded }
        else -> tracks.sortedBy { it.title.lowercase() }
    }
    val albums = sorted.groupBy { it.album to it.albumArtist.ifBlank { it.artist } }.map { (key, items) ->
        AlbumGroup(key.first, key.second, items.size, items.sumOf { it.durationMs }, items.first().uri, items.maxOf { it.year })
    }.sortedBy { it.album.lowercase() }
    val artists = sorted.groupBy { it.artist }.map { (name, items) ->
        ArtistGroup(name, items.size, items.map { it.album }.distinct().size, items.first().uri)
    }.sortedBy { it.artist.lowercase() }
    val genres = sorted.filter { it.genre.isNotBlank() }.groupBy { it.genre }.map { (name, items) ->
        NameGroup(name, items.size, items.first().uri)
    }.sortedBy { it.name.lowercase() }
    val folders = sorted.groupBy { it.bucket.ifBlank { it.relativePath.substringBeforeLast('/', "library") } }.map { (name, items) ->
        NameGroup(name.ifBlank { "library" }, items.size, items.first().uri)
    }.sortedBy { it.name.lowercase() }
    return LibraryIndex(sorted, albums, artists, genres, folders)
}

class LibraryViewModel(
    app: Application,
    private val container: AppContainer,
) : AndroidViewModel(app) {
    val scan = container.indexer.state
    val favorites = container.db.favorites().uris().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = container.db.playlists().playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = container.db.history().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentUris = container.db.history().recentUris().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = container.prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GadgetSettings())
    val library = combine(container.db.tracks().summaries(), container.prefs.settings) { tracks, settings ->
        indexLibrary(tracks, settings.sort)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryIndex())

    fun scan(granted: Boolean) {
        viewModelScope.launch { container.indexer.scan(granted) }
    }

    fun addSaf(uri: android.net.Uri) = viewModelScope.launch {
        container.prefs.addSafTree(uri.toString())
        container.indexer.scan(true)
    }

    fun removeSaf(uri: String) = viewModelScope.launch {
        container.prefs.removeSafTree(uri)
        container.indexer.scan(true)
    }

    fun toggleFavorite(uri: String) = viewModelScope.launch(Dispatchers.IO) {
        if (uri in favorites.value) container.db.favorites().remove(uri)
        else container.db.favorites().add(com.nazatric.thegadget.data.db.FavoriteEntity(uri, System.currentTimeMillis()))
    }

    fun createPlaylist(name: String, onId: (Long) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val id = container.db.playlists().insert(PlaylistEntity(name = name.trim().ifBlank { "playlist" }, createdAt = System.currentTimeMillis()))
        onId(id)
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch(Dispatchers.IO) {
        container.db.playlists().rename(id, name.trim().ifBlank { "playlist" })
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch(Dispatchers.IO) { container.db.playlists().delete(id) }

    fun addToPlaylist(id: Long, uri: String) = viewModelScope.launch(Dispatchers.IO) {
        val pos = container.db.playlists().maxPosition(id) + 1
        container.db.playlists().add(com.nazatric.thegadget.data.db.PlaylistTrackEntity(id, uri, pos))
    }

    fun removeFromPlaylist(id: Long, uri: String) = viewModelScope.launch(Dispatchers.IO) {
        container.db.playlists().remove(id, uri)
    }

    fun playlistUris(id: Long) = container.db.playlists().uris(id)

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { container.db.history().clear() }

    fun clearArtwork() = container.artwork.clear()

    fun artworkBytes(): Long = container.artwork.cacheBytes()

    suspend fun lyrics(uri: String): String? = container.db.tracks().lyrics(uri)

    suspend fun full(uri: String) = container.db.tracks().track(uri)

    fun inspect(uri: String, on: (com.nazatric.thegadget.data.db.TrackEntity) -> Unit) = viewModelScope.launch {
        full(uri)?.let { on(it) }
    }

    val player get() = container.player
    val artwork get() = container.artwork
    val prefs get() = container.prefs
}

class GamesViewModel(private val container: AppContainer) : ViewModel() {
    val games = container.games.games.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun refresh() = viewModelScope.launch { container.games.refresh() }

    fun importZip(uri: Uri) = viewModelScope.launch {
        container.games.importZip(uri).onSuccess { message.value = "added $it" }.onFailure { message.value = it.message }
    }

    fun importTree(uri: Uri) = viewModelScope.launch {
        container.games.importTree(uri).onSuccess { message.value = "added $it" }.onFailure { message.value = it.message }
    }

    fun delete(id: String) = viewModelScope.launch { container.games.delete(id) }
}

class GadgetFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = (app as GadgetApp).container
        return when {
            modelClass.isAssignableFrom(SessionViewModel::class.java) -> SessionViewModel(container.prefs) as T
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(app, container) as T
            modelClass.isAssignableFrom(GamesViewModel::class.java) -> GamesViewModel(container) as T
            else -> error("unknown ${modelClass.name}")
        }
    }
}

val ScanState.label: String
    get() = when (this) {
        ScanState.Idle -> "library idle"
        is ScanState.Running -> "${this.label} · ${this.seen}"
        is ScanState.Done -> "${this.count} recordings"
        is ScanState.Failed -> this.message
        ScanState.NeedsPermission -> "audio access needed"
    }
