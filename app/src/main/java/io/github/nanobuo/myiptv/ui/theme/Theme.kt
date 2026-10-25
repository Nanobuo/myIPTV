package io.github.nanobuo.myiptv.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.tv.material3.Glow
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemGlow
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.data.AppSettings
import androidx.compose.material3.ColorScheme as M3ColorScheme
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme as m3LightColorScheme
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme

/** Available theme colors: key, label and hue of the seed color. */
enum class ThemeColor(val key: String, val label: Int, val hue: Float) {
    DYNAMIC(AppSettings.THEME_DYNAMIC, R.string.theme_dynamic, 222f),
    BLUE("blue", R.string.theme_blue, 214f),
    GREEN("green", R.string.theme_green, 150f),
    PURPLE("purple", R.string.theme_purple, 275f),
    ORANGE("orange", R.string.theme_orange, 28f),
    RED("red", R.string.theme_red, 355f);

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: DYNAMIC
    }
}

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** True when the light (Apple TV-like) appearance is in use. */
val LocalIsLightTheme = staticCompositionLocalOf { false }

/** Light theme focus looks like tvOS: a soft shadow under the focused white item. */
@Composable
fun appListItemGlow(): ListItemGlow = if (LocalIsLightTheme.current) {
    ListItemDefaults.glow(focusedGlow = Glow(elevationColor = Color(0x40000000), elevation = 10.dp))
} else {
    ListItemDefaults.glow()
}

/** Tone (0-100) of a hue, an approximation of the Material 3 tonal palettes. */
private fun tone(hue: Float, saturation: Float, tone: Int): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, tone / 100f)))

private fun seededDarkScheme(hue: Float): M3ColorScheme {
    val tertiaryHue = (hue + 60f) % 360f
    return m3DarkColorScheme(
        primary = tone(hue, 0.85f, 80),
        onPrimary = tone(hue, 0.85f, 20),
        primaryContainer = tone(hue, 0.6f, 30),
        onPrimaryContainer = tone(hue, 0.9f, 90),
        inversePrimary = tone(hue, 0.7f, 40),
        secondary = tone(hue, 0.3f, 80),
        onSecondary = tone(hue, 0.3f, 20),
        secondaryContainer = tone(hue, 0.25f, 30),
        onSecondaryContainer = tone(hue, 0.4f, 90),
        tertiary = tone(tertiaryHue, 0.5f, 80),
        onTertiary = tone(tertiaryHue, 0.5f, 20),
        tertiaryContainer = tone(tertiaryHue, 0.4f, 30),
        onTertiaryContainer = tone(tertiaryHue, 0.6f, 90),
        background = tone(hue, 0.12f, 7),
        onBackground = tone(hue, 0.1f, 90),
        surface = tone(hue, 0.12f, 7),
        onSurface = tone(hue, 0.1f, 90),
        surfaceVariant = tone(hue, 0.12f, 22),
        onSurfaceVariant = tone(hue, 0.12f, 80),
        surfaceTint = tone(hue, 0.85f, 80),
        inverseSurface = tone(hue, 0.1f, 90),
        inverseOnSurface = tone(hue, 0.1f, 20),
        outline = tone(hue, 0.08f, 60),
        outlineVariant = tone(hue, 0.1f, 30),
        surfaceContainerLowest = tone(hue, 0.12f, 4),
        surfaceContainerLow = tone(hue, 0.12f, 10),
        surfaceContainer = tone(hue, 0.12f, 12),
        surfaceContainerHigh = tone(hue, 0.12f, 17),
        surfaceContainerHighest = tone(hue, 0.12f, 22),
    )
}

private fun seededLightScheme(hue: Float): M3ColorScheme {
    val tertiaryHue = (hue + 60f) % 360f
    return m3LightColorScheme(
        primary = tone(hue, 0.85f, 45),
        onPrimary = Color.White,
        primaryContainer = tone(hue, 0.9f, 90),
        onPrimaryContainer = tone(hue, 0.8f, 15),
        inversePrimary = tone(hue, 0.85f, 80),
        secondary = tone(hue, 0.25f, 40),
        onSecondary = Color.White,
        secondaryContainer = tone(hue, 0.4f, 88),
        onSecondaryContainer = tone(hue, 0.4f, 15),
        tertiary = tone(tertiaryHue, 0.5f, 40),
        onTertiary = Color.White,
        tertiaryContainer = tone(tertiaryHue, 0.6f, 90),
        onTertiaryContainer = tone(tertiaryHue, 0.6f, 15),
    )
}

/**
 * Neutral tones inspired by the tvOS light appearance: a soft light-gray canvas,
 * near-white panels and pure white focused items, with almost-black text.
 */
private fun appleLightNeutrals(scheme: M3ColorScheme): M3ColorScheme = scheme.copy(
    background = Color(0xFFE8E8ED),
    onBackground = Color(0xFF1D1D1F),
    surface = Color(0xFFF2F2F5),
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFDCDCE1),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceTint = Color.Transparent,
    inverseSurface = Color.White,
    inverseOnSurface = Color(0xFF1D1D1F),
    outline = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFFD1D1D6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainer = Color(0xFFF2F2F5),
    surfaceContainerHigh = Color(0xFFECECF0),
    surfaceContainerHighest = Color(0xFFE5E5EA),
)

@Composable
fun MyIptvTheme(themeColor: String, light: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val selected = ThemeColor.from(themeColor)
    val dynamic = selected == ThemeColor.DYNAMIC && supportsDynamicColor
    val m3 = when {
        light && dynamic -> appleLightNeutrals(dynamicLightColorScheme(context))
        light -> appleLightNeutrals(seededLightScheme(selected.hue))
        dynamic -> dynamicDarkColorScheme(context)
        else -> seededDarkScheme(selected.hue)
    }
    val base = if (light) tvLightColorScheme() else tvDarkColorScheme()
    val tv = base.copy(
        primary = m3.primary,
        onPrimary = m3.onPrimary,
        primaryContainer = m3.primaryContainer,
        onPrimaryContainer = m3.onPrimaryContainer,
        inversePrimary = m3.inversePrimary,
        secondary = m3.secondary,
        onSecondary = m3.onSecondary,
        secondaryContainer = m3.secondaryContainer,
        onSecondaryContainer = m3.onSecondaryContainer,
        tertiary = m3.tertiary,
        onTertiary = m3.onTertiary,
        tertiaryContainer = m3.tertiaryContainer,
        onTertiaryContainer = m3.onTertiaryContainer,
        background = m3.background,
        onBackground = m3.onBackground,
        surface = if (light) m3.surface else m3.surfaceContainer,
        onSurface = m3.onSurface,
        surfaceVariant = if (light) m3.surfaceVariant else m3.surfaceContainerHighest,
        onSurfaceVariant = m3.onSurfaceVariant,
        surfaceTint = m3.surfaceTint,
        inverseSurface = m3.inverseSurface,
        inverseOnSurface = m3.inverseOnSurface,
        error = m3.error,
        onError = m3.onError,
        errorContainer = m3.errorContainer,
        onErrorContainer = m3.onErrorContainer,
        border = m3.outline,
        borderVariant = m3.outlineVariant,
        scrim = m3.scrim,
    )
    CompositionLocalProvider(LocalIsLightTheme provides light) {
        M3Theme(colorScheme = m3) {
            TvTheme(colorScheme = tv, content = content)
        }
    }
}
