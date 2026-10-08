package com.nazatric.thegadget.data.library

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.nazatric.thegadget.data.artwork.ArtworkStore
import com.nazatric.thegadget.data.db.GadgetDatabase
import com.nazatric.thegadget.data.db.TrackEntity
import com.nazatric.thegadget.data.prefs.GadgetPrefs
import com.nazatric.thegadget.logic.Stamp
import com.nazatric.thegadget.logic.audioExtension
import com.nazatric.thegadget.logic.diffScan
import com.nazatric.thegadget.nativecore.NativeAudio
import com.nazatric.thegadget.nativecore.NativeProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val seen: Int, val label: String) : ScanState
    data class Done(val count: Int, val added: Int, val at: Long) : ScanState
    data class Failed(val message: String) : ScanState
    data object NeedsPermission : ScanState
}

data class RawAudio(
    val uri: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val dateModified: Long,
    val dateAdded: Long,
    val mime: String,
    val trackNumber: Int,
    val year: Int,
    val composer: String,
    val relativePath: String,
    val displayName: String,
    val bucket: String,
    val albumContentUri: Uri?,
    val bitrate: Int,
)

class LibraryIndexer(
    private val context: Context,
    private val db: GadgetDatabase,
    private val artwork: ArtworkStore,
    private val prefs: GadgetPrefs,
) {
    val state = MutableStateFlow<ScanState>(ScanState.Idle)
    private val gate = Mutex()

    suspend fun scan(hasAudioPermission: Boolean) = gate.withLock {
        withContext(Dispatchers.IO) {
            try {
                val settings = prefs.settings.first()
                state.value = ScanState.Running(0, "reading library")
                val incoming = ArrayList<RawAudio>()
                if (hasAudioPermission) {
                    incoming += queryMediaStore(settings.minDurationSec, settings.includePodcasts)
                } else if (settings.safTrees.isEmpty()) {
                    state.value = ScanState.NeedsPermission
                    return@withContext
                }
                settings.safTrees.forEach { tree ->
                    incoming += querySaf(Uri.parse(tree), settings.minDurationSec)
                }
                val existing = db.tracks().stamps()
                val delta = diffScan(
                    existing.filter { it.source == "mediastore" || settings.safTrees.isNotEmpty() }.map {
                        Stamp(it.uri, it.dateModified, it.fileSize, it.enriched)
                    },
                    incoming.map { Stamp(it.uri, it.dateModified, it.size, false) },
                )
                val byUri = incoming.associateBy { it.uri }
                val todo = (delta.fresh + delta.changed).mapNotNull { byUri[it] }
                var done = 0
                val batch = ArrayList<TrackEntity>(48)
                for (raw in todo) {
                    batch += enrich(raw, settings.preferEmbeddedArt, settings.artworkPx)
                    done++
                    if (batch.size >= 40) {
                        db.tracks().upsertAll(batch.toList())
                        batch.clear()
                        state.value = ScanState.Running(done, "reading metadata")
                    }
                }
                if (batch.isNotEmpty()) db.tracks().upsertAll(batch)
                val removable = delta.missing.filter { uri ->
                    val stamp = existing.find { it.uri == uri } ?: return@filter false
                    stamp.source == "mediastore" || settings.safTrees.none { uri.startsWith(it) }
                }
                if (removable.isNotEmpty()) {
                    removable.chunked(200).forEach { db.tracks().deleteUris(it) }
                }
                val count = db.tracks().count().first()
                state.value = ScanState.Done(count, delta.fresh.size, System.currentTimeMillis())
            } catch (t: SecurityException) {
                state.value = ScanState.NeedsPermission
            } catch (t: Throwable) {
                state.value = ScanState.Failed(t.message ?: "library scan failed")
            }
        }
    }

    private fun queryMediaStore(minDurationSec: Int, includePodcasts: Boolean): List<RawAudio> {
        val minMs = minDurationSec * 1000L
        val out = ArrayList<RawAudio>()
        val collections = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.getExternalVolumeNames(context).map { MediaStore.Audio.Media.getContentUri(it) }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_PODCAST,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            projection += MediaStore.Audio.Media.RELATIVE_PATH
            projection += MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
            projection += MediaStore.Audio.Media.ALBUM_ID
        }
        if (Build.VERSION.SDK_INT >= 30) projection += MediaStore.Audio.Media.BITRATE
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
            if (!includePodcasts) append(" AND ${MediaStore.Audio.Media.IS_PODCAST} = 0")
        }
        collections.forEach { collection ->
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                selection,
                arrayOf(minMs.toString()),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val addCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val composerCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val bitrateCol = if (Build.VERSION.SDK_INT >= 30) cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val trackRaw = cursor.getInt(trackCol)
                    val track = if (trackRaw > 1000) trackRaw % 1000 else trackRaw
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else -1L
                    val albumUri = if (albumId > 0) {
                        ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                    } else {
                        null
                    }
                    out += RawAudio(
                        uri = uri.toString(),
                        source = "mediastore",
                        title = cursor.getString(titleCol).orEmpty(),
                        artist = cursor.getString(artistCol).orEmpty().ifBlank { "unknown artist" },
                        album = cursor.getString(albumCol).orEmpty().ifBlank { "unknown album" },
                        durationMs = cursor.getLong(durCol),
                        size = cursor.getLong(sizeCol),
                        dateModified = cursor.getLong(modCol),
                        dateAdded = cursor.getLong(addCol),
                        mime = cursor.getString(mimeCol).orEmpty(),
                        trackNumber = track,
                        year = cursor.getInt(yearCol),
                        composer = cursor.getString(composerCol).orEmpty(),
                        relativePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "",
                        displayName = cursor.getString(nameCol).orEmpty(),
                        bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else "",
                        albumContentUri = albumUri,
                        bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0,
                    )
                }
            }
        }
        return out
    }

    private fun querySaf(tree: Uri, minDurationSec: Int): List<RawAudio> {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val out = ArrayList<RawAudio>()
        val stack = ArrayDeque<Pair<DocumentFile, Int>>()
        stack.add(root to 0)
        var walked = 0
        while (stack.isNotEmpty() && walked < 20_000) {
            val (doc, depth) = stack.removeLast()
            if (doc.isDirectory) {
                if (depth > 12) continue
                doc.listFiles().forEach { stack.add(it to depth + 1) }
                continue
            }
            walked++
            val name = doc.name ?: continue
            val mime = doc.type.orEmpty()
            if (!mime.startsWith("audio/") && !audioExtension(name)) continue
            val uri = doc.uri
            val duration = readDuration(uri)
            if (duration in 1 until minDurationSec * 1000L) continue
            out += RawAudio(
                uri = uri.toString(),
                source = "saf",
                title = name.substringBeforeLast('.'),
                artist = "unknown artist",
                album = root.name ?: "folder",
                durationMs = duration,
                size = doc.length(),
                dateModified = doc.lastModified() / 1000,
                dateAdded = System.currentTimeMillis() / 1000,
                mime = mime,
                trackNumber = 0,
                year = 0,
                composer = "",
                relativePath = name,
                displayName = name,
                bucket = root.name.orEmpty(),
                albumContentUri = null,
                bitrate = 0,
            )
        }
        return out
    }

    private suspend fun enrich(raw: RawAudio, preferEmbedded: Boolean, artPx: Int): TrackEntity {
        val parsed = Uri.parse(raw.uri)
        var probe = NativeProbe()
        runCatching {
            context.contentResolver.openFileDescriptor(parsed, "r")?.use { pfd ->
                probe = NativeAudio.probeFd(pfd.fd)
            }
        }
        val retrieverTags = readRetriever(parsed)
        val title = firstNonBlank(probe.title, retrieverTags.title, raw.title, raw.displayName.substringBeforeLast('.'), "untitled")
        val artist = firstNonBlank(probe.artist, retrieverTags.artist, raw.artist, "unknown artist")
        val album = firstNonBlank(probe.album, retrieverTags.album, raw.album, "unknown album")
        val bytes = if (preferEmbedded) artwork.embeddedBytes(parsed) else null
        val hasEmbedded = bytes != null
        if (hasEmbedded) artwork.storeEmbedded(raw.uri, bytes, artPx)
        else artwork.storeAlbumThumb(raw.uri, raw.albumContentUri, artPx)
        val codec = probe.codec.ifBlank { codecFromMime(raw.mime, raw.displayName) }
        val lossless = probe.lossless || codec in setOf("FLAC", "ALAC", "WAV", "AIFF", "PCM")
        return TrackEntity(
            uri = raw.uri,
            source = raw.source,
            title = title,
            artist = artist,
            album = album,
            albumArtist = probe.albumArtist.ifBlank { artist },
            genre = probe.genre.ifBlank { retrieverTags.genre },
            composer = probe.composer.ifBlank { raw.composer },
            year = probe.year.takeIf { it > 0 } ?: raw.year,
            trackNumber = probe.track.takeIf { it > 0 } ?: raw.trackNumber,
            discNumber = probe.disc,
            comment = probe.comment,
            copyright = probe.copyright,
            lyrics = probe.lyrics.ifBlank { retrieverTags.lyrics },
            encoder = probe.encoder,
            bitrate = probe.bitrate.takeIf { it > 0 } ?: raw.bitrate,
            sampleRate = probe.sampleRate,
            bitDepth = probe.bitDepth,
            channels = probe.channels,
            durationMs = raw.durationMs.takeIf { it > 0 } ?: retrieverTags.durationMs,
            mime = raw.mime,
            codec = codec,
            lossless = lossless,
            fileSize = raw.size,
            relativePath = raw.relativePath,
            displayName = raw.displayName,
            bucket = raw.bucket.ifBlank { raw.relativePath.substringBeforeLast('/', "").ifBlank { "library" } },
            dateModified = raw.dateModified,
            dateAdded = raw.dateAdded,
            replayGainTrack = probe.replayGainTrack,
            replayGainAlbum = probe.replayGainAlbum,
            hasArt = hasEmbedded || artwork.has(raw.uri, artPx) || artwork.has(raw.uri, 160),
            enriched = true,
        )
    }

    private fun readDuration(uri: Uri): Long = readRetriever(uri).durationMs

    private data class RetrieverTags(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val genre: String = "",
        val lyrics: String = "",
        val durationMs: Long = 0,
    )

    private fun readRetriever(uri: Uri): RetrieverTags = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            RetrieverTags(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty(),
                lyrics = "",
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } finally {
            r.release()
        }
    }.getOrDefault(RetrieverTags())

    private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() && it != "<unknown>" }.orEmpty()

    private fun codecFromMime(mime: String, name: String): String {
        val ext = name.substringAfterLast('.', "").uppercase()
        return when {
            mime.contains("flac") || ext == "FLAC" -> "FLAC"
            mime.contains("mpeg") || ext == "MP3" -> "MP3"
            mime.contains("mp4") || ext == "M4A" || ext == "AAC" -> "AAC"
            mime.contains("ogg") || ext == "OGG" -> "Vorbis"
            mime.contains("opus") || ext == "OPUS" -> "Opus"
            mime.contains("wav") || ext == "WAV" -> "WAV"
            ext == "ALAC" -> "ALAC"
            ext.isNotBlank() -> ext
            else -> mime.substringAfter('/').uppercase()
        }
    }
}
