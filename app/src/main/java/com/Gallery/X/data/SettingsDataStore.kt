package com.Gallery.X.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "galleryx_settings")

data class GallerySettings(
    val useCustomTheme: Boolean = false,
    val backgroundColorArgb: Long = 0xFF0E1014,
    val primaryTextColorArgb: Long = 0xFFF3F4F6,
    val accentColorArgb: Long = 0xFF60A5FA,
    val gridColumns: Int = 3,
    val languageTag: String = ""
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val USE_CUSTOM_THEME = booleanPreferencesKey("use_custom_theme")
        val BG_COLOR = longPreferencesKey("bg_color_argb")
        val TEXT_COLOR = longPreferencesKey("text_color_argb")
        val ACCENT_COLOR = longPreferencesKey("accent_color_argb")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
    }

    val settingsFlow: Flow<GallerySettings> = context.settingsDataStore.data.map { prefs ->
        GallerySettings(
            useCustomTheme = prefs[Keys.USE_CUSTOM_THEME] ?: false,
            backgroundColorArgb = prefs[Keys.BG_COLOR] ?: 0xFF0E1014,
            primaryTextColorArgb = prefs[Keys.TEXT_COLOR] ?: 0xFFF3F4F6,
            accentColorArgb = prefs[Keys.ACCENT_COLOR] ?: 0xFF60A5FA,
            gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(3, 5),
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: ""
        )
    }

    suspend fun setUseCustomTheme(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_CUSTOM_THEME] = enabled }
    }

    suspend fun setBackgroundColor(argb: Long) {
        context.settingsDataStore.edit {
            it[Keys.USE_CUSTOM_THEME] = true
            it[Keys.BG_COLOR] = argb
        }
    }

    suspend fun setPrimaryTextColor(argb: Long) {
        context.settingsDataStore.edit {
            it[Keys.USE_CUSTOM_THEME] = true
            it[Keys.TEXT_COLOR] = argb
        }
    }

    suspend fun setAccentColor(argb: Long) {
        context.settingsDataStore.edit {
            it[Keys.USE_CUSTOM_THEME] = true
            it[Keys.ACCENT_COLOR] = argb
        }
    }

    suspend fun setGridColumns(columns: Int) {
        context.settingsDataStore.edit {
            it[Keys.GRID_COLUMNS] = columns.coerceIn(3, 5)
        }
    }

    suspend fun setLanguageTag(tag: String) {
        context.settingsDataStore.edit {
            it[Keys.LANGUAGE_TAG] = tag
        }
    }

    suspend fun resetThemeDefaults(isSystemDark: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.USE_CUSTOM_THEME] = false
            if (isSystemDark) {
                it[Keys.BG_COLOR] = 0xFF0E1014
                it[Keys.TEXT_COLOR] = 0xFFF3F4F6
                it[Keys.ACCENT_COLOR] = 0xFF60A5FA
            } else {
                it[Keys.BG_COLOR] = 0xFFF7F7F5
                it[Keys.TEXT_COLOR] = 0xFF141518
                it[Keys.ACCENT_COLOR] = 0xFF2563EB
            }
        }
    }
}
