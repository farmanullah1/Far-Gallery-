package com.example.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalGlass
import com.example.ui.theme.LocalReduceTransparency

/**
 * Minimal frosted translucent glass backed by pure Compose gradients, specular hairline borders,
 * and surface fallback when reduce-transparency is enabled.
 */
enum class GlassKind { Bar, Chip, Sheet, Overlay }

@Immutable
object FarGlassState

@Composable
fun rememberGlass(): FarGlassState = remember { FarGlassState }

fun Modifier.glassSource(state: FarGlassState): Modifier = this

@Composable
fun Modifier.glass(
    state: FarGlassState,
    shape: Shape,
    kind: GlassKind = GlassKind.Bar,
    forceFallback: Boolean = false,
    elevated: Boolean = true,
): Modifier {
    val g = LocalGlass.current
    val reduce = LocalReduceTransparency.current || forceFallback
    val border = Brush.verticalGradient(listOf(g.borderTop, g.borderBottom))
    val base = if (elevated) {
        this.shadow(10.dp, shape, ambientColor = Color.Black.copy(0.12f), spotColor = Color.Black.copy(0.12f))
    } else {
        this
    }

    if (reduce) {
        return base.clip(shape)
            .background(g.fallback.copy(alpha = 0.96f))
            .border(0.75.dp, border, shape)
    }

    val surfaceBrush = when (kind) {
        GlassKind.Sheet, GlassKind.Overlay -> Brush.verticalGradient(
            listOf(g.tintStrong.copy(alpha = 0.96f), g.fallback.copy(alpha = 0.92f))
        )
        GlassKind.Chip -> Brush.verticalGradient(
            listOf(g.tintStrong.copy(alpha = 0.90f), g.tint.copy(alpha = 0.86f))
        )
        GlassKind.Bar -> Brush.verticalGradient(
            listOf(g.tintStrong.copy(alpha = 0.93f), g.tint.copy(alpha = 0.88f))
        )
    }

    return base.clip(shape)
        .background(surfaceBrush)
        .border(0.75.dp, border, shape)
}

@Composable
fun GlassSurface(
    state: FarGlassState,
    shape: Shape,
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Bar,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(state, shape, kind), content = content)
}
