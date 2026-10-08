package com.nazatric.thegadget.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GadgetLogicTest {
    @Test
    fun usernameNeverUsesTheRemovedWatermark() {
        assertNotEquals(Defaults.bannedUsername, Defaults.USERNAME.lowercase())
        assertEquals(Defaults.USERNAME, neutralUsername("  "))
        assertEquals(Defaults.USERNAME, neutralUsername(Defaults.bannedUsername))
        assertEquals(Defaults.USERNAME, neutralUsername(Defaults.bannedUsername.replaceFirstChar { it.uppercase() }))
        assertEquals("ada", neutralUsername("  ada  "))
    }

    @Test
    fun replayGainMath() {
        assertEquals(1f, dbToLinear(0f), 0.001f)
        assertEquals(0.5f, dbToLinear(-6.0206f), 0.01f)
        assertEquals(-7.5f, parseGainDb("-7.50 dB")!!, 0.001f)
        assertNull(parseGainDb("none"))
    }

    @Test
    fun scanDiffIsIncremental() {
        val existing = listOf(
            Stamp("a", 1, 10, true),
            Stamp("b", 2, 20, true),
            Stamp("c", 3, 30, false),
        )
        val incoming = listOf(
            Stamp("a", 1, 10, true),
            Stamp("b", 9, 20, true),
            Stamp("d", 1, 1, false),
        )
        val delta = diffScan(existing, incoming)
        assertEquals(listOf("d"), delta.fresh)
        assertEquals(listOf("b"), delta.changed)
        assertEquals(listOf("c"), delta.missing)
    }

    @Test
    fun lrcParsingAndCursor() {
        val lines = parseLrc("[00:01.00]one\n[00:03.50]two\nplain\n")!!
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals(0, activeLrcIndex(lines, 1500))
        assertEquals(1, activeLrcIndex(lines, 4000))
        assertNull(parseLrc("no timestamps here"))
    }

    @Test
    fun gameManifestAndZipSlip() {
        val manifest = parseGameManifest(
            """{"title":"Orbit","description":"a quiet game","artwork":"cover.png","entry":"play.html"}""",
            "folder",
        )
        assertEquals("Orbit", manifest.title)
        assertEquals("play.html", manifest.entry)
        assertEquals("cover.png", manifest.artwork)
        assertEquals("untitled folder", parseGameManifest(null, "untitled-folder").title)
        assertNull(safeZipEntry("../secret"))
        assertNull(safeZipEntry("a/../../b"))
        assertEquals("games/index.html", safeZipEntry("games/index.html"))
        assertEquals("index.html", safeZipEntry("/index.html"))
    }

    @Test
    fun durationAndBytes() {
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertTrue(formatBytes(1536).contains("KB"))
        assertTrue(audioExtension("song.FLAC"))
        assertFalse(audioExtension("notes.txt"))
    }
}
