package com.nazatric.thegadget.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nazatric.thegadget.R
import kotlin.random.Random

object GadgetColors {
    val black = Color(0xFF000000)
    val text = Color(0xFFF7F7F8)
    val soft = Color(0xFFC5C7CE)
    val dim = Color(0xFF8C909A)
    val hair = Color.White.copy(alpha = 0.28f)
    val glass = Color.White.copy(alpha = 0.075f)
    val glassStrong = Color.White.copy(alpha = 0.12f)
}

val GadgetSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val GadgetSans = FontFamily(
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_medium, FontWeight.Medium),
)

fun italicLabel(size: androidx.compose.ui.unit.TextUnit = 15.sp, color: Color = GadgetColors.text) = TextStyle(
    fontFamily = GadgetSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    letterSpacing = 0.6.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

fun sans(size: androidx.compose.ui.unit.TextUnit = 14.sp, color: Color = GadgetColors.soft, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = GadgetSans,
    fontWeight = weight,
    fontSize = size,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val scheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    primary = Color(0xFFF4F4F5),
    onPrimary = Color.Black,
    onBackground = Color(0xFFF7F7F8),
    onSurface = Color(0xFFF7F7F8),
)

@Composable
fun GadgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun GadgetBackdrop(
    glow: Float,
    grain: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val specks = remember {
        val rnd = Random(11)
        List(220) { Offset(rnd.nextFloat(), rnd.nextFloat()) to rnd.nextFloat() }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A2C33).copy(alpha = 0.55f * glow),
                        Color(0xFF0A0B0E).copy(alpha = 0.85f),
                        Color.Black,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.40f),
                    radius = size.maxDimension * 0.78f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f * glow), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.46f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.5f, size.height * 0.46f),
            )
            if (grain) {
                specks.forEach { (p, a) ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.035f * a),
                        radius = 0.7f,
                        center = Offset(p.x * size.width, p.y * size.height),
                    )
                }
            }
        }
        content()
    }
}
