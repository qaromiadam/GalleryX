package com.Gallery.X.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.Gallery.X.R
import com.Gallery.X.data.GallerySettings
import com.Gallery.X.ui.icons.AppIcons
import com.Gallery.X.ui.theme.PresetAccentSwatches
import com.Gallery.X.ui.theme.PresetBackgroundSwatches
import com.Gallery.X.ui.theme.PresetTextSwatches
import com.Gallery.X.ui.theme.parseHexColorOrNull
import com.Gallery.X.ui.theme.toHexString
import kotlin.math.roundToInt

private enum class ColorTarget {
    BACKGROUND,
    TEXT,
    ACCENT
}

@Composable
fun SettingsScreen(
    settings: GallerySettings,
    onToggleCustomTheme: (Boolean) -> Unit,
    onPickBackgroundColor: (Long) -> Unit,
    onPickTextColor: (Long) -> Unit,
    onPickAccentColor: (Long) -> Unit,
    onResetColors: (Boolean) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onSetLanguageTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSystemDark = isSystemInDarkTheme()
    var activePickerTarget by remember { mutableStateOf<ColorTarget?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            // Top Live UI Theme Preview Card
            item(key = "live_preview") {
                LiveThemePreviewCard(gridColumns = settings.gridColumns)
            }

            // Theme & Colors
            item(key = "theme_section") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = AppIcons.ColorLens,
                            title = stringResource(R.string.settings_theme_section)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_use_custom_theme),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.settings_use_custom_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.useCustomTheme,
                                onCheckedChange = onToggleCustomTheme,
                                modifier = Modifier.testTag("custom_theme_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        ColorSettingRow(
                            label = stringResource(R.string.settings_bg_color),
                            color = if (settings.useCustomTheme) Color(settings.backgroundColorArgb) else MaterialTheme.colorScheme.background,
                            testTag = "pick_bg_color_row",
                            onClick = { activePickerTarget = ColorTarget.BACKGROUND }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ColorSettingRow(
                            label = stringResource(R.string.settings_text_color),
                            color = if (settings.useCustomTheme) Color(settings.primaryTextColorArgb) else MaterialTheme.colorScheme.onBackground,
                            testTag = "pick_text_color_row",
                            onClick = { activePickerTarget = ColorTarget.TEXT }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ColorSettingRow(
                            label = stringResource(R.string.settings_accent_color),
                            color = if (settings.useCustomTheme) Color(settings.accentColorArgb) else MaterialTheme.colorScheme.primary,
                            testTag = "pick_accent_color_row",
                            onClick = { activePickerTarget = ColorTarget.ACCENT }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { onResetColors(isSystemDark) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reset_colors_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.Reset,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_reset_colors))
                        }
                    }
                }
            }

            // Grid Density
            item(key = "grid_section") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = AppIcons.GridView,
                            title = stringResource(R.string.settings_layout_section)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_grid_columns),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(3, 4, 5).forEach { cols ->
                                val selected = settings.gridColumns == cols
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetGridColumns(cols) },
                                    label = {
                                        Text(stringResource(R.string.settings_columns_n, cols))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grid_cols_$cols")
                                )
                            }
                        }
                    }
                }
            }

            // Language Switcher (System / EN / FR / AR RTL)
            item(key = "language_section") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = AppIcons.Language,
                            title = stringResource(R.string.settings_language_section)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val languageOptions = listOf(
                            "" to stringResource(R.string.lang_system),
                            "en" to stringResource(R.string.lang_en),
                            "fr" to stringResource(R.string.lang_fr),
                            "ar" to stringResource(R.string.lang_ar)
                        )

                        languageOptions.forEach { (tag, label) ->
                            val selected = settings.languageTag == tag
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSetLanguageTag(tag)
                                        val locales = if (tag.isEmpty()) {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(tag)
                                        }
                                        AppCompatDelegate.setApplicationLocales(locales)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("lang_option_${tag.ifEmpty { "sys" }}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Zero-Network Offline Architecture Card
            item(key = "privacy_section") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = AppIcons.Shield,
                            title = stringResource(R.string.settings_privacy_section)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_privacy_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    activePickerTarget?.let { target ->
        val initialColor = when (target) {
            ColorTarget.BACKGROUND -> if (settings.useCustomTheme) Color(settings.backgroundColorArgb) else MaterialTheme.colorScheme.background
            ColorTarget.TEXT -> if (settings.useCustomTheme) Color(settings.primaryTextColorArgb) else MaterialTheme.colorScheme.onBackground
            ColorTarget.ACCENT -> if (settings.useCustomTheme) Color(settings.accentColorArgb) else MaterialTheme.colorScheme.primary
        }
        val swatches = when (target) {
            ColorTarget.BACKGROUND -> PresetBackgroundSwatches
            ColorTarget.TEXT -> PresetTextSwatches
            ColorTarget.ACCENT -> PresetAccentSwatches
        }
        HexRgbColorPickerDialog(
            initialColor = initialColor,
            swatches = swatches,
            onConfirm = { picked ->
                val argb = picked.toArgb().toLong() and 0xFFFFFFFFL
                when (target) {
                    ColorTarget.BACKGROUND -> onPickBackgroundColor(argb)
                    ColorTarget.TEXT -> onPickTextColor(argb)
                    ColorTarget.ACCENT -> onPickAccentColor(argb)
                }
                activePickerTarget = null
            },
            onDismiss = { activePickerTarget = null }
        )
    }
}

@Composable
private fun LiveThemePreviewCard(gridColumns: Int) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.BrandMarkGX,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_preview_subtitle, gridColumns),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = stringResource(R.string.settings_preview_chip),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                repeat(gridColumns) { idx ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (idx == 0) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ColorSettingRow(
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = color.toHexString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}

@Composable
private fun HexRgbColorPickerDialog(
    initialColor: Color,
    swatches: List<Long>,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red * 255f) }
    var green by remember { mutableFloatStateOf(initialColor.green * 255f) }
    var blue by remember { mutableFloatStateOf(initialColor.blue * 255f) }
    var hexInput by remember { mutableStateOf(initialColor.toHexString()) }

    val currentColor = Color(
        red = (red.roundToInt().coerceIn(0, 255)) / 255f,
        green = (green.roundToInt().coerceIn(0, 255)) / 255f,
        blue = (blue.roundToInt().coerceIn(0, 255)) / 255f,
        alpha = 1f
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )

                // Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    swatches.forEach { argb ->
                        val swatchColor = Color(argb)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    red = swatchColor.red * 255f
                                    green = swatchColor.green * 255f
                                    blue = swatchColor.blue * 255f
                                    hexInput = swatchColor.toHexString()
                                }
                        )
                    }
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { next ->
                        hexInput = next
                        parseHexColorOrNull(next)?.let { parsed ->
                            red = parsed.red * 255f
                            green = parsed.green * 255f
                            blue = parsed.blue * 255f
                        }
                    },
                    label = { Text(stringResource(R.string.color_hex_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                RgbSlider(
                    label = stringResource(R.string.color_red),
                    value = red,
                    activeColor = Color(0xFFEF4444),
                    onValueChange = {
                        red = it
                        hexInput = Color(
                            it.roundToInt().coerceIn(0, 255),
                            green.roundToInt().coerceIn(0, 255),
                            blue.roundToInt().coerceIn(0, 255)
                        ).toHexString()
                    }
                )
                RgbSlider(
                    label = stringResource(R.string.color_green),
                    value = green,
                    activeColor = Color(0xFF22C55E),
                    onValueChange = {
                        green = it
                        hexInput = Color(
                            red.roundToInt().coerceIn(0, 255),
                            it.roundToInt().coerceIn(0, 255),
                            blue.roundToInt().coerceIn(0, 255)
                        ).toHexString()
                    }
                )
                RgbSlider(
                    label = stringResource(R.string.color_blue),
                    value = blue,
                    activeColor = Color(0xFF3B82F6),
                    onValueChange = {
                        blue = it
                        hexInput = Color(
                            red.roundToInt().coerceIn(0, 255),
                            green.roundToInt().coerceIn(0, 255),
                            it.roundToInt().coerceIn(0, 255)
                        ).toHexString()
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentColor) },
                modifier = Modifier.testTag("confirm_color_button")
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text("${value.roundToInt()}", style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
