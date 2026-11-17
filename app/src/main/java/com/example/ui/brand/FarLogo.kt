package com.example.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion

/**
 * In-app logo: dusk-gradient rounded square, sun and two mountain ridges.
 * Plays once on first composition (sun rises, ridges slide in); static when reduced motion is on.
 */
@Composable
fun FarLogo(modifier: Modifier = Modifier, size: Dp = 120.dp, animate: Boolean = true) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (animate && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate && !reduced) progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Canvas(modifier.size(size)) {
        val w = this.size.width
        val p = progress.value
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF5B6CFF), Color(0xFFB45CFF), Color(0xFFFF8A65))),
            cornerRadius = CornerRadius(w * 0.24f),
            size = Size(w, w),
        )
        // sun rises from behind the ridge
        drawCircle(Color.White.copy(alpha = 0.95f), radius = w * 0.11f, center = Offset(w * 0.68f, w * (0.50f - 0.12f * p)))
        // back ridge
        val back = Path().apply {
            moveTo(0f, w * 0.80f)
            lineTo(w * 0.30f, w * (0.80f - 0.30f * p))
            lineTo(w * 0.52f, w * 0.72f)
            lineTo(w * 0.74f, w * (0.80f - 0.22f * p))
            lineTo(w, w * 0.78f)
            lineTo(w, w)
            lineTo(0f, w)
            close()
        }
        drawPath(back, Color.White.copy(alpha = 0.55f))
        // front ridge
        val front = Path().apply {
            moveTo(0f, w * 0.90f)
            lineTo(w * 0.42f, w * (0.90f - 0.36f * p))
            lineTo(w, w * 0.88f)
            lineTo(w, w)
            lineTo(0f, w)
            close()
        }
        drawPath(front, Color.White)
    }
}
