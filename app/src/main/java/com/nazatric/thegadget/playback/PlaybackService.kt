package com.nazatric.thegadget.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nazatric.thegadget.MainActivity
import com.nazatric.thegadget.R
import com.nazatric.thegadget.data.db.GadgetDatabase
import com.nazatric.thegadget.data.prefs.GadgetPrefs
import com.nazatric.thegadget.data.prefs.queueList
import com.nazatric.thegadget.logic.dbToLinear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val gain = ReplayGainProcessor()
    private var sleepJob: Job? = null
    private var gapless = true
    private var skipOnError = true
    private var gainMode = 1
    private var preamp = 0f
    private var advancing = false

    override fun onCreate() {
        super.onCreate()
        val prefs = GadgetPrefs(this)
        val settings = kotlinx.coroutines.runBlocking(Dispatchers.IO) { prefs.settings.first() }
        gapless = settings.gapless
        skipOnError = settings.skipOnError
        gainMode = settings.replayGainMode
        preamp = settings.replayGainPreamp
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput || settings.floatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(gain))
                    .build()
            }
        }
        val exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo.pauseAtEndOfMediaItems = !gapless
        exo.repeatMode = when (settings.repeatMode) {
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exo.shuffleModeEnabled = settings.shuffle
        applyOffload(exo, settings.offload)
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyGain(mediaItem)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    rememberHistory(mediaItem)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                PlaybackBus.error.tryEmit(error.errorCodeName + ": " + (error.message ?: "playback failed"))
                if (skipOnError && exo.hasNextMediaItem()) exo.seekToNextMediaItem()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && !gapless && !advancing) {
                    advancing = true
                    scope.launch {
                        delay(280)
                        advancing = false
                        if (exo.hasNextMediaItem()) {
                            exo.seekToNextMediaItem()
                            exo.play()
                        }
                    }
                }
            }
        })
        player = exo
        val activity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN, "now")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    return Futures.immediateFuture(resumption())
                }
            })
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("gadget_playback")
                .setChannelName(R.string.notification_channel)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_stat_gadget) },
        )
        scope.launch(Dispatchers.IO) {
            prefs.settings.collect { next ->
                gapless = next.gapless
                skipOnError = next.skipOnError
                gainMode = next.replayGainMode
                preamp = next.replayGainPreamp
                launch(Dispatchers.Main) {
                    exo.pauseAtEndOfMediaItems = !next.gapless
                    applyOffload(exo, next.offload)
                    applyGain(exo.currentMediaItem)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SLEEP) {
            scheduleSleep(intent.getIntExtra(EXTRA_MINUTES, 0))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        sleepJob?.cancel()
        session?.release()
        player?.release()
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            PlaybackBus.sleepUntil = 0L
            return
        }
        val until = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
        PlaybackBus.sleepUntil = until
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player?.pause()
            PlaybackBus.sleepUntil = 0L
        }
    }

    private fun applyOffload(exo: ExoPlayer, enabled: Boolean) {
        val mode = if (enabled) {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(mode)
                    .build(),
            )
            .build()
    }

    private fun applyGain(item: MediaItem?) {
        val extras = item?.mediaMetadata?.extras
        val track = extras?.getFloat("rg_track", Float.NaN) ?: Float.NaN
        val album = extras?.getFloat("rg_album", Float.NaN) ?: Float.NaN
        val chosen = when (gainMode) {
            2 -> album.takeIf { it.isFinite() } ?: track.takeIf { it.isFinite() }
            1 -> track.takeIf { it.isFinite() } ?: album.takeIf { it.isFinite() }
            else -> null
        }
        gain.linearGain = if (chosen == null && preamp == 0f) 1f else dbToLinear((chosen ?: 0f) + preamp)
    }

    private fun rememberHistory(item: MediaItem?) {
        val uri = item?.mediaId ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                GadgetDatabase.get(this@PlaybackService).history().insert(
                    com.nazatric.thegadget.data.db.HistoryEntity(
                        uri = uri,
                        playedAt = System.currentTimeMillis(),
                        positionMs = player?.currentPosition ?: 0L,
                    ),
                )
            }
        }
    }

    private fun resumption(): MediaSession.MediaItemsWithStartPosition {
        val settings = kotlinx.coroutines.runBlocking(Dispatchers.IO) { GadgetPrefs(this@PlaybackService).settings.first() }
        val uris = settings.queueList()
        if (uris.isEmpty()) return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        val db = GadgetDatabase.get(this)
        val items = uris.map { uri ->
            val track = kotlinx.coroutines.runBlocking(Dispatchers.IO) { db.tracks().track(uri) }
            MediaItem.Builder()
                .setMediaId(uri)
                .setUri(uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track?.title ?: uri)
                        .setArtist(track?.artist)
                        .setAlbumTitle(track?.album)
                        .build(),
                )
                .build()
        }
        return MediaSession.MediaItemsWithStartPosition(
            items,
            settings.queueIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            settings.queuePosition.coerceAtLeast(0L),
        )
    }

    companion object {
        const val ACTION_SLEEP = "com.nazatric.thegadget.SLEEP"
        const val EXTRA_MINUTES = "minutes"
    }
}

object PlaybackBus {
    val error = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    @Volatile var sleepUntil: Long = 0L
}
