package com.nazatric.thegadget.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.ui.components.GlassOrb
import com.nazatric.thegadget.ui.components.Glyph
import com.nazatric.thegadget.ui.components.GlyphKind
import com.nazatric.thegadget.ui.theme.GadgetBackdrop
import com.nazatric.thegadget.ui.theme.italicLabel

@Composable
fun ScreenFrame(
    title: String,
    glow: Float,
    grain: Boolean,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable () -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GadgetBackdrop(glow = glow, grain = grain) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(top = top + 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassOrb(size = 44.dp, onClick = onBack) {
                    Glyph(GlyphKind.Back, Modifier.size(18.dp))
                }
                Text(
                    title,
                    style = italicLabel(30.sp),
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                )
                trailing()
            }
            if (scroll) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottom + 28.dp),
                ) { content() }
            } else {
                Column(Modifier.weight(1f).padding(bottom = bottom)) { content() }
            }
        }
    }
}
