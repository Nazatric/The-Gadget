package com.nazatric.thegadget.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.ui.theme.GadgetColors
import com.nazatric.thegadget.ui.theme.italicLabel
import com.nazatric.thegadget.ui.theme.sans

fun Modifier.glass(radius: Dp = 28.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape)
        .background(GadgetColors.glass)
        .border(0.7.dp, GadgetColors.hair, shape)
}

@Composable
fun GlassOrb(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glow = if (active || pressed) 0.34f else 0.16f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = glow), Color.Transparent),
                    center = center,
                    radius = this.size.minDimension * 0.72f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.02f)),
                    center = Offset(center.x - this.size.minDimension * 0.12f, center.y - this.size.minDimension * 0.16f),
                    radius = this.size.minDimension * 0.7f,
                ),
                radius = this.size.minDimension * 0.42f,
            )
            drawCircle(
                color = Color.White.copy(alpha = if (active) 0.85f else 0.42f),
                radius = this.size.minDimension * 0.40f,
                style = Stroke(width = 1.2f),
            )
        }
        content()
    }
}

@Composable
fun StatusPill(
    username: String,
    online: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(46.dp)
            .glass(23.dp)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.16f))
                drawCircle(Color.White.copy(alpha = 0.7f), style = Stroke(1.1f), radius = size.minDimension * 0.42f)
            }
            Text(
                username.take(1).uppercase(),
                style = italicLabel(13.sp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(username, style = italicLabel(16.sp))
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.size(7.dp)) {
            drawCircle(Color.White.copy(alpha = if (online) 0.95f else 0.35f))
        }
    }
}

@Composable
fun Glyph(
    kind: GlyphKind,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
        when (kind) {
            GlyphKind.Back -> {
                val path = Path().apply {
                    moveTo(s * 0.62f, s * 0.22f)
                    lineTo(s * 0.34f, s * 0.50f)
                    lineTo(s * 0.62f, s * 0.78f)
                }
                drawPath(path, tint, style = stroke)
            }
            GlyphKind.Play -> {
                val path = Path().apply {
                    moveTo(s * 0.38f, s * 0.26f)
                    lineTo(s * 0.38f, s * 0.74f)
                    lineTo(s * 0.74f, s * 0.50f)
                    close()
                }
                drawPath(path, tint)
            }
            GlyphKind.Pause -> {
                drawRoundRectish(tint, s * 0.30f, s * 0.24f, s * 0.12f, s * 0.52f)
                drawRoundRectish(tint, s * 0.56f, s * 0.24f, s * 0.12f, s * 0.52f)
            }
            GlyphKind.Next -> drawSkip(tint, s, forward = true)
            GlyphKind.Previous -> drawSkip(tint, s, forward = false)
            GlyphKind.Search -> {
                drawCircle(tint, s * 0.22f, Offset(s * 0.42f, s * 0.42f), style = stroke)
                drawLine(tint, Offset(s * 0.58f, s * 0.58f), Offset(s * 0.76f, s * 0.76f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            }
            GlyphKind.Heart -> {
                val path = Path().apply {
                    moveTo(s * 0.50f, s * 0.74f)
                    cubicTo(s * 0.18f, s * 0.52f, s * 0.20f, s * 0.28f, s * 0.38f, s * 0.28f)
                    cubicTo(s * 0.46f, s * 0.28f, s * 0.50f, s * 0.36f, s * 0.50f, s * 0.36f)
                    cubicTo(s * 0.50f, s * 0.36f, s * 0.54f, s * 0.28f, s * 0.62f, s * 0.28f)
                    cubicTo(s * 0.80f, s * 0.28f, s * 0.82f, s * 0.52f, s * 0.50f, s * 0.74f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            GlyphKind.HeartFill -> {
                val path = Path().apply {
                    moveTo(s * 0.50f, s * 0.76f)
                    cubicTo(s * 0.16f, s * 0.52f, s * 0.18f, s * 0.26f, s * 0.38f, s * 0.26f)
                    cubicTo(s * 0.46f, s * 0.26f, s * 0.50f, s * 0.36f, s * 0.50f, s * 0.36f)
                    cubicTo(s * 0.50f, s * 0.36f, s * 0.54f, s * 0.26f, s * 0.62f, s * 0.26f)
                    cubicTo(s * 0.82f, s * 0.26f, s * 0.84f, s * 0.52f, s * 0.50f, s * 0.76f)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectish(color: Color, l: Float, t: Float, w: Float, h: Float) {
    drawRoundRect(color = color, topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkip(tint: Color, s: Float, forward: Boolean) {
    val path = Path()
    if (forward) {
        path.moveTo(s * 0.28f, s * 0.28f)
        path.lineTo(s * 0.28f, s * 0.72f)
        path.lineTo(s * 0.58f, s * 0.50f)
        path.close()
        drawPath(path, tint)
        drawLine(tint, Offset(s * 0.66f, s * 0.28f), Offset(s * 0.66f, s * 0.72f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    } else {
        path.moveTo(s * 0.72f, s * 0.28f)
        path.lineTo(s * 0.72f, s * 0.72f)
        path.lineTo(s * 0.42f, s * 0.50f)
        path.close()
        drawPath(path, tint)
        drawLine(tint, Offset(s * 0.32f, s * 0.28f), Offset(s * 0.32f, s * 0.72f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
}

enum class GlyphKind { Back, Play, Pause, Next, Previous, Search, Heart, HeartFill }

@Composable
fun GadgetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = italicLabel(16.sp),
        cursorBrush = SolidColor(Color.White),
        singleLine = true,
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.glass(18.dp).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (value.isEmpty()) Text(placeholder, style = sans(15.sp, GadgetColors.dim))
                inner()
            }
        },
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(0.6.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                ),
            ),
    )
}
