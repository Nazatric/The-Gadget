package com.nazatric.thegadget.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.R
import com.nazatric.thegadget.ui.components.GlassOrb
import com.nazatric.thegadget.ui.components.StatusPill
import com.nazatric.thegadget.ui.theme.GadgetBackdrop
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import kotlin.math.cos
import kotlin.math.sin

data class HubNode(val label: String, val route: String)

private val nodes = listOf(
    HubNode("features", "features"),
    HubNode("config", "config"),
    HubNode("music", "music"),
    HubNode("games", "games"),
    HubNode("account", "account"),
    HubNode("username", "username"),
)

@Composable
fun HomeScreen(
    username: String,
    glow: Float,
    grain: Boolean,
    reduceMotion: Boolean,
    playing: Boolean,
    onOpen: (String) -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "float")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    GadgetBackdrop(glow = glow, grain = grain) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val w = maxWidth
            val h = maxHeight
            val sphere = minOf(minOf(w, h) * 0.78f, 460.dp)
            val cx = w / 2
            val cy = h * 0.47f
            val floatY = if (reduceMotion) 0.dp else ((drift - 0.5f) * 8f).dp
            Image(
                painter = painterResource(R.drawable.chrome_sphere),
                contentDescription = "home",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(sphere)
                    .offset(x = cx - sphere / 2, y = cy - sphere / 2 + floatY)
                    .semantics { contentDescription = "home" },
            )
            Text(
                "home",
                style = italicLabel(13.sp, GadgetColors.soft),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = cx - 18.dp, y = cy + sphere * 0.16f + floatY)
                    .alpha(0.9f),
            )
            val orbit = sphere * 0.40f
            nodes.forEachIndexed { index, node ->
                val angle = Math.toRadians((-90.0 + index * 60.0))
                val phase = if (reduceMotion) 0f else sin((drift * 6.28f) + index).toFloat()
                val nx = cx + (cos(angle).toFloat() * orbit.value).dp + (phase * 1.5f).dp
                val ny = cy + (sin(angle).toFloat() * orbit.value * 0.98f).dp + floatY * 0.35f
                val nodeSize = 54.dp
                GlassOrb(
                    size = nodeSize,
                    onClick = { onOpen(node.route) },
                    modifier = Modifier.offset(x = nx - nodeSize / 2, y = ny - nodeSize / 2),
                    active = node.route == "music" && playing,
                )
                val outwardX = cos(angle).toFloat()
                val outwardY = sin(angle).toFloat()
                Text(
                    node.label,
                    style = italicLabel(13.sp),
                    modifier = Modifier.offset(
                        x = nx + (outwardX * 36f).dp - 28.dp,
                        y = ny + (outwardY * 34f).dp + 8.dp,
                    ),
                )
            }
            StatusPill(
                username = username,
                online = true,
                onClick = { onOpen("username") },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 22.dp, top = topInset + 12.dp),
            )
            Text(
                "the gadget",
                style = italicLabel(12.sp, GadgetColors.dim),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 16.dp)
                    .alpha(0.8f),
            )
        }
    }
}
