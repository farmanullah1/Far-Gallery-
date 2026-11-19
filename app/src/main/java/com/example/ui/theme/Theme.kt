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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

enum class ThemeMode {
    System, Light, Dark, Amoled;

    fun next(): ThemeMode = when (this) {
        System -> Light
        Light -> Dark
        Dark -> Amoled
        Amoled -> System
    }

    fun labelRes(): Int = when (this) {
        System -> R.string.theme_system
        Light -> R.string.theme_light
        Dark -> R.string.theme_dark
        Amoled -> R.string.theme_amoled
    }
}

object FarSpacing {
    val screenPadding: Dp = 16.dp
    val sectionGap: Dp = 12.dp
    val itemGap: Dp = 8.dp
    val tightGap: Dp = 4.dp
    val minTouch: Dp = 48.dp
    val bottomNavHeight: Dp = 56.dp
    val bottomDockClearance: Dp = 96.dp
}

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

@Immutable
data class FarSemanticColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val cardBorder: Color,
    val elevatedSurface: Color,
)

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
val LocalSemanticColors = compositionLocalOf { lightSemanticColors() }

private fun lightSemanticColors() = FarSemanticColors(
    success = Color(0xFF1B873B),
    successContainer = Color(0xFFE6F4EA),
    warning = Color(0xFFA85700),
    warningContainer = Color(0xFFFEF3E0),
    info = Color(0xFF0A66C2),
    cardBorder = Color(0x1C000000),
    elevatedSurface = Color(0xFFFFFFFF),
)

private fun darkSemanticColors(amoled: Boolean) = FarSemanticColors(
    success = Color(0xFF34C759),
    successContainer = Color(0xFF132E1C),
    warning = Color(0xFFFFB340),
    warningContainer = Color(0xFF2D210B),
    info = Color(0xFF64B5F6),
    cardBorder = if (amoled) Color(0x36FFFFFF) else Color(0x28FFFFFF),
    elevatedSurface = if (amoled) Color(0xFF0E0E12) else Color(0xFF1B1B20),
)

private fun lightGlass() = GlassTokens(
    tint = Color(0xFFFFFFFF).copy(alpha = 0.80f),
    tintStrong = Color(0xFFFFFFFF).copy(alpha = 0.92f),
    borderTop = Color.White.copy(alpha = 0.98f),
    borderBottom = Color.Black.copy(alpha = 0.14f),
    fallback = Color(0xFFF6F7FA),
    blur = 24.dp,
)

private fun darkGlass(amoled: Boolean = false) = GlassTokens(
    tint = (if (amoled) Color(0xFF101015) else Color(0xFF1B1B21)).copy(alpha = if (amoled) 0.84f else 0.78f),
    tintStrong = (if (amoled) Color(0xFF14141A) else Color(0xFF1E1E26)).copy(alpha = 0.92f),
    borderTop = Color.White.copy(alpha = if (amoled) 0.50f else 0.42f),
    borderBottom = Color.White.copy(alpha = if (amoled) 0.16f else 0.14f),
    fallback = if (amoled) Color(0xFF101014) else Color(0xFF1E1E24),
    blur = 24.dp,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f),
    onPrimaryContainer = accent,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF0B0B0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0B0F),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF4A4A52),
    outlineVariant = Color(0x20000000),
    error = Color(0xFFD93025),
    onError = Color.White,
)

private fun darkScheme(accent: Color, amoled: Boolean) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.24f),
    onPrimaryContainer = Color.White,
    background = if (amoled) Color.Black else Color(0xFF0B0B0F),
    onBackground = Color.White,
    surface = if (amoled) Color(0xFF0B0B0E) else Color(0xFF17171B),
    onSurface = Color.White,
    surfaceVariant = if (amoled) Color(0xFF16161B) else Color(0xFF24242A),
    onSurfaceVariant = if (amoled) Color(0xFFC2C2CA) else Color(0xFFBDBDC5),
    outlineVariant = if (amoled) Color(0x3EFFFFFF) else Color(0x32FFFFFF),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

internal fun enforceThemeContrast(base: ColorScheme, dark: Boolean, amoled: Boolean): ColorScheme = when {
    !dark -> base.copy(
        onBackground = if (base.onBackground.luminance() > 0.35f) Color(0xFF0B0B0F) else base.onBackground,
        onSurface = if (base.onSurface.luminance() > 0.35f) Color(0xFF0B0B0F) else base.onSurface,
        onSurfaceVariant = if (base.onSurfaceVariant.luminance() > 0.45f) Color(0xFF4A4A52) else base.onSurfaceVariant,
    )
    amoled -> base.copy(
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF0B0B0E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF16161B),
        onSurfaceVariant = Color(0xFFC2C2CA),
        outlineVariant = Color(0x3EFFFFFF),
    )
    else -> base.copy(
        onBackground = if (base.onBackground.luminance() < 0.7f) Color.White else base.onBackground,
        onSurface = if (base.onSurface.luminance() < 0.7f) Color.White else base.onSurface,
        onSurfaceVariant = if (base.onSurfaceVariant.luminance() < 0.45f) Color(0xFFBDBDC5) else base.onSurfaceVariant,
        outlineVariant = if (base.outlineVariant.alpha < 0.16f) Color(0x32FFFFFF) else base.outlineVariant,
    )
}

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
    val amoled = dark && mode == ThemeMode.Amoled
    val ctx = LocalContext.current
    val baseScheme: ColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkScheme(accent, amoled)
        else -> lightScheme(accent)
    }
    val scheme = enforceThemeContrast(baseScheme, dark, amoled)

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
        LocalGlass provides (if (dark) darkGlass(amoled) else lightGlass()),
        LocalReducedMotion provides reduceMotion,
        LocalReduceTransparency provides reduceTransparency,
        LocalIsDark provides dark,
        LocalHapticsEnabled provides hapticsEnabled,
        LocalSemanticColors provides (if (dark) darkSemanticColors(amoled) else lightSemanticColors()),
        LocalContentColor provides scheme.onBackground,
    ) {
        MaterialTheme(colorScheme = scheme, typography = FarTypography, shapes = FarShapes) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = scheme.background,
                contentColor = scheme.onBackground,
                content = content,
            )
        }
    }
}
