package com.example.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Glass = backdrop blur (Haze, RenderEffect on API 31+) + translucent tint + hairline border.
 * API 30 / reduce-transparency / low-power => opaque tint fallback with identical layout.
 */
enum class GlassKind { Bar, Chip, Sheet, Overlay }

@Composable
fun rememberGlass(): HazeState = remember { HazeState() }

fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

@Composable
fun Modifier.glass(
    state: HazeState,
    shape: Shape,
    kind: GlassKind = GlassKind.Bar,
    forceFallback: Boolean = false,
    elevated: Boolean = true,
): Modifier {
    val g = LocalGlass.current
    val reduce = LocalReduceTransparency.current || forceFallback
    val border = Brush.verticalGradient(listOf(g.borderTop, g.borderBottom))
    val base = if (elevated) {
        this.shadow(12.dp, shape, ambientColor = Color.Black.copy(0.12f), spotColor = Color.Black.copy(0.12f))
    } else {
        this
    }

    if (reduce) {
        return base.clip(shape)
            .background(g.fallback.copy(alpha = 0.96f))
            .border(0.75.dp, border, shape)
    }

    val radius = when (kind) {
        GlassKind.Bar -> g.blur
        GlassKind.Chip -> g.blur * 0.7f
        GlassKind.Sheet -> g.blur * 1.4f
        GlassKind.Overlay -> g.blur * 1.8f
    }
    val tint = when (kind) {
        GlassKind.Sheet, GlassKind.Overlay -> g.tintStrong
        else -> g.tint
    }
    return base.clip(shape)
        .hazeEffect(state) {
            blurRadius = radius
            tints = listOf(HazeTint(tint))
            noiseFactor = 0.02f
            fallbackTint = HazeTint(g.fallback.copy(alpha = 0.94f))
        }
        .border(0.75.dp, border, shape)
}

@Composable
fun GlassSurface(
    state: HazeState,
    shape: Shape,
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Bar,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(state, shape, kind), content = content)
}
