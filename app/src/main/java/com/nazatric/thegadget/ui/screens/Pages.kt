package com.nazatric.thegadget.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.BuildConfig
import com.nazatric.thegadget.data.prefs.GadgetSettings
import com.nazatric.thegadget.logic.formatBytes
import com.nazatric.thegadget.logic.formatHours
import com.nazatric.thegadget.ui.components.GadgetField
import com.nazatric.thegadget.ui.components.Hairline
import com.nazatric.thegadget.ui.components.glass
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import com.nazatric.thegadget.ui.theme.sans
import java.text.DateFormat
import java.util.Date

@Composable
fun UsernameScreen(
    settings: GadgetSettings,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(settings.username) { mutableStateOf(settings.username) }
    var bio by remember(settings.bio) { mutableStateOf(settings.bio) }
    ScreenFrame("username", glow, grain, onBack) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("stored on this device", style = sans(13.sp, GadgetColors.dim))
            Spacer(Modifier.height(18.dp))
            GadgetField(name, { name = it.take(24) }, "name", Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            GadgetField(bio, { bio = it.take(160) }, "a short note", Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Text(
                "save",
                style = italicLabel(18.sp),
                modifier = Modifier
                    .glass(20.dp)
                    .clickable { onSave(name, bio) }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun FeaturesScreen(
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val items = listOf(
        Triple("library", "MediaStore indexing, incremental scans, artists, albums, genres, folders, search and sort.", "music"),
        Triple("playback", "Media3 session, gapless transitions, queue, shuffle, repeat, speed, resume, audio focus.", "music/now"),
        Triple("quality", "Lossless formats where the device decoder allows them. ReplayGain. Honest output report. No fake loudness.", "config"),
        Triple("metadata", "Embedded title, artist, album, genre, year, track, disc, composer, comment, copyright, lyrics, encoder, art.", "music"),
        Triple("controls", "Notification, lock screen, bluetooth and headset through the media session.", "config"),
        Triple("games", "An empty container for HTML games you add later. Nothing is shipped.", "games"),
        Triple("native", "C++ probe for codec, rate, depth, channels and ReplayGain tags.", "config"),
    )
    ScreenFrame("features", glow, grain, onBack) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            items.forEach { (title, body, route) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .glass(22.dp)
                        .clickable { onOpen(route) }
                        .padding(18.dp),
                ) {
                    Text(title, style = italicLabel(22.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(body, style = sans(14.sp, GadgetColors.soft))
                }
            }
        }
    }
}

@Composable
fun AccountScreen(
    settings: GadgetSettings,
    songs: Int,
    albums: Int,
    artists: Int,
    listenedMs: Long,
    cacheBytes: Long,
    games: Int,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    onUsername: () -> Unit,
    onClearHistory: () -> Unit,
    onClearCache: () -> Unit,
) {
    val since = if (settings.firstLaunch > 0) {
        DateFormat.getDateInstance().format(Date(settings.firstLaunch))
    } else {
        "today"
    }
    ScreenFrame("account", glow, grain, onBack) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text(settings.username, style = italicLabel(36.sp))
            if (settings.bio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(settings.bio, style = sans(14.sp))
            }
            Spacer(Modifier.height(8.dp))
            Text("on device since $since", style = sans(13.sp, GadgetColors.dim))
            Spacer(Modifier.height(22.dp))
            InfoRow("recordings", songs.toString())
            InfoRow("albums", albums.toString())
            InfoRow("artists", artists.toString())
            InfoRow("listened", formatHours(listenedMs))
            InfoRow("artwork cache", formatBytes(cacheBytes))
            InfoRow("games", games.toString())
            Spacer(Modifier.height(18.dp))
            Action("edit username", onUsername)
            Action("clear listening history", onClearHistory)
            Action("clear artwork cache", onClearCache)
        }
    }
}

@Composable
fun ConfigScreen(
    settings: GadgetSettings,
    engine: String,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    set: SessionActions,
) {
    ScreenFrame("config", glow, grain, onBack) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Section("appearance")
            Toggle("glow", settings.glow > 0.8f) { set.glow(if (it) 1f else 0.45f) }
            Stepper("glow amount", "%.2f".format(settings.glow),
                { set.glow((settings.glow - 0.1f).coerceIn(0.35f, 1.4f)) },
                { set.glow((settings.glow + 0.1f).coerceIn(0.35f, 1.4f)) },
            )
            Toggle("reduce motion", settings.reduceMotion, set::reduceMotion)
            Toggle("grain", settings.grain, set::grain)
            Section("playback")
            Toggle("resume queue", settings.resume, set::resume)
            Toggle("resume playing", settings.autoplayResume, set::autoplay)
            Toggle("gapless transitions", settings.gapless, set::gapless)
            Toggle("skip unreadable files", settings.skipOnError, set::skipOnError)
            Choice("replaygain", listOf("off", "track", "album"), settings.replayGainMode, set::replayGain)
            Stepper("preamp", "%+.1f dB".format(settings.replayGainPreamp),
                { set.preamp(settings.replayGainPreamp - 0.5f) },
                { set.preamp(settings.replayGainPreamp + 0.5f) },
            )
            Toggle("keep screen on while playing", settings.keepScreenOn, set::keepScreen)
            Section("audio path")
            Toggle("prefer audio offload", settings.offload, set::offload)
            Text(
                "Offload can break gapless and ReplayGain on some devices. Leave it off unless you need it.",
                style = sans(12.sp, GadgetColors.dim),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Toggle("float output", settings.floatOutput, set::floatOutput)
            Text(
                "Float output applies the next time playback starts. It is not a bit-perfect switch.",
                style = sans(12.sp, GadgetColors.dim),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Toggle("show technical line", settings.showTech, set::showTech)
            Toggle("show lyrics", settings.showLyrics, set::showLyrics)
            Toggle("artwork in notification", settings.notificationArtwork, set::notificationArt)
            Section("library")
            Toggle("scan on open", settings.autoScan, set::autoScan)
            Stepper("skip under", "${settings.minDurationSec}s",
                { set.minDuration((settings.minDurationSec - 5).coerceIn(0, 180)) },
                { set.minDuration((settings.minDurationSec + 5).coerceIn(0, 180)) },
            )
            Toggle("include podcasts", settings.includePodcasts, set::podcasts)
            Toggle("prefer embedded art", settings.preferEmbeddedArt, set::embeddedArt)
            Choice("artwork size", listOf("256", "512", "1024"), listOf(256, 512, 1024).indexOf(settings.artworkPx).coerceAtLeast(1)) {
                set.artworkPx(listOf(256, 512, 1024)[it])
            }
            Section("about")
            Text("the gadget ${BuildConfig.VERSION_NAME}", style = italicLabel(18.sp), modifier = Modifier.padding(8.dp))
            Text("native $engine", style = sans(13.sp, GadgetColors.dim), modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                "Instrument Serif and Lato are used under the SIL Open Font License. Playback uses AndroidX Media3.",
                style = sans(13.sp, GadgetColors.soft),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

class SessionActions(
    val glow: (Float) -> Unit,
    val reduceMotion: (Boolean) -> Unit,
    val grain: (Boolean) -> Unit,
    val resume: (Boolean) -> Unit,
    val autoplay: (Boolean) -> Unit,
    val gapless: (Boolean) -> Unit,
    val skipOnError: (Boolean) -> Unit,
    val replayGain: (Int) -> Unit,
    val preamp: (Float) -> Unit,
    val keepScreen: (Boolean) -> Unit,
    val offload: (Boolean) -> Unit,
    val floatOutput: (Boolean) -> Unit,
    val showTech: (Boolean) -> Unit,
    val showLyrics: (Boolean) -> Unit,
    val notificationArt: (Boolean) -> Unit,
    val autoScan: (Boolean) -> Unit,
    val minDuration: (Int) -> Unit,
    val podcasts: (Boolean) -> Unit,
    val embeddedArt: (Boolean) -> Unit,
    val artworkPx: (Int) -> Unit,
)

@Composable
private fun Section(title: String) {
    Text(title, style = italicLabel(20.sp, GadgetColors.soft), modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { set(!on) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = italicLabel(16.sp), modifier = Modifier.weight(1f))
        Text(if (on) "on" else "off", style = sans(13.sp, if (on) GadgetColors.text else GadgetColors.dim))
    }
    Hairline(Modifier.padding(horizontal = 8.dp))
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = italicLabel(16.sp), modifier = Modifier.weight(1f))
        Text("−", style = italicLabel(20.sp), modifier = Modifier.clickable(onClick = onMinus).padding(8.dp))
        Text(value, style = sans(13.sp), modifier = Modifier.width(84.dp))
        Text("+", style = italicLabel(20.sp), modifier = Modifier.clickable(onClick = onPlus).padding(8.dp))
    }
}

@Composable
private fun Choice(label: String, options: List<String>, index: Int, set: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(label, style = italicLabel(16.sp))
        Row {
            options.forEachIndexed { i, option ->
                Text(
                    option,
                    style = italicLabel(14.sp, if (i == index) GadgetColors.text else GadgetColors.dim),
                    modifier = Modifier
                        .padding(end = 14.dp, top = 6.dp)
                        .clickable { set(i) },
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = italicLabel(16.sp), modifier = Modifier.weight(1f))
        Text(value, style = sans(14.sp))
    }
    Hairline()
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = italicLabel(16.sp),
        modifier = Modifier
            .padding(vertical = 6.dp)
            .glass(18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
