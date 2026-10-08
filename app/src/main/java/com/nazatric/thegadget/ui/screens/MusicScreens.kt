package com.nazatric.thegadget.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nazatric.thegadget.data.artwork.ArtworkStore
import com.nazatric.thegadget.data.db.HistoryEntity
import com.nazatric.thegadget.data.db.PlaylistEntity
import com.nazatric.thegadget.data.db.TrackEntity
import com.nazatric.thegadget.data.db.TrackSummary
import com.nazatric.thegadget.logic.formatBytes
import com.nazatric.thegadget.logic.formatDuration
import com.nazatric.thegadget.ui.AlbumGroup
import com.nazatric.thegadget.ui.ArtistGroup
import com.nazatric.thegadget.ui.LibraryIndex
import com.nazatric.thegadget.ui.NameGroup
import com.nazatric.thegadget.ui.components.GadgetField
import com.nazatric.thegadget.ui.components.GlassOrb
import com.nazatric.thegadget.ui.components.Glyph
import com.nazatric.thegadget.ui.components.GlyphKind
import com.nazatric.thegadget.ui.components.Hairline
import com.nazatric.thegadget.ui.components.glass
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import com.nazatric.thegadget.ui.theme.sans

@Composable
fun ArtDisc(uri: String?, size: Dp, px: Int, store: ArtworkStore, modifier: Modifier = Modifier, extract: Boolean = false) {
    var bitmap by remember(uri, px) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, px, extract) {
        bitmap = if (uri.isNullOrBlank()) null else store.load(uri, px, extract)
    }
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("○", style = italicLabel(14.sp, GadgetColors.dim))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TrackSummary,
    active: Boolean,
    favorite: Boolean,
    store: ArtworkStore,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtDisc(track.uri, 46.dp, 160, store)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, style = italicLabel(16.sp, if (active) GadgetColors.text else GadgetColors.text))
            Text(track.artist, style = sans(12.sp, GadgetColors.dim), maxLines = 1)
        }
        if (favorite) Text("•", style = italicLabel(14.sp), modifier = Modifier.padding(end = 8.dp))
        Text(formatDuration(track.durationMs), style = sans(12.sp, GadgetColors.dim))
    }
    Hairline(Modifier.padding(start = 76.dp, end = 18.dp))
}

@Composable
fun MusicHome(
    index: LibraryIndex,
    recent: List<String>,
    scanLabel: String,
    glow: Float,
    grain: Boolean,
    store: ArtworkStore,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    onScan: () -> Unit,
    onGrant: () -> Unit,
    onFolder: () -> Unit,
    needsPermission: Boolean,
) {
    ScreenFrame("music", glow, grain, onBack, scroll = true) {
        Column {
            Text(scanLabel, style = sans(13.sp, GadgetColors.dim), modifier = Modifier.padding(horizontal = 22.dp))
            if (needsPermission) {
                Spacer(Modifier.height(12.dp))
                Text("allow audio", style = italicLabel(16.sp), modifier = Modifier.padding(horizontal = 22.dp).glass(16.dp).clickable(onClick = onGrant).padding(12.dp))
                Text("or choose a folder", style = italicLabel(16.sp), modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp).clickable(onClick = onFolder))
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                listOf(
                    "songs" to "music/songs",
                    "albums" to "music/albums",
                    "artists" to "music/artists",
                    "genres" to "music/genres",
                    "folders" to "music/folders",
                    "lists" to "music/playlists",
                    "search" to "music/search",
                ).forEach { (label, route) ->
                    Text(
                        label,
                        style = italicLabel(14.sp),
                        modifier = Modifier.padding(horizontal = 8.dp).clickable { onOpen(route) },
                    )
                }
            }
            Text("scan", style = italicLabel(14.sp, GadgetColors.soft), modifier = Modifier.padding(horizontal = 22.dp).clickable(onClick = onScan))
            if (recent.isNotEmpty()) {
                Text("recent", style = italicLabel(18.sp), modifier = Modifier.padding(start = 22.dp, top = 18.dp))
                recent.take(8).forEach { uri ->
                    val track = index.tracks.find { it.uri == uri } ?: return@forEach
                    TrackRow(track, false, false, store, { onPlay(uri) }, {})
                }
            }
            if (index.tracks.isEmpty() && !needsPermission) {
                Text("no recordings yet", style = italicLabel(22.sp), modifier = Modifier.padding(22.dp))
            }
        }
    }
}

@Composable
fun SongList(
    title: String,
    tracks: List<TrackSummary>,
    current: String?,
    favorites: Set<String>,
    store: ArtworkStore,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onPlay: (TrackSummary) -> Unit,
    onMenu: (TrackSummary) -> Unit,
    header: @Composable () -> Unit = {},
) {
    ScreenFrame(title, glow, grain, onBack, scroll = false) {
        header()
        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks, key = { it.uri }) { track ->
                TrackRow(track, track.uri == current, track.uri in favorites, store, { onPlay(track) }, { onMenu(track) })
            }
        }
    }
}

@Composable
fun CollectionScreen(
    title: String,
    groups: List<NameGroup>,
    store: ArtworkStore,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onOpen: (NameGroup) -> Unit,
) {
    ScreenFrame(title, glow, grain, onBack, scroll = false) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups, key = { it.name }) { group ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(group) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtDisc(group.artUri, 52.dp, 160, store)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(group.name, style = italicLabel(18.sp))
                        Text("${group.count}", style = sans(12.sp, GadgetColors.dim))
                    }
                }
                Hairline(Modifier.padding(start = 84.dp))
            }
        }
    }
}

