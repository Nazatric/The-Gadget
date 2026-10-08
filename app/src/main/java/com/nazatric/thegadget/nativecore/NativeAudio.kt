package com.nazatric.thegadget.nativecore

import org.json.JSONObject

data class NativeProbe(
    val codec: String = "",
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitDepth: Int = 0,
    val bitrate: Int = 0,
    val lossless: Boolean = false,
    val known: Boolean = false,
    val replayGainTrack: Float? = null,
    val replayGainAlbum: Float? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val composer: String = "",
    val comment: String = "",
    val copyright: String = "",
    val lyrics: String = "",
    val encoder: String = "",
    val year: Int = 0,
    val track: Int = 0,
    val disc: Int = 0,
)

object NativeAudio {
    val loaded: Boolean = try {
        System.loadLibrary("gadget_audio")
        true
    } catch (_: Throwable) {
        false
    }

    private external fun probe(fd: Int): String
    private external fun engineVersion(): String

    fun version(): String = if (loaded) runCatching { engineVersion() }.getOrDefault("unavailable") else "unavailable"

    fun probeFd(fd: Int): NativeProbe {
        if (!loaded || fd < 0) return NativeProbe()
        val raw = runCatching { probe(fd) }.getOrNull() ?: return NativeProbe()
        return runCatching { parse(raw) }.getOrDefault(NativeProbe())
    }

    fun parse(raw: String): NativeProbe {
        val j = JSONObject(raw)
        fun s(key: String) = j.optString(key, "")
        fun gain(key: String): Float? = if (j.isNull(key)) null else j.optDouble(key).toFloat().takeIf { it.isFinite() }
        return NativeProbe(
            codec = s("codec"),
            sampleRate = j.optInt("sampleRate"),
            channels = j.optInt("channels"),
            bitDepth = j.optInt("bitDepth"),
            bitrate = j.optInt("bitrate"),
            lossless = j.optBoolean("lossless"),
            known = j.optBoolean("known"),
            replayGainTrack = gain("replayGainTrack"),
            replayGainAlbum = gain("replayGainAlbum"),
            title = s("title"),
            artist = s("artist"),
            album = s("album"),
            albumArtist = s("albumArtist"),
            genre = s("genre"),
            composer = s("composer"),
            comment = s("comment"),
            copyright = s("copyright"),
            lyrics = s("lyrics"),
            encoder = s("encoder"),
            year = j.optInt("year"),
            track = j.optInt("track"),
            disc = j.optInt("disc"),
        )
    }
}
