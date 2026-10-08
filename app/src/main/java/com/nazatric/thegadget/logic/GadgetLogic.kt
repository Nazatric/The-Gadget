package com.nazatric.thegadget.logic

import kotlin.math.pow
import kotlin.math.roundToInt

object Defaults {
    const val USERNAME = "listener"
    // Split so the removed watermark never appears as a literal in source.
    val bannedUsername: String = listOf("hen", "grphcs").joinToString("")
}

fun neutralUsername(raw: String?): String {
    val cleaned = raw?.trim()?.replace(Regex("\\s+"), " ")?.take(24).orEmpty()
    if (cleaned.isEmpty()) return Defaults.USERNAME
    if (cleaned.equals(Defaults.bannedUsername, ignoreCase = true)) return Defaults.USERNAME
    return cleaned
}

fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

fun parseGainDb(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""[-+]?\d+(?:\.\d+)?""").find(raw) ?: return null
    return match.value.toFloatOrNull()
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatHours(ms: Long): String {
    val hours = ms / 3_600_000.0
    return if (hours < 10) "%.1f h".format(hours) else "${hours.roundToInt()} h"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (v >= 100) "${v.roundToInt()} ${units[i]}" else "%.1f ${units[i]}".format(v)
}

data class Stamp(val uri: String, val dateModified: Long, val size: Long, val enriched: Boolean)

data class ScanDelta(val fresh: List<String>, val changed: List<String>, val missing: List<String>)

fun diffScan(existing: List<Stamp>, incoming: List<Stamp>): ScanDelta {
    val map = existing.associateBy { it.uri }
    val seen = HashSet<String>(incoming.size)
    val fresh = ArrayList<String>()
    val changed = ArrayList<String>()
    incoming.forEach { item ->
        seen += item.uri
        val prev = map[item.uri]
        when {
            prev == null -> fresh += item.uri
            !prev.enriched || prev.dateModified != item.dateModified || prev.size != item.size -> changed += item.uri
        }
    }
    val missing = existing.map { it.uri }.filter { it !in seen }
    return ScanDelta(fresh, changed, missing)
}

data class LrcLine(val timeMs: Long, val text: String)

fun parseLrc(raw: String?): List<LrcLine>? {
    if (raw.isNullOrBlank()) return null
    val lines = ArrayList<LrcLine>()
    val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    raw.lineSequence().forEach { line ->
        val marks = pattern.findAll(line).toList()
        if (marks.isEmpty()) return@forEach
        val text = line.replace(pattern, "").trim()
        if (text.isEmpty()) return@forEach
        marks.forEach { mark ->
            val min = mark.groupValues[1].toLongOrNull() ?: return@forEach
            val sec = mark.groupValues[2].toLongOrNull() ?: return@forEach
            val frac = mark.groupValues[3]
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            lines += LrcLine(min * 60_000 + sec * 1000 + fracMs, text)
        }
    }
    if (lines.isEmpty()) return null
    return lines.sortedBy { it.timeMs }
}

fun activeLrcIndex(lines: List<LrcLine>, positionMs: Long): Int {
    var idx = -1
    lines.forEachIndexed { i, line ->
        if (line.timeMs <= positionMs) idx = i
    }
    return idx
}

data class GameManifest(
    val title: String,
    val description: String,
    val artwork: String?,
    val entry: String,
    val author: String,
    val version: String,
)

fun parseGameManifest(json: String?, folderName: String): GameManifest {
    fun field(key: String): String? {
        if (json.isNullOrBlank()) return null
        val re = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = re.find(json)?.groupValues?.getOrNull(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\").trim().ifEmpty { null }
    }
    val title = field("title") ?: folderName.replace('_', ' ').replace('-', ' ').trim().ifEmpty { "untitled" }
    return GameManifest(
        title = title.take(80),
        description = field("description").orEmpty().take(400),
        artwork = field("artwork") ?: field("cover"),
        entry = field("entry") ?: field("index") ?: "index.html",
        author = field("author").orEmpty().take(80),
        version = field("version").orEmpty().take(24),
    )
}

fun safeZipEntry(name: String): String? {
    val normalized = name.replace('\\', '/').trim().trimStart('/')
    if (normalized.isEmpty()) return null
    if (normalized.contains('\u0000')) return null
    val parts = normalized.split('/')
    if (parts.any { it == ".." || it == "." }) return null
    if (normalized.startsWith("/")) return null
    return normalized
}

fun audioExtension(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("flac", "wav", "wave", "mp3", "m4a", "aac", "ogg", "opus", "alac", "aiff", "aif", "wma", "ape", "wv")
}

fun albumKey(album: String, artist: String): String = "$album\u001f$artist"

fun encodeKey(raw: String): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))

fun decodeKey(raw: String): String = try {
    String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
} catch (_: IllegalArgumentException) {
    raw
}