@Composable
fun AlbumList(
    albums: List<AlbumGroup>,
    store: ArtworkStore,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onOpen: (AlbumGroup) -> Unit,
) {
    ScreenFrame("albums", glow, grain, onBack, scroll = false) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(albums, key = { it.album + it.artist }) { album ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(album) }.padding(18.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtDisc(album.artUri, 64.dp, 160, store)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(album.album, style = italicLabel(18.sp))
                        Text("${album.artist} · ${album.count}", style = sans(12.sp, GadgetColors.dim))
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistList(
    artists: List<ArtistGroup>,
    store: ArtworkStore,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onOpen: (ArtistGroup) -> Unit,
) {
    ScreenFrame("artists", glow, grain, onBack, scroll = false) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(artists, key = { it.artist }) { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(artist) }.padding(18.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtDisc(artist.artUri, 52.dp, 160, store)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(artist.artist, style = italicLabel(18.sp))
                        Text("${artist.albums} albums · ${artist.count}", style = sans(12.sp, GadgetColors.dim))
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    tracks: List<TrackSummary>,
    store: ArtworkStore,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onPlay: (TrackSummary) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, tracks) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else tracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) || it.genre.lowercase().contains(q) ||
                it.composer.lowercase().contains(q)
        }.take(200)
    }
    ScreenFrame("search", glow, grain, onBack, scroll = false) {
        GadgetField(query, { query = it }, "title, artist, album", Modifier.fillMaxWidth().padding(horizontal = 18.dp))
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(filtered, key = { it.uri }) { track ->
                TrackRow(track, false, false, store, { onPlay(track) }, {})
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    playlists: List<PlaylistEntity>,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ScreenFrame("playlists", glow, grain, onBack) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            GadgetField(name, { name = it }, "new playlist", Modifier.fillMaxWidth())
            Text("create", style = italicLabel(16.sp), modifier = Modifier.padding(top = 10.dp).clickable {
                if (name.isNotBlank()) {
                    onCreate(name)
                    name = ""
                }
            })
            playlists.forEach { playlist ->
                Text(
                    playlist.name,
                    style = italicLabel(20.sp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { onOpen(playlist.id) },
                )
                Hairline()
            }
            Text("favorites", style = italicLabel(20.sp), modifier = Modifier.padding(vertical = 12.dp).clickable { onOpen(-1) })
            Text("history", style = italicLabel(20.sp), modifier = Modifier.padding(vertical = 8.dp).clickable { onOpen(-2) })
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<HistoryEntity>,
    tracks: List<TrackSummary>,
    store: ArtworkStore,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onPlay: (TrackSummary) -> Unit,
) {
    ScreenFrame("history", glow, grain, onBack, scroll = false) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(history, key = { it.id }) { row ->
                val track = tracks.find { it.uri == row.uri } ?: return@items
                TrackRow(track, false, false, store, { onPlay(track) }, {})
            }
        }
    }
}

@Composable
fun DetailOverlay(
    track: TrackEntity,
    playlists: List<PlaylistEntity>,
    onAdd: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            .padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.glass(24.dp).padding(18.dp).clickable {}) {
            Text(track.title, style = italicLabel(24.sp))
            Text(track.artist, style = sans(14.sp, GadgetColors.soft))
            Spacer(Modifier.height(10.dp))
            listOf(
                "album" to track.album,
                "album artist" to track.albumArtist,
                "genre" to track.genre,
                "year" to track.year.takeIf { it > 0 }?.toString().orEmpty(),
                "track" to track.trackNumber.takeIf { it > 0 }?.toString().orEmpty(),
                "disc" to track.discNumber.takeIf { it > 0 }?.toString().orEmpty(),
                "composer" to track.composer,
                "codec" to track.codec,
                "bitrate" to track.bitrate.takeIf { it > 0 }?.let { "$it bps" }.orEmpty(),
                "rate" to track.sampleRate.takeIf { it > 0 }?.let { "$it Hz" }.orEmpty(),
                "depth" to track.bitDepth.takeIf { it > 0 }?.let { "$it-bit" }.orEmpty(),
                "channels" to track.channels.takeIf { it > 0 }?.toString().orEmpty(),
                "lossless" to if (track.lossless) "yes" else "no",
                "duration" to formatDuration(track.durationMs),
                "size" to formatBytes(track.fileSize),
                "mime" to track.mime,
                "path" to track.relativePath,
                "encoder" to track.encoder,
                "comment" to track.comment,
                "copyright" to track.copyright,
                "replaygain" to track.replayGainTrack?.let { "$it dB" }.orEmpty(),
            ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
                Text("$k  $v", style = sans(12.sp, GadgetColors.soft), modifier = Modifier.padding(vertical = 2.dp))
            }
            if (playlists.isNotEmpty()) {
                Text("add to", style = italicLabel(14.sp), modifier = Modifier.padding(top = 10.dp))
                playlists.take(8).forEach { playlist ->
                    Text(
                        playlist.name,
                        style = sans(13.sp),
                        modifier = Modifier.padding(vertical = 3.dp).clickable { onAdd(playlist.id) },
                    )
                }
            }
            Text("close", style = italicLabel(14.sp), modifier = Modifier.padding(top = 12.dp).clickable(onClick = onDismiss))
        }
    }
}

fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

@Composable
fun rememberAudioGranted(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, audioPermission()) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
