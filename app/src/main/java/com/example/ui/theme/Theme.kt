package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.R

enum class ThemeMode { System, Light, Dark, Amoled }

object FarPalette {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Pink = Color(0xFFFF375F)
    val Purple = Color(0xFFBF5AF2)
    val CameraYellow = Color(0xFFFFD60A)
    val RecordRed = Color(0xFFFF3B30)
    val curated = listOf(Blue, Green, Orange, Pink, Purple)
}

/** Glass tokens consumed by Modifier.glass(). */
@Immutable
data class GlassTokens(
    val tint: Color,
    val tintStrong: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val fallback: Color,
    val blur: Dp,
)

val LocalGlass = compositionLocalOf { lightGlass() }
val LocalReducedMotion = compositionLocalOf { false }
val LocalReduceTransparency = compositionLocalOf { false }
val LocalIsDark = compositionLocalOf { false }
val LocalHapticsEnabled = compositionLocalOf { true }

private fun lightGlass() = GlassTokens(
    tint = Color(0xFFFFFFFF).copy(alpha = 0.62f),
    tintStrong = Color(0xFFFFFFFF).copy(alpha = 0.78f),
    borderTop = Color.White.copy(alpha = 0.92f),
    borderBottom = Color.Black.copy(alpha = 0.10f),
    fallback = Color(0xFFF6F6FA),
    blur = 24.dp,
)

private fun darkGlass() = GlassTokens(
    tint = Color(0xFF1C1C1E).copy(alpha = 0.60f),
    tintStrong = Color(0xFF1C1C1E).copy(alpha = 0.78f),
    borderTop = Color.White.copy(alpha = 0.42f),
    borderBottom = Color.White.copy(alpha = 0.08f),
    fallback = Color(0xFF232326),
    blur = 24.dp,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f),
    onPrimaryContainer = accent,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B0B0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0B0F),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF636366),
    outlineVariant = Color(0x1F000000),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private fun darkScheme(accent: Color, amoled: Boolean) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.22f),
    onPrimaryContainer = Color.White,
    background = if (amoled) Color.Black else Color(0xFF0B0B0F),
    onBackground = Color.White,
    surface = if (amoled) Color(0xFF080808) else Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = if (amoled) Color(0xFF141414) else Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outlineVariant = Color(0x33FFFFFF),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

private val DisplayFont = FontFamily(Font(R.font.space_grotesk, FontWeight.SemiBold), Font(R.font.space_grotesk, FontWeight.Bold))
private val BodyFont = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal), Font(R.font.plus_jakarta_sans, FontWeight.Medium), Font(R.font.plus_jakarta_sans, FontWeight.SemiBold))
val MonoFont = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private val FarTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val FarShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spring for movement/size, short tween for opacity. */
object FarMotion {
    fun <T> spatial(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) tween(80) else spring(dampingRatio = 0.82f, stiffness = 380f)

    fun <T> effects(reduced: Boolean = false): FiniteAnimationSpec<T> =
        tween(if (reduced) 60 else 180, easing = FastOutSlowInEasing)
}

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun FarTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Color = FarPalette.Blue,
    useDynamicColor: Boolean = false,
    reduceMotion: Boolean = rememberSystemReducedMotion(),
    reduceTransparency: Boolean = false,
    hapticsEnabled: Boolean = true,
    forceDarkSystemBars: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkScheme(accent, mode == ThemeMode.Amoled)
        else -> lightScheme(accent)
    }.let { if (dark && mode == ThemeMode.Amoled) it.copy(background = Color.Black, surface = Color(0xFF080808)) else it }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            ctx.findActivity()?.window?.let { window ->
                val lightIcons = !(forceDarkSystemBars ?: dark)
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = lightIcons
                controller.isAppearanceLightNavigationBars = lightIcons
            }
        }
    }

    CompositionLocalProvider(
        LocalGlass provides (if (dark) darkGlass() else lightGlass()),
        LocalReducedMotion provides reduceMotion,
        LocalReduceTransparency provides reduceTransparency,
        LocalIsDark provides dark,
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(colorScheme = scheme, typography = FarTypography, shapes = FarShapes, content = content)
    }
}
