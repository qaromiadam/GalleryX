package com.Gallery.X.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.Gallery.X.data.GallerySettings
import kotlin.math.abs

val GalleryXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val DefaultLightScheme = lightColorScheme(
    primary = StudioLightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = StudioLightTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = StudioLightSurfaceVariant,
    onSecondaryContainer = StudioLightTextPrimary,
    background = StudioLightBackground,
    onBackground = StudioLightTextPrimary,
    surface = StudioLightSurface,
    onSurface = StudioLightTextPrimary,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = StudioLightTextSecondary,
    surfaceContainerLow = StudioLightSurface,
    surfaceContainer = StudioLightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE3E4DF),
    outline = StudioLightOutline,
    outlineVariant = Color(0xFFE5E7EB),
    error = StudioError,
    onError = Color.White
)

private val DefaultDarkScheme = darkColorScheme(
    primary = StudioDarkAccent,
    onPrimary = Color(0xFF0A1429),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = StudioDarkTextSecondary,
    onSecondary = Color(0xFF111318),
    secondaryContainer = StudioDarkSurfaceVariant,
    onSecondaryContainer = StudioDarkTextPrimary,
    background = StudioDarkBackground,
    onBackground = StudioDarkTextPrimary,
    surface = StudioDarkSurface,
    onSurface = StudioDarkTextPrimary,
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = StudioDarkTextSecondary,
    surfaceContainerLow = StudioDarkSurface,
    surfaceContainer = StudioDarkSurfaceVariant,
    surfaceContainerHigh = Color(0xFF2B303D),
    outline = StudioDarkOutline,
    outlineVariant = Color(0xFF232834),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0A0A)
)

fun buildCustomColorScheme(
    bgColor: Color,
    textColor: Color,
    accentColor: Color
): ColorScheme {
    val isDarkBg = bgColor.luminance() < 0.4f
    val surfaceColor = shiftColor(bgColor, if (isDarkBg) 0.06f else -0.02f)
    val surfaceVariantColor = shiftColor(bgColor, if (isDarkBg) 0.12f else -0.06f)
    val surfaceHighColor = shiftColor(bgColor, if (isDarkBg) 0.17f else -0.10f)
    val secondaryText = textColor.copy(alpha = 0.70f)
    val onAccent = if (accentColor.luminance() > 0.52f) Color(0xFF0B0E14) else Color.White
    val primaryContainer = if (isDarkBg) {
        blendColors(accentColor, bgColor, 0.72f)
    } else {
        blendColors(accentColor, Color.White, 0.82f)
    }
    val onPrimaryContainer = if (primaryContainer.luminance() > 0.5f) {
        Color(0xFF0F172A)
    } else {
        Color(0xFFF8FAFC)
    }
    val outlineColor = textColor.copy(alpha = 0.18f)

    return if (isDarkBg) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryText,
            onSecondary = bgColor,
            secondaryContainer = surfaceVariantColor,
            onSecondaryContainer = textColor,
            background = bgColor,
            onBackground = textColor,
            surface = surfaceColor,
            onSurface = textColor,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = secondaryText,
            surfaceContainerLow = surfaceColor,
            surfaceContainer = surfaceVariantColor,
            surfaceContainerHigh = surfaceHighColor,
            outline = outlineColor,
            outlineVariant = textColor.copy(alpha = 0.10f),
            error = Color(0xFFF87171),
            onError = Color(0xFF2B0A0A)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryText,
            onSecondary = Color.White,
            secondaryContainer = surfaceVariantColor,
            onSecondaryContainer = textColor,
            background = bgColor,
            onBackground = textColor,
            surface = surfaceColor,
            onSurface = textColor,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = secondaryText,
            surfaceContainerLow = surfaceColor,
            surfaceContainer = surfaceVariantColor,
            surfaceContainerHigh = surfaceHighColor,
            outline = outlineColor,
            outlineVariant = textColor.copy(alpha = 0.10f),
            error = StudioError,
            onError = Color.White
        )
    }
}

private fun shiftColor(color: Color, delta: Float): Color {
    val r = (color.red + delta).coerceIn(0f, 1f)
    val g = (color.green + delta).coerceIn(0f, 1f)
    val b = (color.blue + delta).coerceIn(0f, 1f)
    return Color(r, g, b, color.alpha)
}

private fun blendColors(foreground: Color, background: Color, bgWeight: Float): Color {
    val fgWeight = 1f - bgWeight
    return Color(
        red = (foreground.red * fgWeight + background.red * bgWeight).coerceIn(0f, 1f),
        green = (foreground.green * fgWeight + background.green * bgWeight).coerceIn(0f, 1f),
        blue = (foreground.blue * fgWeight + background.blue * bgWeight).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun Color.toHexString(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

fun parseHexColorOrNull(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    val parsed = cleaned.toLongOrNull(16) ?: return null
    val argb = if (cleaned.length == 6) (0xFF000000L or parsed) else parsed
    return Color(argb)
}

fun contrastAwareText(background: Color, preferred: Color): Color {
    return if (abs(background.luminance() - preferred.luminance()) > 0.25f) {
        preferred
    } else if (background.luminance() > 0.5f) {
        Color(0xFF111827)
    } else {
        Color(0xFFF8FAFC)
    }
}

@Composable
fun GalleryXTheme(
    settings: GallerySettings,
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (settings.useCustomTheme) {
        buildCustomColorScheme(
            bgColor = Color(settings.backgroundColorArgb),
            textColor = Color(settings.primaryTextColorArgb),
            accentColor = Color(settings.accentColorArgb)
        )
    } else {
        if (systemDarkTheme) DefaultDarkScheme else DefaultLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GalleryXTypography,
        shapes = GalleryXShapes,
        content = content
    )
}
