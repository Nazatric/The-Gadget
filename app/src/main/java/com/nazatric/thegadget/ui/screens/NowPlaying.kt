package com.nazatric.thegadget.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.data.artwork.ArtworkStore
import com.nazatric.thegadget.logic.activeLrcIndex
import com.nazatric.thegadget.logic.formatDuration
import com.nazatric.thegadget.logic.parseLrc
import com.nazatric.thegadget.playback.OutputReport
import com.nazatric.thegadget.playback.PlaybackUiState
import com.nazatric.thegadget.playback.QueueItem
import com.nazatric.thegadget.ui.components.GlassOrb
import com.nazatric.thegadget.ui.components.glass
import com.nazatric.thegadget.ui.components.Glyph
import com.nazatric.thegadget.ui.components.GlyphKind
import com.nazatric.thegadget.ui.theme.GadgetBackdrop
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import com.nazatric.thegadget.ui.theme.sans

@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    store: ArtworkStore,
    lyrics: String?,
    report: OutputReport?,
    showTech: Boolean,
    showLyrics: Boolean,
    glow: Float,
    grain: Boolean,
    keepScreen: Boolean,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    onSleep: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(state.playing, keepScreen) {
        val window = (context as? Activity)?.window
        if (state.playing && keepScreen) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GadgetBackdrop(glow, grain) {
        Column(
            Modifier.fillMaxSize().padding(top = top + 8.dp, bottom = bottom + 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassOrb(44.dp, onBack) { Glyph(GlyphKind.Back, Modifier.size(18.dp)) }
                Text("now", style = italicLabel(18.sp), modifier = Modifier.padding(start = 12.dp).weight(1f))
                Text("queue", style = italicLabel(14.sp, GadgetColors.soft), modifier = Modifier.clickable(onClick = onQueue))
            }
            Spacer(Modifier.height(18.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(280.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * glow), Color.Transparent)),
                        radius = size.minDimension * 0.5f,
                    )
                }
                ArtDisc(state.uri, 210.dp, 512, store, extract = true)
            }
            Spacer(Modifier.height(16.dp))
            Text(state.title.ifBlank { "nothing playing" }, style = italicLabel(28.sp))
            Text(state.artist, style = sans(14.sp, GadgetColors.soft))
            Text(state.album, style = sans(12.sp, GadgetColors.dim))
            SeekBar(state.positionMs, state.durationMs, onSeek, Modifier.padding(horizontal = 28.dp, vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(state.positionMs), style = sans(11.sp, GadgetColors.dim))
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs), style = sans(11.sp, GadgetColors.dim))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.shuffle) "shuffle" else "in order", style = italicLabel(13.sp, GadgetColors.soft), modifier = Modifier.clickable(onClick = onShuffle).padding(8.dp))
                GlassOrb(46.dp, onPrevious) { Glyph(GlyphKind.Previous, Modifier.size(18.dp)) }
                Spacer(Modifier.size(10.dp))
                GlassOrb(64.dp, onToggle, active = state.playing) {
                    Glyph(if (state.playing) GlyphKind.Pause else GlyphKind.Play, Modifier.size(22.dp))
                }
                Spacer(Modifier.size(10.dp))
                GlassOrb(46.dp, onNext) { Glyph(GlyphKind.Next, Modifier.size(18.dp)) }
                Text(
                    when (state.repeatMode) { 1 -> "one"; 2 -> "all"; else -> "once" },
                    style = italicLabel(13.sp, GadgetColors.soft),
                    modifier = Modifier.clickable(onClick = onRepeat).padding(8.dp),
                )
            }
            Row(Modifier.padding(top = 8.dp)) {
                Text(if (favorite) "loved" else "love", style = italicLabel(14.sp), modifier = Modifier.clickable(onClick = onFavorite).padding(8.dp))
                Text("${state.speed}×", style = italicLabel(14.sp), modifier = Modifier.clickable(onClick = onSpeed).padding(8.dp))
                Text("sleep", style = italicLabel(14.sp, GadgetColors.soft), modifier = Modifier.clickable(onClick = onSleep).padding(8.dp))
            }
            if (showTech && report != null) {
                Text(
                    "${report.outputSampleRate} Hz · ${report.note}",
                    style = sans(11.sp, GadgetColors.dim),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            if (showLyrics && !lyrics.isNullOrBlank()) {
                val synced = remember(lyrics) { parseLrc(lyrics) }
                val line = synced?.getOrNull(activeLrcIndex(synced, state.positionMs))?.text ?: lyrics
                Text(line, style = italicLabel(14.sp, GadgetColors.soft), modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableFloatStateOf(-1f) }
    val fraction = if (drag >= 0f) drag else if (duration > 0) position.toFloat() / duration else 0f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (duration > 0 && drag >= 0f) onSeek((drag * duration).toLong())
                        drag = -1f
                    },
                    onHorizontalDrag = { change, _ ->
                        drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        val y = size.height / 2
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 2f)
        drawCircle(Color.White, 5f, Offset(size.width * fraction, y))
    }
}

@Composable
fun QueueScreen(
    rows: List<QueueItem>,
    currentIndex: Int,
    glow: Float,
    grain: Boolean,
    store: ArtworkStore,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    ScreenFrame("queue", glow, grain, onBack, scroll = false, trailing = {
        Text("clear", style = italicLabel(14.sp, GadgetColors.soft), modifier = Modifier.clickable(onClick = onClear))
    }) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { index, row -> "$index-${row.uri}" }) { index, row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(index) }.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtDisc(row.uri, 40.dp, 120, store)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(row.title, style = italicLabel(15.sp, if (index == currentIndex) GadgetColors.text else GadgetColors.soft))
                        Text(row.artist, style = sans(12.sp, GadgetColors.dim))
                    }
                    Text("up", style = sans(12.sp), modifier = Modifier.clickable { if (index > 0) onMove(index, index - 1) }.padding(6.dp))
                    Text("×", style = italicLabel(16.sp), modifier = Modifier.clickable { onRemove(index) }.padding(6.dp))
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    store: ArtworkStore,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.uri == null) return
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = bottom + 10.dp)
            .fillMaxWidth()
            .height(58.dp)
            .glass(29.dp)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtDisc(state.uri, 42.dp, 160, store)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(state.title, style = italicLabel(14.sp), maxLines = 1)
            Text(state.artist, style = sans(11.sp, GadgetColors.dim), maxLines = 1)
        }
        GlassOrb(40.dp, onToggle) {
            Glyph(if (state.playing) GlyphKind.Pause else GlyphKind.Play, Modifier.size(16.dp))
        }
    }
}


