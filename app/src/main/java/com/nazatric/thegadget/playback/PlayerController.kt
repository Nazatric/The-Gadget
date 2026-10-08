package com.nazatric.thegadget.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nazatric.thegadget.data.artwork.ArtworkStore
import com.nazatric.thegadget.data.db.TrackSummary
import com.nazatric.thegadget.data.prefs.GadgetPrefs
import com.nazatric.thegadget.data.prefs.queueList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueItem(val uri: String, val title: String, val artist: String)

data class PlaybackUiState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val uri: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val buffering: Boolean = false,
)

class PlayerController(
    private val app: Context,
    private val prefs: GadgetPrefs,
    private val artwork: ArtworkStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var restored = false
    @Volatile private var showNotificationArt = true
    val state = MutableStateFlow(PlaybackUiState())
    val errors = PlaybackBus.error

    fun connect() {
        if (controller != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val created = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = created
            created.addListener(listener)
            publish()
            startTicker()
            scope.launch { restoreIfNeeded() }
        }, ContextCompat.getMainExecutor(app))
    }

    fun play(tracks: List<TrackSummary>, startUri: String?) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toItem() }
        val index = tracks.indexOfFirst { it.uri == startUri }.coerceAtLeast(0)
        val player = controller ?: return
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
        persist()
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > 3_000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) { controller?.seekTo(position.coerceAtLeast(0L)) }
    fun seekQueue(index: Int) { controller?.seekTo(index, 0L) }
    fun removeQueue(index: Int) { controller?.removeMediaItem(index); persist() }
    fun moveQueue(from: Int, to: Int) { controller?.moveMediaItem(from, to); persist() }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun cycleSpeed() {
        val player = controller ?: return
        val next = when (player.playbackParameters.speed) {
            1f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 0.75f
            else -> 1f
        }
        player.setPlaybackSpeed(next)
    }

    fun addNext(track: TrackSummary) {
        val player = controller ?: return
        val index = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(index, track.toItem())
        persist()
    }

    fun enqueue(track: TrackSummary) {
        controller?.addMediaItem(track.toItem())
        persist()
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        scope.launch(Dispatchers.IO) { prefs.setQueue(emptyList(), 0, 0) }
    }

    fun sleep(minutes: Int) {
        app.startService(
            Intent(app, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SLEEP
                putExtra(PlaybackService.EXTRA_MINUTES, minutes)
            },
        )
    }

    private suspend fun restoreIfNeeded() {
        if (restored) return
        restored = true
        val player = controller ?: return
        if (player.mediaItemCount > 0) return
        val settings = prefs.settings.first()
        if (!settings.resume) return
        val uris = settings.queueList()
        if (uris.isEmpty()) return
        // Titles are filled when the library is available; uri playback still works.
        val items = uris.map { uri ->
            MediaItem.Builder().setMediaId(uri).setUri(uri).setMediaMetadata(
                MediaMetadata.Builder().setTitle(uri.substringAfterLast('/')).build(),
            ).build()
        }
        player.setMediaItems(
            items,
            settings.queueIndex.coerceIn(0, items.lastIndex),
            settings.queuePosition.coerceAtLeast(0L),
        )
        player.prepare()
        if (settings.autoplayResume) player.play()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                publish()
                persist()
                delay(if (controller?.isPlaying == true) 250 else 800)
            }
        }
    }

    private fun persist() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val uris = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        scope.launch(Dispatchers.IO) {
            prefs.setQueue(uris, player.currentMediaItemIndex, player.currentPosition)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    private fun publish() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val queue = (0 until player.mediaItemCount).map { index ->
            val media = player.getMediaItemAt(index)
            QueueItem(
                uri = media.mediaId,
                title = media.mediaMetadata.title?.toString().orEmpty().ifBlank { "untitled" },
                artist = media.mediaMetadata.artist?.toString().orEmpty(),
            )
        }
        state.value = PlaybackUiState(
            connected = true,
            playing = player.isPlaying,
            uri = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "untitled" },
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            speed = player.playbackParameters.speed,
            queue = queue,
            index = player.currentMediaItemIndex,
            buffering = player.playbackState == Player.STATE_BUFFERING,
        )
    }

    private fun TrackSummary.toItem(): MediaItem {
        val extras = Bundle().apply {
            replayGainTrack?.let { putFloat("rg_track", it) }
            replayGainAlbum?.let { putFloat("rg_album", it) }
            putString("codec", codec)
            putInt("sampleRate", sampleRate)
            putInt("bitDepth", bitDepth)
            putInt("channels", channels)
            putInt("bitrate", bitrate)
            putBoolean("lossless", lossless)
        }
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(if (showNotificationArt) artwork.contentUri(app, uri) else null)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}


