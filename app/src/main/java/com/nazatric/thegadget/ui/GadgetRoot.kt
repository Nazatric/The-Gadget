package com.nazatric.thegadget.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nazatric.thegadget.AppContainer
import com.nazatric.thegadget.GadgetApp
import com.nazatric.thegadget.data.db.TrackEntity
import com.nazatric.thegadget.data.db.TrackSummary
import com.nazatric.thegadget.nativecore.NativeAudio
import com.nazatric.thegadget.playback.OutputReport
import com.nazatric.thegadget.playback.outputReport
import com.nazatric.thegadget.ui.screens.AccountScreen
import com.nazatric.thegadget.ui.screens.AlbumList
import com.nazatric.thegadget.ui.screens.ArtistList
import com.nazatric.thegadget.ui.screens.CollectionScreen
import com.nazatric.thegadget.ui.screens.ConfigScreen
import com.nazatric.thegadget.ui.screens.DetailOverlay
import com.nazatric.thegadget.ui.screens.FeaturesScreen
import com.nazatric.thegadget.ui.screens.GamesScreen
import com.nazatric.thegadget.ui.screens.HistoryScreen
import com.nazatric.thegadget.ui.screens.HomeScreen
import com.nazatric.thegadget.ui.screens.MiniPlayer
import com.nazatric.thegadget.ui.screens.MusicHome
import com.nazatric.thegadget.ui.screens.NowPlayingScreen
import com.nazatric.thegadget.ui.screens.PlaylistScreen
import com.nazatric.thegadget.ui.screens.QueueScreen
import com.nazatric.thegadget.ui.screens.SearchScreen
import com.nazatric.thegadget.ui.screens.SessionActions
import com.nazatric.thegadget.ui.screens.SongList
import com.nazatric.thegadget.ui.screens.UsernameScreen
import com.nazatric.thegadget.ui.screens.audioPermission
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun GadgetRoot(container: AppContainer, openNow: Boolean, consumeOpen: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GadgetApp
    val session: SessionViewModel = viewModel(factory = GadgetFactory(app))
    val library: LibraryViewModel = viewModel(factory = GadgetFactory(app))
    val games: GamesViewModel = viewModel(factory = GadgetFactory(app))
    val settings by session.settings.collectAsState()
    val index by library.library.collectAsState()
    val playback by container.player.state.collectAsState()
    val scan by library.scan.collectAsState()
    val favorites by library.favorites.collectAsState()
    val playlists by library.playlists.collectAsState()
    val history by library.history.collectAsState()
    val recent by library.recentUris.collectAsState()
    val gameList by games.games.collectAsState()
    val gameMessage by games.message.collectAsState()
    val nav = rememberNavController()
    var detail by remember { mutableStateOf<TrackEntity?>(null) }
    var lyrics by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<OutputReport?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    val favSet = favorites.toSet()
    val player = container.player

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        library.scan(granted)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            library.addSaf(uri)
        }
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun granted() = ContextCompat.checkSelfPermission(context, audioPermission()) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun ensureNotify() {
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!ok) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun play(tracks: List<TrackSummary>, start: TrackSummary) {
        if (tracks.isEmpty()) return
        ensureNotify()
        player.play(tracks, start.uri)
    }

    LaunchedEffect(playback.uri, settings.showLyrics, settings.showTech) {
        val uri = playback.uri
        lyrics = if (uri != null && settings.showLyrics) library.lyrics(uri) else null
        val full = if (uri != null && settings.showTech) library.full(uri) else null
        report = full?.let { outputReport(context, it.sampleRate, it.bitDepth, it.lossless) }
    }
    LaunchedEffect(Unit) {
        player.errors.collect { banner = it }
    }
    LaunchedEffect(openNow) {
        if (openNow) {
            nav.navigate("music/now") { launchSingleTop = true }
            consumeOpen()
        }
    }
    LaunchedEffect(settings.autoScan) {
        if (settings.autoScan && granted()) library.scan(true)
    }

    val glow = settings.glow
    val grain = settings.grain
    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(settings.username, glow, grain, settings.reduceMotion, playback.playing) {
                    nav.navigate(it)
                }
            }
            composable("username") {
                UsernameScreen(settings, glow, grain, { nav.popBackStack() }) { name, bio ->
                    session.username(name)
                    session.bio(bio)
                    nav.popBackStack()
                }
            }
            composable("features") {
                FeaturesScreen(glow, grain, { nav.popBackStack() }) { nav.navigate(it) }
            }
            composable("account") {
                AccountScreen(
                    settings, index.tracks.size, index.albums.size, index.artists.size,
                    history.sumOf { it.positionMs }, library.artworkBytes(), gameList.size,
                    glow, grain, { nav.popBackStack() },
                    { nav.navigate("username") },
                    { library.clearHistory() },
                    { library.clearArtwork() },
                )
            }
            composable("config") {
                ConfigScreen(
                    settings,
                    NativeAudio.version(),
                    glow,
                    grain,
                    { nav.popBackStack() },
                    SessionActions(
                        glow = session::glow,
                        reduceMotion = session::reduceMotion,
                        grain = session::grain,
                        resume = session::resume,
                        autoplay = session::autoplay,
                        gapless = session::gapless,
                        skipOnError = session::skipOnError,
                        replayGain = session::replayGain,
                        preamp = session::preamp,
                        keepScreen = session::keepScreen,
                        offload = session::offload,
                        floatOutput = session::floatOutput,
                        showTech = session::showTech,
                        showLyrics = session::showLyrics,
                        notificationArt = session::notificationArt,
                        autoScan = session::autoScan,
                        minDuration = session::minDuration,
                        podcasts = session::podcasts,
                        embeddedArt = session::embeddedArt,
                        artworkPx = session::artworkPx,
                    ),
                )
            }
            composable("games") {
                GamesScreen(
                    gameList, gameMessage, glow, grain, { nav.popBackStack() },
                    games::importZip, games::importTree, games::delete,
                )
            }
            composable("music") {
                MusicHome(
                    index, recent, scan.label, glow, grain, library.artwork,
                    { nav.popBackStack() },
                    { nav.navigate(it) },
                    { uri -> index.tracks.find { it.uri == uri }?.let { play(listOf(it), it) } },
                    { if (granted()) library.scan(true) else permission.launch(audioPermission()) },
                    { permission.launch(audioPermission()) },
                    { folder.launch(null) },
                    !granted() && index.tracks.isEmpty(),
                )
            }
            composable("music/songs") {
                SongList(
                    "songs", index.tracks, playback.uri, favSet, library.artwork, glow, grain,
                    { nav.popBackStack() },
                    { play(index.tracks, it) },
                    { library.inspect(it.uri) { found -> detail = found } },
                ) {
                    Row(Modifier.padding(horizontal = 10.dp)) {
                        listOf("title", "artist", "album", "year", "added", "duration").forEach { key ->
                            Text(
                                key,
                                style = italicLabel(13.sp, if (settings.sort == key) GadgetColors.text else GadgetColors.dim),
                                modifier = Modifier.padding(8.dp).clickable { session.sort(key) },
                            )
                        }
                    }
                }
            }
            composable("music/search") {
                SearchScreen(index.tracks, library.artwork, glow, grain, { nav.popBackStack() }) {
                    play(listOf(it), it)
                }
            }
            composable("music/albums") {
                AlbumList(index.albums, library.artwork, glow, grain, { nav.popBackStack() }) {
                    nav.navigate("music/album/${enc(it.album)}/${enc(it.artist)}")
                }
            }
            composable(
                "music/album/{album}/{artist}",
                arguments = listOf(
                    navArgument("album") { type = NavType.StringType },
                    navArgument("artist") { type = NavType.StringType },
                ),
            ) { entry ->
                val album = dec(entry.arguments?.getString("album"))
                val artist = dec(entry.arguments?.getString("artist"))
                val tracks = index.tracks.filter {
                    it.album == album && (it.albumArtist.ifBlank { it.artist } == artist || it.artist == artist)
                }
                SongList(album, tracks, playback.uri, favSet, library.artwork, glow, grain, { nav.popBackStack() },
                    { play(tracks, it) }, { library.inspect(it.uri) { found -> detail = found } })
            }
            composable("music/artists") {
                ArtistList(index.artists, library.artwork, glow, grain, { nav.popBackStack() }) {
                    nav.navigate("music/artist/${enc(it.artist)}")
                }
            }
            composable("music/artist/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                val name = dec(entry.arguments?.getString("name"))
                val tracks = index.tracks.filter { it.artist == name }
                SongList(name, tracks, playback.uri, favSet, library.artwork, glow, grain, { nav.popBackStack() },
                    { play(tracks, it) }, { library.inspect(it.uri) { found -> detail = found } })
            }
            composable("music/genres") {
                CollectionScreen("genres", index.genres, library.artwork, glow, grain, { nav.popBackStack() }) {
                    nav.navigate("music/genre/${enc(it.name)}")
                }
            }
            composable("music/genre/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                val name = dec(entry.arguments?.getString("name"))
                val tracks = index.tracks.filter { it.genre == name }
                SongList(name, tracks, playback.uri, favSet, library.artwork, glow, grain, { nav.popBackStack() },
                    { play(tracks, it) }, { library.inspect(it.uri) { found -> detail = found } })
            }
            composable("music/folders") {
                CollectionScreen("folders", index.folders, library.artwork, glow, grain, { nav.popBackStack() }) {
                    nav.navigate("music/folder/${enc(it.name)}")
                }
            }
            composable("music/folder/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                val name = dec(entry.arguments?.getString("name"))
                val tracks = index.tracks.filter {
                    (it.bucket.ifBlank { it.relativePath.substringBeforeLast('/', "library") }.ifBlank { "library" }) == name
                }
                SongList(name, tracks, playback.uri, favSet, library.artwork, glow, grain, { nav.popBackStack() },
                    { play(tracks, it) }, { library.inspect(it.uri) { found -> detail = found } })
            }
            composable("music/playlists") {
                PlaylistScreen(playlists, glow, grain, { nav.popBackStack() }, { id ->
                    when (id) {
                        -1L -> nav.navigate("music/favorites")
                        -2L -> nav.navigate("music/history")
                        else -> nav.navigate("music/playlist/$id")
                    }
                }, { library.createPlaylist(it) })
            }
            composable("music/favorites") {
                val tracks = index.tracks.filter { it.uri in favSet }
                SongList("favorites", tracks, playback.uri, favSet, library.artwork, glow, grain, { nav.popBackStack() },
                    { play(tracks, it) }, { library.inspect(it.uri) { found -> detail = found } })
            }
            composable("music/history") {
                HistoryScreen(history, index.tracks, library.artwork, glow, grain, { nav.popBackStack() }) {
                    play(listOf(it), it)
                }
            }
            composable("music/playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val uris by library.playlistUris(id).collectAsState(emptyList())
                val tracks = uris.mapNotNull { uri -> index.tracks.find { it.uri == uri } }
                val name = playlists.find { it.id == id }?.name ?: "playlist"
                SongList(name, tracks, playback.uri, favSet, library.artwork, glow, grain, { nav.popBackStack() },
                    { play(tracks, it) }, { library.inspect(it.uri) { found -> detail = found } })
            }
            composable("music/now") {
                NowPlayingScreen(
                    playback, library.artwork, lyrics, report, settings.showTech, settings.showLyrics,
                    glow, grain, settings.keepScreenOn, playback.uri in favSet,
                    { nav.popBackStack() },
                    { player.toggle() },
                    { player.next() },
                    { player.previous() },
                    { player.seekTo(it) },
                    { player.toggleShuffle() },
                    { player.cycleRepeat() },
                    { player.cycleSpeed() },
                    { playback.uri?.let { library.toggleFavorite(it) } },
                    { nav.navigate("music/queue") },
                    { player.sleep(30) },
                )
            }
            composable("music/queue") {
                QueueScreen(
                    playback.queue, playback.index, glow, grain, library.artwork, { nav.popBackStack() },
                    { player.seekQueue(it) },
                    { player.removeQueue(it) },
                    { from, to -> player.moveQueue(from, to) },
                    { player.clearQueue() },
                )
            }
        }
        val route = nav.currentBackStackEntry?.destination?.route.orEmpty()
        if (!route.startsWith("home") && route != "music/now" && playback.uri != null) {
            MiniPlayer(
                playback, library.artwork,
                { nav.navigate("music/now") },
                { player.toggle() },
                Modifier.align(Alignment.BottomCenter),
            )
        }
        detail?.let { track ->
            DetailOverlay(track, playlists, { library.addToPlaylist(it, track.uri) }) { detail = null }
        }
        banner?.let {
            Text(
                it,
                style = italicLabel(13.sp, GadgetColors.soft),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp).clickable { banner = null },
            )
        }
    }
}

private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
private fun dec(value: String?) = URLDecoder.decode(value.orEmpty(), Charsets.UTF_8.name())
